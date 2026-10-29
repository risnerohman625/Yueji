package cn.yueji.balance;

import android.content.Context;
import java.time.*;

/** Bounded local catch-up on foreground/background checks, independent of market polling time. */
final class InvestmentProcessing {
    static TradingCalendar calendar(Context c){TradingCalendar calendar=new TradingCalendar();for(int y=2000;y<=FundEngine.now().getYear()+1;y++)calendar.load(c,y);return calendar;}
    static boolean run(Context context){
        synchronized(LedgerStore.class){
            LedgerStore store=new LedgerStore(context);
            try{Ledger.Data data=store.load();boolean needed=false;for(Ledger.Fund f:data.funds)needed|=f.plan.automatic&&f.plan.enabled;for(Ledger.FundOrder o:data.fundOrders)needed|=!"已完成".equals(o.status);if(!needed)return false;String before=LedgerStore.encode(data);TradingCalendar c=calendar(context);
                for(Ledger.Fund f:data.funds)if(f.plan.automatic&&f.plan.enabled)try{AutomaticInvestments.generate(data,f,FundEngine.now(),c);}catch(Exception e){f.plan.error=e.getMessage();}
                FundSettlement.apply(data,FundEngine.now(),c);boolean changed=!before.equals(LedgerStore.encode(data));if(changed)store.save(data);return changed;
            }finally{store.close();}
        }
    }
    private InvestmentProcessing(){}
}
