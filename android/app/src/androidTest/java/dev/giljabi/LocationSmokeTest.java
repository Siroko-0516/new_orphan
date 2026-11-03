package dev.giljabi;

import android.Manifest;
import android.content.Context;
import android.location.*;
import android.os.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses an explicitly seeded emulator fix; no physical user's location is claimed. */
@RunWith(AndroidJUnit4.class)
public class LocationSmokeTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void shell(String command)throws Exception{
        ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
    }
    @Test public void androidLocationManagerReturnsTheSeededCoordinates()throws Exception{
        String provider=LocationManager.GPS_PROVIDER,app=context().getPackageName();
        LocationManager manager=context().getSystemService(LocationManager.class);
        boolean wasEnabled=manager.isLocationEnabled();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Location> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        AssistantLocation input=new AssistantLocation(context(),new AssistantLocation.Callback(){public void found(Location l){result.set(l);done.countDown();}public void failed(String message){error.set(message);done.countDown();}});
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            shell("cmd location set-location-enabled true");
            shell("appops set "+app+" android:mock_location allow");
            manager.addTestProvider(provider,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);manager.setTestProviderEnabled(provider,true);
            Location fix=new Location(provider);fix.setLatitude(37.5);fix.setLongitude(127.0);fix.setAccuracy(1);fix.setTime(System.currentTimeMillis());fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(provider,fix);
            long seededUntil=SystemClock.elapsedRealtime()+3000;
            while(SystemClock.elapsedRealtime()<seededUntil){Location last=manager.getLastKnownLocation(provider);if(last!=null&&Math.abs(last.getLatitude()-37.5)<.00001)break;SystemClock.sleep(50);}
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->input.start(context()));
            assertTrue("Location callback did not complete",done.await(12,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(result.get());assertEquals(37.5,result.get().getLatitude(),.00001);assertEquals(127.0,result.get().getLongitude(),.00001);assertTrue(AssistantLocation.usable(result.get()));
        }finally{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(input::close);
            try{manager.removeTestProvider(provider);}catch(RuntimeException ignored){}
            shell("appops set "+app+" android:mock_location default");
            shell("cmd location set-location-enabled "+wasEnabled);
        }
    }
}
