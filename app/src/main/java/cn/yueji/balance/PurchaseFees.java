package cn.yueji.balance;

import java.math.*;
import java.util.regex.*;

/** Only an unambiguous percentage tier is suggested; fixed fees stay manual. */
final class PurchaseFees {
    static BigDecimal rate(String rules,long cents){
        for(String line:rules.split("\n")){
            String[] cells=line.trim().split("\\s{2,}|\\s*\\|\\s*");
            Matcher percentages=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)%").matcher(line);
            BigDecimal rate=null;while(percentages.find())rate=new BigDecimal(percentages.group(1)).movePointLeft(2);
            if(rate==null)continue;
            String tier=cells[0];int firstPercent=tier.indexOf('%');if(firstPercent>=0)tier=tier.substring(0,firstPercent).replaceAll("[0-9.]+$","").trim();
            Matcher limits=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(万|亿)?元").matcher(tier);BigDecimal lower=null,upper=null;
            while(limits.find()){BigDecimal value=new BigDecimal(limits.group(1)).multiply("万".equals(limits.group(2))?new BigDecimal("10000"):"亿".equals(limits.group(2))?new BigDecimal("100000000"):BigDecimal.ONE);String before=tier.substring(0,limits.start());int split=Math.max(before.lastIndexOf('，'),before.lastIndexOf(','));before=before.substring(split+1);if(before.contains("小于")||before.contains("以下"))upper=value;else if(before.contains("大于")||before.contains("以上"))lower=value;}
            BigDecimal amount=BigDecimal.valueOf(cents,2);
            if((lower==null||amount.compareTo(lower)>=0)&&(upper==null||amount.compareTo(upper)<0))return rate;
        }
        return null;
    }
    static Long suggest(String rules,long cents){BigDecimal rate=rate(rules,cents);return rate==null?null:BigDecimal.valueOf(cents).multiply(rate).setScale(0,RoundingMode.HALF_UP).longValueExact();}
    private PurchaseFees(){}
}
