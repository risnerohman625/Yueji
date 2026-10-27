package cn.yueji.balance;

import org.json.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

final class FundCodec {
    static JSONObject encode(Ledger.Fund f)throws JSONException{
        JSONObject o=new JSONObject().put("id",f.id).put("code",f.code).put("name",f.name).put("cost",f.cost)
            .put("shares",f.shares.toPlainString()).put("nav",f.nav.toPlainString()).put("previous",f.previous.toPlainString())
            .put("navDate",f.navDate).put("error",f.error).put("updatedAt",f.updatedAt).put("historyComplete",f.historyComplete).put("fundType",f.fundType).put("hidden",f.hidden)
            .put("removedAt",f.removedAt==null?JSONObject.NULL:f.removedAt.toString()).put("frozenOn",f.frozenOn==null?JSONObject.NULL:f.frozenOn.toString()).put("tag",f.tag);
        JSONArray buys=new JSONArray();for(Ledger.FundBuy b:f.buys)buys.put(new JSONObject().put("id",b.id).put("amount",b.amount).put("fee",b.fee)
            .put("at",b.at).put("tradeDate",b.tradeDate==null?JSONObject.NULL:b.tradeDate.toString())
            .put("confirmDate",b.confirmDate==null?JSONObject.NULL:b.confirmDate.toString())
            .put("saleDate",b.saleDate==null?JSONObject.NULL:b.saleDate.toString()).put("saleAt",b.saleAt==null?JSONObject.NULL:b.saleAt.toString()).put("saleAmount",b.saleAmount)
            .put("units",b.units.toPlainString()).put("price",b.price.toPlainString()).put("overrideDates",b.overrideDates)
            .put("buyAccountLinked",b.buyAccountLinked).put("buyAccountId",b.buyAccountId)
            .put("saleAccountLinked",b.saleAccountLinked).put("saleAccountId",b.saleAccountId)
            .put("accountFlowManaged",b.accountFlowManaged).put("recurringRecordId",b.recurringRecordId));
        JSONArray planRecords=new JSONArray();
        for(Ledger.RecurringRecord r:f.plan.records)planRecords.put(new JSONObject().put("id",r.id).put("buyId",r.buyId).put("amount",r.amount)
            .put("accountId",r.accountId).put("date",r.date==null?JSONObject.NULL:r.date.toString()).put("status",r.status));
        JSONObject plan=new JSONObject().put("enabled",f.plan.enabled).put("amount",f.plan.amount).put("fee",f.plan.fee)
            .put("accountId",f.plan.accountId).put("dayOfMonth",f.plan.dayOfMonth).put("frequency",f.plan.frequency).put("totalPeriods",f.plan.totalPeriods)
            .put("startDate",f.plan.startDate).put("nextDate",f.plan.nextDate).put("records",planRecords);
        JSONObject prices=new JSONObject(),returns=new JSONObject(),dividends=new JSONObject(),splits=new JSONObject();
        for(Map.Entry<LocalDate,BigDecimal> e:f.prices.entrySet())prices.put(e.getKey().toString(),e.getValue().toPlainString());
        for(Map.Entry<LocalDate,BigDecimal> e:f.navReturns.entrySet())returns.put(e.getKey().toString(),e.getValue().toPlainString());
        for(Map.Entry<LocalDate,BigDecimal> e:f.dividends.entrySet())dividends.put(e.getKey().toString(),e.getValue().toPlainString());
        for(Map.Entry<LocalDate,BigDecimal> e:f.splits.entrySet())splits.put(e.getKey().toString(),e.getValue().toPlainString());
        return o.put("buys",buys).put("prices",prices).put("returns",returns).put("dividends",dividends).put("splits",splits).put("plan",plan).put("profile",f.profile.encode());
    }
    static Ledger.Fund decode(JSONObject o)throws JSONException{
        Ledger.Fund f=new Ledger.Fund();f.profile.read(o.optJSONObject("profile"));f.id=o.getLong("id");f.code=o.getString("code");f.name=o.getString("name");f.cost=o.optLong("cost");
        f.shares=new BigDecimal(o.optString("shares","0"));f.nav=new BigDecimal(o.optString("nav","0"));f.previous=new BigDecimal(o.optString("previous","0"));
        f.navDate=o.optString("navDate");f.error=o.optString("error");f.updatedAt=o.optString("updatedAt");f.fundType=o.optString("fundType");f.historyComplete=o.optBoolean("historyComplete");f.hidden=o.optBoolean("hidden");f.tag=o.optString("tag","");
        if(!o.isNull("removedAt")&&!o.optString("removedAt").isEmpty())f.removedAt=LocalDateTime.parse(o.getString("removedAt"));
        if(!o.isNull("frozenOn")&&!o.optString("frozenOn").isEmpty())f.frozenOn=LocalDate.parse(o.getString("frozenOn"));
        JSONArray buys=o.optJSONArray("buys");if(buys!=null)for(int i=0;i<buys.length();i++){JSONObject r=buys.getJSONObject(i);Ledger.FundBuy b=new Ledger.FundBuy();
            b.id=r.getLong("id");b.amount=r.getLong("amount");b.fee=r.optLong("fee");b.at=LocalDateTime.parse(r.getString("at"));b.units=new BigDecimal(r.optString("units","0"));b.price=new BigDecimal(r.optString("price","0"));
            if(!r.isNull("tradeDate"))b.tradeDate=LocalDate.parse(r.getString("tradeDate"));if(!r.isNull("confirmDate"))b.confirmDate=LocalDate.parse(r.getString("confirmDate"));
            if(!r.isNull("saleDate"))b.saleDate=LocalDate.parse(r.getString("saleDate"));if(!r.isNull("saleAt"))b.saleAt=LocalDateTime.parse(r.getString("saleAt"));b.saleAmount=r.optLong("saleAmount");b.overrideDates=r.optBoolean("overrideDates");
            b.buyAccountLinked=r.optBoolean("buyAccountLinked");b.buyAccountId=r.optLong("buyAccountId");
            b.saleAccountLinked=r.optBoolean("saleAccountLinked");b.saleAccountId=r.optLong("saleAccountId");
            b.accountFlowManaged=r.optBoolean("accountFlowManaged");b.recurringRecordId=r.optLong("recurringRecordId");f.buys.add(b);}
        JSONObject plan=o.optJSONObject("plan");
        if(plan!=null){
            f.plan.enabled=plan.optBoolean("enabled");f.plan.amount=plan.optLong("amount");f.plan.fee=plan.optLong("fee");
            f.plan.accountId=plan.optLong("accountId");f.plan.dayOfMonth=plan.optInt("dayOfMonth",1);
            f.plan.totalPeriods=plan.optInt("totalPeriods");f.plan.frequency=plan.optString("frequency","每月");f.plan.startDate=plan.optString("startDate");f.plan.nextDate=plan.optString("nextDate");
            JSONArray records=plan.optJSONArray("records");if(records!=null)for(int i=0;i<records.length();i++){
                JSONObject r=records.getJSONObject(i);Ledger.RecurringRecord record=new Ledger.RecurringRecord();
                record.id=r.getLong("id");record.buyId=r.optLong("buyId");record.amount=r.getLong("amount");record.accountId=r.getLong("accountId");
                if(!r.isNull("date"))record.date=LocalDate.parse(r.getString("date"));record.status=r.optString("status","待执行");f.plan.records.add(record);
            }
        }
        JSONObject prices=o.optJSONObject("prices");if(prices!=null){Iterator<String> keys=prices.keys();while(keys.hasNext()){String k=keys.next();f.prices.put(LocalDate.parse(k),new BigDecimal(prices.getString(k)));}}
        JSONObject returns=o.optJSONObject("returns");if(returns!=null){Iterator<String> keys=returns.keys();while(keys.hasNext()){String k=keys.next();f.navReturns.put(LocalDate.parse(k),new BigDecimal(returns.getString(k)));}}
        JSONObject dividends=o.optJSONObject("dividends");if(dividends!=null){Iterator<String> keys=dividends.keys();while(keys.hasNext()){String k=keys.next();f.dividends.put(LocalDate.parse(k),new BigDecimal(dividends.getString(k)));}}
        JSONObject splits=o.optJSONObject("splits");if(splits!=null){Iterator<String> keys=splits.keys();while(keys.hasNext()){String k=keys.next();f.splits.put(LocalDate.parse(k),new BigDecimal(splits.getString(k)));}}
        if(f.prices.isEmpty()&&!f.navDate.isEmpty()&&f.nav.signum()>0)f.prices.put(LocalDate.parse(f.navDate),f.nav);
        return f;
    }
    static void validate(Ledger.Fund f){
        f.profile.validate();
        if(f.tag==null||f.tag.length()>12||f.tag.contains("\n"))throw new IllegalArgumentException("基金标签最多12个字，不能换行");
        if(!f.code.matches("[0-9]{6}")||f.id<=0||f.name.trim().isEmpty()||f.cost<0||f.cost>Ledger.MAX_MONEY||f.shares.signum()<0||f.shares.compareTo(new BigDecimal("100000000000"))>0||f.nav.signum()<0||f.previous.signum()<0)throw new IllegalArgumentException("基金数据无效");
        if((f.removedAt==null)!=(f.frozenOn==null))throw new IllegalArgumentException("基金删除时间与收益截止日不一致");
        Set<Long> ids=new HashSet<>();for(Ledger.FundBuy b:f.buys){
            if(b.id<=0||!ids.add(b.id)||b.at==null||b.at.getYear()<2000||b.at.isAfter(FundEngine.now())||b.amount<=0||b.amount>Ledger.MAX_MONEY||b.fee<0||b.fee>=b.amount||b.units.signum()<0||b.units.compareTo(new BigDecimal("100000000000"))>0)throw new IllegalArgumentException("买入金额、手续费或时间无效");
            if(b.tradeDate!=null&&b.tradeDate.isBefore(b.at.toLocalDate())||b.confirmDate!=null&&(b.tradeDate==null||!b.confirmDate.isAfter(b.tradeDate)))throw new IllegalArgumentException("确认日期应晚于交易日期");
            if(b.saleDate!=null&&(b.confirmDate==null||b.saleDate.isBefore(b.confirmDate)||b.saleDate.isAfter(FundEngine.now().toLocalDate())||b.saleAmount<0||b.saleAmount>Ledger.MAX_MONEY))throw new IllegalArgumentException("卖出时间或到账金额无效");
            if(b.buyAccountLinked!=(b.buyAccountId>0)||b.saleAccountLinked!=(b.saleAccountId>0))throw new IllegalArgumentException("基金交易账户关联状态无效");
            if(b.saleAt!=null&&(b.saleDate==null||b.saleAt.toLocalDate().isBefore(b.saleDate)||b.saleAt.isAfter(FundEngine.now())))throw new IllegalArgumentException("卖出到账时间无效");
            if(b.saleAccountLinked&&b.saleDate==null)throw new IllegalArgumentException("未卖出的买入记录不能关联到账账户");
        }
        if(f.buys.isEmpty()&&f.shares.signum()<=0)throw new IllegalArgumentException("旧持仓份额无效");
        if(f.plan.enabled&&(f.plan.amount<=0||f.plan.amount>Ledger.MAX_MONEY||f.plan.fee<0||f.plan.fee>=f.plan.amount||f.plan.accountId<=0||f.plan.dayOfMonth<1||f.plan.dayOfMonth>28||!Arrays.asList("每日","每周","每月").contains(f.plan.frequency)))throw new IllegalArgumentException("定投配置无效");
        if(f.plan.records.size()>10000)throw new IllegalArgumentException("定投记录数量超限");
        for(Ledger.RecurringRecord record:f.plan.records)if(record.id<=0||record.amount<=0||record.amount>Ledger.MAX_MONEY||record.accountId<=0||record.date==null||record.status==null||record.status.length()>20)throw new IllegalArgumentException("定投记录无效");
        if(f.prices.size()>30000||f.buys.size()>1000)throw new IllegalArgumentException("基金数据量超限");
        for(BigDecimal price:f.prices.values())if(price.signum()<=0||price.compareTo(new BigDecimal("1000000"))>0)throw new IllegalArgumentException("净值超出范围");
    }
    private FundCodec(){}
}
