package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;
public class AssistantCallGuardTest {
 @Test public void silentPromptCannotAuthorizeCall(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackCompleted(20);assertFalse(g.confirm(1000));}
 @Test public void startedButUnfinishedQuestionCannotAuthorizeCall(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackStarted();assertFalse(g.confirm(1000));}
 @Test public void finishedQuestionIsSingleUse(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackStarted();g.playbackCompleted(100);assertTrue(g.confirm(1000));assertFalse(g.confirm(1001));}
 @Test public void confirmationExpiresAfterAFullMinute(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackStarted();g.playbackCompleted(100);assertFalse(g.confirm(60101));}
 @Test public void delayedVoiceCannotReviveAnOldQuestion(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackStarted();g.playbackCompleted(120011);assertFalse(g.confirm(120012));}
 @Test public void confirmationCannotTravelBackInTime(){AssistantCallGuard g=new AssistantCallGuard(100);g.playbackStarted();g.playbackCompleted(200);assertFalse(g.confirm(199));}
}
