package cn.yueji.balance;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.time.LocalDate;
import java.util.*;

/** Signed cumulative profit, with a neutral cursor and a tooltip next to the selected point. */
public final class ProfitChart extends View {
    final List<Map.Entry<LocalDate,Long>> points;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path=new Path(),fill=new Path();
    final ChartTooltip tooltip=new ChartTooltip();
    int selected=-1;
    private ChartScale cachedScale;
    private float cachedLeft;
    private final Runnable hideSelection=()->{selected=-1;invalidate();};
    public ProfitChart(Context context,SortedMap<LocalDate,Long> values){
        super(context);points=new ArrayList<>(values.entrySet());setContentDescription("累计盈亏折线图，滑动指示线查看日期与金额");
    }
    float d(float value){return value*getResources().getDisplayMetrics().density;}
    float left(){
        if(cachedLeft>0)return cachedLeft;
        ChartScale scale=scale();paint.setTextSize(d(9));float width=0;
        for(long tick=scale.low;tick<=scale.high;tick+=scale.step)width=Math.max(width,paint.measureText(ChartScale.label(tick)));
        return cachedLeft=width+d(6);
    }
    float right(){return Math.max(left()+1,getWidth()-d(4));}
    float top(){return d(8);}
    float bottom(){return Math.max(top()+1,getHeight()-d(25));}
    ChartScale scale(){
        if(cachedScale!=null)return cachedScale;
        long min=0,max=0;boolean found=false;
        for(Map.Entry<LocalDate,Long> point:points)if(point.getValue()!=null){if(!found){min=max=point.getValue();found=true;}else{min=Math.min(min,point.getValue());max=Math.max(max,point.getValue());}}
        return cachedScale=ChartScale.signed(min,max);
    }
    float pointX(int i){return points.size()==1?(left()+right())/2:left()+(right()-left())*i/Math.max(1,points.size()-1);}
    float pointY(long amount){return bottom()-(bottom()-top())*scale().fraction(amount);}
    @Override protected void onDraw(Canvas canvas){
        if(getHeight()<d(45))return;
        ChartScale scale=scale();paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(d(.7f));paint.setTextSize(d(9));
        for(long tick=scale.low;tick<=scale.high;tick+=scale.step){
            float y=bottom()-(bottom()-top())*scale.fraction(tick);
            paint.setColor(Ui.BORDER);canvas.drawLine(left(),y,right(),y,paint);
            paint.setColor(Ui.MUTED);paint.setTextAlign(Paint.Align.RIGHT);
            String label=ChartScale.label(tick);
            paint.setTextSize(d(9));while(paint.measureText(label)>left()-d(3)&&paint.getTextSize()>d(6))paint.setTextSize(paint.getTextSize()-d(.5f));
            canvas.drawText(label,left()-d(3),y+d(3),paint);
        }
        if(points.isEmpty()){paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Ui.MUTED);canvas.drawText("暂无已确认收益",(left()+right())/2,(top()+bottom())/2,paint);return;}
        path.reset();fill.reset();boolean open=false;float lastX=0;
        for(int i=0;i<points.size();i++){
            Long value=points.get(i).getValue();if(value==null){if(open){fill.lineTo(lastX,bottom());fill.close();}open=false;continue;}
            float x=pointX(i),y=pointY(value);if(!open){path.moveTo(x,y);fill.moveTo(x,bottom());fill.lineTo(x,y);}else{path.lineTo(x,y);fill.lineTo(x,y);}open=true;lastX=x;
        }
        if(open){fill.lineTo(lastX,bottom());fill.close();}
        paint.setColor((Ui.ACCENT&0xffffff)|0x14000000);canvas.drawPath(fill,paint);
        paint.setColor(Ui.ACCENT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<points.size();i++)if(points.get(i).getValue()!=null&&(i==0||points.get(i-1).getValue()==null)&&(i+1==points.size()||points.get(i+1).getValue()==null))canvas.drawCircle(pointX(i),pointY(points.get(i).getValue()),d(3),paint);
        paint.setColor(Ui.MUTED);paint.setTextSize(d(9));paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(points.get(0).getKey().toString(),left(),bottom()+d(17),paint);
        if(points.size()>1){paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(points.get(points.size()-1).getKey().toString(),right(),bottom()+d(17),paint);}
        if(selected>=0&&selected<points.size()){
            Map.Entry<LocalDate,Long> point=points.get(selected);float x=pointX(selected),y=point.getValue()==null?bottom():pointY(point.getValue());
            paint.setColor(Ui.INK_SOFT);paint.setStrokeWidth(d(1));canvas.drawLine(x,top(),x,bottom(),paint);
            if(point.getValue()!=null){paint.setColor(Ui.INK);canvas.drawCircle(x,y,d(4),paint);paint.setColor(Ui.SURFACE);canvas.drawCircle(x,y,d(2),paint);}
            tooltip.draw(canvas,point.getKey()+(point.getValue()==null?" 待更新":" ¥ "+Ledger.decimal(point.getValue())),x,y,getWidth(),getHeight(),d(1));
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(points.isEmpty())return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){
            removeCallbacks(hideSelection);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);selected=Math.max(0,Math.min(points.size()-1,Math.round((event.getX()-left())/(right()-left())*(points.size()-1))));invalidate();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();postDelayed(hideSelection,FinanceChart.SELECTION_MILLIS);return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();if(selected>=0)announceForAccessibility(points.get(selected).getKey()+" "+(points.get(selected).getValue()==null?"待更新":Ledger.decimal(points.get(selected).getValue())+"元"));return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(hideSelection);selected=-1;super.onDetachedFromWindow();}
}
