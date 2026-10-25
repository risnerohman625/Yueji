package cn.yueji.balance;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** Multi-image purchase-detail drafts; uncertain identities cannot commit until service verification. */
final class FundScreenshot {
    static final int PICK=108;
    final MainActivity m;
    List<FundRecognition.Row> rows=new ArrayList<>();
    boolean deleteSources=true,busy;
    int excluded;
    volatile int generation;
    AlertDialog progress;
    final ArrayList<Uri> committedSources=new ArrayList<>();
    FundScreenshot(MainActivity m){this.m=m;}
    void pick(){m.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION),PICK);}
    void read(Uri uri){read(Collections.singletonList(uri));}
    void read(List<Uri> uris){
        if(uris.size()>20){error("一次最多选择20张买入详情截图");return;}
        cancelWork();deleteSources=true;int token=++generation;busy=true;
        progress=AppIcons.dialog(m).setTitle("识别买入详情").setMessage("正在本机识别文字…").setNegativeButton("取消",(d,w)->cancelWork()).create();
        progress.setOnCancelListener(d->cancelWork());progress.show();
        readNext(uris,0,new ArrayList<>(),new ArrayList<>(),token);
    }
    void readNext(List<Uri> sources,int index,List<FundRecognition.Row> pending,List<String> errors,int token){
        if(!active(token))return;
        if(index==sources.size()){match(pending,errors,token);return;}
        Uri uri=sources.get(index);progress.setMessage("正在识别 "+(index+1)+"/"+sources.size());
        ScreenshotOcr.recognize(m,uri,lines->{
            if(!active(token))return;
            try{FundRecognition.Row row=FundDetailRecognition.parse(lines);row.source=uri.toString();pending.add(row);}
            catch(Exception e){errors.add("第"+(index+1)+"张："+e.getMessage());}
            readNext(sources,index+1,pending,errors,token);
        },e->{if(active(token)){errors.add("第"+(index+1)+"张无法读取，请使用清晰买入详情截图");readNext(sources,index+1,pending,errors,token);}});
    }
    void match(List<FundRecognition.Row> pending,List<String> errors,int token){
        if(pending.isEmpty()){finishProgress();error(String.join("\n",errors));return;}
        progress.setMessage("逐字核对基金名称与代码…");
        new Thread(()->{
            Map<String,List<FundClient.Match>> cache=new HashMap<>();boolean offline=false;
            for(FundRecognition.Row row:pending){
                if(!active(token))return;if(row.name.isEmpty())continue;
                if(offline){row.problem=joinProblem(row.problem,"查询网络不可用，需联网核对代码");continue;}
                try{
                    List<FundClient.Match> found=cache.get(row.name);
                    if(found==null){found=FundClient.search(row.name);if(found.isEmpty()&&row.name.length()>6)found=FundClient.search(row.name.substring(0,6));cache.put(row.name,found);}
                    row.matches.addAll(found);row.matches.sort(Comparator.comparingInt(candidate->FundDetailRecognition.distance(row.name,candidate.name)));
                    List<FundClient.Match> exact=new ArrayList<>();for(FundClient.Match candidate:found)if(FundDetailRecognition.exact(row.name,candidate.name))exact.add(candidate);
                    if(exact.size()==1){row.code=exact.get(0).code;row.verified=true;}else row.problem=joinProblem(row.problem,"名称未逐字匹配，请核对最接近的产品");
                }catch(Exception e){row.problem=joinProblem(row.problem,"代码查询失败，需重新核对；截图内容已保留");offline=NetworkIo.connectivity(e);}
            }
            m.runOnUiThread(()->{if(!active(token))return;rows=pending;markDuplicates();finishProgress();m.go("fund_scan_preview");if(!errors.isEmpty())error(String.join("\n",errors));else promptMismatch();});
        },"purchase-detail-match").start();
    }
    static String joinProblem(String old,String problem){return old.isEmpty()?problem:old+"\n"+problem;}
    void markDuplicates(){
        Ledger.Data latest=m.store.load();Set<String> seen=new HashSet<>();
        for(FundRecognition.Row row:rows){
            boolean old=row.added;String key=row.code+"@"+row.at;
            row.added=row.verified&&row.at!=null&&(FundDetailRecognition.duplicate(row,latest)||!seen.add(key));
            if(row.added)row.selected=false;else if(old)row.selected=true;
        }
    }
    void promptMismatch(){
        for(int i=0;i<rows.size();i++)if(!rows.get(i).verified||rows.get(i).needsReview){
            FundRecognition.Row row=rows.get(i);int index=i;String nearest=row.matches.isEmpty()?"没有可核验候选，请修改名称或代码":row.matches.get(0).name+"（"+row.matches.get(0).code+"）";
            AppIcons.dialog(m).setTitle(row.needsReview?"买入详情需核对":"基金名称未完全匹配").setMessage("截图名称："+row.name+"\n最接近："+nearest+"\n"+row.problem+"\nA/C等任何字符不同都不能自动导入。")
                .setPositiveButton("核对并修改",(d,w)->edit(row,index)).setNegativeButton("稍后核对",null).show();return;
        }
    }
    void error(String message){
        if(m.isFinishing()||m.isDestroyed())return;
        AppIcons.dialog(m).setTitle("截图识别不符合要求").setMessage(message).setPositiveButton("知道了",null)
            .setNeutralButton("查看标准截图方式",(d,w)->m.go("fund_screenshot_standard")).show();
    }
    void standard(){
        m.top("标准截图方式",null,null);LinearLayout info=m.card(m.body);
        info.addView(m.text("请截取已成功的基金买入详情页，完整包含买入产品、买入金额、手续费和买入时间；不使用多基金交易列表。可一次选择多张详情截图。",13,Ui.INK,false));
        info.addView(m.text("示例银行卡与订单号已遮盖，不参与识别。",11,Ui.MUTED,false));ImageView image=new ImageView(m);image.setAdjustViewBounds(true);
        try(java.io.InputStream in=m.getAssets().open("fund-purchase-standard.jpg")){image.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(in,null));}catch(Exception e){info.addView(m.text("请使用完整买入详情页",12,Ui.AMBER,false));}
        m.body.addView(image,new LinearLayout.LayoutParams(-1,-2));
    }
    boolean active(int token){return token==generation&&!m.isDestroyed()&&!m.isFinishing();}
    void finishProgress(){busy=false;if(progress!=null)progress.dismiss();progress=null;}
    void cancelWork(){generation++;finishProgress();}
    void dispose(){cancelWork();}
    void show(){
        markDuplicates();m.top("识别结果 · 核对后导入",null,null);LinearLayout note=m.card(m.body);
        note.addView(m.text("共 "+rows.size()+" 张买入详情。名称逐字一致才可导入；同一基金同一分钟已有买入显示“已添加”。",12,Ui.INK,false));
        CheckBox cleanup=new CheckBox(m);cleanup.setText("导入完成后删除截图");cleanup.setChecked(deleteSources);cleanup.setOnCheckedChangeListener((v,b)->deleteSources=b);m.body.addView(cleanup);
        for(int i=0;i<rows.size();i++){
            FundRecognition.Row row=rows.get(i);int index=i;LinearLayout card=m.card(m.body),head=m.row();
            CheckBox check=new CheckBox(m);check.setChecked(row.selected);check.setEnabled(!row.added);check.setContentDescription("选择识别记录"+i);check.setOnCheckedChangeListener((v,b)->row.selected=b);head.addView(check);
            m.weight(head,m.text(row.name,14,Ui.INK,true));head.addView(m.ui.link("编辑",()->edit(row,index)));card.addView(head);
            card.addView(m.text("¥ "+m.money(row.amount)+" · "+(row.at==null?"时间待核对":MainActivity.TIME.format(row.at)),14,Ui.INK,true));
            card.addView(m.text("基金代码 "+(row.code.isEmpty()?"待核对":row.code)+" · 手续费 ¥ "+m.money(row.fee),11,Ui.MUTED,false));
            if(row.added)card.addView(m.text("已添加",13,Ui.ACCENT,true));else if(!row.verified||row.needsReview)card.addView(m.text(row.problem,11,Ui.AMBER,false));
        }
        m.body.addView(m.action("重新导入截图",this::pick));m.gap(m.body,8);m.body.addView(m.action("取消导入",()->{rows.clear();m.back();}));m.gap(m.body,8);m.body.addView(m.button("完成导入",this::complete));
    }
    void edit(FundRecognition.Row row,int index){
        LinearLayout form=m.col();form.setPadding(m.dp(16),0,m.dp(16),m.dp(14));
        EditText name=m.field(form,"识别基金名称",row.name,false),code=m.field(form,"识别基金代码",row.code,false),amount=m.field(form,"识别购入金额",row.amount<=0?"":Ledger.decimal(row.amount),true),fee=m.field(form,"识别手续费",row.needsReview&&row.problem.contains("手续费")?"":Ledger.decimal(row.fee),true);
        MainActivity.DateControl time=m.new DateControl(form,"识别购买时间",row.at==null?FundEngine.now():row.at,false);
        CheckBox timeChecked=new CheckBox(m);timeChecked.setText("已核对截图中的购买日期与时间");timeChecked.setChecked(row.at!=null);
        if(row.at==null)form.addView(timeChecked);
        TextView candidate=m.text(row.matches.isEmpty()?"可修改名称搜索，或输入代码核验":"最接近："+row.matches.get(0).name+"  "+row.matches.get(0).code,12,Ui.AMBER,false);form.addView(candidate);
        AlertDialog dialog=AppIcons.dialog(m).setTitle("核对完整基金信息").setView(m.wrap(form)).setNegativeButton("取消",null).setPositiveButton("联网核验并更新",null).create();dialog.show();
        TextView find=m.action("按名称查找候选",()->{});form.addView(find);
        find.setOnClickListener(v->{
            String query=name.getText().toString().trim();find.setEnabled(false);
            new Thread(()->{try{List<FundClient.Match> matches=FundClient.search(query);m.runOnUiThread(()->{if(!dialog.isShowing()||m.isDestroyed())return;find.setEnabled(true);if(matches.isEmpty()){m.toast("未找到，请校正名称或输入代码");return;}String[] names=new String[matches.size()];for(int j=0;j<names.length;j++)names[j]=matches.get(j).name+"  "+matches.get(j).code;AppIcons.dialog(m).setTitle("选择并核对准确份额").setItems(names,(d,w)->{name.setText(matches.get(w).name);code.setText(matches.get(w).code);}).show();});}catch(Exception e){m.runOnUiThread(()->{if(dialog.isShowing()){find.setEnabled(true);m.networkProblem(NetworkIo.message(e));}});}},"fund-candidate").start();
        });
        dialog.getButton(-1).setOnClickListener(v->{
            try{
                if(!timeChecked.isChecked())throw new IllegalArgumentException("购买时间未识别，请填写并勾选确认，不能把当前时间自动当作买入时间");
                FundRecognition.Row edited=new FundRecognition.Row();edited.name=name.getText().toString().trim();edited.code=code.getText().toString().trim();edited.amount=Ledger.parseMoney(amount.getText().toString(),true);edited.fee=Ledger.parseMoney(fee.getText().toString(),false);edited.at=time.at;edited.asImport();dialog.getButton(-1).setEnabled(false);
                new Thread(()->{try{
                    FundClient.Quote quote=FundClient.fetch(edited.code);
                    m.runOnUiThread(()->{if(!dialog.isShowing()||m.isDestroyed())return;dialog.getButton(-1).setEnabled(true);
                        if(!FundDetailRecognition.exact(edited.name,quote.name)){AppIcons.dialog(m).setTitle("名称与代码不一致").setMessage("该代码对应："+quote.name+"\n必须逐字一致。是否使用此官方名称？").setNegativeButton("继续修改",null).setPositiveButton("使用并重新核验",(d,w)->{name.setText(quote.name);candidate.setText("已更新官方名称，请再次核验保存");}).show();return;}
                        row.name=quote.name;row.code=edited.code;row.amount=edited.amount;row.fee=edited.fee;row.at=edited.at;row.verified=true;row.needsReview=false;row.problem="";dialog.dismiss();markDuplicates();m.render();
                    });
                }catch(Exception e){m.runOnUiThread(()->{if(dialog.isShowing()){dialog.getButton(-1).setEnabled(true);m.networkProblem(NetworkIo.message(e));}});}},"verify-fund-code").start();
            }catch(Exception e){m.error(e);}
        });
    }
    void complete(){
        try{
            markDuplicates();List<FundSheet.Row> selected=new ArrayList<>();committedSources.clear();
            for(FundRecognition.Row row:rows)if(row.selected&&!row.added){if(!row.verified||row.needsReview)throw new IllegalArgumentException("请先编辑核对 "+row.name+" 的名称、代码及买入字段");selected.add(row.asImport());if(!row.source.isEmpty())committedSources.add(Uri.parse(row.source));}
            if(selected.isEmpty())throw new IllegalArgumentException("没有新的已勾选记录；已添加项不会重复导入");
            FundSheet.Plan plan=FundSheet.plan(selected,m.store.load());m.fundScreens.importRecognized(selected,plan.codes);
        }catch(Exception e){m.error(e);}
    }
    void imported(){if(deleteSources&&!committedSources.isEmpty())m.screenshotCleanup.delete(committedSources);committedSources.clear();}
    Bundle save(){
        Bundle b=new Bundle();JSONArray array=new JSONArray();
        try{for(FundRecognition.Row row:rows)array.put(new JSONObject().put("name",row.name).put("code",row.code).put("amount",row.amount).put("fee",row.fee).put("at",row.at==null?"":row.at.toString()).put("selected",row.selected).put("verified",row.verified).put("review",row.needsReview).put("source",row.source).put("problem",row.problem));}catch(JSONException ignored){}
        b.putString("rows",array.toString());b.putBoolean("delete",deleteSources);return b;
    }
    void restore(Bundle b){
        if(b==null)return;deleteSources=b.getBoolean("delete",true);
        try{JSONArray array=new JSONArray(b.getString("rows","[]"));for(int i=0;i<array.length();i++){JSONObject json=array.getJSONObject(i);FundRecognition.Row row=new FundRecognition.Row();row.name=json.getString("name");row.code=json.getString("code");row.amount=json.getLong("amount");row.fee=json.getLong("fee");String at=json.optString("at");if(!at.isEmpty())row.at=LocalDateTime.parse(at);row.selected=json.getBoolean("selected");row.verified=json.optBoolean("verified");row.needsReview=json.optBoolean("review");row.source=json.optString("source");row.problem=json.optString("problem");rows.add(row);}}catch(Exception e){rows.clear();}
    }
}
