package cn.yueji.balance;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Published total-return chart with elapsed-time x positions and a draggable dated cursor. */
final class WatchChart extends View {
    final WatchAnalytics.Result result;final boolean repair;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    final List<Map.Entry<LocalDate,Double>> entries;final ChartTooltip tooltip=new ChartTooltip();int selected=-1;boolean touching;
    double minimum,maximum,tick;long span;LocalDate firstDate;
    final List<RectF> repairLabels=new ArrayList<>();
    final Runnable hide=()->{selected=-1;invalidate();};
    WatchChart(Context c,WatchAnalytics.Result r,boolean repair){super(c);result=r;this.repair=repair;entries=new ArrayList<>(r.growth.entrySet());double low=0,high=0;for(Map.Entry<LocalDate,Double> e:entries){low=Math.min(low,e.getValue());high=Math.max(high,e.getValue());}double raw=Math.max(.05,(high-low)/4),magnitude=Math.pow(10,Math.floor(Math.log10(raw)));double step=Math.ceil(raw/magnitude/0.5)*0.5*magnitude;tick=step;minimum=Math.floor(low/step)*step;maximum=Math.ceil(high/step)*step;if(maximum<=minimum)maximum=minimum+step;if(!entries.isEmpty()){firstDate=entries.get(0).getKey();span=ChronoUnit.DAYS.between(firstDate,entries.get(entries.size()-1).getKey());}setContentDescription("自选基金趋势图，拖动查看日期和涨跌幅");}
    float d(float n){return n*getResources().getDisplayMetrics().density;}
    float left(){return d(43);}float right(){return getWidth()-d(8);}float top(){return d(17);}float bottom(){return getHeight()-d(29);}
    double min(){return minimum;}
    double max(){return maximum;}
    float x(LocalDate day){return left()+(right()-left())*ChronoUnit.DAYS.between(firstDate,day)/Math.max(1f,span);}
    float y(double value){return bottom()-(float)((value-min())/(max()-min()))*(bottom()-top());}
    protected void onDraw(Canvas c){
        p.setTypeface(Ui.MEDIUM);p.setTextSize(d(10));p.setStyle(Paint.Style.FILL);
        if(entries.size()<2){p.setColor(Ui.MUTED);p.setTextAlign(Paint.Align.CENTER);c.drawText("该周期历史不足，指标按 0.0 显示",getWidth()/2f,getHeight()/2f,p);return;}
        p.setStrokeWidth(d(.6f));for(double value=minimum;value<=maximum+tick*.01;value+=tick){float yy=y(value);p.setColor(Ui.BORDER);c.drawLine(left(),yy,right(),yy,p);p.setColor(Ui.MUTED);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(Locale.CHINA,"%.1f%%",value),left()-d(5),yy+d(3),p);}

