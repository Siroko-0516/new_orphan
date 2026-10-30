package dev.giljabi;

import java.util.*;

/** Heuristic new-object events, never a measurement of distance or collision risk. */
final class ObstacleTracker {
    private static final long LOST_MS=3000, COOLDOWN_MS=2000;
    private final List<Track> tracks=new ArrayList<>();
    private long frame,lastSent=-COOLDOWN_MS;
    private static final class Track {
        YoloDetector.Box box;long seen,lastFrame;int consecutive;boolean reported;
    }
    void reset(){tracks.clear();frame=0;lastSent=-COOLDOWN_MS;}
    YoloDetector.Box update(List<YoloDetector.Box> boxes,long now){
        frame++;tracks.removeIf(t->now-t.seen>LOST_MS);
        Set<Track> used=new HashSet<>();
        for(YoloDetector.Box box:boxes){
            if(!candidate(box))continue;
            Track match=null;float best=.25f;
            for(Track t:tracks){float overlap=YoloDetector.iou(t.box.xy,box.xy);if(!used.contains(t)&&t.box.label==box.label&&overlap>=best){match=t;best=overlap;}}
            if(match==null){if(tracks.size()>=64)continue;match=new Track();tracks.add(match);}
            match.consecutive=match.lastFrame==frame-1?match.consecutive+1:1;
            match.box=box;match.seen=now;match.lastFrame=frame;used.add(match);
        }
        if(now-lastSent<COOLDOWN_MS)return null;
        for(Track t:tracks)if(t.lastFrame==frame&&t.consecutive>=3&&!t.reported){t.reported=true;lastSent=now;return t.box;}
        return null;
    }
    private static boolean candidate(YoloDetector.Box b){
        if(b.score<.55f||YoloDetector.area(b.xy)<.025f||b.xy[3]<.4f||b.xy[2]<.15f||b.xy[0]>.85f)return false;
        switch(b.label){
            case 0:case 1:case 2:case 3:case 5:case 6:case 7:case 13:
            case 15:case 16:case 17:case 19:case 20:case 21:case 22:case 23:
            case 28:case 56:case 57:case 58:case 59:case 60:case 61:return true;
            default:return false;
        }
    }
}
