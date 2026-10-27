package cn.yueji.balance;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** V1 tables stay intact until the first successful V2 transaction. */
public final class LedgerStore extends SQLiteOpenHelper {
 static final int CHUNK_CHARS=64000;
 private static long dataEpoch;
 static synchronized long epoch(){return dataEpoch;}
 public void replaceBackup(Ledger.Data data){synchronized(LedgerStore.class){save(data);dataEpoch++;}}
 public LedgerStore(Context c){super(c,"yueji.db",null,4);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL)");db.execSQL("CREATE TABLE state_chunks(id INTEGER NOT NULL, part INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(id,part))");}
 @Override public void onUpgrade(SQLiteDatabase db,int old,int next){db.execSQL("CREATE TABLE IF NOT EXISTS state(id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS state_chunks(id INTEGER NOT NULL, part INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(id,part))");}
 public Ledger.Data load(){synchronized(LedgerStore.class){return loadInternal();}}
 private Ledger.Data loadInternal(){
  SQLiteDatabase db=getReadableDatabase();StringBuilder chunked=new StringBuilder();int expected=0;
  try(Cursor c=db.rawQuery("SELECT part,json FROM state_chunks WHERE id=1 ORDER BY part",null)){
   while(c.moveToNext()){if(c.getInt(0)!=expected++)throw new IllegalStateException("账本分块不完整，请从备份恢复");chunked.append(c.getString(1));}
  }
  if(expected>0)return decode(chunked.toString());
  // Read legacy single-row JSON with SQL substr so oversized state never has to fit one CursorWindow row.
  try(Cursor c=db.rawQuery("SELECT length(json) FROM state WHERE id=1",null)){if(c.moveToFirst()){int length=c.getInt(0);for(int start=1;start<=length;start+=CHUNK_CHARS){try(Cursor part=db.rawQuery("SELECT substr(json,?,?) FROM state WHERE id=1",new String[]{""+start,""+CHUNK_CHARS})){if(part.moveToFirst())chunked.append(part.getString(0));}}return decode(chunked.toString());}}Ledger.Data d=new Ledger.Data();
  try(Cursor c=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='accounts'",null)){if(!c.moveToFirst())return d;}
  try(Cursor c=db.rawQuery("SELECT id,name,type FROM accounts",null)){while(c.moveToNext())d.accounts.add(new Ledger.Account(c.getLong(0),c.getString(1),c.getString(2)));}
  try(Cursor c=db.rawQuery("SELECT id,at,note FROM snapshots ORDER BY at",null)){while(c.moveToNext()){Ledger.Snapshot s=new Ledger.Snapshot(c.getLong(0),LocalDateTime.parse(c.getString(1)),c.getString(2));try(Cursor b=db.rawQuery("SELECT account_id,amount FROM balances WHERE snapshot_id=?",new String[]{""+s.id})){while(b.moveToNext())s.balances.put(b.getLong(0),b.getLong(1));}d.snapshots.add(s);}}
  for(Ledger.Account a:d.accounts)for(Ledger.Snapshot s:d.snapshots)if(s.balances.containsKey(a.id)){a.created=s.at;a.opening=s.balances.get(a.id);break;}
  try(Cursor c=db.rawQuery("SELECT id,income,amount,at,title,note,account_id FROM entries",null)){while(c.moveToNext()){Ledger.Entry e=new Ledger.Entry(c.getLong(0),c.getInt(1)==1,c.getLong(2),LocalDateTime.parse(c.getString(3)),c.getString(4),c.getString(5),c.isNull(6)?0:c.getLong(6));if(e.income)e.category="其他";d.entries.add(e);}}d.sort();return d;
 }
 public void save(Ledger.Data d){synchronized(LedgerStore.class){
  validate(d);String json=encode(d);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
  try{
   db.delete("state_chunks","id=1",null);
   for(int offset=0,part=0;offset<json.length();part++){
    int end=Math.min(json.length(),offset+CHUNK_CHARS);
    if(end<json.length()&&Character.isHighSurrogate(json.charAt(end-1)))end--;
    ContentValues chunk=new ContentValues();chunk.put("id",1);chunk.put("part",part);chunk.put("json",json.substring(offset,end));
    db.insertOrThrow("state_chunks",null,chunk);offset=end;
   }
   db.delete("state","id=1",null);db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }}
 public void updateQuote(long id,String code,FundClient.Quote quote,String error){
  synchronized(LedgerStore.class){Ledger.Data current=load();for(Ledger.Fund f:current.funds)if(f.id==id&&f.code.equals(code)){if(quote!=null){f.name=quote.name;f.nav=quote.nav;f.previous=quote.previous;f.navDate=quote.date;}f.error=error==null?"行情更新失败":error;save(current);break;}}
 }
 public void updateFundMarket(long id,String code,FundClient.Quote quote,String error,TradingCalendar calendar){
  updateFundMarket(id,code,quote,error,calendar,epoch());
 }
 public void updateFundMarket(long id,String code,FundClient.Quote quote,String error,TradingCalendar calendar,long expectedEpoch){
  synchronized(LedgerStore.class){if(dataEpoch!=expectedEpoch)return;Ledger.Data current=load();for(Ledger.Fund f:current.funds)if(f.id==id&&f.code.equals(code)){
    if(quote!=null){f.name=quote.name;f.nav=quote.nav;f.previous=quote.previous;f.navDate=quote.date;f.fundType=quote.fundType;f.error="";f.updatedAt=LocalDateTime.now().toString();f.historyComplete=quote.complete;f.prices.putAll(quote.history);f.navReturns.putAll(quote.returns);f.dividends.putAll(quote.dividends);f.splits.putAll(quote.splits);FundEngine.resolve(f,calendar);}
    else f.error=error==null?"行情更新失败":error;save(current);break;
  }}
 }
 public static void validate(Ledger.Data d){
  RecurringInvestments.reconcile(d);
  AccountCash.rebuild(d);
  ImportJournal.validate(d);
  DebtLedger.validate(d);
  if(MarketSession.interval(d.settings.refreshMinutes)!=d.settings.refreshMinutes)throw new IllegalArgumentException("自动刷新间隔无效");
  if(d.accounts.size()>200||d.snapshots.size()>50000||d.entries.size()>100000||d.funds.size()>200)throw new IllegalArgumentException("数据量超限");
  if(d.settings.checkDay1<1||d.settings.checkDay1>31||d.settings.checkDay2<1||d.settings.checkDay2>31||d.settings.checkDay1==d.settings.checkDay2||d.settings.reminderHour<0||d.settings.reminderHour>23)throw new IllegalArgumentException("更新提醒日期或时间无效");
  Set<Long> ids=new HashSet<>(),accounts=new HashSet<>();Set<LocalDateTime> dates=new HashSet<>();Set<YearMonth> months=new HashSet<>();
  for(Ledger.Account a:d.accounts)if(a.id<=0||!accounts.add(a.id)||a.created==null||a.created.getYear()<2000||a.created.isAfter(LocalDateTime.now())||a.name.trim().isEmpty()||a.name.length()>80||a.type.length()>30||a.opening<0||a.opening>Ledger.MAX_MONEY)throw new IllegalArgumentException("账户数据无效");
  d.sort();for(Ledger.Snapshot s:d.snapshots){if(s.id<=0||!ids.add(s.id)||!dates.add(s.at)||s.at.getYear()<2000||s.at.isAfter(LocalDateTime.now())||s.balances.isEmpty())throw new IllegalArgumentException("更新时间重复或无效");for(Map.Entry<Long,Long> b:s.balances.entrySet())if(!accounts.contains(b.getKey())||b.getValue()<0||b.getValue()>Ledger.MAX_MONEY)throw new IllegalArgumentException("更新余额无效");if(!s.partial)for(Ledger.Account a:d.accounts)if(!a.created.isAfter(s.at)&&(a.deletedAt==null||s.at.isBefore(a.deletedAt))&&!s.balances.containsKey(a.id))throw new IllegalArgumentException("更新 "+s.at.toString().replace('T',' ')+" 缺少账户「"+a.name+"」：请先把该账户补进这次更新（编辑更新页可补入），或把这次更新时间改到账户添加之前");}
  ids.clear();for(Ledger.Entry e:d.entries){
   if(e.id<=0||!ids.add(e.id)||e.amount<=0||e.amount>Ledger.MAX_MONEY||e.at.getYear()<2000||e.at.isAfter(LocalDateTime.now())||e.title.trim().isEmpty()||e.title.length()>100||e.note.length()>1000||e.accountId!=0&&!accounts.contains(e.accountId))throw new IllegalArgumentException("记录数据无效");
   if(!Arrays.asList(Ledger.INCOME,Ledger.LARGE,Ledger.REIMBURSE,Ledger.INVEST_IN,Ledger.INVEST_OUT).contains(e.kind))throw new IllegalArgumentException("记录类型无效");
   if(Ledger.INCOME.equals(e.kind)&&e.category.trim().isEmpty())throw new IllegalArgumentException("请选择收入类别");
   if(Ledger.REIMBURSE.equals(e.kind)){
    if(e.receipts.size()>100)throw new IllegalArgumentException("单笔报销到账次数超过100次");
    for(Ledger.Receipt r:e.receipts)if(r.amount<=0||r.at==null||r.at.isBefore(e.at)||r.at.isAfter(LocalDateTime.now())||r.accountId!=0&&!accounts.contains(r.accountId))throw new IllegalArgumentException("报销到账金额、时间或账户无效");
    long got=e.receivedTotal();
    if(got<0||got>e.amount||e.receipts.isEmpty()&&got>0&&(e.receivedAt==null||e.receivedAt.isBefore(e.at)||e.receivedAt.isAfter(LocalDateTime.now())))throw new IllegalArgumentException("报销到账金额或时间无效");
    if(!e.reimbursementStatus().equals(e.status))throw new IllegalArgumentException("报销状态与到账金额不一致");
   }
  }
  for(Ledger.HistoryMonth h:d.history)if(!months.add(h.month)||h.expense<0||h.expense>Ledger.MAX_MONEY||h.month.getYear()<2000||h.month.isAfter(YearMonth.now())||!h.full()&&(h.cutoff.isAfter(LocalDateTime.now())||!YearMonth.from(h.cutoff).equals(h.month)||h.cutoff.isBefore(h.month.atDay(1).atStartOfDay())))throw new IllegalArgumentException("历史月支出无效："+h.month);
  Set<String> codes=new HashSet<>();ids.clear();for(Ledger.Fund f:d.funds){
   if(!codes.add(f.code)||!ids.add(f.id))throw new IllegalArgumentException("基金代码或标识重复");FundCodec.validate(f);RecurringInvestments.validate(f,d);
   for(Ledger.FundBuy b:f.buys){
    if(b.buyAccountLinked){if(b.buyAccountId<=0)throw new IllegalArgumentException("基金买入账户无效");AccountCash.requireAccount(d,b.buyAccountId,FundEngine.ledgerTime(b.at));}
    if(b.saleAccountLinked){if(b.saleDate==null||b.saleAccountId<=0)throw new IllegalArgumentException("基金卖出账户无效");AccountCash.requireAccount(d,b.saleAccountId,FundEngine.ledgerTime(b.saleAt==null?b.saleDate.atTime(15,0):b.saleAt));}
   }
  }
  ids.clear();if(d.banks.size()>200)throw new IllegalArgumentException("银行理财记录超限");for(BankLedger.Product p:d.banks){if(!ids.add(p.id))throw new IllegalArgumentException("银行理财标识重复");BankLedger.validate(p);}
  WatchValidation.validate(d);
  if(d.categories.size()>100||new HashSet<>(d.categories).size()!=d.categories.size())throw new IllegalArgumentException("类别重复或超过100个");for(String c:d.categories)if(c.trim().isEmpty()||c.length()>30)throw new IllegalArgumentException("类别名称无效");
 }
 public String exportJson(){return encode(load());}
 public Ledger.Data parseBackup(String json){if(json==null||json.length()>64*1024*1024)throw new IllegalArgumentException("备份文件无效或超过64MB");Ledger.Data d=decode(json);validate(d);return d;}
 static String encode(Ledger.Data d){try{
  JSONObject root=new JSONObject().put("format","yueji-backup").put("version",13).put("imports",ImportJournal.encode(d)).put("debts",DebtLedger.encode(d));JSONArray aa=new JSONArray(),ss=new JSONArray(),ee=new JSONArray(),hh=new JSONArray(),ff=new JSONArray();
  for(Ledger.Account a:d.accounts)aa.put(new JSONObject().put("id",a.id).put("name",a.name).put("type",a.type).put("opening",a.opening).put("created",a.created).put("deletedAt",a.deletedAt==null?JSONObject.NULL:a.deletedAt.toString()));
  for(Ledger.Snapshot s:d.snapshots){JSONObject b=new JSONObject();for(Map.Entry<Long,Long> e:s.balances.entrySet())b.put(""+e.getKey(),e.getValue());ss.put(new JSONObject().put("id",s.id).put("at",s.at).put("note",s.note).put("balances",b).put("partial",s.partial).put("cashGenerated",s.cashGenerated).put("cashAccounts",new JSONArray(s.cashAccounts)));}
  for(Ledger.Entry e:d.entries){JSONArray receipts=new JSONArray();for(Ledger.Receipt r:e.receipts)receipts.put(new JSONObject().put("amount",r.amount).put("at",r.at).put("accountId",r.accountId).put("cashLinked",r.cashLinked));ee.put(new JSONObject().put("id",e.id).put("kind",e.kind).put("amount",e.amount).put("at",e.at).put("title",e.title).put("note",e.note).put("accountId",e.accountId).put("cashLinked",e.cashLinked).put("category",e.category).put("status",e.status).put("received",e.receivedTotal()).put("receivedAt",e.receivedAt==null?JSONObject.NULL:e.receivedAt.toString()).put("receipts",receipts));}
  for(Ledger.HistoryMonth h:d.history)hh.put(new JSONObject().put("month",h.month).put("expense",h.expense).put("cutoff",h.cutoff).put("includesAdvances",h.includesAdvances).put("importId",h.importId));
  for(Ledger.Fund f:d.funds)ff.put(FundCodec.encode(f));
  JSONArray banks=new JSONArray();for(BankLedger.Product p:d.banks)banks.put(BankCodec.encode(p));root.put("banks",banks);
  JSONArray watch=new JSONArray();for(Ledger.WatchFund f:d.watchFunds){
   JSONObject prices=new JSONObject(),returns=new JSONObject();for(Map.Entry<LocalDate,BigDecimal> e:f.prices.entrySet())prices.put(e.getKey().toString(),e.getValue().toPlainString());for(Map.Entry<LocalDate,BigDecimal> e:f.returns.entrySet())returns.put(e.getKey().toString(),e.getValue().toPlainString());
   watch.put(new JSONObject().put("code",f.code).put("name",f.name).put("profile",f.profile.encode()).put("navDate",f.navDate).put("fundType",f.fundType).put("establishedDate",f.establishedDate).put("scaleAmount",f.scaleAmount).put("lockDays",f.lockDays).put("buyFeeBasisPoints",f.buyFeeBasisPoints).put("managementFeeBasisPoints",f.managementFeeBasisPoints).put("custodyFeeBasisPoints",f.custodyFeeBasisPoints).put("serviceFeeBasisPoints",f.serviceFeeBasisPoints).put("saleFeeBasisPoints",f.saleFeeBasisPoints).put("nav",f.nav.toPlainString()).put("previous",f.previous.toPlainString()).put("historyComplete",f.historyComplete).put("prices",prices).put("returns",returns));
  }
  JSONArray groups=new JSONArray();for(Ledger.WatchGroup group:d.watchGroups)groups.put(new JSONObject().put("id",group.id).put("name",group.name).put("codes",new JSONArray(group.codes)));
  root.put("watchAllIncluded",new JSONArray(d.watchAllIncluded));
  root.put("watchFunds",watch).put("watchGroups",groups).put("watchRange",d.watchRange).put("watchSort",d.watchSort).put("watchDescending",d.watchDescending);
  root.put("settings",new JSONObject().put("day1",d.settings.checkDay1).put("day2",d.settings.checkDay2).put("reminderHour",d.settings.reminderHour).put("checkNotifications",d.settings.checkNotifications).put("fundNotifications",d.settings.fundNotifications).put("autoRefresh",d.settings.autoRefresh).put("lossOnly",d.settings.lossOnly).put("refreshMinutes",d.settings.refreshMinutes));return root.put("accounts",aa).put("snapshots",ss).put("entries",ee).put("history",hh).put("funds",ff).put("categories",new JSONArray(d.categories)).put("categoryIcons",new JSONObject(d.categoryIcons)).toString(2);
 }catch(JSONException e){throw new IllegalArgumentException("数据编码失败",e);}}
 static Ledger.Data decode(String json){try{
  JSONObject root=new JSONObject(json);if(!"yueji-backup".equals(root.getString("format"))||root.getInt("version")<1||root.getInt("version")>13)throw new IllegalArgumentException("不支持的备份版本");
  for(String key:new String[]{"accounts","snapshots","entries","history","funds","banks","watchFunds","watchGroups","watchAllIncluded","imports","debts","categories"})if(root.has(key)&&!(root.get(key) instanceof JSONArray))throw new IllegalArgumentException(key+" 必须是列表");
  Ledger.Data d=new Ledger.Data();ImportJournal.decode(d,root.optJSONArray("imports"));DebtLedger.decode(d,root.optJSONArray("debts"));JSONArray aa=root.getJSONArray("accounts"),ss=root.getJSONArray("snapshots"),ee=root.getJSONArray("entries");
  for(int i=0;i<aa.length();i++){JSONObject o=aa.getJSONObject(i);Ledger.Account a=new Ledger.Account(o.getLong("id"),o.getString("name"),o.getString("type"));if(o.has("created")){a.created=LocalDateTime.parse(o.getString("created"));a.opening=o.getLong("opening");}if(!o.isNull("deletedAt"))a.deletedAt=LocalDateTime.parse(o.getString("deletedAt"));d.accounts.add(a);}
  for(int i=0;i<ss.length();i++){JSONObject o=ss.getJSONObject(i);Ledger.Snapshot s=new Ledger.Snapshot(o.getLong("id"),LocalDateTime.parse(o.getString("at")),o.optString("note"));s.partial=o.optBoolean("partial");s.cashGenerated=o.optBoolean("cashGenerated");JSONArray cash=o.optJSONArray("cashAccounts");if(cash!=null)for(int k=0;k<cash.length();k++)s.cashAccounts.add(cash.getLong(k));JSONObject b=o.getJSONObject("balances");Iterator<String> keys=b.keys();while(keys.hasNext()){String k=keys.next();s.balances.put(Long.parseLong(k),b.getLong(k));}d.snapshots.add(s);}d.sort();
  for(Ledger.Account a:d.accounts)if(a.created==null)for(Ledger.Snapshot s:d.snapshots)if(s.balances.containsKey(a.id)){a.created=s.at;a.opening=s.balances.get(a.id);break;}
  for(int i=0;i<ee.length();i++){JSONObject o=ee.getJSONObject(i);String kind=o.optString("kind",o.optBoolean("income")?Ledger.INCOME:Ledger.LARGE);Ledger.Entry e=new Ledger.Entry(o.getLong("id"),Ledger.INCOME.equals(kind),o.getLong("amount"),LocalDateTime.parse(o.getString("at")),o.getString("title"),o.optString("note"),o.optLong("accountId"));e.kind=kind;e.cashLinked=o.optBoolean("cashLinked");e.category=o.optString("category",e.income?"其他":"");e.status=o.optString("status","待报销");e.received=o.optLong("received");if(!o.isNull("receivedAt")&&!o.optString("receivedAt").isEmpty())e.receivedAt=LocalDateTime.parse(o.getString("receivedAt"));JSONArray receipts=o.optJSONArray("receipts");if(receipts!=null)for(int j=0;j<receipts.length();j++){JSONObject r=receipts.getJSONObject(j);Ledger.Receipt receipt=new Ledger.Receipt(r.getLong("amount"),LocalDateTime.parse(r.getString("at")));receipt.accountId=r.optLong("accountId");receipt.cashLinked=r.optBoolean("cashLinked");e.receipts.add(receipt);}d.entries.add(e);}
  JSONArray hh=root.optJSONArray("history");if(hh!=null)for(int i=0;i<hh.length();i++){JSONObject o=hh.getJSONObject(i);Ledger.HistoryMonth h=new Ledger.HistoryMonth(YearMonth.parse(o.getString("month")),o.getLong("expense"),LocalDateTime.parse(o.getString("cutoff")));h.includesAdvances=o.optBoolean("includesAdvances");h.importId=o.optLong("importId");d.history.add(h);}
  JSONArray ff=root.optJSONArray("funds");if(ff!=null)for(int i=0;i<ff.length();i++)d.funds.add(FundCodec.decode(ff.getJSONObject(i)));
  JSONArray banks=root.optJSONArray("banks");if(banks!=null)for(int i=0;i<banks.length();i++)d.banks.add(BankCodec.decode(banks.getJSONObject(i)));
  JSONArray watch=root.optJSONArray("watchFunds");if(watch!=null)for(int i=0;i<watch.length();i++){
   JSONObject o=watch.getJSONObject(i);Ledger.WatchFund f=new Ledger.WatchFund(o.getString("code"),o.getString("name"));f.profile.read(o.optJSONObject("profile"));f.navDate=o.optString("navDate");f.fundType=o.optString("fundType");f.establishedDate=o.optString("establishedDate");f.scaleAmount=o.optString("scaleAmount");f.lockDays=o.optInt("lockDays");f.buyFeeBasisPoints=o.optInt("buyFeeBasisPoints",-1);f.managementFeeBasisPoints=o.optInt("managementFeeBasisPoints",-1);f.custodyFeeBasisPoints=o.optInt("custodyFeeBasisPoints",-1);f.serviceFeeBasisPoints=o.optInt("serviceFeeBasisPoints",-1);f.saleFeeBasisPoints=o.optInt("saleFeeBasisPoints",-1);f.nav=new BigDecimal(o.optString("nav","0"));f.previous=new BigDecimal(o.optString("previous","0"));f.historyComplete=o.optBoolean("historyComplete");
   JSONObject prices=o.optJSONObject("prices");if(prices!=null){Iterator<String> keys=prices.keys();while(keys.hasNext()){String k=keys.next();f.prices.put(LocalDate.parse(k),new BigDecimal(prices.getString(k)));}}
   JSONObject returns=o.optJSONObject("returns");if(returns!=null){Iterator<String> keys=returns.keys();while(keys.hasNext()){String k=keys.next();f.returns.put(LocalDate.parse(k),new BigDecimal(returns.getString(k)));}}
   d.watchFunds.add(f);
  }
  JSONArray groups=root.optJSONArray("watchGroups");if(groups!=null)for(int i=0;i<groups.length();i++){JSONObject o=groups.getJSONObject(i);Ledger.WatchGroup group=new Ledger.WatchGroup(o.getLong("id"),o.getString("name"));JSONArray codes=o.optJSONArray("codes");if(codes!=null)for(int j=0;j<codes.length();j++)group.codes.add(codes.getString(j));d.watchGroups.add(group);}
  JSONArray inbox=root.optJSONArray("watchAllIncluded");if(inbox!=null)for(int i=0;i<inbox.length();i++)d.watchAllIncluded.add(inbox.getString(i));
  d.watchRange=Math.max(0,Math.min(9,root.optInt("watchRange",4)));d.watchSort=Math.max(0,Math.min(2,root.optInt("watchSort",0)));d.watchDescending=root.optBoolean("watchDescending",true);
  JSONObject settings=root.optJSONObject("settings");if(settings!=null){d.settings.checkDay1=settings.optInt("day1",1);d.settings.checkDay2=settings.optInt("day2",15);d.settings.reminderHour=settings.optInt("reminderHour",9);d.settings.checkNotifications=settings.optBoolean("checkNotifications",true);d.settings.fundNotifications=settings.optBoolean("fundNotifications",true);d.settings.autoRefresh=settings.optBoolean("autoRefresh",true);d.settings.lossOnly=settings.optBoolean("lossOnly",true);d.settings.refreshMinutes=MarketSession.interval(settings.optInt("refreshMinutes",120));}JSONArray cc=root.optJSONArray("categories");if(cc!=null){d.categories.clear();for(int i=0;i<cc.length();i++)d.categories.add(cc.getString(i));}for(Ledger.Entry e:d.entries)if(e.income&&!d.categories.contains(e.category))d.categories.add(e.category);
  JSONObject icons=root.optJSONObject("categoryIcons");if(icons!=null){d.categoryIcons.clear();Iterator<String> names=icons.keys();while(names.hasNext()){String name=names.next();d.categoryIcons.put(name,icons.getString(name));}}
  d.ensureIcons();d.sort();return d;
 }catch(Exception e){throw new IllegalArgumentException("备份内容无效："+e.getMessage(),e);}}
}
