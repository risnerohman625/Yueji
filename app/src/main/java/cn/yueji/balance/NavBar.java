package cn.yueji.balance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * Bottom bar with four destinations. The first three can be rearranged by long-pressing an item and dragging it
 * sideways; settings always stays on the right. Only the visible order changes — page state is untouched.
 */
final class NavBar extends LinearLayout {
    interface Moved { void moved(int[] order); }
    interface Selected { void selected(int tab); }
    static final String[] LABELS = {"资产", "收支", "基金", "设置"};

    private final float d;
    private final int barHeight, movable;
    private final NavItem[] items;
    private int[] order;
    private int active, downSlot, target;
    private float downX;
    private final float slop;
    private boolean dragging, pressed;
    private Selected onSelected = t -> {};
    private Moved onMoved = o -> {};
    private final Runnable longPress = this::beginLift;
    private void beginLift() {
        if (dragging || !pressed) return;
        dragging = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        getParent().requestDisallowInterceptTouchEvent(true);
        items[downSlot].lift(true);
    }

    NavBar(Context context, int[] order, int active) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        this.order = order.clone();
        this.active = active;
        barHeight = Math.round(62 * d);
        movable = Math.max(1, order.length - 1);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(HORIZONTAL);
        setBackgroundColor(Ui.SURFACE);
        setElevation(10 * d);
        setClipChildren(false);
        items = new NavItem[order.length];
        for (int i = 0; i < order.length; i++) {
            NavItem item = new NavItem(context, i);
            item.setOnClickListener(v -> onSelected.selected(this.order[item.slot]));
            items[i] = item;
            addView(item, new LinearLayout.LayoutParams(0, barHeight, 1));
        }
        refresh();
    }

    void onSelected(Selected s) { onSelected = s; }
    void onMoved(Moved m) { onMoved = m; }

    private void refresh() {
        for (NavItem item : items) {
            item.setContentDescription("导航" + LABELS[order[item.slot]]);
            item.invalidate();
        }
    }

    private float slotWidth() { return getWidth() / (float) items.length; }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downSlot = Math.max(0, Math.min(items.length - 1, (int) (downX / slotWidth())));
                target = downSlot;
                dragging = false;
                pressed = downSlot < movable;
                if (pressed) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float limit = (movable - 1) * slotWidth();
                    float from = downSlot * slotWidth();
                    float shift = Math.max(-from, Math.min(limit - from, e.getX() - downX));
                    items[downSlot].setTranslationX(shift);
                    retarget(from + shift + slotWidth() / 2f);
                    return true;
                }
                if (pressed && Math.abs(e.getX() - downX) > slop) { pressed = false; removeCallbacks(longPress); }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (dragging) { finishDrag(e.getActionMasked() == MotionEvent.ACTION_UP); return true; }
                pressed = false;
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    /** Neighbours slide aside as the lifted item's centre crosses them. */
    private void retarget(float center) {
        float w = slotWidth();
        int next = Math.max(0, Math.min(movable - 1, (int) (center / w)));
        if (next == target) return;
        target = next;
        for (int i = 0; i < movable; i++) {
            if (i == downSlot) continue;
            float shift = 0;
            if (downSlot < target && i > downSlot && i <= target) shift = -w;
            else if (downSlot > target && i >= target && i < downSlot) shift = w;
            items[i].animate().translationX(shift).setDuration(110).start();
        }
    }

    private void finishDrag(boolean commit) {
        dragging = false;
        pressed = false;
        float w = slotWidth();
        NavItem lifted = items[downSlot];
        float liftedOffset = lifted.getTranslationX();
        lifted.lift(false);
        boolean changed = commit && target != downSlot;
        int from = downSlot, to = target;
        for (NavItem item : items) { item.animate().cancel(); item.setTranslationX(0); }
        if (changed) {
            int[] next = order.clone();
            int moved = next[from];
            if (from < to) System.arraycopy(next, from + 1, next, from, to - from);
            else System.arraycopy(next, to, next, to + 1, from - to);
            next[to] = moved;
            order = next;
            // The view now showing the moved tab continues from where the finger left it.
            items[to].setTranslationX(liftedOffset + (from - to) * w);
            items[to].animate().translationX(0).setDuration(130).start();
            onMoved.moved(order.clone());
        } else {
            lifted.setTranslationX(liftedOffset);
            lifted.animate().translationX(0).setDuration(130).start();
        }
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(longPress);
        super.onDetachedFromWindow();
    }

    void setActive(int tab) { active = tab; refresh(); }

    /** One destination: a soft pill behind the active glyph, the glyph, and its label. */
    private final class NavItem extends View {
        final int slot;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        private float scale = 1f;

        NavItem(Context context, int slot) {
            super(context);
            this.slot = slot;
            setClickable(true);
            setFocusable(true);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void lift(boolean on) {
            scale = on ? 1.14f : 1f;
            setAlpha(on ? 0.9f : 1f);
            setTranslationZ(on ? 6 * d : 0);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int tab = order[slot];
            boolean on = tab == active;
            float w = getWidth(), h = getHeight(), cx = w / 2f, iconY = h / 2f - 8 * d;
            canvas.save();
            canvas.scale(scale, scale, cx, h / 2f);
            if (on) {
                rect.set(cx - 24 * d, iconY - 14.5f * d, cx + 24 * d, iconY + 14.5f * d);
                p.setColor(Ui.PURPLE_SOFT);
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(rect, 15 * d, 15 * d, p);
            }
            canvas.save();
            canvas.translate(cx - 11 * d, iconY - 11 * d);
            canvas.scale(22 * d / 24f, 22 * d / 24f);
            p.setColor(on ? Ui.PURPLE : Ui.FAINT);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            glyph(canvas, tab);
            canvas.restore();
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(on ? Ui.MEDIUM : regular);
            p.setTextSize(10.5f * d);
            p.setColor(on ? Ui.PURPLE_DEEP : Ui.MUTED);
            canvas.drawText(LABELS[tab], cx, h / 2f + 16 * d, p);
            canvas.restore();
        }

        private void glyph(Canvas canvas, int tab) {
            if (tab == MainActivity.TAB_ACCOUNTS) {
                rect.set(3f, 6f, 21f, 19.5f);
                canvas.drawRoundRect(rect, 3.2f, 3.2f, p);
                canvas.drawLine(3f, 10.2f, 21f, 10.2f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(17f, 14.8f, 1.4f, p);
            } else if (tab == MainActivity.TAB_FLOW) {
                path.reset();
                path.moveTo(4f, 17.5f); path.lineTo(9.2f, 11.4f); path.lineTo(13.4f, 14.6f); path.lineTo(20f, 6.6f);
                canvas.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(20f, 6.6f, 1.5f, p);
                canvas.drawCircle(4f, 17.5f, 1.5f, p);
            } else if (tab == MainActivity.TAB_FUNDS) {
                p.setStyle(Paint.Style.FILL);
                rect.set(4f, 12f, 7.6f, 20f); canvas.drawRoundRect(rect, 1.5f, 1.5f, p);
                rect.set(10.2f, 7.2f, 13.8f, 20f); canvas.drawRoundRect(rect, 1.5f, 1.5f, p);
                rect.set(16.4f, 10f, 20f, 20f); canvas.drawRoundRect(rect, 1.5f, 1.5f, p);
            } else {
                canvas.drawCircle(12f, 12f, 3.1f, p);
                canvas.drawCircle(12f, 12f, 7f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4 + Math.PI / 8;
                    canvas.drawLine((float) (12 + 7.2 * Math.cos(a)), (float) (12 + 7.2 * Math.sin(a)),
                            (float) (12 + 9.8 * Math.cos(a)), (float) (12 + 9.8 * Math.sin(a)), p);
                }
            }
        }
    }
}
