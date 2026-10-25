package cn.yueji.balance;

import java.time.*;
import java.util.*;

/**
 * Strict template-based .xlsx import/export. Each module has its own named sheet and complete ordered header.
 */
public final class HistoryImport {
    public static final String EXPENSE_HEADER="支出类型,时间,金额,名称,账户,备注,报销口径";
    public static final String INCOME_HEADER="收入类别,到账时间,金额,账户,备注";
    public static final String REIMBURSE_HEADER="报销名称,垫付时间,垫付金额,账户,备注,报销状态,到账明细";
    private static final String EXPENSE="expense",INCOME="income",REIMBURSE="reimburse";
    private static final String SUMMARY_NOTE="Yueji汇总 · ";
    private static final int[] EXPENSE_WIDTHS={12,14,14,26,16,32,14},INCOME_WIDTHS={14,14,14,16,32},REIMBURSE_WIDTHS={26,14,14,16,32,12,44};

    public static final class Result {
        public Ledger.Data data;byte[] source;public int months,expenses,incomes,reimbursements,entries,skipped,replaced;
        public String description(){return "月支出 "+months+" 条\n大宗支出 "+expenses+" 条\n收入 "+incomes+" 条\n报销 "+reimbursements+" 条\n重复跳过 "+skipped+" 条\n替换同月总额 "+replaced+" 条";}
    }

    /** Parses into a copy of the ledger and validates it; nothing is written until the caller commits. */
    /** CSV/text input is deliberately unsupported, even when renamed to .xlsx. */
    public static Result preview(String text,Ledger.Data original){throw new IllegalArgumentException("仅支持 .xlsx 模板，不支持 CSV 或文本");}
    public static Result preview(byte[] file,Ledger.Data original){
        Ledger.Data copy=LedgerStore.decode(LedgerStore.encode(original));
        Result result=importInto(copy,file);result.source=file.clone();LedgerStore.validate(copy);return result;
    }

    /** Keeps validation errors tied to the original worksheet and row. */
    static final class Block {
        final String label;final List<List<String>> rows;
        Block(String label,List<List<String>> rows){this.label=label;this.rows=rows;}
        String where(int index){return "「"+label+"」表第"+(index+1)+"行";}
    }
    public static Result importInto(Ledger.Data d,String text){throw new IllegalArgumentException("仅支持 .xlsx 模板，不支持 CSV 或文本");}
    public static Result importInto(Ledger.Data d,byte[] file){
        Result result=new Result();result.data=d;List<Ledger.Entry> existing=new ArrayList<>(d.entries);boolean used=false;
        Set<YearMonth> months=new HashSet<>();
        for(Xlsx.Sheet sheet:SheetSchema.read(file,new HashSet<>(Arrays.asList("支出","收入","报销")))){
            if(sheet.name.equals("说明"))continue;
            used=true;String module=sheet.name.equals("支出")?EXPENSE:sheet.name.equals("收入")?INCOME:REIMBURSE;
            Map<String,Integer> columns=SheetSchema.header(sheet,module.equals(EXPENSE)?EXPENSE_HEADER:module.equals(INCOME)?INCOME_HEADER:REIMBURSE_HEADER);
            Block block=new Block(sheet.name,sheet.rows);
            for(int i=1;i<block.rows.size();i++){
                List<String> row=block.rows.get(i);if(blank(row))continue;
                try{
                    SheetSchema.width(row,columns.size());
                    if(module.equals(EXPENSE)&&get(row,columns,"支出类型").equals("月支出")){
                        String time=get(row,columns,"时间");
                        if(time.matches("\\d{4}/\\d{1,2}/\\d{1,2}")){
                            if(!sheet.dateCells.contains("B"+(i+1)))throw new IllegalArgumentException("月份须写成2026/10；累计截止时间请写完整时分，如2026/10/16 12:00");
                            row.set(columns.get("时间"),Ledger.csvMonth(Ledger.parseCsvMonth(time)));
                        }
                        YearMonth month=historyMonth(get(row,columns,"时间"));
                        if(!months.add(month))throw new IllegalArgumentException("同一月份在文件中只能填写一次："+Ledger.csvMonth(month));
                    }
                    if(module.equals(EXPENSE))expenseRow(d,result,columns,row,existing);
                    else if(module.equals(INCOME))incomeRow(d,result,columns,row,existing);
                    else reimburseRow(d,result,columns,row,existing);
                }catch(IllegalArgumentException|ArithmeticException e){throw new IllegalArgumentException(block.where(i)+"："+e.getMessage());}
            }
        }
        if(!used)throw new IllegalArgumentException("没有找到可导入的表：请使用支出、收入、报销模板");
        result.entries=result.expenses+result.incomes+result.reimbursements;
        if(result.months+result.entries+result.skipped==0)throw new IllegalArgumentException("表头下方没有可导入的数据行");
        return result;
    }

