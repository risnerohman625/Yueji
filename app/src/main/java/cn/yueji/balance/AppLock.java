package cn.yueji.balance;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemClock;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.Executor;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The app lock: a six-digit PIN (stored only as a salted PBKDF2 hash) with optional fingerprint / face unlock.
 * Everything that decides something (hashing, lockout, when to lock again) is plain Java over a tiny {@link Vault},
 * so it is unit-tested without a device. Platform biometrics live in the nested {@link Bio}, which is only loaded
 * when a prompt is actually shown, so older devices never touch those classes.
 */
final class AppLock {
    static final int PIN_LENGTH = 6;
    static final int ITERATIONS = 100_000;
    static final long GRACE_MILLIS = 15_000;
    /** Wrong PINs allowed before the first lockout. */
    static final int FREE_ATTEMPTS = 5;
    static final long MAX_LOCKOUT_MILLIS = 15 * 60_000L;

    private static final String PREFS = "applock", K_PIN = "pin", K_BIO = "bio", K_FAILS = "fails", K_UNTIL = "until";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ------------------------------------------------------------------ pure core
    /** Minimal key-value store; the app uses SharedPreferences, tests use a map. */
    interface Vault {
        String getString(String key);
        long getLong(String key, long fallback);
        void putString(String key, String value);
        void putLong(String key, long value);
        void clear();
    }

    private static final class Record {
        final int iterations;
        final byte[] salt, hash;
        Record(int iterations, byte[] salt, byte[] hash) { this.iterations = iterations; this.salt = salt; this.hash = hash; }
    }

