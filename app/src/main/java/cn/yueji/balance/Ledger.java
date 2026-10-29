package cn.yueji.balance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Monetary amounts are cents. Cutoffs use [start,end), in local wall time. */
public final class Ledger {
 public static final long MAX_MONEY=9_000_000_000_000L;
 public static final String INCOME="income", LARGE="large", REIMBURSE="reimburse", INVEST_OUT="invest_out", INVEST_IN="invest_in";
 public static final String[] ICONS={"🎓","📚","👪","🔬","💰","💼","🏆","🧑‍🏫","🧳","💸","🧧","🎁","✍️","📖","📋","🤝","📈","💬","🎤","🏠","🏦","💹","🪙","🪪","👴","🛡️","🧾","💳","💻","🎨","🎵"};
 public static final String[] ICON_NAMES={"奖学金","助学金","生活费","助研费","其他收入","工资","奖金","家教","差旅补贴","转账","红包","礼金","稿酬","版税","项目报酬","佣金","提成","咨询费","讲课费","租金","利息","分红","投资收益","补助","养老金","保险金","税费补助","收款","技术报酬","设计报酬","演出报酬"};
 public static final Set<String> ICON_SET=new LinkedHashSet<>(Arrays.asList(ICONS));
 public static final class Account {
  public long id,opening; public String name,type; public LocalDateTime created;
  public LocalDateTime deletedAt;
  public Account(long id,String name,String type){this.id=id;this.name=name;this.type=type;}
 }
 public static final class Snapshot {
  public boolean partial;
  public boolean cashGenerated;
  public final Set<Long> cashAccounts=new LinkedHashSet<>();
  public long id; public LocalDateTime at; public String note;
  public final Map<Long,Long> balances=new LinkedHashMap<>();
  public Snapshot(long id,LocalDateTime at,String note){this.id=id;this.at=at;this.note=note;}
  public long total(){long v=0;for(long b:balances.values())v=Math.addExact(v,b);return v;}
  public boolean isMonthStart(){return at.getDayOfMonth()==1&&at.toLocalTime().equals(LocalTime.MIDNIGHT);}
 }
 public static final class Entry {
  public long id,amount,accountId,received; public boolean income;
  public boolean cashLinked;
  public String kind,title,note,category="",status="待报销"; public LocalDateTime at,receivedAt;
  public final List<Receipt> receipts=new ArrayList<>();
  public Entry(long id,boolean income,long amount,LocalDateTime at,String title,String note,long accountId){this.id=id;this.income=income;this.kind=income?INCOME:LARGE;this.amount=amount;this.at=at;this.title=title;this.note=note;this.accountId=accountId;}
  public long receivedTotal(){long total=0;for(Receipt r:receipts)total=Math.addExact(total,r.amount);return receipts.isEmpty()?received:total;}
  public String reimbursementStatus(){long total=receivedTotal();return total==0?"待报销":total==amount?"已报销":"部分报销";}
 }
 public static final class Receipt {
  public long amount,accountId;public LocalDateTime at;
  public boolean cashLinked;
  public Receipt(long amount,LocalDateTime at){this.amount=amount;this.at=at;}
 }
 public static final class HistoryMonth {
  public long importId;
  public YearMonth month; public long expense; public LocalDateTime cutoff; public boolean includesAdvances;
  public HistoryMonth(YearMonth m,long e,LocalDateTime c){month=m;expense=e;cutoff=c;}
  public boolean full(){return cutoff.equals(month.plusMonths(1).atDay(1).atStartOfDay());}
 }
 public static final class Fund {
  public long id,cost; public String code,name,navDate="",error="",tag="";
  /** Hidden funds stay in cumulative history, not the home list or totals. */
  public boolean hidden;
  /** Set when the fund is deleted from the home page; profit stops growing after frozenOn (the last NAV date known then). */
  public LocalDateTime removedAt;public LocalDate frozenOn;
  public boolean removed(){return removedAt!=null;}
  public BigDecimal shares=BigDecimal.ZERO,nav=BigDecimal.ZERO,previous=BigDecimal.ZERO;
  public final List<FundBuy> buys=new ArrayList<>();
  public final TreeMap<LocalDate,BigDecimal> prices=new TreeMap<>();
  public final TreeMap<LocalDate,BigDecimal> navReturns=new TreeMap<>();
  public final TreeMap<LocalDate,BigDecimal> dividends=new TreeMap<>(),splits=new TreeMap<>();
  public String fundType="",updatedAt="";public boolean historyComplete,pendingOrder;
  /** Optional recurring investment configuration for ordinary funds. */
  public final RecurringPlan plan=new RecurringPlan();public final FundProfile profile=new FundProfile();
  public long value(){return shares.multiply(nav).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}
  public long profit(){return value()-cost;}
  public long dayProfit(){return shares.multiply(nav.subtract(previous)).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}
 }
 public static final class FundBuy {
  public long id,amount,fee,saleAmount;public LocalDateTime at,saleAt;
  public LocalDate tradeDate,confirmDate,saleDate;public BigDecimal units=BigDecimal.ZERO,price=BigDecimal.ZERO;
  public boolean overrideDates;
  /** Account linkage is opt-in and may be disabled for historical purchases. */
  public boolean buyAccountLinked,saleAccountLinked;
  public long buyAccountId,saleAccountId;
  public boolean accountFlowManaged;
  public long recurringRecordId;
  public long saleOrderId;
  public String transactionTag="",saleTag="";
  public LocalDateTime positionAt;
  public boolean releasePending;
 }
 public static final class RecurringPlan {
  public boolean enabled;
  public long amount,fee,accountId;
  public int dayOfMonth=1;
  public int totalPeriods;
  public String frequency="每月",startDate="",nextDate="";
  public boolean automatic;
  public String firstAt="",nextAt="",nextNominal="",error="";
  public final List<RecurringRecord> records=new ArrayList<>();
 }
 public static final class RecurringRecord {
  public long id,amount,accountId,buyId;
  public LocalDate date;
  public String status="待执行";
 }
 public static final class FundOrder {
  public long id,sourceId,accountId,fee,targetBuyId,netAmount;
  public String targetCode="",targetName="",status="待净值",error="";
  public LocalDateTime at,receivedAt;
  public LocalDate tradeDate,confirmDate;
  public final Set<Long> buyIds=new LinkedHashSet<>();
 }
 public static final class WatchGroup {
  public long id;
  public String name;
  public final LinkedHashSet<String> codes=new LinkedHashSet<>();
  public WatchGroup(long id,String name){this.id=id;this.name=name;}
 }
 public static final class WatchFund {
  public String code,name,navDate="",fundType="";
  public String establishedDate="",scaleAmount="";
  public BigDecimal nav=BigDecimal.ZERO,previous=BigDecimal.ZERO;
  public boolean historyComplete;public int lockDays,buyFeeBasisPoints=-1,managementFeeBasisPoints=-1,custodyFeeBasisPoints=-1,serviceFeeBasisPoints=-1,saleFeeBasisPoints=-1;
  public final TreeMap<LocalDate,BigDecimal> prices=new TreeMap<>();
  public final TreeMap<LocalDate,BigDecimal> returns=new TreeMap<>();
  public final FundProfile profile=new FundProfile();
  public WatchFund(String code,String name){this.code=code;this.name=name;}
 }
 public static final class Settings {
  public int checkDay1=1,checkDay2=15,reminderHour=9;
  public boolean checkNotifications=true,fundNotifications=true,autoRefresh=true,appLockEnabled=false;
  public boolean lossOnly=true;
  public int refreshMinutes=120;
 }
 public static final class Data {
  public final List<DebtLedger.Loan> debts=new ArrayList<>();
  public final List<ImportJournal.Batch> imports=new ArrayList<>();
  public final List<BankLedger.Product> banks=new ArrayList<>();
  public final List<Account> accounts=new ArrayList<>(); public final List<Snapshot> snapshots=new ArrayList<>(); public final List<Entry> entries=new ArrayList<>();
  public final List<HistoryMonth> history=new ArrayList<>();public final List<Fund> funds=new ArrayList<>();
  public final List<WatchGroup> watchGroups=new ArrayList<>();
  public final List<WatchFund> watchFunds=new ArrayList<>();
  public final List<FundOrder> fundOrders=new ArrayList<>();
  public final Set<String> watchAllIncluded=new LinkedHashSet<>();
  public int watchRange=4,watchSort=0;public boolean watchDescending=true;
  public final Settings settings=new Settings();
  public final List<String> categories=new ArrayList<>(Arrays.asList("奖学金","助学金","生活费","助研费","其他"));
  public final Map<String,String> categoryIcons=new LinkedHashMap<>();
  public Data(){ensureIcons();}
  public String iconOf(String category){String icon=categoryIcons.get(category);return icon==null?"●":icon;}
  public void ensureIcons(){
   categoryIcons.keySet().retainAll(categories);Set<String> used=new HashSet<>();
   for(String cat:categories){String icon=categoryIcons.get(cat);if(!ICON_SET.contains(icon)||!used.add(icon))categoryIcons.remove(cat);}
   for(String cat:categories)if(!categoryIcons.containsKey(cat))for(String icon:ICONS)if(used.add(icon)){categoryIcons.put(cat,icon);break;}
  }
  public void sort(){snapshots.sort(Comparator.comparing(s->s.at));entries.sort(Comparator.comparing((Entry e)->e.at).reversed());}
  public Snapshot latest(){return snapshots.isEmpty()?null:snapshots.get(snapshots.size()-1);}
  public String accountName(long id){for(Account a:accounts)if(a.id==id)return a.name;return "未指定账户";}
  public long balance(Account a){for(int i=snapshots.size()-1;i>=0;i--){Long v=snapshots.get(i).balances.get(a.id);if(v!=null)return v;}return a.opening;}
  public long total(){long v=0;for(Account a:accounts)if(a.deletedAt==null)v=Math.addExact(v,balance(a));return v;}
  public List<Account> activeAccounts(){List<Account> list=new ArrayList<>();for(Account a:accounts)if(a.deletedAt==null)list.add(a);return list;}
  public long nextId(){long n=System.currentTimeMillis();for(FundOrder order:fundOrders)n=Math.max(n,order.id+1);for(DebtLedger.Loan loan:debts){n=Math.max(n,loan.id+1);for(DebtLedger.Payment p:loan.payments)n=Math.max(n,p.id+1);}for(ImportJournal.Batch b:imports)n=Math.max(n,b.id+1);for(Account a:accounts)n=Math.max(n,a.id+1);for(Snapshot s:snapshots)n=Math.max(n,s.id+1);for(Entry e:entries)n=Math.max(n,e.id+1);for(WatchGroup g:watchGroups)n=Math.max(n,g.id+1);for(Fund f:funds){n=Math.max(n,f.id+1);for(FundBuy b:f.buys)n=Math.max(n,b.id+1);for(RecurringRecord r:f.plan.records)n=Math.max(n,r.id+1);}for(BankLedger.Product p:banks){n=Math.max(n,p.id+1);for(BankLedger.Observation o:p.observations)n=Math.max(n,o.id+1);for(BankLedger.Event e:p.events)n=Math.max(n,e.id+1);}return n;}
 }
 public static final class Report {
  public final YearMonth month;public Snapshot start,end;public boolean baseline,closed,anomaly,available,imported;public long expense;public int days;public LocalDateTime through;
  public Report(YearMonth m){month=m;}
  public boolean hasData(){return available;}
  public boolean usable(){return available&&!anomaly;}
  public boolean hasDaily(){return usable()&&baseline&&days>0;}
  public long daily(){return BigDecimal.valueOf(expense).divide(BigDecimal.valueOf(days),0,RoundingMode.HALF_UP).longValueExact();}
  public String status(){if(anomaly)return "请核对收入、报销与账户余额";if(!available)return "暂无支出数据";if(start!=null&&end==start&&!imported)return "仅一次余额更新，支出暂为0";if(!baseline)return start==null?"缺少月初基准":"缺少月初基准 · 自"+start.at.toLocalDate()+"更新起累计";if(closed)return "完整月份 · "+days+"天";return "截至 "+through.toLocalDate()+" · "+days+"天";}
 }
 public static long parseMoney(String s,boolean positive){if(s==null||!s.trim().matches("[0-9]+(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("请输入金额，最多两位小数");try{long v=new BigDecimal(s.trim()).movePointRight(2).longValueExact();if(v>MAX_MONEY||(positive&&v==0))throw new IllegalArgumentException("金额超出范围");return v;}catch(ArithmeticException e){throw new IllegalArgumentException("金额超出范围");}}
 /** CSV dates are strict: 2026/10/21, 2026/10 and 2026/10/21 12:06. Month and day may skip the leading zero (Excel does). */
 private static final java.util.regex.Pattern CSV_DATE=java.util.regex.Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2})"),CSV_MONTH=java.util.regex.Pattern.compile("(\\d{4})/(\\d{1,2})"),CSV_MINUTE=java.util.regex.Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2}) (\\d{1,2}):(\\d{2})");
 public static LocalDate parseCsvDate(String s){
  String v=s==null?"":s.trim();if(v.isEmpty())throw new IllegalArgumentException("缺少日期");
  java.util.regex.Matcher m=CSV_DATE.matcher(v);if(!m.matches())throw new IllegalArgumentException("日期须写成 2026/10/21 的格式："+v);
  try{LocalDate d=LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));if(d.getYear()<2000)throw new IllegalArgumentException("年份不能早于2000："+v);return d;}catch(DateTimeException e){throw new IllegalArgumentException("日期不存在："+v);}
 }
 /** A month is 2026/10. A month typed into an Excel General cell becomes the date 2026/10/01, so exactly that form is accepted too; any other day (Excel turns "Aug-22" into 2026/8/22) is refused instead of guessed. */
 public static YearMonth parseCsvMonth(String s){
  String v=s==null?"":s.trim();if(v.isEmpty())throw new IllegalArgumentException("缺少月份");
  java.util.regex.Matcher m=CSV_MONTH.matcher(v),full=CSV_DATE.matcher(v);
  if(!m.matches()&&!full.matches())throw new IllegalArgumentException("月份须写成 2026/10 的格式："+v);
  if(!m.matches()&&Integer.parseInt(full.group(3))!=1)throw new IllegalArgumentException("月份须写成 2026/10 的格式（用日期单元格时须是当月1日，当前是 "+v+"，可能被 Excel 改过）："+v);
  java.util.regex.Matcher use=m.matches()?m:full;
  try{YearMonth ym=YearMonth.of(Integer.parseInt(use.group(1)),Integer.parseInt(use.group(2)));if(ym.getYear()<2000)throw new IllegalArgumentException("年份不能早于2000："+v);return ym;}catch(DateTimeException e){throw new IllegalArgumentException("月份不存在："+v);}
 }
 public static LocalDateTime parseCsvMinute(String s){
  String v=s==null?"":s.trim();if(v.isEmpty())throw new IllegalArgumentException("缺少时间");
  java.util.regex.Matcher m=CSV_MINUTE.matcher(v);if(!m.matches())throw new IllegalArgumentException("时间须写成 2026/10/21 12:06（日期与时分之间一个空格）："+v);
  try{LocalDateTime t=LocalDateTime.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)));if(t.getYear()<2000)throw new IllegalArgumentException("年份不能早于2000："+v);return t;}catch(DateTimeException e){throw new IllegalArgumentException("时间不存在："+v);}
 }
 public static String csvDate(LocalDate d){return String.format(Locale.ROOT,"%04d/%02d/%02d",d.getYear(),d.getMonthValue(),d.getDayOfMonth());}
 public static String csvMonth(YearMonth m){return String.format(Locale.ROOT,"%04d/%02d",m.getYear(),m.getMonthValue());}
 public static String csvMinute(LocalDateTime t){return csvDate(t.toLocalDate())+String.format(Locale.ROOT," %02d:%02d",t.getHour(),t.getMinute());}
 public static String decimal(long v){return BigDecimal.valueOf(v,2).toPlainString();}
 public static boolean between(LocalDateTime at,LocalDateTime a,LocalDateTime b){return at!=null&&!at.isBefore(a)&&at.isBefore(b);}
 public static long expenseBetween(Snapshot a,Snapshot b,Data d){
  long total=Math.subtractExact(a.total(),b.total());
  // Added capital establishes a baseline; income is global and independent of account labels.
  for(Account x:d.accounts)if(!a.balances.containsKey(x.id)&&b.balances.containsKey(x.id))total=Math.addExact(total,x.opening);
  for(Account x:d.accounts)if(x.deletedAt!=null&&a.balances.containsKey(x.id)&&!b.balances.containsKey(x.id))total=Math.subtractExact(total,a.balances.get(x.id));
  for(Entry e:d.entries){if(AccountCash.between(e.at,a.at,b.at,e.cashLinked)){if(INCOME.equals(e.kind)||INVEST_IN.equals(e.kind))total=Math.addExact(total,e.amount);if(REIMBURSE.equals(e.kind)||INVEST_OUT.equals(e.kind))total=Math.subtractExact(total,e.amount);}
   if(REIMBURSE.equals(e.kind)){
    if(e.receipts.isEmpty()){if(e.received>0&&between(e.receivedAt,a.at,b.at))total=Math.addExact(total,e.received);}
    else for(Receipt receipt:e.receipts)if(AccountCash.between(receipt.at,a.at,b.at,receipt.cashLinked))total=Math.addExact(total,receipt.amount);
   }
  }
  for(InvestmentCash.Flow flow:InvestmentCash.flows(d))if(AccountCash.between(flow.at,a.at,b.at,true))total=Math.addExact(total,flow.delta);
  return total;
 }
 public static long expenseBetween(Snapshot a,Snapshot b,List<Entry> entries){Data d=new Data();d.entries.addAll(entries);for(Map.Entry<Long,Long> v:b.balances.entrySet()){Account x=new Account(v.getKey(),"","");x.opening=v.getValue();d.accounts.add(x);}return expenseBetween(a,b,d);}
 /** The newest imported month is read as "spent until the first check" when that check falls inside the same month after its first instant; a check at 00:00 on the 1st leaves nothing to bridge, so the figure stays the whole month. */
 public static LocalDateTime historyCutoff(Data d,HistoryMonth h){
  if(!h.full())return h.cutoff;
  YearMonth newest=null;for(HistoryMonth x:d.history)if(newest==null||x.month.isAfter(newest))newest=x.month;
  if(!h.month.equals(newest))return h.cutoff;
  LocalDateTime first=null;for(Snapshot s:d.snapshots)if(!s.cashGenerated&&YearMonth.from(s.at).equals(h.month)&&(first==null||s.at.isBefore(first)))first=s.at;
  return first!=null&&first.isAfter(h.month.atDay(1).atStartOfDay())?first:h.cutoff;
 }
 /** An account first counted by a check dated before it was added starts at that check, with that balance as its baseline. */
 public static void pullAccountsBack(Data d,Snapshot s){
  for(Account a:d.accounts){Long v=s.balances.get(a.id);if(v!=null&&(a.created==null||s.at.isBefore(a.created))){a.created=s.at;a.opening=v;}}
 }
 public static Report month(Data d,YearMonth ym){
  return MonthLedger.calculate(d,ym).report;
 }
 public static long income(Data d,int year){long v=0;for(Entry e:d.entries)if(INCOME.equals(e.kind)&&e.at.getYear()==year)v=Math.addExact(v,e.amount);return v;}
 /** Income totals per category in the user's persisted order. */
 public static LinkedHashMap<String,Long> incomeCategories(Data d,boolean usedOnly){
  LinkedHashMap<String,Long> sums=new LinkedHashMap<>();
  for(String s:d.categories)sums.put(s,0L);
  for(Entry e:d.entries)if(INCOME.equals(e.kind))sums.merge(e.category,e.amount,Long::sum);
  LinkedHashMap<String,Long> result=new LinkedHashMap<>();
  if(!usedOnly){result.putAll(sums);return result;}
  List<Map.Entry<String,Long>> list=new ArrayList<>();
  for(Map.Entry<String,Long> e:sums.entrySet())if(e.getValue()>0)list.add(e);
  for(Map.Entry<String,Long> e:list)result.put(e.getKey(),e.getValue());
  return result;
 }
 public static long income(Data d,YearMonth ym){long v=0;for(Entry e:d.entries)if(INCOME.equals(e.kind)&&YearMonth.from(e.at).equals(ym))v+=e.amount;return v;}
 public static Long yearExpense(Data d,int y){long v=0;boolean found=false;for(int m=1;m<=12;m++){Report r=month(d,YearMonth.of(y,m));if(r.usable()){v=Math.addExact(v,r.expense);found=true;}}return found?v:null;}
 public static List<LocalDate> missingNodes(Data d,LocalDate today){List<LocalDate> list=new ArrayList<>();if(d.snapshots.isEmpty())return list;for(LocalDate day=d.snapshots.get(0).at.toLocalDate();!day.isAfter(today);day=day.plusDays(1)){if(day.getDayOfMonth()!=Math.min(d.settings.checkDay1,day.lengthOfMonth())&&day.getDayOfMonth()!=Math.min(d.settings.checkDay2,day.lengthOfMonth()))continue;boolean found=false;for(Snapshot s:d.snapshots)if(s.at.toLocalDate().equals(day)&&(day.getDayOfMonth()!=1||s.isMonthStart())){found=true;break;}for(HistoryMonth h:d.history)if(h.month.equals(YearMonth.from(day))&&!historyCutoff(d,h).isBefore(day.atStartOfDay()))found=true;if(!found)list.add(day);}return list;}
 public static LocalDate nextNode(LocalDate t){return t.getDayOfMonth()<15?t.withDayOfMonth(15):t.withDayOfMonth(1).plusMonths(1);}
 private Ledger(){}
}
