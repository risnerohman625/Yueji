package cn.yueji.balance;

import java.math.*;

/** Axis limits use round yuan steps; amounts remain integer cents throughout the ledger. */
final class ChartScale {
    final long low,high,step;
    private ChartScale(long low,long high,long step){this.low=low;this.high=high;this.step=step;}
    static ChartScale positive(long maximum){
        long visibleMax=Math.max(100000,maximum);
        long step=nice(visibleMax/4.0);
        long high=Math.max(step,((visibleMax+step-1)/step)*step);
        return new ChartScale(0,high,step);
    }
    static ChartScale signed(long min,long max){
        if(min==max){min-=100;max+=100;}
        long step=nice(Math.max(1,(double)max-min)/4);
        long low=Math.floorDiv(min,step)*step;
        long high=(long)Math.ceil(max/(double)step)*step;
        if(high<=low)high=low+step;
        return new ChartScale(low,high,step);
    }
    private static long nice(double raw){
        double power=Math.pow(10,Math.floor(Math.log10(Math.max(1,raw))));
        double n=raw/power;
        return Math.max(1,(long)((n<=1?1:n<=2?2:n<=5?5:10)*power));
    }
    static String label(long cents){
        BigDecimal yuan=BigDecimal.valueOf(cents,2);
        long magnitude=Math.abs(cents);
        if(magnitude>=1000000)return yuan.divide(BigDecimal.valueOf(10000),2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"万";
        if(magnitude>=100000)return yuan.divide(BigDecimal.valueOf(1000),2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"千";
        return yuan.stripTrailingZeros().toPlainString();
    }
    float fraction(long cents){return (float)((cents-(double)low)/(high-(double)low));}
}
