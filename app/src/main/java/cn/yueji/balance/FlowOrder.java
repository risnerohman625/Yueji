package cn.yueji.balance;

import java.util.*;

/** Device-local order; older three-card preferences append debt without losing their order. */
final class FlowOrder {
    static int[] read(String saved){
        List<Integer> order=new ArrayList<>();
        if(saved!=null)for(String part:saved.split(",")){
            try{int value=Integer.parseInt(part.trim());if(value>=0&&value<4&&!order.contains(value))order.add(value);}
            catch(NumberFormatException ignored){}
        }
        for(int i=0;i<4;i++)if(!order.contains(i))order.add(i);
        return order.stream().mapToInt(Integer::intValue).toArray();
    }
    static int[] move(int[] order,int from,int to){
        List<Integer> result=new ArrayList<>();for(int value:order)result.add(value);
        result.add(to,result.remove(from));return result.stream().mapToInt(Integer::intValue).toArray();
    }
    static String encode(int[] order){StringJoiner text=new StringJoiner(",");for(int value:order)text.add(""+value);return text.toString();}
    private FlowOrder(){}
}
