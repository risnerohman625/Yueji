package cn.yueji.balance;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Total-return metrics based on published daily return factors (cash distributions included). */
final class WatchAnalytics {
    static final String[] RANGES={"近1周","近1月","近3月","近6月","近1年","近3年","近5年","近10年","今年来","成立来"};
    static String elapsed(LocalDate established,LocalDate today){
        if(established==null||today==null||established.isAfter(today))return "待获取";
        long years=ChronoUnit.YEARS.between(established,today);
        if(years==0)return ChronoUnit.DAYS.between(established,today)+"天";
        long days=ChronoUnit.DAYS.between(established.plusYears(years),today);
        return years+"年"+days+"日";
    }
    static final class Result {
        final TreeMap<LocalDate,Double> growth=new TreeMap<>();
        double change,high,sharpe,drawdown;boolean available,recovered;long recoveryDays,declineDays,underwaterDays;
        LocalDate peakDate,troughDate,recoveryDate;
    }
    static LocalDate start(int range,LocalDate end){switch(range){case 0:return end.minusWeeks(1);case 1:return end.minusMonths(1);case 2:return end.minusMonths(3);case 3:return end.minusMonths(6);case 4:return end.minusYears(1);case 5:return end.minusYears(3);case 6:return end.minusYears(5);case 7:return end.minusYears(10);case 8:return end.withDayOfYear(1).minusDays(1);default:return LocalDate.of(2000,1,1);}}
    static Result calculate(Ledger.WatchFund fund,int range,LocalDate end){
        Result r=new Result();if(fund.prices.size()<2)return r;
        end=fund.prices.floorKey(end);if(end==null)return r;
        LocalDate boundary=start(range,end),base=range==9?fund.prices.firstKey():fund.prices.floorKey(boundary);
        if(base==null||base.isAfter(end))return r;
        List<Map.Entry<LocalDate,BigDecimal>> entries=new ArrayList<>(fund.prices.subMap(base,true,end,true).entrySet());if(entries.size()<2)return r;
        double value=100,peak=100,sum=0,sq=0;
        if(range==9&&fund.returns.containsKey(base)){value*=1+fund.returns.get(base).doubleValue()/100;peak=Math.max(peak,value);}int n=0;LocalDate peakDate=base;BigDecimal previous=entries.get(0).getValue();r.growth.put(base,value-100);
        for(int i=1;i<entries.size();i++){
            Map.Entry<LocalDate,BigDecimal> item=entries.get(i);BigDecimal published=fund.returns.get(item.getKey());
            double ratio=item.getValue().divide(previous,12,RoundingMode.HALF_UP).doubleValue()-1;
            double daily=published==null||Math.abs(published.doubleValue()/100-ratio)<=.000051?ratio:published.doubleValue()/100;
            value*=1+daily;sum+=daily;sq+=daily*daily;n++;double growth=value-100;r.growth.put(item.getKey(),growth);r.high=Math.max(r.high,growth);
            if(value>=peak){peak=value;peakDate=item.getKey();}double drop=(1-value/peak)*100;
            if(drop>r.drawdown+1e-10){r.drawdown=drop;r.peakDate=peakDate;r.troughDate=item.getKey();r.recovered=false;r.recoveryDate=null;}
            previous=item.getValue();
        }
        r.available=true;r.change=value-100;
        if(n>1){double mean=sum/n,sd=Math.sqrt(Math.max(0,(sq-sum*sum/n)/(n-1)));r.sharpe=sd<=1e-12?0:mean/sd*Math.sqrt(252);}
        if(r.troughDate!=null){double target=r.growth.get(r.peakDate);for(Map.Entry<LocalDate,Double> e:r.growth.tailMap(r.troughDate,false).entrySet())if(e.getValue()+1e-8>=target){r.recovered=true;r.recoveryDate=e.getKey();break;}LocalDate through=r.recovered?r.recoveryDate:r.growth.lastKey();r.declineDays=ChronoUnit.DAYS.between(r.peakDate,r.troughDate);r.recoveryDays=ChronoUnit.DAYS.between(r.troughDate,through);r.underwaterDays=ChronoUnit.DAYS.between(r.peakDate,through);}
        else r.recovered=true;return r;
    }
    static Ledger.WatchFund owned(Ledger.Fund fund){Ledger.WatchFund w=new Ledger.WatchFund(fund.code,fund.name);w.nav=fund.nav;w.previous=fund.previous;w.navDate=fund.navDate;w.fundType=fund.fundType;w.historyComplete=fund.historyComplete;w.prices.putAll(fund.prices);w.returns.putAll(fund.navReturns);try{w.profile.read(fund.profile.encode());}catch(Exception ignored){}return w;}
    private WatchAnalytics(){}
}
