package cn.yueji.balance;

import android.view.Gravity;
import android.widget.*;

final class UiBanner {
    static PopupWindow show(MainActivity activity,String message,boolean error,boolean upper){
        TextView text=activity.text(message,14,error?Ui.RED:Ui.ACCENT_DEEP,true);text.setGravity(Gravity.CENTER);text.setMaxLines(3);
        text.setPadding(activity.dp(18),activity.dp(14),activity.dp(18),activity.dp(14));text.setBackground(Ui.roundStroke(error?Ui.RED_SOFT:Ui.ACCENT_SOFT,Ui.BORDER,activity.dp(12),activity.dp(1)));
        text.setContentDescription(upper?"基金导入结果横幅":"最新版本横幅");
        int screenHeight=activity.getResources().getDisplayMetrics().heightPixels;
        PopupWindow popup=new PopupWindow(text,activity.getResources().getDisplayMetrics().widthPixels-activity.dp(44),-2,false);popup.setElevation(activity.dp(6));
        popup.showAtLocation(activity.root,(upper?Gravity.TOP:Gravity.BOTTOM)|Gravity.CENTER_HORIZONTAL,0,upper?screenHeight/3:screenHeight/4);
        text.postDelayed(popup::dismiss,4500);return popup;
    }
    private UiBanner(){}
}
