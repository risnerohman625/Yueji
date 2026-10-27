package cn.yueji.balance;

import java.util.*;

/** All is the uncategorized inbox, with explicit user additions allowed. */
final class WatchGroups {
    static boolean held(Ledger.Data data,String code){for(Ledger.Fund f:data.funds)if(!f.removed()&&f.code.equals(code))return true;return false;}
    static boolean member(Ledger.Data data,String code,String name){
        if("持有".equals(name))return held(data,code);
        if("全部".equals(name)){
            if(data.watchAllIncluded.contains(code))return true;
            if(held(data,code))return false;
            for(Ledger.WatchGroup g:data.watchGroups)if(g.codes.contains(code))return false;
            return true;
        }
        for(Ledger.WatchGroup g:data.watchGroups)if(g.name.equals(name))return g.codes.contains(code);
        return false;
    }
    static void move(Ledger.Data data,String code,String source,long target,boolean remove){
        if(target==0)data.watchAllIncluded.add(code);
        else {boolean found=false;for(Ledger.WatchGroup g:data.watchGroups)if(g.id==target){g.codes.add(code);found=true;}if(!found)throw new IllegalArgumentException("分组不存在");}
        if(remove){
            if("全部".equals(source)&&target!=0)data.watchAllIncluded.remove(code);
            for(Ledger.WatchGroup g:data.watchGroups)if(g.name.equals(source)&&g.id!=target)g.codes.remove(code);
        }else if("全部".equals(source)&&target!=0)data.watchAllIncluded.add(code);
    }
    private WatchGroups(){}
}
