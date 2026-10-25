package cn.yueji.balance;

import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** One inline asset panel, with the account actions kept outside its replaceable content. */
final class AssetPanels {
    static final String[] TITLES={"资产构成","资产趋势","账户列表"};
    final MainActivity m;FrameLayout host;TextView title;
    AssetPanels(MainActivity m){this.m=m;}
    View selector(){
        LinearLayout group=m.row();group.setGravity(Gravity.CENTER_VERTICAL);group.setBaselineAligned(false);group.setBackground(m.ripple(Ui.ACCENT_TINT,12));
        TextView left=arrow("◀","上一个资产视图",-1),right=arrow("▶","下一个资产视图",1);
        title=m.fit(m.text(TITLES[m.assetTab],13,Ui.INK,true),13,9);title.setGravity(Gravity.CENTER);title.setIncludeFontPadding(false);title.setContentDescription("切换资产视图");
        title.setOnClickListener(v->switchTo((m.assetTab+1)%3));group.addView(left,new LinearLayout.LayoutParams(m.dp(24),m.dp(40)));
        group.addView(title,new LinearLayout.LayoutParams(m.dp(62),m.dp(40)));group.addView(right,new LinearLayout.LayoutParams(m.dp(24),m.dp(40)));
        return group;
    }
    TextView arrow(String label,String description,int direction){
        TextView view=m.text(label,11,Ui.ACCENT,true);view.setGravity(Gravity.CENTER);view.setIncludeFontPadding(false);view.setContentDescription(description);
        view.setOnClickListener(v->switchTo((m.assetTab+direction+3)%3));return view;
    }
    void mount(LinearLayout parent){
        host=new FrameLayout(m);host.setContentDescription("资产视图内容");parent.addView(host,new LinearLayout.LayoutParams(-1,0,1));show();
    }
    void switchTo(int index){m.assetTab=index;title.setText(TITLES[index]);show();}
    void show(){
        if(host==null)return;host.removeAllViews();View content=m.assetTab==2?m.accountList():m.assetTab==0?composition():trend();
        host.addView(content,new FrameLayout.LayoutParams(-1,-1));
    }
    LinearLayout panel(){
        LinearLayout card=m.card();card.setPadding(m.dp(12),m.dp(13),m.dp(12),m.dp(14));return card;
    }
    LinearLayout composition(){
        LinearLayout card=panel();AssetSlices slices=new AssetSlices(AssetHistory.composition(m.data,FundEngine.now()));
        AssetDonut donut=new AssetDonut(m,slices);card.addView(donut,new LinearLayout.LayoutParams(-1,0,1));
        summary(card,"占比最大",slices,slices.extreme(true),Ui.ACCENT);
        m.gap(card,8);summary(card,"占比最小",slices,slices.extreme(false),Ui.INK_SOFT);
        return card;
    }
    void summary(LinearLayout parent,String label,AssetSlices slices,int index,int color){
        LinearLayout row=m.row();TextView caption=m.text(label,11,Ui.MUTED,false);caption.setPadding(m.dp(4),0,m.dp(12),0);row.addView(caption);
        String value=index<0?"暂无资产":slices.names[index]+"  "+String.format(Locale.CHINA,"%.2f%%",slices.percent(index));
        TextView text=m.fit(m.text(value,13,color,true),13,9);text.setGravity(Gravity.END);m.weight(row,text);parent.addView(row,new LinearLayout.LayoutParams(-1,m.dp(27)));
    }
    LinearLayout trend(){
        LinearLayout card=panel(),head=m.row();TextView caption=m.text("资产变动",14,Ui.INK,true);m.weight(head,caption);
        String[] modes={"更新时间","按月","按年"};TextView period=m.action(modes[m.assetPeriod]+" ▾",()->{});period.setContentDescription("资产趋势时间粒度");
        period.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(m,period);for(int i=0;i<modes.length;i++)menu.getMenu().add(0,i,i,modes[i]);
            menu.setOnMenuItemClickListener(item->{m.assetPeriod=item.getItemId();show();return true;});menu.show();
        });head.addView(period);card.addView(head);m.gap(card,3);
        List<AssetHistory.Point> raw=AssetHistory.observations(m.data,LocalDateTime.now()),points=AssetHistory.series(raw,m.assetPeriod);
        boolean unknown=points.stream().anyMatch(p->p.amount==null);
        TextView hint=m.text(unknown?"图内拖动查看 · 缺少净值或过渡金额处断线":"图内拖动查看金额 · 下方时间轴左右滑动",10,Ui.MUTED,false);card.addView(hint);
        AssetTrendChart chart=new AssetTrendChart(m,points,m.assetPeriod);card.addView(chart,new LinearLayout.LayoutParams(-1,0,1));chart.axis(card);
        m.gap(card,5);change(card,AssetHistory.extreme(raw,true),true);m.gap(card,4);change(card,AssetHistory.extreme(raw,false),false);
        return card;
    }
    void change(LinearLayout parent,AssetHistory.Change change,boolean positive){
        LinearLayout row=m.row();TextView arrow=m.text(positive?"↗":"↘",22,positive?Ui.RED:Ui.GREEN,true);arrow.setGravity(Gravity.CENTER);
        row.addView(arrow,new LinearLayout.LayoutParams(m.dp(28),m.dp(32)));
        String text=change==null?(positive?"暂无正变动记录":"暂无负变动记录"):
            change.from.at.format(AssetTrendChart.DATE)+" → "+change.to.at.format(AssetTrendChart.DATE)+"  "+(positive?"+":"−")+"¥ "+m.money(Math.abs(change.amount));
        TextView value=m.fit(m.text(text,11.5f,change==null?Ui.MUTED:positive?Ui.RED:Ui.GREEN,false),11.5f,8);
        value.setContentDescription((positive?"资产最大正变动":"资产最大负变动")+"："+text);m.weight(row,value);parent.addView(row,new LinearLayout.LayoutParams(-1,m.dp(32)));
    }
}
