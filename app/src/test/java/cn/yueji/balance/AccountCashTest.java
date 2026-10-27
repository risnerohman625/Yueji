package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class AccountCashTest {
    static LocalDateTime at(String value){return LocalDateTime.parse(value);}
    Ledger.Data data(){
        Ledger.Data d=new Ledger.Data();d.settings.autoRefresh=false;
        Ledger.Account a=new Ledger.Account(1,"支付宝","支付宝");a.created=at("2026-09-01T00:00");a.opening=100000;
        Ledger.Account b=new Ledger.Account(2,"中国银行","中国银行");b.created=a.created;b.opening=200000;d.accounts.add(a);d.accounts.add(b);
        snap(d,10,"2026-09-01T00:00",1,100000,2,200000);return d;
    }
    void snap(Ledger.Data d,long id,String time,long...values){
        Ledger.Snapshot s=new Ledger.Snapshot(id,at(time),"真实余额");
        for(int i=0;i<values.length;i+=2)s.balances.put(values[i],values[i+1]);s.partial=values.length<4;d.snapshots.add(s);d.sort();
    }
    Ledger.Entry entry(Ledger.Data d,long id,String kind,long amount,String time,long account){
        Ledger.Entry e=new Ledger.Entry(id,kind.equals(Ledger.INCOME),amount,at(time),kind.equals(Ledger.INCOME)?"工资":"差旅垫付","",account);
        e.kind=kind;if(e.income)e.category="工资";e.cashLinked=true;d.entries.add(e);return e;
    }
    Ledger.Receipt receipt(Ledger.Entry e,long amount,String time,long account){
        Ledger.Receipt r=new Ledger.Receipt(amount,at(time));r.accountId=account;r.cashLinked=true;e.receipts.add(r);e.status=e.reimbursementStatus();return r;
    }
    @Test public void incomeAddsBalanceAndIsExcludedFromDifferenceExpense(){
        Ledger.Data d=data();entry(d,20,Ledger.INCOME,50000,"2026-09-05T12:00",1);AccountCash.rebuild(d);
        assertEquals(150000,d.balance(d.accounts.get(0)));assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);
        assertEquals(50000,Ledger.income(d,2026));assertEquals(2,d.snapshots.size());Ledger.Snapshot generated=d.snapshots.get(1);
        assertTrue(generated.cashGenerated);assertEquals(Collections.singleton(1L),generated.cashAccounts);assertEquals(1,generated.balances.size());
        snap(d,30,"2026-10-01T00:00",1,140000,2,200000);AccountCash.rebuild(d);assertEquals(10000,Ledger.month(d,YearMonth.of(2026,9)).expense);
    }
    @Test public void crossMonthAdvanceAndReturnOnDifferentAccountsNeverBecomeExpenseOrIncome(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.REIMBURSE,50000,"2026-09-05T12:00",1);receipt(e,50000,"2026-10-22T12:00",2);
        snap(d,30,"2026-10-01T00:00",1,50000,2,200000);AccountCash.rebuild(d);
        assertEquals(50000,d.balance(d.accounts.get(0)));assertEquals(250000,d.balance(d.accounts.get(1)));assertEquals(300000,d.total());
        assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);assertEquals(0,Ledger.month(d,YearMonth.of(2026,10)).expense);
        assertEquals(0,Ledger.income(d,2026));assertEquals(250000L,(long)AssetHistory.valueAt(d,at("2026-09-05T12:00")));
        assertEquals(300000L,(long)AssetHistory.valueAt(d,at("2026-10-22T12:00")));
    }
    @Test public void sameMonthOctoberExampleCreatesTwoRealAccountTimelineRows(){
        Ledger.Data d=data();snap(d,30,"2026-10-01T00:00",1,100000,2,200000);
        Ledger.Entry e=entry(d,20,Ledger.REIMBURSE,50000,"2026-10-05T10:00",1);receipt(e,50000,"2026-10-22T15:00",2);AccountCash.rebuild(d);
        assertEquals(4,d.snapshots.size());assertEquals(0,Ledger.month(d,YearMonth.of(2026,10)).expense);assertEquals(300000,d.total());
        assertEquals(250000L,(long)AssetHistory.valueAt(d,at("2026-10-06T00:00")));
    }
    @Test public void multipleReceiptsUseTheirOwnAccountsAndDoNotRepeatPrincipal(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.REIMBURSE,50000,"2026-09-05T10:00",1);
        receipt(e,20000,"2026-09-10T10:00",1);receipt(e,30000,"2026-09-15T10:00",2);AccountCash.rebuild(d);
        assertEquals(70000,d.balance(d.accounts.get(0)));assertEquals(230000,d.balance(d.accounts.get(1)));assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);
        String once=LedgerStore.encode(d);AccountCash.rebuild(d);assertEquals(once,LedgerStore.encode(d));
    }
    @Test public void changingAmountAccountAndTimeRebuildsOnlyOwnedObservations(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);AccountCash.rebuild(d);
        e.amount=80000;e.at=at("2026-09-06T11:00");e.accountId=2;AccountCash.rebuild(d);
        assertEquals(100000,d.balance(d.accounts.get(0)));assertEquals(280000,d.balance(d.accounts.get(1)));
        assertEquals(2,d.snapshots.size());assertEquals(e.at,d.snapshots.get(1).at);assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);
    }
    @Test public void deletingReceiptOrEntryRemovesOnlyItsOwnedBalanceEffects(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.REIMBURSE,50000,"2026-09-05T10:00",1);receipt(e,50000,"2026-09-10T10:00",2);AccountCash.rebuild(d);
        e.receipts.clear();e.status=e.reimbursementStatus();AccountCash.rebuild(d);assertEquals(250000,d.total());assertEquals(2,d.snapshots.size());
        d.entries.clear();AccountCash.rebuild(d);assertEquals(300000,d.total());assertEquals(1,d.snapshots.size());
    }
    @Test public void actualObservationResetsBalanceAndIsNeverRewrittenByEditingAnOlderIncome(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);snap(d,30,"2026-09-06T10:00",1,140000);AccountCash.rebuild(d);
        assertEquals(140000,d.balance(d.accounts.get(0)));e.amount=60000;AccountCash.rebuild(d);assertEquals(140000,d.balance(d.accounts.get(0)));assertEquals(20000,Ledger.month(d,YearMonth.of(2026,9)).expense);
        assertEquals(140000L,(long)d.snapshots.stream().filter(s->s.id==30).findFirst().get().balances.get(1L));
    }
    @Test public void cashAtSameTimestampAsActualBalanceIsNotAddedTwice(){
        Ledger.Data d=data();entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);snap(d,30,"2026-09-05T10:00",1,150000);AccountCash.rebuild(d);
        assertEquals(150000,d.balance(d.accounts.get(0)));assertEquals(2,d.snapshots.size());assertFalse(d.snapshots.get(1).cashGenerated);
        assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);
    }
    @Test public void simultaneousCashFlowsMergeIntoSinglePostTransactionSnapshot(){
        Ledger.Data d=data();entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);entry(d,21,Ledger.REIMBURSE,20000,"2026-09-05T10:00",1);
        entry(d,22,Ledger.INCOME,30000,"2026-09-05T10:00",2);AccountCash.rebuild(d);
        assertEquals(2,d.snapshots.size());assertEquals(130000,d.balance(d.accounts.get(0)));assertEquals(230000,d.balance(d.accounts.get(1)));
        assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);
    }
    @Test public void sameTimestampManualPartialRetainsOtherAccountsAutomaticCell(){
        Ledger.Data d=data();entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);snap(d,30,"2026-09-05T10:00",2,195000);AccountCash.rebuild(d);
        assertEquals(2,d.snapshots.size());Ledger.Snapshot s=d.snapshots.get(1);assertFalse(s.cashGenerated);assertEquals(150000L,(long)s.balances.get(1L));assertEquals(195000L,(long)s.balances.get(2L));
        d.entries.clear();AccountCash.rebuild(d);assertEquals(1,s.balances.size());assertEquals(195000L,(long)s.balances.get(2L));
    }
    @Test public void manualVerificationOfGeneratedCellPreventsFutureRewrite(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);AccountCash.rebuild(d);
        MonthLedger.updateAccount(d,1,e.at,160000);AccountCash.rebuild(d);e.amount=60000;AccountCash.rebuild(d);
        assertEquals(160000,d.balance(d.accounts.get(0)));assertFalse(d.snapshots.get(1).cashGenerated);assertEquals(0,d.snapshots.get(1).cashAccounts.size());
    }
    @Test public void removingManualVerificationRevealsCalculatedBalanceAgain(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);AccountCash.rebuild(d);
        MonthLedger.updateAccount(d,1,e.at,160000);d.snapshots.removeIf(s->s.at.equals(e.at));AccountCash.rebuild(d);
        assertEquals(150000,d.balance(d.accounts.get(0)));assertTrue(d.snapshots.get(1).cashGenerated);
    }
    @Test public void oldUnassignedAndAssignedImportedEntriesDoNotGenerateBalances(){
        Ledger.Data d=data();Ledger.Entry a=entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",0);a.cashLinked=false;
        Ledger.Entry b=entry(d,21,Ledger.REIMBURSE,20000,"2026-09-06T10:00",1);b.cashLinked=false;
        AccountCash.rebuild(d);assertEquals(1,d.snapshots.size());assertEquals(300000,d.total());
    }
    @Test public void newReceiptOnLegacyUnassignedReimbursementOnlyAppliesTheNewCredit(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.REIMBURSE,50000,"2026-09-05T10:00",0);e.cashLinked=false;receipt(e,50000,"2026-10-22T12:00",2);
        snap(d,30,"2026-10-01T00:00",1,100000,2,200000);AccountCash.rebuild(d);assertEquals(100000,d.balance(d.accounts.get(0)));assertEquals(250000,d.balance(d.accounts.get(1)));assertEquals(0,Ledger.month(d,YearMonth.of(2026,10)).expense);
    }
    @Test public void generatedEventsDoNotMoveImportedHistoryCutoff(){
        Ledger.Data d=data();Ledger.HistoryMonth h=new Ledger.HistoryMonth(YearMonth.of(2026,9),9000,at("2026-10-01T00:00"));d.history.add(h);
        d.snapshots.clear();entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",1);AccountCash.rebuild(d);
        assertEquals(h.cutoff,Ledger.historyCutoff(d,h));assertEquals(9000,Ledger.month(d,YearMonth.of(2026,9)).expense);
    }
    @Test public void monthBoundaryEventsUsePostTransactionRightInclusiveIntervals(){
        Ledger.Data d=data();entry(d,20,Ledger.INCOME,50000,"2026-10-01T00:00",1);AccountCash.rebuild(d);
        assertEquals(0,Ledger.month(d,YearMonth.of(2026,9)).expense);assertEquals(0,Ledger.month(d,YearMonth.of(2026,10)).expense);
        assertEquals(50000,Ledger.income(d,YearMonth.of(2026,10)));
    }
    @Test public void backupRoundTripRetainsCashOwnershipAndRemainsIdempotent()throws Exception{
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.REIMBURSE,50000,"2026-09-05T10:00",1);receipt(e,50000,"2026-10-22T10:00",2);
        LedgerStore.validate(d);String original=LedgerStore.encode(d);assertEquals(13,new org.json.JSONObject(original).getInt("version"));
        Ledger.Data restored=LedgerStore.decode(original);LedgerStore.validate(restored);assertEquals(original,LedgerStore.encode(restored));assertTrue(restored.entries.get(0).cashLinked);assertTrue(restored.entries.get(0).receipts.get(0).cashLinked);
    }
    @Test public void missingAccountAndInsufficientBalanceAreRejected(){
        Ledger.Data d=data();Ledger.Entry e=entry(d,20,Ledger.INCOME,50000,"2026-09-05T10:00",0);
        try{AccountCash.rebuild(d);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("账户"));}
        e.accountId=1;e.kind=Ledger.REIMBURSE;e.income=false;e.amount=100001;
        try{AccountCash.rebuild(d);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("余额"));}
    }
    @Test public void historicalEntryBeforeOpeningIsRejectedRatherThanGuessingBalance(){
        Ledger.Data d=data();entry(d,20,Ledger.INCOME,50000,"2026-08-05T10:00",1);
        try{AccountCash.rebuild(d);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("补录"));}
    }
}
