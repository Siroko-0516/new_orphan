package dev.giljabi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.io.*;

/** Bounded photo decoding shared by foreground UI and monitoring service. */
final class PhotoCodec {
    static Bitmap decode(InputStream input,int orientation) throws IOException {
        if(input==null)throw new IOException("사진을 읽을 수 없습니다.");
        // Bounded encoded data; decode sampled pixels rather than a full camera-resolution bitmap.
        ByteArrayOutputStream encodedStream=new ByteArrayOutputStream();byte[] block=new byte[16384];int size;
        while((size=input.read(block))!=-1){if(encodedStream.size()+size>20*1024*1024)throw new IOException("20MB 이하 사진을 선택하세요.");encodedStream.write(block,0,size);}
        byte[] encoded=encodedStream.toByteArray();
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        if(options.outWidth<=0||options.outHeight<=0||Math.max(options.outWidth,options.outHeight)>30000)throw new IOException("지원하지 않는 사진입니다.");
        options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1536)options.inSampleSize*=2;
        Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);if(decoded==null)throw new IOException("사진 디코딩 실패");
        Matrix matrix=new Matrix();if(orientation==6)matrix.postRotate(90);else if(orientation==3)matrix.postRotate(180);else if(orientation==8)matrix.postRotate(270);else if(orientation==2)matrix.postScale(-1,1);else if(orientation==4)matrix.postScale(1,-1);else if(orientation==5){matrix.postRotate(90);matrix.postScale(-1,1);}else if(orientation==7){matrix.postRotate(270);matrix.postScale(-1,1);}
        Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);if(rotated!=decoded)decoded.recycle();
        float scale=Math.min(1f,768f/Math.max(rotated.getWidth(),rotated.getHeight()));Bitmap resized=Bitmap.createScaledBitmap(rotated,Math.max(1,Math.round(rotated.getWidth()*scale)),Math.max(1,Math.round(rotated.getHeight()*scale)),true);if(resized!=rotated)rotated.recycle();return resized;
    }
}
