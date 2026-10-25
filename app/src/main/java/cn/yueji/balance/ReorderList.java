package cn.yueji.balance;

import android.content.Context;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list whose SwipeRow children can be reordered by long-press dragging. Rows keep their place in the
 * child list while dragging (only translated); the new order is reported once on drop so the caller can save and redraw.
 */
final class ReorderList extends LinearLayout implements SwipeRow.DragHost {
    interface Listener {void moved(List<Object> order);}
    Listener listener;
    private ScrollView scroller;private SwipeRow dragged;
    private int from,to,startScroll;private float startRaw,lastRaw;private int[] tops,heights,slot;
    private final Runnable autoScroll=new Runnable(){@Override public void run(){
        if(dragged==null||scroller==null)return;
        int[] at=new int[2];scroller.getLocationOnScreen(at);int edge=Math.round(56*getResources().getDisplayMetrics().density),step=Math.round(10*getResources().getDisplayMetrics().density);
        int before=scroller.getScrollY();
        if(lastRaw<at[1]+edge)scroller.scrollBy(0,-step);else if(lastRaw>at[1]+scroller.getHeight()-edge)scroller.scrollBy(0,step);else return;
        if(scroller.getScrollY()!=before){drag(lastRaw);postOnAnimation(this);}
    }};

    ReorderList(Context c){super(c);setOrientation(VERTICAL);}

    @Override public void beginDrag(SwipeRow row,float rawY){
        ViewParent p=getParent();while(p!=null&&!(p instanceof ScrollView))p=p.getParent();scroller=(ScrollView)p;
        dragged=row;from=to=indexOfChild(row);startRaw=lastRaw=rawY;startScroll=scroller==null?0:scroller.getScrollY();
        int n=getChildCount();tops=new int[n];heights=new int[n];slot=new int[n];
        for(int i=0;i<n;i++){View v=getChildAt(i);tops[i]=v.getTop();heights[i]=v.getHeight();LayoutParams lp=(LayoutParams)v.getLayoutParams();slot[i]=v.getHeight()+lp.topMargin+lp.bottomMargin;}
        row.setTranslationZ(Math.round(8*getResources().getDisplayMetrics().density));row.setAlpha(0.96f);
    }
    @Override public void drag(float rawY){
        if(dragged==null)return;
        lastRaw=rawY;float dy=rawY-startRaw+(scroller==null?0:scroller.getScrollY()-startScroll);
        dragged.setTranslationY(dy);
        float center=tops[from]+heights[from]/2f+dy;int target=from;
        for(int i=0;i<tops.length;i++)if(center>=tops[i]&&center<tops[i]+slot[i]){target=i;break;}
        if(center<tops[0])target=0;else if(center>=tops[tops.length-1]+slot[slot.length-1])target=tops.length-1;
        if(target!=to){
            to=target;
            for(int i=0;i<getChildCount();i++){
                if(i==from)continue;float shift=0;
                if(from<to&&i>from&&i<=to)shift=-slot[from];else if(from>to&&i>=to&&i<from)shift=slot[from];
                getChildAt(i).animate().translationY(shift).setDuration(120).start();
            }
        }
        removeCallbacks(autoScroll);postOnAnimation(autoScroll);
    }
    @Override public void endDrag(boolean commit){
        removeCallbacks(autoScroll);
        if(dragged==null)return;
        SwipeRow row=dragged;dragged=null;
        List<Object> order=new ArrayList<>();for(int i=0;i<getChildCount();i++)order.add(((SwipeRow)getChildAt(i)).key);
        if(commit&&from!=to){Object moved=order.remove(from);order.add(to,moved);}
        row.setTranslationZ(0);row.setAlpha(1f);
        if(commit&&from!=to&&listener!=null){listener.moved(order);return;}
        for(int i=0;i<getChildCount();i++)getChildAt(i).animate().translationY(0).setDuration(120).start();
    }
}
