package cn.yueji.balance;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Full-screen PIN screen. Opaque, swallows every touch, and sizes itself from the space it is given: the header
 * (icon, title, hint, six dots) and the 3x4 keypad share the height, the keys shrink on short screens and the
 * layout switches to two columns in landscape, so nothing ever scrolls. Colours are read from {@link Ui} when the
 * overlay is built, so it follows the current mode and accent.
 *
 * Back is the caller's job: {@link #onBack()} returns false in UNLOCK mode (let the activity leave the app) and
 * cancels the screen in every other mode.
 */
final class LockOverlay extends FrameLayout {
    enum Mode { UNLOCK, CREATE, CONFIRM_DISABLE, CHANGE_VERIFY_OLD }

    private static final int DIGIT = 0, BIO = 1, DELETE = 2;

    private final Activity activity;
    private final Mode mode;
    private final Runnable onDone, onCancel;
    private final float u;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean biometric, cancellable, verifies;

    private final Stage stage;
    private final TextView title, message, cancelLink;
    private final Glyph glyph;
    private final Dots dots;
    private final Key[] keys = new Key[12];

    private final StringBuilder entry = new StringBuilder();
    private int phase;
    private String firstEntry;
    private boolean busy, locked, attached, autoPrompted;
    private CancellationSignal bioCancel;
    private String hint = "";

