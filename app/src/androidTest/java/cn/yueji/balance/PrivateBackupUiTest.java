package cn.yueji.balance;

import java.nio.file.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Optional local-only fixture; no ledger payload is included in sources or APK assets. */
@RunWith(AndroidJUnit4.class)
public class PrivateBackupUiTest {
    final V4711UiTest old=new V4711UiTest();final YuejiTest t=old.t;
    @Before public void before(){old.before();t.dir=new File(t.context.getExternalFilesDir(null),"private-qa");t.dir.mkdirs();}
    @After public void after()throws Exception{old.after();}
    Ledger.Data fixture(String name)throws Exception{File file=new File(t.context.getExternalFilesDir(null),name);Assume.assumeTrue("External local fixture is optional",file.isFile());String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).replace("\ufeff","");return t.store.parseBackup(text);}
    @Test public void suppliedBackupMigratesSQLiteRoundtripsAndShowsAllLinkedSources()throws Exception{
        Ledger.Data d=fixture("private-original.json");int entries=d.entries.size(),funds=d.funds.size(),history=d.history.size();long total=d.total();d.settings.autoRefresh=false;t.start(d);Ledger.Data saved=t.store.parseBackup(t.store.exportJson());assertEquals(entries,saved.entries.size());assertEquals(funds,saved.funds.size());assertEquals(history,saved.history.size());assertEquals(total,saved.total());assertTrue(saved.snapshots.stream().noneMatch(s->s.cashGenerated||!s.cashAccounts.isEmpty()));
        for(Ledger.Account a:saved.accounts)assertEquals(AccountCash.flows(saved).stream().filter(f->f.accountId==a.id).count(),AccountCash.rows(saved,a).size());
        Ledger.Account active=saved.accounts.stream().max(Comparator.comparingInt(a->AccountCash.rows(saved,a).size())).get();old.history(active.id);t.device.waitForIdle();old.old.shot("private-source-history.png");
    }
    @Test public void repairedBackupPreservesFullHistoricalTotalsAndResaveRestoresOneIncome()throws Exception{
        Ledger.Data d=fixture("private-repaired.json");for(Ledger.HistoryMonth h:d.history){assertTrue(h.fixedCutoff);assertEquals(h.expense,Ledger.month(d,h.month).expense);}Ledger.Entry source=d.entries.stream().filter(e->Ledger.INCOME.equals(e.kind)&&e.cashLinked).findFirst().get();AccountCash.hide(d,source.accountId,"entry:"+source.id);long total=d.total();d.settings.autoRefresh=false;t.start(d);int before=AccountCash.rows(t.app.data,AccountRecords.account(t.app.data,source.accountId)).size();t.ui(()->t.app.openEntry(Ledger.INCOME,t.app.data.entries.stream().filter(e->e.id==source.id).findFirst().get()));old.save();Ledger.Data saved=t.store.load();assertEquals(before+1,AccountCash.rows(saved,AccountRecords.account(saved,source.accountId)).size());assertEquals(total,saved.total());assertEquals(d.entries.size(),saved.entries.size());for(Ledger.HistoryMonth h:saved.history)assertEquals(h.expense,Ledger.month(saved,h.month).expense);old.history(source.accountId);old.old.shot("private-restored-source.png");
    }
}
