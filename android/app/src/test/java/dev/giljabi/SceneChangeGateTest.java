package dev.giljabi;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SceneChangeGateTest {
    private static final List<YoloDetector.Box> NONE=Collections.emptyList();
    private SceneSignature sample(boolean changed,int exposure){int[] p=new int[192];for(int i=0;i<p.length;i++){int n=((i%16<8)^changed)?60:180;n+=exposure;p[i]=0xff000000|(n<<16)|(n<<8)|n;}return new SceneSignature(p);}
    private YoloDetector.Box person(){return new YoloDetector.Box(0,.9f,new float[]{.3f,.4f,.7f,.9f});}
    private SceneChangeGate ready(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),NONE,0);g.accepted(g.decision(1));return g;}
    @Test public void initialDescriptionThenSilenceForStationaryScene(){SceneChangeGate g=ready();for(int i=2;i<100;i++){g.observe(i,sample(false,0),NONE,i*200);assertNull(g.decision(i));}}
    @Test public void globalExposureAndOneFrameMotionDoNotAnnounce(){SceneChangeGate g=ready();g.observe(2,sample(false,20),NONE,200);assertNull(g.decision(2));g.observe(3,sample(true,0),NONE,400);assertNull(g.decision(3));g.observe(4,sample(false,0),NONE,600);assertNull(g.decision(4));}
    @Test public void stableLargeChangeIsAnnouncedOnce(){SceneChangeGate g=ready();for(int i=2;i<=4;i++)g.observe(i,sample(true,0),NONE,i*200);SceneChangeGate.Decision d=g.decision(4);assertNotNull(d);assertEquals(SceneChangeGate.Reason.SCENE_CHANGE,d.reason);g.accepted(d);g.observe(5,sample(true,0),NONE,1000);assertNull(g.decision(5));}
    @Test public void confirmedNewObstacleTriggersWithoutLargePixelChange(){SceneChangeGate g=ready();for(int i=2;i<=4;i++)g.observe(i,sample(false,0),Collections.singletonList(person()),i*200);assertEquals(SceneChangeGate.Reason.NEW_OBSTACLE,g.decision(4).reason);}
    @Test public void objectAlreadyInFirstDescriptionDoesNotRepeat(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),Collections.singletonList(person()),0);g.accepted(g.decision(1));for(int i=2;i<20;i++){g.observe(i,sample(false,0),Collections.singletonList(person()),i*200);assertNull(g.decision(i));}}
    @Test public void newObstacleDuringInferenceIsPreserved(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),NONE,0);SceneChangeGate.Decision first=g.decision(1);for(int i=2;i<=4;i++)g.observe(i,sample(false,0),Collections.singletonList(person()),i*200);g.accepted(first);assertEquals(SceneChangeGate.Reason.NEW_OBSTACLE,g.decision(4).reason);}
    @Test public void failedAnalysisDoesNotConsumeInitialEvent(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),NONE,0);assertNotNull(g.decision(1));g.observe(2,sample(false,0),NONE,2000);assertEquals(SceneChangeGate.Reason.INITIAL,g.decision(2).reason);}
    @Test public void oldProviderResultCannotConsumeNewProvidersFirstDescription(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),NONE,0);SceneChangeGate.Decision old=g.decision(1);g.reset();g.observe(2,sample(false,0),NONE,1000);g.accepted(old);assertEquals(SceneChangeGate.Reason.INITIAL,g.decision(2).reason);}
    @Test public void staleDescriptionCannotSpeakAfterViewChanges(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),NONE,0);SceneChangeGate.Decision old=g.decision(1);assertTrue(g.relevant(old));g.observe(2,sample(true,0),NONE,200);assertFalse(g.relevant(old));}
    @Test public void oldDescriptionIsObsoleteWhenNewObstacleAppears(){SceneChangeGate g=new SceneChangeGate();g.reset();g.observe(1,sample(false,0),NONE,0);SceneChangeGate.Decision old=g.decision(1);for(int i=2;i<5;i++)g.observe(i,sample(false,0),Collections.singletonList(person()),i*200);assertFalse(g.relevant(old));}
    @Test public void fastNoticeDoesNotInventDistanceOrCollision(){String text=FastObstacleNotice.describe(new YoloDetector.Box(56,.9f,new float[]{.05f,.4f,.25f,.9f}));assertEquals("화면 왼쪽에 의자 감지.",text);assertFalse(text.contains("위험"));}
    @Test public void reconnectDoesNotRepeatUnchangedScene(){SceneChangeGate g=ready();g.reconnect();g.observe(100,sample(false,0),NONE,5000);assertNull(g.decision(100));}
}