    /** Exactly six ASCII digits. */
    static boolean validPin(String pin) {
        if (pin == null || pin.length() != PIN_LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static byte[] hash(String pin, byte[] salt, int iterations) {
        char[] chars = pin.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    static boolean sameBytes(byte[] a, byte[] b) {
        return a != null && b != null && MessageDigest.isEqual(a, b);
    }

    /** How long the keypad stays locked after the given number of consecutive wrong PINs: 30 s, 1 min, 5 min, then 15 min. */
    static long lockoutFor(int fails) {
        if (fails < FREE_ATTEMPTS) return 0;
        switch (fails - FREE_ATTEMPTS) {
            case 0: return 30_000L;
            case 1: return 60_000L;
            case 2: return 300_000L;
            default: return MAX_LOCKOUT_MILLIS;
        }
    }

    private static String enc(byte[] b) { return Base64.getEncoder().withoutPadding().encodeToString(b); }

    private static Record read(Vault v) {
        String s = v.getString(K_PIN);
        if (s == null) return null;
        try {
            String[] p = s.split("\\$");
            if (p.length != 4 || !p[0].equals("v1")) return null;
            int iterations = Integer.parseInt(p[1]);
            if (iterations < 1) return null;
            return new Record(iterations, Base64.getDecoder().decode(p[2]), Base64.getDecoder().decode(p[3]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean enabled(Vault v) { return read(v) != null; }

    static void setPin(Vault v, String pin, int iterations) {
        if (!validPin(pin)) throw new IllegalArgumentException("密码须为6位数字");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] h = hash(pin, salt, iterations);
        synchronized (AppLock.class) {
            v.putString(K_PIN, "v1$" + iterations + "$" + enc(salt) + "$" + enc(h));
            v.putLong(K_FAILS, 0);
            v.putLong(K_UNTIL, 0);
        }
    }

    /** Milliseconds left of the current lockout (0 when the keypad is free). A clock moved far back cannot extend it past the maximum. */
    static long lockedFor(Vault v, long now) {
        long left = v.getLong(K_UNTIL, 0) - now;
        return left <= 0 ? 0 : Math.min(left, MAX_LOCKOUT_MILLIS);
    }

    /**
     * Checks a PIN. The attempt is counted (and its lockout written) before the slow hash runs and cleared only on
     * success, so killing the process mid-check cannot be used to get free guesses. Nothing is checked or counted while locked out.
     */
    static boolean verify(Vault v, String pin, long now) {
        synchronized (AppLock.class) {
            Record r = read(v);
            if (r == null || pin == null) return false;
            if (lockedFor(v, now) > 0) return false;
            long fails = v.getLong(K_FAILS, 0) + 1;
            v.putLong(K_FAILS, fails);
            long wait = lockoutFor((int) Math.min(fails, 1000));
            if (wait > 0) v.putLong(K_UNTIL, now + wait);
            boolean ok = sameBytes(hash(pin, r.salt, r.iterations), r.hash);
            if (ok) {
                v.putLong(K_FAILS, 0);
                v.putLong(K_UNTIL, 0);
            }
            return ok;
        }
    }

    /** Lock when enabled and either nobody has unlocked this process yet or the app was away longer than the grace period. */
    static boolean decideLock(boolean enabled, boolean unlocked, long backgroundAt, long now) {
        if (!enabled) return false;
        if (!unlocked) return true;
        return backgroundAt >= 0 && now - backgroundAt > GRACE_MILLIS;
    }

    // ------------------------------------------------------------------ Android-facing API
    private static final class PrefsVault implements Vault {
        final SharedPreferences p;
        PrefsVault(SharedPreferences p) { this.p = p; }
        @Override public String getString(String key) { return p.getString(key, null); }
        @Override public long getLong(String key, long fallback) { return p.getLong(key, fallback); }
        @Override public void putString(String key, String value) { p.edit().putString(key, value).commit(); }
        @Override public void putLong(String key, long value) { p.edit().putLong(key, value).commit(); }
        @Override public void clear() { p.edit().clear().commit(); }
    }

    private static Vault vault(Context c) {
        return new PrefsVault(c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    static boolean enabled(Context c) { return enabled(vault(c)); }
    static boolean biometricEnabled(Context c) { return vault(c).getLong(K_BIO, 0) == 1; }
    static void setBiometric(Context c, boolean on) { vault(c).putLong(K_BIO, on ? 1 : 0); }
    static void setPin(Context c, String pin) { setPin(vault(c), pin, ITERATIONS); }
    static boolean verify(Context c, String pin) { return verify(vault(c), pin, System.currentTimeMillis()); }
    static long lockedForMillis(Context c) { return lockedFor(vault(c), System.currentTimeMillis()); }
    static int failedAttempts(Context c) { return (int) vault(c).getLong(K_FAILS, 0); }

    /** Removes the PIN, the biometric switch and the failure counters. */
    static void disable(Context c) {
        synchronized (AppLock.class) {
            vault(c).clear();
            backgroundAt = -1;
        }
    }

    // ------------------------------------------------------------------ when to lock
    private static boolean unlocked;
    private static long backgroundAt = -1;

    /** Call from onStop. */
    static void markBackground() { backgroundAt = SystemClock.elapsedRealtime(); }

    /** Call after a successful unlock, and after creating a PIN (the user just proved presence). */
    static void markUnlocked() {
        unlocked = true;
        backgroundAt = -1;
    }

    /** True when the lock screen must be shown now. A true answer stays true until markUnlocked(). Call from onCreate and onResume. */
    static boolean shouldLockNow(Context c) {
        boolean lock = decideLock(enabled(c), unlocked, backgroundAt, SystemClock.elapsedRealtime());
        backgroundAt = -1;
        if (lock) unlocked = false;
        return lock;
    }

    // ------------------------------------------------------------------ biometrics (platform android.hardware.biometrics, API 28+)
    interface BioResult {
        void success();
        /** code is a BiometricPrompt error code; 13 means the "use password" button, 10 / 5 mean the user or system cancelled. */
        void error(int code, CharSequence message);
    }

    static boolean biometricAvailable(Context c) {
        try {
            return Bio.available(c);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Shows the system prompt. Returns the signal to cancel it, or throws if it cannot be shown (caller hides the button). */
    static CancellationSignal authenticate(Activity a, String title, BioResult result) {
        return Bio.start(a, title, result);
    }

    /** Platform classes are confined here and only loaded on devices that have them. */
    private static final class Bio {
        static boolean available(Context c) {
            if (Build.VERSION.SDK_INT >= 30) return available30(c);
            if (Build.VERSION.SDK_INT == 29) return available29(c);
            if (Build.VERSION.SDK_INT == 28) return available28(c);
            return false;
        }

        @TargetApi(30)
        private static boolean available30(Context c) {
            android.hardware.biometrics.BiometricManager m = c.getSystemService(android.hardware.biometrics.BiometricManager.class);
            // BIOMETRIC_WEAK also accepts face unlock on phones that classify it as weak; the PIN remains the primary secret.
            return m != null && m.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
        }

        @TargetApi(29)
        @SuppressWarnings("deprecation")
        private static boolean available29(Context c) {
            android.hardware.biometrics.BiometricManager m = c.getSystemService(android.hardware.biometrics.BiometricManager.class);
            return m != null && m.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
        }

        @TargetApi(28)
        @SuppressWarnings("deprecation")
        private static boolean available28(Context c) {
            android.hardware.fingerprint.FingerprintManager fm =
                    (android.hardware.fingerprint.FingerprintManager) c.getSystemService(Context.FINGERPRINT_SERVICE);
            return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        }

        @TargetApi(28)
        static CancellationSignal start(Activity a, String title, BioResult result) {
            Executor executor = a.getMainExecutor();
            android.hardware.biometrics.BiometricPrompt.Builder b = new android.hardware.biometrics.BiometricPrompt.Builder(a).setTitle(title);
            b.setNegativeButton("使用密码", executor, (dialog, which) -> result.error(13, "使用密码"));
            if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK);
            android.hardware.biometrics.BiometricPrompt prompt = b.build();
            CancellationSignal cancel = new CancellationSignal();
            prompt.authenticate(cancel, executor, new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) { result.success(); }
                @Override public void onAuthenticationError(int code, CharSequence message) { result.error(code, message); }
            });
            return cancel;
        }
    }

    private AppLock() {}
}
