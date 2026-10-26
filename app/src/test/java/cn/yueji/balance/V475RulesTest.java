package cn.yueji.balance;

import org.junit.Test;
import java.math.*;
import java.time.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class V475RulesTest {
    Ledger.WatchFund prices(String... values){Ledger.WatchFund fund=new Ledger.WatchFund("007329","测试基金");for(int i=0;i<values.length;i++)fund.prices.put(LocalDate.of(2026,1,1).plusDays(i),new BigDecimal(values[i]));return fund;}
    @Test public void reboundInsideDrawdownDoesNotSplitTheEpisode(){WatchAnalytics.Result r=WatchAnalytics.calculate(prices("1.10","1.05","1.07","1.01"),9,LocalDate.of(2026,1,4));assertEquals(100*(1-1.01/1.10),r.drawdown,1e-8);assertEquals(LocalDate.of(2026,1,1),r.peakDate);assertEquals(LocalDate.of(2026,1,4),r.troughDate);assertFalse(r.recovered);}
    @Test public void recoveryStartsAtTroughNotPeak(){WatchAnalytics.Result r=WatchAnalytics.calculate(prices("1.10","1.05","1.07","1.01","1.09","1.10"),9,LocalDate.of(2026,1,6));assertTrue(r.recovered);assertEquals(2,r.recoveryDays);assertEquals(3,r.declineDays);assertEquals(5,r.underwaterDays);}
    @Test public void actual007329MatchesTheSupplied239DayExample()throws Exception{
        Ledger.WatchFund fund=new Ledger.WatchFund("007329","摩根瑞益纯债债券A");
        try(InputStream input=getClass().getResourceAsStream("/007329-public-2019-2021.csv")){assertNotNull(input);try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(line.startsWith("date"))continue;String[] cells=line.split(",");LocalDate date=LocalDate.parse(cells[0]);fund.prices.put(date,new BigDecimal(cells[1]));if(cells.length>2)fund.returns.put(date,new BigDecimal(cells[2]));}}}
        WatchAnalytics.Result result=WatchAnalytics.calculate(fund,9,LocalDate.of(2021,3,10));
        assertEquals(1.86,result.drawdown,.005);assertEquals(LocalDate.of(2020,4,23),result.peakDate);assertEquals(LocalDate.of(2020,7,14),result.troughDate);assertEquals(LocalDate.of(2021,3,10),result.recoveryDate);assertEquals(239,result.recoveryDays);assertEquals(321,result.underwaterDays);assertTrue(result.recovered);
    }
    @Test public void calendarDaysIncludeWeekends(){Ledger.WatchFund f=new Ledger.WatchFund("007329","test");String[] values={"1.10","1.00","1.05","1.10","1.11"};for(int i=0;i<values.length;i++)f.prices.put(LocalDate.of(2026,1,1).plusDays(i==0?0:i==1?1:i==2?3:i==3?10:11),new BigDecimal(values[i]));WatchAnalytics.Result r=WatchAnalytics.calculate(f,9,LocalDate.of(2026,1,12));assertEquals(9,r.recoveryDays);assertEquals(10,r.underwaterDays);}
    @Test public void equalPeaksUseMostRecentAndEqualDrawdownsKeepFirst(){WatchAnalytics.Result r=WatchAnalytics.calculate(prices("1.1","1.1","1.0","1.1","1.0","1.1"),9,LocalDate.of(2026,1,6));assertEquals(LocalDate.of(2026,1,2),r.peakDate);assertEquals(LocalDate.of(2026,1,3),r.troughDate);assertEquals(1,r.recoveryDays);}
    @Test public void allCodesAreDeduplicatedAndMissingDateNeverCountsAsPublished(){Ledger.Data d=new Ledger.Data();Ledger.Fund f=new Ledger.Fund();f.code="007329";f.id=1;f.navDate="2026-10-26";f.historyComplete=true;d.funds.add(f);Ledger.WatchFund w=new Ledger.WatchFund(f.code,"基金");w.navDate="2026-10-23";w.historyComplete=true;d.watchFunds.add(w);assertEquals(1,MarketRefresh.codes(d).size());assertEquals(1,MarketRefresh.pending(d,LocalDate.of(2026,10,26)).size());w.navDate=f.navDate;assertTrue(MarketRefresh.pending(d,LocalDate.of(2026,10,26)).isEmpty());w.historyComplete=false;assertFalse(MarketRefresh.published(d,f.code,LocalDate.of(2026,10,26)));}
    @Test public void automaticWindowUsesTradingDaysAndShanghaiClose(){TradingCalendar c=new TradingCalendar();LocalDate day=LocalDate.of(2026,10,26);assertFalse(MarketSession.open(c,day.atTime(14,59)));assertTrue(MarketSession.open(c,day.atTime(15,0)));assertTrue(MarketSession.open(c,day.atTime(23,59)));assertFalse(MarketSession.open(c,day.plusDays(1).atStartOfDay()));c.addClosed(day);assertFalse(MarketSession.open(c,day.atTime(20,0)));assertFalse(MarketSession.open(c,LocalDate.of(2026,10,25).atTime(20,0)));}
    @Test public void updateSummaryIsBoundedAndRemovesMarkdown(){assertEquals("优化体验与稳定性，建议备份后更新。",UpdatePanel.summary(""));String summary=UpdatePanel.summary("# 新版本\n- "+String.join("",Collections.nCopies(150,"改")));assertTrue(summary.length()<=101);assertFalse(summary.contains("\n"));assertFalse(summary.contains("#"));}
}
