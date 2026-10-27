package cn.yueji.balance;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V477UiTest {
    final V470UiTest old=new V470UiTest();final YuejiTest t=old.t;final V43UiTest h=old.h;
    @Before public void before(){old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",27).commit();}
    @After public void after()throws Exception{old.after();}
    @Test public void detailPeriodDoesNotChangeListPeriodAndGroupEntryExists(){t.start(old.sample());t.ui(()->{t.app.go("fund_watchlist");t.app.watchScreens.range=2;t.app.watchFocus="006431";t.app.go("watch_detail");assertNotNull(t.findText(t.app.body,"+添加分组"));View chip=h.desc(t.app.body,"绘图周期成立来");chip.performClick();assertEquals(9,t.app.watchScreens.detailRange);assertEquals(2,t.app.watchScreens.range);t.app.go("fund_watchlist");assertEquals(2,t.app.watchScreens.range);});old.shot("v477-isolated-period.png");}
    @Test public void cachedFactsSurviveOwnedOverlapAndRestart(){Ledger.Data d=old.sample();Ledger.WatchFund w=WatchAnalytics.owned(d.funds.get(0));w.profile.established="2020-01-01";w.profile.scale="4亿元";w.profile.updated="2026-10-27";d.watchFunds.add(w);t.start(d);t.ui(()->{t.app.watchFocus=w.code;t.app.go("watch_detail");assertNotNull(t.findText(t.app.body,"2020-01-01"));assertNotNull(t.findText(t.app.body,"4亿元"));});t.start(t.store.load());t.ui(()->{t.app.watchFocus=w.code;t.app.go("watch_detail");assertNotNull(t.findText(t.app.body,"2020-01-01"));assertNotNull(t.findText(t.app.body,"4亿元"));});old.shot("v477-cached-profile.png");}
    @Test public void detailGroupDialogMovesFundAndCanRestoreInbox(){t.start(old.sample());t.ui(()->{t.app.watchFocus="006432";t.app.go("watch_detail");t.findText(t.app.body,"+添加分组").performClick();});old.tap("短债");old.tap("是否从本组移除（持有组由持仓自动管理）");old.tap("确认");assertTrue(t.store.load().watchGroups.get(0).codes.contains("006432"));assertFalse(WatchGroups.member(t.store.load(),"006432","全部"));t.ui(()->t.findText(t.app.body,"+添加分组").performClick());old.tap("全部");old.tap("确认");assertTrue(WatchGroups.member(t.store.load(),"006432","全部"));old.shot("v477-detail-group.png");}
    @Test public void calloutsAvoidAllMarkersAndEachOtherAtBothCorners(){t.start(old.sample());t.ui(()->{for(boolean right:new boolean[]{false,true}){Ledger.WatchFund f=new Ledger.WatchFund("006431","test");String[] prices=right?new String[]{"1","1.1","1.2","1.3","1.4","1.3","1.4"}:new String[]{"1.1","1.0","1.1","1.2","1.3","1.4","1.5"};for(int i=0;i<prices.length;i++)f.prices.put(LocalDate.of(2026,1,1).plusDays(i),new BigDecimal(prices[i]));WatchChart chart=new WatchChart(t.app,WatchAnalytics.calculate(f,9,LocalDate.of(2026,2,1)),true);chart.layout(0,0,t.app.dp(310),t.app.dp(180));Bitmap bitmap=Bitmap.createBitmap(chart.getWidth(),chart.getHeight(),Bitmap.Config.ARGB_8888);chart.draw(new Canvas(bitmap));assertEquals(2,chart.repairLabels.size());assertFalse(RectF.intersects(chart.repairLabels.get(0),chart.repairLabels.get(1)));for(RectF box:chart.repairLabels)for(PointF point:chart.repairPoints()){RectF padded=new RectF(box);padded.inset(-chart.d(5),-chart.d(5));assertFalse(padded.contains(point.x,point.y));}bitmap.recycle();}});}
}
