package cn.yueji.balance;

import android.app.*;
import android.app.job.*;
import android.content.*;
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
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V465UiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;byte[] oldCustom,oldPending;
    @Before public void before()throws Exception{
        h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v465");t.dir.mkdirs();
        oldCustom=AppIcons.custom(t.context,false).isFile()?Files.readAllBytes(AppIcons.custom(t.context,false).toPath()):null;
        oldPending=AppIcons.custom(t.context,true).isFile()?Files.readAllBytes(AppIcons.custom(t.context,true).toPath()):null;
    }
    @After public void after()throws Exception{
        try{h.after();}finally{
            restore(AppIcons.custom(t.context,false),oldCustom);restore(AppIcons.custom(t.context,true),oldPending);
            AppIcons.sync(t.context);FundRefreshJob.schedule(t.context);t.context.getSystemService(NotificationManager.class).cancel(23);
        }
    }
    void restore(File file,byte[] original)throws Exception{if(original==null)file.delete();else Files.write(file.toPath(),original);}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(180);t.device.takeScreenshot(new File(t.dir,name));}
    void tap(String text){UiObject2 item=t.device.wait(Until.findObject(By.text(text)),4000);assertNotNull(text,item);item.click();t.instrument.waitForIdleSync();}
    @Test public void threeIconsAreOneRowAndSelectionWaitsForActualActivityExitAndRelaunch(){
        t.start(t.sample());int before=AppIcons.selected(t.context),target=(before+1)%3;t.ui(()->AppIcons.show(t.app));
        t.ui(()->{
            View root=t.app.iconChooser.getWindow().getDecorView();
            ImageView first=(ImageView)h.desc(root,"应用图标选项0"),second=(ImageView)h.desc(root,"应用图标选项1");
            assertSame(first.getParent(),second.getParent());assertEquals(3,((ViewGroup)first.getParent()).getChildCount());
            h.desc(root,"应用图标选项"+target).performClick();
            assertFalse(t.app.isFinishing());assertEquals(before,AppIcons.selected(t.context));assertEquals(target,AppIcons.desired(t.context));
            assertTrue(h.desc(root,"应用图标选项"+target).isSelected());
        });shot("v465-icon-row-pending.png");tap("完成");
        MainActivity old=t.app;t.ui(()->old.finish());SystemClock.sleep(300);t.start(t.store.load());
        assertEquals(target,AppIcons.selected(t.context));assertNotSame(old,t.app);
    }
    @Test public void removedCustomIconPreferenceFallsBackToBuiltInWithoutDeletingOriginal()throws Exception{
        t.start(t.sample());File source=AppIcons.custom(t.context,false);Files.write(source.toPath(),new byte[]{1,2,3});
        t.context.getSharedPreferences("MainActivity",0).edit().putInt("appIcon",3).putInt("pendingIcon",3).commit();
        t.ui(()->AppIcons.beginProcess(t.context));assertEquals(AppIcons.DEFAULT,AppIcons.selected(t.context));assertTrue(source.isFile());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(source.toPath()));
    }    @Test public void amountAndProfitSortingUseOneCombinedFundAndBankList(){
        Ledger.Data data=t.sample();BankLedger.Draft draft=new BankLedger.Draft();draft.name="最大持仓银行产品";draft.balance=9999999L;draft.at=FundEngine.now().minusDays(1);BankLedger.Product bank=BankLedger.apply(data,0,draft,false,null);
        t.start(data);t.ui(()->{t.app.getPreferences(0).edit().putInt("productFilter",0).putInt("fundSort",1).commit();t.app.openTab(MainActivity.TAB_FUNDS);});
        t.ui(()->{
            List<PortfolioOrder.Key> rows=keys();assertEquals("bank:"+bank.id,rows.get(0).identity);
            for(int i=1;i<rows.size();i++)assertTrue(rows.get(i-1).value>=rows.get(i).value);
            assertNotNull(t.findText(t.app.body,"⇅ 持有金额"));
            h.desc(t.app.body,"基金排序").performClick();assertEquals(0,t.app.fundScreens.sortOrder());
            h.desc(t.app.body,"基金排序").performClick();assertEquals(2,t.app.fundScreens.sortOrder());
            rows=keys();for(int i=1;i<rows.size();i++)assertTrue(rows.get(i-1).profit>=rows.get(i).profit);
            assertNotNull(t.findText(t.app.body,"⇅ 持有收益"));
        });shot("v465-combined-holding-profit-sort.png");
    }
    List<PortfolioOrder.Key> keys(){List<PortfolioOrder.Key> list=new ArrayList<>();for(int i=0;i<t.app.body.getChildCount();i++)if(t.app.body.getChildAt(i).getTag() instanceof PortfolioOrder.Key)list.add((PortfolioOrder.Key)t.app.body.getChildAt(i).getTag());return list;}
    @Test public void notificationUsesSystemTemplateWithNoRepeatedNameOrImage()throws Exception{
        t.start(t.sample());t.ui(()->t.app.testNotification());Notification notice=null;
        for(android.service.notification.StatusBarNotification active:t.context.getSystemService(NotificationManager.class).getActiveNotifications())if(active.getId()==23)notice=active.getNotification();
        assertNotNull(notice);assertNull(notice.contentView);assertNull(notice.bigContentView);assertNull(notice.headsUpContentView);assertNull(notice.extras.getString(Notification.EXTRA_TITLE));assertNull(notice.getLargeIcon());
        assertEquals("Yueji",t.context.getPackageManager().getApplicationLabel(t.context.getApplicationInfo()).toString());
        t.device.openNotification();SystemClock.sleep(350);shot("v465-system-native-notification.png");t.device.pressBack();
    }
    @Test public void intervalPreferenceChangesSystemJobAndHelpHasBoldPermissionNames(){
        Ledger.Data data=t.sample();data.settings.autoRefresh=true;t.start(data);
        t.ui(()->t.app.refreshInterval());tap("30分钟");
        for(int i=0;i<40&&t.store.load().settings.refreshMinutes!=30;i++)SystemClock.sleep(100);
        assertEquals(30,t.store.load().settings.refreshMinutes);
        for(int i=0;i<40;i++){
            JobInfo job=t.context.getSystemService(JobScheduler.class).getPendingJob(221);
            if(job!=null&&job.getIntervalMillis()==30*60_000L)break;
            SystemClock.sleep(100);
        }
        assertEquals(30*60_000L,t.context.getSystemService(JobScheduler.class).getPendingJob(221).getIntervalMillis());
        t.ui(()->t.app.backgroundHelp());
        UiObject2 explanation=t.device.wait(Until.findObject(By.textContains("通知权限：")),4000);assertNotNull(explanation);
        t.ui(()->{
            android.text.Spanned text=(android.text.Spanned)t.app.refreshPermissionText.getText();
            android.text.style.StyleSpan[] spans=text.getSpans(0,text.length(),android.text.style.StyleSpan.class);assertEquals(5,spans.length);
            for(android.text.style.StyleSpan span:spans)assertEquals(android.graphics.Typeface.BOLD,span.getStyle());
        });
        shot("v465-refresh-permissions.png");tap("知道了");
    }
    void collect(View view,List<TextView> texts){if(view instanceof TextView)texts.add((TextView)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),texts);}}
    @Test public void closedMarketBlocksFirstEntryAndNotifications(){
        Ledger.Data data=t.sample();data.settings.autoRefresh=true;t.start(data);if(MarketSession.open(t.context))return;
        t.ui(()->{t.app.getPreferences(0).edit().remove("fundAttempt").commit();t.app.openTab(MainActivity.TAB_FUNDS);assertFalse(t.app.refreshing);assertEquals(0,t.app.getPreferences(0).getLong("fundAttempt",0));assertFalse(t.app.fundPageVisited);});
        FundNotifications.publish(t.context,t.store.load());for(android.service.notification.StatusBarNotification active:t.context.getSystemService(NotificationManager.class).getActiveNotifications())assertNotEquals(23,active.getId());
    }
    @Test public void cacheCleanupPreservesLedgerAndReportsStorageComponents()throws Exception{
        t.start(t.sample());String before=t.store.exportJson();File dir=new File(t.context.getCacheDir(),"test-clean-only"),child=new File(dir,"nested");assertTrue(child.mkdirs()||child.isDirectory());Files.write(new File(child,"temporary.bin").toPath(),new byte[16384]);
        assertEquals(16384,StorageScreen.bytes(dir));StorageScreen.clean(dir);assertEquals(0,StorageScreen.bytes(dir));assertEquals(before,t.store.exportJson());dir.delete();
        t.ui(()->t.app.go("storage"));shot("v465-storage-breakdown.png");
        try(PrintWriter out=new PrintWriter(new File(t.dir,"storage-breakdown.txt"),"UTF-8")){
            android.app.usage.StorageStats stats=t.context.getSystemService(android.app.usage.StorageStatsManager.class).queryStatsForUid(android.os.storage.StorageManager.UUID_DEFAULT,android.os.Process.myUid());
            out.println("system app="+stats.getAppBytes()+" data="+stats.getDataBytes()+" cache="+stats.getCacheBytes());
            out.println("APK="+new File(t.context.getApplicationInfo().sourceDir).length()+" native libs="+StorageScreen.bytes(new File(t.context.getApplicationInfo().nativeLibraryDir)));
            out.println("JSON="+t.store.exportJson().getBytes("UTF-8").length+" database directory="+StorageScreen.bytes(t.context.getDatabasePath("yueji.db").getParentFile()));
            out.println("code cache="+StorageScreen.bytes(t.context.getCodeCacheDir())+" files="+StorageScreen.bytes(t.context.getFilesDir()));
        }
    }
    @Test public void headerMetricsAndDailyWeeklyLabelsFollowTheThreeProductFilters(){
        Ledger.Data data=t.sample();LocalDate today=FundEngine.now().toLocalDate();
        BankLedger.Draft draft=new BankLedger.Draft();draft.name="理财汇总测试";draft.balance=250000L;draft.at=FundEngine.now().minusDays(8);
        BankLedger.Product bank=BankLedger.apply(data,0,draft,false,null);BankLedger.Observation evidence=bank.observations.get(0);
        evidence.cumulative=300L;evidence.reportDate=today;evidence.weekStart=BankLedger.monday(today);evidence.weekly=180L;evidence.weeks.put(evidence.weekStart.minusWeeks(1),120L);evidence.daily.put(today,24L);evidence.daily.put(today.minusDays(1),-5L);
        t.start(data);
        for(int filter:new int[]{0,1,2}){
            final int selected=filter;t.ui(()->{t.app.getPreferences(0).edit().putInt("productFilter",selected).commit();t.app.openTab(MainActivity.TAB_FUNDS);});
            t.ui(()->{
                PortfolioSummary expected=PortfolioSummary.of(t.app.data,selected,FundEngine.now(),t.app.fundScreens.calendar());
                ViewGroup header=(ViewGroup)t.app.body.getChildAt(0);
                TextView amount=(TextView)t.findText(header,"¥ "+t.app.money(expected.value));assertNotNull(amount);
                assertEquals(t.app.signed(expected.value-expected.cost),metric(header,"持有收益"));assertEquals(t.app.fundScreens.rate(expected.value-expected.cost,expected.cost),metric(header,"持有收益率"));
                assertEquals(""+expected.count,metric(header,selected==1?"持有基金数量":"持有产品数量"));
                if(selected==2){assertNotNull(t.findText(header,"本周收益"));assertNull(t.findText(header,"当日收益"));assertEquals(t.app.signed(180),metric(header,"本周收益"));assertEquals(t.app.signed(120),metric(header,"上周收益"));}
                else{assertNotNull(t.findText(header,"当日收益"));assertNull(t.findText(header,"本周收益"));assertEquals(t.app.signed(expected.current),metric(header,"当日收益"));}
            });shot("v465-header-filter-"+filter+".png");
        }
        t.ui(()->{
            Ledger.Data missing=t.app.copy();for(BankLedger.Observation o:missing.banks.get(0).observations)o.daily.clear();
            t.app.commit(missing);t.app.getPreferences(0).edit().putInt("productFilter",0).commit();t.app.render();
            long fundOnly=missing.funds.stream().mapToLong(f->FundEngine.today(f,FundEngine.now())).sum();
            assertEquals(t.app.signed(fundOnly),metric((ViewGroup)t.app.body.getChildAt(0),"当日收益"));
        });
    }
    String metric(ViewGroup header,String label){
        TextView title=(TextView)t.findText(header,label);assertNotNull(label,title);ViewGroup cell=(ViewGroup)title.getParent();
        return ((TextView)cell.getChildAt(2)).getText().toString();
    }
}
