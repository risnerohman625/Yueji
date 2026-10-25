package cn.yueji.balance;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Test-only counting provider: no photographs, files or user records are accessible through it. */
public final class TestScreenshotProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}
    @Override public String getType(Uri uri){return "image/jpeg";}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){return 0;}
    @Override public int delete(Uri uri,String where,String[] args){getContext().getSharedPreferences("delete-test",0).edit().putInt("count",getContext().getSharedPreferences("delete-test",0).getInt("count",0)+1).commit();return 1;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(method.startsWith("picker-")){
            if(method.equals("picker-reset"))getContext().getSharedPreferences("picker-test",0).edit().putBoolean("deleted",false).putInt("count",0).commit();
            Bundle result=new Bundle();result.putInt("count",getContext().getSharedPreferences("picker-test",0).getInt("count",0));return result;
        }
        if(method.equals("reset"))getContext().getSharedPreferences("delete-test",0).edit().clear().commit();Bundle result=new Bundle();result.putInt("count",getContext().getSharedPreferences("delete-test",0).getInt("count",0));return result;
    }
}
