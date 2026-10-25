package cn.yueji.balance;

import java.time.YearMonth;
import java.util.*;

/** Local search never changes the ledger, and matches notes as well as record titles. */
final class RecordQuery {
    static boolean reimbursementState(Ledger.Entry e,int state){
        return state==2||state==0&&e.receivedTotal()<e.amount||state==1&&e.receivedTotal()==e.amount;
    }
    static List<Ledger.Entry> filter(Ledger.Data data,String kind,String category,String query,String month){
        List<Ledger.Entry> result=new ArrayList<>();String key=query.trim().toLowerCase(Locale.ROOT);
        for(Ledger.Entry e:data.entries){
            if(!e.kind.equals(kind)||category!=null&&!category.equals(e.category))continue;
            if(!month.isEmpty()&&!YearMonth.from(e.at).toString().equals(month))continue;
            String status=Ledger.REIMBURSE.equals(e.kind)?e.reimbursementStatus():"";
            String haystack=(e.title+"\n"+e.category+"\n"+e.note+"\n"+data.accountName(e.accountId)+"\n"+status).toLowerCase(Locale.ROOT);
            if(key.isEmpty()||haystack.contains(key))result.add(e);
        }
        return result;
    }
    private RecordQuery(){}
}
