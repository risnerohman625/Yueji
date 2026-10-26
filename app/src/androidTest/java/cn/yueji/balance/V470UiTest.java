package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.BitmapFactory;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.Assume;
import org.junit.runner.RunWith;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V470UiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
    @Before public void before(){h.before();assertTrue("emulator only",android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"));t.dir=new File(t.context.getExternalFilesDir(null),"qa-v470");t.dir.mkdirs();}
    @After public void after()throws Exception{if(t.app!=null)t.ui(()->t.app.watchScreens.dispose());h.after();}
    Ledger.Data sample(){Ledger.Data d=t.sample();for(int k=0;k<5;k++){String code=String.format(Locale.ROOT,"%06d",6431+k);Ledger.WatchFund f=new Ledger.WatchFund(code,k==0?"汇安鼎利纯债债券A":"测试自选基金"+k);f.fundType="债券型";f.profile.established="2024-07-23";f.profile.scale="11.47亿元";f.profile.updated="2026-10-25";f.profile.buyRules="小于100万元 0.30% | 0.03%";f.profile.saleRules="持有180天以上 0.00%";f.profile.management="0.30%每年";f.profile.custody="0.05%每年";f.profile.service="0.00%每年";f.profile.lockCount=6;f.profile.lockUnit="月";
        LocalDate from=LocalDate.of(2024,7,23),end=FundEngine.now().toLocalDate();int n=0;for(LocalDate day=from;!day.isAfter(end);day=day.plusDays(1)){double price=1+n*(.00012+k*.000025)+.008*Math.sin(n/18d);f.prices.put(day,BigDecimal.valueOf(price));n++;}f.nav=f.prices.lastEntry().getValue();f.previous=f.prices.lowerEntry(f.prices.lastKey()).getValue();f.navDate=f.prices.lastKey().toString();d.watchFunds.add(f);}
        Ledger.WatchGroup group=new Ledger.WatchGroup(500,"短债");group.codes.add("006431");d.watchGroups.add(group);d.watchGroups.add(new Ledger.WatchGroup(501,"长期"));return d;}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(200);t.device.takeScreenshot(new File(t.dir,name));}
    void tap(String text){UiObject2 v=t.device.wait(Until.findObject(By.text(text)),4000);assertNotNull(text,v);v.click();t.instrument.waitForIdleSync();}
    @Test public void fundHomeOpensWatchlistAndItIncludesOwnedFunds(){t.start(sample());t.ui(()->{t.app.openTab(MainActivity.TAB_FUNDS);assertNull(t.findText(t.app.body,"资金划转"));assertNotNull(t.findText(t.app.body,"自选"));t.findText(t.app.body,"自选").performClick();assertEquals("fund_watchlist",t.app.page);assertEquals(7,t.app.watchScreens.visible().size());});shot("v470-watchlist.png");t.ui(()->{t.app.watchScreens.activeGroup="持有";t.app.render();assertEquals(2,t.app.watchScreens.visible().size());});}
    @Test public void sortDirectionsAndPeriodMenuPersist(){t.start(sample());t.ui(()->{t.app.go("fund_watchlist");h.desc(t.app.body,"涨跌幅递增排序").performClick();assertFalse(t.app.data.watchDescending);h.desc(t.app.body,"最大回撤递减排序").performClick();assertEquals(2,t.app.data.watchSort);assertTrue(t.app.data.watchDescending);h.desc(t.app.body,"自选数据周期").performClick();});tap("近3月");assertEquals(2,t.store.load().watchRange);shot("v470-watch-sort-period.png");}
    @Test public void scrollableDetailsFitThreePortraitSizesAndSwitchCharts()throws Exception{
        for(String[] config:new String[][]{{"1080x1920","420","1.0"},{"1080x2400","420","1.0"},{"1260x2800","560","1.3"}}){t.device.executeShellCommand("wm size "+config[0]);t.device.executeShellCommand("wm density "+config[1]);t.device.executeShellCommand("settings put system font_scale "+config[2]);t.start(sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");assertTrue(t.app.body.getParent() instanceof ScrollView);});SystemClock.sleep(180);t.ui(()->{WatchChart chart=h.first(t.app.body,WatchChart.class);assertNotNull(chart);assertTrue("plot minimum height",chart.getHeight()>t.app.dp(130));View rules=t.findText(t.app.body,"买入 / 卖出规则  ▾");int[] pos=new int[2];rules.getLocationOnScreen(pos);((ScrollView)t.app.body.getParent()).fullScroll(View.FOCUS_DOWN);assertTrue("rules are laid out",rules.getHeight()>0);t.findText(t.app.body,"回撤修复").performClick();assertEquals(1,t.app.watchScreens.detailChartMode);assertNotNull(t.findText(t.app.body,"最大回撤修复天数"));});shot("v470-watch-detail-"+config[0]+"-"+config[2]+".png");}
    }
    @Test public void rulesExpandIntoScrollablePageAndHaveTabs(){t.start(sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");t.findText(t.app.body,"买入 / 卖出规则  ▾").performClick();assertTrue(t.app.body.getParent() instanceof ScrollView);assertTrue(t.app.watchScreens.rulesExpanded);assertNotNull(t.findText(t.app.body,"买入 / 卖出规则  ▴"));});shot("v470-watch-rules-expanded.png");}
    @Test public void cursorShowsSelectionAndExpiresAfterRelease(){t.start(sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");});WatchChart chart=h.first(t.app.body,WatchChart.class);int[] at=new int[2];t.ui(()->chart.getLocationOnScreen(at));t.device.swipe(at[0]+chart.getWidth()-30,at[1]+chart.getHeight()/2,at[0]+chart.getWidth()/3,at[1]+chart.getHeight()/2,20);t.instrument.waitForIdleSync();assertTrue(chart.selected>=0);shot("v470-watch-cursor.png");SystemClock.sleep(2750);t.instrument.waitForIdleSync();assertEquals(-1,chart.selected);}
    @Test public void groupMoveCannotRemoveAllOrEditHeld(){t.start(sample());t.ui(()->{t.app.go("fund_watchlist");t.app.watchScreens.activeGroup="全部";t.app.watchScreens.addToGroup(t.app.watchScreens.find("006431"),501,true);assertEquals(7,t.app.watchScreens.all().size());t.app.watchScreens.activeGroup="短债";t.app.watchScreens.addToGroup(t.app.watchScreens.find("006431"),501,true);assertFalse(t.store.load().watchGroups.get(0).codes.contains("006431"));assertTrue(t.store.load().watchGroups.get(1).codes.contains("006431"));t.app.watchScreens.activeGroup="持有";t.app.watchScreens.enterSelection("000001");assertEquals(2,t.app.watchScreens.visible().size());});}
    @Test public void newBuyDefaultsToAccountAndOldBuyStaysUnlinked(){t.start(sample());t.ui(()->t.app.fundScreens.buy(t.app.data.funds.get(0),null));UiObject2 field=t.device.wait(Until.findObject(By.desc("买入资金来源账户")),4000);assertNotNull(field);assertTrue(field.getText()==null||!field.getText().contains("不关联"));shot("v470-buy-account.png");t.device.pressBack();t.ui(()->t.app.fundScreens.buy(t.app.data.funds.get(0),t.app.data.funds.get(0).buys.get(0)));assertTrue(t.device.wait(Until.hasObject(By.text("不关联账户")),4000));}
    @Test public void planButtonIsBetweenBuyAndTradesAndConfigRequiresAccount(){t.start(sample());t.ui(()->{t.app.fundFocus=40;t.app.go("fund_detail");View buy=t.findText(t.app.body,"追加买入"),plan=h.desc(t.app.body,"定投配置"),trades=h.desc(t.app.body,"交易记录");ViewGroup actions=(ViewGroup)plan.getParent();assertSame(buy,actions.getChildAt(0));assertSame(plan,actions.getChildAt(1));assertSame(trades,actions.getChildAt(2));plan.performClick();});assertTrue(t.device.wait(Until.hasObject(By.text("定投资金账户（必选）")),4000));shot("v470-plan-config.png");}
    @Test public void assetSelectorHasTriangleAndCardSurface(){t.start(sample());t.ui(()->{TextView title=(TextView)h.desc(t.app.body,"切换资产视图");assertNotNull(((View)title.getParent()).getBackground());assertNotNull(t.findText(t.app.body,"◀"));assertNotNull(t.findText(t.app.body,"▶"));assertEquals(title.getTextSize(),((TextView)t.findText(t.app.body,"＋ 添加账户")).getTextSize(),2.0);h.desc(t.app.body,"下一个资产视图").performClick();});shot("v470-asset-composition.png");}
    @Test public void settingsContainsVerifiedRepository(){t.start(sample());t.ui(()->{t.app.openTab(MainActivity.TAB_SETTINGS);assertNotNull(t.findText(t.app.body,"GitHub"));});shot("v470-settings-github.png");}

    void fill(String desc,String text){UiObject2 v=t.device.wait(Until.findObject(By.desc(desc)),4000);assertNotNull(desc,v);v.setText(text);}
    @Test public void planSavesAndPausesWithoutChangingBalances(){
        t.start(sample());long total=t.store.load().total();t.ui(()->t.app.fundScreens.configurePlan(t.app.data.funds.get(0)));
        fill("每期金额","100");fill("每期手续费","0");t.device.findObject(By.desc("定投资金账户（必选）")).click();tap("主账户 · 招商银行");tap("保存");
        Ledger.Data saved=t.store.load();assertTrue(saved.funds.get(0).plan.enabled);assertEquals(10000,saved.funds.get(0).plan.amount);assertEquals(1,saved.funds.get(0).plan.accountId);assertEquals(total,saved.total());
        t.ui(()->t.app.fundScreens.configurePlan(t.app.data.funds.get(0)));UiObject2 pause=t.device.findObject(By.text("暂停定投"));if(pause==null){t.device.swipe(500,1750,500,600,20);pause=t.device.wait(Until.findObject(By.text("暂停定投")),4000);}assertNotNull(pause);pause.click();t.instrument.waitForIdleSync();assertFalse(t.store.load().funds.get(0).plan.enabled);assertEquals(total,t.store.load().total());
    }
    @Test public void saleUiCreditsTheSelectedAccountExactlyOnce(){
        t.start(sample());long before=t.store.load().balance(t.store.load().accounts.get(0));t.ui(()->{Ledger.Fund f=t.app.data.funds.get(0);t.app.fundScreens.sell(f,f.buys.get(0));});fill("本批次卖出净到账金额（已扣费用）","1100");tap("保存");
        Ledger.Data saved=t.store.load();assertEquals(before+110000,saved.balance(saved.accounts.get(0)));assertTrue(saved.funds.get(0).buys.get(0).saleAccountLinked);
        t.ui(()->{Ledger.Fund f=t.app.data.funds.get(0);t.app.fundScreens.sell(f,f.buys.get(0));});tap("保存");saved=t.store.load();assertEquals(before+110000,saved.balance(saved.accounts.get(0)));
    }
    @Test public void bankOpeningUiDefaultsToAccountAndNoPlanButton(){
        t.start(sample());long before=t.store.load().balance(t.store.load().accounts.get(0));t.ui(()->t.app.bankScreens.start(0));t.ui(()->{t.app.bankScreens.nameField.setText("测试理财账户关联");t.app.bankScreens.balanceField.setText("100");t.findText(t.app.body,"确认保存").performClick();});
        Ledger.Data saved=t.store.load();assertEquals(1,saved.banks.size());assertTrue(saved.banks.get(0).openingAccountLinked);assertEquals(before-10000,saved.balance(saved.accounts.get(0)));t.ui(()->assertNull(t.findText(t.app.body,"定投")));
    }
    @Test public void blankLedgerShipsNoAccountsFundsOrWatchlist(){t.start(new Ledger.Data());t.ui(()->{assertTrue(t.app.data.accounts.isEmpty());assertTrue(t.app.data.funds.isEmpty());assertTrue(t.app.data.watchFunds.isEmpty());t.app.go("fund_watchlist");assertTrue(t.app.watchScreens.visible().isEmpty());});shot("v470-empty-watchlist.png");}
    @Test public void themeDarkAndAssetCompositionActuallyRender(){
        t.start(sample());t.ui(()->{t.app.assetTab=0;t.app.render();assertNotNull(h.first(t.app.body,AssetDonut.class));});shot("v470-asset-composition.png");t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",2).commit();t.app.watchFocus="006431";t.app.go("watch_detail");});shot("v470-watch-dark.png");
    }
    @Test public void detailMetricRowsUseEvenCenteredColumnsAndCalendarAge(){
        t.start(sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");});
        t.ui(()->{
            String navDate=t.app.watchScreens.find("006431").navDate;
            for(String[] labels:new String[][]{{"近1年涨跌幅","成立来涨跌幅","日涨跌幅\n"+navDate,"最新净值\n"+navDate},{"成立日期","距今","基金规模"},{"本时期涨跌幅","本时期最高下跌幅度"}}){
                int previous=-1,step=-1,width=-1;
                for(String label:labels){ViewGroup column=(ViewGroup)h.desc(t.app.body,"自选详情指标"+label);assertNotNull(label,column);
                    if(width<0)width=column.getWidth();else assertEquals("equal metric column widths",width,column.getWidth(),2);
                    int center=column.getLeft()+column.getWidth()/2;if(previous>=0){if(step<0)step=center-previous;else assertEquals("even metric centers",step,center-previous,2);}previous=center;
                    for(int i=0;i<column.getChildCount();i++){TextView text=(TextView)column.getChildAt(i);assertEquals(Gravity.CENTER_HORIZONTAL,text.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);}
                }
            }
            assertNotNull(t.findText(t.app.body,WatchAnalytics.elapsed(LocalDate.of(2024,7,23),FundEngine.now().toLocalDate())));
        });shot("v470-watch-even-columns.png");
    }
}
