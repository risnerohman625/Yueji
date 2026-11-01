package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Monthly endpoint accounting. Intermediate estimates never introduce additional consumption. */
final class MonthLedger {
    static final class Change {
        final long accountId,before,after;final LocalDateTime from,to;
        Change(long id,long before,long after,LocalDateTime from,LocalDateTime to){accountId=id;this.before=before;this.after=after;this.from=from;this.to=to;}
        long delta(){return Math.subtractExact(after,before);}
    }
    static final class Result {
        final Ledger.Report report;final List<Change> changes=new ArrayList<>();Ledger.HistoryMonth history;LocalDateTime cutoff;long historical,adjustment;
        final List<Long> missingAccounts=new ArrayList<>();
        Result(YearMonth month){report=new Ledger.Report(month);}
    }
    static final class Window {
        Ledger.Snapshot start,end;boolean includeStart;
        Window(Ledger.Snapshot start,Ledger.Snapshot end,boolean includeStart){this.start=start;this.end=end;this.includeStart=includeStart;}
    }
    static boolean estimated(Ledger.Snapshot s,long id){return s.cashGenerated||s.cashAccounts.contains(id);}
    static Ledger.Snapshot corrected(Ledger.Snapshot source,long id,long delta){
        if(delta==0)return source;Ledger.Snapshot s=new Ledger.Snapshot(source.id,source.at,source.note);s.partial=true;s.cashGenerated=source.cashGenerated;s.cashAccounts.addAll(source.cashAccounts);s.balances.put(id,Math.subtractExact(source.balances.get(id),delta));return s;
    }
    static boolean covers(Window w,LocalDateTime at,boolean post,LocalDateTime begin,LocalDateTime finish){
        if(w==null||at==null||!Ledger.between(at,begin,finish))return false;
        boolean left=w.includeStart?!at.isBefore(w.start.at):post?at.isAfter(w.start.at):!at.isBefore(w.start.at);
        return left&&(post?!at.isAfter(w.end.at):at.isBefore(w.end.at));
    }
    static Result calculate(Ledger.Data data,YearMonth month){
        Result result=new Result(month);Ledger.Report r=result.report;LocalDateTime begin=month.atDay(1).atStartOfDay(),finish=month.plusMonths(1).atDay(1).atStartOfDay();
        for(Ledger.HistoryMonth h:data.history)if(h.month.equals(month))result.history=h;
        if(result.history!=null){
            result.cutoff=Ledger.historyCutoff(data,result.history);result.historical=result.history.expense;
            if(result.history.includesAdvances)for(Ledger.Entry e:data.entries)if(Ledger.REIMBURSE.equals(e.kind)&&Ledger.between(e.at,begin,result.cutoff))result.historical=Math.subtractExact(result.historical,e.amount);
            r.expense=result.historical;r.imported=r.available=r.baseline=true;r.through=result.cutoff;
            if(result.cutoff.equals(finish)){r.closed=!finish.isAfter(LocalDateTime.now());r.days=r.closed?month.lengthOfMonth():LocalDate.now().getDayOfMonth();if(!r.closed)r.through=LocalDateTime.now();r.anomaly=r.expense<0;return result;}
        }
        Map<Long,TreeMap<LocalDateTime,Ledger.Snapshot>> records=new LinkedHashMap<>();Map<Long,Ledger.Snapshot> openings=new HashMap<>();
        for(Ledger.Account a:data.accounts)if(a.openingKnown&&a.created!=null){Ledger.Snapshot s=new Ledger.Snapshot(0,a.created,"");s.partial=true;s.balances.put(a.id,a.opening);openings.put(a.id,s);records.computeIfAbsent(a.id,k->new TreeMap<>()).put(a.created,s);}
        for(Ledger.Snapshot s:data.snapshots)for(long id:s.balances.keySet())if(AccountCash.actual(s,id))records.computeIfAbsent(id,k->new TreeMap<>()).put(s.at,s);
        List<AccountCash.Flow> flows=AccountCash.flows(data);Map<Long,Long> atBegin=new HashMap<>(),atEnd=new HashMap<>();
        for(AccountCash.Flow f:flows){if(f.at.equals(begin))atBegin.merge(f.accountId,f.delta,Math::addExact);if(f.at.equals(finish))atEnd.merge(f.accountId,f.delta,Math::addExact);}
        Map<Long,Window> windows=new LinkedHashMap<>();boolean baseline=true,closed=true;LocalDateTime previousBegin=month.minusMonths(1).atDay(1).atStartOfDay();
        for(Map.Entry<Long,TreeMap<LocalDateTime,Ledger.Snapshot>> account:records.entrySet()){
            long id=account.getKey();TreeMap<LocalDateTime,Ledger.Snapshot> list=account.getValue();Map.Entry<LocalDateTime,Ledger.Snapshot> last=list.floorEntry(finish);if(last==null||last.getKey().isBefore(begin))continue;
            Ledger.Snapshot end=last.getValue(),start=null;boolean includeStart=false,accountBaseline=true;
            if(result.history!=null){Map.Entry<LocalDateTime,Ledger.Snapshot> first=list.ceilingEntry(result.cutoff);if(first!=null&&!first.getKey().isAfter(finish))start=first.getValue();}
            else{
                start=list.get(begin);includeStart=start!=null;
                if(start==null){Map.Entry<LocalDateTime,Ledger.Snapshot> prior=list.lowerEntry(begin);if(prior!=null&&!prior.getKey().isBefore(previousBegin)){start=prior.getValue();r.boundaryWarning=true;}}
                if(start==null){Map.Entry<LocalDateTime,Ledger.Snapshot> first=list.ceilingEntry(begin);if(first!=null&&!first.getKey().isAfter(finish))start=first.getValue();accountBaseline=false;}
            }
            if(start==null||start.at.isAfter(end.at)||start.at.equals(finish))continue;
            baseline&=accountBaseline;
            Ledger.Snapshot opening=openings.get(id);boolean legacyOpening=opening!=null&&opening.at.equals(start.at)&&estimated(start,id);
            if(legacyOpening){start=opening;includeStart=true;}else if(includeStart)start=corrected(start,id,atBegin.getOrDefault(id,0L));
            if(end.at.equals(finish))end=corrected(end,id,atEnd.getOrDefault(id,0L));
            Window w=new Window(start,end,includeStart);windows.put(id,w);r.available=true;
            if(r.start==null||start.at.isBefore(r.start.at))r.start=start;if(r.end==null||end.at.isAfter(r.end.at))r.end=end;
            if(r.through==null||end.at.isAfter(r.through))r.through=end.at;
            if(!start.at.equals(end.at)||start.balances.get(id).longValue()!=end.balances.get(id).longValue()){
                Change change=new Change(id,start.balances.get(id),end.balances.get(id),start.at,end.at);result.changes.add(change);r.expense=Math.subtractExact(r.expense,change.delta());
            }
            r.estimated|=estimated(start,id)||estimated(end,id);closed&=end.at.equals(finish);
        }
        if(result.history==null)for(Ledger.Account a:data.accounts)if((a.created==null||a.created.isBefore(finish))&&(a.deletedAt==null||a.deletedAt.isAfter(begin))&&!windows.containsKey(a.id))result.missingAccounts.add(a.id);
        r.incomplete=r.available&&!result.missingAccounts.isEmpty();
        Window global=r.start==null?null:new Window(r.start,r.end,!r.start.at.isAfter(begin));
        LocalDateTime adjustmentBegin=result.history==null?begin:result.cutoff;
        for(Ledger.Entry e:data.entries){
            Window w=e.accountId==0?global:windows.get(e.accountId);
            if(covers(w,e.at,e.cashLinked||e.nearestCash,adjustmentBegin,finish)){
                if(Ledger.INCOME.equals(e.kind)||Ledger.INVEST_IN.equals(e.kind))result.adjustment=Math.addExact(result.adjustment,e.amount);
                if(Ledger.REIMBURSE.equals(e.kind)||Ledger.INVEST_OUT.equals(e.kind))result.adjustment=Math.subtractExact(result.adjustment,e.amount);
            }
            if(Ledger.REIMBURSE.equals(e.kind)){
                if(e.receipts.isEmpty()){if(covers(global,e.receivedAt,false,adjustmentBegin,finish))result.adjustment=Math.addExact(result.adjustment,e.received);}
                else for(Ledger.Receipt receipt:e.receipts)if(covers(receipt.accountId==0?global:windows.get(receipt.accountId),receipt.at,receipt.cashLinked,adjustmentBegin,finish))result.adjustment=Math.addExact(result.adjustment,receipt.amount);
            }
        }
        for(InvestmentCash.Flow f:InvestmentCash.flows(data))if(covers(windows.get(f.accountId),f.at,true,adjustmentBegin,finish))result.adjustment=Math.addExact(result.adjustment,f.delta);
        for(AccountCash.Flow f:flows)if(f.transfer&&covers(windows.get(f.accountId),f.at,true,adjustmentBegin,finish))result.adjustment=Math.addExact(result.adjustment,f.delta);
        r.expense=Math.addExact(r.expense,result.adjustment);if(!r.imported)r.baseline=baseline&&!windows.isEmpty();
        if(r.through!=null){r.closed=closed&&!windows.isEmpty();r.days=r.through.equals(finish)?month.lengthOfMonth():r.through.getDayOfMonth();}
        r.anomaly=r.expense<0;result.changes.sort(Comparator.comparing((Change c)->c.to).thenComparingLong(c->c.accountId));return result;
    }
    static void updateAccount(Ledger.Data data,long accountId,LocalDateTime at,long balance){
        updateAccount(data,accountId,at,balance,"用户手动更新余额");
    }
    static void updateAccount(Ledger.Data data,long accountId,LocalDateTime at,long balance,String note){
        Ledger.Account account=null;for(Ledger.Account a:data.accounts)if(a.id==accountId)account=a;
        if(account==null||account.deletedAt!=null)throw new IllegalArgumentException("账户不存在或已删除");
        if(at==null||at.getYear()<2000||at.isAfter(LocalDateTime.now())||balance<0||balance>Ledger.MAX_MONEY)throw new IllegalArgumentException("余额或更新时间无效");
        Ledger.Snapshot target=null;for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(at))target=s;
        if(target==null){target=new Ledger.Snapshot(data.nextId(),at,"");target.partial=true;data.snapshots.add(target);}
        target.balances.put(accountId,balance);LocalDateTime before=account.created;Ledger.pullAccountsBack(data,target);
        if(account.created.equals(at)&&account.openingKnown)account.opening=balance;
        if(!Objects.equals(before,account.created)){account.openingKnown=true;AccountRecords.sparseBefore(data,account);}data.sort();
        AccountCash.makeManual(data,accountId,at);
        target.notes.put(accountId,note==null?"":note.trim());
    }
    private MonthLedger(){}
}
