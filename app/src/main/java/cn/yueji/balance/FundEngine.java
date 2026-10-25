package cn.yueji.balance;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class FundEngine {
    public static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
    public static LocalDateTime ledgerTime(LocalDateTime beijingTime){return beijingTime.atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();}
    public static long cents(BigDecimal value){return value.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}
    public static BigDecimal shares(long amount,long fee,BigDecimal nav){
        if(amount<=fee||fee<0||nav.signum()<=0)throw new IllegalArgumentException("买入金额须大于手续费且净值须大于0");
        return BigDecimal.valueOf(amount-fee,2).divide(nav,2,RoundingMode.HALF_UP);
    }
    public static void resolve(Ledger.Fund f,TradingCalendar calendar){
        for(Ledger.FundBuy b:f.buys){
            if(!b.overrideDates){b.tradeDate=calendar.tradeDate(b.at);b.confirmDate=calendar.next(b.tradeDate);}
            BigDecimal price=f.prices.get(b.tradeDate);
            b.price=price==null?BigDecimal.ZERO:price;
            b.units=price==null?BigDecimal.ZERO:shares(b.amount,b.fee,price);
        }
        f.shares=units(f,now().toLocalDate());f.cost=holdingCost(f,now().toLocalDate());
    }
    public static boolean active(Ledger.FundBuy b,LocalDate date){return !date.isBefore(b.at.toLocalDate())&&(b.saleDate==null||date.isBefore(b.saleDate));}
    public static boolean held(Ledger.Fund f,LocalDate date){if(f.buys.isEmpty())return f.shares.signum()>0;for(Ledger.FundBuy b:f.buys)if(active(b,date))return true;return false;}
    public static BigDecimal units(Ledger.Fund f,LocalDate date){
        if(f.buys.isEmpty())return f.shares;
        BigDecimal units=BigDecimal.ZERO;for(Ledger.FundBuy b:f.buys)if(active(b,date)&&b.confirmDate!=null&&!date.isBefore(b.confirmDate))units=units.add(unitsAt(f,b,date));return units;
    }
    static BigDecimal unitsAt(Ledger.Fund f,Ledger.FundBuy b,LocalDate date){
        BigDecimal amount=b.units;if(b.tradeDate==null||date.isBefore(b.tradeDate))return BigDecimal.ZERO;
        for(BigDecimal factor:f.splits.subMap(b.tradeDate,false,date,true).values())amount=amount.multiply(factor);
        return amount.setScale(2,RoundingMode.HALF_UP);
    }
    static long dividends(Ledger.Fund f,Ledger.FundBuy b,LocalDate date){
        if(b.confirmDate==null||date.isBefore(b.confirmDate))return 0;long value=0;
        for(Map.Entry<LocalDate,BigDecimal> payout:f.dividends.subMap(b.confirmDate,true,date,true).entrySet())value=Math.addExact(value,cents(unitsAt(f,b,payout.getKey()).multiply(payout.getValue())));
        return value;
    }
    public static long holdingCost(Ledger.Fund f,LocalDate date){if(f.buys.isEmpty())return f.cost;long total=0;for(Ledger.FundBuy b:f.buys)if(active(b,date))total=Math.addExact(total,Math.max(0,b.amount-dividends(f,b,date)));return total;}
    public static long value(Ledger.Fund f,LocalDate date){
        Map.Entry<LocalDate,BigDecimal> quote=f.prices.floorEntry(date);BigDecimal nav=quote==null?f.nav:quote.getValue();
        if(f.buys.isEmpty())return cents(f.shares.multiply(nav));
        long value=0;for(Ledger.FundBuy b:f.buys)if(active(b,date)){
            if(b.confirmDate==null||date.isBefore(b.confirmDate)||b.units.signum()==0)value=Math.addExact(value,b.amount);
            else value=Math.addExact(value,cents(unitsAt(f,b,date).multiply(nav)));
        }return value;
    }
    private static Long profitAt(Ledger.Fund f,Ledger.FundBuy b,LocalDate date){
        if(b.confirmDate==null||date.isBefore(b.confirmDate))return 0L;
        if(b.price.signum()<=0||b.units.signum()==0)return null;
        Map.Entry<LocalDate,BigDecimal> quote=f.prices.floorEntry(date);
        return quote==null?null:cents(unitsAt(f,b,date).multiply(quote.getValue()))-b.amount;
    }
    /** Dates after a fund was deleted from the home page count as the last date it was followed. */
    public static LocalDate clamp(Ledger.Fund f,LocalDate date){return f.frozenOn!=null&&date.isAfter(f.frozenOn)?f.frozenOn:date;}
    public static void remove(Ledger.Fund f,LocalDateTime at){
        LocalDate day=at.toLocalDate(),last=f.prices.isEmpty()?parseDate(f.navDate):f.prices.floorKey(day);
        f.removedAt=at;f.frozenOn=last==null||last.isAfter(day)?day:last;
    }
    public static void restore(Ledger.Fund f){f.removedAt=null;f.frozenOn=null;}
    public static long cumulative(Ledger.Fund f,LocalDate date){
        date=clamp(f,date);
        if(f.buys.isEmpty())return value(f,date)-f.cost;
        long total=0;for(Ledger.FundBuy b:f.buys){
            if(date.isBefore(b.at.toLocalDate()))continue;
            if(b.saleDate!=null&&!date.isBefore(b.saleDate)){total=Math.addExact(total,b.saleAmount-b.amount+dividends(f,b,b.saleDate));continue;}
            Long profit=profitAt(f,b,date);if(profit!=null)total=Math.addExact(total,profit+dividends(f,b,date));
        }return total;
    }
    public static LocalDate firstDate(Ledger.Fund f){
        LocalDate first=null;for(Ledger.FundBuy b:f.buys)if(first==null||b.at.toLocalDate().isBefore(first))first=b.at.toLocalDate();
        if(first==null&&!f.navDate.isEmpty())try{first=LocalDate.parse(f.navDate);}catch(Exception ignored){}
        return first;
    }
    public static Long daily(Ledger.Fund f,LocalDate day){
        return daily(f,day,null);
    }
    public static Long daily(Ledger.Fund f,LocalDate day,TradingCalendar calendar){
        if(day.isAfter(now().toLocalDate()))return null;
        if(f.frozenOn!=null&&day.isAfter(f.frozenOn))return 0L;
        boolean open=calendar!=null?calendar.open(day):day.getDayOfWeek()!=DayOfWeek.SATURDAY&&day.getDayOfWeek()!=DayOfWeek.SUNDAY;
        if(!open)return cumulative(f,day)-cumulative(f,day.minusDays(1));
        if(f.buys.isEmpty()){
            if(day.toString().equals(f.navDate))return f.dayProfit();
            return null;
        }
        LocalDate first=firstDate(f);if(first==null||day.isBefore(first))return 0L;
        boolean confirmed=false;for(Ledger.FundBuy b:f.buys)if(b.confirmDate!=null&&!day.isBefore(b.confirmDate)&&active(b,day))confirmed=true;
        if(confirmed&&!f.prices.containsKey(day))return null;
        for(Ledger.FundBuy buy:f.buys)if(active(buy,day)&&buy.confirmDate!=null&&!day.isBefore(buy.confirmDate)){
            if(profitAt(f,buy,day)==null||profitAt(f,buy,day.minusDays(1))==null)return null;
        }
        return cumulative(f,day)-cumulative(f,day.minusDays(1));
    }
    public static long today(Ledger.Fund f,LocalDateTime now){
        if(now.toLocalTime().isBefore(LocalTime.of(15,0)))return 0;
        Long value=daily(f,now.toLocalDate());return value==null?0:value;
    }
    public static long heldProfit(Ledger.Fund f,LocalDate day){return value(f,day)-holdingCost(f,day);}
    public static LocalDate lastPublishedDay(Ledger.Fund f,LocalDate today){return f.prices.isEmpty()?parseDate(f.navDate):f.prices.floorKey(today);}
    public static boolean known(Ledger.Fund f,LocalDate date){
        date=clamp(f,date);
        for(Ledger.FundBuy b:f.buys)if(!date.isBefore(b.at.toLocalDate())&&(b.confirmDate==null||!date.isBefore(b.confirmDate))&&b.units.signum()==0&&(b.saleDate==null||date.isBefore(b.saleDate)))return false;
        return true;
    }
    private static LocalDate parseDate(String s){try{return LocalDate.parse(s);}catch(Exception e){return null;}}
    public static double rate(long profit,long cost){return cost==0?0:profit*100.0/cost;}
    public static int heldDays(Ledger.Fund f,LocalDate day){
        LocalDate first=null;for(Ledger.FundBuy b:f.buys)if(active(b,day)&&b.confirmDate!=null&&!b.confirmDate.isAfter(day)&&(first==null||b.confirmDate.isBefore(first)))first=b.confirmDate;
        return first==null?0:(int)ChronoUnit.DAYS.between(first,day)+1;
    }
    public static long invested(Ledger.Fund f){long total=0;for(Ledger.FundBuy b:f.buys)total=Math.addExact(total,b.amount);return total;}
    /** True once at least one purchase batch of the fund is confirmed and still held on that day. */
    public static boolean positionConfirmed(Ledger.Fund f,LocalDate day){
        for(Ledger.FundBuy b:f.buys)if(b.confirmDate!=null&&!day.isBefore(b.confirmDate)&&active(b,day))return true;
        return false;
    }
    /** Highest and lowest confirmed daily profit of the funds together, or null when no trading day is known yet. */
    public static long[] dailyExtremes(List<Ledger.Fund> funds,TradingCalendar calendar){
        LocalDate today=now().toLocalDate(),first=null;
        for(Ledger.Fund f:funds){LocalDate day=firstDate(f);if(day!=null&&(first==null||day.isBefore(first)))first=day;}
        if(first==null)return null;
        boolean any=false;long high=0,low=0;
        for(LocalDate day=first;!day.isAfter(today);day=day.plusDays(1)){
            if(!calendar.open(day))continue;
            boolean held=false,complete=true;long total=0;
            for(Ledger.Fund f:funds){
                if(f.frozenOn!=null&&day.isAfter(f.frozenOn))continue;
                if(positionConfirmed(f,day))held=true;
                Long value=daily(f,day,calendar);if(value==null){complete=false;break;}total=Math.addExact(total,value);
            }
            if(!held||!complete)continue;
            if(!any){high=low=total;any=true;}else{high=Math.max(high,total);low=Math.min(low,total);}
        }
        return any?new long[]{high,low}:null;
    }
    public static SortedMap<LocalDate,Long> series(List<Ledger.Fund> funds,LocalDate from,LocalDate to){
        SortedMap<LocalDate,Long> result=new TreeMap<>();for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){long value=0;for(Ledger.Fund f:funds)value=Math.addExact(value,cumulative(f,day));result.put(day,value);}return result;
    }
    private FundEngine(){}
}
