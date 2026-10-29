package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V475UiTest {
    final V470UiTest old=new V470UiTest();final YuejiTest t=old.t;final V43UiTest h=old.h;
    @Before public void before(){old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",28).commit();t.context.getSharedPreferences("market-refresh",0).edit().clear().commit();}
    @After public void after()throws Exception{old.after();}
    FundClient.Quote quote(String code,LocalDate date){FundClient.Quote q=new FundClient.Quote();q.code=code;q.name="基金"+code;q.date=date.toString();q.fundType="债券型";q.nav=new BigDecimal("1.10");q.previous=new BigDecimal("1.09");q.history.put(date.minusDays(1),q.previous);q.history.put(date,q.nav);q.complete=true;return q;}
    @Test public void automaticRefreshDeduplicatesSkipsPublishedAndManualAlwaysWorks(){
        Ledger.Data data=new Ledger.Data();data.settings.autoRefresh=true;data.settings.refreshMinutes=120;
        for(String code:new String[]{"000001","000002"}){Ledger.Fund fund=new Ledger.Fund();fund.id=Long.parseLong(code);fund.code=code;fund.name="测试"+code;fund.shares=BigDecimal.ONE;data.funds.add(fund);}
        for(int i=0;i<5;i++){String code=String.format(Locale.ROOT,"%06d",6431+i);data.watchFunds.add(new Ledger.WatchFund(code,"测试"+code));}
        Ledger.WatchFund duplicate=WatchAnalytics.owned(data.funds.get(0));data.watchFunds.add(duplicate);t.store.save(data);
        LocalDate date=LocalDate.of(2026,10,26);List<String> called=new ArrayList<>();MarketRefresh.Fetcher fetch=code->{called.add(code);return quote(code,date);};
        MarketRefresh.run(t.context,true,true,null,fetch,()->date.atTime(14,59));assertTrue(called.isEmpty());
        MarketRefresh.run(t.context,true,true,null,fetch,()->date.atTime(15,0));assertEquals(7,called.size());assertEquals(7,new HashSet<>(called).size());assertEquals(date.toString(),t.store.load().watchFunds.get(5).navDate);
        called.clear();MarketRefresh.run(t.context,true,false,null,fetch,()->date.atTime(17,0));assertTrue(called.isEmpty());
        MarketRefresh.run(t.context,false,false,null,fetch,()->date.atTime(10,0));assertEquals(7,called.size());
    }
    @Test public void oldNavRetriesOnlyAfterIntervalAndFirstVisitBypassesWait(){
        Ledger.Data data=new Ledger.Data();data.settings.autoRefresh=true;data.watchFunds.add(new Ledger.WatchFund("007329","测试"));t.store.save(data);
        LocalDate day=LocalDate.of(2026,10,26);List<String> calls=new ArrayList<>();MarketRefresh.Fetcher fetch=code->{calls.add(code);return quote(code,day.minusDays(3));};
        MarketRefresh.run(t.context,true,true,null,fetch,()->day.atTime(15,0));assertEquals(1,calls.size());
        MarketRefresh.run(t.context,true,false,null,fetch,()->day.atTime(16,59));assertEquals(1,calls.size());
        MarketRefresh.run(t.context,true,false,null,fetch,()->day.atTime(17,0));assertEquals(2,calls.size());
        MarketRefresh.run(t.context,true,true,null,fetch,()->day.atTime(17,1));assertEquals(3,calls.size());
    }
    @Test public void updateCardHasBoundedNotesProgressAndInlineSourceError(){
        t.start(old.sample());t.ui(()->{t.app.openTab(MainActivity.TAB_SETTINGS);t.app.updates().showRelease("v9.0.0","# 新版\n修正收益计算，优化自动更新与稳定性。","https://example.com/release.apk");});
        assertNotNull(t.device.wait(Until.findObject(By.desc("新版更新面板")),3000));assertNotNull(t.device.findObject(By.text("稍后更新")));assertNotNull(t.device.findObject(By.text("立即更新")));
        t.ui(()->{UpdatePanel panel=t.app.updates().panel;panel.downloading();panel.percent(37);assertEquals(37,panel.progress.getProgress());assertFalse(panel.update.isEnabled());panel.failure();assertTrue(panel.update.isEnabled());assertEquals(View.GONE,panel.progress.getVisibility());});
        assertNotNull(t.device.findObject(By.desc("更新下载状态")));assertNotNull(t.device.wait(Until.findObject(By.text("稍后更新")),2000));old.shot("v475-update-error.png");
    }
    @Test public void latestAndImportBannersHaveRequestedPositions(){
        t.start(old.sample());t.ui(()->t.app.updates().banner("已是最新版本V4.7.5"));UiObject2 latest=t.device.wait(Until.findObject(By.desc("最新版本横幅")),2000);assertNotNull(latest);assertTrue(latest.getVisibleBounds().centerY()>t.device.getDisplayHeight()/2);old.shot("v475-latest-banner.png");
        t.ui(()->UiBanner.show(t.app,"导入成功：1只基金",false,true));UiObject2 added=t.device.wait(Until.findObject(By.desc("基金导入结果横幅")),2000);assertNotNull(added);assertTrue(added.getVisibleBounds().top>t.device.getDisplayHeight()/4);assertTrue(added.getVisibleBounds().top<t.device.getDisplayHeight()/2);old.shot("v475-import-banner.png");
    }
    @Test public void installerTargetDirectlyOwnsRealUiAndAliasDoesNotBounce()throws Exception{
        t.store.save(new Ledger.Data());Intent launch=t.context.getPackageManager().getLaunchIntentForPackage(t.context.getPackageName());assertNotNull(launch);
        assertEquals("cn.yueji.balance.UpdateLaunchActivity",launch.getComponent().getClassName());
        Intent installerIntent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_INFO).setPackage(t.context.getPackageName());
        assertNotNull(t.context.getPackageManager().resolveActivity(installerIntent,PackageManager.MATCH_DEFAULT_ONLY));
        t.app=(MainActivity)t.instrument.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));t.instrument.waitForIdleSync();assertTrue(t.app instanceof UpdateLaunchActivity);assertNotNull(t.app.root);assertFalse(t.app.isFinishing());
        ActivityInfo alias=t.context.getPackageManager().getActivityInfo(AppIcons.component(t.context,AppIcons.selected(t.context)),PackageManager.MATCH_DISABLED_COMPONENTS);assertEquals("cn.yueji.balance.UpdateLaunchActivity",alias.targetActivity);
        Intent second=new Intent(t.context,UpdateLaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);t.context.startActivity(second);t.instrument.waitForIdleSync();assertFalse(t.app.isFinishing());old.shot("v475-installer-open.png");
    }
    @Test public void compactOwnedCardsStayReadableAndSmallerThanOldCards(){
        t.start(old.sample());t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));View card=h.desc(t.app.body,"基金000001");assertNotNull(card);assertTrue("compact height",card.getHeight()<=t.app.dp(115));assertNotNull(t.findText((ViewGroup)card,"持有金额"));old.shot("v475-owned-cards.png");
    }
    @Test public void realManualWatchImportShowsResultAndPersistsTheCode(){
        t.start(new Ledger.Data());t.ui(()->{t.app.data.settings.autoRefresh=false;t.app.go("fund_watchlist");t.app.watchScreens.load(Collections.singletonList("007329"));});
        UiObject2 banner=t.device.wait(Until.findObject(By.desc("基金导入结果横幅")),35000);assertNotNull("Import must report its result",banner);
        String result=banner.getText();assertTrue(result,result.startsWith("导入成功")||result.startsWith("导入失败"));
        assertTrue("Real import banner must not use the unmeasured root height",banner.getVisibleBounds().top>t.device.getDisplayHeight()/4);
        assertTrue(banner.getVisibleBounds().top<t.device.getDisplayHeight()/2);
        if(result.startsWith("导入成功")){assertEquals(1,t.store.load().watchFunds.size());assertEquals("007329",t.store.load().watchFunds.get(0).code);}else assertTrue(t.store.load().watchFunds.isEmpty());
        old.shot("v475-real-import-result.png");
    }
    @Test public void actualDownloadFailureIsReportedInsideTheExistingPanel(){
        t.start(old.sample());final UpdatePanel[] original={null};
        t.ui(()->{t.app.openTab(MainActivity.TAB_SETTINGS);t.app.updates().showRelease("v9.0.0","测试下载失败","not-a-url");original[0]=t.app.updates().panel;original[0].update.performClick();});
        assertNotNull(t.device.wait(Until.findObject(By.textContains("下载或校验失败")),4000));
        t.ui(()->{assertSame(original[0],t.app.updates().panel);assertFalse(t.app.updates().busy);assertTrue(original[0].update.isEnabled());assertEquals(View.GONE,original[0].progress.getVisibility());});
    }
    @Test public void newVersionIsAModalWindowNotASettingsChild(){
        t.start(old.sample());t.ui(()->{t.app.openTab(MainActivity.TAB_SETTINGS);t.app.updates().showRelease("v9.0.0","修复回撤修复天数，优化基金更新。","not-a-url");});
        t.ui(()->{
            UpdatePanel panel=t.app.updates().panel;assertTrue(panel.dialog.isShowing());assertNull(t.findText(t.app.body,"发现新版本 v9.0.0"));
            assertTrue((panel.dialog.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND)!=0);
            assertTrue(panel.dialog.getWindow().getAttributes().dimAmount>=.3f);assertNotSame(t.app.getWindow(),panel.dialog.getWindow());
        });old.shot("v475-floating-update-dialog.png");
    }
    @Test public void metricColumnsAreCompactBalancedAndFitLargeAmounts(){
        t.start(old.sample());final LinearLayout[] target={null};
        t.ui(()->{
            t.app.openTab(MainActivity.TAB_FUNDS);LinearLayout row=t.app.row();target[0]=row;
            for(String label:new String[]{"持有金额","当日收益","昨日收益","持有收益"})t.app.fundScreens.compactMetric(row,label,"90,000,000,000.00",Ui.INK);
            t.app.body.addView(row);
        });
        t.ui(()->{
            LinearLayout row=target[0];int width=((View)row.getChildAt(0)).getWidth();
            for(int i=0;i<4;i++){
                LinearLayout cell=(LinearLayout)row.getChildAt(i);assertEquals(width,cell.getWidth(),1);TextView number=(TextView)cell.getChildAt(1);
                assertTrue("full long amount fits",number.getPaint().measureText(number.getText().toString())<=number.getWidth()-number.getPaddingLeft()-number.getPaddingRight()+1);
                if(i==3)assertEquals(Gravity.RIGHT,number.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);
            }
            t.app.go("fund_watchlist");LinearLayout values=t.app.row();t.app.watchScreens.value(values,"+123.45%",1);assertEquals(t.app.watchScreens.metricWidth(),values.getChildAt(0).getLayoutParams().width);
        });old.shot("v475-compact-watch-columns.png");
    }
}
