package dev.giljabi;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import java.io.IOException;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AssistantSmokeTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.BLUETOOTH_CONNECT);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Test public void communicationAudioPermissionIsGrantedAndRouteCanBeConfigured(){
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context().checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS));
        android.media.AudioManager audio=context().getSystemService(android.media.AudioManager.class);int previous=audio.getMode();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){audio.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);long until=SystemClock.elapsedRealtime()+3000;while(audio.getMode()!=android.media.AudioManager.MODE_IN_COMMUNICATION&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);assertEquals(android.media.AudioManager.MODE_IN_COMMUNICATION,audio.getMode());}
        finally{audio.setMode(previous);}
    }
    private static final class Probe implements AssistantInput.Listener {
        final CountDownLatch ready=new CountDownLatch(1),done=new CountDownLatch(1);
        volatile String failure,route;volatile byte[] wav;volatile boolean limit;
        public void ready(String route){this.route=route;ready.countDown();}
        public void finished(byte[] wav,boolean reachedLimit){this.wav=wav;limit=reachedLimit;done.countDown();}
        public void error(String message){failure=message;ready.countDown();done.countDown();}
    }
    @Test public void realManualMicrophoneWaitsForButtonAndProducesWav()throws Exception{
        Probe probe=new Probe();AssistantMic mic=new AssistantMic(context(),probe);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            mic.start();assertTrue("microphone did not start",probe.ready.await(10,TimeUnit.SECONDS));assertNull(probe.failure);assertNotNull(probe.route);
            boolean ended=probe.done.await(16,TimeUnit.SECONDS);
            if(ended){assertNull(probe.failure);assertTrue("only the hard size limit may end a recording without a button",probe.limit);}
            else{mic.finish();assertTrue("finish did not release recording",probe.done.await(10,TimeUnit.SECONDS));}
            assertNull(probe.failure);assertNotNull(probe.wav);assertTrue(probe.wav.length>6400);assertTrue(probe.wav.length<=AssistantWav.MAX_WAV);
            assertEquals('R',probe.wav[0]);assertEquals('I',probe.wav[1]);assertEquals('F',probe.wav[2]);assertEquals('F',probe.wav[3]);
        }finally{mic.close();}
    }
    @Test public void cancellingInputLetsTheNextButtonTurnAcquireMicrophone()throws Exception{
        Probe first=new Probe(),next=new Probe();AssistantMic a=new AssistantMic(context(),first),b=new AssistantMic(context(),next);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            a.start();assertTrue(first.ready.await(10,TimeUnit.SECONDS));assertNull(first.failure);a.close();
            b.start();assertTrue("previous recording kept the input device",next.ready.await(10,TimeUnit.SECONDS));assertNull(next.failure);assertNotNull(next.route);
            Thread.sleep(350);b.finish();assertTrue(next.done.await(10,TimeUnit.SECONDS));assertNull(next.failure);assertNotNull(next.wav);
            assertNull("cancelled input delivered audio",first.wav);
        }finally{a.close();b.close();}
    }
    @Test public void enabledButtonServiceRemainsIdleAfterHomeAndStopsExplicitly()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            activity.onActivity(a->{try{AssistantService.start(a,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC);}catch(IOException e){throw new AssertionError(e);}});
            long end=SystemClock.elapsedRealtime()+10000;while(!AssistantService.active()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);
            assertTrue(AssistantService.snapshot().state,AssistantService.active());assertFalse(AssistantService.busy());assertEquals("",AssistantService.snapshot().inputRoute);
            device.pressHome();Thread.sleep(1200);assertTrue(AssistantService.active());assertFalse(AssistantService.busy());
        }finally{main(()->AssistantService.stop(context()));long end=SystemClock.elapsedRealtime()+10000;while(AssistantService.active()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);assertFalse(AssistantService.active());}
    }
}
