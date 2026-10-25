package cn.yueji.balance;

import java.math.*;
import java.time.*;
import java.util.*;

/** Fixed opening amount plus evidence: importing the same cumulative return cannot repeatedly reduce principal. */
final class BankAccounting {
    static BankLedger.Observation first(BankLedger.Product p,int segment){for(BankLedger.Observation o:p.observations)if(o.segment==segment)return o;return null;}
    static LocalDate date(BankLedger.Observation o){return o.reportDate==null?o.at.toLocalDate():o.reportDate;}
    static Long segmentProfit(BankLedger.Product p,int segment){
        BankLedger.Observation last=null;for(BankLedger.Observation o:p.observations)if(o.segment==segment&&o.cumulative!=null&&(last==null||!date(o).isBefore(date(last))))last=o;
        if(last!=null)return last.cumulative;
        long sum=0;boolean found=false;Map<LocalDate,Long> days=new TreeMap<>();
        for(BankLedger.Observation o:p.observations)if(o.segment==segment)days.putAll(o.daily);
        for(long value:days.values()){sum=Math.addExact(sum,value);found=true;}return found?sum:null;
    }
    static long basis(BankLedger.Product p,int segment){
        if(p.bases.containsKey(segment))return p.bases.get(segment);
        BankLedger.Observation first=first(p,segment);if(first==null)return 0;
        if(first.cumulative!=null)return Math.subtractExact(first.balance,first.cumulative);
        BankLedger.Observation last=first;boolean known=false;for(BankLedger.Observation o:p.observations)if(o.segment==segment){last=o;if(o.cumulative!=null)known=true;}
        return known?first.balance:last.balance;
    }
    static List<BankLedger.Event> flows(BankLedger.Product p,int segment,LocalDateTime through){
        List<BankLedger.Event> result=new ArrayList<>();for(BankLedger.Event e:p.events)if(e.segment==segment&&(e.kind.equals("IN")||e.kind.equals("OUT")||e.kind.equals("BUY")||e.kind.equals("SELL"))){
            LocalDateTime complete=e.completedAt==null?(e.kind.equals("IN")||e.kind.equals("OUT")?e.at:null):e.completedAt;
            BankLedger.Observation anchor=first(p,segment);
            if(anchor!=null&&!p.bases.containsKey(segment)&&anchor.cumulative==null&&segmentProfit(p,segment)==null)
                for(BankLedger.Observation snapshot:p.observations)if(snapshot.segment==segment)anchor=snapshot;
            if(complete!=null&&!complete.isAfter(through)&&(anchor==null||complete.isAfter(anchor.at)))result.add(e);
        }result.sort(Comparator.comparing(e->e.completedAt==null?e.at:e.completedAt));return result;
    }
    static boolean buy(BankLedger.Event e){return e.kind.equals("IN")||e.kind.equals("BUY");}
    static long cost(BankLedger.Product p,LocalDateTime through){
        int segment=BankLedger.segment(p);long cost=basis(p,segment);
        for(BankLedger.Event e:flows(p,segment,through)){if(buy(e))cost=Math.addExact(cost,e.amount);else cost=Math.subtractExact(cost,e.soldCost==null?Math.min(cost,e.amount):e.soldCost);}
        return Math.max(0,cost);
    }
    static long realized(BankLedger.Product p,int segment,LocalDateTime through){
        long total=0;for(BankLedger.Event e:flows(p,segment,through))if(!buy(e)&&e.soldCost!=null)total=Math.addExact(total,e.amount-e.soldCost);return total;
    }
    static long heldProfit(BankLedger.Product p){
        Long cumulative=segmentProfit(p,BankLedger.segment(p));
        return cumulative==null?0:Math.subtractExact(cumulative,realized(p,BankLedger.segment(p),FundEngine.now()));
    }
    static long value(BankLedger.Product p,LocalDateTime through){
        BankLedger.Observation first=first(p,BankLedger.segment(p));if(first==null)return 0;
        long value=basis(p,BankLedger.segment(p));Long profit=cumulativeAt(p,BankLedger.segment(p),through.toLocalDate());if(profit!=null)value=Math.addExact(value,profit);
        for(BankLedger.Event e:flows(p,BankLedger.segment(p),through))value=Math.addExact(value,buy(e)?e.amount:-e.amount);
        return Math.max(0,value);
    }
    static long invested(BankLedger.Product p){
        long sum=0;for(int segment=0;segment<=BankLedger.segment(p);segment++)sum=Math.addExact(sum,basis(p,segment));
        for(int segment=0;segment<=BankLedger.segment(p);segment++)for(BankLedger.Event e:flows(p,segment,FundEngine.now()))if(buy(e))sum=Math.addExact(sum,e.amount);
        return sum;
    }
    static void importIncome(Ledger.Data data,BankLedger.Product p,BankLedger.Draft income){
        if(p.removed())throw new IllegalArgumentException("请先恢复产品");
        if(income.cumulative==null||income.daily.isEmpty())throw new IllegalArgumentException("收益截图必须包含累计收益和带日期的每日收益");
        LocalDate report=income.daily.lastKey();if(report.isAfter(FundEngine.now().toLocalDate()))throw new IllegalArgumentException("收益日期不能在未来");
        int segment=BankLedger.segment(p);BankLedger.Observation anchor=first(p,segment);
        if(segment>0&&report.isBefore(anchor.at.toLocalDate()))throw new IllegalArgumentException("收益日期早于当前产品变更，不能把旧产品收益导入新产品");
        if(segment>0&&!income.daily.isEmpty()&&income.daily.firstKey().isBefore(anchor.at.toLocalDate()))throw new IllegalArgumentException("包含产品变更前的日收益，请在核对页取消这些日期，累计收益须按新产品口径核对");
        SortedMap<LocalDate,Long> allDaily=daily(p);allDaily.putAll(income.daily);
        for(BankLedger.Observation old:p.observations)if(old.segment==segment&&old.cumulative!=null&&!date(old).equals(report)){
            LocalDate from=date(old).isBefore(report)?date(old):report,to=date(old).isBefore(report)?report:date(old);
            long change=0;boolean complete=true;for(LocalDate day=from.plusDays(1);!day.isAfter(to);day=day.plusDays(1)){Long gain=allDaily.get(day);if(gain==null){complete=false;break;}change=Math.addExact(change,gain);}
            if(complete){long expected=date(old).isBefore(report)?old.cumulative+change:old.cumulative-change;if(expected!=income.cumulative)throw new IllegalArgumentException("累计收益与已有日收益不能衔接，请核对截图日期、产品及累计收益，不能静默覆盖不同口径");}
        }
        long afterAnchor=0;for(Map.Entry<LocalDate,Long> day:income.daily.entrySet())if(day.getKey().isAfter(anchor.at.toLocalDate()))afterAnchor=Math.addExact(afterAnchor,day.getValue());
        if(!p.bases.containsKey(segment)){
            if(anchor.cumulative!=null){long fixed=Math.subtractExact(anchor.balance,anchor.cumulative);if(fixed<0)throw new IllegalArgumentException("初始累计收益与金额不一致");p.bases.put(segment,fixed);}
            else{
            for(LocalDate day=anchor.at.toLocalDate().plusDays(1);!day.isAfter(report);day=day.plusDays(1))
                if(day.getDayOfWeek()!=DayOfWeek.SATURDAY&&day.getDayOfWeek()!=DayOfWeek.SUNDAY&&!allDaily.containsKey(day))
                    throw new IllegalArgumentException("初始金额与收益截止日期之间缺少日收益，请补齐数据或核对初始金额对应时间后再核定本金");
            afterAnchor=0;for(Map.Entry<LocalDate,Long> day:allDaily.entrySet())if(day.getKey().isAfter(anchor.at.toLocalDate())&&!day.getKey().isAfter(report))afterAnchor=Math.addExact(afterAnchor,day.getValue());
            long initialProfit=income.cumulative-afterAnchor;
            long fixed=Math.subtractExact(anchor.balance,initialProfit);
            if(fixed<0)throw new IllegalArgumentException("累计收益大于初始金额，请核对金额与收益对应时间");
            p.bases.put(segment,fixed);
            }
        }
        BankLedger.Observation target=null;for(BankLedger.Observation o:p.observations)if(o.segment==segment&&date(o).equals(report)&&!o.daily.isEmpty())target=o;
        if(target==null){target=new BankLedger.Observation();target.id=data.nextId();target.name=p.name;target.at=FundEngine.now();target.segment=segment;target.weekStart=BankLedger.monday(report);target.reportDate=report;p.observations.add(target);}
        target.daily.putAll(income.daily);for(Map.Entry<LocalDate,Long> week:income.weeks.entrySet())if(segment==0||!week.getKey().isBefore(BankLedger.monday(anchor.at.toLocalDate())))target.weeks.put(week.getKey(),week.getValue());target.cumulative=income.cumulative;target.weekly=income.weekly;
        for(BankLedger.Observation observation:p.observations)if(observation.segment==segment)for(Map.Entry<LocalDate,Long> day:income.daily.entrySet())if(observation.daily.containsKey(day.getKey()))observation.daily.put(day.getKey(),day.getValue());
        target.balance=value(p,FundEngine.now());
    }
    static void flow(Ledger.Data data,BankLedger.Product p,long amount,boolean buy,LocalDateTime placed,LocalDateTime completed){
        if(p.removed()||amount<=0||amount>Ledger.MAX_MONEY||placed==null||placed.isAfter(FundEngine.now())||completed!=null&&(completed.isBefore(placed)||completed.isAfter(FundEngine.now())))throw new IllegalArgumentException("请核对资金金额、交易时间与完成时间");
        if(!buy&&amount>value(p,FundEngine.now()))throw new IllegalArgumentException("卖出到账金额超过当前持有金额，请先更新收益或核对金额");
        BankLedger.Event e=new BankLedger.Event();e.id=data.nextId();e.kind=buy?"BUY":"SELL";e.amount=amount;e.at=placed;e.completedAt=completed;e.segment=BankLedger.segment(p);
        if(!buy&&completed!=null){
            long value=value(p,completed),cost=cost(p,completed);e.soldCost=value==0?0:BigDecimal.valueOf(cost).multiply(BigDecimal.valueOf(amount)).divide(BigDecimal.valueOf(value),0,RoundingMode.HALF_UP).longValueExact();
        }
        BankLedger.Observation anchor=first(p,e.segment);
        e.note=completed==null?"待完成，不计入持有金额":completed.isAfter(anchor.at)?"交易已完成，按完成时间计入；卖出成本按最近已确认金额比例分摊":"初始金额之前的历史记录，不重复计入持有金额";p.events.add(e);
    }
    static SortedMap<LocalDate,Long> daily(BankLedger.Product p){SortedMap<LocalDate,Long> days=new TreeMap<>();for(BankLedger.Observation o:p.observations)days.putAll(o.daily);return days;}
    static Long cumulativeAt(BankLedger.Product p,int segment,LocalDate target){
        BankLedger.Observation past=null,future=null;SortedMap<LocalDate,Long> days=new TreeMap<>();
        for(BankLedger.Observation o:p.observations)if(o.segment==segment){
            days.putAll(o.daily);if(o.cumulative==null)continue;
            if(!date(o).isAfter(target)&&(past==null||date(o).isAfter(date(past))||date(o).equals(date(past))))past=o;
            if(date(o).isAfter(target)&&(future==null||date(o).isBefore(date(future))))future=o;
        }
        if(future!=null){
            long sum=future.cumulative;boolean complete=true;
            for(LocalDate day=date(future);day.isAfter(target);day=day.minusDays(1)){Long income=days.get(day);if(income==null){complete=false;break;}sum=Math.subtractExact(sum,income);}
            if(complete)return sum;
        }
        if(past!=null){long sum=past.cumulative;for(Map.Entry<LocalDate,Long> day:days.entrySet())if(day.getKey().isAfter(date(past))&&!day.getKey().isAfter(target))sum=Math.addExact(sum,day.getValue());return sum;}
        return null;
    }
    static SortedMap<LocalDate,Long> cumulativeSeries(BankLedger.Product p){
        SortedMap<LocalDate,Long> series=new TreeMap<>();SortedMap<LocalDate,Long> days=daily(p);
        for(BankLedger.Observation o:p.observations)if(o.cumulative!=null){
            LocalDate end=date(o);long sum=o.cumulative;series.put(end,sum);
            for(LocalDate day=end;days.containsKey(day);day=day.minusDays(1)){series.put(day,sum);sum=Math.subtractExact(sum,days.get(day));}
        }return series;
    }
    private BankAccounting(){}
}
