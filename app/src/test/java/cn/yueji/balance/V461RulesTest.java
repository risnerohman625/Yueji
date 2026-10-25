package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class V461RulesTest {
    List<FundRecognition.Line> lines(String... values){List<FundRecognition.Line> lines=new ArrayList<>();for(int i=0;i<values.length;i++)lines.add(new FundRecognition.Line(values[i],20,i*50,800,i*50+30));return lines;}
    @Test public void detailPageReadsOnlyLabeledGrossFeeAndPurchaseTime(){
        FundRecognition.Row r=FundDetailRecognition.parse(lines("记录详情","平安基金管理有限公司","1,000.00元","买入成功","买入信息","买入产品 平安元泓30天滚动持有短债债券A","买入金额1,000.00元","福利抵扣0.60元","实付金额999.40元","买入时间2026-10-19 01:40:24","确认信息","确认金额999.40元","确认份额867.23份","确认净值1.15240000","手续费 手续费抵扣券|福利已抵扣0.60元","确认时间2026-10-20","订单号20261019009030003000000000650005797221"));
        assertEquals(100000,r.amount);assertEquals(60,r.fee);assertEquals(LocalDateTime.of(2026,10,19,1,40),r.at);assertEquals("平安元泓30天滚动持有短债债券A",r.name);
    }
    @Test public void oldMultiFundListIsRejectedButMissingFeeRequiresReview(){
        ImportTest.rejects("买入详情",()->FundDetailRecognition.parse(lines("交易记录","买入基金|基金A 1000.00元","2026-10-19 01:40")));
        FundRecognition.Row row=FundDetailRecognition.parse(lines("买入成功","买入信息","确认信息","买入产品基金A","买入金额1000.00元","买入时间2026-10-19 01:40"));
        assertTrue(row.needsReview);assertTrue(row.problem.contains("手续费"));
    }
    @Test public void namesMustMatchEveryCharacterIncludingPunctuationAndShareClass(){
        assertTrue(FundDetailRecognition.exact("基金(LOF)A","基金(LOF)A"));
        assertFalse(FundDetailRecognition.exact("基金(LOF)A","基金（LOF）A"));assertFalse(FundDetailRecognition.exact("基金A","基金C"));assertFalse(FundDetailRecognition.exact("基金a","基金A"));
    }
    @Test public void minuteDuplicateDoesNotDependOnFeeOrMoney(){
        Ledger.Data d=new Ledger.Data();Ledger.Fund f=new Ledger.Fund();f.code="000001";Ledger.FundBuy b=new Ledger.FundBuy();b.at=LocalDateTime.of(2026,10,19,1,40,24);b.amount=100000;f.buys.add(b);d.funds.add(f);
        FundRecognition.Row row=new FundRecognition.Row();row.code="000001";row.at=LocalDateTime.of(2026,10,19,1,40);row.amount=99940;assertTrue(FundDetailRecognition.duplicate(row,d));
        row.code="000002";assertFalse(FundDetailRecognition.duplicate(row,d));
    }
    BankLedger.Product product(Ledger.Data d){BankLedger.Draft draft=new BankLedger.Draft();draft.name="浦银理财鑫享利日申月赎5号A";draft.balance=479903L;draft.at=LocalDateTime.of(2026,10,22,23,59);return BankLedger.apply(d,0,draft,false,null);}
    BankLedger.Draft income(long total){BankLedger.Draft r=new BankLedger.Draft();r.cumulative=total;r.weekly=104L;r.daily.put(LocalDate.of(2026,10,19),-1L);r.daily.put(LocalDate.of(2026,10,20),76L);r.daily.put(LocalDate.of(2026,10,21),5L);r.daily.put(LocalDate.of(2026,10,22),24L);r.weeks.put(LocalDate.of(2026,10,19),104L);return r;}
    @Test public void principalIsDerivedFromFixedInitialAmountOnlyOnce(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));
        assertEquals(479799,BankAccounting.cost(p,FundEngine.now()));assertEquals(479903,BankLedger.balance(p));
        for(int i=0;i<5;i++)BankAccounting.importIncome(d,p,income(104));
        assertEquals(479799,BankAccounting.cost(p,FundEngine.now()));assertEquals(479903,BankLedger.balance(p));assertEquals(104,BankAccounting.heldProfit(p));assertEquals(2,p.observations.size());
    }
    @Test public void actualDailyReturnsNeverUseWeeklyAverages(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));
        assertEquals(Long.valueOf(-1),BankLedger.daily(p,LocalDate.of(2026,10,19)));assertEquals(Long.valueOf(76),BankLedger.daily(p,LocalDate.of(2026,10,20)));assertNull(BankLedger.daily(p,LocalDate.of(2026,10,23)));
        assertEquals(Long.valueOf(104),BankLedger.week(p,LocalDate.of(2026,10,19)));
    }
    @Test public void completedBuyAffectsHoldingOnlyAtCompletion(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));LocalDateTime now=FundEngine.now();
        BankAccounting.flow(d,p,100000,true,now.minusMinutes(2),null);assertEquals(479903,BankLedger.balance(p));
        p.events.get(0).completedAt=now.minusMinutes(1);assertEquals(579903,BankLedger.balance(p));assertEquals(579799,BankAccounting.cost(p,now));assertEquals(104,BankAccounting.heldProfit(p));
    }
    @Test public void redemptionSeparatesRealizedAndHoldingProfit(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));
        LocalDateTime at=FundEngine.now().minusSeconds(1);BankAccounting.flow(d,p,479903,false,at,at);
        assertEquals(0,BankLedger.balance(p));assertEquals(0,BankAccounting.cost(p,FundEngine.now()));assertEquals(0,BankAccounting.heldProfit(p));assertEquals(104,BankLedger.profit(p));
    }
    @Test public void historicalCashFlowBeforeAnchorIsNotCountedTwice(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));
        BankAccounting.flow(d,p,479799,true,LocalDateTime.of(2026,10,20,10,0),LocalDateTime.of(2026,10,21,10,0));assertEquals(479903,BankLedger.balance(p));assertEquals(479799,BankAccounting.invested(p));
    }
    @Test public void incomeOcrUsesDatesAndTwoDistinctHeaderColumns(){
        List<FundRecognition.Line> lines=Arrays.asList(new FundRecognition.Line("收益明细",10,0,200,30),new FundRecognition.Line("本周收益(元)",20,70,200,100),new FundRecognition.Line("累计收益(元)",500,70,700,100),
            new FundRecognition.Line("1.04",50,130,150,170),new FundRecognition.Line("1.04",550,130,650,170),
            new FundRecognition.Line("2026年10月22日",20,700,400,735),new FundRecognition.Line("+0.24",650,700,800,735),
            new FundRecognition.Line("2026年10月21日",20,800,400,835),new FundRecognition.Line("+0.05",650,800,800,835),
            new FundRecognition.Line("2026年10月20日",20,900,400,935),new FundRecognition.Line("+0.76",650,900,800,935),
            new FundRecognition.Line("2026年10月19日",20,1000,400,1035),new FundRecognition.Line("-0.01",650,1000,800,1035));
        BankLedger.Draft parsed=BankOcr.income(lines);assertEquals(Long.valueOf(104),parsed.cumulative);assertEquals(Long.valueOf(104),parsed.weekly);assertEquals(4,parsed.daily.size());assertEquals(Long.valueOf(-1),parsed.daily.get(LocalDate.of(2026,10,19)));
    }
    @Test public void amountScreenshotCannotBeUsedAsIncomeDetails(){
        ImportTest.rejects("收益明细",()->BankOcr.income(lines("代销|浦银理财","4,799.03","本周收益+1.04")));
    }
    @Test public void importingAnOlderConsistentReportDoesNotRollBackCurrentProfit(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));
        BankLedger.Draft older=new BankLedger.Draft();older.cumulative=80L;older.daily.put(LocalDate.of(2026,10,21),5L);BankAccounting.importIncome(d,p,older);
        assertEquals(104,BankLedger.profit(p));assertEquals(479903,BankLedger.balance(p));assertEquals(479799,BankAccounting.cost(p,FundEngine.now()));
    }
    @Test public void inconsistentCumulativeEvidenceCannotSilentlyRewriteTheTrend(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=product(d);BankAccounting.importIncome(d,p,income(104));
        BankLedger.Draft older=new BankLedger.Draft();older.cumulative=100L;older.daily.put(LocalDate.of(2026,10,21),5L);
        ImportTest.rejects("不能衔接",()->BankAccounting.importIncome(d,p,older));assertEquals(104,BankLedger.profit(p));
    }
}
