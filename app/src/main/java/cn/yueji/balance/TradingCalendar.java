package cn.yueji.balance;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Weekends are always closed (an adjusted working weekend is not a trading day). Holidays come from a
 * holiday-cn file per year: a copy downloaded into the app's files folder wins over the bundled one.
 * A year only counts as covered when its file lists at least one day, because next year's file is
 * published as an empty placeholder until the State Council announces the arrangement.
 */
public final class TradingCalendar {
    private final Set<LocalDate> holidays=new HashSet<>();
    private final Set<Integer> covered=new HashSet<>();
    public TradingCalendar(){}
    public void addClosed(LocalDate date){holidays.add(date);covered.add(date.getYear());}
    public static File downloaded(Context context,int year){return new File(new File(context.getFilesDir(),"holidays"),year+".json");}
    public void load(Context context,int year){
        if(covered.contains(year))return;
        String json=read(downloaded(context,year));
        if(json==null||!parse(json))try(InputStream in=context.getAssets().open("holidays/"+year+".json")){parse(new String(readAll(in),StandardCharsets.UTF_8));}catch(Exception ignored){}
    }
    private static String read(File file){
        if(!file.isFile())return null;
        try(InputStream in=new FileInputStream(file)){return new String(readAll(in),StandardCharsets.UTF_8);}catch(IOException e){return null;}
    }
    static byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
    }
    private boolean parse(String json){
        try{
            JSONObject root=new JSONObject(json);JSONArray days=root.getJSONArray("days");if(days.length()==0)return false;
            int year=root.getInt("year");
            for(int i=0;i<days.length();i++){JSONObject d=days.getJSONObject(i);if(d.getBoolean("isOffDay"))holidays.add(LocalDate.parse(d.getString("date")));}
            covered.add(year);return true;
        }catch(Exception e){return false;}
    }
    public boolean covered(int year){return covered.contains(year);}
    public boolean open(LocalDate d){return d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY&&!holidays.contains(d);}
    public LocalDate onOrAfter(LocalDate d){while(!open(d))d=d.plusDays(1);return d;}
    public LocalDate next(LocalDate d){return onOrAfter(d.plusDays(1));}
    public LocalDate tradeDate(LocalDateTime at){return onOrAfter(at.toLocalTime().isBefore(LocalTime.of(15,0))?at.toLocalDate():at.toLocalDate().plusDays(1));}
}
