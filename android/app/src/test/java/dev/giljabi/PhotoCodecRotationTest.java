package dev.giljabi;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoCodecRotationTest {
    private byte[] fixture(){
        Bitmap b=Bitmap.createBitmap(2,3,Bitmap.Config.ARGB_8888);
        b.setPixels(new int[]{Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW,Color.CYAN,Color.MAGENTA},0,2,0,0,2,3);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));b.recycle();return out.toByteArray();
    }
    @Test public void espTurnsClockwiseAndSwapsDimensions()throws Exception{
        Bitmap result=PhotoCodec.decodeEsp(new ByteArrayInputStream(fixture()));
        try{
            assertEquals(3,result.getWidth());assertEquals(2,result.getHeight());
            int[] actual=new int[6];result.getPixels(actual,0,3,0,0,3,2);
            assertArrayEquals(new int[]{Color.CYAN,Color.BLUE,Color.RED,Color.MAGENTA,Color.YELLOW,Color.GREEN},actual);
        }finally{result.recycle();}
    }
    @Test public void ordinaryPhotosKeepTheirOwnOrientation()throws Exception{
        Bitmap result=PhotoCodec.decode(new ByteArrayInputStream(fixture()),1);
        try{assertEquals(2,result.getWidth());assertEquals(3,result.getHeight());assertEquals(Color.RED,result.getPixel(0,0));assertEquals(Color.MAGENTA,result.getPixel(1,2));}
        finally{result.recycle();}
    }
}
