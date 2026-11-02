package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;
public class VoiceTurnGateTest {
 @Test public void speechOnsetIsConfirmed(){VoiceTurnGate g=new VoiceTurnGate(0);assertEquals(VoiceTurnGate.Event.NONE,g.step(.9f,32,false,true));assertEquals(VoiceTurnGate.Event.NONE,g.step(.9f,64,false,true));assertEquals(VoiceTurnGate.Event.START,g.step(.9f,96,false,true));}
 @Test public void userCanInterruptSpeaking(){VoiceTurnGate g=new VoiceTurnGate(0);g.step(.95f,32,true,true);g.step(.95f,64,true,true);assertEquals(VoiceTurnGate.Event.START,g.step(.95f,96,true,true));}
 @Test public void blockedSpeakerEchoCannotInterrupt(){VoiceTurnGate g=new VoiceTurnGate(0);for(int i=0;i<100;i++)assertEquals(VoiceTurnGate.Event.NONE,g.step(.99f,i*32,true,false));}
 @Test public void weakerPlaybackNoiseCannotInterrupt(){VoiceTurnGate g=new VoiceTurnGate(0);for(int i=0;i<100;i++)assertEquals(VoiceTurnGate.Event.NONE,g.step(.8f,i*32,true,true));}
 @Test public void silenceEndsTheUtterance(){VoiceTurnGate g=new VoiceTurnGate(0);for(int i=1;i<=8;i++)g.step(.9f,i*32,false,true);assertEquals(VoiceTurnGate.Event.END,g.step(0,1200,false,true));}
 @Test public void idleDisarmsAfterFifteenSeconds(){assertEquals(VoiceTurnGate.Event.IDLE,new VoiceTurnGate(0).step(0,15001,false,true));}
 @Test public void requestWaitKeepsSessionAlive(){VoiceTurnGate g=new VoiceTurnGate(0);g.keepAlive(16000);assertEquals(VoiceTurnGate.Event.NONE,g.step(0,16001,false,true));}
 @Test public void rearmDropsOldOnset(){VoiceTurnGate g=new VoiceTurnGate(0);g.step(.9f,32,false,true);g.step(.9f,64,false,true);g.reset(65);assertEquals(VoiceTurnGate.Event.NONE,g.step(.9f,96,false,true));}
}
