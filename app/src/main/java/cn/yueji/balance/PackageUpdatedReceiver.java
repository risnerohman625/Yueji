package cn.yueji.balance;

import android.content.*;

/** Post-upgrade scheduling only: never launches UI from a package-replaced broadcast. */
public final class PackageUpdatedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){if(!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;PendingResult result=goAsync();new Thread(()->{try{FundRefreshJob.schedule(context);ReminderReceiver.schedule(context);}finally{result.finish();}},"package-updated").start();}
}
