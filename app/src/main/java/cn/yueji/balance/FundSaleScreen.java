package cn.yueji.balance;

import android.widget.*;
import java.time.*;
import java.util.*;

final class FundSaleScreen {
    final MainActivity m;long fundId;int mode;boolean saving;
    MainActivity.DateControl date;EditText fee,confirm,received,code;Spinner account;LinearLayout destination;
    FormDraft draft=new FormDraft();
    FundSaleScreen(MainActivity m){this.m=m;}
    void open(Ledger.Fund f){fundId=f.id;mode=0;saving=false;draft=new FormDraft();m.go("fund_sale");}
    void show(){
        Ledger.Fund fund=FundSettlement.find(m.data,fundId);if(fund==null){m.back();return;}m.top("卖出 · "+fund.name,"取消",m::back);
        date=m.new DateControl(m.body,"卖出时间（北京时间）",FundEngine.now().withSecond(0).withNano(0),false);
        Segmented tabs=new Segmented(m,"资金账户","其他基金");tabs.setSelected(mode);m.body.addView(tabs);destination=m.col();m.body.addView(destination);tabs.onSelect(()->{mode=tabs.selected();destination();});destination();
        fee=m.field(m.body,"卖出及转换费用（元）","0",true);
        confirm=m.field(m.body,"卖出确认工作日 T+","1",true);received=m.field(m.body,"资金到账工作日 T+","3",true);
        TextView preview=m.text("",12,Ui.MUTED,false);m.body.addView(preview);Runnable update=()->{try{TradingCalendar c=m.fundScreens.calendar();LocalDate trade=c.tradeDate(date.at);preview.setText("预计交易日 "+trade+"\n预计确认 "+FundSettlement.plusOpen(c,trade,Integer.parseInt(confirm.getText().toString()))+"\n预计到账 "+FundSettlement.plusOpen(c,trade,Integer.parseInt(received.getText().toString()))+" 12:00\n当前为全部可卖出份额。T+1/T+3只是预填估计，须按平台实际规则调整；转换按双方交易日净值、确认日12:00衔接，填写实际赎回/补差合计费用。");}catch(Exception ignored){preview.setText("请填写平台确认/到账工作日");}};date.changed=update;android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){update.run();}public void afterTextChanged(android.text.Editable e){}};confirm.addTextChangedListener(watcher);received.addTextChangedListener(watcher);update.run();
        m.body.addView(m.button("保存卖出计划",()->save(fund)));
        for(Ledger.FundOrder o:m.data.fundOrders)if(o.sourceId==fund.id){m.gap(m.body,10);m.body.addView(m.text(o.at+" · "+o.status+(o.targetCode.isEmpty()?"":" · 转换 "+o.targetCode)+"\n确认 "+o.confirmDate+" · 到账 "+o.receivedAt+(o.error.isEmpty()?"":"\n"+o.error),12,Ui.MUTED,false));if(!"已完成".equals(o.status))m.body.addView(m.action("取消待处理卖出",()->AppIcons.dialog(m).setTitle("撤销待处理计划？").setMessage("恢复原持仓，撤销尚未完成的转换买入及关联资金处理。").setNegativeButton("返回",null).setPositiveButton("撤销",(d,w)->{try{Ledger.Data c=m.copy();FundSettlement.cancel(c,o.id);for(Ledger.Fund f:c.funds)FundEngine.resolve(f,m.fundScreens.calendar());m.commit(c);m.render();}catch(Exception e){m.error(e);}}).show()));}
        draft.restore(m.body);
    }
    android.os.Bundle saveState(){if(date!=null&&"fund_sale".equals(m.page))draft.capture(m.body);android.os.Bundle b=new android.os.Bundle();b.putLong("fundId",fundId);b.putInt("mode",mode);b.putBundle("draft",draft.values);return b;}
    void restoreState(android.os.Bundle b){if(b==null)return;fundId=b.getLong("fundId");mode=b.getInt("mode");if(b.getBundle("draft")!=null)draft=new FormDraft(b.getBundle("draft"));}
    void destination(){destination.removeAllViews();if(mode==0){List<Ledger.Account> accounts=m.data.activeAccounts();String[] names=new String[accounts.size()+1];names[0]="请选择到账账户";for(int i=0;i<accounts.size();i++)names[i+1]=accounts.get(i).name+" · "+accounts.get(i).type;account=m.choices(destination,"卖出到账账户",names,0);account.setTag(accounts);}else code=m.field(destination,"转换目标基金代码","",true);}
    @SuppressWarnings("unchecked") void save(Ledger.Fund fund){
        try{if(saving)return;int days=Integer.parseInt(confirm.getText().toString().trim()),receipt=Integer.parseInt(received.getText().toString().trim());long charge=Ledger.parseMoney(fee.getText().toString(),false);
            String symbol=mode==1?code.getText().toString().trim():"";long accountId=mode==0&&account.getSelectedItemPosition()>0?((List<Ledger.Account>)account.getTag()).get(account.getSelectedItemPosition()-1).id:0;LocalDateTime at=date.at;long epoch=LedgerStore.epoch();TradingCalendar calendar=m.fundScreens.calendar();LinearLayout owner=m.body;long originalId=fundId;
            if(mode==1&&!symbol.matches("[0-9]{6}"))throw new IllegalArgumentException("目标基金代码须为6位");
            saving=true;new Thread(()->{FundClient.Quote source=null,target=null;String error="";try{source=FundClient.fetchHistoryKnown(fund.code,fund.name,fund.fundType);if(!symbol.isEmpty())target=FundClient.fetchHistory(symbol);}catch(Exception e){error=NetworkIo.message(e);}FundClient.Quote q=source,t=target;String problem=error;
                m.runOnUiThread(()->{saving=false;if(!"fund_sale".equals(m.page)||m.body!=owner||m.isDestroyed()||m.isFinishing())return;try{Ledger.Data c=m.store.load();if(q!=null)MarketRefresh.apply(c,q,calendar);Ledger.Fund original=FundSettlement.find(c,originalId);
                    if(!symbol.isEmpty()){Ledger.Fund dest=null;for(Ledger.Fund f:c.funds)if(f.code.equals(symbol))dest=f;if(dest!=null&&(dest.removed()||dest.buys.isEmpty()&&dest.shares.signum()>0))throw new IllegalArgumentException("目标基金已移除或为旧版份额持仓，请先整理后转换");if(dest==null){if(t==null)throw new IllegalArgumentException("无法确认目标基金名称，请联网重试");dest=new Ledger.Fund();dest.id=c.nextId();dest.code=symbol;dest.name=t.name;dest.pendingOrder=true;c.funds.add(dest);}if(t!=null)MarketRefresh.apply(c,t,calendar);}
                    FundSettlement.create(c,original,at,accountId,symbol,charge,days,receipt,calendar);FundSettlement.apply(c,FundEngine.now(),calendar);m.commit(c,epoch);FundRefreshJob.schedule(m);m.back();if(q==null||!symbol.isEmpty()&&t==null)m.networkProblem(problem+"\n卖出计划已保存，等待净值");
                }catch(Exception e){m.error(e);}});},"fund-sale").start();
        }catch(Exception e){m.error(e);}
    }
}
