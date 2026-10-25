package cn.yueji.balance;

import android.graphics.*;

final class ChartTooltip {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF box=new RectF();
    void draw(Canvas canvas,String text,float x,float y,int width,int height,float density){
        draw(canvas,text,x,y,width,height,density,false);
    }
    void drawAbove(Canvas canvas,String text,float x,float y,int width,int height,float density){
        draw(canvas,text,x,y,width,height,density,true);
    }
    private void draw(Canvas canvas,String text,float x,float y,int width,int height,float density,boolean above){
        paint.setTypeface(Ui.MEDIUM);paint.setTextSize(11*density);paint.setTextAlign(Paint.Align.LEFT);
        while(paint.measureText(text)>width-24*density&&paint.getTextSize()>7*density)paint.setTextSize(paint.getTextSize()-.5f*density);
        float w=Math.min(width-8*density,paint.measureText(text)+14*density),h=26*density;
        float left=above?x-w/2:x+9*density;
        if(!above&&left+w>width-4*density)left=x-w-9*density;
        left=Math.max(4*density,Math.min(left,width-w-4*density));
        float top=y-h-9*density;if(!above&&top<2*density)top=y+9*density;
        top=Math.max(2*density,Math.min(top,height-h-2*density));
        box.set(left,top,left+w,top+h);paint.setColor(Ui.INK);canvas.drawRoundRect(box,6*density,6*density,paint);
        paint.setColor(Ui.SURFACE);canvas.drawText(text,left+7*density,top+h/2-(paint.ascent()+paint.descent())/2,paint);
    }
    ChartTooltip(){}
}
