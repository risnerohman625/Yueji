package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

public class V465RulesTest {
    LocalDate monday=LocalDate.of(2026,10,19);
    boolean open(int hour,int minute){return MarketSession.open(new TradingCalendar(),monday.atTime(hour,minute));}
    @Test public void publicationWindowStartsAtClose(){
        assertFalse(open(9,30));assertFalse(open(11,29));assertFalse(open(13,0));assertFalse(open(14,59));assertTrue(open(15,0));assertTrue(open(23,59));
    }
    @Test public void weekendsAndOfficialClosuresNeverRun(){
        TradingCalendar calendar=new TradingCalendar();calendar.addClosed(monday);
        assertFalse(MarketSession.open(calendar,monday.atTime(10,0)));
        assertFalse(MarketSession.open(calendar,LocalDate.of(2026,10,24).atTime(10,0)));
        assertFalse(MarketSession.open(calendar,LocalDate.of(2026,10,25).atTime(14,0)));
    }
    @Test public void selectedIntervalRoundTripsAndOldBackupsDefaultToTwoHours()throws Exception{
        Ledger.Data data=new Ledger.Data();data.settings.refreshMinutes=30;
        Ledger.Data roundTrip=LedgerStore.decode(LedgerStore.encode(data));assertEquals(30,roundTrip.settings.refreshMinutes);
        org.json.JSONObject old=new org.json.JSONObject(LedgerStore.encode(data));old.put("version",8);old.getJSONObject("settings").remove("refreshMinutes");
        assertEquals(120,LedgerStore.decode(old.toString()).settings.refreshMinutes);
        assertEquals(120,MarketSession.interval(0));assertEquals(120,MarketSession.interval(7));assertEquals(15,MarketSession.interval(15));
    }
    @Test public void mixedProductOrderUsesCurrentValuesAndAbsoluteProfits(){
        PortfolioOrder.Key fund=new PortfolioOrder.Key("fund:1",false,100000,5000,monday.atTime(10,0));
        PortfolioOrder.Key bank=new PortfolioOrder.Key("bank:2",false,500000,2000,monday.minusDays(1).atTime(10,0));
        PortfolioOrder.Key loss=new PortfolioOrder.Key("fund:3",false,200000,-1000,monday.plusDays(1).atTime(10,0));
        List<PortfolioOrder.Key> rows=new ArrayList<>(Arrays.asList(fund,bank,loss));
        rows.sort(PortfolioOrder.comparator(1));assertEquals("bank:2",rows.get(0).identity);assertEquals("fund:3",rows.get(1).identity);
        rows.sort(PortfolioOrder.comparator(2));assertEquals("fund:1",rows.get(0).identity);assertEquals("fund:3",rows.get(2).identity);
        rows.sort(PortfolioOrder.comparator(0));assertEquals("fund:3",rows.get(0).identity);
    }
    @Test public void profitsDoNotSortByPercentageAnyMore(){
        Ledger.Data d=new Ledger.Data();Ledger.Fund large=new Ledger.Fund(),small=new Ledger.Fund();
        large.id=1;large.code="000001";large.cost=1000000;large.shares=new BigDecimal("10000");large.nav=new BigDecimal("1.01");large.name="大仓位";
        small.id=2;small.code="000002";small.cost=10000;small.shares=new BigDecimal("100");small.nav=new BigDecimal("1.20");small.name="小仓位";
        d.funds.add(small);d.funds.add(large);
        assertEquals(large.id,FundRules.sorted(d,2,false,monday).get(0).id);
        assertEquals(large.id,FundRules.sorted(d,1,false,monday).get(0).id);
    }
    @Test public void hiddenProductsAlwaysStayAfterVisibleOnes(){
        PortfolioOrder.Key visible=new PortfolioOrder.Key("fund:1",false,1,1,monday.atStartOfDay());
        PortfolioOrder.Key hidden=new PortfolioOrder.Key("bank:2",true,999999,999999,monday.plusDays(1).atStartOfDay());
        for(int sort=0;sort<3;sort++)assertTrue(PortfolioOrder.comparator(sort).compare(visible,hidden)<0);
    }
    BankLedger.Product bank(Ledger.Data data){
        BankLedger.Draft draft=new BankLedger.Draft();draft.name="测试理财";draft.balance=100000L;draft.at=FundEngine.now().minusDays(10);return BankLedger.apply(data,0,draft,false,null);
    }
    @Test public void summarySelectionChangesMoneyProfitCostCountAndNeverMixesWeeklyWithDaily(){
        Ledger.Data data=new Ledger.Data();Ledger.Fund fund=new Ledger.Fund();fund.id=1;fund.code="000001";fund.name="基金";fund.shares=new BigDecimal("100");fund.cost=10000;fund.nav=new BigDecimal("1.2");data.funds.add(fund);
        BankLedger.Product bank=bank(data);BankLedger.Observation observation=bank.observations.get(0);
        LocalDateTime now=FundEngine.now();LocalDate today=now.toLocalDate();observation.cumulative=700L;observation.weekStart=BankLedger.monday(today);observation.weekly=500L;observation.weeks.put(observation.weekStart.minusWeeks(1),200L);
        observation.daily.put(today,24L);observation.daily.put(today.minusDays(1),-5L);
        PortfolioSummary onlyFund=PortfolioSummary.of(data,1,now,new TradingCalendar()),onlyBank=PortfolioSummary.of(data,2,now,new TradingCalendar()),all=PortfolioSummary.of(data,0,now,new TradingCalendar());
        assertEquals(1,onlyFund.count);assertEquals(1,onlyBank.count);assertEquals(2,all.count);
        assertEquals(onlyFund.value+onlyBank.value,all.value);assertEquals(onlyFund.cost+onlyBank.cost,all.cost);
        assertEquals(onlyFund.current+24,all.current);assertEquals(onlyFund.previous-5,all.previous);
        assertEquals(500,onlyBank.current);assertEquals(200,onlyBank.previous);assertEquals(1,onlyBank.selected.banks.size());assertTrue(onlyBank.selected.funds.isEmpty());
    }
    @Test public void missingBankDaysContributeZeroWithoutDisablingKnownFundTotals(){
        Ledger.Data data=new Ledger.Data();BankLedger.Product product=bank(data);product.observations.get(0).weekly=700L;
        PortfolioSummary totals=PortfolioSummary.of(data,0,FundEngine.now(),new TradingCalendar());
        assertEquals(0,totals.current);assertEquals(0,totals.previous);assertTrue(totals.previousKnown);
        product.hidden=true;assertEquals(0,PortfolioSummary.of(data,0,FundEngine.now(),new TradingCalendar()).count);
    }
}