    private static void expenseRow(Ledger.Data d,Result r,Map<String,Integer> c,List<String> row,List<Ledger.Entry> existing){
        String type=get(row,c,"支出类型");
        if(type.equals("月支出")){
            SheetSchema.empty(row,c,"名称","账户");
            String time=get(row,c,"时间");YearMonth month=historyMonth(time);
            if(month.isAfter(YearMonth.now()))throw new IllegalArgumentException("月份不能晚于当前月份："+Ledger.csvMonth(month));
            long amount=Ledger.parseMoney(get(row,c,"金额"),false);String basis=get(row,c,"报销口径");
            if(!basis.equals("已排除报销")&&!basis.equals("包含报销垫付"))throw new IllegalArgumentException("报销口径填写已排除报销或包含报销垫付");
            boolean includes=basis.equals("包含报销垫付");LocalDateTime end=historyEnd(time);
            if(end.isAfter(LocalDateTime.now())&&!end.equals(month.plusMonths(1).atDay(1).atStartOfDay()))throw new IllegalArgumentException("历史数据截止时间不能在未来");
            if(get(row,c,"备注").startsWith(SUMMARY_NOTE)){
                Ledger.Report current=Ledger.month(d,month);
                // A report already includes snapshot deltas; importing it as the historical baseline would count them twice.
                if(!includes&&current.usable()&&current.expense==amount){r.skipped++;return;}
                boolean snapshots=false;for(Ledger.Snapshot s:d.snapshots)if(YearMonth.from(s.at).equals(month))snapshots=true;
                if(current.hasData()&&current.start!=null||snapshots)
                    throw new IllegalArgumentException("此行为导出的月汇总，现有余额更新与汇总不一致；请使用原始历史月支出模板，完整恢复请用 JSON 备份，避免重复累计");
            }
            Ledger.HistoryMonth old=null;for(Ledger.HistoryMonth h:d.history)if(h.month.equals(month))old=h;
            if(old!=null){
                if(old.expense==amount&&old.cutoff.equals(end)&&old.includesAdvances==includes){r.skipped++;return;}
                d.history.remove(old);r.replaced++;
            }
            Ledger.HistoryMonth h=new Ledger.HistoryMonth(month,amount,end);h.includesAdvances=includes;d.history.add(h);r.months++;
        }else if(type.equals("大宗支出")){
            SheetSchema.empty(row,c,"报销口径");
            LocalDate day=day(get(row,c,"时间"));long amount=Ledger.parseMoney(get(row,c,"金额"),true);String name=text(get(row,c,"名称"),100,"名称");
            if(name.isEmpty())throw new IllegalArgumentException("请填写名称");
            Ledger.Entry e=new Ledger.Entry(d.nextId(),false,amount,day.atStartOfDay(),name,text(get(row,c,"备注"),1000,"备注"),account(d,get(row,c,"账户")));e.kind=Ledger.LARGE;
            if(add(d,r,existing,e))r.expenses++;
        }else throw new IllegalArgumentException("支出类型仅支持 月支出 或 大宗支出");
    }

