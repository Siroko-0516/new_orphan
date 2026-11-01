package dev.giljabi;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SceneEvidenceTest {
    private SceneSignature solid(int rgb){int[] p=new int[192];Arrays.fill(p,rgb);return new SceneSignature(p);}
    @Test public void uniformDarkAndWhiteInputCannotInventStairs(){for(int rgb:new int[]{0xff000000,0xffffffff,0xff808080,0xffff0000,0xff3030a0})assertNotNull(solid(rgb).unclearMessage());}
    @Test public void visibleStructureCanReachModel(){int[] p=new int[192];for(int i=0;i<p.length;i++)p[i]=i%2==0?0xff303030:0xffb0b0b0;assertNull(new SceneSignature(p).unclearMessage());}
    @Test public void unsupportedCaneClaimsAreRejectedButVisibleCaneIsAllowed(){String bad="계단은 흰 지팡이로 감지하지 못하므로 위험합니다.";assertNotEquals(bad,SceneLanguage.grounded(bad));String visible="왼쪽에 흰 지팡이가 보입니다.";assertEquals(visible,SceneLanguage.grounded(visible));assertNotEquals("A white cane cannot detect this curb.",SceneLanguage.grounded("A white cane cannot detect this curb."));}
    @Test public void localReplyHidesThinkingAndStopsAfterShortSentences(){assertEquals("",LocalReply.clean("<think>hidden"));assertEquals("앞에 의자입니다.",LocalReply.clean("<think>hidden</think>앞에 의자입니다."));assertFalse(LocalReply.complete("A box is 1.5 units wide."));assertTrue(LocalReply.complete("A chair is visible on the left. A person is on the right."));}
    @Test public void briefYoloContextExcludesUncertainAndTinyDetections(){assertEquals("none confidently detected",YoloDetector.brief(Arrays.asList(new YoloDetector.Box(0,.5f,new float[]{0,0,1,1}),new YoloDetector.Box(2,.99f,new float[]{0,0,.01f,.01f}))));}
}
