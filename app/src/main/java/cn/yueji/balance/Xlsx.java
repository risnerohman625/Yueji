package cn.yueji.balance;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Minimal .xlsx reader and writer (zip + XML, no third-party library; runs on Android and the plain JVM).
 * Writing: every column is Text-formatted for the whole column, so Excel/WPS never turn a typed "2026/10" into a date.
 * Reading: tolerant of real Excel files (shared strings, inline strings, sparse cells, date-styled numbers).
 */
public final class Xlsx {
    public static final int MAX_FILE=10*1024*1024,MAX_PART=30*1024*1024,MAX_TOTAL=100*1024*1024,MAX_ROW=200000,MAX_COLUMN=1000;

    /** Rows are lists of strings. widths / money are writer hints: money columns write numeric cells shown as 0.00, all others are text. Row 1 is the header. */
    public static final class Sheet {
        public final String name;public final List<List<String>> rows=new ArrayList<>();public int[] widths;public boolean[] money;
        public final Set<String> dateCells=new HashSet<>();
        public Sheet(String name){this.name=name;}
        public Sheet add(String... cells){rows.add(new ArrayList<>(Arrays.asList(cells)));return this;}
        public Sheet widths(int... w){widths=w;return this;}
        public Sheet money(int... columns){int n=0;for(int c:columns)n=Math.max(n,c+1);money=new boolean[n];for(int c:columns)money[c]=true;return this;}
    }

    // ------------------------------------------------------------------ writer
    private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main",REL="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final int S_HEADER=1,S_TEXT=2,S_MONEY=3;

