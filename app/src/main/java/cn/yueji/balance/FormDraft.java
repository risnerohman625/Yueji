package cn.yueji.balance;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.time.LocalDateTime;

/** Keeps an unfinished record intact while visiting label management or rotating the device. */
final class FormDraft {
    final Bundle values;
    FormDraft(){values=new Bundle();}
    FormDraft(Bundle values){this.values=values;}
    void capture(View v){
        String key=v.getContentDescription()==null?null:v.getContentDescription().toString();
        if(key!=null){
            if(v instanceof EditText)values.putString("text:"+key,((EditText)v).getText().toString());
            else if(v instanceof Spinner){Object item=((Spinner)v).getSelectedItem();if(item!=null)values.putString("choice:"+key,item.toString());}
            else if(v.getTag() instanceof MainActivity.DateControl)values.putString("date:"+key,((MainActivity.DateControl)v.getTag()).at.toString());
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)capture(g.getChildAt(i));}
    }
    void restore(View v){
        String key=v.getContentDescription()==null?null:v.getContentDescription().toString();
        if(key!=null){
            if(v instanceof EditText&&values.containsKey("text:"+key))((EditText)v).setText(values.getString("text:"+key));
            else if(v instanceof Spinner&&values.containsKey("choice:"+key)){
                Spinner s=(Spinner)v;String value=values.getString("choice:"+key);for(int i=0;i<s.getCount();i++)if(value.equals(s.getItemAtPosition(i).toString())){s.setSelection(i);break;}
            }else if(v.getTag() instanceof MainActivity.DateControl&&values.containsKey("date:"+key)){
                MainActivity.DateControl date=(MainActivity.DateControl)v.getTag();date.at=LocalDateTime.parse(values.getString("date:"+key));date.update();
            }
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)restore(g.getChildAt(i));}
    }
}
