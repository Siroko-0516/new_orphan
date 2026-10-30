package dev.giljabi;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ObstacleTrackerTest {
    private YoloDetector.Box person(){return new YoloDetector.Box(0,.9f,new float[]{.3f,.4f,.7f,.9f});}
    private List<YoloDetector.Box> one(){return Collections.singletonList(person());}
    @Test public void requiresThreeConsecutiveObservationsAndDoesNotRepeatStationaryObject(){
        ObstacleTracker tracker=new ObstacleTracker();assertNull(tracker.update(one(),0));assertNull(tracker.update(one(),250));assertNotNull(tracker.update(one(),500));
        for(int i=3;i<30;i++)assertNull(tracker.update(one(),i*250));
    }
    @Test public void aMissResetsConfirmationButBriefOcclusionDoesNotCreateNewEvent(){
        ObstacleTracker tracker=new ObstacleTracker();tracker.update(one(),0);tracker.update(one(),250);tracker.update(Collections.emptyList(),500);
        assertNull(tracker.update(one(),750));assertNull(tracker.update(one(),1000));assertNotNull(tracker.update(one(),1250));
        tracker.update(Collections.emptyList(),1500);
        for(int i=7;i<20;i++)assertNull(tracker.update(one(),i*250));
    }
    @Test public void objectGoneForSeveralSecondsCanBeAnnouncedAgain(){
        ObstacleTracker tracker=new ObstacleTracker();tracker.update(one(),0);tracker.update(one(),250);assertNotNull(tracker.update(one(),500));
        assertNull(tracker.update(one(),4000));assertNull(tracker.update(one(),4250));assertNotNull(tracker.update(one(),4500));
    }
    @Test public void ignoresWeakTinyAndUpperBackgroundBoxes(){
        ObstacleTracker tracker=new ObstacleTracker();
        List<YoloDetector.Box> ignored=Arrays.asList(new YoloDetector.Box(0,.5f,person().xy),new YoloDetector.Box(0,.99f,new float[]{.4f,.5f,.41f,.51f}),new YoloDetector.Box(0,.99f,new float[]{.2f,0,.8f,.3f}));
        for(int i=0;i<20;i++)assertNull(tracker.update(ignored,i*250));
    }
    @Test public void resetRequiresFreshEvidenceAfterReconnect(){
        ObstacleTracker tracker=new ObstacleTracker();tracker.update(one(),0);tracker.update(one(),250);tracker.update(one(),500);tracker.reset();
        assertNull(tracker.update(one(),750));assertNull(tracker.update(one(),1000));assertNotNull(tracker.update(one(),1250));
    }
    @Test public void cooldownDelaysOtherConfirmedObjectsInsteadOfFloodingSignals(){
        ObstacleTracker tracker=new ObstacleTracker();List<YoloDetector.Box> both=Arrays.asList(person(),new YoloDetector.Box(2,.9f,new float[]{.15f,.5f,.35f,.9f}));
        tracker.update(both,0);tracker.update(both,250);assertNotNull(tracker.update(both,500));
        for(int i=3;i<10;i++)assertNull(tracker.update(both,i*250));
        assertNotNull(tracker.update(both,2500));
    }
}
