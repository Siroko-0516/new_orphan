package dev.giljabi;
import android.content.Intent;
import android.util.Size;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.IOException;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class PhoneConfigTest {
    private MonitorConfig config(String url,int source){return new MonitorConfig(url,"describe","gemini-2.5-flash",false,true,true,false,5000,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC,source);}
    @Test public void phoneWorksWithoutEspAddressOrNetwork()throws Exception{config("",CameraSource.BACK).validate();config("",CameraSource.FRONT).validate();}
    @Test(expected=IOException.class) public void espStillRequiresPrivateAddress()throws Exception{config("https://example.com/stream",CameraSource.ESP).validate();}
    @Test(expected=IOException.class) public void invalidSourceRejected()throws Exception{config("",99).validate();}
    @Test public void serviceIntentPreservesPhoneSelection(){MonitorConfig original=config("",CameraSource.FRONT);MonitorConfig restored=MonitorConfig.read(original.write(new Intent()));assertEquals(CameraSource.FRONT,restored.source);assertEquals(original.question,restored.question);assertEquals(original.speak,restored.speak);assertEquals(original.intervalMs,restored.intervalMs);assertEquals(CameraSource.ESP,MonitorConfig.read(new Intent()).source);}
    @Test public void captureSizeIsBoundedAndPrefer640(){assertEquals(new Size(640,480),PhoneCamera.chooseSize(new Size[]{new Size(4000,3000),new Size(320,240),new Size(1280,720),new Size(640,480)}));}
    @Test(expected=IllegalArgumentException.class) public void oversizedOnlyCameraFailsClearly(){PhoneCamera.chooseSize(new Size[]{new Size(4000,3000)});}
}
