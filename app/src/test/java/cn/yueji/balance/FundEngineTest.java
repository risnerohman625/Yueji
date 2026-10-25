package cn.yueji.balance;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class FundEngineTest {
    TradingCalendar calendar(){TradingCalendar c=new TradingCalendar();for(int day=1;day<=7;day++)c.addClosed(LocalDate.of(2026,10,day));return c;}
    Ledger.Fund fund(){Ledger.Fund f=new Ledger.Fund();f.id=1;f.code="000001";f.name="测试";return f;}
    Ledger.FundBuy buy(Ledger.Fund f,String at,long amount,long fee){Ledger.FundBuy b=new Ledger.FundBuy();b.id=f.buys.size()+1;b.amount=amount;b.fee=fee;b.at=LocalDateTime.parse(at);f.buys.add(b);return b;}
    void price(Ledger.Fund f,String day,String nav){f.prices.put(LocalDate.parse(day),new BigDecimal(nav));f.nav=new BigDecimal(nav);f.navDate=day;}
    @Test public void cutoffAt1500MovesToNextTradingDay(){
        TradingCalendar c=calendar();assertEquals(LocalDate.of(2026,10,19),c.tradeDate(LocalDateTime.parse("2026-10-19T14:59")));
        assertEquals(LocalDate.of(2026,10,20),c.tradeDate(LocalDateTime.parse("2026-10-19T15:00")));
    }
    @Test public void saturdayOrderUsesMondayAndTuesdayConfirmation(){
        TradingCalendar c=calendar();Ledger.Fund f=fund();Ledger.FundBuy b=buy(f,"2026-10-17T10:00",100000,0);price(f,"2026-10-19","1");FundEngine.resolve(f,c);
        assertEquals(LocalDate.of(2026,10,19),b.tradeDate);assertEquals(LocalDate.of(2026,10,20),b.confirmDate);
        assertEquals(0,FundEngine.units(f,LocalDate.of(2026,10,19)).signum());assertEquals(new BigDecimal("1000.00"),FundEngine.units(f,LocalDate.of(2026,10,20)));
    }
    @Test public void holidaysAndWeekendsAreSkipped(){
        TradingCalendar c=calendar();assertEquals(LocalDate.of(2026,10,8),c.tradeDate(LocalDateTime.parse("2026-10-01T10:00")));
        assertEquals(LocalDate.of(2026,10,12),c.tradeDate(LocalDateTime.parse("2026-10-09T15:01")));
    }
    @Test public void feeIsSubtractedBeforeSharesAndReturn(){
        Ledger.Fund f=fund();Ledger.FundBuy b=buy(f,"2026-09-07T10:00",100000,100);price(f,"2026-09-07","1.2");price(f,"2026-09-08","1.212");FundEngine.resolve(f,calendar());
        assertEquals(new BigDecimal("832.50"),b.units);assertEquals(100899,FundEngine.value(f,LocalDate.of(2026,9,8)));assertEquals(899,FundEngine.cumulative(f,LocalDate.of(2026,9,8)));
    }
    @Test public void noNavDoesNotInventConfirmationOrProfit(){
        Ledger.Fund f=fund();Ledger.FundBuy b=buy(f,"2026-09-07T10:00",100000,0);FundEngine.resolve(f,calendar());assertEquals(BigDecimal.ZERO,b.units);assertEquals(100000,FundEngine.value(f,LocalDate.of(2026,9,8)));assertEquals(0,FundEngine.cumulative(f,LocalDate.of(2026,9,8)));
    }
    @Test public void soldFundRemainsInCumulativeProfit(){
        Ledger.Fund f=fund();Ledger.FundBuy b=buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");FundEngine.resolve(f,calendar());b.saleDate=LocalDate.of(2026,9,9);b.saleAmount=120000;
        assertEquals(0,FundEngine.value(f,LocalDate.of(2026,9,10)));assertEquals(0,FundEngine.holdingCost(f,LocalDate.of(2026,9,10)));assertEquals(20000,FundEngine.cumulative(f,LocalDate.of(2026,9,10)));assertEquals(10000,(long)FundEngine.daily(f,LocalDate.of(2026,9,9)));
    }
    @Test public void dailyAmountsSumToCumulativeChange(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,100);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");price(f,"2026-09-09","1.05");price(f,"2026-09-10","1.15");FundEngine.resolve(f,calendar());long sum=0;for(int day=7;day<=10;day++)sum+=FundEngine.daily(f,LocalDate.of(2026,9,day));assertEquals(FundEngine.cumulative(f,LocalDate.of(2026,9,10)),sum);
    }
    @Test public void portfolioCurveIsSumOfFunds(){
        Ledger.Fund f=fund(),g=fund();buy(f,"2026-09-07T10:00",100000,0);buy(g,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");price(g,"2026-09-07","1");price(g,"2026-09-08","0.95");FundEngine.resolve(f,calendar());FundEngine.resolve(g,calendar());
        assertEquals(5000,(long)FundEngine.series(Arrays.asList(f,g),LocalDate.of(2026,9,8),LocalDate.of(2026,9,8)).get(LocalDate.of(2026,9,8)));
    }
    @Test public void actualConfirmationOverrideIsKept(){
        Ledger.Fund f=fund();Ledger.FundBuy b=buy(f,"2026-09-07T10:00",100000,0);b.overrideDates=true;b.tradeDate=LocalDate.of(2026,9,8);b.confirmDate=LocalDate.of(2026,9,10);price(f,"2026-09-08","1");FundEngine.resolve(f,calendar());assertEquals(LocalDate.of(2026,9,10),b.confirmDate);
    }
    @Test public void before1500CurrentProfitIsZero(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");FundEngine.resolve(f,calendar());assertEquals(0,FundEngine.today(f,LocalDateTime.parse("2026-09-08T14:59")));assertEquals(10000,FundEngine.today(f,LocalDateTime.parse("2026-09-08T15:00")));
    }
    @Test public void accountRemovalDoesNotCreateSpending(){
        Ledger.Data d=new Ledger.Data();Ledger.Account a=new Ledger.Account(1,"账户","银行");a.opening=10000;a.created=LocalDateTime.parse("2026-09-01T00:00");a.deletedAt=LocalDateTime.parse("2026-09-10T12:00");d.accounts.add(a);
        Ledger.Account other=new Ledger.Account(2,"其他","银行");d.accounts.add(other);Ledger.Snapshot start=new Ledger.Snapshot(1,LocalDateTime.parse("2026-09-01T00:00"),""),end=new Ledger.Snapshot(2,LocalDateTime.parse("2026-09-15T12:00"),"");start.balances.put(1L,10000L);start.balances.put(2L,100000L);end.balances.put(2L,90000L);assertEquals(10000,Ledger.expenseBetween(start,end,d));
    }
    @Test public void cashDividendDoesNotBecomeFalseLoss(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","0.9");f.dividends.put(LocalDate.of(2026,9,8),new BigDecimal("0.1"));FundEngine.resolve(f,calendar());
        assertEquals(90000,FundEngine.value(f,LocalDate.of(2026,9,8)));assertEquals(90000,FundEngine.holdingCost(f,LocalDate.of(2026,9,8)));assertEquals(0,FundEngine.heldProfit(f,LocalDate.of(2026,9,8)));assertEquals(0,FundEngine.cumulative(f,LocalDate.of(2026,9,8)));assertEquals(0,(long)FundEngine.daily(f,LocalDate.of(2026,9,8)));
    }
    @Test public void splitAdjustsUnitsAndDoesNotBecomeFalseLoss(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","2");price(f,"2026-09-08","1");f.splits.put(LocalDate.of(2026,9,8),new BigDecimal("2"));FundEngine.resolve(f,calendar());
        assertEquals(new BigDecimal("1000.00"),FundEngine.units(f,LocalDate.of(2026,9,8)));assertEquals(100000,FundEngine.value(f,LocalDate.of(2026,9,8)));assertEquals(0,FundEngine.cumulative(f,LocalDate.of(2026,9,8)));
    }
    @Test public void dividendRemainsInRealizedCumulativeAfterSale(){
        Ledger.Fund f=fund();Ledger.FundBuy b=buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","0.9");f.dividends.put(LocalDate.of(2026,9,8),new BigDecimal("0.1"));FundEngine.resolve(f,calendar());b.saleDate=LocalDate.of(2026,9,9);b.saleAmount=90000;
        assertEquals(0,FundEngine.cumulative(f,LocalDate.of(2026,9,10)));
    }
    @Test public void missingPurchaseDateNavDoesNotInventShares(){
        Ledger.Fund f=fund();buy(f,"2026-09-08T10:00",100000,0);price(f,"2026-09-04","1");price(f,"2026-09-07","1.1");FundEngine.resolve(f,calendar());
        assertEquals(BigDecimal.ZERO,f.buys.get(0).units);assertFalse(FundEngine.known(f,LocalDate.of(2026,9,10)));
    }
    @Test public void deletedFundKeepsItsProfitFrozenAndStopsAccruing(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");price(f,"2026-09-09","1.2");price(f,"2026-09-10","1.5");FundEngine.resolve(f,calendar());
        FundEngine.remove(f,LocalDateTime.parse("2026-09-09T20:00"));
        assertEquals(LocalDate.of(2026,9,9),f.frozenOn);assertTrue(f.removed());
        long frozen=FundEngine.cumulative(f,LocalDate.of(2026,9,9));assertEquals(20000,frozen);
        assertEquals(frozen,FundEngine.cumulative(f,LocalDate.of(2026,9,10)));assertEquals(frozen,FundEngine.cumulative(f,LocalDate.of(2026,10,20)));
        assertEquals(0,(long)FundEngine.daily(f,LocalDate.of(2026,9,10),calendar()));assertEquals(10000,(long)FundEngine.daily(f,LocalDate.of(2026,9,9),calendar()));
        FundEngine.restore(f);assertNull(f.removedAt);assertNull(f.frozenOn);assertEquals(50000,FundEngine.cumulative(f,LocalDate.of(2026,9,10)));
    }
    @Test public void freezeUsesTheLastNavDateKnownAtDeletion(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");FundEngine.resolve(f,calendar());
        FundEngine.remove(f,LocalDateTime.parse("2026-09-09T10:00"));assertEquals(LocalDate.of(2026,9,8),f.frozenOn);
        price(f,"2026-09-09","1.3");assertEquals(10000,FundEngine.cumulative(f,LocalDate.of(2026,9,9)));
    }
    @Test public void deletedFundWithoutAnyNavFreezesOnTheDeletionDay(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);FundEngine.remove(f,LocalDateTime.parse("2026-09-09T10:00"));assertEquals(LocalDate.of(2026,9,9),f.frozenOn);
    }
    @Test public void dailyExtremesIgnoreDaysBeforeConfirmationAndUnknownDays(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");price(f,"2026-09-09","1.05");price(f,"2026-09-10","1.15");FundEngine.resolve(f,calendar());
        long[] extremes=FundEngine.dailyExtremes(Collections.singletonList(f),calendar());assertNotNull(extremes);assertEquals(10000,extremes[0]);assertEquals(-5000,extremes[1]);
        Ledger.Fund none=fund();buy(none,"2026-09-07T10:00",100000,0);FundEngine.resolve(none,calendar());assertNull(FundEngine.dailyExtremes(Collections.singletonList(none),calendar()));
    }
    @Test public void dailyExtremesOfSeveralFundsAddUpPerDay(){
        Ledger.Fund f=fund(),g=fund();g.id=2;g.code="000002";buy(f,"2026-09-07T10:00",100000,0);buy(g,"2026-09-07T10:00",100000,0);
        price(f,"2026-09-07","1");price(f,"2026-09-08","1.1");price(g,"2026-09-07","1");price(g,"2026-09-08","0.95");FundEngine.resolve(f,calendar());FundEngine.resolve(g,calendar());
        long[] extremes=FundEngine.dailyExtremes(Arrays.asList(f,g),calendar());assertEquals(5000,extremes[0]);assertEquals(5000,extremes[1]);
    }
    @Test public void investedSumsEveryBatchIncludingSoldOnes(){
        Ledger.Fund f=fund();buy(f,"2026-09-07T10:00",100000,0);Ledger.FundBuy second=buy(f,"2026-09-08T10:00",50000,0);second.saleDate=LocalDate.of(2026,9,20);assertEquals(150000,FundEngine.invested(f));
    }
}
