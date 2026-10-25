package cn.yueji.balance;

import org.json.*;
import java.time.*;
import java.util.*;

/** A separate liability ledger. Borrowings and repayments never create income or expense entries. */
final class DebtLedger {
    static final class Loan {
        long id,amount;String title="",note="";LocalDate at;
        final List<Payment> payments=new ArrayList<>();
    }
    static final class Payment {
        long id,amount;String note="";LocalDate at;
    }
    static Loan find(Ledger.Data data,long id){for(Loan loan:data.debts)if(loan.id==id)return loan;return null;}
    static long paid(Loan loan,LocalDate through){long result=0;for(Payment p:loan.payments)if(!p.at.isAfter(through))result=Math.addExact(result,p.amount);return result;}
    static long balance(Loan loan,LocalDate through){return loan.at.isAfter(through)?0:Math.subtractExact(loan.amount,paid(loan,through));}
    static long total(Ledger.Data data,LocalDate through){long result=0;for(Loan loan:data.debts)result=Math.addExact(result,balance(loan,through));return result;}
    static List<Loan> sorted(Ledger.Data data,LocalDate through){
        List<Loan> list=new ArrayList<>(data.debts);
        list.sort(Comparator.comparing((Loan loan)->balance(loan,through)==0).thenComparing(loan->loan.at,Comparator.reverseOrder()).thenComparing(loan->loan.id,Comparator.reverseOrder()));return list;
    }
    static Loan save(Ledger.Data data,long id,String title,long amount,LocalDate at,String note){
        Loan loan=id==0?new Loan():find(data,id);if(loan==null)throw new IllegalArgumentException("负债记录已删除");
        Loan proposed=new Loan();proposed.id=id==0?data.nextId():id;proposed.title=title.trim();proposed.amount=amount;proposed.at=at;proposed.note=note.trim();proposed.payments.addAll(loan.payments);validate(proposed,LocalDate.now());
        if(id==0){loan=proposed;data.debts.add(loan);}else{loan.title=proposed.title;loan.amount=amount;loan.at=at;loan.note=proposed.note;}
        return loan;
    }
    static void repay(Ledger.Data data,long loanId,long amount,LocalDate at,String note){
        Loan loan=find(data,loanId);if(loan==null)throw new IllegalArgumentException("负债记录已删除");
        if(amount<=0||amount>Ledger.MAX_MONEY||amount>balance(loan,LocalDate.now()))throw new IllegalArgumentException("还款金额须大于0且不能超过剩余负债");
        if(at==null||at.isBefore(loan.at)||at.isAfter(LocalDate.now()))throw new IllegalArgumentException("还款日期须在借款日至今天之间");
        if(note.trim().length()>1000||loan.payments.size()>=1000)throw new IllegalArgumentException("还款备注或记录数量超限");
        Payment p=new Payment();p.id=data.nextId();p.amount=amount;p.at=at;p.note=note.trim();loan.payments.add(p);
    }
    static void validate(Loan loan,LocalDate today){
        if(loan.id<=0||loan.title==null||loan.title.trim().isEmpty()||loan.title.length()>100||loan.note==null||loan.note.length()>1000||
            loan.amount<=0||loan.amount>Ledger.MAX_MONEY||loan.at==null||loan.at.getYear()<2000||loan.at.isAfter(today)||loan.payments.size()>1000)
            throw new IllegalArgumentException("请核对负债名称、金额和日期");
        Set<Long> ids=new HashSet<>();long paid=0;
        for(Payment p:loan.payments){
            if(p.id<=0||!ids.add(p.id)||p.amount<=0||p.amount>Ledger.MAX_MONEY||p.at==null||p.at.isBefore(loan.at)||p.at.isAfter(today)||p.note==null||p.note.length()>1000)
                throw new IllegalArgumentException("还款日期不能早于负债日期，金额和标识须有效");
            paid=Math.addExact(paid,p.amount);
        }
        if(paid>loan.amount)throw new IllegalArgumentException("负债金额不能小于已还款合计");
    }
    static void validate(Ledger.Data data){
        if(data.debts.size()>10000)throw new IllegalArgumentException("负债记录超限");
        Set<Long> ids=new HashSet<>();for(Loan loan:data.debts){if(!ids.add(loan.id))throw new IllegalArgumentException("负债标识重复");validate(loan,LocalDate.now());}
        total(data,LocalDate.now());
    }
    static SortedMap<Integer,Long> yearly(Ledger.Data data,LocalDate through){
        int first=through.getYear();for(Loan loan:data.debts)first=Math.min(first,loan.at.getYear());
        SortedMap<Integer,Long> series=new TreeMap<>();
        for(int year=first;year<=through.getYear();year++)series.put(year,total(data,year==through.getYear()?through:LocalDate.of(year,12,31)));
        return series;
    }
    static JSONArray encode(Ledger.Data data)throws JSONException{
        JSONArray list=new JSONArray();for(Loan loan:data.debts){
            JSONArray payments=new JSONArray();for(Payment p:loan.payments)payments.put(new JSONObject().put("id",p.id).put("amount",p.amount).put("at",p.at).put("note",p.note));
            list.put(new JSONObject().put("id",loan.id).put("amount",loan.amount).put("title",loan.title).put("at",loan.at).put("note",loan.note).put("payments",payments));
        }return list;
    }
    static void decode(Ledger.Data data,JSONArray list)throws JSONException{
        if(list==null)return;if(list.length()>10000)throw new IllegalArgumentException("负债记录超限");
        for(int i=0;i<list.length();i++){
            JSONObject j=list.getJSONObject(i);Loan loan=new Loan();loan.id=j.getLong("id");loan.amount=j.getLong("amount");loan.title=j.getString("title");loan.at=LocalDate.parse(j.getString("at"));loan.note=j.optString("note");
            JSONArray payments=j.getJSONArray("payments");if(payments.length()>1000)throw new IllegalArgumentException("还款记录超限");
            for(int k=0;k<payments.length();k++){JSONObject r=payments.getJSONObject(k);Payment p=new Payment();p.id=r.getLong("id");p.amount=r.getLong("amount");p.at=LocalDate.parse(r.getString("at"));p.note=r.optString("note");loan.payments.add(p);}
            data.debts.add(loan);
        }
    }
    private DebtLedger(){}
}
