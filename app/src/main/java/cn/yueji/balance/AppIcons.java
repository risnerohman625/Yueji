package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import java.io.*;
import android.view.*;
import android.widget.*;

/** Launcher aliases and all app-controlled icon surfaces share one persisted choice. */
final class AppIcons {
    static final String[] NAMES={"经典账本","月亮与金点","双花瓣"};
    static final String[] ALIASES={"IconClassic","IconMoon","IconBloom"};
    static final int DEFAULT=2;
    static int selected(Context context){int value=context.getSharedPreferences("MainActivity",0).getInt("appIcon",DEFAULT);return value>=0&&value<3?value:DEFAULT;}
    static int desired(Context context){int value=context.getSharedPreferences("MainActivity",0).getInt("pendingIcon",selected(context));return value>=0&&value<3?value:DEFAULT;}
    static File custom(Context context,boolean pending){return new File(context.getFilesDir(),pending?"custom-icon-pending.png":"custom-icon.png");}
    static void beginProcess(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences("MainActivity",0);
        if(prefs.contains("pendingIcon")){
            int choice=desired(context);
            prefs.edit().putInt("appIcon",choice).remove("pendingIcon").commit();
        }
        if(prefs.getInt("appIcon",DEFAULT)==3)prefs.edit().putInt("appIcon",DEFAULT).commit();
        sync(context);NotificationArt.restyle(context);
    }
    static int resource(int choice){return choice==0?R.mipmap.icon_classic:choice==1?R.mipmap.icon_moon:R.mipmap.icon_petals;}
    static int small(Context context){int choice=selected(context);return choice==0?R.drawable.ic_stat_classic:choice==1?R.drawable.ic_stat_moon:R.drawable.ic_stat_petals;}
    static Drawable drawable(Context context){
        return context.getDrawable(resource(selected(context)));
    }
    static Bitmap bitmap(Context context,int size){return bitmap(context,selected(context),size);}
    static Bitmap bitmap(Context context,int choice,int size){
        Drawable icon=context.getDrawable(resource(choice));Bitmap result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        icon.setBounds(0,0,size,size);icon.draw(new Canvas(result));return result;
    }
    static ComponentName component(Context c,int choice){return new ComponentName(c.getPackageName(),c.getPackageName()+"."+ALIASES[choice]);}
    static void sync(Context context){
        PackageManager manager=context.getPackageManager();int selected=selected(context);if(selected==3)selected=DEFAULT;
        if(Build.VERSION.SDK_INT>=33){
            java.util.List<PackageManager.ComponentEnabledSetting> settings=new java.util.ArrayList<>();
            for(int i=0;i<3;i++){
                int state=i==selected?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                if(manager.getComponentEnabledSetting(component(context,i))!=state)settings.add(new PackageManager.ComponentEnabledSetting(component(context,i),state,PackageManager.DONT_KILL_APP));
            }
            if(!settings.isEmpty())manager.setComponentEnabledSettings(settings);
        }else{
            // Enable the target first so no intermediate state loses every launcher entry.
            for(int i:new int[]{selected,(selected+1)%3,(selected+2)%3}){
                int state=i==selected?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                if(manager.getComponentEnabledSetting(component(context,i))!=state)manager.setComponentEnabledSetting(component(context,i),state,PackageManager.DONT_KILL_APP);
            }
        }
    }
    static void select(MainActivity activity,int choice){
        if(choice<0||choice>=3)throw new IllegalArgumentException("未知应用图标");
        if(!activity.getSharedPreferences("MainActivity",0).edit().putInt("pendingIcon",choice).commit())throw new IllegalStateException("图标偏好保存失败");
    }
    static void apply(Activity activity){
        activity.setTitle("Yueji");activity.getWindow().setIcon(resource(selected(activity)));
        activity.setTaskDescription(new ActivityManager.TaskDescription("Yueji",bitmap(activity,192),0xFF7354B1));
    }
    static AlertDialog.Builder dialog(Context context){return new AlertDialog.Builder(context).setIcon(drawable(context));}
    static void toast(MainActivity m,String message){
        LinearLayout row=m.row();row.setPadding(m.dp(14),m.dp(10),m.dp(18),m.dp(10));
        row.setBackground(Ui.roundStroke(Ui.ACCENT_SOFT,Ui.BORDER,m.dp(26),m.dp(1)));row.setElevation(m.dp(5));
        ImageView icon=new ImageView(m);icon.setImageDrawable(drawable(m));row.addView(icon,new LinearLayout.LayoutParams(m.dp(28),m.dp(28)));
        TextView text=m.text(message,13,Ui.INK,false);text.setMaxWidth(m.getResources().getDisplayMetrics().widthPixels-m.dp(120));text.setPadding(m.dp(10),0,0,0);row.addView(text);
        Toast toast=new Toast(m);toast.setView(row);toast.setDuration(Toast.LENGTH_SHORT);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,m.dp(90));toast.show();
    }
    static void page(MainActivity m){
        m.top("更换图标",null,null);m.body.addView(m.action("更换图标",()->show(m)));show(m);
    }
    static void show(MainActivity m){
        if(m.iconChooser!=null&&m.iconChooser.isShowing())return;
        LinearLayout content=m.col(),row=m.row();content.setPadding(m.dp(16),m.dp(20),m.dp(16),m.dp(8));
        row.setContentDescription("应用图标选择");content.addView(row);
        for(int i=0;i<3;i++){
            ImageView preview=tile(m,i,m.getDrawable(resource(i)));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,m.dp(88),1);params.leftMargin=params.rightMargin=m.dp(4);row.addView(preview,params);
        }
        m.gap(content,14);TextView hint=m.text("已选图标在退出并重新打开应用后生效",12,Ui.MUTED,false);hint.setGravity(Gravity.CENTER);content.addView(hint);
        m.iconChooser=new AlertDialog.Builder(m).setView(content).setPositiveButton("完成",null).create();m.iconChooser.show();
    }
    static ImageView tile(MainActivity m,int choice,Drawable icon){
        ImageView view=new ImageView(m);view.setImageDrawable(icon);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setPadding(m.dp(12),m.dp(12),m.dp(12),m.dp(12));
        view.setBackground(Ui.roundStroke(Ui.ACCENT_TINT,desired(m)==choice?Ui.ACCENT:Ui.BORDER,m.dp(16),m.dp(desired(m)==choice?2.5f:1)));
        view.setSelected(desired(m)==choice);view.setContentDescription("应用图标选项"+choice);
        view.setOnClickListener(v->{try{
            select(m,choice);ViewGroup row=(ViewGroup)view.getParent();
            for(int i=0;i<row.getChildCount();i++){
                View tile=row.getChildAt(i);boolean checked=i==choice;tile.setSelected(checked);
                tile.setBackground(Ui.roundStroke(Ui.ACCENT_TINT,checked?Ui.ACCENT:Ui.BORDER,m.dp(16),m.dp(checked?2.5f:1)));
            }
        }catch(Exception e){m.error(e);}});return view;
    }
    private AppIcons(){}
}
