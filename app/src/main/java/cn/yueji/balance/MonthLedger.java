package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Monthly provenance and per-account intervals. A missing account observation is never a zero balance. */
final class MonthLedger {
    static final class Change {
        final long accountId,before,after;
        final LocalDateTime from,to;
        Change(long accountId,long before,long after,LocalDateTime from,LocalDateTime to){
            this.accountId=accountId;this.before=before;this.after=after;this.from=from;this.to=to;
        }
        long delta(){return Math.subtractExact(after,before);}
    }
    static final class Result {
        final Ledger.Report report;
        final List<Change> changes=new ArrayList<>();
        Ledger.HistoryMonth history;
        LocalDateTime cutoff;
        long historical,adjustment;
        Result(YearMonth month){report=new Ledger.Report(month);}
    }
    static Result calculate(Ledger.Data data,YearMonth month){
        Result result=new Result(month);Ledger.Report r=result.report;
        LocalDateTime begin=month.atDay(1).atStartOfDay(),finish=month.plusMonths(1).atDay(1).atStartOfDay();
        for(Ledger.HistoryMonth h:data.history)if(h.month.equals(month))result.history=h;
        if(result.history!=null){
            result.cutoff=Ledger.historyCutoff(data,result.history);result.historical=result.history.expense;
            if(result.history.includesAdvances)for(Ledger.Entry e:data.entries)
                if(Ledger.REIMBURSE.equals(e.kind)&&Ledger.between(e.at,begin,result.cutoff))result.historical=Math.subtractExact(result.historical,e.amount);
            r.expense=result.historical;r.imported=r.available=r.baseline=true;r.through=result.cutoff;
            if(result.cutoff.equals(finish)){
                r.closed=!finish.isAfter(LocalDateTime.now());r.days=r.closed?month.lengthOfMonth():LocalDate.now().getDayOfMonth();
                if(!r.closed)r.through=LocalDateTime.now();r.anomaly=r.expense<0;return result;
            }
            begin=result.cutoff;
        }
        List<Ledger.Snapshot> observations=new ArrayList<>();
        for(Ledger.Snapshot s:data.snapshots)if(!s.at.isBefore(begin)&&!s.at.isAfter(finish))observations.add(s);
        observations.sort(Comparator.comparing(s->s.at));
        if(observations.size()==1&&observations.get(0).at.equals(finish))observations.clear();
        Map<Long,Ledger.Snapshot> last=new LinkedHashMap<>();
        Map<Long,LocalDateTime> firstAt=new HashMap<>(),lastAt=new HashMap<>();
        for(Ledger.Snapshot s:observations){
            r.available=true;if(r.start==null)r.start=s;r.end=s;
            if(r.through==null||s.at.isAfter(r.through))r.through=s.at;
            for(Map.Entry<Long,Long> balance:s.balances.entrySet()){
                long id=balance.getKey();Ledger.Snapshot prior=last.put(id,s);firstAt.putIfAbsent(id,s.at);lastAt.put(id,s.at);
                if(prior!=null){
                    Change change=new Change(id,prior.balances.get(id),balance.getValue(),prior.at,s.at);
                    result.changes.add(change);r.expense=Math.subtractExact(r.expense,change.delta());
                }
            }
        }
        // Linked cash adjustments use that account's own observed interval. Unassigned entries are counted once globally.
        LocalDateTime globalStart=r.start==null?null:r.start.at,globalEnd=r.end==null?null:r.end.at;
        for(Ledger.Entry e:data.entries){
            LocalDateTime start=e.accountId==0?globalStart:firstAt.get(e.accountId),end=e.accountId==0?globalEnd:lastAt.get(e.accountId);
            if(start!=null&&end!=null&&AccountCash.between(e.at,start,end,e.cashLinked)){
                if(Ledger.INCOME.equals(e.kind)||Ledger.INVEST_IN.equals(e.kind))result.adjustment=Math.addExact(result.adjustment,e.amount);
                if(Ledger.REIMBURSE.equals(e.kind)||Ledger.INVEST_OUT.equals(e.kind))result.adjustment=Math.subtractExact(result.adjustment,e.amount);
            }
            if(Ledger.REIMBURSE.equals(e.kind)){
                if(e.receipts.isEmpty()){
                    if(globalStart!=null&&globalEnd!=null&&Ledger.between(e.receivedAt,globalStart,globalEnd))result.adjustment=Math.addExact(result.adjustment,e.received);
                }else for(Ledger.Receipt receipt:e.receipts){
                    start=receipt.accountId==0?globalStart:firstAt.get(receipt.accountId);end=receipt.accountId==0?globalEnd:lastAt.get(receipt.accountId);
                    if(start!=null&&end!=null&&AccountCash.between(receipt.at,start,end,receipt.cashLinked))result.adjustment=Math.addExact(result.adjustment,receipt.amount);
                }
            }
        }
        for(InvestmentCash.Flow flow:InvestmentCash.flows(data)){
            LocalDateTime start=firstAt.get(flow.accountId),end=lastAt.get(flow.accountId);
            if(start!=null&&end!=null&&AccountCash.between(flow.at,start,end,true))result.adjustment=Math.addExact(result.adjustment,flow.delta);
        }
        r.expense=Math.addExact(r.expense,result.adjustment);
        if(!r.imported&&r.start!=null){r.baseline=true;for(LocalDateTime first:firstAt.values())if(!first.equals(month.atDay(1).atStartOfDay()))r.baseline=false;}
        if(r.through!=null){
            r.closed=r.through.equals(finish);
            for(LocalDateTime lastObservation:lastAt.values())if(!lastObservation.equals(finish))r.closed=false;
            r.days=r.through.equals(finish)?month.lengthOfMonth():r.through.getDayOfMonth();
        }
        // Full snapshots still permit the existing interval validation. Sparse observations cannot be compared as full totals.
        for(int i=1;i<observations.size();i++)if(!observations.get(i-1).partial&&!observations.get(i).partial
            &&Ledger.expenseBetween(observations.get(i-1),observations.get(i),data)<0)r.anomaly=true;
        r.anomaly=r.anomaly||r.expense<0;
        result.changes.sort(Comparator.comparing((Change c)->c.to).thenComparingLong(c->c.accountId));
        return result;
    }
    static void updateAccount(Ledger.Data data,long accountId,LocalDateTime at,long balance){
        Ledger.Account account=null;for(Ledger.Account a:data.accounts)if(a.id==accountId)account=a;
        if(account==null||account.deletedAt!=null)throw new IllegalArgumentException("账户不存在或已删除");
        if(at==null||at.getYear()<2000||at.isAfter(LocalDateTime.now())||balance<0||balance>Ledger.MAX_MONEY)throw new IllegalArgumentException("余额或更新时间无效");
        Ledger.Snapshot target=null;for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(at))target=s;
        if(target==null){target=new Ledger.Snapshot(data.nextId(),at,"");target.partial=true;data.snapshots.add(target);}
        target.balances.put(accountId,balance);Ledger.pullAccountsBack(data,target);data.sort();
        AccountCash.makeManual(data,accountId,at);
    }
    private MonthLedger(){}
}
