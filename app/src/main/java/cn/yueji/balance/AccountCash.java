package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Linked cash amounts are source-backed activities, never inferred balance observations. */
final class AccountCash {
    static final class Flow {
        final long entryId,accountId,delta;final LocalDateTime at;final boolean transfer;final String key,source;
        Flow(long id,long account,long delta,LocalDateTime at,String key,boolean transfer,String source){entryId=id;accountId=account;this.delta=delta;this.at=at;this.key=key;this.transfer=transfer;this.source=source;}
    }
    static boolean between(LocalDateTime at,LocalDateTime start,LocalDateTime end,boolean linked){return linked?at!=null&&at.isAfter(start)&&!at.isAfter(end):Ledger.between(at,start,end);}
    static void receiptIds(Ledger.Data data){
        Set<Long> used=new HashSet<>();long next=data.nextId();
        for(Ledger.Entry e:data.entries)for(Ledger.Receipt r:e.receipts)if(r.id<0||r.id>0&&!used.add(r.id))throw new IllegalArgumentException("报销到账标识重复或无效");
        for(Ledger.Entry e:data.entries)for(Ledger.Receipt r:e.receipts)if(r.id==0){while(used.contains(next))next++;r.id=next++;used.add(r.id);}
    }
    static List<Flow> flows(Ledger.Data data){
        receiptIds(data);List<Flow> result=new ArrayList<>();
        for(Ledger.Entry e:data.entries){
            if(e.cashLinked){
                if(!Arrays.asList(Ledger.INCOME,Ledger.LARGE,Ledger.REIMBURSE,Ledger.INVEST_OUT,Ledger.INVEST_IN).contains(e.kind))throw new IllegalArgumentException("账户记账类型无效");
                requireAccount(data,e.accountId,e.at);boolean credit=Ledger.INCOME.equals(e.kind)||Ledger.INVEST_IN.equals(e.kind);
                String label=Ledger.INCOME.equals(e.kind)?"收入":Ledger.LARGE.equals(e.kind)?"大宗支出":Ledger.REIMBURSE.equals(e.kind)?"报销垫付":"投资划转";
                result.add(new Flow(e.id,e.accountId,credit?e.amount:-e.amount,e.at,"entry:"+e.id,false,label+" · "+e.title));
            }
            for(Ledger.Receipt r:e.receipts)if(r.cashLinked){
                if(!Ledger.REIMBURSE.equals(e.kind))throw new IllegalArgumentException("只有报销可以添加到账");requireAccount(data,r.accountId,r.at);
                result.add(new Flow(e.id,r.accountId,r.amount,r.at,"receipt:"+r.id,false,"报销到账 · "+e.title));
            }
        }
        for(InvestmentCash.Flow f:InvestmentCash.flows(data)){requireAccount(data,f.accountId,f.at);result.add(new Flow(f.id,f.accountId,f.delta,f.at,f.key,false,f.source));}
        for(Ledger.Transfer t:data.transfers){TransferLedger.check(data,t);String source=TransferLedger.description(data,t)+(t.note.isEmpty()?"":" · "+t.note);result.add(new Flow(t.id,t.fromId,-t.amount,t.at,"transfer:"+t.id+":out",true,source));result.add(new Flow(t.id,t.toId,t.amount,t.at,"transfer:"+t.id+":in",true,source));}
        result.sort(Comparator.comparing((Flow f)->f.at).thenComparing(f->f.key).thenComparingLong(f->f.accountId));return result;
    }
    static Ledger.Account requireAccount(Ledger.Data data,long id,LocalDateTime at){
        Ledger.Account found=null;for(Ledger.Account a:data.accounts)if(a.id==id)found=a;
        if(found==null)throw new IllegalArgumentException("请选择关联账户");
        if(at==null||found.created==null||at.isBefore(found.created))throw new IllegalArgumentException("记录早于「"+found.name+"」的启用时间，请先在编辑账户中核对启用时间和初始余额");
        if(found.deletedAt!=null&&!at.isBefore(found.deletedAt))throw new IllegalArgumentException("记录时此账户已删除，请选择有效账户");return found;
    }
    static boolean actual(Ledger.Snapshot s,long id){return !s.cashGenerated&&!s.cashAccounts.contains(id);}
    static void stripLegacy(Ledger.Data data){
        // Old generated cells are disposable caches. Keep all genuine cells and user notes.
        for(Iterator<Ledger.Snapshot> it=data.snapshots.iterator();it.hasNext();){Ledger.Snapshot s=it.next();
            if(s.cashGenerated){it.remove();continue;}
            for(long id:s.cashAccounts){s.balances.remove(id);s.notes.remove(id);}s.cashAccounts.clear();s.sources.clear();
            if(s.balances.isEmpty())it.remove();
        }
        receiptIds(data);data.sort();
    }
    static void rebuild(Ledger.Data data){
        stripLegacy(data);List<Flow> all=flows(data);
        for(Ledger.Transfer t:data.transfers)if(AccountRecords.balanceAt(data,requireAccount(data,t.fromId,t.at),t.at,all)==null||AccountRecords.balanceAt(data,requireAccount(data,t.toId,t.at),t.at,all)==null)throw new IllegalArgumentException("转账前请先补录双方实际余额");
        for(Ledger.Account a:data.accounts){Long value=AccountRecords.balanceAt(data,a,null,all);if(value!=null&&(value<0||value>Ledger.MAX_MONEY))throw new IllegalArgumentException("「"+a.name+"」账面余额超出范围，请核对金额或补录实际余额");}
    }
    static List<Flow> rows(Ledger.Data data,Ledger.Account a){List<Flow> result=new ArrayList<>();for(Flow f:flows(data))if(f.accountId==a.id&&!a.hiddenActivityKeys.contains(f.key))result.add(f);return result;}
    static void hide(Ledger.Data data,long account,String key){AccountRecords.account(data,account).hiddenActivityKeys.add(key);}
    static void restore(Ledger.Data data,String key){for(Ledger.Account a:data.accounts)a.hiddenActivityKeys.remove(key);}
    static void restoreEntry(Ledger.Data data,Ledger.Entry e){restore(data,"entry:"+e.id);for(Ledger.Receipt r:e.receipts)restore(data,"receipt:"+r.id);}
    static List<Ledger.Entry> owners(Ledger.Data data,Ledger.Snapshot s,long account){Set<Long> ids=new LinkedHashSet<>();for(Flow f:flows(data))if(f.at.equals(s.at)&&(account==0||account==f.accountId))ids.add(f.entryId);List<Ledger.Entry> result=new ArrayList<>();for(Ledger.Entry e:data.entries)if(ids.contains(e.id))result.add(e);return result;}
    static void makeManual(Ledger.Data data,long account,LocalDateTime at){for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(at)){if(s.cashGenerated){s.cashGenerated=false;s.note="";for(long id:s.balances.keySet())if(id!=account)s.cashAccounts.add(id);}s.cashAccounts.remove(account);s.sources.remove(account);}}
    private AccountCash(){}
}
