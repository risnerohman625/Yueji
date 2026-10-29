package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Generates only explicitly enabled local bookkeeping plans; never sends a financial order. */
final class AutomaticInvestments {
    static LocalDateTime latest(Ledger.Fund fund){LocalDateTime last=null;if(fund!=null)for(Ledger.FundBuy b:fund.buys)if(last==null||b.at.isAfter(last))last=b.at;return last;}
    static void requireAppend(Ledger.Fund fund,LocalDateTime at){LocalDateTime last=latest(fund);if(last!=null&&at.isBefore(last))throw new IllegalArgumentException("买入/首期时间不能早于最新买入记录："+last);}
    static LocalDate scheduled(LocalDate nominal,TradingCalendar calendar){if(!calendar.covered(nominal.getYear()))throw new IllegalArgumentException("请先更新"+nominal.getYear()+"年休市日历，避免节假日错误生成");return calendar.onOrAfter(nominal);}
    static LocalDate afterNominal(Ledger.RecurringPlan p,LocalDate previous){
        if("每周".equals(p.frequency))return previous.plusWeeks(1);
        if("每两周".equals(p.frequency))return previous.plusWeeks(2);
        YearMonth month=YearMonth.from(previous).plusMonths(1);return month.atDay(Math.min(p.dayOfMonth,month.lengthOfMonth()));
    }
    static void configure(Ledger.Fund fund,String frequency,int day,LocalDateTime first,LocalDateTime now,TradingCalendar calendar){
        Ledger.RecurringPlan p=fund.plan;LocalDate nominal;LocalDateTime execution;
        if(p.automatic&&frequency.equals(p.frequency)&&day==p.dayOfMonth&&(!"每日".equals(frequency)||first!=null&&first.toString().equals(p.firstAt))){
            p.enabled=!RecurringInvestments.complete(p);p.error="";
            if(p.enabled&&p.nextAt.isEmpty()){LocalDateTime last=latest(fund);advance(p,last==null?LocalDateTime.parse(p.firstAt):last,calendar);}
            return;
        }
        if("每日".equals(frequency)){
            if(first==null||first.getYear()<2000)throw new IllegalArgumentException("请配置首期购买时间");
            requireAppend(fund,first);
            nominal=first.toLocalDate();scheduled(nominal,calendar);
            LocalDate trade=calendar.tradeDate(first);
            execution=trade.equals(first.toLocalDate())?first.withSecond(0).withNano(0):trade.atTime(12,0);
        }else{
            if(day<1||day>("每月".equals(frequency)?31:7))throw new IllegalArgumentException("执行日无效");
            nominal=now.toLocalDate().plusDays(1);
            if("每月".equals(frequency)){YearMonth month=YearMonth.from(nominal);nominal=month.atDay(Math.min(day,month.lengthOfMonth()));if(!nominal.isAfter(now.toLocalDate())){month=month.plusMonths(1);nominal=month.atDay(Math.min(day,month.lengthOfMonth()));}}
            else while(nominal.getDayOfWeek().getValue()!=day)nominal=nominal.plusDays(1);
            execution=scheduled(nominal,calendar).atTime(12,0);requireAppend(fund,execution);
        }
        p.frequency=frequency;p.dayOfMonth=day;p.automatic=true;p.firstAt=execution.toString();p.nextAt=execution.toString();p.nextNominal=nominal.toString();p.startDate=execution.toLocalDate().toString();p.nextDate=execution.toLocalDate().toString();p.error="";p.enabled=!RecurringInvestments.complete(p);
    }
    static void advance(Ledger.RecurringPlan p,LocalDateTime execution,TradingCalendar calendar){
        LocalDate nominal="每日".equals(p.frequency)?calendar.next(execution.toLocalDate()):afterNominal(p,LocalDate.parse(p.nextNominal));
        p.nextNominal=nominal.toString();p.nextAt=calendar.onOrAfter(nominal).atTime(12,0).toString();p.nextDate=LocalDateTime.parse(p.nextAt).toLocalDate().toString();
    }
    static int generate(Ledger.Data data,Ledger.Fund fund,LocalDateTime now,TradingCalendar calendar){
        Ledger.RecurringPlan p=fund.plan;if(!p.automatic||!p.enabled||fund.removed())return 0;
        int count=0;
        while(p.enabled&&!p.nextAt.isEmpty()&&!LocalDateTime.parse(p.nextAt).isAfter(now)){
            if(RecurringInvestments.complete(p)){p.enabled=false;p.nextAt="";p.nextDate="";break;}
            LocalDateTime at=LocalDateTime.parse(p.nextAt);boolean recorded=false;
            for(Ledger.RecurringRecord r:p.records)if(at.toLocalDate().equals(r.date))recorded=true;
            if(recorded){advance(p,at,calendar);continue;}
            if(fund.buys.size()>=1000)throw new IllegalArgumentException("买入记录达到1000笔上限，请暂停并整理计划");
            scheduled(at.toLocalDate(),calendar);requireAppend(fund,at);
            Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=data.nextId();buy.amount=p.amount;buy.fee=p.fee;buy.at=at;buy.buyAccountLinked=true;buy.buyAccountId=p.accountId;buy.accountFlowManaged=true;
            AccountCash.requireAccount(data,p.accountId,FundEngine.ledgerTime(at));fund.buys.add(buy);FundEngine.resolve(fund,calendar);
            Ledger.RecurringRecord r=new Ledger.RecurringRecord();r.id=data.nextId();r.buyId=buy.id;r.amount=buy.amount;r.accountId=p.accountId;r.date=at.toLocalDate();r.status="已登记";p.records.add(r);buy.recurringRecordId=r.id;
            try{AccountCash.rebuild(data);}catch(Exception e){fund.buys.remove(buy);p.records.remove(r);AccountCash.rebuild(data);throw e;}
            count++;
            if(RecurringInvestments.complete(p)){p.enabled=false;p.nextAt="";p.nextDate="";}else advance(p,at,calendar);
        }
        p.error="";return count;
    }
    private AutomaticInvestments(){}
}
