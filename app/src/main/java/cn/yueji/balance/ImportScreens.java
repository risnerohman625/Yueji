package cn.yueji.balance;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** One expanded import batch per page. Deletion always re-reads the ledger before applying ownership. */
final class ImportScreens {
    final MainActivity m;final String module;
    ImportScreens(MainActivity m,String module){this.m=m;this.module=module;}
    void show(){
        m.top(module.equals(ImportJournal.RECORDS)?"收支报销 · 导入数据管理":"基金 · 导入数据管理",null,null);
        LinearLayout note=m.card(m.body);note.addView(m.text("只记录实际新增或替换的数据；重复跳过的行不进入批次。删除会同步更新统计，建议先导出完整备份。",12,Ui.MUTED,false));
        List<ImportJournal.Batch> list=ImportJournal.visible(m.data,module);
        if(list.isEmpty())m.card(m.body).addView(m.text("暂无已记录的导入批次",14,Ui.INK,true));
        for(ImportJournal.Batch batch:list)batch(batch);
        ImportJournal.Batch old=ImportJournal.untracked(m.data,module);
        if(!old.items.isEmpty())legacy(old);
    }
    void batch(ImportJournal.Batch batch){
        LinearLayout card=m.card(m.body),header=m.row(),label=m.col();
        label.addView(m.text(batch.at.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))+"  "+(m.openImportBatch==batch.id?"▴":"▾"),14,Ui.INK,true));
        label.addView(m.text(ImportJournal.summary(batch),12,Ui.ACCENT,true));
        label.addView(m.text(batch.source+" · "+batch.at.format(DateTimeFormatter.ofPattern("HH:mm:ss")),10,Ui.MUTED,false));
        m.weight(header,label);TextView delete=m.ui.link("删除",()->confirm(batch,null,false));delete.setTextColor(Ui.RED);delete.setContentDescription("删除导入批次"+batch.id);header.addView(delete);
        label.setOnClickListener(v->{m.openImportBatch=m.openImportBatch==batch.id?-1:batch.id;m.rerenderKeepingScroll();});
        label.setContentDescription("展开导入批次"+batch.id);card.addView(header);
        if(m.openImportBatch==batch.id)for(ImportJournal.Item item:batch.items)if(!item.deleted)detail(card,batch,item,false);
    }
    void legacy(ImportJournal.Batch batch){
        LinearLayout card=m.card(m.body),header=m.row(),label=m.col();
        label.addView(m.text("来源未记录的数据（含手动记录） "+(m.openImportBatch==0?"▴":"▾"),13,Ui.INK,true));
        label.addView(m.text("共 "+batch.items.size()+" 条 · 无法还原原导入日期与批次",11,Ui.MUTED,false));m.weight(header,label);
        label.setContentDescription("展开来源未记录数据");label.setOnClickListener(v->{m.openImportBatch=m.openImportBatch==0?-1:0;m.rerenderKeepingScroll();});
        TextView delete=m.ui.link("删除",()->confirm(batch,null,true));delete.setTextColor(Ui.RED);delete.setContentDescription("删除来源未记录数据");header.addView(delete);card.addView(header);
        if(m.openImportBatch==0){
            card.addView(m.text("旧版没有保存导入来源，不能伪造批次。这里也可能包含手动记录，请逐条核对后删除。",11,Ui.AMBER,false));
            for(ImportJournal.Item item:batch.items)detail(card,batch,item,true);
        }
    }
    void detail(LinearLayout card,ImportJournal.Batch batch,ImportJournal.Item item,boolean legacy){
        m.gap(card,10);card.addView(m.ui.divider());m.gap(card,8);LinearLayout head=m.row();
        TextView name=m.text(type(item)+" · "+item.title,13,Ui.INK,true);m.weight(head,name);
        TextView delete=m.ui.link("删除",()->confirm(batch,item,legacy));delete.setTextColor(Ui.RED);delete.setContentDescription("删除导入明细"+item.kind+item.ref);head.addView(delete);card.addView(head);
        TextView detail=m.text(item.detail,11.5f,Ui.MUTED,false);detail.setContentDescription("导入明细"+item.kind+item.ref);card.addView(detail);
        if(!legacy){
            String state=ImportJournal.state(m.data,batch,item);
            if(!state.isEmpty())card.addView(m.text(state,11,Ui.AMBER,false));
        }
    }
    String type(ImportJournal.Item i){
        if(i.kind.equals("month"))return "月支出";if(i.kind.equals("buy"))return "基金买入";if(i.kind.equals("bank"))return "银行理财";
        return i.category.equals(Ledger.INCOME)?"收入":i.category.equals(Ledger.REIMBURSE)?"报销":"大宗支出";
    }
    void confirm(ImportJournal.Batch batch,ImportJournal.Item item,boolean legacy){
        String message=legacy?"这些数据没有来源标记，可能含手动记录。":
            "只处理本次实际导入的数据，跳过的重复行不受影响。同月或银行理财被后续导入替换的版本不删除当前数据；仍归本次所有的替换项恢复导入前版本。";
        message+="\n"+(item==null?"删除当前批次全部明细？":"删除「"+item.title+"」？")+
            "\n删除基金最后一笔买入会一并删除该基金及其收益；自动关联资金划转同步删除，其他记录保留。此操作不可撤销。";
        AppIcons.dialog(m).setTitle(item==null?"删除这组数据？":"删除这条数据？").setMessage(message).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
            try{
                ImportJournal.Removal result;
                synchronized(LedgerStore.class){
                    Ledger.Data copy=m.store.load();
                    result=legacy?ImportJournal.removeUntrackedBatch(copy,module,item==null?batch.items:Collections.singletonList(item),m.fundScreens.calendar()):
                        ImportJournal.remove(copy,batch.id,item==null?null:item.ref,item==null?null:item.kind,m.fundScreens.calendar());
                    m.commit(copy);
                }
                m.rerenderKeepingScroll();String status="已处理 "+result.removed+" 条";
                if(result.preserved>0)status+="；"+result.preserved+" 条已被手动修改，为保护后续数据未删除，可在“来源未记录”中核对";
                if(result.absent>0)status+="；"+result.absent+" 条已删除或被后续版本替换";
                if(result.preserved>0)AppIcons.dialog(m).setTitle("删除结果").setMessage(status).setPositiveButton("知道了",null).show();else m.toast(status);
            }catch(Exception e){m.error(e);}
        }).show();
    }
}
