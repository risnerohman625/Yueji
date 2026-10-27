package cn.yueji.balance;
import android.view.*;import android.widget.*;import androidx.test.ext.junit.runners.AndroidJUnit4;import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class V472UiTest {
 final V470UiTest old=new V470UiTest();final V43UiTest h=old.h;final YuejiTest t=old.t;
 @Before public void before(){old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",26).commit();}
 @After public void after()throws Exception{old.after();}
 @Test public void watchlistLongPressEntersBatchMode(){t.start(old.sample());t.ui(()->{t.app.go("fund_watchlist");t.app.watchScreens.enterSelection("006431");assertTrue(t.app.watchScreens.selectionMode);assertNotNull(t.findText(t.app.root,"添加到"));assertNotNull(t.findText(t.app.root,"删除"));assertNotNull(t.findText(t.app.root,"取消"));});}
 @Test public void switchingRulesUpdatesOnlyRuleContent(){t.start(old.sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");t.app.watchScreens.rulesExpanded=true;t.app.render();});t.instrument.waitForIdleSync();t.ui(()->{LinearLayout content=t.app.watchScreens.ruleContent;assertNotNull(content);t.app.watchScreens.ruleTab=1;t.app.watchScreens.updateRuleContent();assertSame(content,t.app.watchScreens.ruleContent);});}
 @Test public void foldedDetailHasRulesCard(){t.start(old.sample());t.ui(()->{t.app.watchFocus="006431";t.app.go("watch_detail");assertNotNull(t.findText(t.app.body,"买入 / 卖出规则  ▾"));});old.shot("v472-folded-detail.png");}
}
