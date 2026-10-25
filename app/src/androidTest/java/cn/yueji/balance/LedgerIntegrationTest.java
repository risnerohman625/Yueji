package cn.yueji.balance;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LedgerIntegrationTest {
    private Context isolated;
    private LedgerStore store;
    @Before public void setUp(){
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        isolated=new ContextWrapper(app){
            @Override public java.io.File getDatabasePath(String name){return super.getDatabasePath("integration-"+name);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return super.openOrCreateDatabase("integration-"+name,mode,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler){return super.openOrCreateDatabase("integration-"+name,mode,factory,handler);}
        };
        app.deleteDatabase("integration-yueji.db");store=new LedgerStore(isolated);
    }
    @After public void tearDown(){store.close();InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase("integration-yueji.db");}
    Ledger.Data base(){
        Ledger.Data d=new Ledger.Data();Ledger.Account a=new Ledger.Account(1,"主账户","招商银行");a.created=LocalDateTime.parse("2026-10-19T12:00");a.opening=1000000;d.accounts.add(a);
        Ledger.Snapshot s=new Ledger.Snapshot(2,a.created,"");s.balances.put(1L,1000000L);d.snapshots.add(s);return d;
    }
    @Test public void saveRestoreRoundTripAndOptionalIncomeAccount(){
        Ledger.Data d=base();Ledger.Entry income=new Ledger.Entry(3,true,200000,LocalDateTime.parse("2025-01-10T12:00"),"奖学金","",0);income.category="奖学金";d.entries.add(income);store.save(d);
        Ledger.Data read=store.load();assertEquals(200000,Ledger.income(read,2025));assertEquals(0,read.entries.get(0).accountId);
        Ledger.Data backup=store.parseBackup(store.exportJson());assertEquals(1000000,backup.total());assertEquals(1,backup.entries.size());
    }
    @Test public void importBridgePreviewDoesNotWriteUntilConfirmed(){
        Ledger.Data original=base();store.save(original);
        byte[] book=Xlsx.write(java.util.Arrays.asList(
            new Xlsx.Sheet("支出").add(HistoryImport.EXPENSE_HEADER.split(",")).add("月支出","2026/10","2000.00","","","","已排除报销"),
            new Xlsx.Sheet("收入").add(HistoryImport.INCOME_HEADER.split(",")).add("奖学金","2025/01/10","1000.00","","")));
        HistoryImport.Result r=HistoryImport.preview(book,original);assertEquals(1,r.months);assertEquals(1,r.entries);assertTrue(store.load().history.isEmpty());assertTrue(original.history.isEmpty());
        store.save(r.data);assertEquals(200000,Ledger.month(store.load(),YearMonth.of(2026,10)).expense);assertEquals(2,HistoryImport.preview(book,store.load()).skipped);
    }
    @Test public void splitReceiptsAndHistoryBasisSurviveBackup(){
        Ledger.Data d=base();Ledger.Entry e=new Ledger.Entry(3,false,60000,LocalDateTime.parse("2025-01-10T12:00"),"差旅","",0);e.kind=Ledger.REIMBURSE;e.status="已报销";e.receipts.add(new Ledger.Receipt(20000,LocalDateTime.parse("2025-02-10T12:00")));e.receipts.add(new Ledger.Receipt(40000,LocalDateTime.parse("2025-03-10T12:00")));d.entries.add(e);
        Ledger.HistoryMonth h=new Ledger.HistoryMonth(YearMonth.of(2025,1),260000,LocalDateTime.parse("2025-02-01T00:00"));h.includesAdvances=true;d.history.add(h);store.save(d);
        Ledger.Data read=store.parseBackup(store.exportJson());assertEquals(2,read.entries.get(0).receipts.size());assertEquals(200000,Ledger.month(read,YearMonth.of(2025,1)).expense);
    }
    @Test public void importXlsxMultilineAndSplitReceiptDates(){
        byte[] book=Xlsx.write(java.util.Collections.singletonList(new Xlsx.Sheet("报销").add(HistoryImport.REIMBURSE_HEADER.split(",")).add("差旅","2025/01/10","600.00","","第一行\n第二行","已报销","2025/02/10=200.00;2025/03/10=400.00")));
        HistoryImport.Result r=HistoryImport.preview(book,base());assertEquals(1,r.entries);assertEquals("差旅",r.data.entries.get(0).title);assertEquals(2,r.data.entries.get(0).receipts.size());assertEquals("第一行\n第二行",r.data.entries.get(0).note);
    }
    @Test public void invalidImportIsAtomic(){
        Ledger.Data original=base();store.save(original);
        byte[] book=Xlsx.write(java.util.Collections.singletonList(new Xlsx.Sheet("报销").add(HistoryImport.REIMBURSE_HEADER.split(",")).add("差旅","2025/01/10","600.00","","","已报销","2025/02/10=700.00")));
        try{HistoryImport.preview(book,original);fail("invalid reimbursement should reject");}catch(IllegalArgumentException expected){}
        assertEquals(0,store.load().entries.size());assertEquals(0,original.entries.size());
    }
    @Test public void oldV1BackupCanBeRestored(){
        String json="{\"format\":\"yueji-backup\",\"version\":1,\"accounts\":[{\"id\":1,\"name\":\"银行卡\",\"type\":\"银行\"}],\"snapshots\":[{\"id\":2,\"at\":\"2026-10-19T12:00\",\"note\":\"\",\"balances\":{\"1\":1000000}}],\"entries\":[{\"id\":3,\"income\":true,\"amount\":100000,\"at\":\"2025-01-10T12:00\",\"title\":\"补贴\",\"note\":\"\",\"accountId\":1}]}";
        Ledger.Data d=store.parseBackup(json);assertEquals("其他",d.entries.get(0).category);assertEquals(1000000,d.accounts.get(0).opening);store.save(d);assertEquals(1000000,store.load().total());
    }
    @Test public void databaseMigrationFromV1KeepsAccountBalances(){
        store.close();Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();app.deleteDatabase("integration-yueji.db");
        SQLiteDatabase db=isolated.openOrCreateDatabase("yueji.db",0,null);
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY,name TEXT,type TEXT)");db.execSQL("CREATE TABLE snapshots(id INTEGER PRIMARY KEY,at TEXT,note TEXT)");db.execSQL("CREATE TABLE balances(snapshot_id INTEGER,account_id INTEGER,amount INTEGER)");db.execSQL("CREATE TABLE entries(id INTEGER,income INTEGER,amount INTEGER,at TEXT,title TEXT,note TEXT,account_id INTEGER)");
        db.execSQL("INSERT INTO accounts VALUES(1,'主账户','银行')");db.execSQL("INSERT INTO snapshots VALUES(2,'2026-10-19T12:00','')");db.execSQL("INSERT INTO balances VALUES(2,1,1000000)");db.setVersion(1);db.close();
        store=new LedgerStore(isolated);Ledger.Data d=store.load();assertEquals(1000000,d.total());store.save(d);assertEquals(1,store.load().snapshots.size());
    }
    @Test public void fundQuoteIsDownloadedOnAndroidDevice()throws Exception{
        FundClient.Quote q=FundClient.fetch("000001");assertEquals("000001",q.code);assertFalse(q.name.isEmpty());assertTrue(q.nav.compareTo(BigDecimal.ZERO)>0);assertTrue(q.previous.compareTo(BigDecimal.ZERO)>0);assertNotNull(LocalDate.parse(q.date));
    }
    @Test public void fullHistoricalNavSupportsAnOldPurchase()throws Exception{
        FundClient.Quote q=FundClient.fetchHistory("000001");assertTrue(q.complete);assertTrue(q.history.size()>1000);
        assertNotNull(q.history.get(LocalDate.of(2020,1,10)));
        Ledger.Fund f=new Ledger.Fund();f.id=10;f.code=q.code;f.name=q.name;f.prices.putAll(q.history);f.navReturns.putAll(q.returns);f.dividends.putAll(q.dividends);f.splits.putAll(q.splits);f.nav=q.nav;f.previous=q.previous;f.navDate=q.date;
        Ledger.FundBuy b=new Ledger.FundBuy();b.id=11;b.amount=100000;b.fee=150;b.at=LocalDateTime.parse("2020-01-10T10:00");f.buys.add(b);TradingCalendar calendar=new TradingCalendar();calendar.load(isolated,2020);FundEngine.resolve(f,calendar);
        assertTrue(b.units.signum()>0);assertEquals(LocalDate.of(2020,1,10),b.tradeDate);assertEquals(LocalDate.of(2020,1,13),b.confirmDate);Ledger.Data d=base();d.funds.add(f);store.save(d);assertEquals(b.units,store.load().funds.get(0).buys.get(0).units);
    }
    @Test public void customNodesAndReceiptAccountsSurviveBackup(){
        Ledger.Data d=base();d.settings.checkDay1=10;d.settings.checkDay2=31;
        Ledger.Entry e=new Ledger.Entry(3,false,10000,LocalDateTime.parse("2026-09-10T12:00"),"差旅","",0);e.kind=Ledger.REIMBURSE;Ledger.Receipt r=new Ledger.Receipt(10000,LocalDateTime.parse("2026-10-19T12:00"));r.accountId=1;e.receipts.add(r);e.status="已报销";d.entries.add(e);store.save(d);Ledger.Data copy=store.parseBackup(store.exportJson());assertEquals(31,copy.settings.checkDay2);assertEquals(1,copy.entries.get(0).receipts.get(0).accountId);
    }
    @Test public void chosenIconsAndCategoryOrderSurviveDatabaseAndBackup(){
        Ledger.Data d=base();java.util.Collections.swap(d.categories,0,2);
        String old=d.iconOf("奖学金");String chosen=LabelRules.firstFree(d,null);
        LabelRules.edit(d,"奖学金","学业奖励",chosen);store.save(d);
        Ledger.Data restored=store.parseBackup(store.exportJson());
        assertEquals(d.categories,restored.categories);assertEquals(chosen,restored.iconOf("学业奖励"));assertNotEquals(old,chosen);
        assertEquals(restored.categories.size(),new java.util.HashSet<>(restored.categoryIcons.values()).size());
    }
    @Test public void failureAfterAnEarlierValidSheetStillLeavesLedgerUntouched(){
        Ledger.Data d=base();store.save(d);String original=store.exportJson();
        byte[] book=Xlsx.write(java.util.Arrays.asList(
            new Xlsx.Sheet("收入").add(HistoryImport.INCOME_HEADER.split(",")).add("工资","2025/01/01","100","",""),
            new Xlsx.Sheet("基金").add(FundSheet.HEADER.split(",")).add("基金","1","错误","100","0")));
        try{HistoryImport.preview(book,d);fail();}catch(IllegalArgumentException expected){}
        assertEquals(original,store.exportJson());assertTrue(d.entries.isEmpty());
    }
    @Test public void fundTagsPersistAndOldBackupsDefaultToEmptyTags()throws Exception{
        Ledger.Data data=base();Ledger.Fund f=new Ledger.Fund();f.id=40;f.code="000001";f.name="基金";f.tag="余利宝";
        Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=41;buy.amount=100000;buy.at=LocalDateTime.of(2025,1,6,10,0);f.buys.add(buy);
        FundEngine.resolve(f,new TradingCalendar());data.funds.add(f);store.save(data);
        assertEquals("余利宝",store.load().funds.get(0).tag);assertEquals("余利宝",store.parseBackup(store.exportJson()).funds.get(0).tag);
        org.json.JSONObject json=new org.json.JSONObject(store.exportJson());json.getJSONArray("funds").getJSONObject(0).remove("tag");
        assertEquals("",store.parseBackup(json.toString()).funds.get(0).tag);
    }
    @Test public void correctedCodeSurvivesDatabaseWithoutOldMarketData()throws Exception{
        Ledger.Data data=base();Ledger.Fund f=new Ledger.Fund();f.id=40;f.code="000001";f.name="原基金";f.tag="零钱通";
        Ledger.FundBuy b=new Ledger.FundBuy();b.id=41;b.amount=100000;b.at=LocalDateTime.of(2025,1,6,10,0);f.buys.add(b);f.prices.put(b.at.toLocalDate(),BigDecimal.ONE);data.funds.add(f);FundEngine.resolve(f,new TradingCalendar());
        FundClient.Quote q=new FundClient.Quote();q.code="000002";q.name="修正基金";q.nav=new BigDecimal("2");q.previous=q.nav;q.date="2025-01-06";q.history.put(b.at.toLocalDate(),q.nav);
        FundTransactions.save(data,40,41,"000002",100000,0,b.at,q,"",false,new TradingCalendar());store.save(data);
        Ledger.Fund saved=store.load().funds.get(0);assertEquals("000002",saved.code);assertEquals(new BigDecimal("500.00"),saved.buys.get(0).units);assertEquals("零钱通",saved.tag);
    }
}
