package dev.giljabi;
import org.junit.Test;
import static org.junit.Assert.*;
public class NarrationMemoryTest {
 @Test public void countFlickerAndParaphraseDoNotRepeatChair(){NarrationMemory m=new NarrationMemory();assertTrue(m.offer(1,"seat:body","전방 통로에 의자가 있는 것 같아요."));assertFalse(m.offer(1,"seat:lower","앞쪽에 의자 두 개가 보여요."));assertFalse(m.offer(1,"seat:body","의자가 세 개 놓여 있는 것 같아요."));}
 @Test public void genuineNewEpisodeCanMentionSameType(){NarrationMemory m=new NarrationMemory();assertTrue(m.offer(1,"seat","앞쪽에 의자가 있어요."));assertTrue(m.offer(2,"seat","앞쪽에 의자가 있어요."));}
 @Test public void newHazardAndBlockingSituationCanBeAnnounced(){NarrationMemory m=new NarrationMemory();assertTrue(m.offer(1,"seat","앞쪽에 의자가 있어요."));assertTrue(m.offer(1,"seat","앞쪽 의자가 통로를 가로막고 있어요."));assertTrue(m.offer(1,"seat","전방에 계단이 보여요."));}
 @Test public void pendingOrFailedSpeechDoesNotCountAsSpoken(){NarrationMemory m=new NarrationMemory();assertTrue(m.eligible(1,"seat","앞쪽에 의자가 있어요."));assertEquals("",m.previous());assertTrue(m.eligible(1,"seat","앞쪽에 의자가 있어요."));m.offer(1,"seat","앞쪽에 의자가 있어요.");assertFalse(m.eligible(1,"seat","전방에 의자가 보여요."));}
 @Test public void woodenChairAndGeneralKindsDoNotBecomeNewTree(){NarrationMemory m=new NarrationMemory();m.offer(1,"seat","전방 통로에 의자가 보여요.");assertFalse(m.offer(1,"seat","전방 통로에 나무 의자가 있어요."));assertFalse(m.offer(1,"seat","여러 가지 의자가 통로에 있어요."));assertTrue(m.offer(1,"seat","전방 통로에 나뭇가지가 보여요."));}
 @Test public void newEpisodePromptDoesNotPretendPriorChairWasAlreadyAnnounced(){NarrationMemory m=new NarrationMemory();m.offer(1,"seat","전방에 의자가 있어요.");assertEquals("",m.previous(2));assertFalse(m.previous(1).isEmpty());}
 @Test public void oppositeStairDirectionIsNewInformation(){NarrationMemory m=new NarrationMemory();m.offer(1,"stairs","올라가는 계단이 보여요.");assertTrue(m.offer(1,"stairs","내려가는 계단이 보여요."));}
 @Test public void confirmedWorseningCanEscalateOnceForSameObject(){NarrationMemory m=new NarrationMemory();m.offer(1,"vehicle","전방 통로에 차량이 보여요.",false);assertTrue(m.offer(1,"vehicle","전방 통로에 차량이 보여요.",true));assertFalse(m.offer(1,"vehicle","전방 통로에 차량이 보여요.",true));}
 @Test public void resetLetsNewSessionStart(){NarrationMemory m=new NarrationMemory();m.offer(1,"seat","앞쪽에 의자가 있어요.");m.reset();assertTrue(m.offer(1,"seat","앞쪽에 의자가 있어요."));}
}
