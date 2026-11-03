package dev.giljabi;

import android.Manifest;
import android.content.*;
import android.graphics.Rect;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Explicit emulator GPS fixtures; exercises the actual app service and UI without AI credentials. */
@RunWith(AndroidJUnit4.class)
public class LocationUiSmokeTest {
    @Rule public GrantPermissionRule permissions=GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.RECORD_AUDIO,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS);
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private String shell(String command)throws Exception{
        ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[1024];for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);return out.toString("UTF-8").trim();}
    }
    private void await(String message,BooleanSupplier predicate)throws Exception{
        long end=SystemClock.elapsedRealtime()+12000;while(!predicate.getAsBoolean()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);assertTrue(message,predicate.getAsBoolean());
    }
    private TextView find(View view,String text,boolean exact){
        if(view instanceof TextView){String value=((TextView)view).getText().toString();if(exact?value.equals(text):value.contains(text))return (TextView)view;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=find(((ViewGroup)view).getChildAt(i),text,exact);if(result!=null)return result;}
        return null;
    }
    private void seed(LocationManager manager,double latitude,double longitude){
        Location fix=new Location(LocationManager.GPS_PROVIDER);fix.setLatitude(latitude);fix.setLongitude(longitude);fix.setAccuracy(5);fix.setTime(System.currentTimeMillis());fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,fix);
    }
    @Test public void currentLocationButtonShowsGpsCoordinatesMapAndUpdatedFix()throws Exception{
        LocationManager manager=context().getSystemService(LocationManager.class);boolean wasEnabled=manager.isLocationEnabled();String app=context().getPackageName();
        String wifi=shell("settings get global wifi_on"),data=shell("settings get global mobile_data");
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            // Check coordinate/map fallback without relying on an address lookup server.
            shell("svc wifi disable");shell("svc data disable");shell("cmd location set-location-enabled true");shell("appops set "+app+" android:mock_location allow");
            manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);
            seed(manager,37.5,127.0);
            await("Seeded GPS was not available",()->{Location l=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);return l!=null&&Math.abs(l.getLatitude()-37.5)<.000001;});
            activity.onActivity(a->{try{AssistantService.start(a,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC);}catch(IOException e){throw new AssertionError(e);}});
            await("Assistant service did not arm",AssistantService::active);
            activity.onActivity(a->{TextView button=find(a.getWindow().getDecorView(),"현재 위치 확인 · AI 서버 없이 조회",true);assertNotNull(button);assertTrue(button.isEnabled());assertTrue(button.performClick());});
            await("Current-location button did not display coordinates",()->AssistantService.snapshot().locationText.contains("37.50000")&&AssistantService.snapshot().mapUrl.endsWith("37.5,127.0"));
            String first=AssistantService.snapshot().mapUrl;
            seed(manager,37.5005,127.0005);
            await("Updated GPS was not available",()->{Location l=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);return l!=null&&Math.abs(l.getLatitude()-37.5005)<.000001;});
            activity.onActivity(a->AssistantService.ask(a,"내 위치가 어디야"));
            await("New request kept an old GPS position",()->AssistantService.snapshot().locationText.contains("37.50050")&&AssistantService.snapshot().mapUrl.endsWith("37.5005,127.0005"));
            activity.onActivity(a->{TextView label=find(a.getWindow().getDecorView(),"37.50050",false);assertNotNull("Coordinates are missing from the actual activity",label);label.requestRectangleOnScreen(new Rect(0,0,label.getWidth(),label.getHeight()),true);});
            SystemClock.sleep(500);
            File folder=new File(context().getFilesDir(),"location-system");assertTrue(folder.isDirectory()||folder.mkdirs());
            assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(folder,"location-system-screen.png")));
            JSONObject result=new JSONObject().put("android_api",Build.VERSION.SDK_INT).put("actual_android_location_api",true).put("actual_app_location_button",true).put("actual_assistant_service",true).put("coordinates_visible_in_activity",true).put("first_map_url",first).put("updated_map_url",AssistantService.snapshot().mapUrl).put("updated_location_text",AssistantService.snapshot().locationText).put("new_fix_replaces_previous_fix",true).put("fixture_latitude",37.5005).put("fixture_longitude",127.0005).put("mock_gps_injected",true).put("physical_gps_reception_verified",false).put("ai_key_required",false).put("wifi_and_mobile_data_disabled_for_test",true).put("live_address_lookup_verified",false);
            try(FileOutputStream output=new FileOutputStream(new File(folder,"location-system-check.json"))){output.write((result.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));}
            SystemEvidence.preserve(new File(folder,"location-system-check.json"));SystemEvidence.preserve(new File(folder,"location-system-screen.png"));
        }finally{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->AssistantService.stop(context()));
            try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(RuntimeException ignored){}
            shell("appops set "+app+" android:mock_location default");shell("cmd location set-location-enabled "+wasEnabled);
            shell("svc wifi "+(wifi.equals("1")?"enable":"disable"));shell("svc data "+(data.equals("1")?"enable":"disable"));
        }
    }
}
