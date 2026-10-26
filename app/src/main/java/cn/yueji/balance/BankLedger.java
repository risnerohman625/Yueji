package cn.yueji.balance;

import java.math.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

/** Screenshot observations are evidence, not NAVs. Missing profits and cash-flow corrections stay explicit. */
final class BankLedger {
    static final class Product {
        long id,importId;String name="",tag="银行理财";
        boolean hidden;LocalDateTime removedAt;
        boolean openingAccountLinked;long openingAccountId;
        final List<Observation> observations=new ArrayList<>();
        final List<Event> events=new ArrayList<>();
        final Map<Integer,Long> bases=new TreeMap<>();
        boolean removed(){return removedAt!=null;}
    }
    static final class Observation {
        long id,balance;LocalDateTime at;String name="";
        Long weekly,cumulative;
        LocalDate weekStart;
        int segment;
        final SortedMap<LocalDate,Long> daily=new TreeMap<>();
        final SortedMap<LocalDate,Long> weeks=new TreeMap<>();
        LocalDate reportDate;
    }
    static final class Event {
        long id;String kind="",oldName="",newName="",note="";
        LocalDateTime at;
        int segment;
        long amount;
        Long closingProfit;
        LocalDateTime completedAt;Long soldCost;
        boolean accountLinked;long accountId;
    }
    static final class Draft {
        String name="";Long balance,weekly,cumulative;LocalDateTime at=FundEngine.now();
        final SortedMap<LocalDate,Long> daily=new TreeMap<>();
        final SortedMap<LocalDate,Long> weeks=new TreeMap<>();
    }
    static Product find(Ledger.Data data,long id){for(Product p:data.banks)if(p.id==id)return p;return null;}
    static Observation latest(Product p){return p.observations.isEmpty()?null:p.observations.get(p.observations.size()-1);}
    static int segment(Product p){Observation latest=latest(p);return latest==null?0:latest.segment;}
    static LocalDate monday(LocalDate date){return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
    static long balance(Product p){return BankAccounting.value(p,FundEngine.now());}
    static long profit(Product p){
        long sum=0;for(int s=0;s<=segment(p);s++){Long closed=null;
            for(Event event:p.events)if(event.kind.equals("CHANGE")&&event.segment==s)closed=event.closingProfit;
            if(closed!=null){sum=Math.addExact(sum,closed);continue;}
            Long cumulative=null;LocalDate through=null;Map<LocalDate,Long> weeks=new TreeMap<>();
            for(Observation o:p.observations)if(o.segment==s){
                if(o.cumulative!=null&&(through==null||!BankAccounting.date(o).isBefore(through))){cumulative=o.cumulative;through=BankAccounting.date(o);}
                boolean transition=false;for(Event e:p.events)if(e.kind.equals("CHANGE")&&monday(e.at.toLocalDate()).equals(o.weekStart))transition=true;
                if(o.weekly!=null&&!transition)weeks.put(o.weekStart,o.weekly);
            }
            if(cumulative!=null)sum=Math.addExact(sum,cumulative);
            for(Map.Entry<LocalDate,Long> week:weeks.entrySet())if(through==null||week.getKey().isAfter(monday(through)))sum=Math.addExact(sum,week.getValue());
        }return sum;
    }
    static boolean incomplete(Product p){
        for(Event e:p.events)if(e.kind.equals("CHANGE")&&e.closingProfit==null)return true;
        Observation latest=latest(p);return latest==null||latest.cumulative==null;
    }
    static Long week(Product p,LocalDate start){
        Observation found=null;
        for(Observation o:p.observations)if(o.weekStart.equals(start)&&o.weekly!=null)found=o;
        // A platform's "this week" may span a sale and repurchase. Do not allocate it across that transition.
        for(Event e:p.events)if(e.kind.equals("CHANGE")&&!e.at.toLocalDate().isBefore(start)&&e.at.toLocalDate().isBefore(start.plusDays(7)))return null;
        Long explicit=null;for(Observation o:p.observations)if(o.weeks.containsKey(start))explicit=o.weeks.get(start);
        if(explicit!=null)return explicit;
        return found==null?null:found.weekly;
    }
    static Long dailyAverage(Product p,LocalDate day){
        return daily(p,day);
    }
    static Long daily(Product p,LocalDate day){Long found=null;for(Observation o:p.observations){Long value=o.daily.get(day);if(value!=null)found=value;}return found;}
    static Long transitionTotal(Observation baseline,long redemption,long additionalIn,long cashOut){
        if(baseline==null||baseline.cumulative==null)return null;
        // Only a complete prior segment total plus explicit settlement and cash flows can close the old segment.
        return Math.addExact(baseline.cumulative,Math.addExact(Math.subtractExact(redemption,baseline.balance),Math.subtractExact(cashOut,additionalIn)));
    }
    static Product apply(Ledger.Data data,long id,Draft draft,boolean productChange,Long closingProfit){
        if(draft.name==null||draft.name.trim().isEmpty()||draft.name.length()>100)throw new IllegalArgumentException("请核对产品名称");
        if(draft.balance==null||draft.balance<0||draft.balance>Ledger.MAX_MONEY)throw new IllegalArgumentException("请核对总金额");
        if(draft.at==null||draft.at.isAfter(FundEngine.now())||draft.at.getYear()<2000)throw new IllegalArgumentException("记录时间不能在未来");
        Product p=id==0?null:find(data,id);if(id!=0&&(p==null||p.removed()))throw new IllegalArgumentException("产品已移除，请先恢复");
        if(p==null){p=new Product();p.id=data.nextId();data.banks.add(p);}
        Observation prior=latest(p);String name=draft.name.trim();int segment=segment(p);
        boolean changed=prior!=null&&!normalizeName(prior.name).equals(normalizeName(name));
        if(prior!=null&&draft.at.equals(prior.at)&&changed)throw new IllegalArgumentException("产品变更时间须晚于上一条数据记录");
        if(prior!=null&&draft.at.isBefore(prior.at))throw new IllegalArgumentException("更新不能早于上一张已确认数据；请到记录中修正");
        if(changed&&!productChange)throw new IllegalArgumentException("产品名称发生变化，请核对后确认产品变更");
        if(changed){
            Event event=new Event();event.id=data.nextId();event.kind="CHANGE";event.at=draft.at;event.segment=segment;
            event.oldName=prior.name;event.newName=name;event.closingProfit=closingProfit;
            event.note=closingProfit==null?"过渡期收益待确认，未用余额差计算":"旧产品累计收益已核对结算";
            p.events.add(event);segment++;
        }
        Observation o=new Observation();o.id=data.nextId();o.name=name;o.balance=draft.balance;o.weekly=draft.weekly;o.cumulative=draft.cumulative;o.daily.putAll(draft.daily);o.weeks.putAll(draft.weeks);o.reportDate=draft.daily.isEmpty()?draft.at.toLocalDate():draft.daily.lastKey();
        o.at=draft.at;o.weekStart=monday(draft.at.toLocalDate());o.segment=segment;
        if(prior!=null&&prior.at.equals(o.at)&&prior.segment==segment)p.observations.remove(prior);
        p.observations.add(o);p.name=name;return p;
    }
    static void flow(Ledger.Data data,Product product,long amount,boolean into,LocalDateTime at){
        if(product.removed()||amount<=0||amount>Ledger.MAX_MONEY||at.isAfter(FundEngine.now()))throw new IllegalArgumentException("资金变动金额或时间无效");
        Event e=new Event();e.id=data.nextId();e.kind=into?"IN":"OUT";e.at=at;e.amount=amount;e.segment=segment(product);
        e.note="仅记录理财资金变动，不将余额变化自动当作收益";product.events.add(e);
    }
    static SortedMap<LocalDate,Long> profitSeries(Product p){
        SortedMap<LocalDate,Long> values=new TreeMap<>();
        Product prefix=new Product();prefix.id=p.id;prefix.name=p.name;
        for(Observation o:p.observations){
            prefix.observations.add(o);prefix.events.clear();
            boolean gap=false;for(Event e:p.events)if(!e.at.isAfter(o.at)){prefix.events.add(e);if(e.kind.equals("CHANGE")&&e.closingProfit==null)gap=true;}
            boolean known=o.cumulative!=null||o.weekly!=null;
            values.put(o.at.toLocalDate(),gap||!known?null:profit(prefix));
        }
        return values;
    }
    static void validate(Product p){
        if(p.id<=0||p.name.trim().isEmpty()||p.name.length()>100||p.tag.length()>12||p.observations.isEmpty()||p.observations.size()>5000||p.events.size()>5000)throw new IllegalArgumentException("银行理财记录无效");
        LocalDateTime last=null;Set<Long> ids=new HashSet<>();
        for(Observation o:p.observations){
            if(o.id<=0||!ids.add(o.id)||o.balance<0||o.balance>Ledger.MAX_MONEY||o.name.trim().isEmpty()||o.segment<0||o.at==null||o.at.isAfter(FundEngine.now())||last!=null&&o.at.isBefore(last)||o.weekStart==null||!o.weekStart.equals(monday(o.reportDate==null?o.at.toLocalDate():o.reportDate)))throw new IllegalArgumentException("银行理财数据观测无效");
            for(Long value:new Long[]{o.weekly,o.cumulative})if(value!=null&&(value>Ledger.MAX_MONEY||value< -Ledger.MAX_MONEY))throw new IllegalArgumentException("银行理财收益超出范围");
            last=o.at;
            for(Map.Entry<LocalDate,Long> day:o.daily.entrySet())if(day.getKey().isAfter(FundEngine.now().toLocalDate())||day.getKey().getYear()<2000||day.getValue()>Ledger.MAX_MONEY||day.getValue()< -Ledger.MAX_MONEY)throw new IllegalArgumentException("银行理财日收益无效");
        }
        for(Event e:p.events)if(e.id<=0||!ids.add(e.id)||!Arrays.asList("CHANGE","IN","OUT","BUY","SELL").contains(e.kind)||e.at==null||e.at.isAfter(FundEngine.now())||e.amount<0||e.amount>Ledger.MAX_MONEY||e.completedAt!=null&&(e.completedAt.isBefore(e.at)||e.completedAt.isAfter(FundEngine.now()))||e.closingProfit!=null&&(e.closingProfit>Ledger.MAX_MONEY||e.closingProfit< -Ledger.MAX_MONEY))throw new IllegalArgumentException("银行理财交易事件无效");
        for(long basis:p.bases.values())if(basis<0||basis>Ledger.MAX_MONEY)throw new IllegalArgumentException("银行理财本金无效");
        for(Event e:p.events)if(e.soldCost!=null&&(e.soldCost<0||e.soldCost>Ledger.MAX_MONEY))throw new IllegalArgumentException("银行理财卖出成本无效");
    }
    static String normalizeName(String name){return name.replaceAll("\\s+","").replace('（','(').replace('）',')').replace("基金","").toUpperCase(Locale.ROOT);}
    private BankLedger(){}
}
