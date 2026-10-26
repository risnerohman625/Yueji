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
        try{if(c.getResponseCode()!=200)throw new IOException("行情服务 HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");out.write(b,0,n);if(out.size()>2000000)throw new IOException("行情响应过大");}String encoding=StandardCharsets.UTF_8.name();String type=c.getContentType();if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).matches(".*charset=(?:gb2312|gbk|gb18030).*"))encoding="GB18030";return out.toString(encoding);}}finally{c.disconnect();}
    }
    public static Quote fetch(String code)throws Exception{
        return NetworkIo.call(()->fetchNow(code));
    }
    private static Quote fetchNow(String code)throws Exception{
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码为6位数字");
        JSONObject search=new JSONObject(get("https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key="+code));JSONArray found=search.getJSONArray("Datas");JSONObject exact=null;
        for(int i=0;i<found.length();i++){JSONObject o=found.getJSONObject(i);if(code.equals(o.optString("CODE"))&&o.has("FundBaseInfo")&&!o.isNull("FundBaseInfo")){exact=o;break;}}
        if(exact==null)throw new IllegalArgumentException("未找到该公募基金");
        return fetchNav(code,exact.getString("NAME"),exact.getJSONObject("FundBaseInfo").optString("FTYPE"));
    }
    private static Quote fetchNav(String code,String name,String type)throws Exception{
        JSONObject response=new JSONObject(get("https://api.fund.eastmoney.com/f10/lsjz?fundCode="+code+"&pageIndex=1&pageSize=2"));
        JSONArray list=response.getJSONObject("Data").getJSONArray("LSJZList");if(list.length()<2)throw new IOException("净值数据不足");
        Quote q=new Quote();q.code=code;q.name=name;q.fundType=type;q.date=list.getJSONObject(0).getString("FSRQ");
        q.nav=new BigDecimal(list.getJSONObject(0).getString("DWJZ"));q.previous=new BigDecimal(list.getJSONObject(1).getString("DWJZ"));
        if(q.nav.signum()<=0||q.previous.signum()<=0||type.contains("货币"))throw new IllegalArgumentException("暂不支持货币基金或无单位净值产品");
        java.time.LocalDate date=java.time.LocalDate.parse(q.date);
        if(date.isAfter(FundEngine.now().toLocalDate()))throw new IOException("行情日期异常");
        return q;
    }
    public static Quote fetchHistory(String code)throws Exception{
        return NetworkIo.call(()->fetchHistoryNow(code));
    }
    private static Quote fetchHistoryNow(String code)throws Exception{
        Quote q=fetchNow(code);
        return history(q);
    }
    /** A previously verified identity needs NAV/history refresh only, never another name search. */
    public static Quote fetchHistoryKnown(String code,String name,String type)throws Exception{
        if(!code.matches("[0-9]{6}")||name==null||name.trim().isEmpty())throw new IllegalArgumentException("基金身份无效");
        return NetworkIo.call(()->history(fetchNav(code,name,type)));
    }
    private static Quote history(Quote q)throws Exception{
        String code=q.code;
        String source=get("https://fund.eastmoney.com/pingzhongdata/"+code+".js?v="+System.currentTimeMillis());
        Matcher match=Pattern.compile("var\\s+Data_netWorthTrend\\s*=\\s*(\\[.*?\\]);",Pattern.DOTALL).matcher(source);
        if(!match.find())throw new IOException("历史净值暂不可用");
        JSONArray history=new JSONArray(match.group(1));
        BigDecimal previous=null;
        for(int i=0;i<history.length();i++){
            JSONObject item=history.getJSONObject(i);
            LocalDate day=Instant.ofEpochMilli(item.getLong("x")).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            if(item.isNull("y"))continue;BigDecimal value=new BigDecimal(item.get("y").toString());
            if(!day.isAfter(LocalDate.parse(q.date))&&value.signum()>0){
                q.history.put(day,value);BigDecimal change=item.isNull("equityReturn")?null:new BigDecimal(item.get("equityReturn").toString());if(change!=null)q.returns.put(day,change);
                String event=item.optString("unitMoney");
                if(previous!=null&&!event.contains("分红")&&!event.contains("拆分")&&!event.contains("折算"))q.returns.put(day,value.divide(previous,16,java.math.RoundingMode.HALF_UP).subtract(BigDecimal.ONE).movePointRight(2));
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
    public static String refresh(android.content.Context context){return MarketRefresh.run(context,false,false,null);}
    static String refreshDuringMarket(android.content.Context context){return MarketRefresh.run(context,true,false,null);}
    static FundProfile profile(String code,String name)throws Exception{
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码无效");
        return NetworkIo.call(()->{String overview="",fees="";try{overview=get("https://fundf10.eastmoney.com/jbgk_"+code+".html");}catch(IOException ignored){}try{fees=get("https://fundf10.eastmoney.com/jjfl_"+code+".html");}catch(IOException ignored){}FundProfile p=FundProfile.parse(name,overview,fees);if(p.management.isEmpty())p.management=FundProfile.match(overview,"管理费率</th>\\s*<td[^>]*>(.*?)</td>");if(p.custody.isEmpty())p.custody=FundProfile.match(overview,"托管费率</th>\\s*<td[^>]*>(.*?)</td>");if(p.service.isEmpty())p.service=FundProfile.match(overview,"销售服务费率</th>\\s*<td[^>]*>(.*?)</td>");if(overview.isEmpty()&&fees.isEmpty())p.updated="";return p;});
    }
    private FundClient(){}
}
