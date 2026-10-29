package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Actual linked investment cash flows, independent of market valuation and legacy corrections. */
final class InvestmentCash {
    static final class Flow {
        final long id,accountId,delta;final LocalDateTime at;
        Flow(long id,long accountId,long delta,LocalDateTime at){this.id=id;this.accountId=accountId;this.delta=delta;this.at=FundEngine.ledgerTime(at);}
    }
    static List<Flow> flows(Ledger.Data data){
        List<Flow> result=new ArrayList<>();
        for(Ledger.Fund fund:data.funds)for(Ledger.FundBuy buy:fund.buys){
            if(buy.buyAccountLinked)result.add(new Flow(buy.id,buy.buyAccountId,-buy.amount,buy.at));
            if(buy.saleAccountLinked&&buy.saleDate!=null&&(buy.saleAt==null||!buy.saleAt.isAfter(FundEngine.now())))result.add(new Flow(buy.id,buy.saleAccountId,buy.saleAmount,buy.saleAt==null?buy.saleDate.atTime(15,0):buy.saleAt));
        }
        for(BankLedger.Product product:data.banks){
            if(product.openingAccountLinked&&!product.observations.isEmpty()){
                BankLedger.Observation first=product.observations.get(0);
                result.add(new Flow(product.id,product.openingAccountId,-first.balance,first.at));
            }
            for(BankLedger.Event event:product.events)if(event.accountLinked&&event.completedAt!=null)
                result.add(new Flow(event.id,event.accountId,BankAccounting.buy(event)?-event.amount:event.amount,event.completedAt));
        }
        return result;
    }
    private InvestmentCash(){}
}
