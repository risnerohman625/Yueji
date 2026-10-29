package cn.yueji.balance;

import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** One full-page entry for new purchases, additions and explicitly authorized automatic plans. */
final class FundPurchaseScreen {
    final MainActivity m;long fundId;String presetCode="",presetName="";int mode,frequency;
    EditText code,name,amount,fee,periods;Spinner account,execution;MainActivity.DateControl first;
    Segmented modes,cycles;LinearLayout details;
    boolean saving,fixedCode;
    MarketRefresh.Fetcher fetcher;
    final FormDraft[] drafts={new FormDraft(),new FormDraft()};
    FundPurchaseScreen(MainActivity m){this.m=m;}
    void open(Ledger.Fund fund,String title,String symbol,boolean plan){
        fundId=fund==null?0:fund.id;presetCode=fund==null?symbol:fund.code;presetName=fund==null?title:fund.name;fixedCode=fund!=null||symbol!=null&&!symbol.isEmpty();mode=plan?1:0;frequency=0;
        if(fund!=null){frequency=Arrays.asList("每日","每周","每两周","每月").indexOf(fund.plan.frequency);if(frequency<0)frequency=0;}
        drafts[0]=new FormDraft();drafts[1]=new FormDraft();saving=false;m.go("fund_purchase");
    }
    void show(){
        Ledger.Fund fund=FundSettlement.find(m.data,fundId);m.top("添加基金买入","取消",m::back);
        modes=new Segmented(m,"普通买入","定投");modes.setSelected(mode);modes.onSelect(()->{if(saving){modes.setSelected(mode);return;}presetCode=code.getText().toString();presetName=name.getText().toString();drafts[mode].capture(details);mode=modes.selected();renderFields(fund);});m.body.addView(modes);m.gap(m.body,12);
        details=m.col();m.body.addView(details);renderFields(fund);
    }
    void renderFields(Ledger.Fund fund){
        details.removeAllViews();first=null;
        LinearLayout amounts=m.row(),a=m.col(),b=m.col();m.weight(amounts,a);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=m.dp(8);amounts.addView(b,bp);details.addView(amounts);
        amount=m.field(a,mode==0?"买入金额（含手续费）":"每期金额",mode==1&&fund!=null&&fund.plan.amount>0?Ledger.decimal(fund.plan.amount):"",true);
        fee=m.field(b,mode==0?"手续费（元）":"每期手续费",mode==1&&fund!=null?Ledger.decimal(fund.plan.fee):"0",true);
        if(mode==1){cycles=new Segmented(m,"每日","每周","每两周","每月");cycles.setSelected(frequency);cycles.onSelect(()->{if(saving){cycles.setSelected(frequency);return;}drafts[mode].capture(details);frequency=cycles.selected();renderFields(fund);});m.gap(details,10);details.addView(cycles);}
        code=m.field(details,"基金代码",presetCode==null?"":presetCode,true);code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);code.setEnabled(!fixedCode);
        name=m.field(details,"基金名称（自动获取）",presetName==null?"":presetName,false);name.setEnabled(false);TextView hint=m.text("",11,Ui.MUTED,false);details.addView(hint);
        if(mode==1){
            if(frequency==0)first=m.new DateControl(details,"首期购买时间（北京时间）",fund!=null&&!fund.plan.firstAt.isEmpty()?LocalDateTime.parse(fund.plan.firstAt):FundEngine.now().withSecond(0).withNano(0),false,true);
            else {String[] days=new String[frequency==3?31:7];for(int i=0;i<days.length;i++)days[i]=frequency==3?(i+1)+"日":"星期"+new String[]{"一","二","三","四","五","六","日"}[i];execution=m.choices(details,"执行日",days,fund==null?0:Math.min(days.length-1,fund.plan.dayOfMonth-1));}
            periods=m.field(details,"计划总期数（0为不限）",fund==null?"0":String.valueOf(fund.plan.totalPeriods),true);
        }else first=m.new DateControl(details,"买入时间（北京时间）",FundEngine.now().withSecond(0).withNano(0),false);
        List<Ledger.Account> accounts=m.data.activeAccounts();String[] labels=new String[accounts.size()+1];labels[0]=mode==1?"请选择资金账户":"不关联账户";for(int i=0;i<accounts.size();i++)labels[i+1]=accounts.get(i).name+" · "+accounts.get(i).type;int selected=accounts.isEmpty()?0:1;if(fund!=null)for(int i=0;i<accounts.size();i++)if(accounts.get(i).id==fund.plan.accountId)selected=i+1;
        account=m.choices(details,mode==1?"定投资金账户（必选）":"买入资金来源账户",labels,selected);
        if(mode==1&&fund!=null){m.gap(details,8);details.addView(m.text("已登记 "+RecurringInvestments.completed(fund.plan)+" 期 · "+(fund.plan.nextAt.isEmpty()?"":("下一期 "+fund.plan.nextAt.replace('T',' ')))+(fund.plan.error.isEmpty()?"":"\n"+fund.plan.error),11,Ui.MUTED,false));details.addView(m.action("查看定投记录",()->m.fundScreens.planRecords(fund)));if(fund.plan.enabled)details.addView(m.action("暂停定投",()->{Ledger.Data c=m.copy();FundSettlement.find(c,fund.id).plan.enabled=false;m.commit(c);m.back();}));}
        m.gap(details,14);TextView save=m.button(mode==0?"获取净值并保存":"保存定投并补齐已到期记录",()->save(accounts));details.addView(save);
        drafts[mode].restore(details);
        PurchaseFeeBinding binding=new PurchaseFeeBinding(m,code,name,amount,fee,hint);binding.manual=drafts[mode].values.containsKey("text:"+(mode==0?"手续费（元）":"每期手续费"));binding.startPage(details);
    }
    android.os.Bundle saveState(){if(details!=null)drafts[mode].capture(details);android.os.Bundle b=new android.os.Bundle();b.putLong("fundId",fundId);b.putBoolean("fixed",fixedCode);b.putString("code",presetCode);b.putString("name",presetName);b.putInt("mode",mode);b.putInt("frequency",frequency);b.putBundle("ordinary",drafts[0].values);b.putBundle("plan",drafts[1].values);return b;}
    void restoreState(android.os.Bundle b){if(b==null)return;fundId=b.getLong("fundId");fixedCode=b.getBoolean("fixed");presetCode=b.getString("code","");presetName=b.getString("name","");mode=b.getInt("mode");frequency=b.getInt("frequency");if(b.getBundle("ordinary")!=null)drafts[0]=new FormDraft(b.getBundle("ordinary"));if(b.getBundle("plan")!=null)drafts[1]=new FormDraft(b.getBundle("plan"));}
    void enabled(View view,boolean value){view.setEnabled(value);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enabled(group.getChildAt(i),value);}if(value){code.setEnabled(!fixedCode);name.setEnabled(false);}}
    void save(List<Ledger.Account> accounts){
        try{
            if(saving)return;
            String symbol=code.getText().toString().trim();if(!symbol.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码为6位数字");
            long value=Ledger.parseMoney(amount.getText().toString(),true),charge=Ledger.parseMoney(fee.getText().toString(),false);if(charge>=value)throw new IllegalArgumentException("手续费须小于金额");
            long source=account.getSelectedItemPosition()==0?0:accounts.get(account.getSelectedItemPosition()-1).id;int selection=mode,cycle=frequency,total=mode==1?Integer.parseInt(periods.getText().toString().trim()):0,day=mode==1&&frequency!=0?execution.getSelectedItemPosition()+1:1;
            if(total<0||total>10000)throw new IllegalArgumentException("总期数应为0至10000");if(selection==1&&source==0)throw new IllegalArgumentException("请选择定投资金账户");
            LocalDateTime at=first==null?FundEngine.now():first.at;Ledger.Fund existing=FundSettlement.find(m.data,fundId);if(selection==0||cycle==0&&(existing==null||!existing.plan.automatic||!at.toString().equals(existing.plan.firstAt)))AutomaticInvestments.requireAppend(existing,at);
            if(at.isAfter(FundEngine.now())&&selection==0)throw new IllegalArgumentException("普通买入时间不能在未来");TradingCalendar calendar=m.fundScreens.calendar();long epoch=LedgerStore.epoch();String known=name.getText().toString().trim();LinearLayout owner=details;
            saving=true;enabled(owner,false);new Thread(()->{
                FundClient.Quote quote=null;String problem="";try{quote=fetcher!=null?fetcher.fetch(symbol):known.isEmpty()?FundClient.fetchHistory(symbol):FundClient.fetchHistoryKnown(symbol,known,"");}catch(Exception e){problem=NetworkIo.message(e);}FundClient.Quote result=quote;String error=problem;
                m.runOnUiThread(()->{saving=false;if(!"fund_purchase".equals(m.page)||details!=owner)return;enabled(owner,true);try{
                    Ledger.Data c=m.store.load();Ledger.Fund f=null;for(Ledger.Fund x:c.funds)if(x.code.equals(symbol))f=x;if(f!=null&&f.removed())throw new IllegalArgumentException("请先恢复已移除基金");String title=result==null?known:result.name;if(title.isEmpty())throw new IllegalArgumentException("无法获取基金名称，请联网重试");
                    if(selection==0){if(f!=null)AutomaticInvestments.requireAppend(f,at);f=FundTransactions.save(c,f==null?0:f.id,0,symbol,value,charge,at,result,error,false,source>0,source,calendar);f.name=title;}
                    else{
                        if(f==null){f=new Ledger.Fund();f.id=c.nextId();f.code=symbol;f.name=title;c.funds.add(f);}if(result!=null)MarketRefresh.apply(c,result,calendar);
                        for(Ledger.FundOrder order:c.fundOrders)if((order.sourceId==f.id||order.targetCode.equals(symbol))&&!"已完成".equals(order.status))throw new IllegalArgumentException("基金存在待处理卖出/转换，请先处理后再配置定投");
                        f.plan.amount=value;f.plan.fee=charge;f.plan.accountId=source;f.plan.totalPeriods=total;
                        AutomaticInvestments.configure(f,new String[]{"每日","每周","每两周","每月"}[cycle],day,at,FundEngine.now(),calendar);AutomaticInvestments.generate(c,f,FundEngine.now(),calendar);
                    }
                    m.commit(c,epoch);FundRefreshJob.schedule(m);m.fundFocus=f.id;m.back();if(result==null)m.networkProblem(error+"\n已保存；份额等待历史净值");
                }catch(Exception e){m.error(e);}});
            },"purchase-page").start();
        }catch(Exception e){m.error(e);}
    }
}