    static YearMonth historyMonth(String time){return YearMonth.from(historyEnd(time).minusNanos(time.matches("\\d{4}/\\d{1,2}")?1:0));}
    static LocalDateTime historyEnd(String time){
        if(time.matches("\\d{4}/\\d{1,2}"))return Ledger.parseCsvMonth(time).plusMonths(1).atDay(1).atStartOfDay();
        return Ledger.parseCsvMinute(time);
    }
    private static void incomeRow(Ledger.Data d,Result r,Map<String,Integer> c,List<String> row,List<Ledger.Entry> existing){
        LocalDate day=day(get(row,c,"到账时间"));long amount=Ledger.parseMoney(get(row,c,"金额"),true);
        String category=text(get(row,c,"收入类别"),30,"收入类别");if(category.isEmpty())throw new IllegalArgumentException("请填写收入类别");
        Ledger.Entry e=new Ledger.Entry(d.nextId(),true,amount,day.atStartOfDay(),category,text(get(row,c,"备注"),1000,"备注"),account(d,get(row,c,"账户")));e.category=category;
        if(add(d,r,existing,e)){r.incomes++;if(!d.categories.contains(category))LabelRules.add(d,category,LabelRules.firstFree(d,null));}
    }

    private static void reimburseRow(Ledger.Data d,Result r,Map<String,Integer> c,List<String> row,List<Ledger.Entry> existing){
        String name=text(get(row,c,"报销名称"),100,"报销名称");if(name.isEmpty())throw new IllegalArgumentException("请填写报销名称");
        LocalDate day=day(get(row,c,"垫付时间"));long amount=Ledger.parseMoney(get(row,c,"垫付金额"),true);
        Ledger.Entry e=new Ledger.Entry(d.nextId(),false,amount,day.atStartOfDay(),name,text(get(row,c,"备注"),1000,"备注"),account(d,get(row,c,"账户")));e.kind=Ledger.REIMBURSE;
        long got=0;
        for(String part:get(row,c,"到账明细").split(";")){
            if(part.trim().isEmpty())continue;String[] pair=part.trim().split("=");
            if(pair.length!=2)throw new IllegalArgumentException("到账明细格式：2026/10/21=200.00;2026/11/05=100.00");
            LocalDate when=day(pair[0]);long money=Ledger.parseMoney(pair[1],true);
            if(when.isBefore(day))throw new IllegalArgumentException("到账日期不能早于垫付日期："+pair[0].trim());
            got=Math.addExact(got,money);e.receipts.add(new Ledger.Receipt(money,when.atStartOfDay()));
        }
        if(got>amount)throw new IllegalArgumentException("到账合计不能超过垫付金额");
        String derived=e.reimbursementStatus(),status=get(row,c,"报销状态");
        if(!status.isEmpty()){
            if(!Arrays.asList("待报销","部分报销","已报销").contains(status))throw new IllegalArgumentException("报销状态填写待报销、部分报销或已报销");
            if(!status.equals(derived))throw new IllegalArgumentException("报销状态应为“"+derived+"”，须与到账明细一致");
        }
        e.status=derived;if(add(d,r,existing,e))r.reimbursements++;
    }

