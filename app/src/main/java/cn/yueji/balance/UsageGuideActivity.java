package cn.yueji.balance;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Foreground-only reading timer; leaving the activity pauses the required reading time. */
public final class UsageGuideActivity extends Activity {
    static final String REVISION="4.7.8-27";
    long remaining=10000,last;boolean required,active;TextView done;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable tick=new Runnable(){public void run(){if(!active)return;long now=SystemClock.elapsedRealtime();remaining=Math.max(0,remaining-(now-last));last=now;done.setText(remaining>0?"请阅读 "+((remaining+999)/1000)+" 秒":"我已阅读，开始使用");done.setEnabled(remaining==0);handler.postDelayed(this,200);}};
    public void onCreate(Bundle state){super.onCreate(state);setTheme(Ui.dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);required=getIntent().getBooleanExtra("required",false);if(!required)remaining=0;if(state!=null)remaining=state.getLong("remaining",remaining);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,48,28,32);root.setBackgroundColor(Ui.BG);setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(28+bars.left,20+bars.top,28+bars.right,20+bars.bottom);}else v.setPadding(28,20+insets.getSystemWindowInsetTop(),28,20+insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();TextView title=label("软件使用说明",22,true);root.addView(title);ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(String[] section:SECTIONS){content.addView(label(section[0],17,true));for(int i=1;i<section.length;i++)content.addView(label("• "+section[i],14,false));}
        done=label("我已阅读，开始使用",16,true);done.setGravity(Gravity.CENTER);done.setBackground(Ui.round(Ui.ACCENT_SOFT,18));done.setOnClickListener(v->{if(remaining>0)return;getSharedPreferences("updates",0).edit().putString("guideRead",REVISION).apply();finish();});root.addView(done);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    TextView label(String text,int size,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Ui.INK);v.setTypeface(bold?Ui.BOLD:Ui.MEDIUM);v.setPadding(10,18,10,18);return v;}
    protected void onResume(){super.onResume();active=true;last=SystemClock.elapsedRealtime();handler.post(tick);}
    protected void onPause(){active=false;handler.removeCallbacks(tick);super.onPause();}
    protected void onSaveInstanceState(Bundle state){state.putLong("remaining",remaining);super.onSaveInstanceState(state);}
    void back(){if(required&&remaining>0)return;finish();}
    @android.annotation.SuppressLint("GestureBackNavigation")
    public void onBackPressed(){back();}
    static final String[][] SECTIONS={
        {"一、记账原理与账户余额","本软件以账户的真实余额变化反推日常支出，不要求逐笔录入每次消费。基本关系是：期初余额－期末余额＋收入－非消费流出＋非消费流入。","新增账户时填写对应时间的真实余额；之后用更新账户余额记录实际余额。每个账户只比较自身相邻观察值，首次余额是起点，不代表已发生支出。不要用虚构余额补齐未更新的账户。","收入、报销、投资等关联账户的记录会生成余额变动；之后手工更新的是当时真实余额，不要再人为加减一次相同交易。历史导入记录不会自动重复扣款。","月支出仅基于已知余额区间和历史导入数据。历史支出截止时间前后按规则衔接，缺失月份不等于真实零支出；负支出可能表示余额或调整项需要核对。"},
        {"二、收支与报销","收入：新增时选择到账账户，会增加该账户余额，并作为收入调整项，避免把收入误认为负支出。","大额支出：用于标记、分类查看已经反映在余额差中的消费，不再重复扣减账户或把同一消费额额外叠加到月支出。","报销：垫付时选择出款账户，扣减余额，但作为非个人消费从支出中剔除；到账时单独填写实际到账时间、金额及账户，支持分次到账，不当作普通收入。","负债：贷款与还款是独立台账，不自动改变资产、账户余额或收支。实际资金变化需在账户余额及适合的资金记录中独立核对。"},
        {"三、普通基金、银行理财与自选基金","已导入的普通基金代表你的买入批次和实际持仓，需记录代码、买入金额、费用和时间；软件用交易净值、确认份额、分红等数据计算持仓及收益。","自选基金只是行情观察名单，不是买入记录，不会凭空增加持仓、资产或收益。持有分组自动显示仍有效的已购基金。删除自选仅移除关注与分组，不删除已购记录。","基金只支持手动填写交易信息或XLSX导入，不再提供截图识别。手动添加输入代码后自动查询名称；自选详情的购买入口复用已保存的名称与代码，只需填写金额、手续费及时间。A/C份额不同，请核对基金代码。","银行理财使用手动记录的余额、收益与买卖流水，不通过普通基金净值自动推算；缺失日期不伪造日收益，买卖进行中与已完成状态应按实际确认时间填写。"},
        {"四、关联账户与收益规则","普通基金买入/卖出、银行理财申购/赎回可选择关联账户：买入扣款、卖出按净到账入账，属于投资划转，不是日常消费或工资收入。选择不关联账户则仅管理持仓，不改变账户余额；已有数据不会擅自追溯扣款。","持有收益为当前持有价值相对成本的盈亏；累计收益还包括已卖出部分。总览遵守当前分类与隐藏状态；已删除普通基金的收益停在删除时，保留在累计收益明细中。","普通基金从首页删除是移除并冻结收益，可在累计收益中恢复；永久删除需先从首页删除，会清除其交易及关联划转，无法恢复。删除或回滚前先备份，并核对账户影响。","定投配置只是计划管理，必须由你确认实际买入才记录交易，不会连接银行自动扣款或替你购买基金。","基金数据来自东方财富公开净值和资料，不是实时成交报价；节假日沿用最新公布净值。普通日收益按净值计算，分红/拆分按事件调整；不同平台截止日或披露口径可能不同。费用、锁定期及限购以基金公司和实际销售平台公告为准，未知费用不等于零。"},
        {"五、操作与指标说明","每日定投安排交易工作日，跳过周末和已知休市日；计划总期数0为不限，达到已登记实际期数后停止。计划不会自动向银行扣款或替你下单。新增买入手续费按获取的优惠费率乘以金额估算，可手动修改；未知或按笔固定费用请核对平台填写。","全部分组现在是未归类基金的收件箱：持有基金和已分组基金默认不显示；可用添加到分组明确放回全部。删除分组不会删除基金，未归类且未持有的基金自动回到全部。首页时期和详情绘图时期相互独立。","长按自选基金进入多选；勾选框旁保留基金名称和代码，底部固定操作栏用于取消、添加到分组和删除。持有组仍自动同步已购记录。","夏普比率按日收益、252交易日年化和无风险利率0计算，非官方评级。周期历史不足时显示0.0不代表真实零收益。","基金按T+1和15:00界限估算，QDII及实际确认日以交易平台为准；历史买入可不关联账户，避免重复扣款。"},
        {"六、备份、隐私与软件更新","XLSX导入导出用于指定表格数据交换，需使用模板和完整表头；完整JSON备份包含自选基金和分组，用于替换恢复整个账本；旧备份未包含的自选记录不会保留，重要修改、删除和升级前请先备份并妥善保管。","账本保存在本机。公开行情查询需要联网；GitHub/Gitee仅提供项目和安装包，不会因检查更新上传账本。","应用升级通过系统安装界面，需要你允许安装来源并确认；软件不能静默安装或保证系统自动重启。安装后请重新打开，阅读新版说明和更新内容。"},
        {"七、最大回撤与修复的计算口径","自选图表指标由本机根据公开历史净值及分红拆分调整后的总回报序列计算，不是直接读取支付宝的评级结果。不同平台数据源、截止日、复权方式或同幅回撤的选取可能不同。","本时期最高下跌幅度就是所选区间最大回撤：逐日比较此前最高的总回报净值与其后低点，取最大的 1－低点/前高。中间可以有反弹，不只比较单独连续下跌段，也不是累计涨幅的百分点差。例如累计涨幅10%→5%→7%→1%，用1.10到1.01算回撤约8.18%，并非9%。","最大回撤修复天数从最大回撤谷底到首次恢复该段前高的日期，按自然日计，包含周末休市日，不含下跌期；前高到谷底是下跌天数，前高到恢复是完整回撤周期。尚未修复显示正在修复中，不把截至今天的等待天数当已修复天数。相同最大回撤优先保留最早一段；重复前高采用最近一次。","近1年、成立来涨跌幅是区间复合总收益率，不是单日涨跌幅之和；日涨跌幅对应标注的净值日期，不是手机今天或盘中估值。缺少净值的日期不虚构收益，选择区间历史不足时指标0.0不代表实际零风险。","夏普比率按样本日收益、252交易日年化、无风险利率0计算。单独的基金收益率不等于用户持有收益率，买入日期、手续费、分红与多笔交易都会影响实际持仓盈亏。"},
        {"八、收市后自动更新","默认每2小时检查一次，沿用设置里的自动刷新开关和间隔。只在北京时间交易日15:00后至24:00运行；周末和休市日不自动请求。首次进入基金或自选页会额外检查一次。已购与自选按代码去重，一次结果写入两处。","请求成功不等于今天净值已公布：只有净值日期达到当天且完整历史到位，才排除该基金。全部完成后当天不再联网；失败或仍为旧净值下个间隔重试。QDII、跨境及披露较晚基金可能次日才公布，可手动下拉补刷。系统调度会推迟任务，不保证整点，强行停止后须重新打开应用。"}
    };
}
