package cn.yueji.balance;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class AppLockTest {
    static final int FAST = 1000;

    /** In-memory stand-in for SharedPreferences. */
    static class MapVault implements AppLock.Vault {
        final Map<String, Object> map = new HashMap<>();
        @Override public String getString(String key) { return (String) map.get(key); }
        @Override public long getLong(String key, long fallback) { Object v = map.get(key); return v == null ? fallback : (Long) v; }
        @Override public void putString(String key, String value) { map.put(key, value); }
        @Override public void putLong(String key, long value) { map.put(key, value); }
        @Override public void clear() { map.clear(); }
    }

    MapVault withPin(String pin) { MapVault v = new MapVault(); AppLock.setPin(v, pin, FAST); return v; }

    @Test public void onlySixAsciiDigitsAreAPin() {
        assertTrue(AppLock.validPin("123456"));
        assertTrue(AppLock.validPin("000000"));
        for (String bad : new String[]{null, "", "12345", "1234567", "12a456", "12 456", "١٢٣٤٥٦", "１２３４５６", "-12345"})
            assertFalse("rejects " + bad, AppLock.validPin(bad));
    }

    @Test public void hashDependsOnPinSaltAndIterationsButNotOnRepeats() {
        byte[] salt = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, other = salt.clone();
        other[0] = 9;
        byte[] a = AppLock.hash("123456", salt, FAST);
        assertEquals(32, a.length);
        assertArrayEquals(a, AppLock.hash("123456", salt, FAST));
        assertFalse(AppLock.sameBytes(a, AppLock.hash("123457", salt, FAST)));
        assertFalse(AppLock.sameBytes(a, AppLock.hash("123456", other, FAST)));
        assertFalse(AppLock.sameBytes(a, AppLock.hash("123456", salt, FAST + 1)));
    }

    @Test public void sameBytesHandlesNullAndLengths() {
        assertTrue(AppLock.sameBytes(new byte[]{1, 2}, new byte[]{1, 2}));
        assertFalse(AppLock.sameBytes(new byte[]{1, 2}, new byte[]{1, 3}));
        assertFalse(AppLock.sameBytes(new byte[]{1, 2}, new byte[]{1, 2, 3}));
        assertFalse(AppLock.sameBytes(null, new byte[]{1}));
        assertFalse(AppLock.sameBytes(new byte[]{1}, null));
    }

    @Test public void thePinIsNeverStoredInTheClear() {
        MapVault v = withPin("246810");
        for (Object value : v.map.values()) assertFalse(String.valueOf(value).contains("246810"));
        assertTrue(AppLock.enabled(v));
        assertTrue(v.getString("pin").startsWith("v1$" + FAST + "$"));
    }

    @Test public void verifyAcceptsTheRightPinAndRejectsOthers() {
        MapVault v = withPin("135790");
        assertTrue(AppLock.verify(v, "135790", 0));
        assertFalse(AppLock.verify(v, "135791", 0));
        assertFalse(AppLock.verify(v, "", 0));
        assertFalse(AppLock.verify(v, null, 0));
        assertTrue("a wrong guess does not break the next right one", AppLock.verify(v, "135790", 0));
    }

    @Test public void nothingIsEnabledWithoutAPin() {
        MapVault v = new MapVault();
        assertFalse(AppLock.enabled(v));
        assertFalse(AppLock.verify(v, "123456", 0));
        v.putString("pin", "garbage");
        assertFalse("a damaged record counts as no lock", AppLock.enabled(v));
        v.putString("pin", "v1$x$y$z");
        assertFalse(AppLock.enabled(v));
    }

    @Test public void settingAnInvalidPinThrowsAndChangesNothing() {
        MapVault v = withPin("111111");
        try { AppLock.setPin(v, "12345", FAST); fail(); } catch (IllegalArgumentException expected) { }
        assertTrue(AppLock.verify(v, "111111", 0));
    }

    @Test public void lockoutScheduleIs30sThen1minThen5minThen15min() {
        assertEquals(0, AppLock.lockoutFor(0));
        assertEquals(0, AppLock.lockoutFor(4));
        assertEquals(30_000, AppLock.lockoutFor(5));
        assertEquals(60_000, AppLock.lockoutFor(6));
        assertEquals(300_000, AppLock.lockoutFor(7));
        assertEquals(900_000, AppLock.lockoutFor(8));
        assertEquals(900_000, AppLock.lockoutFor(500));
    }

    @Test public void fifthWrongPinLocksTheKeypadForThirtySeconds() {
        MapVault v = withPin("123456");
        long now = 1_000_000;
        for (int i = 0; i < 4; i++) { assertFalse(AppLock.verify(v, "000000", now)); assertEquals(0, AppLock.lockedFor(v, now)); }
        assertFalse(AppLock.verify(v, "000000", now));
        assertEquals(30_000, AppLock.lockedFor(v, now));
        assertEquals(5, v.getLong("fails", 0));
        assertFalse("the right PIN is refused while locked", AppLock.verify(v, "123456", now + 10_000));
        assertEquals("attempts during a lockout are not counted", 5, v.getLong("fails", 0));
        assertEquals(20_000, AppLock.lockedFor(v, now + 10_000));
        assertEquals(0, AppLock.lockedFor(v, now + 30_000));
        assertTrue(AppLock.verify(v, "123456", now + 30_001));
        assertEquals("success clears the counter", 0, v.getLong("fails", 0));
        assertEquals(0, AppLock.lockedFor(v, now + 30_001));
    }

    @Test public void lockoutsEscalateWithEachFurtherWrongPin() {
        MapVault v = withPin("123456");
        long now = 0;
        for (int i = 0; i < 5; i++) AppLock.verify(v, "000000", now);
        assertEquals(30_000, AppLock.lockedFor(v, now));
        now += 31_000;
        AppLock.verify(v, "000000", now);
        assertEquals(60_000, AppLock.lockedFor(v, now));
        now += 61_000;
        AppLock.verify(v, "000000", now);
        assertEquals(300_000, AppLock.lockedFor(v, now));
        now += 301_000;
        AppLock.verify(v, "000000", now);
        assertEquals(900_000, AppLock.lockedFor(v, now));
        now += 901_000;
        AppLock.verify(v, "000000", now);
        assertEquals("stays at the 15 minute cap", 900_000, AppLock.lockedFor(v, now));
    }

    @Test public void aRightPinBeforeTheFifthWrongOneResetsTheCount() {
        MapVault v = withPin("123456");
        for (int i = 0; i < 4; i++) AppLock.verify(v, "000000", 0);
        assertTrue(AppLock.verify(v, "123456", 0));
        for (int i = 0; i < 4; i++) { AppLock.verify(v, "000000", 0); assertEquals(0, AppLock.lockedFor(v, 0)); }
    }

    @Test public void theAttemptIsCountedBeforeTheCheckSoKillingTheProcessCannotBuyFreeGuesses() {
        MapVault seed = withPin("123456");
        final boolean[] counted = {false};
        MapVault spy = new MapVault() {
            @Override public void putLong(String key, long value) { super.putLong(key, value); if (key.equals("fails") && value == 1) counted[0] = true; }
        };
        spy.map.putAll(seed.map);
        assertTrue(AppLock.verify(spy, "123456", 0));
        assertTrue("the counter was already written when the hash ran", counted[0]);
        assertEquals("and cleared again by the success", 0, spy.getLong("fails", -1));
    }

    @Test public void aClockMovedFarBackCannotStretchTheLockoutPastTheCap() {
        MapVault v = withPin("123456");
        v.putLong("until", 10_000_000_000L);
        assertEquals(AppLock.MAX_LOCKOUT_MILLIS, AppLock.lockedFor(v, 0));
    }

    @Test public void settingANewPinClearsFailuresAndLockout() {
        MapVault v = withPin("123456");
        for (int i = 0; i < 5; i++) AppLock.verify(v, "000000", 0);
        assertTrue(AppLock.lockedFor(v, 0) > 0);
        AppLock.setPin(v, "654321", FAST);
        assertEquals(0, AppLock.lockedFor(v, 0));
        assertTrue(AppLock.verify(v, "654321", 0));
        assertFalse(AppLock.verify(v, "123456", 0));
    }

    @Test public void clearingTheVaultDisablesTheLock() {
        MapVault v = withPin("123456");
        v.putLong("bio", 1);
        v.clear();
        assertFalse(AppLock.enabled(v));
        assertEquals(0, v.getLong("bio", 0));
    }

    @Test public void lockDecisionFollowsTheGracePeriod() {
        long g = AppLock.GRACE_MILLIS;
        assertFalse("disabled never locks", AppLock.decideLock(false, false, 0, 1_000_000));
        assertFalse(AppLock.decideLock(false, true, 0, 1_000_000));
        assertTrue("a fresh process locks", AppLock.decideLock(true, false, -1, 5));
        assertFalse("no time in background", AppLock.decideLock(true, true, -1, 1_000_000));
        assertFalse("14 s away (file picker, permission dialog)", AppLock.decideLock(true, true, 1000, 1000 + g - 1000));
        assertFalse("exactly the grace period", AppLock.decideLock(true, true, 1000, 1000 + g));
        assertTrue("just over the grace period", AppLock.decideLock(true, true, 1000, 1000 + g + 1));
        assertTrue("long away", AppLock.decideLock(true, true, 1000, 10 * 60_000));
    }
}
