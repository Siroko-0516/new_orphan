package dev.giljabi;

/** Single-flight scheduling and generation fencing, independent of Android and wall-clock time. */
final class ContinuousGate {
    static final long MAX_FRAME_AGE_MS=5000;
    static final class Ticket {
        final long generation,frame;
        private Ticket(long generation,long frame){this.generation=generation;this.frame=frame;}
    }
    private long generation,lastFrame=-1,nextStart;
    private boolean running,paused;
    private Ticket inFlight;
    synchronized void start(){running=true;paused=false;generation++;lastFrame=-1;nextStart=0;}
    synchronized void stop(){running=false;paused=false;generation++;}
    synchronized void reset(){generation++;paused=false;lastFrame=-1;nextStart=0;}
    synchronized void invalidateFrames(){generation++;lastFrame=-1;nextStart=0;}
    synchronized Ticket begin(long frame,long receivedAt,long now,long minimumInterval){
        if(!running||paused||inFlight!=null||frame<=lastFrame||now<receivedAt||now-receivedAt>MAX_FRAME_AGE_MS||now<nextStart)return null;
        inFlight=new Ticket(generation,frame);lastFrame=frame;nextStart=now+Math.max(1000,minimumInterval);return inFlight;
    }
    synchronized boolean current(Ticket ticket){return running&&ticket!=null&&ticket.generation==generation;}
    synchronized boolean finish(Ticket ticket){boolean accept=current(ticket);if(inFlight==ticket)inFlight=null;return accept;}
    synchronized void defer(Ticket ticket,long until){if(current(ticket))nextStart=Math.max(nextStart,until);}
    synchronized void pause(Ticket ticket){if(current(ticket))paused=true;}
    synchronized boolean idle(){return inFlight==null;}
    synchronized boolean isPaused(){return paused;}
}
