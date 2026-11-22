package dev.giljabi;

/** No stability wait: current usable image, or a very recent clear image during a blur. */
final class MotionFrameWindow<T> {
    static final long BLUR_GRACE_MS=600;
    private static final class Frame<T> {final T value;final long at;final boolean usable;Frame(T value,long at,boolean usable){this.value=value;this.at=at;this.usable=usable;}}
    private Frame<T> newest,clear;
    synchronized void offer(T value,long at,boolean usable){
        if(newest!=null&&at<newest.at)return;
        newest=new Frame<>(value,at,usable);if(usable)clear=newest;
    }
    synchronized T latest(long now){
        if(newest==null)return null;
        if(!newest.usable&&clear!=null&&now>=newest.at&&now>=clear.at&&now-clear.at<=BLUR_GRACE_MS)return clear.value;
        return newest.value;
    }
    synchronized void clear(){newest=clear=null;}
}
