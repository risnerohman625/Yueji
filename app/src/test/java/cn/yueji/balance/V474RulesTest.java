package cn.yueji.balance;

import org.json.*;
import org.junit.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class V474RulesTest {
    Ledger.Data watchData(){
        Ledger.Data data=new Ledger.Data();
        Ledger.WatchFund fund=new Ledger.WatchFund("006431","汇安鼎利纯债债券A");
        fund.nav=new BigDecimal("1.1121");fund.previous=new BigDecimal("1.1120");fund.navDate="2026-10-23";
        fund.prices.put(LocalDate.of(2026,10,22),fund.previous);fund.prices.put(LocalDate.of(2026,10,23),fund.nav);
        fund.returns.put(LocalDate.of(2026,10,23),new BigDecimal("0.01"));
        fund.profile.established="2019-01-01";fund.profile.buyRules="小于100万元  0.30%  0.03%";
        data.watchFunds.add(fund);
        Ledger.WatchGroup group=new Ledger.WatchGroup(7,"债券");group.codes.add(fund.code);data.watchGroups.add(group);
        data.watchRange=7;data.watchSort=2;data.watchDescending=false;
        return data;
    }
    @Test public void backupContainsCompleteWatchDataAndRoundTrips(){
        Ledger.Data data=watchData();LedgerStore.validate(data);
        String json=LedgerStore.encode(data);Ledger.Data restored=LedgerStore.decode(json);LedgerStore.validate(restored);
        assertEquals(json,LedgerStore.encode(restored));
        assertEquals("006431",restored.watchGroups.get(0).codes.iterator().next());
        assertEquals(2,restored.watchFunds.get(0).prices.size());assertEquals(1,restored.watchFunds.get(0).returns.size());
        assertEquals(7,restored.watchRange);assertEquals(2,restored.watchSort);assertFalse(restored.watchDescending);
    }
    @Test public void everySupportedOldVersionWithoutWatchFieldsLoadsAsEmpty()throws Exception{
        for(int version=1;version<=11;version++){
            JSONObject root=new JSONObject(LedgerStore.encode(new Ledger.Data()));root.put("version",version);
            for(String key:new String[]{"watchFunds","watchGroups","watchRange","watchSort","watchDescending"})root.remove(key);
            Ledger.Data restored=LedgerStore.decode(root.toString());LedgerStore.validate(restored);
            assertTrue(restored.watchFunds.isEmpty());assertTrue(restored.watchGroups.isEmpty());assertEquals(4,restored.watchRange);
        }
    }
    @Test public void malformedWatchArraysCannotSilentlyEraseData()throws Exception{
        for(Object bad:new Object[]{JSONObject.NULL,new JSONObject(),"bad"}){
            JSONObject root=new JSONObject(LedgerStore.encode(watchData()));root.put("watchFunds",bad);
            try{LedgerStore.decode(root.toString());fail("invalid array accepted");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("watchFunds"));}
        }
    }
    @Test public void unknownWatchCodesAndDuplicateIdentitiesAreRejected(){
        Ledger.Data data=watchData();data.watchGroups.get(0).codes.add("999999");
        try{LedgerStore.validate(data);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("代码不存在"));}
        data=watchData();data.watchFunds.add(new Ledger.WatchFund("006431","重复"));
        try{LedgerStore.validate(data);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("自选"));}
    }
    @Test public void invalidOrFutureWatchNavDatesAreRejected(){
        for(String date:new String[]{"broken","2099-01-01"}){
            Ledger.Data data=watchData();data.watchFunds.get(0).navDate=date;
            try{LedgerStore.validate(data);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("日期"));}
        }
    }
    @Test public void manualBankIncomeAcceptsNegativeValuesButNoExtraSigns(){
        assertEquals(-104,BankScreens.signed("-1.04"));assertEquals(104,BankScreens.signed("+1.04"));assertNull(BankScreens.optional(" "));
        try{BankScreens.signed("--1.04");fail();}catch(IllegalArgumentException expected){}
    }
}
