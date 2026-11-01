package dev.giljabi;

import java.util.*;

/** Keeps walking-scene evidence across white-cane sweeps. It requests AI review, never speech. */
final class WalkingSceneGate {
    enum Reason { INITIAL, PATH_CHANGE, VISUAL_REVIEW }
    static final class Decision {
        final long epoch,sequence,observedAt,episode;
        final SceneSignature signature;
        final List<YoloDetector.Box> boxes;
        final Reason reason;
        final String context,stateKey;
        Decision(long epoch,long sequence,long observedAt,long episode,SceneSignature signature,List<YoloDetector.Box> boxes,Reason reason,String context,String stateKey){
            this.epoch=epoch;this.sequence=sequence;this.observedAt=observedAt;this.episode=episode;this.signature=signature;this.boxes=Collections.unmodifiableList(copyBoxes(boxes));this.reason=reason;this.context=context;this.stateKey=stateKey;
        }
    }
    private static final long KEEP_MS=12000,EVIDENCE_MS=3000,CLEAR_MS=3000,REVIEW_MS=12000;
    private static final int WINDOW=5,MAX_TRACKS=64;
    private static final String NO_CUES="no_confirmed_path_cues";
    private static final class Track {
        final long id;final String category;
        final boolean[] seen=new boolean[WINDOW],central=new boolean[WINDOW];
        final long[] times=new long[WINDOW];
        final int[] labels=new int[WINDOW];
        int slot,label;boolean confirmed,pathConfirmed;
        long lastSeen,lastSequence,lastPath=-1;
        String episodeBand;
        YoloDetector.Box box;
        Track(long id,String category,YoloDetector.Box box){this.id=id;this.category=category;this.box=copyBox(box);label=box.label;Arrays.fill(labels,-1);}
        void advance(){slot=(slot+1)%WINDOW;seen[slot]=central[slot]=false;labels[slot]=-1;}
        int count(boolean[] values,long now){int total=0;for(int i=0;i<WINDOW;i++)if(values[i]&&now>=times[i]&&now-times[i]<=EVIDENCE_MS)total++;return total;}
        void update(YoloDetector.Box next,long sequence,long now){
            float[] smooth=new float[4];for(int i=0;i<4;i++)smooth[i]=box.xy[i]*.4f+next.xy[i]*.6f;
            box=new YoloDetector.Box(next.label,next.score,smooth);lastSeen=now;lastSequence=sequence;
            seen[slot]=true;central[slot]=inCorridor(next);times[slot]=now;labels[slot]=next.label;
            if(count(seen,now)>=3)confirmed=true;
            // Keep a stable label within a family when the detector alternates chair/bench, etc.
            int bestVotes=0,best=label;
            for(int candidate:labels){if(candidate<0)continue;int votes=0;for(int i=0;i<WINDOW;i++)if(labels[i]==candidate&&now-times[i]<=EVIDENCE_MS)votes++;if(votes>bestVotes||(votes==bestVotes&&candidate==label)){bestVotes=votes;best=candidate;}}
            label=best;
            boolean retained=pathConfirmed&&lastPath>=0&&now-lastPath<=KEEP_MS;
            if(confirmed&&count(central,now)>=2){
                if(!retained)episodeBand=band(box);
                pathConfirmed=true;
                if(central[slot])lastPath=now;
            }else if(retained&&central[slot])lastPath=now;
        }
        boolean path(long now){return confirmed&&pathConfirmed&&lastPath>=0&&now-lastPath<=KEEP_MS;}
    }
    private final List<Track> tracks=new ArrayList<>();
    private long epoch,episode=1,nextId=1,sequence=-1,now=-1,lastAcceptedAt=-1,lastAcceptedSequence=-1,clearSince=-1;
    private int clearFrames;
    private boolean episodeHadPath;
    private SceneSignature latest;
    private List<YoloDetector.Box> latestBoxes=Collections.emptyList();
    private String acceptedKey;

