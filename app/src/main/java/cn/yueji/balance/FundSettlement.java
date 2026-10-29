package cn.yueji.balance;

import java.math.*;
import java.time.*;
import java.util.*;

/** Explicit redemption instructions wait for dated NAVs and keep cash in transit in the portfolio. */
final class FundSettlement {
    static Ledger.Fund find(Ledger.Data d,long id){for(Ledger.Fund f:d.funds)if(f.id==id)return f;return null;}
    static LocalDate plusOpen(TradingCalendar c,LocalDate day,int n){for(int i=0;i<n;i++)day=c.next(day);return day;}
    static Ledger.FundOrder create(Ledger.Data data,Ledger.Fund fund,LocalDateTime at,long account,String target,long fee,int confirmDays,int receiveDays,TradingCalendar calendar){
        if(at==null||at.isAfter(FundEngine.now()))throw new IllegalArgumentException("卖出时间不能在未来");
        if(fund==null||fund.removed()||fund.buys.isEmpty())throw new IllegalArgumentException("请先补齐原基金买入记录");
        if(confirmDays<1||confirmDays>30||receiveDays<confirmDays||receiveDays>60||fee<0)throw new IllegalArgumentException("请核对确认、到账工作日及费用");
        AutomaticInvestments.requireAppend(fund,at);
        if(account<=0&&!target.matches("[0-9]{6}"))throw new IllegalArgumentException("请选择到账账户或输入目标基金代码");
        if(!target.isEmpty()&&target.equals(fund.code))throw new IllegalArgumentException("不能转换到同一基金");
        for(Ledger.FundOrder old:data.fundOrders)if(old.sourceId==fund.id&&!"已完成".equals(old.status))throw new IllegalArgumentException("该基金已有待处理卖出");
        Ledger.FundOrder order=new Ledger.FundOrder();order.id=data.nextId();order.sourceId=fund.id;order.at=at;order.tradeDate=calendar.tradeDate(at);order.confirmDate=plusOpen(calendar,order.tradeDate,confirmDays);order.receivedAt=plusOpen(calendar,order.tradeDate,receiveDays).atTime(12,0);order.fee=fee;order.accountId=account;order.targetCode=target;
        for(Ledger.FundBuy b:fund.buys)if(b.saleDate==null){if(b.confirmDate==null||!b.confirmDate.isBefore(order.tradeDate)&&!b.confirmDate.equals(order.tradeDate))throw new IllegalArgumentException("包含尚未确认的买入批次，不能卖出");fund.profile.inferLock(fund.name);LocalDate unlock=fund.profile.unlock(b.confirmDate);if(unlock!=null&&order.tradeDate.isBefore(unlock))throw new IllegalArgumentException("仍在锁定期，请核对开放日："+unlock);order.buyIds.add(b.id);}
        if(order.buyIds.isEmpty())throw new IllegalArgumentException("没有可卖出的份额");
        if(account>0)AccountCash.requireAccount(data,account,FundEngine.ledgerTime(order.receivedAt));
        data.fundOrders.add(order);fund.plan.enabled=false;
        return order;
    }
    static boolean releaseReached(Ledger.FundBuy b,LocalDate day){
        if(b.releasePending)return false;
        if(b.saleAt==null)return true;
        LocalDateTime through=day.equals(FundEngine.now().toLocalDate())?FundEngine.now():day.atTime(LocalTime.MAX);
        return !b.saleAt.isAfter(through);
    }
    static void cancel(Ledger.Data data,long id){
        Ledger.FundOrder order=null;for(Ledger.FundOrder o:data.fundOrders)if(o.id==id)order=o;
        if(order==null||"已完成".equals(order.status))throw new IllegalArgumentException("已完成交易不能作为待处理计划撤销");
        long targetId=order.targetBuyId;for(Ledger.Fund f:data.funds)for(Ledger.FundBuy b:f.buys)if(b.id==targetId&&b.saleDate!=null)throw new IllegalArgumentException("目标买入已卖出，不能撤销");
        for(Ledger.Fund f:data.funds){f.buys.removeIf(b->targetId>0&&b.id==targetId);for(Ledger.FundBuy b:f.buys)if(b.saleOrderId==id){b.saleDate=null;b.saleAt=null;b.saleAmount=0;b.saleAccountLinked=false;b.saleAccountId=0;b.saleOrderId=0;b.saleTag="";b.releasePending=false;}}
        data.fundOrders.remove(order);
        for(Iterator<Ledger.Fund> it=data.funds.iterator();it.hasNext();){Ledger.Fund f=it.next();if(f.pendingOrder&&f.buys.isEmpty()){boolean used=false;for(Ledger.FundOrder o:data.fundOrders)if(o.targetCode.equals(f.code))used=true;if(!used){for(Ledger.WatchGroup group:data.watchGroups)group.codes.remove(f.code);data.watchAllIncluded.remove(f.code);it.remove();}}}
    }
    static boolean positionReached(Ledger.FundBuy b,LocalDate day){
        if(b.positionAt==null)return true;
        LocalDateTime through=day.equals(FundEngine.now().toLocalDate())?FundEngine.now():day.atTime(LocalTime.MAX);
        return !b.positionAt.isAfter(through);
    }
    static boolean apply(Ledger.Data data,LocalDateTime now,TradingCalendar c){
        boolean changed=false;
        for(Ledger.FundOrder order:data.fundOrders){
            if("已完成".equals(order.status))continue;
            Ledger.Fund source=find(data,order.sourceId);if(source==null){order.error="原基金记录不存在";continue;}
            BigDecimal nav=source.prices.get(order.tradeDate);
            if(nav==null||nav.signum()<=0){order.error="等待卖出交易日净值："+order.tradeDate;continue;}
            List<Ledger.FundBuy> buys=new ArrayList<>();long gross=0;
            for(Ledger.FundBuy b:source.buys)if(order.buyIds.contains(b.id)){if(b.units.signum()<=0)break;buys.add(b);gross=Math.addExact(gross,FundEngine.cents(FundEngine.unitsAt(source,b,order.tradeDate).multiply(nav)));}
            if(buys.size()!=order.buyIds.size()){order.error="等待原买入交易日净值/份额";continue;}
            if(gross<=order.fee){order.error="卖出金额不足以支付费用，请修改卖出计划";continue;}
            Ledger.Fund target=null;LocalDateTime released=order.receivedAt;
            if(!order.targetCode.isEmpty()){
                for(Ledger.Fund f:data.funds)if(f.code.equals(order.targetCode))target=f;
                released=order.confirmDate.atTime(12,0);
            }
            long net=gross-order.fee,assigned=0;
            for(int i=0;i<buys.size();i++){
                Ledger.FundBuy b=buys.get(i);long part=i==buys.size()-1?net-assigned:BigDecimal.valueOf(net).multiply(BigDecimal.valueOf(FundEngine.cents(FundEngine.unitsAt(source,b,order.tradeDate).multiply(nav)))).divide(BigDecimal.valueOf(gross),0,RoundingMode.DOWN).longValueExact();assigned+=part;
                b.saleDate=order.tradeDate;b.saleAt=released;b.saleAmount=part;b.saleAccountLinked=order.targetCode.isEmpty();b.saleAccountId=b.saleAccountLinked?order.accountId:0;b.accountFlowManaged=true;b.saleOrderId=order.id;b.saleTag=order.targetCode.isEmpty()?"卖出":"转换";
            }
            order.netAmount=net;changed=true;
            if(!order.targetCode.isEmpty()&&(target==null||target.removed()||!target.prices.containsKey(order.tradeDate))){for(Ledger.FundBuy b:buys)b.releasePending=true;order.error="等待目标基金交易日净值："+order.targetCode;FundEngine.resolve(source,c);continue;}
            for(Ledger.FundBuy b:buys)b.releasePending=false;
            if(target!=null&&order.targetBuyId==0){
                Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=data.nextId();buy.amount=net;buy.at=order.at;buy.positionAt=released;buy.transactionTag="转换";buy.overrideDates=true;buy.tradeDate=order.tradeDate;buy.confirmDate=order.confirmDate;target.buys.add(buy);target.pendingOrder=false;order.targetBuyId=buy.id;FundEngine.resolve(target,c);
            }
            if(target!=null&&order.targetBuyId>0){for(Ledger.FundBuy buy:target.buys)if(buy.id==order.targetBuyId)buy.amount=net;FundEngine.resolve(target,c);}
            order.error="";order.status=now.isBefore(order.confirmDate.atTime(12,0))?"待卖出确认":now.isBefore(released)?"待资金到账":"已完成";FundEngine.resolve(source,c);changed=true;
        }
        return changed;
    }
    static void validate(Ledger.Data data){
        Set<Long> ids=new HashSet<>();
        for(Ledger.FundOrder o:data.fundOrders){Ledger.Fund f=find(data,o.sourceId);if(o.id<=0||!ids.add(o.id)||f==null||o.at==null||o.at.isAfter(FundEngine.now())||o.tradeDate==null||o.confirmDate==null||o.confirmDate.isBefore(o.tradeDate)||o.receivedAt==null||o.receivedAt.toLocalDate().isBefore(o.confirmDate)||o.fee<0||o.fee>Ledger.MAX_MONEY||o.buyIds.isEmpty()||o.accountId<=0&& !o.targetCode.matches("[0-9]{6}"))throw new IllegalArgumentException("卖出计划数据无效");Set<Long> buys=new HashSet<>();for(Ledger.FundBuy b:f.buys)buys.add(b.id);if(!buys.containsAll(o.buyIds))throw new IllegalArgumentException("卖出计划引用的买入已删除");if(o.accountId>0)AccountCash.requireAccount(data,o.accountId,FundEngine.ledgerTime(o.receivedAt));}
        if(data.fundOrders.size()>10000)throw new IllegalArgumentException("卖出计划数量超限");
        for(Ledger.FundOrder o:data.fundOrders){
            if(!Arrays.asList("待净值","待卖出确认","待资金到账","已完成").contains(o.status)||!o.targetCode.isEmpty()&&(!o.targetCode.matches("[0-9]{6}")||o.accountId!=0||o.targetCode.equals(find(data,o.sourceId).code))||o.targetCode.isEmpty()&&o.accountId<=0||o.netAmount<0||o.netAmount>Ledger.MAX_MONEY)throw new IllegalArgumentException("卖出/转换状态无效");
            if(o.targetBuyId>0){boolean found=false;for(Ledger.Fund f:data.funds)if(f.code.equals(o.targetCode))for(Ledger.FundBuy b:f.buys)if(b.id==o.targetBuyId&&"转换".equals(b.transactionTag))found=true;if(!found)throw new IllegalArgumentException("转换买入关联无效");}
        }
        for(Ledger.Fund f:data.funds)for(Ledger.FundBuy b:f.buys){if(b.saleOrderId>0){boolean found=false;for(Ledger.FundOrder o:data.fundOrders)if(o.id==b.saleOrderId&&o.sourceId==f.id&&o.buyIds.contains(b.id))found=true;if(!found)throw new IllegalArgumentException("卖出批次关联无效");}if(b.positionAt!=null){boolean found=false;for(Ledger.FundOrder o:data.fundOrders)if(o.targetBuyId==b.id&&o.targetCode.equals(f.code))found=true;if(!found)throw new IllegalArgumentException("转换份额时间缺少关联计划");}}
    }
    private FundSettlement(){}
}
