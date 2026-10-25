package cn.yueji.balance;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The plot only selects. A separate horizontal-axis scroller moves the viewport. */
final class AssetTrendChart extends View {
    final List<AssetHistory.Point> points;
    final int mode;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final ChartTooltip tooltip=new ChartTooltip();
    final ChartScale scale;
    float offset,downX,downY;int selected=-1;boolean vertical;
    final Runnable hide=()->{selected=-1;invalidate();};
    static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy/MM/dd");
    static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    AssetTrendChart(Context c,List<AssetHistory.Point> points,int mode){
        super(c);this.points=points;this.mode=mode;long low=Long.MAX_VALUE,high=0;
        for(AssetHistory.Point p:points)if(p.amount!=null){low=Math.min(low,p.amount);high=Math.max(high,p.amount);}
        scale=low==Long.MAX_VALUE?ChartScale.positive(0):ChartScale.signed(low,high);
        setContentDescription("资产趋势折线图，滑动图内选择时间与金额；滑动下方横轴翻页");setFocusable(true);
    }
    float d(float n){return n*getResources().getDisplayMetrics().density;}
    float left(){
        paint.setTypeface(Ui.MEDIUM);paint.setTextSize(d(10));float max=0;for(long tick=scale.low;tick<=scale.high;tick+=scale.step)max=Math.max(max,paint.measureText(ChartScale.label(tick)));return max+d(10);
    }
    float top(){return d(16);}float bottom(){return Math.max(top()+1,getHeight()-d(12));}
    float plotWidth(){return Math.max(1,getWidth()-left()-d(8));}
    float slot(){return plotWidth()/Math.max(1,Math.min(10,points.size()));}
    float pointX(int i){return left()+(i+.5f)*slot()-offset;}
    float pointY(long amount){return bottom()-(bottom()-top())*scale.fraction(amount);}
    String label(int i){
        java.time.LocalDateTime t=points.get(i).at;
        return mode==2?""+t.getYear():mode==1?Ledger.csvMonth(java.time.YearMonth.from(t)):t.format(DATE);
    }
    void scrollToOffset(int x){offset=x;removeCallbacks(hide);selected=-1;invalidate();}
    void axis(LinearLayout host){
        LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);host.addView(row,new LinearLayout.LayoutParams(-1,(int)d(mode==0?51:42)));
        View spacer=new View(getContext());row.addView(spacer,new LinearLayout.LayoutParams(Math.round(left()),-1));
        HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setHorizontalScrollBarEnabled(false);scroll.setOverScrollMode(OVER_SCROLL_NEVER);scroll.setContentDescription("资产趋势横轴，左右滑动翻页");
        row.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));LinearLayout labels=new LinearLayout(getContext());scroll.addView(labels,new HorizontalScrollView.LayoutParams(-2,-1));
        Runnable size=()->{
            if(getWidth()==0)return;labels.removeAllViews();
            int count=Math.max(1,Math.min(10,points.size())),available=scroll.getWidth();float width=available/(float)count;
            for(int i=0;i<points.size();i++){
                final int index=i;TextView text=new TextView(getContext());text.setText(mode==0?label(i).replaceFirst("/","/\n"):label(i));text.setTextColor(Ui.INK_SOFT);text.setTextSize(mode==0?8:9);
                text.setGravity(Gravity.CENTER);text.setTypeface(Ui.MEDIUM);text.setContentDescription("资产节点"+label(i));
                text.setOnClickListener(v->{removeCallbacks(hide);selected=index;invalidate();postDelayed(hide,2500);});
                int start=Math.round(i*width),end=Math.round((i+1)*width);labels.addView(text,new LinearLayout.LayoutParams(end-start,-1));
            }
        };
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)size.run();});
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->scrollToOffset(x));
        post(size);
        // The right inset matches the plot, so each axis label remains under its own point.
        row.setPadding(0,0,Math.round(d(8)),0);
    }
    @Override protected void onDraw(Canvas c){
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setTypeface(Ui.MEDIUM);paint.setTextSize(d(10));
        for(long tick=scale.low;tick<=scale.high;tick+=scale.step){
            float y=pointY(tick);paint.setStrokeWidth(d(.7f));paint.setColor(Ui.BORDER);c.drawLine(left(),y,getWidth()-d(8),y,paint);
            paint.setColor(Ui.MUTED);paint.setTextAlign(Paint.Align.RIGHT);c.drawText(ChartScale.label(tick),left()-d(7),y+d(3),paint);
        }
        if(points.isEmpty()){paint.setColor(Ui.MUTED);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(d(13));c.drawText("暂无资产更新记录",(getWidth()+left())/2f,getHeight()/2f,paint);return;}
        if(points.stream().noneMatch(p->p.amount!=null)){paint.setColor(Ui.MUTED);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(d(12));c.drawText("历史净值或过渡金额待补全",(getWidth()+left())/2f,getHeight()/2f,paint);}
        c.save();c.clipRect(left(),0,getWidth()-d(8),getHeight());Path path=new Path(),area=new Path();boolean open=false;float lastX=0;
        for(int i=0;i<points.size();i++){
            Long value=points.get(i).amount;
            if(value==null){if(open){area.lineTo(lastX,bottom());area.close();}open=false;continue;}
            float x=pointX(i),y=pointY(value);
            if(open){path.lineTo(x,y);area.lineTo(x,y);}else{path.moveTo(x,y);area.moveTo(x,bottom());area.lineTo(x,y);}open=true;lastX=x;
        }
        if(open){area.lineTo(lastX,bottom());area.close();}
        paint.setShader(new LinearGradient(0,top(),0,bottom(),(Ui.ACCENT&0xFFFFFF)|0x32000000,(Ui.ACCENT&0xFFFFFF)|0x02000000,Shader.TileMode.CLAMP));c.drawPath(area,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2.1f));paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(Ui.ACCENT);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<points.size();i++)if(points.get(i).amount!=null){
            float x=pointX(i),y=pointY(points.get(i).amount);if(x<left()-d(5)||x>getWidth())continue;paint.setColor(Ui.SURFACE);c.drawCircle(x,y,d(3.4f),paint);paint.setColor(Ui.ACCENT);c.drawCircle(x,y,d(2.4f),paint);
        }
        if(selected>=0){
            AssetHistory.Point point=points.get(selected);float x=pointX(selected),y=point.amount==null?bottom():pointY(point.amount);
            paint.setColor(Ui.INK_SOFT);paint.setStrokeWidth(d(1));c.drawLine(x,top(),x,bottom(),paint);
            if(point.amount!=null){paint.setColor(Ui.SURFACE);c.drawCircle(x,y,d(4),paint);paint.setColor(Ui.ACCENT);c.drawCircle(x,y,d(2.5f),paint);}
        }
        c.restore();
        if(selected>=0){
            AssetHistory.Point point=points.get(selected);String text=point.at.format(TIME)+"  "+(point.amount==null?"净值待补全":"¥ "+String.format(Locale.CHINA,"%,.2f",point.amount/100d));
            tooltip.draw(c,text,pointX(selected),point.amount==null?getHeight()/2f:pointY(point.amount),getWidth(),getHeight(),getResources().getDisplayMetrics().density);
        }
    }
    void choose(float x){selected=Math.max(0,Math.min(points.size()-1,(int)((x-left()+offset)/slot())));invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(points.isEmpty())return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(e.getX()<left())return false;removeCallbacks(hide);vertical=false;downX=e.getX();downY=e.getY();choose(e.getX());getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(e.getY()-downY)>d(14)&&Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX)*1.4f){vertical=true;getParent().requestDisallowInterceptTouchEvent(false);}
                if(!vertical)choose(e.getX());return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);postDelayed(hide,2500);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
        }
        return false;
    }
    @Override public boolean performClick(){super.performClick();if(selected>=0)announceForAccessibility(label(selected)+" "+(points.get(selected).amount==null?"净值待补全":Ledger.decimal(points.get(selected).amount)+"元"));return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(hide);super.onDetachedFromWindow();}
}
