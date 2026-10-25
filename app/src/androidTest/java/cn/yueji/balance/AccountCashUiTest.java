package cn.yueji.balance;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AccountCashUiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v466");t.dir.mkdirs();}
    @After public void after()throws Exception{h.after();}
    Ledger.Data data(){
        Ledger.Data d=new Ledger.Data();d.settings.autoRefresh=false;
        Ledger.Account a=new Ledger.Account(1,"支付宝","支付宝");a.created=LocalDateTime.parse("2026-09-01T00:00");a.opening=100000;
        Ledger.Account b=new Ledger.Account(2,"中国银行","中国银行");b.created=a.created;b.opening=200000;d.accounts.add(a);d.accounts.add(b);
        Ledger.Snapshot baseline=new Ledger.Snapshot(10,a.created,"真实余额");baseline.balances.put(1L,100000L);baseline.balances.put(2L,200000L);d.snapshots.add(baseline);
        return d;
    }
    void tap(String text){UiObject2 view=t.device.wait(Until.findObject(By.text(text)),5000);assertNotNull(text,view);view.click();t.instrument.waitForIdleSync();}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(200);t.device.takeScreenshot(new File(t.dir,name));}
    void date(String description,String value){MainActivity.DateControl at=(MainActivity.DateControl)h.desc(t.app.body,description+"日期").getTag();at.at=LocalDateTime.parse(value);at.update();}
    void amount(String value){((EditText)h.desc(t.app.body,"金额")).setText(value);}
    @Test public void newIncomeRequiresAccountThenUpdatesBalanceAndEditingRebuildsOnce(){
        t.start(data());t.ui(()->{t.app.openEntry(Ledger.INCOME,null);amount("500");date("到账时间","2026-10-05T12:00");t.findText(t.app.body,"保存记录").performClick();});
        assertTrue(t.device.wait(Until.hasObject(By.text("请选择关联账户")),4000));assertEquals(0,t.store.load().entries.size());tap("知道了");
        t.ui(()->{((Spinner)h.desc(t.app.body,"到账账户（必选）")).setSelection(1);t.findText(t.app.body,"保存记录").performClick();});
        Ledger.Data saved=t.store.load();assertEquals(150000,saved.balance(saved.accounts.get(0)));assertEquals(50000,Ledger.income(saved,2026));assertEquals(2,saved.snapshots.size());
        Ledger.Entry income=saved.entries.get(0);assertTrue(income.cashLinked);assertEquals(LocalDateTime.parse("2026-10-05T12:00"),income.at);
        t.ui(()->{t.app.openEntry(Ledger.INCOME,t.app.data.entries.get(0));amount("600");t.findText(t.app.body,"保存记录").performClick();});
        saved=t.store.load();assertEquals(160000,saved.balance(saved.accounts.get(0)));assertEquals(2,saved.snapshots.size());
        t.ui(()->{t.app.accountId=1;t.app.go("account_history");});assertTrue(t.device.hasObject(By.text("收支 / 投资账户自动记账")));shot("v466-income-linked-account-history.png");
    }
    @Test public void reimbursementAdvanceAndDifferentAccountReceiptAreSavedThroughUi()throws Exception{
        Ledger.Data initial=data();Ledger.Snapshot october=new Ledger.Snapshot(11,LocalDateTime.parse("2026-10-01T00:00"),"月初");october.balances.put(1L,50000L);october.balances.put(2L,200000L);initial.snapshots.add(october);
        t.start(initial);t.ui(()->{
            t.app.openEntry(Ledger.REIMBURSE,null);((EditText)h.desc(t.app.body,"名称")).setText("跨月差旅报销");amount("500");date("支出时间","2026-09-05T12:00");
            ((Spinner)h.desc(t.app.body,"垫付账户（必选）")).setSelection(1);t.findText(t.app.body,"保存记录").performClick();
        });
        Ledger.Data saved=t.store.load();assertEquals(50000,saved.balance(saved.accounts.get(0)));assertEquals(0,Ledger.month(saved,YearMonth.of(2026,9)).expense);
        t.ui(()->{t.app.openEntry(Ledger.REIMBURSE,t.app.data.entries.get(0));((Spinner)h.desc(t.app.body,"报销状态")).setSelection(2);});
        t.ui(()->t.findText(t.app.body,"＋ 添加一次报销到账").performClick());
        UiObject2 money=t.device.wait(Until.findObject(By.desc("到账金额")),5000);assertNotNull(money);money.setText("500");
        t.device.findObject(By.desc("到账账户（必选）")).click();tap("中国银行");tap("保存到账");
        t.ui(()->t.findText(t.app.body,"保存记录").performClick());saved=t.store.load();Ledger.Entry e=saved.entries.get(0);
        assertEquals(1,e.receipts.size());assertTrue(e.receipts.get(0).cashLinked);assertEquals(2,e.receipts.get(0).accountId);
        assertEquals(250000,saved.balance(saved.accounts.get(1)));assertEquals(300000,saved.total());assertEquals(0,Ledger.income(saved,2026));
        assertEquals(0,Ledger.month(saved,YearMonth.of(2026,9)).expense);assertEquals(0,Ledger.month(saved,YearMonth.of(2026,10)).expense);
        t.ui(()->{t.app.accountId=2;t.app.go("account_history");});shot("v466-reimbursement-received-account-history.png");
        t.ui(()->{t.app.openTab(MainActivity.TAB_ACCOUNTS);t.app.assetTab=1;t.app.render();});shot("v466-reimbursement-asset-trend.png");
        String backup=t.store.exportJson();Ledger.Data restored=t.store.parseBackup(backup);assertEquals(backup,LedgerStore.encode(restored));
        t.ui(()->t.app.openEntry(Ledger.REIMBURSE,t.app.data.entries.get(0)));t.ui(()->t.findText(t.app.body,"删除记录").performClick());tap("删除");
        saved=t.store.load();assertTrue(saved.entries.isEmpty());assertEquals(200000,saved.balance(saved.accounts.get(1)));assertEquals(2,saved.snapshots.size());
    }
    @Test public void editingLegacyUnassignedIncomeDoesNotTouchBalances(){
        Ledger.Data d=data();Ledger.Entry e=new Ledger.Entry(20,true,10000,LocalDateTime.parse("2026-09-05T00:00"),"历史收入","",0);e.category="其他";d.entries.add(e);t.start(d);
        t.ui(()->{t.app.openEntry(Ledger.INCOME,t.app.data.entries.get(0));assertNotNull(h.desc(t.app.body,"账户（可选）"));amount("200");t.findText(t.app.body,"保存记录").performClick();});
        Ledger.Data saved=t.store.load();assertEquals(1,saved.snapshots.size());assertEquals(300000,saved.total());assertFalse(saved.entries.get(0).cashLinked);assertEquals(0,saved.entries.get(0).accountId);
    }
    @Test public void deletingNewIncomeRemovesItsGeneratedObservation(){
        t.start(data());t.ui(()->{t.app.openEntry(Ledger.INCOME,null);amount("300");date("到账时间","2026-09-05T12:00");((Spinner)h.desc(t.app.body,"到账账户（必选）")).setSelection(2);t.findText(t.app.body,"保存记录").performClick();});
        assertEquals(330000,t.store.load().total());t.ui(()->t.app.openEntry(Ledger.INCOME,t.app.data.entries.get(0)));t.ui(()->t.findText(t.app.body,"删除记录").performClick());tap("删除");
        Ledger.Data saved=t.store.load();assertEquals(300000,saved.total());assertEquals(1,saved.snapshots.size());assertEquals(0,saved.entries.size());
    }
}
