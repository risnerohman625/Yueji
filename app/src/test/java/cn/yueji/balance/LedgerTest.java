package cn.yueji.balance;

import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import static org.junit.Assert.*;

public class LedgerTest {
    private Ledger.Data data() { Ledger.Data d=new Ledger.Data();d.accounts.add(new Ledger.Account(1,"银行","银行"));return d; }
    private Ledger.Snapshot snapshot(Ledger.Data d,String at,long yuan) {Ledger.Snapshot s=new Ledger.Snapshot(d.snapshots.size()+1,LocalDateTime.parse(at),"");s.balances.put(1L,yuan*100);d.snapshots.add(s);d.sort();return s;}
    private void income(Ledger.Data d,String at,long yuan) {d.entries.add(new Ledger.Entry(d.entries.size()+1,true,yuan*100,LocalDateTime.parse(at),"补贴","",1));}
    @Test public void incomeIncreaseStillProducesConsumption() {
        Ledger.Data d=data();snapshot(d,"2026-10-01T00:00",10000);snapshot(d,"2026-10-15T20:00",11000);income(d,"2026-10-10T12:00",2000);
        Ledger.Report r=Ledger.month(d,YearMonth.of(2026,10));assertTrue(r.hasDaily());assertEquals(100000,r.expense);assertEquals(15,r.days);assertEquals(6667,r.daily());
    }
    @Test public void fullMonthIncludesLastDayIncomeAndUsesAllDays() {
        Ledger.Data d=data();snapshot(d,"2026-01-01T00:00",10000);snapshot(d,"2026-02-01T00:00",9900);income(d,"2026-01-31T22:00",3000);income(d,"2026-02-01T00:00",9000);
        Ledger.Report r=Ledger.month(d,YearMonth.of(2026,1));assertTrue(r.closed);assertEquals(310000,r.expense);assertEquals(31,r.days);assertEquals(10000,r.daily());
    }
    @Test public void nextMonthIncomeAtMidnightBelongsToNextMonth() {
        Ledger.Data d=data();snapshot(d,"2026-01-01T00:00",10000);snapshot(d,"2026-02-01T00:00",9000);snapshot(d,"2026-02-15T20:00",10000);income(d,"2026-02-01T00:00",2000);
        assertEquals(100000,Ledger.month(d,YearMonth.of(2026,1)).expense);assertEquals(100000,Ledger.month(d,YearMonth.of(2026,2)).expense);
    }
    @Test public void extraChecksDoNotDoubleCountIncome() {
        Ledger.Data d=data();snapshot(d,"2026-10-01T00:00",10000);snapshot(d,"2026-10-08T18:00",11000);snapshot(d,"2026-10-15T20:00",10500);income(d,"2026-10-06T12:00",2000);
        assertEquals(150000,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void largeExpenseRecordDoesNotChangeBalanceCalculation() {
        Ledger.Data d=data();snapshot(d,"2026-10-01T00:00",10000);snapshot(d,"2026-10-15T20:00",5000);d.entries.add(new Ledger.Entry(1,false,500000,LocalDateTime.parse("2026-10-10T12:00"),"电脑","",0));assertEquals(500000,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void accountTransfersCancelOut() {
        Ledger.Data d=data();d.accounts.add(new Ledger.Account(2,"微信","微信"));Ledger.Snapshot a=snapshot(d,"2026-10-01T00:00",10000);a.balances.put(2L,0L);Ledger.Snapshot b=snapshot(d,"2026-10-15T20:00",7000);b.balances.put(2L,300000L);assertEquals(0,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void addingAccountEstablishesBaselineWithoutIncome() {
        Ledger.Data d=data();Ledger.Account added=new Ledger.Account(2,"微信","微信");added.opening=500000;d.accounts.add(added);Ledger.Snapshot a=snapshot(d,"2026-10-01T00:00",10000),b=snapshot(d,"2026-10-08T18:00",9000),c=snapshot(d,"2026-10-15T20:00",8500);b.balances.put(2L,500000L);c.balances.put(2L,480000L);assertEquals(170000,Ledger.month(d,YearMonth.of(2026,10)).expense);assertEquals(100000,Ledger.expenseBetween(a,b,d));
    }
    @Test public void incomeWithoutAccountIsIncludedInGlobalDifference() {
        Ledger.Data d=data();snapshot(d,"2026-10-01T00:00",10000);snapshot(d,"2026-10-15T20:00",11000);d.entries.add(new Ledger.Entry(1,true,200000,LocalDateTime.parse("2026-10-06T12:00"),"收入","",0));assertEquals(100000,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void missingMonthStartNeverInventsDailyAverage() {
        Ledger.Data d=data();snapshot(d,"2026-10-08T18:00",10000);snapshot(d,"2026-10-15T20:00",9000);Ledger.Report r=Ledger.month(d,YearMonth.of(2026,10));assertTrue(r.usable());assertFalse(r.baseline);assertFalse(r.hasDaily());assertEquals(100000,r.expense);
    }
    @Test public void missingBoundaryDoesNotAllocateAcrossMonths() {
        Ledger.Data d=data();snapshot(d,"2026-09-15T20:00",10000);snapshot(d,"2026-10-15T20:00",8000);
        assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);assertTrue(Ledger.month(d,YearMonth.of(2026,9)).hasData());
        assertEquals(0,Ledger.month(d,YearMonth.of(2026,10)).expense);assertFalse(Ledger.month(d,YearMonth.of(2026,10)).hasDaily());
    }
    @Test public void negativeIntervalFlagsMissingIncomeEvenWhenMonthlySumPositive() {
        Ledger.Data d=data();snapshot(d,"2026-10-01T00:00",10000);snapshot(d,"2026-10-08T18:00",11000);snapshot(d,"2026-10-15T20:00",8000);Ledger.Report r=Ledger.month(d,YearMonth.of(2026,10));assertTrue(r.anomaly);assertFalse(r.usable());income(d,"2026-10-06T12:00",2000);assertFalse(Ledger.month(d,YearMonth.of(2026,10)).anomaly);assertEquals(400000,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void leapYearAndYearTransition() {
        Ledger.Data d=data();snapshot(d,"2024-02-01T00:00",10000);snapshot(d,"2024-03-01T00:00",7100);Ledger.Report r=Ledger.month(d,YearMonth.of(2024,2));assertEquals(29,r.days);assertEquals(10000,r.daily());snapshot(d,"2025-01-01T00:00",5000);snapshot(d,"2025-02-01T00:00",1900);assertEquals(31,Ledger.month(d,YearMonth.of(2025,1)).days);
    }
    @Test public void firstOfMonthRequiresMidnightWhile15thAllowsExtraChecks() {
        Ledger.Data d=data();snapshot(d,"2026-10-01T09:00",10000);snapshot(d,"2026-10-15T20:00",9000);assertEquals(1,Ledger.missingNodes(d,LocalDate.of(2026,10,19)).size());assertEquals(LocalDate.of(2026,10,1),Ledger.missingNodes(d,LocalDate.of(2026,10,19)).get(0));
    }
    @Test public void moneyPreservesCents() {assertEquals(1001,Ledger.parseMoney("10.01",true));assertEquals("10.01",Ledger.decimal(1001));assertEquals(0,Ledger.parseMoney("0",false));}
    @Test(expected=IllegalArgumentException.class) public void moneyRejectsThirdDecimal() {Ledger.parseMoney("10.001",false);}
    @Test(expected=IllegalArgumentException.class) public void moneyRejectsNegative() {Ledger.parseMoney("-1",false);}
    @Test(expected=IllegalArgumentException.class) public void incomeRejectsZero() {Ledger.parseMoney("0",true);}
    @Test public void incomeAnnualTotalUsesOccurredYear() {Ledger.Data d=data();income(d,"2025-12-31T12:00",8000);income(d,"2026-01-01T12:00",1000);assertEquals(800000,Ledger.income(d,2025));assertEquals(100000,Ledger.income(d,2026));}
    private Ledger.Entry advance(Ledger.Data d,String date,long amount) {Ledger.Entry e=new Ledger.Entry(d.entries.size()+1,false,amount*100,LocalDateTime.parse(date),"出差","",0);e.kind=Ledger.REIMBURSE;d.entries.add(e);return e;}
    @Test public void advanceExcludedInOriginalMonthAndReceiptInFollowingMonth(){
        Ledger.Data d=data();snapshot(d,"2026-08-01T00:00",10000);snapshot(d,"2026-09-01T00:00",8000);snapshot(d,"2026-10-01T00:00",8000);
        Ledger.Entry e=advance(d,"2026-08-10T12:00",1000);e.receipts.add(new Ledger.Receipt(100000,LocalDateTime.parse("2026-09-10T12:00")));
        assertEquals(100000,Ledger.month(d,YearMonth.of(2026,8)).expense);assertEquals(100000,Ledger.month(d,YearMonth.of(2026,9)).expense);
        assertEquals(0,Ledger.income(d,2026));
    }
    @Test public void splitReceiptsBelongToTheirActualMonths(){
        Ledger.Data d=data();snapshot(d,"2026-07-01T00:00",10000);snapshot(d,"2026-08-01T00:00",8000);snapshot(d,"2026-09-01T00:00",7900);snapshot(d,"2026-10-01T00:00",8000);
        Ledger.Entry e=advance(d,"2026-07-10T12:00",1000);e.receipts.add(new Ledger.Receipt(40000,LocalDateTime.parse("2026-08-10T12:00")));e.receipts.add(new Ledger.Receipt(60000,LocalDateTime.parse("2026-09-10T12:00")));
        assertEquals(100000,Ledger.month(d,YearMonth.of(2026,7)).expense);assertEquals(50000,Ledger.month(d,YearMonth.of(2026,8)).expense);assertEquals(50000,Ledger.month(d,YearMonth.of(2026,9)).expense);
        assertEquals("已报销",e.reimbursementStatus());
    }
    @Test public void historyBridgeContinuesFromOctober19WithoutOctober1Balances(){
        Ledger.Data d=data();snapshot(d,"2026-10-19T12:00",10000);snapshot(d,"2026-10-20T12:00",10500);
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),200000,LocalDateTime.parse("2026-10-19T12:00")));
        income(d,"2026-10-10T12:00",2000);income(d,"2026-10-19T18:00",1000);
        Ledger.Report r=Ledger.month(d,YearMonth.of(2026,10));assertEquals(250000,r.expense);assertEquals(20,r.days);assertTrue(r.hasDaily());assertEquals(12500,r.daily());
    }
    @Test public void fullImportedMonthOverridesSnapshotsAndDoesNotAddHistoricalIncome(){
        Ledger.Data d=data();snapshot(d,"2026-09-01T00:00",10000);snapshot(d,"2026-10-01T00:00",5000);income(d,"2026-09-10T12:00",8000);
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,9),300000,LocalDateTime.parse("2026-10-01T00:00")));
        assertEquals(300000,Ledger.month(d,YearMonth.of(2026,9)).expense);assertEquals(800000,Ledger.income(d,2026));assertEquals(300000,(long)Ledger.yearExpense(d,2026));
    }
    @Test public void historyBasisCanExcludePreviouslyIncludedReimbursement(){
        Ledger.Data d=data();Ledger.HistoryMonth h=new Ledger.HistoryMonth(YearMonth.of(2026,8),300000,LocalDateTime.parse("2026-09-01T00:00"));h.includesAdvances=true;d.history.add(h);advance(d,"2026-08-10T12:00",600);
        assertEquals(240000,Ledger.month(d,YearMonth.of(2026,8)).expense);h.includesAdvances=false;assertEquals(300000,Ledger.month(d,YearMonth.of(2026,8)).expense);
    }
    @Test public void importedOldAdvanceAndNewReceiptDoNotCreateNewSpending(){
        Ledger.Data d=data();snapshot(d,"2026-10-19T12:00",10000);snapshot(d,"2026-10-20T12:00",10500);d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),200000,LocalDateTime.parse("2026-10-19T12:00")));
        Ledger.Entry e=advance(d,"2026-09-10T12:00",1000);e.receipts.add(new Ledger.Receipt(100000,LocalDateTime.parse("2026-10-19T18:00")));assertEquals(250000,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void investmentTransfersAreExcludedFromLivingExpense(){
        Ledger.Data d=data();snapshot(d,"2026-09-01T00:00",10000);snapshot(d,"2026-10-01T00:00",6500);Ledger.Entry buy=new Ledger.Entry(1,false,400000,LocalDateTime.parse("2026-09-10T12:00"),"申购","",0);buy.kind=Ledger.INVEST_OUT;d.entries.add(buy);
        Ledger.Entry sell=new Ledger.Entry(2,false,100000,LocalDateTime.parse("2026-09-20T12:00"),"赎回","",0);sell.kind=Ledger.INVEST_IN;d.entries.add(sell);assertEquals(50000,Ledger.month(d,YearMonth.of(2026,9)).expense);assertEquals(0,Ledger.income(d,2026));
    }
    @Test public void fundMoneyAndReturnsUseDecimalArithmetic(){
        Ledger.Fund f=new Ledger.Fund();f.shares=new java.math.BigDecimal("123.45");f.nav=new java.math.BigDecimal("1.2345");f.previous=new java.math.BigDecimal("1.2245");f.cost=15000;
        assertEquals(15240,f.value());assertEquals(240,f.profit());assertEquals(123,f.dayProfit());
    }
    @Test public void incomeCategoriesOmitEmptyOnesAndSortByAmount(){
        Ledger.Data d=data();
        Ledger.Entry a=new Ledger.Entry(1,true,100000,LocalDateTime.parse("2026-10-01T12:00"),"助研费","",0);a.category="助研费";d.entries.add(a);
        Ledger.Entry b=new Ledger.Entry(2,true,300000,LocalDateTime.parse("2026-10-02T12:00"),"奖学金","",0);b.category="奖学金";d.entries.add(b);
        Ledger.Entry c=new Ledger.Entry(3,true,50000,LocalDateTime.parse("2026-10-03T12:00"),"助研费","",0);c.category="助研费";d.entries.add(c);
        java.util.List<String> used=new java.util.ArrayList<>(Ledger.incomeCategories(d,true).keySet());
        assertEquals(java.util.Arrays.asList("奖学金","助研费"),used);
        assertEquals(150000L,(long)Ledger.incomeCategories(d,true).get("助研费"));
        assertEquals(5,Ledger.incomeCategories(d,false).size());
        assertTrue(Ledger.incomeCategories(data(),true).isEmpty());
    }
    interface Run {void go();}
    private void assertRejected(Run run){try{run.go();fail("should be rejected");}catch(IllegalArgumentException expected){}}
    @Test public void csvDatesAreStrictSlashFormats(){
        assertEquals(LocalDate.of(2026,10,21),Ledger.parseCsvDate("2026/10/21"));
        assertEquals(LocalDate.of(2025,5,5),Ledger.parseCsvDate("2025/5/5"));
        assertEquals(YearMonth.of(2026,10),Ledger.parseCsvMonth("2026/10"));
        assertEquals(YearMonth.of(2022,8),Ledger.parseCsvMonth("2022/8"));assertEquals(YearMonth.of(2026,10),Ledger.parseCsvMonth("2026/10/01"));assertEquals(YearMonth.of(2026,10),Ledger.parseCsvMonth("2026/10/1"));
        assertEquals(LocalDateTime.of(2026,10,21,12,6),Ledger.parseCsvMinute("2026/10/21 12:06"));
        assertEquals(LocalDateTime.of(2026,1,5,9,5),Ledger.parseCsvMinute("2026/1/5 9:05"));
        for(String bad:new String[]{"2026-10-21","2026年10月21日","Aug-22","20261021","26/10/21","2026/10/21 12:06","","2026/02/30","1999/1/1"})assertRejected(()->Ledger.parseCsvDate(bad));
        for(String bad:new String[]{"2026-10","Aug-22","2026/13","2026/10/21","2026/8/22","2026/10/32","2026/02/31","2026年10月","1999/12","2026-10-21"})assertRejected(()->Ledger.parseCsvMonth(bad));
        for(String bad:new String[]{"2026/10/21","2026-10-21 12:06","2026/10/21 12:06:30","2026/10/21  12:06","2026/10/21 12-06","2026/02/30 10:00","2026/10/21 25:00"})assertRejected(()->Ledger.parseCsvMinute(bad));
        assertEquals("2026/10/05",Ledger.csvDate(LocalDate.of(2026,10,5)));assertEquals("2026/10",Ledger.csvMonth(YearMonth.of(2026,10)));assertEquals("2026/10/21 09:05",Ledger.csvMinute(LocalDateTime.of(2026,10,21,9,5)));
    }
    private Ledger.HistoryMonth fullMonth(Ledger.Data d,String month,long yuan){YearMonth m=YearMonth.parse(month);Ledger.HistoryMonth h=new Ledger.HistoryMonth(m,yuan*100,m.plusMonths(1).atDay(1).atStartOfDay());d.history.add(h);return h;}
    @Test public void newestImportedMonthContinuesFromFirstCheckInsideThatMonth(){
        Ledger.Data d=data();snapshot(d,"2026-10-19T12:00",10000);snapshot(d,"2026-10-20T12:00",10500);fullMonth(d,"2026-10",2000);
        income(d,"2026-10-10T12:00",2000);income(d,"2026-10-19T18:00",1000);
        Ledger.Report r=Ledger.month(d,YearMonth.of(2026,10));assertEquals(250000,r.expense);assertEquals(20,r.days);assertTrue(r.hasDaily());assertEquals(12500,r.daily());assertFalse(r.closed);
    }
    @Test public void firstCheckNeedNotBeOnTheFirstWhenItSharesTheNewestImportedMonth(){
        Ledger.Data d=data();fullMonth(d,"2026-09",3000);fullMonth(d,"2026-10",1200);snapshot(d,"2026-10-07T08:30",8000);snapshot(d,"2026-10-15T20:00",7500);
        assertEquals(300000,Ledger.month(d,YearMonth.of(2026,9)).expense);assertTrue(Ledger.month(d,YearMonth.of(2026,9)).closed);
        Ledger.Report r=Ledger.month(d,YearMonth.of(2026,10));assertEquals(120000+50000,r.expense);assertEquals(15,r.days);assertTrue(r.usable());
    }
    @Test public void firstCheckInALaterMonthKeepsImportedMonthWholeAndStartsFresh(){
        Ledger.Data d=data();fullMonth(d,"2026-08",1500);snapshot(d,"2026-10-20T22:00",8000);snapshot(d,"2026-10-25T22:00",7000);
        Ledger.Report aug=Ledger.month(d,YearMonth.of(2026,8));assertTrue(aug.closed);assertEquals(31,aug.days);assertEquals(150000,aug.expense);
        Ledger.Report oct=Ledger.month(d,YearMonth.of(2026,10));assertEquals(100000,oct.expense);assertFalse(oct.baseline);assertFalse(oct.hasDaily());assertTrue(oct.usable());assertTrue(oct.status().contains("2026-10-20"));
        assertFalse(Ledger.month(d,YearMonth.of(2026,9)).hasData());
    }
    @Test public void onlyTheNewestImportedMonthIsBridged(){
        Ledger.Data d=data();fullMonth(d,"2026-08",1500);fullMonth(d,"2026-09",1800);snapshot(d,"2026-09-12T10:00",9000);snapshot(d,"2026-09-30T10:00",8000);
        assertEquals(150000,Ledger.month(d,YearMonth.of(2026,8)).expense);assertTrue(Ledger.month(d,YearMonth.of(2026,8)).closed);
        Ledger.Report sep=Ledger.month(d,YearMonth.of(2026,9));assertEquals(180000+100000,sep.expense);assertEquals(30,sep.days);
    }
    @Test public void importedCurrentMonthWithoutAnyCheckIsInProgress(){
        Ledger.Data d=data();YearMonth now=YearMonth.now();fullMonth(d,now.toString(),1234);
        Ledger.Report r=Ledger.month(d,now);assertTrue(r.usable());assertFalse(r.closed);assertEquals(123400,r.expense);assertEquals(LocalDate.now().getDayOfMonth(),r.days);assertTrue(r.hasDaily());
    }
    @Test public void bridgedMonthDoesNotHideMissingCheckNodes(){
        Ledger.Data d=data();fullMonth(d,"2026-10",2000);snapshot(d,"2026-10-03T10:00",10000);
        assertEquals(java.util.Collections.singletonList(LocalDate.of(2026,10,15)),Ledger.missingNodes(d,LocalDate.of(2026,10,19)));
    }
    @Test public void legacyBridgeWithExplicitCutoffStillWorks(){
        Ledger.Data d=data();snapshot(d,"2026-10-05T12:00",9000);snapshot(d,"2026-10-19T12:00",10000);snapshot(d,"2026-10-20T12:00",9500);
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),200000,LocalDateTime.parse("2026-10-19T12:00")));
        assertEquals(250000,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void accountAddedAfterTheCheckDateStartsAtThatCheck(){
        Ledger.Data d=data();d.accounts.get(0).created=LocalDateTime.parse("2026-10-01T00:00");Ledger.Snapshot first=snapshot(d,"2026-10-19T12:00",10000);
        Ledger.Account added=new Ledger.Account(2,"微信","微信");added.created=LocalDateTime.parse("2026-10-21T10:00");added.opening=500000;d.accounts.add(added);
        Ledger.Snapshot s=snapshot(d,"2026-10-20T22:00",9000);s.balances.put(2L,480000L);
        Ledger.pullAccountsBack(d,s);assertEquals(s.at,added.created);assertEquals(480000,added.opening);
        assertEquals(100000,Ledger.expenseBetween(first,s,d));
        Ledger.Snapshot later=snapshot(d,"2026-10-22T09:00",8800);later.balances.put(2L,480000L);Ledger.pullAccountsBack(d,later);assertEquals(s.at,added.created);
    }
    @Test public void accountCreatedBeforeTheCheckKeepsItsTypedOpening(){
        Ledger.Data d=data();d.accounts.get(0).created=LocalDateTime.parse("2026-10-01T00:00");Ledger.Snapshot first=snapshot(d,"2026-10-19T12:00",10000);
        Ledger.Account added=new Ledger.Account(2,"微信","微信");added.created=LocalDateTime.parse("2026-10-21T10:00");added.opening=500000;d.accounts.add(added);
        Ledger.Snapshot s=snapshot(d,"2026-10-22T09:00",9000);s.balances.put(2L,480000L);Ledger.pullAccountsBack(d,s);
        assertEquals(500000,added.opening);assertEquals(120000,Ledger.expenseBetween(first,s,d));
    }

    @Test public void firstCheckAtMidnightOnTheFirstLeavesTheImportedMonthWhole(){
        Ledger.Data d=data();snapshot(d,"2026-09-01T00:00",10000);snapshot(d,"2026-10-01T00:00",5000);fullMonth(d,"2026-09",3000);
        Ledger.Report r=Ledger.month(d,YearMonth.of(2026,9));assertEquals(300000,r.expense);assertTrue(r.closed);
    }
}
