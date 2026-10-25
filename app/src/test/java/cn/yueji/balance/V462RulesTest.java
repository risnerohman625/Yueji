package cn.yueji.balance;

import org.junit.Test;
import java.time.*;
import java.math.*;
import java.util.*;
import static org.junit.Assert.*;

public class V462RulesTest {
    final V461RulesTest old=new V461RulesTest();
    Ledger.Data clone(Ledger.Data d){return LedgerStore.decode(LedgerStore.encode(d));}
    Ledger.Entry entry(Ledger.Data d,String kind,long amount,String title){
        Ledger.Entry e=new Ledger.Entry(d.nextId(),kind.equals(Ledger.INCOME),amount,LocalDateTime.of(2026,10,19,9,0),title,"测试备注",0);e.kind=kind;if(e.income)e.category="工资";d.entries.add(e);return e;
    }
    void month(Ledger.Data d,long amount){d.history.clear();d.history.add(new Ledger.HistoryMonth(YearMonth.of(2026,9),amount,LocalDateTime.of(2026,10,1,0,0)));}
    ImportJournal.Batch changeMonth(Ledger.Data d,long amount){Ledger.Data before=clone(d);month(d,amount);return ImportJournal.record(before,d,ImportJournal.RECORDS,"表格导入");}
    @Test public void splitLabelsAndOcrVariantsAreGroupedWithoutChangingProductName(){
        List<FundRecognition.Line> lines=old.lines("买人成功","买人产品 基金（LOF）a","买人金額 1,000.00元","买人时间2026-10-19 01:40","手续费0.60元");
        FundRecognition.Row row=FundDetailRecognition.parse(lines);assertTrue(row.needsReview);assertTrue(row.problem.contains("六位基金代码"));assertEquals("基金（LOF）a",row.name);assertEquals(60,row.fee);
        lines=old.lines("买入成功","手续费0.60元","买入时间2026-10-19 01:40");
        lines.add(new FundRecognition.Line("买入",10,200,70,225));lines.add(new FundRecognition.Line("产品",75,201,135,226));lines.add(new FundRecognition.Line("任意产品A",300,200,600,225));
        lines.add(new FundRecognition.Line("买入金额",10,250,140,275));lines.add(new FundRecognition.Line("1,000.00元",300,250,600,275));
        row=FundDetailRecognition.parse(lines);assertEquals("任意产品A",row.name);assertEquals(100000,row.amount);assertTrue(row.needsReview);assertTrue(row.problem.contains("六位基金代码"));
    }
    @Test public void incompleteDetailGoesToCorrectionNotWrongScreenshot(){
        FundRecognition.Row row=FundDetailRecognition.parse(old.lines("买入成功","买入产品 新基金A","买入金额1000.00元","确认时间2026-10-20"));
        assertTrue(row.needsReview);assertNull(row.at);assertTrue(row.problem.contains("手续费"));assertTrue(row.problem.contains("买入时间"));assertEquals(100000,row.amount);
    }
    @Test public void failedOrderNeverCreatesDraft(){
        ImportTest.rejects("买入详情",()->FundDetailRecognition.parse(old.lines("交易失败","买入成功","买入产品 基金A","买入金额1000.00元")));
    }
    @Test public void multipleConflictingAmountsRequireExplicitCorrection(){
        FundRecognition.Row row=FundDetailRecognition.parse(old.lines("买入成功","买入产品 基金A","买入金额1000.00元999.40元","买入时间2026-10-19 01:40","手续费0.60元"));
        assertTrue(row.needsReview);assertTrue(row.problem.contains("买入金额"));assertEquals(0,row.amount);
    }
    @Test public void journalTracksOnlyNewRowsAndRoundTripsAllDetails(){
        Ledger.Data d=new Ledger.Data();entry(d,Ledger.INCOME,100,"保留");Ledger.Data before=clone(d);
        entry(d,Ledger.LARGE,200,"电脑");entry(d,Ledger.INCOME,300,"工资");Ledger.Entry r=entry(d,Ledger.REIMBURSE,400,"差旅");r.receipts.add(new Ledger.Receipt(100,r.at));r.status=r.reimbursementStatus();month(d,500);
        ImportJournal.Batch b=ImportJournal.record(before,d,ImportJournal.RECORDS,"表格导入");
        assertEquals(4,b.items.size());assertTrue(ImportJournal.summary(b).contains("支出 2 条"));assertTrue(b.items.get(2).detail.contains("到账"));
        LedgerStore.validate(d);Ledger.Data read=clone(d);assertEquals(4,read.imports.get(0).items.size());
        ImportJournal.remove(read,b.id,null,null,new TradingCalendar());assertEquals(1,read.entries.size());assertTrue(read.history.isEmpty());assertEquals("保留",read.entries.get(0).title);
    }
    @Test public void noOpImportsDoNotInventBatches(){
        Ledger.Data d=new Ledger.Data();entry(d,Ledger.LARGE,100,"手动");assertNull(ImportJournal.record(clone(d),d,ImportJournal.RECORDS,"表格导入"));assertTrue(d.imports.isEmpty());
    }
    @Test public void individualRemovalKeepsBatchAndOtherRows(){
        Ledger.Data d=new Ledger.Data(),before=clone(d);Ledger.Entry a=entry(d,Ledger.LARGE,100,"第一条");entry(d,Ledger.INCOME,200,"第二条");
        ImportJournal.Batch b=ImportJournal.record(before,d,ImportJournal.RECORDS,"表格导入");ImportJournal.remove(d,b.id,""+a.id,"entry",new TradingCalendar());
        assertEquals(1,d.entries.size());assertEquals(1,ImportJournal.visible(d,ImportJournal.RECORDS).size());assertTrue(b.items.get(0).deleted);assertFalse(b.items.get(1).deleted);
    }
    @Test public void deletingLatestMonthRestoresTheTrueOriginal(){
        Ledger.Data d=new Ledger.Data();month(d,100);ImportJournal.Batch a=changeMonth(d,200),b=changeMonth(d,300);
        ImportJournal.remove(d,b.id,null,null,new TradingCalendar());assertEquals(200,d.history.get(0).expense);assertEquals(a.id,d.history.get(0).importId);
        ImportJournal.remove(d,a.id,null,null,new TradingCalendar());assertEquals(100,d.history.get(0).expense);assertEquals(0,d.history.get(0).importId);
    }
    @Test public void olderDeletionNeverDeletesNewerMonthAndCannotBeResurrected(){
        Ledger.Data d=new Ledger.Data();month(d,100);ImportJournal.Batch a=changeMonth(d,200),b=changeMonth(d,300);
        ImportJournal.remove(d,a.id,null,null,new TradingCalendar());assertEquals(300,d.history.get(0).expense);
        d=clone(d);ImportJournal.remove(d,b.id,null,null,new TradingCalendar());assertEquals(100,d.history.get(0).expense);assertEquals(0,d.history.get(0).importId);
    }
    @Test public void deletingOldAndNewMonthDoesNotRestoreDeletedImportedMonth(){
        Ledger.Data d=new Ledger.Data();ImportJournal.Batch a=changeMonth(d,200),b=changeMonth(d,300);
        ImportJournal.remove(d,a.id,null,null,new TradingCalendar());ImportJournal.remove(d,b.id,null,null,new TradingCalendar());assertTrue(d.history.isEmpty());
    }
    @Test public void manuallyChangedAggregateIsProtectedAndShownAsUnknown(){
        Ledger.Data d=new Ledger.Data();ImportJournal.Batch a=changeMonth(d,200);d.history.get(0).expense=999;
        ImportJournal.Removal r=ImportJournal.remove(d,a.id,null,null,new TradingCalendar());assertEquals(1,r.preserved);assertEquals(999,d.history.get(0).expense);
        assertEquals(1,ImportJournal.untracked(d,ImportJournal.RECORDS).items.size());
    }
    Ledger.Fund buy(Ledger.Data d,String code){
        Ledger.Fund f=FundSheet.find(d,code);if(f==null){f=new Ledger.Fund();f.id=d.nextId();f.code=code;f.name="产品"+code;d.funds.add(f);}
        Ledger.FundBuy b=new Ledger.FundBuy();b.id=d.nextId();b.amount=10000;b.at=LocalDateTime.of(2026,10,19,9,0);f.buys.add(b);FundEngine.resolve(f,new TradingCalendar());return f;
    }
    @Test public void fundDeletionKeepsOtherBatchesAndManualRecords(){
        Ledger.Data d=new Ledger.Data();Ledger.Fund f=buy(d,"000001");long oldId=f.buys.get(0).id;
        Ledger.Data before=clone(d);buy(d,"000001");ImportJournal.Batch a=ImportJournal.record(before,d,ImportJournal.FUNDS,"买入详情截图");
        ImportJournal.remove(d,a.id,null,null,new TradingCalendar());assertEquals(1,f.buys.size());assertEquals(oldId,f.buys.get(0).id);
        before=clone(d);Ledger.Fund newFund=buy(d,"000002");a=ImportJournal.record(before,d,ImportJournal.FUNDS,"表格导入");
        Ledger.Entry transfer=entry(d,Ledger.INVEST_OUT,10000,"划转");transfer.note="自动关联买入 "+newFund.buys.get(0).id;entry(d,Ledger.LARGE,100,"手动支出");
        ImportJournal.remove(d,a.id,null,null,new TradingCalendar());assertEquals(1,d.funds.size());assertEquals(1,d.entries.size());
    }
    @Test public void movedBuyStillBelongsToOriginalImport(){
        Ledger.Data d=new Ledger.Data(),before=clone(d);Ledger.Fund f=buy(d,"000001");ImportJournal.Batch b=ImportJournal.record(before,d,ImportJournal.FUNDS,"截图");
        f.code="000002";f.name="校正代码";ImportJournal.remove(d,b.id,null,null,new TradingCalendar());assertTrue(d.funds.isEmpty());
    }
    @Test public void legacyRecordsAreExplicitlyUnknownNotFakeImportBatches(){
        Ledger.Data d=new Ledger.Data();entry(d,Ledger.LARGE,100,"旧版或手动");buy(d,"000001");
        assertTrue(ImportJournal.visible(d,ImportJournal.RECORDS).isEmpty());assertEquals(1,ImportJournal.untracked(d,ImportJournal.RECORDS).items.size());
        ImportJournal.removeUntracked(d,ImportJournal.FUNDS,null,new TradingCalendar());assertTrue(d.funds.isEmpty());assertEquals(1,d.entries.size());
    }
    @Test public void deletingDisplayedUnknownGroupCannotDeleteRecordsAddedAfterConfirmationOpened(){
        Ledger.Data d=new Ledger.Data();entry(d,Ledger.LARGE,100,"已展示");
        List<ImportJournal.Item> displayed=ImportJournal.untracked(d,ImportJournal.RECORDS).items;
        Ledger.Entry later=entry(d,Ledger.INCOME,200,"弹窗后新增");
        ImportJournal.removeUntrackedBatch(d,ImportJournal.RECORDS,displayed,new TradingCalendar());
        assertEquals(1,d.entries.size());assertEquals(later.id,d.entries.get(0).id);
    }
    @Test public void deletedBuyCanBeImportedAgainWithoutAnyImageBlacklist(){
        Ledger.Data d=new Ledger.Data();Ledger.Fund f=buy(d,"000001");FundRecognition.Row row=new FundRecognition.Row();row.code=f.code;row.at=f.buys.get(0).at;
        assertTrue(FundDetailRecognition.duplicate(row,d));ImportJournal.removeUntracked(d,ImportJournal.FUNDS,null,new TradingCalendar());assertFalse(FundDetailRecognition.duplicate(row,d));
    }
    @Test public void bankRollbackRestoresPriorEvidenceButProtectsLaterManualFlows(){
        Ledger.Data d=new Ledger.Data();BankLedger.Product p=old.product(d);Ledger.Data before=clone(d);
        BankAccounting.importIncome(d,p,old.income(104));ImportJournal.Batch b=ImportJournal.record(before,d,ImportJournal.FUNDS,"银行理财截图");
        assertEquals(1,b.items.size());ImportJournal.remove(d,b.id,null,null,new TradingCalendar());assertEquals(0,BankLedger.profit(d.banks.get(0)));assertEquals(1,d.banks.get(0).observations.size());
        before=clone(d);p=d.banks.get(0);BankAccounting.importIncome(d,p,old.income(104));b=ImportJournal.record(before,d,ImportJournal.FUNDS,"银行理财截图");
        BankAccounting.flow(d,p,10000,true,FundEngine.now().minusMinutes(2),null);
        assertEquals(1,ImportJournal.remove(d,b.id,null,null,new TradingCalendar()).preserved);assertEquals(104,BankLedger.profit(d.banks.get(0)));
    }
    @Test public void originalFormatSixLoadsWithoutInventedProvenance()throws Exception{
        Ledger.Data d=new Ledger.Data();entry(d,Ledger.INCOME,100,"工资");
        org.json.JSONObject json=new org.json.JSONObject(LedgerStore.encode(d));json.put("version",6);json.remove("imports");
        Ledger.Data restored=LedgerStore.decode(json.toString());assertTrue(restored.imports.isEmpty());assertEquals(1,restored.entries.size());
    }
}
