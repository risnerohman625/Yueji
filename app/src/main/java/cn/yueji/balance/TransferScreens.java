package cn.yueji.balance;

import android.app.AlertDialog;
import android.widget.*;
import android.view.*;
import java.time.*;
import java.util.*;

final class TransferScreens {
    final MainActivity m;
    TransferScreens(MainActivity m){this.m=m;}
    void open(long focus,Ledger.Transfer old){
        List<Ledger.Account> available=new ArrayList<>();for(Ledger.Account a:m.data.accounts)if(a.deletedAt==null||old!=null&&(a.id==old.fromId||a.id==old.toId))available.add(a);
        if(available.size()<2){m.error(new IllegalArgumentException("请先添加至少两个有效账户"));return;}
        LinearLayout form=m.col();form.setPadding(m.dp(18),0,m.dp(18),m.dp(12));String[] names=new String[available.size()+1];names[0]="请选择账户";int selected=0,target=0;
        for(int i=0;i<available.size();i++){Ledger.Account a=available.get(i);names[i+1]=a.name;if(a.id==(old==null?focus:old.fromId))selected=i+1;if(old!=null&&a.id==old.toId)target=i+1;}
        Spinner from=m.choices(form,"转出账户",names,selected),to=m.choices(form,"转入账户",names,target);
        EditText amount=m.field(form,"转账金额",old==null?defaultAmount(m.data,selected==0?null:available.get(selected-1)):Ledger.decimal(old.amount),true);
        final int[] lastFrom={selected};from.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(old==null&&position!=lastFrom[0])amount.setText(defaultAmount(m.store.load(),position==0?null:available.get(position-1)));lastFrom[0]=position;}});
        MainActivity.DateControl at=m.new DateControl(form,"转账时间",old==null?LocalDateTime.now():old.at,false);
        EditText note=m.field(form,"转账备注（可选）",old==null?"":old.note,false);
        InlineHelp.add(m,form,"内部转账","仅记录两账户账面划转，不发起真实付款，也不计入收入或支出。\n两边余额与备注同时生成；后续真实余额不会被历史转账改写。\n手续费如有，请另记支出，不混入转账金额。");
        AlertDialog dialog=AppIcons.dialog(m).setTitle(old==null?"账户转账":"编辑转账").setView(m.wrap(form)).setNegativeButton("取消",null).setNeutralButton("查看转账记录",(d,w)->{m.accountId=focus;m.go("account_transfers");}).setPositiveButton("保存转账",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{
            if(from.getSelectedItemPosition()==0||to.getSelectedItemPosition()==0)throw new IllegalArgumentException("请选择转出和转入账户");
            Ledger.Data data=m.copy();TransferLedger.save(data,old==null?0:old.id,available.get(from.getSelectedItemPosition()-1).id,available.get(to.getSelectedItemPosition()-1).id,Ledger.parseMoney(amount.getText().toString(),true),at.at,note.getText().toString());m.commit(data);dialog.dismiss();m.render();
        }catch(Exception e){m.error(e);}});
    }
    static String defaultAmount(Ledger.Data data,Ledger.Account account){if(account==null)return "";Ledger.Account latest=null;for(Ledger.Account a:data.accounts)if(a.id==account.id)latest=a;if(latest==null||!AccountRecords.known(data,latest))return "";return Ledger.decimal(data.balance(latest));}
    void list(){
        long focus=m.accountId;m.top("账户转账","＋转账",()->open(focus,null));List<Ledger.Transfer> rows=new ArrayList<>();for(Ledger.Transfer t:m.data.transfers)if(t.fromId==focus||t.toId==focus)rows.add(t);rows.sort(Comparator.comparing((Ledger.Transfer t)->t.at).reversed());
        if(rows.isEmpty())m.body.addView(m.text("暂无转账记录",13,Ui.MUTED,false));
        for(Ledger.Transfer t:rows){LinearLayout card=m.card();card.addView(m.text(TransferLedger.description(m.data,t),15,Ui.INK,true));m.gap(card,5);card.addView(m.text(Ledger.csvMinute(t.at)+" · 内部划转，不计收支",12,Ui.MUTED,false));if(!t.note.isEmpty())card.addView(m.text(t.note,12,Ui.MUTED,false));card.setOnClickListener(v->open(focus,t));
            SwipeRow swipe=new SwipeRow(m,card,new SwipeRow.Action("编辑","编辑转账"+t.id,Ui.ACCENT,false,()->open(focus,t)),new SwipeRow.Action("删除","删除转账"+t.id,Ui.RED,false,()->AppIcons.dialog(m).setTitle("删除这笔转账？").setMessage("同步移除双方自动余额影响。已由用户核对的真实余额保留，不删除其他收支或转账。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{Ledger.Data data=m.copy();data.transfers.removeIf(x->x.id==t.id);m.commit(data);m.render();}catch(Exception e){m.error(e);}}).show()));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=m.dp(8);m.body.addView(swipe,lp);
        }
    }
}
