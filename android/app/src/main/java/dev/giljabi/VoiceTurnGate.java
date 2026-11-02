package dev.giljabi;

/** Speech probability hysteresis; media playback alone must not start a new turn. */
final class VoiceTurnGate {
    enum Event { NONE, START, END, IDLE }
    private int onset,voiced;private long start,lastVoice,lastActivity;private boolean recording;
    VoiceTurnGate(long now){reset(now);}
    void reset(long now){onset=voiced=0;start=lastVoice=lastActivity=now;recording=false;}
    void keepAlive(long now){lastActivity=now;}
    Event step(float probability,long now,boolean speaking,boolean bargeAllowed){
        if(speaking)lastActivity=now;
        boolean speech=Float.isFinite(probability)&&probability>=(speaking?.85f:.65f)&&(!speaking||bargeAllowed);
        if(!recording){
            onset=speech?onset+1:0;
            if(onset>=3){recording=true;voiced=onset;start=lastVoice=lastActivity=now;return Event.START;}
            return !speaking&&now-lastActivity>=15000?Event.IDLE:Event.NONE;
        }
        if(speech){voiced++;lastVoice=lastActivity=now;}
        if(now-lastVoice>=900||now-start>=10000){recording=false;onset=0;return voiced>=4?Event.END:Event.NONE;}
        return Event.NONE;
    }
    boolean recording(){return recording;}
}