    /** Identical rows already in the ledger are consumed one by one, so repeated rows inside one file are all kept. */
    private static boolean add(Ledger.Data d,Result r,List<Ledger.Entry> existing,Ledger.Entry e){
        for(Iterator<Ledger.Entry> it=existing.iterator();it.hasNext();)if(same(it.next(),e)){it.remove();r.skipped++;return false;}
        d.entries.add(e);return true;
    }
    private static boolean same(Ledger.Entry a,Ledger.Entry b){
        if(!a.kind.equals(b.kind)||!a.at.toLocalDate().equals(b.at.toLocalDate())||a.amount!=b.amount||!a.title.equals(b.title)||!a.category.equals(b.category)||a.accountId!=b.accountId||!a.note.equals(b.note))return false;
        return !Ledger.REIMBURSE.equals(a.kind)||a.reimbursementStatus().equals(b.reimbursementStatus())&&receiptKeys(a).equals(receiptKeys(b));
    }
    private static List<String> receiptKeys(Ledger.Entry e){
        List<String> keys=new ArrayList<>();
        if(e.receipts.isEmpty()){if(e.received>0&&e.receivedAt!=null)keys.add(e.receivedAt.toLocalDate()+"="+e.received);}
        else for(Ledger.Receipt r:e.receipts)keys.add(r.at.toLocalDate()+"="+r.amount);
        Collections.sort(keys);return keys;
    }
    private static LocalDate day(String s){
        LocalDate day=Ledger.parseCsvDate(s);if(day.isAfter(LocalDate.now()))throw new IllegalArgumentException("日期不能晚于今天："+s.trim());return day;
    }
    private static String text(String s,int limit,String field){if(s.length()>limit)throw new IllegalArgumentException(field+"不能超过"+limit+"个字");return s;}
    private static long account(Ledger.Data d,String name){
        if(name.isEmpty())return 0;
        for(Ledger.Account a:d.accounts)if(a.name.equals(name)&&a.deletedAt==null)return a.id;
        for(Ledger.Account a:d.accounts)if(a.name.equals(name))return a.id;
        throw new IllegalArgumentException("账户不存在，可留空："+name);
    }

    private static boolean blank(List<String> row){for(String s:row)if(!s.trim().isEmpty())return false;return true;}
    private static String get(List<String> row,int index){return index<row.size()?row.get(index).trim():"";}
    private static String get(List<String> row,Map<String,Integer> columns,String name){Integer i=columns.get(name);return i==null?"":get(row,i);}

