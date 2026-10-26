package cn.yueji.balance;

import android.app.usage.*;
import android.os.storage.StorageManager;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Read-only breakdown, and an explicit cache-only cleanup that never touches the ledger or models. */
final class StorageScreen {
    static long bytes(File file){
        if(file==null||!file.exists())return 0;if(file.isFile())return file.length();
        long result=0;File[] children=file.listFiles();if(children!=null)for(File child:children)try{if(child.getCanonicalPath().startsWith(file.getCanonicalPath()+File.separator))result+=bytes(child);}catch(IOException ignored){}return result;
    }
    static String size(long bytes){return bytes>=1_000_000?String.format(Locale.CHINA,"%.2f MB",bytes/1_000_000.0):String.format(Locale.CHINA,"%.2f KB",bytes/1000.0);}
    static void show(MainActivity m){
        m.top("存储空间与缓存","刷新",m::render);LinearLayout card=m.card(m.body);
        try{
            StorageStats stats=m.getSystemService(StorageStatsManager.class).queryStatsForUid(StorageManager.UUID_DEFAULT,android.os.Process.myUid());
            m.metric(card,"系统统计 · 应用",size(stats.getAppBytes()),Ui.INK);
            m.metric(card,"系统统计 · 数据（含缓存）",size(stats.getDataBytes()),Ui.INK);
            m.metric(card,"系统统计 · 缓存",size(stats.getCacheBytes()),Ui.INK);
        }catch(Exception e){card.addView(m.text("系统统计暂不可读，下方为可访问文件的实际长度。",11,Ui.MUTED,false));}
        LinearLayout details=m.card(m.body);
        m.metric(details,"当前安装APK",size(new File(m.getApplicationInfo().sourceDir).length()),Ui.ACCENT_DEEP);
        m.metric(details,"已解压本机库",size(bytes(new File(m.getApplicationInfo().nativeLibraryDir))),Ui.ACCENT_DEEP);
        m.metric(details,"账本数据库及日志",size(bytes(m.getDatabasePath("yueji.db").getParentFile())),Ui.INK);
        m.metric(details,"账本JSON实际内容",size(LedgerStore.encode(m.data).getBytes(java.nio.charset.StandardCharsets.UTF_8).length),Ui.INK);
        m.metric(details,"应用文件（模型/日历/图标）",size(bytes(m.getFilesDir())),Ui.INK);
        m.metric(details,"模型及不可备份文件",size(bytes(m.getNoBackupFilesDir())),Ui.INK);
        long external=0;for(File dir:m.getExternalFilesDirs(null))external+=bytes(dir);
        m.metric(details,"应用外部文件",size(external),Ui.INK);
        m.metric(details,"可清理临时缓存",size(cacheBytes(m)),Ui.INK);
        m.metric(details,"系统运行缓存",size(bytes(m.getCodeCacheDir())),Ui.MUTED);
        details.addView(m.text("这些是不同统计口径，部分项目存在包含关系，不应逐项相加。系统“应用”还可能包含编译优化文件、页对齐和安装元数据，运行后增长不等于账本变大。",11,Ui.MUTED,false));
        LinearLayout explanation=m.card(m.body);
        explanation.addView(m.text("本版已移除全部截图识别模块和模型。Android运行时仍可能生成编译优化文件。历史净值会作为有效账本数据保存以绘制收益，不能当缓存随意删除。",12,Ui.INK,false));
        explanation.addView(m.text("清理只删除cache目录内的临时文件，不清除数据库、基金历史、图标、JSON或系统编译缓存。系统设置中的“清除数据”会删除账本，请勿把它当清缓存。",11,Ui.MUTED,false));
        m.body.addView(m.action("清理临时缓存",()->AppIcons.dialog(m).setTitle("清理临时缓存？").setMessage("不会删除账本、账户、基金、负债或导入记录。").setNegativeButton("取消",null).setPositiveButton("清理",(d,w)->{
            try{long before=cacheBytes(m);clean(m.getCacheDir());File[] dirs=m.getExternalCacheDirs();if(dirs!=null)for(File dir:dirs)clean(dir);m.render();m.toast("已清理 "+size(Math.max(0,before-cacheBytes(m))));}catch(Exception e){m.error(e);}
        }).show()));
    }
    static long cacheBytes(android.content.Context context){long result=bytes(context.getCacheDir());File[] dirs=context.getExternalCacheDirs();if(dirs!=null)for(File dir:dirs)result+=bytes(dir);return result;}
    static void clean(File directory)throws IOException{
        if(directory==null||!directory.isDirectory())return;String base=directory.getCanonicalPath()+File.separator;File[] children=directory.listFiles();if(children==null)return;
        for(File child:children){
            if(!child.getCanonicalPath().startsWith(base))continue;
            if(child.isDirectory())clean(child);
            if(child.exists()&&!child.delete())throw new IOException("部分缓存正在使用，请稍后再试");
        }
    }
    private StorageScreen(){}
}
