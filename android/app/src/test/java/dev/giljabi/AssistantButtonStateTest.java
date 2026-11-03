package dev.giljabi;
import org.junit.Test;
import static org.junit.Assert.*;

public class AssistantButtonStateTest {
    @Test public void secondPressFinishesAndCompletionStartsRequest(){
        AssistantButtonState s=new AssistantButtonState();
        assertEquals(AssistantButtonState.Action.START,s.press());
        assertEquals(AssistantButtonState.Phase.RECORDING,s.phase());
        assertEquals(AssistantButtonState.Action.FINISH,s.press());
        assertFalse(s.enabled());
        assertEquals(AssistantButtonState.Action.IGNORE,s.press());
        assertTrue(s.recorded());
        assertEquals(AssistantButtonState.Phase.WAITING,s.phase());
    }
    @Test public void reachingLimitStillRequiresAButtonToSend(){
        AssistantButtonState s=new AssistantButtonState();s.press();
        assertFalse(s.recorded());
        assertEquals(AssistantButtonState.Phase.READY,s.phase());
        assertEquals(AssistantButtonState.Action.SEND,s.press());
        assertEquals(AssistantButtonState.Phase.WAITING,s.phase());
    }
    @Test public void buttonDuringThinkingOrSpeakingStartsFreshRecording(){
        AssistantButtonState s=new AssistantButtonState();
        s.waiting();assertEquals(AssistantButtonState.Action.START,s.press());
        s.speaking();assertEquals(AssistantButtonState.Action.START,s.press());
        assertEquals(AssistantButtonState.Phase.RECORDING,s.phase());
    }
    @Test public void idleDoesNotAcceptALateRecording(){
        AssistantButtonState s=new AssistantButtonState();s.press();s.idle();
        assertFalse(s.recorded());assertEquals(AssistantButtonState.Phase.IDLE,s.phase());
    }
}
