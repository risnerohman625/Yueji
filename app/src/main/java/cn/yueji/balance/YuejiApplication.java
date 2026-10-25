package cn.yueji.balance;

public final class YuejiApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();AppIcons.beginProcess(this);}
}
