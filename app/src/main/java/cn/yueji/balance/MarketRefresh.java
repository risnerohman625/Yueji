package cn.yueji.balance;

import android.content.Context;
import java.time.*;
import java.util.*;

/** One code, one fetch; publication is confirmed by NAV date, never the time of a successful request. */
final class MarketRefresh {
    static final Object LOCK=new Object();
    interface Fetcher {FundClient.Quote fetch(String code)throws Exception;}
    static LinkedHashSet<String> codes(Ledger.Data data){
        LinkedHashSet<String> codes=new LinkedHashSet<>();
        for(Ledger.Fund fund:data.funds)if(!fund.removed())codes.add(fund.code);
        for(Ledger.WatchFund fund:data.watchFunds)codes.add(fund.code);
        return codes;
    }
    static boolean current(String date,LocalDate target){try{return !LocalDate.parse(date).isBefore(target);}catch(Exception e){return false;}}
    static boolean published(Ledger.Data data,String code,LocalDate target){
        boolean found=false;
        for(Ledger.Fund fund:data.funds)if(!fund.removed()&&fund.code.equals(code)){found=true;if(!current(fund.navDate,target)||!fund.historyComplete)return false;}
        for(Ledger.WatchFund fund:data.watchFunds)if(fund.code.equals(code)){found=true;if(!current(fund.navDate,target)||!fund.historyComplete)return false;}
        return found;
    }
    static List<String> pending(Ledger.Data data,LocalDate day){List<String> result=new ArrayList<>();for(String code:codes(data))if(!published(data,code,day))result.add(code);return result;}
    static boolean due(Context context,Ledger.Data data,LocalDateTime now,boolean firstVisit){
        if(!data.settings.autoRefresh||pending(data,now.toLocalDate()).isEmpty())return false;
        if(firstVisit)return true;
        android.content.SharedPreferences preferences=context.getSharedPreferences("market-refresh",0);
        long stamp=now.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        return !preferences.getString("day","").equals(now.toLocalDate().toString())||stamp-preferences.getLong("attempt",0)>=MarketSession.interval(data.settings.refreshMinutes)*60_000L;
    }
    static void apply(Ledger.Data data,FundClient.Quote quote,TradingCalendar calendar){
        for(Ledger.Fund fund:data.funds)if(!fund.removed()&&fund.code.equals(quote.code)){
            if(fund.navDate.compareTo(quote.date)>0)continue;
            fund.name=quote.name;fund.nav=quote.nav;fund.previous=quote.previous;fund.navDate=quote.date;fund.fundType=quote.fundType;
            fund.prices.putAll(quote.history);fund.navReturns.putAll(quote.returns);fund.dividends.putAll(quote.dividends);fund.splits.putAll(quote.splits);
            fund.historyComplete=quote.complete;fund.error="";fund.updatedAt=FundEngine.now().toString();FundEngine.resolve(fund,calendar);
        }
        for(Ledger.WatchFund fund:data.watchFunds)if(fund.code.equals(quote.code)){
            if(fund.navDate.compareTo(quote.date)>0)continue;
            fund.name=quote.name;fund.nav=quote.nav;fund.previous=quote.previous;fund.navDate=quote.date;fund.fundType=quote.fundType;
            fund.prices.putAll(quote.history);fund.returns.putAll(quote.returns);fund.historyComplete=quote.complete;
        }
    }
    static String run(Context context,boolean automatic,boolean firstVisit,Collection<String> requested){
        return run(context,automatic,firstVisit,requested,FundClient::fetchHistory);
    }
    static String run(Context context,boolean automatic,boolean firstVisit,Collection<String> requested,Fetcher fetcher){
        return run(context,automatic,firstVisit,requested,fetcher,FundEngine::now);
    }
    static String run(Context context,boolean automatic,boolean firstVisit,Collection<String> requested,Fetcher fetcher,java.util.function.Supplier<LocalDateTime> clock){
        synchronized(LOCK){
            LedgerStore store=new LedgerStore(context);
            try{
                LocalDateTime now=clock.get();TradingCalendar calendar=new TradingCalendar();for(int year=2010;year<=now.getYear()+1;year++)calendar.load(context,year);
                Ledger.Data initial;long epoch;synchronized(LedgerStore.class){initial=store.load();epoch=LedgerStore.epoch();}
                if(automatic&&(!initial.settings.autoRefresh||!MarketSession.open(calendar,now)))return null;
                android.content.SharedPreferences preferences=context.getSharedPreferences("market-refresh",0);
                List<String> codes=automatic?pending(initial,now.toLocalDate()):new ArrayList<>(requested==null?codes(initial):new LinkedHashSet<>(requested));
                if(codes.isEmpty())return null;
                long stamp=now.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
                if(automatic&&!firstVisit&&preferences.getString("day","").equals(now.toLocalDate().toString())&&stamp-preferences.getLong("attempt",0)<MarketSession.interval(initial.settings.refreshMinutes)*60_000L)return null;
                if(automatic)preferences.edit().putString("day",now.toLocalDate().toString()).putLong("attempt",stamp).apply();
                String failure=null;
                for(String code:codes){
                    if(Thread.currentThread().isInterrupted()||LedgerStore.epoch()!=epoch)break;
                    if(automatic&&(!MarketSession.open(calendar,clock.get())||!clock.get().toLocalDate().equals(now.toLocalDate())))break;
                    try{
                        synchronized(LedgerStore.class){Ledger.Data current=store.load();if(!codes(current).contains(code)||automatic&&published(current,code,now.toLocalDate()))continue;}
                        FundClient.Quote quote=fetcher.fetch(code);
                        synchronized(LedgerStore.class){if(LedgerStore.epoch()!=epoch)break;Ledger.Data current=store.load();apply(current,quote,calendar);store.save(current);}
                    }catch(Exception e){if(failure==null)failure=NetworkIo.message(e);if(NetworkIo.connectivity(e))break;}
                }
                if(automatic)FundNotifications.publish(context,store.load());
                return failure;
            }finally{store.close();}
        }
    }
    private MarketRefresh(){}
}