    public static byte[] write(List<Sheet> sheets){
        if(sheets.isEmpty())throw new IllegalArgumentException("至少需要一张表");
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(bytes)){
                StringBuilder types=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
                for(int i=1;i<=sheets.size();i++)types.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                types.append("</Types>");put(zip,"[Content_Types].xml",types.toString());
                put(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                StringBuilder book=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\""+NS+"\" xmlns:r=\""+REL+"\"><bookViews><workbookView/></bookViews><sheets>");
                StringBuilder rels=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
                Set<String> used=new HashSet<>();
                for(int i=1;i<=sheets.size();i++){
                    String name=sheetName(sheets.get(i-1).name);while(!used.add(name.toLowerCase(Locale.ROOT)))name=name.length()>28?name.substring(0,28)+"_"+i:name+"_"+i;
                    book.append("<sheet name=\"").append(esc(name)).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
                    rels.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL).append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
                }
                book.append("</sheets></workbook>");rels.append("<Relationship Id=\"rId").append(sheets.size()+1).append("\" Type=\"").append(REL).append("/styles\" Target=\"styles.xml\"/></Relationships>");
                put(zip,"xl/workbook.xml",book.toString());put(zip,"xl/_rels/workbook.xml.rels",rels.toString());put(zip,"xl/styles.xml",STYLES);
                for(int i=1;i<=sheets.size();i++)put(zip,"xl/worksheets/sheet"+i+".xml",sheetXml(sheets.get(i-1)));
            }
            return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static final String STYLES="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\""+NS+"\">"
        +"<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FF4A2F7A\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>"
        +"<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE9DCF7\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
        +"<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        +"<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        +"<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        +"<xf numFmtId=\"49\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
        +"<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        +"<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"
        +"<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";

    private static String sheetXml(Sheet sheet){
        int columns=0;for(List<String> row:sheet.rows)columns=Math.max(columns,row.size());
        if(sheet.widths!=null)columns=Math.max(columns,sheet.widths.length);
        StringBuilder out=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\""+NS+"\">");
        out.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>");
        if(columns>0){
            out.append("<cols>");
            for(int c=0;c<columns;c++){int width=sheet.widths!=null&&c<sheet.widths.length?sheet.widths[c]:18;out.append("<col min=\"").append(c+1).append("\" max=\"").append(c+1).append("\" width=\"").append(width).append("\" style=\"").append(isMoney(sheet,c)?S_MONEY:S_TEXT).append("\" customWidth=\"1\"/>");}
            out.append("</cols>");
        }
        out.append("<sheetData>");
        for(int r=0;r<sheet.rows.size();r++){
            List<String> row=sheet.rows.get(r);out.append("<row r=\"").append(r+1).append("\">");
            for(int c=0;c<row.size();c++){
                String value=row.get(c);if(value==null||value.isEmpty())continue;String ref=column(c)+(r+1);
                if(r==0)text(out,ref,S_HEADER,value);
                else if(isMoney(sheet,c)&&value.matches("-?[0-9]+(\\.[0-9]+)?"))out.append("<c r=\"").append(ref).append("\" s=\"").append(S_MONEY).append("\"><v>").append(value).append("</v></c>");
                else text(out,ref,isMoney(sheet,c)?S_MONEY:S_TEXT,value);
            }
            out.append("</row>");
        }
        return out.append("</sheetData></worksheet>").toString();
    }
    private static boolean isMoney(Sheet sheet,int c){return sheet.money!=null&&c<sheet.money.length&&sheet.money[c];}
    private static void text(StringBuilder out,String ref,int style,String value){out.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(value)).append("</t></is></c>");}
    static String column(int index){StringBuilder s=new StringBuilder();for(int n=index+1;n>0;n=(n-1)/26)s.insert(0,(char)('A'+(n-1)%26));return s.toString();}
    private static String sheetName(String name){String n=name==null?"":name.replaceAll("[\\[\\]:*?/\\\\]","_").trim();if(n.isEmpty())n="Sheet";return n.length()>31?n.substring(0,31):n;}
    private static String esc(String s){
        StringBuilder out=new StringBuilder(s.length()+8);
        for(int i=0;i<s.length();i++){char c=s.charAt(i);
            if(c=='&')out.append("&amp;");else if(c=='<')out.append("&lt;");else if(c=='>')out.append("&gt;");else if(c=='"')out.append("&quot;");
            else if(c<0x20&&c!='\n'&&c!='\t')continue;else if(c=='\r')continue;else if(c==0xFFFE||c==0xFFFF)continue;else out.append(c);}
        return out.toString();
    }
    private static void put(ZipOutputStream zip,String name,String content)throws IOException{
        ZipEntry entry=new ZipEntry(name);entry.setTime(315532800000L+43200000L);zip.putNextEntry(entry);zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }

    // ------------------------------------------------------------------ reader
    public static boolean isXlsx(byte[] file){return file!=null&&file.length>=4&&file[0]=='P'&&file[1]=='K';}

    public static List<Sheet> read(byte[] file){return read(file,false);}
    public static List<Sheet> readStrict(byte[] file){return read(file,true);}
    private static List<Sheet> read(byte[] file,boolean strict){
        if(file==null||file.length==0)throw new IllegalArgumentException("文件为空");
        if(file.length>MAX_FILE)throw new IllegalArgumentException("文件不能超过10MB");
        if(!isXlsx(file))throw new IllegalArgumentException("不是有效的 xlsx 文件，仅支持 .xlsx 模板，不支持 CSV、xls 或文本");
        if(strict)requireZipEnd(file);
        Map<String,byte[]> parts=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(file))){
            ZipEntry entry;long total=0;int entries=0;
            while((entry=zip.getNextEntry())!=null){
                if(++entries>2000)throw new IllegalArgumentException("xlsx 内部文件过多");
                String name=entry.getName().replace('\\','/');while(name.startsWith("/"))name=name.substring(1);
                if(strict&&(name.endsWith("vbaProject.bin")||name.contains("externalLinks/")))throw new IllegalArgumentException("xlsx 模板不支持宏或外部链接");
                boolean wanted=name.equals("[Content_Types].xml")||name.startsWith("xl/")&&(name.endsWith(".xml")||name.endsWith(".rels"));
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;long size=0;
                while((n=zip.read(buffer))>0){size+=n;total+=n;if(size>MAX_PART||total>MAX_TOTAL)throw new IllegalArgumentException("xlsx 内容过大，已拒绝读取");if(wanted)out.write(buffer,0,n);}
                if(wanted&&parts.put(name,out.toByteArray())!=null)throw new IllegalArgumentException("xlsx 内部文件重复："+name);
            }
        }catch(ZipException e){throw new IllegalArgumentException("xlsx 文件已损坏，无法打开："+e.getMessage());}
        catch(IOException e){throw new IllegalArgumentException("xlsx 文件已损坏，无法读取");}
        if(parts.isEmpty())throw new IllegalArgumentException("xlsx 文件里没有工作表，请确认文件是 Excel 工作簿（.xlsx）");
        if(strict){
            if(!parts.containsKey("xl/workbook.xml")||!parts.containsKey("xl/_rels/workbook.xml.rels")||!parts.containsKey("[Content_Types].xml"))
                throw new IllegalArgumentException("xlsx 工作簿结构不完整，请重新保存为 .xlsx");
            String types=new String(parts.get("[Content_Types].xml"),StandardCharsets.UTF_8);
            if(!types.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"))
                throw new IllegalArgumentException("仅支持 .xlsx 工作簿，不支持 xlsm 或其他格式");
        }

        List<String> shared=new ArrayList<>();byte[] sst=parts.get("xl/sharedStrings.xml");if(sst!=null)parse(sst,new SharedStrings(shared));
        Styles styles=new Styles();byte[] st=parts.get("xl/styles.xml");if(st!=null)parse(st,styles);
        boolean[] date=styles.dateFlags();

        List<String[]> order=new ArrayList<>();boolean date1904=false;
        byte[] workbook=parts.get("xl/workbook.xml");
        if(workbook!=null){
            Workbook w=new Workbook();parse(workbook,w);date1904=w.date1904;
            Map<String,String> targets=new HashMap<>();byte[] rels=parts.get("xl/_rels/workbook.xml.rels");if(rels!=null){Rels r=new Rels();parse(rels,r);targets=r.targets;}
            for(String[] sheet:w.sheets){String target=targets.get(sheet[1]);if(target!=null&&parts.containsKey(path(target)))order.add(new String[]{sheet[0],path(target)});else if(strict)throw new IllegalArgumentException("工作表「"+sheet[0]+"」的引用无效");}
        }
        if(order.isEmpty()){
            List<String> names=new ArrayList<>();for(String n:parts.keySet())if(n.startsWith("xl/worksheets/")&&n.endsWith(".xml"))names.add(n);Collections.sort(names);
            for(int i=0;i<names.size();i++)order.add(new String[]{"Sheet"+(i+1),names.get(i)});
        }
        List<Sheet> sheets=new ArrayList<>();
        for(String[] item:order){
            byte[] xml=parts.get(item[1]);if(xml==null)continue;
            SheetReader reader=new SheetReader(shared,date,date1904,strict&&!item[0].equals("说明"),item[0]);parse(xml,reader);
            Sheet sheet=new Sheet(item[0]);sheet.rows.addAll(reader.result());sheet.dateCells.addAll(reader.dateCells);sheets.add(sheet);
        }
        if(sheets.isEmpty())throw new IllegalArgumentException("xlsx 文件里没有工作表，请确认文件是 Excel 工作簿（.xlsx）");
        return sheets;
    }
    private static void requireZipEnd(byte[] file){
        for(int i=file.length-22;i>=Math.max(0,file.length-65557);i--){
            if(file[i]=='P'&&file[i+1]=='K'&&file[i+2]==5&&file[i+3]==6){
                int comment=(file[i+20]&255)|((file[i+21]&255)<<8);
                if(i+22+comment==file.length)return;
            }
        }
        throw new IllegalArgumentException("xlsx 文件已损坏或未完整保存，请重新保存为 .xlsx");
    }

    private static String path(String target){
        String t=target.replace('\\','/');
        if(t.startsWith("/"))return t.substring(1);
        Deque<String> parts=new ArrayDeque<>();for(String p:("xl/"+t).split("/")){if(p.equals(".."))parts.pollLast();else if(!p.isEmpty()&&!p.equals("."))parts.addLast(p);}
        return String.join("/",parts);
    }
    private static void parse(byte[] xml,DefaultHandler handler){
        try{
            SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(true);
            feature(f,"http://apache.org/xml/features/disallow-doctype-decl",true);feature(f,"http://xml.org/sax/features/external-general-entities",false);feature(f,"http://xml.org/sax/features/external-parameter-entities",false);feature(f,XMLConstants.FEATURE_SECURE_PROCESSING,true);
            try{f.setXIncludeAware(false);}catch(Throwable ignored){}
            f.newSAXParser().parse(new ByteArrayInputStream(xml),handler);
        }catch(SAXException e){
            Exception inner=e.getException();if(inner instanceof IllegalArgumentException)throw (IllegalArgumentException)inner;
            throw new IllegalArgumentException("xlsx 文件内容无法解析："+e.getMessage());
        }catch(ParserConfigurationException|IOException e){throw new IllegalArgumentException("xlsx 文件内容无法解析");}
    }
    private static void feature(SAXParserFactory f,String name,boolean value){try{f.setFeature(name,value);}catch(Throwable ignored){}}
    private static String local(String localName,String qName){if(localName!=null&&!localName.isEmpty())return localName;int i=qName.indexOf(':');return i<0?qName:qName.substring(i+1);}

    private static class Base extends DefaultHandler {
        @Override public InputSource resolveEntity(String publicId,String systemId){return new InputSource(new StringReader(""));}
    }
    private static final class SharedStrings extends Base {
        final List<String> list;StringBuilder current;boolean inT;int phonetic;
        SharedStrings(List<String> list){this.list=list;}
        @Override public void startElement(String uri,String localName,String qName,Attributes a){
            String n=local(localName,qName);
            if(n.equals("si")){current=new StringBuilder();phonetic=0;}else if(n.equals("rPh"))phonetic++;else if(n.equals("t")&&current!=null&&phonetic==0)inT=true;
        }
        @Override public void endElement(String uri,String localName,String qName){
            String n=local(localName,qName);
            if(n.equals("t"))inT=false;else if(n.equals("rPh"))phonetic--;else if(n.equals("si")&&current!=null){list.add(current.toString());current=null;}
        }
        @Override public void characters(char[] ch,int start,int length){if(inT&&current!=null)current.append(ch,start,length);}
    }
    private static final class Workbook extends Base {
        final List<String[]> sheets=new ArrayList<>();boolean date1904;
        @Override public void startElement(String uri,String localName,String qName,Attributes a){
            String n=local(localName,qName);
            if(n.equals("workbookPr")){String v=a.getValue("date1904");date1904="1".equals(v)||"true".equalsIgnoreCase(v);}
            else if(n.equals("sheet")){String id=null;for(int i=0;i<a.getLength();i++)if(local(a.getLocalName(i),a.getQName(i)).equals("id"))id=a.getValue(i);sheets.add(new String[]{a.getValue("name")==null?"Sheet":a.getValue("name"),id});}
        }
    }
    private static final class Rels extends Base {
        final Map<String,String> targets=new HashMap<>();
        @Override public void startElement(String uri,String localName,String qName,Attributes a){
            if(local(localName,qName).equals("Relationship")&&a.getValue("Id")!=null&&a.getValue("Target")!=null&&!"External".equals(a.getValue("TargetMode")))targets.put(a.getValue("Id"),a.getValue("Target"));
        }
    }
    private static final class Styles extends Base {
        final Map<Integer,String> codes=new HashMap<>();final List<Integer> xfs=new ArrayList<>();boolean inCellXfs;
        @Override public void startElement(String uri,String localName,String qName,Attributes a){
            String n=local(localName,qName);
            if(n.equals("numFmt")){try{codes.put(Integer.parseInt(a.getValue("numFmtId")),a.getValue("formatCode"));}catch(RuntimeException ignored){}}
            else if(n.equals("cellXfs"))inCellXfs=true;
            else if(n.equals("xf")&&inCellXfs){int id=0;try{id=Integer.parseInt(a.getValue("numFmtId"));}catch(RuntimeException ignored){}xfs.add(id);}
        }
        @Override public void endElement(String uri,String localName,String qName){if(local(localName,qName).equals("cellXfs"))inCellXfs=false;}
        boolean[] dateFlags(){boolean[] flags=new boolean[xfs.size()];for(int i=0;i<flags.length;i++)flags[i]=isDateFormat(xfs.get(i),codes.get(xfs.get(i)));return flags;}
    }
    static boolean isDateFormat(int id,String code){
        if((id>=14&&id<=22)||(id>=27&&id<=36)||(id>=45&&id<=47)||(id>=50&&id<=58))return true;
        if(id<164||code==null)return false;
        String c=code.replaceAll("\"[^\"]*\"","").replaceAll("\\[[^\\]]*\\]","").replaceAll("\\\\.","").replaceAll("_.","").replaceAll("\\*.","");
        if(c.toLowerCase(Locale.ROOT).contains("general"))c=c.replaceAll("(?i)general","");
        return c.matches("(?s).*[yYmMdDhHsS].*");
    }

    private static final class SheetReader extends Base {
        final List<String> shared;final boolean[] date;final boolean date1904;
        final boolean strict;final String label;final Set<String> dateCells=new HashSet<>();int cellCount;
        final TreeMap<Integer,List<String>> rows=new TreeMap<>();
        int rowNo,lastRow,lastColumn,column;List<String> cells;String type;int style;
        final StringBuilder value=new StringBuilder(),inline=new StringBuilder();boolean inV,inT,inIs;int phonetic;
        SheetReader(List<String> shared,boolean[] date,boolean date1904,boolean strict,String label){this.shared=shared;this.date=date;this.date1904=date1904;this.strict=strict;this.label=label;}
        @Override public void startElement(String uri,String localName,String qName,Attributes a)throws SAXException{
            String n=local(localName,qName);
            switch(n){
                case "row":{String r=a.getValue("r");rowNo=r==null?lastRow+1:parseInt(r,-1);if(rowNo>MAX_ROW)throw new SAXException(new IllegalArgumentException("表格行数过多（最多"+MAX_ROW+"行）"));if(rowNo<1||rowNo<=lastRow)throw new SAXException(new IllegalArgumentException("工作表行号无效或重复"));lastRow=rowNo;cells=new ArrayList<>();lastColumn=-1;break;}
                case "c":{String ref=a.getValue("r");column=ref==null?lastColumn+1:columnOf(ref,-1);if(column>=MAX_COLUMN||++cellCount>500000)throw new SAXException(new IllegalArgumentException("表格列数或单元格数量过多"));if(column<0||column<=lastColumn)throw new SAXException(new IllegalArgumentException("单元格位置无效或重复"));lastColumn=column;type=a.getValue("t");style=parseInt(a.getValue("s"),0);if(strict&&("b".equals(type)||"e".equals(type)))throw problem("不支持布尔值或错误值，请填写模板规定的数据");value.setLength(0);inline.setLength(0);break;}
                case "f":if(strict)throw problem("不支持公式，请粘贴为数值后导入");break;
                case "mergeCell":if(strict)throw problem("模板不支持合并单元格");break;
                case "v":inV=true;break;
                case "is":inIs=true;phonetic=0;break;
                case "rPh":phonetic++;break;
                case "t":if(inIs&&phonetic==0)inT=true;break;
                default:
            }
        }
        @Override public void endElement(String uri,String localName,String qName){
            String n=local(localName,qName);
            switch(n){
                case "v":inV=false;break;
                case "t":inT=false;break;
                case "rPh":phonetic--;break;
                case "is":inIs=false;break;
                case "c":if(cells!=null){String text=convert();while(cells.size()<=column)cells.add("");cells.set(column,text);}break;
                case "row":if(cells!=null){boolean any=false;for(String s:cells)if(!s.trim().isEmpty()){any=true;break;}if(any)rows.put(rowNo,cells);cells=null;}break;
                default:
            }
        }
        @Override public void characters(char[] ch,int start,int length){if(inV)value.append(ch,start,length);else if(inT)inline.append(ch,start,length);}
        SAXException problem(String message){return new SAXException(new IllegalArgumentException("「"+label+"」表 "+column(column)+rowNo+"："+message));}
        String convert(){
            String t=type==null?"n":type,v=value.toString();
            switch(t){
                case "s":{int i=parseInt(v.trim(),-1);if(i<0||i>=shared.size())throw new IllegalArgumentException("「"+label+"」表共享文本引用无效");return shared.get(i);}
                case "inlineStr":return inline.toString();
                case "str":return v;
                case "b":return v.trim().equals("1")?"TRUE":"FALSE";
                case "e":return v;
                case "d":dateCells.add(column(column)+rowNo);return isoDate(v.trim());
                default:
                    String s=v.trim();if(s.isEmpty())return "";
                    try{BigDecimal number=new BigDecimal(s);if(style>=0&&style<date.length&&date[style]){dateCells.add(column(column)+rowNo);return serial(number,date1904);}return plain(number);}catch(NumberFormatException e){return v;}
            }
        }
        List<List<String>> result(){
            List<List<String>> list=new ArrayList<>();if(rows.isEmpty())return list;
            int last=rows.lastKey();for(int i=1;i<=last;i++){List<String> row=rows.get(i);list.add(row==null?new ArrayList<String>():row);}
            return list;
        }
    }
    private static int parseInt(String s,int fallback){try{return Integer.parseInt(s.trim());}catch(RuntimeException e){return fallback;}}
    private static int columnOf(String ref,int fallback){
        int n=0,i=0;for(;i<ref.length();i++){char c=ref.charAt(i);if(c>='A'&&c<='Z')n=n*26+(c-'A'+1);else if(c>='a'&&c<='z')n=n*26+(c-'a'+1);else break;}
        return i==0?fallback:n-1;
    }
    static String plain(BigDecimal number){
        BigDecimal rounded=number.round(new MathContext(15)).stripTrailingZeros();if(rounded.scale()<0)rounded=rounded.setScale(0);return rounded.toPlainString();
    }
    /** Excel serial day number to 2026/10/21 or 2026/10/21 12:06 (when the day has a fraction); time-only values become 12:06. */
    static String serial(BigDecimal number,boolean date1904){
        try{
            long days=number.setScale(0,java.math.RoundingMode.FLOOR).longValueExact();
            long minutes=number.subtract(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(1440)).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
            if(minutes>=1440){days++;minutes-=1440;}
            LocalDate base=date1904?LocalDate.of(1904,1,1):days<61?LocalDate.of(1899,12,31):LocalDate.of(1899,12,30);
            String time=String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60);
            if(days==0&&!date1904)return time;
            String day=Ledger.csvDate(base.plusDays(days));
            return minutes==0?day:day+" "+time;
        }catch(RuntimeException e){return plain(number);}
    }
    private static String isoDate(String v){
        try{if(v.length()>10&&v.charAt(10)=='T'){LocalDateTime t=LocalDateTime.parse(v.length()>19?v.substring(0,19):v);return t.toLocalTime().equals(LocalTime.MIDNIGHT)?Ledger.csvDate(t.toLocalDate()):Ledger.csvMinute(t);}return Ledger.csvDate(LocalDate.parse(v));}
        catch(RuntimeException e){return v;}
    }
    private Xlsx(){}
}
