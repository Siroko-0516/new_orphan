package dev.giljabi;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android service lifetime; the emulator has no physical HM-10 radio peer. */
@RunWith(AndroidJUnit4.class)
public class BoardLifecycleSmokeTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.CAMERA);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void waitFor(java.util.function.BooleanSupplier condition,long timeout,String error)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);assertTrue(error,condition.getAsBoolean());}
    @Test public void boardForegroundServiceOutlivesCameraAssistantAndActivity()throws Exception{
        AtomicInteger frames=new AtomicInteger();
        MonitorService.Observer observer=new MonitorService.Observer(){public void state(MonitorService.Snapshot s){}public void frame(MonitorService.ViewFrame f){if(f.detectionMs>=0)frames.incrementAndGet();f.bitmap.recycle();}};
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            activity.onActivity(a->{try{BoardService.connect(a,"AA:BB:CC:DD:EE:01",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC);}catch(Exception e){throw new AssertionError(e);}});
            waitFor(BoardService::active,10000,"Board foreground service did not start");
            // No hardware connection is claimed. An unavailable adapter/peer may leave it retrying.
            main(()->MonitorService.observe(observer));
            activity.onActivity(a->{try{MonitorService.start(a,new MonitorConfig("","설명",GeminiClient.DEFAULT_MODEL,false,false,true,false,2000,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC,CameraSource.BACK));}catch(Exception e){throw new AssertionError(e);}});
            waitFor(()->frames.get()>=2,60000,"Camera service did not provide YOLO frames");
            main(()->MonitorService.stop(context()));waitFor(()->!MonitorService.active(),15000,"Camera service did not stop");assertTrue("AI stop ended the independent board service",BoardService.active());
            activity.onActivity(a->{try{AssistantService.start(a,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC);}catch(Exception e){throw new AssertionError(e);}});
            waitFor(AssistantService::active,10000,"Assistant did not start");
            main(()->AssistantService.stop(context()));waitFor(()->!AssistantService.active(),10000,"Assistant did not stop");assertTrue("Assistant stop ended the board service",BoardService.active());
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();Thread.sleep(1200);assertTrue("Home ended the board service",BoardService.active());
        }finally{
            main(()->{MonitorService.forget(observer);MonitorService.stop(context());AssistantService.stop(context());BoardService.disconnect(context());});
            waitFor(()->!MonitorService.active()&&!AssistantService.active()&&!BoardService.active(),15000,"Services failed explicit cleanup");
        }
    }
}
