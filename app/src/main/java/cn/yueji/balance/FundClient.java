package cn.yueji.balance;

import org.json.*;
import java.io.*;
import java.net.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.*;
import java.util.regex.*;

public final class FundClient {
    static final class Match {
        final String code,name;
        Match(String code,String name){this.code=code;this.name=name;}
    }
    static List<Match> search(String name)throws Exception{
        return NetworkIo.call(()->{
            String source=get("https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key="+URLEncoder.encode(name,StandardCharsets.UTF_8.name()));
            JSONArray datas=new JSONObject(source).getJSONArray("Datas");List<Match> found=new ArrayList<>();Set<String> codes=new HashSet<>();
            for(int i=0;i<datas.length();i++){
                JSONObject row=datas.getJSONObject(i);String code=row.optString("CODE"),title=row.optString("NAME");
                if(code.matches("[0-9]{6}")&&!title.trim().isEmpty()&&row.has("FundBaseInfo")&&!row.isNull("FundBaseInfo")&&codes.add(code))found.add(new Match(code,title));
            }
            return found;
        });
    }
    public static final class Quote {
        public String code,name,date,fundType="";
        public BigDecimal nav,previous;
        public final TreeMap<LocalDate,BigDecimal> history=new TreeMap<>();
        public final TreeMap<LocalDate,BigDecimal> returns=new TreeMap<>();
        public final TreeMap<LocalDate,BigDecimal> dividends=new TreeMap<>(),splits=new TreeMap<>();
        public boolean complete;
    }
    private static String get(String url)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);
        c.setRequestProperty("Referer","https://fund.eastmoney.com/");c.setRequestProperty("User-Agent","Mozilla/5.0 Yueji/4.5.0");
        try{if(c.getResponseCode()!=200)throw new IOException("行情服务 HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");out.write(b,0,n);if(out.size()>2000000)throw new IOException("行情响应过大");}return out.toString(StandardCharsets.UTF_8.name());}}finally{c.disconnect();}
    }
    public static Quote fetch(String code)throws Exception{
        return NetworkIo.call(()->fetchNow(code));
    }
    private static Quote fetchNow(String code)throws Exception{
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码为6位数字");
        JSONObject search=new JSONObject(get("https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key="+code));JSONArray found=search.getJSONArray("Datas");JSONObject exact=null;
        for(int i=0;i<found.length();i++){JSONObject o=found.getJSONObject(i);if(code.equals(o.optString("CODE"))&&o.has("FundBaseInfo")&&!o.isNull("FundBaseInfo")){exact=o;break;}}
        if(exact==null)throw new IllegalArgumentException("未找到该公募基金");
        JSONObject response=new JSONObject(get("https://api.fund.eastmoney.com/f10/lsjz?fundCode="+code+"&pageIndex=1&pageSize=2"));
        JSONArray list=response.getJSONObject("Data").getJSONArray("LSJZList");if(list.length()<2)throw new IOException("净值数据不足");
        Quote q=new Quote();q.code=code;q.name=exact.getString("NAME");q.fundType=exact.getJSONObject("FundBaseInfo").optString("FTYPE");q.date=list.getJSONObject(0).getString("FSRQ");
        q.nav=new BigDecimal(list.getJSONObject(0).getString("DWJZ"));q.previous=new BigDecimal(list.getJSONObject(1).getString("DWJZ"));
        if(q.nav.signum()<=0||q.previous.signum()<=0||exact.getJSONObject("FundBaseInfo").optString("FTYPE").contains("货币"))throw new IllegalArgumentException("暂不支持货币基金或无单位净值产品");
        java.time.LocalDate date=java.time.LocalDate.parse(q.date);
        if(date.isAfter(FundEngine.now().toLocalDate()))throw new IOException("行情日期异常");
        return q;
    }
    public static Quote fetchHistory(String code)throws Exception{
        return NetworkIo.call(()->fetchHistoryNow(code));
    }
    private static Quote fetchHistoryNow(String code)throws Exception{
        Quote q=fetchNow(code);
        String source=get("https://fund.eastmoney.com/pingzhongdata/"+code+".js?v="+System.currentTimeMillis());
        Matcher match=Pattern.compile("var\\s+Data_netWorthTrend\\s*=\\s*(\\[.*?\\]);",Pattern.DOTALL).matcher(source);
        if(!match.find())throw new IOException("历史净值暂不可用");
        JSONArray history=new JSONArray(match.group(1));
        BigDecimal previous=null;
        for(int i=0;i<history.length();i++){
            JSONObject item=history.getJSONObject(i);
            LocalDate day=Instant.ofEpochMilli(item.getLong("x")).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            if(item.isNull("y"))continue;BigDecimal value=new BigDecimal(item.getString("y"));
            if(!day.isAfter(FundEngine.now().toLocalDate())&&value.signum()>0){
                q.history.put(day,value);BigDecimal change=item.isNull("equityReturn")?null:new BigDecimal(item.getString("equityReturn"));if(change!=null)q.returns.put(day,change);
                String event=item.optString("unitMoney");
                if(previous!=null&&change!=null){
                    BigDecimal theoretical=previous.multiply(BigDecimal.ONE.add(change.movePointLeft(2)));
                    if(event.contains("拆分")||event.contains("折算")){BigDecimal factor=theoretical.divide(value,8,java.math.RoundingMode.HALF_UP);if(factor.signum()>0)q.splits.put(day,factor);}
                    else if(event.contains("分红")){BigDecimal cash=theoretical.subtract(value).setScale(6,java.math.RoundingMode.HALF_UP);if(cash.compareTo(new BigDecimal("0.00005"))>0)q.dividends.put(day,cash);}
                }
                previous=value;
            }
        }
        if(q.history.size()<2)throw new IOException("历史净值不足");
        q.history.put(LocalDate.parse(q.date),q.nav);q.date=q.history.lastKey().toString();q.nav=q.history.lastEntry().getValue();q.previous=q.history.lowerEntry(q.history.lastKey()).getValue();q.complete=true;return q;
    }
    /** Returns a short failure for the foreground; background jobs never open an activity or dialog. */
    public static String refresh(android.content.Context context){
        return refresh(context,false);
    }
    static String refreshDuringMarket(android.content.Context context){return refresh(context,true);}
    private static String refresh(android.content.Context context,boolean marketHoursOnly){
        LedgerStore store=new LedgerStore(context);
        try{
            String problem=null;
            Ledger.Data initial=store.load();
            TradingCalendar calendar=new TradingCalendar();for(int year=2010;year<=FundEngine.now().toLocalDate().getYear()+1;year++)calendar.load(context,year);
            for(Ledger.Fund f:initial.funds){
                if(marketHoursOnly&&!MarketSession.open(calendar,FundEngine.now()))break;
                if(f.removed())continue;
                Quote q=null;String error="";boolean stop=false;
                try{q=fetchHistory(f.code);}catch(Exception e){error=NetworkIo.message(e);if(problem==null)problem=error;stop=NetworkIo.connectivity(e);}
                store.updateFundMarket(f.id,f.code,q,error,calendar);
                if(stop||Thread.currentThread().isInterrupted()||marketHoursOnly&&!MarketSession.open(calendar,FundEngine.now()))break;
            }
            FundNotifications.publish(context,store.load());
            return problem;
        }finally{store.close();}
    }
    static FundProfile profile(String code,String name)throws Exception{
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码无效");
        return NetworkIo.call(()->FundProfile.parse(name,get("https://fundf10.eastmoney.com/jbgk_"+code+".html"),get("https://fundf10.eastmoney.com/jjfl_"+code+".html")));
    }
    private FundClient(){}
}
