package cn.yueji.balance;

import android.content.*;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

/** Device checks for the 4.3.0 requirements, reusing only the fixture helpers, not the old test methods. */
@RunWith(AndroidJUnit4.class)
public class V43UiTest {
    YuejiTest t=new YuejiTest();Map<String,?> preferences;
    @Before public void before(){t.before();preferences=new HashMap<>(t.context.getSharedPreferences("MainActivity",0).getAll());t.context.getSharedPreferences("MainActivity",0).edit().putInt("appearance",1).putInt("palette",0).commit();}
    @After public void after()throws Exception{
        try{t.device.executeShellCommand("wm size reset");t.device.executeShellCommand("wm density reset");t.device.executeShellCommand("settings put system font_scale 1.0");}
        finally{
            SharedPreferences.Editor p=t.context.getSharedPreferences("MainActivity",0).edit().clear();
            for(Map.Entry<String,?> e:preferences.entrySet()){
                Object v=e.getValue();if(v instanceof String)p.putString(e.getKey(),(String)v);else if(v instanceof Integer)p.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)p.putLong(e.getKey(),(Long)v);else if(v instanceof Boolean)p.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Float)p.putFloat(e.getKey(),(Float)v);
            }
            p.commit();t.after();
        }
    }
    View desc(View view,String label){
        if(view.getContentDescription()!=null&&label.contentEquals(view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=desc(g.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    <T extends View> T first(View view,Class<T> type){
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){T found=first(g.getChildAt(i),type);if(found!=null)return found;}}
        return null;
    }
    void clickText(String label){t.ui(()->{View v=t.findText(t.app.body,label);assertNotNull(label,v);v.performClick();});}
    @Test public void assetHeroAlignmentAndComposition(){
        t.start(t.sample());
        t.ui(()->{
            TextView label=(TextView)t.findText(t.app.body,"总资产");
            LinearLayout left=(LinearLayout)label.getParent(),head=(LinearLayout)left.getParent();
            assertEquals(Gravity.CENTER_VERTICAL,head.getGravity()&Gravity.VERTICAL_GRAVITY_MASK);
            assertEquals(Gravity.START,((TextView)left.getChildAt(2)).getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);
            desc(t.app.body,"资产构成").performClick();assertEquals("asset_mix",t.app.page);
            assertNotNull(desc(t.app.body,"账户类型与基金占比环形图"));
        });t.shot("v43-asset-composition.png");
    }
    @Test public void accountHistoryHasPurposeAndEditLivesBesideSwipeDelete(){
        t.start(t.sample());t.swipeLeft("账户主账户");
        assertNotNull(t.device.findObject(By.desc("编辑账户主账户")));assertNotNull(t.device.findObject(By.desc("删除账户主账户")));
        t.device.findObject(By.desc("编辑账户主账户")).click();assertTrue(t.device.wait(Until.hasObject(By.text("编辑账户")),3000));
        t.ui(()->{t.app.accountId=1;t.app.go("account_history");});
        assertTrue(t.device.hasObject(By.text("账户余额更新记录")));assertFalse(t.device.hasObject(By.text("编辑账户")));
    }
    @Test public void settingsAccordionAllowsOnlyOneOpenGroup(){
        t.start(t.sample());t.ui(()->{
            t.app.openTab(MainActivity.TAB_SETTINGS);
            ((LinearLayout)desc(t.app.body,"分组基金数据管理")).getChildAt(0).performClick();
            assertEquals(View.VISIBLE,t.app.sectionContents.get("funds").getVisibility());
            ((LinearLayout)desc(t.app.body,"分组更新与通知")).getChildAt(0).performClick();
            assertEquals(View.GONE,t.app.sectionContents.get("funds").getVisibility());
            assertEquals(Collections.singleton("notify"),t.app.openSections);
            assertNotNull(desc(t.app.body,"分组日历更新"));
        });t.shot("v43-settings-accordion.png");
    }
    @Test public void annualAxisOpensSortableIncomeAndMonthlyExpensePage(){
        t.start(t.sample());t.ui(()->{t.app.openTab(MainActivity.TAB_FLOW);t.app.yearPopup(2026);});
        assertTrue(t.device.wait(Until.hasObject(By.text("2026 年收支明细")),3000));
        assertTrue(t.device.hasObject(By.text("年度结余")));assertTrue(t.device.hasObject(By.text("月支出")));
        t.tapSegment(1);assertEquals(1,t.app.yearKind);
        assertTrue(t.device.hasObject(By.textContains("奖学金")));t.shot("v43-year-income.png");
        t.tapSegment(0);assertEquals(0,t.app.yearKind);assertTrue(t.device.hasObject(By.text("2026/10")));t.shot("v43-year-expense.png");
    }
    @Test public void keywordSearchUpdatesOnlyRecordResults(){
        Ledger.Data d=t.sample();d.entries.get(0).note="朋友AbC转账";
        t.start(d);t.ui(()->{t.app.categoryFocus="全部收入";t.app.go("income_detail");});
        final View[] chart={null};
        t.ui(()->{chart[0]=first(t.app.body,FinanceChart.class);((EditText)desc(t.app.body,"搜索明细")).setText("abc");});
        assertTrue(t.device.hasObject(By.textContains("找到 1 笔记录 · 共计")));
        t.ui(()->{assertSame(chart[0],first(t.app.body,FinanceChart.class));((EditText)desc(t.app.body,"搜索明细")).setText("不存在");});
        assertTrue(t.device.hasObject(By.textContains("找到 0 笔记录 · 共计")));t.shot("v43-search.png");
    }
    @Test public void labelLibrarySavesUniqueIconsAndKeepsAnIncomeDraft(){
        t.start(t.sample());t.ui(()->{t.app.openEntry(Ledger.INCOME,null);
            ((EditText)desc(t.app.body,"金额")).setText("123.45");
            ((EditText)desc(t.app.body,"备注（可选）")).setText("朋友abc");
            MainActivity.DateControl date=(MainActivity.DateControl)desc(t.app.body,"到账时间日期").getTag();date.at=LocalDateTime.of(2026,10,20,21,20);date.update();
            t.app.manageCategories();
        });
        assertEquals("labels",t.app.page);
        t.device.wait(Until.findObject(By.desc("新增标签")),3000).click();
        String icon=LabelRules.firstFree(t.store.load(),null);
        t.ui(()->{((EditText)desc(t.app.body,"标签名称")).setText("兼职");desc(t.app.body,"图标"+icon).performClick();});
        clickText("保存");
        assertEquals(icon,t.store.load().iconOf("兼职"));t.shot("v43-label-grid.png");
        t.ui(()->{
            t.app.back();assertEquals("income_form",t.app.page);
            assertEquals("123.45",((EditText)desc(t.app.body,"金额")).getText().toString());
            assertEquals("朋友abc",((EditText)desc(t.app.body,"备注（可选）")).getText().toString());
            MainActivity.DateControl date=(MainActivity.DateControl)desc(t.app.body,"到账时间日期").getTag();assertEquals(LocalDateTime.of(2026,10,20,21,20),date.at);
            ((Spinner)desc(t.app.body,"到账账户（必选）")).setSelection(1);t.findText(t.app.body,"保存记录").performClick();
        });
        boolean found=false;for(Ledger.Entry e:t.store.load().entries)if(e.note.equals("朋友abc")){assertEquals(12345,e.amount);assertEquals(LocalDateTime.of(2026,10,20,21,20),e.at);found=true;}assertTrue(found);
    }
    @Test public void incomeAxisLongPressReordersAndPersistsCategories(){
        t.start(t.sample());t.ui(()->t.app.go("income_detail"));
        UiObject2 first=t.device.wait(Until.findObject(By.desc("收入类别详情奖学金")),3000);
        UiObject2 last=t.device.findObject(By.desc("收入类别详情家教"));assertNotNull(last);
        Rect a=first.getVisibleBounds(),b=last.getVisibleBounds();long down=SystemClock.uptimeMillis();
        t.instrument.sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,a.centerX(),a.centerY(),0));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+180);
        for(int i=1;i<=8;i++){t.instrument.sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,a.centerX()+(b.centerX()-a.centerX())*i/8f,a.centerY(),0));SystemClock.sleep(30);}
        t.instrument.sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,b.centerX(),a.centerY(),0));t.device.waitForIdle();
        assertTrue(t.store.load().categories.indexOf("家教")<t.store.load().categories.indexOf("奖学金"));
        t.shot("v43-income-reordered.png");
    }
    @Test public void hiddenFundIsAbsentFromHomeAndLastInCumulativeHistory(){
        t.start(t.sample());t.ui(()->{t.app.openTab(MainActivity.TAB_FUNDS);t.app.fundScreens.setHidden(t.app.data.funds.get(1),true);});
        assertFalse(t.device.hasObject(By.desc("基金000002")));assertNotNull(t.device.findObject(By.desc("基金持有总金额历史走势")));
        assertNotNull(t.device.findObject(By.desc("基金排序")));t.shot("v43-fund-summary.png");
        t.ui(()->t.app.go("fund_profit"));
        assertTrue(t.device.hasObject(By.desc("累计000002")));
        List<Ledger.Fund> sorted=FundRules.sorted(t.store.load(),0,true,FundEngine.now().toLocalDate());assertTrue(sorted.get(sorted.size()-1).hidden);
        t.ui(()->t.app.fundScreens.confirmPurge(t.app.data.funds.get(0)));
        assertFalse(t.device.hasObject(By.text("确认永久删除")));assertEquals(2,t.store.load().funds.size());
    }
    @Test public void lastPurchaseAndLegacyHoldingCannotBypassDeletionProtection(){
        t.start(t.sample());t.ui(()->{
            Ledger.Fund fund=t.app.data.funds.get(1);
            assertFalse(t.app.fundScreens.mutateBuy(fund,fund.buys.get(0),true,null));
        });
        assertEquals(2,t.store.load().funds.size());assertEquals(1,t.store.load().funds.get(1).buys.size());
        t.device.wait(Until.findObject(By.text("知道了")),3000).click();
        t.ui(()->{
            Ledger.Data candidate=t.app.copy();candidate.funds.get(1).buys.clear();t.app.commit(candidate);
            t.app.fundFocus=41;t.app.go("fund_trades");
        });
        clickText("从首页删除旧持仓");t.device.wait(Until.findObject(By.text("确认删除")),3000);t.device.waitForIdle();
        t.device.findObject(By.clazz("android.widget.Button").text("确认删除")).click();
        for(int attempt=0;attempt<20&&!t.store.load().funds.get(1).removed();attempt++){
            SystemClock.sleep(100);
            if(attempt==8&&t.device.hasObject(By.clazz("android.widget.Button").text("确认删除")))t.device.findObject(By.clazz("android.widget.Button").text("确认删除")).click();
        }
        t.device.waitForIdle();t.shot("v44-legacy-remove.png");
        assertEquals(2,t.store.load().funds.size());assertTrue(t.store.load().funds.get(1).removed());
    }
    @Test public void chartMarksHaveSeparateAmountTooltips()throws Exception{
        t.start(t.sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        SystemClock.sleep(150);
        int[] xy=new int[2];
        t.ui(()->{
            FinanceChart chart=first(t.app.body,FinanceChart.class);chart.getLocationOnScreen(xy);
            long b=Ledger.month(t.app.data,YearMonth.of(2026,10)).expense;
            xy[0]+=Math.round(chart.pointX(9));xy[1]+=Math.round(chart.pointY(b));
        });
        t.device.click(xy[0],xy[1]);t.device.waitForIdle();
        Field selected=FinanceChart.class.getDeclaredField("selected");selected.setAccessible(true);
        final int[] at={-1};t.ui(()->{try{at[0]=selected.getInt(first(t.app.body,FinanceChart.class));}catch(Exception e){throw new AssertionError(e);}});
        assertEquals(9,at[0]);t.shot("v43-month-tooltip.png");
        t.ui(()->t.app.monthPopup(10));assertTrue(t.device.wait(Until.hasObject(By.text("总支出")),3000));t.shot("v43-month-detail.png");
    }
    @Test public void primaryPagesFitShortAndHighResolutionPortraits()throws Exception{
        try{
            for(String size:new String[]{"1080x1920","1080x2400","1260x2800"}){
                t.device.executeShellCommand("wm size "+size);t.device.executeShellCommand("wm density 420");
                t.start(t.sample());
                t.ui(()->{
                    t.app.openTab(MainActivity.TAB_FLOW);
                    assertFalse(t.app.body.getParent() instanceof ScrollView);
                });
                SystemClock.sleep(150);assertBodyFits();t.shot("v43-flow-"+size+".png");
                t.ui(()->{t.app.fundFocus=40;t.app.go("fund_detail");});SystemClock.sleep(150);assertBodyFits();t.shot("v43-detail-"+size+".png");
            }
            t.device.executeShellCommand("wm size 1080x1920");t.device.executeShellCommand("settings put system font_scale 1.3");
            t.start(t.sample());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));SystemClock.sleep(150);assertBodyFits();t.shot("v43-flow-large-font.png");
        }finally{t.device.executeShellCommand("wm size reset");t.device.executeShellCommand("wm density reset");t.device.executeShellCommand("settings put system font_scale 1.0");}
    }
    void assertBodyFits(){
        t.ui(()->{
            LinearLayout body=t.app.body;assertTrue(body.getHeight()>0);
            View last=body.getChildAt(body.getChildCount()-1);assertTrue("last card fits",last.getBottom()<=body.getHeight()-body.getPaddingBottom()+2);
            assertTrue("chart is allocated meaningful height",last.getHeight()>t.app.dp(140));
        });
    }
    @Test public void everyPaletteSupportsLightDarkAndSystem(){
        t.start(t.sample());
        for(int mode=0;mode<3;mode++)for(int palette=0;palette<Ui.PALETTES.length;palette++){
            final int md=mode,pl=palette;
            t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",md).putInt("palette",pl).commit();t.app.openTab(MainActivity.TAB_FUNDS);});
            assertEquals(mode==2||mode==0&&(t.app.getResources().getConfiguration().uiMode&0x30)==0x20,Ui.dark);
        }
        t.shot("v43-theme-dark.png");t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",1).putInt("palette",1).commit();t.app.openTab(MainActivity.TAB_FLOW);});t.shot("v43-theme-blue.png");
    }
    @Test public void reminderIntentOpensBalanceUpdateDirectly(){
        t.start(t.sample());t.ui(()->t.app.onNewIntent(new Intent(t.context,MainActivity.class).putExtra("openPage","check")));
        assertEquals("check",t.app.page);assertTrue(t.device.hasObject(By.text("更新全部账户")));assertNull(t.app.snapshotEditing);
    }
}