    synchronized void reset(){epoch++;episode=1;nextId=1;sequence=now=lastAcceptedAt=lastAcceptedSequence=-1;clearSince=-1;clearFrames=0;episodeHadPath=false;tracks.clear();latest=null;latestBoxes=Collections.emptyList();acceptedKey=null;}
    synchronized void reconnect(){
        latest=null;latestBoxes=Collections.emptyList();sequence=-1;
        // Connection recovery does not create a new scene or forget an announced object.
        for(Track track:tracks){Arrays.fill(track.seen,false);Arrays.fill(track.central,false);Arrays.fill(track.labels,-1);track.lastSequence=-1;}
    }
    synchronized void observe(long sequence,SceneSignature signature,List<YoloDetector.Box> boxes,long now){
        if(sequence<=this.sequence||now<this.now)return;
        if(signature==null)throw new IllegalArgumentException("A scene signature is required");
        this.sequence=sequence;this.now=now;latest=signature;
        latestBoxes=signature.unclearMessage()==null?copyBoxes(boxes):Collections.emptyList();
        tracks.removeIf(t->now-t.lastSeen>KEEP_MS);
        for(Track track:tracks)track.advance();
        Set<Track> used=new HashSet<>();boolean visiblePathCandidate=false;
        for(YoloDetector.Box box:latestBoxes){
            if(!usable(box))continue;
            visiblePathCandidate|=inCorridor(box);
            String category=category(box.label);Track best=null;double bestCost=Double.MAX_VALUE;
            for(Track track:tracks){
                if(used.contains(track)||!track.category.equals(category))continue;
                double cost=association(track,box,now);
                if(cost<bestCost){best=track;bestCost=cost;}
            }
            if(best==null){
                if(tracks.size()>=MAX_TRACKS){Track victim=null;for(Track track:tracks)if(!used.contains(track)&&!track.path(now)&&(victim==null||track.lastSeen<victim.lastSeen))victim=track;if(victim==null)continue;tracks.remove(victim);}
                best=new Track(nextId++,category,box);tracks.add(best);
            }
            best.update(box,sequence,now);used.add(best);
        }
        boolean rememberedPath=false;for(Track track:tracks)rememberedPath|=track.path(now);
        if(rememberedPath){episodeHadPath=true;clearSince=-1;clearFrames=0;}
        else if(episodeHadPath&&!visiblePathCandidate&&signature.unclearMessage()==null){
            if(clearSince<0){clearSince=now;clearFrames=1;}else clearFrames++;
            if(now-clearSince>=CLEAR_MS&&clearFrames>=3){episode++;episodeHadPath=false;clearSince=-1;clearFrames=0;}
        }else{clearSince=-1;clearFrames=0;}
    }
    synchronized Decision decision(long frame){
        if(latest==null||frame!=sequence)return null;
        String key=stateKey();
        Reason reason=acceptedKey==null?Reason.INITIAL:!key.equals(acceptedKey)?Reason.PATH_CHANGE:
            now-lastAcceptedAt>=REVIEW_MS&&latest.unclearMessage()==null?Reason.VISUAL_REVIEW:null;
        return reason==null?null:new Decision(epoch,sequence,now,episode,latest,latestBoxes,reason,context(key),key);
    }
    /** Silent AI inspection snapshot; this does not consume or manufacture a path change. */
    synchronized Decision review(long frame){
        if(latest==null||frame!=sequence)return null;
        String key=stateKey();
        return new Decision(epoch,sequence,now,episode,latest,latestBoxes,Reason.VISUAL_REVIEW,context(key),key);
    }
    synchronized void accepted(Decision decision){
        if(decision.epoch!=epoch||decision.sequence<lastAcceptedSequence)return;
        acceptedKey=decision.stateKey;lastAcceptedSequence=decision.sequence;lastAcceptedAt=now;
    }
    synchronized boolean relevant(Decision decision){
        if(latest==null||decision.epoch!=epoch||decision.episode!=episode||now<decision.observedAt)return false;
        if(!Objects.equals(decision.signature.unclearMessage(),latest.unclearMessage())&&(decision.signature.unclearMessage()!=null||latest.unclearMessage()!=null))return false;
        // Raw image colour changes are expected during a cane sweep and do not invalidate a scene.
        return stateKey().equals(decision.stateKey);
    }
    private String stateKey(){
        SortedSet<String> keys=new TreeSet<>();
        for(Track track:tracks)if(track.path(now))keys.add(track.category+":"+track.episodeBand+":path");
        return keys.isEmpty()?NO_CUES:String.join("|",keys);
    }
    private String context(String key){
        StringBuilder text=new StringBuilder("camera_mount=white_cane_sweep; corridor=image_center_candidate_only; distance_metres=unavailable; image_upper_band_does_not_measure_head_height; no_detector_cue_does_not_mean_clear.\n");
        text.append("episode=").append(episode).append("; semantic_state=").append(key).append("\n");
        List<Track> ordered=new ArrayList<>();for(Track track:tracks)if(track.confirmed)ordered.add(track);
        ordered.sort((a,b)->{int priorityA=(a.path(now)?0:2)+(a.lastSequence==sequence?0:1),priorityB=(b.path(now)?0:2)+(b.lastSequence==sequence?0:1);return priorityA!=priorityB?Integer.compare(priorityA,priorityB):Long.compare(a.id,b.id);});
        int count=0;for(Track track:ordered){
            if(count++==8)break;boolean visible=track.lastSequence==sequence;
            text.append("track=").append(track.id).append("; object=").append(YoloDetector.LABELS[track.label]).append("; type=").append(track.category).append("; image_band=").append(band(track.box)).append("; corridor=").append(track.path(now)?"potential_path_cue":"side_or_background").append("; currently_visible=").append(visible).append("; age_ms=").append(now-track.lastSeen).append("; retained_only=").append(!visible).append("; box=");
            for(int i=0;i<4;i++){if(i>0)text.append(',');text.append(String.format(Locale.ROOT,"%.2f",track.box.xy[i]));}
            text.append('\n');
        }
        text.append("Retained-only tracks prevent repeat announcements; they are not current visible obstacles. Judge the current images before speaking.\n");
        return text.toString();
    }
    private static double association(Track track,YoloDetector.Box box,long now){
        double oldArea=Math.max(.0001,YoloDetector.area(track.box.xy)),area=Math.max(.0001,YoloDetector.area(box.xy));
        double ratio=Math.max(area/oldArea,oldArea/area);if(ratio>8)return Double.MAX_VALUE;
        double dx=(track.box.xy[0]+track.box.xy[2]-box.xy[0]-box.xy[2])/2,dy=(track.box.xy[1]+track.box.xy[3]-box.xy[1]-box.xy[3])/2;
        double distance=Math.hypot(dx,dy),overlap=YoloDetector.iou(track.box.xy,box.xy);
        if(overlap<.08&&distance>.45)return Double.MAX_VALUE;
        return distance+.10*Math.abs(Math.log(ratio))-.30*overlap+(now-track.lastSeen)/120000.0-(track.confirmed?.02:0);
    }
    private static boolean usable(YoloDetector.Box box){
        if(box.label<0||box.label>=YoloDetector.LABELS.length||!Float.isFinite(box.score)||box.score<.55f||box.xy==null||box.xy.length!=4)return false;
        for(float value:box.xy)if(!Float.isFinite(value)||value<0||value>1)return false;
        return box.xy[2]>box.xy[0]&&box.xy[3]>box.xy[1]&&YoloDetector.area(box.xy)>=.008f;
    }
    private static boolean inCorridor(YoloDetector.Box box){
        double y=(box.xy[1]+box.xy[3])/2,half=.14+.14*y,left=.5-half,right=.5+half;
        double overlap=Math.max(0,Math.min(right,box.xy[2])-Math.max(left,box.xy[0]));
        return overlap/Math.min(right-left,box.xy[2]-box.xy[0])>=.45;
    }
    private static String band(YoloDetector.Box box){double center=(box.xy[1]+box.xy[3])/2;return center<.38?"upper":center>.68?"lower":"body";}
    private static String category(int label){
        switch(label){case 0:return "person";case 1:case 3:return "two_wheeler";case 2:case 5:case 6:case 7:return "vehicle";case 13:case 56:case 57:return "seat";case 24:case 26:case 28:return "bag";case 58:return "plant";case 60:return "table";case 14:case 15:case 16:case 17:case 18:case 19:case 20:case 21:case 22:case 23:return "animal";default:return YoloDetector.LABELS[label];}
    }
    private static YoloDetector.Box copyBox(YoloDetector.Box box){return new YoloDetector.Box(box.label,box.score,box.xy.clone());}
    private static List<YoloDetector.Box> copyBoxes(List<YoloDetector.Box> boxes){List<YoloDetector.Box> copies=new ArrayList<>();for(YoloDetector.Box box:boxes)if(box!=null&&box.xy!=null&&box.xy.length==4)copies.add(copyBox(box));return copies;}
}
