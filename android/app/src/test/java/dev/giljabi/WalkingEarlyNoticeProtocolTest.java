package dev.giljabi;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WalkingEarlyNoticeProtocolTest {
    private final byte[] jpeg={(byte)255,(byte)216,2,(byte)255,(byte)217};
    @Test public void bothProvidersKeepEarlyNoticeAtSystemPriority()throws Exception{
        String question=WalkingGuidance.prompt("주변 안내","새 관측","앞쪽에 사람이 있어요.",200,false);
        JSONObject gpt=new JSONObject(new String(OpenAiClient.visionPayload(OpenAiClient.DEFAULT_MODEL,new byte[][]{jpeg,jpeg},question,true),StandardCharsets.UTF_8));
        JSONObject gemini=new JSONObject(new String(GeminiClient.walkingPayload(new byte[][]{jpeg,jpeg},question),StandardCharsets.UTF_8));
        String a=gpt.getJSONArray("messages").getJSONObject(0).getString("content"),b=gemini.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text");
        for(String system:new String[]{a,b}){assertTrue(system.contains("unannounced person"));assertTrue(system.contains("do not wait for a fully blocked path"));assertTrue(system.contains("object class is unchanged"));assertTrue(system.contains("Claim approach or crossing only when chronological images support it"));}
        assertTrue(question.contains("\"previous_warning_matches_review_scope\":false"));
    }
    @Test public void earlyPresenceAndSupportedMotionRemainSpeakable()throws Exception{
        for(String message:new String[]{"앞쪽 통로에 사람이 있어요.","앞쪽에서 사람이 다가오고 있어요.","사람이 통로를 가로지르고 있어요."}){
            WalkingGuidance.Reply reply=WalkingGuidance.parse(new JSONObject().put("speak",true).put("message",message).put("urgency","notice").toString());
            assertTrue(reply.valid);assertTrue(reply.speak);assertFalse(reply.urgent);
        }
    }
}
