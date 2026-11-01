package cn.yueji.balance;

import java.util.*;

/** All is the uncategorized inbox, with explicit user additions allowed. */
final class WatchGroups {
    static final String LIMITED="定投";
    static boolean explicit(Ledger.Data data,String code){for(Ledger.WatchFund f:data.watchFunds)if(f.code.equals(code))return true;return false;}
    static FundProfile profile(Ledger.Data data,String code){FundProfile result=new FundProfile();for(Ledger.Fund f:data.funds)if(!f.removed()&&f.code.equals(code))result.merge(f.profile);for(Ledger.WatchFund f:data.watchFunds)if(f.code.equals(code))result.merge(f.profile);return result;}
    static void reconcile(Ledger.Data data){
        Set<String> known=MarketRefresh.codes(data);data.watchLimitClassified.retainAll(known);
        for(String code:known){if(profile(data,code).limited()){
            // Only the first classification removes old groups; later manual additions must survive refresh.
            if(data.watchLimitClassified.add(code)){data.watchAllIncluded.remove(code);for(Ledger.WatchGroup g:data.watchGroups)g.codes.remove(code);}
        }else data.watchLimitClassified.remove(code);}
    }
    static void migrateReserved(Ledger.Data data){for(Ledger.WatchGroup group:data.watchGroups)if(LIMITED.equals(group.name)){String base="定投(原分组)",name=base;int n=2;boolean used=true;while(used){used=false;for(Ledger.WatchGroup g:data.watchGroups)if(g.name.equals(name)){used=true;name="定投(原分组"+(n++)+")";break;}}group.name=name;}}
    static boolean held(Ledger.Data data,String code){for(Ledger.Fund f:data.funds)if(!f.removed()&&f.code.equals(code))return true;return false;}
    static boolean member(Ledger.Data data,String code,String name){
        if("持有".equals(name))return held(data,code)&&!profile(data,code).limited();
        if(LIMITED.equals(name))return profile(data,code).limited();
        if("全部".equals(name)){
            if(data.watchAllIncluded.contains(code))return true;
            if(held(data,code)||profile(data,code).limited())return false;
            for(Ledger.WatchGroup g:data.watchGroups)if(g.codes.contains(code))return false;
            return true;
        }
        for(Ledger.WatchGroup g:data.watchGroups)if(g.name.equals(name))return g.codes.contains(code);
        return false;
    }
    static void move(Ledger.Data data,String code,String source,long target,boolean remove){
        if(target==-1)throw new IllegalArgumentException("定投是自动限购分组，不能手动加入");
        if(target==0)data.watchAllIncluded.add(code);
        else {boolean found=false;for(Ledger.WatchGroup g:data.watchGroups)if(g.id==target){g.codes.add(code);found=true;}if(!found)throw new IllegalArgumentException("分组不存在");}
        if(remove){
            if("全部".equals(source)&&target!=0)data.watchAllIncluded.remove(code);
            for(Ledger.WatchGroup g:data.watchGroups)if(g.name.equals(source)&&g.id!=target)g.codes.remove(code);
        }else if("全部".equals(source)&&target!=0)data.watchAllIncluded.add(code);
    }
    private WatchGroups(){}
}
