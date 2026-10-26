package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Watchlists contain market data, never position amounts or synthetic purchases. */
final class WatchlistScreens {
    final MainActivity m;
    int range,detailChartMode,ruleTab,generation,periodScrollX;
    boolean selectionMode;
    HorizontalScrollView detailPeriods;
    String rangeFund="";
    final Set<String> selectedCodes=new LinkedHashSet<>();
    boolean rulesExpanded;
    String activeGroup="全部";
    PullRefreshLayout pull;
    final Set<String> profileAttempts=new HashSet<>();
    LinearLayout ruleContent;Segmented ruleTabs;Ledger.WatchFund ruleFund;
    WatchlistScreens(MainActivity m){this.m=m;range=m.store.load().watchRange;}
    boolean live(int token){return token==generation&&!m.isDestroyed()&&!m.isFinishing();}
    AlertDialog progress;
    void dispose(){generation++;if(progress!=null)progress.dismiss();progress=null;}
    void show(){
        m.top("自选基金",null,null);toolbar();tabs();table();
        if(visible().isEmpty())m.body.addView(m.text("本组暂无基金",12,Ui.MUTED,false));
    }
    void toolbar(){
        LinearLayout row=m.row();row.addView(m.button("＋ 添加",this::addDialog),new LinearLayout.LayoutParams(0,m.dp(42),1));
        TextView period=periodButton();LinearLayout.LayoutParams periodLayout=new LinearLayout.LayoutParams(0,m.dp(42),1);periodLayout.leftMargin=m.dp(8);row.addView(period,periodLayout);
        m.body.addView(row);m.gap(m.body,8);
    }
    TextView periodButton(){TextView period=m.action(WatchAnalytics.RANGES[range]+" ▾",()->{});period.setContentDescription("自选数据周期");period.setOnClickListener(v->chooseRange(period));return period;}
    void chooseRange(TextView anchor){LinearLayout list=m.col();list.setBackground(Ui.round(Ui.SURFACE,m.dp(10)));PopupWindow popup=new PopupWindow(list,m.dp(104),-2,true);popup.setBackgroundDrawable(Ui.round(Ui.SURFACE,m.dp(10)));popup.setElevation(m.dp(8));for(int i=0;i<WatchAnalytics.RANGES.length;i++){final int choice=i;TextView item=m.text(WatchAnalytics.RANGES[i],14,Ui.INK,false);item.setPadding(m.dp(14),m.dp(10),m.dp(14),m.dp(10));item.setOnClickListener(v->{range=choice;Ledger.Data c=m.copy();c.watchRange=range;m.commit(c);popup.dismiss();m.render();});list.addView(item);}popup.showAsDropDown(anchor);}

