package cn.yueji.balance;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Leader labels use true percentages even where a tiny positive slice has a minimum visual angle. */
final class AssetDonut extends View {
    final AssetSlices slices;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final ChartTooltip tooltip=new ChartTooltip();
    final int[] colors={0xFF5369CF,0xFF6D83DD,0xFF879BE7,0xFF9EAFE8,0xFFB4C1ED,0xFFCBD4F2,0xFF7C66B7,0xFF9D88CD,0xFFB7A7DE,0xFF879CBD,0xFFA6B6CF,0xFFC0CCE0,0xFF757DAD,0xFF969CC2};
    int selected=-1;boolean touching;
    final Runnable hide=()->{selected=-1;invalidate();};
    AssetDonut(Context context,AssetSlices slices){
        super(context);this.slices=slices;setContentDescription("账户类型与基金占比环形图");setFocusable(true);
    }
    float d(float n){return n*getResources().getDisplayMetrics().density;}
    float radius(){return Math.min(getWidth()*.23f,getHeight()*.36f);}
    float cx(){return getWidth()/2f;}float cy(){return getHeight()/2f;}
    float thickness(){return radius()*.37f;}
    float angle(int i){float a=-90;for(int j=0;j<i;j++)a+=slices.sweeps[j];return a;}
    float mid(int i){return angle(i)+slices.sweeps[i]/2;}
    @Override protected void onDraw(Canvas c){
        float r=radius(),w=thickness();RectF box=new RectF(cx()-r,cy()-r,cx()+r,cy()+r);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w);paint.setStrokeCap(Paint.Cap.BUTT);
        if(slices.total==0){paint.setColor(Ui.BORDER);c.drawOval(box,paint);}
        for(int i=0;i<slices.names.length;i++){
            paint.setColor(colors[i%colors.length]);c.drawArc(box,angle(i),slices.sweeps[i],false,paint);
            // Radial hairlines separate adjacent slices without consuming a tiny slice's whole angle.
            double a=Math.toRadians(angle(i));paint.setColor(Ui.SURFACE);paint.setStrokeWidth(d(.8f));
            c.drawLine(cx()+(float)Math.cos(a)*(r-w/2),cy()+(float)Math.sin(a)*(r-w/2),cx()+(float)Math.cos(a)*(r+w/2),cy()+(float)Math.sin(a)*(r+w/2),paint);paint.setStrokeWidth(w);
        }
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Ui.MEDIUM);
        paint.setColor(Ui.INK);paint.setTextSize(d(15));c.drawText("资产分布",cx(),cy()-(paint.ascent()+paint.descent())/2,paint);
        if(slices.total==0){paint.setTextSize(d(11));paint.setColor(Ui.MUTED);c.drawText("暂无资产",cx(),cy()+d(25),paint);return;}
        if(selected>=0){
            int i=selected;Path outline=new Path();RectF outer=new RectF(cx()-r-w/2,cy()-r-w/2,cx()+r+w/2,cy()+r+w/2);
            RectF inner=new RectF(cx()-r+w/2,cy()-r+w/2,cx()+r-w/2,cy()+r-w/2);
            outline.arcTo(outer,angle(i),slices.sweeps[i]);outline.arcTo(inner,angle(i)+slices.sweeps[i],-slices.sweeps[i]);outline.close();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2.4f));paint.setColor(Ui.ACCENT);c.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);
            double a=Math.toRadians(mid(i));float x=cx()+(float)Math.cos(a)*r,y=cy()+(float)Math.sin(a)*r;
            String value=slices.names[i]+"  ¥ "+String.format(Locale.CHINA,"%,.2f",slices.amounts[i]/100d)+"  "+String.format(Locale.CHINA,"%.2f%%",slices.percent(i));
            tooltip.draw(c,value,x,y,getWidth(),getHeight(),getResources().getDisplayMetrics().density);
        }else{leaders(c,false);leaders(c,true);}
    }
    void leaders(Canvas c,boolean right){
        List<Integer> side=new ArrayList<>();
        for(int i=0;i<slices.names.length;i++)if((Math.cos(Math.toRadians(mid(i)))>=0)==right)side.add(i);
        side.sort(Comparator.comparingDouble(i->Math.sin(Math.toRadians(mid(i)))));
        float r=radius()+thickness()/2,spacing=Math.min(d(34),(getHeight()-d(32))/Math.max(1,side.size()));
        float[] ys=new float[side.size()];float last=d(8)-spacing;
        for(int j=0;j<side.size();j++){float ideal=cy()+(float)Math.sin(Math.toRadians(mid(side.get(j))))*(r+d(10));ys[j]=Math.max(last+spacing,ideal);last=ys[j];}
        if(ys.length>0&&ys[ys.length-1]>getHeight()-d(12)){
            ys[ys.length-1]=getHeight()-d(12);for(int j=ys.length-2;j>=0;j--)ys[j]=Math.min(ys[j],ys[j+1]-spacing);
        }
        for(int j=0;j<side.size();j++){
            int i=side.get(j);double a=Math.toRadians(mid(i));
            float x=cx()+(float)Math.cos(a)*(r+d(2)),y=cy()+(float)Math.sin(a)*(r+d(2));
            float end=cx()+(right?1:-1)*(r+d(12)),labelX=right?end+d(4):end-d(4),labelY=ys[j];
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(colors[i%colors.length]);
            Path line=new Path();line.moveTo(x,y);line.lineTo(end-(right?d(5):-d(5)),labelY);line.lineTo(end,labelY);c.drawPath(line,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTypeface(Ui.MEDIUM);paint.setTextAlign(right?Paint.Align.LEFT:Paint.Align.RIGHT);
            paint.setTextSize(d(10.5f));paint.setColor(Ui.INK_SOFT);
            c.drawText(slices.names[i],labelX,labelY-d(2),paint);
            c.drawText(String.format(Locale.CHINA,"%.1f%%",slices.percent(i)),labelX,labelY+d(11),paint);
        }
    }
    int hit(float x,float y){
        float dx=x-cx(),dy=y-cy(),distance=(float)Math.hypot(dx,dy);
        if(distance<radius()-thickness()/2-d(7)||distance>radius()+thickness()/2+d(12))return -1;
        float angle=((float)Math.toDegrees(Math.atan2(dy,dx))+450)%360,sum=0;
        for(int i=0;i<slices.sweeps.length;i++){sum+=slices.sweeps[i];if(angle<sum)return i;}return -1;
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                int index=hit(e.getX(),e.getY());if(index<0)return false;removeCallbacks(hide);touching=true;selected=index;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(!touching)return false;int next=hit(e.getX(),e.getY());if(next>=0)selected=next;invalidate();return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(!touching)return false;touching=false;getParent().requestDisallowInterceptTouchEvent(false);postDelayed(hide,2500);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
        }
        return false;
    }
    @Override public boolean performClick(){super.performClick();if(selected>=0)announceForAccessibility(slices.names[selected]+" "+String.format(Locale.CHINA,"%.2f%%",slices.percent(selected)));return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(hide);super.onDetachedFromWindow();}
}
