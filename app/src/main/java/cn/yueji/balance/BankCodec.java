package cn.yueji.balance;

import org.json.*;
import java.time.*;
import java.util.*;

final class BankCodec {
    static JSONObject encode(BankLedger.Product p)throws JSONException{
        JSONObject bases=new JSONObject();for(Map.Entry<Integer,Long> entry:p.bases.entrySet())bases.put(entry.getKey().toString(),entry.getValue());
        JSONObject json=new JSONObject().put("id",p.id).put("importId",p.importId).put("name",p.name).put("tag",p.tag).put("hidden",p.hidden).put("removedAt",p.removedAt==null?JSONObject.NULL:p.removedAt).put("bases",bases);
        JSONArray observations=new JSONArray(),events=new JSONArray();
        for(BankLedger.Observation o:p.observations){
            observations.put(new JSONObject().put("id",o.id).put("name",o.name).put("at",o.at).put("balance",o.balance).put("weekly",nullable(o.weekly)).put("cumulative",nullable(o.cumulative)).put("weekStart",o.weekStart).put("segment",o.segment)
                .put("reportDate",o.reportDate==null?o.at.toLocalDate():o.reportDate).put("daily",map(o.daily)).put("weeks",map(o.weeks)));
        }
        for(BankLedger.Event e:p.events)events.put(new JSONObject().put("id",e.id).put("kind",e.kind).put("at",e.at).put("completedAt",nullable(e.completedAt)).put("soldCost",nullable(e.soldCost)).put("oldName",e.oldName).put("newName",e.newName).put("note",e.note).put("amount",e.amount).put("segment",e.segment).put("closingProfit",nullable(e.closingProfit)));
        json.put("openingAccountLinked",p.openingAccountLinked).put("openingAccountId",p.openingAccountId);
        for(int i=0;i<events.length();i++){BankLedger.Event e=p.events.get(i);events.getJSONObject(i).put("accountLinked",e.accountLinked).put("accountId",e.accountId);}
        return json.put("observations",observations).put("events",events);
    }
    static Object nullable(Object value){return value==null?JSONObject.NULL:value;}
    static JSONObject map(Map<LocalDate,Long> values)throws JSONException{JSONObject json=new JSONObject();for(Map.Entry<LocalDate,Long> e:values.entrySet())json.put(e.getKey().toString(),e.getValue());return json;}
    static void readMap(JSONObject json,Map<LocalDate,Long> output)throws JSONException{if(json==null)return;Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();output.put(LocalDate.parse(key),json.getLong(key));}}
    static BankLedger.Product decode(JSONObject json)throws JSONException{
        BankLedger.Product p=new BankLedger.Product();p.id=json.getLong("id");p.importId=json.optLong("importId");p.name=json.getString("name");p.tag=json.optString("tag","银行理财");p.hidden=json.optBoolean("hidden");
        if(!json.isNull("removedAt"))p.removedAt=LocalDateTime.parse(json.getString("removedAt"));
        p.openingAccountLinked=json.optBoolean("openingAccountLinked");p.openingAccountId=json.optLong("openingAccountId");
        JSONObject bases=json.optJSONObject("bases");if(bases!=null){Iterator<String> keys=bases.keys();while(keys.hasNext()){String key=keys.next();p.bases.put(Integer.parseInt(key),bases.getLong(key));}}
        JSONArray observations=json.getJSONArray("observations");
        for(int i=0;i<observations.length();i++){
            JSONObject row=observations.getJSONObject(i);BankLedger.Observation o=new BankLedger.Observation();
            o.id=row.getLong("id");o.name=row.getString("name");o.at=LocalDateTime.parse(row.getString("at"));o.balance=row.getLong("balance");o.weekStart=LocalDate.parse(row.getString("weekStart"));o.segment=row.getInt("segment");o.reportDate=LocalDate.parse(row.optString("reportDate",o.at.toLocalDate().toString()));
            if(!row.isNull("weekly"))o.weekly=row.getLong("weekly");if(!row.isNull("cumulative"))o.cumulative=row.getLong("cumulative");readMap(row.optJSONObject("daily"),o.daily);readMap(row.optJSONObject("weeks"),o.weeks);p.observations.add(o);
        }
        JSONArray events=json.optJSONArray("events");if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject row=events.getJSONObject(i);BankLedger.Event e=new BankLedger.Event();e.id=row.getLong("id");e.kind=row.getString("kind");e.at=LocalDateTime.parse(row.getString("at"));e.oldName=row.optString("oldName");e.newName=row.optString("newName");e.note=row.optString("note");e.amount=row.optLong("amount");e.segment=row.getInt("segment");
            if(!row.isNull("closingProfit"))e.closingProfit=row.getLong("closingProfit");if(!row.isNull("completedAt"))e.completedAt=LocalDateTime.parse(row.getString("completedAt"));if(!row.isNull("soldCost"))e.soldCost=row.getLong("soldCost");p.events.add(e);
        }
        if(events!=null)for(int i=0;i<events.length();i++){JSONObject row=events.getJSONObject(i);BankLedger.Event e=p.events.get(i);e.accountLinked=row.optBoolean("accountLinked");e.accountId=row.optLong("accountId");}
        return p;
    }
    private BankCodec(){}
}
