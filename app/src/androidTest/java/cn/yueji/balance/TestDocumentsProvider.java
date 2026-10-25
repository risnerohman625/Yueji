package cn.yueji.balance;

import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsProvider;
import android.provider.DocumentsContract.*;
import java.io.*;

/** Test APK only: lets the real system file picker grant a URI to the supplied screenshot. */
public final class TestDocumentsProvider extends DocumentsProvider {
    static final String AUTHORITY="cn.yueji.balance.test.documents",DOC="purchase-detail";
    static final String[] ROOT={Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS,Root.COLUMN_MIME_TYPES,Root.COLUMN_ICON};
    static final String[] DOCUMENT={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED};
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){
        MatrixCursor cursor=new MatrixCursor(projection==null?ROOT:projection);
        cursor.newRow().add(Root.COLUMN_ROOT_ID,"qa").add(Root.COLUMN_DOCUMENT_ID,"root").add(Root.COLUMN_TITLE,"Yueji 4.6.2 测试截图")
            .add(Root.COLUMN_FLAGS,Root.FLAG_LOCAL_ONLY).add(Root.COLUMN_MIME_TYPES,"image/jpeg").add(Root.COLUMN_ICON,android.R.drawable.ic_menu_gallery);return cursor;
    }
    boolean deleted(){return getContext().getSharedPreferences("picker-test",0).getBoolean("deleted",false);}
    void add(MatrixCursor cursor,String id)throws FileNotFoundException{
        if(id.equals("root")){cursor.newRow().add(Document.COLUMN_DOCUMENT_ID,id).add(Document.COLUMN_DISPLAY_NAME,"Yueji 测试截图")
            .add(Document.COLUMN_MIME_TYPE,Document.MIME_TYPE_DIR).add(Document.COLUMN_FLAGS,0).add(Document.COLUMN_SIZE,0).add(Document.COLUMN_LAST_MODIFIED,0L);return;}
        if(!id.equals(DOC)||deleted())throw new FileNotFoundException(id);
        cursor.newRow().add(Document.COLUMN_DOCUMENT_ID,id).add(Document.COLUMN_DISPLAY_NAME,"01-基金买入详情.jpg").add(Document.COLUMN_MIME_TYPE,"image/jpeg")
            .add(Document.COLUMN_FLAGS,Document.FLAG_SUPPORTS_DELETE).add(Document.COLUMN_SIZE,file().length()).add(Document.COLUMN_LAST_MODIFIED,1792713600000L);
    }
    File file()throws FileNotFoundException{
        File file=new File(getContext().getCacheDir(),"picker-purchase-detail.jpg");
        if(!file.exists())try(InputStream in=getContext().getAssets().open("fund-purchase-detail.jpg");OutputStream out=new FileOutputStream(file)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }catch(IOException e){throw new FileNotFoundException(e.toString());}return file;
    }
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor cursor=new MatrixCursor(projection==null?DOCUMENT:projection);add(cursor,id);return cursor;}
    @Override public Cursor queryChildDocuments(String id,String[] projection,String order)throws FileNotFoundException{
        MatrixCursor cursor=new MatrixCursor(projection==null?DOCUMENT:projection);if(id.equals("root")&&!deleted())add(cursor,DOC);return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        if(!DOC.equals(id)||deleted()||!mode.equals("r"))throw new FileNotFoundException(id);
        return ParcelFileDescriptor.open(file(),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public void deleteDocument(String id)throws FileNotFoundException{
        if(!DOC.equals(id))throw new FileNotFoundException(id);
        int count=getContext().getSharedPreferences("picker-test",0).getInt("count",0);
        getContext().getSharedPreferences("picker-test",0).edit().putBoolean("deleted",true).putInt("count",count+1).commit();
        File file=new File(getContext().getCacheDir(),"picker-purchase-detail.jpg");if(file.exists()&&!file.delete())throw new FileNotFoundException("Fixture deletion failed");
        getContext().getContentResolver().notifyChange(android.provider.DocumentsContract.buildChildDocumentsUri(AUTHORITY,"root"),null);
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(method.equals("reset")||method.equals("count")){
            if(method.equals("reset"))getContext().getSharedPreferences("picker-test",0).edit().putBoolean("deleted",false).putInt("count",0).commit();
            Bundle b=new Bundle();b.putInt("count",getContext().getSharedPreferences("picker-test",0).getInt("count",0));return b;
        }
        return super.call(method,arg,extras);
    }
}
