package cn.yueji.balance;

import java.time.*;
import java.util.*;
import org.json.*;

/** A transfer is one atomic source record, never an income or a consumption entry. */
final class TransferLedger {
    static Ledger.Transfer find(Ledger.Data data,long id){for(Ledger.Transfer t:data.transfers)if(t.id==id)return t;return null;}
    static String description(Ledger.Data data,Ledger.Transfer t){return data.accountName(t.fromId)+"向"+data.accountName(t.toId)+"转账"+Ledger.decimal(t.amount)+"元";}
    static Ledger.Transfer save(Ledger.Data data,long id,long from,long to,long amount,LocalDateTime at,String note){
        Ledger.Transfer t=new Ledger.Transfer();t.id=id==0?data.nextId():id;t.fromId=from;t.toId=to;t.amount=amount;t.at=at;t.note=note==null?"":note.trim();
        if(id!=0&&find(data,id)==null)throw new IllegalArgumentException("原转账已删除，请重新打开");
        check(data,t);data.transfers.removeIf(v->v.id==t.id);data.transfers.add(t);return t;
    }
    static void check(Ledger.Data data,Ledger.Transfer t){
        if(t.id<=0||t.fromId==t.toId||t.amount<=0||t.amount>Ledger.MAX_MONEY||t.at==null||t.at.getYear()<2000||t.at.isAfter(LocalDateTime.now())||t.note.length()>1000)throw new IllegalArgumentException("转账账户、时间或金额无效，转出与转入账户必须不同");
        AccountCash.requireAccount(data,t.fromId,t.at);AccountCash.requireAccount(data,t.toId,t.at);
    }
    static void validate(Ledger.Data data){if(data.transfers.size()>50000)throw new IllegalArgumentException("转账记录超过上限");Set<Long> ids=new HashSet<>();for(Ledger.Transfer t:data.transfers){if(!ids.add(t.id))throw new IllegalArgumentException("转账标识重复");check(data,t);}}
    static JSONArray encode(Ledger.Data data)throws JSONException{JSONArray a=new JSONArray();for(Ledger.Transfer t:data.transfers)a.put(new JSONObject().put("id",t.id).put("fromId",t.fromId).put("toId",t.toId).put("amount",t.amount).put("at",t.at.toString()).put("note",t.note));return a;}
    static void decode(Ledger.Data data,JSONArray a)throws JSONException{if(a==null)return;for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Ledger.Transfer t=new Ledger.Transfer();t.id=number(o,"id");t.fromId=number(o,"fromId");t.toId=number(o,"toId");t.amount=number(o,"amount");t.at=LocalDateTime.parse(o.getString("at"));if(o.has("note")&&!(o.get("note") instanceof String))throw new IllegalArgumentException("转账备注必须是文字");t.note=o.optString("note");data.transfers.add(t);}}
    static long number(JSONObject o,String key)throws JSONException{Object v=o.get(key);if(!(v instanceof Number))throw new IllegalArgumentException("转账"+key+"必须是整数");try{return new java.math.BigDecimal(v.toString()).longValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException("转账"+key+"必须是有效整数");}}
    private TransferLedger(){}
}
