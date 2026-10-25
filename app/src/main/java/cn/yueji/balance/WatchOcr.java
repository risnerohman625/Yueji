package cn.yueji.balance;

import java.util.*;
import java.util.regex.*;

/** Code anchors and left-column names from a multi-fund watchlist screenshot. */
final class WatchOcr {
    static final class Row {String code,name;boolean selected=true;Row(String c,String n){code=c;name=n;}}
    static List<Row> parse(List<FundRecognition.Line> input){
        List<FundRecognition.Line> lines=new ArrayList<>(input);lines.sort(Comparator.comparingInt((FundRecognition.Line l)->l.top).thenComparingInt(l->l.left));
        List<FundRecognition.Line> anchors=new ArrayList<>();Pattern code=Pattern.compile("(?<![0-9.])([0-9]{6})(?![0-9])");
        for(FundRecognition.Line l:lines)if(code.matcher(l.text).find())anchors.add(l);
        List<Row> rows=new ArrayList<>();Set<String> seen=new HashSet<>();int previous=0;
        for(FundRecognition.Line anchor:anchors){Matcher m=code.matcher(anchor.text);if(!m.find())continue;String symbol=m.group(1);if(!seen.add(symbol))continue;
            StringBuilder name=new StringBuilder();int height=Math.max(12,anchor.bottom-anchor.top),right=anchor.left+height*19;
            for(FundRecognition.Line l:lines)if(l.top>=Math.max(previous,anchor.top-height*5)&&l.bottom<=anchor.bottom&&l.left<right){
                String value=l.text.trim();if(l==anchor){String prefix=value.substring(0,m.start()).trim();if(!prefix.isEmpty())name.append(prefix);continue;}
                if(value.matches("[0-9\\s.,+%/:：-]+")||value.contains("%")||value.contains("基金名称")||value.contains("全部")||value.contains("历史胜率")||value.contains("规模小")||value.contains("自选"))continue;
                if(value.matches(".*[\\p{IsHan}A-Za-z].*"))name.append(value.replaceAll("\\s+",""));
            }
            rows.add(new Row(symbol,name.toString()));previous=anchor.bottom+1;
        }
        return rows;
    }
    private WatchOcr(){}
}
