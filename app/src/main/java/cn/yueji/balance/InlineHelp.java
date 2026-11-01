package cn.yueji.balance;

import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Small anchored explanations, reserved for easily misunderstood bookkeeping rules. */
final class InlineHelp {
    static final String CASH_POLICY="勾选：必选账户，生成带正负号的金额流水，不推算实测余额。\n不勾选：仅存明细；收入仍参与统计与支出修正。\n账户金额＝最后实测＋此后流水，下一次实测重新起算。\n月支出只按实测差额核算；大宗支出不重复加。";
    static String forTitle(String title){
        switch(title){
            case "大宗支出明细":return CASH_POLICY;
            case "收入明细":case "全部收入":return CASH_POLICY;
            case "报销明细":return "垫付不算个人消费，到账不算普通收入。\n关联账户后自动记录资金变化；分次到账按实际日期填写。\n月份卡片按垫付月份汇总申请金额，不重复累计到账。";
            case "负债":return "负债与还款仅作独立台账。\n不会自动改变账户余额、总资产或收支，请另行核对实际资金。";
            case "自选基金":return "自选仅用于观察行情，不代表实际持仓。\n删除自选不删除已购基金。\n定投组按已确认日限购自动归类，不会创建定投计划；限额不代表开放申购，请核对交易平台。";
            case "添加基金买入":return "关联账户会记录账面扣款；历史补录请避免重复扣款。\n定投自动生成的是本地记账，不会向平台下单或实际扣款。";
            default:return null;
        }
    }
    static void add(MainActivity m,LinearLayout parent,String title,String message){
        LinearLayout row=m.row();row.addView(m.text(title,12,Ui.MUTED,false));row.addView(icon(m,title,message));parent.addView(row);
    }
    static View icon(MainActivity m,String title,String message){
        View icon=new View(m){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=m.dp(8);
                p.setColor(Ui.MUTED);p.setStrokeWidth(m.dp(1.2f));p.setStyle(Paint.Style.STROKE);c.drawCircle(x,y,r,p);
                p.setStyle(Paint.Style.FILL);p.setTypeface(Ui.BOLD);p.setTextSize(m.dp(12));p.setTextAlign(Paint.Align.CENTER);c.drawText("!",x,y-(p.ascent()+p.descent())/2,p);
            }
        };
        icon.setLayoutParams(new LinearLayout.LayoutParams(m.dp(40),m.dp(40)));icon.setContentDescription(title+"说明");icon.setFocusable(true);
        final PopupWindow[] popup={null};icon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){if(popup[0]!=null)popup[0].dismiss();}});
        icon.setOnClickListener(v->{
            if(popup[0]!=null&&popup[0].isShowing()){popup[0].dismiss();return;}
            TextView note=m.text(message,13,Color.WHITE,false);note.setPadding(m.dp(14),m.dp(11),m.dp(14),m.dp(11));note.setLineSpacing(m.dp(3),1);note.setBackground(Ui.round(0xE6222933,m.dp(12)));
            Rect frame=new Rect();v.getWindowVisibleDisplayFrame(frame);int width=Math.min(m.dp(300),frame.width()-m.dp(24));note.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            PopupWindow window=new PopupWindow(note,width,-2,true);popup[0]=window;window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setOutsideTouchable(true);window.setElevation(m.dp(8));
            int[] pos=new int[2];v.getLocationOnScreen(pos);int x=Math.max(frame.left+m.dp(12),Math.min(pos[0],frame.right-width-m.dp(12)));
            int y=pos[1]+v.getHeight();if(y+note.getMeasuredHeight()>frame.bottom)y=pos[1]-note.getMeasuredHeight();
            window.showAtLocation(v,Gravity.TOP|Gravity.LEFT,x,Math.max(frame.top,y));
        });return icon;
    }
    private InlineHelp(){}
}
