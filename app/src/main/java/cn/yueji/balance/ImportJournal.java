package cn.yueji.balance;

import org.json.*;
import java.time.*;
import java.util.*;

/** Import ownership is recorded at commit, never inferred from dates or matching amounts. */
final class ImportJournal {
    static final String RECORDS="records",FUNDS="funds";
    static final class Batch {
        long id;LocalDateTime at;String module="",source="";boolean deleted;
        final List<Item> items=new ArrayList<>();
    }
    static final class Item {
        String kind="",ref="",category="",title="",detail="",before="",after="";
        boolean deleted;
    }
    static final class Removal {int removed,preserved,absent;}
    static Batch record(Ledger.Data before,Ledger.Data after,String module,String source){
        Batch batch=new Batch();batch.id=after.nextId();batch.at=LocalDateTime.now();batch.module=module;batch.source=source;
        if(module.equals(RECORDS)){
            Set<Long> ids=new HashSet<>();for(Ledger.Entry e:before.entries)ids.add(e.id);
            for(Ledger.Entry e:after.entries)if(!ids.contains(e.id)){
                Item item=item("entry",""+e.id,e.kind,e.title,
                    Ledger.csvMinute(e.at)+" · ¥ "+Ledger.decimal(e.amount)+"\n账户："+after.accountName(e.accountId)+
                    (e.category.isEmpty()?"":"\n收入类别："+e.category)+(e.note.isEmpty()?"":"\n备注："+e.note));
                if(e.kind.equals(Ledger.REIMBURSE)){
                    item.detail+="\n"+e.reimbursementStatus()+" · 已到账 ¥ "+Ledger.decimal(e.receivedTotal());
                    for(Ledger.Receipt receipt:e.receipts)item.detail+="\n到账 "+Ledger.csvMinute(receipt.at)+" · ¥ "+Ledger.decimal(receipt.amount)+" · "+after.accountName(receipt.accountId);
                }
                batch.items.add(item);
            }
            for(Ledger.HistoryMonth h:after.history){
                Ledger.HistoryMonth old=month(before,h.month);
                if(old!=null&&sameMonth(old,h))continue;
                Item item=item("month",h.month.toString(),"expense",Ledger.csvMonth(h.month)+" 月支出",
                    "¥ "+Ledger.decimal(h.expense)+"\n"+(h.full()?"完整月份":"累计截止 "+Ledger.csvMinute(h.cutoff))+
                    "\n"+(h.includesAdvances?"包含报销垫付":"已排除报销"));
                item.before=old==null?"":monthJson(old);h.importId=batch.id;item.after=monthJson(h);batch.items.add(item);
            }
        }else if(module.equals(FUNDS)){
            Set<Long> buys=new HashSet<>();for(Ledger.Fund f:before.funds)for(Ledger.FundBuy b:f.buys)buys.add(b.id);
            for(Ledger.Fund f:after.funds)for(Ledger.FundBuy b:f.buys)if(!buys.contains(b.id))
                batch.items.add(item("buy",""+b.id,"buy",f.name,
                    "代码 "+f.code+"\n买入 "+Ledger.csvMinute(b.at)+" · ¥ "+Ledger.decimal(b.amount)+"\n手续费 ¥ "+Ledger.decimal(b.fee)));
            for(BankLedger.Product p:after.banks){
                BankLedger.Product old=BankLedger.find(before,p.id);
                String current=bankJson(p);if(old!=null&&current.equals(bankJson(old)))continue;
                Item item=item("bank",""+p.id,"bank",p.name,bankDetail(p));
                item.before=old==null?"":bankJson(old);p.importId=batch.id;item.after=bankJson(p);batch.items.add(item);
            }
        }else throw new IllegalArgumentException("未知导入分类");
        if(batch.items.isEmpty())return null;
        after.imports.add(batch);return batch;
    }
    static Item item(String kind,String ref,String category,String title,String detail){
        Item item=new Item();item.kind=kind;item.ref=ref;item.category=category;item.title=title;item.detail=detail;return item;
    }
    static String bankDetail(BankLedger.Product p){
        BankLedger.Observation o=BankLedger.latest(p);String detail="金额 ¥ "+Ledger.decimal(o.balance)+" · "+Ledger.csvMinute(o.at);
        if(o.cumulative!=null)detail+="\n累计收益 ¥ "+Ledger.decimal(o.cumulative);
        if(o.weekly!=null)detail+="\n本周收益 ¥ "+Ledger.decimal(o.weekly);
        for(Map.Entry<LocalDate,Long> day:o.daily.entrySet())detail+="\n"+Ledger.csvDate(day.getKey())+" 收益 ¥ "+Ledger.decimal(day.getValue());
        return detail;
    }
    static Batch find(Ledger.Data data,long id){for(Batch b:data.imports)if(b.id==id)return b;return null;}
    static Item find(Batch batch,String kind,String ref){if(batch!=null)for(Item i:batch.items)if(i.kind.equals(kind)&&i.ref.equals(ref))return i;return null;}
    static List<Batch> visible(Ledger.Data data,String module){
        List<Batch> list=new ArrayList<>();for(Batch b:data.imports)if(!b.deleted&&b.module.equals(module)&&b.items.stream().anyMatch(i->!i.deleted))list.add(b);
        list.sort(Comparator.comparing((Batch b)->b.at).thenComparingLong(b->b.id).reversed());return list;
    }
    static String summary(Batch batch){
        int expense=0,income=0,reimburse=0,buy=0,bank=0;
        for(Item i:batch.items)if(!i.deleted){
            if(i.kind.equals("month")||i.category.equals(Ledger.LARGE))expense++;
            else if(i.category.equals(Ledger.INCOME))income++;
            else if(i.category.equals(Ledger.REIMBURSE))reimburse++;
            else if(i.kind.equals("buy"))buy++;else if(i.kind.equals("bank"))bank++;
        }
        return batch.module.equals(RECORDS)?"导入支出 "+expense+" 条 · 收入 "+income+" 条 · 报销 "+reimburse+" 条":
            "导入基金买入 "+buy+" 条"+(bank>0?" · 银行理财 "+bank+" 条":"");
    }
    static boolean exists(Ledger.Data data,Item item){
        if(item.kind.equals("entry"))return data.entries.stream().anyMatch(e->(""+e.id).equals(item.ref));
        if(item.kind.equals("month"))return data.history.stream().anyMatch(h->h.month.toString().equals(item.ref));
        if(item.kind.equals("buy"))return data.funds.stream().anyMatch(f->f.buys.stream().anyMatch(b->(""+b.id).equals(item.ref)));
        return data.banks.stream().anyMatch(p->(""+p.id).equals(item.ref));
    }
    static String state(Ledger.Data data,Batch batch,Item item){
        if(!exists(data,item))return "原记录已删除";
        if(item.kind.equals("month")){
            Ledger.HistoryMonth h=month(data,YearMonth.parse(item.ref));
            if(h.importId!=batch.id)return "已被后续导入或手动记录替换；删除此明细不影响当前值";
            if(!sameMonth(h,parseMonth(item.after)))return "后续已手动修改；删除批次时保护当前值";
        }else if(item.kind.equals("bank")){
            BankLedger.Product p=BankLedger.find(data,Long.parseLong(item.ref));
            if(p.importId!=batch.id)return "已被后续导入替换；删除此明细不影响当前值";
            if(!bankJson(p).equals(item.after))return "后续已修改或添加交易；删除批次时保护当前产品";
        }
        return "";
    }
    static Batch untracked(Ledger.Data data,String module){
        Batch result=new Batch();result.module=module;
        Set<String> tracked=new HashSet<>();for(Batch b:visible(data,module))for(Item i:b.items)if(!i.deleted)tracked.add(i.kind+"/"+i.ref);
        if(module.equals(RECORDS)){
            for(Ledger.Entry e:data.entries)if(Arrays.asList(Ledger.INCOME,Ledger.LARGE,Ledger.REIMBURSE).contains(e.kind)&&!tracked.contains("entry/"+e.id)){
                Item i=item("entry",""+e.id,e.kind,e.title,Ledger.csvMinute(e.at)+" · ¥ "+Ledger.decimal(e.amount)+"\n账户："+data.accountName(e.accountId)+(e.note.isEmpty()?"":"\n备注："+e.note));
                if(e.kind.equals(Ledger.REIMBURSE)){i.detail+="\n"+e.reimbursementStatus();for(Ledger.Receipt r:e.receipts)i.detail+="\n到账 "+Ledger.csvMinute(r.at)+" · ¥ "+Ledger.decimal(r.amount);}
                result.items.add(i);
            }
            for(Ledger.HistoryMonth h:data.history)if(h.importId==0||!tracked.contains("month/"+h.month))result.items.add(item("month",h.month.toString(),"expense",Ledger.csvMonth(h.month)+" 月支出","¥ "+Ledger.decimal(h.expense)+" · 截止 "+Ledger.csvMinute(h.cutoff)));
        }else{
            for(Ledger.Fund f:data.funds)for(Ledger.FundBuy b:f.buys)if(!tracked.contains("buy/"+b.id))result.items.add(item("buy",""+b.id,"buy",f.name,"代码 "+f.code+"\n"+Ledger.csvMinute(b.at)+" · ¥ "+Ledger.decimal(b.amount)+"\n手续费 ¥ "+Ledger.decimal(b.fee)));
            for(BankLedger.Product p:data.banks)if(p.importId==0||!tracked.contains("bank/"+p.id))result.items.add(item("bank",""+p.id,"bank",p.name,bankDetail(p)));
        }
        return result;
    }
    static Removal removeUntracked(Ledger.Data data,String module,Item selected,TradingCalendar calendar){
        return removeUntrackedBatch(data,module,selected==null?untracked(data,module).items:Collections.singletonList(selected),calendar);
    }
    static Removal removeUntrackedBatch(Ledger.Data data,String module,Collection<Item> displayed,TradingCalendar calendar){
        Batch unknown=untracked(data,module);Removal result=new Removal();
        Set<String> allowed=new HashSet<>();for(Item i:displayed)allowed.add(i.kind+"/"+i.ref);
        for(Item i:unknown.items)if(allowed.contains(i.kind+"/"+i.ref)){
            if(i.kind.equals("entry"))data.entries.removeIf(e->(""+e.id).equals(i.ref));
            else if(i.kind.equals("month"))data.history.removeIf(h->h.month.toString().equals(i.ref));
            else if(i.kind.equals("bank"))data.banks.removeIf(p->(""+p.id).equals(i.ref));
            else{
                for(Iterator<Ledger.Fund> it=data.funds.iterator();it.hasNext();){
                    Ledger.Fund f=it.next();if(f.buys.removeIf(b->(""+b.id).equals(i.ref))){if(f.buys.isEmpty())it.remove();else FundEngine.resolve(f,calendar);}
                }
                data.entries.removeIf(e->e.kind.equals(Ledger.INVEST_OUT)&&e.note.equals("自动关联买入 "+i.ref)||e.kind.equals(Ledger.INVEST_IN)&&e.note.equals("自动关联卖出 "+i.ref));
            }
            result.removed++;
        }
        return result;
    }
    static Removal remove(Ledger.Data data,long batchId,String onlyRef,String onlyKind,TradingCalendar calendar){
        Batch batch=find(data,batchId);if(batch==null||batch.deleted)throw new IllegalArgumentException("导入记录已删除");
        Removal result=new Removal();
        for(Item item:batch.items)if(!item.deleted&&(onlyRef==null||item.ref.equals(onlyRef)&&item.kind.equals(onlyKind))){
            if(!exists(data,item))result.absent++;
            else if(item.kind.equals("entry")){
                data.entries.removeIf(e->(""+e.id).equals(item.ref));result.removed++;
            }else if(item.kind.equals("buy")){
                for(Iterator<Ledger.Fund> it=data.funds.iterator();it.hasNext();){
                    Ledger.Fund f=it.next();boolean removed=f.buys.removeIf(b->(""+b.id).equals(item.ref));
                    if(removed){if(f.buys.isEmpty())it.remove();else FundEngine.resolve(f,calendar);result.removed++;}
                }
                data.entries.removeIf(e->(e.kind.equals(Ledger.INVEST_OUT)&&e.note.equals("自动关联买入 "+item.ref))||
                    (e.kind.equals(Ledger.INVEST_IN)&&e.note.equals("自动关联卖出 "+item.ref)));
            }else if(item.kind.equals("month")){
                Ledger.HistoryMonth current=month(data,YearMonth.parse(item.ref));
                if(current.importId!=batch.id){result.absent++;}
                else if(!sameMonth(current,parseMonth(item.after))){current.importId=0;result.preserved++;}
                else{
                    data.history.remove(current);String restore=previous(data,item,"month");
                    if(!restore.isEmpty())data.history.add(parseMonth(restore));result.removed++;
                }
            }else if(item.kind.equals("bank")){
                BankLedger.Product current=BankLedger.find(data,Long.parseLong(item.ref));
                if(current.importId!=batch.id){result.absent++;}
                else if(!bankJson(current).equals(item.after)){current.importId=0;result.preserved++;}
                else{
                    data.banks.remove(current);String restore=previous(data,item,"bank");
                    if(!restore.isEmpty())data.banks.add(parseBank(restore));result.removed++;
                }
            }
            item.deleted=true;
        }
        if(batch.items.stream().allMatch(i->i.deleted))batch.deleted=true;
        return result;
    }
    // Follow revision ownership so deleting an older batch never resurrects it during a later undo.
    static String previous(Ledger.Data data,Item item,String kind){
        String restore=item.before;Set<Long> visited=new HashSet<>();
        while(!restore.isEmpty()){
            long owner=kind.equals("month")?parseMonth(restore).importId:parseBank(restore).importId;
            if(owner==0)return restore;
            if(!visited.add(owner))throw new IllegalArgumentException("导入版本链无效");
            Batch old=find(data,owner);Item prior=find(old,kind,item.ref);
            if(old==null||prior==null)return "";
            if(!old.deleted&&!prior.deleted)return restore;
            restore=prior.before;
        }
        return "";
    }
    static Ledger.HistoryMonth month(Ledger.Data d,YearMonth month){for(Ledger.HistoryMonth h:d.history)if(h.month.equals(month))return h;return null;}
    static boolean sameMonth(Ledger.HistoryMonth a,Ledger.HistoryMonth b){return a.expense==b.expense&&a.cutoff.equals(b.cutoff)&&a.includesAdvances==b.includesAdvances;}
    static String monthJson(Ledger.HistoryMonth h){try{return new JSONObject().put("month",h.month).put("expense",h.expense).put("cutoff",h.cutoff).put("includesAdvances",h.includesAdvances).put("importId",h.importId).toString();}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static Ledger.HistoryMonth parseMonth(String json){try{JSONObject j=new JSONObject(json);Ledger.HistoryMonth h=new Ledger.HistoryMonth(YearMonth.parse(j.getString("month")),j.getLong("expense"),LocalDateTime.parse(j.getString("cutoff")));h.includesAdvances=j.getBoolean("includesAdvances");h.importId=j.optLong("importId");return h;}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static String bankJson(BankLedger.Product p){try{return BankCodec.encode(p).toString();}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static BankLedger.Product parseBank(String json){try{return BankCodec.decode(new JSONObject(json));}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static JSONArray encode(Ledger.Data data)throws JSONException{
        JSONArray array=new JSONArray();for(Batch b:data.imports){
            JSONArray items=new JSONArray();for(Item i:b.items)items.put(new JSONObject().put("kind",i.kind).put("ref",i.ref).put("category",i.category).put("title",i.title).put("detail",i.detail).put("before",i.before).put("after",i.after).put("deleted",i.deleted));
            array.put(new JSONObject().put("id",b.id).put("at",b.at).put("module",b.module).put("source",b.source).put("deleted",b.deleted).put("items",items));
        }return array;
    }
    static void decode(Ledger.Data data,JSONArray array)throws JSONException{
        if(array==null)return;if(array.length()>10000)throw new IllegalArgumentException("导入批次超限");
        for(int n=0;n<array.length();n++){
            JSONObject j=array.getJSONObject(n);Batch b=new Batch();b.id=j.getLong("id");b.at=LocalDateTime.parse(j.getString("at"));b.module=j.getString("module");b.source=j.getString("source");b.deleted=j.optBoolean("deleted");
            JSONArray items=j.getJSONArray("items");if(items.length()>100000)throw new IllegalArgumentException("导入明细超限");
            for(int k=0;k<items.length();k++){JSONObject r=items.getJSONObject(k);Item i=item(r.getString("kind"),r.getString("ref"),r.getString("category"),r.getString("title"),r.getString("detail"));i.before=r.optString("before");i.after=r.optString("after");i.deleted=r.optBoolean("deleted");b.items.add(i);}data.imports.add(b);
        }
    }
    static void validate(Ledger.Data data){
        Set<Long> ids=new HashSet<>();int count=0;
        if(data.imports.size()>10000)throw new IllegalArgumentException("导入批次超限");
        for(Batch b:data.imports){
            if(b.id<=0||!ids.add(b.id)||b.at==null||b.at.getYear()<2000||!Arrays.asList(RECORDS,FUNDS).contains(b.module)||b.source.length()>80)throw new IllegalArgumentException("导入批次无效");
            Set<String> keys=new HashSet<>();for(Item i:b.items){
                if(++count>200000||!keys.add(i.kind+"/"+i.ref)||!Arrays.asList("entry","month","buy","bank").contains(i.kind)||i.title.length()>200||i.detail.length()>30000)throw new IllegalArgumentException("导入明细无效");
                if(i.kind.equals("month")){if(!parseMonth(i.after).month.toString().equals(i.ref))throw new IllegalArgumentException("导入月份无效");if(!i.before.isEmpty())parseMonth(i.before);}
                else{if(Long.parseLong(i.ref)<=0)throw new IllegalArgumentException("导入标识无效");if(i.kind.equals("bank")){parseBank(i.after);if(!i.before.isEmpty())parseBank(i.before);}}
            }
        }
    }
    private ImportJournal(){}
}
