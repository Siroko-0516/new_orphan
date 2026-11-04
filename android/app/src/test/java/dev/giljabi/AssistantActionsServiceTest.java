package dev.giljabi;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
        final List<String> calls=new ArrayList<>(),spoken=new ArrayList<>();boolean locationFails,skipAudible,primaryAddressFails,placesFail=true;int locations,backupLookups;
        String backup="";List<NearbyPlaces.Place> results=Collections.emptyList();
        @Override boolean hasAssistantKey(){return false;}
        @Override void playAnswer(long output){spoken.add(AssistantService.snapshot().answer);if(!skipAudible)playbackStarted();}
        @Override AssistantContacts.Contact findPhoneContact(String target)throws IOException{if(target.equals("없는사람"))throw new IOException("연락처에서 이름을 찾지 못했어요.");return new AssistantContacts.Contact(target,"01012345678");}
        @Override void placePhoneCall(String number){calls.add(number);}
        @Override void locate(AssistantLocation.Callback callback){locations++;if(locationFails){callback.failed("위치 기능을 켜주세요.");return;}Location l=new Location("gps");l.setLatitude(37.5);l.setLongitude(127);l.setAccuracy(10);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());callback.found(l);}
        @Override void resolveAddress(Location fix,Consumer<String> callback){callback.accept(primaryAddressFails?"":"서울특별시 중구 테스트로 10");}
        @Override String backupAddress(Location fix,long turn){backupLookups++;return backup;}
        @Override List<NearbyPlaces.Place> searchPlaces(String category,Location fix,long turn)throws Exception{if(placesFail)throw new IOException("simulated map server unavailable");return results;}
    }
    private ServiceController<TestService> controller;private TestService service;
    @Before public void start(){shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS);controller=Robolectric.buildService(TestService.class).create();service=controller.get();service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_START"),0,1);}
    @After public void close(){controller.destroy();shadowOf(Looper.getMainLooper()).idle();}
    private void ask(String text){service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_ASK").putExtra("text",text),0,2);}
    private void finish(){service.speechDone();}
    private String said(){return service.spoken.isEmpty()?"":service.spoken.get(service.spoken.size()-1);}
    private void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+3_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}assertTrue("action callback timed out",condition.getAsBoolean());}
    private void question(String target)throws Exception{ask(target+"에게 전화해줘");await(()->said().contains(target+"에게 전화할까요"));}
    @Test public void currentLocationIsSpokenAsAnAddress(){ask("내 위치가 어디야");assertEquals(1,service.locations);assertTrue(said().contains("서울특별시 중구 테스트로 10"));assertFalse(said().contains("지도"));assertFalse(AssistantService.snapshot().mapRequested);assertTrue(AssistantService.snapshot().locationText.contains("37.50000"));}
    @Test public void addressFailureReadsTheBackupAddress()throws Exception{service.primaryAddressFails=true;service.backup="경기도 성남시 테스트동 테스트길 20";ask("내 위치 알려줘");await(()->!said().isEmpty());assertEquals(1,service.backupLookups);assertTrue(said().contains(service.backup));assertFalse(said().contains("지도"));}
    @Test public void failedAddressIsExplainedByVoice()throws Exception{service.primaryAddressFails=true;ask("내 위치 알려줘");await(()->!said().isEmpty());assertTrue(said().contains("주소를 확인하지 못했어요"));assertFalse(said().contains("지도"));assertFalse(said().contains("화면"));}
    @Test public void failedLocationClearsPreviousMap(){ask("현재 위치 알려줘");finish();service.locationFails=true;ask("내 위치 알려줘");assertEquals("",AssistantService.snapshot().mapUrl);assertEquals("",AssistantService.snapshot().locationText);assertTrue(said().contains("위치 기능"));}
    @Test public void hospitalNamesAndApproximateDistancesAreSpoken()throws Exception{service.placesFail=false;service.results=Arrays.asList(new NearbyPlaces.Place("테스트중앙병원","",37.501,127,250),new NearbyPlaces.Place("테스트의원","",37.502,127,500));ask("주변에 병원 있냐");await(()->!said().isEmpty());assertTrue(said().contains("테스트중앙병원"));assertTrue(said().contains("250미터"));assertTrue(said().contains("테스트의원"));assertTrue(said().contains("500미터"));assertFalse(said().contains("지도"));}
    @Test public void failedHospitalSearchDoesNotSendTheUserToAScreen()throws Exception{ask("주변에 병원 있냐");await(()->!said().isEmpty());assertTrue(said().contains("정보를 불러오지 못했어요"));assertFalse(said().contains("지도"));assertFalse(said().contains("화면"));assertTrue(AssistantService.snapshot().places.isEmpty());}
    @Test public void emptyResultsDoNotClaimThereAreNoHospitals()throws Exception{service.placesFail=false;ask("병원 있어");await(()->!said().isEmpty());assertTrue(said().contains("없다는 뜻은 아니"));assertFalse(said().contains("지도"));}
    @Test public void genericCallAsksWhoThenConfirmsNameAndWaitsForYes()throws Exception{ask("전화해줘");assertTrue(said().contains("누구에게 전화할까요"));finish();ask("엄마");await(()->said().contains("엄마에게 전화할까요"));assertTrue(service.calls.isEmpty());finish();ask("네");assertTrue(said().contains("엄마에게 전화할게요"));assertTrue(service.calls.isEmpty());finish();assertEquals(Collections.singletonList("01012345678"),service.calls);}
    @Test public void aCompletedQuestionAndAffirmativeAnswerDialOnlyOnce()throws Exception{question("엄마");finish();ask("응 전화해줘");assertTrue(service.calls.isEmpty());finish();assertEquals(1,service.calls.size());ask("네");assertEquals(1,service.calls.size());}
    @Test public void negativeAnswersAreSpokenAndNeverCall()throws Exception{for(String reply:new String[]{"아니","아니요","아니오","안해","취소","하지마","괜찮아"}){question("엄마");finish();ask(reply);assertTrue(reply,said().contains("전화하지 않을게요"));assertTrue(service.calls.isEmpty());finish();}}
    @Test public void yesBeforeTheQuestionEndsOnlyRepeatsIt()throws Exception{question("엄마");ask("네");assertTrue(service.calls.isEmpty());assertTrue(said().contains("엄마에게 전화할까요"));finish();ask("네");finish();assertEquals(1,service.calls.size());}
    @Test public void unheardQuestionCannotAuthorizeADial()throws Exception{service.skipAudible=true;question("아빠");finish();ask("네");assertTrue(service.calls.isEmpty());assertTrue(said().contains("아빠에게 전화할까요"));}
    @Test public void ambiguousAnswersNeedAnotherExplicitConfirmation()throws Exception{question("엄마");finish();ask("글쎄");assertTrue(service.calls.isEmpty());assertTrue(said().contains("엄마에게 전화할까요"));}
    @Test public void expiredConfirmationDoesNotCall()throws Exception{question("엄마");finish();shadowOf(Looper.getMainLooper()).idleFor(61,TimeUnit.SECONDS);ask("네");assertTrue(service.calls.isEmpty());assertTrue(said().contains("엄마에게 전화할까요"));}
    @Test public void cancelCanStopTheConnectionAnnouncement()throws Exception{question("엄마");finish();ask("네");ask("취소");finish();assertTrue(service.calls.isEmpty());}
    @Test public void aDifferentActionDiscardsOldConsent()throws Exception{question("엄마");finish();ask("내 위치 알려줘");finish();ask("네");assertTrue(service.calls.isEmpty());}
    @Test public void unknownContactReasksWho()throws Exception{ask("없는사람에게 전화해줘");await(()->!said().isEmpty());assertTrue(said().contains("누구에게 전화할까요"));assertTrue(service.calls.isEmpty());}
    @Test public void missingCallPermissionPreventsDialing(){shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE);ask("엄마에게 전화해줘");assertTrue(service.calls.isEmpty());assertTrue(said().contains("권한"));}
    @Test public void missingContactsPermissionIsSpokenWithoutRepeatedNamePrompts(){shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS);ask("엄마에게 전화해줘");assertTrue(service.calls.isEmpty());assertTrue(said().contains("연락처 권한"));assertFalse(said().contains("누구에게 전화할까요"));}
    @Test public void revokedPermissionDuringAnnouncementPreventsTheCall()throws Exception{question("엄마");finish();ask("네");shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CALL_PHONE);finish();assertTrue(service.calls.isEmpty());assertTrue(said().contains("권한"));}
    @Test public void recognitionErrorNeverLeavesOldConsentArmed()throws Exception{question("엄마");finish();service.recognitionFailed("음성 인식 실패");ask("네");assertTrue(service.calls.isEmpty());assertTrue(said().contains("누구에게 전화할까요"));}
    @Test public void recognitionErrorSpeaksThatNoCallWasMade()throws Exception{question("엄마");service.recognitionFailed("음성 인식 실패");assertTrue(said().contains("전화하지 않았어요"));finish();ask("네");assertTrue(service.calls.isEmpty());}
}
