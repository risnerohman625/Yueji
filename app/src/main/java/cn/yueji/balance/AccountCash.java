package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Derived cash observations never overwrite an absolute balance supplied by the user. */
final class AccountCash {
    static final class Flow {
        final long entryId,accountId,delta;final LocalDateTime at;final boolean nearest,transfer;final String source;
        Flow(long id,long account,long delta,LocalDateTime at){this(id,account,delta,at,false,false,"");}
        Flow(long id,long account,long delta,LocalDateTime at,boolean nearest,boolean transfer,String source){entryId=id;accountId=account;this.delta=delta;this.at=at;this.nearest=nearest;this.transfer=transfer;this.source=source;}
    }
    static boolean between(LocalDateTime at,LocalDateTime start,LocalDateTime end,boolean linked){return linked?at!=null&&at.isAfter(start)&&!at.isAfter(end):Ledger.between(at,start,end);}
    static List<Flow> flows(Ledger.Data data){
        List<Flow> result=new ArrayList<>();
        for(Ledger.Entry e:data.entries){
            if(e.cashLinked){
                if(!Arrays.asList(Ledger.INCOME,Ledger.LARGE,Ledger.REIMBURSE,Ledger.INVEST_OUT,Ledger.INVEST_IN).contains(e.kind))throw new IllegalArgumentException("自动账户记账类型无效");
                requireAccount(data,e.accountId,e.at);boolean credit=Ledger.INCOME.equals(e.kind)||Ledger.INVEST_IN.equals(e.kind);
                String label=Ledger.INCOME.equals(e.kind)?"收入":Ledger.LARGE.equals(e.kind)?"大宗支出":Ledger.REIMBURSE.equals(e.kind)?"报销垫付":"投资划转";
                result.add(new Flow(e.id,e.accountId,credit?e.amount:-e.amount,e.at,e.nearestCash,false,label+"："+e.title+" "+Ledger.decimal(e.amount)+"元"));
            }
            for(Ledger.Receipt r:e.receipts)if(r.cashLinked){
                if(!Ledger.REIMBURSE.equals(e.kind))throw new IllegalArgumentException("只有报销可以添加到账");requireAccount(data,r.accountId,r.at);
                result.add(new Flow(e.id,r.accountId,r.amount,r.at,false,false,"报销到账："+e.title+" "+Ledger.decimal(r.amount)+"元"));
            }
        }
        for(InvestmentCash.Flow f:InvestmentCash.flows(data)){requireAccount(data,f.accountId,f.at);result.add(new Flow(f.id,f.accountId,f.delta,f.at,false,false,"投资划转 "+(f.delta>=0?"+":"")+Ledger.decimal(f.delta)+"元"));}
        for(Ledger.Transfer t:data.transfers){TransferLedger.check(data,t);String source=TransferLedger.description(data,t)+(t.note.isEmpty()?"":" · "+t.note);result.add(new Flow(t.id,t.fromId,-t.amount,t.at,false,true,source));result.add(new Flow(t.id,t.toId,t.amount,t.at,false,true,source));}
        result.sort(Comparator.comparing((Flow f)->f.at).thenComparingLong(f->f.entryId).thenComparingLong(f->f.accountId));return result;
    }
    static Ledger.Account requireAccount(Ledger.Data data,long id,LocalDateTime at){
        Ledger.Account found=null;for(Ledger.Account a:data.accounts)if(a.id==id)found=a;
        if(found==null)throw new IllegalArgumentException("请选择关联账户");
        if(at==null||found.created==null||at.isBefore(found.created))throw new IllegalArgumentException("记录早于「"+found.name+"」的启用时间，请先在编辑账户中核对启用时间和初始余额");
        if(found.deletedAt!=null&&!at.isBefore(found.deletedAt))throw new IllegalArgumentException("记录时此账户已删除，请选择有效账户");return found;
    }
    static final class Timeline {
        final TreeMap<LocalDateTime,Long> actual=new TreeMap<>(),deltas=new TreeMap<>(),prefix=new TreeMap<>();
        final Set<LocalDateTime> openingBeforeFlows=new HashSet<>();
        long total(LocalDateTime at){Map.Entry<LocalDateTime,Long> e=prefix.floorEntry(at);return e==null?0:e.getValue();}
        void index(){long sum=0;for(Map.Entry<LocalDateTime,Long> e:deltas.entrySet()){sum=Math.addExact(sum,e.getValue());prefix.put(e.getKey(),sum);}}
        Map.Entry<LocalDateTime,Long> anchor(LocalDateTime at,boolean nearest){
            Map.Entry<LocalDateTime,Long> before=actual.floorEntry(at),after=nearest?actual.ceilingEntry(at):null;
            if(before==null)return after;if(after==null)return before;
            return Duration.between(at,after.getKey()).compareTo(Duration.between(before.getKey(),at))<0?after:before;
        }
        long value(LocalDateTime at,Map.Entry<LocalDateTime,Long> anchor){long delta=Math.subtractExact(total(at),total(anchor.getKey()));if(openingBeforeFlows.contains(anchor.getKey()))delta=Math.addExact(delta,deltas.getOrDefault(anchor.getKey(),0L));return Math.addExact(anchor.getValue(),delta);}
    }
    static void rebuild(Ledger.Data data){
        List<Flow> all=flows(data);Map<LocalDateTime,Long> oldIds=new HashMap<>();long next=data.nextId();
        for(Iterator<Ledger.Snapshot> it=data.snapshots.iterator();it.hasNext();){Ledger.Snapshot s=it.next();
            if(s.cashGenerated){oldIds.put(s.at,s.id);it.remove();}
            else{for(long id:s.cashAccounts){s.balances.remove(id);s.notes.remove(id);}s.cashAccounts.clear();s.sources.clear();if(s.balances.isEmpty())it.remove();}
        }
        Map<Long,Timeline> timelines=new HashMap<>();for(Ledger.Account a:data.accounts){Timeline line=new Timeline();if(a.openingKnown&&a.created!=null){line.actual.put(a.created,a.opening);line.openingBeforeFlows.add(a.created);}timelines.put(a.id,line);}
        TreeMap<LocalDateTime,Ledger.Snapshot> manual=new TreeMap<>();
        for(Ledger.Snapshot s:data.snapshots){manual.put(s.at,s);for(Map.Entry<Long,Long> b:s.balances.entrySet()){Timeline line=timelines.get(b.getKey());if(line!=null){line.actual.put(s.at,b.getValue());line.openingBeforeFlows.remove(s.at);}}}
        TreeMap<LocalDateTime,Map<Long,List<Flow>>> groups=new TreeMap<>();
        for(Flow f:all){timelines.get(f.accountId).deltas.merge(f.at,f.delta,Math::addExact);groups.computeIfAbsent(f.at,k->new LinkedHashMap<>()).computeIfAbsent(f.accountId,k->new ArrayList<>()).add(f);}
        for(Timeline line:timelines.values())line.index();
        for(Map.Entry<LocalDateTime,Map<Long,List<Flow>>> group:groups.entrySet()){
            LocalDateTime at=group.getKey();Ledger.Snapshot s=manual.get(at);
            for(Map.Entry<Long,List<Flow>> cells:group.getValue().entrySet()){
                long id=cells.getKey();List<Flow> events=cells.getValue();StringJoiner source=new StringJoiner("；");int shown=0;for(Flow f:events)if(shown++<4)source.add(f.source.length()>300?f.source.substring(0,300)+"…":f.source);if(events.size()>4)source.add("共"+events.size()+"笔资金变动");
                if(s!=null&&s.balances.containsKey(id)){s.sources.put(id,source.toString()+"（同刻实测余额为准）");continue;}
                Timeline line=timelines.get(id);boolean nearest=events.stream().anyMatch(f->f.nearest);Map.Entry<LocalDateTime,Long> anchor=line.anchor(at,nearest);
                if(anchor==null){if(events.stream().anyMatch(f->f.transfer))throw new IllegalArgumentException("请先补录「"+data.accountName(id)+"」在转账之前的实际余额");continue;}
                long amount=line.value(at,anchor);if(amount<0||amount>Ledger.MAX_MONEY)throw new IllegalArgumentException("自动记账后「"+data.accountName(id)+"」余额超出范围，请核对交易金额或补录实际余额");
                if(AccountRecords.account(data,id).skippedCashObservations.contains(at))continue;
                if(s==null){s=new Ledger.Snapshot(oldIds.containsKey(at)?oldIds.get(at):next++,at,"自动记账（推算）");s.partial=true;s.cashGenerated=true;data.snapshots.add(s);}
                s.balances.put(id,amount);s.cashAccounts.add(id);s.sources.put(id,source+"\n参考 "+Ledger.csvMinute(anchor.getKey())+(anchor.getKey().isAfter(at)?" 后续实测逆推":" 之前实测正推"));
            }
        }
        data.sort();
    }
    static List<Ledger.Entry> owners(Ledger.Data data,Ledger.Snapshot s,long account){Set<Long> ids=new LinkedHashSet<>();for(Flow f:flows(data))if(f.at.equals(s.at)&&(account==0||account==f.accountId))ids.add(f.entryId);List<Ledger.Entry> result=new ArrayList<>();for(Ledger.Entry e:data.entries)if(ids.contains(e.id))result.add(e);return result;}
    static void makeManual(Ledger.Data data,long account,LocalDateTime at){for(Ledger.Snapshot s:data.snapshots)if(s.at.equals(at)){if(s.cashGenerated){s.cashGenerated=false;s.note="";for(long id:s.balances.keySet())if(id!=account)s.cashAccounts.add(id);}s.cashAccounts.remove(account);s.sources.remove(account);}}
    private AccountCash(){}
}
