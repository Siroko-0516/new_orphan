package dev.giljabi;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fixed frame sequences reproducing missed oncoming/entering people and cane flicker. */
public class WalkingEncounterRegressionTest {
    private SceneSignature image(){int[] p=new int[192];for(int i=0;i<p.length;i++)p[i]=i%16<8?0xff444444:0xffbbbbbb;return new SceneSignature(p);}
    private YoloDetector.Box person(float l,float t,float r,float b){return new YoloDetector.Box(0,.91f,new float[]{l,t,r,b});}
    private YoloDetector.Box person(){return person(.42f,.35f,.58f,.75f);}
    private void frame(WalkingSceneGate g,long n,long at,YoloDetector.Box... boxes){g.observe(n,image(),Arrays.asList(boxes),at);}
    private WalkingSceneGate initial(YoloDetector.Box box){WalkingSceneGate g=new WalkingSceneGate();g.reset();for(int i=1;i<=3;i++)frame(g,i,i*200,box);g.accepted(g.decision(3));return g;}
    @Test public void growingPersonRearmsSpeechAndMotorWithoutWaitingForClearScene(){
        WalkingSceneGate g=initial(person());WalkingSceneGate.Decision first=g.review(3);
        NarrationMemory speech=new NarrationMemory(),motor=new NarrationMemory();String text="앞쪽 통로에 사람이 있어요.";
        assertTrue(speech.offer(first.episode,first.memoryKey,text));assertTrue(motor.offer(first.episode,first.memoryKey,text));
        YoloDetector.Box larger=person(.35f,.15f,.65f,.85f);
        frame(g,4,800,larger);assertNull(g.decision(4));frame(g,5,1100,larger);
        WalkingSceneGate.Decision change=g.decision(5);assertNotNull(change);assertEquals(WalkingSceneGate.Reason.PATH_CHANGE,change.reason);
        assertEquals(first.episode,change.episode);assertEquals(first.stateKey,change.stateKey);assertNotEquals(first.memoryKey,change.memoryKey);
        assertTrue(speech.offer(change.episode,change.memoryKey,text));assertTrue(motor.offer(change.episode,change.memoryKey,text));
        assertFalse(speech.offer(change.episode,change.memoryKey,text));assertFalse(motor.offer(change.episode,change.memoryKey,text));
    }
    @Test public void smallBoxJitterAndCaneSweepDoNotRearmPerson(){
        WalkingSceneGate g=initial(person());String key=g.review(3).memoryKey;
        for(int i=4;i<=18;i++){float center=i%2==0?.42f:.58f;frame(g,i,i*200,person(center-.085f,.34f,center+.085f,.76f));assertEquals(key,g.review(i).memoryKey);assertNull(g.decision(i));}
    }
    @Test public void aGrowingSidePersonDoesNotCreateAPathEvent(){
        WalkingSceneGate g=new WalkingSceneGate();g.reset();frame(g,1,0);g.accepted(g.decision(1));String key=g.review(1).memoryKey;
        for(int i=2;i<=9;i++){frame(g,i,i*200,person(.01f,.10f,.14f,.85f));assertNull(g.decision(i));assertEquals(key,g.review(i).memoryKey);}
    }
    @Test public void stableDistinctPersonEntryRearmsOnce(){
        WalkingSceneGate g=initial(person());WalkingSceneGate.Decision first=g.review(3);YoloDetector.Box entrant=person(.67f,.70f,.83f,.99f);
        frame(g,4,800,person(),entrant);frame(g,5,1000,person(),entrant);assertEquals(first.memoryKey,g.review(5).memoryKey);
        frame(g,6,1200,person(),entrant);WalkingSceneGate.Decision change=g.decision(6);assertNotNull(change);assertNotEquals(first.memoryKey,change.memoryKey);g.accepted(change);
        for(int i=7;i<=12;i++){frame(g,i,i*200,person(),entrant);assertEquals(change.memoryKey,g.review(i).memoryKey);assertNull(g.decision(i));}
    }
    @Test public void simultaneousInitialPeopleDoNotCountAsAnotherEncounter(){
        WalkingSceneGate g=new WalkingSceneGate();g.reset();YoloDetector.Box second=person(.67f,.70f,.83f,.99f);frame(g,1,0,person(),second);String initial=g.review(1).memoryKey.substring(g.review(1).memoryKey.indexOf(";motion="));
        frame(g,2,200,person(),second);frame(g,3,400,person(),second);assertTrue(g.review(3).memoryKey.endsWith(initial));
    }
    @Test public void aMuchSmallerPersonAfterGapGetsFreshEncounterEvidence(){
        WalkingSceneGate g=initial(person(.32f,.15f,.68f,.90f));WalkingSceneGate.Decision first=g.review(3);frame(g,4,2600);
        YoloDetector.Box newcomer=person(.44f,.35f,.56f,.65f);frame(g,5,2800,newcomer);frame(g,6,3000,newcomer);frame(g,7,3200,newcomer);
        WalkingSceneGate.Decision next=g.review(7);assertEquals(first.episode,next.episode);assertNotEquals(first.memoryKey,next.memoryKey);
    }
    @Test public void briefPersonDropoutDoesNotRearm(){WalkingSceneGate g=initial(person());String key=g.review(3).memoryKey;frame(g,4,1000);frame(g,5,1200,person());assertEquals(key,g.review(5).memoryKey);assertNull(g.decision(5));}
    @Test public void newDetectorCueDoesNotCancelStillFreshVisualOnlyWarning(){WalkingSceneGate g=new WalkingSceneGate();g.reset();frame(g,1,0);WalkingSceneGate.Decision before=g.review(1);for(int i=2;i<=4;i++)frame(g,i,i*200,person());assertTrue(g.relevant(before));}
}
