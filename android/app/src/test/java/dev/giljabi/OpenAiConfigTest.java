package dev.giljabi;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class OpenAiConfigTest {
    @Test public void providerSurvivesServiceIntentWithoutAnyKey()throws Exception{
        MonitorConfig c=new MonitorConfig("","보행 안내",OpenAiClient.DEFAULT_MODEL,true,true,true,true,2000,"",BleLink.DEFAULT_SERVICE,BleLink.DEFAULT_CHARACTERISTIC,CameraSource.BACK,AiProvider.OPENAI);
        c.validate();Intent i=c.write(new Intent());MonitorConfig restored=MonitorConfig.read(i);restored.validate();assertEquals(AiProvider.OPENAI,restored.provider);assertEquals(OpenAiClient.DEFAULT_MODEL,restored.model);assertFalse(i.hasExtra("key"));assertFalse(i.hasExtra("api_key"));
    }
    @Test public void oldGeminiConfigurationStillSelectsGemini()throws Exception{
        Intent i=new Intent().putExtra("cloud",true).putExtra("source",CameraSource.BACK).putExtra("model",GeminiClient.DEFAULT_MODEL);MonitorConfig c=MonitorConfig.read(i);c.validate();assertEquals(AiProvider.GEMINI,c.provider);
    }
}
