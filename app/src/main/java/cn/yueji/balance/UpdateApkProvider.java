package cn.yueji.balance;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Grants read-only access to exactly one verified update APK, never the ledger. */
public final class UpdateApkProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    static File file(Context context){return new File(context.getCacheDir(),"verified-update.apk");}
    void check(Uri uri)throws FileNotFoundException{if(!"/verified-update.apk".equals(uri.getPath()))throw new FileNotFoundException();}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{check(uri);if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
    public String getType(Uri uri){return "application/vnd.android.package-archive";}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){MatrixCursor c=new MatrixCursor(new String[]{"_display_name","_size"});if("/verified-update.apk".equals(uri.getPath()))c.addRow(new Object[]{"Yueji-update.apk",file(getContext()).length()});return c;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
