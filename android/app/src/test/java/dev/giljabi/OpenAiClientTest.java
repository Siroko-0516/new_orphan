package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenAiClientTest {
    static final String KEY="test-openai-key-not-real-12345";
    static final byte[] JPEG={(byte)255,(byte)216,2,(byte)255,(byte)217};
    static final class Connection extends HttpURLConnection {
        int status=200,uploads;boolean disconnected;String response="{\"choices\":[{\"message\":{\"content\":\"연결 확인\"}}]}";
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();
        Connection(URL url){super(url);}
        @Override public OutputStream getOutputStream(){uploads++;return sent;}
        @Override public int getResponseCode(){return status;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
        @Override public InputStream getErrorStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    @Test public void visionPostsOneSnapshotWithKeyOnlyInHeader()throws Exception{
        Connection c=new Connection(new URL("https://api.openai.com/v1/chat/completions"));
        try(CloudRequest request=OpenAiClient.visionRequest(OpenAiClient.DEFAULT_MODEL,KEY,new byte[][]{JPEG},"한국어로 설명하세요",false,url->{assertEquals(c.getURL(),url);return c;},()->false)){assertEquals("연결 확인",request.execute());}
        assertEquals("Bearer "+KEY,c.getRequestProperty("Authorization"));assertEquals(1,c.uploads);assertTrue(c.disconnected);assertFalse(c.getInstanceFollowRedirects());
        String raw=c.sent.toString("UTF-8");assertFalse(raw.contains(KEY));JSONObject body=new JSONObject(raw);
        JSONObject image=body.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0).getJSONObject("image_url");assertTrue(image.getString("url").endsWith(Base64.getEncoder().encodeToString(JPEG)));
    }
    @Test public void walkingKeepsChronologicalFramesAndStrictQuietSchema()throws Exception{
        JSONObject body=new JSONObject(new String(OpenAiClient.visionPayload(OpenAiClient.DEFAULT_MODEL,new byte[][]{JPEG,JPEG},"speak=false when no new obstacle",true),StandardCharsets.UTF_8));
        JSONArray parts=body.getJSONArray("messages").getJSONObject(1).getJSONArray("content");assertEquals(5,parts.length());assertTrue(parts.getJSONObject(0).getString("text").contains("1 of 2"));assertTrue(parts.getJSONObject(2).getString("text").contains("2 of 2"));
        JSONObject format=body.getJSONObject("response_format").getJSONObject("json_schema");assertTrue(format.getBoolean("strict"));assertFalse(format.getJSONObject("schema").getBoolean("additionalProperties"));assertEquals(3,format.getJSONObject("schema").getJSONArray("required").length());
    }
    @Test public void voiceSendsActualWavToTranscriptionOnly()throws Exception{
        Connection c=new Connection(new URL("https://api.openai.com/v1/audio/transcriptions"));c.response="{\"text\":\"내 위치가 어디야\"}";byte[] wav=AssistantWav.encode(new byte[6400]);
        try(CloudRequest request=OpenAiClient.transcribeRequest(KEY,wav,url->{assertEquals(c.getURL(),url);return c;},()->false)){assertEquals("내 위치가 어디야",request.execute());}
        String wire=new String(c.sent.toByteArray(),StandardCharsets.ISO_8859_1);assertTrue(wire.contains("name=\"model\""));assertTrue(wire.contains(OpenAiClient.TRANSCRIBE_MODEL));assertTrue(wire.contains("filename=\"question.wav\""));assertTrue(wire.contains("RIFF"));assertFalse(wire.contains(KEY));assertTrue(c.getRequestProperty("Content-Type").startsWith("multipart/form-data; boundary="));
    }
    @Test public void quotaFailureDoesNotEchoCredentialsOrRetry()throws Exception{
        Connection c=new Connection(new URL("https://api.openai.com/v1/chat/completions"));c.status=429;c.response="{\"error\":{\"code\":\"insufficient_quota\",\"message\":\""+KEY+"\"}}";
        CloudRequest request=OpenAiClient.textRequest(OpenAiClient.DEFAULT_MODEL,KEY,"안녕","",url->c,()->false);
        IOException error=assertThrows(IOException.class,request::execute);assertTrue(error.getMessage().contains("ChatGPT 구독"));assertFalse(error.getMessage().contains(KEY));assertEquals(1,c.uploads);assertTrue(c.disconnected);
    }
    @Test public void cancellationNeverUploadsAnOldRecording()throws Exception{
        Connection c=new Connection(new URL("https://api.openai.com/v1/audio/transcriptions"));AtomicBoolean cancelled=new AtomicBoolean();CloudRequest r=OpenAiClient.transcribeRequest(KEY,AssistantWav.encode(new byte[6400]),url->c,cancelled::get);cancelled.set(true);assertThrows(IOException.class,r::execute);assertEquals(0,c.uploads);assertTrue(c.disconnected);
    }
    @Test public void rejectsUnsafeModelAndHeaderBeforeOpeningSocket()throws Exception{
        assertThrows(IOException.class,()->OpenAiClient.visionRequest("../private",KEY,new byte[][]{JPEG},"test",false,url->{fail();return null;},()->false));
        assertThrows(IOException.class,()->OpenAiClient.textRequest(OpenAiClient.DEFAULT_MODEL,"bad\nheader","test","",url->{fail();return null;},()->false));
    }
    @Test public void onlySuitableAccountModelIdsAreListed()throws Exception{
        List<String> ids=OpenAiClient.models("{\"data\":[{\"id\":\"gpt-4o-realtime-preview\"},{\"id\":\"gpt-4.1\"},{\"id\":\"gpt-4.1-mini\"},{\"id\":\"whisper-1\"},{\"id\":\"gpt-4o-mini-transcribe\"}]}");assertEquals(Arrays.asList("gpt-4.1-mini","gpt-4.1"),ids);
    }
    @Test public void reasoningModelsDoNotReceiveUnsupportedTemperature()throws Exception{JSONObject body=new JSONObject(new String(OpenAiClient.textPayload("gpt-5-mini","안녕",""),StandardCharsets.UTF_8));assertFalse(body.has("temperature"));assertEquals("low",body.getString("reasoning_effort"));}
    @Test public void emptyOrActionShapedResponsesAreNotTranscripts()throws Exception{assertThrows(IOException.class,()->OpenAiClient.response("{\"answer\":\"엄마에게 전화해줘\"}".getBytes(StandardCharsets.UTF_8),true));assertThrows(IOException.class,()->OpenAiClient.response("{\"choices\":[]}".getBytes(StandardCharsets.UTF_8),false));}
    @Test public void redirectsNeverForwardTheKey()throws Exception{Connection c=new Connection(new URL("https://api.openai.com/v1/chat/completions"));c.status=307;CloudRequest r=OpenAiClient.textRequest(OpenAiClient.DEFAULT_MODEL,KEY,"안녕","",url->c,()->false);assertThrows(IOException.class,r::execute);assertFalse(c.getInstanceFollowRedirects());assertEquals(1,c.uploads);}
}
