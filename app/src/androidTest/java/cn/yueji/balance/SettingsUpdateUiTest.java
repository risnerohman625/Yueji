package cn.yueji.balance;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class SettingsUpdateUiTest {
 final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
 @Before public void before(){h.before();t.context.getSharedPreferences("updates",0).edit().putString("guideRead",UsageGuideActivity.REVISION).putLong("lastOpenedBuild",22).commit();}
 @After public void after()throws Exception{h.after();}
 @Test public void nestedManagementHasIndependentChildren(){t.start(t.sample());t.ui(()->{t.app.openTab(MainActivity.TAB_SETTINGS);View title=t.findText(t.app.body,"数据导入及导出备份管理");assertNotNull(title);((View)title.getParent().getParent()).performClick();assertEquals(View.VISIBLE,t.app.sectionContents.get("dataManagement").getVisibility());View child=t.findText(t.app.body,"收支报销数据管理");((View)child.getParent()).performClick();assertNotNull(t.findText(t.app.body,"导入收支报销"));assertEquals(View.VISIBLE,t.app.sectionContents.get("dataManagement").getVisibility());assertNotNull(t.findText(t.app.body,"软件使用说明"));assertNotNull(t.findText(t.app.body,"Yueji V4.7.3"));assertNotNull(t.findText(t.app.body,"检查更新"));});}
 @Test public void versionOrderingAndApkProviderAreRestricted()throws Exception{assertTrue(AppUpdates.compare("v4.8.0","4.7.1")>0);assertEquals(0,AppUpdates.compare("v4.7.1","4.7.1"));assertTrue(AppUpdates.compare("4.7.0","4.7.1")<0);UpdateApkProvider provider=new UpdateApkProvider();try{provider.check(android.net.Uri.parse("content://cn.yueji.balance.updates/ledger.db"));fail();}catch(java.io.FileNotFoundException expected){}}
 @Test public void requiredGuideCannotCloseBeforeTenSeconds(){t.start(t.sample());t.ui(()->t.app.startActivity(new Intent(t.app,UsageGuideActivity.class).putExtra("required",true)));assertTrue(t.device.wait(Until.hasObject(By.text("软件使用说明")),3000));t.device.pressBack();assertTrue(t.device.hasObject(By.text("软件使用说明")));SystemClock.sleep(10500);UiObject2 done=t.device.wait(Until.findObject(By.text("我已阅读，开始使用")),3000);assertNotNull(done);done.click();assertEquals(UsageGuideActivity.REVISION,t.context.getSharedPreferences("updates",0).getString("guideRead",""));}
}
