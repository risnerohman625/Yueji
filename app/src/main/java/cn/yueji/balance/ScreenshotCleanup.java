package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.util.*;

/** Delete only sources whose selected data committed. Provider/system consent is never bypassed. */
final class ScreenshotCleanup {
    static final int REQUEST=120;
    final MainActivity m;
    final ArrayList<Uri> pending=new ArrayList<>();
    int failed,deleted;
    ScreenshotCleanup(MainActivity m){this.m=m;}
    void delete(Collection<Uri> sources){
        pending.clear();pending.addAll(new LinkedHashSet<>(sources));failed=deleted=0;next();
    }
    void next(){
        while(!pending.isEmpty()){
            Uri uri=pending.remove(0);
            // A stale persisted source must not grant deletion of arbitrary local files.
            if(!"content".equals(uri.getScheme())){failed++;continue;}
            try{
                if(DocumentsContract.isDocumentUri(m,uri)){
                    if(DocumentsContract.deleteDocument(m.getContentResolver(),uri)){deleted++;continue;}
                }else if(m.getContentResolver().delete(uri,null,null)>0){deleted++;continue;}
                failed++;
            }catch(SecurityException e){
                if(Build.VERSION.SDK_INT>=29&&e instanceof RecoverableSecurityException){
                    try{pending.add(0,uri);m.startIntentSenderForResult(((RecoverableSecurityException)e).getUserAction().getActionIntent().getIntentSender(),REQUEST,null,0,0,0);return;}catch(Exception denied){if(!pending.isEmpty()&&pending.get(0).equals(uri))pending.remove(0);failed++;continue;}
                }
                if(Build.VERSION.SDK_INT>=30){
                    try{
                        Uri media=MediaStore.getMediaUri(m,uri);if(media==null)media=uri;
                        PendingIntent request=MediaStore.createDeleteRequest(m.getContentResolver(),Collections.singletonList(media));
                        pending.add(0,uri);m.startIntentSenderForResult(request.getIntentSender(),REQUEST+1,null,0,0,0);return;
                    }catch(Exception denied){if(!pending.isEmpty()&&pending.get(0).equals(uri))pending.remove(0);failed++;}
                }else failed++;
            }catch(Exception e){failed++;}
        }
        if(deleted>0||failed>0)m.toast("截图清理：已删除 "+deleted+" 张"+(failed==0?"":"，"+failed+" 张未删除（无权限或已取消），数据已保存"));
    }
    void result(int request,int result){
        if(pending.isEmpty())return;
        if(result!=Activity.RESULT_OK){pending.remove(0);failed++;}
        else if(request==REQUEST+1){pending.remove(0);deleted++;}
        next();
    }
    Bundle save(){Bundle b=new Bundle();ArrayList<String> uris=new ArrayList<>();for(Uri u:pending)uris.add(u.toString());b.putStringArrayList("pending",uris);b.putInt("failed",failed);b.putInt("deleted",deleted);return b;}
    void restore(Bundle b){if(b==null)return;failed=b.getInt("failed");deleted=b.getInt("deleted");for(String u:b.getStringArrayList("pending")==null?new ArrayList<String>():b.getStringArrayList("pending"))pending.add(Uri.parse(u));}
}
