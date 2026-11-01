package dev.giljabi;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** HWUI can retain an old frame beyond the next UI update. It must remain drawable. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewLifetimeTest {
    @Test public void previousDisplayedBitmapSurvivesMultipleReplacements() throws Exception {
        org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        try{
            MainActivity activity=controller.get();
            java.lang.reflect.Method replace=MainActivity.class.getDeclaredMethod("setPicture",Bitmap.class);replace.setAccessible(true);
            Bitmap held=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);held.eraseColor(Color.RED);replace.invoke(activity,held);
            for(int i=0;i<10;i++)replace.invoke(activity,Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888));
            assertFalse("RenderThread's retained frame must not be explicitly recycled",held.isRecycled());
            Bitmap target=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);new Canvas(target).drawBitmap(held,0,0,null);
            assertEquals(Color.RED,target.getPixel(0,0));
        }finally{controller.pause().stop().destroy();}
    }
}
