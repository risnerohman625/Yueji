package cn.yueji.balance;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class V466RulesTest {
    Ledger.Data accounts(){
        Ledger.Data d=new Ledger.Data();
        Ledger.Account a=new Ledger.Account(1,"银行","银行");a.opening=10000;a.created=LocalDateTime.parse("2026-01-01T00:00");
        Ledger.Account b=new Ledger.Account(2,"现金","现金");b.opening=20000;b.created=a.created;
        d.accounts.add(a);d.accounts.add(b);return d;
    }
    Ledger.Snapshot snapshot(Ledger.Data d,int id,String at,long account,long amount){
        Ledger.Snapshot s=new Ledger.Snapshot(id,LocalDateTime.parse(at),"");s.partial=true;s.balances.put(account,amount);d.snapshots.add(s);return s;
    }
    @Test public void partialUpdatesCarryEachAccountsActualLastBalance(){
        Ledger.Data d=accounts();snapshot(d,1,"2026-01-05T10:00",1,11000);snapshot(d,2,"2026-01-06T10:00",2,24000);
        snapshot(d,3,"2026-01-07T10:00",1,9000);
        List<AssetHistory.Point> p=AssetHistory.observations(d,LocalDateTime.parse("2026-02-01T00:00"));
        assertEquals(31000L,(long)p.get(0).amount);assertEquals(35000L,(long)p.get(1).amount);assertEquals(33000L,(long)p.get(2).amount);
        assertEquals(1,d.snapshots.get(1).balances.size());
    }
    @Test public void snapshotsAreValuedByTimeEvenIfInputOrderIsShuffled(){
        Ledger.Data d=accounts();snapshot(d,3,"2026-01-07T10:00",1,9000);snapshot(d,1,"2026-01-05T10:00",1,11000);
        assertEquals(29000L,(long)AssetHistory.valueAt(d,LocalDateTime.parse("2026-01-08T00:00")));
    }
    @Test public void createdAndDeletedAccountsRespectTheirRealLifetimes(){
        Ledger.Data d=accounts();d.accounts.get(1).created=LocalDateTime.parse("2026-01-10T00:00");d.accounts.get(0).deletedAt=LocalDateTime.parse("2026-01-12T00:00");
        assertEquals(10000L,(long)AssetHistory.valueAt(d,LocalDateTime.parse("2026-01-09T00:00")));
        assertEquals(30000L,(long)AssetHistory.valueAt(d,LocalDateTime.parse("2026-01-11T00:00")));
        assertEquals(20000L,(long)AssetHistory.valueAt(d,LocalDateTime.parse("2026-01-12T00:00")));
    }
    @Test public void debtAndRepaymentDoNotEnterAnyAssetPoint(){
        Ledger.Data d=accounts();Long before=AssetHistory.valueAt(d,LocalDateTime.parse("2026-10-24T12:00"));
        DebtLedger.save(d,0,"负债",9000000,LocalDate.of(2026,1,1),"");
        assertEquals(before,AssetHistory.valueAt(d,LocalDateTime.parse("2026-10-24T12:00")));
        assertEquals(2,AssetHistory.composition(d,LocalDateTime.parse("2026-10-24T12:00")).size());
    }
    List<AssetHistory.Point> points(int...days){
        List<AssetHistory.Point> list=new ArrayList<>();for(int day:days)list.add(new AssetHistory.Point(LocalDate.of(2026,1,day).atStartOfDay(),(long)day*100));return list;
    }
    @Test public void choosesFourObservationsByTimeRatherThanRecordIndex(){
        List<AssetHistory.Point> sample=AssetHistory.series(points(1,2,3,4,5,6,10,20,30,31),0);
        assertEquals(4,sample.size());assertEquals(1,sample.get(0).at.getDayOfMonth());assertEquals(10,sample.get(1).at.getDayOfMonth());assertEquals(20,sample.get(2).at.getDayOfMonth());assertEquals(31,sample.get(3).at.getDayOfMonth());
    }
    @Test public void sparseMonthsNeverCreateSyntheticObservations(){
        List<AssetHistory.Point> p=points(1,5,9);assertEquals(p,AssetHistory.series(p,0));
        assertEquals(1,AssetHistory.series(p,1).size());assertSame(p.get(2),AssetHistory.series(p,1).get(0));
    }
    @Test public void monthAndYearModesUseLastActualObservationAndSkipEmptyPeriods(){
        List<AssetHistory.Point> p=points(1,5);p.add(new AssetHistory.Point(LocalDateTime.parse("2026-03-05T12:00"),9000L));p.add(new AssetHistory.Point(LocalDateTime.parse("2027-01-01T12:00"),8000L));
        assertEquals(3,AssetHistory.series(p,1).size());assertEquals(2,AssetHistory.series(p,2).size());
        assertEquals(9000L,(long)AssetHistory.series(p,2).get(0).amount);
    }
    @Test public void uniformSamplerAlwaysKeepsEndpointsAndDistinctPoints(){
        List<AssetHistory.Point> all=points(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31);
        List<AssetHistory.Point> p=AssetHistory.evenly(all,4);assertSame(all.get(0),p.get(0));assertSame(all.get(30),p.get(3));assertEquals(4,new HashSet<>(p).size());
    }
    @Test public void extremesUseUnsampledRealAdjacentUpdates(){
        List<AssetHistory.Point> p=points(1,2,3);p.add(new AssetHistory.Point(LocalDateTime.parse("2026-01-04T00:00"),-10000L));
        assertEquals(100,AssetHistory.extreme(p,true).amount);assertEquals(-10300,AssetHistory.extreme(p,false).amount);
    }
    @Test public void unknownValuationDoesNotBecomeZeroOrBridgeExtrema(){
        List<AssetHistory.Point> p=points(1);p.add(new AssetHistory.Point(LocalDateTime.parse("2026-01-02T00:00"),null));p.addAll(points(3));
        assertNull(AssetHistory.extreme(p,true));assertNull(AssetHistory.extreme(p,false));
    }
    @Test public void historicalNavMustNotUseLatestPriceBeforeItWasKnown(){
        Ledger.Data d=accounts();Ledger.Fund f=new Ledger.Fund();f.shares=new BigDecimal("100");f.nav=BigDecimal.TEN;f.navDate="2026-01-10";f.prices.put(LocalDate.of(2026,1,10),BigDecimal.TEN);
        Ledger.FundBuy b=new Ledger.FundBuy();b.at=LocalDateTime.parse("2026-01-01T10:00");b.confirmDate=LocalDate.of(2026,1,2);b.units=new BigDecimal("100");b.tradeDate=LocalDate.of(2026,1,1);b.amount=10000;f.buys.add(b);d.funds.add(f);
        assertNull(AssetHistory.valueAt(d,LocalDateTime.parse("2026-01-05T12:00")));
        assertEquals(130000L,(long)AssetHistory.valueAt(d,LocalDateTime.parse("2026-01-10T12:00")));
    }
    @Test public void pendingPurchaseCountsItsAmountOnlyAfterItsPurchaseTime(){
        Ledger.Fund f=new Ledger.Fund();Ledger.FundBuy b=new Ledger.FundBuy();b.at=LocalDateTime.parse("2026-01-01T15:00");b.amount=12345;f.buys.add(b);
        assertEquals(0L,(long)AssetHistory.fundAt(f,b.at.minusHours(1)));assertEquals(12345L,(long)AssetHistory.fundAt(f,b.at.plusMinutes(1)));
    }
    @Test public void bankUsesPastSegmentAndOnlyCompletedFlowsAfterStatement(){
        BankLedger.Product p=new BankLedger.Product();BankLedger.Observation a=new BankLedger.Observation();a.at=LocalDateTime.parse("2026-01-02T12:00");a.balance=10000;a.segment=0;p.observations.add(a);
        BankLedger.Event buy=new BankLedger.Event();buy.kind="BUY";buy.amount=3000;buy.segment=0;buy.at=a.at.plusHours(1);buy.completedAt=a.at.plusDays(1);p.events.add(buy);
        BankLedger.Observation next=new BankLedger.Observation();next.segment=1;next.balance=25000;next.at=a.at.plusDays(5);p.observations.add(next);
        assertEquals(0,(long)AssetHistory.bankAt(p,a.at.minusHours(1)));assertEquals(10000,(long)AssetHistory.bankAt(p,buy.at));assertEquals(13000,(long)AssetHistory.bankAt(p,buy.completedAt));
        assertEquals(25000,(long)AssetHistory.bankAt(p,next.at));assertEquals(13000,(long)AssetHistory.bankAt(p,next.at.minusHours(1)));
        BankLedger.Event change=new BankLedger.Event();change.kind="CHANGE";change.segment=0;change.at=next.at.minusHours(2);p.events.add(change);
        assertNull(AssetHistory.bankAt(p,next.at.minusHours(1)));assertEquals(25000,(long)AssetHistory.bankAt(p,next.at));
    }
    @Test public void tinyPositiveSliceGetsThreeDegreesWithoutChangingActualPercent(){
        LinkedHashMap<String,Long> map=new LinkedHashMap<>();map.put("主要",99999L);map.put("很少",1L);map.put("零",0L);
        AssetSlices s=new AssetSlices(map);assertEquals(2,s.names.length);assertEquals(3f,s.sweeps[1],.001);assertEquals(.001,s.percent(1),.0000001);assertEquals(100000,s.total);
        assertEquals(360f,s.sweeps[0]+s.sweeps[1],.001);assertEquals(0,s.extreme(true));assertEquals(1,s.extreme(false));
    }
    @Test public void emptyDonutHasNoSyntheticSlice(){
        AssetSlices s=new AssetSlices(Collections.emptyMap());assertEquals(0,s.total);assertEquals(0,s.sweeps.length);assertEquals(-1,s.extreme(false));
    }
    @Test public void manyTinySlicesStillSumToAFullCircle(){
        Map<String,Long> values=new LinkedHashMap<>();for(int i=0;i<20;i++)values.put(""+i,i==0?10000000L:1L);AssetSlices s=new AssetSlices(values);
        float sum=0;for(float sweep:s.sweeps){assertTrue(sweep>=3f);sum+=sweep;}assertEquals(360,sum,.01);
    }
    @Test public void debtCanMoveToFirstAndLegacyThreeCardOrderIsPreserved(){
        int[] restored=FlowOrder.read("2,0,1");assertArrayEquals(new int[]{2,0,1,3},restored);
        assertArrayEquals(new int[]{3,2,0,1},FlowOrder.move(restored,3,0));assertEquals("3,2,0,1",FlowOrder.encode(FlowOrder.move(restored,3,0)));
    }
    @Test public void compactAxisPreservesFractionAndSign(){
        assertEquals("2千",ChartScale.label(200000));assertEquals("5万",ChartScale.label(5000000));assertEquals("-1.25万",ChartScale.label(-1250000));assertEquals("32.41",ChartScale.label(3241));
    }
}
