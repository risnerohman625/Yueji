package cn.yueji.balance;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

final class DebtScreens {
    final MainActivity m;
    DebtScreens(MainActivity m){this.m=m;}
    void show(){
        m.top("负债","＋ 记录",()->edit(0));
        LocalDate today=LocalDate.now();LinearLayout summary=m.card(m.body);
        summary.addView(m.text("总负债 · 当前待还",13,Ui.MUTED,false));
        summary.addView(m.fit(m.text("¥ "+m.money(DebtLedger.total(m.data,today)),30,Ui.AMBER,true),30,14),new LinearLayout.LayoutParams(-1,m.dp(48)));
        long open=m.data.debts.stream().filter(loan->DebtLedger.balance(loan,today)>0).count();
        summary.addView(m.text("待还 "+open+" 笔 · 已还清 "+(m.data.debts.size()-open)+" 笔",12,Ui.MUTED,false));
        LinearLayout graph=m.card(m.body);graph.addView(m.text("年度负债变化",15,Ui.INK,true));
        graph.addView(m.text("往年取年末待还余额，本年截至今天",10.5f,Ui.MUTED,false));
        SortedMap<Integer,Long> series=DebtLedger.yearly(m.data,today);String[] labels=new String[series.size()];Long[] values=new Long[series.size()];int n=0;
        for(Map.Entry<Integer,Long> value:series.entrySet()){labels[n]=""+value.getKey();values[n++]=value.getValue();}
        LinearLayout host=m.col();graph.addView(host,new LinearLayout.LayoutParams(-1,m.dp(215)));
        m.horizontalChart(host,labels,values,null,false,true,Ui.AMBER,index->m.toast(labels[index]+"年待还 ¥ "+m.money(values[index])));
        FinanceChart chart=m.findFinanceChart(host);if(chart!=null)chart.asYears();
        m.body.addView(m.text("负债记录",16,Ui.INK,true));m.gap(m.body,8);
        if(m.data.debts.isEmpty())m.card(m.body).addView(m.text("暂无负债，点击右上角记录",13,Ui.MUTED,false));
        for(DebtLedger.Loan loan:DebtLedger.sorted(m.data,today))card(loan);
        m.card(m.body).addView(m.text("负债和还款仅在本模块记录与展示，不参与总资产计算，不自动修改账户余额或新增收入、支出。实际账户余额仍由你独立更新，原有余额差算法不变。负债和还款记录随完整JSON备份保存。",11,Ui.MUTED,false));
    }
    void card(DebtLedger.Loan loan){
        long remaining=DebtLedger.balance(loan,LocalDate.now());boolean closed=remaining==0;
        LinearLayout card=m.card(m.body);card.setContentDescription("负债记录"+loan.id);LinearLayout row=m.row(),left=m.col(),buttons=m.col();
        left.addView(m.text(loan.title,15,Ui.INK,true));
        left.addView(m.fit(m.text("¥ "+m.money(remaining),22,closed?Ui.MUTED:Ui.AMBER,true),22,12),new LinearLayout.LayoutParams(-1,m.dp(34)));
        left.addView(m.text(Ledger.csvDate(loan.at)+" · "+(closed?"已还清":"待还款"),11,Ui.MUTED,false));
        left.addView(m.text("原金额 ¥ "+m.money(loan.amount)+" · 已还 ¥ "+m.money(DebtLedger.paid(loan,LocalDate.now())),10.5f,Ui.MUTED,false));m.weight(row,left);
        TextView edit=m.action("编辑",()->edit(loan.id));edit.setContentDescription("编辑负债"+loan.id);buttons.addView(edit);
        m.gap(buttons,5);TextView pay=m.action("还款",()->repay(loan.id));pay.setContentDescription("还款负债"+loan.id);pay.setEnabled(!closed);buttons.addView(pay);row.addView(buttons);card.addView(row);
        if(!loan.note.isEmpty()){m.gap(card,6);card.addView(m.text(loan.note,11,Ui.MUTED,false));}
        if(!loan.payments.isEmpty()){
            m.gap(card,8);card.addView(m.ui.link("还款记录 "+loan.payments.size()+" 笔",()->payments(loan.id)));
        }
        if(closed)SwipeRow.dim(card,.48f);
    }
    void edit(long id){
        DebtLedger.Loan old=DebtLedger.find(m.data,id);if(id!=0&&old==null){m.toast("记录已删除");return;}
        LinearLayout form=m.col();form.setPadding(m.dp(18),0,m.dp(18),m.dp(16));
        EditText title=m.field(form,"负债名称",old==null?"":old.title,false),amount=m.field(form,"负债原始金额",old==null?"":Ledger.decimal(old.amount),true);
        MainActivity.DateControl date=m.new DateControl(form,"负债日期",(old==null?LocalDate.now():old.at).atStartOfDay(),true);
        EditText note=m.field(form,"负债备注",old==null?"":old.note,false);
        if(old!=null)form.addView(m.text("已还款 ¥ "+m.money(DebtLedger.paid(old,LocalDate.now()))+"；金额不能改得小于已还合计。",11,Ui.MUTED,false));
        AlertDialog dialog=AppIcons.dialog(m).setTitle(old==null?"记录负债":"编辑负债").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{
            Ledger.Data data=m.store.load();DebtLedger.save(data,id,title.getText().toString(),Ledger.parseMoney(amount.getText().toString(),true),date.at.toLocalDate(),note.getText().toString());m.commit(data);dialog.dismiss();m.render();
        }catch(Exception e){m.error(e);}});
        if(old!=null){TextView remove=m.action("删除本条负债",()->AppIcons.dialog(m).setTitle("删除负债及全部还款记录？").setMessage(old.title+"\n仅删除负债台账，不修改账户或收支记录。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{Ledger.Data data=m.store.load();data.debts.removeIf(loan->loan.id==id);m.commit(data);dialog.dismiss();m.render();}catch(Exception e){m.error(e);}}).show());remove.setTextColor(Ui.RED);form.addView(remove);}
    }
    void repay(long id){
        DebtLedger.Loan loan=DebtLedger.find(m.data,id);if(loan==null){m.toast("记录已删除");return;}
        LinearLayout form=m.col();form.setPadding(m.dp(18),0,m.dp(18),m.dp(16));form.addView(m.text(loan.title+" · 待还 ¥ "+m.money(DebtLedger.balance(loan,LocalDate.now())),14,Ui.INK,true));
        EditText amount=m.field(form,"还款金额","",true);MainActivity.DateControl date=m.new DateControl(form,"还款日期",LocalDate.now().atStartOfDay(),true);EditText note=m.field(form,"还款备注","",false);
        AlertDialog dialog=AppIcons.dialog(m).setTitle("记录还款").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存还款",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{
            Ledger.Data data=m.store.load();DebtLedger.repay(data,id,Ledger.parseMoney(amount.getText().toString(),true),date.at.toLocalDate(),note.getText().toString());m.commit(data);dialog.dismiss();m.render();
        }catch(Exception e){m.error(e);}});
    }
    void payments(long id){
        DebtLedger.Loan loan=DebtLedger.find(m.data,id);if(loan==null)return;
        LinearLayout rows=m.col();rows.setPadding(m.dp(18),0,m.dp(18),m.dp(16));List<DebtLedger.Payment> list=new ArrayList<>(loan.payments);list.sort(Comparator.comparing((DebtLedger.Payment p)->p.at).reversed());
        AlertDialog dialog=AppIcons.dialog(m).setTitle(loan.title+" · 还款记录").setView(m.wrap(rows)).setPositiveButton("完成",null).create();
        for(DebtLedger.Payment payment:list){
            LinearLayout row=m.row();m.weight(row,m.text(Ledger.csvDate(payment.at)+" · ¥ "+m.money(payment.amount)+(payment.note.isEmpty()?"":"\n"+payment.note),12,Ui.INK,false));
            row.addView(m.ui.link("删除",()->AppIcons.dialog(m).setTitle("撤销这次还款？").setMessage("删除后恢复对应待还金额。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{Ledger.Data data=m.store.load();DebtLedger.Loan current=DebtLedger.find(data,id);if(current!=null)current.payments.removeIf(p->p.id==payment.id);m.commit(data);dialog.dismiss();m.render();}catch(Exception e){m.error(e);}}).show()));rows.addView(row);m.gap(rows,10);
        }dialog.show();
    }
}
