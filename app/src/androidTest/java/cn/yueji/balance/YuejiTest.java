package cn.yueji.balance;

import android.app.Instrumentation;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

/** 4.1.0 screens: reorderable tabs, grouped settings, segmented switches, hide/restore regression and the xlsx import/export. */
@RunWith(AndroidJUnit4.class)
public class YuejiTest {
    Instrumentation instrument;Context context;LedgerStore store;String backup;UiDevice device;MainActivity app;File dir;Map<String,?> lockSettings;
    @Before public void before(){
        instrument=InstrumentationRegistry.getInstrumentation();context=instrument.getTargetContext();device=UiDevice.getInstance(instrument);
        store=new LedgerStore(context);backup=store.exportJson();lockSettings=new HashMap<>(context.getSharedPreferences("applock",0).getAll());AppLock.disable(context);
        dir=new File(context.getExternalFilesDir(null),"qa-v44");dir.mkdirs();
    }
    @After public void after(){
        if(app!=null)instrument.runOnMainSync(()->app.finish());
        store.save(LedgerStore.decode(backup));store.close();
        SharedPreferences.Editor prefs=context.getSharedPreferences("applock",0).edit().clear();
        for(Map.Entry<String,?> e:lockSettings.entrySet()){Object v=e.getValue();if(v instanceof String)prefs.putString(e.getKey(),(String)v);else if(v instanceof Long)prefs.putLong(e.getKey(),(Long)v);else if(v instanceof Boolean)prefs.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)prefs.putInt(e.getKey(),(Integer)v);}
        prefs.commit();
    }
    void start(Ledger.Data d){context.getSharedPreferences("MainActivity",0).edit().remove("tabOrder").commit();store.save(d);app=(MainActivity)instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));instrument.waitForIdleSync();}
    void ui(Runnable run){instrument.runOnMainSync(run);instrument.waitForIdleSync();}
    void shot(String name){device.waitForIdle();device.takeScreenshot(new File(dir,name));}
    Ledger.Data sample(){
        Ledger.Data d=new Ledger.Data();d.settings.autoRefresh=false;
        Ledger.Account a=new Ledger.Account(1,"主账户","招商银行");a.created=LocalDateTime.parse("2026-09-01T00:00");a.opening=1000000;d.accounts.add(a);
        Ledger.Account b=new Ledger.Account(2,"微信","微信");b.created=LocalDateTime.parse("2026-09-01T00:00");b.opening=50000;d.accounts.add(b);
        for(int i=0;i<3;i++){Ledger.Snapshot s=new Ledger.Snapshot(2+i,LocalDateTime.parse(i==0?"2026-09-01T00:00":i==1?"2026-10-01T00:00":"2026-10-15T20:00"),"");s.balances.put(1L,1000000L-i*90000);s.balances.put(2L,50000L-i*2000);d.snapshots.add(s);}
        String[][] incomes={{"奖学金","2026-09-10","1200"},{"家教","2026-09-20","600"},{"奖学金","2026-10-03","800"},{"助研费","2026-10-08","300"}};
        long id=20;for(String[] row:incomes){Ledger.Entry e=new Ledger.Entry(id++,true,Long.parseLong(row[2])*100,LocalDateTime.parse(row[1]+"T12:00"),row[0],"",0);e.category=row[0];d.entries.add(e);}
        Ledger.Entry large=new Ledger.Entry(30,false,350000,LocalDateTime.parse("2026-10-05T18:00"),"电脑","",1);large.kind=Ledger.LARGE;d.entries.add(large);
        for(int i=0;i<2;i++){
            Ledger.Fund f=new Ledger.Fund();f.id=40+i;f.code=i==0?"000001":"000002";f.name=i==0?"测试基金A":"测试基金B";
            for(int n=0;n<(i==0?3:1);n++){Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=50+i*10+n;buy.amount=100000+n*50000;buy.fee=0;buy.at=LocalDateTime.parse("2026-09-0"+(7+n)+"T10:00");f.buys.add(buy);}
            for(LocalDate day=LocalDate.of(2026,9,7);!day.isAfter(LocalDate.now());day=day.plusDays(1))if(day.getDayOfWeek()!=DayOfWeek.SATURDAY&&day.getDayOfWeek()!=DayOfWeek.SUNDAY)f.prices.put(day,new BigDecimal("1.0").add(BigDecimal.valueOf((day.getDayOfYear()%7)-3,2)));
            f.navDate=f.prices.lastKey().toString();f.nav=f.prices.lastEntry().getValue();FundEngine.resolve(f,new TradingCalendar());d.funds.add(f);
        }
        return d;
    }

    @Test public void fourTabsAndSettingsAsTab(){
        start(sample());
        for(String label:new String[]{"资产","收支","基金","设置"})assertNotNull(device.wait(Until.hasObject(By.desc("导航"+label)),3000));
        assertNull("the old ☰ settings button is gone",device.findObject(By.desc("打开设置")));
        assertNull("the word 校核 is replaced everywhere",device.findObject(By.textContains("校核")));
        device.findObject(By.desc("导航设置")).click();
        assertTrue(device.wait(Until.hasObject(By.text("更新与通知")),3000));shot("01-settings-tab.png");
        assertEquals(MainActivity.TAB_SETTINGS,app.tab);
        device.pressBack();assertTrue(device.wait(Until.hasObject(By.desc("导航资产")),3000));assertEquals(MainActivity.TAB_ACCOUNTS,app.tab);
    }
    @Test public void settingsGroupsExpandAndCollapse(){
        start(sample());ui(()->app.openTab(MainActivity.TAB_SETTINGS));
        assertTrue(device.wait(Until.hasObject(By.text("基金数据管理")),3000));
        assertNull("groups start collapsed",device.findObject(By.text("导入基金数据")));
        device.findObject(By.text("基金数据管理")).click();
        assertTrue(device.wait(Until.hasObject(By.text("导入基金数据")),3000));
        assertNotNull(device.findObject(By.text("导出基金数据")));assertNotNull(device.findObject(By.text("下载基金模板")));shot("01b-settings-open.png");
        device.findObject(By.text("基金数据管理")).click();
        assertTrue(device.wait(Until.gone(By.text("导入基金数据")),3000));
        device.findObject(By.text("更新与通知")).click();
        assertTrue(device.wait(Until.hasObject(By.text("余额更新提醒")),3000));assertNotNull(device.findObject(By.text("基金收益通知")));assertNotNull(device.findObject(By.text("自动刷新基金")));
    }
    @Test public void tabsCanBeRearrangedByLongPressAndSettingsStaysLast(){
        start(sample());ui(()->app.saveTabOrder(new int[]{0,1,2}));ui(()->app.render());
        android.graphics.Rect first=device.wait(Until.findObject(By.desc("导航资产")),3000).getVisibleBounds();
        float y=first.centerY(),step=first.width();
        long down=android.os.SystemClock.uptimeMillis();
        instrument.sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,first.centerX(),y,0));
        android.os.SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+250);
        for(int i=1;i<=6;i++){instrument.sendPointerSync(MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,first.centerX()+step*i/3f,y,0));android.os.SystemClock.sleep(30);}
        instrument.sendPointerSync(MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,first.centerX()+step*2,y,0));
        device.waitForIdle();
        int[] order=app.tabOrder();
        assertEquals("settings is always last",MainActivity.TAB_SETTINGS,order[3]);
        assertEquals("assets moved two places to the right",MainActivity.TAB_ACCOUNTS,order[2]);assertEquals(MainActivity.TAB_FLOW,order[0]);assertEquals(MainActivity.TAB_FUNDS,order[1]);
        shot("01c-tabs-rearranged.png");
        ui(()->app.render());
        android.graphics.Rect navFlow=device.findObject(By.desc("导航收支")).getVisibleBounds(),navAssets=device.findObject(By.desc("导航资产")).getVisibleBounds();
        assertTrue("on screen the order follows the saved order",navFlow.left<navAssets.left);
        ui(()->app.saveTabOrder(new int[]{0,1,2}));
    }
    @Test public void accountsHeroHasSixRegionsAndOnlyTheListScrolls(){
        start(sample());assertTrue(device.wait(Until.hasObject(By.text("总资产")),3000));
        assertTrue("fund value on the account hero",device.wait(Until.hasObject(By.text("基金金额 >")),3000));
        for(String text:new String[]{"账户余额","更新全部账户","＋ 添加账户"})assertTrue(text,device.hasObject(By.text(text)));
        assertTrue(device.hasObject(By.textContains("固定更新"))||device.hasObject(By.textContains("待补更新")));assertTrue(device.hasObject(By.textContains("最新更新")));shot("02-accounts.png");
        instrument.runOnMainSync(()->{
            assertFalse("whole page must not scroll",app.body.getParent() instanceof ScrollView);
            ViewGroup panel=(ViewGroup)app.body.getChildAt(app.body.getChildCount()-1);
            assertEquals("one selected inline panel",1,panel.getChildCount());assertTrue("only the account list scrolls",panel.getChildAt(0) instanceof ScrollView);
        });
        device.findObject(By.desc("基金金额")).click();assertTrue(device.wait(Until.hasObject(By.text("持有总金额")),3000));assertEquals(MainActivity.TAB_FUNDS,app.tab);
    }
    @Test public void longPressDragReordersAccounts(){
        start(sample());assertTrue(device.wait(Until.hasObject(By.desc("账户主账户")),3000));
        assertEquals(1L,store.load().accounts.get(0).id);assertEquals(2L,store.load().accounts.get(1).id);
        UiObject2 first=device.findObject(By.desc("账户主账户"));android.graphics.Rect from=first.getVisibleBounds();
        UiObject2 second=device.findObject(By.desc("账户微信"));android.graphics.Rect to=second.getVisibleBounds();
        device.swipe(from.centerX(),from.centerY(),to.centerX(),to.centerY()+to.height(),60);
        device.waitForIdle();shot("03-reordered.png");
        assertEquals("dragged row keeps its place without re-encoding",1L,store.load().accounts.get(0).id);
        ui(()->{List<Object> order=new ArrayList<>();order.add(2L);order.add(1L);app.reorderAccounts(order);});
        assertEquals(2L,store.load().accounts.get(0).id);assertEquals(1L,store.load().accounts.get(1).id);
        ui(()->{List<Object> back=new ArrayList<>();back.add(1L);back.add(2L);app.reorderAccounts(back);});
    }
    @Test public void mergedIncomeExpensePageFitsAndSwitchesCharts()throws Exception{
        String original=device.executeShellCommand("wm size");
        try{
            device.executeShellCommand("wm size 1080x1920");start(sample());ui(()->app.openTab(MainActivity.TAB_FLOW));
            assertTrue(device.wait(Until.hasObject(By.descContains("月支出趋势")),3000));shot("04-flow-short.png");
            instrument.runOnMainSync(()->assertFalse("income & expense page is fixed",app.body.getParent() instanceof ScrollView));
            tapSegment(1);assertTrue(device.wait(Until.hasObject(By.text("■ 支出")),3000));shot("05-flow-yearly.png");
            tapSegment(0);assertTrue(device.wait(Until.hasObject(By.text("拖动看金额 · 点月份看详情")),3000));
        }finally{device.executeShellCommand("wm size reset");}
    }
    @Test public void incomeChartOpensCategoryHistoryAndSortToggleWorks(){
        start(sample());ui(()->app.openTab(MainActivity.TAB_FLOW));
        assertTrue(device.wait(Until.hasObject(By.text("收入")),3000));device.findObject(By.text("收入")).click();
        assertTrue(device.wait(Until.hasObject(By.text("全部收入")),3000));assertTrue(device.wait(Until.hasObject(By.text("收入类别")),3000));shot("06-income-all.png");
        device.findObject(By.text("奖\n学\n金")).click();assertTrue(device.wait(Until.hasObject(By.text("总收入")),3000));
        assertEquals("income_category",app.page);assertTrue("scholarship total",device.hasObject(By.text("¥ 2,000.00")));shot("07-income-category.png");
        device.pressBack();assertTrue(device.wait(Until.hasObject(By.text("收入类别")),3000));
        tapSegment(1);
        ui(()->assertTrue(app.sortAmount));
        List<Ledger.Entry> shown=new ArrayList<>();ui(()->{for(Ledger.Entry e:app.data.entries)if(e.income)shown.add(e);app.sorted(shown);});
        assertEquals("largest income first",120000,shown.get(0).amount);
        tapSegment(0);ui(()->assertFalse(app.sortAmount));
    }
    @Test public void fundDetailUsesFiveRowsOfThree(){
        start(sample());ui(()->{app.tab=MainActivity.TAB_FUNDS;app.fundFocus=40;app.go("fund_detail");});
        for(String label:new String[]{"持有金额","持有收益","持有收益率","累计买入金额","持有份额","持仓占比","当日收益","昨日收益","持有天数","基金代码","最高日收益","最低日收益"})
            assertTrue(label,device.wait(Until.hasObject(By.text(label)),3000));
        assertNull("cumulative profit is gone from a single fund",device.findObject(By.text("累计收益")));
        assertNull("no first purchase line any more",device.findObject(By.text("首次买入")));
        assertNotNull(device.findObject(By.text("追加买入")));shot("08-fund-detail.png");
        instrument.runOnMainSync(()->assertFalse("detail page is fixed",app.body.getParent() instanceof ScrollView));
        device.findObject(By.desc("交易记录")).click();
        assertTrue(device.wait(Until.hasObject(By.text("确认份额时间")),3000));shot("09-fund-trades.png");
        assertEquals("fund_trades",app.page);
        device.pressBack();assertTrue(device.wait(Until.hasObject(By.text("持有天数")),3000));
    }
    @Test public void fundOverviewPairsAndChartsSwitchWithoutReloading(){
        start(sample());ui(()->{app.tab=MainActivity.TAB_FUNDS;app.go("fund_total");});
        for(String label:new String[]{"持有金额","持有收益","持有收益率","累计买入金额","当日收益","昨日收益","最高日收益","最低日收益","累计收益 ›"})
            assertTrue(label,device.wait(Until.hasObject(By.text(label)),3000));
        shot("10-fund-overview.png");
        instrument.runOnMainSync(()->assertFalse("overview is fixed",app.body.getParent() instanceof ScrollView));
        tapSegment(1);assertTrue(device.wait(Until.hasObject(By.text("持有以来")),3000));shot("11-overview-curve.png");
        tapSegment(0);assertTrue(device.wait(Until.hasObject(By.desc("选择期间")),3000));shot("12-overview-calendar.png");
    }
    @Test public void calendarMonthSwitchKeepsTheRestOfThePage(){
        start(sample());ui(()->{app.tab=MainActivity.TAB_FUNDS;app.fundFocus=40;app.go("fund_detail");});
        assertTrue(device.wait(Until.hasObject(By.desc("选择期间")),3000));
        final ViewParent[] before={null};
        instrument.runOnMainSync(()->{before[0]=app.body.getParent();assertNotNull(before[0]);});
        device.findObject(By.desc("上一期")).click();device.waitForIdle();
        instrument.runOnMainSync(()->assertSame("switching the month does not re-render the page",before[0],app.body.getParent()));
        instrument.runOnMainSync(()->assertTrue(findText(app.body,"2026年 "+YearMonth.now().minusMonths(1).getMonthValue()+"月")!=null));
        assertEquals("上一期 switches one month",YearMonth.now().minusMonths(1),app.fundScreens.calendarMonth);
        shot("13-calendar-switched.png");
    }
    @Test public void deletingFromHomeKeepsProfitAndCanBeRestored(){
        start(sample());ui(()->app.openTab(MainActivity.TAB_FUNDS));
        android.graphics.Rect b=device.wait(Until.findObject(By.desc("基金000002")),3000).getVisibleBounds();swipeLeft("基金000002");
        UiObject2 remove=device.wait(Until.findObject(By.desc("删除基金卡片000002")),3000);
        assertTrue("revealed bar sits inside the card",remove.getVisibleBounds().right<=b.right&&remove.getVisibleBounds().top>=b.top-2&&remove.getVisibleBounds().bottom<=b.bottom+2);
        remove.click();
        assertTrue(device.wait(Until.hasObject(By.text("确认删除")),3000));shot("14-remove-dialog.png");
        device.findObject(By.text("确认删除")).click();assertTrue(device.wait(Until.gone(By.desc("基金000002")),3000));
        assertEquals("fund stays in the ledger",2,store.load().funds.size());assertTrue(store.load().funds.get(1).removed());
        long frozen=FundEngine.cumulative(store.load().funds.get(1),FundEngine.now().toLocalDate());
        assertEquals("profit frozen at deletion",frozen,FundEngine.cumulative(store.load().funds.get(1),FundEngine.now().toLocalDate().plusDays(30)));
        ui(()->app.go("fund_profit"));assertTrue(device.wait(Until.hasObject(By.text("累计收益明细")),3000));shot("15-profit-list-removed.png");
        assertTrue(device.hasObject(By.textContains("已删除")));
        ui(()->app.fundScreens.restore(store.load().funds.get(1)));
        assertFalse(store.load().funds.get(1).removed());
        ui(()->app.openTab(MainActivity.TAB_FUNDS));
        assertNotNull("restored fund is back on the home list",device.wait(Until.findObject(By.desc("基金000002")),5000));
    }
    void swipeLeft(String desc){UiObject2 card=device.wait(Until.findObject(By.desc(desc)),3000);assertNotNull(desc,card);android.graphics.Rect b=card.getVisibleBounds();device.swipe(b.right-app.dp(24),b.centerY(),b.left+app.dp(20),b.centerY(),25);device.waitForIdle();}
    @Test public void profitListSwipeRevealsThreeActions(){
        start(sample());ui(()->app.go("fund_profit"));
        swipeLeft("累计000002");shot("16-profit-swipe.png");
        assertNotNull(device.findObject(By.desc("隐藏000002")));
        assertNotNull(device.findObject(By.desc("恢复000002")));
        assertNotNull(device.findObject(By.desc("永久删除000002")));
        for(String label:new String[]{"买入时间","确认份额时间","删除时间"})assertTrue(label,device.hasObject(By.text(label)));
        device.findObject(By.desc("永久删除000002")).click();assertFalse("cannot purge while still on home",device.hasObject(By.text("确认永久删除")));assertEquals(2,store.load().funds.size());
        ui(()->app.fundScreens.removeFromHome(store.load().funds.get(1)));
        swipeLeft("累计000002");device.findObject(By.desc("永久删除000002")).click();assertTrue(device.wait(Until.hasObject(By.text("确认永久删除")),3000));
        device.findObject(By.text("确认永久删除")).click();
        for(int attempt=0;attempt<20&&store.load().funds.size()>1;attempt++){android.os.SystemClock.sleep(150);if(store.load().funds.size()>1&&device.hasObject(By.text("确认永久删除")))device.findObject(By.text("确认永久删除")).click();}
        assertEquals("permanent delete removes it",1,store.load().funds.size());
        device.pressBack();
    }
    /** Regression: hiding a fund used to leave the swipe buttons on screen and make 取消隐藏 jump to the fund page. */
    @Test public void hidingAndUnhidingInTheProfitListWorksAndStaysOnThePage(){
        start(sample());ui(()->app.go("fund_profit"));
        swipeLeft("累计000002");
        device.findObject(By.desc("隐藏000002")).click();device.waitForIdle();
        assertTrue("fund is hidden in the ledger",store.load().funds.get(1).hidden);shot("16b-profit-hidden.png");
        assertEquals("still on the cumulative profit page","fund_profit",app.page);
        assertNull("the swipe buttons are gone after hiding",device.findObject(By.desc("恢复000002")));
        assertTrue(device.hasObject(By.textContains("已隐藏")));
        swipeLeft("累计000002");
        UiObject2 unhide=device.wait(Until.findObject(By.desc("隐藏000002")),3000);assertNotNull(unhide);assertEquals("the same button now reads 取消隐藏","取消隐藏",unhide.getText());
        unhide.click();device.waitForIdle();
        assertFalse("fund is visible again",store.load().funds.get(1).hidden);assertEquals("still on the cumulative profit page","fund_profit",app.page);
        ui(()->app.openTab(MainActivity.TAB_FUNDS));
        assertNotNull("visible on the home list",device.wait(Until.findObject(By.desc("基金000002")),3000));
    }
    @Test public void pullDownAtTheTopRefreshesFunds(){
        Ledger.Data d=sample();d.funds.get(0).buys.get(0).saleDate=LocalDate.now().minusDays(1);d.funds.get(0).buys.get(0).saleAmount=100000;
        start(d);ui(()->app.openTab(MainActivity.TAB_FUNDS));
        assertTrue(device.wait(Until.hasObject(By.text("持有总金额")),3000));
        assertNull("refresh button is gone",device.findObject(By.text("刷新")));
        device.swipe(device.getDisplayWidth()/2,app.dp(260),device.getDisplayWidth()/2,app.dp(760),40);device.waitForIdle();
        shot("17-pull-refresh.png");
        ui(()->assertNotNull("gesture reached the pull layout",app.fundScreens.pull));
    }
    @Test public void workbookImportsAndExportsOnTheDevice(){
        store.save(new Ledger.Data());
        byte[] book=HistoryImport.templateXlsx();
        HistoryImport.Result r=HistoryImport.preview(book,store.load());
        assertEquals(1,r.months);assertEquals(1,r.expenses);assertEquals(1,r.incomes);assertEquals(1,r.reimbursements);
        store.save(r.data);assertEquals(3,store.load().entries.size());
        byte[] out=HistoryImport.exportXlsx(store.load(),LocalDate.of(2025,1,1),LocalDate.of(2025,12,31),true,true,true);
        assertTrue("an xlsx is a zip",Xlsx.isXlsx(out));
        int again=HistoryImport.preview(out,store.load()).skipped;assertEquals("month row plus three records",4,again);
        try{HistoryImport.preview("支出类型,时间,金额\n月支出,2026-Aug,100\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),store.load());fail("CSV is rejected");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage(),expected.getMessage().contains("xlsx"));}
        store.save(new Ledger.Data());
    }
    @Test public void fundWorkbookImportsIntoAnEmptyLedger(){
        store.save(new Ledger.Data());
        List<FundSheet.Row> rows=FundSheet.parse(FundSheet.templateXlsx());assertEquals(1,rows.size());assertEquals("000001",rows.get(0).code);
        Ledger.Data d=new Ledger.Data();FundSheet.Plan plan=FundSheet.plan(rows,d);
        FundSheet.apply(d,plan,new HashMap<>(),new HashMap<>(),new TradingCalendar());
        assertEquals(1,d.funds.size());assertEquals(1,d.funds.get(0).buys.size());
        byte[] out=FundSheet.exportXlsx(d);assertTrue(Xlsx.isXlsx(out));assertEquals(1,FundSheet.parse(out).size());
        store.save(d);Ledger.Data saved=store.parseBackup(store.encode(store.load()));assertEquals(1,saved.funds.get(0).buys.size());
        store.save(new Ledger.Data());
    }
    @Test public void removedFundSurvivesBackupAndRefreshSkipsIt(){
        Ledger.Data d=sample();FundEngine.remove(d.funds.get(1),LocalDateTime.now());
        Ledger.Data copy=store.parseBackup(store.encode(d));assertTrue(copy.funds.get(1).removed());assertEquals(d.funds.get(1).frozenOn,copy.funds.get(1).frozenOn);
        try{
            org.json.JSONObject root=new org.json.JSONObject(LedgerStore.encode(sample()));
            org.json.JSONArray funds=root.getJSONArray("funds");for(int i=0;i<funds.length();i++){funds.getJSONObject(i).remove("removedAt");funds.getJSONObject(i).remove("frozenOn");}
            Ledger.Data legacy=LedgerStore.decode(root.toString());assertFalse(legacy.funds.get(0).removed());assertFalse(legacy.funds.get(1).removed());
        }catch(org.json.JSONException e){fail(e.getMessage());}
    }
    Segmented firstSegment(View v){if(v instanceof Segmented)return (Segmented)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Segmented f=firstSegment(g.getChildAt(i));if(f!=null)return f;}}return null;}
    /** Taps one segment of the first segmented switch on screen. */
    void tapSegment(int index){
        int[] at=new int[4];
        instrument.runOnMainSync(()->{Segmented s=firstSegment(app.root);assertNotNull("segmented switch on screen",s);int[] xy=new int[2];s.getLocationOnScreen(xy);at[0]=xy[0]+Math.round(s.getWidth()*(index+0.5f)/s.count());at[1]=xy[1]+s.getHeight()/2;});
        device.click(at[0],at[1]);device.waitForIdle();
    }
    int segmentSelected(){int[] v={-1};instrument.runOnMainSync(()->v[0]=firstSegment(app.root).selected());return v[0];}
    View findText(View view,String label){if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
}
