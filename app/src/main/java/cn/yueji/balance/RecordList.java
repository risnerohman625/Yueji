package cn.yueji.balance;

import android.app.AlertDialog;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import java.time.YearMonth;
import java.util.*;

/** Rebuilds only the record list while typing, so neither the keyboard nor the chart loses focus. */
final class RecordList {
    final MainActivity m;final String kind,category,key;
    LinearLayout rows;TextView count,monthButton;
    RecordList(MainActivity activity,String kind,String category){
        m=activity;this.kind=kind;this.category=category;key=kind+"|"+(category==null?"":category);
    }
    void show(){
        if(Ledger.REIMBURSE.equals(kind)){
            Segmented status=new Segmented(m,"未报销","已报销","所有");status.setSelected(m.reimburseFilter);
            status.setContentDescription("报销状态筛选");status.onSelect(()->{m.reimburseFilter=status.selected();populate();});m.body.addView(status);m.gap(m.body,10);
        }
        LinearLayout controls=m.card(m.body);
        EditText search=new EditText(m);search.setTextSize(14);search.setSingleLine(true);
        search.setTextColor(Ui.INK);search.setHintTextColor(Ui.MUTED);search.setHint("搜索名称、标签、账户或备注");
        search.setContentDescription("搜索明细");search.setBackground(Ui.round(Ui.ACCENT_TINT,m.dp(10)));
        search.setPadding(m.dp(12),0,m.dp(12),0);search.setText(m.searches.getOrDefault(key,""));
        controls.addView(search,new LinearLayout.LayoutParams(-1,m.dp(44)));m.gap(controls,10);
        LinearLayout toolbar=m.row();monthButton=m.action(monthLabel(),this::chooseMonth);monthButton.setContentDescription("按月筛选");
        toolbar.addView(monthButton,new LinearLayout.LayoutParams(0,-2,1));
        Segmented sort=new Segmented(m,"时间","金额");sort.setSelected(m.sortAmount?1:0);
        sort.setContentDescription("排序方式");sort.onSelect(()->{m.sortAmount=sort.selected()==1;populate();});
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(m.dp(138),-2);sp.leftMargin=m.dp(10);toolbar.addView(sort,sp);controls.addView(toolbar);
        m.gap(controls,8);count=m.text("",11.5f,Ui.MUTED,false);controls.addView(count);
        rows=m.col();m.body.addView(rows);populate();
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c){m.searches.put(key,s.toString());populate();}
            public void afterTextChanged(Editable e){}
        });
    }
    String monthLabel(){String month=m.monthFilters.getOrDefault(key,"");return month.isEmpty()?"全部月份 ▾":month+" ▾";}
    void chooseMonth(){
        TreeSet<String> months=new TreeSet<>(Comparator.reverseOrder());
        for(Ledger.Entry e:m.data.entries)if(e.kind.equals(kind)&&(category==null||category.equals(e.category)))months.add(YearMonth.from(e.at).toString());
        List<String> options=new ArrayList<>();options.add("全部月份");options.addAll(months);
        AppIcons.dialog(m).setTitle("筛选月份").setItems(options.toArray(new String[0]),(d,w)->{
            m.monthFilters.put(key,w==0?"":options.get(w));monthButton.setText(monthLabel());populate();
        }).show();
    }
    void populate(){
        rows.removeAllViews();
        List<Ledger.Entry> list=RecordQuery.filter(m.data,kind,category,m.searches.getOrDefault(key,""),m.monthFilters.getOrDefault(key,""));
        if(Ledger.REIMBURSE.equals(kind))list.removeIf(e->!RecordQuery.reimbursementState(e,m.reimburseFilter));
        long total=0;for(Ledger.Entry e:list)total=Math.addExact(total,e.amount);
        count.setText("找到 "+list.size()+" 笔记录 · 共计 "+m.money(total)+" 元");
        for(Ledger.Entry e:m.sorted(list))m.entryRow(rows,e);
        if(list.isEmpty()){TextView empty=m.text("没有符合条件的记录",13,Ui.MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,m.dp(24),0,m.dp(24));rows.addView(empty);}
    }
}
