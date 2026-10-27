package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.math.*;
import static org.junit.Assert.*;

public class V478RulesTest {
    @Test public void dailySkipsWeekendAndKnownHoliday(){Ledger.RecurringPlan p=new Ledger.RecurringPlan();p.frequency="每日";TradingCalendar c=new TradingCalendar();c.addClosed(LocalDate.of(2026,10,26));assertEquals(LocalDate.of(2026,10,27),RecurringInvestments.next(p,LocalDate.of(2026,10,23),c));assertEquals(LocalDate.of(2026,10,27),RecurringInvestments.first(p,LocalDate.of(2026,10,24),c));}
    @Test public void finitePlanStopsAfterActualPurchasesAndRoundTrips(){Ledger.Data d=new Ledger.Data();Ledger.Fund f=new Ledger.Fund();f.plan.frequency="每日";f.plan.enabled=true;f.plan.totalPeriods=1;Ledger.FundBuy b=new Ledger.FundBuy();b.id=1;b.amount=10000;b.buyAccountLinked=true;b.buyAccountId=1;b.at=LocalDateTime.of(2026,10,23,10,0);RecurringInvestments.record(d,f,b);assertFalse(f.plan.enabled);assertEquals("",f.plan.nextDate);assertTrue(RecurringInvestments.complete(f.plan));f.id=2;f.code="000001";f.name="测试";f.buys.add(b);d.funds.add(f);Ledger.Fund restored=LedgerStore.decode(LedgerStore.encode(d)).funds.get(0);assertEquals(1,restored.plan.totalPeriods);assertEquals("每日",restored.plan.frequency);}
    @Test public void discountedRateUsesLastPercentageAndAmountTier(){String fees="适用金额  原费率  优惠费率\n小于100万元  0.80%  0.08%\n大于等于100万元，小于300万元  0.50%  0.05%\n大于等于300万元  每笔1000元";assertEquals(Long.valueOf(80),PurchaseFees.suggest(fees,100000));assertEquals(Long.valueOf(50000),PurchaseFees.suggest(fees,100000000));assertNull(PurchaseFees.suggest(fees,300000000));assertEquals(Long.valueOf(0),PurchaseFees.suggest("小于100万元  0.00%",10000));assertNull(PurchaseFees.suggest("",10000));}
}
