package dev.giljabi;
import org.junit.Test;
import static org.junit.Assert.*;
public class MotionFrameWindowTest {
 @Test public void continuousChangingImagesNeverNeedAStablePeriod(){MotionFrameWindow<Integer> w=new MotionFrameWindow<>();for(int i=0;i<100;i++){w.offer(i,i*150,true);assertEquals(Integer.valueOf(i),w.latest(i*150));}}
 @Test public void briefBlurUsesRecentClearFrameWithoutAccumulatingVideo(){MotionFrameWindow<String> w=new MotionFrameWindow<>();w.offer("선명",1000,true);w.offer("흔들림",1200,false);assertEquals("선명",w.latest(1400));w.offer("새장면",1500,true);assertEquals("새장면",w.latest(1500));}
 @Test public void persistentBlurCannotReplayOldScene(){MotionFrameWindow<String> w=new MotionFrameWindow<>();w.offer("선명",1000,true);w.offer("흐림",1600,false);assertEquals("선명",w.latest(1600));assertEquals("흐림",w.latest(1601));}
 @Test public void clearAndReconnectForgetPriorImages(){MotionFrameWindow<String> w=new MotionFrameWindow<>();w.offer("옛장면",1000,true);w.clear();assertNull(w.latest(1100));w.offer("새연결흐림",1200,false);assertEquals("새연결흐림",w.latest(1200));}
 @Test public void outOfOrderOrFutureTimestampsDoNotSelectPastClearImage(){MotionFrameWindow<String> w=new MotionFrameWindow<>();w.offer("현재",1000,true);w.offer("오래된프레임",900,false);assertEquals("현재",w.latest(1000));w.offer("미래시각흐림",1500,false);assertEquals("미래시각흐림",w.latest(1400));}
}
