package cn.yueji.balance;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class XlsxTest {
    interface Run {void go();}
    void rejects(String fragment,Run run){try{run.go();fail("should reject: "+fragment);}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains(fragment));}}

    static byte[] zip(Map<String,String> parts){
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(Map.Entry<String,String> e:parts.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();}}return out.toByteArray();}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    static final String MAIN="xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"",R="xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"";
    static final String HEAD="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    /** What Excel itself writes: shared strings, custom date format, date-styled serials, sheet paths through relationships. */
    static Map<String,String> excelLike(boolean date1904,String sheet1,String sheet2){
        Map<String,String> p=new LinkedHashMap<>();
        p.put("[Content_Types].xml",HEAD+"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        p.put("xl/workbook.xml",HEAD+"<workbook "+MAIN+" "+R+"><workbookPr"+(date1904?" date1904=\"1\"":"")+"/><sheets><sheet name=\"支出\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"基金\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
        p.put("xl/_rels/workbook.xml.rels",HEAD+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"/xl/worksheets/sheet2.xml\"/></Relationships>");
        p.put("xl/styles.xml",HEAD+"<styleSheet "+MAIN+"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"yyyy/m/d\\ h:mm\"/></numFmts><cellStyleXfs count=\"1\"><xf numFmtId=\"14\"/></cellStyleXfs>"
            +"<cellXfs count=\"5\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/><xf numFmtId=\"164\"/><xf numFmtId=\"49\"/><xf numFmtId=\"2\"/></cellXfs></styleSheet>");
        // 0 支出类型 1 时间 2 金额 3 名称 4 基金名称 5 基金代码 6 购入时间 7 购入金额 8 手续费 9 月支出 10 大宗支出 11 示例 (rich) 12 备注 with phonetic
        p.put("xl/sharedStrings.xml",HEAD+"<sst "+MAIN+" count=\"13\" uniqueCount=\"13\"><si><t>支出类型</t></si><si><t>时间</t></si><si><t>金额</t></si><si><t>名称</t></si><si><t>基金名称</t></si><si><t>基金代码</t></si><si><t>购入时间</t></si><si><t>购入金额</t></si><si><t>手续费</t></si><si><t>月支出</t></si><si><t>大宗支出</t></si>"
            +"<si><r><rPr><b/></rPr><t>示例</t></r><r><t xml:space=\"preserve\"> 基金</t></r></si><si><t>电脑</t><rPh sb=\"0\" eb=\"2\"><t>ＤＩＮＮＡＯ</t></rPh></si></sst>");
        p.put("xl/worksheets/sheet1.xml",HEAD+"<worksheet "+MAIN+"><sheetData>"+sheet1+"</sheetData></worksheet>");
        p.put("xl/worksheets/sheet2.xml",HEAD+"<worksheet "+MAIN+"><sheetData>"+sheet2+"</sheetData></worksheet>");
        return p;
    }
    static final String SHEET1="<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row>"
        +"<row r=\"3\"><c r=\"A3\" t=\"s\"><v>9</v></c><c r=\"B3\" s=\"1\"><v>46266</v></c><c r=\"C3\"><v>1500.1000000000001</v></c></row>"
        +"<row r=\"4\"><c r=\"A4\" t=\"s\"><v>10</v></c><c r=\"B4\" s=\"1\"><v>46285</v></c><c r=\"C4\" s=\"4\"><v>0.30000000000000004</v></c><c r=\"D4\" t=\"s\"><v>12</v></c></row>"
        +"<row r=\"6\"><c r=\"A6\" s=\"3\"/><c r=\"B6\" s=\"3\"/></row>";
    static final String SHEET2="<row r=\"1\"><c r=\"A1\" t=\"s\"><v>4</v></c><c r=\"B1\" t=\"s\"><v>5</v></c><c r=\"C1\" t=\"s\"><v>6</v></c><c r=\"D1\" t=\"s\"><v>7</v></c><c r=\"E1\" t=\"s\"><v>8</v></c></row>"
        +"<row r=\"2\"><c r=\"A2\" t=\"s\"><v>11</v></c><c r=\"B2\"><v>5827</v></c><c r=\"C2\" s=\"2\"><v>46285.4375</v></c><c r=\"D2\"><v>1000</v></c><c r=\"E2\" t=\"inlineStr\"><is><r><t>1.</t></r><r><t>50</t></r></is></c><c r=\"F2\" t=\"b\"><v>1</v></c><c r=\"G2\" t=\"str\"><f>1+1</f><v>2</v></c></row>";

    @Test public void readsAnExcelStyleWorkbook(){
        List<Xlsx.Sheet> sheets=Xlsx.read(zip(excelLike(false,SHEET1,SHEET2)));
        assertEquals(2,sheets.size());assertEquals("支出",sheets.get(0).name);assertEquals("基金",sheets.get(1).name);
        List<List<String>> a=sheets.get(0).rows;
        assertEquals(4,a.size());                                       // row 2 is an empty gap row, row 5/6 blanks are dropped
        assertEquals(Arrays.asList("支出类型","时间","金额","名称"),a.get(0));assertEquals(Arrays.asList(),a.get(1));
        assertEquals(Arrays.asList("月支出","2026/09/01","1500.1"),a.get(2));
        assertEquals(Arrays.asList("大宗支出","2026/09/20","0.3","电脑"),a.get(3));  // rPh phonetic text is not part of the value
        List<String> f=sheets.get(1).rows.get(1);
        assertEquals("示例 基金",f.get(0));assertEquals("5827",f.get(1));assertEquals("2026/09/20 10:30",f.get(2));assertEquals("1000",f.get(3));assertEquals("1.50",f.get(4));assertEquals("TRUE",f.get(5));assertEquals("2",f.get(6));
    }
    @Test public void excelStyleFilesFlowThroughTheImporters(){
        String sheet1="<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c></row>"
            +"<row r=\"2\"><c r=\"A2\" t=\"s\"><v>9</v></c><c r=\"B2\" s=\"1\"><v>46266</v></c><c r=\"C2\"><v>1500.1000000000001</v></c></row>";
        // Old mixed/partial-header workbooks are no longer legal import templates.
        rejects("xlsx",()->HistoryImport.importInto(new Ledger.Data(),zip(excelLike(false,sheet1,SHEET2))));
        rejects("xlsx",()->FundSheet.parse(zip(excelLike(false,sheet1,SHEET2))));
    }
    static Map<String,String> parts(byte[] file){
        Map<String,String> result=new LinkedHashMap<>();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(file))){
            ZipEntry e;while((e=z.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=z.read(b))>0)out.write(b,0,n);result.put(e.getName(),out.toString("UTF-8"));}
        }catch(IOException e){throw new UncheckedIOException(e);}return result;
    }
    static byte[] typedDate(Xlsx.Sheet sheet,String cell,String serial,boolean epoch1904){
        Map<String,String> p=parts(Xlsx.write(Collections.singletonList(sheet)));
        p.put("xl/styles.xml",p.get("xl/styles.xml").replace("<cellXfs count=\"4\">","<cellXfs count=\"5\">").replace("</cellXfs>","<xf numFmtId=\"22\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"));
        p.put("xl/worksheets/sheet1.xml",p.get("xl/worksheets/sheet1.xml").replaceAll("<c r=\""+cell+"\"[^>]*>.*?</c>","<c r=\""+cell+"\" s=\"4\"><v>"+serial+"</v></c>"));
        if(epoch1904)p.put("xl/workbook.xml",p.get("xl/workbook.xml").replace("<bookViews>","<workbookPr date1904=\"1\"/><bookViews>"));
        return zip(p);
    }
    @Test public void strictImportStillUnderstandsRealExcelDateCells(){
        Xlsx.Sheet month=ImportTest.sheet("支出",HistoryImport.EXPENSE_HEADER,ImportTest.month("placeholder","1500.10"));
        Ledger.Data d=new Ledger.Data();HistoryImport.importInto(d,typedDate(month,"B2","46266",false));assertEquals(YearMonth.of(2026,9),d.history.get(0).month);
        Ledger.Data mac=new Ledger.Data();HistoryImport.importInto(mac,typedDate(month,"B2","44804",true));assertEquals(YearMonth.of(2026,9),mac.history.get(0).month);
        rejects("月份须写成",()->HistoryImport.importInto(new Ledger.Data(),typedDate(month,"B2","46285",false)));
        Xlsx.Sheet fund=ImportTest.sheet("基金",FundSheet.HEADER,new String[]{"基金","005827","placeholder","1000","1.50"});
        assertEquals(LocalDateTime.of(2026,9,20,10,30),FundSheet.parse(typedDate(fund,"C2","46285.4375",false)).get(0).at);
        assertEquals(LocalDateTime.of(2026,9,20,0,0),FundSheet.parse(typedDate(fund,"C2","46285",false)).get(0).at);
    }
    @Test public void strictImportRejectsFormulasEvenWhenCachedValueLooksValid(){
        Map<String,String> p=parts(ImportTest.incomes(ImportTest.income("工资","2025/01/01","100")));
        p.put("xl/worksheets/sheet1.xml",p.get("xl/worksheets/sheet1.xml").replaceAll("<c r=\"C2\"[^>]*>.*?</c>","<c r=\"C2\"><f>50+50</f><v>100</v></c>"));
        rejects("C2",()->HistoryImport.importInto(new Ledger.Data(),zip(p)));rejects("公式",()->HistoryImport.importInto(new Ledger.Data(),zip(p)));
    }
    @Test public void strictImportRejectsBooleanErrorMergeAndMissingParts(){
        for(String cell:new String[]{"<c r=\"C2\" t=\"b\"><v>1</v></c>","<c r=\"C2\" t=\"e\"><v>#VALUE!</v></c>"}){
            Map<String,String> p=parts(ImportTest.incomes(ImportTest.income("工资","2025/01/01","100")));
            p.put("xl/worksheets/sheet1.xml",p.get("xl/worksheets/sheet1.xml").replaceAll("<c r=\"C2\"[^>]*>.*?</c>",cell));
            rejects("C2",()->HistoryImport.importInto(new Ledger.Data(),zip(p)));
        }
        Map<String,String> p=parts(HistoryImport.templateXlsx());p.remove("xl/workbook.xml");
        rejects("结构不完整",()->HistoryImport.importInto(new Ledger.Data(),zip(p)));
        Map<String,String> merge=parts(ImportTest.incomes(ImportTest.income("工资","2025/01/01","100")));
        merge.put("xl/worksheets/sheet1.xml",merge.get("xl/worksheets/sheet1.xml").replace("</worksheet>","<mergeCells count=\"1\"><mergeCell ref=\"A2:B2\"/></mergeCells></worksheet>"));
        rejects("合并单元格",()->HistoryImport.importInto(new Ledger.Data(),zip(merge)));
    }
    @Test public void financialPrecisionSurvivesBeyondTwelveDigits(){
        assertEquals("89999999999.99",Xlsx.plain(new java.math.BigDecimal("89999999999.99")));
        Xlsx.Sheet s=ImportTest.sheet("收入",HistoryImport.INCOME_HEADER,ImportTest.income("工资","2025/01/01","89999999999.99")).money(2);
        Ledger.Data d=new Ledger.Data();HistoryImport.importInto(d,Xlsx.write(Collections.singletonList(s)));assertEquals(8_999_999_999_999L,d.entries.get(0).amount);
    }
    @Test public void strictImportRequiresCompleteZipFooter(){
        byte[] file=HistoryImport.templateXlsx();
        rejects("未完整保存",()->HistoryImport.importInto(new Ledger.Data(),Arrays.copyOf(file,file.length-22)));
    }
    @Test public void date1904WorkbooksUseTheirOwnEpoch(){
        String sheet="<row r=\"1\"><c r=\"A1\" t=\"s\"><v>1</v></c><c r=\"B1\" s=\"1\"><v>44804</v></c></row>";
        assertEquals("2026/09/01",Xlsx.read(zip(excelLike(true,sheet,SHEET2))).get(0).rows.get(0).get(1));
    }
    @Test public void serialDatesRoundToTheMinuteAndRollOver(){
        assertEquals("2026/10/01",Xlsx.serial(new java.math.BigDecimal("46296"),false));
        assertEquals("2026/10/01 12:06",Xlsx.serial(new java.math.BigDecimal("46296.504166666666"),false));
        assertEquals("2026/10/02",Xlsx.serial(new java.math.BigDecimal("46296.99999"),false));
        assertEquals("12:00",Xlsx.serial(new java.math.BigDecimal("0.5"),false));
        assertEquals("2026/10/01",Xlsx.serial(new java.math.BigDecimal("44834"),true));
        assertEquals("1500.1",Xlsx.plain(new java.math.BigDecimal("1500.1000000000001")));assertEquals("0.3",Xlsx.plain(new java.math.BigDecimal("0.30000000000000004")));assertEquals("2500",Xlsx.plain(new java.math.BigDecimal("2500.0")));assertEquals("1000000",Xlsx.plain(new java.math.BigDecimal("1E+6")));
    }
    @Test public void dateFormatDetection(){
        for(int id:new int[]{14,15,17,22,27,36,45,47,50,58})assertTrue(""+id,Xlsx.isDateFormat(id,null));
        for(int id:new int[]{0,1,2,3,4,9,10,11,12,49,23,26,37,44,48,49,59})assertFalse(""+id,Xlsx.isDateFormat(id,null));
        for(String code:new String[]{"yyyy\\-mm\\-dd","yyyy/m/d\\ h:mm","[$-409]mmm-yy","h:mm AM/PM","yyyy\"年\"m\"月\"d\"日\"","[$-F800]dddd\\,\\ mmmm\\ dd\\,\\ yyyy"})assertTrue(code,Xlsx.isDateFormat(170,code));
        for(String code:new String[]{"0.00_);[Red]\\(0.00\\)","#,##0.00","\"yy\"0","General","@","0.00\"元\"","[Red]0.0"})assertFalse(code,Xlsx.isDateFormat(170,code));
    }
    @Test public void writeThenReadKeepsEveryCellAndTypedColumnsStayText(){
        Xlsx.Sheet a=new Xlsx.Sheet("支出").widths(10,14,12).money(2).add("类型","时间","金额").add("月支出","2026/10","2500.00").add("a & b <c> \"q\"","2026/10/21","0.5").add("第一行\n第二行","","").add("","","3");
        Xlsx.Sheet b=new Xlsx.Sheet("基金").widths(10,10).add("代码","名称").add("000123","x").add("007","y");
        List<Xlsx.Sheet> back=Xlsx.read(Xlsx.write(Arrays.asList(a,b)));
        assertEquals(2,back.size());
        assertEquals(Arrays.asList("类型","时间","金额"),back.get(0).rows.get(0));
        assertEquals(Arrays.asList("月支出","2026/10","2500"),back.get(0).rows.get(1));
        assertEquals(Arrays.asList("a & b <c> \"q\"","2026/10/21","0.5"),back.get(0).rows.get(2));
        assertEquals("第一行\n第二行",back.get(0).rows.get(3).get(0));
        assertEquals(Arrays.asList("","","3"),back.get(0).rows.get(4));
        assertEquals("000123",back.get(1).rows.get(1).get(0));assertEquals("007",back.get(1).rows.get(2).get(0));
    }
    @Test public void writtenWorkbookMarksEveryColumnAsTextExceptMoney() throws Exception{
        Xlsx.Sheet a=new Xlsx.Sheet("支出").widths(10,14,12).money(2).add("类型","时间","金额").add("月支出","2026/10","2500.00");
        Map<String,String> parts=new HashMap<>();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(Xlsx.write(Collections.singletonList(a))))){ZipEntry e;while((e=z.getNextEntry())!=null){ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=z.read(buf))>0)o.write(buf,0,n);parts.put(e.getName(),o.toString("UTF-8"));}}
        assertTrue(parts.keySet().containsAll(Arrays.asList("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/_rels/workbook.xml.rels","xl/styles.xml","xl/worksheets/sheet1.xml")));
        String sheet=parts.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet,sheet.contains("<col min=\"1\" max=\"1\" width=\"10\" style=\"2\" customWidth=\"1\"/>"));assertTrue(sheet.contains("<col min=\"2\" max=\"2\" width=\"14\" style=\"2\" customWidth=\"1\"/>"));assertTrue(sheet.contains("<col min=\"3\" max=\"3\" width=\"12\" style=\"3\" customWidth=\"1\"/>"));
        assertTrue(sheet.contains("state=\"frozen\""));assertTrue(sheet.contains("<c r=\"C2\" s=\"3\"><v>2500.00</v></c>"));assertTrue(sheet.contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">2026/10</t>"));
        assertTrue(parts.get("xl/styles.xml").contains("numFmtId=\"49\""));
    }
    @Test public void emptyAndTrailingBlankRowsAndSparseCells(){
        String sheet="<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>a</t></is></c><c r=\"D2\" t=\"inlineStr\"><is><t>d</t></is></c></row><row r=\"3\"><c r=\"A3\" t=\"inlineStr\"><is><t> </t></is></c></row><row r=\"9\"><c r=\"B9\" t=\"inlineStr\"><is><t>z</t></is></c></row><row r=\"10\"/>";
        List<List<String>> rows=Xlsx.read(zip(excelLike(false,sheet,SHEET2))).get(0).rows;
        assertEquals(9,rows.size());assertEquals(Arrays.asList(),rows.get(0));assertEquals(Arrays.asList("a","","","d"),rows.get(1));assertEquals(Arrays.asList(),rows.get(2));assertEquals(Arrays.asList("","z"),rows.get(8));
    }
    @Test public void cellsWithoutRowOrCellReferencesStillLineUp(){
        String sheet="<row><c t=\"inlineStr\"><is><t>a</t></is></c><c t=\"inlineStr\"><is><t>b</t></is></c></row><row><c t=\"inlineStr\"><is><t>c</t></is></c></row>";
        assertEquals(Arrays.asList(Arrays.asList("a","b"),Arrays.asList("c")),Xlsx.read(zip(excelLike(false,sheet,SHEET2))).get(0).rows);
    }
    @Test public void workbookWithoutRelationshipsFallsBackToWorksheetFiles(){
        Map<String,String> p=excelLike(false,SHEET1,SHEET2);p.remove("xl/workbook.xml");p.remove("xl/_rels/workbook.xml.rels");
        List<Xlsx.Sheet> sheets=Xlsx.read(zip(p));assertEquals(2,sheets.size());assertEquals("Sheet1",sheets.get(0).name);
    }

    @Test public void brokenAndHostileFilesAreRejectedWithClearMessages(){
        rejects("不是有效的 xlsx",()->Xlsx.read("hello,world".getBytes(StandardCharsets.UTF_8)));
        rejects("文件为空",()->Xlsx.read(new byte[0]));
        try{Xlsx.read(new byte[]{'P','K',3,4,0,0,0,0,0,0,0,0});fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains("xlsx"));}
        byte[] good=Xlsx.write(Collections.singletonList(new Xlsx.Sheet("a").add("x")));
        byte[] truncated=Arrays.copyOf(good,good.length/2);
        try{Xlsx.read(truncated);fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains("xlsx"));}
        byte[] big=new byte[Xlsx.MAX_FILE+1];big[0]='P';big[1]='K';rejects("10MB",()->Xlsx.read(big));
        char[] filler=new char[Xlsx.MAX_PART+1024];Arrays.fill(filler,'a');
        Map<String,String> bomb=new LinkedHashMap<>();bomb.put("xl/worksheets/sheet1.xml",new String(filler));
        rejects("过大",()->Xlsx.read(zip(bomb)));
        Map<String,String> xxe=excelLike(false,"","");xxe.put("xl/worksheets/sheet1.xml","<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><worksheet "+MAIN+"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>&e;</t></is></c></row></sheetData></worksheet>");
        try{List<Xlsx.Sheet> sheets=Xlsx.read(zip(xxe));assertFalse(sheets.get(0).rows.toString(),sheets.get(0).rows.toString().contains("root:"));}catch(IllegalArgumentException expected){}
        Map<String,String> deep=excelLike(false,"<row r=\"1000000\"><c r=\"A1000000\" t=\"inlineStr\"><is><t>x</t></is></c></row>","");
        rejects("行数过多",()->Xlsx.read(zip(deep)));
        Map<String,String> malformed=excelLike(false,"","");malformed.put("xl/worksheets/sheet1.xml","<worksheet><sheetData><row></sheetData>");
        rejects("无法解析",()->Xlsx.read(zip(malformed)));
    }
    @Test public void sheetNamesAreSanitisedAndUnique(){
        List<Xlsx.Sheet> back=Xlsx.read(Xlsx.write(Arrays.asList(new Xlsx.Sheet("a/b:c").add("x"),new Xlsx.Sheet("A/B:C").add("y"),new Xlsx.Sheet("").add("z"))));
        assertEquals("a_b_c",back.get(0).name);assertNotEquals(back.get(0).name.toLowerCase(),back.get(1).name.toLowerCase());assertEquals("Sheet",back.get(2).name);
    }
}
