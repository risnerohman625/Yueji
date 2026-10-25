package cn.yueji.balance;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

final class WatchValidation {
    static void validate(Ledger.Data d){
        if(d.watchFunds.size()>500||d.watchGroups.size()>30||d.watchRange<0||d.watchRange>9||d.watchSort<0||d.watchSort>2)throw new IllegalArgumentException("自选数据量或排序无效");
        Set<String> codes=new HashSet<>(),names=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(Ledger.WatchFund f:d.watchFunds){if(f.code==null||!f.code.matches("[0-9]{6}")||!codes.add(f.code)||f.name==null||f.name.trim().isEmpty()||f.name.length()>100||f.nav.signum()<0||f.previous.signum()<0||f.prices.size()>30000||f.returns.size()>30000)throw new IllegalArgumentException("自选基金数据无效");f.profile.validate();for(Map.Entry<LocalDate,BigDecimal> e:f.prices.entrySet())if(e.getKey().isAfter(FundEngine.now().toLocalDate())||e.getValue().signum()<=0||e.getValue().compareTo(new BigDecimal("1000000"))>0)throw new IllegalArgumentException("自选历史净值无效");for(BigDecimal value:f.returns.values())if(value.compareTo(new BigDecimal("-100"))<=0||value.compareTo(new BigDecimal("1000000"))>0)throw new IllegalArgumentException("自选涨跌幅无效");}
        for(Ledger.Fund f:d.funds)codes.add(f.code);
        for(Ledger.WatchGroup g:d.watchGroups){if(g.id<=0||!ids.add(g.id)||g.name==null||g.name.trim().isEmpty()||g.name.length()>12||!names.add(g.name)||Arrays.asList("全部","持有").contains(g.name)||g.codes.size()>500)throw new IllegalArgumentException("自选分组无效");for(String code:g.codes)if(!codes.contains(code))throw new IllegalArgumentException("分组基金代码不存在");}
        Set<Long> accounts=new HashSet<>();for(Ledger.Account a:d.accounts)accounts.add(a.id);
        for(BankLedger.Product p:d.banks){if(p.openingAccountLinked&&!accounts.contains(p.openingAccountId))throw new IllegalArgumentException("银行理财关联账户不存在");for(BankLedger.Event e:p.events)if(e.accountLinked&&!accounts.contains(e.accountId))throw new IllegalArgumentException("银行理财交易账户不存在");if(p.openingAccountLinked!=(p.openingAccountId>0))throw new IllegalArgumentException("银行理财来源账户无效");for(BankLedger.Event e:p.events)if(e.accountLinked!=(e.accountId>0)||e.accountLinked&&!(e.kind.equals("BUY")||e.kind.equals("SELL")))throw new IllegalArgumentException("银行理财交易账户无效");}
    }
    private WatchValidation(){}
}
