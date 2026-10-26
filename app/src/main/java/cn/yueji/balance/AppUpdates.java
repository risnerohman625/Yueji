package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public release checks; APK identity and signing certificate are verified before handing off to Android. */
final class AppUpdates {
    final MainActivity m;final android.content.SharedPreferences prefs;
    boolean busy,disposed;String pendingNotes="";AlertDialog progress;UpdatePanel panel;
    AppUpdates(MainActivity m){this.m=m;prefs=m.getSharedPreferences("updates",0);}
    String gitee(){return prefs.getString("giteeRepository","risnerohman576/yueji");}
    void startup(){if(disposed||m.isFinishing())return;
        if(!UsageGuideActivity.REVISION.equals(prefs.getString("guideRead",""))){m.startActivity(new Intent(m,UsageGuideActivity.class).putExtra("required",true));return;}
        long installed=code();if(prefs.getLong("lastOpenedBuild",0)<installed){long previous=prefs.getLong("lastOpenedBuild",0);prefs.edit().putLong("lastOpenedBuild",installed).apply();if(previous>0&&!prefs.contains("pendingVersion"))AppIcons.dialog(m).setTitle("更新内容 · V"+m.versionName()).setMessage("统一银行理财与基金卡片间距；自选分组显示数量，收窄列表边距和排序箭头；重复添加提示已在自选列表。").setPositiveButton("知道了",null).show();}
        long pending=prefs.getLong("pendingVersion",0);
        if(pending>0&&installed>=pending){String notes=prefs.getString("pendingNotes","");prefs.edit().remove("pendingVersion").remove("pendingNotes").apply();AppIcons.dialog(m).setTitle("更新内容 · V"+m.versionName()).setMessage(notes.isEmpty()?"本次更新优化软件体验。":notes).setPositiveButton("知道了",null).show();}
    }
    long code(){try{return version(m.getPackageManager().getPackageInfo(m.getPackageName(),0));}catch(Exception e){return 0;}}
    void about(LinearLayout card){card.addView(m.text("Yueji V"+m.versionName(),16,Ui.INK,true));m.gap(card,8);LinearLayout links=m.row();links.addView(m.text("项目地址  ",12,Ui.MUTED,false));repoLink(links,"GitHub",R.drawable.ic_github,()->open("https://github.com/risnerohman625/Yueji"));links.addView(m.text(" | ",12,Ui.MUTED,false));repoLink(links,"Gitee",R.drawable.ic_gitee,()->{if(gitee().isEmpty())configure();else open("https://gitee.com/"+gitee());});card.addView(links);m.gap(card,8);
        LinearLayout row=m.row();TextView check=m.action(busy?"检查更新中":"检查更新",()->check());check.setEnabled(!busy);row.addView(check,new LinearLayout.LayoutParams(0,-2,1));if(busy){ProgressBar spinner=new ProgressBar(m);spinner.setContentDescription("检查更新中旋转指示器");row.addView(spinner,new LinearLayout.LayoutParams(m.dp(22),m.dp(22)));}Segmented sources=new Segmented(m,"GitHub","Gitee");sources.setSelected(prefs.getInt("source",0));sources.onSelect(()->{prefs.edit().putInt("source",sources.selected()).apply();if(sources.selected()==1&&gitee().isEmpty())configure();});row.addView(sources,new LinearLayout.LayoutParams(0,m.dp(42),1));card.addView(row);
    }
    void repoLink(LinearLayout row,String text,int icon,Runnable click){ImageView image=new ImageView(m);image.setImageResource(icon);image.setColorFilter(Ui.INK);image.setOnClickListener(v->click.run());row.addView(image,new LinearLayout.LayoutParams(m.dp(18),m.dp(18)));TextView link=m.ui.link(text,click);row.addView(link);}
    void open(String url){m.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    void configure(){EditText input=new EditText(m);input.setSingleLine(true);input.setHint("https://gitee.com/用户名/仓库名");input.setText(gitee());AlertDialog dialog=AppIcons.dialog(m).setTitle("Gitee 项目地址").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.show();dialog.getButton(-1).setOnClickListener(v->{String value=input.getText().toString().trim().replaceFirst("^https://gitee.com/","").replaceFirst("\\.git$","").replaceFirst("/$","");if(!value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")){m.toast("请输入有效 Gitee 仓库地址");return;}prefs.edit().putString("giteeRepository",value).apply();dialog.dismiss();});}
    void check(){if(busy)return;final int source=prefs.getInt("source",0);final String repository=source==0?"risnerohman625/Yueji":gitee();if(repository.isEmpty()){configure();return;}busy=true;m.rerenderKeepingScroll();new Thread(()->{try{String api=source==0?"https://api.github.com/repos/":"https://gitee.com/api/v5/repos/";String json=read(api+repository+"/releases/latest",2*1024*1024);JSONObject release=new JSONObject(json);String tag=release.optString("tag_name"),notes=release.optString("body",""),url="";Object assets=release.opt("assets");JSONArray files=assets instanceof JSONArray?(JSONArray)assets:assets instanceof JSONObject?((JSONObject)assets).optJSONArray("links"):null;if(files!=null)for(int i=0;i<files.length();i++){JSONObject a=files.getJSONObject(i);String link=a.optString("browser_download_url",a.optString("direct_asset_url",a.optString("url")));if(a.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")||link.toLowerCase(Locale.ROOT).endsWith(".apk")){url=link;break;}}
        final String apk=url;final int ordering=compare(tag,m.versionName());ui(()->{busy=false;m.rerenderKeepingScroll();if(ordering<=0){banner("已是最新版本V"+m.versionName());return;}showRelease(tag,notes,apk);if(apk.isEmpty()){panel.failure();panel.status.setText("此更新源没有 APK，请更换更新源后检查更新。");panel.update.setEnabled(false);}});
        }catch(Exception e){ui(()->{busy=false;m.rerenderKeepingScroll();m.toast("检查更新失败，请检查网络、仓库与发布配置");});}},"release-check").start();}
    static int compare(String left,String right){String[] a=left.replaceFirst("^[vV]","").split("\\."),b=right.replaceFirst("^[vV]","").split("\\.");try{for(int i=0;i<Math.max(a.length,b.length);i++){int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0;if(x!=y)return Integer.compare(x,y);}return 0;}catch(Exception e){throw new IllegalArgumentException("发布版本号无效");}}
    void banner(String message){UiBanner.show(m,message,false,false);}
    void showRelease(String version,String notes,String url){if(panel!=null)panel.dismiss();int source=prefs.getInt("source",0);panel=new UpdatePanel(m,version,notes,()->{if(prefs.getInt("source",0)!=source){panel.dismiss();check();}else download(url,notes);});panel.show();}
    void download(String url,String notes){
        if(busy)return;if(panel==null)showRelease("",notes,url);busy=true;panel.downloading();
        new Thread(()->{File temp=new File(m.getCacheDir(),"update-download.part");
            try{
                HttpURLConnection connection=connection(url);
                try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                    long expected=connection.getContentLengthLong(),total=0;byte[] buffer=new byte[32768];int length,last=-2;
                    while((length=in.read(buffer))!=-1){if(disposed)throw new IOException("cancelled");total+=length;if(total>150L*1024*1024)throw new IOException("APK too large");out.write(buffer,0,length);int percent=expected>0?(int)Math.min(99,total*100/expected):-1;if(percent!=last){last=percent;ui(()->panel.percent(percent));}}
                    if(expected>0&&expected!=total)throw new IOException("incomplete download");
                }finally{connection.disconnect();}
                ui(()->{panel.percent(100);panel.status.setText("正在校验包名、版本与签名…");});
                long version=verify(temp);File target=UpdateApkProvider.file(m);if(target.exists()&&!target.delete())throw new IOException();if(!temp.renameTo(target))throw new IOException();
                prefs.edit().putLong("pendingVersion",version).putString("pendingNotes",notes).apply();
                ui(()->{busy=false;panel.dismiss();install();});
            }catch(Exception e){temp.delete();ui(()->{busy=false;panel.failure();});}
        },"update-download").start();
    }
    static long version(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    long verify(File apk)throws Exception{PackageManager pm=m.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo incoming=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(m.getPackageName(),flags);if(incoming==null||!m.getPackageName().equals(incoming.packageName)||version(incoming)<=version(current))throw new IOException("package/version mismatch");Signature[] incomingSigners=Build.VERSION.SDK_INT>=28&&incoming.signingInfo!=null?incoming.signingInfo.getApkContentsSigners():incoming.signatures;Signature[] currentSigners=Build.VERSION.SDK_INT>=28&&current.signingInfo!=null?current.signingInfo.getApkContentsSigners():current.signatures;if(incomingSigners==null||currentSigners==null)throw new IOException("missing signer");Set<String> a=new HashSet<>(),b=new HashSet<>();for(Signature signature:incomingSigners)a.add(signature.toCharsString());for(Signature signature:currentSigners)b.add(signature.toCharsString());if(!a.equals(b))throw new IOException("signer mismatch");return version(incoming);}
    void install(){if(!m.getPackageManager().canRequestPackageInstalls()){AppIcons.dialog(m).setTitle("允许安装应用更新").setMessage("系统需要允许此来源安装应用。设置完成后返回并点击继续安装。").setNegativeButton("取消",null).setPositiveButton("前往设置",(d,w)->{m.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+m.getPackageName())));}).show();AppIcons.dialog(m).setTitle("安装包已下载").setPositiveButton("继续安装",(d,w)->install()).setNegativeButton("稍后",null).show();return;}Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://cn.yueji.balance.updates/verified-update.apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{m.startActivity(intent);}catch(Exception e){m.toast("无法打开系统安装器，请手动安装下载的APK");}}
    static HttpURLConnection connection(String url)throws Exception{URL target=new URL(url);if(!"https".equals(target.getProtocol()))throw new IOException("HTTPS required");HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","Yueji-update/4.7.6");c.setInstanceFollowRedirects(false);for(int i=0;i<6;i++){int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");URL redirected=new URL(c.getURL(),next);c.disconnect();if(!"https".equals(redirected.getProtocol()))throw new IOException();c=(HttpURLConnection)redirected.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);continue;}if(status!=200){c.disconnect();throw new IOException("HTTP "+status);}return c;}c.disconnect();throw new IOException("redirect loop");}
    static String read(String url,int limit)throws Exception{HttpURLConnection c=connection(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>limit)throw new IOException();}return out.toString(StandardCharsets.UTF_8.name());}finally{c.disconnect();}}
    void ui(Runnable run){m.runOnUiThread(()->{if(!disposed&&!m.isDestroyed()&&!m.isFinishing())run.run();});}
    void dispose(){disposed=true;if(progress!=null)progress.dismiss();if(panel!=null)panel.dismiss();}
}
