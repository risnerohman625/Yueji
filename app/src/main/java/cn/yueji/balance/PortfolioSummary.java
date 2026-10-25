package cn.yueji.balance;

import java.time.*;

/** The header and its curve use the same product selection as the visible list. */
final class PortfolioSummary {
    long value,cost,cumulative,current,previous,heldValue;
    int count,hidden,deleted,shown;
    boolean complete=true,previousKnown=true,currentKnown=true,banks;
    final Ledger.Data selected=new Ledger.Data();
    static PortfolioSummary of(Ledger.Data data,int filter,LocalDateTime now,TradingCalendar calendar){
        PortfolioSummary result=new PortfolioSummary();LocalDate day=now.toLocalDate();
        if(filter!=2)for(Ledger.Fund fund:data.funds){
            result.selected.funds.add(fund);
            if(fund.removed()){result.deleted++;if(!fund.hidden)result.cumulative+=FundEngine.cumulative(fund,day);continue;}
            if(fund.hidden){result.hidden++;continue;}
            result.shown++;long value=FundEngine.value(fund,day);result.value+=value;
            if(FundEngine.held(fund,day)){result.heldValue+=value;result.count++;}
            result.cost+=FundEngine.holdingCost(fund,day);result.cumulative+=FundEngine.cumulative(fund,day);
            result.current+=FundEngine.today(fund,now);Long prior=FundEngine.daily(fund,day.minusDays(1),calendar);
            if(prior==null)result.previousKnown=false;else result.previous+=prior;
            if(!FundEngine.known(fund,day))result.complete=false;
        }
        if(filter!=1)for(BankLedger.Product bank:data.banks){
            result.selected.banks.add(bank);
            if(bank.removed()){result.deleted++;if(!bank.hidden)result.cumulative+=BankLedger.profit(bank);continue;}
            if(bank.hidden){result.hidden++;continue;}
            result.banks=true;result.shown++;result.count++;
            long value=BankAccounting.value(bank,now);result.value+=value;result.heldValue+=value;
            result.cost+=BankAccounting.cost(bank,now);result.cumulative+=BankLedger.profit(bank);
            if(filter==2){
                LocalDate week=BankLedger.monday(day);Long current=BankLedger.week(bank,week),previous=BankLedger.week(bank,week.minusWeeks(1));
                if(current==null)result.currentKnown=false;else result.current+=current;
                if(previous==null)result.previousKnown=false;else result.previous+=previous;
            }else{
                Long current=BankLedger.daily(bank,day),previous=BankLedger.daily(bank,day.minusDays(1));
                // Absence means zero contribution to the aggregate, never a weekly-average estimate.
                if(current!=null)result.current+=current;if(previous!=null)result.previous+=previous;
            }
        }
        return result;
    }
    private PortfolioSummary(){}
}
