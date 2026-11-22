package dev.giljabi;

import android.graphics.Bitmap;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EspCameraProfileTest {
    private static final String ADDRESS="http://192.168.4.1:81/stream";
    private byte[] jpeg(int width,int height){Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(b.compress(Bitmap.CompressFormat.JPEG,80,out));b.recycle();return out.toByteArray();}
    private class Camera implements LocalCamera.Connector {
        int frame=6,quality=20;boolean newEnum,rejectVga,wrongSize;final List<String> calls=new ArrayList<>();
        final byte[] small=jpeg(320,240),half=jpeg(480,320),vga=jpeg(640,480);
        public HttpURLConnection open(URL url){assertEquals("192.168.4.1",url.getHost());assertEquals(80,url.getPort());calls.add(url.getFile());
            return new HttpURLConnection(url){
                public int getResponseCode(){String query=url.getQuery();if(query!=null&&query.startsWith("var=framesize")){int value=Integer.parseInt(query.substring(query.lastIndexOf('=')+1));if(rejectVga&&value>6)return 500;frame=value;}if(query!=null&&query.startsWith("var=quality"))quality=Integer.parseInt(query.substring(query.lastIndexOf('=')+1));return 200;}
                public String getContentType(){return url.getPath().equals("/capture")?"image/jpeg":"application/json";}
                public InputStream getInputStream(){if(url.getPath().equals("/capture"))return new ByteArrayInputStream(wrongSize||frame==6?small:newEnum&&frame==9?half:vga);if(url.getPath().equals("/status"))return new ByteArrayInputStream(("{\"framesize\":"+frame+",\"pixformat\":4,\"vflip\":1,\"hmirror\":1}").getBytes(java.nio.charset.StandardCharsets.UTF_8));return new ByteArrayInputStream(new byte[0]);}
                public void disconnect(){}public void connect(){}public boolean usingProxy(){return false;}
            };
        }
    }
    @Test public void olderModernEnumUsesVerifiedVgaWithoutTouchingOrientation(){Camera c=new Camera();String result=EspCameraProfile.configure(ADDRESS,EspCameraProfile.CLEAR,c,()->false);assertTrue(result.contains("640×480"));assertEquals(9,c.frame);assertEquals(18,c.quality);assertFalse(c.calls.stream().anyMatch(x->x.contains("vflip")||x.contains("hmirror")));}
    @Test public void insertedFrameEnumIsDetectedFromActualJpegDimensions(){Camera c=new Camera();c.newEnum=true;String result=EspCameraProfile.configure(ADDRESS,EspCameraProfile.CLEAR,c,()->false);assertTrue(result.contains("640×480"));assertEquals(10,c.frame);assertEquals(2,c.calls.stream().filter(x->x.equals("/capture")).count());}
    @Test public void unavailableVgaKeepsOriginalFrameSizeAndQuality(){Camera c=new Camera();c.rejectVga=true;assertTrue(EspCameraProfile.configure(ADDRESS,EspCameraProfile.CLEAR,c,()->false).contains("기존"));assertEquals(6,c.frame);assertEquals(20,c.quality);}
    @Test public void aFalseSuccessfulControlReplyDoesNotPretendResolutionImproved(){Camera c=new Camera();c.wrongSize=true;assertTrue(EspCameraProfile.configure(ADDRESS,EspCameraProfile.CLEAR,c,()->false).contains("기존"));assertEquals(6,c.frame);}
    @Test public void fastModeIsVerifiedAsQvga(){Camera c=new Camera();c.frame=10;String result=EspCameraProfile.configure(ADDRESS,EspCameraProfile.FAST,c,()->false);assertTrue(result.contains("320×240"));assertEquals(6,c.frame);assertEquals(20,c.quality);}
    @Test public void keepModeAndCustomPortsMakeNoConfigurationCalls(){LocalCamera.Connector never=u->{throw new AssertionError("Unexpected control request");};assertTrue(EspCameraProfile.configure(ADDRESS,EspCameraProfile.KEEP,never,()->false).contains("유지"));assertTrue(EspCameraProfile.configure("http://192.168.4.1:8080/stream",EspCameraProfile.CLEAR,never,()->false).contains("유지"));}
    @Test public void cancelledStartupDoesNotSendControlRequests(){Camera c=new Camera();assertTrue(EspCameraProfile.configure(ADDRESS,EspCameraProfile.CLEAR,c,()->true).contains("유지"));assertTrue(c.calls.isEmpty());}
}
