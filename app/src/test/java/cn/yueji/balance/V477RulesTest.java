package cn.yueji.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class V477RulesTest {
    Ledger.Data sample(){Ledger.Data d=new Ledger.Data();d.watchFunds.add(new Ledger.WatchFund("006431","测试"));d.watchGroups.add(new Ledger.WatchGroup(1,"债券"));return d;}
    @Test public void inboxMovesAreExplicitAndRoundTrip(){Ledger.Data d=sample();assertTrue(WatchGroups.member(d,"006431","全部"));WatchGroups.move(d,"006431","全部",1,true);assertFalse(WatchGroups.member(d,"006431","全部"));assertTrue(WatchGroups.member(d,"006431","债券"));WatchGroups.move(d,"006431","债券",0,false);d=LedgerStore.decode(LedgerStore.encode(d));assertTrue(WatchGroups.member(d,"006431","全部"));assertTrue(WatchGroups.member(d,"006431","债券"));WatchGroups.move(d,"006431","全部",1,true);assertFalse(WatchGroups.member(d,"006431","全部"));}
    @Test public void heldDefaultsOutsideInboxButCanBeAdded(){Ledger.Data d=sample();Ledger.Fund f=new Ledger.Fund();f.code="006431";d.funds.add(f);assertTrue(WatchGroups.member(d,f.code,"持有"));assertFalse(WatchGroups.member(d,f.code,"全部"));WatchGroups.move(d,f.code,"持有",0,true);assertTrue(WatchGroups.member(d,f.code,"全部"));assertTrue(WatchGroups.member(d,f.code,"持有"));}
    @Test public void copyFromInboxCanKeepMembership(){Ledger.Data d=sample();WatchGroups.move(d,"006431","全部",1,false);assertTrue(WatchGroups.member(d,"006431","全部"));assertTrue(WatchGroups.member(d,"006431","债券"));d.watchGroups.clear();assertTrue(WatchGroups.member(d,"006431","全部"));}
    @Test public void partialProfileDoesNotEraseKnownFacts(){FundProfile p=new FundProfile();p.established="2020-01-01";p.scale="4亿元";p.management="0.3%";FundProfile fresh=new FundProfile();fresh.updated="2026-10-27";fresh.scale="5亿元";p.merge(fresh);assertEquals("2020-01-01",p.established);assertEquals("5亿元",p.scale);assertEquals("0.3%",p.management);}
    @Test public void oldBackupDefaultsInboxAndMalformedMembershipIsRejected()throws Exception{Ledger.Data d=sample();org.json.JSONObject old=new org.json.JSONObject(LedgerStore.encode(d));old.put("version",11);old.remove("watchAllIncluded");assertTrue(WatchGroups.member(LedgerStore.decode(old.toString()),"006431","全部"));old.put("watchAllIncluded","not an array");try{LedgerStore.decode(old.toString());fail();}catch(IllegalArgumentException expected){}d.watchAllIncluded.add("999999");try{WatchValidation.validate(d);fail();}catch(IllegalArgumentException expected){}}
}
