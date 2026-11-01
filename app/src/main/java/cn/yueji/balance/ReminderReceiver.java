package cn.yueji.balance;
import android.app.*;
import android.content.*;
/** Upgrade cleanup only: cancel the obsolete alarm, never schedule a balance reminder. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static void schedule(Context context){
        PendingIntent old=PendingIntent.getBroadcast(context,11,new Intent(context,ReminderReceiver.class).setAction("cn.yueji.balance.CHECK_BALANCES"),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(old!=null){context.getSystemService(AlarmManager.class).cancel(old);old.cancel();}
        NotificationManager nm=context.getSystemService(NotificationManager.class);nm.cancel(1);nm.deleteNotificationChannel("balance-check");
    }
    @Override public void onReceive(Context context,Intent intent){schedule(context);if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){AppIcons.sync(context);FundRefreshJob.schedule(context);}}
}
