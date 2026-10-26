package cn.yueji.balance;

import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V451UiTest {
    final V43UiTest h=new V43UiTest();
    final YuejiTest t=h.t;
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v451");t.dir.mkdirs();}
    @After public void after()throws Exception{h.after();}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(250);t.device.takeScreenshot(new File(t.dir,name));}
    Ledger.Data base(){
        Ledger.Data d=new Ledger.Data();d.settings.autoRefresh=false;
        Ledger.Account a=new Ledger.Account(1,"支付宝","支付宝");a.created=LocalDateTime.of(2026,10,1,0,0);a.opening=500000;d.accounts.add(a);
        Ledger.Account b=new Ledger.Account(2,"微信","微信");b.created=a.created;b.opening=300000;d.accounts.add(b);
        MonthLedger.updateAccount(d,1,a.created,500000);MonthLedger.updateAccount(d,2,a.created,300000);
        return d;
    }
    @Test public void partialAccountUpdatesSurviveBackupAndMonthDetailsMatch(){
        Ledger.Data d=base();MonthLedger.updateAccount(d,1,LocalDateTime.of(2026,10,15,12,0),400000);MonthLedger.updateAccount(d,2,LocalDateTime.of(2026,10,20,12,0),250000);
        t.start(d);t.ui(()->{
            Ledger.Data restored=t.store.parseBackup(t.store.exportJson());assertTrue(restored.snapshots.get(1).partial);
            assertEquals(150000,Ledger.month(restored,YearMonth.of(2026,10)).expense);
            t.app.year=2026;t.app.monthPopup(10);assertEquals("month_detail",t.app.page);
            assertNotNull(t.findText(t.app.body,"总支出"));assertNotNull(t.findText(t.app.body,"日均支出"));assertNotNull(t.findText(t.app.body,"较上月"));
            assertNotNull(t.findText(t.app.body,"¥ 1,500.00"));
        });shot("v451-month-account-changes.png");
    }
    @Test public void historicalCutoffIsShownAndPreCutoffObservationsAreNotInDetails(){
        Ledger.Data d=base();MonthLedger.updateAccount(d,1,LocalDateTime.of(2026,10,12,0,0),900000);
        MonthLedger.updateAccount(d,1,LocalDateTime.of(2026,10,16,0,0),500000);MonthLedger.updateAccount(d,1,LocalDateTime.of(2026,10,22,0,0),400000);
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,10),100000,LocalDateTime.of(2026,10,16,0,0)));
        t.start(d);t.ui(()->{t.app.year=2026;t.app.monthPopup(10);assertEquals(200000,Ledger.month(t.app.data,YearMonth.of(2026,10)).expense);});
        assertTrue(t.device.hasObject(By.textContains("10月16日 00:00，导入历史数据")));
        assertFalse(t.device.hasObject(By.textContains("10月12日")));shot("v451-month-history-bridge.png");
    }
    @Test public void updateBalanceButtonChangesOnlyTheChosenAccount(){
        t.start(base());t.ui(()->{t.app.accountId=1;t.app.go("account_history");t.findText(t.app.body,"更新余额").performClick();});
        UiObject2 input=t.device.wait(Until.findObject(By.desc("本账户余额")),3000);assertNotNull(input);input.setText("4500.00");
        t.device.findObject(By.clazz("android.widget.Button").text("保存")).click();t.device.waitForIdle();
        Ledger.Data saved=t.store.load();assertEquals(450000,saved.balance(saved.accounts.get(0)));assertEquals(300000,saved.balance(saved.accounts.get(1)));
        assertEquals(1,saved.latest().balances.size());assertEquals(50000,Ledger.month(saved,YearMonth.of(2026,10)).expense);
        assertFalse(t.device.hasObject(By.text("初始余额")));shot("v451-account-history.png");
    }
    @Test public void monthWithoutDataHasZeroPointButNotAnExtremum(){
        Ledger.Data d=base();MonthLedger.updateAccount(d,1,LocalDateTime.of(2026,10,20,0,0),400000);
        t.start(d);t.ui(()->{t.app.openTab(MainActivity.TAB_FLOW);FinanceChart chart=h.first(t.app.body,FinanceChart.class);
            assertEquals(Long.valueOf(0),chart.first[0]);assertFalse(chart.known(0));assertTrue(chart.known(9));assertEquals(Long.valueOf(100000),chart.first[9]);
        });shot("v451-month-complete-curve.png");
    }




    @Test public void fundNameSearchReturnsOnlyExplicitCandidates()throws Exception{
        List<FundClient.Match> matches=FundClient.search("国投瑞银白银期货(LOF)A");assertFalse(matches.isEmpty());
        assertTrue(matches.stream().anyMatch(match->match.code.equals("161226")));for(FundClient.Match match:matches)assertTrue(match.code.matches("[0-9]{6}"));
    }
    @Test public void selectedAnnualBarGetsAnOutline(){
        Ledger.Data d=base();d.history.add(new Ledger.HistoryMonth(YearMonth.of(2025,1),100000,LocalDateTime.of(2025,2,1,0,0)));
        t.start(d);t.ui(()->{t.app.flowTab=1;t.app.openTab(MainActivity.TAB_FLOW);});SystemClock.sleep(200);
        t.ui(()->{FinanceChart chart=h.first(t.app.body,FinanceChart.class);
            int index=0;float x=chart.pointX(index)-t.app.dp(5),y=chart.pointY(chart.first[index])+t.app.dp(4);
            MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0);chart.onTouchEvent(down);down.recycle();
            Bitmap bitmap=Bitmap.createBitmap(chart.getWidth(),chart.getHeight(),Bitmap.Config.ARGB_8888);chart.draw(new Canvas(bitmap));
            assertTrue(chart.selectionText().contains("支出"));bitmap.recycle();
        });shot("v451-selected-annual-bar.png");
    }
}
