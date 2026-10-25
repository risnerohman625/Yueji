package cn.yueji.balance;

import java.math.*;
import java.time.*;

final class YearSummary {
    final Long expense,averageExpense;
    final long income,averageIncome;
    final int expenseMonths,incomeMonths;
    YearSummary(Ledger.Data data,int year,YearMonth today){
        Long total=null;int count=0;
        for(int month=1;month<=12;month++){
            Ledger.Report report=Ledger.month(data,YearMonth.of(year,month));
            if(report.usable()){total=Math.addExact(total==null?0:total,report.expense);count++;}
        }
        expense=total;expenseMonths=count;income=Ledger.income(data,year);
        incomeMonths=year<today.getYear()?12:year==today.getYear()?today.getMonthValue():0;
        averageExpense=total==null?null:divide(total,count);
        averageIncome=incomeMonths==0?0:divide(income,incomeMonths);
    }
    private static long divide(long cents,int months){
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(months),0,RoundingMode.HALF_UP).longValueExact();
    }
}
