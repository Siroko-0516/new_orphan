package dev.giljabi;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.*;
import java.io.IOException;
import java.util.*;
import java.util.function.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AssistantActionsServiceTest {
    public static final class TestService extends AssistantService {
        final List<String> calls=new ArrayList<>();boolean locationFails,skipAudible;int locations;
        @Override boolean hasAssistantKey(){return false;} // Native actions must work without cloud credentials.
        @Override void playAnswer(long output){if(!skipAudible)playbackStarted();}
        @Override AssistantContacts.Contact findPhoneContact(String target){return new AssistantContacts.Contact(target,"01012345678");}
        @Override void placePhoneCall(String number){calls.add(number);}
        @Override void locate(AssistantLocation.Callback callback){locations++;if(locationFails){callback.failed("위치 기능을 켜주세요.");return;}Location l=new Location("gps");l.setLatitude(37.5);l.setLongitude(127);l.setAccuracy(10);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());callback.found(l);}
        @Override void resolveAddress(Location fix,Consumer<String> callback){callback.accept("서울특별시 테스트 주소");}
        @Override List<NearbyPlaces.Place> searchPlaces(String category,Location fix,long turn)throws Exception{throw new IOException("simulated map server unavailable");}
    }
    private ServiceController<TestService> controller;private TestService service;
    @Before public void start(){shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS);controller=Robolectric.buildService(TestService.class).create();service=controller.get();service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_START"),0,1);}
    @After public void close(){controller.destroy();shadowOf(Looper.getMainLooper()).idle();}
    private void ask(String text){service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_ASK").putExtra("text",text),0,2);}
    private void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+3_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}assertTrue("action callback timed out",condition.getAsBoolean());}
    @Test public void currentLocationBypassesAiRefusalAndShowsRealFix(){ask("내 위치가 어디야");assertEquals(1,service.locations);assertTrue(AssistantService.snapshot().answer.contains("서울특별시 테스트 주소"));assertTrue(AssistantService.snapshot().locationText.contains("37.50000"));assertTrue(AssistantService.snapshot().mapUrl.endsWith("37.5,127.0"));}
    @Test public void failedLocationClearsPreviousMap(){ask("현재 위치 알려줘");service.speechDone();service.locationFails=true;ask("내 위치 알려줘");assertEquals("",AssistantService.snapshot().mapUrl);assertEquals("",AssistantService.snapshot().locationText);assertTrue(AssistantService.snapshot().answer.contains("위치 기능"));}
    @Test public void mapServerFailureLeavesActualLocationSearchLink()throws Exception{ask("주변에 병원 있냐");await(()->AssistantService.snapshot().answer.contains("지도 검색 서버"));assertTrue(AssistantService.snapshot().places.isEmpty());String link=AssistantService.snapshot().mapUrl;assertTrue(link.startsWith("https://www.google.com/maps/search/"));assertTrue(java.net.URLDecoder.decode(link,"UTF-8").contains("병원 near 37.5,127.0"));service.speechDone();ask("지도 열어줘");assertTrue(AssistantService.snapshot().mapRequested);assertEquals(link,AssistantService.takeMap());}
    @Test public void contactCallOnlyHappensAfterAudibleConfirmation()throws Exception{ask("엄마에게 전화해줘");await(()->AssistantService.snapshot().answer.contains("엄마에게 전화할까요"));assertTrue(service.calls.isEmpty());service.speechDone();ask("네");assertEquals(Collections.singletonList("01012345678"),service.calls);ask("네");assertEquals(1,service.calls.size());}
    @Test public void silentConfirmationAndCancellationNeverDial()throws Exception{service.skipAudible=true;ask("아빠에게 전화해줘");await(()->AssistantService.snapshot().answer.contains("전화할까요"));service.speechDone();ask("네");assertTrue(service.calls.isEmpty());service.speechDone();ask("엄마에게 전화하지마");assertTrue(service.calls.isEmpty());}
    @Test public void deniedCallingPermissionNeverReachesPhoneGateway(){shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE);ask("엄마에게 전화해줘");assertTrue(service.calls.isEmpty());assertTrue(AssistantService.snapshot().answer.contains("통화 권한"));}
}
