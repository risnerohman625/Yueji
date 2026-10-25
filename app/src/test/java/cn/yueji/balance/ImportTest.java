package cn.yueji.balance;

import org.junit.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

/** Positive round-trips and deliberately invalid templates. CSV is only ever a rejection fixture. */
public class ImportTest {
    interface Run{void go();}
    static void rejects(String fragment,Run run){
        try{run.go();fail("must reject "+fragment);}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains(fragment));}
    }
    static Xlsx.Sheet sheet(String name,String header,String[]... rows){
        Xlsx.Sheet s=new Xlsx.Sheet(name).add(header.split(","));for(String[] row:rows)s.add(row);return s;
    }
    static byte[] book(Xlsx.Sheet... sheets){return Xlsx.write(Arrays.asList(sheets));}
    static String[] income(String category,String date,String amount){return new String[]{category,date,amount,"",""};}
    static String[] month(String date,String amount){return new String[]{"月支出",date,amount,"","","整月汇总","已排除报销"};}
    static byte[] monthly(String date,String amount){return book(sheet("支出",HistoryImport.EXPENSE_HEADER,month(date,amount)));}
    static byte[] incomes(String[]... rows){return book(sheet("收入",HistoryImport.INCOME_HEADER,rows));}
    static byte[] reimburse(String status,String receipts){
        return book(sheet("报销",HistoryImport.REIMBURSE_HEADER,new String[]{"差旅","2025/01/15","600.00","","差旅,住宿\n第二行",status,receipts}));
    }
    static byte[] funds(String code,String time,String amount,String fee){
        return book(sheet("基金",FundSheet.HEADER,new String[]{"示例基金",code,time,amount,fee}));
    }
    Ledger.Data data(){
        Ledger.Data d=new Ledger.Data();Ledger.Account a=new Ledger.Account(1,"中国银行","中国银行");a.created=LocalDateTime.of(2025,1,1,0,0);d.accounts.add(a);return d;
    }
    static List<List<String>> rows(byte[] file,String name){
        for(Xlsx.Sheet s:Xlsx.read(file))if(s.name.equals(name))return s.rows;throw new AssertionError(name);
    }
    @Test public void fullTemplateImportsEveryModule(){
        Ledger.Data d=data();HistoryImport.Result r=HistoryImport.importInto(d,HistoryImport.templateXlsx());
        assertEquals(1,r.months);assertEquals(1,r.expenses);assertEquals(1,r.incomes);assertEquals(1,r.reimbursements);assertEquals(3,r.entries);
        assertEquals(250000,Ledger.month(d,YearMonth.of(2025,1)).expense);
    }
    @Test public void templateHasExactNamesAndOrderedCompleteHeaders(){
        byte[] file=HistoryImport.templateXlsx();
        assertEquals(Arrays.asList(HistoryImport.EXPENSE_HEADER.split(",")),rows(file,"支出").get(0));
        assertEquals(Arrays.asList(HistoryImport.INCOME_HEADER.split(",")),rows(file,"收入").get(0));
        assertEquals(Arrays.asList(HistoryImport.REIMBURSE_HEADER.split(",")),rows(file,"报销").get(0));
    }
    @Test public void moduleSheetsCanBeOmittedAndReorderedButNotRenamed(){
        byte[] file=book(sheet("报销",HistoryImport.REIMBURSE_HEADER),new Xlsx.Sheet("说明").add("说明"),sheet("收入",HistoryImport.INCOME_HEADER,income("奖学金","2025/01/01","100")));
        assertEquals(1,HistoryImport.importInto(data(),file).incomes);
        rejects("不支持工作表",()->HistoryImport.importInto(data(),book(sheet("工资",HistoryImport.INCOME_HEADER))));
    }
    @Test public void missingExtraReorderedDuplicatedAndMisspelledHeadersAreRejected(){
        String[] headers={"收入类别,到账时间,金额,账户","收入类别,到账时间,金额,账户,备注,多余","到账时间,收入类别,金额,账户,备注","收入类别,到账时间,金额,账户,账户","收入类别,到帐时间,金额,账户,备注"};
        for(String h:headers)rejects("第1行",()->HistoryImport.importInto(data(),book(new Xlsx.Sheet("收入").add(h.split(",")))));
    }
    @Test public void headerMustBeFirstRowAndCannotRecurInsideData(){
        rejects("第1行",()->HistoryImport.importInto(data(),book(new Xlsx.Sheet("收入").add("收入").add(HistoryImport.INCOME_HEADER.split(",")))));
        rejects("第2行",()->HistoryImport.importInto(data(),book(new Xlsx.Sheet("收入").add(HistoryImport.INCOME_HEADER.split(",")).add(HistoryImport.INCOME_HEADER.split(",")))));
    }
    @Test public void noUnknownSheetsAreSilentlyDiscarded(){
        byte[] file=book(sheet("收入",HistoryImport.INCOME_HEADER,income("工资","2025/01/01","100")),new Xlsx.Sheet("其他").add("不能忽略的记录"));
        rejects("不支持工作表",()->HistoryImport.importInto(data(),file));
        rejects("不支持工作表",()->HistoryImport.importInto(data(),FundSheet.templateXlsx()));
        rejects("不支持工作表",()->FundSheet.parse(HistoryImport.templateXlsx()));
    }
    @Test public void onlyXlsxBytesAreAcceptedIncludingRenamedCsvAndGbk()throws Exception{
        for(String encoding:new String[]{"UTF-8","GBK"}){
            byte[] csv=("收入类别,到账时间,金额,账户,备注\n工资,2025/01/01,100,,").getBytes(encoding);
            rejects("xlsx",()->HistoryImport.importInto(data(),csv));rejects("xlsx",()->FundSheet.parse(csv));
        }
        rejects("CSV",()->HistoryImport.importInto(data(),"x,y,z"));
        rejects("CSV",()->FundSheet.parse("x,y,z"));
        rejects("文件为空",()->HistoryImport.importInto(data(),new byte[0]));
        rejects("xlsx",()->FundSheet.parse(new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0}));
    }
    @Test public void blankDataAndOutOfTemplateCellsAreRejected(){
        rejects("没有可导入",()->HistoryImport.importInto(data(),book(sheet("收入",HistoryImport.INCOME_HEADER))));
        rejects("超出模板",()->HistoryImport.importInto(data(),incomes(new String[]{"工资","2025/01/01","100","","","不能丢失"})));
        rejects("超出模板",()->FundSheet.parse(book(sheet("基金",FundSheet.HEADER,new String[]{"基金","000001","2025/01/01 12:00","100","0","额外"}))));
    }
    @Test public void blankRowsDoNotChangeTheOriginalErrorRow(){
        Xlsx.Sheet s=sheet("收入",HistoryImport.INCOME_HEADER).add("","","","","").add(income("工资","错误","100"));
        rejects("「收入」表第3行",()->HistoryImport.importInto(data(),book(s)));
    }
    @Test public void badDatesMonthsAndFutureDatesAreNotGuessed(){
        for(String date:new String[]{"2025-01-01","2025年1月1日","2025/02/30","2025/01/01 12:00",Ledger.csvDate(LocalDate.now().plusDays(1))})
            rejects("第2行",()->HistoryImport.importInto(data(),incomes(income("工资",date,"100"))));
        for(String date:new String[]{"Aug-22","2026/8/22","2025/01/01","2025-01","1999/01","2025/13"})
            rejects("第2行",()->HistoryImport.importInto(data(),monthly(date,"100")));
    }
    @Test public void amountsAreNonnegativeCentsAndNeverSilentlyRounded(){
        for(String amount:new String[]{"-1","1.001","1e3","NaN","1,000","0","90000000000.01"})
            rejects("第2行",()->HistoryImport.importInto(data(),incomes(income("工资","2025/01/01",amount))));
        Ledger.Data d=data();HistoryImport.importInto(d,incomes(income("工资","2025/01/01","89999999999.99")));
        assertEquals(8_999_999_999_999L,d.entries.get(0).amount);
        assertEquals(0,HistoryImport.importInto(data(),monthly("2025/01","0")).data.history.get(0).expense);
    }
    @Test public void monthlyRowsRequireBasisAndEmptyNameAndAccount(){
        String[] row=month("2025/01","100");row[6]="";
        rejects("报销口径",()->HistoryImport.importInto(data(),book(sheet("支出",HistoryImport.EXPENSE_HEADER,row))));
        row[6]="已排除报销";row[3]="不应该忽略";
        rejects("名称列须留空",()->HistoryImport.importInto(data(),book(sheet("支出",HistoryImport.EXPENSE_HEADER,row))));
        row[3]="";row[4]="中国银行";
        rejects("账户列须留空",()->HistoryImport.importInto(data(),book(sheet("支出",HistoryImport.EXPENSE_HEADER,row))));
    }
    @Test public void duplicateMonthsInsideAFileAreRejectedNotLastWins(){
        rejects("同一月份",()->HistoryImport.importInto(data(),book(sheet("支出",HistoryImport.EXPENSE_HEADER,month("2025/01","100"),month("2025/1","200")))));
    }
    @Test public void sameFileIsIdempotentAndNewerMonthReplacesOldTotal(){
        Ledger.Data d=data();byte[] file=HistoryImport.templateXlsx();HistoryImport.importInto(d,file);
        assertEquals(4,HistoryImport.importInto(d,file).skipped);assertEquals(3,d.entries.size());
        HistoryImport.Result r=HistoryImport.importInto(d,monthly("2025/01","200"));assertEquals(1,r.replaced);assertEquals(20000,d.history.get(0).expense);
    }
    @Test public void identicalIncomeRowsAreKeptAndConsumedOneForOne(){
        String[] row=income("家教","2025/01/01","100");Ledger.Data d=data();
        assertEquals(2,HistoryImport.importInto(d,incomes(row,row)).incomes);
        HistoryImport.Result r=HistoryImport.importInto(d,incomes(row,row,row));assertEquals(2,r.skipped);assertEquals(1,r.incomes);
    }
    @Test public void dateOnlyImportsMatchExistingTimedRecords(){
        Ledger.Data d=data();Ledger.Entry e=new Ledger.Entry(5,true,10000,LocalDateTime.of(2025,1,1,18,30),"工资","",0);e.category="工资";d.entries.add(e);d.categories.add("工资");
        assertEquals(1,HistoryImport.importInto(d,incomes(income("工资","2025/01/01","100"))).skipped);
    }
    @Test public void unknownAccountsAndMissingCategoriesAreRejected(){
        rejects("账户不存在",()->HistoryImport.importInto(data(),incomes(new String[]{"工资","2025/01/01","100","不存在",""})));
        rejects("请填写收入类别",()->HistoryImport.importInto(data(),incomes(income("","2025/01/01","100"))));
    }
    @Test public void importedCategoriesReceiveUniqueIcons(){
        Ledger.Data d=data();HistoryImport.importInto(d,incomes(income("家教","2025/01/01","100"),income("薪酬","2025/01/01","100")));
        assertNotNull(d.iconOf("家教"));assertNotEquals(d.iconOf("家教"),d.iconOf("薪酬"));
        assertEquals(d.categories.size(),new HashSet<>(d.categoryIcons.values()).size());
    }
    @Test public void reimbursementStatusMustAgreeWithReceipts(){
        rejects("应为“部分报销”",()->HistoryImport.importInto(data(),reimburse("已报销","2025/02/10=200")));
        rejects("超过垫付金额",()->HistoryImport.importInto(data(),reimburse("","2025/02/10=700")));
        rejects("早于垫付日期",()->HistoryImport.importInto(data(),reimburse("","2025/01/14=100")));
        rejects("到账明细格式",()->HistoryImport.importInto(data(),reimburse("","2025/02/10")));
        Ledger.Data d=data();HistoryImport.importInto(d,reimburse("","2025/02/10=200;2025/03/10=400"));
        assertEquals("已报销",d.entries.get(0).status);assertEquals(2,d.entries.get(0).receipts.size());
    }
    @Test public void fullExportRoundTripRetainsReceiptsAndSpecialCharacters(){
        Ledger.Data d=data();HistoryImport.importInto(d,reimburse("已报销","2025/02/10=200;2025/03/10=400"));
        Ledger.Entry e=new Ledger.Entry(20,false,100,LocalDateTime.of(2025,1,20,10,0),"a,\"b\" <&>","第一行\n第二行",1);d.entries.add(e);
        byte[] file=HistoryImport.exportXlsx(d,LocalDate.of(2025,1,1),LocalDate.of(2025,12,31),true,true,true);
        Ledger.Data back=data();HistoryImport.importInto(back,file);
        assertEquals(2,back.entries.size());assertEquals(2,back.entries.get(1).receipts.size());assertEquals(e.title,back.entries.get(0).title);assertEquals(e.note,back.entries.get(0).note);
        assertEquals(2,HistoryImport.importInto(back,file).skipped);
    }
    @Test public void exportRespectsDateEdgesAndKindSelection(){
        Ledger.Data d=data();HistoryImport.importInto(d,HistoryImport.templateXlsx());
        byte[] file=HistoryImport.exportXlsx(d,LocalDate.of(2025,1,10),LocalDate.of(2025,1,10),false,true,false);
        assertEquals(1,Xlsx.read(file).size());assertEquals(2,rows(file,"收入").size());
        assertEquals(1,rows(HistoryImport.exportXlsx(d,LocalDate.of(2025,1,11),LocalDate.of(2025,1,31),false,true,false),"收入").size());
        rejects("至少勾选",()->HistoryImport.exportXlsx(d,LocalDate.now(),LocalDate.now(),false,false,false));
        rejects("起始日期",()->HistoryImport.exportXlsx(d,LocalDate.now(),LocalDate.now().minusDays(1),true,false,false));
    }
    @Test public void currentMonthAndBridgeRuleStillWork(){
        Ledger.Data d=data();YearMonth now=YearMonth.now();HistoryImport.importInto(d,monthly(Ledger.csvMonth(now),"979.65"));
        assertEquals(97965,Ledger.month(d,now).expense);assertFalse(Ledger.month(d,now).closed);
        Ledger.Snapshot s=new Ledger.Snapshot(3,now.atDay(Math.min(10,LocalDate.now().getDayOfMonth())).atTime(10,0),"");s.balances.put(1L,100000L);d.snapshots.add(s);
        assertEquals(s.at,Ledger.historyCutoff(d,d.history.get(0)));
    }
    @Test public void exportedBridgedSummaryIsNotCountedAsHistoricalBaselineAgain(){
        Ledger.Data d=data();HistoryImport.importInto(d,monthly("2025/01","100"));
        Ledger.Snapshot first=new Ledger.Snapshot(10,LocalDateTime.of(2025,1,15,12,0),"");first.balances.put(1L,100000L);d.snapshots.add(first);
        Ledger.Snapshot last=new Ledger.Snapshot(11,LocalDateTime.of(2025,2,1,0,0),"");last.balances.put(1L,98000L);d.snapshots.add(last);
        assertEquals(12000,Ledger.month(d,YearMonth.of(2025,1)).expense);
        byte[] file=HistoryImport.exportXlsx(d,LocalDate.of(2025,1,1),LocalDate.of(2025,1,31),true,false,false);
        assertEquals(1,HistoryImport.importInto(d,file).skipped);
        assertEquals(10000,d.history.get(0).expense);assertEquals(12000,Ledger.month(d,YearMonth.of(2025,1)).expense);
        last.balances.put(1L,97000L);
        rejects("避免重复累计",()->HistoryImport.importInto(d,file));assertEquals(10000,d.history.get(0).expense);
    }
    @Test public void snapshotOnlyPartialMonthExportAlsoReimportsWithoutDoubleCounting(){
        Ledger.Data d=data();Ledger.Snapshot a=new Ledger.Snapshot(10,LocalDateTime.of(2025,1,15,12,0),"");a.balances.put(1L,100000L);d.snapshots.add(a);
        Ledger.Snapshot b=new Ledger.Snapshot(11,LocalDateTime.of(2025,1,20,12,0),"");b.balances.put(1L,98000L);d.snapshots.add(b);
        byte[] file=HistoryImport.exportXlsx(d,LocalDate.of(2025,1,1),LocalDate.of(2025,1,31),true,false,false);
        assertEquals(1,HistoryImport.importInto(d,file).skipped);assertTrue(d.history.isEmpty());assertEquals(2000,Ledger.month(d,YearMonth.of(2025,1)).expense);
    }
    @Test public void fundTemplateHasExactTextCodeAndMinuteTime(){
        List<FundSheet.Row> rows=FundSheet.parse(FundSheet.templateXlsx());assertEquals(1,rows.size());assertEquals("000001",rows.get(0).code);
        assertEquals(Arrays.asList(FundSheet.HEADER.split(",")),rows(FundSheet.templateXlsx(),"基金").get(0));
    }
    @Test public void shortenedNumericOrInvalidFundCodesAreRejectedNotPadded(){
        for(String code:new String[]{"1","12345","1234567","12a456","1.0",""})
            rejects("6位数字",()->FundSheet.parse(funds(code,"2025/01/01 12:00","100","0")));
    }
    @Test public void fundTimeFeeAndAmountMustBeValid(){
        for(String time:new String[]{"2025-01-01 12:00","2025/01/01","2099/01/01 12:00","2025/02/30 12:00"})
            rejects("第2行",()->FundSheet.parse(funds("000001",time,"100","0")));
        rejects("手续费须小于",()->FundSheet.parse(funds("000001","2025/01/01 12:00","100","100")));
        rejects("第2行",()->FundSheet.parse(funds("000001","2025/01/01 12:00","100","-1")));
        assertEquals(0,FundSheet.parse(funds("000001","2025/01/01 12:00","100","")).get(0).fee);
    }
    @Test public void fundHeaderAndDataCannotBeMissing(){
        rejects("第1行",()->FundSheet.parse(book(new Xlsx.Sheet("基金").add("基金代码","购入时间"))));
        rejects("没有基金数据行",()->FundSheet.parse(book(sheet("基金",FundSheet.HEADER))));
        rejects("没有找到",()->FundSheet.parse(book(new Xlsx.Sheet("说明").add("说明"))));
        rejects("基金名称",()->FundSheet.parse(book(sheet("基金",FundSheet.HEADER,new String[]{"","000001","2025/01/01 12:00","100","0"}))));
    }
    @Test public void fundImportPlansSkipsThenAppliesWithoutAccountTransfers(){
        Ledger.Data d=data();byte[] file=funds("000001","2025/01/01 12:00","100","1.50");
        FundSheet.Plan plan=FundSheet.plan(FundSheet.parse(file),d);assertEquals(1,plan.add.size());
        FundClient.Quote q=new FundClient.Quote();q.name="联网基金";q.history.put(LocalDate.of(2025,1,1),BigDecimal.ONE);q.nav=BigDecimal.ONE;q.previous=BigDecimal.ONE;q.date="2025-01-01";
        assertEquals(1,FundSheet.apply(d,plan,Collections.singletonMap("000001",q),Collections.emptyMap(),new TradingCalendar()));
        assertEquals("联网基金",d.funds.get(0).name);assertEquals(new BigDecimal("98.50"),d.funds.get(0).buys.get(0).units);assertTrue(d.entries.isEmpty());
        assertEquals(1,FundSheet.plan(FundSheet.parse(file),d).skipped);
    }
    @Test public void missingNetworkDataNeverFabricatesFundShares(){
        Ledger.Data d=data();FundSheet.Plan plan=FundSheet.plan(FundSheet.parse(funds("000001","2025/01/01 12:00","100","0")),d);
        FundSheet.apply(d,plan,Collections.emptyMap(),Collections.singletonMap("000001","离线"),new TradingCalendar());
        assertEquals(BigDecimal.ZERO,d.funds.get(0).buys.get(0).units);assertTrue(d.funds.get(0).error.contains("离线"));
    }
    @Test public void removedFundsRequireRestoreBeforeImportingNewPurchases(){
        Ledger.Data d=data();byte[] file=funds("000001","2025/01/01 12:00","100","0");FundSheet.apply(d,FundSheet.plan(FundSheet.parse(file),d),Collections.emptyMap(),Collections.emptyMap(),new TradingCalendar());
        FundEngine.remove(d.funds.get(0),LocalDateTime.of(2025,2,1,0,0));
        rejects("已从首页删除",()->FundSheet.plan(FundSheet.parse(file),d));
        FundEngine.restore(d.funds.get(0));d.funds.get(0).buys.clear();
        rejects("旧版份额",()->FundSheet.plan(FundSheet.parse(file),d));
    }
    @Test public void fundExportContainsHeldBatchesOnlyAndRoundTrips(){
        Ledger.Data d=data();byte[] file=funds("000001","2025/01/01 12:00","100","0");
        FundSheet.apply(d,FundSheet.plan(FundSheet.parse(file),d),Collections.emptyMap(),Collections.emptyMap(),new TradingCalendar());
        assertEquals(1,FundSheet.parse(FundSheet.exportXlsx(d)).size());
        d.funds.get(0).hidden=true;assertEquals(1,rows(FundSheet.exportXlsx(d),"基金").size());d.funds.get(0).hidden=false;
        d.funds.get(0).buys.get(0).saleDate=LocalDate.of(2025,2,1);assertEquals(1,rows(FundSheet.exportXlsx(d),"基金").size());
        d.funds.get(0).buys.get(0).saleDate=null;FundEngine.remove(d.funds.get(0),LocalDateTime.of(2025,2,1,0,0));assertEquals(1,rows(FundSheet.exportXlsx(d),"基金").size());
    }
}
