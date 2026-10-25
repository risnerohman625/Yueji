package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;
import java.util.*;

public final class FundNotifications {
    public static void publish(Context context,Ledger.Data data){
        if(!data.settings.fundNotifications||!MarketSession.open(context))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        FundNotice.Report report=FundNotice.report(data,FundEngine.now());if(report==null||data.settings.lossOnly&&!report.loss)return;
        String signature=report.signature;
        SharedPreferences prefs=context.getSharedPreferences("fund-notifications",Context.MODE_PRIVATE);
        if(signature.equals(prefs.getString("last","")))return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        channels(context);
        PendingIntent open=PendingIntent.getActivity(context,23,new Intent(context,MainActivity.class).putExtra("openTab",MainActivity.TAB_FUNDS),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=build(context,report,open);
        manager.notify(23,n);prefs.edit().putString("last",signature).apply();
    }
    static void channels(Context context){
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel losses=new NotificationChannel("fund-loss-v4","基金亏损提醒",NotificationManager.IMPORTANCE_HIGH);losses.setDescription("已公布净值出现亏损时提醒，同一结果不重复提示");losses.enableVibration(true);manager.createNotificationChannel(losses);
        manager.createNotificationChannel(new NotificationChannel("fund-profit-v4","基金收益摘要",NotificationManager.IMPORTANCE_DEFAULT));
    }
    static Notification build(Context context,FundNotice.Report report,PendingIntent open){
        Notification.Builder builder=new Notification.Builder(context,report.loss?"fund-loss-v4":"fund-profit-v4")
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).setVisibility(Notification.VISIBILITY_PRIVATE);
        return NotificationArt.style(context,builder,report.text,System.currentTimeMillis()).build();
    }
    private FundNotifications(){}
}
