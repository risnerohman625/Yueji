package cn.yueji.balance;

import org.junit.Test;
import org.junit.Assume;
import java.io.*;
import java.time.*;
import static org.junit.Assert.*;

/** The author's real history: the shipped .xlsx, and the original CSV it was converted from (while that file is still around). */
public class RealCsvTest {
    static File find(String name){
        File file=new File("docs/"+name);if(!file.isFile())file=new File("../docs/"+name);return file;
    }
    static byte[] read(File file){try{return java.nio.file.Files.readAllBytes(file.toPath());}catch(IOException e){throw new UncheckedIOException(e);}}

    static void verify(Ledger.Data ledger,HistoryImport.Result r){
        assertEquals(51,r.months);assertEquals(71,r.incomes);assertEquals(16,r.reimbursements);assertEquals(0,r.expenses);
        Ledger.Report oct=Ledger.month(r.data,YearMonth.of(2026,10));
        long sourceAmount=-1;
        for(Xlsx.Sheet sheet:Xlsx.read(read(find("Yueji-历史收支报销导入.xlsx"))))if(sheet.name.equals("支出"))
            for(java.util.List<String> row:sheet.rows)if(row.size()>2&&row.get(0).equals("月支出")&&row.get(1).equals("2026/10"))sourceAmount=Ledger.parseMoney(row.get(2),false);
        assertTrue(sourceAmount>=0);assertTrue(oct.usable());assertEquals(sourceAmount,oct.expense);
        assertTrue(Ledger.yearExpense(r.data,2025)>0);
        assertEquals(87,r.entries);
        byte[] out=HistoryImport.exportXlsx(r.data,LocalDate.of(2022,8,1),LocalDate.of(2026,10,31),true,true,true);
        assertEquals("re-export imports without new records",87+51,HistoryImport.importInto(r.data,out).skipped);
    }

    @Test public void realHistoricalWorkbookImports(){
        File file=find("Yueji-历史收支报销导入.xlsx");Assume.assumeTrue("Optional private history fixture is not distributed",file.isFile());
        Ledger.Data ledger=new Ledger.Data();verify(ledger,HistoryImport.importInto(ledger,read(file)));
    }
    /** The old CSV was saved through Excel, which turned the month "Aug-22" into the date 2026/8/22. That must be refused, not read as August 2026. */
    @Test public void excelMangledMonthsAreRefusedWithARowNumber(){
        try{HistoryImport.importInto(new Ledger.Data(),ImportTest.monthly("2026/8/22","1874.24"));fail();}
        catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().startsWith("「支出」表第2行：")&&e.getMessage().contains("2026/10"));}
    }
}
