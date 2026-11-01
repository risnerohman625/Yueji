package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Account-cell edits never remove observations belonging to another account or their source transactions. */
final class AccountRecords {
    static Ledger.Account account(Ledger.Data data,long id){
        for(Ledger.Account a:data.accounts)if(a.id==id)return a;
        throw new IllegalArgumentException("账户不存在");
    }
    static void sparseBefore(Ledger.Data data,Ledger.Account account){
        for(Ledger.Snapshot s:data.snapshots)if(!s.at.isBefore(account.created)&&!s.balances.containsKey(account.id))s.partial=true;
    }
    static List<Ledger.Snapshot> rows(Ledger.Data data,Ledger.Account account){
        List<Ledger.Snapshot> result=new ArrayList<>();boolean initial=false;
        for(Ledger.Snapshot s:data.snapshots)if(s.balances.containsKey(account.id)){result.add(s);if(s.at.equals(account.created))initial=true;}
        if(account.openingKnown&&!initial){Ledger.Snapshot s=new Ledger.Snapshot(0,account.created,"");s.partial=true;s.balances.put(account.id,account.opening);result.add(s);}
        result.sort(Comparator.comparing((Ledger.Snapshot s)->s.at).reversed());return result;
    }
    static void setOpening(Ledger.Data data,long id,LocalDateTime at,long amount){
        Ledger.Account a=account(data,id);
        if(at==null||at.getYear()<2000||at.isAfter(LocalDateTime.now())||amount<0||amount>Ledger.MAX_MONEY)throw new IllegalArgumentException("启用时间或初始余额无效");
        LocalDateTime old=a.created;
        if(!at.equals(old))for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(at)&&s.balances.containsKey(id))throw new IllegalArgumentException("此账户在目标时刻已有余额记录，请先调整该条记录");
        for(Ledger.Snapshot s:data.snapshots)if(s.balances.containsKey(id)&&!s.at.equals(old)&&s.at.isBefore(at))throw new IllegalArgumentException("启用时间不能晚于已有余额记录，请先调整较早的记录");
        for(AccountCash.Flow f:AccountCash.flows(data))if(f.accountId==id&&f.at.isBefore(at))throw new IllegalArgumentException("启用时间不能晚于已关联的收支或投资交易");
        removeCell(data,a,old);a.created=at;a.opening=amount;a.openingKnown=true;
        sparseBefore(data,a);MonthLedger.updateAccount(data,id,at,amount);
    }
    static void edit(Ledger.Data data,long id,LocalDateTime from,LocalDateTime to,long amount){
        Ledger.Account a=account(data,id);
        if(a.openingKnown&&from.equals(a.created)){setOpening(data,id,to,amount);return;}
        if(!from.equals(to))for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(to)&&s.balances.containsKey(id))throw new IllegalArgumentException("此账户在目标时刻已有余额记录，请编辑该条记录");
        removeCell(data,a,from);MonthLedger.updateAccount(data,id,to,amount);
    }
    static void delete(Ledger.Data data,long id,LocalDateTime at){
        Ledger.Account a=account(data,id);removeCell(data,a,at);
        if(at.equals(a.created)){a.openingKnown=false;a.opening=0;}
    }
    private static void removeCell(Ledger.Data data,Ledger.Account a,LocalDateTime at){
        // Suppression survives rebuild/backup; the linked income or investment itself is not deleted.
        a.skippedCashObservations.add(at);
        for(Iterator<Ledger.Snapshot> it=data.snapshots.iterator();it.hasNext();){Ledger.Snapshot s=it.next();if(!s.at.equals(at))continue;
            s.balances.remove(a.id);s.cashAccounts.remove(a.id);s.partial=true;if(s.balances.isEmpty())it.remove();
        }
    }
    static boolean known(Ledger.Data data,Ledger.Account a){
        if(a.openingKnown)return true;for(Ledger.Snapshot s:data.snapshots)if(s.balances.containsKey(a.id))return true;return false;
    }
    static Long balanceAt(Ledger.Data data,Ledger.Account a,LocalDateTime through){
        Long amount=a.openingKnown?a.opening:null;LocalDateTime latest=null;
        for(Ledger.Snapshot s:data.snapshots)if((through==null||!s.at.isAfter(through))&&s.balances.containsKey(a.id)&&(latest==null||!s.at.isBefore(latest))){amount=s.balances.get(a.id);latest=s.at;}
        if(amount==null)return null;
        boolean pending=false;for(LocalDateTime at:a.skippedCashObservations)if((latest==null?!at.isBefore(a.created):at.isAfter(latest))&&(through==null||!at.isAfter(through))){pending=true;break;}
        // Hiding a generated observation must not undo its still-linked real transaction.
        if(pending)for(AccountCash.Flow f:AccountCash.flows(data))if(f.accountId==a.id&&a.skippedCashObservations.contains(f.at)&&(latest==null?!f.at.isBefore(a.created):f.at.isAfter(latest))&&(through==null||!f.at.isAfter(through)))amount=Math.addExact(amount,f.delta);
        return amount;
    }
    private AccountRecords(){}
}
