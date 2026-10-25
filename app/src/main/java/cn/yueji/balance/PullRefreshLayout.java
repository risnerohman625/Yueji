package cn.yueji.balance;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;

/** Pull the scroll view down while it is at the top; a spinner appears and releasing past the threshold refreshes. */
final class PullRefreshLayout extends FrameLayout {
    private final ScrollView scroll;private final ProgressBar spinner;private final Runnable onRefresh;
    private final float threshold,hold,maxPull;private final int slop,spinnerSize;
    private float downX,downY,pullStart,offset;private boolean pulling,refreshing;

    PullRefreshLayout(Context c,ScrollView scroll,Runnable onRefresh){
        super(c);this.scroll=scroll;this.onRefresh=onRefresh;
        float d=c.getResources().getDisplayMetrics().density;threshold=64*d;hold=56*d;maxPull=120*d;spinnerSize=Math.round(30*d);slop=ViewConfiguration.get(c).getScaledTouchSlop();
        spinner=new ProgressBar(c);spinner.setIndeterminate(true);spinner.setContentDescription("正在刷新");spinner.setAlpha(0f);
        addView(spinner,new FrameLayout.LayoutParams(spinnerSize,spinnerSize,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        spinner.setTranslationY(-spinnerSize-8*d);
    }
    private void apply(float y){
        offset=y;scroll.setTranslationY(y);
        float progress=Math.min(1f,y/threshold);spinner.setAlpha(refreshing?1f:progress);
        spinner.setTranslationY(Math.max(-spinnerSize,y-spinnerSize-6*getResources().getDisplayMetrics().density));
    }
    private void settle(float target,Runnable end){
        android.animation.ValueAnimator a=android.animation.ValueAnimator.ofFloat(offset,target);a.setDuration(160);
        a.addUpdateListener(v->apply((float)v.getAnimatedValue()));
        a.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator x){if(end!=null)end.run();}});a.start();
    }
    /** Shows or hides the spinner without calling the refresh action (used when a refresh starts by itself). */
    void setRefreshing(boolean value){
        if(value==refreshing)return;refreshing=value;
        if(value){spinner.setAlpha(1f);settle(hold,null);}else settle(0f,()->spinner.setAlpha(0f));
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();pulling=false;break;
            case MotionEvent.ACTION_MOVE:{
                float dy=e.getY()-downY,dx=e.getX()-downX;
                if(!pulling&&!refreshing&&dy>slop&&dy>Math.abs(dx)*1.5f&&!scroll.canScrollVertically(-1)){pulling=true;pullStart=e.getY();return true;}
                break;
            }
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                if(pulling){apply(Math.max(0,Math.min(maxPull,(e.getY()-pullStart)*0.5f)));return true;}
                return false;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                if(!pulling)return false;
                pulling=false;
                if(e.getActionMasked()==MotionEvent.ACTION_UP&&offset>=threshold){refreshing=true;spinner.setAlpha(1f);settle(hold,null);onRefresh.run();}
                else settle(0f,()->{if(!refreshing)spinner.setAlpha(0f);});
                return true;
        }
        return pulling;
    }
}
