package dev.giljabi;

/** One-use consent is accepted only after the complete target-specific question was played. */
final class AssistantCallGuard {
    private final long created;private long readyAt=-1,expires;private boolean started,consumed;
    AssistantCallGuard(long now){created=now;}
    void playbackStarted(){if(!consumed)started=true;}
    void playbackCompleted(long now){if(started&&!consumed&&now>=created&&now-created<=120000){readyAt=now;expires=Math.min(created+120000,now+60000);}}
    boolean ready(long now){return !consumed&&readyAt>=0&&now>=readyAt&&now<=expires;}
    boolean confirm(long now){boolean accepted=ready(now);consumed=true;return accepted;}
}
