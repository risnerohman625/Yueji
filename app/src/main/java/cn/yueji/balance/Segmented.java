package cn.yueji.balance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Two-to-four option switch with real contrast: the selected segment is a solid deep purple with white text,
 * the rest sit on the pale track. Used for chart switches, periods and sort order. Tap targets are 40 dp high.
 */
final class Segmented extends View {
    private final String[] labels;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;
    private int selected;
    private Runnable onSelect = () -> {};

    Segmented(Context c, String... labels) {
        super(c);
        this.labels = labels;
        dp = c.getResources().getDisplayMetrics().density;
        paint.setTypeface(Ui.MEDIUM);
        paint.setTextSize(12.5f * dp);
        paint.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setFocusable(true);
        setContentDescription("选项卡" + String.join("/", labels));
    }

    void setSelected(int index) { selected = index; invalidate(); }
    int selected() { return selected; }
    int count() { return labels.length; }
    void onSelect(Runnable r) { onSelect = r; }

    @Override protected void onMeasure(int width, int height) {
        super.onMeasure(width, height);
        setMeasuredDimension(getMeasuredWidth(), Math.round(40 * dp));
    }

    private float slot() { return (getWidth() - 2 * 3 * dp) / labels.length; }
    private float left(int i) { return 3 * dp + slot() * i; }

    @Override protected void onDraw(Canvas c) {
        float r = 11 * dp;
        rect.set(0, 0, getWidth(), getHeight());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.PURPLE_SOFT);
        c.drawRoundRect(rect, r, r, paint);
        rect.set(left(selected) + 2 * dp, 3 * dp, left(selected) + slot() - 2 * dp, getHeight() - 3 * dp);
        paint.setColor(Ui.dark?Ui.HERO_A:Ui.PURPLE_DEEP);
        c.drawRoundRect(rect, r - 2 * dp, r - 2 * dp, paint);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < labels.length; i++) {
            paint.setColor(i == selected ? Color.WHITE : Ui.INK_SOFT);
            c.drawText(labels[i], left(i) + slot() / 2f, baseline, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            setPressed(true);
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            setPressed(false);
            float slot = slot();
            int index = Math.max(0, Math.min(labels.length - 1, (int) ((x - 3 * dp) / slot)));
            if (index != selected) {
                selected = index;
                invalidate();
                performClick();
                onSelect.run();
            } else performClick();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_CANCEL) { setPressed(false); return true; }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.RadioGroup");
        info.setContentDescription("选项卡" + labels[selected]);
    }
}
