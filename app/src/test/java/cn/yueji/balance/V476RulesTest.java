package cn.yueji.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class V476RulesTest {
    @Test public void existingOwnedAndWatchIdentitiesAreNotNewImports(){Ledger.Data data=new Ledger.Data();data.watchFunds.add(new Ledger.WatchFund("006431","自选"));Ledger.Fund fund=new Ledger.Fund();fund.code="000001";data.funds.add(fund);assertTrue(WatchlistScreens.containsCode(data,"006431"));assertTrue(WatchlistScreens.containsCode(data,"000001"));assertFalse(WatchlistScreens.containsCode(data,"007329"));}
    @Test public void duplicateMessageNeverClaimsSuccess(){assertEquals("该基金已在自选列表",WatchlistScreens.importMessage(0,1,0));assertFalse(WatchlistScreens.importMessage(0,3,0).contains("成功"));assertEquals("导入成功：2只基金，1只已在自选列表",WatchlistScreens.importMessage(2,1,0));assertTrue(WatchlistScreens.importMessage(0,0,1).startsWith("导入失败"));}
}
