package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.service.notification.StatusBarNotification;

/** Let the phone render its own app name, icon and timestamp; the payload contains only the message. */
final class NotificationArt {
    static Notification.Builder style(Context c,Notification.Builder builder,String text,long time){
        // Android 17 can otherwise reuse a cached launcher icon on the notification card.
        android.os.Bundle artwork=new android.os.Bundle();artwork.putBoolean("android.app.preferSmallIcon",true);builder.addExtras(artwork);
        return builder.setSmallIcon(AppIcons.notificationIcon(c)).setLargeIcon((android.graphics.Bitmap)null)
            .setContentTitle(null).setContentText(text).setWhen(time).setShowWhen(true).setColor(0xFF7C5CC4)
            .setCustomContentView(null).setCustomBigContentView(null).setCustomHeadsUpContentView(null)
            .setStyle(new Notification.BigTextStyle().bigText(text));
    }
    static void restyle(Context context){
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        for(StatusBarNotification active:manager.getActiveNotifications()){
            Notification old=active.getNotification();String text=old.extras.getCharSequence(Notification.EXTRA_TEXT,"").toString();
            Notification.Builder builder=new Notification.Builder(context,old.getChannelId()).setContentIntent(old.contentIntent)
                .setAutoCancel((old.flags&Notification.FLAG_AUTO_CANCEL)!=0).setCategory(old.category).setVisibility(old.visibility);
            manager.notify(active.getTag(),active.getId(),style(context,builder,text,old.when).setOnlyAlertOnce(true).build());
        }
    }
    private NotificationArt(){}
}
