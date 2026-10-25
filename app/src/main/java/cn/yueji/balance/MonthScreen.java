package cn.yueji.balance;

import android.widget.*;
import java.time.*;
import java.util.*;

final class MonthScreen {
    static void show(MainActivity m,YearMonth month){
        m.top(month.getYear()+"年"+month.getMonthValue()+"月",null,null);
        MonthLedger.Result result=MonthLedger.calculate(m.data,month);Ledger.Report r=result.report;
        Ledger.Report previous=Ledger.month(m.data,month.minusMonths(1));
        LinearLayout summary=m.card(m.body);
        m.detailMetric(summary,"总支出",r.hasData()?"¥ "+m.money(r.expense):"暂无数据",r.anomaly?Ui.AMBER:Ui.GREEN);
        m.detailMetric(summary,"日均支出",r.hasDaily()?"¥ "+m.money(r.daily()):"—",Ui.ACCENT);
        String compared="—";
        if(r.usable()&&previous.usable()){
            long delta=r.expense-previous.expense;compared=(delta>=0?"增加 ¥ ":"减少 ¥ ")+m.money(Math.abs(delta));
            if(previous.expense>0)compared+=String.format(Locale.CHINA," (%+.1f%%)",delta*100.0/previous.expense);
        }
        m.detailMetric(summary,"较上月",compared,Ui.INK);summary.addView(m.text(r.status(),11,Ui.MUTED,false));
        if(result.history!=null){
            LinearLayout card=m.card(m.body);card.addView(m.text(stamp(result.cutoff)+"，导入历史数据",13,Ui.INK,true));m.gap(card,7);
            card.addView(m.text(month.getMonthValue()+"月支出 ¥ "+m.money(result.historical),17,Ui.ACCENT,true));
            if(result.history.includesAdvances)card.addView(m.text("已扣除该历史区间内的报销垫付",11,Ui.MUTED,false));
            card.setOnClickListener(v->m.historyForm(result.history));
        }
        for(MonthLedger.Change c:result.changes){
            LinearLayout card=m.card(m.body);card.addView(m.text(stamp(c.from)+" → "+stamp(c.to)+"  "+m.data.accountName(c.accountId),12,Ui.INK,true));m.gap(card,8);
            LinearLayout amounts=m.row();TextView before=m.fit(m.text("¥ "+m.money(c.before)+" → ¥ "+m.money(c.after),15,Ui.INK,true),15,8);m.weight(amounts,before);
            TextView delta=m.fit(m.text((c.delta()>=0?"+":"−")+"¥ "+m.money(Math.abs(c.delta())),15,c.delta()<0?Ui.GREEN:c.delta()>0?Ui.RED:Ui.INK,true),15,8);
            amounts.addView(delta,new LinearLayout.LayoutParams(m.dp(100),m.dp(32)));card.addView(amounts);
            card.setOnClickListener(v->{m.accountId=c.accountId;m.go("account_history");});
        }
        if(result.adjustment!=0){
            LinearLayout adjustments=m.card(m.body);adjustments.addView(m.text("收入、报销及理财划转修正",13,Ui.INK,true));m.gap(adjustments,6);
            adjustments.addView(m.text(m.signed(result.adjustment)+" 元",16,Ui.ACCENT,true));
            adjustments.addView(m.text("收入和报销按各自关联账户的观测区间校正。新增收入自动加余额，垫付减余额，报销到账加余额；报销不算收入或支出。历史未指定账户的记录沿用原规则，大宗支出不重复扣除。",11,Ui.MUTED,false));
        }
        if(result.changes.isEmpty()){
            LinearLayout note=m.card(m.body);note.addView(m.text(result.history==null?"本月尚无两个余额观测点，支出暂为0；下一次更新后自动计算。":"历史基准之后暂无可计算的余额变化，当前仅显示历史金额。",12,Ui.MUTED,false));
        }
    }
    static String stamp(LocalDateTime at){return at.getMonthValue()+"月"+at.getDayOfMonth()+"日 "+String.format(Locale.ROOT,"%02d:%02d",at.getHour(),at.getMinute());}
    private MonthScreen(){}
}