    LockOverlay(Activity a, Mode mode, Runnable onDone, Runnable onCancel) {
        super(a);
        this.activity = a;
        this.mode = mode;
        this.onDone = onDone;
        this.onCancel = onCancel;
        u = a.getResources().getDisplayMetrics().density;
        verifies = mode != Mode.CREATE;
        cancellable = mode != Mode.UNLOCK;
        biometric = (mode == Mode.UNLOCK || mode == Mode.CONFIRM_DISABLE) && AppLock.biometricEnabled(a) && AppLock.biometricAvailable(a);

        setBackgroundColor(Ui.BG);
        setClickable(true);
        setFocusable(true);
        stage = new Stage(a);
        addView(stage, new LayoutParams(-1, -1));

        glyph = new Glyph(a);
        stage.addView(glyph);
        title = label(a, 20, Ui.INK, true);
        stage.addView(title);
        message = label(a, 13.5f, Ui.MUTED, false);
        message.setMaxLines(2);
        stage.addView(message);
        dots = new Dots(a);
        stage.addView(dots);
        for (int i = 0; i < 12; i++) {
            int kind = i == 9 ? BIO : i == 11 ? DELETE : DIGIT;
            int digit = i < 9 ? i + 1 : 0;
            Key k = new Key(a, kind, digit);
            if (kind == BIO && !biometric) k.setVisibility(INVISIBLE);
            keys[i] = k;
            stage.addView(k);
            final int d = digit;
            if (kind == DIGIT) k.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); press(d); });
            else if (kind == DELETE) {
                k.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); backspace(); });
                k.setOnLongClickListener(v -> { clearEntry(); return true; });
            } else if (biometric) k.setOnClickListener(v -> promptBiometric());
        }
        cancelLink = label(a, 15, Ui.ACCENT, true);
        cancelLink.setText("取消");
        cancelLink.setPadding((int) (18 * u), (int) (10 * u), (int) (18 * u), (int) (10 * u));
        cancelLink.setClickable(true);
        cancelLink.setOnClickListener(v -> cancel());
        cancelLink.setVisibility(cancellable ? VISIBLE : GONE);
        stage.addView(cancelLink);

        setOnApplyWindowInsetsListener((v, insets) -> { applyInsets(insets); return insets; });
        refreshTexts();
        if (verifies && AppLock.lockedForMillis(a) > 0) tick.run();
    }

    /** Adds a new overlay on top of the whole window. It removes itself before onDone / onCancel run. */
    static LockOverlay show(Activity a, Mode mode, Runnable onDone, Runnable onCancel) {
        LockOverlay o = new LockOverlay(a, mode, onDone, onCancel);
        ((ViewGroup) a.getWindow().getDecorView()).addView(o, new FrameLayout.LayoutParams(-1, -1));
        return o;
    }

    void dismiss() {
        cancelBiometric();
        handler.removeCallbacksAndMessages(null);
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
    }

    /** Returns true when the back press was used (the screen was cancelled). False in UNLOCK mode: the caller should leave the app. */
    boolean onBack() {
        if (!cancellable) return false;
        cancel();
        return true;
    }

    /** Shows the system fingerprint / face prompt (no-op when unavailable or not enabled). */
    void promptBiometric() {
        if (!biometric || !attached || Build.VERSION.SDK_INT < 28) return;
        cancelBiometric();
        try {
            bioCancel = AppLock.authenticate(activity, mode == Mode.UNLOCK ? "解锁 Yueji" : "关闭应用锁", new AppLock.BioResult() {
                @Override public void success() {
                    bioCancel = null;
                    if (!attached) return;
                    if (mode == Mode.UNLOCK) AppLock.markUnlocked();
                    finish(onDone);
                }
                @Override public void error(int code, CharSequence text) {
                    bioCancel = null;
                    if (!attached) return;
                    if (code == 13 || code == 10 || code == 5 || code == 3) return;
                    String m = code == 7 ? "指纹/面容已暂时锁定，请输入密码"
                            : code == 9 ? "指纹/面容已锁定，请输入密码"
                            : text != null && text.length() > 0 && text.length() < 24 ? text.toString() : "暂时无法使用指纹/面容，请输入密码";
                    showError(m);
                }
            });
        } catch (RuntimeException e) {
            bioCancel = null;
            showError("暂时无法使用指纹/面容，请输入密码");
        }
    }

    // ------------------------------------------------------------------ state machine
    private void refreshTexts() {
        switch (mode) {
            case UNLOCK: title.setText("解锁 Yueji"); hint = "输入 6 位数字密码"; break;
            case CREATE:
                title.setText(phase == 0 ? "设置 6 位数字密码" : "再次输入密码");
                hint = phase == 0 ? "用于打开 Yueji" : "请再输入一次以确认";
                break;
            case CONFIRM_DISABLE: title.setText("关闭应用锁"); hint = "输入当前密码以确认"; break;
            default: title.setText("验证当前密码"); hint = "验证后可设置新密码"; break;
        }
        showHint();
    }

    private void showHint() {
        message.setTextColor(Ui.MUTED);
        message.setText(hint);
    }

    private void showError(String text) {
        message.setTextColor(Ui.RED);
        message.setText(text);
    }

    private void press(int digit) {
        if (busy || locked || entry.length() >= AppLock.PIN_LENGTH) return;
        if (entry.length() == 0 && message.getCurrentTextColor() == Ui.RED) showHint();
        dots.setError(false);
        entry.append((char) ('0' + digit));
        dots.set(entry.length());
        if (entry.length() == AppLock.PIN_LENGTH) {
            busy = true;
            final String pin = entry.toString();
            handler.postDelayed(() -> submit(pin), 140);
        }
    }

    private void backspace() {
        if (busy || locked || entry.length() == 0) return;
        entry.setLength(entry.length() - 1);
        dots.set(entry.length());
    }

    private void clearEntry() {
        if (busy) return;
        entry.setLength(0);
        dots.setError(false);
        dots.set(0);
    }

    private void submit(String pin) {
        if (!attached) return;
        if (mode == Mode.CREATE) {
            if (phase == 0) {
                firstEntry = pin;
                phase = 1;
                resetEntry();
                refreshTexts();
            } else if (pin.equals(firstEntry)) {
                AppLock.setPin(activity, pin);
                AppLock.markUnlocked();
                busy = false;
                finish(onDone);
            } else {
                phase = 0;
                firstEntry = null;
                reject();
                refreshTexts();
                showError("两次输入不一致，请重新设置");
                handler.postDelayed(this::resetEntry, 380);
            }
            return;
        }
        new Thread(() -> {
            final boolean ok = AppLock.verify(activity, pin);
            handler.post(() -> {
                if (!attached) return;
                if (ok) {
                    if (mode == Mode.UNLOCK) AppLock.markUnlocked();
                    busy = false;
                    finish(onDone);
                    return;
                }
                reject();
                if (AppLock.lockedForMillis(activity) > 0) tick.run();
                else showError("密码错误，还可再试 " + Math.max(1, AppLock.FREE_ATTEMPTS - AppLock.failedAttempts(activity)) + " 次");
                handler.postDelayed(this::resetEntry, 380);
            });
        }, "applock-verify").start();
    }

    private void resetEntry() {
        busy = false;
        entry.setLength(0);
        dots.setError(false);
        dots.set(0);
    }

    private void reject() {
        dots.setError(true);
        dots.shake();
        dots.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }

    /** Keeps the keypad closed and shows the countdown while a lockout is running. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long left = AppLock.lockedForMillis(activity);
            if (left > 0) {
                locked = true;
                for (Key k : keys) k.setEnabled(false);
                showError("尝试次数过多，请 " + format((left + 999) / 1000) + " 后重试");
                handler.postDelayed(this, 500);
            } else if (locked) {
                locked = false;
                for (Key k : keys) k.setEnabled(true);
                showHint();
            }
        }
    };

    private static String format(long seconds) {
        return seconds < 60 ? seconds + " 秒" : (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
    }

    private void cancel() {
        finish(onCancel);
    }

    private void finish(Runnable next) {
        dismiss();
        if (next != null) next.run();
    }

    private void cancelBiometric() {
        if (bioCancel != null) {
            try { bioCancel.cancel(); } catch (RuntimeException ignored) { }
            bioCancel = null;
        }
    }

    // ------------------------------------------------------------------ window plumbing
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets root = getRootWindowInsets();
            if (root != null) applyInsets(root);
        }
        maybeAutoPrompt();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        cancelBiometric();
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) maybeAutoPrompt();
    }

    /** Offers the biometric prompt once per screen, as soon as the window really has focus. */
    private void maybeAutoPrompt() {
        if (autoPrompted || mode != Mode.UNLOCK || !biometric || !attached || !hasWindowFocus()) return;
        autoPrompted = true;
        handler.postDelayed(this::promptBiometric, 250);
    }

    @SuppressWarnings("deprecation")
    private void applyInsets(WindowInsets i) {
        int l, t, r, b;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets x = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            l = x.left; t = x.top; r = x.right; b = x.bottom;
        } else {
            l = i.getSystemWindowInsetLeft(); t = i.getSystemWindowInsetTop(); r = i.getSystemWindowInsetRight(); b = i.getSystemWindowInsetBottom();
        }
        stage.setPadding(l, t, r, b);
    }

    private static TextView label(Context c, float size, int color, boolean medium) {
        TextView v = new TextView(c);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        if (medium) v.setTypeface(Ui.MEDIUM);
        return v;
    }

    // ------------------------------------------------------------------ layout
    /** Lays out header, dots, keypad and the cancel link from the measured space; no scrolling on any screen size. */
    private final class Stage extends ViewGroup {
        Stage(Context c) { super(c); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(0, w), resolveSize(0, h));
        }

        private void put(View v, float cx, float top, float w, float h) {
            v.measure(MeasureSpec.makeMeasureSpec(Math.round(w), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(h), MeasureSpec.EXACTLY));
            int left = Math.round(cx - w / 2f), y = Math.round(top);
            v.layout(left, y, left + Math.round(w), y + Math.round(h));
        }

        private float measureHeight(TextView v, float width) {
            v.measure(MeasureSpec.makeMeasureSpec(Math.round(width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            return v.getMeasuredHeight();
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            float W = r - l - getPaddingLeft() - getPaddingRight(), H = b - t - getPaddingTop() - getPaddingBottom();
            float ox = getPaddingLeft(), oy = getPaddingTop();
            boolean landscape = W > H * 1.2f;
            boolean showLink = cancellable;
            float textW = Math.min((landscape ? W * 0.5f : W) - 40 * u, 400 * u);
            float th = measureHeight(title, textW), mh = Math.max(measureHeight(message, textW), 22 * u);
            float lh = 0;
            if (showLink) {
                cancelLink.measure(MeasureSpec.makeMeasureSpec(Math.round(textW), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                lh = Math.max(cancelLink.getMeasuredHeight(), 44 * u);
            }
            float dotsH = 20 * u;
            if (landscape) layoutLandscape(ox, oy, W, H, textW, th, mh, lh, dotsH);
            else layoutPortrait(ox, oy, W, H, textW, th, mh, lh, dotsH);
        }

        private void layoutPortrait(float ox, float oy, float W, float H, float textW, float th, float mh, float lh, float dotsH) {
            float glyphS = 44 * u, g1 = 14 * u, g2 = 6 * u, g3 = 22 * u, g4 = 28 * u, gl = 8 * u, top = 12 * u, bottom = 12 * u;
            boolean showGlyph = true;
            float d = 0;
            for (int pass = 0; pass < 3; pass++) {
                float fixed = (showGlyph ? glyphS + g1 : 0) + th + g2 + mh + g3 + dotsH + g4 + (lh > 0 ? gl + lh : 0);
                d = Math.min(Math.min(76 * u, (H - top - bottom - fixed) / 4.6f), (W - 32 * u) / 3.68f);
                if (d < 50 * u && showGlyph) { showGlyph = false; continue; }
                if (d < 44 * u && pass < 2) { g3 *= 0.6f; g4 *= 0.6f; g1 *= 0.6f; continue; }
                break;
            }
            d = Math.max(d, 36 * u);
            float rowGap = 0.2f * d;
            float padH = 4 * d + 3 * rowGap;
            float total = (showGlyph ? glyphS + g1 : 0) + th + g2 + mh + g3 + dotsH + g4 + padH + (lh > 0 ? gl + lh : 0);
            float y = oy + Math.max(0, (H - total) * 0.42f);
            float cx = ox + W / 2f;
            glyph.setVisibility(showGlyph ? VISIBLE : GONE);
            if (showGlyph) { put(glyph, cx, y, glyphS, glyphS); y += glyphS + g1; }
            put(title, cx, y, textW, th); y += th + g2;
            put(message, cx, y, textW, mh); y += mh + g3;
            put(dots, cx, y, dots.width(), dotsH); y += dotsH + g4;
            pad(cx, y, d);
            y += padH;
            if (lh > 0) put(cancelLink, cx, y + gl, cancelLink.getMeasuredWidth(), lh);
            else cancelLink.layout(0, 0, 0, 0);
        }

        private void layoutLandscape(float ox, float oy, float W, float H, float textW, float th, float mh, float lh, float dotsH) {
            float half = W / 2f, g1 = 14 * u, g2 = 6 * u, g3 = 18 * u, gl = 10 * u, glyphS = 44 * u;
            boolean showGlyph = H > 300 * u;
            float headerH = (showGlyph ? glyphS + g1 : 0) + th + g2 + mh + g3 + dotsH + (lh > 0 ? gl + lh : 0);
            float y = oy + Math.max(8 * u, (H - headerH) / 2f), cx = ox + half / 2f;
            glyph.setVisibility(showGlyph ? VISIBLE : GONE);
            if (showGlyph) { put(glyph, cx, y, glyphS, glyphS); y += glyphS + g1; }
            put(title, cx, y, textW, th); y += th + g2;
            put(message, cx, y, textW, mh); y += mh + g3;
            put(dots, cx, y, dots.width(), dotsH); y += dotsH;
            if (lh > 0) put(cancelLink, cx, y + gl, cancelLink.getMeasuredWidth(), lh);
            else cancelLink.layout(0, 0, 0, 0);
            float d = Math.max(36 * u, Math.min(Math.min(76 * u, (H - 20 * u) / 4.6f), (half - 24 * u) / 3.68f));
            float padH = 4 * d + 3 * 0.2f * d;
            pad(ox + half + half / 2f, oy + Math.max(8 * u, (H - padH) / 2f), d);
        }

        /** Places the twelve keys; each key owns its whole grid cell so the tap area is larger than the drawn circle. */
        private void pad(float cx, float top, float d) {
            float colGap = 0.34f * d, rowGap = 0.2f * d, pitchX = d + colGap, pitchY = d + rowGap;
            float left = cx - (3 * d + 2 * colGap) / 2f;
            for (int i = 0; i < 12; i++) {
                int row = i / 3, col = i % 3;
                float x = left + col * pitchX - colGap / 2f, y = top + row * pitchY - rowGap / 2f;
                keys[i].diameter = d;
                keys[i].measure(MeasureSpec.makeMeasureSpec(Math.round(pitchX), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(pitchY), MeasureSpec.EXACTLY));
                keys[i].layout(Math.round(x), Math.round(y), Math.round(x) + Math.round(pitchX), Math.round(y) + Math.round(pitchY));
            }
        }
    }

    // ------------------------------------------------------------------ pieces
    /** Rounded lock icon at the top. */
    private static final class Glyph extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        Glyph(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 48f;
            c.save();
            c.scale(s, s);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.ACCENT_SOFT);
            c.drawCircle(24, 24, 24, p);
            p.setColor(Ui.ACCENT);
            r.set(15, 23, 33, 35);
            c.drawRoundRect(r, 3.2f, 3.2f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.6f);
            p.setStrokeCap(Paint.Cap.ROUND);
            r.set(18.5f, 13f, 29.5f, 26f);
            c.drawArc(r, 180, 180, false, p);
            c.drawLine(18.5f, 19.5f, 18.5f, 24f, p);
            c.drawLine(29.5f, 19.5f, 29.5f, 24f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.ACCENT_SOFT);
            c.drawCircle(24, 28.6f, 1.9f, p);
            c.restore();
        }
    }

    /** Six dots that fill with a small overshoot, turn red on a wrong PIN and shake. */
    private final class Dots extends View {
        private final float[] fill = new float[AppLock.PIN_LENGTH];
        private final ValueAnimator[] animators = new ValueAnimator[AppLock.PIN_LENGTH];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean error;

        Dots(Context c) {
            super(c);
            setContentDescription("已输入 0 位，共 6 位");
        }

        float width() { return AppLock.PIN_LENGTH * 13 * u + (AppLock.PIN_LENGTH - 1) * 20 * u; }

        void set(int n) {
            setContentDescription("已输入 " + n + " 位，共 6 位");
            for (int i = 0; i < fill.length; i++) {
                final int index = i;
                float target = i < n ? 1f : 0f;
                if (animators[i] != null) animators[i].cancel();
                if (fill[i] == target) continue;
                ValueAnimator a = ValueAnimator.ofFloat(fill[i], target);
                a.setDuration(target > fill[i] ? 160 : 90);
                if (target > fill[i]) a.setInterpolator(new OvershootInterpolator(2.4f));
                a.addUpdateListener(v -> { fill[index] = (float) v.getAnimatedValue(); invalidate(); });
                animators[i] = a;
                a.start();
            }
        }

        void setError(boolean e) { error = e; invalidate(); }

        void shake() {
            ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0, -14 * u, 14 * u, -10 * u, 10 * u, -5 * u, 5 * u, 0).setDuration(380).start();
        }

        @Override protected void onDraw(Canvas c) {
            float radius = 6.5f * u, pitch = 33 * u, cy = getHeight() / 2f;
            float left = (getWidth() - width()) / 2f + radius;
            for (int i = 0; i < fill.length; i++) {
                float cx = left + i * pitch;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f * u);
                p.setColor(error ? Ui.RED : fill[i] > 0.5f ? Ui.ACCENT : Ui.FAINT);
                c.drawCircle(cx, cy, radius - 0.9f * u, p);
                if (fill[i] > 0) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(error ? Ui.RED : Ui.ACCENT);
                    c.drawCircle(cx, cy, Math.max(0, radius * fill[i]), p);
                }
            }
        }
    }

    /** One key of the pad. The circle is drawn at {@link #diameter}; the view itself covers the whole grid cell. */
    private static final class Key extends View {
        final int kind;
        final String label;
        float diameter;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        Key(Context c, int kind, int digit) {
            super(c);
            this.kind = kind;
            this.label = kind == DIGIT ? String.valueOf(digit) : "";
            setClickable(true);
            setContentDescription(kind == DIGIT ? label : kind == DELETE ? "删除" : "使用指纹或面容解锁");
        }

        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.4f);
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float u = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, d = diameter > 0 ? diameter : Math.min(getWidth(), getHeight());
            boolean pressed = isPressed() && isEnabled();
            if (kind == DIGIT) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(pressed ? Ui.ACCENT_SOFT : Ui.SURFACE);
                c.drawCircle(cx, cy, d / 2f - u, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, u));
                p.setColor(pressed ? Ui.ACCENT : Ui.BORDER);
                c.drawCircle(cx, cy, d / 2f - u, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.INK);
                p.setTypeface(Ui.MEDIUM);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(d * 0.4f);
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, p);
                return;
            }
            if (pressed) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.ACCENT_SOFT);
                c.drawCircle(cx, cy, d / 2f - u, p);
            }
            float s = d * 0.40f / 24f;
            c.save();
            c.translate(cx - 12 * s, cy - 12 * s);
            c.scale(s, s);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(kind == BIO ? Ui.ACCENT : Ui.INK_SOFT);
            if (kind == DELETE) {
                path.reset();
                path.moveTo(2.5f, 12f); path.lineTo(8f, 5f); path.lineTo(21f, 5f); path.lineTo(21f, 19f); path.lineTo(8f, 19f); path.close();
                c.drawPath(path, p);
                c.drawLine(11.5f, 9.5f, 16.5f, 14.5f, p);
                c.drawLine(16.5f, 9.5f, 11.5f, 14.5f, p);
            } else {
                // fingerprint: concentric arcs with a gap, plus a centre stroke
                r.set(5.5f, 5f, 18.5f, 18f);
                c.drawArc(r, 200, 270, false, p);
                r.set(8.3f, 7.8f, 15.7f, 15.2f);
                c.drawArc(r, 190, 250, false, p);
                r.set(2.5f, 2.5f, 21.5f, 21.5f);
                c.drawArc(r, 215, 230, false, p);
                c.drawLine(12f, 11.5f, 12f, 20.5f, p);
                c.drawLine(8.6f, 13.5f, 8.6f, 18.5f, p);
                c.drawLine(15.4f, 13.5f, 15.4f, 17f, p);
            }
            c.restore();
        }
    }
}
