package cn.yueji.balance;
import org.junit.Test;
import java.time.*;
import java.math.*;
import java.util.*;
import static org.junit.Assert.*;
public class V471RulesTest {
 @Test public void roundedDailyReturnsDoNotAccumulateError(){Ledger.WatchFund f=new Ledger.WatchFund("021675","测试");f.prices.put(LocalDate.of(2025,10,23),new BigDecimal("1.0180"));f.prices.put(LocalDate.of(2026,10,23),new BigDecimal("1.0721"));f.returns.put(LocalDate.of(2026,10,23),new BigDecimal("5.31"));WatchAnalytics.Result r=WatchAnalytics.calculate(f,4,LocalDate.of(2026,10,26));assertEquals(5.3143,r.change,.0001);assertEquals(LocalDate.of(2026,10,23),r.growth.lastKey());}
 @Test public void codeOnlyPurchaseKeepsFinancialFields(){List<FundRecognition.Line> lines=new V461RulesTest().lines("买入成功","买入产品 错误名称A","基金代码 006431","买入金额1000.00元","买入时间2026-10-19 01:40","手续费0.60元");FundRecognition.Row row=FundDetailRecognition.parse(lines);assertEquals("006431",row.code);assertFalse(row.needsReview);assertEquals(100000,row.amount);assertEquals(60,row.fee);}
 @Test public void ambiguousCodeRequiresReview(){FundRecognition.Row row=FundDetailRecognition.parse(new V461RulesTest().lines("买入成功","买入产品 测试","基金代码006431 006636","买入金额1000.00元","买入时间2026-10-19 01:40","手续费0.60元"));assertTrue(row.needsReview);assertEquals("",row.code);}
 @Test public void ageUsesCalendarYears(){assertEquals("365天",WatchAnalytics.elapsed(LocalDate.of(2023,3,1),LocalDate.of(2024,2,29)));assertEquals("1年0日",WatchAnalytics.elapsed(LocalDate.of(2023,3,1),LocalDate.of(2024,3,1)));}
 @Test public void codesAreNotCrossCheckedAgainstNames(){List<WatchOcr.Row> rows=WatchOcr.parse(new V461RulesTest().lines("错名A","006431","1.062900","错名C","006636"));assertEquals(2,rows.size());assertEquals("",rows.get(0).name);}
}
