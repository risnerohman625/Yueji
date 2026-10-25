package cn.yueji.balance;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** A curve only: no axes, dots or invented data. The vertical range follows the observed values. */
final class HoldingSparkline extends View {
    final List<Long> points;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
    HoldingSparkline(Context context,List<Long> points){super(context);this.points=points;setContentDescription("基金持有总金额历史走势");}
    @Override protected void onDraw(Canvas c){
        long min=Long.MAX_VALUE,max=Long.MIN_VALUE;int count=0;
        for(Long value:points)if(value!=null){min=Math.min(min,value);max=Math.max(max,value);count++;}
        float d=getResources().getDisplayMetrics().density;
        if(count<2){p.setStyle(Paint.Style.FILL);p.setColor(Ui.MUTED);p.setTextSize(11*d);p.setTextAlign(Paint.Align.CENTER);c.drawText("走势待积累",getWidth()/2f,getHeight()/2f,p);return;}
        double span=Math.max(1,(double)max-min),pad=span*.12,low=min-pad,high=max+pad;
        if(min==max){low=min-1;high=max+1;}
        path.reset();boolean open=false;float lastX=0,lastY=0;
        for(int i=0;i<points.size();i++){
            Long value=points.get(i);if(value==null){open=false;continue;}
            float x=4*d+(getWidth()-8*d)*i/Math.max(1,points.size()-1),y=5*d+(float)((high-value)/(high-low))*(getHeight()-10*d);
            if(open){float mid=(lastX+x)/2;path.cubicTo(mid,lastY,mid,y,x,y);}else path.moveTo(x,y);
            lastX=x;lastY=y;open=true;
        }
        p.setColor(Ui.ACCENT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setStrokeCap(Paint.Cap.ROUND);c.drawPath(path,p);
    }
}
