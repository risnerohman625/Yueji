package cn.yueji.balance;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A card that slides left to reveal action buttons. With a drag host, a long press starts reordering instead.
 * The buttons sit under the card and are only visible while the card is slid aside, so a card that is drawn
 * dimmed (hidden funds) can never show them through its own surface.
 */
final class SwipeRow extends FrameLayout {
    static final class Action {
        final String label,description;final int color;final Runnable run;final boolean dim;
        Action(String label,String description,int color,boolean dim,Runnable run){this.label=label;this.description=description;this.color=color;this.dim=dim;this.run=run;}
    }
    interface DragHost {void beginDrag(SwipeRow row,float rawY);void drag(float rawY);void endDrag(boolean commit);}

    private static SwipeRow open;
    final View content;final int reveal;
    DragHost host;Object key;
    private final LinearLayout bar;
    private final int slop;private final Runnable longPress;
    private float downX,downY,rawY,startTranslation;private boolean dragging,pressed,swallow;

    SwipeRow(MainActivity m,View content,Action... actions){
        super(m);this.content=content;slop=ViewConfiguration.get(m).getScaledTouchSlop();
        setClipChildren(false);
        int each=m.dp(70);reveal=each*actions.length;
        bar=new LinearLayout(m);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setVisibility(INVISIBLE);
        float r=m.dp(18);
        for(int i=0;i<actions.length;i++){
            Action a=actions[i];TextView v=m.text(a.label,13.5f,Color.WHITE,true);v.setGravity(Gravity.CENTER);
            GradientDrawable bg=new GradientDrawable();bg.setColor(a.color);
            if(i==actions.length-1)bg.setCornerRadii(new float[]{0,0,r,r,r,r,0,0});
            v.setBackground(bg);v.setContentDescription(a.description);if(a.dim)v.setAlpha(0.45f);
            v.setOnClickListener(x->{close();a.run.run();});
            bar.addView(v,new LinearLayout.LayoutParams(each,-1));
        }
        addView(bar,new FrameLayout.LayoutParams(reveal,-1,Gravity.END));
        addView(content,new FrameLayout.LayoutParams(-1,-2));
        longPress=()->{
            if(dragging||host==null)return;
            pressed=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);getParent().requestDisallowInterceptTouchEvent(true);host.beginDrag(this,rawY);
        };
    }
    /** Dims what is on a card without making the card itself see-through. */
    static void dim(View card,float alpha){
        if(card instanceof ViewGroup){ViewGroup g=(ViewGroup)card;for(int i=0;i<g.getChildCount();i++)g.getChildAt(i).setAlpha(alpha);}
    }
    void close(){
        if(open==this)open=null;
        content.animate().translationX(0).setDuration(120).withEndAction(()->{if(content.getTranslationX()==0)bar.setVisibility(INVISIBLE);}).start();
    }
    void openUp(){if(open!=null&&open!=this)open.close();open=this;bar.setVisibility(VISIBLE);content.animate().translationX(-reveal).setDuration(120).withEndAction(null).start();}
    @Override protected void onDetachedFromWindow(){if(open==this)open=null;removeCallbacks(longPress);super.onDetachedFromWindow();}
    private void cancelPress(){removeCallbacks(longPress);}

    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        rawY=e.getRawY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(open!=null&&open!=this)open.close();
                downX=e.getX();downY=e.getY();startTranslation=content.getTranslationX();dragging=false;pressed=false;swallow=false;
                if(content.getTranslationX()!=0&&e.getX()<getWidth()-reveal){close();swallow=true;return true;}
                if(host!=null){removeCallbacks(longPress);postDelayed(longPress,ViewConfiguration.getLongPressTimeout());}
                return false;
            case MotionEvent.ACTION_MOVE:{
                if(pressed)return true;
                float dx=e.getX()-downX,dy=e.getY()-downY;
                if(!dragging&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.3f){cancelPress();dragging=true;bar.setVisibility(VISIBLE);getParent().requestDisallowInterceptTouchEvent(true);return true;}
                if(Math.abs(dx)>slop||Math.abs(dy)>slop)cancelPress();
                return false;
            }
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                cancelPress();
                if(pressed){pressed=false;host.endDrag(e.getActionMasked()==MotionEvent.ACTION_UP);return true;}
                return false;
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        rawY=e.getRawY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                if(pressed)host.drag(e.getRawY());
                else if(dragging)content.setTranslationX(Math.max(-reveal,Math.min(0,startTranslation+e.getX()-downX)));
                return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                if(pressed){pressed=false;host.endDrag(e.getActionMasked()==MotionEvent.ACTION_UP);}
                else if(dragging){if(content.getTranslationX()<-reveal*0.42f)openUp();else close();}
                dragging=false;swallow=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return true;
    }
}
