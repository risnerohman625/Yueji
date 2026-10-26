package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V462UiTest {
    final V43UiTest h=new V43UiTest();final YuejiTest t=h.t;
    final Uri provider=Uri.parse("content://cn.yueji.balance.test.screenshots");
    @Before public void before(){h.before();t.dir=new File(t.context.getExternalFilesDir(null),"qa-v462");t.dir.mkdirs();}
    @After public void after()throws Exception{h.after();}
    void shot(String name){t.instrument.waitForIdleSync();SystemClock.sleep(250);t.device.takeScreenshot(new File(t.dir,name));}
    void tap(String text){
        for(int attempt=0;attempt<3;attempt++){
            UiObject2 v=t.device.wait(Until.findObject(By.text(text)),4000);assertNotNull(text,v);
            try{v.click();t.instrument.waitForIdleSync();return;}catch(StaleObjectException e){if(attempt==2)throw e;SystemClock.sleep(200);}
        }
    }







    @Test public void importManagementAccordionIndividualAndWholeBatchDeletion(){
        Ledger.Data d=new Ledger.Data(),before=LedgerStore.decode(LedgerStore.encode(d));
        for(int i=0;i<2;i++){Ledger.Entry e=new Ledger.Entry(d.nextId(),true,10000,LocalDateTime.of(2026,10,19,8,i),"工资"+i,"",0);e.category="工资";d.entries.add(e);}
        ImportJournal.Batch a=ImportJournal.record(before,d,ImportJournal.RECORDS,"表格导入");before=LedgerStore.decode(LedgerStore.encode(d));
        Ledger.Entry third=new Ledger.Entry(d.nextId(),false,30000,LocalDateTime.of(2026,10,20,8,0),"差旅","",0);third.kind=Ledger.REIMBURSE;d.entries.add(third);
        ImportJournal.Batch b=ImportJournal.record(before,d,ImportJournal.RECORDS,"表格导入");
        t.start(d);t.ui(()->t.app.go("import_records"));
        t.ui(()->h.desc(t.app.body,"展开导入批次"+a.id).performClick());
        t.ui(()->{assertNotNull(h.desc(t.app.body,"导入明细entry"+a.items.get(0).ref));assertNull(h.desc(t.app.body,"导入明细entry"+b.items.get(0).ref));});
        t.ui(()->h.desc(t.app.body,"展开导入批次"+b.id).performClick());
        t.ui(()->{assertNull(h.desc(t.app.body,"导入明细entry"+a.items.get(0).ref));assertNotNull(h.desc(t.app.body,"导入明细entry"+b.items.get(0).ref));});shot("v462-record-import-accordion.png");
        t.ui(()->h.desc(t.app.body,"删除导入明细entry"+b.items.get(0).ref).performClick());tap("删除");assertEquals(2,t.store.load().entries.size());
        t.ui(()->h.desc(t.app.body,"删除导入批次"+a.id).performClick());tap("删除");assertTrue(t.store.load().entries.isEmpty());assertTrue(ImportJournal.visible(t.store.load(),ImportJournal.RECORDS).isEmpty());
    }
    @Test public void fundNamesFilterAndWheelStayCompact()throws Exception{
        Ledger.Data d=t.sample();d.funds.get(0).name="平安元泓30天滚动持有短债债券A";d.funds.get(1).name="这是一条特别特别长的产品完整名称用于测试超过最小字体以后恢复默认字体并且能够横向滑动查看全部内容的基金A";
        d.history.add(new Ledger.HistoryMonth(YearMonth.of(2022,1),10000,LocalDateTime.of(2022,2,1,0,0)));
        t.start(d);t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));SystemClock.sleep(150);
        t.ui(()->{
            FundNameView name=(FundNameView)h.desc(t.app.body,"产品全名："+d.funds.get(0).name);assertEquals(1,name.label.getMaxLines());assertFalse(name.scrolls);
            assertNotNull(h.desc(t.app.body,"产品分类").getBackground());assertNull(t.findText(t.app.body,"000001"));
        });shot("v462-fund-cards.png");
        FundNameView[] longName=new FundNameView[1];int[] titlePosition=new int[2],titleSize=new int[2];
        t.ui(()->{longName[0]=(FundNameView)h.desc(t.app.body,"产品全名："+d.funds.get(1).name);assertTrue(longName[0].scrolls);longName[0].getLocationOnScreen(titlePosition);titleSize[0]=longName[0].getWidth();titleSize[1]=longName[0].getHeight();});
        t.device.swipe(titlePosition[0]+titleSize[0]-20,titlePosition[1]+titleSize[1]/2,titlePosition[0]+20,titlePosition[1]+titleSize[1]/2,18);
        t.ui(()->assertTrue("title itself scrolls rather than sliding the card",longName[0].getScrollX()>0));shot("v462-long-name-scrolled.png");
        t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
        t.ui(()->{YearStepper picker=h.first(t.app.body,YearStepper.class);picker.performClick();assertTrue(picker.popup.isShowing());assertEquals(picker.getWidth(),picker.popup.getWidth());assertEquals(t.app.dp(114),picker.popup.getHeight());});
        int[] position=new int[2],size=new int[2];
        t.ui(()->{YearStepper picker=h.first(t.app.body,YearStepper.class);picker.wheel.getLocationOnScreen(position);size[0]=picker.wheel.getWidth();size[1]=picker.wheel.getHeight();});
        int x=position[0]+size[0]/2,y=position[1]+size[1]/2;
        t.device.swipe(x,y-35,x,y+75,18);SystemClock.sleep(750);
        t.ui(()->assertTrue("a short physical swipe changes years",h.first(t.app.body,YearStepper.class).year()<LocalDate.now().getYear()));shot("v462-wheel-after-swipe.png");
    }
    @Test public void percentagesAreAccentBoldAndWheelIsRoundedTranslucentInBothThemes(){
        Ledger.Data d=t.sample();d.history.add(new Ledger.HistoryMonth(YearMonth.of(2022,1),10000,LocalDateTime.of(2022,2,1,0,0)));
        BankLedger.Draft bank=new BankLedger.Draft();bank.name="银行理财测试产品";bank.balance=100000L;bank.at=LocalDateTime.of(2026,10,22,10,0);BankLedger.apply(d,0,bank,false,null);
        t.start(d);
        for(int appearance:new int[]{1,2}){
            t.ui(()->{t.app.getPreferences(0).edit().putInt("appearance",appearance).putInt("productFilter",0).commit();t.app.openTab(MainActivity.TAB_FUNDS);});
            t.ui(()->{
                List<TextView> texts=new ArrayList<>();collect(t.app.body,texts);int emphasized=0;
                for(TextView view:texts)if("holding-percentage".equals(view.getTag())&&view.getText().toString().startsWith("持仓占比")){
                    assertTrue(view.getText() instanceof android.text.Spanned);android.text.Spanned text=(android.text.Spanned)view.getText();
                    android.text.style.ForegroundColorSpan[] colors=text.getSpans(0,text.length(),android.text.style.ForegroundColorSpan.class);
                    assertEquals(1,colors.length);assertEquals(Ui.ACCENT_DEEP,colors[0].getForegroundColor());assertNotEquals(Ui.MUTED,colors[0].getForegroundColor());
                    assertEquals(0,text.getSpanStart(colors[0]));assertTrue(text.getSpanEnd(colors[0])<text.length());assertEquals(1,text.getSpans(0,text.length(),android.text.style.StyleSpan.class).length);emphasized++;
                }
                assertEquals("two funds and the bank use the same highlighted holding percentage",3,emphasized);
            });shot("v462-percentage-theme-"+appearance+".png");
            t.ui(()->t.app.openTab(MainActivity.TAB_FLOW));
            t.ui(()->{
                YearStepper picker=h.first(t.app.body,YearStepper.class);picker.performClick();picker.wheel.setValue(2025);picker.select(2025);
                assertTrue(picker.popup.getBackground() instanceof android.graphics.drawable.ColorDrawable);
                assertEquals(android.graphics.Color.TRANSPARENT,((android.graphics.drawable.ColorDrawable)picker.popup.getBackground()).getColor());
                View panel=picker.popup.getContentView();assertTrue(panel.getClipToOutline());
                android.graphics.drawable.GradientDrawable glass=(android.graphics.drawable.GradientDrawable)panel.getBackground();
                assertTrue(glass.getCornerRadius()>=t.app.dp(18));assertEquals(android.graphics.PixelFormat.TRANSLUCENT,glass.getOpacity());
                assertEquals("hide the original year to avoid double text through the glass",0f,picker.getAlpha(),0f);
            });
            SystemClock.sleep(200);
            t.ui(()->{
                YearStepper picker=h.first(t.app.body,YearStepper.class);int[] anchor=new int[2],panel=new int[2];
                picker.getLocationOnScreen(anchor);picker.popup.getContentView().getLocationOnScreen(panel);
                assertEquals("glass wheel stays anchored to the year button",anchor[0],panel[0],t.app.dp(1));
                assertEquals(anchor[1]+picker.getHeight()/2,panel[1]+picker.popup.getHeight()/2,t.app.dp(2));
            });shot("v462-glass-wheel-theme-"+appearance+".png");
            t.ui(()->{YearStepper picker=h.first(t.app.body,YearStepper.class);picker.popup.dismiss();assertEquals(1f,picker.getAlpha(),0f);});
        }
    }
    void collect(View view,List<TextView> texts){if(view instanceof TextView)texts.add((TextView)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)collect(group.getChildAt(n),texts);}}
    @Test public void xlsxConfirmationRecordsRealBatchAndPreservesChangesMadeSincePreview(){
        Xlsx.Sheet expense=new Xlsx.Sheet("支出").add(HistoryImport.EXPENSE_HEADER.split(",")).add("月支出","2026/9","123.45","","","","已排除报销");
        Xlsx.Sheet income=new Xlsx.Sheet("收入").add(HistoryImport.INCOME_HEADER.split(",")).add("工资","2026/10/19","1000.00","","测试工资");
        byte[] file=Xlsx.write(Arrays.asList(expense,income));Ledger.Data blank=new Ledger.Data();blank.settings.autoRefresh=false;t.start(blank);
        t.ui(()->{t.app.pendingImport=HistoryImport.preview(file,t.store.load());t.app.go("import_preview");});
        Ledger.Data latest=t.store.load();Ledger.Entry manual=new Ledger.Entry(latest.nextId(),false,20000,LocalDateTime.of(2026,10,20,0,0),"预览后新建的手工记录","",0);latest.entries.add(manual);t.store.save(latest);
        tap("确认导入");Ledger.Data saved=t.store.load();assertEquals(2,saved.entries.size());assertEquals(1,saved.history.size());
        assertEquals(1,ImportJournal.visible(saved,ImportJournal.RECORDS).size());ImportJournal.Batch batch=ImportJournal.visible(saved,ImportJournal.RECORDS).get(0);assertEquals(2,batch.items.size());
        t.ui(()->{t.app.go("import_records");t.app.openImportBatch=batch.id;t.app.render();});shot("v462-real-xlsx-batch.png");
        t.ui(()->h.desc(t.app.body,"删除导入批次"+batch.id).performClick());tap("删除");
        assertEquals(1,t.store.load().entries.size());assertEquals(manual.id,t.store.load().entries.get(0).id);assertTrue(t.store.load().history.isEmpty());
    }
}
