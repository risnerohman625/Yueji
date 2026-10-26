package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.math.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

/** Portfolio screens use confirmed historical NAVs, never the current NAV for an old purchase. */
final class FundScreens {
    final MainActivity m;
    YearMonth calendarMonth=YearMonth.from(FundEngine.now());int mode=0,range=2,chartTab=0;boolean percent=false;TradingCalendar cachedCalendar;
    PullRefreshLayout pull;
    FundScreens(MainActivity activity){m=activity;}

    TradingCalendar calendar(){
        if(cachedCalendar==null){TradingCalendar c=new TradingCalendar();for(int y=2010;y<=FundEngine.now().toLocalDate().getYear()+1;y++)c.load(m,y);cachedCalendar=c;}return cachedCalendar;
    }
    int color(long cents){return cents>0?MainActivity.RED:cents<0?MainActivity.EXP:MainActivity.MUTED;}
    String rate(long profit,long cost){return String.format(Locale.CHINA,"%+.2f%%",FundEngine.rate(profit,cost));}
    Ledger.Fund find(long id){for(Ledger.Fund f:m.data.funds)if(f.id==id)return f;return null;}

    static final class Cell {
        final String label,value,description;final int color;final View.OnClickListener click;
        Cell(String label,String value,int color,String description,View.OnClickListener click){this.label=label;this.value=value;this.color=color;this.description=description;this.click=click;}
        Cell(String label,String value,int color){this(label,value,color,null,null);}
    }
    /** One line of three labelled values, the layout used by every fund summary block. */
    LinearLayout metricRow(LinearLayout card,Cell... cells){
        return metricRow(card,false,cells);
    }
    LinearLayout metricRow(LinearLayout card,boolean strong,Cell... cells){
        LinearLayout row=m.row();int vertical=m.compactScreen()?3:5;row.setPadding(0,m.dp(vertical),0,m.dp(vertical));
        for(Cell c:cells){
            LinearLayout column=m.col();column.setPadding(m.dp(2),0,m.dp(5),0);column.setClipChildren(true);
            TextView label=m.fit(m.text(c.label,11,strong?MainActivity.INK:MainActivity.MUTED,c.click!=null),11,9);
            if(c.click!=null)label.setTypeface(Ui.BOLD);
            column.addView(label,new LinearLayout.LayoutParams(-1,m.dp(20)));m.gap(column,5);
            TextView value=m.fit(m.text(c.value,15,strong&&c.color==MainActivity.MUTED?MainActivity.INK:c.color,true),15,8);value.setTag("bounded-number");
            if(c.click!=null)value.setTypeface(Ui.BOLD);
            column.addView(value,new LinearLayout.LayoutParams(-1,m.dp(26)));
            if(c.click!=null)column.setOnClickListener(c.click);
            if(c.description!=null)column.setContentDescription(c.description);
            m.weight(row,column);
        }
        card.addView(row);return row;
    }

