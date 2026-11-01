package cn.yueji.balance;

import java.time.*;

/** Presence, not a nonzero sum, selects the most recent earlier record month. */
final class EntryMonths {
    static final class Summary {
        long current,previous;YearMonth previousMonth;
        String previousLabel(YearMonth now){return previousMonth==null||previousMonth.equals(now.minusMonths(1))?"上月":(previousMonth.getYear()==now.getYear()?"":previousMonth.getYear()+"年")+previousMonth.getMonthValue()+"月";}
    }
    static Summary calculate(Ledger.Data data,String kind,YearMonth now){
        Summary result=new Summary();
        for(Ledger.Entry e:data.entries)if(kind.equals(e.kind)){
            YearMonth month=YearMonth.from(e.at);
            if(month.equals(now))result.current=Math.addExact(result.current,e.amount);
            else if(month.isBefore(now)){
                if(result.previousMonth==null||month.isAfter(result.previousMonth)){result.previousMonth=month;result.previous=0;}
                if(month.equals(result.previousMonth))result.previous=Math.addExact(result.previous,e.amount);
            }
        }
        return result;
    }
    private EntryMonths(){}
}
