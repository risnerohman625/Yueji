package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Actual linked investment cash flows, independent of market valuation and legacy corrections. */
final class InvestmentCash {
    static final class Flow {
        final long id,accountId,delta;final LocalDateTime at;final String key,source;
        Flow(long id,long accountId,long delta,LocalDateTime at,String key,String source){this.key=key;this.source=source;this.id=id;this.accountId=accountId;this.delta=delta;this.at=FundEngine.ledgerTime(at);}
    }
    static List<Flow> flows(Ledger.Data data){
        List<Flow> result=new ArrayList<>();
        for(Ledger.Fund fund:data.funds)for(Ledger.FundBuy buy:fund.buys){
            if(buy.buyAccountLinked)result.add(new Flow(buy.id,buy.buyAccountId,-buy.amount,buy.at,"fund_buy:"+fund.id+":"+buy.id,"基金买入 · "+fund.name));
            if(buy.saleAccountLinked&&buy.saleDate!=null&&(buy.saleAt==null||!buy.saleAt.isAfter(FundEngine.now())))result.add(new Flow(buy.id,buy.saleAccountId,buy.saleAmount,buy.saleAt==null?buy.saleDate.atTime(15,0):buy.saleAt,"fund_sale:"+fund.id+":"+buy.id,"基金赎回到账 · "+fund.name));
        }
        for(BankLedger.Product product:data.banks){
            if(product.openingAccountLinked&&!product.observations.isEmpty()){
                BankLedger.Observation first=product.observations.get(0);
                result.add(new Flow(product.id,product.openingAccountId,-first.balance,first.at,"bank_open:"+product.id,"银行理财申购 · "+product.name));
            }
            for(BankLedger.Event event:product.events)if(event.accountLinked&&event.completedAt!=null)
                result.add(new Flow(event.id,event.accountId,BankAccounting.buy(event)?-event.amount:event.amount,event.completedAt,"bank_event:"+product.id+":"+event.id,(BankAccounting.buy(event)?"银行理财申购 · ":"银行理财赎回 · ")+product.name));
        }
        return result;
    }
    private InvestmentCash(){}
}
