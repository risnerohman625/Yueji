package cn.yueji.balance;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V463UiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v463");t.dir.mkdirs();}
    @After public void after()throws Exception{try{t.context.getSystemService(NotificationManager.class).cancel(23);h.after();}finally{AppIcons.sync(t.context);FundRefreshJob.schedule(t.context);}}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(180);t.device.takeScreenshot(new File(t.dir,name));}
    void tap(String text){UiObject2 item=t.device.wait(Until.findObject(By.text(text)),4000);assertNotNull(text,item);item.click();t.instrument.waitForIdleSync();}
    @Test public void wheelHidesNumbersAndClosesOneSecondAfterLastTouch(){
        t.start(t.sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));YearStepper[] picker=new YearStepper[1];
        t.ui(()->{picker[0]=h.first(t.app.body,YearStepper.class);picker[0].performClick();assertEquals(0f,picker[0].getAlpha(),0f);assertEquals(View.INVISIBLE,picker[0].getChildAt(0).getVisibility());});
        SystemClock.sleep(650);t.ui(()->assertTrue(picker[0].popup.isShowing()));
        SystemClock.sleep(550);t.ui(()->{assertFalse(picker[0].popup.isShowing());assertEquals(1f,picker[0].getAlpha(),0f);assertEquals(View.VISIBLE,picker[0].getChildAt(0).getVisibility());});shot("v463-year-restored.png");
    }
    @Test public void wheelDoesNotDismissWhileFingerIsHeld(){
        Ledger.Data data=t.sample();data.history.add(new Ledger.HistoryMonth(YearMonth.of(2022,1),10000,LocalDateTime.of(2022,2,1,0,0)));
        t.start(data);t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));YearStepper[] picker=new YearStepper[1];
        t.ui(()->{
            picker[0]=h.first(t.app.body,YearStepper.class);long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,20,20,0);picker[0].dispatchTouchEvent(down);down.recycle();
        });
        SystemClock.sleep(1400);t.ui(()->{assertTrue(picker[0].popup.isShowing());long now=SystemClock.uptimeMillis();MotionEvent up=MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,20,20,0);picker[0].dispatchTouchEvent(up);up.recycle();});
        SystemClock.sleep(1400);t.ui(()->assertFalse(picker[0].popup.isShowing()));
    }
    @Test public void allThreeIconsStaySelectedAcrossActivityRestartAndMatchDialogsAndNotifications(){
        Ledger.Data data=t.sample();t.start(data);
        for(int choice=0;choice<3;choice++){
            final int selected=choice;int activeBefore=AppIcons.selected(t.context);
            t.ui(()->{AppIcons.select(t.app,selected);t.app.go("app_icons");assertFalse(t.app.isFinishing());assertEquals(activeBefore,AppIcons.selected(t.context));assertEquals(selected,AppIcons.desired(t.context));AppIcons.beginProcess(t.context);});shot("v463-icon-choice-"+choice+".png");
            List<ResolveInfo> launchers=t.context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(t.context.getPackageName()),0);
            assertEquals(1,launchers.size());assertTrue(launchers.get(0).activityInfo.name.endsWith(AppIcons.ALIASES[choice]));
            assertEquals(AppIcons.resource(choice),launchers.get(0).activityInfo.icon);
            t.start(t.store.load());assertEquals(choice,AppIcons.selected(t.context));
            t.ui(()->{AlertDialog dialog=AppIcons.dialog(t.app).setTitle("当前应用图标").setMessage(AppIcons.NAMES[selected]).setPositiveButton("完成",null).show();assertNotNull(dialog.findViewById(android.R.id.icon));});
            shot("v463-icon-dialog-"+choice+".png");tap("完成");
            FundNotifications.channels(t.context);FundNotice.Report report=new FundNotice.Report();report.loss=true;report.text="图标同步测试，不是实际收益";
            PendingIntent open=PendingIntent.getActivity(t.context,23,new Intent(t.context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=FundNotifications.build(t.context,report,open);assertEquals(AppIcons.small(t.context),n.getSmallIcon().getResId());assertNull("do not repeat a third icon at the right",n.getLargeIcon());
            t.context.getSystemService(NotificationManager.class).notify(23,n);
        }
        t.ui(()->{AppIcons.select(t.app,0);AppIcons.beginProcess(t.context);});StatusBarNotification notification=null;for(StatusBarNotification n:t.context.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==23)notification=n;
        assertNotNull(notification);assertEquals(R.drawable.ic_stat_classic,notification.getNotification().getSmallIcon().getResId());
    }
    @Test public void systemNotificationUsesNativeAppHeaderAndNoOngoingService()throws Exception{
        t.start(t.sample());t.ui(()->t.app.testNotification());
        NotificationManager manager=t.context.getSystemService(NotificationManager.class);StatusBarNotification actual=null;for(StatusBarNotification n:manager.getActiveNotifications())if(n.getId()==23)actual=n;
        assertNotNull(actual);Notification notification=actual.getNotification();assertNull(notification.extras.getString(Notification.EXTRA_TITLE));assertNull(notification.contentView);assertNull(notification.bigContentView);assertNull(notification.headsUpContentView);
        assertEquals(0,notification.flags&Notification.FLAG_ONGOING_EVENT);assertEquals(NotificationManager.IMPORTANCE_HIGH,manager.getNotificationChannel("fund-loss-v4").getImportance());
        t.device.openNotification();SystemClock.sleep(600);shot("v463-system-notification.png");t.device.pressBack();
        PackageInfo info=t.context.getPackageManager().getPackageInfo(t.context.getPackageName(),PackageManager.GET_PERMISSIONS);
        for(String permission:info.requestedPermissions)assertFalse(permission.contains("FOREGROUND_SERVICE")||permission.contains("SYSTEM_ALERT_WINDOW"));
    }
    @Test public void periodicRefreshPersistsWithoutOpenActivityAndCanBeDisabled()throws Exception{
        Ledger.Data data=new Ledger.Data();data.settings.autoRefresh=true;t.start(data);
        JobScheduler scheduler=t.context.getSystemService(JobScheduler.class);JobInfo job=scheduler.getPendingJob(221);assertNotNull(job);assertTrue(job.isPersisted());assertTrue(job.isPeriodic());assertEquals(2*60*60*1000L,job.getIntervalMillis());assertEquals(JobInfo.NETWORK_TYPE_ANY,job.getNetworkType());
        t.ui(()->t.app.finish());String force=t.device.executeShellCommand("cmd jobscheduler run -f cn.yueji.balance 221");assertTrue(force,force.contains("Running job"));
        SystemClock.sleep(700);assertNotNull(scheduler.getPendingJob(221));
        data.settings.autoRefresh=false;t.store.save(data);FundRefreshJob.schedule(t.context);assertNull(scheduler.getPendingJob(221));
    }
    @Test public void forcedBackgroundJobRefreshesRealFundNavAfterActivityCloses()throws Exception{
        Ledger.Data data=t.sample();data.settings.autoRefresh=true;data.settings.fundNotifications=false;
        data.funds.removeIf(fund->!fund.code.equals("000001"));data.funds.get(0).updatedAt="";t.start(data);t.ui(()->t.app.finish());
        String result=t.device.executeShellCommand("cmd jobscheduler run -f cn.yueji.balance 221");assertTrue(result,result.contains("Running job"));
        if(!MarketSession.open(t.context)){
            SystemClock.sleep(700);assertEquals("",t.store.load().funds.get(0).updatedAt);
            assertEquals("closed",t.context.getSharedPreferences("fund-job",0).getString("lastState",""));
            Ledger.Data stop=t.store.load();stop.settings.autoRefresh=false;t.store.save(stop);FundRefreshJob.schedule(t.context);return;
        }
        Ledger.Fund updated=null;
        for(int i=0;i<240;i++){Ledger.Data latest=t.store.load();if(!latest.funds.get(0).updatedAt.isEmpty()){updated=latest.funds.get(0);break;}SystemClock.sleep(250);}
        Ledger.Data stop=t.store.load();stop.settings.autoRefresh=false;t.store.save(stop);FundRefreshJob.schedule(t.context);
        assertNotNull("system job must actually download public NAVs, not only remain scheduled",updated);
        assertTrue(updated.historyComplete);assertTrue(updated.prices.size()>10);assertFalse(updated.name.startsWith("测试"));
        try(PrintWriter writer=new PrintWriter(new File(t.dir,"background-live-nav.txt"),"UTF-8")){writer.println(updated.code+" "+updated.name);writer.println("NAV date="+updated.navDate+" value="+updated.nav);writer.println("updated="+updated.updatedAt+" historical prices="+updated.prices.size());writer.println("Activity finished before the system job was forced; no ongoing foreground service.");}
    }
    @Test public void debtCardCreatesEditsRepaysAndDimsCompletedRecords()throws Exception{
        Ledger.Data data=t.sample();int entries=data.entries.size();long accountAssets=data.total();DebtLedger.Loan other=DebtLedger.save(data,0,"另一笔待还",50000,LocalDate.of(2026,10,1),"");t.start(data);
        long[] originalAssets={0};t.ui(()->originalAssets[0]=t.app.data.total()+t.app.heldFundValue());
        t.ui(()->{t.app.openTab(MainActivity.TAB_FLOW);assertNotNull(h.desc(t.app.body,"入口负债"));h.desc(t.app.body,"入口负债").performClick();});
        t.ui(()->t.app.debtScreens.edit(0));t.device.findObject(By.desc("负债名称")).setText("房租周转");t.device.findObject(By.desc("负债原始金额")).setText("1000.00");tap("保存");
        Ledger.Data saved=t.store.load();assertEquals(2,saved.debts.size());DebtLedger.Loan loan=saved.debts.stream().filter(item->item.title.equals("房租周转")).findFirst().get();
        t.ui(()->h.desc(t.app.body,"编辑负债"+loan.id).performClick());t.device.findObject(By.desc("负债原始金额")).setText("1200.00");tap("保存");
        t.ui(()->h.desc(t.app.body,"还款负债"+loan.id).performClick());t.device.findObject(By.desc("还款金额")).setText("200.00");tap("保存还款");assertEquals(100000,DebtLedger.balance(DebtLedger.find(t.store.load(),loan.id),LocalDate.now()));
        t.ui(()->h.desc(t.app.body,"还款负债"+loan.id).performClick());t.device.findObject(By.desc("还款金额")).setText("1000.00");tap("保存还款");
        saved=t.store.load();assertEquals(0,DebtLedger.balance(DebtLedger.find(saved,loan.id),LocalDate.now()));assertEquals(loan.id,DebtLedger.sorted(saved,LocalDate.now()).get(1).id);assertEquals(entries,saved.entries.size());assertEquals(accountAssets,saved.total());
        t.ui(()->assertEquals("debt never changes the asset page's account-plus-fund total",originalAssets[0],t.app.data.total()+t.app.heldFundValue()));
        t.ui(()->{ViewGroup card=(ViewGroup)h.desc(t.app.body,"负债记录"+loan.id);assertTrue(card.getChildAt(0).getAlpha()<.6f);assertFalse(h.desc(t.app.body,"还款负债"+loan.id).isEnabled());});shot("v463-debt-repaid.png");
        Ledger.Data restored=t.store.parseBackup(t.store.exportJson());assertEquals(50000,DebtLedger.total(restored,LocalDate.now()));assertEquals(2,DebtLedger.find(restored,loan.id).payments.size());
    }
    @Test public void debtChartUsesYearEndBalancesAndTheNewFlowCardFitsPhoneMatrix()throws Exception{
        Ledger.Data data=t.sample();DebtLedger.Loan loan=DebtLedger.save(data,0,"跨年借款",100000,LocalDate.of(2024,3,1),"");DebtLedger.repay(data,loan.id,25000,LocalDate.of(2025,8,1),"");DebtLedger.repay(data,loan.id,25000,LocalDate.of(2026,10,1),"");
        for(String[] shape:new String[][]{{"1080x1920","420"},{"1260x2800","560"}}){
            t.device.executeShellCommand("wm size "+shape[0]);t.device.executeShellCommand("wm density "+shape[1]);t.start(data);t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
            t.ui(()->{assertTrue(h.desc(t.app.body,"入口负债").getHeight()>0);assertTrue(h.first(t.app.body,FinanceChart.class).getHeight()>t.app.dp(65));});shot("v463-flow-"+shape[0]+".png");
            t.ui(()->{t.app.go("debts");FinanceChart chart=h.first(t.app.body,FinanceChart.class);assertArrayEquals(new String[]{"2024","2025","2026"},chart.labels);assertArrayEquals(new Long[]{100000L,75000L,50000L},chart.first);});shot("v463-debt-years-"+shape[0]+".png");
        }
    }
}
