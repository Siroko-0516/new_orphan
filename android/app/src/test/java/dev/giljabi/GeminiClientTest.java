package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiClientTest {
    static final String KEY="test-personal-key-not-real_12345";
    static final byte[] JPEG={(byte)255,(byte)216,2,(byte)255,(byte)217};
    static final String ANSWER="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"앞에 의자가 있습니다.\"}]},\"finishReason\":\"STOP\"}]}";
    static class Connection extends HttpURLConnection {
        int code=200,outputOpens;boolean disconnected,bodyClosed;
        ByteArrayOutputStream sent=new ByteArrayOutputStream();
        InputStream body=new FilterInputStream(new ByteArrayInputStream(ANSWER.getBytes(StandardCharsets.UTF_8))){@Override public void close()throws IOException{bodyClosed=true;super.close();}};
        Connection()throws Exception{super(new URL("https://generativelanguage.googleapis.com/v1beta/models/"+GeminiClient.DEFAULT_MODEL+":generateContent"));}
        @Override public OutputStream getOutputStream(){outputOpens++;return sent;}
        @Override public int getResponseCode()throws IOException{return code;}
        @Override public InputStream getInputStream()throws IOException{return body;}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    private GeminiClient.Request request(Connection connection,AtomicBoolean cancelled)throws IOException {
        return new GeminiClient.Request(GeminiClient.DEFAULT_MODEL,KEY,JPEG,"사진을 한국어로 설명하세요. YOLO may be wrong.",url->{assertEquals(connection.getURL(),url);return connection;},cancelled::get);
    }
    @Test public void postsOneSnapshotAndPromptWithKeyInHeaderOnly()throws Exception {
        Connection c=new Connection();assertEquals("앞에 의자가 있습니다.",request(c,new AtomicBoolean()).execute());
        assertEquals("POST",c.getRequestMethod());assertEquals(KEY,c.getRequestProperty("x-goog-api-key"));assertFalse(c.getURL().toString().contains(KEY));
        String raw=c.sent.toString(StandardCharsets.UTF_8);assertFalse(raw.contains(KEY));JSONObject body=new JSONObject(raw);
        JSONArray parts=body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts");
        assertArrayEquals(JPEG,Base64.getDecoder().decode(parts.getJSONObject(0).getJSONObject("inlineData").getString("data")));
        assertEquals("image/jpeg",parts.getJSONObject(0).getJSONObject("inlineData").getString("mimeType"));
        assertTrue(parts.getJSONObject(1).getString("text").contains("한국어"));assertEquals(1,c.outputOpens);assertTrue(c.disconnected);assertTrue(c.bodyClosed);
    }
    @Test public void excludesThoughtPartsAndJoinsVisibleAnswer()throws Exception {
        String json="{\"promptFeedback\":{\"blockReason\":\"BLOCK_REASON_UNSPECIFIED\"},\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"},{\"text\":\"Hello \"},{\"text\":\"world\"}]}}]}";
        assertEquals("Hello world",GeminiClient.response(json.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void explainsQuotaAndDoesNotEchoServerErrorOrRetry()throws Exception {
        Connection c=new Connection();c.code=429;c.body=new ByteArrayInputStream(("{\"error\":\""+KEY+"\"}").getBytes(StandardCharsets.UTF_8));
        IOException error=assertThrows(IOException.class,()->request(c,new AtomicBoolean()).execute());
        assertTrue(error.getMessage().contains("429"));assertFalse(error.getMessage().contains(KEY));assertEquals(1,c.outputOpens);assertTrue(c.disconnected);
    }
    @Test public void rejectsRedirectWithoutForwardingKey()throws Exception {
        Connection c=new Connection();c.code=307;
        IOException error=assertThrows(IOException.class,()->request(c,new AtomicBoolean()).execute());
        assertTrue(error.getMessage().contains("307"));assertFalse(c.getInstanceFollowRedirects());assertEquals(1,c.outputOpens);
    }
    @Test public void rejectsUnsafeModelAndHeaderBeforeOpeningConnection()throws Exception {
        AtomicBoolean opened=new AtomicBoolean();GeminiClient.Connector connector=url->{opened.set(true);throw new AssertionError();};
        assertThrows(IOException.class,()->new GeminiClient.Request("../evil?key=x",KEY,JPEG,"question",connector,()->false));
        assertThrows(IOException.class,()->new GeminiClient.Request(GeminiClient.DEFAULT_MODEL,KEY+"\r\nInjected: x",JPEG,"question",connector,()->false));
        assertFalse(opened.get());
    }
    @Test public void rejectsOversizedOrNonJpegBeforeOpeningConnection()throws Exception {
        GeminiClient.Connector connector=url->{throw new AssertionError("Invalid image must not open a connection");};
        byte[] big=new byte[GeminiClient.MAX_IMAGE+1];big[0]=(byte)255;big[1]=(byte)216;
        assertThrows(IOException.class,()->new GeminiClient.Request(GeminiClient.DEFAULT_MODEL,KEY,big,"question",connector,()->false));
        assertThrows(IOException.class,()->new GeminiClient.Request(GeminiClient.DEFAULT_MODEL,KEY,new byte[]{1,2,3,4},"question",connector,()->false));
    }
    @Test public void cancelledRequestDoesNotUpload()throws Exception {
        Connection c=new Connection();AtomicBoolean stopped=new AtomicBoolean();GeminiClient.Request r=request(c,stopped);stopped.set(true);
        assertThrows(IOException.class,r::execute);assertEquals(0,c.outputOpens);assertTrue(c.disconnected);
    }
    @Test public void closesBodyThatArrivesAfterCancellation()throws Exception {
        AtomicReference<GeminiClient.Request> holder=new AtomicReference<>();
        Connection c=new Connection(){@Override public InputStream getInputStream(){holder.get().close();return body;}};
        GeminiClient.Request r=request(c,new AtomicBoolean());holder.set(r);
        assertThrows(IOException.class,r::execute);assertTrue(c.disconnected);assertTrue(c.bodyClosed);
    }
    @Test public void reportsGeminiTimeoutSeparatelyFromCamera()throws Exception {
        Connection c=new Connection(){@Override public int getResponseCode()throws IOException{throw new SocketTimeoutException();}};
        IOException error=assertThrows(IOException.class,()->request(c,new AtomicBoolean()).execute());
        assertTrue(error.getMessage().contains("Gemini"));assertTrue(error.getMessage().contains("시간 초과"));assertTrue(c.disconnected);
    }
    @Test public void limitsResponseMemory()throws Exception {
        Connection c=new Connection();c.body=new ByteArrayInputStream(new byte[GeminiClient.MAX_RESPONSE+1]);
        IOException error=assertThrows(IOException.class,()->request(c,new AtomicBoolean()).execute());assertTrue(error.getMessage().contains("너무 큽"));
    }
    @Test public void blockedEmptyAndMalformedResponsesAreNotSuccesses()throws Exception {
        for(String body:new String[]{"{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}","{\"candidates\":[]}","{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"}]}}]}","not json"}){
            assertThrows(IOException.class,()->GeminiClient.response(body.getBytes(StandardCharsets.UTF_8)));
        }
    }
    @Test public void labelsTruncatedOutput()throws Exception {
        String body=ANSWER.replace("STOP","MAX_TOKENS");assertTrue(GeminiClient.response(body.getBytes(StandardCharsets.UTF_8)).contains("출력 길이 제한"));
    }
}
