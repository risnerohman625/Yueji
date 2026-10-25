package cn.yueji.balance;

import android.view.*;
import android.widget.LinearLayout;
import java.time.*;
import java.util.*;

/** Sort the visible mixed product list, instead of appending bank products after sorted funds. */
final class PortfolioOrder {
    static final class Key {
        final String identity;final boolean hidden;final long value,profit;final LocalDateTime bought;
        Key(String identity,boolean hidden,long value,long profit,LocalDateTime bought){this.identity=identity;this.hidden=hidden;this.value=value;this.profit=profit;this.bought=bought;}
    }
    static Key fund(Ledger.Fund fund,LocalDate date){
        LocalDate at=FundEngine.clamp(fund,date);return new Key("fund:"+fund.id,fund.hidden,FundEngine.value(fund,at),FundEngine.heldProfit(fund,at),FundRules.firstBuy(fund));
    }
    static Key bank(BankLedger.Product product){
        return new Key("bank:"+product.id,product.hidden,BankLedger.balance(product),BankAccounting.heldProfit(product),product.observations.get(0).at);
    }
    static Comparator<Key> comparator(int order){
        Comparator<Key> time=Comparator.comparing((Key key)->key.bought,Comparator.reverseOrder()).thenComparing(key->key.identity);
        Comparator<Key> values=order==1?Comparator.comparingLong((Key key)->key.value).reversed().thenComparing(time):
            order==2?Comparator.comparingLong((Key key)->key.profit).reversed().thenComparing(time):time;
        return Comparator.comparing((Key key)->key.hidden).thenComparing(values);
    }
    static void apply(LinearLayout parent,int order){
        List<View> rows=new ArrayList<>();int start=-1;
        for(int i=0;i<parent.getChildCount();i++){View view=parent.getChildAt(i);if(view.getTag() instanceof Key){if(start<0)start=i;rows.add(view);}}
        if(start<0)return;
        rows.sort((a,b)->comparator(order).compare((Key)a.getTag(),(Key)b.getTag()));
        for(View row:rows)parent.removeView(row);for(int i=0;i<rows.size();i++)parent.addView(rows.get(i),start+i);
    }
    private PortfolioOrder(){}
}
