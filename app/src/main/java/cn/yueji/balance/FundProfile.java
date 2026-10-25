package cn.yueji.balance;

import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Disclosed product information only. Unknown terms are never silently treated as free/unlocked. */
final class FundProfile {
    String established="",scale="",buyRules="",saleRules="",management="",custody="",service="",updated="";
    String dailyLimit="";String lockUnit="";int lockCount;
    JSONObject encode()throws JSONException{return new JSONObject().put("dailyLimit",dailyLimit).put("established",established).put("scale",scale).put("buyRules",buyRules).put("saleRules",saleRules).put("management",management).put("custody",custody).put("service",service).put("lockUnit",lockUnit).put("lockCount",lockCount).put("updated",updated);}
    void read(JSONObject o){if(o==null)return;dailyLimit=o.optString("dailyLimit");established=o.optString("established");scale=o.optString("scale");buyRules=o.optString("buyRules");saleRules=o.optString("saleRules");management=o.optString("management");custody=o.optString("custody");service=o.optString("service");lockUnit=o.optString("lockUnit");lockCount=o.optInt("lockCount");updated=o.optString("updated");}
    static String clean(String html){return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&le;","≤").replace("&ge;","≥").replace("&#x2264;","≤").replace("&amp;","&").replaceAll("\\s+"," ").trim();}
    static String match(String html,String expression){Matcher m=Pattern.compile(expression,Pattern.DOTALL).matcher(html);return m.find()?clean(m.group(1)):"";}
    static FundProfile parse(String name,String overview,String fees){
        FundProfile p=new FundProfile();p.established=match(overview,"成立日期[：:]\\s*<span[^>]*>([^<]+)");
        if(p.established.isEmpty()){String date=match(overview,"成立日期/规模</th>\\s*<td[^>]*>(.*?)</td>");Matcher d=Pattern.compile("(20\\d{2})年(\\d{1,2})月(\\d{1,2})日").matcher(date);if(d.find())p.established=LocalDate.of(Integer.parseInt(d.group(1)),Integer.parseInt(d.group(2)),Integer.parseInt(d.group(3))).toString();}
        p.scale=match(overview,"净资产规模</th>\\s*<td[^>]*>(.*?)(?:</td>|<th)");
        p.management=match(fees,"管理费率</td>\\s*<td[^>]*>(.*?)</td>");p.custody=match(fees,"托管费率</td>\\s*<td[^>]*>(.*?)</td>");p.service=match(fees,"销售服务费率</td>\\s*<td[^>]*>(.*?)</td>");
        String limit=match(overview,"限(?:制)?(?:单日|大额)?(?:申购|购买)[^0-9<]{0,20}([0-9.,]+(?:万|千)?元)");p.dailyLimit=limit;
        p.buyRules=table(fees,"申购费率");p.saleRules=table(fees,"赎回费率");p.inferLock(name);p.updated=FundEngine.now().toLocalDate().toString();return p;
    }
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
    void validate(){for(String s:new String[]{established,scale,buyRules,saleRules,management,custody,service,updated,lockUnit})if(s==null||s.length()>10000)throw new IllegalArgumentException("基金资料无效");if(!established.isEmpty())LocalDate.parse(established);if(lockCount<0||lockCount>10000||lockCount>0&&!Arrays.asList("天","月","年").contains(lockUnit))throw new IllegalArgumentException("基金锁定期无效");}
}
