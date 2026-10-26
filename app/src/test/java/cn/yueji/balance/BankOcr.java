package cn.yueji.balance;

import java.time.*;
import java.util.*;
import java.util.regex.*;

final class BankOcr {
    static final Pattern MONEY=Pattern.compile("[+＋\\-−－]?\\s*[¥￥]?\\s*\\d+(?:[,，]\\d{3})*[.．]\\d{2}(?!\\d)");
    static BankLedger.Draft parse(List<FundRecognition.Line> lines){
        BankLedger.Draft draft=new BankLedger.Draft();FundRecognition.Line main=null;
        for(FundRecognition.Line line:lines){
            String text=line.text;
            int label=text.indexOf("代销");if(label<0)label=text.indexOf("代銷");
            if(label>=0){String name=text.substring(label+2).replaceFirst("^[\\s|｜丨Il1]+","").trim();if(!name.isEmpty())draft.name=name;}
            Matcher matcher=MONEY.matcher(text);
            if(matcher.find()&&!text.contains("%")&&!text.contains("％")&&signed(matcher.group())>=0&&(main==null||line.bottom-line.top>main.bottom-main.top)){main=line;draft.balance=signed(matcher.group());}
        }
        if(draft.name.isEmpty()||draft.balance==null)throw new IllegalArgumentException("请使用同时包含产品名称与总金额的截图");
        return draft;
    }
    static BankLedger.Draft income(List<FundRecognition.Line> lines){
        BankLedger.Draft result=new BankLedger.Draft();result.weekly=nearLabel(lines,"本周收益",null);result.cumulative=nearLabel(lines,"累计收益",null);
        Pattern date=Pattern.compile("(20\\d{2})[年/.-]\\s*(\\d{1,2})[月/.-]\\s*(\\d{1,2})日?");
        for(FundRecognition.Line label:lines){
            Matcher matcher=date.matcher(label.text);if(!matcher.find())continue;
            try{
                LocalDate day=LocalDate.of(Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2)),Integer.parseInt(matcher.group(3)));
                String without=label.text.substring(matcher.end());Matcher inline=MONEY.matcher(without);Long value=inline.find()?signed(inline.group()):numberRight(lines,label);
                if(value!=null)result.daily.put(day,value);
            }catch(DateTimeException ignored){}
        }
        if(result.cumulative==null||result.daily.isEmpty())throw new IllegalArgumentException("请上传收益明细截图，完整包含累计收益和带年月日的每日收益；金额首页不能替代收益明细");
        LocalDate last=result.daily.lastKey();if(last.isAfter(FundEngine.now().toLocalDate()))throw new IllegalArgumentException("截图收益日期晚于今天");
        int year=last.getYear();
        Pattern range=Pattern.compile("(\\d{1,2})[.月]\\s*(\\d{1,2})\\s*[-–—]\\s*(\\d{1,2})[.月]\\s*(\\d{1,2})");
        for(FundRecognition.Line label:lines){
            Matcher matcher=range.matcher(label.text);if(!matcher.find())continue;
            try{
                int month=Integer.parseInt(matcher.group(1)),endMonth=Integer.parseInt(matcher.group(3));
                LocalDate start=LocalDate.of(month>endMonth&&last.getMonthValue()==1?year-1:year,month,Integer.parseInt(matcher.group(2)));
                Long amount=numberBelow(lines,label,null);if(amount!=null&&!start.isAfter(BankLedger.monday(last)))result.weeks.put(start,amount);
            }catch(DateTimeException ignored){}
        }
        if(result.weekly!=null)result.weeks.put(BankLedger.monday(last),result.weekly);
        result.at=last.atTime(23,59);if(result.at.isAfter(FundEngine.now()))result.at=FundEngine.now();
        long weekSum=0;LocalDate first=BankLedger.monday(last);for(Map.Entry<LocalDate,Long> day:result.daily.entrySet())if(!day.getKey().isBefore(first))weekSum=Math.addExact(weekSum,day.getValue());
        if(result.weekly!=null&&!result.daily.firstKey().isAfter(first)&&weekSum!=result.weekly)
            throw new IllegalArgumentException("本周每日收益合计与本周收益不符，请使用清晰且完整的收益明细截图");
        return result;
    }
    static Long numberRight(List<FundRecognition.Line> lines,FundRecognition.Line anchor){
        FundRecognition.Line best=null;float score=Float.MAX_VALUE;
        for(FundRecognition.Line line:lines)if(line!=anchor&&line.left>anchor.right-10&&Math.abs((line.top+line.bottom-anchor.top-anchor.bottom)/2f)<Math.max(16,anchor.bottom-anchor.top)){
            if(line.text.contains("%"))continue;Matcher m=MONEY.matcher(line.text);if(!m.find())continue;
            float distance=line.left-anchor.right;if(distance<score){best=line;score=distance;}
        }return best==null?null:firstMoney(best.text);
    }
    static Long numberBelow(List<FundRecognition.Line> lines,FundRecognition.Line anchor,FundRecognition.Line exclude){
        FundRecognition.Line best=null;float score=Float.MAX_VALUE;
        for(FundRecognition.Line line:lines){
            if(line==anchor||line==exclude||line.text.contains("%"))continue;Matcher matcher=MONEY.matcher(line.text);if(!matcher.find())continue;
            float dx=Math.abs((line.left+line.right-anchor.left-anchor.right)/2f),dy=line.top-anchor.bottom;
            if(dy< -8||dy>Math.max(180,(anchor.bottom-anchor.top)*4)||dx>Math.max(120,(anchor.right-anchor.left)*.8f))continue;
            if(dx+Math.abs(dy)<score){score=dx+Math.abs(dy);best=line;}
        }return best==null?null:firstMoney(best.text);
    }
    static Long nearLabel(List<FundRecognition.Line> lines,String name,FundRecognition.Line main){
        for(FundRecognition.Line label:lines)if(FundRecognition.compact(label.text).contains(name)){
            Matcher inline=MONEY.matcher(label.text);if(inline.find())return signed(inline.group());
            Long below=numberBelow(lines,label,main);if(below!=null)return below;
            FundRecognition.Line best=null;float distance=Float.MAX_VALUE;
            for(FundRecognition.Line number:lines){
                if(number==main||number.text.contains("%")||!MONEY.matcher(number.text).find())continue;
                float dx=Math.abs((number.left+number.right-label.left-label.right)/2f),dy=label.top-number.bottom;
                if(dy< -10||dy>Math.max(150,(label.bottom-label.top)*4)||dx>Math.max(120,(label.right-label.left)*.8f))continue;
                if(dx+Math.abs(dy)<distance){best=number;distance=dx+Math.abs(dy);}
            }if(best!=null)return firstMoney(best.text);
        }return null;
    }
    static Long firstMoney(String text){Matcher m=MONEY.matcher(text);return m.find()?signed(m.group()):null;}
    static long signed(String raw){
        String clean=raw.replaceAll("[\\s¥￥,，]","").replace('．','.').replace('−','-').replace('－','-').replace('＋','+');
        boolean negative=clean.startsWith("-");clean=clean.replaceFirst("^[+-]","");long value=Ledger.parseMoney(clean,false);return negative?-value:value;
    }
    private BankOcr(){}
}
