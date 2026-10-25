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
    void waitScan()throws Exception{
        for(int n=0;n<360&&(t.app.fundScreenshot.busy||!t.app.page.equals("fund_scan_preview"));n++)SystemClock.sleep(250);
        assertFalse("OCR/name lookup must finish",t.app.fundScreenshot.busy);
        shot("v462-scan-result.png");t.device.dumpWindowHierarchy(new File(t.dir,"scan-result.xml"));
        assertEquals("fund_scan_preview",t.app.page);
        assertFalse(t.app.fundScreenshot.rows.isEmpty());
    }
    void pickThroughSystemDocuments()throws Exception{
        t.ui(()->t.app.openTab(MainActivity.TAB_FUNDS));tap("＋ 添加基金");tap("自动识别");
        UiObject2 file=t.device.wait(Until.findObject(By.descStartsWith("01-基金买入详情.jpg")),1800);
        if(file==null){
            UiObject2 drawer=t.device.findObject(By.desc("Show roots"));
            if(drawer==null)drawer=t.device.findObject(By.desc("显示根目录"));
            if(drawer==null)drawer=t.device.findObject(By.desc("显示位置"));
            if(drawer!=null)drawer.click();
            tap("Yueji 4.6.2 测试截图");
        }
        file=t.device.wait(Until.findObject(By.descStartsWith("01-基金买入详情.jpg")),6000);
        if(file==null)file=t.device.findObject(By.text("01-基金买入详情.jpg"));
        if(file==null){shot("v462-picker-problem.png");t.device.dumpWindowHierarchy(new File(t.dir,"picker-problem.xml"));}
        assertNotNull("supplied purchase screenshot must be selectable through Android DocumentsUI",file);shot("v462-system-image-picker.png");
        for(int retry=0;retry<3;retry++){
            try{file=t.device.findObject(By.descStartsWith("01-基金买入详情.jpg"));if(file==null)file=t.device.findObject(By.text("01-基金买入详情.jpg"));assertNotNull(file);file.click();break;}
            catch(StaleObjectException e){if(retry==2)throw e;SystemClock.sleep(200);}
        }
        t.instrument.waitForIdleSync();waitScan();
    }
    void confirmIdentityIfNeeded()throws Exception{
        FundRecognition.Row row=t.app.fundScreenshot.rows.get(0);
        try(PrintWriter out=new PrintWriter(new File(t.dir,"live-identity.txt"),"UTF-8")){
            out.println("recognized="+row.name+" code="+row.code+" verified="+row.verified+" review="+row.needsReview);
            out.println("problem="+row.problem);for(FundClient.Match match:row.matches)out.println(match.code+" "+match.name);
        }
        assertEquals(100000,row.amount);assertEquals(60,row.fee);assertEquals(LocalDateTime.of(2026,10,19,1,40),row.at);assertFalse(row.problem,row.needsReview);
        assertEquals("平安元泓30天滚动持有短债债券A",row.name);
        if(!row.verified){
            assertFalse("live service must produce candidates, not invented codes",row.matches.isEmpty());
            FundClient.Match official=null;for(FundClient.Match match:row.matches)if(match.name.contains("元泓30天")&&match.name.endsWith("A")){official=match;break;}
            assertNotNull("correct product/share class",official);tap("核对并修改");
            t.device.findObject(By.desc("识别基金名称")).setText(official.name);t.device.findObject(By.desc("识别基金代码")).setText(official.code);
            tap("联网核验并更新");
            for(int n=0;n<160&&!row.verified;n++)SystemClock.sleep(250);
            assertTrue("real network code verification",row.verified);
        }
    }
    void commit()throws Exception{
        tap("完成导入");
        for(int n=0;n<260&&t.store.load().funds.isEmpty();n++)SystemClock.sleep(250);
        if(t.device.hasObject(By.text("知道了")))tap("知道了");
        assertEquals(1,t.store.load().funds.size());Ledger.Fund fund=t.store.load().funds.get(0);
        assertEquals(1,fund.buys.size());assertEquals(100000,fund.buys.get(0).amount);assertEquals(60,fund.buys.get(0).fee);
        assertEquals(LocalDateTime.of(2026,10,19,1,40),fund.buys.get(0).at);assertEquals(1,ImportJournal.visible(t.store.load(),ImportJournal.FUNDS).size());
        shot("v462-import-saved-fund.png");
    }
    @Test public void actualSystemPickerOcrLiveMatchSaveDuplicateDeleteAndReimport()throws Exception{
        t.context.getContentResolver().call(provider,"picker-reset",null,null);Ledger.Data blank=new Ledger.Data();blank.settings.autoRefresh=false;t.start(blank);
        pickThroughSystemDocuments();confirmIdentityIfNeeded();shot("v462-original-review.png");commit();
        for(int n=0;n<40&&t.context.getContentResolver().call(provider,"picker-count",null,null).getInt("count")==0;n++)SystemClock.sleep(100);
        assertEquals("only committed source document deleted",1,t.context.getContentResolver().call(provider,"picker-count",null,null).getInt("count"));
        t.context.getContentResolver().call(provider,"picker-reset",null,null);pickThroughSystemDocuments();confirmIdentityIfNeeded();
        t.ui(()->{FundRecognition.Row row=t.app.fundScreenshot.rows.get(0);assertTrue(row.added);assertFalse(row.selected);assertNotNull(t.findText(t.app.body,"已添加"));});shot("v462-original-duplicate.png");
        assertEquals(1,t.store.load().funds.get(0).buys.size());assertEquals(0,t.context.getContentResolver().call(provider,"picker-count",null,null).getInt("count"));
        t.ui(()->{t.app.fundScreenshot.rows.clear();t.app.go("import_funds");t.app.openImportBatch=t.store.load().imports.get(0).id;t.app.render();});shot("v462-import-management-funds.png");
        t.ui(()->h.desc(t.app.body,"删除导入批次"+t.store.load().imports.get(0).id).performClick());tap("删除");assertTrue(t.store.load().funds.isEmpty());
        pickThroughSystemDocuments();confirmIdentityIfNeeded();assertFalse(t.app.fundScreenshot.rows.get(0).added);commit();
    }
    @Test public void compressedImageAndRedactedReferenceStillProduceReviewableFields()throws Exception{
        t.start(new Ledger.Data());
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("fund-purchase-detail.jpg")){
            Bitmap original=BitmapFactory.decodeStream(in);
            for(int width:new int[]{720,480}){
                Bitmap resized=Bitmap.createScaledBitmap(original,width,original.getHeight()*width/original.getWidth(),true);
                File file=new File(t.context.getCacheDir(),"compressed-"+width+".jpg");try(OutputStream out=new FileOutputStream(file)){resized.compress(Bitmap.CompressFormat.JPEG,65,out);}resized.recycle();
                FundRecognition.Row row=ocr(file);assertEquals(100000,row.amount);assertEquals(LocalDateTime.of(2026,10,19,1,40),row.at);
                assertFalse("fee/time cannot default silently",row.fee==0&&!row.needsReview);file.delete();
            }original.recycle();
        }
        File standard=new File(t.context.getCacheDir(),"redacted-standard.jpg");
        try(InputStream in=t.context.getAssets().open("fund-purchase-standard.jpg");OutputStream out=new FileOutputStream(standard)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        FundRecognition.Row row=ocr(standard);assertEquals(100000,row.amount);assertEquals(60,row.fee);assertFalse(row.needsReview);standard.delete();
    }
    FundRecognition.Row ocr(File file)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<List<FundRecognition.Line>> result=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();
        t.ui(()->ScreenshotOcr.recognize(t.context,Uri.fromFile(file),lines->{result.set(lines);done.countDown();},e->{error.set(e);done.countDown();}));
        assertTrue(done.await(40,TimeUnit.SECONDS));if(error.get()!=null)throw error.get();
        try(PrintWriter out=new PrintWriter(new File(t.dir,file.getName()+".ocr.txt"),"UTF-8")){for(FundRecognition.Line line:result.get())out.println(line.top+","+line.left+": "+line.text);}
        return FundDetailRecognition.parse(result.get());
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
