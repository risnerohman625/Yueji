package cn.yueji.balance;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.net.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V45UiTest {
    final V44UiTest previous=new V44UiTest();
    final V43UiTest h=previous.h;
    final YuejiTest t=h.t;
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v45");t.dir.mkdirs();}
    @After public void after()throws Exception{h.after();}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(250);t.device.takeScreenshot(new File(t.dir,name));}
    Ledger.Data sample(){
        Ledger.Data d=previous.realistic();
        for(int year=2022;year<2025;year++)d.history.add(new Ledger.HistoryMonth(YearMonth.of(year,1),100000*year%700000+100000,LocalDateTime.of(year,2,1,0,0)));
        return d;
    }
    void send(long start,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);
        try{t.instrument.sendPointerSync(event);}finally{event.recycle();}
    }
    void holdDrag(Rect from,Rect to){
        long start=SystemClock.uptimeMillis();send(start,MotionEvent.ACTION_DOWN,from.centerX(),from.centerY());
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+160);
        for(int i=1;i<=16;i++){
            send(start,MotionEvent.ACTION_MOVE,from.centerX()+(to.centerX()-from.centerX())*i/16f,from.centerY()+(to.centerY()-from.centerY())*i/16f);SystemClock.sleep(24);
        }
        send(start,MotionEvent.ACTION_UP,to.centerX(),to.centerY());t.instrument.waitForIdleSync();
    }
    void touch(View view,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,y,0);
        try{view.onTouchEvent(event);}finally{event.recycle();}
    }
    List<TextView> texts(View view){
        List<TextView> list=new ArrayList<>();if(view instanceof TextView)list.add((TextView)view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)list.addAll(texts(group.getChildAt(i)));}
        return list;
    }
    @Test public void portfolioSummaryHasDarkLabelsAndTrulyBoldCumulativeProfit(){
        t.start(sample());t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));
        t.ui(()->{
            ViewGroup summary=(ViewGroup)t.app.body.getChildAt(0);
            for(TextView text:texts(summary))assertNotEquals("no muted gray: "+text.getText(),Ui.MUTED,text.getCurrentTextColor());
            TextView label=(TextView)t.findText(summary,"累计收益 ›");assertTrue(label.getTypeface().isBold());
            LinearLayout column=(LinearLayout)label.getParent();assertTrue(((TextView)column.getChildAt(2)).getTypeface().isBold());
        });shot("v45-funds.png");previous.assertFits();
    }
    @Test public void sortButtonCyclesWithoutOpeningAPickerAndKeepsPreference(){
        t.start(sample());t.ui(()->{t.app.getPreferences(0).edit().putInt("fundSort",2).commit();t.app.openTab(MainActivity.TAB_FUNDS);});
        for(int expected:new int[]{1,0,2}){
            t.ui(()->h.desc(t.app.body,"基金排序").performClick());
            t.ui(()->assertEquals(expected,t.app.fundScreens.sortOrder()));
            assertFalse(t.device.hasObject(By.text("取消")));
        }
        t.ui(()->{t.app.go("fund_profit");assertEquals(2,t.app.fundScreens.sortOrder());h.desc(t.app.body,"基金排序").performClick();assertEquals(1,t.app.fundScreens.sortOrder());});
    }
    @Test public void flowCardsLongPressMoveVerticallyWithoutBreakingRecordButtons(){
        t.start(sample());t.ui(()->{t.app.getPreferences(0).edit().remove("flowOrder").commit();t.app.openTab(MainActivity.TAB_FLOW);});
        Rect from=t.device.findObject(By.desc("入口收入")).getVisibleBounds(),to=t.device.findObject(By.desc("入口报销")).getVisibleBounds();
        from.right=from.left+from.width()/2;to.right=to.left+to.width()/2;holdDrag(from,to);
        t.ui(()->assertArrayEquals(new int[]{1,2,0,3},t.app.flowOrder()));
        shot("v45-flow-reordered.png");
        t.ui(()->{t.app.openTab(MainActivity.TAB_ACCOUNTS);t.app.openTab(MainActivity.TAB_FLOW);assertArrayEquals(new int[]{1,2,0,3},t.app.flowOrder());});
        t.device.findObject(By.desc("记录收入")).click();assertTrue(t.device.wait(Until.hasObject(By.text("记录收入")),3000));
        assertEquals("income_form",t.app.page);
    }
    @Test public void cancelledLongPressNeverPersistsAnOrderChange(){
        t.start(sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));int[] before=t.app.flowOrder();
        t.ui(()->{
            FlowLayout entries=(FlowLayout)h.desc(t.app.body,"收支入口排序");
            long start=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,20,20,0);
            entries.dispatchTouchEvent(down);down.recycle();
        });
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+100);
        t.ui(()->{
            FlowLayout entries=(FlowLayout)h.desc(t.app.body,"收支入口排序");
            long now=SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,20,20,0);
            entries.dispatchTouchEvent(cancel);cancel.recycle();assertArrayEquals(before,t.app.flowOrder());
        });
    }
    @Test public void yearWheelSelectsYearsAndPreservesAccessibleBoundaries(){
        t.start(sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));int newest=LocalDate.now().getYear();
        t.ui(()->{
            YearStepper picker=h.first(t.app.body,YearStepper.class);assertEquals(newest,picker.year());
            picker.performClick();assertTrue(picker.popup.isShowing());
            picker.wheel.setValue(newest-1);picker.select(newest-1);
            assertEquals(newest-1,picker.year());assertEquals(newest-1,t.app.year);
            picker.wheel.setValue(2022);picker.select(2022);
            assertEquals(2022,picker.year());
            picker.popup.dismiss();
            assertEquals(12,h.first(t.app.body,FinanceChart.class).labels.length);
        });
        assertFalse(t.device.hasObject(By.text("取消")));shot("v45-year-stepper.png");
        t.ui(()->{
            YearStepper picker=h.first(t.app.body,YearStepper.class);
            for(int i=2022;i<newest;i++)picker.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null);
            assertEquals(newest,picker.year());
            picker.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null);assertEquals(newest,picker.year());
        });
    }
    @Test public void unfinishedMonthShowsKnownAmountWithNoDailyAverageInvented(){
        Ledger.Data d=sample();d.snapshots.removeIf(s->s.at.getMonthValue()==10&&s.at.getDayOfMonth()==1);
        Ledger.Snapshot s=new Ledger.Snapshot(900,LocalDate.of(2026,10,10).atTime(10,0),"");
        s.balances.put(1L,900000L);s.balances.put(2L,48000L);d.snapshots.add(s);
        t.start(d);t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        t.ui(()->{
            Ledger.Report report=Ledger.month(t.app.data,YearMonth.of(2026,10));assertTrue(report.hasData());assertFalse(report.hasDaily());
            FinanceChart chart=h.first(t.app.body,FinanceChart.class);assertEquals(Long.valueOf(report.expense),chart.first[9]);
            touch(chart,MotionEvent.ACTION_DOWN,chart.pointX(9),chart.pointY(report.expense));
            assertEquals("10月 ¥ "+Ledger.decimal(report.expense)+"元",chart.selectionText());
        });shot("v45-month-in-progress.png");
    }
    @Test public void anomalousKnownMonthShowsAmountAndWarningRatherThanNoData(){
        Ledger.Data d=sample();Ledger.Snapshot extra=new Ledger.Snapshot(900,LocalDateTime.of(2026,10,8,10,0),"");
        extra.balances.put(1L,1200000L);extra.balances.put(2L,48000L);d.snapshots.add(extra);
        t.start(d);t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        t.ui(()->{
            Ledger.Report report=Ledger.month(t.app.data,YearMonth.of(2026,10));assertTrue(report.anomaly);
            assertEquals(Long.valueOf(report.expense),h.first(t.app.body,FinanceChart.class).first[9]);
            assertNull(t.findText(t.app.body,"待核对月份显示已计算金额，暂不计入年支出"));
        });
    }
    @Test public void lineCursorStaysWhileHeldThenDisappearsAfterRelease(){
        t.start(sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        t.ui(()->{FinanceChart chart=h.first(t.app.body,FinanceChart.class);touch(chart,MotionEvent.ACTION_DOWN,chart.pointX(9),chart.bottom()-30);});
        SystemClock.sleep(2700);t.ui(()->assertFalse(h.first(t.app.body,FinanceChart.class).selectionText().isEmpty()));
        t.ui(()->{FinanceChart chart=h.first(t.app.body,FinanceChart.class);touch(chart,MotionEvent.ACTION_UP,chart.pointX(9),chart.bottom()-30);});
        SystemClock.sleep(1200);t.ui(()->assertFalse(h.first(t.app.body,FinanceChart.class).selectionText().isEmpty()));
        SystemClock.sleep(1500);t.ui(()->assertEquals("",h.first(t.app.body,FinanceChart.class).selectionText()));
    }
    @Test public void incomeAndProfitLinesAlsoDismissAndNewTouchResetsTimer(){
        t.start(sample());t.ui(()->t.app.go("income_detail"));
        t.ui(()->{FinanceChart chart=h.first(t.app.body,FinanceChart.class);touch(chart,MotionEvent.ACTION_DOWN,chart.pointX(0),chart.bottom()-20);touch(chart,MotionEvent.ACTION_UP,chart.pointX(0),chart.bottom()-20);});
        SystemClock.sleep(2700);t.ui(()->assertEquals("",h.first(t.app.body,FinanceChart.class).selectionText()));
        t.ui(()->{t.app.go("fund_total");t.app.fundScreens.chartTab=1;t.app.render();});
        t.ui(()->{ProfitChart chart=h.first(t.app.body,ProfitChart.class);touch(chart,MotionEvent.ACTION_DOWN,chart.left(),chart.top()+20);touch(chart,MotionEvent.ACTION_UP,chart.left(),chart.top()+20);});
        SystemClock.sleep(1700);
        t.ui(()->{ProfitChart chart=h.first(t.app.body,ProfitChart.class);touch(chart,MotionEvent.ACTION_DOWN,chart.right(),chart.top()+20);});
        SystemClock.sleep(1100);t.ui(()->assertTrue(h.first(t.app.body,ProfitChart.class).selected>=0));
        t.ui(()->{ProfitChart chart=h.first(t.app.body,ProfitChart.class);touch(chart,MotionEvent.ACTION_UP,chart.right(),chart.top()+20);});
        SystemClock.sleep(2700);t.ui(()->assertEquals(-1,h.first(t.app.body,ProfitChart.class).selected));
    }
    @Test public void annualBarsHaveNoCursorLineAndTooltipIsAboveTheSelectedBar(){
        t.start(sample());t.ui(()->{t.app.flowTab=1;t.app.openTab(MainActivity.TAB_FLOW);});
        t.ui(()->{
            FinanceChart chart=h.first(t.app.body,FinanceChart.class);int index=chart.labels.length-1;
            float x=chart.pointX(index)+t.app.dp(5),y=chart.pointY(chart.second[index])+t.app.dp(10);
            touch(chart,MotionEvent.ACTION_DOWN,x,y);touch(chart,MotionEvent.ACTION_UP,x,y);
            assertTrue(chart.selectionText().contains("收入"));
            Bitmap bitmap=Bitmap.createBitmap(chart.getWidth(),chart.getHeight(),Bitmap.Config.ARGB_8888);
            chart.draw(new Canvas(bitmap));
            int px=Math.round(chart.pointX(index));
            assertNotEquals("no neutral vertical cursor in the gap between bars",Ui.INK_SOFT,bitmap.getPixel(px,Math.round((chart.top()+chart.bottom())/2)));
            bitmap.recycle();
        });shot("v45-year-bar-tooltip.png");
    }
    @Test public void assetLabelsIncreaseAndFundAmountIsAnObviousLink(){
        t.start(sample());shot("v45-assets.png");t.ui(()->{
            TextView total=(TextView)t.findText(t.app.body,"总资产"),account=(TextView)t.findText(t.app.body,"账户余额"),fund=(TextView)t.findText(t.app.body,"基金金额 >");
            float scale=t.context.getResources().getDisplayMetrics().scaledDensity;
            assertEquals(17,total.getTextSize()/scale,.2f);assertEquals(14.5,account.getTextSize()/scale,.2f);assertEquals(14.5,fund.getTextSize()/scale,.2f);
            h.desc(t.app.body,"基金金额").performClick();assertEquals(MainActivity.TAB_FUNDS,t.app.tab);
        });
    }
    @Test public void tradeCardDropsConfirmedStateAndHasEditNextToConfirmedUnits(){
        t.start(sample());t.ui(()->{t.app.fundFocus=40;t.app.go("fund_trades");});
        t.ui(()->{
            assertNull(t.findText(t.app.body,"份额已确认"));long id=t.app.data.funds.get(0).buys.get(0).id;
            View edit=h.desc(t.app.body,"编辑交易"+id+"买入");assertNotNull(edit);
            View card=t.app.body.getChildAt(1);assertTrue(card.getHeight()<t.app.dp(180));
            ViewGroup valueRow=(ViewGroup)edit.getParent();assertEquals(2,valueRow.getChildCount());
            assertTrue(valueRow.getChildAt(0) instanceof TextView);
            assertEquals("确认份额",((TextView)((ViewGroup)valueRow.getParent()).getChildAt(0)).getText().toString());
            edit.performClick();
        });assertTrue(t.device.wait(Until.hasObject(By.text("编辑交易")),3000));t.device.pressBack();shot("v45-trades.png");previous.assertFits();
    }
    @Test public void networkFailureUsesModalNotRawSocketDetailsAndKeepsCachedValues()throws Exception{
        String wifi=t.device.executeShellCommand("settings get global wifi_on").trim();
        String mobile=t.device.executeShellCommand("settings get global mobile_data").trim();
        try{
            t.device.executeShellCommand("svc wifi disable");t.device.executeShellCommand("svc data disable");SystemClock.sleep(1000);
            t.start(sample());t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));long value=t.store.load().funds.get(0).value();
            t.ui(()->t.app.refreshFunds());
            assertTrue(t.device.wait(Until.hasObject(By.text("网络或数据服务异常")),22000));
            assertFalse(t.app.refreshing);assertEquals(value,t.store.load().funds.get(0).value());shot("v45-network-dialog.png");
            t.device.findObject(By.text("知道了")).click();
            t.ui(()->{for(TextView text:texts(t.app.body)){assertFalse(text.getText().toString().contains("failed to connect"));assertFalse(text.getText().toString().contains("(port"));}});
        }finally{
            t.device.executeShellCommand("svc wifi "+("1".equals(wifi)?"enable":"disable"));
            t.device.executeShellCommand("svc data "+("1".equals(mobile)?"enable":"disable"));SystemClock.sleep(1500);
        }
    }
    @Test public void requestedLayoutsFitScreenMatrixAndDarkTheme()throws Exception{
        for(String[] size:new String[][]{{"1080x1920","420"},{"1080x2400","420"},{"1260x2800","480"},{"1260x2800","560"}}){
            t.device.executeShellCommand("wm size "+size[0]);t.device.executeShellCommand("wm density "+size[1]);t.start(sample());
            t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));previous.assertFits();shot("v45-funds-"+size[0]+"-"+size[1]+".png");
            t.ui(()->{t.app.fundFocus=40;t.app.go("fund_detail");});previous.assertFits();shot("v45-detail-"+size[0]+"-"+size[1]+".png");
            t.ui(()->{
                LinearLayout info=(LinearLayout)t.app.body.getChildAt(1);View row=info.getChildAt(0);
                assertTrue(row.getPaddingTop()<=t.app.dp(5));
                View chartCard=t.app.body.getChildAt(t.app.body.getChildCount()-1);assertTrue(chartCard.getHeight()>t.app.dp(180));
            });
            t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));shot("v45-flow-"+size[0]+"-"+size[1]+".png");
            t.ui(()->assertTrue(h.first(t.app.body,FinanceChart.class).getHeight()>t.app.dp(70)));
        }
        t.device.executeShellCommand("wm size 1080x1920");t.device.executeShellCommand("wm density 420");
        t.device.executeShellCommand("settings put system font_scale 1.3");t.start(sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));shot("v45-flow-large-font.png");
        t.device.executeShellCommand("settings put system font_scale 1.0");t.start(sample());
        t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",2).commit();t.app.openTab(MainActivity.TAB_FUNDS);});
        previous.assertFits();shot("v45-funds-dark.png");
    }
    @Test public void emptyLedgerAndMissingMonthsStayEmptyNotInventedZero(){
        t.start(new Ledger.Data());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        t.ui(()->{FinanceChart chart=h.first(t.app.body,FinanceChart.class);for(int i=0;i<chart.first.length;i++){assertEquals(Long.valueOf(0),chart.first[i]);assertFalse(chart.known(i));}assertNull(Ledger.yearExpense(t.app.data,LocalDate.now().getYear()));});
        shot("v45-flow-empty.png");
    }
}
