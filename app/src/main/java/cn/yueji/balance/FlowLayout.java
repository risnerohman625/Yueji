package cn.yueji.balance;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * Wrapping container for chips and tiles. With {@link #columns(int)} it is an equal-width grid (the cell width is
 * derived from its own width, each child keeps its own height, a row is as high as its tallest child); with 0 columns
 * children keep their own width and wrap to the next line. Children must use {@link ViewGroup.MarginLayoutParams}.
 *
 * <p>Optionally the first N children can be re-ordered: long-press one, drag it, the others slide into the free slot;
 * on release {@link Reorder#moved} reports the old and new index once. The layout never re-orders its own children —
 * the caller saves the new order and rebuilds.
 */
final class FlowLayout extends ViewGroup {
    interface Reorder { void moved(int from, int to); }

    private int columns, hGap, vGap, draggable;
    private Reorder reorder;
    private int[] lefts = new int[0], tops = new int[0];
    private final float density;
    private final int slop;

    // drag state
    private final Runnable longPress = this::beginDrag;
    private float downX, downY, lastRawX, lastRawY;
    private int downIndex = -1, from, to;
    private boolean dragging;
    private View lifted;
    private int[] slotL, slotT;
    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            if (!dragging) return;
            ScrollView s = scroller();
            if (s != null) {
                int[] at = new int[2];
                s.getLocationOnScreen(at);
                int edge = dp(64), step = dp(9), before = s.getScrollY();
                if (lastRawY < at[1] + edge) s.scrollBy(0, -step);
                else if (lastRawY > at[1] + s.getHeight() - edge) s.scrollBy(0, step);
                if (s.getScrollY() != before) moveLifted();
            }
            postOnAnimation(this);
        }
    };

    FlowLayout(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** Equal-width columns; 0 switches back to free wrapping. */
    FlowLayout columns(int n) { columns = Math.max(0, n); requestLayout(); return this; }
    /** Horizontal and vertical gaps in px. */
    FlowLayout gaps(int horizontal, int vertical) { hGap = horizontal; vGap = vertical; requestLayout(); return this; }
    /** The first {@code count} children can be dragged to new positions. */
    FlowLayout draggable(int count, Reorder listener) { draggable = Math.max(0, count); reorder = listener; return this; }

    private int dp(float v) { return Math.round(v * density); }

    // ------------------------------------------------------------------ layout
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int mode = MeasureSpec.getMode(widthSpec);
        int avail = mode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE / 4 : Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        int n = getChildCount();
        if (lefts.length < n) { lefts = new int[n]; tops = new int[n]; }
        int cell = columns > 0 ? Math.max(0, (avail - (columns - 1) * hGap) / columns) : 0;
        int x = 0, y = 0, rowHeight = 0, widest = 0, col = 0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) { lefts[i] = x; tops[i] = y; continue; }
            MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
            int mh = lp.leftMargin + lp.rightMargin, mv = lp.topMargin + lp.bottomMargin;
            int cw;
            if (columns > 0) cw = MeasureSpec.makeMeasureSpec(Math.max(0, cell - mh), MeasureSpec.EXACTLY);
            else cw = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(avail, mode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST), mh, lp.width);
            int ch = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), mv, lp.height);
            v.measure(cw, ch);
            int w = v.getMeasuredWidth() + mh, h = v.getMeasuredHeight() + mv;
            boolean wrap = columns > 0 ? col == columns : x > 0 && x + w > avail;
            if (wrap) { y += rowHeight + vGap; x = 0; rowHeight = 0; col = 0; }
            lefts[i] = x + lp.leftMargin;
            tops[i] = y + lp.topMargin;
            x += w + hGap;
            col++;
            rowHeight = Math.max(rowHeight, h);
            widest = Math.max(widest, x - hGap);
            any = true;
        }
        int contentW = columns > 0 && mode != MeasureSpec.UNSPECIFIED ? avail : widest;
        int contentH = any ? y + rowHeight : 0;
        setMeasuredDimension(resolveSize(contentW + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(contentH + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int left = getPaddingLeft() + lefts[i], top = getPaddingTop() + tops[i];
            v.layout(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
        }
    }

    @Override protected boolean checkLayoutParams(LayoutParams p) { return p instanceof MarginLayoutParams; }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new MarginLayoutParams(getContext(), attrs); }
    @Override protected LayoutParams generateLayoutParams(LayoutParams p) { return new MarginLayoutParams(p); }

    // ------------------------------------------------------------------ drag to re-order
    private int hit(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() != GONE && x >= v.getLeft() && x < v.getRight() && y >= v.getTop() && y < v.getBottom()) return i;
        }
        return -1;
    }

    private ScrollView scroller() {
        android.view.ViewParent p = getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        return (ScrollView) p;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (draggable == 0 && !dragging) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                lastRawX = e.getRawX();
                lastRawY = e.getRawY();
                downIndex = hit(downX, downY);
                if (downIndex < 0 || downIndex >= draggable) downIndex = -1;
                else { removeCallbacks(longPress); postDelayed(longPress, ViewConfiguration.getLongPressTimeout()); }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging) { handle(e); return true; }
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) removeCallbacks(longPress);
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (dragging) { handle(e); return true; }
                return false;
            default:
                return dragging;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return false;
        handle(e);
        return true;
    }

    /** Called from both touch entry points: the event that makes us intercept is consumed there, never reaching onTouchEvent. */
    private void handle(MotionEvent e) {
        lastRawX = e.getRawX();
        lastRawY = e.getRawY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: moveLifted(); break;
            case MotionEvent.ACTION_UP: finish(true); break;
            case MotionEvent.ACTION_CANCEL: finish(false); break;
            default: break;
        }
    }

    private void beginDrag() {
        if (dragging || downIndex < 0 || downIndex >= getChildCount()) return;
        dragging = true;
        from = to = downIndex;
        lifted = getChildAt(from);
        int n = getChildCount();
        slotL = new int[n];
        slotT = new int[n];
        for (int i = 0; i < n; i++) { slotL[i] = getChildAt(i).getLeft(); slotT[i] = getChildAt(i).getTop(); }
        lifted.setTranslationZ(dp(8));
        lifted.setScaleX(1.06f);
        lifted.setScaleY(1.06f);
        lifted.setAlpha(0.94f);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        postOnAnimation(autoScroll);
    }

    /** The lifted child follows the finger (measured in this layout's own coordinates, so auto-scrolling keeps it under the finger). */
    private void moveLifted() {
        if (lifted == null) return;
        int[] at = new int[2];
        getLocationOnScreen(at);
        float dx = (lastRawX - at[0]) - downX, dy = (lastRawY - at[1]) - downY;
        lifted.setTranslationX(dx);
        lifted.setTranslationY(dy);
        float cx = slotL[from] + lifted.getWidth() / 2f + dx, cy = slotT[from] + lifted.getHeight() / 2f + dy;
        int best = to;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < Math.min(draggable, getChildCount()); i++) {
            float sx = slotL[i] + getChildAt(i).getWidth() / 2f, sy = slotT[i] + getChildAt(i).getHeight() / 2f;
            float d = (cx - sx) * (cx - sx) + (cy - sy) * (cy - sy);
            if (d < bestDistance) { bestDistance = d; best = i; }
        }
        if (best != to) { to = best; slideOthers(); }
    }

    private void slideOthers() {
        for (int i = 0; i < Math.min(draggable, getChildCount()); i++) {
            if (i == from) continue;
            int slot = i;
            if (from < to && i > from && i <= to) slot = i - 1;
            else if (from > to && i >= to && i < from) slot = i + 1;
            getChildAt(i).animate().translationX(slotL[slot] - slotL[i]).translationY(slotT[slot] - slotT[i]).setDuration(140).start();
        }
    }

    private void finish(boolean commit) {
        removeCallbacks(longPress);
        removeCallbacks(autoScroll);
        boolean wasDragging = dragging;
        dragging = false;
        if (!wasDragging) return;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (lifted != null) {
            lifted.setTranslationZ(0);
            lifted.setScaleX(1f);
            lifted.setScaleY(1f);
            lifted.setAlpha(1f);
        }
        boolean changed = commit && to != from;
        int a = from, b = to;
        lifted = null;
        downIndex = -1;
        // A committed move leaves the children where they were slid to; the caller saves the order and rebuilds the layout.
        if (changed && reorder != null) { reorder.moved(a, b); return; }
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).animate().translationX(0).translationY(0).setDuration(140).start();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(longPress);
        removeCallbacks(autoScroll);
        super.onDetachedFromWindow();
    }
}
