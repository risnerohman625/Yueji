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

    BankLedger.Product add(Ledger.Data d){BankLedger.Draft input=new BankLedger.Draft();input.name="浦银理财鑫享利日申月赎5号A";input.balance=479903L;input.at=LocalDateTime.of(2026,10,22,23,59);return BankLedger.apply(d,0,input,false,null);}





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
