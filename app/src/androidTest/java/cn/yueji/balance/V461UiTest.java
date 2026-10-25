package cn.yueji.balance;

import android.content.*;
import android.content.pm.*;
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
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V461UiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v461");t.dir.mkdirs();}
    @After public void after()throws Exception{h.after();}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(220);t.device.takeScreenshot(new File(t.dir,name));}
    File fixture(String name)throws Exception{File file=new File(t.context.getCacheDir(),"fixture-"+name);try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);OutputStream out=new FileOutputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return file;}
    List<FundRecognition.Line> ocr(String asset)throws Exception{
        File image=new File(t.context.getCacheDir(),"test-"+asset);try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(asset);OutputStream out=new FileOutputStream(image)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        CountDownLatch done=new CountDownLatch(1);AtomicReference<List<FundRecognition.Line>> result=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();
        t.ui(()->ScreenshotOcr.recognize(t.context,Uri.fromFile(image),lines->{result.set(lines);done.countDown();},e->{error.set(e);done.countDown();}));
        assertTrue(done.await(40,TimeUnit.SECONDS));if(error.get()!=null)throw error.get();
        try(PrintWriter writer=new PrintWriter(new File(t.dir,asset+".ocr.txt"),"UTF-8")){for(FundRecognition.Line line:result.get())writer.println(line.top+","+line.left+": "+line.text);}
        image.delete();return result.get();
    }
    BankLedger.Product add(Ledger.Data d){BankLedger.Draft input=new BankLedger.Draft();input.name="浦银理财鑫享利日申月赎5号A";input.balance=479903L;input.at=LocalDateTime.of(2026,10,22,23,59);return BankLedger.apply(d,0,input,false,null);}
    @Test public void providedPurchaseDetailsReadGrossFeeAndMinuteWithoutOrderNoise()throws Exception{
        t.start(new Ledger.Data());FundRecognition.Row row=FundDetailRecognition.parse(ocr("fund-purchase-detail.jpg"));
        assertEquals(100000,row.amount);assertEquals(60,row.fee);assertEquals(LocalDateTime.of(2026,10,19,1,40),row.at);
        assertTrue(row.name.contains("平安"));assertTrue(row.name.endsWith("A"));
        t.ui(()->{t.app.fundScreenshot.rows.add(row);t.app.go("fund_scan_preview");});shot("v461-purchase-detail-preview.png");
    }
    @Test public void actualIncomeScreenshotPreservesSignedDailyValuesAndCumulative()throws Exception{
        t.start(new Ledger.Data());BankLedger.Draft parsed=BankOcr.income(ocr("bank-income-v461.jpg"));
        assertEquals(Long.valueOf(104),parsed.cumulative);assertEquals(Long.valueOf(104),parsed.weekly);assertEquals(4,parsed.daily.size());
        assertEquals(Long.valueOf(-1),parsed.daily.get(LocalDate.of(2026,10,19)));assertEquals(Long.valueOf(76),parsed.daily.get(LocalDate.of(2026,10,20)));
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=add(d);BankAccounting.importIncome(d,p,parsed);
        t.start(d);t.ui(()->{t.app.bankScreens.focus=p.id;t.app.go("bank_detail");});shot("v461-bank-daily-calendar.png");
        t.ui(()->{assertNotNull(t.findText(t.app.body,"持有收益率"));assertNull(t.findText(t.app.body,"基金代码"));assertNull(t.findText(t.app.body,"持有份额"));assertNotNull(t.findText(t.app.body,"导入收益数据"));t.app.bankScreens.chart=1;t.app.render();});shot("v461-bank-cumulative-chart.png");
    }
    @Test public void amountScreenshotReadsOnlyNameAndBalanceAndManualFormHasThreeInputs()throws Exception{
        t.start(new Ledger.Data());BankLedger.Draft parsed=BankOcr.parse(ocr("bank-amount-v461.jpg"));
        assertEquals(Long.valueOf(479903),parsed.balance);assertNull(parsed.weekly);assertNull(parsed.cumulative);
        t.ui(()->t.app.bankScreens.start(0));
        assertTrue(t.device.hasObject(By.desc("产品名称")));assertTrue(t.device.hasObject(By.desc("持有金额")));assertTrue(t.device.hasObject(By.text("截图输入")));
        assertFalse(t.device.hasObject(By.textContains("屏幕共享")));assertFalse(t.device.hasObject(By.textContains("开启浮窗")));shot("v461-bank-amount-form.png");
    }
    @Test public void duplicateMinuteIsUnselectedAndDisplayedAsAdded(){
        Ledger.Data d=t.sample();Ledger.Fund fund=d.funds.get(0);Ledger.FundBuy buy=fund.buys.get(0);
        t.start(d);t.ui(()->{
            FundRecognition.Row row=new FundRecognition.Row();row.name=fund.name;row.code=fund.code;row.at=buy.at.withSecond(0).withNano(0);row.amount=99900;row.verified=true;
            t.app.fundScreenshot.rows.add(row);t.app.go("fund_scan_preview");CheckBox check=(CheckBox)h.desc(t.app.body,"选择识别记录0");assertFalse(check.isChecked());assertFalse(check.isEnabled());assertNotNull(t.findText(t.app.body,"已添加"));
        });shot("v461-duplicate-added.png");
    }
    @Test public void invalidScreenshotOffersTheRedactedStandardReference(){
        t.start(new Ledger.Data());t.ui(()->t.app.fundScreenshot.error("不是已成功的基金买入详情页"));
        UiObject2 standard=t.device.wait(Until.findObject(By.res("android","button3")),3000);assertNotNull(standard);standard.click();t.instrument.waitForIdleSync();SystemClock.sleep(300);shot("v461-standard-after-click.png");
        assertEquals("fund_screenshot_standard",t.app.page);t.ui(()->assertNotNull(h.first(t.app.body,ImageView.class).getDrawable()));shot("v461-standard-reference.png");
    }
    @Test public void repeatedIncomeImportRoundTripsWithoutErodingPrincipal(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=add(d);BankLedger.Draft income=new BankLedger.Draft();income.cumulative=104L;income.weekly=104L;income.daily.put(LocalDate.of(2026,10,22),24L);
        BankAccounting.importIncome(d,p,income);BankAccounting.importIncome(d,p,income);t.start(d);
        Ledger.Data restored=t.store.parseBackup(t.store.exportJson());BankLedger.Product r=restored.banks.get(0);
        assertEquals(479799,BankAccounting.cost(r,FundEngine.now()));assertEquals(479903,BankLedger.balance(r));assertEquals(2,r.observations.size());
        t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));assertFalse(t.device.hasObject(By.text("见各产品")));assertFalse(t.device.hasObject(By.textContains("含估算")));shot("v461-bank-home.png");
    }
    @Test public void mainSummaryIncludesActualManualBankDailyReturns(){
        Ledger.Data d=t.sample();BankLedger.Product p=add(d);LocalDate today=FundEngine.now().toLocalDate();
        BankLedger.Observation opening=p.observations.get(0);opening.at=FundEngine.now().minusMinutes(1);opening.reportDate=today;opening.weekStart=BankLedger.monday(today);
        BankLedger.Draft income=new BankLedger.Draft();income.cumulative=104L;income.daily.put(today,104L);BankAccounting.importIncome(d,p,income);
        t.start(d);t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));
        long expected=104+d.funds.stream().mapToLong(f->FundEngine.today(f,FundEngine.now())).sum();
        t.ui(()->{TextView label=(TextView)t.findText(t.app.body.getChildAt(0),"当日收益");ViewGroup column=(ViewGroup)label.getParent();assertEquals(t.app.signed(expected),((TextView)column.getChildAt(2)).getText().toString());});
    }
    @Test public void moneyFlowsRecordPlacementAndCompletionAndShowHistory()throws Exception{
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=add(d);t.start(d);t.ui(()->{t.app.bankScreens.focus=p.id;t.app.go("bank_flows");});
        assertTrue(t.device.hasObject(By.text("添加买/卖出记录")));t.ui(()->t.findText(t.app.body,"添加买/卖出记录").performClick());
        assertTrue(t.device.hasObject(By.desc("买/卖出时间日期")));assertTrue(t.device.hasObject(By.desc("交易完成时间日期")));t.device.findObject(By.desc("交易金额")).setText("100.00");
        t.device.findObject(By.clazz("android.widget.Button").text("保存")).click();t.instrument.waitForIdleSync();for(int i=0;i<30&&t.store.load().banks.get(0).events.isEmpty();i++)SystemClock.sleep(100);
        if(t.store.load().banks.get(0).events.isEmpty()){shot("v461-flow-save-problem.png");t.device.dumpWindowHierarchy(new File(t.dir,"flow-save-problem.xml"));}
        assertFalse("a validated flow must commit",t.store.load().banks.get(0).events.isEmpty());assertEquals("BUY",t.store.load().banks.get(0).events.get(0).kind);assertNotNull(t.store.load().banks.get(0).events.get(0).completedAt);shot("v461-bank-flow-records.png");
    }
    @Test public void captureServiceAndItsSpecialPermissionsAreAbsent()throws Exception{
        PackageInfo info=t.context.getPackageManager().getPackageInfo(t.context.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES);
        if(info.requestedPermissions!=null)for(String permission:info.requestedPermissions){assertFalse(permission.endsWith("SYSTEM_ALERT_WINDOW"));assertFalse(permission.endsWith("FOREGROUND_SERVICE_MEDIA_PROJECTION"));}
        if(info.services!=null)for(ServiceInfo service:info.services)assertFalse(service.name.contains("BankCapture"));
    }
    @Test public void screenshotPickerAllowsMultipleDocuments(){
        t.start(new Ledger.Data());AtomicReference<Intent> captured=new AtomicReference<>();
        android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor(){
            @Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){captured.set(intent);return new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null);}return null;}
        };
        t.instrument.addMonitor(monitor);try{t.ui(()->t.app.fundScreenshot.pick());assertNotNull(captured.get());assertTrue(captured.get().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));assertEquals("image/*",captured.get().getType());}finally{t.instrument.removeMonitor(monitor);}
    }
    @Test public void twoPurchaseDetailImagesProduceTwoReviewRowsWithoutWriting()throws Exception{
        t.start(new Ledger.Data());File file=fixture("fund-purchase-detail.jpg");Uri uri=Uri.fromFile(file);
        t.ui(()->t.app.fundScreenshot.read(Arrays.asList(uri,uri)));
        for(int i=0;i<180&&t.app.fundScreenshot.busy;i++)SystemClock.sleep(250);
        assertFalse(t.app.fundScreenshot.busy);t.ui(()->{assertEquals(2,t.app.fundScreenshot.rows.size());for(FundRecognition.Row r:t.app.fundScreenshot.rows){assertEquals(100000,r.amount);assertEquals(60,r.fee);}assertTrue(t.store.load().funds.isEmpty());});
        if(t.device.hasObject(By.text("稍后核对")))t.device.findObject(By.text("稍后核对")).click();
        shot("v461-multiple-detail-images.png");file.delete();
    }
    @Test public void cleanupRunsOnlyAfterSelectedImportAndHonorsTheOptOut()throws Exception{
        Uri authority=Uri.parse("content://cn.yueji.balance.test.screenshots");
        t.context.getContentResolver().call(authority,"reset",null,null);t.start(new Ledger.Data());
        FundClient.Quote quote=FundClient.fetch("000001");
        t.ui(()->{
            FundRecognition.Row r=new FundRecognition.Row();r.name=quote.name;r.code="000001";r.amount=100000;r.at=LocalDateTime.of(2026,10,19,10,0);r.verified=true;r.source=authority+"/selected-1";
            FundRecognition.Row skip=new FundRecognition.Row();skip.name=quote.name;skip.code=r.code;skip.amount=200000;skip.at=r.at.plusMinutes(1);skip.verified=true;skip.source=authority+"/unselected";skip.selected=false;
            t.app.fundScreenshot.rows.add(r);t.app.fundScreenshot.rows.add(skip);t.app.fundScreenshot.deleteSources=false;t.app.go("fund_scan_preview");
        });
        assertEquals(0,t.context.getContentResolver().call(authority,"count",null,null).getInt("count"));
        t.ui(()->t.findText(t.app.body,"完成导入").performClick());
        for(int i=0;i<160&&t.store.load().funds.isEmpty();i++)SystemClock.sleep(250);
        assertEquals(1,t.store.load().funds.size());assertEquals(1,t.store.load().funds.get(0).buys.size());assertEquals(0,t.context.getContentResolver().call(authority,"count",null,null).getInt("count"));
        if(t.device.hasObject(By.text("知道了")))t.device.findObject(By.text("知道了")).click();
        t.ui(()->{
            FundRecognition.Row r=new FundRecognition.Row();r.name=quote.name;r.code="000001";r.amount=100000;r.at=LocalDateTime.of(2026,10,19,10,2);r.verified=true;r.source=authority+"/selected-2";
            t.app.fundScreenshot.rows.clear();t.app.fundScreenshot.rows.add(r);t.app.fundScreenshot.deleteSources=true;t.app.go("fund_scan_preview");t.findText(t.app.body,"完成导入").performClick();
        });
        for(int i=0;i<160&&t.context.getContentResolver().call(authority,"count",null,null).getInt("count")==0;i++)SystemClock.sleep(250);
        assertEquals(1,t.context.getContentResolver().call(authority,"count",null,null).getInt("count"));assertEquals(2,t.store.load().funds.get(0).buys.size());
    }
    @Test public void realIncomePickerCallbackIsNotMistakenForDeletionConsent()throws Exception{
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=add(d);t.start(d);File file=fixture("bank-income-v461.jpg");
        assertNotEquals(BankScreens.INCOME,ScreenshotCleanup.REQUEST);assertNotEquals(BankScreens.INCOME,ScreenshotCleanup.REQUEST+1);
        t.ui(()->{t.app.bankScreens.focus=p.id;t.app.bankScreens.captureTarget=p.id;t.app.onActivityResult(BankScreens.INCOME,android.app.Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file)));});
        for(int i=0;i<120&&!t.app.page.equals("bank_income_preview");i++)SystemClock.sleep(200);
        assertEquals("bank_income_preview",t.app.page);shot("v461-bank-income-review.png");
        t.ui(()->{t.app.bankScreens.deleteSources=false;t.findText(t.app.body,"确认导入收益").performClick();});
        assertEquals(479799,BankAccounting.cost(t.store.load().banks.get(0),FundEngine.now()));file.delete();
    }
    @Test public void bankDetailFitsPhoneMatrixAndAllCalendarPeriods()throws Exception{
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=add(d);BankLedger.Draft income=new BankLedger.Draft();income.cumulative=104L;income.weekly=104L;income.daily.put(LocalDate.of(2026,10,22),24L);BankAccounting.importIncome(d,p,income);
        for(String[] shape:new String[][]{{"1080x1920","420"},{"1260x2800","480"},{"1260x2800","560"}}){
            t.device.executeShellCommand("wm size "+shape[0]);t.device.executeShellCommand("wm density "+shape[1]);t.start(d);
            t.ui(()->{t.app.bankScreens.focus=p.id;t.app.go("bank_detail");});
            SystemClock.sleep(150);
            t.ui(()->{View grid=t.app.body.getChildAt(t.app.body.getChildCount()-1);assertTrue(grid.getHeight()>t.app.dp(180));List<TextView> fitted=new V44UiTest().fitted(t.app.body);for(TextView value:fitted)if(value.isShown())assertTrue(value.getText().toString(),value.getPaint().measureText(value.getText().toString())<=value.getWidth()-value.getCompoundPaddingLeft()-value.getCompoundPaddingRight()+2);});
            shot("v461-bank-detail-"+shape[0]+"-"+shape[1]+".png");
            for(int mode=1;mode<4;mode++){int selected=mode;t.ui(()->{t.app.bankScreens.mode=selected;t.app.render();});shot("v461-bank-period-"+mode+"-"+shape[1]+".png");}
        }
        t.device.executeShellCommand("settings put system font_scale 1.3");t.start(d);t.ui(()->{t.app.bankScreens.focus=p.id;t.app.go("bank_detail");});shot("v461-bank-large-font.png");
    }
}
