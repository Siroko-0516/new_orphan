package dev.giljabi;

import android.Manifest;
import android.content.Intent;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AssistantButtonServiceTest {
    public static final class TestService extends AssistantService {
        final List<FakeInput> inputs=new ArrayList<>();
        final List<byte[]> uploads=new ArrayList<>();int answers;
        @Override boolean hasAssistantKey(){return true;}
        @Override AssistantInput createMicrophone(AssistantInput.Listener listener){FakeInput input=new FakeInput(listener);inputs.add(input);return input;}
        @Override void submitAudio(byte[] wav){uploads.add(wav);}
        @Override void playAnswer(long output){answers++;}
    }
    static final class FakeInput implements AssistantInput {
        final Listener listener;int starts,finishes;boolean closed;
        FakeInput(Listener listener){this.listener=listener;}
        public void start(){starts++;}
        public void finish(){finishes++;}
        public void close(){closed=true;}
    }
    private ServiceController<TestService> controller;private TestService service;
    private final byte[] wav=AssistantWav.encode(new byte[16000]);
    @Before public void start(){
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.BLUETOOTH_CONNECT);
        controller=Robolectric.buildService(TestService.class).create();service=controller.get();
        service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_START"),0,1);
        assertTrue(AssistantService.active());
    }
    @After public void stop(){controller.destroy();shadowOf(Looper.getMainLooper()).idle();}
    private void press(){service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_TRIGGER"),0,2);}
    private FakeInput record(){press();assertEquals(1,service.inputs.size());return service.inputs.get(0);}
    private void send(FakeInput input){press();input.listener.finished(wav,false);assertEquals(1,service.uploads.size());}

    @Test public void silenceDoesNotSendAndNextPressSubmitsOnce(){
        FakeInput input=record();shadowOf(Looper.getMainLooper()).idleFor(20,TimeUnit.SECONDS);
        assertTrue(service.uploads.isEmpty());assertEquals(0,input.finishes);
        press();assertEquals(1,input.finishes);assertTrue(service.uploads.isEmpty());
        press();assertEquals(1,input.finishes);assertEquals(1,service.inputs.size());
        input.listener.finished(wav,false);assertEquals(1,service.uploads.size());assertArrayEquals(wav,service.uploads.get(0));
    }
    @Test public void aLongRecordingWaitsForExplicitSend(){
        FakeInput input=record();input.listener.finished(wav,true);
        assertTrue(service.uploads.isEmpty());assertEquals("녹음한 말 전송",AssistantService.snapshot().buttonLabel);
        press();assertEquals(1,service.uploads.size());assertEquals(1,service.inputs.size());
    }
    @Test public void buttonDuringAnswerStopsPlaybackAndStartsNewInput(){
        FakeInput input=record();send(input);service.speak("첫 번째 답변이에요.",null);
        assertEquals(1,service.answers);assertEquals("답변 취소하고 새 녹음",AssistantService.snapshot().buttonLabel);
        press();assertEquals(2,service.inputs.size());assertEquals(1,service.inputs.get(1).starts);
        assertEquals("녹음 끝내고 전송",AssistantService.snapshot().buttonLabel);
        service.speechDone(); // A late completion from the cancelled answer cannot end the new recording.
        assertTrue(AssistantService.busy());assertEquals("녹음 끝내고 전송",AssistantService.snapshot().buttonLabel);
    }
    @Test public void buttonWhileWaitingStartsAReplacementQuestion(){
        FakeInput input=record();send(input);press();
        assertEquals(2,service.inputs.size());assertEquals(1,service.uploads.size());
        assertEquals("녹음 끝내고 전송",AssistantService.snapshot().buttonLabel);
    }
    @Test public void replyCompletionReturnsToIdleWithoutStartingMicrophone(){
        FakeInput input=record();send(input);service.speak("답변이에요.",null);service.speechDone();
        assertFalse(AssistantService.busy());assertEquals("버튼 누르고 말하기",AssistantService.snapshot().buttonLabel);
        shadowOf(Looper.getMainLooper()).idleFor(20,TimeUnit.SECONDS);
        assertEquals(1,service.inputs.size());assertEquals(1,service.uploads.size());
    }
    @Test public void stoppingDiscardsInputAndItsLateCallback(){
        FakeInput input=record();service.onStartCommand(new Intent().setAction("dev.giljabi.ASSISTANT_STOP"),0,3);
        assertTrue(input.closed);input.listener.finished(wav,false);assertTrue(service.uploads.isEmpty());assertFalse(AssistantService.active());
    }
    @Test public void hardwareButtonUsesTheSameStartAndFinishFlow(){
        assertTrue(AssistantService.boardButton());
        assertEquals(1,service.inputs.size());
        shadowOf(Looper.getMainLooper()).idleFor(800,TimeUnit.MILLISECONDS);
        assertTrue(AssistantService.boardButton());
        assertEquals(1,service.inputs.get(0).finishes);assertTrue(service.uploads.isEmpty());
        service.inputs.get(0).listener.finished(wav,false);assertEquals(1,service.uploads.size());
    }
    @Test public void spokenPhoneConfirmationSurvivesWaitingForNextButton()throws Exception{
        FakeInput input=record();send(input);
        Class<?> type=Class.forName("dev.giljabi.AssistantService$PendingCall");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(AssistantContacts.Contact.class);constructor.setAccessible(true);
        Object pending=constructor.newInstance(new AssistantContacts.Contact("엄마","01012345678"));
        ReflectionHelpers.setField(service,"pendingCall",pending);
        service.speak("엄마에게 전화할까요?",null);service.speechDone();
        assertSame(pending,ReflectionHelpers.getField(service,"pendingCall"));
        press();assertSame(pending,ReflectionHelpers.getField(service,"pendingCall"));
        assertEquals(2,service.inputs.size());
    }
}
