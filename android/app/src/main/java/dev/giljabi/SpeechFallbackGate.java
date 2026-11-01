package dev.giljabi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** One terminal outcome per utterance, fenced by cancellation and its caller's context. */
final class SpeechFallbackGate {
    private final BooleanSupplier valid;
    private final AtomicBoolean terminal=new AtomicBoolean(),cancelled=new AtomicBoolean(),started=new AtomicBoolean();
    SpeechFallbackGate(BooleanSupplier valid){this.valid=valid;}
    boolean current(){
        if(cancelled.get())return false;
        try{return valid.getAsBoolean()&&!cancelled.get();}catch(RuntimeException invalidContext){return false;}
    }
    boolean active(){return !terminal.get()&&current();}
    boolean hasStarted(){return started.get();}
    boolean claimStart(){return active()&&started.compareAndSet(false,true)&&current();}
    boolean fallback(){return current()&&terminal.compareAndSet(false,true)&&current();}
    boolean complete(){return current()&&terminal.compareAndSet(false,true)&&current();}
    void cancel(){cancelled.set(true);terminal.set(true);}
}
