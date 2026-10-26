package cn.yueji.balance;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

public final class FundRefreshJob extends JobService {
    private ExecutorService executor;
    private volatile boolean stopped;
    private volatile long runId;
    static final long PERIOD=2*60*60*1000L;
    public static void schedule(Context c){
        LedgerStore store=new LedgerStore(c);boolean enabled;int minutes;try{Ledger.Settings settings=store.load().settings;enabled=settings.autoRefresh;minutes=MarketSession.interval(settings.refreshMinutes);}finally{store.close();}
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(!enabled){scheduler.cancel(221);return;}
        long period=minutes*60_000L;
        JobInfo existing=scheduler.getPendingJob(221);if(existing!=null&&existing.isPeriodic()&&existing.getIntervalMillis()==period)return;
        JobInfo job=new JobInfo.Builder(221,new ComponentName(c,FundRefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(period,Math.max(5*60_000L,period/4))
            .setPersisted(true).build();
        scheduler.schedule(job);
    }
    @Override public boolean onStartJob(JobParameters params){
        if(!MarketSession.open(this)){
            getSharedPreferences("fund-job",0).edit().putString("lastState","closed").putLong("checkedAt",System.currentTimeMillis()).apply();return false;
        }
        LedgerStore check=new LedgerStore(this);try{if(MarketRefresh.pending(check.load(),FundEngine.now().toLocalDate()).isEmpty())return false;}finally{check.close();}
        stopped=false;long token=++runId;executor=Executors.newSingleThreadExecutor();ExecutorService worker=executor;
        worker.execute(()->{LedgerStore store=new LedgerStore(this);try{if(store.load().settings.autoRefresh&&MarketSession.open(this)){FundClient.refreshDuringMarket(this);if(!Thread.currentThread().isInterrupted()&&!stopped&&token==runId&&MarketSession.open(this)&&HolidayUpdater.due(this))HolidayUpdater.update(this);}}catch(Exception ignored){ }finally{store.close();if(!stopped&&token==runId)jobFinished(params,false);worker.shutdown();}});return true;
    }
    @Override public boolean onStopJob(JobParameters params){stopped=true;runId++;if(executor!=null)executor.shutdownNow();return true;}
    @Override public void onDestroy(){stopped=true;runId++;if(executor!=null)executor.shutdownNow();super.onDestroy();}
}
