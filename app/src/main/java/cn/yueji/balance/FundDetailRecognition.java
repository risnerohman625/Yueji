package cn.yueji.balance;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** One completed purchase-detail page per image. Values are tied to labels, never to the largest number. */
final class FundDetailRecognition {
    static FundRecognition.Row parse(List<FundRecognition.Line> raw){
        List<FundRecognition.Line> lines=bands(raw);
        StringBuilder all=new StringBuilder();for(FundRecognition.Line line:lines)all.append(labelText(line.text)).append('\n');
        String page=all.toString();
        if(page.contains("交易关闭")||page.contains("交易失败")||page.contains("已撤销")||page.contains("已取消")||
            !(page.contains("买入产品")&&page.contains("买入金额")&&(page.contains("买入成功")||page.contains("确认信息"))))
            throw new IllegalArgumentException("不是已成功的基金买入详情页，请查看标准截图方式");
        String name=field(lines,"买入产品"),amount=field(lines,"买入金额"),fee=field(lines,"手续费"),time=field(lines,"买入时间");
        name=name.replaceAll("[>›〉》]+$","").trim();
        FundRecognition.Row row=new FundRecognition.Row();List<String> missing=new ArrayList<>();
        row.name=name;
        Set<String> codes=new LinkedHashSet<>();
        for(FundRecognition.Line line:raw){String t=labelText(line.text);if(t.contains("订单")||t.contains("金额")||t.contains("手续费"))continue;Matcher code=Pattern.compile("(?<![0-9.])([0-9]{6})(?![0-9])").matcher(t);while(code.find())codes.add(code.group(1));}
        if(codes.size()==1)row.code=codes.iterator().next();else missing.add("六位基金代码");
        Matcher date=FundRecognition.DATE.matcher(time);
        try{
            if(!date.find())throw new DateTimeException("missing");
            row.at=LocalDateTime.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3)),Integer.parseInt(date.group(4)),Integer.parseInt(date.group(5)));
            if(row.at.isAfter(FundEngine.now()))throw new DateTimeException("future");
        }catch(DateTimeException e){row.at=null;missing.add("买入时间");}
        try{row.amount=money(amount);if(row.amount<=0)missing.add("买入金额");}catch(IllegalArgumentException e){missing.add("买入金额");}
        try{row.fee=money(fee);if(row.fee>=row.amount)missing.add("手续费");}catch(IllegalArgumentException e){missing.add("手续费");}
        row.needsReview=!missing.isEmpty();
        if(row.needsReview)row.problem="详情已识别，以下字段需手动核对："+String.join("、",missing);
        return row;
    }
    static List<FundRecognition.Line> bands(List<FundRecognition.Line> raw){
        List<List<FundRecognition.Line>> bands=new ArrayList<>();
        List<FundRecognition.Line> sorted=new ArrayList<>(raw);sorted.sort(Comparator.comparingInt(l->l.top));
        for(FundRecognition.Line line:sorted){
            List<FundRecognition.Line> found=null;float center=(line.top+line.bottom)/2f;
            for(List<FundRecognition.Line> band:bands){
                FundRecognition.Line first=band.get(0);float tolerance=Math.max(5,Math.min(line.bottom-line.top,first.bottom-first.top)*.55f);
                if(Math.abs(center-(first.top+first.bottom)/2f)<=tolerance){found=band;break;}
            }
            if(found==null){found=new ArrayList<>();bands.add(found);}found.add(line);
        }
        List<FundRecognition.Line> result=new ArrayList<>();
        for(List<FundRecognition.Line> band:bands){
            band.sort(Comparator.comparingInt(l->l.left));StringBuilder text=new StringBuilder();
            int left=Integer.MAX_VALUE,top=left,right=0,bottom=0;
            for(FundRecognition.Line l:band){text.append(l.text).append(' ');left=Math.min(left,l.left);top=Math.min(top,l.top);right=Math.max(right,l.right);bottom=Math.max(bottom,l.bottom);}
            result.add(new FundRecognition.Line(text.toString(),left,top,right,bottom));
        }
        result.sort(Comparator.comparingInt(l->l.top));return result;
    }
    // Normalize labels only. Product names must retain every character for identity verification.
    static String labelText(String text){return FundRecognition.compact(text).replaceFirst("^[|｜丨]+","").replace("买人","买入").replace("金額","金额").replace("全額","金额").replace("全额","金额").replace("手績费","手续费");}
    static String field(List<FundRecognition.Line> lines,String label){
        for(FundRecognition.Line anchor:lines){
            String compact=labelText(anchor.text);int at=compact.indexOf(label);if(at<0)continue;
            if(!compact.startsWith(label))continue;
            String original=FundRecognition.compact(anchor.text).replaceFirst("^[|｜丨]+","");
            String suffix=original.substring(at+label.length()).replaceFirst("^[：:]","");
            List<FundRecognition.Line> band=new ArrayList<>();int center=(anchor.top+anchor.bottom)/2,height=anchor.bottom-anchor.top;
            for(FundRecognition.Line line:lines)if(line!=anchor&&line.left>=anchor.right-8&&Math.abs((line.top+line.bottom)/2-center)<Math.max(12,height*.8f))band.add(line);
            band.sort(Comparator.comparingInt(l->l.left));StringBuilder value=new StringBuilder(suffix);
            for(FundRecognition.Line item:band)value.append(FundRecognition.compact(item.text));
            return value.toString();
        }
        return "";
    }
    static long money(String text){
        Matcher matcher=FundRecognition.MONEY.matcher(text);String last=null;while(matcher.find()){
            String value=matcher.group(1).replace(",","").replace("，","").replace('．','.');
            if(last!=null&&!last.equals(value))throw new IllegalArgumentException("同一金额字段有多个不同数值，请手动核对");
            last=value;
        }
        if(last==null)throw new IllegalArgumentException("金额或手续费未识别到，请查看标准截图方式");
        return Ledger.parseMoney(last.replace(",","").replace("，","").replace('．','.'),false);
    }
    static boolean exact(String recognized,String official){return recognized.equals(official);}
    static boolean duplicate(FundRecognition.Row row,Ledger.Data data){
        for(Ledger.Fund f:data.funds)if(f.code.equals(row.code))for(Ledger.FundBuy b:f.buys)if(b.at.withSecond(0).withNano(0).equals(row.at.withSecond(0).withNano(0)))return true;
        return false;
    }
    static int distance(String a,String b){
        int[] previous=new int[b.length()+1];for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){int[] current=new int[b.length()+1];current[0]=i;for(int j=1;j<=b.length();j++)current[j]=Math.min(Math.min(previous[j]+1,current[j-1]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));previous=current;}
        return previous[b.length()];
    }
    private FundDetailRecognition(){}
}
