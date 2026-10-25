package cn.yueji.balance;

import org.junit.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class V44RulesTest {
    final TradingCalendar calendar=new TradingCalendar();
    Ledger.Fund fund(long id,String code,long... amounts){
        Ledger.Fund fund=new Ledger.Fund();fund.id=id;fund.code=code;fund.name="原基金";
        for(int i=0;i<amounts.length;i++){
            Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=id*100+i;buy.amount=amounts[i];buy.at=LocalDateTime.of(2025,1,6+i,10,0);
            fund.buys.add(buy);
        }
        fund.prices.put(LocalDate.of(2025,1,6),BigDecimal.ONE);fund.prices.put(LocalDate.of(2025,1,7),BigDecimal.ONE);
        fund.nav=BigDecimal.ONE;fund.navDate="2025-01-07";FundEngine.resolve(fund,calendar);return fund;
    }
    FundClient.Quote quote(String code,String price){
        FundClient.Quote q=new FundClient.Quote();q.code=code;q.name="修正基金"+code;q.nav=new BigDecimal(price);q.previous=q.nav;q.date="2025-02-10";
        for(LocalDate d=LocalDate.of(2025,1,1);!d.isAfter(LocalDate.of(2025,2,10));d=d.plusDays(1))q.history.put(d,q.nav);
        return q;
    }
    @Test public void axesUseRoundStepsCoveringTheirData(){
        ChartScale scale=ChartScale.positive(389750);
        assertEquals(100000,scale.step);assertEquals(400000,scale.high);assertEquals("1千",ChartScale.label(100000));
        assertEquals("2千",ChartScale.label(200000));assertEquals("500",ChartScale.label(50000));
        assertEquals(0,ChartScale.positive(0).low);
        assertEquals(100000,ChartScale.positive(0).high);
        for(long max:new long[]{1,100,10000,99999,129900,259700,Ledger.MAX_MONEY}){
            ChartScale s=ChartScale.positive(max);assertTrue(s.high>=max);assertTrue(s.step>0);assertEquals(0,s.high%s.step);
        }
    }
    @Test public void signedScalesCoverNegativeFlatAndTinyProfits(){
        for(long[] limits:new long[][]{{-1200,500},{0,0},{100,100},{-100,-100},{-1,1}}){
            ChartScale scale=ChartScale.signed(limits[0],limits[1]);assertTrue(scale.low<=limits[0]);assertTrue(scale.high>=limits[1]);assertTrue(scale.high>scale.low);
        }
    }
    @Test public void monthlyAveragesExcludeMissingExpensesAndUseElapsedIncomeMonths(){
        Ledger.Data data=new Ledger.Data();data.history.add(new Ledger.HistoryMonth(YearMonth.of(2025,1),10000,LocalDateTime.of(2025,2,1,0,0)));
        data.history.add(new Ledger.HistoryMonth(YearMonth.of(2025,3),30000,LocalDateTime.of(2025,4,1,0,0)));
        Ledger.Entry e=new Ledger.Entry(1,true,120000,LocalDateTime.of(2025,1,1,0,0),"工资","",0);e.category="工资";data.entries.add(e);
        YearSummary summary=new YearSummary(data,2025,YearMonth.of(2026,10));
        assertEquals(2,summary.expenseMonths);assertEquals(Long.valueOf(20000),summary.averageExpense);assertEquals(10000,summary.averageIncome);
        e.at=LocalDateTime.of(2026,1,1,0,0);
        summary=new YearSummary(data,2026,YearMonth.of(2026,10));assertNull(summary.averageExpense);assertEquals(12000,summary.averageIncome);
    }
    @Test public void partialReimbursementsRemainUnpaidUntilFullyReceived(){
        Ledger.Entry e=new Ledger.Entry(1,false,10000,LocalDateTime.of(2025,1,1,0,0),"报销","",0);e.kind=Ledger.REIMBURSE;
        assertTrue(RecordQuery.reimbursementState(e,0));assertFalse(RecordQuery.reimbursementState(e,1));
        e.receipts.add(new Ledger.Receipt(5000,LocalDateTime.of(2025,1,2,0,0)));
        assertTrue(RecordQuery.reimbursementState(e,0));assertTrue(RecordQuery.reimbursementState(e,2));
        e.receipts.add(new Ledger.Receipt(5000,LocalDateTime.of(2025,1,3,0,0)));
        assertTrue(RecordQuery.reimbursementState(e,1));assertFalse(RecordQuery.reimbursementState(e,0));
    }
    @Test public void iconLibraryHasOneIncomeMeaningForEveryGlyphAndMigratesOldIcons(){
        assertEquals(Ledger.ICONS.length,Ledger.ICON_NAMES.length);assertEquals(Ledger.ICONS.length,new HashSet<>(Arrays.asList(Ledger.ICONS)).size());
        Ledger.Data data=new Ledger.Data();data.categoryIcons.put("生活费","🍜");data.categoryIcons.put("其他","📦");data.ensureIcons();
        assertTrue(Ledger.ICON_SET.contains(data.iconOf("生活费")));assertNotEquals("🍜",data.iconOf("生活费"));
        assertEquals(data.categories.size(),new HashSet<>(data.categoryIcons.values()).size());
    }
    @Test public void correctingOnlyBatchChangesCodeWithoutLosingIdentityOrTag(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund f=fund(1,"000001",100000);f.tag="余利宝";data.funds.add(f);long buyId=f.buys.get(0).id;
        Ledger.Fund corrected=FundTransactions.save(data,f.id,buyId,"000002",120000,0,LocalDateTime.of(2025,1,6,10,0),quote("000002","2"),"",false,calendar);
        assertEquals(1,data.funds.size());assertEquals(1,corrected.id);assertEquals("000002",corrected.code);assertEquals("余利宝",corrected.tag);
        assertEquals(buyId,corrected.buys.get(0).id);assertEquals(new BigDecimal("600.00"),corrected.buys.get(0).units);
        assertEquals(new BigDecimal("2"),corrected.prices.get(LocalDate.of(2025,1,6)));
    }
    @Test public void correctingOneOfSeveralBatchesMovesOnlyThatBatch(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund source=fund(1,"000001",100000,200000);source.tag="钱包";data.funds.add(source);long id=source.buys.get(0).id;
        Ledger.Fund target=FundTransactions.save(data,source.id,id,"000002",100000,0,LocalDateTime.of(2025,1,6,10,0),quote("000002","2"),"",false,calendar);
        assertEquals(2,data.funds.size());assertEquals(1,source.buys.size());assertEquals(200000,source.buys.get(0).amount);
        assertEquals("钱包",target.tag);assertEquals(1,target.buys.size());assertEquals(id,target.buys.get(0).id);
    }
    @Test public void codeCorrectionMergesIntoExistingCodeWithoutDroppingTransfersOrSale(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund source=fund(1,"000001",100000),target=fund(2,"000002",200000);source.tag="余利宝";data.funds.add(source);data.funds.add(target);
        Ledger.FundBuy old=source.buys.get(0);old.saleDate=LocalDate.of(2025,2,5);old.saleAmount=110000;
        Ledger.Entry transfer=new Ledger.Entry(9,false,100000,FundEngine.ledgerTime(old.at),"基金申购 000001","自动关联买入 "+old.id,0);transfer.kind=Ledger.INVEST_OUT;data.entries.add(transfer);
        Ledger.Fund result=FundTransactions.save(data,source.id,old.id,"000002",120000,0,old.at,quote("000002","2"),"",false,calendar);
        assertSame(target,result);assertEquals(1,data.funds.size());assertEquals(2,target.buys.size());assertEquals("余利宝",target.tag);
        Ledger.FundBuy corrected=target.buys.get(1);assertEquals(LocalDate.of(2025,2,5),corrected.saleDate);assertEquals(110000,corrected.saleAmount);
        assertEquals(120000,transfer.amount);assertTrue(transfer.title.endsWith("000002"));
    }
    @Test public void offlineCodeCorrectionIsRejectedBeforeMutatingAnything(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund f=fund(1,"000001",100000);data.funds.add(f);
        ImportTest.rejects("历史净值",()->FundTransactions.save(data,f.id,f.buys.get(0).id,"000002",100000,0,f.buys.get(0).at,null,"离线",false,calendar));
        assertEquals("000001",f.code);assertEquals(1,f.buys.size());assertEquals(1,data.funds.size());
    }
    @Test public void missingBatchRemovedTargetAndMismatchedQuotesAreRejected(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund f=fund(1,"000001",100000);data.funds.add(f);
        ImportTest.rejects("原买入记录",()->FundTransactions.save(data,1,999,"000001",100000,0,f.buys.get(0).at,null,"",false,calendar));
        ImportTest.rejects("行情代码",()->FundTransactions.save(data,1,f.buys.get(0).id,"000001",100000,0,f.buys.get(0).at,quote("000002","2"),"",false,calendar));
        Ledger.Fund removed=fund(2,"000002",100000);FundEngine.remove(removed,LocalDateTime.of(2025,2,10,12,0));data.funds.add(removed);
        ImportTest.rejects("目标基金已",()->FundTransactions.save(data,1,f.buys.get(0).id,"000002",100000,0,f.buys.get(0).at,quote("000002","2"),"",false,calendar));
    }
    @Test public void sameCodeEditsCanUseStoredHistoricalPricesOffline(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund f=fund(1,"000001",100000);data.funds.add(f);
        Ledger.Fund result=FundTransactions.save(data,f.id,f.buys.get(0).id,f.code,150000,100,LocalDateTime.of(2025,1,7,10,0),null,"离线",false,calendar);
        assertEquals(new BigDecimal("1499.00"),result.buys.get(0).units);assertTrue(result.error.contains("离线"));
    }
    @Test public void changingOrUncheckingRedemptionTransferReplacesOrRemovesOldCorrection(){
        Ledger.Data data=new Ledger.Data();LocalDateTime at=LocalDateTime.of(2025,1,15,12,0);
        FundTransactions.saleTransfer(data,41,"000001",100000,at,true);
        FundTransactions.saleTransfer(data,41,"000001",120000,at.plusDays(1),true);
        assertEquals(1,data.entries.size());assertEquals(120000,data.entries.get(0).amount);assertEquals(Ledger.INVEST_IN,data.entries.get(0).kind);
        FundTransactions.saleTransfer(data,41,"000001",120000,at.plusDays(1),false);assertTrue(data.entries.isEmpty());
    }
}
