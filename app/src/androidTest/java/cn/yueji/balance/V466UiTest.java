package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V466UiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v466");t.dir.mkdirs();}
    @After public void after()throws Exception{h.after();AppIcons.sync(t.context);t.context.getSystemService(NotificationManager.class).cancel(23);}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(220);t.device.takeScreenshot(new File(t.dir,name));}
    void tap(String text){UiObject2 v=t.device.wait(Until.findObject(By.text(text)),5000);assertNotNull(text,v);v.click();t.instrument.waitForIdleSync();}
    Ledger.Data timeline(){
        Ledger.Data d=t.sample();d.funds.clear();d.snapshots.clear();
        for(Ledger.Account a:d.accounts)a.created=LocalDateTime.parse("2026-01-01T00:00");
        for(int month=1;month<=10;month++)for(int day:new int[]{1,8,16,24}){
            Ledger.Snapshot s=new Ledger.Snapshot(month*100+day,LocalDate.of(2026,month,day).atTime(8,0),"");s.partial=true;
            long id=day==8||day==24?2:1;s.balances.put(id,id==1?1000000L+month*90000-day*1000:100000L+month*18000+day*350);d.snapshots.add(s);
        }
        d.sort();return d;
    }
    @Test public void defaultAccountsCycleInlineAndActionsKeepTheirOriginalPosition(){
        t.start(t.sample());View[] actions=new View[2];
        t.ui(()->{
            assertEquals(2,t.app.assetTab);assertNotNull(h.desc(t.app.body,"账户主账户"));
            actions[0]=t.findText(t.app.body,"＋ 添加账户");actions[1]=t.findText(t.app.body,"更新全部账户");
            h.desc(t.app.body,"下一个资产视图").performClick();assertEquals(0,t.app.assetTab);
            assertNotNull(h.first(t.app.body,AssetDonut.class));assertNotNull(t.findText(t.app.body,"占比最大"));assertNotNull(t.findText(t.app.body,"占比最小"));
            assertSame(actions[0],t.findText(t.app.body,"＋ 添加账户"));assertSame(actions[1],t.findText(t.app.body,"更新全部账户"));
        });shot("v466-assets-composition.png");
        t.ui(()->{
            h.desc(t.app.body,"下一个资产视图").performClick();assertEquals(1,t.app.assetTab);assertNotNull(h.first(t.app.body,AssetTrendChart.class));
            h.desc(t.app.body,"下一个资产视图").performClick();assertEquals(2,t.app.assetTab);assertNotNull(h.desc(t.app.body,"账户主账户"));
        });
    }
    @Test public void donutSelectionHidesLeadersAndExpiresAfterRelease(){
        Ledger.Data d=timeline();Ledger.Account tiny=new Ledger.Account(3,"极少现金","现金");tiny.opening=1;tiny.created=LocalDateTime.parse("2026-01-01T00:00");d.accounts.add(tiny);t.start(d);
        t.ui(()->h.desc(t.app.body,"下一个资产视图").performClick());AssetDonut donut=h.first(t.app.body,AssetDonut.class);
        int index=donut.slices.extreme(false);assertEquals(3f,donut.slices.sweeps[index],.01);
        int[] position=new int[2];t.ui(()->donut.getLocationOnScreen(position));double angle=Math.toRadians(donut.mid(index));
        int x=position[0]+Math.round(donut.cx()+(float)Math.cos(angle)*donut.radius()),y=position[1]+Math.round(donut.cy()+(float)Math.sin(angle)*donut.radius());
        t.device.click(x,y);t.instrument.waitForIdleSync();assertEquals(index,donut.selected);shot("v466-donut-tiny-selected.png");
        SystemClock.sleep(1800);assertEquals(index,donut.selected);SystemClock.sleep(1000);t.instrument.waitForIdleSync();assertEquals(-1,donut.selected);shot("v466-donut-leaders-restored.png");
    }
    @Test public void trendShowsTenNodesPansOnlyFromAxisAndCursorExpires(){
        t.start(timeline());t.ui(()->h.desc(t.app.body,"上一个资产视图").performClick());AssetTrendChart chart=h.first(t.app.body,AssetTrendChart.class);
        assertEquals(40,chart.points.size());assertEquals(chart.plotWidth()/10,chart.slot(),.1);shot("v466-trend-initial-ten-nodes.png");
        UiObject2 axis=t.device.findObject(By.desc("资产趋势横轴，左右滑动翻页"));assertNotNull(axis);Rect bounds=axis.getVisibleBounds();
        t.device.swipe(bounds.right-15,bounds.centerY(),bounds.left+15,bounds.centerY(),25);t.instrument.waitForIdleSync();SystemClock.sleep(350);assertTrue(chart.offset>0);float offset=chart.offset;
        int[] at=new int[2];AssetTrendChart initial=chart;t.ui(()->initial.getLocationOnScreen(at));
        t.device.swipe(at[0]+chart.getWidth()-25,at[1]+chart.getHeight()/2,at[0]+Math.round(chart.left())+10,at[1]+chart.getHeight()/2,25);t.instrument.waitForIdleSync();
        assertEquals(offset,chart.offset,.1);assertTrue(chart.selected>=0);shot("v466-trend-scrolled-cursor.png");
        SystemClock.sleep(2800);t.instrument.waitForIdleSync();assertEquals(-1,chart.selected);
        t.ui(()->h.desc(t.app.body,"资产趋势时间粒度").performClick());tap("按月");assertTrue(t.device.wait(Until.hasObject(By.text("按月 ▾")),3000));chart=h.first(t.app.body,AssetTrendChart.class);assertEquals(10,chart.points.size());assertEquals(1,chart.mode);shot("v466-trend-month.png");
        t.ui(()->h.desc(t.app.body,"资产趋势时间粒度").performClick());tap("按年");assertTrue(t.device.wait(Until.hasObject(By.text("按年 ▾")),3000));chart=h.first(t.app.body,AssetTrendChart.class);assertEquals(1,chart.points.size());assertEquals(2,chart.mode);shot("v466-trend-year.png");
    }
    @Test public void debtCanBeDraggedAheadOfTheOtherThreeAndRecordButtonStillWorks(){
        t.start(timeline());t.ui(()->{t.app.getPreferences(0).edit().putString("flowOrder","0,1,2").commit();t.app.openTab(MainActivity.TAB_FLOW);});
        Rect from=t.device.findObject(By.desc("入口负债")).getVisibleBounds(),to=t.device.findObject(By.desc("入口收入")).getVisibleBounds();
        from.right=from.left+from.width()/2;to.right=to.left+to.width()/2;holdDrag(from,to);
        t.ui(()->assertArrayEquals(new int[]{3,0,1,2},t.app.flowOrder()));shot("v466-debt-dragged-to-first.png");
        t.ui(()->{t.app.openTab(MainActivity.TAB_ACCOUNTS);t.app.openTab(MainActivity.TAB_FLOW);assertArrayEquals(new int[]{3,0,1,2},t.app.flowOrder());});
        t.device.findObject(By.desc("记录负债")).click();assertTrue(t.device.wait(Until.hasObject(By.text("记录负债")),5000));t.device.pressBack();
    }
    @Test public void builtInDialogDoesNotExitAndRestartRefreshesExistingNotification()throws Exception{
        t.start(timeline());t.context.getSharedPreferences("MainActivity",0).edit().putInt("appIcon",2).remove("pendingIcon").commit();t.ui(()->{AppIcons.sync(t.context);t.app.testNotification();AppIcons.show(t.app);});
        View chooser=t.app.iconChooser.getWindow().getDecorView();
        t.ui(()->{
            ViewGroup row=(ViewGroup)h.desc(chooser,"应用图标选择");assertEquals(3,row.getChildCount());
            assertNull(t.findText(chooser,"＋ 添加图标"));h.desc(chooser,"应用图标选项0").performClick();
            assertTrue(h.desc(chooser,"应用图标选项0").isSelected());assertFalse(t.app.isFinishing());assertEquals(2,AppIcons.selected(t.context));
            assertEquals(R.drawable.ic_stat_petals,currentNotice().getSmallIcon().getResId());
        });shot("v466-icon-dialog-pending.png");tap("完成");
        t.ui(()->t.app.finish());SystemClock.sleep(200);t.start(timeline());assertEquals(0,AppIcons.selected(t.context));assertEquals(R.drawable.ic_stat_classic,currentNotice().getSmallIcon().getResId());
        t.ui(()->AppIcons.show(t.app));t.ui(()->h.desc(t.app.iconChooser.getWindow().getDecorView(),"应用图标选项2").performClick());tap("完成");
        t.ui(()->t.app.finish());SystemClock.sleep(200);t.start(timeline());
        assertEquals(2,AppIcons.selected(t.context));assertEquals(R.drawable.ic_stat_petals,currentNotice().getSmallIcon().getResId());
        assertEquals(R.mipmap.icon_petals,t.context.getPackageManager().getApplicationInfo(t.context.getPackageName(),0).icon);
        assertNull(currentNotice().getLargeIcon());assertNull(currentNotice().contentView);
        t.device.openNotification();SystemClock.sleep(400);shot("v466-petals-system-notification.png");t.device.pressBack();
        t.ui(()->AppIcons.dialog(t.app).setTitle("图标同步检查").setMessage("双花瓣图标").setPositiveButton("完成",null).show());shot("v466-petals-dialog.png");tap("完成");
    }
    Notification currentNotice(){
        for(android.service.notification.StatusBarNotification n:t.context.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==23)return n.getNotification();throw new AssertionError("missing notification");
    }
    void holdDrag(Rect from,Rect to){
        long start=SystemClock.uptimeMillis();send(start,MotionEvent.ACTION_DOWN,from.centerX(),from.centerY());SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+160);
        for(int i=1;i<=16;i++){send(start,MotionEvent.ACTION_MOVE,from.centerX()+(to.centerX()-from.centerX())*i/16f,from.centerY()+(to.centerY()-from.centerY())*i/16f);SystemClock.sleep(24);}
        send(start,MotionEvent.ACTION_UP,to.centerX(),to.centerY());t.instrument.waitForIdleSync();
    }
    void send(long start,int action,float x,float y){MotionEvent e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);t.instrument.sendPointerSync(e);e.recycle();}
    @Test public void assetPanelsFitShortTallLargeFontAndDarkScreens()throws Exception{
        for(String[] config:new String[][]{{"1080x1920","420","1.0"},{"1260x2800","560","1.3"},{"1080x2400","420","1.0"}}){
            t.device.executeShellCommand("wm size "+config[0]);t.device.executeShellCommand("wm density "+config[1]);t.device.executeShellCommand("settings put system font_scale "+config[2]);t.start(timeline());
            for(int index=0;index<3;index++){
                final int selection=index;t.ui(()->{t.app.assetTab=selection;t.app.render();});SystemClock.sleep(150);
                View content=h.desc(t.app.body,"资产视图内容");assertTrue(content.getHeight()>t.app.dp(190));
                assertTrue(t.findText(t.app.body,"更新全部账户").getWidth()>0);assertTrue(h.desc(t.app.body,"下一个资产视图").getWidth()>=t.app.dp(24));
                int[] arrowAt=new int[2],addAt=new int[2];View arrow=h.desc(t.app.body,"下一个资产视图"),add=t.findText(t.app.body,"＋ 添加账户");
                arrow.getLocationOnScreen(arrowAt);add.getLocationOnScreen(addAt);assertTrue("selector never overlaps account actions",arrowAt[0]+arrow.getWidth()<=addAt[0]);
                AssetTrendChart chart=h.first(t.app.body,AssetTrendChart.class);if(chart!=null)assertTrue(chart.getHeight()>t.app.dp(105));
                shot("v466-panel-"+index+"-"+config[0]+"-"+config[1]+"-"+config[2]+".png");
            }
        }
        t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",2).commit();t.app.assetTab=0;t.app.render();});shot("v466-composition-dark.png");
        t.ui(()->{t.app.assetTab=1;t.app.render();});shot("v466-trend-dark.png");
    }
}