        Path line=new Path();for(int i=0;i<entries.size();i++){Map.Entry<LocalDate,Double> e=entries.get(i);if(i==0)line.moveTo(x(e.getKey()),y(e.getValue()));else line.lineTo(x(e.getKey()),y(e.getValue()));}
        Path area=new Path(line);area.lineTo(right(),bottom());area.lineTo(left(),bottom());area.close();p.setShader(new LinearGradient(0,top(),0,bottom(),Ui.ACCENT_SOFT,Ui.SURFACE,Shader.TileMode.CLAMP));c.drawPath(area,p);p.setShader(null);
        p.setColor(Ui.ACCENT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1.8f));c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
        repairLabels.clear();
        if(repair&&result.troughDate!=null){
            tag(c,"最大回撤"+String.format(Locale.CHINA,"%.2f%%",result.drawdown),result.troughDate,Ui.GREEN,false);
            if(result.recovered)tag(c,result.recoveryDays+"天修复",result.recoveryDate,Ui.RED,true);
            // Draw markers last so neither callout nor leader can hide them.
            marker(c,result.peakDate,Ui.GREEN);marker(c,result.troughDate,Ui.GREEN);
            if(result.recovered)marker(c,result.recoveryDate,Ui.RED);
        }
        p.setTextSize(d(9));p.setColor(Ui.MUTED);p.setTextAlign(Paint.Align.LEFT);c.drawText(entries.get(0).getKey().toString(),left(),getHeight()-d(8),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(entries.get(entries.size()-1).getKey().toString(),right(),getHeight()-d(8),p);
        if(selected>=0&&selected<entries.size()){Map.Entry<LocalDate,Double> e=entries.get(selected);float xx=x(e.getKey()),yy=y(e.getValue());p.setColor(Ui.MUTED);p.setStrokeWidth(d(1));c.drawLine(xx,top(),xx,bottom(),p);p.setColor(Ui.ACCENT);c.drawCircle(xx,yy,d(3),p);tooltip.draw(c,e.getKey()+"  "+String.format(Locale.CHINA,"%+.2f%%",e.getValue()),xx,yy,getWidth(),getHeight(),getResources().getDisplayMetrics().density);}
    }
    void marker(Canvas c,LocalDate day,int color){float xx=x(day),yy=y(result.growth.get(day));p.setColor(Ui.SURFACE);c.drawCircle(xx,yy,d(4.5f),p);p.setColor(color);c.drawCircle(xx,yy,d(3),p);}
    List<PointF> repairPoints(){List<PointF> points=new ArrayList<>();for(LocalDate day:Arrays.asList(result.peakDate,result.troughDate,result.recovered?result.recoveryDate:null))if(day!=null)points.add(new PointF(x(day),y(result.growth.get(day))));return points;}
    RectF labelBounds(float width,float height,float xx,float yy,boolean above){
        RectF best=null;double bestScore=Double.MAX_VALUE;
        List<PointF> points=repairPoints();
        // Search the plot for a compact callout that clears every marked point and prior label.
        for(float cy=top();cy<=bottom()-height+.1f;cy+=d(3))for(float cx=left();cx<=right()-width+.1f;cx+=d(3)){
            RectF box=new RectF(cx,cy,cx+width,cy+height);boolean blocked=false;
            for(PointF point:points){RectF safe=new RectF(box);safe.inset(-d(7),-d(7));if(safe.contains(point.x,point.y)){blocked=true;break;}}
            if(blocked)continue;for(RectF other:repairLabels){RectF safe=new RectF(other);safe.inset(-d(3),-d(3));if(RectF.intersects(box,safe)){blocked=true;break;}}if(blocked)continue;
            float ax=Math.max(box.left,Math.min(box.right,xx)),ay=Math.max(box.top,Math.min(box.bottom,yy));
            double score=Math.hypot(ax-xx,ay-yy)+((above?box.centerY()>yy:box.centerY()<yy)?d(8):0);
            if(score<bestScore){bestScore=score;best=box;}
        }
        return best==null?new RectF(left(),top(),left()+width,top()+height):best;
    }
    void tag(Canvas c,String text,LocalDate day,int color,boolean above){
        p.setTextSize(d(9));p.setTextAlign(Paint.Align.LEFT);float xx=x(day),yy=y(result.growth.get(day));
        RectF box=labelBounds(p.measureText(text)+d(10),d(17),xx,yy,above);repairLabels.add(box);
        float startX=Math.max(box.left,Math.min(box.right,xx)),startY=Math.max(box.top,Math.min(box.bottom,yy));
        double angle=Math.atan2(yy-startY,xx-startX);float tipX=xx-(float)Math.cos(angle)*d(5),tipY=yy-(float)Math.sin(angle)*d(5);
        p.setColor(color);p.setStrokeWidth(d(1));c.drawLine(startX,startY,tipX,tipY,p);
        for(double offset:new double[]{-.5,.5})c.drawLine(tipX,tipY,tipX-(float)Math.cos(angle+offset)*d(5),tipY-(float)Math.sin(angle+offset)*d(5),p);
        c.drawRoundRect(box,d(3),d(3),p);p.setColor(Color.WHITE);c.drawText(text,box.left+d(5),box.bottom-d(4),p);
    }
    void select(float x){if(entries.isEmpty())return;int best=0;for(int i=1;i<entries.size();i++)if(Math.abs(x(entries.get(i).getKey())-x)<Math.abs(x(entries.get(best).getKey())-x))best=i;selected=best;invalidate();}
    public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:removeCallbacks(hide);touching=true;getParent().requestDisallowInterceptTouchEvent(true);select(e.getX());return true;case MotionEvent.ACTION_MOVE:if(touching)select(e.getX());return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:touching=false;getParent().requestDisallowInterceptTouchEvent(false);postDelayed(hide,2500);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;default:return false;}}
    public boolean performClick(){super.performClick();return true;}
    protected void onDetachedFromWindow(){removeCallbacks(hide);super.onDetachedFromWindow();}
}
