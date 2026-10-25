package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class V463RulesTest {
    final LocalDate day=LocalDate.of(2026,10,19);
    Ledger.Data data(){return new Ledger.Data();}
    DebtLedger.Loan loan(Ledger.Data d,long amount){return DebtLedger.save(d,0,"借款",amount,day,"测试");}
    @Test public void borrowingAndRepaymentNeverCreateIncomeOrExpense(){
        Ledger.Data d=data();DebtLedger.Loan loan=loan(d,100000);DebtLedger.repay(d,loan.id,30000,day.plusDays(1),"首期");
        assertEquals(70000,DebtLedger.total(d,LocalDate.now()));assertTrue(d.entries.isEmpty());assertTrue(d.snapshots.isEmpty());
    }
    @Test public void recordingEditingAndRepayingDebtNeverChangeAssetBalances(){
        Ledger.Data d=data();Ledger.Account account=new Ledger.Account(d.nextId(),"账户","现金");account.created=day.atStartOfDay();account.opening=500000;d.accounts.add(account);
        Ledger.Snapshot snapshot=new Ledger.Snapshot(d.nextId(),day.atStartOfDay(),"原余额");snapshot.balances.put(account.id,500000L);d.snapshots.add(snapshot);
        long before=d.total();DebtLedger.Loan loan=loan(d,100000);assertEquals(before,d.total());
        DebtLedger.save(d,loan.id,"修改",200000,day,"");assertEquals(before,d.total());
        DebtLedger.repay(d,loan.id,200000,day,"还清");assertEquals(before,d.total());assertEquals(0,DebtLedger.total(d,LocalDate.now()));
        assertEquals(500000,d.balance(account));assertEquals(1,d.snapshots.size());assertTrue(d.entries.isEmpty());
    }
    @Test public void annualBalancesUseTheActualDates(){
        Ledger.Data d=data();DebtLedger.Loan loan=DebtLedger.save(d,0,"旧借款",100000,LocalDate.of(2024,1,1),"");
        DebtLedger.repay(d,loan.id,20000,LocalDate.of(2025,6,1),"");DebtLedger.repay(d,loan.id,30000,day,"");
        assertEquals(Long.valueOf(100000),DebtLedger.yearly(d,LocalDate.now()).get(2024));assertEquals(Long.valueOf(80000),DebtLedger.yearly(d,LocalDate.now()).get(2025));assertEquals(Long.valueOf(50000),DebtLedger.yearly(d,LocalDate.now()).get(2026));
        assertEquals(0,DebtLedger.balance(loan,LocalDate.of(2023,12,31)));
    }
    @Test public void fullyPaidLoansSortLastAndCanReopenAfterCorrection(){
        Ledger.Data d=data();DebtLedger.Loan a=loan(d,100000),b=loan(d,200000);DebtLedger.repay(d,b.id,200000,day,"全部");
        assertEquals(a.id,DebtLedger.sorted(d,LocalDate.now()).get(0).id);assertEquals(b.id,DebtLedger.sorted(d,LocalDate.now()).get(1).id);
        DebtLedger.save(d,b.id,"修正金额",220000,day,"");assertEquals(20000,DebtLedger.balance(b,LocalDate.now()));
    }
    @Test public void overpaymentAndImpossibleEditAreRejectedWithoutMutation(){
        Ledger.Data d=data();DebtLedger.Loan loan=loan(d,100000);DebtLedger.repay(d,loan.id,60000,day,"");
        ImportTest.rejects("不能超过",()->DebtLedger.repay(d,loan.id,40001,day,""));assertEquals(1,loan.payments.size());
        ImportTest.rejects("不能小于",()->DebtLedger.save(d,loan.id,"不可保存",50000,day,""));assertEquals("借款",loan.title);assertEquals(100000,loan.amount);
    }
    @Test public void repaymentDatesAndFutureLoansAreValidated(){
        Ledger.Data d=data();DebtLedger.Loan loan=loan(d,100000);
        ImportTest.rejects("借款日",()->DebtLedger.repay(d,loan.id,1,day.minusDays(1),""));
        ImportTest.rejects("借款日",()->DebtLedger.repay(d,loan.id,1,LocalDate.now().plusDays(1),""));
        ImportTest.rejects("请核对",()->DebtLedger.save(d,0,"未来",10000,LocalDate.now().plusDays(1),""));
        DebtLedger.repay(d,loan.id,1,day,"");
        ImportTest.rejects("早于",()->DebtLedger.save(d,loan.id,"修改日期",100000,day.plusDays(1),""));
        assertEquals(day,loan.at);
    }
    @Test public void debtAndRepaymentAndNotificationPreferenceSurviveBackup()throws Exception{
        Ledger.Data d=data();DebtLedger.Loan loan=loan(d,100000);DebtLedger.repay(d,loan.id,33333,day,"备注");d.settings.lossOnly=false;
        Ledger.Data restored=LedgerStore.decode(LedgerStore.encode(d));LedgerStore.validate(restored);
        assertEquals(66667,DebtLedger.total(restored,LocalDate.now()));assertEquals("备注",restored.debts.get(0).payments.get(0).note);assertFalse(restored.settings.lossOnly);
        assertEquals(11,new org.json.JSONObject(LedgerStore.encode(d)).getInt("version"));
    }
    @Test public void oldBackupDoesNotInventDebts()throws Exception{
        org.json.JSONObject json=new org.json.JSONObject(LedgerStore.encode(data()));json.put("version",7);json.remove("debts");
        Ledger.Data restored=LedgerStore.decode(json.toString());assertTrue(restored.debts.isEmpty());assertTrue(restored.settings.lossOnly);
    }
    @Test public void repeatedRepaymentsHaveDifferentImmutableIds(){
        Ledger.Data d=data();DebtLedger.Loan loan=loan(d,100000);DebtLedger.repay(d,loan.id,20000,day,"");DebtLedger.repay(d,loan.id,20000,day,"");assertNotEquals(loan.payments.get(0).id,loan.payments.get(1).id);
    }
    Ledger.Fund fund(long id,boolean loss){
        Ledger.Fund f=new Ledger.Fund();f.id=id;f.code=String.format("%06d",id);f.name="产品"+id;
        Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=100+id;buy.amount=100000;buy.at=day.minusDays(5).atTime(10,0);f.buys.add(buy);
        f.prices.put(day.minusDays(5),BigDecimal.ONE);f.prices.put(day,new BigDecimal(loss?"0.99":"1.02"));f.navDate=day.toString();f.nav=f.prices.get(day);FundEngine.resolve(f,new TradingCalendar());return f;
    }
    @Test public void oneLosingFundStillAlertsEvenWithPositivePortfolioReturn(){
        Ledger.Data d=data();d.funds.add(fund(1,false));d.funds.add(fund(2,true));
        FundNotice.Report report=FundNotice.report(d,day.atTime(23,0));assertNotNull(report);assertTrue(report.loss);assertTrue(report.total>0);assertTrue(report.losses.contains("产品2"));assertFalse(report.losses.contains("000002"));
    }
    @Test public void hiddenAndRemovedFundsNeverGenerateLossAlerts(){
        Ledger.Data d=data();Ledger.Fund f=fund(1,true);f.hidden=true;d.funds.add(f);assertNull(FundNotice.report(d,day.atTime(23,0)));
        f.hidden=false;FundEngine.remove(f,day.atTime(23,0));assertNull(FundNotice.report(d,day.atTime(23,0)));
    }
    @Test public void noIntradayEstimatesOrStaleAlerts(){
        Ledger.Data d=data();d.funds.add(fund(1,true));assertNull(FundNotice.report(d,day.atTime(14,59)));assertNull(FundNotice.report(d,day.plusDays(4).atTime(20,0)));
        assertNotNull(FundNotice.report(d,day.plusDays(2).atTime(20,0)));
    }
    @Test public void partialNavUpdateReportsStaleFundAsPending(){
        Ledger.Data d=data();Ledger.Fund updated=fund(1,true),unupdated=fund(2,false);unupdated.navDate=day.minusDays(1).toString();
        updated.prices.put(day.minusDays(1),BigDecimal.ONE);updated.prices.put(day,new BigDecimal("0.99"));unupdated.prices.put(day.minusDays(1),BigDecimal.ONE);unupdated.nav=new BigDecimal("1.02");
        updated.navDate=day.toString();FundEngine.resolve(updated,new TradingCalendar());FundEngine.resolve(unupdated,new TradingCalendar());d.funds.add(updated);d.funds.add(unupdated);
        FundNotice.Report report=FundNotice.report(d,day.atTime(20,0));assertNotNull(report);assertEquals(1,report.counted);assertEquals(1,report.missing);assertTrue(report.loss);
    }
    @Test public void notificationSignatureIsStableAcrossPortfolioOrder(){
        Ledger.Data d=data();d.funds.add(fund(1,true));d.funds.add(fund(2,true));String signature=FundNotice.report(d,day.atTime(23,0)).signature;
        java.util.Collections.reverse(d.funds);assertEquals(signature,FundNotice.report(d,day.atTime(23,0)).signature);
    }
}
