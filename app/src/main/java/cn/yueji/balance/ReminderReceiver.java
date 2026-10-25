package cn.yueji.balance;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String ACTION="cn.yueji.balance.CHECK_BALANCES";
    public static void schedule(Context context) {
        NotificationManager nm=context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("balance-check","余额更新提醒",NotificationManager.IMPORTANCE_DEFAULT));
        LedgerStore store=new LedgerStore(context);Ledger.Settings settings;try{settings=store.load().settings;}finally{store.close();}
        ZonedDateTime now=ZonedDateTime.now(), next=now.withHour(settings.reminderHour).withMinute(0).withSecond(0).withNano(0);
        if(!next.isAfter(now)) next=next.plusDays(1);
        while(next.getDayOfMonth()!=Math.min(settings.checkDay1,next.toLocalDate().lengthOfMonth()) && next.getDayOfMonth()!=Math.min(settings.checkDay2,next.toLocalDate().lengthOfMonth())) next=next.plusDays(1);
        PendingIntent pending=PendingIntent.getBroadcast(context,11,new Intent(context,ReminderReceiver.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(settings.checkNotifications)context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pending);
        else context.getSystemService(AlarmManager.class).cancel(pending);
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(ACTION.equals(intent.getAction())) {
            LocalDate today=LocalDate.now();
            LedgerStore configuration=new LedgerStore(context);Ledger.Settings settings;try{settings=configuration.load().settings;}finally{configuration.close();}
            if(settings.checkNotifications&&(today.getDayOfMonth()==Math.min(settings.checkDay1,today.lengthOfMonth()) || today.getDayOfMonth()==Math.min(settings.checkDay2,today.lengthOfMonth())) && (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)) {
                LedgerStore store=new LedgerStore(context); boolean done=false;
                try { for(Ledger.Snapshot s:store.load().snapshots) if(s.at.toLocalDate().equals(today) && (today.getDayOfMonth()!=1 || s.isMonthStart())) done=true; } finally { store.close(); }
                if(!done) {
                    PendingIntent open=PendingIntent.getActivity(context,12,new Intent(context,MainActivity.class).putExtra("openPage","check").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    String text=today.getDayOfMonth()==1?"月初余额更新 · 填写1日00:00余额。":"月中余额更新 · 更新全部账户余额与本月支出。";
                    Notification n=NotificationArt.style(context,new Notification.Builder(context,"balance-check").setContentIntent(open).setAutoCancel(true),text,System.currentTimeMillis()).build();
                    context.getSystemService(NotificationManager.class).notify(1,n);
                }
            }
        }
        schedule(context);
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){AppIcons.sync(context);FundRefreshJob.schedule(context);}
    }
}
