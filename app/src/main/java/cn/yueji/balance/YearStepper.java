package cn.yueji.balance;

import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import java.util.function.IntConsumer;

/** An anchored three-row wheel, exactly as wide as the original year button. */
final class YearStepper extends LinearLayout {
    private final MainActivity m;
    private final int minimum,maximum;
    private final IntConsumer changed;
    private final TextView number;
    private int year;
    PopupWindow popup;
    NumberPicker wheel;
    private boolean forwarded,moved;
    private float downY;
    private boolean touching;
    private int scrollState;
    static final long IDLE_MILLIS=1000;
    private final Runnable dismissIdle=()->{if(!touching&&scrollState==NumberPicker.OnScrollListener.SCROLL_STATE_IDLE&&popup!=null)popup.dismiss();};
    private void idleLater(){removeCallbacks(dismissIdle);if(!touching&&scrollState==NumberPicker.OnScrollListener.SCROLL_STATE_IDLE)postDelayed(dismissIdle,IDLE_MILLIS);}

    YearStepper(MainActivity m,int year,int minimum,int maximum,IntConsumer changed){
        super(m);this.m=m;this.minimum=minimum;this.maximum=maximum;this.year=Math.max(minimum,Math.min(maximum,year));this.changed=changed;
        setOrientation(HORIZONTAL);setGravity(Gravity.CENTER);setPadding(m.dp(10),m.dp(4),m.dp(8),m.dp(4));
        GradientDrawable buttonGlass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{
            (Ui.ACCENT_TINT&0x00ffffff)|0xE6000000,(Ui.ACCENT_SOFT&0x00ffffff)|0xB3000000});
        buttonGlass.setCornerRadius(m.dp(19));
        buttonGlass.setStroke(Math.max(1,m.dp(.5f)),(Ui.ACCENT&0x00ffffff)|(Ui.dark?0x38000000:0x20000000));
        setBackground(buttonGlass);setClipToOutline(true);setMinimumHeight(m.dp(38));
        setContentDescription("选择年份");setFocusable(true);setClickable(true);
        number=m.text(this.year+"",14,Ui.ACCENT,true);number.setTypeface(Ui.BOLD);number.setGravity(Gravity.CENTER);
        addView(number,new LayoutParams(m.dp(44),m.dp(30)));
        TextView arrows=m.text("▴\n▾",12,Ui.ACCENT,true);arrows.setGravity(Gravity.CENTER);arrows.setLineSpacing(0,.8f);
        addView(arrows,new LayoutParams(m.dp(16),m.dp(30)));
        updateState();
    }
    int year(){return year;}
    private void step(int direction){
        select(year+direction);
    }
    void select(int value){
        value=Math.max(minimum,Math.min(maximum,value));if(year==value)return;
        year=value;number.setText(year+"");updateState();performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);changed.accept(year);idleLater();
    }
    void open(){
        if(!isAttachedToWindow())return;
        if(getWidth()==0||getHeight()==0){post(this::open);return;}
        if(popup!=null&&popup.isShowing())return;
        FrameLayout panel=new FrameLayout(m){
            @Override public boolean dispatchTouchEvent(MotionEvent event){
                int action=event.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){touching=true;removeCallbacks(dismissIdle);YearStepper.this.removeCallbacks(dismissIdle);}
                boolean result=super.dispatchTouchEvent(event);
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;idleLater();}
                return result;
            }
        };
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{
            (Ui.SURFACE&0x00ffffff)|0xF2000000,(Ui.ACCENT_TINT&0x00ffffff)|0xE6000000});
        glass.setCornerRadius(m.dp(22));
        glass.setStroke(Math.max(1,m.dp(.5f)),Ui.dark?0x557F778D:0xD9FFFFFF);
        panel.setBackground(glass);panel.setClipToOutline(true);panel.setElevation(m.dp(6));panel.setContentDescription("年份滚轮");
        View selected=new View(m);selected.setBackground(Ui.roundStroke((Ui.ACCENT&0x00ffffff)|0x18000000,(Ui.ACCENT&0x00ffffff)|0x18000000,m.dp(14),m.dp(.5f)));
        FrameLayout.LayoutParams selection=new FrameLayout.LayoutParams(-1,m.dp(35),Gravity.CENTER);selection.leftMargin=selection.rightMargin=m.dp(6);panel.addView(selected,selection);
        wheel=new NumberPicker(m);wheel.setMinValue(minimum);wheel.setMaxValue(maximum);wheel.setWrapSelectorWheel(false);wheel.setValue(year);
        wheel.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);wheel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);wheel.setFocusableInTouchMode(true);wheel.requestFocus();wheel.setContentDescription("年份滚轮");
        if(android.os.Build.VERSION.SDK_INT>=29){wheel.setTextColor(Ui.ACCENT_DEEP);wheel.setTextSize(m.dp(17));wheel.setSelectionDividerHeight(0);}
        wheel.setOnValueChangedListener((p,old,value)->select(value));panel.addView(wheel,new FrameLayout.LayoutParams(-1,-1));
        scrollState=NumberPicker.OnScrollListener.SCROLL_STATE_IDLE;
        wheel.setOnScrollListener((picker,state)->{scrollState=state;removeCallbacks(dismissIdle);if(state==NumberPicker.OnScrollListener.SCROLL_STATE_IDLE)idleLater();});
        for(boolean top:new boolean[]{true,false}){
            View fade=new View(m);GradientDrawable gradient=new GradientDrawable(top?GradientDrawable.Orientation.TOP_BOTTOM:GradientDrawable.Orientation.BOTTOM_TOP,new int[]{(Ui.SURFACE&0x00ffffff)|0xB0000000,Ui.SURFACE&0x00ffffff});
            fade.setBackground(gradient);fade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            panel.addView(fade,new FrameLayout.LayoutParams(-1,m.dp(27),top?Gravity.TOP:Gravity.BOTTOM));
        }
        int height=m.dp(114);popup=new PopupWindow(panel,Math.max(m.dp(78),getWidth()),height,false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(m.dp(6));
        popup.setOnDismissListener(()->{removeCallbacks(dismissIdle);number.setVisibility(VISIBLE);getChildAt(1).setVisibility(VISIBLE);setAlpha(1f);touching=false;});
        int[] position=new int[2];getLocationOnScreen(position);
        popup.showAtLocation(getRootView(),Gravity.TOP|Gravity.LEFT,position[0],position[1]+getHeight()/2-height/2);
        // The translucent selection must not show a second copy of the anchor's year underneath.
        number.setVisibility(INVISIBLE);getChildAt(1).setVisibility(INVISIBLE);setAlpha(0f);idleLater();
    }
    private void forward(MotionEvent event){
        if(wheel==null)return;
        int[] position=new int[2];wheel.getLocationOnScreen(position);
        MotionEvent copy=MotionEvent.obtain(event);copy.setLocation(event.getRawX()-position[0],event.getRawY()-position[1]);wheel.dispatchTouchEvent(copy);copy.recycle();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downY=event.getY();moved=false;forwarded=false;touching=true;removeCallbacks(dismissIdle);open();
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                post(()->{if(popup!=null&&popup.isShowing()){long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,wheel.getWidth()/2f,wheel.getHeight()/2f,0);wheel.dispatchTouchEvent(down);down.recycle();forwarded=true;}});
                return true;
            case MotionEvent.ACTION_MOVE:
                moved|=Math.abs(event.getY()-downY)>ViewConfiguration.get(m).getScaledTouchSlop();if(forwarded)forward(event);return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                if(forwarded)forward(event);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
                touching=false;if(!moved&&event.getActionMasked()==MotionEvent.ACTION_UP)performClick();idleLater();return true;
            default:return true;
        }
    }
    private void updateState(){if(android.os.Build.VERSION.SDK_INT>=30)setStateDescription(year+"年");}
    @Override public boolean performClick(){super.performClick();open();return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(dismissIdle);if(popup!=null)popup.dismiss();popup=null;super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setScrollable(true);
        info.setClassName(NumberPicker.class.getName());
        if(year<maximum)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        if(year>minimum)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){step(1);return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){step(-1);return true;}
        return super.performAccessibilityAction(action,args);
    }
}
