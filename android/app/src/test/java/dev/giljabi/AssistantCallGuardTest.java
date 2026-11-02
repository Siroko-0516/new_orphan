package dev.giljabi;
import org.junit.Test;import static org.junit.Assert.*;
public class AssistantCallGuardTest {
 @Test public void silentOrFailedPromptCannotAuthorizeCall(){assertFalse(new AssistantCallGuard(10).confirm(1000));}
 @Test public void confirmedAudiblePromptIsSingleUse(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackStarted();assertTrue(g.confirm(1000));assertFalse(g.confirm(1001));}
 @Test public void expiredPromptCannotAuthorizeCall(){AssistantCallGuard g=new AssistantCallGuard(10);g.playbackStarted();assertFalse(g.confirm(30011));}
 @Test public void confirmationCannotTravelBackInTime(){AssistantCallGuard g=new AssistantCallGuard(100);g.playbackStarted();assertFalse(g.confirm(99));}
}
