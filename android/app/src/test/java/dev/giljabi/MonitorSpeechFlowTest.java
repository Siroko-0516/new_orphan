package dev.giljabi;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real monitor queue/completion wiring with a recording TTS, no cloud or radio calls. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MonitorSpeechFlowTest {
    private ServiceController<MonitorService> controller;private MonitorService service;private RecordingSpeech voice;private Class<?> announcement;
    private static final class RecordingSpeech extends TextToSpeech {
        final List<String> said=new ArrayList<>();int stops;float rate;String lastId;
        RecordingSpeech(){super(RuntimeEnvironment.getApplication(),null);}
        @Override public Set<Voice> getVoices(){return Collections.singleton(new Voice("installed-ko",Locale.KOREA,400,100,false,Collections.emptySet()));}
        @Override public int setVoice(Voice value){return SUCCESS;}
        @Override public int setSpeechRate(float value){rate=value;return SUCCESS;}
        @Override public int speak(CharSequence text,int mode,Bundle options,String id){said.add(text.toString());lastId=id;return SUCCESS;}
        @Override public int stop(){stops++;return SUCCESS;}
        @Override public void shutdown(){}
    }
    private void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private Object get(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    @Before public void setup()throws Exception{
        controller=Robolectric.buildService(MonitorService.class).create();service=controller.get();voice=new RecordingSpeech();set(service,"tts",voice);set(service,"ttsReady",true);announcement=Class.forName("dev.giljabi.MonitorService$Announcement");
    }
    @After public void close(){MonitorService.pauseForAssistant();service.onStartCommand(new Intent().setAction("dev.giljabi.MONITOR_STOP"),0,99);controller.destroy();voice.shutdown();}
    private Object offer(String text,boolean urgent,BooleanSupplier valid)throws Exception{
        WalkingSceneGate scene=new WalkingSceneGate();scene.reset();int[] pixels=new int[192];for(int i=0;i<pixels.length;i++)pixels[i]=i%16<8?0xff444444:0xffbbbbbb;scene.observe(1,new SceneSignature(pixels),Collections.emptyList(),0);
        Constructor<?> c=announcement.getDeclaredConstructor(WalkingSceneGate.Decision.class,String.class,ContinuousGate.Ticket.class,boolean.class,String.class);c.setAccessible(true);Object next=c.newInstance(scene.review(1),text,null,urgent,text);set(next,"valid",valid);
        @SuppressWarnings("unchecked") GuidanceSpeechQueue<Object> queue=(GuidanceSpeechQueue<Object>)get(service,"speechQueue");Object ready=queue.offer(next,urgent,valid);
        if(ready!=null){Method begin=MonitorService.class.getDeclaredMethod("beginSpeech",announcement);begin.setAccessible(true);begin.invoke(service,ready);}return next;
    }
    private void done(String id)throws Exception{Method method=MonitorService.class.getDeclaredMethod("finishNative",String.class,boolean.class);method.setAccessible(true);method.invoke(service,id,false);}
    @Test public void ordinarySceneUpdatesFinishCurrentSentenceAtFasterRate()throws Exception{
        offer("앞쪽 통로에 사람이 있어요.",false,()->true);String id=voice.lastId;int stops=voice.stops;
        offer("통로에 의자가 있어요.",false,()->true);offer("통로 위로 나뭇가지가 나와 있어요.",false,()->true);
        assertEquals(Collections.singletonList("앞쪽 통로에 사람이 있어요."),voice.said);assertEquals(stops,voice.stops);assertEquals(1.2f,voice.rate,.001f);
        done(id);assertEquals(Arrays.asList("앞쪽 통로에 사람이 있어요.","통로 위로 나뭇가지가 나와 있어요."),voice.said);
    }
    @Test public void urgentWarningPreemptsAndOldCompletionCannotFinishNewOutput()throws Exception{
        offer("앞쪽에 의자가 있어요.",false,()->true);String old=voice.lastId;Object urgent=offer("앞쪽 통로가 더 가려졌어요.",true,()->true);assertEquals(2,voice.said.size());done(old);assertSame(urgent,get(service,"announcement"));done(voice.lastId);assertNull(get(service,"announcement"));
    }
    @Test public void assistantButtonCancelsCurrentAndWaitingOutput()throws Exception{
        offer("앞쪽에 사람이 있어요.",false,()->true);String old=voice.lastId;offer("앞쪽에 의자가 있어요.",false,()->true);MonitorService.pauseForAssistant();done(old);assertEquals(1,voice.said.size());assertNull(get(service,"announcement"));
    }
    @Test public void oldQueuedSceneIsNeverReadAfterCurrentSentence()throws Exception{
        offer("앞쪽에 사람이 있어요.",false,()->true);String old=voice.lastId;AtomicBoolean fresh=new AtomicBoolean(true);offer("앞쪽에 의자가 있어요.",false,fresh::get);fresh.set(false);done(old);assertEquals(1,voice.said.size());assertNull(get(service,"announcement"));
    }
}
