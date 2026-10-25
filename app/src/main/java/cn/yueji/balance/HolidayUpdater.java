package cn.yueji.balance;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Fetches the State Council holiday arrangement (NateScarlet/holiday-cn) for this and next year into the app's files folder. */
public final class HolidayUpdater {
    private static final String[] SOURCES={
        "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
        "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json"};
    private static final long RETRY_MILLIS=12L*3600*1000;

    /** True when a background refresh may try again; at most one attempt per 12 hours. */
    public static boolean due(Context context){
        SharedPreferences p=context.getSharedPreferences("holidays",Context.MODE_PRIVATE);
        return System.currentTimeMillis()-p.getLong("attempt",0)>RETRY_MILLIS;
    }

    /** Returns the years whose calendar file was added or changed. Throws when no source could be reached. */
    public static List<Integer> update(Context context)throws IOException{
        try{return NetworkIo.call(()->updateNow(context));}
        catch(IOException e){throw e;}
        catch(Exception e){throw new IOException("日历服务暂不可用",e);}
    }
    private static List<Integer> updateNow(Context context)throws IOException{
        context.getSharedPreferences("holidays",Context.MODE_PRIVATE).edit().putLong("attempt",System.currentTimeMillis()).apply();
        int year=FundEngine.now().getYear();List<Integer> changed=new ArrayList<>();IOException failure=null;int reached=0;
        for(int y=year;y<=year+1;y++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");
            String json=null;
            for(String pattern:SOURCES){
                try{json=get(String.format(Locale.ROOT,pattern,y));break;}catch(IOException e){failure=e;}
            }
            if(json==null)continue;
            reached++;
            if(!usable(json,y))continue;
            File target=TradingCalendar.downloaded(context,y);
            String old=null;if(target.isFile())try(InputStream in=new FileInputStream(target)){old=new String(TradingCalendar.readAll(in),StandardCharsets.UTF_8);}
            if(json.equals(old))continue;
            File dir=target.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建节假日目录");
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");
            File temp=new File(dir,y+".json.tmp");
            try(OutputStream out=new FileOutputStream(temp)){out.write(json.getBytes(StandardCharsets.UTF_8));}
            if(!temp.renameTo(target)){target.delete();if(!temp.renameTo(target))throw new IOException("无法保存节假日文件");}
            changed.add(y);
        }
        if(reached==0&&failure!=null)throw failure;
        return changed;
    }

    /** A file counts only when it is for the asked year and lists days; an empty placeholder means "not announced yet". */
    static boolean usable(String json,int year){
        try{
            JSONObject root=new JSONObject(json);JSONArray days=root.getJSONArray("days");
            if(root.getInt("year")!=year||days.length()==0)return false;
            for(int i=0;i<days.length();i++){JSONObject d=days.getJSONObject(i);d.getBoolean("isOffDay");if(LocalDate.parse(d.getString("date")).getYear()!=year)return false;}
            return true;
        }catch(Exception e){return false;}
    }

    private static String get(String url)throws IOException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","Yueji");
        try{
            if(c.getResponseCode()!=200)throw new IOException("节假日服务 HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("网络操作已取消");out.write(b,0,n);if(out.size()>300000)throw new IOException("节假日响应过大");}
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
    private HolidayUpdater(){}
}
