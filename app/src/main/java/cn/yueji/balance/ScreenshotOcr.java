package cn.yueji.balance;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.io.*;
import java.util.*;
import java.util.function.*;

/** Bundled Chinese OCR runs on the device; the screenshot is never uploaded. */
final class ScreenshotOcr {
    static void bitmap(Bitmap bitmap,Consumer<List<FundRecognition.Line>> success,Consumer<Exception> failure){
        TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(text->{
            List<FundRecognition.Line> lines=new ArrayList<>();for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
                Rect r=line.getBoundingBox();if(r!=null)lines.add(new FundRecognition.Line(line.getText(),r.left,r.top,r.right,r.bottom));
            }success.accept(lines);
        }).addOnFailureListener(failure::accept).addOnCompleteListener(task->recognizer.close());
    }
    static void recognize(Context context,Uri uri,Consumer<List<FundRecognition.Line>> success,Consumer<Exception> failure){
        TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
            if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>24_000_000L)throw new IOException("请使用清晰且不超过2400万像素的交易截图");
            InputImage image=InputImage.fromFilePath(context,uri);
            recognizer.process(image).addOnSuccessListener(text->{
                List<FundRecognition.Line> lines=new ArrayList<>();
                for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
                    Rect r=line.getBoundingBox();if(r!=null)lines.add(new FundRecognition.Line(line.getText(),r.left,r.top,r.right,r.bottom));
                }
                success.accept(lines);
            }).addOnFailureListener(failure::accept).addOnCompleteListener(task->recognizer.close());
        }catch(Exception e){recognizer.close();failure.accept(e);}
    }
    private ScreenshotOcr(){}
}
