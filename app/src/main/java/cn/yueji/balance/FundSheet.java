package cn.yueji.balance;

import java.time.*;
import java.util.*;

/**
 * Fund purchases as an .xlsx workbook (sheet 基金): 基金名称,基金代码,购入时间,购入金额,手续费. Times are Beijing time written
 * as 2026/10/21 12:06. Only the exact xlsx template is accepted.
 */
public final class FundSheet {
    public static final String HEADER="基金名称,基金代码,购入时间,购入金额,手续费";
    private static final String[] COLUMNS={"基金名称","基金代码","购入时间","购入金额","手续费"};

    public static final class Row {public int line;public String where="";public String name,code;public LocalDateTime at;public long amount,fee;public long sourceAccountId;public boolean sourceAccountLinked;}
    public static final class Plan {public final List<Row> add=new ArrayList<>();public final Set<String> codes=new LinkedHashSet<>();public int skipped;}

    private static Xlsx.Sheet sheet(){return new Xlsx.Sheet("基金").widths(30,12,20,14,12).money(3,4).add(COLUMNS);}

    /** Every purchase batch still held by a fund that is on the home page. Sold batches and deleted funds are not part of this file. */
    public static byte[] exportXlsx(Ledger.Data d){
        Xlsx.Sheet sheet=sheet();
        for(Ledger.Fund f:d.funds){
            if(f.removed()||f.hidden)continue;
            List<Ledger.FundBuy> buys=new ArrayList<>(f.buys);buys.sort(Comparator.comparing((Ledger.FundBuy b)->b.at).thenComparingLong(b->b.id));
            for(Ledger.FundBuy b:buys){
                if(b.saleDate!=null)continue;
                sheet.add(f.name,f.code,Ledger.csvMinute(b.at),Ledger.decimal(b.amount),Ledger.decimal(b.fee));
            }
        }
        return Xlsx.write(Collections.singletonList(sheet));
    }
    /** One sample row plus a 说明 sheet (ignored on import). */
    public static byte[] templateXlsx(){
        Xlsx.Sheet sheet=sheet().add("示例基金","000001","2026/10/21 12:06","1000.00","1.50");
        Xlsx.Sheet help=new Xlsx.Sheet("说明").widths(110).add("填写说明（本表不会被导入）");
        help.add("每行是一笔买入：基金名称、6位基金代码、购入时间（北京时间，精确到分钟）、购入金额（含手续费）、手续费（可留空）。");
        help.add("购入时间写成 2026/10/21 12:06（日期与时分之间一个空格），不能晚于现在；这一列已设为“文本”格式，直接输入即可。");
        help.add("请先删除示例行。表名、第一行完整表头、列数和顺序不能修改；代码必须原样填写6位数字，不自动补零。基金名称必填，联网成功后以行情名称为准。");
        help.add("导入时联网读取历史净值计算份额，不修改账户余额，也不生成资金划转记录；与已有买入完全相同（同一分钟、金额、手续费）的行会跳过。");
        return Xlsx.write(Arrays.asList(sheet,help));
    }

    public static List<Row> parse(byte[] file){
        Xlsx.Sheet fund=null;
        for(Xlsx.Sheet sheet:SheetSchema.read(file,Collections.singleton("基金"))){
            if(sheet.name.equals("说明"))continue;
            SheetSchema.header(sheet,HEADER);fund=sheet;
        }
        if(fund==null)throw new IllegalArgumentException("没有找到“基金”表，请使用软件导出的基金模板");
        return parseRows(fund,"「基金」表");
    }
    public static List<Row> parse(String text){throw new IllegalArgumentException("仅支持 .xlsx 模板，不支持 CSV 或文本");}
    private static boolean blank(List<String> cells){for(String s:cells)if(!s.trim().isEmpty())return false;return true;}

