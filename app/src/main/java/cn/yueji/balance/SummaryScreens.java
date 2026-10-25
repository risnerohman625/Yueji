package cn.yueji.balance;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Year drill-down and asset composition, using the same accounting definitions as the home page. */
final class SummaryScreens {
    final MainActivity m;
    SummaryScreens(MainActivity activity){m=activity;}
    void year(){
        m.top(m.detailYear+" 年收支明细",null,null);
        YearSummary result=new YearSummary(m.data,m.detailYear,YearMonth.now());
        Long expense=result.expense;long income=result.income;
        LinearLayout summary=m.card(m.body),metrics=m.row();
        cell(metrics,"年度支出",expense==null?"—":"¥ "+m.money(expense),Ui.GREEN);
        cell(metrics,"年度收入","¥ "+m.money(income),Ui.RED);
        cell(metrics,"年度结余",expense==null?"—":"¥ "+m.money(income-expense),Ui.ACCENT);
        summary.addView(metrics);m.gap(summary,10);
        LinearLayout averages=m.row();cell(averages,"月均支出",result.averageExpense==null?"—":"¥ "+m.money(result.averageExpense),Ui.GREEN);
        cell(averages,"月均收入","¥ "+m.money(result.averageIncome),Ui.RED);summary.addView(averages);m.gap(summary,8);
        summary.addView(m.text("月均支出按 "+result.expenseMonths+" 个有记录月份计算；月均收入按"+(m.detailYear==YearMonth.now().getYear()?"本年已过月份":"全年")+"（"+result.incomeMonths+" 个月）计算。",11,Ui.MUTED,false));
        Segmented kinds=new Segmented(m,"支出","收入");kinds.setSelected(m.yearKind);m.body.addView(kinds);
        m.gap(m.body,10);LinearLayout order=m.row();
        order.addView(m.text("排序",12,Ui.MUTED,false));Segmented sort=new Segmented(m,"时间","金额");sort.setSelected(m.sortAmount?1:0);
        sort.setContentDescription("排序方式");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(m.dp(148),-2);p.leftMargin=m.dp(12);order.addView(sort,p);m.body.addView(order);m.gap(m.body,12);
        LinearLayout list=m.col();m.body.addView(list);
        Runnable fill=()->yearRows(list);
        kinds.onSelect(()->{m.yearKind=kinds.selected();fill.run();});
        sort.onSelect(()->{m.sortAmount=sort.selected()==1;fill.run();});fill.run();
    }
    void cell(LinearLayout parent,String label,String value,int color){
        m.ui.metricCell(parent,label,value,color,16,28);
    }
    static final class YearRow {
        final LocalDateTime time;final long amount;final String category,label;
        YearRow(LocalDateTime time,long amount,String category,String label){this.time=time;this.amount=amount;this.category=category;this.label=label;}
    }
    void yearRows(LinearLayout parent){
        parent.removeAllViews();List<YearRow> rows=new ArrayList<>();
        if(m.yearKind==0){
            for(int month=1;month<=12;month++){
                Ledger.Report r=Ledger.month(m.data,YearMonth.of(m.detailYear,month));
                if(r.usable())rows.add(new YearRow(r.month.atDay(1).atStartOfDay(),r.expense,"月支出",Ledger.csvMonth(r.month)));
            }
        }else for(Ledger.Entry e:m.data.entries)if(e.income&&e.at.getYear()==m.detailYear)
            rows.add(new YearRow(e.at,e.amount,e.category,Ledger.csvDate(e.at.toLocalDate())));
        if(m.sortAmount)rows.sort(Comparator.comparingLong((YearRow r)->r.amount).reversed().thenComparing(r->r.time,Comparator.reverseOrder()));
        else rows.sort(Comparator.comparing((YearRow r)->r.time).reversed());
        for(YearRow r:rows){
            LinearLayout item=m.card(parent),line=m.row();
            TextView date=m.fit(m.text(r.label,11.5f,Ui.MUTED,false),11,8);line.addView(date,new LinearLayout.LayoutParams(0,m.dp(28),.34f));
            TextView amount=m.fit(m.text("¥ "+m.money(r.amount),15,m.yearKind==0?Ui.GREEN:Ui.RED,true),15,8);amount.setTag("bounded-number");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,m.dp(28),.40f);ap.leftMargin=m.dp(6);line.addView(amount,ap);
            TextView category=m.text((m.yearKind==1?m.data.iconOf(r.category)+" ":"")+r.category,10.5f,Ui.ACCENT_DEEP,true);category.setMaxLines(2);category.setPadding(m.dp(4),m.dp(3),m.dp(4),m.dp(3));category.setBackground(Ui.round(Ui.ACCENT_SOFT,m.dp(7)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,.26f);cp.leftMargin=m.dp(5);line.addView(category,cp);item.addView(line);
        }
        if(rows.isEmpty())parent.addView(m.text("本年度暂无"+(m.yearKind==0?"月支出":"收入")+"记录",13,Ui.MUTED,false));
    }
    void assets(){
        m.top("资产构成",null,null);
        LinearLayout panel=new AssetPanels(m).composition();
        m.body.addView(panel,new LinearLayout.LayoutParams(-1,m.dp(430)));
    }
}