package cn.yueji.balance;

import java.util.*;

/** Template validation shared by both importers. Never guesses a missing column or silently drops an unknown sheet. */
final class SheetSchema {
    static List<Xlsx.Sheet> read(byte[] file, Set<String> allowed) {
        List<Xlsx.Sheet> sheets=Xlsx.readStrict(file);
        Set<String> names=new HashSet<>();
        for(Xlsx.Sheet sheet:sheets){
            if(!names.add(sheet.name))throw new IllegalArgumentException("工作表名称重复："+sheet.name);
            if(!sheet.name.equals("说明")&&!allowed.contains(sheet.name))
                throw new IllegalArgumentException("不支持工作表「"+sheet.name+"」，请使用对应的 xlsx 模板，不要修改表名");
        }
        return sheets;
    }

    static Map<String,Integer> header(Xlsx.Sheet sheet,String header) {
        String where="「"+sheet.name+"」表第1行：";
        String[] names=header.split(",");
        if(sheet.rows.isEmpty())throw new IllegalArgumentException(where+"缺少表头，须为 "+header);
        List<String> row=sheet.rows.get(0);
        if(row.size()!=names.length)throw new IllegalArgumentException(where+"表头列数须为 "+names.length+"，完整顺序为 "+header);
        Map<String,Integer> columns=new LinkedHashMap<>();
        for(int i=0;i<names.length;i++){
            if(!names[i].equals(row.get(i)))throw new IllegalArgumentException(where+Xlsx.column(i)+"列表头须为「"+names[i]+"」，请勿删列、改名或调整顺序");
            columns.put(names[i],i);
        }
        return columns;
    }

    static boolean blank(List<String> row) {
        for(String value:row)if(!value.trim().isEmpty())return false;
        return true;
    }

    static void width(List<String> row,int columns) {
        for(int i=columns;i<row.size();i++)
            if(!row.get(i).trim().isEmpty())throw new IllegalArgumentException(Xlsx.column(i)+"列超出模板范围");
    }

    static void empty(List<String> row,Map<String,Integer> columns,String... names) {
        for(String name:names){int i=columns.get(name);if(i<row.size()&&!row.get(i).trim().isEmpty())
            throw new IllegalArgumentException(name+"列须留空");}
    }

    private SheetSchema(){}
}