    void tabs(){
        HorizontalScrollView scroll=new HorizontalScrollView(m);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=m.row();List<String> names=new ArrayList<>(Arrays.asList("全部","持有"));for(Ledger.WatchGroup group:m.data.watchGroups)names.add(group.name);
        if(!names.contains(activeGroup))activeGroup="全部";
        for(String name:names){TextView t=m.action(name,()->{activeGroup=name;m.render();});t.setTextColor(activeGroup.equals(name)?Ui.ACCENT_DEEP:Ui.MUTED);if(activeGroup.equals(name))t.setTypeface(Ui.BOLD);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,m.dp(39));p.rightMargin=m.dp(5);row.addView(t,p);}row.addView(m.action("自定义",this::manageGroups));scroll.addView(row);m.body.addView(scroll);m.gap(m.body,14);
    }
    List<Ledger.WatchFund> all(){
        LinkedHashMap<String,Ledger.WatchFund> result=new LinkedHashMap<>();
        for(Ledger.WatchFund f:m.data.watchFunds)result.put(f.code,f);
        for(Ledger.Fund f:m.data.funds)if(!f.removed()){
            Ledger.WatchFund previous=result.get(f.code),owned=WatchAnalytics.owned(f);
            if(previous!=null){
                TreeMap<LocalDate,BigDecimal> prices=new TreeMap<>(previous.prices),returns=new TreeMap<>(previous.returns);
                prices.putAll(owned.prices);returns.putAll(owned.returns);owned.prices.clear();owned.prices.putAll(prices);owned.returns.clear();owned.returns.putAll(returns);
                if(previous.navDate.compareTo(owned.navDate)>0){owned.navDate=previous.navDate;owned.nav=previous.nav;owned.previous=previous.previous;}
                if(previous.profile.updated.compareTo(owned.profile.updated)>0)try{owned.profile.read(previous.profile.encode());}catch(Exception ignored){}
            }
            result.put(f.code,owned);
        }
        return new ArrayList<>(result.values());
    }
    Ledger.WatchFund find(String code){for(Ledger.WatchFund f:all())if(f.code.equals(code))return f;return null;}
    List<Ledger.WatchFund> visible(){
        List<Ledger.WatchFund> result=new ArrayList<>();for(Ledger.WatchFund fund:all()){
            boolean yes="全部".equals(activeGroup);if("持有".equals(activeGroup))for(Ledger.Fund f:m.data.funds)if(!f.removed()&&f.code.equals(fund.code))yes=true;
            for(Ledger.WatchGroup g:m.data.watchGroups)if(g.name.equals(activeGroup)&&g.codes.contains(fund.code))yes=true;if(yes)result.add(fund);
        }
        Map<String,WatchAnalytics.Result> metrics=new HashMap<>();for(Ledger.WatchFund f:result)metrics.put(f.code,metrics(f));
        Comparator<Ledger.WatchFund> by=Comparator.comparingDouble(f->{WatchAnalytics.Result r=metrics.get(f.code);return m.data.watchSort==1?r.sharpe:m.data.watchSort==2?r.drawdown:r.change;});if(m.data.watchDescending)by=by.reversed();result.sort(by.thenComparing(f->f.code));return result;
    }
    WatchAnalytics.Result metrics(Ledger.WatchFund f){return WatchAnalytics.calculate(f,range,FundEngine.now().toLocalDate());}
    void table(){
        LinearLayout shell=m.row();shell.setGravity(Gravity.TOP);shell.setBackground(Ui.round(Ui.SURFACE,m.dp(15)));LinearLayout names=m.col(),numbers=m.col();
        TextView title=m.text("基金名称 / 代码",11,Ui.MUTED,true);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(m.dp(8),0,m.dp(4),0);names.addView(title,new LinearLayout.LayoutParams(-1,m.dp(46)));
        LinearLayout headers=m.row();String[] labels={"涨跌幅","夏普比率","最大回撤","最新净值","日涨跌幅"};
        for(int i=0;i<labels.length;i++){final int key=i;LinearLayout h=m.row();h.setGravity(Gravity.CENTER);TextView label=m.text(labels[i],11,Ui.MUTED,true);h.addView(label);if(i<3){LinearLayout arrows=m.col();for(int direction=0;direction<2;direction++){final boolean desc=direction==1;TextView arrow=m.text(desc?"▼":"▲",10,m.data.watchSort==key&&m.data.watchDescending==desc?Ui.ACCENT:Ui.FAINT,true);arrow.setGravity(Gravity.CENTER);arrow.setContentDescription(labels[i]+(desc?"递减排序":"递增排序"));arrow.setOnClickListener(v->sortBy(key,desc));arrows.addView(arrow,new LinearLayout.LayoutParams(m.dp(22),m.dp(13)));}h.addView(arrows);}headers.addView(h,new LinearLayout.LayoutParams(m.dp(91),m.dp(46)));}numbers.addView(headers);
        for(Ledger.WatchFund fund:visible()){
            LinearLayout n=m.row();n.setGravity(Gravity.CENTER_VERTICAL);n.setPadding(m.dp(5),m.dp(6),m.dp(4),m.dp(6));
            if(selectionMode){CheckBox check=new CheckBox(m);check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));check.setContentDescription("选择基金"+fund.code);check.setChecked(selectedCodes.contains(fund.code));check.setOnCheckedChangeListener((button,checked)->{if(checked)selectedCodes.add(fund.code);else selectedCodes.remove(fund.code);});n.addView(check,new LinearLayout.LayoutParams(m.dp(30),m.dp(40)));}
            LinearLayout identity=m.col();TextView name=m.text(fund.name,12,Ui.INK,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.addView(name);m.gap(identity,3);identity.addView(m.text(fund.code,10,Ui.MUTED,false));n.addView(identity,new LinearLayout.LayoutParams(0,-2,1));n.setContentDescription("自选基金"+fund.code);
            n.setOnClickListener(v->{if(selectionMode){CheckBox check=(CheckBox)n.getChildAt(0);check.setChecked(!check.isChecked());}else open(fund.code);});n.setOnLongClickListener(v->{enterSelection(fund.code);return true;});names.addView(n,new LinearLayout.LayoutParams(-1,m.dp(72)));names.addView(m.ui.divider());
            WatchAnalytics.Result r=metrics(fund);LinearLayout row=m.row();row.setOnClickListener(v->{if(!selectionMode)open(fund.code);});row.setOnLongClickListener(v->{enterSelection(fund.code);return true;});value(row,percent(r.change),r.change);value(row,String.format(Locale.CHINA,"%.2f",r.sharpe),Double.NaN);value(row,String.format(Locale.CHINA,"%.2f%%",r.drawdown),Double.NaN);value(row,fund.nav.setScale(4,RoundingMode.HALF_UP).toPlainString(),Double.NaN);double daily=daily(fund);value(row,percent(daily),daily);numbers.addView(row,new LinearLayout.LayoutParams(-2,m.dp(72)));numbers.addView(m.ui.divider());
        }
        shell.addView(names,new LinearLayout.LayoutParams(m.dp(139),-2));HorizontalScrollView horizontal=new HorizontalScrollView(m);horizontal.setHorizontalScrollBarEnabled(true);horizontal.setContentDescription("自选指标列表，左右滑动");horizontal.addView(numbers);shell.addView(horizontal,new LinearLayout.LayoutParams(0,-2,1));m.body.addView(shell);m.gap(m.body,8);
    }
    void value(LinearLayout row,String text,double sign){TextView v=m.fit(m.text(text,14,Double.isNaN(sign)?Ui.INK:sign>0?Ui.RED:sign<0?Ui.GREEN:Ui.MUTED,true),14,9);v.setGravity(Gravity.CENTER);row.addView(v,new LinearLayout.LayoutParams(m.dp(91),m.dp(50)));}
    double daily(Ledger.WatchFund f){try{BigDecimal value=f.returns.get(LocalDate.parse(f.navDate));if(value!=null)return value.doubleValue();}catch(Exception ignored){}return f.previous.signum()==0?0:f.nav.subtract(f.previous).divide(f.previous,10,RoundingMode.HALF_UP).doubleValue()*100;}
    String percent(double value){return String.format(Locale.CHINA,"%+.2f%%",value);}
    void sortBy(int key,boolean descending){Ledger.Data c=m.copy();c.watchSort=key;c.watchDescending=descending;m.commit(c);m.render();}
    void open(String code){rangeFund="";periodScrollX=0;m.watchFocus=code;rulesExpanded=false;detailChartMode=0;m.go("watch_detail");fetchProfile(code,false);}
    void addDialog(){
        LinearLayout form=m.col();form.setPadding(m.dp(16),0,m.dp(16),m.dp(14));
        EditText code=m.field(form,"基金代码（6位）","",true);
        AlertDialog dialog=AppIcons.dialog(m).setTitle("添加自选基金").setView(form).setNegativeButton("取消",null).setPositiveButton("联网核验",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{String symbol=code.getText().toString().trim();if(!symbol.matches("[0-9]{6}")){m.error(new IllegalArgumentException("基金代码为6位数字"));return;}dialog.dismiss();load(Collections.singletonList(symbol));});
    }
    void load(List<String> codes){
        if(codes.isEmpty()){if(pull!=null)pull.setRefreshing(false);return;}
        dispose();int token=generation;long epoch=LedgerStore.epoch();
        progress=AppIcons.dialog(m).setTitle("更新自选基金").setMessage("正在读取基金资料…").setNegativeButton("取消",(d,w)->dispose()).create();
        progress.setOnCancelListener(d->dispose());progress.show();
        new Thread(()->{
            List<FundClient.Quote> quotes=new ArrayList<>();List<String> failures=new ArrayList<>();
            for(String code:codes){
                if(!live(token))return;
                try{quotes.add(FundClient.fetchHistory(code));}
                catch(Exception e){failures.add(code+"："+NetworkIo.message(e));if(NetworkIo.connectivity(e))break;}
            }
            m.runOnUiThread(()->{
                if(!live(token))return;progress.dismiss();progress=null;if(pull!=null)pull.setRefreshing(false);
                try{if(!quotes.isEmpty()){synchronized(LedgerStore.class){Ledger.Data current=m.store.load();for(FundClient.Quote quote:quotes)apply(current,quote);m.commit(current,epoch);}m.render();}if(!failures.isEmpty())m.networkProblem(String.join("\n",failures));}
                catch(Exception e){m.error(e);}
            });
        },"watchlist-market").start();
    }
    void apply(Ledger.Data c,FundClient.Quote q){Ledger.WatchFund f=null;for(Ledger.WatchFund w:c.watchFunds)if(w.code.equals(q.code))f=w;if(f==null){f=new Ledger.WatchFund(q.code,q.name);c.watchFunds.add(f);}f.name=q.name;f.nav=q.nav;f.previous=q.previous;f.navDate=q.date;f.fundType=q.fundType;f.prices.putAll(q.history);f.returns.putAll(q.returns);f.historyComplete=q.complete;f.profile.inferLock(q.name);for(Ledger.WatchGroup group:c.watchGroups)if(group.name.equals(activeGroup))group.codes.add(q.code);}
    void refresh(){List<String> codes=new ArrayList<>();for(Ledger.WatchFund f:visible())codes.add(f.code);load(codes);}
    void manageGroups(){if(m.data.watchGroups.isEmpty()){groupEditor(null);return;}String[] names=new String[m.data.watchGroups.size()+1];names[0]="＋ 新建分组";for(int i=0;i<m.data.watchGroups.size();i++)names[i+1]=m.data.watchGroups.get(i).name;AppIcons.dialog(m).setTitle("管理自选分组").setItems(names,(d,w)->groupEditor(w==0?null:m.data.watchGroups.get(w-1))).setNegativeButton("关闭",null).show();}
    void groupEditor(Ledger.WatchGroup old){EditText name=new EditText(m);name.setSingleLine(true);name.setText(old==null?"":old.name);AlertDialog dialog=AppIcons.dialog(m).setTitle(old==null?"新建分组":"编辑分组").setView(name).setNegativeButton("取消",null).setPositiveButton("保存",null).setNeutralButton(old==null?null:"删除分组",null).create();dialog.show();dialog.getButton(-1).setOnClickListener(v->{try{String text=name.getText().toString().trim();if(text.isEmpty()||text.length()>12||Arrays.asList("全部","持有").contains(text))throw new IllegalArgumentException("分组名为1至12字，不能使用全部或持有");for(Ledger.WatchGroup g:m.data.watchGroups)if((old==null||g.id!=old.id)&&g.name.equals(text))throw new IllegalArgumentException("分组名重复");Ledger.Data c=m.copy();if(old==null)c.watchGroups.add(new Ledger.WatchGroup(c.nextId(),text));else for(Ledger.WatchGroup g:c.watchGroups)if(g.id==old.id)g.name=text;m.commit(c);activeGroup=text;dialog.dismiss();m.render();}catch(Exception e){m.error(e);}});if(old!=null)dialog.getButton(-3).setOnClickListener(v->AppIcons.dialog(m).setTitle("删除分组？").setMessage("基金仍保留在全部和其他分组中。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Ledger.Data c=m.copy();c.watchGroups.removeIf(g->g.id==old.id);m.commit(c);activeGroup="全部";dialog.dismiss();m.render();}).show());}
    void enterSelection(String code){selectionMode=true;selectedCodes.clear();selectedCodes.add(code);m.render();}
    void clearSelection(){selectionMode=false;selectedCodes.clear();m.render();}
    void selectionBar(){LinearLayout bar=m.row();bar.setPadding(0,m.dp(8),0,m.dp(4));bar.addView(m.action("取消",this::clearSelection),new LinearLayout.LayoutParams(0,m.dp(42),1));bar.addView(m.button("添加到",this::batchAdd),new LinearLayout.LayoutParams(0,m.dp(42),1));TextView remove=m.action("删除",this::batchDelete);remove.setTextColor(Ui.RED);remove.setTypeface(Ui.BOLD);bar.addView(remove,new LinearLayout.LayoutParams(0,m.dp(42),1));bar.setContentDescription("自选批量操作栏");bar.setBackgroundColor(Ui.SURFACE);m.root.addView(bar,new LinearLayout.LayoutParams(-1,-2));}
    void batchAdd(){if(selectedCodes.isEmpty()){m.toast("请先选择基金");return;}LinearLayout form=m.col();RadioGroup groups=new RadioGroup(m);for(Ledger.WatchGroup g:m.data.watchGroups){RadioButton radio=new RadioButton(m);radio.setId(View.generateViewId());radio.setText(g.name);radio.setTag(g.id);groups.addView(radio);}if(groups.getChildCount()>0)((RadioButton)groups.getChildAt(0)).setChecked(true);form.addView(groups);CheckBox remove=new CheckBox(m);remove.setText("是否从本组移除");form.addView(remove);AlertDialog dialog=AppIcons.dialog(m).setTitle("添加到分组").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("确认",null).create();dialog.show();dialog.getButton(-1).setOnClickListener(v->{int checked=groups.getCheckedRadioButtonId();if(checked==View.NO_ID){m.toast("请选择分组");return;}long id=groups.findViewById(checked).getTag() instanceof Long?(Long)groups.findViewById(checked).getTag():Long.parseLong(groups.findViewById(checked).getTag().toString());Ledger.Data c=m.copy();for(String code:selectedCodes){boolean exists=false;for(Ledger.WatchFund f:c.watchFunds)if(f.code.equals(code))exists=true;if(!exists){Ledger.WatchFund f=find(code);if(f!=null)c.watchFunds.add(f);}}for(Ledger.WatchGroup g:c.watchGroups)for(String code:selectedCodes){if(remove.isChecked()&&g.name.equals(activeGroup)&&g.id!=id)g.codes.remove(code);if(g.id==id)g.codes.add(code);}m.commit(c);dialog.dismiss();clearSelection();});}
    void batchDelete(){if(selectedCodes.isEmpty()){m.toast("请先选择基金");return;}AppIcons.dialog(m).setTitle("删除所选自选基金？").setMessage("只删除自选记录和分组关系，不删除已购基金。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Ledger.Data c=m.copy();c.watchFunds.removeIf(f->selectedCodes.contains(f.code));for(Ledger.WatchGroup g:c.watchGroups)g.codes.removeIf(selectedCodes::contains);m.commit(c);clearSelection();}).show();}
    void refreshDetail(){load(Collections.singletonList(m.watchFocus));fetchProfile(m.watchFocus,true);}
    void renderScroll(boolean bottom){int y=m.body.getParent() instanceof ScrollView?((ScrollView)m.body.getParent()).getScrollY():0;m.render();if(m.body.getParent() instanceof ScrollView){ScrollView scroll=(ScrollView)m.body.getParent();scroll.post(()->{if(bottom)scroll.fullScroll(View.FOCUS_DOWN);else scroll.scrollTo(0,y);});}}

    void addToGroup(Ledger.WatchFund fund,long groupId,boolean removeCurrent){Ledger.Data c=m.copy();boolean exists=false;for(Ledger.WatchFund f:c.watchFunds)if(f.code.equals(fund.code))exists=true;if(!exists)c.watchFunds.add(fund);for(Ledger.WatchGroup g:c.watchGroups){if(removeCurrent&&g.name.equals(activeGroup)&&g.id!=groupId)g.codes.remove(fund.code);if(g.id==groupId)g.codes.add(fund.code);}m.commit(c);m.render();}
    void detail(String code){
        Ledger.WatchFund f=find(code);if(f==null){m.back();return;}if(!code.equals(rangeFund)){range=4;try{if(LocalDate.parse(f.profile.established).plusYears(1).isAfter(FundEngine.now().toLocalDate()))range=9;}catch(Exception ignored){}rangeFund=code;}fetchProfile(code,false);LinearLayout detailHead=m.row();detailHead.addView(m.text("自选基金详情页",15,Ui.MUTED,false));m.body.addView(detailHead);m.gap(m.body,4);TextView fundTitle=m.text(f.name,21,Ui.INK,true);fundTitle.setBackground(Ui.roundStroke(Ui.ACCENT_TINT,Ui.BORDER,m.dp(10),m.dp(1)));fundTitle.setPadding(m.dp(10),m.dp(8),m.dp(10),m.dp(8));m.body.addView(fundTitle);
        LinearLayout identity=m.row();identity.addView(m.text(f.code+" · "+f.fundType,11,Ui.MUTED,false));
        if(f.profile.lockCount>0)identity.addView(m.text("  锁定期"+f.profile.lockCount+f.profile.lockUnit,11,Ui.RED,true));
        if(!f.profile.dailyLimit.isEmpty())identity.addView(m.text("  限制单日"+f.profile.dailyLimit,11,Ui.RED,true));m.body.addView(identity);m.gap(m.body,8);
        m.body.addView(m.button("购买",()->m.fundScreens.buyFromWatch(f.name,f.code)));m.gap(m.body,8);
        LinearLayout first=compactCard();LinearLayout metrics=m.row();WatchAnalytics.Result year=WatchAnalytics.calculate(f,4,FundEngine.now().toLocalDate()),all=WatchAnalytics.calculate(f,9,FundEngine.now().toLocalDate());double day=daily(f);
        detailMetric(metrics,"近1年涨跌幅",percent(year.change),year.change);detailMetric(metrics,"成立来涨跌幅",percent(all.change),all.change);detailMetric(metrics,"日涨跌幅\n"+f.navDate,percent(day),day);detailMetric(metrics,"最新净值\n"+f.navDate,f.nav.setScale(4,RoundingMode.HALF_UP).toPlainString(),Double.NaN);first.addView(metrics);m.body.addView(first);m.gap(m.body,7);
        LinearLayout second=compactCard(),facts=m.row();String elapsed="待获取";try{elapsed=WatchAnalytics.elapsed(LocalDate.parse(f.profile.established),FundEngine.now().toLocalDate());}catch(Exception ignored){}
        detailMetric(facts,"成立日期",f.profile.established.isEmpty()?"待获取":f.profile.established,Double.NaN);detailMetric(facts,"距今",elapsed,Double.NaN);detailMetric(facts,"基金规模",f.profile.scale.isEmpty()?"待获取":f.profile.scale.replaceAll("[（(].*", "").trim(),Double.NaN);second.addView(facts);m.body.addView(second);m.gap(m.body,7);
        LinearLayout chart=compactCard();LinearLayout switcher=m.row();TextView performance=m.action("业绩走势",()->{detailChartMode=0;renderScroll(false);}),repair=m.action("回撤修复",()->{detailChartMode=1;renderScroll(false);});performance.setTextSize(12);repair.setTextSize(12);performance.setBackground(Ui.round(detailChartMode==0?Ui.ACCENT_SOFT:Ui.SURFACE,m.dp(10)));repair.setBackground(Ui.round(detailChartMode==1?Ui.ACCENT_SOFT:Ui.SURFACE,m.dp(10)));performance.setTypeface(detailChartMode==0?Ui.BOLD:Ui.MEDIUM);repair.setTypeface(detailChartMode==1?Ui.BOLD:Ui.MEDIUM);switcher.addView(performance,new LinearLayout.LayoutParams(0,m.dp(38),1));switcher.addView(repair,new LinearLayout.LayoutParams(0,m.dp(38),1));chart.addView(switcher);m.gap(chart,7);
        WatchAnalytics.Result r=metrics(f);LinearLayout labels=m.row();if(detailChartMode==0){detailMetric(labels,"本时期涨跌幅",percent(r.change),r.change);detailMetric(labels,"本时期最高下跌幅度",String.format(Locale.CHINA,"%.2f%%",r.drawdown),Double.NaN);}else{detailMetric(labels,"最大回撤",String.format(Locale.CHINA,"%.2f%%",r.drawdown),Double.NaN);detailMetric(labels,"最大回撤修复天数",r.drawdown==0?"0天":r.recovered?r.recoveryDays+"天":"正在修复中",Double.NaN);}chart.addView(labels);chart.addView(new WatchChart(m,r,detailChartMode==1),new LinearLayout.LayoutParams(-1,m.dp(Math.max(170,Math.min(210,m.getResources().getConfiguration().screenHeightDp/4)))));
        HorizontalScrollView periods=new HorizontalScrollView(m);detailPeriods=periods;periods.setOnScrollChangeListener((v,x,y,oldX,oldY)->periodScrollX=x);periods.setContentDescription("详情绘图周期");periods.setHorizontalScrollBarEnabled(false);LinearLayout chips=m.row();for(int i=0;i<WatchAnalytics.RANGES.length;i++){final int choice=i;TextView chip=m.action(WatchAnalytics.RANGES[i],()->{periodScrollX=periods.getScrollX();range=choice;renderScroll(false);});chip.setContentDescription("绘图周期"+WatchAnalytics.RANGES[i]);chip.setBackground(Ui.round(range==i?Ui.ACCENT_SOFT:Ui.SURFACE,m.dp(18)));chips.addView(chip);}periods.addView(chips);chart.addView(periods);periods.post(()->periods.scrollTo(periodScrollX,0));m.body.addView(chart,new LinearLayout.LayoutParams(-1,-2));m.gap(m.body,7);rules(f);
    }
    LinearLayout compactCard(){LinearLayout card=m.card();card.setPadding(m.dp(8),m.dp(7),m.dp(8),m.dp(7));return card;}
    void detailMetric(LinearLayout row,String label,String value,double sign){
        row.setBaselineAligned(false);
        LinearLayout column=m.col();column.setGravity(Gravity.CENTER);column.setPadding(m.dp(3),0,m.dp(3),0);
        column.setContentDescription("自选详情指标"+label);
        TextView title=m.text(label,10,Ui.MUTED,false);title.setMaxLines(2);title.setGravity(Gravity.CENTER);
        column.addView(title,new LinearLayout.LayoutParams(-1,m.dp(label.contains("\n")||label.startsWith("近1年")||label.startsWith("成立来")?28:18)));
        TextView text=m.fit(m.text(value,label.startsWith("近1年")||label.startsWith("成立来")?16:14,Double.isNaN(sign)?Ui.INK:sign>0?Ui.RED:sign<0?Ui.GREEN:Ui.INK,true),label.startsWith("近1年")||label.startsWith("成立来")?16:14,8);
        text.setGravity(Gravity.CENTER);text.setTypeface(Ui.BOLD);
        column.addView(text,new LinearLayout.LayoutParams(-1,m.dp(24)));
        row.addView(column,new LinearLayout.LayoutParams(0,-2,1));
    }
    void rules(Ledger.WatchFund f){
        LinearLayout card=compactCard();
        TextView toggle=m.action("买入 / 卖出规则  "+(rulesExpanded?"▴":"▾"),()->{rulesExpanded=!rulesExpanded;renderScroll(rulesExpanded);});
        card.addView(toggle);
        ruleFund=f;ruleContent=null;ruleTabs=null;
        if(rulesExpanded){
            m.gap(card,6);ruleTabs=new Segmented(m,"买入规则","卖出规则");ruleTabs.setSelected(ruleTab);ruleTabs.onSelect(()->{ruleTab=ruleTabs.selected();updateRuleContent();});card.addView(ruleTabs);m.gap(card,6);ruleContent=m.col();card.addView(ruleContent);updateRuleContent();
        }
        m.body.addView(card);
    }
    void updateRuleContent(){
        if(ruleContent==null||ruleFund==null)return;int oldHeight=ruleContent.getHeight();ruleContent.setMinimumHeight(oldHeight);ruleContent.removeAllViews();
        if(ruleTab==0){ruleContent.addView(m.text(ruleFund.profile.lockLabel(),13,Ui.AMBER,true));m.gap(ruleContent,6);feeRows(ruleContent,"买入费率",ruleFund.profile.orUnknown(ruleFund.profile.buyRules));m.gap(ruleContent,4);feeRows(ruleContent,"运作费率","管理费  "+ruleFund.profile.orUnknown(ruleFund.profile.management)+"\n托管费  "+ruleFund.profile.orUnknown(ruleFund.profile.custody)+"\n销售服务费  "+ruleFund.profile.orUnknown(ruleFund.profile.service));ruleContent.addView(m.text("上述费用通常从基金资产中计提，净值已扣除。",11,Ui.MUTED,false));}
        else feeRows(ruleContent,"卖出费率（持有时间分档）",ruleFund.profile.orUnknown(ruleFund.profile.saleRules));
        m.gap(ruleContent,6);ruleContent.addView(m.text("不同平台优惠及确认时间可能不同，不是所有基金都T+1。费率/开放日以基金公司及交易平台最新公告为准。资料更新："+(ruleFund.profile.updated.isEmpty()?"未获取":ruleFund.profile.updated),10,Ui.MUTED,false));ruleContent.addView(m.ui.link("查看基金官方披露来源",()->m.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://fundf10.eastmoney.com/jjfl_"+ruleFund.code+".html")))));
    }
    void feeRows(LinearLayout card,String heading,String content){
        card.addView(m.text(heading,13,Ui.INK,true));
        boolean discount=heading.equals("买入费率")&&(content.contains("优惠")||content.contains("|"));
        LinearLayout header=m.row();feeCell(header,heading.equals("运作费率")?"费用类别":heading.startsWith("卖出")?"持有时间":"适用金额",.50f,false,Ui.MUTED);
        if(discount){feeCell(header,"原费率",.25f,false,Ui.MUTED);feeCell(header,"优惠费率",.25f,false,0xFFE87920);}else feeCell(header,"费率",.50f,false,Ui.MUTED);card.addView(header);card.addView(m.ui.divider());
        for(String line:content.split("\n")){
            String clean=line.trim();if(clean.isEmpty()||clean.contains("适用金额")||clean.contains("原费率")||clean.contains("持有期限")||clean.contains("持有时间")||clean.contains("费用类别"))continue;
            String[] cells=clean.split("\\s{2,}|\\s*\\|\\s*");
            if(cells.length>1){java.util.regex.Matcher embedded=java.util.regex.Pattern.compile("^(.*?)\\s+([0-9.]+%)$").matcher(cells[0]);if(embedded.find()){String[] expanded=new String[cells.length+1];expanded[0]=embedded.group(1);expanded[1]=embedded.group(2);System.arraycopy(cells,1,expanded,2,cells.length-1);cells=expanded;}}
            if(cells.length<2){java.util.regex.Matcher match=java.util.regex.Pattern.compile("^(.*?)\\s+((?:[0-9.]+%|每笔[0-9.,]+元|[0-9.,]+元).*)$").matcher(clean);if(match.find())cells=new String[]{match.group(1),match.group(2)};}
            LinearLayout row=m.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,m.dp(7),0,m.dp(7));
            if(cells.length<2){feeCell(row,clean,1,false,Ui.MUTED);}else{
                feeCell(row,cells[0],.50f,false,Ui.INK);
                if(discount){feeCell(row,cells[1],.25f,true,Ui.INK);feeCell(row,cells.length>2?cells[2]:"—",.25f,true,0xFFE87920);}
                else feeCell(row,cells[1],.50f,true,Ui.INK);
            }
            card.addView(row);card.addView(m.ui.divider());
        }
    }
    void feeCell(LinearLayout row,String value,float weight,boolean bold,int color){TextView cell=m.text(value,12,color,bold);cell.setGravity(row.getChildCount()==0?Gravity.START|Gravity.CENTER_VERTICAL:Gravity.RIGHT|Gravity.CENTER_VERTICAL);cell.setPadding(m.dp(2),m.dp(3),m.dp(2),m.dp(3));if(bold)cell.setTypeface(Ui.BOLD);row.addView(cell,new LinearLayout.LayoutParams(0,-2,weight));}
    void fetchProfile(String code,boolean force){Ledger.WatchFund f=find(code);if(f==null||!force&&(!f.profile.updated.isEmpty()||!profileAttempts.add(code)))return;profileAttempts.add(code);int profileToken=generation;long epoch=LedgerStore.epoch();new Thread(()->{try{FundProfile profile=FundClient.profile(code,f.name);m.runOnUiThread(()->{if(!live(profileToken))return;try{Ledger.Data c=m.store.load();boolean found=false;for(Ledger.WatchFund w:c.watchFunds)if(w.code.equals(code)){w.profile.read(profile.encode());found=true;}for(Ledger.Fund owned:c.funds)if(owned.code.equals(code)){owned.profile.read(profile.encode());found=true;}if(found&&live(profileToken)){m.commit(c,epoch);if(m.page.equals("watch_detail")&&m.watchFocus.equals(code))renderScroll(false);}}catch(Exception e){m.error(e);}});}catch(Exception ignored){}},"fund-profile").start();}
    Bundle save(){Bundle state=new Bundle();state.putString("group",activeGroup);state.putInt("mode",detailChartMode);state.putBoolean("expanded",rulesExpanded);return state;}
}
