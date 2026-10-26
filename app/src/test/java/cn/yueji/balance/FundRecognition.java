package cn.yueji.balance;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Pure parsing of OCR lines grouped by purchase rows; closed/cancelled rows never become candidates. */
final class FundRecognition {
    static final class Line {
        final String text;final int left,top,right,bottom;
        Line(String text,int left,int top,int right,int bottom){this.text=text;this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    }
    static final class Row {
        String name="",code="",problem="";
        LocalDateTime at;
        long amount,fee;
        boolean selected=true;
        boolean added,verified,needsReview;
        String source="";
        final List<FundClient.Match> matches=new ArrayList<>();
        FundSheet.Row asImport(){
            if(name.trim().isEmpty()||name.length()>80)throw new IllegalArgumentException("请补全基金名称");
            if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("请确认 "+name+" 对应的六位基金代码");
            if(at==null||at.getYear()<2000||at.isAfter(FundEngine.now()))throw new IllegalArgumentException("请校正 "+name+" 的购买时间");
            if(amount<=0||amount>Ledger.MAX_MONEY||fee<0||fee>=amount)throw new IllegalArgumentException("请校正 "+name+" 的金额与手续费");
            FundSheet.Row row=new FundSheet.Row();row.name=name;row.code=code;row.at=at.withSecond(0).withNano(0);row.amount=amount;row.fee=fee;return row;
        }
    }
    static final class Result {
        final List<Row> rows=new ArrayList<>();int excluded;
    }
    static final Pattern DATE=Pattern.compile("(20\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})日?\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})(?:\\s*[:：]\\s*(\\d{2}))?");
    static final Pattern MONEY=Pattern.compile("(?:[¥￥]\\s*)?([0-9]+(?:[,，][0-9]{3})*[.．][0-9]{2})\\s*(?:[元圆]\\s*)*");
    static Result parse(List<Line> input){
        List<Line> sorted=new ArrayList<>(input);sorted.sort(Comparator.comparingInt((Line l)->l.top).thenComparingInt(l->l.left));
        List<Line> lines=new ArrayList<>();
        for(int i=0;i<sorted.size();){
            Line first=sorted.get(i++);List<Line> band=new ArrayList<>();band.add(first);
            int center=(first.top+first.bottom)/2,tolerance=Math.max(4,(first.bottom-first.top)/2);
            while(i<sorted.size()&&Math.abs((sorted.get(i).top+sorted.get(i).bottom)/2-center)<=tolerance)band.add(sorted.get(i++));
            band.sort(Comparator.comparingInt(l->l.left));StringBuilder joined=new StringBuilder();int right=first.right,bottom=first.bottom;
            for(Line item:band){if(joined.length()>0)joined.append(' ');joined.append(item.text);right=Math.max(right,item.right);bottom=Math.max(bottom,item.bottom);}
            lines.add(new Line(joined.toString(),first.left,first.top,right,bottom));
        }
        Result result=new Result();List<Integer> starts=new ArrayList<>();
        for(int i=0;i<lines.size();i++)if(compact(lines.get(i).text).startsWith("买入"))starts.add(i);
        if(starts.isEmpty())return result;
        for(int n=0;n<starts.size();n++){
            Line anchor=lines.get(starts.get(n));int end=n+1<starts.size()?lines.get(starts.get(n+1)).top:Integer.MAX_VALUE;
            List<Line> block=new ArrayList<>();
            int tolerance=Math.max(4,(anchor.bottom-anchor.top)/2);
            for(Line line:lines)if(line.top>=anchor.top-tolerance&&line.top<end-tolerance)block.add(line);
            String all=compact(join(block));
            if(all.contains("交易关闭")||all.contains("交易失败")||all.contains("已撤销")||all.contains("已取消")){result.excluded++;continue;}
            Row row=new Row();StringBuilder name=new StringBuilder();
            for(Line line:block){
                Matcher date=DATE.matcher(line.text);
                if(date.find()){
                    try{row.at=LocalDateTime.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3)),Integer.parseInt(date.group(4)),Integer.parseInt(date.group(5)));}catch(DateTimeException ignored){}
                }
                Matcher amount=MONEY.matcher(line.text);
                if(!date.find(0)&&amount.find()){
                    try{row.amount=Ledger.parseMoney(amount.group(1).replace(",","").replace("，","").replace('．','.'),true);}catch(IllegalArgumentException ignored){}
                }
                String clean=line.text.replaceFirst("^\\s*买\\s*入\\s*","").replaceFirst("^\\s*基\\s*金\\s*[|｜丨Il1]?\\s*","");
                clean=DATE.matcher(clean).replaceAll("");clean=MONEY.matcher(clean).replaceAll("").replaceAll("[¥￥|｜丨]","").trim();
                if(!clean.isEmpty()&&!clean.matches("[\\d\\s:：.,，年月日-]+")&&!clean.contains("交易成功")&&!clean.contains("确认中")&&!clean.contains("没有更多")&&!clean.contains("下单"))
                    name.append(clean);
            }
            row.name=name.toString().replaceAll("\\s+","");
            if(row.name.isEmpty())row.problem="名称未识别，请编辑";
            if(row.at==null)row.problem="时间未识别，请编辑";
            if(row.amount<=0)row.problem="金额未识别，请编辑";
            result.rows.add(row);
        }
        return result;
    }
    static String compact(String value){return value.replaceAll("\\s+","");}
    static String normalize(String name){return compact(name).replace('（','(').replace('）',')').replace("基金","").toUpperCase(Locale.ROOT);}
    private static String join(List<Line> lines){StringBuilder text=new StringBuilder();for(Line line:lines)text.append(line.text).append('\n');return text.toString();}
    private FundRecognition(){}
}
