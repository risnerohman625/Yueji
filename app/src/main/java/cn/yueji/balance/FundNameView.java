package cn.yueji.balance;

import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Fit one complete name; exceptionally long names remain readable by dragging the title only. */
final class FundNameView extends HorizontalScrollView {
    final TextView label;
    boolean scrolls;
    FundNameView(MainActivity m,String name,Runnable click){
        super(m);
        setFillViewport(false);setHorizontalScrollBarEnabled(false);setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength(m.dp(16));setContentDescription("产品全名："+name);
        label=m.text(name,15,Ui.INK,true);label.setMaxLines(1);label.setHorizontallyScrolling(true);
        label.setOnClickListener(v->click.run());addView(label,new LayoutParams(-2,m.dp(30)));
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
        if(available>0){
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
            float full=label.getPaint().measureText(label.getText().toString());
            float fitted=Math.min(15,15*available/Math.max(1,full));
            scrolls=fitted<11;
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP,scrolls?15:Math.max(11,fitted));
            label.getLayoutParams().width=scrolls?LayoutParams.WRAP_CONTENT:available;
            if(!scrolls&&getScrollX()!=0)scrollTo(0,0);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(scrolls&&getParent()!=null){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);
        }
        return super.dispatchTouchEvent(event);
    }
}
