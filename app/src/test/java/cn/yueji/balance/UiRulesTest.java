package cn.yueji.balance;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class UiRulesTest {
    Ledger.Fund fund(long id,String time,long amount,String nav){
        Ledger.Fund f=new Ledger.Fund();f.id=id;f.name="基金"+id;f.code=String.format("%06d",id);
        Ledger.FundBuy b=new Ledger.FundBuy();b.id=100+id;b.at=LocalDateTime.parse(time);b.amount=amount;f.buys.add(b);
        f.prices.put(b.at.toLocalDate(),BigDecimal.ONE);f.prices.put(LocalDate.of(2026,10,20),new BigDecimal(nav));
        f.navDate="2026-10-20";f.nav=new BigDecimal(nav);FundEngine.resolve(f,new TradingCalendar());return f;
    }
    Ledger.Data funds(){
        Ledger.Data d=new Ledger.Data();d.funds.add(fund(1,"2026-09-01T10:00",10000,"1.20"));d.funds.add(fund(2,"2026-09-10T10:00",30000,"0.90"));return d;
    }
    @Test public void hiddenFundsLeaveHomeButSortLastInHistory(){
        Ledger.Data d=funds();d.funds.get(1).hidden=true;
        assertEquals(1,FundRules.sorted(d,1,false,LocalDate.of(2026,10,20)).size());
        assertEquals(2,FundRules.sorted(d,1,true,LocalDate.of(2026,10,20)).get(1).id);
    }
    @Test public void allThreeFundSortsUseTheirActualDefinitions(){
        Ledger.Data d=funds();LocalDate at=LocalDate.of(2026,10,20);
        assertEquals(2,FundRules.sorted(d,0,false,at).get(0).id);
        assertEquals(2,FundRules.sorted(d,1,false,at).get(0).id);
        assertEquals(1,FundRules.sorted(d,2,false,at).get(0).id);
    }
    @Test public void permanentDeletionRequiresHomeRemoval(){
        Ledger.Fund f=funds().funds.get(0);ImportTest.rejects("请先从基金首页删除",()->FundRules.requirePurge(f));
        FundEngine.remove(f,LocalDateTime.of(2026,10,20,12,0));FundRules.requirePurge(f);
    }
    @Test public void lastBuyCannotBeAPermanentDeletionBackdoor(){
        Ledger.Fund f=funds().funds.get(0);
        ImportTest.rejects("最后一笔",()->FundRules.requireRemoveBuy(f,f.buys.get(0).id));
        Ledger.FundBuy extra=new Ledger.FundBuy();extra.id=300;extra.at=LocalDateTime.of(2026,10,1,12,0);extra.amount=1000;f.buys.add(extra);
        FundRules.requireRemoveBuy(f,extra.id);
        FundEngine.remove(f,LocalDateTime.of(2026,10,20,12,0));
        ImportTest.rejects("恢复后",()->FundRules.requireRemoveBuy(f,extra.id));
    }
    @Test public void sparklineExcludesHiddenAndRemovedAndNeverBackfillsFutureNav(){
        Ledger.Data d=funds();d.funds.get(1).hidden=true;
        List<Long> points=FundRules.trend(d,LocalDate.of(2026,10,20));assertEquals(Long.valueOf(12000),points.get(points.size()-1));
        d.funds.get(0).prices.clear();d.funds.get(0).buys.clear();d.funds.get(0).shares=BigDecimal.TEN;d.funds.get(0).cost=1000;
        List<Long> noHistory=FundRules.trend(d,LocalDate.of(2026,10,20));assertEquals(1,noHistory.size());
        FundEngine.remove(d.funds.get(0),LocalDateTime.of(2026,10,20,12,0));assertTrue(FundRules.trend(d,LocalDate.of(2026,10,20)).isEmpty());
    }
    @Test public void searchMatchesNotesNamesTagsAndAccountWithMonthIntersection(){
        Ledger.Data d=new Ledger.Data();Ledger.Account account=new Ledger.Account(1,"主账户","现金");d.accounts.add(account);
        Ledger.Entry a=new Ledger.Entry(2,true,10000,LocalDateTime.of(2026,9,1,10,0),"朋友转账","朋友AbC转账",1);a.category="生活费";d.entries.add(a);
        Ledger.Entry b=new Ledger.Entry(3,true,20000,LocalDateTime.of(2026,10,1,10,0),"工资","",1);b.category="工资";d.entries.add(b);
        assertEquals(1,RecordQuery.filter(d,Ledger.INCOME,null,"abc","").size());
        assertEquals(0,RecordQuery.filter(d,Ledger.INCOME,null,"abc","2026-10").size());
        assertEquals(1,RecordQuery.filter(d,Ledger.INCOME,"生活费","主账户","2026-09").size());
        assertEquals(2,RecordQuery.filter(d,Ledger.INCOME,null,"","").size());
        assertTrue(RecordQuery.filter(d,Ledger.INCOME,null,"待报销","").isEmpty());
    }
    @Test public void iconMigrationRepairsDuplicatesWithoutStealingExistingChoices(){
        Ledger.Data d=new Ledger.Data();d.categoryIcons.put(d.categories.get(1),d.iconOf(d.categories.get(0)));
        d.ensureIcons();assertEquals(d.categories.size(),new HashSet<>(d.categoryIcons.values()).size());
        String preserved=d.iconOf(d.categories.get(2));d.categories.add("家教");d.ensureIcons();
        assertEquals(preserved,d.iconOf(d.categories.get(2)));assertEquals(d.categories.size(),new HashSet<>(d.categoryIcons.values()).size());
    }
}