    // ---------------------------------------------------------------- export and template
    /** Exports the chosen kinds for [from,to] (inclusive, by date) as a workbook. Monthly rows cover every month the range touches. */
    public static byte[] exportXlsx(Ledger.Data d,LocalDate from,LocalDate to,boolean expense,boolean income,boolean reimburse){
        if(from.isAfter(to))throw new IllegalArgumentException("起始日期不能晚于终止日期");
        List<Xlsx.Sheet> sheets=new ArrayList<>();
        if(expense)sheets.add(expenseSheet(d,from,to));
        if(income)sheets.add(incomeSheet(d,from,to));
        if(reimburse)sheets.add(reimburseSheet(d,from,to));
        if(sheets.isEmpty())throw new IllegalArgumentException("请至少勾选一种数据");
        return Xlsx.write(sheets);
    }
    private static List<Ledger.Entry> inRange(Ledger.Data d,String kind,LocalDate from,LocalDate to){
        List<Ledger.Entry> list=new ArrayList<>();
        for(Ledger.Entry e:d.entries){LocalDate day=e.at.toLocalDate();if(e.kind.equals(kind)&&!day.isBefore(from)&&!day.isAfter(to))list.add(e);}
        list.sort(Comparator.comparing((Ledger.Entry e)->e.at).thenComparingLong(e->e.id));return list;
    }
    private static String accountLabel(Ledger.Data d,long id){return id==0?"":d.accountName(id);}
    private static Xlsx.Sheet expenseSheet(Ledger.Data d,LocalDate from,LocalDate to){
        Xlsx.Sheet sheet=new Xlsx.Sheet("支出").widths(EXPENSE_WIDTHS).money(2).add(EXPENSE_HEADER.split(","));
        for(YearMonth m=YearMonth.from(from);!m.isAfter(YearMonth.from(to));m=m.plusMonths(1)){
            Ledger.Report r=Ledger.month(d,m);if(!r.usable())continue;
            Ledger.HistoryMonth month=null;for(Ledger.HistoryMonth x:d.history)if(x.month.equals(m))month=x;
            LocalDate cut=month==null?r.through.toLocalDate():Ledger.historyCutoff(d,month).toLocalDate();
            sheet.add("月支出",Ledger.csvMonth(m),Ledger.decimal(r.expense),"","",SUMMARY_NOTE+(r.closed?"整月汇总":"截至"+Ledger.csvDate(cut)+"累计"),"已排除报销");
        }
        for(Ledger.Entry e:inRange(d,Ledger.LARGE,from,to))sheet.add("大宗支出",Ledger.csvDate(e.at.toLocalDate()),Ledger.decimal(e.amount),e.title,accountLabel(d,e.accountId),e.note,"");
        return sheet;
    }
    private static Xlsx.Sheet incomeSheet(Ledger.Data d,LocalDate from,LocalDate to){
        Xlsx.Sheet sheet=new Xlsx.Sheet("收入").widths(INCOME_WIDTHS).money(2).add(INCOME_HEADER.split(","));
        for(Ledger.Entry e:inRange(d,Ledger.INCOME,from,to))sheet.add(e.category,Ledger.csvDate(e.at.toLocalDate()),Ledger.decimal(e.amount),accountLabel(d,e.accountId),e.note);
        return sheet;
    }
    private static Xlsx.Sheet reimburseSheet(Ledger.Data d,LocalDate from,LocalDate to){
        Xlsx.Sheet sheet=new Xlsx.Sheet("报销").widths(REIMBURSE_WIDTHS).money(2).add(REIMBURSE_HEADER.split(","));
        for(Ledger.Entry e:inRange(d,Ledger.REIMBURSE,from,to)){
            List<Ledger.Receipt> receipts=new ArrayList<>(e.receipts);
            if(receipts.isEmpty()&&e.received>0&&e.receivedAt!=null)receipts.add(new Ledger.Receipt(e.received,e.receivedAt));
            receipts.sort(Comparator.comparing((Ledger.Receipt x)->x.at));
            StringBuilder detail=new StringBuilder();for(Ledger.Receipt x:receipts){if(detail.length()>0)detail.append(';');detail.append(Ledger.csvDate(x.at.toLocalDate())).append('=').append(Ledger.decimal(x.amount));}
            sheet.add(e.title,Ledger.csvDate(e.at.toLocalDate()),Ledger.decimal(e.amount),accountLabel(d,e.accountId),e.note,e.reimbursementStatus(),detail.toString());
        }
        return sheet;
    }
    /** Example workbook: three sheets with sample rows, plus a 说明 sheet the importer ignores. */
    public static byte[] templateXlsx(){
        Xlsx.Sheet expense=new Xlsx.Sheet("支出").widths(EXPENSE_WIDTHS).money(2).add(EXPENSE_HEADER.split(","));
        expense.add("月支出","2025/01","2500.00","","","整月汇总","已排除报销").add("大宗支出","2025/01/12","4000.00","电脑","","","");
        Xlsx.Sheet income=new Xlsx.Sheet("收入").widths(INCOME_WIDTHS).money(2).add(INCOME_HEADER.split(","));income.add("助研费","2025/01/10","1500.00","","");
        Xlsx.Sheet reimburse=new Xlsx.Sheet("报销").widths(REIMBURSE_WIDTHS).money(2).add(REIMBURSE_HEADER.split(","));reimburse.add("出差","2025/01/15","600.00","","","已报销","2025/02/10=600.00");
        Xlsx.Sheet help=new Xlsx.Sheet("说明").widths(110).add("填写说明（本表不会被导入）");
        help.add("仅支持 xlsx。请先删除示例行再填写；表名、第一行表头、列数和列顺序必须与模板一致，不需要的数据表可以整表删除。");
        help.add("日期写成 2026/10/21，月份写成 2026/10。这些列已设为“文本”格式，直接输入即可，Excel 不会把它改成日期。");
        help.add("月支出：时间写2026/10表示整月/自动衔接，写2026/10/16 12:00表示截至该时刻的累计；该时刻之前的余额更新不再参与后续差额。大宗支出写日期，名称必填。");
        help.add("收入表：第一列是收入类别，账本里没有的类别会自动新增。");
        help.add("报销表：到账明细写成 2025/02/10=200.00;2025/03/10=400.00，多次到账用英文分号隔开；报销状态可留空，由到账明细推导。");
        help.add("账户列可留空；填写时必须与软件里的账户名称完全一致。已有的相同记录导入时会自动跳过。");
        return Xlsx.write(Arrays.asList(expense,income,reimburse,help));
    }

    private HistoryImport(){}
}
