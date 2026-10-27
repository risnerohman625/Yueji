package cn.yueji.balance;

import android.app.AlertDialog;
import android.text.*;
import android.widget.*;
import java.util.*;

/** Manual edits, including clearing the field, always take priority over asynchronous suggestions. */
final class PurchaseFeeBinding {
    final MainActivity m;final EditText code,name,amount,fee;final TextView hint;
    final Map<String,FundProfile> profiles=new HashMap<>();final Map<String,String> names=new HashMap<>();
    boolean manual,writing;String attempted="";int generation;AlertDialog dialog;
    PurchaseFeeBinding(MainActivity m,EditText code,EditText name,EditText amount,EditText fee,TextView hint){
        this.m=m;this.code=code;this.name=name;this.amount=amount;this.fee=fee;this.hint=hint;
        watch(fee,()->{if(!writing)manual=true;});watch(amount,this::suggest);watch(code,this::lookup);
    }
    void watch(EditText view,Runnable action){view.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){action.run();}public void afterTextChanged(Editable e){}});}
    void start(AlertDialog dialog){this.dialog=dialog;lookup();}
    void lookup(){
        String symbol=code.getText().toString().trim();generation++;if(!symbol.matches("[0-9]{6}"))return;
        Ledger.WatchFund cached=m.watchScreens.find(symbol);if(cached!=null){names.put(symbol,cached.name);profiles.put(symbol,cached.profile);name.setText(cached.name);}
        suggest();if(profiles.containsKey(symbol)&&!profiles.get(symbol).buyRules.isEmpty()||symbol.equals(attempted))return;
        attempted=symbol;final int token=generation;final String knownName=names.get(symbol);hint.setText("正在查询买入优惠费率，可随时手动填写手续费");
        new Thread(()->{try{String title=knownName;if(title==null){for(FundClient.Match match:FundClient.search(symbol))if(symbol.equals(match.code)){title=match.name;break;}}if(title==null)throw new IllegalArgumentException("基金不存在");FundProfile profile=FundClient.profile(symbol,title);String resolved=title;m.runOnUiThread(()->{if(!live(token,symbol))return;names.put(symbol,resolved);profiles.put(symbol,profile);name.setText(resolved);suggest();});}catch(Exception e){m.runOnUiThread(()->{if(live(token,symbol))hint.setText("未取得优惠费率，请手动核对手续费；可继续保存");});}},"purchase-fee-profile").start();
    }
    boolean live(int token,String symbol){return token==generation&&!m.isDestroyed()&&dialog!=null&&dialog.isShowing()&&symbol.equals(code.getText().toString().trim());}
    void suggest(){
        if(manual)return;FundProfile profile=profiles.get(code.getText().toString().trim());if(profile==null)return;
        try{long gross=Ledger.parseMoney(amount.getText().toString(),true);Long value=PurchaseFees.suggest(profile.buyRules,gross);if(value==null){hint.setText("费率未知或为固定费用，请手动填写手续费");return;}writing=true;fee.setText(Ledger.decimal(value));hint.setText("按优惠费率 × 买入金额估算，可删除或修改，以实际平台为准");}catch(Exception ignored){}finally{writing=false;}
    }
}
