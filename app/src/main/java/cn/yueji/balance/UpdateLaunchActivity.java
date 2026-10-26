package cn.yueji.balance;

/** Stable installer Open target, independent of the user-selectable launcher aliases. */
public final class UpdateLaunchActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);
        android.content.Intent intent=new android.content.Intent(this,MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);finish();
    }
}
