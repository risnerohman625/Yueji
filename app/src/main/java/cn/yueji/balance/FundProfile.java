package cn.yueji.balance;

import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Disclosed product information only. Unknown terms are never silently treated as free/unlocked. */
final class FundProfile {
    String established="",scale="",buyRules="",saleRules="",management="",custody="",service="",updated="";
    String dailyLimit="",dailyLimitChecked="";boolean dailyLimitConfirmed;String lockUnit="";int lockCount;
    void merge(FundProfile source){
        if(!source.established.isEmpty())established=source.established;
        if(!source.scale.isEmpty())scale=source.scale;
        if(!source.buyRules.isEmpty())buyRules=source.buyRules;
        if(!source.saleRules.isEmpty())saleRules=source.saleRules;
        if(!source.management.isEmpty())management=source.management;
        if(!source.custody.isEmpty())custody=source.custody;
        if(!source.service.isEmpty())service=source.service;
        mergeLimit(source);
        if(source.lockCount>0){lockCount=source.lockCount;lockUnit=source.lockUnit;}
        if(!source.updated.isEmpty())updated=source.updated;
    }
    void mergeLimit(FundProfile source){if(source.dailyLimitConfirmed&&(!dailyLimitConfirmed||source.dailyLimitChecked.compareTo(dailyLimitChecked)>=0)){dailyLimit=source.dailyLimit;dailyLimitConfirmed=true;dailyLimitChecked=source.dailyLimitChecked;}}
    JSONObject encode()throws JSONException{return new JSONObject().put("dailyLimitConfirmed",dailyLimitConfirmed).put("dailyLimitChecked",dailyLimitChecked).put("dailyLimit",dailyLimit).put("established",established).put("scale",scale).put("buyRules",buyRules).put("saleRules",saleRules).put("management",management).put("custody",custody).put("service",service).put("lockUnit",lockUnit).put("lockCount",lockCount).put("updated",updated);}
    void read(JSONObject o){if(o==null)return;if(o.has("dailyLimitConfirmed")&&!(o.opt("dailyLimitConfirmed") instanceof Boolean))throw new IllegalArgumentException("每日限额状态必须是布尔值");if(o.has("dailyLimit")&&!(o.opt("dailyLimit") instanceof String)||o.has("dailyLimitChecked")&&!(o.opt("dailyLimitChecked") instanceof String))throw new IllegalArgumentException("每日限额字段必须是文字");dailyLimit=o.optString("dailyLimit");dailyLimitConfirmed=o.optBoolean("dailyLimitConfirmed");dailyLimitChecked=o.optString("dailyLimitChecked");established=o.optString("established");scale=o.optString("scale");buyRules=o.optString("buyRules");saleRules=o.optString("saleRules");management=o.optString("management");custody=o.optString("custody");service=o.optString("service");lockUnit=o.optString("lockUnit");lockCount=o.optInt("lockCount");updated=o.optString("updated");}
    static String clean(String html){return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&le;","≤").replace("&ge;","≥").replace("&#x2264;","≤").replace("&amp;","&").replaceAll("\\s+"," ").trim();}
    static String match(String html,String expression){Matcher m=Pattern.compile(expression,Pattern.DOTALL).matcher(html);return m.find()?clean(m.group(1)):"";}
    static FundProfile parse(String name,String overview,String fees){
        FundProfile p=new FundProfile();p.established=match(overview,"成立日期[：:]\\s*<span[^>]*>([^<]+)");
        if(p.established.isEmpty()){String date=match(overview,"成立日期/规模</th>\\s*<td[^>]*>(.*?)</td>");Matcher d=Pattern.compile("(20\\d{2})年(\\d{1,2})月(\\d{1,2})日").matcher(date);if(d.find())p.established=LocalDate.of(Integer.parseInt(d.group(1)),Integer.parseInt(d.group(2)),Integer.parseInt(d.group(3))).toString();}
        p.scale=match(overview,"净资产规模</th>\\s*<td[^>]*>(.*?)(?:</td>|<th)");
        p.management=match(fees,"管理费率</td>\\s*<td[^>]*>(.*?)</td>");p.custody=match(fees,"托管费率</td>\\s*<td[^>]*>(.*?)</td>");p.service=match(fees,"销售服务费率</td>\\s*<td[^>]*>(.*?)</td>");
        p.readDailyLimit(fees,overview);
        p.buyRules=table(fees,"申购费率");p.saleRules=table(fees,"赎回费率");p.inferLock(name);p.updated=FundEngine.now().toLocalDate().toString();return p;
    }
    void readDailyLimit(String fees,String overview){
        String value=match(fees,"日累计申购限[额度]</t[dh]>\\s*<td[^>]*>(.*?)</td>");
        if(value.isEmpty())value=match(overview,"单日累计购买上限\\s*([0-9,.]+\\s*(?:万|千|亿)?元)");
        if(value.matches("无限额|不限额|不限|无限制|无上限")){dailyLimit="";dailyLimitConfirmed=true;}
        else{
            Matcher amount=Pattern.compile("([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(万|千|亿)?\\s*元").matcher(value);
            if(!amount.matches())return;
            try{java.math.BigDecimal n=new java.math.BigDecimal(amount.group(1).replace(",",""));if(amount.group(2)!=null)n=n.multiply(new java.math.BigDecimal(amount.group(2).equals("万")?"10000":amount.group(2).equals("千")?"1000":"100000000"));long cents=n.movePointRight(2).longValueExact();if(cents<0||cents>Ledger.MAX_MONEY)return;dailyLimit=Ledger.decimal(cents)+"元";dailyLimitConfirmed=true;}catch(ArithmeticException|NumberFormatException ignored){return;}
        }
        dailyLimitChecked=FundEngine.now().toString();
    }
    boolean limited(){return dailyLimitConfirmed&&!dailyLimit.isEmpty();}
    String limitLabel(){if(!limited())return "";return "限制单日买入"+dailyLimit.replaceFirst("\\.00元$","元");}
    static String table(String html,String heading){
        Matcher m=Pattern.compile(heading+"(?:<a[^>]*>.*?</a>)?</label>.*?<table[^>]*>(.*?)</table>",Pattern.DOTALL).matcher(html);if(!m.find())return "";
        String table=m.group(1);Matcher rows=Pattern.compile("<tr[^>]*>(.*?)</tr>",Pattern.DOTALL).matcher(table);List<String> result=new ArrayList<>();
        while(rows.find()){String text=clean(rows.group(1).replaceAll("(?is)</t[dh]>\\s*<t[dh][^>]*>"," | ")).replace(" | ","  ");if(!text.isEmpty())result.add(text);}return String.join("\n",result);
    }
    void inferLock(String name){
        Matcher m=Pattern.compile("([0-9]+)(个?月|天|年)(?:最短)?持有").matcher(name==null?"":name);
        if(m.find()){lockCount=Integer.parseInt(m.group(1));lockUnit=m.group(2).contains("月")?"月":m.group(2);}
    }
    String lockLabel(){return lockCount>0?"每笔锁定 "+lockCount+(lockUnit.equals("月")?"个月":lockUnit)+"（按确认日起算，以平台实际开放日为准）":"未取得锁定期条款，请核对基金合同";}
    LocalDate unlock(LocalDate confirmed){if(confirmed==null||lockCount<=0)return null;return lockUnit.equals("月")?confirmed.plusMonths(lockCount):lockUnit.equals("年")?confirmed.plusYears(lockCount):confirmed.plusDays(lockCount);}
    String orUnknown(String value){return value==null||value.isEmpty()?"暂未取得，请查看基金公告":value;}
    void validate(){for(String s:new String[]{dailyLimit,dailyLimitChecked,established,scale,buyRules,saleRules,management,custody,service,updated,lockUnit})if(s==null||s.length()>10000)throw new IllegalArgumentException("基金资料无效");if(!dailyLimitChecked.isEmpty()){if(dailyLimitChecked.length()==10)LocalDate.parse(dailyLimitChecked);else LocalDateTime.parse(dailyLimitChecked);if(LocalDate.parse(dailyLimitChecked.substring(0,10)).isAfter(FundEngine.now().toLocalDate()))throw new IllegalArgumentException("每日限额核实日期不能在未来");}if(dailyLimitConfirmed){if(dailyLimitChecked.isEmpty())throw new IllegalArgumentException("每日限额缺少核实日期");if(!dailyLimit.isEmpty()){if(!dailyLimit.matches("[0-9]+\\.[0-9]{2}元"))throw new IllegalArgumentException("每日限额金额无效");Ledger.parseMoney(dailyLimit.replace("元",""),false);}}if(!established.isEmpty())LocalDate.parse(established);if(lockCount<0||lockCount>10000||lockCount>0&&!Arrays.asList("天","月","年").contains(lockUnit))throw new IllegalArgumentException("基金锁定期无效");}
}
