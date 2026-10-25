package cn.yueji.balance;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-only valuations at real observations. Sampling never writes new balance observations. */
final class AssetHistory {
    static final class Point {
        final LocalDateTime at;
        final Long amount;
        Point(LocalDateTime at,Long amount){this.at=at;this.amount=amount;}
    }
    static final class Change {
        final Point from,to;final long amount;
        Change(Point from,Point to){this.from=from;this.to=to;amount=Math.subtractExact(to.amount,from.amount);}
    }
    static LinkedHashMap<String,Long> composition(Ledger.Data data,LocalDateTime now){
        LinkedHashMap<String,Long> values=new LinkedHashMap<>();
        for(Ledger.Account a:data.activeAccounts())values.merge(a.type,data.balance(a),Math::addExact);
        long funds=0,banks=0;
        for(Ledger.Fund f:data.funds)if(!f.hidden&&!f.removed()&&FundEngine.held(f,now.toLocalDate()))funds=Math.addExact(funds,FundEngine.value(f,now.toLocalDate()));
        for(BankLedger.Product p:data.banks)if(!p.hidden&&!p.removed())banks=Math.addExact(banks,BankLedger.balance(p));
        if(funds>0)values.put("基金",funds);
        if(banks>0)values.put("银行理财",banks);
        values.entrySet().removeIf(e->e.getValue()<=0);
        return values;
    }
    static List<Point> observations(Ledger.Data data,LocalDateTime through){
        SortedSet<LocalDateTime> times=new TreeSet<>();
        for(Ledger.Snapshot s:data.snapshots)if(!s.at.isAfter(through))times.add(s.at);
        // A portfolio-only ledger still has real NAV/statement dates to plot.
        if(times.isEmpty()){
            for(Ledger.Fund f:data.funds)if(!f.hidden&&!f.removed()){
                for(LocalDate date:f.prices.keySet())if(!date.isAfter(through.toLocalDate()))times.add(date.atTime(15,0));
                for(Ledger.FundBuy b:f.buys)if(!b.at.isAfter(through))times.add(b.at);
            }
            for(BankLedger.Product p:data.banks)if(!p.hidden&&!p.removed())for(BankLedger.Observation o:p.observations)if(!o.at.isAfter(through))times.add(o.at);
        }
        List<Point> result=new ArrayList<>();
        for(LocalDateTime time:times)if(!time.isAfter(through))result.add(new Point(time,valueAt(data,time)));
        return result;
    }
    static Long valueAt(Ledger.Data data,LocalDateTime time){
        long total=0;
        for(Ledger.Account a:data.accounts){
            if(a.created!=null&&a.created.isAfter(time)||a.deletedAt!=null&&!a.deletedAt.isAfter(time))continue;
            long balance=a.opening;LocalDateTime last=null;long lastId=Long.MIN_VALUE;
            for(Ledger.Snapshot s:data.snapshots)if(!s.at.isAfter(time)&&s.balances.containsKey(a.id)&&(last==null||s.at.isAfter(last)||s.at.equals(last)&&s.id>=lastId)){
                balance=s.balances.get(a.id);last=s.at;lastId=s.id;
            }
            total=Math.addExact(total,balance);
        }
        for(Ledger.Fund f:data.funds)if(!f.hidden&&(f.removedAt==null||time.isBefore(f.removedAt))){
            Long value=fundAt(f,time);if(value==null)return null;total=Math.addExact(total,value);
        }
        for(BankLedger.Product p:data.banks)if(!p.hidden&&(p.removedAt==null||time.isBefore(p.removedAt))){
            Long value=bankAt(p,time);if(value==null)return null;total=Math.addExact(total,value);
        }
        return total;
    }
    static Long fundAt(Ledger.Fund f,LocalDateTime time){
        LocalDate day=time.toLocalDate();LocalDate first=FundEngine.firstDate(f);
        if(first==null||day.isBefore(first))return 0L;
        Map.Entry<LocalDate,BigDecimal> quote=f.prices.floorEntry(day);
        BigDecimal nav=quote==null&&day.toString().equals(f.navDate)?f.nav:quote==null?null:quote.getValue();
        if(f.buys.isEmpty())return f.shares.signum()==0?0L:nav==null?null:FundEngine.cents(f.shares.multiply(nav));
        long total=0;
        for(Ledger.FundBuy b:f.buys)if(!b.at.isAfter(time)&&FundEngine.active(b,day)){
            if(b.confirmDate==null||day.isBefore(b.confirmDate))total=Math.addExact(total,b.amount);
            else{
                if(nav==null||b.units.signum()<=0)return null;
                total=Math.addExact(total,FundEngine.cents(FundEngine.unitsAt(f,b,day).multiply(nav)));
            }
        }
        return total;
    }
    static Long bankAt(BankLedger.Product p,LocalDateTime time){
        BankLedger.Observation last=null;
        for(BankLedger.Observation o:p.observations)if(!o.at.isAfter(time)&&(last==null||!o.at.isBefore(last.at)))last=o;
        if(last==null)return 0L;
        long value=last.balance;
        // Start at an actual statement, then apply only subsequently completed, same-product flows.
        for(BankLedger.Event e:p.events)if(e.segment==last.segment){
            if(e.kind.equals("CHANGE")&&e.at.isAfter(last.at)&&!e.at.isAfter(time))return null;
            LocalDateTime at=e.completedAt!=null?e.completedAt:e.kind.equals("IN")||e.kind.equals("OUT")?e.at:null;
            if(at!=null&&at.isAfter(last.at)&&!at.isAfter(time)&&(e.kind.equals("BUY")||e.kind.equals("SELL")||e.kind.equals("IN")||e.kind.equals("OUT")))
                value=Math.addExact(value,BankAccounting.buy(e)?e.amount:-e.amount);
        }
        return Math.max(0,value);
    }
    static List<Point> series(List<Point> observations,int mode){
        SortedMap<String,List<Point>> groups=new TreeMap<>();
        for(Point p:observations){
            String key=mode==2?String.format(Locale.ROOT,"%04d",p.at.getYear()):YearMonth.from(p.at).toString();
            groups.computeIfAbsent(key,k->new ArrayList<>()).add(p);
        }
        List<Point> result=new ArrayList<>();
        for(List<Point> group:groups.values()){
            group.sort(Comparator.comparing(p->p.at));
            if(mode!=0)result.add(group.get(group.size()-1));else result.addAll(evenly(group,4));
        }
        return result;
    }
    static List<Point> evenly(List<Point> points,int count){
        if(points.size()<=count)return new ArrayList<>(points);
        List<Point> selected=new ArrayList<>();selected.add(points.get(0));int previous=0,n=points.size();
        long span=ChronoUnit.SECONDS.between(points.get(0).at,points.get(n-1).at);
        for(int slot=1;slot<count-1;slot++){
            double target=span*slot/(double)(count-1);int best=previous+1;
            int last=n-(count-slot);
            for(int i=best;i<=last;i++)if(Math.abs(ChronoUnit.SECONDS.between(points.get(0).at,points.get(i).at)-target)<Math.abs(ChronoUnit.SECONDS.between(points.get(0).at,points.get(best).at)-target))best=i;
            selected.add(points.get(best));previous=best;
        }
        selected.add(points.get(n-1));return selected;
    }
    static Change extreme(List<Point> points,boolean positive){
        Change best=null;
        for(int i=1;i<points.size();i++)if(points.get(i-1).amount!=null&&points.get(i).amount!=null){
            Change c=new Change(points.get(i-1),points.get(i));
            if((positive?c.amount>0:c.amount<0)&&(best==null||(positive?c.amount>best.amount:c.amount<best.amount)))best=c;
        }
        return best;
    }
    private AssetHistory(){}
}
