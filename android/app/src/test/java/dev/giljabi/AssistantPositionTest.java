package dev.giljabi;

import android.Manifest;
import android.location.*;
import android.os.SystemClock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AssistantPositionTest {
    @Before public void clock(){ShadowSystemClock.advanceBy(Duration.ofMinutes(10));}
    private Location fix(){Location l=new Location("gps");l.setLatitude(37.5);l.setLongitude(127);l.setAccuracy(12);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());return l;}
    @Test public void recentApproximateFixIsUsableAndMarkedWithAccuracy(){Location l=fix();l.setAccuracy(1500);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-60_000_000_000L);assertTrue(AssistantLocation.usable(l));assertTrue(AssistantPosition.summary(l).contains("1500"));}
    @Test public void staleFutureAndInvalidFixesAreRejected(){Location l=fix();l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-121_000_000_000L);assertFalse(AssistantLocation.usable(l));l=fix();l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()+1);assertFalse(AssistantLocation.usable(l));l=fix();l.setLatitude(Double.NaN);assertFalse(AssistantLocation.usable(l));l=fix();l.setAccuracy(5000);assertFalse(AssistantLocation.usable(l));}
    @Test public void addressesOnlyUseProviderData(){Address a=new Address(Locale.KOREAN);a.setAddressLine(0,"서울특별시 테스트 주소");assertEquals("서울특별시 테스트 주소",AssistantPosition.address(Collections.singletonList(a)));assertEquals("",AssistantPosition.address(Collections.emptyList()));}
    @Test public void mapUsesActualCoordinates(){assertTrue(AssistantPosition.url(fix()).endsWith("37.5,127.0"));}
    @Test public void deniedPermissionDoesNotInventAFix(){
        android.app.Application app=RuntimeEnvironment.getApplication();shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);
        AtomicReference<String> error=new AtomicReference<>();
        AssistantLocation input=new AssistantLocation(app,new AssistantLocation.Callback(){public void found(Location l){fail("Denied permission produced a fix");}public void failed(String m){error.set(m);}});
        input.start(app);assertNotNull(error.get());assertTrue(error.get().contains("위치 권한"));input.close();
    }
}
