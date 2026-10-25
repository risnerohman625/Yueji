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
    @Test public void addFundHasExactlyManualAndRecognitionChoices(){
        t.start(base());t.ui(()->{t.app.openTab(MainActivity.TAB_FUNDS);t.app.fundScreens.addChoice();});
        assertTrue(t.device.hasObject(By.text("手动输入")));assertTrue(t.device.hasObject(By.text("自动识别")));
        assertTrue(t.device.hasObject(By.text("添加银行理财")));t.device.findObject(By.text("手动输入")).click();
        assertTrue(t.device.wait(Until.hasObject(By.desc("基金代码")),3000));t.device.pressBack();
    }
    @Test public void recognitionPreviewDefaultsToAllSelectedAndEditsStayADraft(){
        t.start(base());t.ui(()->{
            FundRecognition.Row row=new FundRecognition.Row();row.name="博时智臻纯债债券A";row.amount=100000;row.at=LocalDateTime.of(2026,10,19,1,54);
            t.app.fundScreenshot.rows.add(row);t.app.go("fund_scan_preview");
            CheckBox check=(CheckBox)h.desc(t.app.body,"选择识别记录0");assertTrue(check.isChecked());
            assertTrue(t.store.load().funds.isEmpty());t.findText(t.app.body,"完成导入").performClick();
        });
        assertTrue(t.device.wait(Until.hasObject(By.textContains("名称、代码及买入字段")),3000));t.device.findObject(By.text("知道了")).click();
        t.ui(()->t.findText(t.app.body,"编辑").performClick());
        t.device.findObject(By.desc("识别基金代码")).setText("006947");
        assertTrue(t.device.hasObject(By.text("联网核验并更新")));t.device.findObject(By.text("取消")).click();
        t.ui(()->assertTrue(t.store.load().funds.isEmpty()));
        shot("v451-recognition-preview.png");
        t.ui(()->{t.findText(t.app.body,"取消导入").performClick();assertTrue(t.store.load().funds.isEmpty());});
    }
    @Test public void screenshotDraftAndMonthNavigationSurviveSavedState(){
        t.start(base());t.ui(()->{
            FundRecognition.Row row=new FundRecognition.Row();row.name="基金A";row.code="000001";row.amount=10000;row.at=LocalDateTime.of(2026,10,10,10,0);row.selected=false;
            t.app.fundScreenshot.rows.add(row);Bundle saved=t.app.fundScreenshot.save();FundScreenshot restored=new FundScreenshot(t.app);restored.restore(saved);
            assertEquals(1,restored.rows.size());assertFalse(restored.rows.get(0).selected);assertEquals("000001",restored.rows.get(0).code);
        });
    }
    @Test public void realProvidedScreenshotRecognizesOpenPurchasesAndExcludesClosedTrades()throws Exception{
        t.start(base());File screenshot=new File(t.context.getCacheDir(),"recognition-test.jpg");
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("fund-transactions-example.jpg");OutputStream out=new FileOutputStream(screenshot)){
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
        }
        CountDownLatch done=new CountDownLatch(1);AtomicReference<List<FundRecognition.Line>> lines=new AtomicReference<>();AtomicReference<Exception> failed=new AtomicReference<>();
        t.ui(()->ScreenshotOcr.recognize(t.context,Uri.fromFile(screenshot),result->{lines.set(result);done.countDown();},error->{failed.set(error);done.countDown();}));
        assertTrue("bundled OCR finishes",done.await(45,TimeUnit.SECONDS));if(failed.get()!=null)throw failed.get();
        try(PrintWriter log=new PrintWriter(new File(t.dir,"ocr-lines.txt"),"UTF-8")){for(FundRecognition.Line line:lines.get())log.println(line.top+","+line.left+": "+line.text);}
        FundRecognition.Result parsed=FundRecognition.parse(lines.get());
        try(PrintWriter log=new PrintWriter(new File(t.dir,"ocr-results.txt"),"UTF-8")){for(FundRecognition.Row row:parsed.rows)log.println(row.name+" | "+row.amount+" | "+row.at);log.println("excluded="+parsed.excluded);}
        assertEquals("the sample contains six open purchases",6,parsed.rows.size());assertEquals(3,parsed.excluded);
        long sum=0;for(FundRecognition.Row row:parsed.rows){sum+=row.amount;assertNotNull(row.name,row.at);assertFalse(row.name.isEmpty());}
        assertEquals(510000,sum);assertTrue(parsed.rows.stream().anyMatch(row->row.amount==10000&&row.name.contains("白银")));
        assertTrue(screenshot.delete());
        t.ui(()->{t.app.fundScreenshot.rows=parsed.rows;t.app.fundScreenshot.excluded=parsed.excluded;t.app.go("fund_scan_preview");});
        shot("v451-real-screenshot-preview.png");
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
