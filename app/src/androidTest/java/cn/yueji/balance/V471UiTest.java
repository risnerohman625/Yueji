package cn.yueji.balance;
import android.view.*;
import android.widget.*;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class V471UiTest {
 final V470UiTest old=new V470UiTest();final V43UiTest h=old.h;final YuejiTest t=old.t;
 @Before public void before(){old.before();}
 @After public void after()throws Exception{old.after();}
 @Test public void detailsKeepChartHeightAndRuleScroll(){t.start(old.sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");assertTrue(t.app.body.getParent() instanceof ScrollView);assertNull(t.findText(t.app.body,"刷新"));assertNotNull(t.findText(t.app.body,"自选基金详情页"));assertNotNull(t.findText(t.app.body,"近1年"));assertNotNull(t.findText(t.app.body,"日涨跌幅\n"+t.app.watchScreens.find("006431").navDate));});t.instrument.waitForIdleSync();final int[] height={0};t.ui(()->{height[0]=h.first(t.app.body,WatchChart.class).getHeight();t.findText(t.app.body,"买入 / 卖出规则  ▾").performClick();});t.instrument.waitForIdleSync();SystemClock.sleep(300);t.ui(()->{assertEquals(height[0],h.first(t.app.body,WatchChart.class).getHeight());assertTrue(((ScrollView)t.app.body.getParent()).getScrollY()>0);t.app.watchScreens.ruleTab=1;t.app.watchScreens.renderScroll(false);});t.instrument.waitForIdleSync();t.ui(()->assertTrue(((ScrollView)t.app.body.getParent()).getScrollY()>0));old.shot("v471-detail-rules.png");}
 @Test public void customReplacesGroupAndSortingArrowsAreVertical(){t.start(old.sample());t.ui(()->{t.app.go("fund_watchlist");assertNull(t.findText(t.app.body,"分组"));assertNotNull(t.findText(t.app.body,"+自定义"));View up=h.desc(t.app.body,"涨跌幅递增排序"),down=h.desc(t.app.body,"涨跌幅递减排序");assertSame(up.getParent(),down.getParent());assertEquals(LinearLayout.VERTICAL,((LinearLayout)up.getParent()).getOrientation());});old.shot("v471-watchlist.png");}
 @Test public void deletingWatchItemLeavesPurchasedFunds(){t.start(old.sample());final int count=t.store.load().funds.size();t.ui(()->{t.app.go("fund_watchlist");t.app.watchScreens.enterSelection("006431");t.findText(t.app.root,"删除").performClick();});old.tap("删除");assertEquals(count,t.store.load().funds.size());assertFalse(t.store.load().watchFunds.stream().anyMatch(f->f.code.equals("006431")));}


}
