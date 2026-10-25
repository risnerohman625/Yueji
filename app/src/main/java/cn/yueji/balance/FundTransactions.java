package cn.yueji.balance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Applies corrections to a candidate ledger, retaining batch IDs and their linked transfers. */
final class FundTransactions {
    static Ledger.Fund save(Ledger.Data data,long sourceId,long buyId,String code,long amount,long fee,
                            LocalDateTime at,FundClient.Quote quote,String failure,boolean addTransfer,TradingCalendar calendar){
        return saveInternal(data,sourceId,buyId,code,amount,fee,at,quote,failure,addTransfer,false,0,false,calendar);
    }
    static Ledger.Fund save(Ledger.Data data,long sourceId,long buyId,String code,long amount,long fee,
                            LocalDateTime at,FundClient.Quote quote,String failure,boolean addTransfer,
                            boolean linkBuy,long buyAccountId,TradingCalendar calendar){
        return saveInternal(data,sourceId,buyId,code,amount,fee,at,quote,failure,addTransfer,linkBuy,buyAccountId,true,calendar);
    }
    private static Ledger.Fund saveInternal(Ledger.Data data,long sourceId,long buyId,String code,long amount,long fee,LocalDateTime at,FundClient.Quote quote,String failure,boolean addTransfer,boolean linkBuy,long buyAccountId,boolean updateLink,TradingCalendar calendar){
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码为6位数字");
        if(amount<=0||amount>Ledger.MAX_MONEY||fee<0||fee>=amount)throw new IllegalArgumentException("买入金额须大于手续费且在有效范围内");
        if(at==null||at.getYear()<2000||at.isAfter(FundEngine.now()))throw new IllegalArgumentException("买入时间无效或在未来");
        Ledger.Fund source=null,target=null;Ledger.FundBuy original=null;
        for(Ledger.Fund f:data.funds){if(f.id==sourceId)source=f;if(f.code.equals(code))target=f;}
        if(sourceId!=0&&(source==null||source.removed()))throw new IllegalArgumentException("原基金已删除或移除，不能修改");
        if(buyId!=0){
            if(source!=null)for(Ledger.FundBuy b:source.buys)if(b.id==buyId)original=b;
            if(original==null)throw new IllegalArgumentException("原买入记录已删除，不能重新创建");
        }
        if(target!=null&&target.removed())throw new IllegalArgumentException("目标基金已从首页删除，请先恢复");
        if(target!=null&&target.buys.isEmpty()&&target.shares.signum()>0)throw new IllegalArgumentException("目标基金是旧版份额持仓，不能混入买入批次");
        boolean codeChanged=source!=null&&!source.code.equals(code);
        if(codeChanged&&quote==null)throw new IllegalArgumentException("修改基金代码需要先取得新代码的历史净值，请联网后重试");
        if(quote!=null&&quote.code!=null&&!quote.code.isEmpty()&&!quote.code.equals(code))throw new IllegalArgumentException("行情代码与输入代码不一致");
        if(codeChanged&&target!=null&&source.buys.size()==1&&!source.tag.isEmpty()&&!target.tag.isEmpty()&&!source.tag.equals(target.tag))
            throw new IllegalArgumentException("原基金与目标基金的标签不同，请先统一标签，避免合并时丢失标签");
        if(target==null){
            if(codeChanged&&source.buys.size()==1){
                target=source;target.code=code;target.name="基金 "+code;clearMarket(target);
            }else{
                target=new Ledger.Fund();target.id=data.nextId();target.code=code;target.name="基金 "+code;
                if(codeChanged){target.tag=source.tag;target.hidden=source.hidden;}
                data.funds.add(target);
            }
        }
        Ledger.FundBuy replacement=new Ledger.FundBuy();replacement.id=buyId==0?data.nextId():buyId;
        replacement.amount=amount;replacement.fee=fee;replacement.at=at;
        replacement.accountFlowManaged=updateLink;
        replacement.buyAccountLinked=linkBuy;
        replacement.buyAccountId=linkBuy?buyAccountId:0;
        if(original!=null){
            replacement.saleDate=original.saleDate;replacement.saleAt=original.saleAt;replacement.saleAmount=original.saleAmount;
            replacement.saleAccountLinked=original.saleAccountLinked;replacement.saleAccountId=original.saleAccountId;
            replacement.accountFlowManaged|=original.saleAccountLinked;
            replacement.recurringRecordId=original.recurringRecordId;
            if(!codeChanged&&at.equals(original.at)){replacement.overrideDates=original.overrideDates;replacement.tradeDate=original.tradeDate;replacement.confirmDate=original.confirmDate;}
            if(!updateLink){replacement.buyAccountLinked=original.buyAccountLinked;replacement.buyAccountId=original.buyAccountId;}
            source.buys.remove(original);
            if(updateLink&&linkBuy)data.entries.removeIf(e->e.note.equals("自动关联买入 "+replacement.id));
            if(source!=target){
                for(java.util.Iterator<Ledger.RecurringRecord> it=source.plan.records.iterator();it.hasNext();){Ledger.RecurringRecord r=it.next();if(r.buyId==replacement.id){target.plan.records.add(r);it.remove();}}
                if(source.buys.isEmpty()&&!target.plan.enabled&&source.plan.enabled){target.plan.enabled=true;target.plan.amount=source.plan.amount;target.plan.fee=source.plan.fee;target.plan.accountId=source.plan.accountId;target.plan.frequency=source.plan.frequency;target.plan.dayOfMonth=source.plan.dayOfMonth;target.plan.startDate=source.plan.startDate;target.plan.nextDate=source.plan.nextDate;}
                if(source.buys.isEmpty()){
                    target.plan.records.addAll(source.plan.records);source.plan.records.clear();
                    if(target.tag.isEmpty())target.tag=source.tag;
                    // All financial records survive in the corrected code; this merges an erroneous identity, not a purge.
                    data.funds.remove(source);
                }else FundEngine.resolve(source,calendar);
            }
        }
        target.buys.add(replacement);
        if(quote!=null){
            target.name=quote.name;target.nav=quote.nav;target.previous=quote.previous;target.navDate=quote.date;
            target.prices.putAll(quote.history);target.navReturns.putAll(quote.returns);target.dividends.putAll(quote.dividends);target.splits.putAll(quote.splits);
            target.historyComplete=quote.complete;target.fundType=quote.fundType;target.updatedAt=FundEngine.now().toString();target.error="";
        }else target.error="净值待获取："+(failure==null?"未联网":failure);
        FundEngine.resolve(target,calendar);
        for(Ledger.Entry e:data.entries){if(e.note.equals("自动关联买入 "+replacement.id)){e.amount=amount;e.at=FundEngine.ledgerTime(at);e.title="基金申购 "+code;}else if(e.note.equals("自动关联卖出 "+replacement.id))e.title="基金赎回 "+code;}
        if(addTransfer&&buyId==0&&!linkBuy){
            Ledger.Entry transfer=new Ledger.Entry(data.nextId(),false,amount,FundEngine.ledgerTime(at),"基金申购 "+code,"自动关联买入 "+replacement.id,0);
            transfer.kind=Ledger.INVEST_OUT;data.entries.add(transfer);
        }
        FundCodec.validate(target);
        return target;
    }
    private static void clearMarket(Ledger.Fund fund){
        fund.profile.read(new org.json.JSONObject());
        fund.prices.clear();fund.navReturns.clear();fund.dividends.clear();fund.splits.clear();
        fund.nav=BigDecimal.ZERO;fund.previous=BigDecimal.ZERO;fund.navDate="";fund.updatedAt="";fund.fundType="";fund.historyComplete=false;fund.error="";
    }
    static void saleTransfer(Ledger.Data data,long buyId,String code,long amount,LocalDateTime received,boolean enabled){
        data.entries.removeIf(e->e.note.equals("自动关联卖出 "+buyId));
        if(enabled&&amount>0){
            Ledger.Entry entry=new Ledger.Entry(data.nextId(),false,amount,FundEngine.ledgerTime(received),"基金赎回 "+code,"自动关联卖出 "+buyId,0);
            entry.kind=Ledger.INVEST_IN;data.entries.add(entry);
        }
    }
    private FundTransactions(){}
}
