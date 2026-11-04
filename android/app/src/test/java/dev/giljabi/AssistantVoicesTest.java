package dev.giljabi;
import android.speech.tts.*;import java.util.*;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AssistantVoicesTest {
 private Voice voice(String name,boolean network,Set<String> features){return new Voice(name,Locale.KOREA,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,network,features);}
 @Test public void missingOrNetworkOnlyVoicesAreNotUsedOffline(){Voice good=voice("installed",false,Collections.emptySet());Set<Voice> all=new HashSet<>(Arrays.asList(good,voice("missing",false,Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)),voice("network",true,Collections.emptySet())));assertEquals(Collections.singletonList(good),AssistantVoices.korean(all,null));}
 @Test public void installedSystemDefaultIsPreferred(){Voice a=voice("a",false,Collections.emptySet()),b=voice("b",false,Collections.emptySet());assertEquals(b,AssistantVoices.korean(new HashSet<>(Arrays.asList(a,b)),b).get(0));}
 @Test public void missingKoreanDataDoesNotSelectEnglish(){Voice en=new Voice("en",Locale.US,400,100,false,Collections.emptySet());assertTrue(AssistantVoices.korean(Collections.singleton(en),en).isEmpty());}
}
