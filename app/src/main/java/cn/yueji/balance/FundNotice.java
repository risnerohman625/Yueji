package cn.yueji.balance;

import java.time.*;
import java.util.*;

/** Only confirmed, visible holdings participate. A loss in any fund can trigger the alert. */
final class FundNotice {
    static final class Report {LocalDate day;long total;int counted,missing;String losses="",signature="",text="";boolean loss;}
    static Report report(Ledger.Data data,LocalDateTime now){
        Report report=new Report();
        for(Ledger.Fund fund:data.funds){
            if(fund.hidden||fund.removed()||fund.navDate.isEmpty())continue;
            try{LocalDate day=LocalDate.parse(fund.navDate);if(day.isAfter(now.toLocalDate())||!FundEngine.held(fund,day))continue;if(report.day==null||day.isAfter(report.day))report.day=day;}catch(DateTimeException ignored){}
        }
        if(report.day==null||report.day.isBefore(now.toLocalDate().minusDays(3))||report.day.equals(now.toLocalDate())&&now.getHour()<15)return null;
        List<String> losses=new ArrayList<>();
        for(Ledger.Fund fund:data.funds){
            if(fund.hidden||fund.removed()||!FundEngine.held(fund,report.day))continue;
            Long gain=FundEngine.daily(fund,report.day);if(gain==null||fund.navDate.isEmpty()||LocalDate.parse(fund.navDate).isBefore(report.day)){report.missing++;continue;}
            report.total=Math.addExact(report.total,gain);report.counted++;
            if(gain<0)losses.add(fund.name+" −¥"+Ledger.decimal(Math.abs(gain)));
        }
        if(report.counted==0)return null;
        Collections.sort(losses);report.loss=!losses.isEmpty();report.losses=String.join("、",losses);
        report.signature=report.day+":"+report.total+":"+report.losses+":"+report.missing;
        report.text=report.day+" 基金收益 "+(report.total>=0?"+":"−")+"¥"+Ledger.decimal(Math.abs(report.total));
        if(report.loss)report.text+="；亏损："+report.losses;if(report.missing>0)report.text+="（"+report.missing+"只待更新）";
        return report;
    }
    private FundNotice(){}
}
