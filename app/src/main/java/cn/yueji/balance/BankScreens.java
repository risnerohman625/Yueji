package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** Manual amount anchors and imported actual returns share the fund presentation, without app capture. */
final class BankScreens {
    final MainActivity m;
    long focus,captureTarget;
    BankLedger.Draft draft;
    int mode,chart,range=2;
    YearMonth month=YearMonth.now();
    EditText nameField,balanceField;
    MainActivity.DateControl observed;Spinner openingAccount;
    BankScreens(MainActivity m){this.m=m;}
    int filter(){return Math.max(0,Math.min(2,m.getPreferences(0).getInt("productFilter",0)));}
    TextView filterButton(){
        String[] names={"全部","基金","银行理财"};
        TextView v=m.ui.link(names[filter()]+" ▾",()->{m.getPreferences(0).edit().putInt("productFilter",(filter()+1)%3).apply();m.rerenderKeepingScroll();});
        v.setContentDescription("产品分类");v.setTextSize(11);v.setGravity(Gravity.CENTER);
        v.setPadding(m.dp(9),m.dp(7),m.dp(9),m.dp(7));v.setMinimumHeight(m.dp(36));
        v.setBackground(Ui.roundStroke(Ui.ACCENT_TINT,Ui.BORDER,m.dp(10),m.dp(1)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=m.dp(7);v.setLayoutParams(lp);return v;
    }
    List<BankLedger.Product> sorted(boolean history){
        List<BankLedger.Product> list=new ArrayList<>();for(BankLedger.Product p:m.data.banks)if(history||!p.hidden&&!p.removed())list.add(p);
        Comparator<BankLedger.Product> compare=Comparator.comparing(p->p.observations.get(0).at,Comparator.reverseOrder());
        if(m.fundScreens.sortOrder()==1)compare=Comparator.comparingLong(BankLedger::balance).reversed();
        if(m.fundScreens.sortOrder()==2)compare=Comparator.comparingLong(BankAccounting::heldProfit).reversed();
        list.sort(Comparator.comparing((BankLedger.Product p)->p.hidden).thenComparing(compare));return list;
    }
    void append(boolean history){
        long total=m.heldFundValue();
        for(BankLedger.Product p:sorted(history)){
            LinearLayout card=m.card();card.setBackground(m.ripple(Ui.SURFACE,18));card.setContentDescription("银行理财"+p.id);card.setPadding(m.dp(12),m.dp(8),m.dp(12),m.dp(8));
            LinearLayout title=m.row();m.weight(title,new FundNameView(m,p.name,()->{focus=p.id;mode=0;chart=0;m.go("bank_detail");}));card.addView(title);m.gap(card,3);
            LinearLayout metrics=m.row();metric(metrics,"持有金额",m.money(BankLedger.balance(p)),Ui.INK);
            Long previous=BankLedger.week(p,BankLedger.monday(FundEngine.now().toLocalDate()).minusWeeks(1)),current=BankLedger.week(p,BankLedger.monday(FundEngine.now().toLocalDate()));
            metric(metrics,"上周收益",previous==null?"待更新":m.signed(previous),previous==null?Ui.MUTED:color(previous));
            metric(metrics,"本周收益",current==null?"待更新":m.signed(current),current==null?Ui.MUTED:color(current));
            metric(metrics,"累计收益",m.signed(BankLedger.profit(p)),color(BankLedger.profit(p)));card.addView(metrics);m.gap(card,3);
            LinearLayout footer=m.row();m.weight(footer,m.fundScreens.holdingStamp(total>0?String.format(Locale.CHINA,"持仓占比 %.1f%%",BankLedger.balance(p)*100.0/total):"","数据 "+BankAccounting.date(BankLedger.latest(p))+(p.removed()?" · 已删除":"")));if(!p.tag.isEmpty()){TextView tag=m.ui.tag(p.tag,Ui.ACCENT_DEEP,Ui.ACCENT_SOFT);tag.setMaxWidth(m.dp(100));tag.setMaxLines(2);footer.addView(tag);}card.addView(footer);
            card.setOnClickListener(v->{focus=p.id;mode=0;chart=0;m.go("bank_detail");});
            SwipeRow row=history?new SwipeRow(m,card,
                new SwipeRow.Action(p.hidden?"显示":"隐藏","隐藏银行理财"+p.id,Ui.AMBER,false,()->mutate(p.id,x->x.hidden=!x.hidden)),
                new SwipeRow.Action("恢复","恢复银行理财"+p.id,Ui.ACCENT,!p.removed(),()->mutate(p.id,x->{x.hidden=false;x.removedAt=null;})),
                new SwipeRow.Action("删除","永久删除银行理财"+p.id,Ui.RED,!p.removed(),()->purge(p))):
                new SwipeRow(m,card,new SwipeRow.Action("删除","删除银行理财"+p.id,Ui.RED,false,()->AppIcons.dialog(m).setTitle("从首页移除？").setMessage("保留收益与交易记录，可在累计收益中恢复。").setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->mutate(p.id,x->x.removedAt=FundEngine.now())).show()),
                new SwipeRow.Action("标签","银行理财标签"+p.id,Ui.AMBER,false,()->tag(p)),
                new SwipeRow.Action("编辑","银行理财记录"+p.id,Ui.ACCENT,false,()->start(p.id)));
            row.setTag(PortfolioOrder.bank(p));
            if(p.hidden||p.removed())SwipeRow.dim(card,.65f);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=m.dp(6);m.body.addView(row,lp);
        }
    }
    int color(long amount){return m.fundScreens.color(amount);}
    void metric(LinearLayout row,String label,String value,int color){m.fundScreens.compactMetric(row,label,value,color);}
    interface Change{void apply(BankLedger.Product p);}
    void mutate(long id,Change change){try{Ledger.Data copy=m.copy();BankLedger.Product p=BankLedger.find(copy,id);if(p==null)throw new IllegalArgumentException("产品不存在");change.apply(p);m.commit(copy);m.render();}catch(Exception e){m.error(e);}}
    void purge(BankLedger.Product p){if(!p.removed()){m.toast("请先从首页删除");return;}AppIcons.dialog(m).setTitle("永久删除产品及收益交易记录？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Ledger.Data copy=m.copy();copy.banks.removeIf(x->x.id==p.id);m.commit(copy);m.render();}).show();}
    void tag(BankLedger.Product p){LinearLayout form=m.col();EditText text=m.field(form,"理财标签（最多12字）",p.tag,false);AppIcons.dialog(m).setTitle("银行理财标签").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->mutate(p.id,x->x.tag=text.getText().toString().trim())).show();}
    void start(long id){
        captureTarget=id;draft=new BankLedger.Draft();nameField=balanceField=null;observed=null;
        BankLedger.Product p=BankLedger.find(m.data,id);if(p!=null){BankLedger.Observation first=BankAccounting.first(p,BankLedger.segment(p));draft.name=p.name;draft.balance=first.balance;draft.at=first.at;}
        m.go("bank_review");
    }
    void review(){
        if(draft==null){m.back();return;}m.top(captureTarget==0?"添加银行理财":"修正理财初始信息",null,null);LinearLayout form=m.card(m.body);
        nameField=m.field(form,"产品名称",draft.name,false);
        balanceField=m.field(form,"持有金额",draft.balance==null?"":Ledger.decimal(draft.balance),true);
        observed=m.new DateControl(form,"金额对应日期时间",draft.at,false);
        BankLedger.Product openingProduct=BankLedger.find(m.data,captureTarget);openingAccount=accountChoice(form,"买入资金来源账户",openingProduct==null?0:openingProduct.openingAccountId,openingProduct==null);if(openingProduct!=null&&BankLedger.segment(openingProduct)>0){openingAccount.setEnabled(false);form.addView(m.text("本页为产品变更后的金额基准，不会重新扣初始账户资金；新买卖请到资金变动中关联。",11,Ui.AMBER,false));}
        form.addView(m.text("请手动填写名称、金额与对应日期时间。实际日收益另行录入，资金进出不作为收益。",11,Ui.MUTED,false));
        m.body.addView(m.button("确认保存",()->{
            try{
                updateDraft();Ledger.Data copy=m.copy();BankLedger.Product existing=BankLedger.find(copy,captureTarget);
                if(existing==null){
                    BankLedger.Product saved=BankLedger.apply(copy,0,draft,false,null);focus=saved.id;
                }else{
                    if(!existing.name.equals(draft.name)){AppIcons.dialog(m).setTitle("产品名称发生变化").setMessage("确认变更为新的产品？旧产品数据将保留，过渡收益继续待核对。").setNegativeButton("继续修改",null).setPositiveButton("确认产品变更",(d,w)->saveChanged()).show();return;}
                    BankLedger.Observation first=BankAccounting.first(existing,BankLedger.segment(existing));first.balance=draft.balance;first.at=draft.at;first.reportDate=draft.at.toLocalDate();first.weekStart=BankLedger.monday(draft.at.toLocalDate());existing.bases.remove(first.segment);
                    BankLedger.Observation evidence=null;for(BankLedger.Observation o:existing.observations)if(o.segment==first.segment&&!o.daily.isEmpty()&&o.cumulative!=null&&(evidence==null||!BankAccounting.date(o).isBefore(BankAccounting.date(evidence))))evidence=o;
                    if(evidence!=null){BankLedger.Draft report=new BankLedger.Draft();report.cumulative=evidence.cumulative;report.weekly=evidence.weekly;report.daily.putAll(evidence.daily);report.weeks.putAll(evidence.weeks);BankAccounting.importIncome(copy,existing,report);}
                    focus=existing.id;
                }
                BankLedger.Product linkedProduct=BankLedger.find(copy,focus);if(BankLedger.segment(linkedProduct)==0){long selectedAccount=selectedAccount(openingAccount);linkedProduct.openingAccountLinked=selectedAccount>0;linkedProduct.openingAccountId=selectedAccount;}
                commitImport(copy);draft=null;nameField=balanceField=null;m.go("bank_detail");
            }catch(Exception e){m.error(e);}
        }));
    }
    void saveChanged(){try{Ledger.Data copy=m.copy();BankLedger.Product saved=BankLedger.apply(copy,captureTarget,draft,true,null);focus=saved.id;
                commitImport(copy);draft=null;nameField=balanceField=null;m.go("bank_detail");}catch(Exception e){m.error(e);}}
    void commitImport(Ledger.Data copy){m.commit(copy);}
    void updateDraft(){
        if(draft==null||nameField==null)return;draft.name=nameField.getText().toString().trim();draft.balance=Ledger.parseMoney(balanceField.getText().toString(),false);draft.at=observed.at;
        if(draft.name.isEmpty()||draft.at.isAfter(FundEngine.now()))throw new IllegalArgumentException("请核对名称、金额与对应时间");
    }
    static long signed(String raw){
        String clean=raw.trim();boolean negative=clean.startsWith("-");if(clean.startsWith("-")||clean.startsWith("+"))clean=clean.substring(1);long value=Ledger.parseMoney(clean,false);return negative?-value:value;
    }
    static Long optional(String text){return text.trim().isEmpty()?null:signed(text);}
    void detail(){
        BankLedger.Product p=BankLedger.find(m.data,focus);if(p==null){m.back();return;}m.top(p.name,null,null);long value=BankLedger.balance(p),cost=BankAccounting.cost(p,FundEngine.now()),profit=BankAccounting.heldProfit(p);
        LinearLayout info=m.card(m.body);m.fundScreens.metricRow(info,new FundScreens.Cell("持有金额","¥ "+m.money(value),Ui.INK),new FundScreens.Cell("持有收益",m.signed(profit),color(profit)),new FundScreens.Cell("持有收益率",m.fundScreens.rate(profit,cost),color(profit)));
        Long high=null,low=null;for(long day:BankAccounting.daily(p).values()){high=high==null?day:Math.max(high,day);low=low==null?day:Math.min(low,day);}
        m.fundScreens.metricRow(info,new FundScreens.Cell("累计买入金额","¥ "+m.money(BankAccounting.invested(p)),Ui.INK),new FundScreens.Cell("最高日收益",high==null?"待更新":m.signed(high),high==null?Ui.MUTED:color(high)),new FundScreens.Cell("最低日收益",low==null?"待更新":m.signed(low),low==null?Ui.MUTED:color(low)));
        LocalDate week=BankLedger.monday(FundEngine.now().toLocalDate());Long current=BankLedger.week(p,week),previous=BankLedger.week(p,week.minusWeeks(1));
        m.fundScreens.metricRow(info,new FundScreens.Cell("本周收益",current==null?"待更新":m.signed(current),current==null?Ui.MUTED:color(current)),new FundScreens.Cell("上周收益",previous==null?"待更新":m.signed(previous),previous==null?Ui.MUTED:color(previous)),new FundScreens.Cell("持仓占比",m.heldFundValue()==0?"—":String.format(Locale.CHINA,"%.1f%%",value*100.0/m.heldFundValue()),Ui.ACCENT_DEEP));
        LinearLayout actions=m.row();m.weight(actions,m.action("资金变动",()->m.go("bank_flows")));info.addView(actions);
        new BankCalendar(this).show(p);
    }
    void flow(BankLedger.Product p){focus=p.id;m.go("bank_flows");}
    void editDay(LocalDate day){
        BankLedger.Product product=BankLedger.find(m.data,focus);if(product==null||product.removed()||day.isAfter(FundEngine.now().toLocalDate()))return;
        Long prior=BankLedger.daily(product,day);LinearLayout form=m.col();EditText amount=m.field(form,"当日收益（元，可为负数）",prior==null?"":Ledger.decimal(prior),false);
        AlertDialog dialog=AppIcons.dialog(m).setTitle(day+" · 当日收益").setView(form).setNegativeButton("取消",null).setPositiveButton("确认",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{long value=signed(amount.getText().toString());Ledger.Data c=m.copy();BankAccounting.setDaily(c,BankLedger.find(c,focus),day,value);m.commit(c);dialog.dismiss();m.render();}catch(Exception e){m.error(e);}});
    }
    void flows(){
        BankLedger.Product p=BankLedger.find(m.data,focus);if(p==null){m.back();return;}m.top("资金变动 · "+p.name,null,null);
        if(!p.removed())m.body.addView(m.button("添加买/卖出记录",()->flowEditor(p)));
        for(BankLedger.Event e:p.events){
            LinearLayout card=m.card(m.body);card.addView(m.text(e.kind.equals("CHANGE")?"产品变更："+e.oldName+" → "+e.newName:(BankAccounting.buy(e)?"买入":"卖出")+" ¥ "+m.money(e.amount),15,Ui.INK,true));
            card.addView(m.text("交易时间 "+MainActivity.TIME.format(e.at),12,Ui.MUTED,false));card.addView(m.text("完成时间 "+(e.completedAt==null?"待完成":MainActivity.TIME.format(e.completedAt)),12,Ui.MUTED,false));card.addView(m.text(e.note,11,Ui.MUTED,false));
            if(!p.removed()&&(e.kind.equals("BUY")||e.kind.equals("SELL")))card.addView(m.ui.link("编辑/完成交易",()->flowEditor(p,e)));
            if(!p.removed()&&e.kind.equals("CHANGE"))card.addView(m.ui.link("补录旧产品结算收益",()->{
                LinearLayout form=m.col();EditText amount=m.field(form,"旧产品最终累计收益",e.closingProfit==null?"":Ledger.decimal(e.closingProfit),false);
                AlertDialog dialog=AppIcons.dialog(m).setTitle("核对结算收益").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();dialog.getButton(-1).setOnClickListener(v->{try{Long closing=optional(amount.getText().toString());Ledger.Data copy=m.copy();for(BankLedger.Event event:BankLedger.find(copy,p.id).events)if(event.id==e.id)event.closingProfit=closing;m.commit(copy);dialog.dismiss();m.render();}catch(Exception error){m.error(error);}});
            }));
        }
        if(p.events.isEmpty()){LinearLayout empty=m.card(m.body);empty.addView(m.text("尚无资金变动记录",13,Ui.MUTED,false));}
    }
    void flowEditor(BankLedger.Product p){
        flowEditor(p,null);
    }
    void flowEditor(BankLedger.Product p,BankLedger.Event existing){
        LinearLayout form=m.col();form.setPadding(m.dp(16),0,m.dp(16),m.dp(12));Spinner kind=m.choices(form,"交易方向",new String[]{"买入","卖出"},0);EditText amount=m.field(form,"交易金额","",true);
        if(existing!=null){kind.setSelection(existing.kind.equals("BUY")?0:1);amount.setText(Ledger.decimal(existing.amount));}
        LocalDateTime now=FundEngine.now().withSecond(0).withNano(0);
        MainActivity.DateControl placed=m.new DateControl(form,"买/卖出时间",existing==null?now:existing.at,false),complete=m.new DateControl(form,"交易完成时间",existing==null||existing.completedAt==null?now:existing.completedAt,false);CheckBox done=new CheckBox(m);done.setText("交易已完成");done.setChecked(existing==null||existing.completedAt!=null);form.addView(done);
        Spinner account=accountChoice(form,"买入来源 / 卖出到账账户",existing==null?0:existing.accountId,existing==null);
        AlertDialog dialog=AppIcons.dialog(m).setTitle("记录资金变动").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{Ledger.Data copy=m.copy();BankLedger.Product product=BankLedger.find(copy,p.id);if(existing!=null)product.events.removeIf(event->event.id==existing.id);BankAccounting.flow(copy,product,Ledger.parseMoney(amount.getText().toString(),true),kind.getSelectedItemPosition()==0,placed.at,done.isChecked()?complete.at:null);BankLedger.Event saved=product.events.get(product.events.size()-1);if(existing!=null)saved.id=existing.id;saved.accountId=selectedAccount(account);saved.accountLinked=saved.accountId>0;m.commit(copy);dialog.dismiss();m.render();}catch(Exception e){m.error(e);}});
    }
    Spinner accountChoice(LinearLayout form,String label,long id,boolean defaultLink){
        List<Ledger.Account> accounts=new ArrayList<>();for(Ledger.Account a:m.data.accounts)if(a.deletedAt==null||a.id==id)accounts.add(a);String[] labels=new String[accounts.size()+1];labels[0]="不关联账户";int selected=defaultLink&&!accounts.isEmpty()?1:0;
        for(int i=0;i<accounts.size();i++){labels[i+1]=accounts.get(i).name; if(accounts.get(i).id==id)selected=i+1;}
        Spinner spinner=m.choices(form,label,labels,selected);spinner.setTag(accounts);form.addView(m.text("历史买入可选不关联；关联时按完成时间更新资产账户，待完成交易不扣款。",11,Ui.MUTED,false));return spinner;
    }
    @SuppressWarnings("unchecked") long selectedAccount(Spinner spinner){if(spinner==null||spinner.getSelectedItemPosition()==0)return 0;return ((List<Ledger.Account>)spinner.getTag()).get(spinner.getSelectedItemPosition()-1).id;}
    void records(){flows();}
    void consume(){}
    Bundle save(){
        Bundle b=new Bundle();b.putLong("focus",focus);b.putLong("target",captureTarget);b.putInt("mode",mode);b.putInt("chart",chart);b.putInt("range",range);b.putString("month",month.toString());
        try{updateDraft();}catch(Exception ignored){}
        if(draft!=null){b.putString("name",draft.name);b.putLong("balance",draft.balance==null?-1:draft.balance);b.putString("at",draft.at.toString());}
        return b;
    }
    void restore(Bundle b){
        if(b==null)return;focus=b.getLong("focus");captureTarget=b.getLong("target");mode=b.getInt("mode");chart=b.getInt("chart");range=b.getInt("range",2);month=YearMonth.parse(b.getString("month",YearMonth.now().toString()));
        if(b.containsKey("name")){draft=new BankLedger.Draft();draft.name=b.getString("name","");if(b.getLong("balance",-1)>=0)draft.balance=b.getLong("balance");draft.at=LocalDateTime.parse(b.getString("at"));}
    }
}
