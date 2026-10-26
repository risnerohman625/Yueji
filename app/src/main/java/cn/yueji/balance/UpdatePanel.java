package cn.yueji.balance;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Independent dimmed dialog window, floating above the settings page and app navigation. */
final class UpdatePanel {
    final MainActivity activity;final Dialog dialog;final LinearLayout panel;
    final TextView status,update,later;final ProgressBar progress;
    UpdatePanel(MainActivity activity,String version,String notes,Runnable download){
        this.activity=activity;panel=activity.col();panel.setPadding(activity.dp(16),activity.dp(14),activity.dp(16),activity.dp(12));
        panel.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,activity.dp(8),activity.dp(1)));panel.setContentDescription("新版更新面板");
        panel.addView(activity.text("发现新版本 "+version,16,Ui.INK,true));
        TextView description=activity.text(summary(notes),12,Ui.MUTED,false);description.setMaxLines(3);description.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(description);activity.gap(panel,8);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);progress.setContentDescription("更新下载进度");panel.addView(progress,new LinearLayout.LayoutParams(-1,activity.dp(8)));
        status=activity.text("",12,Ui.MUTED,false);status.setMaxLines(2);status.setVisibility(View.GONE);status.setContentDescription("更新下载状态");panel.addView(status);activity.gap(panel,8);
        LinearLayout buttons=activity.row();later=activity.action("稍后更新",()->{});update=activity.button("立即更新",download);buttons.addView(later,new LinearLayout.LayoutParams(0,activity.dp(42),1));LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(0,activity.dp(42),1);spacing.leftMargin=activity.dp(8);buttons.addView(update,spacing);panel.addView(buttons);
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(false);later.setOnClickListener(v->dialog.dismiss());
    }
    static String summary(String notes){
        String cleaned=notes.replaceAll("(?m)^#+\\s*","").replaceAll("[`*>]","").replaceAll("\\s+"," ").trim();
        return cleaned.isEmpty()?"优化体验与稳定性，建议备份后更新。":cleaned.length()>100?cleaned.substring(0,100)+"…":cleaned;
    }
    void show(){
        dialog.show();Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.38f);window.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        int navigation=0;for(int i=0;i<activity.root.getChildCount();i++)if(activity.root.getChildAt(i) instanceof NavBar)navigation=activity.root.getChildAt(i).getHeight();
        WindowManager.LayoutParams attributes=window.getAttributes();attributes.width=activity.getResources().getDisplayMetrics().widthPixels-activity.dp(28);attributes.height=WindowManager.LayoutParams.WRAP_CONTENT;attributes.y=Math.max(activity.dp(76),navigation)+activity.dp(10);window.setAttributes(attributes);
    }
    void downloading(){dialog.setCancelable(false);progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);status.setVisibility(View.VISIBLE);status.setTextColor(Ui.MUTED);status.setText("正在连接下载源…");update.setEnabled(false);later.setEnabled(false);}
    void percent(int value){progress.setIndeterminate(value<0);if(value>=0)progress.setProgress(value);status.setText(value<0?"正在下载…":"正在下载 "+value+"%");}
    void failure(){dialog.setCancelable(true);progress.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setTextColor(Ui.RED);status.setText("下载或校验失败，请更换 GitHub / Gitee 更新源后重试。");update.setText("重试更新");update.setEnabled(true);later.setEnabled(true);}
    void dismiss(){dialog.dismiss();}
}
