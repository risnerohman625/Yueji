package cn.yueji.balance;

import android.app.AlertDialog;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Compact editable receipts. Changes stay in memory until the parent record is saved. */
final class ReceiptEditor {
    final MainActivity m;final EditText total;final Spinner status;final LinearLayout rows;
    final TextView add;final List<Ledger.Receipt> values=new ArrayList<>();boolean changing;
    ReceiptEditor(MainActivity m,LinearLayout parent,EditText total,Ledger.Entry old){
        this.m=m;this.total=total;
        status=m.choices(parent,"报销状态",new String[]{"待报销","部分报销","已报销"},old==null?0:Arrays.asList("待报销","部分报销","已报销").indexOf(old.reimbursementStatus()));
        if(old!=null){for(Ledger.Receipt r:old.receipts){Ledger.Receipt c=new Ledger.Receipt(r.amount,r.at);c.accountId=r.accountId;c.cashLinked=r.cashLinked;values.add(c);}if(old.receipts.isEmpty()&&old.received>0)values.add(new Ledger.Receipt(old.received,old.receivedAt));}
        rows=m.col();parent.addView(rows);
        add=m.action("＋ 添加一次报销到账",()->edit(null));parent.addView(add);
        status.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){
            if(changing)return;if(i==0&&!values.isEmpty()){AppIcons.dialog(m).setTitle("保留已有到账记录").setMessage("删除到账记录后可恢复为待报销。").setPositiveButton("知道了",(d,w)->refresh()).show();refresh();}else refreshVisibility();
        }});
        total.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh();}public void afterTextChanged(Editable e){}});
        refresh();
    }
    long received(){long v=0;for(Ledger.Receipt r:values)v=Math.addExact(v,r.amount);return v;}
    long amount(){try{return Ledger.parseMoney(total.getText().toString(),false);}catch(Exception e){return 0;}}
    void refreshVisibility(){boolean canAdd=status.getSelectedItemPosition()>0&&(amount()==0||received()<amount());add.setVisibility(canAdd?View.VISIBLE:View.GONE);}
    void refresh(){
        rows.removeAllViews();long paid=received();if(paid>0){changing=true;status.setSelection(paid==amount()?2:1);changing=false;}
        for(Ledger.Receipt r:values){LinearLayout line=m.row();line.setPadding(0,m.dp(7),0,m.dp(7));
            LinearLayout info=m.col();info.addView(m.text("¥ "+m.money(r.amount)+"  "+MainActivity.TIME.format(r.at),13,MainActivity.INK,true));info.addView(m.text(m.data.accountName(r.accountId),11,MainActivity.MUTED,false));m.weight(line,info);
            TextView edit=m.action("修改",()->edit(r));edit.setTextSize(11);line.addView(edit);TextView remove=m.action("删除",()->{values.remove(r);if(values.isEmpty()){changing=true;status.setSelection(0);changing=false;}refresh();});remove.setTextSize(11);line.addView(remove);rows.addView(line);
        }
        refreshVisibility();
    }
    void edit(Ledger.Receipt old){
        LinearLayout form=m.col();form.setPadding(m.dp(16),0,m.dp(16),m.dp(14));EditText money=m.field(form,"到账金额",old==null?"":Ledger.decimal(old.amount),true);
        MainActivity.DateControl at=m.new DateControl(form,"到账时间",old==null?LocalDateTime.now():old.at,false);
        boolean linked=old==null||old.cashLinked;List<Ledger.Account> available=new ArrayList<>();
        for(Ledger.Account a:m.data.accounts)if(a.deletedAt==null||old!=null&&a.id==old.accountId)available.add(a);
        String[] names=new String[available.size()+1];names[0]=linked?"请选择到账账户":"不指定账户";int selected=0;
        for(int i=0;i<available.size();i++){names[i+1]=available.get(i).name;if(old!=null&&old.accountId==available.get(i).id)selected=i+1;}
        Spinner account=m.choices(form,linked?"到账账户（必选）":"到账账户（历史）",names,selected);
        form.addView(m.text(linked?"保存整笔报销后，自动增加到账账户余额，不计入收入。":"历史到账保留原有记录，不自动补改余额。",11,MainActivity.MUTED,false));
        AlertDialog d=AppIcons.dialog(m).setTitle(old==null?"报销到账":"修改到账").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存到账",null).create();d.show();d.getButton(-1).setOnClickListener(v->{try{
            long cents=Ledger.parseMoney(money.getText().toString(),true);long next=received()-(old==null?0:old.amount)+cents;if(next>amount())throw new IllegalArgumentException("累计到账不能大于报销金额");if(at.at.isAfter(LocalDateTime.now()))throw new IllegalArgumentException("到账时间不能在未来");
            Ledger.Receipt r=new Ledger.Receipt(cents,at.at);r.cashLinked=linked;r.accountId=account.getSelectedItemPosition()==0?0:available.get(account.getSelectedItemPosition()-1).id;
            if(linked)AccountCash.requireAccount(m.data,r.accountId,r.at);
            if(old!=null)values.set(values.indexOf(old),r);else values.add(r);d.dismiss();refresh();
        }catch(Exception e){m.error(e);}});
    }
    void apply(Ledger.Entry e){if(status.getSelectedItemPosition()>0&&values.isEmpty())throw new IllegalArgumentException("请填写报销到账记录");e.receipts.addAll(values);e.status=e.reimbursementStatus();}
}
