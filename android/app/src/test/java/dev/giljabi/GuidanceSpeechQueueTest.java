package dev.giljabi;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuidanceSpeechQueueTest {
 @Test public void regularUpdatesWaitForTheCurrentSentence(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();assertEquals("사람",q.offer("사람",false,()->true));assertNull(q.offer("의자",false,()->true));assertEquals("의자",q.finish("사람"));assertNull(q.finish("의자"));}
 @Test public void onlyTheNewestWaitingNoticeIsKept(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();q.offer("읽는중",false,()->true);q.offer("이전관측",false,()->true);q.offer("최신관측",false,()->true);assertEquals("최신관측",q.finish("읽는중"));assertNull(q.finish("최신관측"));}
 @Test public void urgentWarningInterruptsAndLateCompletionCannotConsumeIt(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();q.offer("보통안내",false,()->true);q.offer("기다린안내",false,()->true);assertEquals("긴급",q.offer("긴급",true,()->true));assertNull(q.finish("보통안내"));assertNull(q.finish("긴급"));}
 @Test public void staleWaitingObservationIsDiscarded(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();AtomicBoolean fresh=new AtomicBoolean(true);q.offer("현재",false,()->true);q.offer("다음",false,fresh::get);fresh.set(false);assertNull(q.finish("현재"));}
 @Test public void stopOrAssistantCallClearsEveryQueuedUtterance(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();q.offer("현재",false,()->true);q.offer("다음",false,()->true);q.clear();assertNull(q.finish("현재"));assertEquals("새질문후새장면",q.offer("새질문후새장면",false,()->true));}
 @Test public void expiredCurrentOutputDoesNotBlockFreshInformation(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();AtomicBoolean valid=new AtomicBoolean(true);q.offer("과거",false,valid::get);valid.set(false);assertEquals("현재",q.offer("현재",false,()->true));assertNull(q.finish("과거"));assertNull(q.finish("현재"));}
 @Test public void invalidNewOutputDoesNotInterruptOrReplaceWaitingOne(){GuidanceSpeechQueue<String> q=new GuidanceSpeechQueue<>();q.offer("현재",false,()->true);q.offer("다음",false,()->true);assertNull(q.offer("만료",true,()->false));assertEquals("다음",q.finish("현재"));}
}
