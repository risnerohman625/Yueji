package cn.yueji.balance;

import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteBlobTooBigException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V474UiTest {
    final V470UiTest old=new V470UiTest();final YuejiTest t=old.t;
    @Before public void before(){
        old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",25).commit();
    }
    @After public void after()throws Exception{old.after();}
    View text(String value){return t.findText(t.app.body,value);}
    void clickSection(String title){View label=text(title);assertNotNull(label);((View)label.getParent().getParent()).performClick();}
    @Test public void fundAndWatchAddHaveOnlyManualInputs(){
        t.start(new Ledger.Data());t.ui(()->{t.app.openTab(MainActivity.TAB_FUNDS);t.app.fundScreens.addChoice();});
        assertNotNull(t.device.wait(Until.findObject(By.text("手动输入")),2000));assertFalse(t.device.hasObject(By.text("自动识别")));t.device.pressBack();
        t.ui(()->{t.app.go("fund_watchlist");text("＋ 添加").performClick();});
        assertNotNull(t.device.wait(Until.findObject(By.text("基金代码（6位）")),2000));assertFalse(t.device.hasObject(By.textContains("识别支付宝")));
        t.device.pressBack();
    }
    @Test public void watchPurchaseHasCachedIdentityAndEditableTradeFields(){
        t.start(old.sample());t.ui(()->{t.app.watchScreens.open("006431");text("购买").performClick();});
        assertNotNull(t.device.wait(Until.findObject(By.text("添加基金买入")),2000));
        assertNotNull(t.device.findObject(By.text("汇安鼎利纯债债券A")));assertNotNull(t.device.findObject(By.text("006431")));
        assertFalse(t.device.findObject(By.text("006431")).isEnabled());
        assertNotNull(t.device.findObject(By.desc("买入金额（含手续费）")));assertNotNull(t.device.findObject(By.desc("手续费（元）")));
        assertNotNull(t.device.findObject(By.text("买入时间（北京时间）")));t.device.pressBack();
        t.ui(()->assertEquals(2,t.store.load().funds.size()));old.shot("v474-watch-purchase.png");
    }
    @Test public void nestedSettingsUseOneOpenSiblingAndFullCardSurfaces(){
        t.start(old.sample());t.ui(()->{
            t.app.openTab(MainActivity.TAB_SETTINGS);clickSection("数据导入及导出备份管理");
            LinearLayout parent=t.app.sectionContents.get("dataManagement");
            ((View)text("收支报销数据管理").getParent()).performClick();assertTrue(t.app.openSections.contains("dataManagement.records"));
            ((View)text("基金数据管理").getParent()).performClick();assertFalse(t.app.openSections.contains("dataManagement.records"));assertTrue(t.app.openSections.contains("dataManagement.funds"));
            LinearLayout records=(LinearLayout)parent.getChildAt(1),funds=(LinearLayout)parent.getChildAt(2);
            assertEquals(View.GONE,records.getChildAt(1).getVisibility());assertEquals(View.VISIBLE,funds.getChildAt(1).getVisibility());
            assertEquals(0,records.getPaddingLeft());assertTrue(records.getClipToOutline());assertTrue(records.getBackground() instanceof GradientDrawable);
            ((View)text("备份与恢复").getParent()).performClick();assertEquals(View.GONE,funds.getChildAt(1).getVisibility());
            clickSection("更新与通知");assertFalse(t.app.openSections.contains("dataManagement.backup"));clickSection("数据导入及导出备份管理");((View)text("基金数据管理").getParent()).performClick();
        });old.shot("v474-settings.png");
    }
    @Test public void bankIncomeEntryIsManualAndPersistsSignedDailyReturns(){
        Ledger.Data data=old.sample();BankLedger.Draft draft=new BankLedger.Draft();draft.name="手动银行理财";draft.balance=100000L;draft.at=LocalDateTime.of(2026,10,23,10,0);
        BankLedger.Product product=BankLedger.apply(data,0,draft,false,null);
        t.start(data);t.ui(()->{t.app.bankScreens.focus=product.id;t.app.bankScreens.importIncome();});
        assertNotNull(t.device.wait(Until.findObject(By.text("添加每日收益")),2000));
        t.device.findObject(By.desc("累计收益")).setText("-1.04");
        t.ui(()->text("添加每日收益").performClick());
        t.device.wait(Until.findObject(By.desc("收益日期 yyyy-MM-dd")),3000).setText("2026-10-23");
        t.device.findObject(By.desc("当日收益（元，可为负数）")).setText("-1.04");
        t.device.findObject(By.text("添加")).click();
        assertNotNull(t.device.wait(Until.findObject(By.textContains("2026-10-23")),2000));
        t.ui(()->text("确认导入收益").performClick());
        assertEquals(Long.valueOf(-104),BankLedger.daily(BankLedger.find(t.store.load(),product.id),LocalDate.of(2026,10,23)));
        assertFalse(t.device.hasObject(By.text("截图输入")));old.shot("v474-bank-manual.png");
    }
    Ledger.Data large(){
        Ledger.Data data=new Ledger.Data();data.settings.autoRefresh=false;
        for(int i=0;i<12;i++){
            Ledger.WatchFund fund=new Ledger.WatchFund(String.format(Locale.ROOT,"%06d",6431+i),"大账本测试基金"+i);
            for(int day=0;day<6000;day++){LocalDate date=LocalDate.of(2000,1,1).plusDays(day);fund.prices.put(date,new BigDecimal("1.12345678901234"));fund.returns.put(date,new BigDecimal("0.01234567890123"));}
            fund.navDate=fund.prices.lastKey().toString();fund.nav=fund.prices.lastEntry().getValue();fund.previous=fund.nav;data.watchFunds.add(fund);
        }
        Ledger.WatchGroup group=new Ledger.WatchGroup(42,"大账本");group.codes.add("006431");data.watchGroups.add(group);return data;
    }
    @Test public void oversizedLegacyRowRecoversAndChunkedLedgerSurvivesReopenAndLaunch(){
        Ledger.Data data=large();String original=LedgerStore.encode(data);assertTrue(original.length()>4*1024*1024);
        SQLiteDatabase db=t.store.getWritableDatabase();db.delete("state_chunks",null,null);ContentValues values=new ContentValues();values.put("id",1);values.put("json",original);db.insertOrThrow("state",null,values);db.setVersion(3);t.store.close();
        t.store=new LedgerStore(t.context);
        boolean tooBig=false;try(Cursor cursor=t.store.getReadableDatabase().rawQuery("SELECT json FROM state",null)){cursor.moveToFirst();cursor.getString(0);}catch(SQLiteBlobTooBigException expected){tooBig=true;}
        assertTrue("Reproduce the original CursorWindow failure",tooBig);
        assertEquals(original,LedgerStore.encode(t.store.load()));
        t.store.save(t.store.load());t.store.close();t.store=new LedgerStore(t.context);
        assertEquals(original,t.store.exportJson());
        try(Cursor cursor=t.store.getReadableDatabase().rawQuery("SELECT max(length(json)),count(*) FROM state_chunks",null)){assertTrue(cursor.moveToFirst());assertTrue(cursor.getInt(0)<=LedgerStore.CHUNK_CHARS);assertTrue(cursor.getInt(1)>1);}
        t.start(t.store.load());assertNotNull(t.app.root);assertEquals(12,t.app.data.watchFunds.size());assertEquals(1,t.app.data.watchGroups.size());
        t.ui(()->{t.app.go("fund_watchlist");assertEquals(12,t.app.watchScreens.visible().size());});old.shot("v474-large-ledger.png");
    }
    @Test public void failedBackupValidationDoesNotReplaceCurrentLedger()throws Exception{
        t.start(old.sample());String before=t.store.exportJson();org.json.JSONObject invalid=new org.json.JSONObject(before);invalid.put("watchFunds",new org.json.JSONObject());
        try{t.store.parseBackup(invalid.toString());fail();}catch(IllegalArgumentException expected){}
        assertEquals(before,t.store.exportJson());
    }
    @Test public void supplementaryUnicodeSurvivesChunkBoundaries(){
        Ledger.Data data=large();data.watchFunds.get(0).name="基金\uD83C\uDF19";
        t.store.save(data);assertEquals("基金\uD83C\uDF19",t.store.load().watchFunds.get(0).name);
    }
    @Test public void failedChunkWriteRollsBackTheEntireSave(){
        t.store.save(new Ledger.Data());String before=t.store.exportJson();SQLiteDatabase db=t.store.getWritableDatabase();
        db.execSQL("CREATE TRIGGER fail_test_chunk BEFORE INSERT ON state_chunks WHEN NEW.part=1 BEGIN SELECT RAISE(ABORT,'test failure'); END");
        try{try{t.store.save(large());fail();}catch(android.database.sqlite.SQLiteException expected){}assertEquals(before,t.store.exportJson());}
        finally{db.execSQL("DROP TRIGGER fail_test_chunk");}
    }
    @Test public void restoredLedgerRejectsStaleRefreshAndDoesNotMergeNewerMarket(){
        Ledger.Data data=old.sample();t.store.save(data);long oldEpoch=LedgerStore.epoch();
        Ledger.Data restored=LedgerStore.decode(LedgerStore.encode(data));restored.funds.get(0).nav=new BigDecimal("1.01");t.store.replaceBackup(restored);
        FundClient.Quote quote=new FundClient.Quote();quote.code=restored.funds.get(0).code;quote.name="旧任务不能改名";quote.nav=new BigDecimal("99");quote.previous=BigDecimal.ONE;quote.date="2026-10-23";
        t.store.updateFundMarket(restored.funds.get(0).id,quote.code,quote,"",new TradingCalendar(),oldEpoch);
        assertEquals(new BigDecimal("1.01"),t.store.load().funds.get(0).nav);assertNotEquals(quote.name,t.store.load().funds.get(0).name);
    }
    @Test public void cachedWatchPurchaseSavesWithoutTypingAName(){
        Ledger.Data data=new Ledger.Data();data.settings.autoRefresh=false;data.watchFunds.add(old.sample().watchFunds.get(0));t.start(data);
        t.ui(()->{t.app.watchScreens.open("006431");text("购买").performClick();});
        t.device.wait(Until.findObject(By.desc("买入金额（含手续费）")),3000).setText("100");
        t.device.findObject(By.desc("手续费（元）")).setText("0.10");
        t.device.findObject(By.text("获取净值并保存")).click();
        assertTrue(t.device.wait(Until.gone(By.text("添加基金买入")),35000));
        Ledger.Data saved=t.store.load();assertEquals(1,saved.funds.size());assertEquals(1,saved.watchFunds.size());
        Ledger.Fund fund=saved.funds.get(0);assertEquals("006431",fund.code);assertEquals("汇安鼎利纯债债券A",fund.name);
        assertEquals(10000,fund.buys.get(0).amount);assertEquals(10,fund.buys.get(0).fee);assertFalse(fund.buys.get(0).buyAccountLinked);
        if(t.device.hasObject(By.text("知道了")))t.device.findObject(By.text("知道了")).click();
    }
    @Test public void realJsonExportIncludesWatchDataAndOldRestoreWarnsBeforeReplacement()throws Exception{
        t.start(old.sample());java.io.File output=new java.io.File(t.context.getCacheDir(),"v474-roundtrip.json");
        t.ui(()->t.app.backupFile(android.net.Uri.fromFile(output),false));
        for(int i=0;i<100&&(!output.isFile()||output.length()==0);i++)android.os.SystemClock.sleep(100);
        assertTrue(output.length()>0);android.os.SystemClock.sleep(300);
        String json=new String(java.nio.file.Files.readAllBytes(output.toPath()),java.nio.charset.StandardCharsets.UTF_8);
        Ledger.Data exported=t.store.parseBackup(json);assertEquals(5,exported.watchFunds.size());assertEquals(2,exported.watchGroups.size());
        org.json.JSONObject oldBackup=new org.json.JSONObject(json);oldBackup.put("version",10);oldBackup.remove("watchFunds");oldBackup.remove("watchGroups");
        java.nio.file.Files.write(output.toPath(),oldBackup.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        t.ui(()->t.app.backupFile(android.net.Uri.fromFile(output),true));
        assertNotNull(t.device.wait(Until.findObject(By.textContains("自选基金 0 只")),5000));
        assertTrue(t.device.hasObject(By.textContains("旧备份没有的自选数据不会保留")));t.device.findObject(By.text("取消")).click();
        assertEquals(5,t.store.load().watchFunds.size());
        t.ui(()->t.app.confirmBackup(t.store.parseBackup(oldBackup.toString())));
        t.device.wait(Until.findObject(By.text("恢复")),2000).click();
        assertTrue(t.device.wait(Until.gone(By.text("正在恢复账本")),10000));
        assertEquals(0,t.store.load().watchFunds.size());assertEquals(0,t.store.load().watchGroups.size());assertEquals(2,t.store.load().funds.size());
    }
}
