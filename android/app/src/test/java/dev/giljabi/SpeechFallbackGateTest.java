package dev.giljabi;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeechFallbackGateTest {
    @Test public void actualPlaybackStartCanOnlyBeRecordedOnce(){SpeechFallbackGate g=new SpeechFallbackGate(()->true);assertFalse(g.hasStarted());assertTrue(g.claimStart());assertTrue(g.hasStarted());assertFalse(g.claimStart());assertTrue(g.complete());}
    @Test public void noStartIsRecordedAfterCancelOrContextChange(){AtomicBoolean valid=new AtomicBoolean(true);SpeechFallbackGate g=new SpeechFallbackGate(valid::get);valid.set(false);assertFalse(g.claimStart());assertFalse(g.hasStarted());SpeechFallbackGate cancelled=new SpeechFallbackGate(()->true);cancelled.cancel();assertFalse(cancelled.claimStart());}
    @Test public void aLaterPlaybackFailureCanStillFallBackAfterSuccessfulStart(){SpeechFallbackGate g=new SpeechFallbackGate(()->true);assertTrue(g.claimStart());assertTrue(g.fallback());assertFalse(g.fallback());assertFalse(g.claimStart());}
    @Test public void serverTimeoutAndPlayerFailureCanOnlyClaimOneFallback(){SpeechFallbackGate g=new SpeechFallbackGate(()->true);assertTrue(g.fallback());assertFalse(g.fallback());assertFalse(g.complete());assertFalse(g.active());}
    @Test public void stopBeforeFailureSuppressesFallback(){SpeechFallbackGate g=new SpeechFallbackGate(()->true);g.cancel();assertFalse(g.current());assertFalse(g.active());assertFalse(g.fallback());}
    @Test public void invalidGuidanceSuppressesFallbackAndLatePlayback(){AtomicBoolean valid=new AtomicBoolean(true);SpeechFallbackGate g=new SpeechFallbackGate(valid::get);assertTrue(g.active());valid.set(false);assertFalse(g.active());assertFalse(g.fallback());}
    @Test public void newUtteranceFencesAnOlderFailure(){AtomicLong generation=new AtomicLong(1);SpeechFallbackGate old=new SpeechFallbackGate(()->generation.get()==1);generation.incrementAndGet();SpeechFallbackGate newer=new SpeechFallbackGate(()->generation.get()==2);assertFalse(old.fallback());assertTrue(newer.fallback());}
    @Test public void successfulPlaybackNeverFallsBackLater(){SpeechFallbackGate g=new SpeechFallbackGate(()->true);assertTrue(g.complete());assertFalse(g.fallback());assertFalse(g.active());}
    @Test public void brokenValidityCheckIsTreatedAsInvalid(){SpeechFallbackGate g=new SpeechFallbackGate(()->{throw new IllegalStateException();});assertFalse(g.current());assertFalse(g.fallback());}
    @Test public void concurrentFailureCallbacksDoNotDuplicateSpeech()throws Exception{SpeechFallbackGate g=new SpeechFallbackGate(()->true);ExecutorService workers=Executors.newFixedThreadPool(8);AtomicInteger claims=new AtomicInteger();CountDownLatch ready=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();try{for(int i=0;i<32;i++)jobs.add(workers.submit(()->{try{ready.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}if(g.fallback())claims.incrementAndGet();}));ready.countDown();for(Future<?> job:jobs)job.get(2,TimeUnit.SECONDS);assertEquals(1,claims.get());}finally{workers.shutdownNow();}}
    @Test public void recognizesMp3FramesAndId3ButRejectsDisguisedJson(){assertTrue(Mp3Audio.hasHeader(new byte[]{(byte)255,(byte)251,(byte)144,100}));byte[] id3={'I','D','3',4,0,0,0,0,0,0,(byte)255,(byte)251,(byte)144,100};assertTrue(Mp3Audio.hasHeader(id3));assertFalse(Mp3Audio.hasHeader("{\"error\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertFalse(Mp3Audio.hasHeader(new byte[]{'I','D','3',0,1,2,3}));}
    @Test public void rejectsInvalidId3SizeAndInvalidFrameParameters(){assertFalse(Mp3Audio.hasHeader(new byte[]{'I','D','3',4,0,0,0,0,127,127}));assertFalse(Mp3Audio.hasHeader(new byte[]{(byte)255,(byte)251,0,100}));assertFalse(Mp3Audio.hasHeader(new byte[]{(byte)255,(byte)233,(byte)144,100}));}
}
