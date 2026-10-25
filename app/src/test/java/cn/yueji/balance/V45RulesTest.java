package cn.yueji.balance;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class V45RulesTest {
    @Test public void fundSortCyclesRateAmountTimeAndBack(){
        assertEquals(1,FundRules.nextSort(2));assertEquals(0,FundRules.nextSort(1));assertEquals(2,FundRules.nextSort(0));
        int sort=2;for(int i=0;i<30;i++)sort=FundRules.nextSort(sort);assertEquals(2,sort);
    }
    @Test public void flowOrderRestoresAndRepairsOnlyMissingOrInvalidKeys(){
        assertArrayEquals(new int[]{0,1,2,3},FlowOrder.read(null));
        assertArrayEquals(new int[]{2,0,1,3},FlowOrder.read("2,0,1"));
        assertArrayEquals(new int[]{2,0,1,3},FlowOrder.read("2,2,7,x,0"));
        assertArrayEquals(new int[]{1,0,2,3},FlowOrder.read("1"));
    }
    @Test public void movingFlowEntriesPreservesIdentityAndOtherRelativeOrder(){
        int[] original={0,1,2,3},moved=FlowOrder.move(original,0,2);
        assertArrayEquals(new int[]{1,2,0,3},moved);assertArrayEquals(new int[]{0,1,2,3},original);
        assertArrayEquals(new int[]{0,1,2,3},FlowOrder.move(moved,2,0));
        assertArrayEquals(moved,FlowOrder.read(FlowOrder.encode(moved)));
    }
    @Test public void axisLabelsUseCompactChineseUnits(){
        assertEquals("0",ChartScale.label(0));assertEquals("-32.41",ChartScale.label(-3241));
        assertEquals("3万",ChartScale.label(3000000));assertEquals("0.01",ChartScale.label(1));
        assertEquals("9000000万",ChartScale.label(Ledger.MAX_MONEY));
    }
    @Test public void wholeNetworkOperationTimesOutAndInterruptsWorker()throws Exception{
        CountDownLatch started=new CountDownLatch(1),interrupted=new CountDownLatch(1);
        try{
            NetworkIo.call(()->{started.countDown();try{Thread.sleep(10000);}catch(InterruptedException e){interrupted.countDown();throw e;}return 1;},100);
            fail("slow operation must stop waiting");
        }catch(SocketTimeoutException expected){assertTrue(NetworkIo.message(expected).contains("超时"));}
        assertTrue(started.await(1,TimeUnit.SECONDS));assertTrue(interrupted.await(1,TimeUnit.SECONDS));
    }
    @Test public void networkSuccessAndValidationFailuresArePreserved()throws Exception{
        assertEquals("ok",NetworkIo.call(()->"ok",1000));
        try{NetworkIo.call(()->{throw new IllegalArgumentException("基金代码为6位数字");},1000);fail();}
        catch(IllegalArgumentException e){assertEquals("基金代码为6位数字",NetworkIo.message(e));assertFalse(NetworkIo.connectivity(e));}
    }
    @Test public void technicalNetworkDetailsNeverReachTheUserMessage(){
        String raw="failed to connect to fund.eastmoney.com/43.171.81.16 (port 443) from /10.80.160.140";
        for(Exception e:new Exception[]{new ConnectException(raw),new UnknownHostException(raw),new SocketTimeoutException(raw),new IOException(raw)}){
            String message=NetworkIo.message(e);assertTrue(NetworkIo.connectivity(e));assertTrue(message.contains("网络"));
            assertFalse(message.contains("43.171"));assertFalse(message.contains("port"));assertFalse(message.contains("eastmoney"));
        }
        assertTrue(NetworkIo.message(new IOException("wrapped",new SocketTimeoutException(raw))).contains("超时"));
    }
    @Test public void ongoingMonthAlreadyHasExpenseBeforeMonthEnd(){
        YearMonth now=YearMonth.now();Ledger.Data d=new Ledger.Data();
        Ledger.Snapshot first=new Ledger.Snapshot(1,now.atDay(1).atStartOfDay(),"");
        Ledger.Snapshot latest=new Ledger.Snapshot(2,now.atDay(15).atTime(20,0),"");
        first.balances.put(1L,1000000L);latest.balances.put(1L,950000L);d.snapshots.add(first);d.snapshots.add(latest);
        Ledger.Report report=Ledger.month(d,now);
        assertTrue(report.hasData());assertTrue(report.usable());assertFalse(report.closed);assertEquals(50000,report.expense);
    }
    @Test public void partialMonthNeedsNoInventedOpeningBalanceOrDailyAverage(){
        Ledger.Data d=new Ledger.Data();YearMonth now=YearMonth.now();
        Ledger.Snapshot first=new Ledger.Snapshot(1,now.atDay(10).atTime(10,0),"");
        Ledger.Snapshot latest=new Ledger.Snapshot(2,now.atDay(15).atTime(20,0),"");
        first.balances.put(1L,100000L);latest.balances.put(1L,90000L);d.snapshots.add(first);d.snapshots.add(latest);
        Ledger.Report report=Ledger.month(d,now);
        assertTrue(report.hasData());assertEquals(10000,report.expense);assertFalse(report.baseline);assertFalse(report.hasDaily());
    }
    @Test public void anomalousKnownMonthIsDistinctFromACompletelyMissingMonth(){
        Ledger.Data d=new Ledger.Data();YearMonth now=YearMonth.now();
        for(int i=0;i<3;i++){
            Ledger.Snapshot s=new Ledger.Snapshot(i+1,now.atDay(i+1).atStartOfDay(),"");
            s.balances.put(1L,i==0?100000L:i==1?120000L:90000L);d.snapshots.add(s);
        }
        Ledger.Report report=Ledger.month(d,now);assertTrue(report.hasData());assertTrue(report.anomaly);assertEquals(10000,report.expense);
        assertFalse(report.usable());assertNull(Ledger.yearExpense(d,now.getYear()));
        assertFalse(Ledger.month(d,now.minusMonths(1)).hasData());
    }
}
