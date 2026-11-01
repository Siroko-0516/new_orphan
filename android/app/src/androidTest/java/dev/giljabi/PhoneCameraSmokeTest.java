package dev.giljabi;

import android.Manifest;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Runs Camera2 + actual bundled YOLO on emulated Android cameras, without external AI calls. */
@RunWith(AndroidJUnit4.class)
public class PhoneCameraSmokeTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.POST_NOTIFICATIONS);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private static View find(View view,java.util.function.Predicate<View> predicate){
        if(predicate.test(view))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),predicate);if(found!=null)return found;}}
        return null;
    }
    private static void waitFor(java.util.function.BooleanSupplier ready,long timeout,String message)throws Exception{
        long end=SystemClock.elapsedRealtime()+timeout;while(!ready.getAsBoolean()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);assertTrue(message,ready.getAsBoolean());
    }
    @Test public void backAndFrontProduceOrientedJpegAndYoloFramesAndReleaseCamera()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            for(int source:new int[]{CameraSource.BACK,CameraSource.FRONT}){
                CountDownLatch frames=new CountDownLatch(3);AtomicReference<String> failure=new AtomicReference<>();AtomicReference<PhoneCamera> camera=new AtomicReference<>();
                try{
                    main(()->camera.set(new PhoneCamera(context(),source,frame->{
                        try{
                            if(frame.detectionMs>=0){assertTrue(frame.bitmap.getWidth()<=1280);assertTrue(frame.bitmap.getHeight()<=1280);assertNotNull(frame.boxes);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(frame.jpeg,0,frame.jpeg.length,bounds);assertEquals(frame.bitmap.getWidth(),bounds.outWidth);assertEquals(frame.bitmap.getHeight(),bounds.outHeight);frames.countDown();}
                        }catch(Throwable e){failure.set(e.toString());}finally{frame.bitmap.recycle();}
                    },text->{},error->failure.set(error.message))));
                    assertTrue("camera "+source+": "+failure.get(),frames.await(60,TimeUnit.SECONDS));assertNull(failure.get());
                }finally{main(()->{if(camera.get()!=null)camera.get().close();});}
                Thread.sleep(1200);
            }
        }
    }
    @Test public void activitySelectionAndBackgroundServiceKeepPhoneFramesAfterHomeAndScreenOff()throws Exception{
        AtomicInteger frames=new AtomicInteger();AtomicReference<String> failure=new AtomicReference<>();
        MonitorService.Observer observer=new MonitorService.Observer(){public void state(MonitorService.Snapshot s){if(!s.active&&!s.status.equals("실시간 AI 중지됨"))failure.set(s.status);}public void frame(MonitorService.ViewFrame f){if(f.detectionMs>=0)frames.incrementAndGet();f.bitmap.recycle();}};
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            main(()->MonitorService.observe(observer));
            activity.onActivity(a->{
                View root=a.getWindow().getDecorView();Spinner sources=(Spinner)find(root,v->"영상 입력 선택".equals(v.getContentDescription()));assertNotNull(sources);sources.setSelection(CameraSource.BACK);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            activity.onActivity(a->{
                View start=find(a.getWindow().getDecorView(),v->v instanceof Button&&"실시간 AI + 백그라운드 시작".contentEquals(((Button)v).getText()));assertNotNull(start);start.performClick();
            });
            waitFor(()->frames.get()>=3,60000,"Phone service did not produce YOLO frames: "+failure.get());
            assertEquals(CameraSource.BACK,MonitorService.snapshot().config.source);
            int beforeHome=frames.get();device.pressHome();waitFor(()->frames.get()>=beforeHome+3,15000,"Frames stopped after Home");
            int beforeSleep=frames.get();device.sleep();waitFor(()->frames.get()>=beforeSleep+3,15000,"Frames stopped with screen off");assertTrue(MonitorService.active());device.wakeUp();
        }finally{
            device.wakeUp();main(()->{MonitorService.forget(observer);MonitorService.stop(context());});
            waitFor(()->!MonitorService.active(),15000,"Monitor service did not stop");
        }
        // A subsequent camera open must succeed after the foreground service releases its camera.
        Thread.sleep(1200);CountDownLatch reopened=new CountDownLatch(1);AtomicReference<PhoneCamera> camera=new AtomicReference<>();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            try{main(()->camera.set(new PhoneCamera(context(),CameraSource.BACK,f->{f.bitmap.recycle();reopened.countDown();},text->{},error->failure.set(error.message))));assertTrue("Camera remained busy after stop: "+failure.get(),reopened.await(30,TimeUnit.SECONDS));}
            finally{main(()->{if(camera.get()!=null)camera.get().close();});}
        }
    }
}