    // ---------------------------------------------------------------- home list
    void list(Ledger.Data data){
        int filter=m.bankScreens.filter();LocalDate today=FundEngine.now().toLocalDate();
        PortfolioSummary totals=PortfolioSummary.of(data,filter,FundEngine.now(),calendar());
        long value=totals.value,cost=totals.cost,cumulative=totals.cumulative,heldValue=totals.heldValue,current=totals.current,yesterday=totals.previous;
        int count=totals.count,hidden=totals.hidden,deleted=totals.deleted,shown=totals.shown;
        boolean complete=totals.complete,yesterdayKnown=totals.previousKnown,banks=totals.banks;
        LinearLayout summary=m.card(m.body);
        android.graphics.drawable.GradientDrawable wash=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Ui.SURFACE,Ui.ACCENT_SOFT});wash.setCornerRadius(m.dp(22));
        summary.setBackground(wash);summary.setPadding(m.dp(18),m.dp(16),m.dp(18),m.dp(14));
        LinearLayout header=m.row(),amount=m.col();amount.setClipChildren(true);amount.addView(m.text("持有总金额",13,MainActivity.INK,true));m.gap(amount,6);
        TextView totalAmount=m.fit(m.text("¥ "+m.money(value),25,MainActivity.INK,true),25,10);totalAmount.setTag("bounded-number");amount.addView(totalAmount,new LinearLayout.LayoutParams(-1,m.dp(36)));
        header.addView(amount,new LinearLayout.LayoutParams(0,-2,1.6f));
        LinearLayout.LayoutParams curveParams=new LinearLayout.LayoutParams(0,m.dp(60),1);curveParams.leftMargin=m.dp(10);header.addView(new HoldingSparkline(m,FundRules.trend(totals.selected,today)),curveParams);summary.addView(header);m.gap(summary,6);
        metricRow(summary,true,new Cell("持有收益",complete?m.signed(value-cost):"待数据",complete?color(value-cost):MainActivity.INK),new Cell("持有收益率",complete?rate(value-cost,cost):"—",complete?color(value-cost):MainActivity.INK),new Cell("累计收益 ›",complete?m.signed(cumulative):"待数据",complete?color(cumulative):MainActivity.INK,"累计收益明细",v->m.go("fund_profit")));
        metricRow(summary,true,new Cell(filter==2?"本周收益":"当日收益",totals.currentKnown?m.signed(current):"待更新",current==0?MainActivity.INK:color(current)),new Cell(filter==2?"上周收益":"昨日收益",yesterdayKnown?m.signed(yesterday):"待更新",yesterdayKnown&&yesterday!=0?color(yesterday):MainActivity.INK),new Cell(filter==1?"持有基金数量":"持有产品数量",""+count,MainActivity.INK));

        if(hidden>0||deleted>0){
            m.gap(summary,8);String note=(hidden>0?"已隐藏 "+hidden+" 只，不计入以上金额与收益":"")+(hidden>0&&deleted>0?"；":"")+(deleted>0?"已删除 "+deleted+" 只，其收益保留在累计收益中":"");
            summary.addView(m.text(note,10.5f,MainActivity.INK,false));
        }
        summary.setOnClickListener(v->m.go("fund_total"));
        LinearLayout tools=m.row();TextView add=m.button("＋ 添加基金",this::addChoice);add.setMinHeight(m.dp(42));add.setTextSize(13.5f);tools.addView(add);
        TextView watch=m.ui.link("自选",()->m.go("fund_watchlist"));watch.setPadding(m.dp(16),m.dp(10),m.dp(16),m.dp(10));watch.setTextSize(14);watch.setTypeface(Ui.BOLD);watch.setBackground(Ui.roundStroke(Ui.ACCENT_TINT,Ui.ACCENT,m.dp(12),m.dp(1)));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-2,-2);wp.leftMargin=m.dp(12);watch.setLayoutParams(wp);watch.setContentDescription("打开自选基金");tools.addView(watch);m.weight(tools,new View(m));tools.addView(m.bankScreens.filterButton());tools.addView(sortButton());m.body.addView(tools);m.gap(m.body,12);
        if(shown==0){
            LinearLayout empty=m.card(m.body);empty.setGravity(Gravity.CENTER);empty.setPadding(m.dp(16),m.dp(30),m.dp(16),m.dp(30));
            empty.addView(m.text(deleted>0?"首页没有基金":"还没有添加基金",15,MainActivity.INK,true));m.gap(empty,8);
            TextView hint=m.text(deleted>0||hidden>0?"已删除或隐藏的基金保存在“累计收益”里，可以在那里恢复显示":"填写基金代码和买入金额，软件按交易净值计算份额与收益",12,MainActivity.MUTED,false);hint.setGravity(Gravity.CENTER);empty.addView(hint);
        }
        for(Ledger.Fund f:FundRules.sorted(data,sortOrder(),false,today)){
            if(m.bankScreens.filter()==2)continue;
            LinearLayout c=m.card();c.setBackground(m.ripple(Ui.SURFACE,18));c.setContentDescription("基金"+f.code);c.setPadding(m.dp(12),m.dp(8),m.dp(12),m.dp(8));
            LinearLayout head=m.row();m.weight(head,new FundNameView(m,f.name,()->{m.fundFocus=f.id;m.go("fund_detail");}));
            boolean holding=FundEngine.held(f,today);long own=FundEngine.value(f,today);
            c.addView(head);m.gap(c,3);
            LinearLayout r=m.row();long fundToday=FundEngine.today(f,FundEngine.now()),heldP=FundEngine.heldProfit(f,today);Long fundYesterday=FundEngine.daily(f,today.minusDays(1),calendar());
            compactMetric(r,"持有金额",m.money(own),MainActivity.INK);
            compactMetric(r,"当日收益",m.signed(fundToday),color(fundToday));
            compactMetric(r,"昨日收益",fundYesterday==null?"待更新":m.signed(fundYesterday),fundYesterday==null?MainActivity.MUTED:color(fundYesterday));
            compactMetric(r,"持有收益",FundEngine.known(f,today)?m.signed(heldP):"待数据",color(heldP));c.addView(r);
            m.gap(c,3);LinearLayout footer=m.row();
            String share=holding&&heldValue>0?String.format(Locale.CHINA,"持仓占比 %.1f%%",own*100.0/heldValue):"已卖出";
            TextView stamp=holdingStamp(share,"更新 "+(f.updatedAt.isEmpty()?(f.navDate.isEmpty()?"待更新":f.navDate):f.updatedAt.replace('T',' ').substring(0,Math.min(16,f.updatedAt.length()))));
            m.weight(footer,stamp);if(!f.tag.isEmpty()){TextView label=m.ui.tag(f.tag,Ui.ACCENT_DEEP,Ui.ACCENT_SOFT);label.setMaxWidth(m.dp(100));label.setMaxLines(2);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=m.dp(6);footer.addView(label,tp);}c.addView(footer);
            c.setOnClickListener(v->{m.fundFocus=f.id;m.go("fund_detail");});
            if(f.hidden)SwipeRow.dim(c,0.55f);
            SwipeRow row=new SwipeRow(m,c,new SwipeRow.Action("删除","删除基金卡片"+f.code,MainActivity.RED,false,()->confirmRemove(f)),
                new SwipeRow.Action("标签","基金标签"+f.code,MainActivity.AMBER,false,()->editTag(f)),
                new SwipeRow.Action("编辑","编辑基金"+f.code,MainActivity.PURPLE,false,()->{m.fundFocus=f.id;m.go("fund_trades");}));
            row.setTag(PortfolioOrder.fund(f,today));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=m.dp(6);m.body.addView(row,p);
        }
        if(m.bankScreens.filter()!=1)m.bankScreens.append(false);
        PortfolioOrder.apply(m.body,sortOrder());
        if(m.refreshing&&pull!=null)pull.setRefreshing(true);
    }
    int sortOrder(){return Math.max(0,Math.min(2,m.getPreferences(0).getInt("fundSort",2)));}
    void addChoice(){AppIcons.dialog(m).setTitle("添加基金").setItems(new String[]{"手动输入","添加银行理财"},(d,w)->{if(w==0)buy(null,null);else m.bankScreens.start(0);}).setNegativeButton("取消",null).show();}
    void buyFromWatch(String name,String code){buyInternal(null,null,0,0,0,false,name,code);}
    TextView sortButton(){
        String[] names={"购入时间","持有金额","持有收益"};int selected=Math.max(0,Math.min(2,sortOrder()));
        TextView button=m.ui.link("⇅ "+names[selected],()->{m.getPreferences(0).edit().putInt("fundSort",FundRules.nextSort(selected)).apply();m.rerenderKeepingScroll();});button.setContentDescription("基金排序");button.setTextSize(12);return button;
    }
    void addTag(LinearLayout row,String label,int color,int fill){TextView t=m.ui.tag(label,color,fill);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=m.dp(8);row.addView(t,p);}
    void compactMetric(LinearLayout row,String label,String value,int color){
        int index=row.getChildCount();int align=(index==0?Gravity.START:index==3?Gravity.END:Gravity.CENTER_HORIZONTAL)|Gravity.CENTER_VERTICAL;
        LinearLayout cell=m.col();cell.setPadding(m.dp(index==0?1:2),0,m.dp(index==3?1:2),0);cell.setContentDescription("持仓指标"+label);
        TextView title=m.fit(m.text(label,10,Ui.MUTED,false),10,9);title.setGravity(align);cell.addView(title,new LinearLayout.LayoutParams(-1,m.dp(15)));
        TextView number=m.fit(m.text(value,14,color,true),14,8);number.setGravity(align);number.setTag("bounded-number");
        if(value.length()>13)number.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD));
        cell.addView(number,new LinearLayout.LayoutParams(-1,m.dp(22)));row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    TextView holdingStamp(String share,String suffix){
        String label=share.isEmpty()?suffix:share+" · "+suffix;SpannableString styled=new SpannableString(label);
        if(share.startsWith("持仓占比")){
            styled.setSpan(new ForegroundColorSpan(Ui.ACCENT_DEEP),0,share.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),0,share.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView view=m.fit(m.text(label,10.5f,Ui.MUTED,false),10.5f,8);view.setText(styled);view.setTag("holding-percentage");return view;
    }
    void editTag(Ledger.Fund fund){
        LinearLayout form=m.col();form.setPadding(m.dp(18),0,m.dp(18),m.dp(12));
        EditText tag=m.field(form,"基金标签（最多12字，留空移除）",fund.tag,false);tag.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});
        AlertDialog dialog=AppIcons.dialog(m).setTitle("基金标签").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{
            Ledger.Data candidate=m.copy();Ledger.Fund current=null;for(Ledger.Fund f:candidate.funds)if(f.id==fund.id)current=f;
            if(current==null)throw new IllegalArgumentException("基金不存在");current.tag=tag.getText().toString().trim();m.commit(candidate);dialog.dismiss();m.rerenderKeepingScroll();
        }catch(Exception e){m.error(e);}});
    }

    void confirmRemove(Ledger.Fund f){
        AppIcons.dialog(m).setTitle("删除 "+f.name+"？")
            .setMessage("基金将从首页移除并停止计算收益，删除前的收益仍保留在“累计收益”里，随时可以在那里恢复。")
            .setNegativeButton("取消",null).setPositiveButton("确认删除",(d,w)->removeFromHome(f)).show();
    }
    void removeFromHome(Ledger.Fund fund){
        try{
            Ledger.Data candidate=m.copy();Ledger.Fund target=null;for(Ledger.Fund f:candidate.funds)if(f.id==fund.id)target=f;
            if(target==null)throw new IllegalArgumentException("基金不存在");
            FundEngine.remove(target,FundEngine.now());m.commit(candidate);m.render();
        }catch(Exception e){m.error(e);}
    }
    void restore(Ledger.Fund fund){
        try{
            Ledger.Data candidate=m.copy();Ledger.Fund target=null;for(Ledger.Fund f:candidate.funds)if(f.id==fund.id)target=f;
            if(target==null)throw new IllegalArgumentException("基金不存在");
            FundEngine.restore(target);target.hidden=false;m.commit(candidate);m.render();m.toast("已恢复到首页");m.refreshFunds();
        }catch(Exception e){m.error(e);}
    }
    /** Permanent removal: purchases and their linked transfer records go too. */
    void confirmPurge(Ledger.Fund f){
        if(!f.removed()){m.toast("请先从基金首页删除，再到累计收益中永久删除");return;}
        AppIcons.dialog(m).setTitle("永久删除 "+f.name+"？")
            .setMessage("基金 "+f.code+" 的全部买入记录与关联的申购、赎回资金划转记录都会被移除，累计收益也不再保留，此操作无法恢复。")
            .setNegativeButton("取消",null).setPositiveButton("确认永久删除",(d,w)->deleteFund(f)).show();
    }
    void deleteFund(Ledger.Fund fund){
        try{
            Ledger.Data candidate=m.copy();Ledger.Fund target=null;for(Ledger.Fund f:candidate.funds)if(f.id==fund.id)target=f;
            if(target==null)throw new IllegalArgumentException("基金不存在");
            FundRules.requirePurge(target);
            Set<Long> buyIds=new HashSet<>();for(Ledger.FundBuy b:target.buys)buyIds.add(b.id);
            candidate.funds.remove(target);
            candidate.entries.removeIf(e->{for(Long id:buyIds)if(e.note.equals("自动关联买入 "+id)||e.note.equals("自动关联卖出 "+id))return true;return false;});
            m.commit(candidate);m.render();
        }catch(Exception e){m.error(e);}
    }
    void setHidden(Ledger.Fund fund,boolean hidden){
        try{
            Ledger.Data candidate=m.copy();boolean found=false;
            for(Ledger.Fund f:candidate.funds)if(f.id==fund.id){f.hidden=hidden;found=true;}
            if(!found)throw new IllegalArgumentException("基金不存在");
            m.commit(candidate);m.render();
        }catch(Exception e){m.error(e);}
    }

    // ---------------------------------------------------------------- cumulative profit page
    /** Every fund ever added. Swipe a card left for hide / restore / permanent delete. */
    void profitList(Ledger.Data data){
        m.top("累计收益明细",null,null);
        LinearLayout toolbar=m.row();m.weight(toolbar,new View(m));toolbar.addView(sortButton());m.body.addView(toolbar);
        LocalDate today=FundEngine.now().toLocalDate();long total=0;int hidden=0,deleted=0;
        for(Ledger.Fund f:data.funds){if(f.removed())deleted++;if(f.hidden){hidden++;continue;}total+=FundEngine.cumulative(f,today);}
        for(BankLedger.Product p:data.banks){if(p.removed())deleted++;if(p.hidden){hidden++;continue;}total+=BankLedger.profit(p);}
        LinearLayout head=m.card(m.body);head.setBackground(Ui.hero(m.dp(22)));head.setPadding(m.dp(18),m.dp(16),m.dp(18),m.dp(16));
        head.addView(m.text("计入总览的累计收益",12.5f,0xCCFFFFFF,false));m.gap(head,6);
        TextView profit=m.fit(m.text(m.signed(total),27,Color.WHITE,true),27,10);profit.setTag("bounded-number");head.addView(profit,new LinearLayout.LayoutParams(-1,m.dp(38)));m.gap(head,9);
        head.addView(m.text("从首页删除的基金不再继续计算，收益停在删除时；左滑卡片可隐藏、恢复到首页或永久删除。",11.5f,0xCCFFFFFF,false));
        if(hidden>0||deleted>0){m.gap(head,6);head.addView(m.text((hidden>0?"隐藏 "+hidden+" 只":"")+(hidden>0&&deleted>0?" · ":"")+(deleted>0?"已删除 "+deleted+" 只":""),11.5f,Color.WHITE,true));}
        if(data.funds.isEmpty()&&data.banks.isEmpty()){LinearLayout empty=m.card(m.body);empty.addView(m.text("还没有添加过基金",13,MainActivity.MUTED,false));return;}
        for(Ledger.Fund f:FundRules.sorted(data,sortOrder(),true,today)){
            LinearLayout c=m.card();c.setBackground(m.ripple(Ui.SURFACE,18));c.setContentDescription("累计"+f.code);
            LinearLayout top=m.row();TextView name=m.text(f.name,14,MainActivity.INK,true);name.setMaxLines(2);top.addView(name,new LinearLayout.LayoutParams(0,-2,1.1f));
            long cumulative=FundEngine.cumulative(f,today);TextView totalValue=m.fit(m.text(m.signed(cumulative),16,color(cumulative),true),16,8);totalValue.setTag("bounded-number");
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,m.dp(28),1);ap.leftMargin=m.dp(8);top.addView(totalValue,ap);c.addView(top);m.gap(c,5);
            String state=f.removed()?"已删除 · 收益保留至 "+f.frozenOn:FundEngine.held(f,today)?"持有收益 "+m.signed(FundEngine.heldProfit(f,today)):"已卖出";
            c.addView(m.text(f.code+" · "+state+(f.hidden?" · 已隐藏":""),11,f.removed()?MainActivity.AMBER:MainActivity.MUTED,false));m.gap(c,8);
            LocalDateTime firstBuy=null;LocalDate firstConfirm=null,lastSale=null;boolean allSold=!f.buys.isEmpty();
            for(Ledger.FundBuy b:f.buys){if(firstBuy==null||b.at.isBefore(firstBuy))firstBuy=b.at;if(b.confirmDate!=null&&(firstConfirm==null||b.confirmDate.isBefore(firstConfirm)))firstConfirm=b.confirmDate;if(b.saleDate==null)allSold=false;else if(lastSale==null||b.saleDate.isAfter(lastSale))lastSale=b.saleDate;}
            String endLabel=f.removed()||!allSold?"删除时间":"卖出时间",end=f.removed()?MainActivity.TIME.format(f.removedAt):allSold?lastSale.toString():"—";
            LinearLayout times=m.row();times.setGravity(Gravity.TOP);
            timeMetric(times,"买入时间",firstBuy==null?"—":MainActivity.TIME.format(firstBuy));
            timeMetric(times,"确认份额时间",firstConfirm==null?(f.buys.isEmpty()?"—":"待确认"):firstConfirm.toString());
            timeMetric(times,endLabel,end);c.addView(times);
            c.setOnClickListener(v->{m.fundFocus=f.id;m.go(f.removed()?"fund_trades":"fund_detail");});
            // Hidden / deleted funds read as dimmed through their contents; the card surface stays opaque so the swipe buttons never show through.
            if(f.hidden||f.removed())SwipeRow.dim(c,f.removed()?0.8f:0.55f);
            SwipeRow row=new SwipeRow(m,c,
                new SwipeRow.Action(f.hidden?"取消隐藏":"隐藏","隐藏"+f.code,MainActivity.AMBER,f.removed(),()->{if(f.removed())m.toast("已删除的基金不在首页，无需隐藏");else setHidden(f,!f.hidden);}),
                new SwipeRow.Action("恢复","恢复"+f.code,MainActivity.PURPLE,!f.removed(),()->{if(!f.removed())m.toast("该基金仍在首页");else restore(f);}),
                new SwipeRow.Action("删除","永久删除"+f.code,MainActivity.RED,!f.removed(),()->confirmPurge(f)));
            row.setTag(PortfolioOrder.fund(f,today));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=m.dp(6);m.body.addView(row,p);
        }
        m.bankScreens.append(true);
        PortfolioOrder.apply(m.body,sortOrder());
    }
    void timeMetric(LinearLayout row,String label,String value){
        LinearLayout column=m.col();column.setPadding(0,0,m.dp(4),0);column.addView(m.text(label,10,MainActivity.MUTED,false));m.gap(column,2);column.addView(m.text(value,11,MainActivity.INK,false));m.weight(row,column);
    }

    // ---------------------------------------------------------------- detail and overview (one portrait screen)
    void detail(Ledger.Data data,long id,boolean all){
        Ledger.Fund focus=all?null:find(id);if(!all&&(focus==null||focus.removed())){m.back();return;}
        List<Ledger.Fund> onHome=new ArrayList<>(),ever=new ArrayList<>();
        if(all){for(Ledger.Fund f:data.funds){if(f.hidden)continue;ever.add(f);if(!f.removed())onHome.add(f);}}else{onHome.add(focus);ever.add(focus);}
        if(all){
            LinearLayout head=m.row();head.setPadding(m.dp(4),m.dp(2),m.dp(2),m.dp(8));m.weight(head,m.text("基金总览",21,MainActivity.INK,true));TextView export=m.action("导出",m::exportFunds);export.setContentDescription("导出基金信息");head.addView(export);m.body.addView(head);
        }else m.top(focus.name,"返回",m::back);
        TradingCalendar calendar=calendar();LocalDate today=FundEngine.now().toLocalDate();
        long value=0,cost=0,current=0,yesterday=0,invested=0,cumulative=0,heldAll=0;boolean yesterdayKnown=true,complete=true,cumulativeKnown=true;
        for(Ledger.Fund f:onHome){value+=FundEngine.value(f,today);cost+=FundEngine.holdingCost(f,today);current+=FundEngine.today(f,FundEngine.now());if(!FundEngine.known(f,today))complete=false;Long prior=FundEngine.daily(f,today.minusDays(1),calendar);if(prior==null)yesterdayKnown=false;else yesterday+=prior;}
        for(Ledger.Fund f:ever){invested+=FundEngine.invested(f);cumulative+=FundEngine.cumulative(f,today);if(!FundEngine.known(f,today))cumulativeKnown=false;}
        for(Ledger.Fund f:data.funds)if(!f.hidden&&!f.removed()&&FundEngine.held(f,today))heldAll+=FundEngine.value(f,today);
        boolean bankTotal=false;
        if(all)for(BankLedger.Product p:data.banks)if(!p.hidden){
            bankTotal=true;cumulative+=BankLedger.profit(p);
            if(!p.removed()){long bankValue=BankLedger.balance(p),profit=BankLedger.profit(p);value+=bankValue;cost+=BankAccounting.cost(p,FundEngine.now());invested+=BankAccounting.invested(p);}
        }
        for(BankLedger.Product p:data.banks)if(!p.hidden&&!p.removed())heldAll+=BankLedger.balance(p);
        long[] extremes=FundEngine.dailyExtremes(ever,calendar);
        String high=extremes==null?"—":m.signed(extremes[0]),low=extremes==null?"—":m.signed(extremes[1]);
        int highColor=extremes==null?MainActivity.MUTED:color(extremes[0]),lowColor=extremes==null?MainActivity.MUTED:color(extremes[1]);
        LinearLayout info=m.card(m.body);info.setPadding(m.dp(12),m.dp(4),m.dp(12),m.dp(6));
        Cell holdValue=new Cell("持有金额","¥ "+m.money(value),MainActivity.INK),holdProfit=new Cell("持有收益",complete?m.signed(value-cost):"待数据",color(value-cost)),holdRate=new Cell("持有收益率",complete?rate(value-cost,cost):"—",color(value-cost));
        Cell buyTotal=new Cell("累计买入金额","¥ "+m.money(invested),MainActivity.INK),todayCell=new Cell("当日收益",m.signed(current),color(current)),yesterdayCell=new Cell("昨日收益",yesterdayKnown?m.signed(yesterday):"待更新",yesterdayKnown?color(yesterday):MainActivity.MUTED);
        metricRow(info,holdValue,holdProfit,holdRate);
        if(all){
            if(bankTotal)info.addView(m.text("持有金额、收益率和累计收益包含银行理财；当日/昨日收益仅含可自动更新的基金。",10,Ui.INK,false));
            metricRow(info,buyTotal,todayCell,yesterdayCell);
            metricRow(info,new Cell("最高日收益",high,highColor),new Cell("最低日收益",low,lowColor),new Cell("累计收益 ›",cumulativeKnown?m.signed(cumulative):"待数据",color(cumulative),"累计收益明细",v->m.go("fund_profit")));
        }else{
            long own=FundEngine.value(focus,today);
            metricRow(info,buyTotal,new Cell("持有份额",FundEngine.units(focus,today).toPlainString(),MainActivity.INK),new Cell("持仓占比",!focus.hidden&&FundEngine.held(focus,today)&&heldAll>0?String.format(Locale.CHINA,"%.1f%%",own*100.0/heldAll):"—",Ui.ACCENT_DEEP));
            metricRow(info,todayCell,yesterdayCell,new Cell("持有天数",FundEngine.heldDays(focus,today)+"天",MainActivity.INK));
            metricRow(info,new Cell("基金代码",focus.code,MainActivity.INK),new Cell("最高日收益",high,highColor),new Cell("最低日收益",low,lowColor));
            m.gap(info,4);LinearLayout actions=m.row();m.weight(actions,m.action("追加买入",()->buy(focus,null)));
            LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,-2,1);gap.leftMargin=m.dp(6);
            TextView plan=m.action("定投",()->configurePlan(focus));plan.setContentDescription("定投配置");actions.addView(plan,gap);
            TextView trades=m.action("交易记录",()->m.go("fund_trades"));trades.setContentDescription("交易记录");actions.addView(trades,gap);info.addView(actions);
            focus.profile.inferLock(focus.name);
            if(focus.profile.lockCount>0){LocalDate last=null;for(Ledger.FundBuy b:focus.buys)if(b.saleDate==null){LocalDate unlock=focus.profile.unlock(b.confirmDate);if(unlock!=null&&(last==null||unlock.isAfter(last)))last=unlock;}m.gap(info,4);info.addView(m.text("锁定期："+focus.profile.lockCount+focus.profile.lockUnit+" · 最晚锁定截止 "+(last==null?"待确认份额":last)+"（每笔单独起算）",11,Ui.AMBER,true));}
            if(focus.plan.enabled){m.gap(info,5);info.addView(m.text("定投："+focus.plan.frequency+" 每次 ¥ "+m.money(focus.plan.amount)+" · "+focus.plan.dayOfMonth+" 日 · 关联 "+m.data.accountName(focus.plan.accountId),10.5f,Ui.ACCENT_DEEP,false));}
        }
        chartCard(m.body,ever);
    }

    // ---------------------------------------------------------------- one card, two charts
    LinearLayout chartCard(LinearLayout parent,List<Ledger.Fund> funds){
        LinearLayout c=m.card();c.setPadding(m.dp(12),m.dp(12),m.dp(12),m.dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);parent.addView(c,p);renderChart(c,funds);return c;
    }
    /** Period and mode chips: the chosen one is solid purple with white text, the others stay pale. */
    TextView chip(String label,boolean selected,Runnable click){
        TextView v=m.text(label,12,selected?Ui.ACCENT_DEEP:Ui.MUTED,selected);v.setGravity(Gravity.CENTER);v.setPadding(m.dp(5),m.dp(3),m.dp(5),m.dp(3));v.setMinHeight(m.dp(31));v.setIncludeFontPadding(false);
        if(selected){android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Ui.SURFACE,Ui.ACCENT_SOFT});background.setCornerRadius(m.dp(5));background.setStroke(m.dp(1),Ui.ACCENT);v.setBackground(background);v.setTypeface(Ui.BOLD);}
        else v.setBackground(Ui.round(Color.TRANSPARENT,m.dp(5)));
        v.setOnClickListener(x->click.run());return v;
    }
    LinearLayout.LayoutParams share(int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=m.dp(left);return p;}
    /** Rebuilds only this card, so switching tabs or months never redraws the whole page. */
    void renderChart(LinearLayout card,List<Ledger.Fund> funds){
        card.removeAllViews();
        String[] labels={"收益日历","累计盈亏"};Segmented tabs=new Segmented(m,labels);tabs.setSelected(chartTab);tabs.onSelect(()->{chartTab=tabs.selected();renderChart(card,funds);});
        card.addView(tabs,new LinearLayout.LayoutParams(-1,-2));m.gap(card,8);
        LinearLayout body=m.col();card.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        if(chartTab==0)calendarBody(body,card,funds);else curveBody(body,card,funds);
    }
    void calendarBody(LinearLayout body,LinearLayout card,List<Ledger.Fund> funds){
        LinearLayout controls=m.row();String[] modes={"日","周","月","年"};
        for(int i=0;i<modes.length;i++){final int index=i;controls.addView(chip(modes[i],mode==i,()->{mode=index;renderChart(card,funds);}),share(i==0?0:4));}
        controls.addView(chip(percent?"%":"¥",false,()->{percent=!percent;renderChart(card,funds);}),share(4));body.addView(controls);m.gap(body,5);
        LinearLayout dates=m.row();TextView prev=chip("‹",false,()->{calendarMonth=calendarMonth.minusMonths(mode>=2?12:1);renderChart(card,funds);});prev.setContentDescription("上一期");
        dates.addView(prev,new LinearLayout.LayoutParams(m.dp(44),-2));
        String title=mode==3?(calendarMonth.getYear()-4)+"–"+calendarMonth.getYear()+"年":mode==2?calendarMonth.getYear()+"年":calendarMonth.getYear()+"年 "+calendarMonth.getMonthValue()+"月";
        TextView period=chip(title,false,()->choosePeriod(card,funds));period.setContentDescription("选择期间");dates.addView(period,share(4));
        TextView next=chip("›",false,()->{YearMonth n=calendarMonth.plusMonths(mode>=2?12:1);if(n.getYear()<=FundEngine.now().toLocalDate().getYear()&&(mode>=2||!n.isAfter(YearMonth.from(FundEngine.now())))){calendarMonth=n;renderChart(card,funds);}});next.setContentDescription("下一期");
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(m.dp(44),-2);np.leftMargin=m.dp(4);dates.addView(next,np);body.addView(dates);m.gap(body,5);
        LinearLayout grid=m.col();body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        if(mode==0)days(grid,funds);else if(mode==1)weeks(grid,funds);else if(mode==2)months(grid,funds);else years(grid,funds);
    }
    void choosePeriod(LinearLayout card,List<Ledger.Fund> funds){
        DatePickerDialog picker=new DatePickerDialog(m,(v,y,mo,d)->{calendarMonth=YearMonth.of(y,mo+1);renderChart(card,funds);},calendarMonth.getYear(),calendarMonth.getMonthValue()-1,1);picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
    }
    void curveBody(LinearLayout body,LinearLayout card,List<Ledger.Fund> funds){
        LinearLayout options=m.row();String[] labels={"近一月","近一年","持有以来"};
        for(int i=0;i<labels.length;i++){final int index=i;options.addView(chip(labels[i],range==i,()->{range=index;renderChart(card,funds);}),share(i==0?0:4));}body.addView(options);m.gap(body,4);
        LocalDate today=FundEngine.now().toLocalDate(),earliest=today;boolean known=false;
        for(Ledger.Fund f:funds){LocalDate first=FundEngine.firstDate(f);if(first!=null){if(first.isBefore(earliest))earliest=first;known=true;}}
        LocalDate from=range==0?today.minusMonths(1):range==1?today.minusYears(1):earliest;if(from.isBefore(earliest))from=earliest;
        for(Ledger.Fund f:funds)if(!FundEngine.known(f,today))known=false;
        SortedMap<LocalDate,Long> values=known?FundEngine.series(funds,from,today):new TreeMap<>();
        body.addView(new ProfitChart(m,values),new LinearLayout.LayoutParams(-1,0,1));
    }

    Long daily(List<Ledger.Fund> funds,LocalDate date){
        if(date.isAfter(FundEngine.now().toLocalDate()))return null;
        long total=0;boolean missing=false;TradingCalendar calendar=calendar();
        for(Ledger.Fund f:funds){if(!FundEngine.known(f,date)){missing=true;continue;}Long value=FundEngine.daily(f,date,calendar);if(value==null){if(calendar.open(date))missing=true;}else total=Math.addExact(total,value);}
        return missing?null:total;
    }
    Long period(List<Ledger.Fund> funds,LocalDate start,LocalDate finish){
        if(start.isAfter(FundEngine.now().toLocalDate()))return null;LocalDate end=finish.isAfter(FundEngine.now().toLocalDate())?FundEngine.now().toLocalDate():finish;
        for(Ledger.Fund f:funds)if(!FundEngine.known(f,end))return null;
        long total=0;boolean known=false;for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)){Long value=daily(funds,day);if(value!=null){known=true;total=Math.addExact(total,value);}}
        return known?total:null;
    }
    long basis(List<Ledger.Fund> funds,LocalDate start,LocalDate end){
        long total=0;for(Ledger.Fund f:funds){total+=FundEngine.holdingCost(f,start.minusDays(1));for(Ledger.FundBuy b:f.buys)if(!b.at.toLocalDate().isBefore(start)&&!b.at.toLocalDate().isAfter(end))total+=b.amount;}return total;
    }

    /** A grid row of equal slots: header, placeholders and day cells all use the same weights, so columns line up. */
    LinearLayout gridRow(LinearLayout grid,int weight){LinearLayout line=new LinearLayout(m);line.setOrientation(LinearLayout.HORIZONTAL);grid.addView(line,new LinearLayout.LayoutParams(-1,weight==0?m.dp(20):0,weight));return line;}
    void slot(LinearLayout line,View view){FrameLayout slot=new FrameLayout(m);slot.setPadding(m.dp(1.5f),m.dp(1.5f),m.dp(1.5f),m.dp(1.5f));if(view!=null)slot.addView(view,new FrameLayout.LayoutParams(-1,-1));line.addView(slot,new LinearLayout.LayoutParams(0,-1,1));}
    void days(LinearLayout grid,List<Ledger.Fund> funds){
        LinearLayout week=gridRow(grid,0);for(String label:new String[]{"日","一","二","三","四","五","六"}){TextView title=m.text(label,11,MainActivity.MUTED,true);title.setGravity(Gravity.CENTER);title.setIncludeFontPadding(false);title.setPadding(0,0,0,m.dp(3));slot(week,title);}
        LocalDate first=calendarMonth.atDay(1),today=FundEngine.now().toLocalDate();int offset=first.getDayOfWeek().getValue()%7;
        int rows=(offset+calendarMonth.lengthOfMonth()+6)/7;
        for(int r=0;r<rows;r++){LinearLayout line=gridRow(grid,1);for(int col=0;col<7;col++){int number=r*7+col-offset+1;if(number<1||number>calendarMonth.lengthOfMonth()){slot(line,null);continue;}
            LocalDate day=calendarMonth.atDay(number);slot(line,cell(day.equals(today)?"今":""+number,daily(funds,day),day,day,funds,day.equals(today)));}}
    }
    LinearLayout cell(String title,Long amount,LocalDate start,LocalDate end,List<Ledger.Fund> funds,boolean highlight){
        LinearLayout cell=m.col();cell.setGravity(Gravity.CENTER);cell.setPadding(m.dp(1),m.dp(1),m.dp(1),m.dp(1));int color=amount==null?MainActivity.MUTED:color(amount);
        int background=amount==null?Ui.ACCENT_TINT:amount>0?Ui.RED_SOFT:amount<0?Ui.GREEN_SOFT:Ui.ACCENT_TINT;
        if(highlight&&amount!=null&&amount!=0){background=color;color=Color.WHITE;}cell.setBackground(m.shape(background,8));
        TextView date=m.text(title,11,color==Color.WHITE?Color.WHITE:MainActivity.INK,true);date.setGravity(Gravity.CENTER);date.setIncludeFontPadding(false);date.setSingleLine(true);cell.addView(date);
        String result=amount==null?(start.isAfter(FundEngine.now().toLocalDate())?"":"待更新"):percent?String.format(Locale.CHINA,"%+.1f%%",FundEngine.rate(amount,basis(funds,start,end))):m.signed(amount);
        TextView number=m.fit(m.text(result,10,color,true),10,6);number.setGravity(Gravity.CENTER);
        cell.addView(number,new LinearLayout.LayoutParams(-1,m.dp(17)));
        cell.setContentDescription(title+" 收益 "+result);cell.setOnClickListener(v->periodDetail(funds,start,end,amount));return cell;
    }
    void weeks(LinearLayout grid,List<Ledger.Fund> funds){
        LocalDate first=calendarMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));List<LocalDate> starts=new ArrayList<>();
        for(LocalDate d=first;!d.isAfter(calendarMonth.atEndOfMonth());d=d.plusWeeks(1))starts.add(d);
        LocalDate today=FundEngine.now().toLocalDate();
        for(int r=0;r<(starts.size()+2)/3;r++){LinearLayout line=gridRow(grid,1);for(int i=0;i<3;i++){int index=r*3+i;if(index>=starts.size()){slot(line,null);continue;}LocalDate a=starts.get(index),b=a.plusDays(6);
            String label=a.getMonthValue()+"."+a.getDayOfMonth()+"–"+b.getMonthValue()+"."+b.getDayOfMonth();slot(line,cell(label,period(funds,a,b),a,b,funds,!today.isBefore(a)&&!today.isAfter(b)));}}
    }
    void months(LinearLayout grid,List<Ledger.Fund> funds){
        for(int r=0;r<3;r++){LinearLayout line=gridRow(grid,2);for(int i=0;i<4;i++){YearMonth ym=YearMonth.of(calendarMonth.getYear(),r*4+i+1);
            slot(line,cell(ym.equals(YearMonth.now())?"本月":ym.getMonthValue()+"月",period(funds,ym.atDay(1),ym.atEndOfMonth()),ym.atDay(1),ym.atEndOfMonth(),funds,ym.equals(YearMonth.now())));}}
    }
    void years(LinearLayout grid,List<Ledger.Fund> funds){
        int end=Math.min(calendarMonth.getYear(),FundEngine.now().toLocalDate().getYear());int begin=end-4;
        for(int row=0;row<2;row++){LinearLayout line=gridRow(grid,1);
            for(int col=0;col<3;col++){int y=begin+row*3+col;if(y>end){slot(line,null);continue;}LocalDate start=LocalDate.of(y,1,1),finish=LocalDate.of(y,12,31);slot(line,cell(y+"年",period(funds,start,finish),start,finish,funds,y==FundEngine.now().toLocalDate().getYear()));}
        }
    }
    void periodDetail(List<Ledger.Fund> funds,LocalDate start,LocalDate end,Long value){
        StringBuilder details=new StringBuilder(start.toString());if(!start.equals(end))details.append(" 至 ").append(end);details.append("\n总收益 ").append(value==null?"待更新":m.signed(value)).append(" 元\n");
        for(Ledger.Fund f:funds){Long amount=start.equals(end)?FundEngine.daily(f,start):period(Collections.singletonList(f),start,end);details.append("\n").append(f.name).append("  ").append(amount==null?"待更新":m.signed(amount));}
        AppIcons.dialog(m).setTitle("收益明细").setMessage(details).setPositiveButton("关闭",null).show();
    }

    // ---------------------------------------------------------------- trade records
    static final class Trade {final LocalDateTime at;final boolean sell;final Ledger.FundBuy buy;Trade(LocalDateTime at,boolean sell,Ledger.FundBuy buy){this.at=at;this.sell=sell;this.buy=buy;}}
    /** Purchases and sales of one fund, earliest first, with the date shares were confirmed. */
    void trades(Ledger.Data data,long id){
        Ledger.Fund fund=find(id);if(fund==null){m.back();return;}
        m.top(fund.name+" · 交易记录","返回",m::back);
        if(fund.removed()){LinearLayout note=m.card(m.body);note.addView(m.text("已于 "+MainActivity.TIME.format(fund.removedAt)+" 从首页删除，收益保留至 "+fund.frozenOn+"。恢复后才能修改记录。",12,MainActivity.AMBER,false));}
        if(fund.buys.isEmpty()){
            LinearLayout legacy=m.card(m.body);legacy.addView(m.text("旧版份额持仓：未记录买入时间，无法回算买入以来的收益。先导出备份，再从首页删除，最后在累计收益中永久删除，之后可按原始买入重新添加。",12,MainActivity.AMBER,false));m.gap(legacy,8);
            if(!fund.removed())legacy.addView(m.action("从首页删除旧持仓",()->confirmRemove(fund)));
        }
        List<Trade> list=new ArrayList<>();
        for(Ledger.FundBuy b:fund.buys){list.add(new Trade(b.at,false,b));if(b.saleDate!=null)list.add(new Trade(b.saleDate.atTime(15,0),true,b));}
        list.sort(Comparator.comparing((Trade t)->t.at).thenComparing(t->t.sell));
        LocalDate today=FundEngine.now().toLocalDate();
        for(Trade t:list){
            Ledger.FundBuy b=t.buy;LinearLayout c=m.card(m.body);c.setPadding(m.dp(14),m.dp(12),m.dp(14),m.dp(10));
            TextView edit=null;
            if(!fund.removed()){
                edit=m.ui.link("编辑",()->buy(fund,b));edit.setTypeface(Ui.BOLD);edit.setPadding(m.dp(8),0,0,0);edit.setMinHeight(m.dp(36));edit.setGravity(Gravity.CENTER);
                edit.setContentDescription("编辑交易"+b.id+(t.sell?"卖出":"买入"));
            }
            LinearLayout head=m.row();TextView tag=m.text(t.sell?"卖出":"买入",12,Color.WHITE,true);tag.setPadding(m.dp(8),m.dp(2),m.dp(8),m.dp(2));tag.setBackground(m.shape(t.sell?MainActivity.EXP:MainActivity.RED,8));head.addView(tag);
            TextView amount=m.fit(m.text("¥ "+m.money(t.sell?b.saleAmount:b.amount),16,MainActivity.INK,true),16,8);amount.setTag("bounded-number");LinearLayout.LayoutParams moneyParams=new LinearLayout.LayoutParams(0,m.dp(28),1);moneyParams.leftMargin=m.dp(18);head.addView(amount,moneyParams);c.addView(head);m.gap(c,8);
            if(fund.profile.lockCount==0)fund.profile.inferLock(fund.name);
            if(fund.profile.lockCount>0&&!t.sell){LocalDate unlock=fund.profile.unlock(b.confirmDate);c.addView(m.text("本笔锁定截止："+(unlock==null?"待份额确认":unlock.toString())+"（以平台实际开放日为准）",11,Ui.AMBER,true));}
            if(t.sell){
                c.addView(m.text("到账账户："+(b.saleAccountLinked?m.data.accountName(b.saleAccountId):"不关联账户"),11,Ui.ACCENT_DEEP,false));
                pair(c,"卖出确认日",b.saleDate.toString(),"对应买入",MainActivity.TIME.format(b.at),edit);
                m.gap(c,4);c.addView(m.text("金额为本批次扣费后的到账金额",10,MainActivity.MUTED,false));
            }else{
                c.addView(m.text((b.recurringRecordId>0?"定投 · ":"")+"资金来源："+(b.buyAccountLinked?m.data.accountName(b.buyAccountId):"不关联账户"),11,Ui.ACCENT_DEEP,false));
                pair(c,"买入时间",MainActivity.TIME.format(b.at),"手续费","¥ "+m.money(b.fee));m.gap(c,6);
                pair(c,"确认份额时间",b.confirmDate==null?"待确认":b.confirmDate.toString(),"确认份额",b.units.signum()>0&&b.confirmDate!=null&&!b.confirmDate.isAfter(today)?b.units.toPlainString():"待确认",edit);
                String state=b.saleDate!=null?"已卖出":b.units.signum()==0?"等待净值公布后确认份额":b.confirmDate!=null&&b.confirmDate.isAfter(today)?"份额尚未确认":"";
                if(!state.isEmpty()){m.gap(c,2);c.addView(m.text(state,10,b.units.signum()==0?MainActivity.AMBER:MainActivity.MUTED,false));}
            }
        }
        LinearLayout source=m.card(m.body);source.addView(m.text("按已公布单位净值计算；15:00前今日收益显示0。分红默认现金分红，红利再投及特殊确认周期请按实际平台核对。",11,MainActivity.MUTED,false));
    }
    void pair(LinearLayout card,String leftLabel,String leftValue,String rightLabel,String rightValue){
        pair(card,leftLabel,leftValue,rightLabel,rightValue,null);
    }
    void pair(LinearLayout card,String leftLabel,String leftValue,String rightLabel,String rightValue,View trailing){
        LinearLayout row=m.row();LinearLayout a=m.col(),b=m.col();
        a.setPadding(0,0,m.dp(8),0);a.addView(m.text(leftLabel,10,MainActivity.MUTED,false));a.addView(m.fit(m.text(leftValue,13,MainActivity.INK,true),13,8),new LinearLayout.LayoutParams(-1,m.dp(26)));
        b.addView(m.text(rightLabel,10,MainActivity.MUTED,false));LinearLayout values=m.row();
        TextView number=m.fit(m.text(rightValue,13,MainActivity.INK,true),13,8);values.addView(number,new LinearLayout.LayoutParams(0,m.dp(26),1));
        if(trailing!=null)values.addView(trailing,new LinearLayout.LayoutParams(-2,m.dp(30)));
        b.addView(values);m.weight(row,a);m.weight(row,b);card.addView(row);
    }
    // ---------------------------------------------------------------- buying, confirming, selling
    List<Ledger.Account> transactionAccounts(long selected){List<Ledger.Account> result=new ArrayList<>();for(Ledger.Account a:m.data.accounts)if(a.deletedAt==null||a.id==selected)result.add(a);return result;}
    void buy(Ledger.Fund old,Ledger.FundBuy editing){buy(old,editing,0,0,0,false);}
    void buy(Ledger.Fund old,Ledger.FundBuy editing,long presetAmount,long presetFee,long presetAccount,boolean recurringRun){
        buyInternal(old,editing,presetAmount,presetFee,presetAccount,recurringRun,null,null);
    }
    void buyInternal(Ledger.Fund old,Ledger.FundBuy editing,long presetAmount,long presetFee,long presetAccount,boolean recurringRun,String presetName,String presetCode){
        LinearLayout form=m.col();form.setPadding(m.dp(16),0,m.dp(16),m.dp(14));
        EditText name=m.field(form,"基金名称（自动获取）",old==null?(presetName==null?"":presetName):old.name,false);name.setEnabled(false);name.setHint("保存时根据代码自动获取");
        EditText code=m.field(form,"基金代码",old==null?(presetCode==null?"":presetCode):old.code,true);code.setInputType(InputType.TYPE_CLASS_NUMBER);if(presetCode!=null)code.setEnabled(false);
        if(old!=null&&editing==null)code.setEnabled(false);
        EditText amount=m.field(form,"买入金额（含手续费）",editing==null?(presetAmount>0?Ledger.decimal(presetAmount):""):Ledger.decimal(editing.amount),true);
        EditText fee=m.field(form,"手续费（元）",editing==null?Ledger.decimal(presetFee):Ledger.decimal(editing.fee),true);
        MainActivity.DateControl date=m.new DateControl(form,"买入时间（北京时间）",editing==null?FundEngine.now():editing.at,false);
        List<Ledger.Account> accounts=transactionAccounts(editing==null?0:editing.buyAccountId);
        String[] accountNames=new String[accounts.size()+1];accountNames[0]="不关联账户";
        for(int i=0;i<accounts.size();i++)accountNames[i+1]=accounts.get(i).name+" · "+accounts.get(i).type;
        int accountSelection=accounts.isEmpty()?0:1;if(presetAccount>0)for(int i=0;i<accounts.size();i++)if(accounts.get(i).id==presetAccount)accountSelection=i+1;if(editing!=null){accountSelection=0;if(editing.buyAccountLinked)for(int i=0;i<accounts.size();i++)if(accounts.get(i).id==editing.buyAccountId)accountSelection=i+1;}
        Spinner sourceAccount=m.choices(form,"买入资金来源账户",accountNames,accountSelection);
        TextView preview=m.text("",12,MainActivity.MUTED,false);m.gap(form,9);form.addView(preview);
        TradingCalendar calendar=calendar();Runnable update=()->{LocalDate trade=calendar.tradeDate(date.at);preview.setText("预计交易日 "+trade+" · 预计确认 "+calendar.next(trade)+(calendar.covered(trade.getYear())?"":"\n该年份休市日历未覆盖，请核对确认日期（设置里可更新休市日历）"));};date.changed=update;update.run();
        CheckBox recurring=new CheckBox(m);recurring.setText("保存后配置定投（仅普通基金）");if(editing==null&&!recurringRun)form.addView(recurring);
        AlertDialog d=AppIcons.dialog(m).setTitle(editing==null?"添加基金买入":"编辑交易").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("获取净值并保存",null).create();
        if(editing!=null){
            m.gap(form,10);LinearLayout options=m.row();
            m.weight(options,m.action("校正确认日期",()->{d.dismiss();confirmation(old,editing);}));
            TextView sale=m.action(editing.saleDate==null?"记录全部卖出":"修改卖出信息",()->{d.dismiss();sell(old,editing);});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=m.dp(8);options.addView(sale,sp);form.addView(options);
            m.gap(form,14);TextView delete=m.text("删除本条交易记录",14,Ui.RED,true);delete.setTypeface(Ui.BOLD);delete.setGravity(Gravity.CENTER);delete.setPadding(0,m.dp(13),0,m.dp(13));delete.setBackground(Ui.round(Ui.RED_SOFT,m.dp(10)));delete.setContentDescription("删除交易"+editing.id);
            delete.setOnClickListener(v->AppIcons.dialog(m).setTitle("删除本条交易？").setMessage("将移除本笔买入及其关联资金划转。最后一笔买入须从首页删除基金后处理。").setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{if(mutateBuy(old,editing,true,null))d.dismiss();}).show());form.addView(delete);
        }
        d.show();
        d.getButton(-1).setOnClickListener(v->{try{
            String symbol=code.getText().toString().trim();if(!symbol.matches("[0-9]{6}"))throw new IllegalArgumentException("基金代码为6位数字");long gross=Ledger.parseMoney(amount.getText().toString(),true),charge=Ledger.parseMoney(fee.getText().toString(),false);
            Ledger.WatchFund cached=m.watchScreens.find(symbol);String knownName=presetCode!=null?presetName:cached==null?null:cached.name;
            String knownType=cached==null?"":cached.fundType;
            LocalDateTime selectedAt=date.at;
            if(charge>=gross)throw new IllegalArgumentException("手续费须小于买入金额");if(selectedAt.isAfter(FundEngine.now()))throw new IllegalArgumentException("买入时间不能在未来");
            for(Ledger.Fund f:m.store.load().funds)if(f.code.equals(symbol)&&f.removed())throw new IllegalArgumentException("该基金已从首页删除，请先在累计收益中恢复或永久删除");
            long epoch=LedgerStore.epoch();d.getButton(-1).setEnabled(false);d.getButton(-1).setText("读取历史净值…");new Thread(()->{
                FundClient.Quote quote=null;String failure="";try{quote=knownName==null?FundClient.fetchHistory(symbol):FundClient.fetchHistoryKnown(symbol,knownName,knownType);}catch(Exception e){failure=NetworkIo.message(e);}
                FundClient.Quote result=quote;String problem=failure;m.runOnUiThread(()->{if(m.isDestroyed()||m.isFinishing()||!d.isShowing())return;try{
                    if(LedgerStore.epoch()!=epoch)throw new IllegalArgumentException("账本已恢复，请关闭后重新添加买入");
                    Ledger.Data candidate=LedgerStore.decode(LedgerStore.encode(m.store.load()));
                    String fundName=result==null?knownName:result.name;
                    if(fundName==null||fundName.isEmpty())throw new IllegalArgumentException("无法联网获取基金名称，请联网后重试；尚未保存买入");
                    name.setText(fundName);
                    boolean linkBuy=sourceAccount.getSelectedItemPosition()>0;
                    long buyAccountId=linkBuy?accounts.get(sourceAccount.getSelectedItemPosition()-1).id:0;
                    Ledger.Fund target=FundTransactions.save(candidate,old==null?0:old.id,editing==null?0:editing.id,symbol,gross,charge,selectedAt,result,problem,false,linkBuy,buyAccountId,calendar);
                    if(result==null){target.name=fundName;if(cached!=null){target.nav=cached.nav;target.previous=cached.previous;target.navDate=cached.navDate;target.fundType=cached.fundType;target.prices.putAll(cached.prices);target.navReturns.putAll(cached.returns);target.profile.read(cached.profile.encode());FundEngine.resolve(target,calendar);}}
                    if(recurringRun){
                        if(!linkBuy)throw new IllegalArgumentException("每笔定投都必须关联资金账户");
                        Ledger.FundBuy saved=target.buys.isEmpty()?null:target.buys.get(target.buys.size()-1);
                        if(saved==null)throw new IllegalArgumentException("定投买入记录创建失败");
                        RecurringInvestments.record(candidate,target,saved);
                    }
                    m.commit(candidate,epoch);m.fundFocus=target.id;d.dismiss();m.render();if(recurring.isChecked()&&editing==null&&!target.fundType.contains("银行"))configurePlan(target);if(result==null)m.networkProblem(problem+"\n买入已保存，历史净值待获取。");
                }catch(Exception e){d.getButton(-1).setEnabled(true);d.getButton(-1).setText("重新获取");if(result==null&&!problem.isEmpty())m.networkProblem(problem+"\n"+e.getMessage());else m.error(e);}});
            },"fund-history").start();
        }catch(Exception e){m.error(e);}});
    }
    interface BuyChange{void apply(Ledger.FundBuy buy);}
    static LocalDate nextPlanDate(Ledger.RecurringPlan plan,LocalDate after){return RecurringInvestments.next(plan,after);}
    void configurePlan(Ledger.Fund fund){
        if(fund==null||fund.fundType.contains("银行")){m.toast("银行理财不支持定投");return;}
        LinearLayout form=m.col();form.setPadding(m.dp(16),0,m.dp(16),m.dp(14));
        EditText amount=m.field(form,"每期金额",fund.plan.enabled?Ledger.decimal(fund.plan.amount):"",true);
        EditText fee=m.field(form,"每期手续费",fund.plan.enabled?Ledger.decimal(fund.plan.fee):"0",true);
        Spinner frequency=m.choices(form,"周期",new String[]{"每月","每周"},fund.plan.frequency.equals("每周")?1:0);
        EditText day=m.field(form,"执行日（每月 1-28；每周填写 1-7）",fund.plan.enabled?""+fund.plan.dayOfMonth:"1",true);
        List<Ledger.Account> accounts=m.data.activeAccounts();String[] names=new String[accounts.size()+1];names[0]="请选择资金账户";
        for(int i=0;i<accounts.size();i++)names[i+1]=accounts.get(i).name+" · "+accounts.get(i).type;
        int selected=0;for(int i=0;i<accounts.size();i++)if(accounts.get(i).id==fund.plan.accountId)selected=i+1;
        Spinner account=m.choices(form,"定投资金账户（必选）",names,selected);
        EditText start=m.field(form,"开始日期 yyyy-MM-dd",fund.plan.startDate.isEmpty()?FundEngine.now().toLocalDate().toString():fund.plan.startDate,false);
        form.addView(m.text("此处只管理计划和实际成交记录，不会向银行或基金平台自动扣款。到期后请在平台完成买入，再点登记本期。休市日确认按基金实际规则核对。",11,Ui.MUTED,false));
        if(!fund.plan.enabled&&!fund.plan.records.isEmpty())form.addView(m.action("查看历史定投记录",()->planRecords(fund)));
        if(!fund.plan.nextDate.isEmpty())form.addView(m.text("下一计划日期："+fund.plan.nextDate+"（需在平台实际买入后登记）",12,Ui.ACCENT_DEEP,true));
        TextView record=m.text(fund.plan.records.isEmpty()?"尚无定投记录":"已记录 "+fund.plan.records.size()+" 期定投，可在此修改配置。",11,MainActivity.MUTED,false);form.addView(record);
        final AlertDialog[] planDialog={null};
        if(fund.plan.enabled){form.addView(m.action("登记本期实际买入",()->{if(planDialog[0]!=null)planDialog[0].dismiss();buy(fund,null,fund.plan.amount,fund.plan.fee,fund.plan.accountId,true);}));form.addView(m.action("查看定投记录",()->planRecords(fund)));form.addView(m.action("暂停定投",()->{Ledger.Data c=m.copy();for(Ledger.Fund f:c.funds)if(f.id==fund.id)f.plan.enabled=false;m.commit(c);if(planDialog[0]!=null)planDialog[0].dismiss();m.render();m.toast("定投已暂停，历史记录保留");}));}
        AlertDialog d=AppIcons.dialog(m).setTitle(fund.plan.enabled?"查看 / 修改定投":"开启定投").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();planDialog[0]=d;d.show();
        d.getButton(-1).setOnClickListener(v->{try{
            long value=Ledger.parseMoney(amount.getText().toString(),true),charge=Ledger.parseMoney(fee.getText().toString(),false);int interval=Integer.parseInt(day.getText().toString().trim());
            if(account.getSelectedItemPosition()==0)throw new IllegalArgumentException("请选择定投资金账户");if(interval<1||interval>(frequency.getSelectedItemPosition()==0?28:7))throw new IllegalArgumentException("执行日范围无效");
            LocalDate begin=LocalDate.parse(start.getText().toString().trim());if(begin.getYear()<2000||begin.getYear()>2100)throw new IllegalArgumentException("开始日期应在2000至2100年");
            Ledger.Data candidate=m.copy();Ledger.Fund target=null;for(Ledger.Fund f:candidate.funds)if(f.id==fund.id)target=f;if(target==null)throw new IllegalArgumentException("基金不存在");
            target.plan.enabled=true;target.plan.amount=value;target.plan.fee=charge;target.plan.dayOfMonth=interval;target.plan.frequency=frequency.getSelectedItem().toString();target.plan.accountId=accounts.get(account.getSelectedItemPosition()-1).id;target.plan.startDate=begin.toString();
            LocalDate anchor=begin;for(Ledger.RecurringRecord r:target.plan.records)if(r.status.equals("已登记")&&!r.date.isBefore(anchor))anchor=r.date.plusDays(1);target.plan.nextDate=RecurringInvestments.first(target.plan,anchor).toString();m.commit(candidate);d.dismiss();m.render();
        }catch(Exception e){m.error(e);}});
    }
    void planRecords(Ledger.Fund fund){
        LinearLayout list=m.col();for(Ledger.RecurringRecord r:fund.plan.records){list.addView(m.text(r.date+" · ¥ "+m.money(r.amount)+" · "+m.data.accountName(r.accountId)+" · "+r.status,12,Ui.INK,false));m.gap(list,10);}if(fund.plan.records.isEmpty())list.addView(m.text("尚无已登记定投",12,Ui.MUTED,false));AppIcons.dialog(m).setTitle("定投记录 · "+fund.name).setView(m.wrap(list)).setPositiveButton("关闭",null).show();
    }
    boolean mutateBuy(Ledger.Fund fund,Ledger.FundBuy buy,boolean remove,BuyChange change){
        try{Ledger.Data candidate=m.copy();Ledger.Fund target=null;for(Ledger.Fund f:candidate.funds)if(f.id==fund.id)target=f;if(target==null)throw new IllegalArgumentException("基金不存在");
            if(target.removed())throw new IllegalArgumentException("基金已从首页删除，恢复后才能修改交易");
            if(remove){FundRules.requireRemoveBuy(target,buy.id);target.buys.removeIf(x->x.id==buy.id);candidate.entries.removeIf(e->e.note.equals("自动关联买入 "+buy.id)||e.note.equals("自动关联卖出 "+buy.id));}
            else{for(Ledger.FundBuy b:target.buys)if(b.id==buy.id)change.apply(b);}
            FundEngine.resolve(target,calendar());
            m.commit(candidate);m.data=m.store.load();if(find(fund.id)==null)m.back();else m.render();return true;
        }catch(Exception e){m.error(e);return false;}
    }
    void confirmation(Ledger.Fund fund,Ledger.FundBuy buy){
        LinearLayout f=m.col();f.setPadding(m.dp(16),0,m.dp(16),m.dp(14));EditText trade=m.field(f,"实际交易日 yyyy-MM-dd",buy.tradeDate==null?"":buy.tradeDate.toString(),false),confirm=m.field(f,"实际确认日 yyyy-MM-dd",buy.confirmDate==null?"":buy.confirmDate.toString(),false);
        AlertDialog d=AppIcons.dialog(m).setTitle("校正确认时间").setView(m.wrap(f)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.show();d.getButton(-1).setOnClickListener(v->{try{LocalDate t=LocalDate.parse(trade.getText().toString()),c=LocalDate.parse(confirm.getText().toString());if(!c.isAfter(t))throw new IllegalArgumentException("确认日应晚于交易日");if(mutateBuy(fund,buy,false,b->{b.overrideDates=true;b.tradeDate=t;b.confirmDate=c;}))d.dismiss();}catch(Exception e){m.error(e);}});
    }
    void sell(Ledger.Fund fund,Ledger.FundBuy buy){
        LinearLayout f=m.col();f.setPadding(m.dp(16),0,m.dp(16),m.dp(14));EditText amount=m.field(f,"本批次卖出净到账金额（已扣费用）",buy.saleDate==null?"":Ledger.decimal(buy.saleAmount),true);
        MainActivity.DateControl date=m.new DateControl(f,"卖出确认日",buy.saleDate==null?FundEngine.now():buy.saleDate.atTime(12,0),false);
        Ledger.Entry linked=null;for(Ledger.Entry e:m.data.entries)if(e.note.equals("自动关联卖出 "+buy.id))linked=e;
        LocalDateTime oldReceipt=buy.saleAt!=null?buy.saleAt:linked==null?FundEngine.now():linked.at.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        MainActivity.DateControl received=m.new DateControl(f,"到账时间（账户余额修正）",oldReceipt,false);
        List<Ledger.Account> accounts=transactionAccounts(buy.saleAccountId);String[] names=new String[accounts.size()+1];names[0]="不关联账户";
        for(int i=0;i<accounts.size();i++)names[i+1]=accounts.get(i).name+" · "+accounts.get(i).type;
        int selected=accounts.isEmpty()?0:1;if(buy.saleDate!=null&&!buy.saleAccountLinked)selected=0;if(buy.saleAccountLinked)for(int i=0;i<accounts.size();i++)if(accounts.get(i).id==buy.saleAccountId)selected=i+1;
        Spinner destination=m.choices(f,"卖出资金到账账户",names,selected);
        f.addView(m.text(accounts.isEmpty()?"未添加资产账户时可选择“不关联账户”。":"默认关联到账账户；历史卖出可选择“不关联账户”。",10.5f,MainActivity.MUTED,false));
        AlertDialog d=AppIcons.dialog(m).setTitle("记录全部卖出").setView(m.wrap(f)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.show();d.getButton(-1).setOnClickListener(v->{try{
            long amountCents=Ledger.parseMoney(amount.getText().toString(),false);
            if(buy.confirmDate==null||date.at.toLocalDate().isBefore(buy.confirmDate)||date.at.isAfter(FundEngine.now()))throw new IllegalArgumentException("卖出日期须在确认后且不晚于现在");
            boolean linkedAccount=destination.getSelectedItemPosition()>0;
            if(linkedAccount&&(received.at.isAfter(FundEngine.now())||received.at.toLocalDate().isBefore(date.at.toLocalDate())))throw new IllegalArgumentException("到账时间应在卖出确认后且不晚于现在");
            Ledger.Data candidate=m.copy();Ledger.Fund target=null;for(Ledger.Fund candidateFund:candidate.funds)if(candidateFund.id==fund.id)target=candidateFund;if(target==null)throw new IllegalArgumentException("持仓已移除");
            for(Ledger.FundBuy batch:target.buys)if(batch.id==buy.id){batch.saleDate=date.at.toLocalDate();batch.saleAt=received.at;batch.saleAmount=amountCents;batch.saleAccountLinked=linkedAccount;batch.saleAccountId=linkedAccount?accounts.get(destination.getSelectedItemPosition()-1).id:0;batch.accountFlowManaged|=linkedAccount;}FundEngine.resolve(target,calendar());
            FundTransactions.saleTransfer(candidate,buy.id,fund.code,amountCents,received.at,false);
            m.commit(candidate);d.dismiss();m.render();
        }catch(Exception e){m.error(e);}});
    }

    // ---------------------------------------------------------------- strict xlsx import
    void importFile(byte[] content){
        final List<FundSheet.Row> rows;final FundSheet.Plan plan;
        try{rows=FundSheet.parse(content);plan=FundSheet.plan(rows,m.store.load());}catch(Exception e){m.error(e);return;}
        if(plan.add.isEmpty()){m.toast("没有新的买入记录（跳过重复 "+plan.skipped+" 笔）");return;}
        AppIcons.dialog(m).setTitle("导入基金买入？")
            .setMessage("新增买入 "+plan.add.size()+" 笔，涉及 "+plan.codes.size()+" 只基金；跳过重复 "+plan.skipped+" 笔。\n导入会联网读取历史净值来计算份额，下一步可为每笔选择资金账户和定投；选择不关联账户时不改余额。")
            .setNegativeButton("取消",null).setPositiveButton("下一步",(d,w)->importOptions(rows,plan.codes)).show();
    }
    final Set<String> importPlanCodes=new LinkedHashSet<>();
    void importOptions(List<FundSheet.Row> rows,Set<String> codes){
        LinearLayout form=m.col();form.setPadding(m.dp(15),0,m.dp(15),m.dp(12));List<Ledger.Account> accounts=transactionAccounts(0);String[] names=new String[accounts.size()+1];names[0]="不关联账户";for(int i=0;i<accounts.size();i++)names[i+1]=accounts.get(i).name;
        Map<FundSheet.Row,Spinner> selected=new LinkedHashMap<>();Map<String,CheckBox> plans=new LinkedHashMap<>();
        for(FundSheet.Row row:rows){Spinner spinner=m.choices(form,row.name+" · "+Ledger.csvMinute(row.at)+" · ¥ "+m.money(row.amount),names,accounts.isEmpty()?0:1);selected.put(row,spinner);if(!plans.containsKey(row.code)){CheckBox plan=new CheckBox(m);plan.setText("导入后为 "+row.code+" 配置定投");form.addView(plan);plans.put(row.code,plan);}}
        AlertDialog dialog=AppIcons.dialog(m).setTitle("资金来源与定投选项").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create();dialog.show();dialog.getButton(-1).setOnClickListener(v->{try{importPlanCodes.clear();for(Map.Entry<FundSheet.Row,Spinner> e:selected.entrySet()){int position=e.getValue().getSelectedItemPosition();e.getKey().sourceAccountLinked=position>0;e.getKey().sourceAccountId=position==0?0:accounts.get(position-1).id;if(position>0)AccountCash.requireAccount(m.data,e.getKey().sourceAccountId,FundEngine.ledgerTime(e.getKey().at));}for(Map.Entry<String,CheckBox> e:plans.entrySet())if(e.getValue().isChecked())importPlanCodes.add(e.getKey());dialog.dismiss();runImport(rows,codes);}catch(Exception e){m.error(e);}});
    }
    private void runImport(List<FundSheet.Row> rows,Set<String> codes){
        long epoch=LedgerStore.epoch();
        AlertDialog progress=AppIcons.dialog(m).setTitle("导入基金").setMessage("正在读取净值…").setCancelable(false).create();progress.show();
        new Thread(()->{
            Map<String,FundClient.Quote> quotes=new HashMap<>();Map<String,String> failures=new HashMap<>();int index=0;String connectionFailure=null;
            for(String code:codes){
                index++;String text="正在读取净值 "+index+"/"+codes.size()+"："+code;m.runOnUiThread(()->progress.setMessage(text));
                if(connectionFailure!=null){failures.put(code,connectionFailure);continue;}
                try{quotes.put(code,FundClient.fetchHistory(code));}catch(Exception e){String problem=NetworkIo.message(e);failures.put(code,problem);if(NetworkIo.connectivity(e))connectionFailure=problem;}
            }
            m.runOnUiThread(()->{
                progress.dismiss();if(m.isDestroyed()||m.isFinishing())return;
                try{
                    if(LedgerStore.epoch()!=epoch)throw new IllegalArgumentException("账本已恢复，请重新导入表格");
                    Ledger.Data before=m.store.load(),candidate=LedgerStore.decode(LedgerStore.encode(before));
                    int added=FundSheet.apply(candidate,FundSheet.plan(rows,candidate),quotes,failures,calendar());
                    ImportJournal.record(before,candidate,ImportJournal.FUNDS,"表格导入");
                    m.commit(candidate,epoch);m.render();if(!importPlanCodes.isEmpty()){List<Ledger.Fund> funds=new ArrayList<>();for(Ledger.Fund fund:candidate.funds)if(importPlanCodes.contains(fund.code))funds.add(fund);String[] titles=new String[funds.size()];for(int i=0;i<titles.length;i++)titles[i]=funds.get(i).name;AppIcons.dialog(m).setTitle("手动配置导入基金定投").setItems(titles,(d,w)->configurePlan(funds.get(w))).setNegativeButton("稍后在详情中配置",null).show();}m.toast("已导入 "+added+" 笔买入");if(!failures.isEmpty())m.networkProblem(failures.values().iterator().next()+"\n已导入 "+added+" 笔买入，"+failures.size()+" 只基金净值待获取。");
                }catch(Exception e){m.error(e);}
            });
        },"fund-import").start();
    }
}
