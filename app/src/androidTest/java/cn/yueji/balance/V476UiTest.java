package cn.yueji.balance;

import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V476UiTest {
    final V470UiTest old=new V470UiTest();final YuejiTest t=old.t;final V43UiTest h=old.h;
    @Before public void before(){old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",25).commit();}
    @After public void after()throws Exception{old.after();}
    @Test public void bankAndFundCardsSharePaddingMetricsAndOuterGap(){
        Ledger.Data data=old.sample();BankLedger.Draft draft=new BankLedger.Draft();draft.name="银行理财紧凑卡片";draft.balance=100000L;draft.at=LocalDateTime.of(2026,10,1,10,0);BankLedger.Product bank=BankLedger.apply(data,0,draft,false,null);bank.tag="";t.start(data);
        t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));
        t.ui(()->{LinearLayout fund=(LinearLayout)h.desc(t.app.body,"基金000001"),card=(LinearLayout)h.desc(t.app.body,"银行理财"+bank.id);assertNotNull(card);assertEquals(fund.getPaddingTop(),card.getPaddingTop());assertEquals(fund.getPaddingBottom(),card.getPaddingBottom());assertEquals(fund.getHeight(),card.getHeight(),t.app.dp(1));assertEquals(((View)fund.getParent()).getLayoutParams().getClass(),((View)card.getParent()).getLayoutParams().getClass());assertEquals(((LinearLayout.LayoutParams)((View)fund.getParent()).getLayoutParams()).bottomMargin,((LinearLayout.LayoutParams)((View)card.getParent()).getLayoutParams()).bottomMargin);});
        old.shot("v476-bank-fund-spacing.png");
    }
    @Test public void groupsCountActualUniqueFundsAndDuplicatesDoNotImport(){
        Ledger.Data data=old.sample();data.watchFunds.add(WatchAnalytics.owned(data.funds.get(0)));t.start(data);
        t.ui(()->{t.app.go("fund_watchlist");assertNotNull(t.findText(t.app.body,"全部(7)"));assertNotNull(t.findText(t.app.body,"持有(2)"));assertNotNull(t.findText(t.app.body,"短债(1)"));assertNotNull(t.findText(t.app.body,"长期(0)"));assertNotNull(t.findText(t.app.body,"+自定义"));});
        String before=t.store.exportJson();t.ui(()->t.app.watchScreens.load(Collections.singletonList("006431")));
        assertNotNull(t.device.wait(Until.findObject(By.text("该基金已在自选列表")),2000));assertEquals(before,t.store.exportJson());t.ui(()->assertNull(t.app.watchScreens.progress));old.shot("v476-existing-watch.png");
    }
    @Test public void firstThreeMetricsFit1260By2800AndHeadersAreCentered()throws Exception{
        for(String density:new String[]{"480","560","600"}){
            t.device.executeShellCommand("wm size 1260x2800");t.device.executeShellCommand("wm density "+density);t.start(old.sample());t.ui(()->t.app.go("fund_watchlist"));
            t.ui(()->{
                HorizontalScrollView scroll=(HorizontalScrollView)h.desc(t.app.body,"自选指标列表，左右滑动");LinearLayout numbers=(LinearLayout)scroll.getChildAt(0),header=(LinearLayout)numbers.getChildAt(0),values=(LinearLayout)numbers.getChildAt(1);
                Rect visible=new Rect();assertTrue(scroll.getGlobalVisibleRect(visible));
                for(int i=0;i<3;i++){
                    View head=header.getChildAt(i),value=values.getChildAt(i);int[] hp=new int[2],vp=new int[2];head.getLocationOnScreen(hp);value.getLocationOnScreen(vp);assertTrue("full metric including % fits",vp[0]+value.getWidth()<=visible.right+1);assertEquals(hp[0]+head.getWidth()/2,vp[0]+value.getWidth()/2,1);
                    LinearLayout group=(LinearLayout)head;View label=group.getChildAt(0),arrows=group.getChildAt(1);assertEquals("combined label and arrows centered",head.getWidth()/2,(label.getLeft()+arrows.getRight())/2,2);assertTrue(arrows.getWidth()<=t.app.dp(13));
                    TextView text=(TextView)value;assertTrue(text.getPaint().measureText(text.getText().toString())<=text.getWidth()+1);
                }
                assertTrue(t.app.body.getPaddingLeft()<=t.app.dp(5));
            });old.shot("v476-watch-1260x2800-"+density+".png");
        }
    }
}
