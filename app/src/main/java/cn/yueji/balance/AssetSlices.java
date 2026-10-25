package cn.yueji.balance;

import java.util.*;

/** Only drawing angles are enlarged; labels and tooltips always use the actual amounts. */
final class AssetSlices {
    final String[] names;final long[] amounts;final float[] sweeps;final long total;
    AssetSlices(Map<String,Long> values){
        List<String> labels=new ArrayList<>();List<Long> money=new ArrayList<>();long sum=0;
        for(Map.Entry<String,Long> e:values.entrySet())if(e.getValue()>0){labels.add(e.getKey());money.add(e.getValue());sum=Math.addExact(sum,e.getValue());}
        total=sum;names=labels.toArray(new String[0]);amounts=new long[money.size()];sweeps=new float[money.size()];
        float min=Math.min(3f,money.isEmpty()?3f:180f/money.size());double remaining=360;long remainingMoney=sum;
        boolean[] fixed=new boolean[money.size()];boolean changed;
        do{
            changed=false;
            for(int i=0;i<money.size();i++)if(!fixed[i]&&money.get(i)*remaining/remainingMoney<min){
                fixed[i]=true;sweeps[i]=min;remaining-=min;remainingMoney-=money.get(i);changed=true;
            }
        }while(changed&&remainingMoney>0);
        for(int i=0;i<money.size();i++){amounts[i]=money.get(i);if(!fixed[i])sweeps[i]=(float)(amounts[i]*remaining/remainingMoney);}
    }
    double percent(int i){return amounts[i]*100d/total;}
    int extreme(boolean largest){
        int best=-1;for(int i=0;i<amounts.length;i++)if(best<0||(largest?amounts[i]>amounts[best]:amounts[i]<amounts[best]))best=i;return best;
    }
}
