package cn.yueji.balance;

import android.content.Context;
import java.time.*;

/** Automatic NAV publication polling runs after the Shanghai close, not during trading. */
final class MarketSession {
    static final int[] INTERVALS={15,30,60,120,240,360};
    static boolean open(TradingCalendar calendar,LocalDateTime beijing){
        if(!calendar.open(beijing.toLocalDate()))return false;
        LocalTime time=beijing.toLocalTime();
        return !time.isBefore(LocalTime.of(15,0));
    }
    static boolean open(Context context){
        LocalDateTime now=FundEngine.now();TradingCalendar calendar=new TradingCalendar();calendar.load(context,now.getYear());return open(calendar,now);
    }
    static int interval(int value){for(int valid:INTERVALS)if(value==valid)return value;return 120;}
    static String label(int minutes){return minutes<60?minutes+"分钟":minutes%60==0?minutes/60+"小时":minutes+"分钟";}
    private MarketSession(){}
}
