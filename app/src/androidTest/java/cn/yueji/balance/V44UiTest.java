package cn.yueji.balance;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V44UiTest {
    V43UiTest h=new V43UiTest();YuejiTest t=h.t;
    @Before public void before(){h.before();}
    @After public void after()throws Exception{h.after();}
    List<TextView> fitted(View view){
        List<TextView> result=new ArrayList<>();
        if(view instanceof TextView&&!(view instanceof EditText)&&((TextView)view).getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM)result.add((TextView)view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)result.addAll(fitted(group.getChildAt(i)));}
        return result;
    }
    void assertFits(){
        t.ui(()->{
            List<TextView> fields=fitted(t.app.body);assertFalse(fields.isEmpty());
            for(TextView field:fields)if(field.isShown()&&field.getWidth()>0&&field.getText().length()>0){
                float width=field.getWidth()-field.getCompoundPaddingLeft()-field.getCompoundPaddingRight();
                assertTrue(field.getText()+" measured="+field.getPaint().measureText(field.getText().toString())+" available="+width,
                    field.getPaint().measureText(field.getText().toString())<=width+1.5f);
                assertTrue("number is left aligned: "+field.getText(),!"bounded-number".equals(field.getTag())||(field.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==Gravity.START);
            }
        });
    }
    Ledger.Data realistic(){
        Ledger.Data data=t.sample();
        data.history.add(new Ledger.HistoryMonth(YearMonth.of(2025,1),1635029,LocalDateTime.of(2025,2,1,0,0)));
        Ledger.Entry e=new Ledger.Entry(500,true,2197600,LocalDateTime.of(2025,1,19,0,0),"奖学金","年度金额测试",0);e.category="奖学金";data.entries.add(e);
        for(int i=0;i<3;i++){
            Ledger.Entry expense=new Ledger.Entry(510+i,false,100000,LocalDateTime.of(2026,9,1+i,0,0),"报销"+i,"",0);expense.kind=Ledger.REIMBURSE;
            if(i>0){expense.receipts.add(new Ledger.Receipt(i==1?50000:100000,LocalDateTime.of(2026,9,10,0,0)));expense.status=expense.reimbursementStatus();}
            data.entries.add(expense);
        }
        data.funds.get(0).tag="余利宝";return data;
    }
    @Test public void numbersFitAtActualPhoneResolutionAndSeveralDensities()throws Exception{
        t.device.executeShellCommand("wm size 1260x2800");
        for(String density:new String[]{"480","560"}){
            t.device.executeShellCommand("wm density "+density);t.start(realistic());
            assertFits();t.shot("v44-assets-"+density+".png");
            t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));assertFits();t.shot("v44-funds-"+density+".png");
            t.ui(()->{t.app.fundFocus=40;t.app.go("fund_detail");});assertFits();t.shot("v44-fund-detail-"+density+".png");
            t.ui(()->t.app.yearPopup(2025));assertFits();t.shot("v44-year-"+density+".png");
        }
    }
    @Test public void amountColumnsKeepTheirLeftEdgeWithDifferentLengths(){
        t.start(realistic());t.ui(()->t.app.go("income_detail"));assertFits();
        t.ui(()->{
            List<TextView> values=new ArrayList<>();for(TextView v:fitted(t.app.body))if("bounded-number".equals(v.getTag())&&v.getText().toString().startsWith("¥ "))values.add(v);
            int first=-1;for(TextView value:values){
                if(value.getParent() instanceof LinearLayout&&((LinearLayout)value.getParent()).getChildAt(0) instanceof TextView){
                    TextView title=(TextView)((LinearLayout)value.getParent()).getChildAt(0);
                    if(title.getText().toString().startsWith("总"))continue;
                    int[] xy=new int[2];value.getLocationOnScreen(xy);
                    if(first==-1)first=xy[0];else assertEquals("list amount left edges",first,xy[0]);
                }
            }
        });
    }
    @Test public void monthChartFitsWithoutScrollingAndCapturesMonthsWhileDragging(){
        t.start(realistic());t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        t.ui(()->{
            assertNull(h.first(t.app.body,HorizontalScrollView.class));FinanceChart chart=h.first(t.app.body,FinanceChart.class);
            assertEquals(LocalDate.now().getMonthValue(),chart.labels.length);assertEquals(""+LocalDate.now().getMonthValue(),chart.labels[chart.labels.length-1]);
            assertNotNull(h.desc(t.app.body,"查看1月支出详情"));
            assertNull(h.desc(t.app.body,"查看12月支出详情"));
        });
        int[] start=new int[2],end=new int[2];
        t.ui(()->{
            FinanceChart chart=h.first(t.app.body,FinanceChart.class);chart.getLocationOnScreen(start);chart.getLocationOnScreen(end);
            start[0]+=Math.round(chart.pointX(0));end[0]+=Math.round(chart.pointX(chart.labels.length-1));
            start[1]+=chart.getHeight()/2;end[1]=start[1];
        });
        t.device.swipe(start[0],start[1],end[0],end[1],30);t.device.waitForIdle();
        t.ui(()->assertTrue(h.first(t.app.body,FinanceChart.class).selectionText().startsWith(""+LocalDate.now().getMonthValue()+"月 ")));
        t.shot("v44-month-cursor.png");
        t.ui(()->h.desc(t.app.body,"查看1月支出详情").performClick());assertNotNull(t.device.wait(Until.findObject(By.text("2026年1月")),3000));
    }
    @Test public void previousYearShowsTwelveTextLabelsWithoutCards(){
        t.start(realistic());t.ui(()->{t.app.year=2025;t.app.openTab(MainActivity.TAB_FLOW);
            FinanceChart chart=h.first(t.app.body,FinanceChart.class);assertEquals(12,chart.labels.length);
            View label=h.desc(t.app.body,"查看12月支出详情");assertNotNull(label);assertNull(label.getBackground());
        });t.shot("v44-month-full-year.png");
    }
    @Test public void fundTagSwipeEntryAndDirectTradeEditorPersist()throws Exception{
        t.start(realistic());t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));
        t.swipeLeft("基金000001");assertNotNull(t.device.findObject(By.desc("基金标签000001")));assertNotNull(t.device.findObject(By.desc("编辑基金000001")));
        t.device.findObject(By.desc("基金标签000001")).click();
        UiObject2 tag=t.device.wait(Until.findObject(By.desc("基金标签（最多12字，留空移除）")),3000);tag.setText("零钱通");
        t.device.waitForIdle();assertEquals("零钱通",tag.getText());
        t.device.findObject(By.clazz("android.widget.Button").text("保存")).click();t.device.waitForIdle();
        for(int n=0;n<20&&!t.store.load().funds.get(0).tag.equals("零钱通");n++)SystemClock.sleep(100);
        t.shot("v44-fund-tag-saved.png");
        assertEquals("零钱通",t.store.load().funds.get(0).tag);
        t.swipeLeft("基金000001");t.device.findObject(By.desc("编辑基金000001")).click();
        assertEquals("fund_trades",t.app.page);long id=t.app.data.funds.get(0).buys.get(0).id;
        UiObject2 edit=t.device.wait(Until.findObject(By.desc("编辑交易"+id+"买入")),3000);assertNotNull(edit);edit.click();
        assertTrue(t.device.wait(Until.hasObject(By.text("编辑交易")),3000));
        UiObject2 code=t.device.findObject(By.desc("基金代码"));assertNotNull(code);assertTrue(code.isEnabled());code.setText("000002");
        new UiScrollable(new UiSelector().className("android.widget.ScrollView")).scrollTextIntoView("删除本条交易记录");
        assertNotNull(t.device.findObject(By.text("删除本条交易记录")));t.shot("v44-trade-editor.png");
        t.device.findObject(By.text("取消")).click();
    }
    @Test public void labelsHaveSeparateLongPressEditorAndFourColumns(){
        t.start(realistic());t.ui(()->t.app.manageCategories());
        assertEquals("labels",t.app.page);assertFalse(t.device.hasObject(By.text("编辑标签")));
        t.ui(()->{
            View tile=h.desc(t.app.body,"标签奖学金");FlowLayout grid=(FlowLayout)tile.getParent();
            assertEquals(4,Math.round(grid.getWidth()/(float)tile.getWidth()));
            assertEquals(3,((ViewGroup)tile).getChildCount());tile.performLongClick();
        });
        assertEquals("label_edit",t.app.page);assertFalse(t.device.hasObject(By.text("收入标签")));
        t.shot("v44-label-editor.png");h.clickText("取消");
        assertEquals("labels",t.app.page);t.shot("v44-label-grid.png");
        t.ui(()->h.desc(t.app.body,"标签奖学金").performClick());assertEquals("income_category",t.app.page);assertTrue(t.device.hasObject(By.textContains("🎓")));
    }
    @Test public void searchTotalsAndReimbursementTabsIntersectFilters(){
        t.start(realistic());t.ui(()->t.app.go("income_detail"));
        t.ui(()->((EditText)h.desc(t.app.body,"搜索明细")).setText("年度金额测试"));
        assertTrue(t.device.hasObject(By.text("找到 1 笔记录 · 共计 21,976.00 元")));
        t.ui(()->t.app.go("reimburse_detail"));assertTrue(t.device.hasObject(By.textContains("找到 2 笔记录")));
        t.tapSegment(1);assertTrue(t.device.hasObject(By.textContains("找到 1 笔记录")));
        t.tapSegment(2);assertTrue(t.device.hasObject(By.textContains("找到 3 笔记录")));t.shot("v44-reimburse-tabs.png");
    }
    @Test public void calendarUsesOnlyRequiredWeeksAndReachesCardBottom()throws Exception{
        t.device.executeShellCommand("wm size 1260x2800");t.device.executeShellCommand("wm density 480");
        t.start(realistic());t.ui(()->{t.app.fundFocus=40;t.app.fundScreens.calendarMonth=YearMonth.of(2026,10);t.app.go("fund_detail");});
        t.ui(()->{
            LinearLayout card=(LinearLayout)t.app.body.getChildAt(t.app.body.getChildCount()-1);
            LinearLayout chartBody=(LinearLayout)card.getChildAt(card.getChildCount()-1);
            LinearLayout grid=(LinearLayout)chartBody.getChildAt(chartBody.getChildCount()-1);
            assertEquals(6,grid.getChildCount()); // weekday header + five October weeks, no empty sixth week.
            assertEquals(grid.getHeight(),grid.getChildAt(grid.getChildCount()-1).getBottom());
        });t.shot("v44-calendar-filled.png");
        t.tapSegment(1);t.shot("v44-profit-secondary-tabs.png");assertFits();
    }
    @Test public void yearSummaryShowsAveragesAndDatesOnlyAndFitsAllDigits(){
        t.start(realistic());t.ui(()->t.app.yearPopup(2025));
        assertTrue(t.device.hasObject(By.text("月均支出")));assertTrue(t.device.hasObject(By.text("月均收入")));
        t.tapSegment(1);assertFits();
        assertTrue(t.device.hasObject(By.text("2025/01/19")));assertFalse(t.device.hasObject(By.textContains("00:00")));
        t.shot("v44-year-income.png");
    }
    @Test public void incomeFormCapturesAccountAndExactCashTime(){
        t.start(realistic());t.ui(()->t.app.openEntry(Ledger.INCOME,null));
        t.ui(()->{
            View time=h.desc(t.app.body,"到账时间时间");assertEquals(View.VISIBLE,time.getVisibility());
            ((EditText)h.desc(t.app.body,"金额")).setText("100.00");
            MainActivity.DateControl date=(MainActivity.DateControl)h.desc(t.app.body,"到账时间日期").getTag();
            date.at=LocalDateTime.of(2026,10,20,12,34);date.update();((Spinner)h.desc(t.app.body,"到账账户（必选）")).setSelection(1);h.desc(t.app.body,"备注（可选）").requestFocus();
            t.findText(t.app.body,"保存记录").performClick();
        });
        assertEquals(LocalTime.of(12,34),t.store.load().entries.stream().filter(e->e.amount==10000).findFirst().get().at.toLocalTime());
    }
    @Test public void hiddenFundsAreExcludedFromDetailHoldingPercent(){
        Ledger.Data data=realistic();data.funds.get(1).hidden=true;t.start(data);
        t.ui(()->{t.app.fundFocus=40;t.app.go("fund_detail");});
        assertTrue(t.device.hasObject(By.text("100.0%")));
    }
}
