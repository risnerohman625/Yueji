package cn.yueji.balance;

import android.app.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V478UiTest {
    final V470UiTest old=new V470UiTest();final YuejiTest t=old.t;
    @Before public void before(){old.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",28).commit();}
    @After public void after()throws Exception{old.after();}
    @Test public void automaticFeeStopsOnManualEditAndDelete(){t.start(old.sample());final EditText[] fields=new EditText[4];final AlertDialog[] dialog={null};t.ui(()->{LinearLayout form=t.app.col();for(int i=0;i<4;i++){fields[i]=new EditText(t.app);form.addView(fields[i]);}fields[0].setText("006431");TextView hint=new TextView(t.app);form.addView(hint);dialog[0]=new AlertDialog.Builder(t.app).setView(form).create();dialog[0].show();PurchaseFeeBinding binding=new PurchaseFeeBinding(t.app,fields[0],fields[1],fields[2],fields[3],hint);binding.start(dialog[0]);fields[2].setText("1000");assertEquals("0.30",fields[3].getText().toString());fields[2].setText("2000");assertEquals("0.60",fields[3].getText().toString());fields[3].setText("1.23");fields[2].setText("3000");assertEquals("1.23",fields[3].getText().toString());fields[3].setText("");fields[2].setText("4000");assertEquals("",fields[3].getText().toString());dialog[0].dismiss();});}
    @Test public void notificationUsesDedicatedTwoPetalResource(){t.start(old.sample());t.ui(()->{t.app.getPreferences(0).edit().putInt("appIcon",2).commit();assertEquals(R.drawable.ic_stat_two_petals,AppIcons.small(t.app));Notification.Builder b=new Notification.Builder(t.app,"test");Notification n=NotificationArt.style(t.app,b,"测试",0).build();assertEquals(android.graphics.drawable.Icon.TYPE_BITMAP,n.getSmallIcon().getType());});}
    @Test public void dailyPlanAndPeriodCountSave(){t.start(old.sample());t.ui(()->t.app.fundScreens.configurePlan(t.app.data.funds.get(0)));old.fill("每期金额","100");old.fill("计划总期数（0为不限）","20");assertEquals("fund_purchase",t.app.page);assertNotNull(t.findText(t.app.body,"保存定投并补齐已到期记录"));old.shot("v478-daily-plan.png");}
}
