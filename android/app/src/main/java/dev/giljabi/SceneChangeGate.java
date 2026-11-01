package dev.giljabi;

import java.util.*;

/** Analyse once, then only confirmed large visual changes or newly confirmed obstacle boxes. */
final class SceneChangeGate {
    enum Reason { INITIAL, SCENE_CHANGE, NEW_OBSTACLE }
    static final class Decision {
        final long epoch,sequence,eventRevision;
        final SceneSignature signature;
        final List<YoloDetector.Box> boxes;
        final Reason reason;
        Decision(long epoch,long sequence,long eventRevision,SceneSignature signature,List<YoloDetector.Box> boxes,Reason reason){this.epoch=epoch;this.sequence=sequence;this.eventRevision=eventRevision;this.signature=signature;this.boxes=new ArrayList<>(boxes);this.reason=reason;}
    }
    private final ObstacleTracker obstacles=new ObstacleTracker();
    private SceneSignature baseline,latest,previous;
    private List<YoloDetector.Box> latestBoxes=Collections.emptyList(),baselineBoxes=Collections.emptyList();
    private YoloDetector.Box newObstacle;
    private long epoch,sequence=-1,eventRevision,ackRevision,eventAt,now,changedSince;
    private int changedFrames;
    synchronized void reset(){epoch++;baseline=null;latest=null;previous=null;sequence=-1;eventRevision=ackRevision=0;newObstacle=null;latestBoxes=baselineBoxes=Collections.emptyList();changedFrames=0;obstacles.reset();}
    synchronized void reconnect(){latest=null;previous=null;sequence=-1;changedFrames=0;newObstacle=null;ackRevision=eventRevision;obstacles.reset();}
    synchronized void observe(long sequence,SceneSignature signature,List<YoloDetector.Box> boxes,long now){
        if(sequence<=this.sequence)return;
        this.sequence=sequence;this.now=now;latest=signature;latestBoxes=new ArrayList<>(boxes);
        YoloDetector.Box event=obstacles.update(boxes,now);
        if(event!=null&&!matches(event,baselineBoxes)){newObstacle=event;eventRevision++;eventAt=now;}
        if(baseline!=null&&signature.majorChange(baseline)){
            if(changedFrames==0||previous==null||signature.difference(previous)>.10){changedFrames=1;changedSince=now;}else changedFrames++;
        }else changedFrames=0;
        previous=signature;
    }
    synchronized Decision decision(long frame){
        if(latest==null||frame!=sequence)return null;
        Reason reason=baseline==null?Reason.INITIAL:
            eventRevision>ackRevision&&now-eventAt<=10000&&newObstacle!=null&&matches(newObstacle,latestBoxes)?Reason.NEW_OBSTACLE:
            changedFrames>=3&&now-changedSince>=400?Reason.SCENE_CHANGE:null;
        return reason==null?null:new Decision(epoch,sequence,eventRevision,latest,latestBoxes,reason);
    }
    synchronized void accepted(Decision decision){
        if(decision.epoch!=epoch)return;
        baseline=decision.signature;baselineBoxes=decision.boxes;ackRevision=Math.max(ackRevision,decision.eventRevision);
        // An object already visible in the analysed picture is not a second new-object alert.
        if(newObstacle!=null&&matches(newObstacle,decision.boxes))ackRevision=eventRevision;
        changedFrames=0;
    }
    synchronized boolean relevant(Decision decision){
        if(decision.epoch!=epoch||latest==null)return false;
        if(latest.majorChange(decision.signature))return false;
        return !(eventRevision>decision.eventRevision&&newObstacle!=null&&matches(newObstacle,latestBoxes)&&!matches(newObstacle,decision.boxes));
    }
    private static boolean matches(YoloDetector.Box box,List<YoloDetector.Box> boxes){
        for(YoloDetector.Box other:boxes)if(box.label==other.label&&YoloDetector.iou(box.xy,other.xy)>=.15f)return true;
        return false;
    }
}
