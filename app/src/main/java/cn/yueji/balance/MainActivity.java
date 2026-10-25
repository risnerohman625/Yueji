package cn.yueji.balance;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    static final int TAB_ACCOUNTS=0,TAB_FLOW=1,TAB_FUNDS=2,TAB_SETTINGS=3;
    static int BG=Ui.BG,INK=Ui.INK,MUTED=Ui.MUTED,PURPLE=Ui.PURPLE,PALE=Ui.PURPLE_SOFT,LILAC=0xFFE3D8F5,WHITE=Ui.SURFACE,RED=Ui.RED,EXP=Ui.GREEN,AMBER=Ui.AMBER;
    static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String[] TYPES={"工商银行","农业银行","中国银行","建设银行","邮储银行","交通银行","招商银行","陕西农信","支付宝","微信","现金","其他"};
    LedgerStore store; Ledger.Data data; LinearLayout root,body; Ui ui;
    int tab=0,year=LocalDate.now().getYear(),flowTab=0,detailYear=LocalDate.now().getYear(),yearKind=0; String page="",categoryFocus=""; long accountId;
    boolean sortAmount;int reimburseFilter;
    final Set<String> openSections=new HashSet<>();
    Ledger.Entry entryEditing; Ledger.Snapshot snapshotEditing; HistoryImport.Result pendingImport;
    LocalDate pendingFrom,pendingTo;boolean pendingExpense,pendingIncome,pendingReimburse;
    boolean refreshing=false;long fundFocus;String watchFocus=""; final ArrayDeque<String> backStack=new ArrayDeque<>();
    FundScreens fundScreens;WatchlistScreens watchScreens;
    LabelScreens labelScreens;
    final Map<String,String> searches=new HashMap<>(),monthFilters=new HashMap<>();
    final Map<String,LinearLayout> sectionContents=new HashMap<>();
    final Map<String,Chevron> sectionArrows=new HashMap<>();
    FormDraft formDraft;
    LockOverlay activeLock;
    AlertDialog networkDialog;
    AlertDialog iconChooser;
    YearMonth monthFocus=YearMonth.now();
    FundScreenshot fundScreenshot; ScreenshotCleanup screenshotCleanup;
    BankScreens bankScreens;
    long openImportBatch=-1;
    DebtScreens debtScreens;
    boolean fundPageVisited;
    int assetTab=2,assetPeriod;
    TextView refreshPermissionText;

    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){return Ui.round(color,dp(radius));}
    TextView text(String s,float size,int color,boolean bold){return ui.text(s,size,color,bold);}
    LinearLayout col(){return ui.col();}
    LinearLayout row(){return ui.row();}
    void gap(LinearLayout l,int h){ui.gap(l,h);}
    void weight(LinearLayout r,View v){ui.weight(r,v);}
    LinearLayout card(LinearLayout parent){return ui.card(parent);}
    LinearLayout card(){return ui.card(null);}
    RippleDrawable ripple(int color,int radius){GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(radius));return new RippleDrawable(ColorStateList.valueOf(0x22805FC7),shape(color,radius),mask);}
    /** Pale secondary button; the page's main action uses button(). */
    TextView action(String label,Runnable click){TextView v=ui.secondary(label,click);v.setTextSize(13);return v;}
    TextView button(String label,Runnable click){return ui.primary(label,click);}
    String money(long v){return String.format(Locale.CHINA,"%,.2f",v/100.0);}
    String signed(long v){return (v>=0?"+":"−")+money(Math.abs(v));}
    void toast(String s){AppIcons.toast(this,s);}
    void error(Exception e){AppIcons.dialog(this).setTitle("请检查内容").setMessage(e.getMessage()==null?"保存失败":e.getMessage()).setPositiveButton("知道了",null).show();}
    void networkProblem(String message){
        if(isFinishing()||isDestroyed())return;
        if(networkDialog!=null&&networkDialog.isShowing())return;
        networkDialog=AppIcons.dialog(this).setTitle("网络或数据服务异常").setMessage(message+"\n已保留本地记录和上次成功更新的数据。").setPositiveButton("知道了",null).create();
        networkDialog.show();
    }
    /** Header of a sub page. Pages are left only with the system back gesture, so there is no back button. */
    void top(String title,String utility,Runnable click){
        LinearLayout r=row();r.setPadding(dp(4),dp(2),dp(2),dp(8));TextView t=text(title,21,INK,true);t.setMaxLines(2);t.setEllipsize(TextUtils.TruncateAt.END);weight(r,t);
        if(utility!=null&&!utility.equals("返回"))r.addView(action(utility,click));
        body.addView(r);
    }
    void metric(LinearLayout c,String name,String value,int color){
        LinearLayout r=row();TextView label=text(name,13,MUTED,false);label.setMaxLines(2);r.addView(label,new LinearLayout.LayoutParams(0,-2,.42f));
        TextView amount=fit(text(value,18,color,true),18,8);amount.setTag("bounded-number");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(30),.58f);p.leftMargin=dp(6);r.addView(amount,p);c.addView(r);gap(c,8);
    }
    Ledger.Data copy(){return LedgerStore.decode(LedgerStore.encode(data));}
    void commit(Ledger.Data candidate){
        synchronized(LedgerStore.class){
            Ledger.Data newest=store.load();
            for(Ledger.Fund f:candidate.funds)for(Ledger.Fund q:newest.funds)
                if(f.id==q.id&&f.code.equals(q.code)&&q.updatedAt.compareTo(f.updatedAt)>0){f.nav=q.nav;f.previous=q.previous;f.navDate=q.navDate;f.error=q.error;f.prices.putAll(q.prices);f.navReturns.putAll(q.navReturns);f.dividends.putAll(q.dividends);f.splits.putAll(q.splits);f.updatedAt=q.updatedAt;FundEngine.resolve(f,fundScreens.calendar());}
            store.save(candidate);
        }
        data=store.load();
    }
    void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    boolean recordForm(){return page.equals("income_form")||page.equals("large_form")||page.equals("reimburse_form");}
    void captureDraft(){if(recordForm()&&body!=null){formDraft=new FormDraft();formDraft.capture(body);}}
    void go(String next){captureDraft();hideKeyboard();if(!next.equals(page)){if(next.isEmpty())backStack.clear();else backStack.push(page);}page=next;render();}
    void back(){
        if(activeLock!=null){if(!activeLock.onBack())finish();return;}
        if(recordForm())formDraft=null;
        if(!page.isEmpty()){page=backStack.isEmpty()?"":backStack.pop();pendingImport=null;render();}else if(tab!=tabOrder()[0]){tab=tabOrder()[0];render();}else finish();
    }
    void openTab(int index){hideKeyboard();tab=index;page="";backStack.clear();render();if(index==TAB_FUNDS&&data.settings.autoRefresh)autoRefreshFunds();}
    @SuppressLint("GestureBackNavigation") @Override public void onBackPressed(){back();}
    /** Bottom-bar order: the first three tabs can be rearranged by long-pressing; settings stays last. */
    int[] tabOrder(){
        int[] order={0,1,2,TAB_SETTINGS};boolean[] used=new boolean[3];int n=0;
        try{for(String part:getPreferences(0).getString("tabOrder","0,1,2").split(",")){int v=Integer.parseInt(part.trim());if(v>=0&&v<3&&!used[v]&&n<3){used[v]=true;order[n++]=v;}}}catch(NumberFormatException ignored){}
        for(int v=0;v<3&&n<3;v++)if(!used[v])order[n++]=v;
        return order;
    }
    void saveTabOrder(int[] order){getPreferences(0).edit().putString("tabOrder",order[0]+","+order[1]+","+order[2]).apply();}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);if(b==null)AppIcons.beginProcess(this);AppIcons.sync(this);AppIcons.apply(this);ui=new Ui(this);store=new LedgerStore(this);fundScreens=new FundScreens(this);watchScreens=new WatchlistScreens(this);labelScreens=new LabelScreens(this);fundScreenshot=new FundScreenshot(this);bankScreens=new BankScreens(this);debtScreens=new DebtScreens(this);screenshotCleanup=new ScreenshotCleanup(this);getSystemService(NotificationManager.class).cancel(4260);getSharedPreferences("bank-capture",0).edit().clear().apply();tab=tabOrder()[0];
        if(b!=null){assetTab=b.getInt("assetTab",2);assetPeriod=b.getInt("assetPeriod",0);bankScreens.restore(b.getBundle("bankScreens"));screenshotCleanup.restore(b.getBundle("screenshotCleanup"));}
        if(b!=null){fundScreenshot.restore(b.getBundle("fundScreenshot"));String savedMonth=b.getString("monthFocus");if(savedMonth!=null)monthFocus=YearMonth.parse(savedMonth);}
        if(b!=null){tab=b.getInt("tab");year=b.getInt("year",year);flowTab=b.getInt("flowTab");sortAmount=b.getBoolean("sortAmount");page=b.getString("page","");accountId=b.getLong("account");fundFocus=b.getLong("fund");categoryFocus=b.getString("category","");String[] stack=b.getStringArray("backStack");if(stack!=null)backStack.addAll(Arrays.asList(stack));String[] sections=b.getStringArray("sections");if(sections!=null)openSections.addAll(Arrays.asList(sections));Ledger.Data saved=store.load();for(Ledger.Entry e:saved.entries)if(e.id==b.getLong("editingEntry"))entryEditing=e;for(Ledger.Snapshot s:saved.snapshots)if(s.id==b.getLong("editingSnapshot"))snapshotEditing=s;}
        if(b!=null){detailYear=b.getInt("detailYear",detailYear);yearKind=b.getInt("yearKind",0);Bundle draft=b.getBundle("formDraft");if(draft!=null)formDraft=new FormDraft(draft);Bundle searchesSaved=b.getBundle("searches"),monthsSaved=b.getBundle("monthFilters");if(searchesSaved!=null)for(String k:searchesSaved.keySet())searches.put(k,searchesSaved.getString(k,""));if(monthsSaved!=null)for(String k:monthsSaved.keySet())monthFilters.put(k,monthsSaved.getString(k,""));}
        if(b!=null){reimburseFilter=b.getInt("reimburseFilter");labelScreens.restoreState(b.getBundle("labelEditor"));openImportBatch=b.getLong("openImportBatch",-1);fundPageVisited=b.getBoolean("fundPageVisited");}
        if(b!=null){watchFocus=b.getString("watchFocus","");watchScreens.activeGroup=b.getString("watchGroup","全部");watchScreens.rulesExpanded=b.getBoolean("watchRules");}
        if(page.equals("settings")){page="";tab=TAB_SETTINGS;}
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        handleDestination(getIntent());ReminderReceiver.schedule(this);FundRefreshJob.schedule(this);render();if(tab==TAB_FUNDS&&page.isEmpty()&&data.settings.autoRefresh)autoRefreshFunds();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(0).getBoolean("asked",false)){
            getPreferences(0).edit().putBoolean("asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
        }
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("watchFocus",watchFocus);b.putString("watchGroup",watchScreens.activeGroup);b.putBoolean("watchRules",watchScreens.rulesExpanded);b.putInt("assetTab",assetTab);b.putInt("assetPeriod",assetPeriod);b.putBoolean("fundPageVisited",fundPageVisited);b.putLong("openImportBatch",openImportBatch);b.putBundle("bankScreens",bankScreens.save());b.putBundle("screenshotCleanup",screenshotCleanup.save());b.putString("monthFocus",monthFocus.toString());b.putBundle("fundScreenshot",fundScreenshot.save());b.putInt("tab",tab);b.putInt("year",year);b.putInt("flowTab",flowTab);b.putBoolean("sortAmount",sortAmount);b.putString("page",page);b.putLong("account",accountId);b.putLong("fund",fundFocus);b.putLong("editingEntry",entryEditing==null?0:entryEditing.id);b.putLong("editingSnapshot",snapshotEditing==null?0:snapshotEditing.id);b.putStringArray("backStack",backStack.toArray(new String[0]));b.putString("category",categoryFocus);b.putStringArray("sections",openSections.toArray(new String[0]));b.putInt("detailYear",detailYear);b.putInt("yearKind",yearKind);captureDraft();if(formDraft!=null)b.putBundle("formDraft",formDraft.values);Bundle sq=new Bundle(),mf=new Bundle();for(String k:searches.keySet())sq.putString(k,searches.get(k));for(String k:monthFilters.keySet())mf.putString(k,monthFilters.get(k));b.putBundle("searches",sq);b.putBundle("monthFilters",mf);b.putInt("reimburseFilter",reimburseFilter);b.putBundle("labelEditor",labelScreens.saveState());}
    void handleDestination(Intent intent){
        if(intent.hasExtra("openTab")){tab=intent.getIntExtra("openTab",TAB_FUNDS);page="";backStack.clear();intent.removeExtra("openTab");}
        if("check".equals(intent.getStringExtra("openPage"))){tab=TAB_ACCOUNTS;page="check";snapshotEditing=null;backStack.clear();backStack.push("");intent.removeExtra("openPage");}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleDestination(intent);render();if(tab==TAB_FUNDS&&page.isEmpty()&&data.settings.autoRefresh)autoRefreshFunds();}
    @Override protected void onPause(){super.onPause();AppLock.markBackground();}
    @Override protected void onResume(){super.onResume();if(data!=null&&data.settings.appLockEnabled&&AppLock.shouldLockNow(this))render();}
    @Override protected void onDestroy(){if(activeLock!=null)activeLock.dismiss();if(networkDialog!=null)networkDialog.dismiss();if(iconChooser!=null)iconChooser.dismiss();if(fundScreenshot!=null)fundScreenshot.dispose();if(watchScreens!=null)watchScreens.dispose();store.close();super.onDestroy();}
    void showLock(LockOverlay.Mode mode,Runnable done,Runnable cancel){
        if(activeLock!=null)return;
        activeLock=LockOverlay.show(this,mode,()->{activeLock=null;done.run();},()->{activeLock=null;cancel.run();});
    }

    void render(){
        data=store.load();
        data.settings.appLockEnabled=AppLock.enabled(this);
        applyAppearance();
        if(data.settings.appLockEnabled&&AppLock.shouldLockNow(this)){
            showLock(LockOverlay.Mode.UNLOCK,()->{AppLock.markUnlocked();render();},()->finish());
            return;
        }
        sectionContents.clear();sectionArrows.clear();
        fundScreens.pull=null;root=col();root.setFocusableInTouchMode(true);root.setBackgroundColor(BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{
            if(Build.VERSION.SDK_INT>=30){Insets x=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(x.left,x.top,x.right,x.bottom);return WindowInsets.CONSUMED;}
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();
        });root.requestApplyInsets();
        boolean home=page.isEmpty();
        // Assets, income & expense and the fund detail pages fit one screen: their cards share the height instead of scrolling.
        boolean fixed=home&&(tab==TAB_ACCOUNTS||tab==TAB_FLOW)||page.equals("fund_detail")||page.equals("fund_total")||page.equals("bank_detail");
        if(fixed){
            body=col();body.setPadding(dp(14),dp(10),dp(14),dp(8));root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);body=col();body.setPadding(dp(14),dp(12),dp(14),dp(14));scroll.addView(body);
            if(home&&tab==TAB_FUNDS){fundScreens.pull=new PullRefreshLayout(this,scroll,this::refreshFunds);root.addView(fundScreens.pull,new LinearLayout.LayoutParams(-1,0,1));}
            else if(page.equals("watch_detail")){watchScreens.pull=new PullRefreshLayout(this,scroll,watchScreens::refreshDetail);root.addView(watchScreens.pull,new LinearLayout.LayoutParams(-1,0,1));}
            else root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        }
        if(!home)subpage();else homePage();
        if(home)navigation();root.requestFocus();
    }
    boolean compactScreen(){return getResources().getConfiguration().screenHeightDp<760;}
    void applyAppearance(){
        Ui.configure(this);BG=Ui.BG;INK=Ui.INK;MUTED=Ui.MUTED;PURPLE=Ui.PURPLE;PALE=Ui.PURPLE_SOFT;LILAC=Ui.PURPLE_TINT;WHITE=Ui.SURFACE;RED=Ui.RED;EXP=Ui.GREEN;AMBER=Ui.AMBER;
        setTheme(Ui.dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(Ui.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    void detailMetric(LinearLayout parent,String title,String value,int color){
        LinearLayout r=row();r.setGravity(Gravity.TOP);r.setPadding(0,dp(8),0,dp(8));
        r.addView(text(title,13,MUTED,false),new LinearLayout.LayoutParams(dp(82),-2));
        TextView amount=text(value,16,color,true);amount.setGravity(Gravity.START);weight(r,amount);parent.addView(r);
    }
    void yearDetail(){new SummaryScreens(this).year();}
    void assetMix(){new SummaryScreens(this).assets();}
    void rerenderKeepingScroll(){
        int y=body.getParent() instanceof ScrollView?((ScrollView)body.getParent()).getScrollY():0;render();
        if(body.getParent() instanceof ScrollView){ScrollView s=(ScrollView)body.getParent();s.post(()->s.scrollTo(0,y));}
    }
    void homePage(){if(tab==TAB_ACCOUNTS)accounts();else if(tab==TAB_FLOW)flow();else if(tab==TAB_FUNDS)funds();else settingsPage();}
    void navigation(){
        NavBar nav=new NavBar(this,tabOrder(),tab);nav.onSelected(this::openTab);nav.onMoved(this::saveTabOrder);
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    }

    // ---------------------------------------------------------------- assets
    long heldFundValue(){
        LocalDate today=FundEngine.now().toLocalDate();long value=0;
        for(Ledger.Fund f:data.funds)if(!f.hidden&&!f.removed()&&FundEngine.held(f,today))value+=FundEngine.value(f,today);
        for(BankLedger.Product p:data.banks)if(!p.hidden&&!p.removed())value=Math.addExact(value,BankLedger.balance(p));
        return value;
    }
    TextView fit(TextView v,float max,float min){return ui.fit(v,max,min);}
    void accounts(){
        long accountTotal=data.total(),fundTotal=heldFundValue();final int soft=0xCCFFFFFF;
        LinearLayout hero=col();hero.setBackground(Ui.hero(dp(24)));hero.setPadding(dp(15),dp(17),dp(15),dp(13));hero.setElevation(dp(3));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(16);body.addView(hero,hp);
        LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left=col();left.setGravity(Gravity.CENTER_VERTICAL);left.setContentDescription("资产构成");left.setOnClickListener(v->go("asset_mix"));
        TextView totalLabel=text("总资产",17,soft,true);totalLabel.setTypeface(Ui.BOLD);left.addView(totalLabel);gap(left,9);left.addView(fit(ui.number("¥ "+money(accountTotal+fundTotal),29,Color.WHITE),29,10),new LinearLayout.LayoutParams(-1,dp(39)));
        ui.weight(head,left,1.18f);
        LinearLayout right=col();right.setGravity(Gravity.START);right.setPadding(dp(9),0,0,0);
        LinearLayout account=col();TextView accountLabel=text("账户余额",14.5f,soft,true);accountLabel.setTypeface(Ui.BOLD);account.addView(accountLabel);
        gap(account,3);account.addView(fit(ui.number("¥ "+money(accountTotal),20,Color.WHITE),20,9),new LinearLayout.LayoutParams(-1,dp(27)));
        right.addView(account,new LinearLayout.LayoutParams(-1,-2));gap(right,8);
        LinearLayout fund=col();fund.setGravity(Gravity.START);fund.setContentDescription("基金金额");fund.setPadding(0,0,0,dp(2));
        TextView fundLabel=text("基金金额 >",14.5f,soft,true);fundLabel.setTypeface(Ui.BOLD);fund.addView(fundLabel,new LinearLayout.LayoutParams(-1,-2));gap(fund,3);TextView fundValue=fit(ui.number("¥ "+money(fundTotal),20,Color.WHITE),20,9);fund.addView(fundValue,new LinearLayout.LayoutParams(-1,dp(27)));
        fund.setOnClickListener(v->openTab(TAB_FUNDS));right.addView(fund,new LinearLayout.LayoutParams(-1,-2));
        ui.weight(head,right,1f);hero.addView(head);
        View line=new View(this);line.setBackgroundColor(0x33FFFFFF);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(0.7f)));lp.topMargin=dp(14);lp.bottomMargin=dp(10);hero.addView(line,lp);
        LinearLayout foot=row();List<LocalDate> missing=Ledger.missingNodes(data,LocalDate.now());
        TextView node=text(missing.isEmpty()?"固定更新  每月 "+data.settings.checkDay1+" 日 / "+data.settings.checkDay2+" 日":"待补更新  "+missing.get(missing.size()-1),11.5f,missing.isEmpty()?soft:0xFFFFE1A6,false);node.setSingleLine(true);weight(foot,node);
        Ledger.Snapshot latest=data.latest();String stamp=latest==null?"尚未更新":"最新更新  "+(latest.at.getYear()==LocalDate.now().getYear()?latest.at.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")):TIME.format(latest.at));
        TextView last=text(stamp,11.5f,soft,false);last.setSingleLine(true);last.setGravity(Gravity.END);foot.addView(last);hero.addView(foot);
        AssetPanels panels=new AssetPanels(this);
        LinearLayout title=row();title.setPadding(0,0,0,dp(10));title.addView(panels.selector(),new LinearLayout.LayoutParams(dp(110),dp(40)));
        TextView add=fit(action("＋ 添加账户",()->go("add_account")),13,9);add.setMinHeight(dp(38));add.setPadding(dp(8),0,dp(8),0);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(40),1);ap.leftMargin=dp(8);title.addView(add,ap);
        TextView update=fit(button("更新全部账户",()->{snapshotEditing=null;go("check");}),13,9);update.setMinHeight(dp(38));update.setPadding(dp(8),0,dp(8),0);
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(0,dp(40),1.1f);up.leftMargin=dp(8);title.addView(update,up);body.addView(title);
        panels.mount(body);
    }
    ScrollView accountList(){
        ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(false);list.setClipToPadding(false);list.setPadding(0,dp(2),0,dp(6));ReorderList items=new ReorderList(this);list.addView(items,new ViewGroup.LayoutParams(-1,-2));
        List<Ledger.Account> active=data.activeAccounts();
        if(active.isEmpty()){
            LinearLayout empty=card();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(28),dp(16),dp(28));
            empty.addView(text("还没有账户",15,INK,true));gap(empty,8);
            TextView hint=text("添加银行卡、支付宝、微信或现金，\n定期更新余额即可自动推算支出",12.5f,MUTED,false);hint.setGravity(Gravity.CENTER);hint.setLineSpacing(0,1.25f);empty.addView(hint);
            empty.setOnClickListener(v->go("add_account"));items.addView(empty);
        }
        items.listener=order->reorderAccounts(order);
        for(Ledger.Account a:active){
            LinearLayout c=card(),r=row();c.setBackground(ripple(WHITE,18));c.setPadding(dp(12),dp(11),dp(12),dp(11));
            r.addView(ui.logo(a.type,40));LinearLayout info=col();info.setPadding(dp(9),0,dp(7),0);info.addView(fit(text(a.name,15,INK,true),15,10),new LinearLayout.LayoutParams(-1,dp(23)));gap(info,3);info.addView(text(a.type,11.5f,MUTED,false));weight(r,info);
            TextView balance=fit(text("¥ "+money(data.balance(a)),17,INK,true),17,9);balance.setTag("bounded-number");r.addView(balance,new LinearLayout.LayoutParams(0,dp(29),1.05f));c.addView(r);
            c.setContentDescription("账户"+a.name);c.setOnClickListener(v->{accountId=a.id;go("account_history");});
            SwipeRow swipe=new SwipeRow(this,c,new SwipeRow.Action("删除","删除账户"+a.name,RED,false,()->confirmDeleteAccount(a,this::render)),
                new SwipeRow.Action("编辑","编辑账户"+a.name,PURPLE,false,()->{accountId=a.id;go("edit_account");}));swipe.host=items;swipe.key=a.id;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(7);items.addView(swipe,p);
        }
        return list;
    }
    void reorderAccounts(List<Object> order){
        try{
            Ledger.Data candidate=copy();List<Ledger.Account> ordered=new ArrayList<>();
            for(Object id:order)for(Ledger.Account a:candidate.accounts)if(a.id==(Long)id)ordered.add(a);
            for(Ledger.Account a:candidate.accounts)if(!ordered.contains(a))ordered.add(a);
            candidate.accounts.clear();candidate.accounts.addAll(ordered);commit(candidate);render();
        }catch(Exception e){error(e);render();}
    }
    void confirmDeleteAccount(Ledger.Account existing,Runnable after){
        AppIcons.dialog(this).setTitle("删除此账户？").setMessage("从账户列表移除并停止后续更新。保留历史余额与记录，避免改变过去支出。")
            .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{Ledger.Data candidate=copy();for(Ledger.Account a:candidate.accounts)if(a.id==existing.id)a.deletedAt=LocalDateTime.now();commit(candidate);after.run();}catch(Exception e){error(e);}}).show();
    }
    ImageView accountLogo(String type){return ui.logo(type,40);}
    void subpage(){
        switch(page){
            case "add_account":accountForm(null);break;case "edit_account":accountForm(focusedAccount());break;
            case "check":checkPage();break;case "account_history":accountHistory();break;case "month_detail":MonthScreen.show(this,monthFocus);break;
            case "income_detail":incomeDetail();break;case "income_category":incomeCategory();break;case "large_detail":entryDetail(Ledger.LARGE);break;case "reimburse_detail":entryDetail(Ledger.REIMBURSE);break;
            case "income_form":entryForm(Ledger.INCOME);break;case "large_form":entryForm(Ledger.LARGE);break;case "reimburse_form":entryForm(Ledger.REIMBURSE);break;
            case "fund_detail":fundScreens.detail(data,fundFocus,false);break;case "fund_total":fundScreens.detail(data,0,true);break;case "fund_profit":fundScreens.profitList(data);break;case "fund_trades":fundScreens.trades(data,fundFocus);break;case "fund_watchlist":watchScreens.show();break;case "watch_detail":watchScreens.detail(watchFocus);break;
            case "fund_scan_preview":fundScreenshot.show();break;case "fund_screenshot_standard":fundScreenshot.standard();break;
            case "bank_review":bankScreens.review();break;case "bank_detail":bankScreens.detail();break;case "bank_records":bankScreens.records();break;case "bank_flows":bankScreens.flows();break;case "bank_income_preview":bankScreens.incomePreview();break;
            case "history":historyPage();break;case "import_preview":importPreview();break;case "investment_transfers":page="fund_watchlist";watchScreens.show();break;
            case "import_records":new ImportScreens(this,ImportJournal.RECORDS).show();break;
            case "import_funds":new ImportScreens(this,ImportJournal.FUNDS).show();break;
            case "debts":debtScreens.show();break;
            case "app_icons":AppIcons.page(this);break;
            case "storage":StorageScreen.show(this);break;
            case "labels":labelScreens.page();break;
            case "label_edit":labelScreens.editPage();break;
            case "year_detail":yearDetail();break;
            case "asset_mix":assetMix();break;
            default:page="";homePage();
        }
    }
    Ledger.Account focusedAccount(){for(Ledger.Account a:data.accounts)if(a.id==accountId)return a;return null;}
    void label(LinearLayout p,String title){gap(p,13);p.addView(text(title,12,MUTED,true));gap(p,6);}
    EditText field(LinearLayout p,String title,String value,boolean number){label(p,title);EditText e=new EditText(this);e.setContentDescription(title);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(INK);e.setPadding(dp(13),dp(10),dp(13),dp(10));e.setBackground(Ui.roundStroke(Ui.PURPLE_TINT,Ui.BORDER,dp(12),dp(1)));e.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(number?18:100)});e.setText(value);p.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));return e;}
    Spinner choices(LinearLayout p,String title,String[] options,int selected){
        label(p,title);Spinner s=new Spinner(this);s.setContentDescription(title);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));s.setSelection(Math.max(0,selected));
        s.setBackground(null);s.setPadding(dp(8),0,dp(30),0);FrameLayout box=new FrameLayout(this);box.setBackground(Ui.roundStroke(Ui.PURPLE_TINT,Ui.BORDER,dp(12),dp(1)));
        box.addView(s,new FrameLayout.LayoutParams(-1,-1));TextView arrow=text("▾",13,MUTED,false);FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.CENTER_VERTICAL);ap.rightMargin=dp(14);box.addView(arrow,ap);
        p.addView(box,new LinearLayout.LayoutParams(-1,dp(48)));return s;
    }
    final class DateControl {
        LocalDateTime at;TextView date,time;Runnable changed=()->{};boolean checkpoint;
        DateControl(LinearLayout parent,String title,LocalDateTime at,boolean checkpoint){this.at=at;this.checkpoint=checkpoint;label(parent,title);LinearLayout r=row();
            date=action("",()->{DatePickerDialog picker=new DatePickerDialog(MainActivity.this,(p,y,m,d)->{this.at=LocalDate.of(y,m+1,d).atTime(checkpoint&&d==1?LocalTime.MIDNIGHT:this.at.toLocalTime());update();changed.run();},this.at.getYear(),this.at.getMonthValue()-1,this.at.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
            time=action("",()->new TimePickerDialog(MainActivity.this,(p,h,m)->{this.at=this.at.withHour(h).withMinute(m).withSecond(0).withNano(0);update();changed.run();},this.at.getHour(),this.at.getMinute(),true).show());date.setContentDescription(title+"日期");time.setContentDescription(title+"时间");date.setTag(this);weight(r,date);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=dp(8);r.addView(time,tp);parent.addView(r);update();
        }
        void update(){date.setText(at.toLocalDate().toString());time.setText(at.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));}
    }
    void accountForm(Ledger.Account existing){
        top(existing==null?"添加账户":"编辑账户","返回",this::back);LinearLayout c=card(body);
        EditText name=field(c,"账户名称",existing==null?"":existing.name,false);int selected=existing==null?0:Arrays.asList(TYPES).indexOf(existing.type);
        Spinner type=choices(c,"银行 / 平台",TYPES,selected);EditText balance=existing==null?field(c,"初始余额", "",true):null;
        if(existing==null){gap(c,10);c.addView(text("填写首次更新时这个账户的余额。之后在更新里选择余额实际对应的日期即可，可以早于添加账户的日期。",12,MUTED,false));}
        gap(body,6);body.addView(button("保存账户",()->{try{
            Ledger.Data candidate=copy();String n=name.getText().toString().trim();if(n.isEmpty())throw new IllegalArgumentException("填写账户名称");for(Ledger.Account a:candidate.accounts)if(a.name.equals(n)&&(existing==null||a.id!=existing.id))throw new IllegalArgumentException("账户名称已存在");
            if(existing==null){Ledger.Account a=new Ledger.Account(candidate.nextId(),n,type.getSelectedItem().toString());a.created=LocalDateTime.now();a.opening=Ledger.parseMoney(balance.getText().toString(),false);candidate.accounts.add(a);}
            else for(Ledger.Account a:candidate.accounts)if(a.id==existing.id){a.name=n;a.type=type.getSelectedItem().toString();}
            commit(candidate);back();
        }catch(Exception e){error(e);}}));
        if(existing!=null){gap(body,14);body.addView(action("删除此账户",()->confirmDeleteAccount(existing,()->go(""))));}
    }
    void checkPage(){
        top(snapshotEditing==null?"更新全部账户":"修改更新","返回",this::back);
        if(data.activeAccounts().isEmpty()&&snapshotEditing==null){body.addView(action("添加账户",()->go("add_account")));return;}
        LinearLayout c=card(body);LocalDateTime initial=snapshotEditing==null?LocalDateTime.now().withSecond(0).withNano(0):snapshotEditing.at;
        if(snapshotEditing==null&&initial.getDayOfMonth()==1)initial=initial.toLocalDate().atStartOfDay();
        DateControl date=new DateControl(c,"更新截止时间",initial,true);gap(c,10);c.addView(text("1日使用月初00:00余额。更新时间可以早于账户的添加时间，按余额实际对应的时刻填写即可。",12,MUTED,false));
        Map<Long,EditText> amounts=new LinkedHashMap<>();Set<Long> optional=new HashSet<>();
        for(Ledger.Account a:data.accounts){
            Long v=snapshotEditing==null?data.balance(a):snapshotEditing.balances.get(a.id);
            if(v==null){if(a.deletedAt!=null)continue;optional.add(a.id);}else if(snapshotEditing==null&&a.deletedAt!=null)continue;
            amounts.put(a.id,field(c,v==null?a.name+"余额（本次未包含，填写即补入）":a.name+"余额",v==null?"":Ledger.decimal(v),true));
        }
        EditText note=field(c,"备注（可选）",snapshotEditing==null?"":snapshotEditing.note,false);gap(body,6);
        body.addView(button("确认保存",()->{try{
            if(date.at.isAfter(LocalDateTime.now()))throw new IllegalArgumentException("更新时间不能晚于现在");
            Ledger.Data candidate=copy();long sid=snapshotEditing==null?candidate.nextId():snapshotEditing.id;
            for(Ledger.Snapshot s:candidate.snapshots)if(s.id!=sid&&s.at.equals(date.at))throw new IllegalArgumentException("此时刻已有更新，请从账户历史中修改");
            Ledger.Snapshot replacement=new Ledger.Snapshot(sid,date.at,note.getText().toString().trim());
            replacement.partial=snapshotEditing!=null&&snapshotEditing.partial;
            for(Map.Entry<Long,EditText> e:amounts.entrySet()){String value=e.getValue().getText().toString().trim();if(value.isEmpty()&&optional.contains(e.getKey()))continue;replacement.balances.put(e.getKey(),Ledger.parseMoney(value,false));}
            candidate.snapshots.removeIf(s->s.id==sid);candidate.snapshots.add(replacement);Ledger.pullAccountsBack(candidate,replacement);
            commit(candidate);snapshotEditing=null;back();
        }catch(Exception e){error(e);}}));
    }
    void accountHistory(){Ledger.Account a=focusedAccount();if(a==null){go("");return;}top("账户余额更新记录","更新余额",()->updateAccountBalance(a,null));
        body.addView(text(a.name+" · "+a.type,13,MUTED,false));gap(body,12);
        boolean initialShown=false;for(int i=data.snapshots.size()-1;i>=0;i--){Ledger.Snapshot s=data.snapshots.get(i);Long value=s.balances.get(a.id);if(value==null)continue;if(s.at.equals(a.created))initialShown=true;
            LinearLayout c=card(body),r=row();c.setBackground(ripple(WHITE,18));
            TextView date=fit(text(TIME.format(s.at),12,INK,true),12,8);r.addView(date,new LinearLayout.LayoutParams(0,dp(29),1));
            TextView balance=fit(text("¥ "+money(value)+(s.at.equals(a.created)?" (初始余额)":""),17,PURPLE,true),17,8);balance.setTag("bounded-number");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(29),1.25f);bp.leftMargin=dp(8);r.addView(balance,bp);
            c.addView(r);if(!s.note.isEmpty()){gap(c,6);c.addView(text(s.note,12,MUTED,false));}c.setOnClickListener(v->{
                if(s.cashGenerated||s.cashAccounts.contains(a.id))cashObservationMenu(a,s);
                else if(s.partial)updateAccountBalance(a,s);else snapshotMenu(s);
            });}
        if(!initialShown){LinearLayout c=card(body);c.addView(text(TIME.format(a.created),12,INK,true));gap(c,6);c.addView(text("¥ "+money(a.opening)+" (初始余额)",17,PURPLE,true));}
    }
    void cashObservationMenu(Ledger.Account account,Ledger.Snapshot snapshot){
        List<String> options=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        for(Ledger.Entry e:AccountCash.owners(data,snapshot,account.id)){options.add("修改关联记录："+e.title);actions.add(()->openEntry(e.kind,e));}
        for(Ledger.Fund fund:data.funds)for(Ledger.FundBuy buy:fund.buys){
            boolean purchased=buy.buyAccountLinked&&buy.buyAccountId==account.id&&FundEngine.ledgerTime(buy.at).equals(snapshot.at);
            boolean sold=buy.saleAccountLinked&&buy.saleAccountId==account.id&&buy.saleDate!=null&&FundEngine.ledgerTime(buy.saleAt==null?buy.saleDate.atTime(15,0):buy.saleAt).equals(snapshot.at);
            if(purchased||sold){options.add("查看基金交易："+fund.name);actions.add(()->{fundFocus=fund.id;go("fund_trades");});}
        }
        for(BankLedger.Product product:data.banks){
            boolean linked=product.openingAccountLinked&&product.openingAccountId==account.id&&!product.observations.isEmpty()&&FundEngine.ledgerTime(product.observations.get(0).at).equals(snapshot.at);
            for(BankLedger.Event event:product.events)if(event.accountLinked&&event.accountId==account.id&&event.completedAt!=null&&FundEngine.ledgerTime(event.completedAt).equals(snapshot.at))linked=true;
            if(linked){options.add("查看银行理财交易："+product.name);actions.add(()->{bankScreens.focus=product.id;go("bank_flows");});}
        }
        options.add("核对为本账户实际余额");actions.add(()->updateAccountBalance(account,snapshot));
        AppIcons.dialog(this).setTitle("收支 / 投资账户变动").setItems(options.toArray(new String[0]),(d,w)->actions.get(w).run()).show();
    }
    void updateAccountBalance(Ledger.Account account,Ledger.Snapshot existing){
        LinearLayout form=col();form.setPadding(dp(18),0,dp(18),dp(14));
        EditText value=field(form,"本账户余额",Ledger.decimal(existing==null?data.balance(account):existing.balances.get(account.id)),true);
        DateControl date=new DateControl(form,"余额对应时间",existing==null?LocalDateTime.now().withSecond(0).withNano(0):existing.at,false);
        form.addView(text("只记录此账户本次实际余额，其他账户继续使用各自上次更新；同一时刻的记录会合并。核对自动记账余额后，以本次实测金额为准，之后修改收入或报销不会改写这条实测余额。",12,MUTED,false));
        AlertDialog dialog=AppIcons.dialog(this).setTitle("更新 "+account.name).setView(wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{
            Ledger.Data candidate=copy();
            if(existing!=null&&!existing.at.equals(date.at))for(Iterator<Ledger.Snapshot> it=candidate.snapshots.iterator();it.hasNext();){Ledger.Snapshot s=it.next();if(s.id==existing.id){s.balances.remove(account.id);if(s.balances.isEmpty())it.remove();}}
            MonthLedger.updateAccount(candidate,account.id,date.at,Ledger.parseMoney(value.getText().toString(),false));commit(candidate);dialog.dismiss();render();
        }catch(Exception e){error(e);}});
    }
    void snapshotMenu(Ledger.Snapshot s){AppIcons.dialog(this).setTitle(TIME.format(s.at)).setItems(new String[]{"修改本次全部账户更新","删除更新"},(d,w)->{
        if(w==0){snapshotEditing=s;go("check");}else AppIcons.dialog(this).setTitle("删除此次更新？").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{try{Ledger.Data candidate=copy();candidate.snapshots.removeIf(v->v.id==s.id);commit(candidate);render();}catch(Exception e){error(e);}}).show();}).show();}

    // ---------------------------------------------------------------- income & expense
    interface AxisClick{void select(int index);}
    int[] flowOrder(){return FlowOrder.read(getPreferences(0).getString("flowOrder","0,1,2"));}
    void flow(){
        String[] names={"收入","大宗支出","报销","负债"};String[] badges={"收","支","销","债"};String[] kinds={Ledger.INCOME,Ledger.LARGE,Ledger.REIMBURSE,"debt"};int[] colors={RED,EXP,PURPLE,Ui.AMBER};int[] soft={Ui.RED_SOFT,Ui.GREEN_SOFT,PALE,Ui.AMBER_SOFT};
        int[] order=flowOrder();
        FlowLayout entries=new FlowLayout(this).columns(1).gaps(0,dp(10));
        entries.draggable(4,(from,to)->{getPreferences(0).edit().putString("flowOrder",FlowOrder.encode(FlowOrder.move(order,from,to))).apply();render();});
        entries.setContentDescription("收支入口排序");body.addView(entries,new LinearLayout.LayoutParams(-1,-2));gap(body,10);
        for(int i:order){
            final String kind=kinds[i];final int index=i;
            LinearLayout strip=row();strip.setBackground(ripple(WHITE,20));strip.setElevation(dp(1.5f));strip.setPadding(dp(14),dp(8),dp(14),dp(8));strip.setContentDescription("入口"+names[i]);
            TextView badge=text(badges[i],18,colors[i],true);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(soft[i],15));strip.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout label=col();label.setPadding(dp(14),0,dp(8),0);label.addView(text(names[i],18,INK,true));gap(label,6);
            long thisMonth=0,count=0;YearMonth now=YearMonth.now();for(Ledger.Entry e:data.entries)if(e.kind.equals(kind)&&YearMonth.from(e.at).equals(now)){thisMonth=Math.addExact(thisMonth,e.amount);count++;}
            String detail=i==3?"当前待还 ¥ "+money(DebtLedger.total(data,LocalDate.now())):count==0?"本月暂无":"本月 "+count+" 笔 · ¥ "+money(thisMonth);
            label.addView(fit(text(detail,12.5f,i==3?Ui.AMBER:count==0?MUTED:colors[i],false),12,9),new LinearLayout.LayoutParams(-1,dp(22)));
            weight(strip,label);
            strip.setOnClickListener(v->{if(index==3)go("debts");else if(kind.equals(Ledger.INCOME)){categoryFocus="全部收入";go("income_detail");}else go(kind.equals(Ledger.LARGE)?"large_detail":"reimburse_detail");});
            TextView record=text("＋ 记录",14,colors[index],true);record.setGravity(Gravity.CENTER);record.setBackground(ripple(soft[index],14));record.setPadding(dp(16),0,dp(16),0);record.setMinHeight(dp(44));record.setContentDescription("记录"+names[index]);record.setOnClickListener(v->{if(index==3)debtScreens.edit(0);else openEntry(kind,null);});
            strip.addView(record,new LinearLayout.LayoutParams(-2,dp(44)));
            int extra=Math.max(0,Math.round((getResources().getConfiguration().fontScale-1)*24));
            ViewGroup.MarginLayoutParams p=new ViewGroup.MarginLayoutParams(-1,dp((compactScreen()?64:72)+extra));entries.addView(strip,p);
        }
        LinearLayout c=card();c.setPadding(dp(14),dp(12),dp(14),dp(10));LinearLayout.LayoutParams fill=new LinearLayout.LayoutParams(-1,0,1);fill.bottomMargin=dp(4);body.addView(c,fill);
        buildFlowCard(c);
    }
    /** Month trend and yearly bars share one card; switching tabs rebuilds only the card. */
    void buildFlowCard(LinearLayout card){
        card.removeAllViews();Segmented tabs=new Segmented(this,"月支出趋势","年收支趋势");tabs.setSelected(flowTab);tabs.onSelect(()->{flowTab=tabs.selected();buildFlowCard(card);});
        card.addView(tabs,new LinearLayout.LayoutParams(-1,-2));gap(card,8);LinearLayout host=col();card.addView(host,new LinearLayout.LayoutParams(-1,0,1));
        if(flowTab==0)monthChart(host);else yearChart(host);
    }
    void monthChart(LinearLayout host){
        LinearLayout head=row();weight(head,text("拖动看金额 · 点月份看详情",10.5f,MUTED,false));
        int[] years=allYears();year=Math.max(years[0],Math.min(years[years.length-1],year));
        LinearLayout plot=col();
        YearStepper pick=new YearStepper(this,year,years[0],years[years.length-1],selected->{year=selected;buildMonthTrend(plot);});
        head.addView(pick);host.addView(head);gap(host,2);host.addView(plot,new LinearLayout.LayoutParams(-1,0,1));buildMonthTrend(plot);
    }
    void buildMonthTrend(LinearLayout host){
        host.removeAllViews();
        int count=year==LocalDate.now().getYear()?LocalDate.now().getMonthValue():12;
        String[] months=new String[count];Long[] values=new Long[count];boolean[] known=new boolean[count];
        for(int m=1;m<=count;m++){months[m-1]=""+m;Ledger.Report report=Ledger.month(data,YearMonth.of(year,m));known[m-1]=report.hasData();values[m-1]=report.hasData()?report.expense:0L;}
        horizontalChart(host,months,values,null,false,false,PURPLE,index->monthPopup(index+1));gap(host,4);
        FinanceChart monthChart=findFinanceChart(host);if(monthChart!=null)monthChart.setKnown(known);
        LinearLayout totals=row();Long expense=Ledger.yearExpense(data,year);
        ui.metricCell(totals,"年支出",expense==null?"—":"¥ "+money(expense),EXP,18,28);
        ui.metricCell(totals,"年收入","¥ "+money(Ledger.income(data,year)),RED,18,28);host.addView(totals);
    }
    FinanceChart findFinanceChart(View view){if(view instanceof FinanceChart)return (FinanceChart)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){FinanceChart found=findFinanceChart(g.getChildAt(i));if(found!=null)return found;}}return null;}
    void yearChart(LinearLayout host){
        int[] all=allYears();String[] labels=new String[all.length];Long[] expenses=new Long[all.length],incomes=new Long[all.length];for(int i=0;i<all.length;i++){labels[i]=""+all[i];expenses[i]=Ledger.yearExpense(data,all[i]);incomes[i]=Ledger.income(data,all[i]);}
        LinearLayout legend=row();legend.setPadding(dp(4),dp(2),0,0);legend.addView(text("■ 支出",11.5f,EXP,true));TextView income=text("■ 收入",11.5f,RED,true);income.setPadding(dp(16),0,0,0);legend.addView(income);host.addView(legend);
        horizontalChart(host,labels,expenses,incomes,true,true,PURPLE,index->yearPopup(all[index]));
    }
    /** The chart fills the height it is given; a fixed copy of the value axis stays put while the plot scrolls sideways. */
    void horizontalChart(LinearLayout parent,String[] labels,Long[] first,Long[] second,boolean bars,boolean latest,int color,AxisClick select){
        int viewport=getResources().getDisplayMetrics().widthPixels-dp(56);
        int slot=Math.max(dp(54),(viewport+4)/5);
        int width=bars&&labels.length>5?Math.max(viewport,slot*labels.length+dp(33)):viewport;
        FrameLayout frame=new FrameLayout(this);parent.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=col();HorizontalScrollView scroll=null;
        if(width>viewport){scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setContentDescription("年度柱状图，可左右滑动");frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));scroll.addView(content,new FrameLayout.LayoutParams(width,-1));}
        else frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
        FinanceChart chart=new FinanceChart(this,labels,first,second,bars).setLineColor(color);if(!bars)chart.markExtremes().asMonths();content.addView(chart,new LinearLayout.LayoutParams(width>viewport?width:-1,0,1));
        int axisWidth=Math.round(chart.left()),axisHeight=dp(32);LinearLayout axis=row();axis.setGravity(Gravity.TOP);axis.setPadding(axisWidth,0,dp(4),0);
        for(int i=0;i<labels.length;i++){
            final int index=i;TextView b=text(labels[i],labels.length>10?10.5f:12,i%2==0?PURPLE:Ui.INK_SOFT,true);b.setTypeface(Ui.BOLD);b.setMaxLines(1);b.setGravity(Gravity.CENTER);
            b.setContentDescription("查看"+labels[i]+(bars?"年度明细":"月支出详情"));b.setOnClickListener(v->select.select(index));axis.addView(b,new LinearLayout.LayoutParams(0,axisHeight,1));
        }
        content.addView(axis,new LinearLayout.LayoutParams(width>viewport?width:-1,axisHeight));
        if(width>viewport){long maximum=1;for(Long v:first)if(v!=null)maximum=Math.max(maximum,v);if(second!=null)for(Long v:second)if(v!=null)maximum=Math.max(maximum,v);
            FinanceChart fixedAxis=new FinanceChart(this,labels,first,second,bars).setMaximum(maximum).setAxisOnly();fixedAxis.setBackgroundColor(WHITE);fixedAxis.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams fixed=new FrameLayout.LayoutParams(axisWidth,-1);fixed.bottomMargin=axisHeight;frame.addView(fixedAxis,fixed);}
        if(latest&&scroll!=null){final HorizontalScrollView scroller=scroll;scroll.post(()->scroller.fullScroll(View.FOCUS_RIGHT));}
    }
    int[] allYears(){int newest=LocalDate.now().getYear(),oldest=newest;for(Ledger.Entry e:data.entries)oldest=Math.min(oldest,e.at.getYear());for(Ledger.HistoryMonth h:data.history)oldest=Math.min(oldest,h.month.getYear());for(Ledger.Snapshot s:data.snapshots)oldest=Math.min(oldest,s.at.getYear());int[] years=new int[newest-oldest+1];for(int i=0;i<years.length;i++)years[i]=oldest+i;return years;}
    void yearPopup(int y){detailYear=y;yearKind=0;go("year_detail");}
    void monthPopup(int m){monthFocus=YearMonth.of(year,m);go("month_detail");}

    // ---------------------------------------------------------------- record lists
    void manageCategories(){
        labelScreens.reset();
        go("labels");
    }
    void refreshCategoryPicker(String selected){
        if(!page.equals("income_form")){render();return;}Spinner picker=findCategoryPicker(body);if(picker!=null){picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,data.categories));picker.setSelection(Math.max(0,data.categories.indexOf(selected)));}
    }
    Spinner findCategoryPicker(View view){if(view instanceof Spinner&&"收入类别".contentEquals(view.getContentDescription()))return (Spinner)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){Spinner found=findCategoryPicker(g.getChildAt(i));if(found!=null)return found;}}return null;}
    /** Newest first by default; the toggle orders by amount, largest first. */
    List<Ledger.Entry> sorted(List<Ledger.Entry> list){
        if(sortAmount)list.sort(Comparator.comparingLong((Ledger.Entry e)->e.amount).reversed().thenComparing((Ledger.Entry e)->e.at,Comparator.reverseOrder()));
        else list.sort(Comparator.comparing((Ledger.Entry e)->e.at).reversed().thenComparing((Ledger.Entry e)->e.id,Comparator.reverseOrder()));
        return list;
    }
    void sortToggle(){
        LinearLayout r=row();r.setPadding(dp(4),0,0,0);r.addView(text("排序",13,MUTED,true));
        Segmented seg=new Segmented(this,"时间","金额");seg.setSelected(sortAmount?1:0);seg.onSelect(()->{sortAmount=seg.selected()==1;rerenderKeepingScroll();});seg.setContentDescription("排序方式");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(150),-2);p.leftMargin=dp(12);r.addView(seg,p);
        body.addView(r);gap(body,12);
    }
    /** All income: the category chart first (tap a category to see its history), then every record. */
    void incomeDetail(){
        top("全部收入","返回",this::back);long total=0;for(Ledger.Entry e:data.entries)if(e.income)total+=e.amount;
        LinearLayout c=card(body);metric(c,"总收入","¥ "+money(total),RED);c.addView(action("＋ 记录收入",()->openEntry(Ledger.INCOME,null)));
        LinearLayout chartCard=card(body);LinearLayout h=row();weight(h,text("收入类别",15.5f,INK,true));h.addView(ui.link("管理类别",this::manageCategories));chartCard.addView(h);
        LinkedHashMap<String,Long> sums=Ledger.incomeCategories(data,true);
        if(sums.isEmpty()){gap(chartCard,10);TextView nothing=text("还没有收入记录",14,MUTED,false);nothing.setGravity(Gravity.CENTER);chartCard.addView(nothing);gap(chartCard,10);}
        else{
            String[] categories=sums.keySet().toArray(new String[0]);Long[] values=sums.values().toArray(new Long[0]);
            HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout plot=col();
            int width=Math.max(getResources().getDisplayMetrics().widthPixels-dp(60),dp(35)*categories.length+dp(33));scroll.addView(plot,new FrameLayout.LayoutParams(width,-2));chartCard.addView(scroll);
            FinanceChart chart=new FinanceChart(this,categories,values,null,false).setLineColor(RED);plot.addView(chart,new LinearLayout.LayoutParams(width,dp(155)));
            int longest=1;for(String s:categories)longest=Math.max(longest,s.codePointCount(0,s.length()));
            FlowLayout axis=new FlowLayout(this).columns(categories.length).gaps(0,0);axis.setPadding(Math.round(chart.left()),0,dp(4),0);int labelLines=Math.min(longest,5);
            for(int i=0;i<categories.length;i++){final String label=categories[i];StringBuilder letters=new StringBuilder();
                label.codePoints().limit(labelLines).forEach(cp->{if(letters.length()>0)letters.append('\n');letters.appendCodePoint(cp);});
                if(label.codePointCount(0,label.length())>labelLines)letters.append("…");
                TextView b=text(letters.toString(),10.5f,i%2==0?PURPLE:Ui.INK_SOFT,true);b.setTypeface(Ui.BOLD);b.setContentDescription("收入类别详情"+label);b.setOnClickListener(v->{categoryFocus=label;go("income_category");});
                b.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);b.setPadding(0,dp(4),0,dp(2));
                axis.addView(b,new ViewGroup.MarginLayoutParams(-1,dp(labelLines*13+8)));}
            axis.draggable(categories.length,(from,to)->{try{Ledger.Data candidate=copy();Collections.swap(candidate.categories,candidate.categories.indexOf(categories[from]),candidate.categories.indexOf(categories[to]));commit(candidate);rerenderKeepingScroll();}catch(Exception e){error(e);}});
            plot.addView(axis,new LinearLayout.LayoutParams(width,-2));
        }
        new RecordList(this,Ledger.INCOME,null).show();
    }
    void incomeCategory(){
        top(data.iconOf(categoryFocus)+"  "+categoryFocus,"返回",this::back);long total=0;List<Ledger.Entry> list=new ArrayList<>();for(Ledger.Entry e:data.entries)if(e.income&&e.category.equals(categoryFocus)){total+=e.amount;list.add(e);}
        LinearLayout c=card(body);metric(c,"总收入","¥ "+money(total),RED);c.addView(action("＋ 记录收入",()->openEntry(Ledger.INCOME,null)));
        new RecordList(this,Ledger.INCOME,categoryFocus).show();
    }
    void entryDetail(String kind){
        top(kind.equals(Ledger.LARGE)?"大宗支出明细":"报销明细","返回",this::back);long total=0,received=0;List<Ledger.Entry> list=new ArrayList<>();
        for(Ledger.Entry e:data.entries)if(e.kind.equals(kind)){total+=e.amount;received+=e.receivedTotal();list.add(e);}
        LinearLayout c=card(body);metric(c,"总金额","¥ "+money(total),kind.equals(Ledger.LARGE)?EXP:PURPLE);
        if(kind.equals(Ledger.REIMBURSE)){metric(c,"已报销","¥ "+money(received),RED);metric(c,"待报销","¥ "+money(total-received),PURPLE);}
        c.addView(action("＋ 记录",()->openEntry(kind,null)));new RecordList(this,kind,null).show();
    }
    void entryRow(Ledger.Entry e){
        entryRow(body,e);
    }
    void entryRow(LinearLayout parent,Ledger.Entry e){
        LinearLayout c=card(parent),r=row();c.setBackground(ripple(WHITE,18));int accent=e.income?RED:e.kind.equals(Ledger.LARGE)?EXP:PURPLE;
        TextView title=fit(text((e.income?data.iconOf(e.category)+"  ":"")+(e.income?e.category:e.title),15,INK,true),15,10);
        r.addView(title,new LinearLayout.LayoutParams(0,dp(29),1));TextView amount=fit(text("¥ "+money(e.amount),17,accent,true),17,9);amount.setTag("bounded-number");
        LinearLayout.LayoutParams amountParams=new LinearLayout.LayoutParams(0,dp(29),1);amountParams.leftMargin=dp(8);r.addView(amount,amountParams);c.addView(r);gap(c,6);
        c.addView(text((e.income?Ledger.csvDate(e.at.toLocalDate()):TIME.format(e.at))+"  ·  "+data.accountName(e.accountId),11.5f,MUTED,false));
        if(e.kind.equals(Ledger.REIMBURSE)){gap(c,8);LinearLayout state=row();String status=e.reimbursementStatus();state.addView(ui.tag(status,status.equals("已报销")?Ui.GREEN:status.equals("部分报销")?Ui.AMBER:Ui.PURPLE_DEEP,status.equals("已报销")?Ui.GREEN_SOFT:status.equals("部分报销")?Ui.AMBER_SOFT:PALE));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.leftMargin=dp(8);state.addView(text("已报 ¥ "+money(e.receivedTotal()),12,MUTED,false),sp);c.addView(state);}
        if(!e.note.isEmpty()){gap(c,6);c.addView(text(e.note,12,MUTED,false));}c.setOnClickListener(v->openEntry(e.kind,e));
    }
    void openEntry(String kind,Ledger.Entry existing){entryEditing=existing;formDraft=null;go(kind.equals(Ledger.INCOME)?"income_form":kind.equals(Ledger.LARGE)?"large_form":"reimburse_form");}
    void entryForm(String kind){
        boolean income=kind.equals(Ledger.INCOME),reimburse=kind.equals(Ledger.REIMBURSE);Ledger.Entry old=entryEditing;
        top((old==null?"记录":"修改")+(income?"收入":reimburse?"报销":"大宗支出"),"返回",this::back);LinearLayout f=card(body);
        Spinner category=null;if(income){int selected=old==null?0:data.categories.indexOf(old.category);category=choices(f,"收入类别",data.categories.toArray(new String[0]),selected);gap(f,8);f.addView(ui.link("＋ 自定义类别",this::manageCategories));}
        EditText name=income?null:field(f,"名称",old==null?"":old.title,false);EditText amount=field(f,"金额",old==null?"":Ledger.decimal(old.amount),true);
        DateControl at=new DateControl(f,income?"到账时间":"支出时间",old==null?LocalDateTime.now():old.at,false);
        boolean linked=(income||reimburse)&&(old==null||old.cashLinked);
        List<Ledger.Account> available=new ArrayList<>();for(Ledger.Account a:data.accounts)if(a.deletedAt==null||old!=null&&a.id==old.accountId)available.add(a);
        String[] accounts=new String[available.size()+1];accounts[0]=linked?"请选择账户":"不指定账户";int selected=0;
        for(int i=0;i<available.size();i++){accounts[i+1]=available.get(i).name;if(old!=null&&old.accountId==available.get(i).id)selected=i+1;}
        Spinner account=choices(f,linked?(income?"到账账户（必选）":"垫付账户（必选）"):"账户（可选）",accounts,selected);
        if(income||reimburse){
            gap(f,9);f.addView(text(linked?(income?"保存后自动增加到账账户余额；计算支出时排除这笔收入。":"垫付自动扣减所选账户，到账自动增加到账账户。报销不计入收入和支出；资产趋势保留金额变动。"):"历史记录保持原状，不自动补改旧余额。新增报销到账须选账户并自动增加余额。",11.5f,MUTED,false));
            if(available.isEmpty())f.addView(action("先添加账户",()->go("add_account")));
        }
        ReceiptEditor receipts=reimburse?new ReceiptEditor(this,f,amount,old):null;
        EditText note=field(f,"备注（可选）",old==null?"":old.note,false);Spinner finalCategory=category;gap(body,4);
        body.addView(button("保存记录",()->{try{
            Ledger.Data candidate=copy();long id=old==null?candidate.nextId():old.id;String title=income?finalCategory.getSelectedItem().toString():name.getText().toString().trim();
            Ledger.Entry e=new Ledger.Entry(id,income,Ledger.parseMoney(amount.getText().toString(),true),at.at,title,note.getText().toString().trim(),account.getSelectedItemPosition()==0?0:available.get(account.getSelectedItemPosition()-1).id);e.kind=kind;e.cashLinked=linked;if(income)e.category=title;
            if(reimburse)receipts.apply(e);
            candidate.entries.removeIf(x->x.id==id);candidate.entries.add(e);commit(candidate);entryEditing=null;back();
        }catch(Exception e){error(e);}}));
        if(old!=null){gap(body,12);body.addView(action("删除记录",()->AppIcons.dialog(this).setTitle("删除这笔记录？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{Ledger.Data candidate=copy();candidate.entries.removeIf(x->x.id==old.id);commit(candidate);entryEditing=null;back();}catch(Exception e){error(e);}}).show()));}
        if(formDraft!=null)formDraft.restore(body);
    }

    // ---------------------------------------------------------------- settings tab
    /** Small chevron that turns from “›” to “﹀” when its group is open. */
    static final class Chevron extends View{
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
        Chevron(Context c){super(c);}
        void open(boolean open){animate().rotation(open?90:0).setDuration(140).start();}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density;p.setColor(Ui.FAINT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f*d);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);path.reset();float w=getWidth(),h=getHeight();path.moveTo(w*0.34f,h*0.25f);path.lineTo(w*0.68f,h*0.5f);path.lineTo(w*0.34f,h*0.75f);c.drawPath(path,p);}
    }
    /** One collapsible group. Returns the container its rows are added to. */
    LinearLayout group(String key,String title,String summary){
        LinearLayout c=card(body);c.setPadding(dp(16),0,dp(16),0);c.setContentDescription("分组"+title);
        LinearLayout head=row();head.setMinimumHeight(dp(58));head.setBackground(ripple(Ui.ACCENT_TINT,16));
        LinearLayout names=col();names.addView(text(title,15.5f,INK,true));if(summary!=null){gap(names,4);names.addView(text(summary,11.5f,MUTED,false));}weight(head,names);
        Chevron arrow=new Chevron(this);arrow.setRotation(openSections.contains(key)?90:0);head.addView(arrow,new LinearLayout.LayoutParams(dp(22),dp(22)));
        head.setPadding(0,dp(10),0,dp(10));c.addView(head,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout content=col();View accentLine=new View(this);accentLine.setBackgroundColor(Ui.ACCENT_SOFT);content.addView(accentLine,new LinearLayout.LayoutParams(-1,dp(2)));content.setVisibility(openSections.contains(key)?View.VISIBLE:View.GONE);c.addView(content);
        sectionContents.put(key,content);sectionArrows.put(key,arrow);
        head.setOnClickListener(v->{
            boolean show=content.getVisibility()!=View.VISIBLE;openSections.clear();
            for(String other:sectionContents.keySet()){
                boolean selected=show&&other.equals(key);sectionContents.get(other).setVisibility(selected?View.VISIBLE:View.GONE);sectionArrows.get(other).open(selected);
            }
            if(show)openSections.add(key);
        });
        return content;
    }
    void rowIn(LinearLayout content,String title,String value,Runnable click){content.addView(ui.divider());LinearLayout r=ui.listRow(content,title,value,click);r.setMinimumHeight(dp(52));r.setContentDescription(title);}
    void settingsPage(){
        LinearLayout titleRow=row();titleRow.setPadding(dp(4),0,0,dp(12));titleRow.addView(text("设置",24,INK,true));body.addView(titleRow);
        LinearLayout notify=group("notify","更新与通知","固定更新日期、提醒、基金通知");
        rowIn(notify,"固定更新日期","每月 "+data.settings.checkDay1+" 日、"+data.settings.checkDay2+" 日",()->{
            LinearLayout f=col();f.setPadding(dp(20),0,dp(20),dp(8));EditText first=field(f,"第一个日期（1-31）",""+data.settings.checkDay1,true),second=field(f,"第二个日期（1-31）",""+data.settings.checkDay2,true);
            AlertDialog dialog=AppIcons.dialog(this).setTitle("更新节点").setView(f).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();
            dialog.getButton(-1).setOnClickListener(v->{try{int a=Integer.parseInt(first.getText().toString()),b=Integer.parseInt(second.getText().toString());if(a<1||b<1||a>31||b>31||a==b)throw new IllegalArgumentException("填写1至31的两个不同日期，短月取最后一天");Ledger.Data c=copy();c.settings.checkDay1=Math.min(a,b);c.settings.checkDay2=Math.max(a,b);commit(c);ReminderReceiver.schedule(this);dialog.dismiss();render();}catch(Exception e){error(e);}});
        });
        settingSwitch(notify,"余额更新提醒",data.settings.checkNotifications,(c,value)->c.settings.checkNotifications=value);
        settingSwitch(notify,"基金收益通知",data.settings.fundNotifications,(c,value)->c.settings.fundNotifications=value);
        settingSwitch(notify,"仅在基金出现亏损时通知",data.settings.lossOnly,(c,value)->c.settings.lossOnly=value);
        settingSwitch(notify,"自动刷新基金",data.settings.autoRefresh,(c,value)->c.settings.autoRefresh=value);
        rowIn(notify,"自动刷新间隔",MarketSession.label(data.settings.refreshMinutes),this::refreshInterval);
        rowIn(notify,"系统通知权限",null,()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())));
        rowIn(notify,"后台刷新与省电设置",null,this::backgroundHelp);
        rowIn(notify,"发送测试通知",null,this::testNotification);
        LinearLayout security=group("security","安全与隐私","应用锁");
        settingSwitch(security,"应用锁",data.settings.appLockEnabled,(c,value)->{
            if(value&&!AppLock.enabled(this)){
                showLock(LockOverlay.Mode.CREATE,this::render,this::render);
            }else if(!value){
                showLock(LockOverlay.Mode.CONFIRM_DISABLE,()->{AppLock.disable(this);render();},this::render);
            }else{
                c.settings.appLockEnabled=value;commit(c);render();
            }
        });
        if(data.settings.appLockEnabled)rowIn(security,"修改密码",null,()->showLock(LockOverlay.Mode.CHANGE_VERIFY_OLD,this::render,()->{}));
        if(data.settings.appLockEnabled)settingSwitch(security,"指纹 / 面容解锁",AppLock.biometricEnabled(this),(c,value)->{
            if(value&&!AppLock.biometricAvailable(this)){toast("系统未提供可用的指纹或面容，请先在系统设置中录入");render();return;}
            AppLock.setBiometric(this,value);render();
        });
        LinearLayout labels=group("labels","标签与历史","收入标签、历史月支出");
        rowIn(labels,"管理收入标签",null,this::manageCategories);
        rowIn(labels,"历史月支出",data.history.isEmpty()?null:data.history.size()+" 个月",()->go("history"));
        LinearLayout records=group("records","收支报销数据管理",".xlsx格式数据导入/导出");
        rowIn(records,"导入收支报销",null,()->pickFile(102));
        rowIn(records,"导出收支报销",null,this::exportRecordsDialog);
        rowIn(records,"下载收支报销模板",null,()->createFile(103,XLSX,"Yueji-收支报销模板.xlsx"));
        rowIn(records,"导入数据管理",null,()->{openImportBatch=-1;go("import_records");});
        LinearLayout funds=group("funds","基金数据管理",".xlsx格式数据导入/导出");
        rowIn(funds,"导入基金数据",null,()->pickFile(105));
        rowIn(funds,"导出基金数据",null,this::exportFunds);
        rowIn(funds,"下载基金模板",null,()->createFile(107,XLSX,"Yueji-基金模板.xlsx"));
        rowIn(funds,"导入数据管理",null,()->{openImportBatch=-1;go("import_funds");});
        LinearLayout backup=group("backup","备份与恢复","完整 JSON 备份");
        rowIn(backup,"导出完整备份",null,()->createFile(100,"application/json","Yueji-"+LocalDate.now()+".json"));
        rowIn(backup,"恢复 JSON 备份",null,()->pickFile(101));
        TradingCalendar calendar=fundScreens.calendar();int thisYear=FundEngine.now().getYear();
        LinearLayout holidays=group("holidays","日历更新",calendar.covered(thisYear)?thisYear+" 年已覆盖":thisYear+" 年未覆盖");
        holidays.addView(ui.divider());LinearLayout hn=col();hn.setPadding(0,dp(12),0,dp(4));
        hn.addView(text(thisYear+" 年："+(calendar.covered(thisYear)?"已覆盖":"未覆盖，暂按仅周末休市")+"\n"+(thisYear+1)+" 年："+(calendar.covered(thisYear+1)?"已覆盖":"未覆盖，暂按仅周末休市"),12.5f,MUTED,false));holidays.addView(hn);
        rowIn(holidays,"联网更新休市日历",null,this::updateHolidays);
        LinearLayout appearance=group("appearance","外观","浅色 / 深色 / 跟随系统 · 主题色");
        rowIn(appearance,"更换图标","点击选择",()->AppIcons.show(this));
        rowIn(appearance,"存储空间与缓存",null,()->go("storage"));
        Segmented modes=new Segmented(this,"跟随系统","浅色","深色");modes.setSelected(getPreferences(0).getInt("appearance",0));
        modes.onSelect(()->{getPreferences(0).edit().putInt("appearance",modes.selected()).apply();render();});appearance.addView(modes);gap(appearance,12);
        FlowLayout colors=new FlowLayout(this).columns(Ui.PALETTES.length).gaps(dp(6),dp(6));
        for(int i=0;i<Ui.PALETTES.length;i++){final int palette=i;TextView choice=text("●\n"+Ui.PALETTES[i],14,Ui.SWATCHES[i],true);choice.setGravity(Gravity.CENTER);choice.setContentDescription("主题色"+Ui.PALETTES[i]);choice.setBackground(Ui.roundStroke(Ui.ACCENT_TINT,getPreferences(0).getInt("palette",0)==i?Ui.ACCENT:Ui.BORDER,dp(10),dp(1)));choice.setOnClickListener(v->{getPreferences(0).edit().putInt("palette",palette).apply();render();});colors.addView(choice,new ViewGroup.MarginLayoutParams(-1,dp(60)));}
        appearance.addView(colors);gap(appearance,12);
        // The optional lock stays last; it must not displace data and appearance tools.
        View securityCard=(View)security.getParent();body.removeView(securityCard);body.addView(securityCard);
        LinearLayout about=card(body);about.addView(text("Yueji  "+versionName(),15.5f,INK,true));gap(about,8);
        gap(about,10);LinearLayout github=row();ImageView githubIcon=new ImageView(this);githubIcon.setImageResource(R.drawable.ic_github);githubIcon.setColorFilter(Ui.INK);github.addView(githubIcon,new LinearLayout.LayoutParams(dp(22),dp(22)));github.addView(action("GitHub · risnerohman625/Yueji",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/risnerohman625/Yueji")))));about.addView(github);
        about.addView(text("本地账本 · 基金数据来自东方财富公开净值，休市日历来自 holiday-cn 开源数据。\n首次更新所在月份若与最后一个历史月份相同，该月合并历史数据；否则两者分别计算。",12,MUTED,false));
    }
    String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "4.7.1";}}
    void refreshInterval(){
        String[] labels=new String[MarketSession.INTERVALS.length];int selected=0;
        for(int i=0;i<labels.length;i++){labels[i]=MarketSession.label(MarketSession.INTERVALS[i]);if(MarketSession.INTERVALS[i]==data.settings.refreshMinutes)selected=i;}
        AppIcons.dialog(this).setTitle("自动刷新间隔").setSingleChoiceItems(labels,selected,(dialog,index)->{try{
            Ledger.Data candidate=store.load();candidate.settings.refreshMinutes=MarketSession.INTERVALS[index];commit(candidate);FundRefreshJob.schedule(this);dialog.dismiss();render();
        }catch(Exception e){error(e);}}).setNegativeButton("取消",null).show();
    }
    void backgroundHelp(){
        String message="自动刷新间隔："+MarketSession.label(data.settings.refreshMinutes)+"。仅在北京时间交易日09:30–11:30、13:00–15:00自动刷新和通知；周末、法定休市日、午休及收市后不自动联网。首次进入基金页也遵守此规则，手动下拉刷新、添加及导入不受限制。\n\n基金净值通常在收市后公布；严格按此规则，收市后新净值需手动刷新，或等下一次开市自动获取，不是盘中实时估值。\n\n通知权限：允许Yueji发送通知，并开启亏损提醒渠道的横幅/声音。\n后台运行权限：vivo应用耗电管理中允许后台高耗电或后台运行。\n自启动权限：如手机提供，允许Yueji自启动。\n电池优化：如希望减少延迟，可设为不限制。无需悬浮窗权限。\n网络权限：使用系统允许的移动数据/Wi-Fi，不需要读取银行账户。\n\n系统调度、省电、休眠和网络会推迟任务，不能保证整点；强行停止后须再次打开。不会常驻前台服务，也不在后台强行弹出应用。";
        SpannableString styled=new SpannableString(message);for(String label:new String[]{"通知权限","后台运行权限","自启动权限","电池优化","网络权限"}){int at=message.indexOf(label);styled.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),at,at+label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        TextView explanation=text("",13,INK,false);refreshPermissionText=explanation;explanation.setText(styled);explanation.setPadding(dp(20),0,dp(20),dp(16));ScrollView scroll=new ScrollView(this);scroll.addView(explanation);
        AppIcons.dialog(this).setTitle("后台刷新与权限").setView(scroll)
            .setNegativeButton("知道了",null).setPositiveButton("应用系统设置",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();
    }
    void testNotification(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);toast("请允许通知后再次测试");return;}
        FundNotifications.channels(this);FundNotice.Report report=new FundNotice.Report();report.loss=true;report.text="测试通知 · 这不是实际基金收益。后台更新后出现亏损会通过此渠道提醒。";
        PendingIntent open=PendingIntent.getActivity(this,23,new Intent(this,MainActivity.class).putExtra("openTab",TAB_FUNDS),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        getSystemService(NotificationManager.class).notify(23,FundNotifications.build(this,report,open));toast("已发送测试通知");
    }
    void updateHolidays(){
        toast("正在更新休市日历…");
        new Thread(()->{
            String message;boolean failed=false;try{List<Integer> changed=HolidayUpdater.update(getApplicationContext());message=changed.isEmpty()?"休市日历已是最新":"已更新 "+changed+" 年休市日历";}catch(Exception e){message=NetworkIo.message(e);failed=true;}
            String shown=message;boolean problem=failed;runOnUiThread(()->{fundScreens.cachedCalendar=null;if(isFinishing()||isDestroyed())return;if(page.isEmpty()&&tab==TAB_SETTINGS)render();if(problem)networkProblem(shown);else toast(shown);});
        },"holiday-update").start();
    }
    interface SettingChange{void apply(Ledger.Data data,boolean value);}
    void settingSwitch(LinearLayout parent,String label,boolean checked,SettingChange update){
        parent.addView(ui.divider());LinearLayout r=row();r.setPadding(dp(2),dp(8),0,dp(8));r.setMinimumHeight(dp(52));r.addView(text(label,14,INK,false),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setContentDescription(label);
        toggle.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{Color.WHITE,0xFFFFFFFF}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{PURPLE,0xFFCFC9D8}));
        toggle.setChecked(checked);r.addView(toggle);parent.addView(r);
        toggle.setOnCheckedChangeListener((b,value)->{try{Ledger.Data c=copy();update.apply(c,value);commit(c);ReminderReceiver.schedule(this);FundRefreshJob.schedule(this);}catch(Exception e){error(e);}});
    }
    void createFile(int request,String type,String name){startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),request);}
    void pickFile(int request){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(request==101?"application/json":XLSX).addCategory(Intent.CATEGORY_OPENABLE),request);}
    void requireXlsxName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c==null||!c.moveToFirst()||c.isNull(0)||!c.getString(0).toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                throw new IllegalArgumentException("仅支持 .xlsx 文件，请使用软件导出的模板");
        }
    }
    void exportFunds(){createFile(106,XLSX,"Yueji-基金-"+LocalDate.now()+".xlsx");}
    void pickDate(LocalDate current,java.util.function.Consumer<LocalDate> done){
        DatePickerDialog picker=new DatePickerDialog(this,(v,y,mo,d)->done.accept(LocalDate.of(y,mo+1,d)),current.getYear(),current.getMonthValue()-1,current.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
    }
    LocalDate earliestRecordDate(){
        LocalDate first=LocalDate.now();
        for(Ledger.Entry e:data.entries)if(e.at.toLocalDate().isBefore(first))first=e.at.toLocalDate();
        for(Ledger.HistoryMonth h:data.history)if(h.month.atDay(1).isBefore(first))first=h.month.atDay(1);
        for(Ledger.Snapshot s:data.snapshots)if(s.at.toLocalDate().isBefore(first))first=s.at.toLocalDate();
        return first;
    }
    /** Pick the period and which of the three data kinds to write, then choose where to save the workbook. */
    void exportRecordsDialog(){
        LocalDate[] range={earliestRecordDate(),LocalDate.now()};
        LinearLayout f=col();f.setPadding(dp(20),0,dp(20),dp(14));
        label(f,"起始日期");TextView from=action(Ledger.csvDate(range[0]),()->{});from.setContentDescription("导出起始日期");from.setOnClickListener(v->pickDate(range[0],d->{range[0]=d;from.setText(Ledger.csvDate(d));}));f.addView(from);
        label(f,"终止日期");TextView to=action(Ledger.csvDate(range[1]),()->{});to.setContentDescription("导出终止日期");to.setOnClickListener(v->pickDate(range[1],d->{range[1]=d;to.setText(Ledger.csvDate(d));}));f.addView(to);
        label(f,"导出的数据");
        CheckBox expense=new CheckBox(this),income=new CheckBox(this),reimburse=new CheckBox(this);
        expense.setText("支出（月支出、大宗支出）");income.setText("收入");reimburse.setText("报销");
        for(CheckBox box:new CheckBox[]{expense,income,reimburse}){box.setChecked(true);box.setTextColor(INK);f.addView(box);}
        AlertDialog d=AppIcons.dialog(this).setTitle("导出收支报销").setView(wrap(f)).setNegativeButton("取消",null).setPositiveButton("导出",null).create();d.show();
        d.getButton(-1).setOnClickListener(v->{
            if(range[0].isAfter(range[1])){error(new IllegalArgumentException("起始日期不能晚于终止日期"));return;}
            if(!expense.isChecked()&&!income.isChecked()&&!reimburse.isChecked()){error(new IllegalArgumentException("请至少勾选一种数据"));return;}
            pendingFrom=range[0];pendingTo=range[1];pendingExpense=expense.isChecked();pendingIncome=income.isChecked();pendingReimburse=reimburse.isChecked();d.dismiss();
            createFile(104,XLSX,"Yueji-收支报销-"+Ledger.csvDate(range[0]).replace("/","")+"-"+Ledger.csvDate(range[1]).replace("/","")+".xlsx");
        });
    }
    void historyPage(){
        top("历史月支出","返回",this::back);LinearLayout c=card(body);
        c.addView(text("历史月支出可指定累计截止时间。只累加截止时间之后各账户的余额变化；此前更新不重复计算。仅写月份时，最后一个历史月按该月首次余额更新衔接。",13,MUTED,false));gap(c,10);
        c.addView(action("＋ 录入月支出",()->historyForm(null)));
        for(Ledger.HistoryMonth h:data.history){LinearLayout item=card(body);item.setBackground(ripple(WHITE,18));metric(item,Ledger.csvMonth(h.month),"¥ "+money(h.expense),PURPLE);if(!h.full())item.addView(text("衔接 "+TIME.format(h.cutoff),11.5f,MUTED,false));item.addView(text(h.includesAdvances?"包含报销垫付，统计时扣除":"已排除报销",11.5f,MUTED,false));item.setOnClickListener(v->historyForm(h));}
    }
    void historyForm(Ledger.HistoryMonth old){
        LinearLayout f=col();f.setPadding(dp(20),0,dp(20),dp(14));EditText month=field(f,"月份（如 2026/10）",old==null?Ledger.csvMonth(YearMonth.now()):Ledger.csvMonth(old.month),false);EditText amount=field(f,"支出金额",old==null?"":Ledger.decimal(old.expense),true);
        EditText cutoff=field(f,"累计截止时间（可空，如 2026/10/16 12:00）",old!=null&&!old.full()?Ledger.csvMinute(old.cutoff):"",false);
        Spinner basis=choices(f,"历史金额是否包含报销垫付",new String[]{"已排除报销","包含报销垫付"},old!=null&&old.includesAdvances?1:0);
        AlertDialog d=AppIcons.dialog(this).setTitle("历史月支出").setView(wrap(f)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.show();
        d.getButton(-1).setOnClickListener(v->{try{
            YearMonth ym=Ledger.parseCsvMonth(month.getText().toString());if(ym.isAfter(YearMonth.now()))throw new IllegalArgumentException("月份不能晚于当前月份");
            LocalDateTime through=cutoff.getText().toString().trim().isEmpty()?ym.plusMonths(1).atDay(1).atStartOfDay():Ledger.parseCsvMinute(cutoff.getText().toString().trim());
            if(!through.equals(ym.plusMonths(1).atDay(1).atStartOfDay())&&!YearMonth.from(through).equals(ym))throw new IllegalArgumentException("累计截止时间须在所选月份内");
            Ledger.HistoryMonth h=new Ledger.HistoryMonth(ym,Ledger.parseMoney(amount.getText().toString(),false),through);h.includesAdvances=basis.getSelectedItemPosition()==1;
            Ledger.Data candidate=copy();candidate.history.removeIf(x->x.month.equals(ym)||old!=null&&x.month.equals(old.month));candidate.history.add(h);commit(candidate);d.dismiss();render();
        }catch(Exception e){error(e);}});
    }
    ScrollView wrap(LinearLayout form){ScrollView s=new ScrollView(this);s.addView(form);return s;}
    void importPreview(){
        top("导入预览","返回",this::back);if(pendingImport==null){body.addView(action("选择 Excel 文件",()->pickFile(102)));return;}
        LinearLayout c=card(body);c.addView(text(pendingImport.description(),16,INK,true));gap(c,12);c.addView(text("将合并记录；相同月份的支出总额按本次文件替换；与账本中已有记录相同的行会跳过。确认后可在导入数据管理中按批次或单条删除。",12,MUTED,false));
        body.addView(button("确认导入",()->{try{
            synchronized(LedgerStore.class){
                Ledger.Data before=store.load();
                HistoryImport.Result fresh=pendingImport.source==null?pendingImport:HistoryImport.preview(pendingImport.source,before);
                ImportJournal.record(before,fresh.data,ImportJournal.RECORDS,"表格导入");commit(fresh.data);
            }
            pendingImport=null;go("");
        }catch(Exception e){error(e);}}));
    }
    void write(Uri uri,byte[] bytes)throws IOException{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("文件无法写入");out.write(bytes);}toast("已导出");}
    void write(Uri uri,String text)throws IOException{write(uri,text.getBytes(StandardCharsets.UTF_8));}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==ScreenshotCleanup.REQUEST||request==ScreenshotCleanup.REQUEST+1){screenshotCleanup.result(request,result);return;}if(request==BankScreens.IMAGE||request==BankScreens.INCOME){bankScreens.result(request,result,intent);return;}if(request==WatchlistScreens.PICK){if(result==RESULT_OK&&intent!=null&&intent.getData()!=null)watchScreens.read(intent.getData());return;}if(result!=RESULT_OK||intent==null)return;if(request==FundScreenshot.PICK){List<Uri> images=new ArrayList<>();if(intent.getClipData()!=null)for(int i=0;i<intent.getClipData().getItemCount();i++)images.add(intent.getClipData().getItemAt(i).getUri());else if(intent.getData()!=null)images.add(intent.getData());if(!images.isEmpty())fundScreenshot.read(images);return;}if(intent.getData()==null)return;Uri uri=intent.getData();try{
        if(request==FundScreenshot.PICK){fundScreenshot.read(uri);return;}
        if(request==100)write(uri,store.exportJson());
        else if(request==103)write(uri,HistoryImport.templateXlsx());
        else if(request==104)write(uri,HistoryImport.exportXlsx(store.load(),pendingFrom,pendingTo,pendingExpense,pendingIncome,pendingReimburse));
        else if(request==106)write(uri,FundSheet.exportXlsx(store.load()));
        else if(request==107)write(uri,FundSheet.templateXlsx());
        else{
            if(request==102||request==105)requireXlsxName(uri);
            byte[] content;try(InputStream in=getContentResolver().openInputStream(uri)){content=readLimit(in);}
            if(request==102){pendingImport=HistoryImport.preview(content,store.load());go("import_preview");}
            else if(request==105)fundScreens.importFile(content);
            else{Ledger.Data restored=store.parseBackup(new String(content,StandardCharsets.UTF_8));AppIcons.dialog(this).setTitle("替换当前数据？").setMessage("账户 "+restored.accounts.size()+" 个，更新 "+restored.snapshots.size()+" 次，记录 "+restored.entries.size()+" 条，负债 "+restored.debts.size()+" 笔。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{try{commit(restored);FundRefreshJob.schedule(this);go("");}catch(Exception e){error(e);}}).show();}
        }
    }catch(Exception e){error(e);}}
    byte[] readLimit(InputStream in)throws IOException{if(in==null)throw new IOException("无法读取文件");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>10*1024*1024)throw new IOException("文件不能超过10MB");out.write(b,0,n);}return out.toByteArray();}

    // ---------------------------------------------------------------- funds
    void funds(){fundScreens.list(data);}
    void autoRefreshFunds(){
        if(fundPageVisited||!data.settings.autoRefresh||!MarketSession.open(this))return;
        fundPageVisited=true;if(!data.funds.isEmpty())refreshFunds(true);
    }
    /** Starts a refresh from the pull gesture or automatically; the pull spinner stays until the page is redrawn. */
    void refreshFunds(){
        refreshFunds(false);
    }
    void refreshFunds(boolean automatic){
        if(automatic&&!MarketSession.open(this))return;
        boolean any=false;for(Ledger.Fund f:data.funds)if(!f.removed())any=true;
        if(refreshing||!any){if(!refreshing&&fundScreens.pull!=null)fundScreens.pull.setRefreshing(false);return;}
        refreshing=true;getPreferences(0).edit().putLong("fundAttempt",System.currentTimeMillis()).apply();if(fundScreens.pull!=null)fundScreens.pull.setRefreshing(true);
        new Thread(()->{String failure=null;try{failure=automatic?FundClient.refreshDuringMarket(getApplicationContext()):FundClient.refresh(getApplicationContext());}catch(Exception e){failure=NetworkIo.message(e);}String message=failure;runOnUiThread(()->{refreshing=false;if(!isFinishing()&&!isDestroyed()){if(fundScreens.pull!=null)fundScreens.pull.setRefreshing(false);if(tab==TAB_FUNDS&&page.isEmpty())render();if(message!=null)networkProblem(message);}});},"fund-refresh").start();
    }
    void fundEditor(Ledger.Fund old){fundScreens.buy(old,null);}
}
