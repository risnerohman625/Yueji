package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V4710UiTest {
    final V470UiTest old=new V470UiTest();final YuejiTest t=old.t;final V43UiTest h=old.h;
    @Before public void before(){old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",29).commit();}
    @After public void after()throws Exception{old.after();}
    Ledger.Data sample(){Ledger.Data d=new Ledger.Data();d.settings.autoRefresh=false;Ledger.Account a=new Ledger.Account(1,"测试银行卡","银行");a.created=LocalDateTime.of(2026,9,30,12,0);a.opening=100000;d.accounts.add(a);return d;}
    void history(){t.ui(()->{t.app.accountId=1;t.app.go("account_history");});}
    @Test public void addAccountIncludesEditableEnablingTimeAndInitialBalance(){
        t.start(new Ledger.Data());t.ui(()->{t.app.go("add_account");((EditText)h.desc(t.app.body,"账户名称")).setText("历史银行");((EditText)h.desc(t.app.body,"初始余额")).setText("1000");MainActivity.DateControl date=(MainActivity.DateControl)h.desc(t.app.body,"账户启用时间日期").getTag();date.at=LocalDateTime.of(2026,9,30,12,0);date.update();t.findText(t.app.body,"保存账户").performClick();});
        Ledger.Account a=t.store.load().accounts.get(0);assertEquals(LocalDateTime.of(2026,9,30,12,0),a.created);assertEquals(100000,a.opening);assertEquals(1,t.store.load().snapshots.size());old.shot("v4710-historical-account.png");
    }
    @Test public void initialCardSwipesToEditAndDeleteAndPersists(){
        t.start(sample());history();t.device.waitForIdle();old.shot("v4710-before-swipe.png");SwipeRow swipe=h.first(t.app.body,SwipeRow.class);assertNotNull(swipe);int[] pos=new int[2];final SwipeRow initial=swipe;t.ui(()->initial.getLocationOnScreen(pos));t.device.swipe(pos[0]+swipe.getWidth()*3/4,pos[1]+swipe.getHeight()/2,pos[0]+swipe.getWidth()/4,pos[1]+swipe.getHeight()/2,20);android.os.SystemClock.sleep(250);t.device.waitForIdle();
        assertTrue("swipe size="+swipe.getWidth()+"x"+swipe.getHeight()+" y="+pos[1],swipe.content.getTranslationX()<0);old.shot("v4710-initial-swipe.png");old.tap("编辑");t.device.wait(Until.findObject(By.desc("本账户余额")),2000).setText("1200");old.tap("保存");assertEquals(120000,t.store.load().accounts.get(0).opening);
        swipe=h.first(t.app.body,SwipeRow.class);final SwipeRow last=swipe;t.ui(last::openUp);old.tap("删除");assertNotNull(t.device.wait(Until.findObject(By.text("删除初始余额？")),2000));old.tap("删除");assertFalse(t.store.load().accounts.get(0).openingKnown);assertEquals(1,t.store.load().accounts.size());assertTrue(t.store.load().snapshots.isEmpty());t.ui(()->{t.app.go("");assertNotNull(t.findText(t.app.body,"待录入余额"));});
    }
    @Test public void ordinaryBalanceDeletionPreservesSharedAccountCell(){
        Ledger.Data d=sample();Ledger.Account other=new Ledger.Account(2,"现金","现金");other.created=d.accounts.get(0).created;other.opening=20000;d.accounts.add(other);Ledger.Snapshot s=new Ledger.Snapshot(3,LocalDateTime.of(2026,10,20,12,0),"");s.balances.put(1L,80000L);s.balances.put(2L,18000L);d.snapshots.add(s);t.start(d);history();t.ui(()->h.first(t.app.body,SwipeRow.class).openUp());old.tap("删除");old.tap("删除");Ledger.Data saved=t.store.load();assertEquals(Long.valueOf(18000),saved.snapshots.get(0).balances.get(2L));assertFalse(saved.snapshots.get(0).balances.containsKey(1L));
    }
    @Test public void inlineExplanationIsAnchoredAndDismissesOutside(){
        t.start(sample());t.ui(()->t.app.go("large_detail"));UiObject2 help=t.device.wait(Until.findObject(By.desc("大宗支出明细说明")),2000);assertNotNull(help);help.click();assertNotNull(t.device.wait(Until.findObject(By.textContains("不扣减账户余额")),2000));old.shot("v4710-large-help.png");t.device.click(10,t.device.getDisplayHeight()/2);assertTrue(t.device.wait(Until.gone(By.textContains("不扣减账户余额")),2000));
    }
    @Test public void fixedDaySettingsAndLegacyNotificationsAreRemoved(){
        t.start(sample());t.ui(()->{assertNull(t.findText(t.app.body,"固定更新"));t.app.openTab(MainActivity.TAB_SETTINGS);((View)t.findText(t.app.body,"更新与通知").getParent().getParent()).performClick();assertNull(t.findText(t.app.body,"固定更新日期"));assertNull(t.findText(t.app.body,"余额更新提醒"));});
        NotificationManager nm=t.context.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("balance-check","旧提醒",3));new ReminderReceiver().onReceive(t.context,new Intent("cn.yueji.balance.CHECK_BALANCES"));assertNull(nm.getNotificationChannel("balance-check"));for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())assertNotEquals(1,n.getId());
    }
    @Test public void deletedOnlyBalanceIsNotPrefilledAsZero(){Ledger.Data d=sample();AccountRecords.delete(d,1,d.accounts.get(0).created);t.start(d);history();t.ui(()->t.findText(t.app.body,"更新余额").performClick());String empty=t.device.wait(Until.findObject(By.desc("本账户余额")),2000).getText();assertTrue(empty==null||empty.isEmpty());old.tap("取消");t.ui(()->{t.app.go("");t.app.snapshotEditing=null;t.app.go("check");EditText amount=(EditText)h.desc(t.app.body,"测试银行卡余额（本次未包含，填写即补入）");assertEquals("",amount.getText().toString());});}
    @Test public void helpUsesExistingDarkThemeAndBackDismisses(){t.start(sample());t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",2).commit();t.app.go("large_detail");});assertTrue(Ui.dark);t.device.findObject(By.desc("大宗支出明细说明")).click();assertNotNull(t.device.wait(Until.findObject(By.textContains("不扣减账户余额")),2000));old.shot("v4710-dark-help.png");t.device.pressBack();assertTrue(t.device.wait(Until.gone(By.textContains("不扣减账户余额")),2000));assertEquals("large_detail",t.app.page);}
    @Test public void guideRetainsBottomAcknowledgmentAcrossRecreation(){
        t.start(sample());Instrumentation.ActivityMonitor monitor=t.instrument.addMonitor(UsageGuideActivity.class.getName(),null,false);t.ui(()->t.app.startActivity(new Intent(t.app,UsageGuideActivity.class).putExtra("required",true)));UsageGuideActivity first=(UsageGuideActivity)monitor.waitForActivityWithTimeout(3000);assertNotNull(first);t.ui(first::recreate);UsageGuideActivity second=(UsageGuideActivity)monitor.waitForActivityWithTimeout(3000);assertNotNull(second);t.instrument.waitForIdleSync();assertFalse(second.done.isEnabled());t.ui(()->second.guideScroll.fullScroll(View.FOCUS_DOWN));assertNotNull(t.device.wait(Until.findObject(By.text("我已阅读，开始使用").enabled(true)),2000));t.ui(second::recreate);UsageGuideActivity third=(UsageGuideActivity)monitor.waitForActivityWithTimeout(3000);assertNotNull(third);t.instrument.waitForIdleSync();assertTrue(third.done.isEnabled());t.ui(()->third.done.performClick());t.instrument.removeMonitor(monitor);
    }
    @Test public void guideReachesBottomWithoutTenSecondWaitAndCannotBackBypass(){
        t.start(sample());Instrumentation.ActivityMonitor monitor=t.instrument.addMonitor(UsageGuideActivity.class.getName(),null,false);t.ui(()->t.app.startActivity(new Intent(t.app,UsageGuideActivity.class).putExtra("required",true)));UsageGuideActivity guide=(UsageGuideActivity)monitor.waitForActivityWithTimeout(3000);assertNotNull(guide);t.instrument.waitForIdleSync();assertFalse(guide.done.isEnabled());t.device.pressBack();assertFalse(guide.isFinishing());
        t.ui(()->guide.guideScroll.fullScroll(View.FOCUS_DOWN));assertNotNull(t.device.wait(Until.findObject(By.text("我已阅读，开始使用").enabled(true)),2000));old.shot("v4710-guide-bottom.png");t.device.findObject(By.text("我已阅读，开始使用")).click();assertEquals(UsageGuideActivity.REVISION,t.context.getSharedPreferences("updates",0).getString("guideRead",""));t.instrument.removeMonitor(monitor);
    }
    void assertNumbers(View root){if(root instanceof TextView&&"bounded-number".equals(root.getTag())){TextView v=(TextView)root;assertTrue(v.getWidth()>0);assertTrue(v.getPaint().measureText(v.getText().toString())<=v.getWidth()-v.getPaddingLeft()-v.getPaddingRight()+2);}if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)assertNumbers(g.getChildAt(i));}}
    @Test public void accountHistoryAndHelpFitScreenAndFontMatrix()throws Exception{
        try{for(String[] config:new String[][]{{"1080x1920","420","1.0"},{"1080x2400","420","1.0"},{"1260x2800","480","1.0"},{"1260x2800","560","1.0"},{"1080x1920","420","1.3"}}){t.device.executeShellCommand("wm size "+config[0]);t.device.executeShellCommand("wm density "+config[1]);t.device.executeShellCommand("settings put system font_scale "+config[2]);t.start(sample());history();t.ui(()->{assertNumbers(t.app.body);});old.shot("v4710-history-"+config[0]+"-"+config[1]+"-"+config[2]+".png");t.ui(()->t.app.go("large_detail"));t.device.findObject(By.desc("大宗支出明细说明")).click();UiObject2 note=t.device.wait(Until.findObject(By.textContains("不扣减账户余额")),2000);assertNotNull(note);android.graphics.Rect bounds=note.getVisibleBounds();assertTrue(bounds.left>=0&&bounds.right<=t.device.getDisplayWidth());old.shot("v4710-help-"+config[0]+"-"+config[1]+"-"+config[2]+".png");t.device.pressBack();}}
        finally{t.device.executeShellCommand("wm size reset");t.device.executeShellCommand("wm density reset");t.device.executeShellCommand("settings put system font_scale 1.0");}
    }
}
