package cn.yueji.balance;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Same period controls and filled-height grid as fund calendars, backed only by actual bank return dates. */
final class BankCalendar {
    final BankScreens b;final MainActivity m;
    BankCalendar(BankScreens b){this.b=b;m=b.m;}
    void show(BankLedger.Product p){
        LinearLayout card=m.card();card.setPadding(m.dp(12),m.dp(12),m.dp(12),m.dp(8));m.body.addView(card,new LinearLayout.LayoutParams(-1,0,1));
        Segmented tabs=new Segmented(m,"收益日历","累计盈亏");tabs.setSelected(b.chart);tabs.onSelect(()->{b.chart=tabs.selected();m.render();});card.addView(tabs);m.gap(card,8);
        if(b.chart==1){
            LinearLayout ranges=m.row();String[] labels={"近一月","近一年","持有以来"};for(int i=0;i<3;i++){int n=i;ranges.addView(m.fundScreens.chip(labels[i],b.range==i,()->{b.range=n;m.render();}),new LinearLayout.LayoutParams(0,-2,1));}card.addView(ranges);
            SortedMap<LocalDate,Long> source=BankAccounting.cumulativeSeries(p);LocalDate from=b.range==0?LocalDate.now().minusMonths(1):b.range==1?LocalDate.now().minusYears(1):source.isEmpty()?LocalDate.now():source.firstKey();
            SortedMap<LocalDate,Long> values=new TreeMap<>();if(!source.isEmpty()){if(from.isBefore(source.firstKey()))from=source.firstKey();for(LocalDate day=from;!day.isAfter(source.lastKey());day=day.plusDays(1))values.put(day,source.get(day));}
            ProfitChart chart=new ProfitChart(m,values);card.addView(chart,new LinearLayout.LayoutParams(-1,0,1));return;
        }
        LinearLayout modes=m.row();String[] names={"日","周","月","年"};
        for(int i=0;i<4;i++){int n=i;modes.addView(m.fundScreens.chip(names[i],b.mode==i,()->{b.mode=n;m.render();}),new LinearLayout.LayoutParams(0,-2,1));}card.addView(modes);
        LinearLayout controls=m.row();controls.addView(m.ui.link("‹",()->{b.month=b.month.minusMonths(b.mode<2?1:12);m.render();}));TextView title=m.text(b.mode<2?b.month.getYear()+"年 "+b.month.getMonthValue()+"月":b.month.getYear()+"年",13,Ui.INK,true);title.setGravity(Gravity.CENTER);m.weight(controls,title);controls.addView(m.ui.link("›",()->{b.month=b.month.plusMonths(b.mode<2?1:12);m.render();}));card.addView(controls);m.gap(card,5);
        LinearLayout grid=m.col();card.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        if(b.mode==0){
            LinearLayout header=line(grid,0);for(String day:new String[]{"日","一","二","三","四","五","六"}){TextView text=m.text(day,11,Ui.MUTED,true);text.setGravity(Gravity.CENTER);slot(header,text);}
            int offset=b.month.atDay(1).getDayOfWeek().getValue()%7,rows=(offset+b.month.lengthOfMonth()+6)/7;
            for(int r=0;r<rows;r++){LinearLayout row=line(grid,1);for(int col=0;col<7;col++){int day=r*7+col-offset+1;if(day<1||day>b.month.lengthOfMonth()){slot(row,null);continue;}LocalDate date=b.month.atDay(day);slot(row,cell(date.equals(LocalDate.now())?"今":""+day,BankLedger.daily(p,date),date,date));}}
        }else if(b.mode==1){
            LocalDate first=BankLedger.monday(b.month.atDay(1));List<LocalDate> weeks=new ArrayList<>();for(LocalDate w=first;!w.isAfter(b.month.atEndOfMonth());w=w.plusWeeks(1))weeks.add(w);
            for(int r=0;r<(weeks.size()+2)/3;r++){LinearLayout row=line(grid,1);for(int col=0;col<3;col++){int i=r*3+col;if(i>=weeks.size()){slot(row,null);continue;}LocalDate w=weeks.get(i);slot(row,cell(w.getMonthValue()+"."+w.getDayOfMonth()+"–"+w.plusDays(6).getMonthValue()+"."+w.plusDays(6).getDayOfMonth(),BankLedger.week(p,w),w,w.plusDays(6)));}}
        }else{
            for(int r=0;r<3;r++){LinearLayout row=line(grid,1);for(int col=0;col<4;col++){int month=r*4+col+1,year=b.mode==3?b.month.getYear()-11+r*4+col:b.month.getYear();YearMonth ym=YearMonth.of(year,b.mode==3?1:month);LocalDate end=b.mode==3?LocalDate.of(year,12,31):ym.atEndOfMonth();slot(row,cell(b.mode==3?year+"年":month+"月",period(p,ym.atDay(1),end),ym.atDay(1),end));}}
        }
    }
    Long period(BankLedger.Product p,LocalDate from,LocalDate to){Long sum=null;for(Map.Entry<LocalDate,Long> day:BankAccounting.daily(p).entrySet())if(!day.getKey().isBefore(from)&&!day.getKey().isAfter(to))sum=sum==null?day.getValue():Math.addExact(sum,day.getValue());return sum;}
    LinearLayout line(LinearLayout grid,int weight){LinearLayout row=m.row();grid.addView(row,new LinearLayout.LayoutParams(-1,weight==0?m.dp(22):0,weight));return row;}
    void slot(LinearLayout row,View value){FrameLayout slot=new FrameLayout(m);slot.setPadding(m.dp(2),m.dp(2),m.dp(2),m.dp(2));if(value!=null)slot.addView(value,new FrameLayout.LayoutParams(-1,-1));row.addView(slot,new LinearLayout.LayoutParams(0,-1,1));}
    View cell(String title,Long amount,LocalDate start,LocalDate end){
        boolean today=!LocalDate.now().isBefore(start)&&!LocalDate.now().isAfter(end)&&amount!=null&&amount!=0;
        LinearLayout cell=m.col();cell.setGravity(Gravity.CENTER);cell.setPadding(1,m.dp(2),1,m.dp(2));cell.setBackground(Ui.round(today?m.fundScreens.color(amount):amount==null?Ui.ACCENT_TINT:amount>0?Ui.RED_SOFT:amount<0?Ui.GREEN_SOFT:Ui.ACCENT_TINT,m.dp(8)));
        TextView date=m.text(title,11,today?android.graphics.Color.WHITE:Ui.INK,true);date.setGravity(Gravity.CENTER);cell.addView(date);
        TextView value=m.fit(m.text(amount==null?(start.isAfter(LocalDate.now())?"":"待更新"):m.signed(amount),11,today?android.graphics.Color.WHITE:amount==null?Ui.MUTED:m.fundScreens.color(amount),true),11,6);value.setGravity(Gravity.CENTER);cell.addView(value,new LinearLayout.LayoutParams(-1,m.dp(22)));
        if(start.equals(end)){cell.setContentDescription("银行收益日期"+start);cell.setOnLongClickListener(v->{b.editDay(start);return true;});}
        cell.setOnClickListener(v->AppIcons.dialog(m).setTitle(start.equals(end)?start.toString():start+" — "+end).setMessage(amount==null?"待更新，未导入该日期的真实收益":m.signed(amount)+" 元\n来自已核对的收益明细").setPositiveButton("知道了",null).show());return cell;
    }
}
