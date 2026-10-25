package cn.yueji.balance;

import java.nio.file.*;
import java.util.*;

/** Generates the current templates; never rewrites the author's historical workbook. */
public final class GenerateDataFiles {
    public static void main(String[] args)throws Exception{
        Path docs=Paths.get(args.length>0?args[0]:"docs");
        Files.write(docs.resolve("Yueji-收支报销模板.xlsx"),HistoryImport.templateXlsx());
        Files.write(docs.resolve("Yueji-基金模板.xlsx"),FundSheet.templateXlsx());
        Xlsx.Sheet sheet=new Xlsx.Sheet("基金").widths(30,12,20,14,12).money(3,4).add(FundSheet.HEADER.split(","))
            .add("华夏成长混合","000001","2026/09/10 10:30","1000.00","1.50")
            .add("易方达消费行业股票","110022","2026/09/15 14:20","2000.00","3.00");
        Files.write(docs.resolve("测试-基金导入.xlsx"),Xlsx.write(Collections.singletonList(sheet)));
        System.out.println("Templates generated. The existing historical workbook was left untouched.");
    }
}
