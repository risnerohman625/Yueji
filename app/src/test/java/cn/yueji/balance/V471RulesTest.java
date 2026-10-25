package cn.yueji.balance;
import org.junit.Test;import java.time.*;import java.util.*;import static org.junit.Assert.*;
public class V471RulesTest {
 @Test public void screenshotPurchaseUsesExactNameForLookup(){List<FundRecognition.Line> lines=new V461RulesTest().lines("买入成功","买入产品 汇安鼎利纯债债券A","买入金额1000.00元","买入时间2026-10-19 01:40","手续费0.60元");FundRecognition.Row row=FundDetailRecognition.parse(lines);assertEquals("汇安鼎利纯债债券A",row.name);assertTrue(row.code.isEmpty());assertFalse(row.needsReview);}
 @Test public void screenshotNameMissingNeedsReview(){FundRecognition.Row row=FundDetailRecognition.parse(new V461RulesTest().lines("买入成功","买入产品 ","买入金额1000.00元","买入时间2026-10-19 01:40","手续费0.60元"));assertTrue(row.needsReview);assertTrue(row.problem.contains("产品名称"));}
 @Test public void manualNameAndCodeAreValidatedByOfficialQuote(){assertTrue(FundDetailRecognition.exact("汇安鼎利纯债债券A","汇安鼎利纯债债券A"));assertFalse(FundDetailRecognition.exact("错误名称","汇安鼎利纯债债券A"));}
 @Test public void ageUsesCalendarYears(){assertEquals("365天",WatchAnalytics.elapsed(LocalDate.of(2023,3,1),LocalDate.of(2024,2,29)));assertEquals("1年0日",WatchAnalytics.elapsed(LocalDate.of(2023,3,1),LocalDate.of(2024,3,1)));}
}
