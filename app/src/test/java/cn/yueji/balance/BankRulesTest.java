package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class BankRulesTest {
    BankLedger.Draft draft(String name,String at,long balance,Long weekly,Long cumulative){
        BankLedger.Draft d=new BankLedger.Draft();d.name=name;d.at=LocalDateTime.parse(at);d.balance=balance;d.weekly=weekly;d.cumulative=cumulative;return d;
    }
    @Test public void balanceIncreaseWithoutProfitEvidenceIsNeverIncome(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-01T10:00",400000,null,null),false,null);
        BankLedger.apply(data,p.id,draft("产品A","2026-10-20T10:00",600000,null,null),false,null);
        assertEquals(600000,BankLedger.balance(p));assertEquals(0,BankLedger.profit(p));assertTrue(BankLedger.incomplete(p));
    }
    @Test public void sameWeekUpdatesReplaceRatherThanDoubleCount(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-19T10:00",400000,100L,null),false,null);
        BankLedger.apply(data,p.id,draft("产品A","2026-10-22T10:00",400200,300L,null),false,null);
        assertEquals(300,BankLedger.profit(p));assertEquals(Long.valueOf(300),BankLedger.week(p,LocalDate.of(2026,10,19)));
    }
    @Test public void missingWeeksAndFutureDailyAveragesStayMissing(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-19T10:00",400000,700L,null),false,null);
        BankLedger.apply(data,p.id,draft("产品A","2026-10-22T10:00",400000,700L,null),false,null);
        assertNull(BankLedger.week(p,LocalDate.of(2026,10,12)));assertNull(BankLedger.dailyAverage(p,LocalDate.of(2026,10,23)));
        assertNull(BankLedger.dailyAverage(p,LocalDate.of(2026,10,20)));
    }
    @Test public void changedProductRequiresExplicitConfirmationAndDoesNotEraseOldEvidence(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-12T10:00",400000,200L,null),false,null);
        ImportTest.rejects("名称",()->BankLedger.apply(data,p.id,draft("产品B","2026-10-20T10:00",600000,100L,null),false,null));
        assertEquals(1,p.observations.size());BankLedger.apply(data,p.id,draft("产品B","2026-10-20T10:00",600000,100L,null),true,null);
        assertEquals(2,p.observations.size());assertEquals("产品A",p.events.get(0).oldName);assertEquals("产品B",p.name);
        assertNull(p.events.get(0).closingProfit);assertNull(BankLedger.week(p,LocalDate.of(2026,10,19)));assertEquals(200,BankLedger.profit(p));
    }
    @Test public void settledSegmentIsCountedOnceAndNewProductBeginsSeparately(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-12T10:00",400000,200L,1000L),false,null);
        BankLedger.apply(data,p.id,draft("产品B","2026-10-20T10:00",401500,null,0L),true,1500L);
        assertEquals(1500,BankLedger.profit(p));assertEquals(1000L,p.observations.get(0).cumulative.longValue());
        BankLedger.apply(data,p.id,draft("产品B","2026-10-22T10:00",401600,100L,100L),false,null);assertEquals(1600,BankLedger.profit(p));
    }
    @Test public void completeCashSettlementExcludesAdditionalDepositsAndWithdrawals(){
        BankLedger.Observation o=new BankLedger.Observation();o.balance=400000;o.cumulative=1000L;
        assertEquals(Long.valueOf(1500),BankLedger.transitionTotal(o,500500,150000,50000));
        o.cumulative=null;assertNull(BankLedger.transitionTotal(o,500500,150000,50000));
    }
    @Test public void cashFlowEventDoesNotCreateProfitOrModifyAccountLedger(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-12T10:00",400000,null,null),false,null);
        BankLedger.flow(data,p,100000,true,LocalDateTime.of(2026,10,13,12,0));
        assertEquals(0,BankLedger.profit(p));assertEquals(0,data.entries.size());assertEquals("IN",p.events.get(0).kind);
    }
    @Test public void cumulativeEvidenceAndLaterWeeksNeverDoubleCountEarlierWeeks(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-12T10:00",400000,200L,1000L),false,null);
        BankLedger.apply(data,p.id,draft("产品A","2026-10-20T10:00",400300,300L,null),false,null);assertEquals(1300,BankLedger.profit(p));
    }
    @Test public void IDsRemainUniqueAcrossBankObservationsAndChangeEvents(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-12T10:00",400000,null,null),false,null);
        BankLedger.apply(data,p.id,draft("产品B","2026-10-20T10:00",400000,null,null),true,null);BankLedger.validate(p);
        assertNotEquals(p.observations.get(0).id,p.observations.get(1).id);assertNotEquals(p.events.get(0).id,p.observations.get(1).id);
    }
    @Test public void OCRPrefersLargeBalanceAndSeparatelyReadsWeeklyProfit(){
        List<FundRecognition.Line> lines=Arrays.asList(
            new FundRecognition.Line("代销 | 浦银理财鑫享利日申月赎5号A",100,150,600,180),
            new FundRecognition.Line("总金额(元)",300,100,450,120),
            new FundRecognition.Line("4,799.03",200,200,600,290),
            new FundRecognition.Line("+1.04",40,400,180,440),
            new FundRecognition.Line("本周收益(元)",30,450,190,480),
            new FundRecognition.Line("2.08%",650,400,800,440));
        BankLedger.Draft d=BankOcr.parse(lines);assertEquals("浦银理财鑫享利日申月赎5号A",d.name);assertEquals(Long.valueOf(479903),d.balance);assertNull(d.weekly);assertNull(d.cumulative);
    }
    @Test public void blackOrUnrelatedScreenshotCannotCreateAProduct(){
        ImportTest.rejects("产品名称",()->BankOcr.parse(Collections.emptyList()));
    }
    @Test public void unresolvedTransitionBreaksTheConfirmedProfitTrend(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product p=BankLedger.apply(data,0,draft("产品A","2026-10-12T10:00",400000,200L,1000L),false,null);
        BankLedger.apply(data,p.id,draft("产品B","2026-10-20T10:00",500000,100L,null),true,null);
        assertEquals(Long.valueOf(1000),BankLedger.profitSeries(p).get(LocalDate.of(2026,10,12)));
        assertNull(BankLedger.profitSeries(p).get(LocalDate.of(2026,10,20)));
        p.events.get(0).closingProfit=1500L;p.observations.get(1).cumulative=100L;
        assertEquals(Long.valueOf(1600),BankLedger.profitSeries(p).get(LocalDate.of(2026,10,20)));
    }
}
