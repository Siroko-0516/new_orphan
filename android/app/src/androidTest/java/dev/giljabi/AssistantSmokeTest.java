package dev.giljabi;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AssistantSmokeTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Test public void bundledSpeechDetectorRunsOnAndroidAndRejectsSilence()throws Exception{
        byte[] bytes;try(InputStream in=context().getAssets().open("silero_vad.onnx");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)out.write(block,0,n);bytes=out.toByteArray();}
        OrtEnvironment env=OrtEnvironment.getEnvironment();float[] memory=new float[256];
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions();OrtSession session=env.createSession(bytes,options)){
            for(int frame=0;frame<16;frame++)try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(new float[576]),new long[]{1,576});OnnxTensor state=OnnxTensor.createTensor(env,FloatBuffer.wrap(memory),new long[]{2,1,128});OnnxTensor rate=OnnxTensor.createTensor(env,LongBuffer.wrap(new long[]{16000}),new long[0]);OrtSession.Result result=session.run(new HashMap<String,OnnxTensor>(){{put("input",x);put("state",state);put("sr",rate);}})){
                assertTrue(((OnnxTensor)result.get(0)).getFloatBuffer().get(0)<.3f);((OnnxTensor)result.get(1)).getFloatBuffer().get(memory);
            }
        }
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
