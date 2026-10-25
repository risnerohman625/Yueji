package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Owned balance observations for new in-app cash records; absolute manual observations remain authoritative. */
final class AccountCash {
    static final class Flow {
        final long entryId,accountId,delta;final LocalDateTime at;
        Flow(long entryId,long accountId,long delta,LocalDateTime at){this.entryId=entryId;this.accountId=accountId;this.delta=delta;this.at=at;}
    }
    static boolean between(LocalDateTime at,LocalDateTime start,LocalDateTime end,boolean linked){
        // Generated observations are POST-transaction balances. The event belongs to (start,end].
        return linked?at!=null&&at.isAfter(start)&&!at.isAfter(end):Ledger.between(at,start,end);
    }
    static List<Flow> flows(Ledger.Data data){
        List<Flow> result=new ArrayList<>();
        for(Ledger.Entry e:data.entries){
            if(e.cashLinked){
                if(!Ledger.INCOME.equals(e.kind)&&!Ledger.REIMBURSE.equals(e.kind)&&!Ledger.INVEST_OUT.equals(e.kind)&&!Ledger.INVEST_IN.equals(e.kind))throw new IllegalArgumentException("自动账户记账类型无效");
                requireAccount(data,e.accountId,e.at);
                result.add(new Flow(e.id,e.accountId,Ledger.INCOME.equals(e.kind)||Ledger.INVEST_IN.equals(e.kind)?e.amount:-e.amount,e.at));
            }
            for(Ledger.Receipt receipt:e.receipts)if(receipt.cashLinked){
                if(!Ledger.REIMBURSE.equals(e.kind))throw new IllegalArgumentException("只有报销可以添加到账");
                requireAccount(data,receipt.accountId,receipt.at);result.add(new Flow(e.id,receipt.accountId,receipt.amount,receipt.at));
            }
        }
        for(InvestmentCash.Flow f:InvestmentCash.flows(data)){
            requireAccount(data,f.accountId,f.at);result.add(new Flow(f.id,f.accountId,f.delta,f.at));
        }
        result.sort(Comparator.comparing((Flow f)->f.at).thenComparingLong(f->f.entryId).thenComparingLong(f->f.accountId));return result;
    }
    static Ledger.Account requireAccount(Ledger.Data data,long id,LocalDateTime at){
        Ledger.Account found=null;for(Ledger.Account a:data.accounts)if(a.id==id)found=a;
        if(found==null)throw new IllegalArgumentException("请选择关联账户");
        if(at==null||found.created==null||at.isBefore(found.created))throw new IllegalArgumentException("记录早于「"+found.name+"」的开户余额，请先补录该账户在发生时间之前的余额");
        if(found.deletedAt!=null&&!at.isBefore(found.deletedAt))throw new IllegalArgumentException("记录时此账户已删除，请选择有效账户");
        return found;
    }
    static void rebuild(Ledger.Data data){
        List<Flow> flows=flows(data);Map<LocalDateTime,Long> oldIds=new HashMap<>();long next=data.nextId();
        // Strip only cells owned by this feature. Never undo an absolute balance entered by the user.
        for(Iterator<Ledger.Snapshot> it=data.snapshots.iterator();it.hasNext();){
            Ledger.Snapshot s=it.next();
            if(s.cashGenerated){oldIds.put(s.at,s.id);next=Math.max(next,s.id+1);it.remove();}
            else{
                for(long account:s.cashAccounts)s.balances.remove(account);s.cashAccounts.clear();
                if(s.balances.isEmpty())it.remove();
            }
        }
        SortedMap<LocalDateTime,Ledger.Snapshot> manual=new TreeMap<>();
        for(Ledger.Snapshot s:data.snapshots)manual.put(s.at,s);
        SortedMap<LocalDateTime,List<Flow>> groups=new TreeMap<>();
        for(Flow f:flows)groups.computeIfAbsent(f.at,k->new ArrayList<>()).add(f);
        SortedSet<LocalDateTime> times=new TreeSet<>(manual.keySet());times.addAll(groups.keySet());
        Map<Long,Long> state=new HashMap<>();for(Ledger.Account a:data.accounts)state.put(a.id,a.opening);
        for(LocalDateTime at:times){
            Ledger.Snapshot s=manual.get(at);Set<Long> observed=s==null?Collections.emptySet():new HashSet<>(s.balances.keySet());
            if(s!=null)state.putAll(s.balances);
            List<Flow> events=groups.get(at);if(events==null)continue;
            Map<Long,Long> deltas=new LinkedHashMap<>();for(Flow f:events)deltas.merge(f.accountId,f.delta,Math::addExact);
            for(Map.Entry<Long,Long> event:deltas.entrySet()){
                long account=event.getKey();if(observed.contains(account))continue;
                long amount=Math.addExact(state.get(account),event.getValue());
                if(amount<0||amount>Ledger.MAX_MONEY)throw new IllegalArgumentException("自动记账后「"+data.accountName(account)+"」余额超出范围，请核对垫付金额或先补录实际余额");
                if(s==null){
                    s=new Ledger.Snapshot(oldIds.containsKey(at)?oldIds.get(at):next++,at,"收支 / 投资账户自动记账");
                    s.partial=true;s.cashGenerated=true;data.snapshots.add(s);
                }
                s.balances.put(account,amount);s.cashAccounts.add(account);state.put(account,amount);
            }
        }
        data.sort();
    }
    static List<Ledger.Entry> owners(Ledger.Data data,Ledger.Snapshot s,long account){
        Set<Long> ids=new LinkedHashSet<>();for(Flow f:flows(data))if(f.at.equals(s.at)&&(account==0||account==f.accountId))ids.add(f.entryId);
        List<Ledger.Entry> result=new ArrayList<>();for(Ledger.Entry e:data.entries)if(ids.contains(e.id))result.add(e);return result;
    }
    static void makeManual(Ledger.Data data,long account,LocalDateTime at){
        for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(at)){
            if(s.cashGenerated){s.cashGenerated=false;s.note="已核对实际余额";for(long id:s.balances.keySet())if(id!=account)s.cashAccounts.add(id);}
            s.cashAccounts.remove(account);
        }
    }
    private AccountCash(){}
}
