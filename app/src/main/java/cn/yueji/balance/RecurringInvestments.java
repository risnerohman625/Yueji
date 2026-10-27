package cn.yueji.balance;

import java.time.*;
import java.util.*;

final class RecurringInvestments {
    static LocalDate next(Ledger.RecurringPlan p,LocalDate after){
        return next(p,after,new TradingCalendar());
    }
    static LocalDate next(Ledger.RecurringPlan p,LocalDate after,TradingCalendar calendar){
        validateSchedule(p);
        if("每日".equals(p.frequency))return calendar.next(after);
        if("每周".equals(p.frequency)){LocalDate n=after.plusDays(1);while(n.getDayOfWeek().getValue()!=p.dayOfMonth)n=n.plusDays(1);return n;}
        LocalDate n=after.withDayOfMonth(p.dayOfMonth);return n.isAfter(after)?n:n.plusMonths(1);
    }
    static void validateSchedule(Ledger.RecurringPlan p){if(!Arrays.asList("每日","每周","每月").contains(p.frequency)||p.dayOfMonth<1||p.dayOfMonth>(p.frequency.equals("每周")?7:28)||p.totalPeriods<0||p.totalPeriods>10000)throw new IllegalArgumentException("定投周期、执行日或期数无效");}
    static LocalDate first(Ledger.RecurringPlan p,LocalDate from){return next(p,from.minusDays(1));}
    static LocalDate first(Ledger.RecurringPlan p,LocalDate from,TradingCalendar calendar){return next(p,from.minusDays(1),calendar);}
    static int completed(Ledger.RecurringPlan p){int n=0;for(Ledger.RecurringRecord r:p.records)if("已登记".equals(r.status))n++;return n;}
    static boolean complete(Ledger.RecurringPlan p){return p.totalPeriods>0&&completed(p)>=p.totalPeriods;}
    static void validate(Ledger.Fund fund,Ledger.Data data){
        Ledger.RecurringPlan p=fund.plan;validateSchedule(p);
        if(p.enabled){if(p.amount<=0||p.amount>Ledger.MAX_MONEY||p.fee<0||p.fee>=p.amount||p.accountId<=0)throw new IllegalArgumentException("定投金额或资金账户无效");LocalDate.parse(p.startDate);if(!p.nextDate.isEmpty())LocalDate.parse(p.nextDate);boolean found=false;for(Ledger.Account a:data.accounts)if(a.id==p.accountId)found=true;if(!found)throw new IllegalArgumentException("定投资金账户不存在");}
    }
    static void record(Ledger.Data data,Ledger.Fund fund,Ledger.FundBuy buy){
        record(data,fund,buy,new TradingCalendar());
    }
    static void record(Ledger.Data data,Ledger.Fund fund,Ledger.FundBuy buy,TradingCalendar calendar){
        if(complete(fund.plan))throw new IllegalArgumentException("计划期数已完成，请先调整定投计划");
        if(!buy.buyAccountLinked||buy.buyAccountId<=0)throw new IllegalArgumentException("每笔定投必须关联资金账户");
        if(buy.recurringRecordId>0)throw new IllegalArgumentException("该笔买入已登记定投，不能重复登记");
        Ledger.RecurringRecord r=new Ledger.RecurringRecord();r.id=data.nextId();r.buyId=buy.id;r.amount=buy.amount;r.accountId=buy.buyAccountId;r.date=buy.at.toLocalDate();r.status="已登记";fund.plan.records.add(r);buy.recurringRecordId=r.id;
        if(complete(fund.plan)){fund.plan.enabled=false;fund.plan.nextDate="";}else{LocalDate anchor=r.date;if(!fund.plan.nextDate.isEmpty()&&LocalDate.parse(fund.plan.nextDate).isAfter(anchor))anchor=LocalDate.parse(fund.plan.nextDate);fund.plan.nextDate=next(fund.plan,anchor,calendar).toString();}
    }
    static void reconcile(Ledger.Data data){
        Map<Long,Ledger.Fund> owners=new HashMap<>();Map<Long,Ledger.FundBuy> buys=new HashMap<>();
        for(Ledger.Fund f:data.funds)for(Ledger.FundBuy b:f.buys){owners.put(b.id,f);buys.put(b.id,b);}
        Map<Ledger.Fund,List<Ledger.RecurringRecord>> moved=new LinkedHashMap<>();Set<Long> ids=new HashSet<>(),recordedBuys=new HashSet<>();
        for(Ledger.Fund f:data.funds)for(Iterator<Ledger.RecurringRecord> it=f.plan.records.iterator();it.hasNext();){
            Ledger.RecurringRecord r=it.next();if(r.id<=0||r.buyId<=0||!ids.add(r.id)||!recordedBuys.add(r.buyId))throw new IllegalArgumentException("定投记录标识重复或无效");Ledger.FundBuy buy=buys.get(r.buyId);Ledger.Fund owner=owners.get(r.buyId);
            if(buy==null){r.status="买入已删除";continue;}
            if(!buy.buyAccountLinked)throw new IllegalArgumentException("每笔定投买入必须保留关联资金账户");
            r.amount=buy.amount;r.accountId=buy.buyAccountId;r.date=buy.at.toLocalDate();r.status="已登记";
            if(owner!=f){it.remove();moved.computeIfAbsent(owner,k->new ArrayList<>()).add(r);}
        }
        for(Map.Entry<Ledger.Fund,List<Ledger.RecurringRecord>> e:moved.entrySet())e.getKey().plan.records.addAll(e.getValue());
    }
    private RecurringInvestments(){}
}
