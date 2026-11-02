package dev.giljabi;

/** A one-use confirmation is valid only after its own spoken prompt actually started. */
final class AssistantCallGuard {
    private final long expires;private boolean audible,consumed;
    AssistantCallGuard(long now){expires=now+30000;}
    void playbackStarted(){if(!consumed)audible=true;}
    boolean confirm(long now){boolean accepted=!consumed&&audible&&now>=expires-30000&&now<=expires;consumed=true;return accepted;}
}