    private static List<Row> parseRows(Xlsx.Sheet sheet,String sheetLabel){
        List<List<String>> rows=sheet.rows;
        Map<String,Integer> columns=null;List<Row> result=new ArrayList<>();
        for(int i=0;i<rows.size();i++){
            List<String> cells=rows.get(i);if(blank(cells))continue;
            String where=sheetLabel+"第"+(i+1)+"行";
            if(columns==null){
                columns=new HashMap<>();for(int c=0;c<cells.size();c++)columns.put(cells.get(c).trim(),c);
                for(String name:COLUMNS)if(!columns.containsKey(name))throw new IllegalArgumentException(where+"：表头须为 "+HEADER);
                continue;
            }
            try{
                SheetSchema.width(cells,COLUMNS.length);
                Row row=new Row();row.line=i+1;row.where=where;row.name=cell(cells,columns,"基金名称");row.code=cell(cells,columns,"基金代码");
                if(row.name.isEmpty())throw new IllegalArgumentException("请填写基金名称");
                if(row.name.length()>80)throw new IllegalArgumentException("基金名称不能超过80个字");
                if(!row.code.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码为6位数字："+row.code);
                String time=cell(cells,columns,"购入时间");
                if(sheet.dateCells.contains("C"+(i+1))&&time.matches("\\d{4}/\\d{1,2}/\\d{1,2}"))time+=" 00:00";
                row.at=Ledger.parseCsvMinute(time);
                if(row.at.isAfter(FundEngine.now()))throw new IllegalArgumentException("购入时间不能在未来（北京时间）："+Ledger.csvMinute(row.at));
                row.amount=Ledger.parseMoney(cell(cells,columns,"购入金额"),true);
                String fee=cell(cells,columns,"手续费");row.fee=fee.isEmpty()?0:Ledger.parseMoney(fee,false);
                if(row.fee>=row.amount)throw new IllegalArgumentException("手续费须小于购入金额");
                result.add(row);
            }catch(IllegalArgumentException e){throw new IllegalArgumentException(where+"："+e.getMessage());}
        }
        if(columns==null)throw new IllegalArgumentException("表格为空，第一行须为 "+HEADER);
        if(result.isEmpty())throw new IllegalArgumentException("没有基金数据行");
        return result;
    }
    private static String cell(List<String> cells,Map<String,Integer> columns,String name){int i=columns.get(name);return i<cells.size()?cells.get(i).trim():"";}

    /** Rows matching an existing purchase (same minute, amount and fee) are skipped, one for one. */
    public static Plan plan(List<Row> rows,Ledger.Data d){
        Plan plan=new Plan();Map<String,List<Ledger.FundBuy>> pools=new HashMap<>();
        for(Row row:rows){
            String where=row.where.isEmpty()?"第"+row.line+"行":row.where;
            Ledger.Fund fund=find(d,row.code);
            if(fund!=null){
                if(fund.removed())throw new IllegalArgumentException(where+"：基金 "+row.code+" 已从首页删除，请先在累计收益中恢复或永久删除");
                if(fund.buys.isEmpty())throw new IllegalArgumentException(where+"：基金 "+row.code+" 是旧版份额持仓，需先移除后再导入原始买入");
                List<Ledger.FundBuy> pool=pools.computeIfAbsent(row.code,k->new ArrayList<>(fund.buys));
                Ledger.FundBuy same=null;
                for(Ledger.FundBuy b:pool)if(b.at.withSecond(0).withNano(0).equals(row.at)&&b.amount==row.amount&&b.fee==row.fee){same=b;break;}
                if(same!=null){pool.remove(same);plan.skipped++;continue;}
            }
            plan.add.add(row);plan.codes.add(row.code);
        }
        return plan;
    }

    /** Adds the planned purchases. A code without downloaded NAVs still imports and shows 待数据 until the next refresh. */
    public static int apply(Ledger.Data d,Plan plan,Map<String,FundClient.Quote> quotes,Map<String,String> failures,TradingCalendar calendar){
        Set<Ledger.Fund> touched=new LinkedHashSet<>();int added=0;
        for(Row row:plan.add){
            Ledger.Fund fund=find(d,row.code);
            if(fund==null){
                fund=new Ledger.Fund();fund.id=d.nextId();fund.code=row.code;fund.name=!row.name.isEmpty()?row.name:"基金 "+row.code;d.funds.add(fund);
            }
            FundClient.Quote quote=quotes.get(row.code);
            if(quote!=null){
                fund.name=quote.name;fund.nav=quote.nav;fund.previous=quote.previous;fund.navDate=quote.date;fund.prices.putAll(quote.history);fund.navReturns.putAll(quote.returns);
                fund.dividends.putAll(quote.dividends);fund.splits.putAll(quote.splits);fund.historyComplete=quote.complete;fund.fundType=quote.fundType;fund.updatedAt=FundEngine.now().toString();fund.error="";
            }else if(fund.error.isEmpty()&&fund.prices.isEmpty())fund.error="净值待获取："+failures.getOrDefault(row.code,"未联网");
            Ledger.FundBuy buy=new Ledger.FundBuy();buy.id=d.nextId();buy.amount=row.amount;buy.fee=row.fee;buy.at=row.at;buy.buyAccountLinked=row.sourceAccountLinked;buy.buyAccountId=row.sourceAccountLinked?row.sourceAccountId:0;fund.buys.add(buy);added++;touched.add(fund);
        }
        for(Ledger.Fund f:touched)FundEngine.resolve(f,calendar);
        return added;
    }
    static Ledger.Fund find(Ledger.Data d,String code){for(Ledger.Fund f:d.funds)if(f.code.equals(code))return f;return null;}
    private FundSheet(){}
}
