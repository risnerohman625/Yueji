package cn.yueji.balance;

import org.json.*;
import java.time.*;

final class FundOrderCodec {
    static JSONArray encode(Ledger.Data data)throws JSONException{
        JSONArray array=new JSONArray();for(Ledger.FundOrder o:data.fundOrders)array.put(new JSONObject().put("id",o.id).put("sourceId",o.sourceId).put("accountId",o.accountId).put("fee",o.fee).put("targetBuyId",o.targetBuyId).put("netAmount",o.netAmount).put("targetCode",o.targetCode).put("targetName",o.targetName).put("status",o.status).put("error",o.error).put("at",o.at).put("receivedAt",o.receivedAt).put("tradeDate",o.tradeDate).put("confirmDate",o.confirmDate).put("buyIds",new JSONArray(o.buyIds)));return array;
    }
    static void decode(Ledger.Data data,JSONArray array)throws JSONException{
        if(array==null)return;for(int i=0;i<array.length();i++){JSONObject r=array.getJSONObject(i);Ledger.FundOrder o=new Ledger.FundOrder();o.id=r.getLong("id");o.sourceId=r.getLong("sourceId");o.accountId=r.getLong("accountId");o.fee=r.getLong("fee");o.targetBuyId=r.getLong("targetBuyId");o.netAmount=r.getLong("netAmount");o.targetCode=r.getString("targetCode");o.targetName=r.getString("targetName");o.status=r.getString("status");o.error=r.getString("error");o.at=LocalDateTime.parse(r.getString("at"));o.receivedAt=LocalDateTime.parse(r.getString("receivedAt"));o.tradeDate=LocalDate.parse(r.getString("tradeDate"));o.confirmDate=LocalDate.parse(r.getString("confirmDate"));JSONArray ids=r.getJSONArray("buyIds");for(int k=0;k<ids.length();k++)o.buyIds.add(ids.getLong(k));data.fundOrders.add(o);}
    }
    private FundOrderCodec(){}
}
