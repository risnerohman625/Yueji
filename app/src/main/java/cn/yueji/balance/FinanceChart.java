package cn.yueji.balance;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Unit-free axes; lines have a temporary cursor, bars only a tooltip above the selected bar. */
public final class FinanceChart extends View {
    final String[] labels;final Long[] first,second;final boolean bars;
    private int lineColor=Ui.ACCENT,selected=-1,series;
    private long fixedMaximum;
    private ChartScale cachedScale;
    private boolean[] known;
    private float cachedLeft;
    private boolean axisOnly,extremes,verticalGesture,months;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(),area=new Path();
    private final ChartTooltip tooltip=new ChartTooltip();
    private float downX,downY;
    private final Runnable hideSelection=()->{selected=-1;invalidate();};
    static final long SELECTION_MILLIS=2500;
    public FinanceChart setLineColor(int color){lineColor=color;return this;}
    public FinanceChart setMaximum(long max){fixedMaximum=max;cachedScale=null;cachedLeft=0;return this;}
    public FinanceChart setAxisOnly(){axisOnly=true;return this;}
    public FinanceChart markExtremes(){extremes=true;return this;}
    public FinanceChart asMonths(){months=true;return this;}
    public FinanceChart asYears(){months=false;return this;}
    public FinanceChart setKnown(boolean[] values){known=values.clone();invalidate();return this;}
    boolean known(int i){return known==null||known[i];}
    public FinanceChart(Context context,String[] labels,Long[] first,Long[] second,boolean bars){
        super(context);this.labels=labels;this.first=first;this.second=second;this.bars=bars;
        StringBuilder description=new StringBuilder(bars?"年收支柱状图，点击柱条查看金额。":"折线图，左右拖动指示线查看金额。");
        for(int i=0;i<labels.length;i++){
            description.append(labels[i]).append("：").append(first[i]==null?"暂无":Ledger.decimal(first[i])).append("元；");
            if(second!=null)description.append("收入：").append(second[i]==null?"暂无":Ledger.decimal(second[i])).append("元；");
        }
        setContentDescription(description);setFocusable(true);
    }
    float d(float value){return value*getResources().getDisplayMetrics().density;}
    float left(){
        if(cachedLeft>0)return cachedLeft;
        ChartScale scale=scale();p.setTextSize(d(9));float width=0;
        for(long tick=scale.low;tick<=scale.high;tick+=scale.step)width=Math.max(width,p.measureText(ChartScale.label(tick)));
        return cachedLeft=width+d(6);
    }
    float right(){return Math.max(left()+1,getWidth()-d(4));}
    float top(){return d(bars?38:8);}
    float bottom(){return Math.max(top()+1,getHeight()-d(6));}
    ChartScale scale(){
        if(cachedScale!=null)return cachedScale;
        long min=0,max=Math.max(1,fixedMaximum);
        for(Long value:first)if(value!=null){min=Math.min(min,value);max=Math.max(max,value);}
        if(second!=null)for(Long value:second)if(value!=null){min=Math.min(min,value);max=Math.max(max,value);}
        return cachedScale=min<0?ChartScale.signed(min,max):ChartScale.positive(max);
    }
    float pointX(int index){return left()+(right()-left())*(index+.5f)/Math.max(1,labels.length);}
    float pointY(long amount){return bottom()-(bottom()-top())*scale().fraction(amount);}
    private float barWidth(){return Math.min(d(20),(right()-left())/Math.max(1,labels.length)*.29f);}
    @Override protected void onDraw(Canvas canvas){
        if(getHeight()<d(35)||labels.length==0)return;
        ChartScale scale=scale();p.setShader(null);p.setTypeface(null);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(d(.7f));
        for(long tick=scale.low;tick<=scale.high;tick+=scale.step){
            float y=bottom()-(bottom()-top())*scale.fraction(tick);
            p.setColor(Ui.BORDER);canvas.drawLine(left(),y,right(),y,p);
            p.setColor(Ui.MUTED);p.setTextAlign(Paint.Align.RIGHT);
            String text=ChartScale.label(tick);float max=left()-d(3);p.setTextSize(d(9));
            while(p.measureText(text)>max&&p.getTextSize()>d(6))p.setTextSize(p.getTextSize()-d(.5f));
            canvas.drawText(text,left()-d(3),y+d(3),p);
        }
        if(axisOnly)return;
        if(bars){
            for(int i=0;i<labels.length;i++){
                float x=pointX(i),w=barWidth();
                drawBar(canvas,first[i],x-w-d(1),x-d(1),Ui.GREEN);
                if(second!=null)drawBar(canvas,second[i],x+d(1),x+w+d(1),Ui.RED);
            }
        }else drawLine(canvas);
        if(selected>=0)drawSelection(canvas);
    }
    private void drawLine(Canvas canvas){
        path.reset();area.reset();boolean open=false;float lastX=0;Long min=null,max=null;
        for(int i=0;i<first.length;i++)if(first[i]!=null&&known(i)){long value=first[i];min=min==null?value:Math.min(min,value);max=max==null?value:Math.max(max,value);}
        for(int i=0;i<first.length;i++){
            if(first[i]==null){if(open){area.lineTo(lastX,bottom());area.close();}open=false;continue;}
            float x=pointX(i),y=pointY(first[i]);
            if(open){path.lineTo(x,y);area.lineTo(x,y);}else{path.moveTo(x,y);area.moveTo(x,bottom());area.lineTo(x,y);}
            open=true;lastX=x;
        }
        if(open){area.lineTo(lastX,bottom());area.close();}
        p.setShader(new LinearGradient(0,top(),0,bottom(),(lineColor&0xffffff)|0x30000000,(lineColor&0xffffff)|0x03000000,Shader.TileMode.CLAMP));
        canvas.drawPath(area,p);p.setShader(null);p.setColor(lineColor);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(2));
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<first.length;i++)if(first[i]!=null){
            int color=pointColor(i,min,max);boolean special=extremes&&known(i)&&min!=null;
            p.setColor(Ui.SURFACE);canvas.drawCircle(pointX(i),pointY(first[i]),d(special?4.6f:3.8f),p);
            p.setColor(color);canvas.drawCircle(pointX(i),pointY(first[i]),d(special?3.3f:2.6f),p);
        }
    }
    private int pointColor(int i,Long min,Long max){
        if(!known(i))return Ui.FAINT;
        if(extremes&&min!=null){
            if(first[i].equals(min)&&min==0)return Ui.GREEN;
            if(first[i].equals(max))return Ui.RED;
            if(first[i].equals(min))return Ui.GREEN;
        }
        return lineColor;
    }
    private void drawBar(Canvas canvas,Long value,float left,float right,int color){
        if(value==null)return;p.setColor(color);p.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(left,Math.min(pointY(value),bottom()-d(2)),right,bottom(),d(3),d(3),p);
        boolean highlight=selected>=0&&Math.abs((left+right)/2-(pointX(selected)+(series==0?-1:1)*(barWidth()/2+d(1))))<d(1);
        if(highlight){p.setColor(Ui.INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(2));canvas.drawRoundRect(left-d(1),Math.min(pointY(value),bottom()-d(2))-d(1),right+d(1),bottom(),d(3),d(3),p);p.setStyle(Paint.Style.FILL);}
    }
    String selectionText(){
        if(selected<0||selected>=labels.length)return "";
        Long value=series==1&&second!=null?second[selected]:first[selected];
        return labels[selected]+(bars?(series==0?" 支出":" 收入"):months?"月":"")+" "+(value==null?"暂无数据":"¥ "+Ledger.decimal(value)+"元"+(!known(selected)?"（无记录）":""));
    }
    private void drawSelection(Canvas canvas){
        Long value=series==1&&second!=null?second[selected]:first[selected];float x=pointX(selected),y=value==null?bottom():pointY(value);
        if(bars)x+=series==0?-(barWidth()/2+d(1)):(barWidth()/2+d(1));
        else{
            p.setColor(Ui.INK_SOFT);p.setStrokeWidth(d(1));canvas.drawLine(x,top(),x,bottom(),p);
            if(value!=null){p.setColor(Ui.INK);canvas.drawCircle(x,y,d(4),p);p.setColor(Ui.SURFACE);canvas.drawCircle(x,y,d(2),p);}
        }
        if(bars)tooltip.drawAbove(canvas,selectionText(),x,y,getWidth(),getHeight(),d(1));
        else tooltip.draw(canvas,selectionText(),x,y,getWidth(),getHeight(),d(1));
    }
    private int nearest(float x){return Math.max(0,Math.min(labels.length-1,(int)((x-left())/(right()-left())*labels.length)));}
    private boolean select(float x,float y){
        if(labels.length==0)return false;
        int i=nearest(x);series=bars&&second!=null&&x>pointX(i)?1:0;
        Long value=series==1&&second!=null?second[i]:first[i];
        if(bars&&(value==null||y<pointY(value)-d(12)||y>bottom()+d(8)||Math.abs(x-pointX(i))>barWidth()+d(3)))return false;
        selected=i;invalidate();return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(axisOnly)return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(hideSelection);downX=event.getX();downY=event.getY();verticalGesture=false;
                if(select(downX,downY))return true;
                postDelayed(hideSelection,SELECTION_MILLIS);return false;
            case MotionEvent.ACTION_MOVE:
                float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);
                if(dy>d(12)&&dy>dx*1.3f){verticalGesture=true;postDelayed(hideSelection,SELECTION_MILLIS);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return false;}
                if(!bars&&!verticalGesture){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);select(event.getX(),event.getY());}
                return true;
            case MotionEvent.ACTION_UP:
                if(!verticalGesture)performClick();postDelayed(hideSelection,SELECTION_MILLIS);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:
                postDelayed(hideSelection,SELECTION_MILLIS);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();if(selected>=0)announceForAccessibility(selectionText());return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(hideSelection);selected=-1;super.onDetachedFromWindow();}
}
