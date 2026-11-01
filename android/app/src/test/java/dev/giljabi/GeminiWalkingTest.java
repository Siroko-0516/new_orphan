package dev.giljabi;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class GeminiWalkingTest {
 private byte[] image(int i){return new byte[]{(byte)255,(byte)216,(byte)i,(byte)217};}
 @Test public void walkingRequestIncludesPreviousAndNewestPhotosAndQuietDecisionSchema()throws Exception{
  JSONObject body=new JSONObject(new String(GeminiClient.walkingPayload(new byte[][]{image(1),image(2),image(3)},"보행 상황을 비교하세요."),java.nio.charset.StandardCharsets.UTF_8));
  JSONArray parts=body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts");int images=0;for(int i=0;i<parts.length();i++)if(parts.getJSONObject(i).has("inlineData"))images++;assertEquals(3,images);
  JSONObject config=body.getJSONObject("generationConfig");assertEquals("application/json",config.getString("responseMimeType"));assertEquals("BOOLEAN",config.getJSONObject("responseSchema").getJSONObject("properties").getJSONObject("speak").getString("type"));
 }
 @Test public void fastThinkingOnlyAppliesToGemini3Walking()throws Exception{
  JSONObject fast=new JSONObject(new String(GeminiClient.walkingPayload(new byte[][]{image(1)},"설명"),java.nio.charset.StandardCharsets.UTF_8));assertEquals("MINIMAL",fast.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));
  JSONObject compatible=new JSONObject(new String(GeminiClient.walkingPayload(new byte[][]{image(1)},"설명","gemini-2.5-flash"),java.nio.charset.StandardCharsets.UTF_8));assertFalse(compatible.getJSONObject("generationConfig").has("thinkingConfig"));
 }
 @Test public void manualPhotoKeepsNormalTextResponse()throws Exception{JSONObject body=new JSONObject(new String(GeminiClient.payload(image(1),"설명"),java.nio.charset.StandardCharsets.UTF_8));assertFalse(body.getJSONObject("generationConfig").has("responseSchema"));}
 @Test public void walkingPhotosHaveCombinedSizeLimit(){assertThrows(java.io.IOException.class,()->GeminiClient.walkingPayload(new byte[][]{image(1),new byte[GeminiClient.MAX_IMAGE]},"설명"));assertThrows(java.io.IOException.class,()->GeminiClient.walkingPayload(new byte[][]{image(1),image(2),image(3),image(4)},"설명"));}
}
