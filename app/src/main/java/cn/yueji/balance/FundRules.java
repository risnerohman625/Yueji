package cn.yueji.balance;

import java.time.*;
import java.util.*;

final class FundRules {
    static int nextSort(int current){return current==2?1:current==1?0:2;}
    static List<Ledger.Fund> sorted(Ledger.Data data,int order,boolean history,LocalDate today){
        List<Ledger.Fund> funds=new ArrayList<>();
        for(Ledger.Fund f:data.funds)if(history||!f.removed()&&!f.hidden)funds.add(f);
        Comparator<Ledger.Fund> byTime=Comparator.comparing(FundRules::firstBuy,Comparator.reverseOrder()).thenComparingLong(f->f.id);
        Comparator<Ledger.Fund> compare=byTime;
        if(order==1)compare=Comparator.comparingLong((Ledger.Fund f)->FundEngine.value(f,FundEngine.clamp(f,today))).reversed().thenComparing(byTime);
        if(order==2)compare=Comparator.comparingLong((Ledger.Fund f)->FundEngine.heldProfit(f,FundEngine.clamp(f,today))).reversed().thenComparing(byTime);
        if(history)compare=Comparator.comparing((Ledger.Fund f)->f.hidden).thenComparing(compare);
        funds.sort(compare);return funds;
    }
    static LocalDateTime firstBuy(Ledger.Fund f){
        LocalDateTime first=null;
        for(Ledger.FundBuy b:f.buys)if(first==null||b.at.isBefore(first))first=b.at;
        return first==null?LocalDateTime.MIN:first;
    }
    static void requirePurge(Ledger.Fund f){
        if(!f.removed())throw new IllegalArgumentException("请先从基金首页删除，再到累计收益中永久删除");
    }
    static void requireRemoveBuy(Ledger.Fund f,long id){
        if(f.removed())throw new IllegalArgumentException("基金已从首页删除，恢复后才能修改交易");
        boolean found=false;for(Ledger.FundBuy b:f.buys)if(b.id==id)found=true;
        if(!found)throw new IllegalArgumentException("买入记录不存在");
        if(f.buys.size()==1)throw new IllegalArgumentException("这是最后一笔买入，不能通过交易记录清空基金；请先从首页删除，再到累计收益中永久删除");
    }
    /** Only historically known valuations enter the curve. No latest-NAV backfill before its publication date. */
    static List<Long> trend(Ledger.Data data,LocalDate today){
        List<Ledger.Fund> funds=new ArrayList<>();LocalDate first=today;
        for(Ledger.Fund f:data.funds)if(!f.hidden&&!f.removed()){
            funds.add(f);LocalDate start=FundEngine.firstDate(f);if(start!=null&&start.isBefore(first))first=start;
        }
        List<BankLedger.Product> banks=new ArrayList<>();
        for(BankLedger.Product p:data.banks)if(!p.hidden&&!p.removed()){banks.add(p);LocalDate start=p.observations.get(0).at.toLocalDate();if(start.isBefore(first))first=start;}
        if(funds.isEmpty()&&banks.isEmpty())return Collections.emptyList();
        first=first.isBefore(today.minusDays(179))?today.minusDays(179):first;
        List<Long> points=new ArrayList<>();
        for(LocalDate day=first;!day.isAfter(today);day=day.plusDays(1)){
            long total=0;boolean known=true;
            for(Ledger.Fund f:funds)if(FundEngine.held(f,day)){
                if(!FundEngine.known(f,day)){known=false;break;}
                if(FundEngine.units(f,day).signum()>0&&f.prices.floorEntry(day)==null&&(!f.navDate.equals(day.toString())||f.nav.signum()<=0)){known=false;break;}
                total=Math.addExact(total,FundEngine.value(f,day));
            }
            for(BankLedger.Product p:banks){BankLedger.Observation last=null;for(BankLedger.Observation o:p.observations)if(!o.at.toLocalDate().isAfter(day))last=o;if(last!=null)total=Math.addExact(total,last.balance);}
            points.add(known?total:null);
        }
        return points;
    }
    private FundRules(){}
}
