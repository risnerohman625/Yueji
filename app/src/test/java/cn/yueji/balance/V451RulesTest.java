package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class V451RulesTest {
    Ledger.Data base(){
        Ledger.Data d=new Ledger.Data();for(int i=1;i<=2;i++){Ledger.Account a=new Ledger.Account(i,"账户"+i,"其他");a.created=LocalDateTime.of(2026,10,1,0,0);a.opening=500000;d.accounts.add(a);}return d;
    }
    void observe(Ledger.Data d,int day,long id,long balance){
        MonthLedger.updateAccount(d,id,LocalDateTime.of(2026,10,day,0,0),balance);
    }
    Ledger.Report month(Ledger.Data d){return Ledger.month(d,YearMonth.of(2026,10));}
    @Test public void historicalCutoffIgnoresEarlierUpdatesAndAddsOnlyLaterDifferences(){
        Ledger.Data d=base();observe(d,12,1,900000);observe(d,16,1,500000);observe(d,22,1,400000);
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),100000,LocalDateTime.of(2026,10,16,0,0)));
        assertEquals(200000,month(d).expense);assertEquals(1,MonthLedger.calculate(d,YearMonth.of(2026,10)).changes.size());
    }
    @Test public void singleUpdateIsZeroAndHistoricalSingleUpdateIsHistoricalAmount(){
        Ledger.Data d=base();observe(d,16,1,500000);assertEquals(0,month(d).expense);assertTrue(month(d).hasData());
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),100000,LocalDateTime.of(2026,10,16,0,0)));
        assertEquals(100000,month(d).expense);assertFalse(month(d).anomaly);
    }
    @Test public void independentlyUpdatedAccountsRetainTheirOwnPreviousObservation(){
        Ledger.Data d=base();observe(d,1,1,400000);observe(d,1,2,200000);observe(d,15,2,300000);observe(d,23,1,500000);
        assertEquals(-200000,month(d).expense);assertEquals(500000,d.balance(d.accounts.get(0)));assertEquals(300000,d.balance(d.accounts.get(1)));
        MonthLedger.Result details=MonthLedger.calculate(d,YearMonth.of(2026,10));assertEquals(2,details.changes.size());
        assertEquals(15,details.changes.get(0).to.getDayOfMonth());assertEquals(23,details.changes.get(1).to.getDayOfMonth());
    }
    @Test public void updatingOneAccountDoesNotCreateFakeObservationsForOthers(){
        Ledger.Data d=base();observe(d,1,1,500000);observe(d,1,2,300000);observe(d,15,1,400000);
        assertEquals(100000,month(d).expense);assertEquals(1,d.snapshots.get(1).balances.size());assertFalse(d.snapshots.get(1).balances.containsKey(2L));
    }
    @Test public void adjustmentsStopAtTheLinkedAccountsOwnLatestUpdate(){
        Ledger.Data d=base();observe(d,1,1,500000);observe(d,1,2,300000);observe(d,15,1,400000);observe(d,23,2,300000);
        Ledger.Entry income=new Ledger.Entry(11,true,80000,LocalDateTime.of(2026,10,20,0,0),"工资","",1);d.entries.add(income);
        assertEquals(100000,month(d).expense);
        observe(d,23,1,480000);assertEquals(100000,month(d).expense);
    }
    @Test public void sameTimestampUpdatesMergeAndDoNotDuplicateIntervals(){
        Ledger.Data d=base();observe(d,1,1,500000);observe(d,1,2,300000);assertEquals(1,d.snapshots.size());
        observe(d,15,1,450000);observe(d,15,1,400000);observe(d,15,2,250000);assertEquals(2,d.snapshots.size());assertEquals(150000,month(d).expense);
    }
    @Test public void missingPostCutoffBaselineDoesNotCompareWithPreCutoffBalance(){
        Ledger.Data d=base();observe(d,12,1,900000);observe(d,20,1,400000);
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),100000,LocalDateTime.of(2026,10,16,0,0)));
        assertEquals(100000,month(d).expense);observe(d,23,1,350000);assertEquals(150000,month(d).expense);
    }
    @Test public void emptyMonthIsMissingAndSingleObservationIsRealZero(){
        Ledger.Data d=base();assertFalse(month(d).hasData());observe(d,15,1,200000);assertTrue(month(d).hasData());assertEquals(0,month(d).expense);
    }
    @Test public void historicalDateAndTimestampHaveExplicitMeaning(){
        assertEquals(LocalDateTime.of(2026,10,16,0,0),HistoryImport.historyEnd("2026/10/16 00:00"));
        assertEquals(LocalDateTime.of(2026,10,16,12,35),HistoryImport.historyEnd("2026/10/16 12:35"));
        assertEquals(YearMonth.of(2026,10),HistoryImport.historyMonth("2026/10"));
        Ledger.Data d=base();HistoryImport.importInto(d,ImportTest.monthly("2026/10/16 12:35","1000"));
        assertEquals(LocalDateTime.of(2026,10,16,12,35),d.history.get(0).cutoff);
    }
    List<FundRecognition.Line> lines(String... rows){
        List<FundRecognition.Line> lines=new ArrayList<>();for(int i=0;i<rows.length;i++)lines.add(new FundRecognition.Line(rows[i],0,i*25,700,i*25+20));return lines;
    }
    @Test public void screenshotParsingExcludesClosedTransactionsAndPreservesEachPurchase(){
        FundRecognition.Result result=FundRecognition.parse(lines(
            "全部交易汇总","7次 买入","共5,101.26元","买入 基金 | 博时智臻纯债债券A","1,000.00元","2026-10-19 01:54:15",
            "买入 基金 | 博时智臻纯债债券A","2,000.00元","交易关闭","2026-10-19 01:50:15",
            "买入 基金 | 国投瑞银白银期货(LO","F)A","100.00元","2026-01-27 10:31:01"));
        assertEquals(2,result.rows.size());assertEquals(1,result.excluded);
        assertEquals("博时智臻纯债债券A",result.rows.get(0).name);assertEquals(100000,result.rows.get(0).amount);
        assertEquals("国投瑞银白银期货(LOF)A",result.rows.get(1).name);assertEquals(10000,result.rows.get(1).amount);
        assertEquals(LocalDateTime.of(2026,10,19,1,54),result.rows.get(0).at);
    }
    @Test public void OCRRowNeverInventsACodeOrMissingTime(){
        FundRecognition.Row row=FundRecognition.parse(lines("买入 基金 | 测试基金A","1,000.00元")).rows.get(0);
        assertEquals("",row.code);assertNull(row.at);ImportTest.rejects("代码",row::asImport);
        row.code="000001";ImportTest.rejects("时间",row::asImport);
        row.at=LocalDateTime.of(2026,10,10,12,0);assertEquals("000001",row.asImport().code);
    }
    @Test public void cancelledAndFailedTradesAreExcludedAcrossSplitLines(){
        FundRecognition.Result result=FundRecognition.parse(lines("买入 基金 | A","100.00元","交易 关闭","2026-10-10 12:00","买入 基金 | B","200.00元","已撤销","2026-10-11 12:00"));
        assertTrue(result.rows.isEmpty());assertEquals(2,result.excluded);
    }
    @Test public void nameNormalizationKeepsShareClassDistinct(){
        assertEquals(FundRecognition.normalize("国投瑞银白银期货（LOF）A"),FundRecognition.normalize("国投瑞银白银期货(LOF)A"));
        assertNotEquals(FundRecognition.normalize("测试基金A"),FundRecognition.normalize("测试基金C"));
    }
    @Test public void OCRSeparatorAndRepeatedCurrencyDoNotPolluteNames(){
        FundRecognition.Result rows=FundRecognition.parse(lines("买入基金1博时智臻纯债债券A","1,000.00元元","2026-10-19 01:54:15"));
        assertEquals("博时智臻纯债债券A",rows.rows.get(0).name);assertEquals(100000,rows.rows.get(0).amount);
    }
}
