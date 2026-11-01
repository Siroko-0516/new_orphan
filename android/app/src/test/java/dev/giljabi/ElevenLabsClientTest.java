package dev.giljabi;

import org.junit.Test;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class ElevenLabsClientTest {
    private static final String KEY="test-eleven-key-not-real";
    private static class Connection extends HttpURLConnection {
        int status=200;String type="audio/mpeg";byte[] bytes={'I','D','3',0,1,2,3};boolean disconnected;
        ByteArrayOutputStream sent=new ByteArrayOutputStream();
        Connection()throws Exception{super(new URL("https://api.elevenlabs.io/"));}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){disconnected=true;}
        public OutputStream getOutputStream(){return sent;}public int getResponseCode(){return status;}public String getContentType(){return type;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public long getContentLengthLong(){return bytes.length;}
    }
    @Test public void sendsOriginalModelAndFixedFemaleVoiceWithoutImage()throws Exception{
        Connection c=new Connection();
        try(ElevenLabsClient.Request request=new ElevenLabsClient.Request("왼쪽에 의자 감지.",KEY,ElevenLabsClient.FEMALE,url->{assertEquals("api.elevenlabs.io",url.getHost());assertTrue(url.getPath().endsWith(ElevenLabsClient.FEMALE));return c;},()->false)){
            assertArrayEquals(c.bytes,request.execute());assertEquals(KEY,c.getRequestProperty("xi-api-key"));JSONObject body=new JSONObject(c.sent.toString(StandardCharsets.UTF_8));assertEquals("eleven_multilingual_v2",body.getString("model_id"));assertEquals("왼쪽에 의자 감지.",body.getString("text"));assertEquals(2,body.length());assertFalse(c.getInstanceFollowRedirects());
        }assertTrue(c.disconnected);
    }
    @Test public void maleChoiceIsPreserved()throws Exception{Connection c=new Connection();try(ElevenLabsClient.Request r=new ElevenLabsClient.Request("안내",KEY,ElevenLabsClient.MALE,u->{assertTrue(u.getPath().endsWith(ElevenLabsClient.MALE));return c;},()->false)){r.execute();}}
    @Test public void authQuotaAndVoiceErrorsDoNotEchoCredentialsOrResponseBody()throws Exception{
        for(int code:new int[]{401,403,404,429,500}){Connection c=new Connection();c.status=code;try(ElevenLabsClient.Request r=new ElevenLabsClient.Request("안내",KEY,ElevenLabsClient.FEMALE,u->c,()->false)){IOException e=assertThrows(IOException.class,r::execute);assertTrue(e.getMessage().contains("ElevenLabs"));assertFalse(e.getMessage().contains(KEY));}assertTrue(c.disconnected);}
    }
    @Test public void rejectsNonAudioAndEmptyAudio()throws Exception{for(boolean empty:new boolean[]{false,true}){Connection c=new Connection();if(empty)c.bytes=new byte[0];else c.type="application/json";try(ElevenLabsClient.Request r=new ElevenLabsClient.Request("안내",KEY,ElevenLabsClient.FEMALE,u->c,()->false)){assertThrows(IOException.class,r::execute);}}}
    @Test public void redirectsAreNeverFollowedWithPrivateKey()throws Exception{Connection c=new Connection();c.status=302;try(ElevenLabsClient.Request r=new ElevenLabsClient.Request("안내",KEY,ElevenLabsClient.FEMALE,u->c,()->false)){assertThrows(IOException.class,r::execute);assertFalse(c.getInstanceFollowRedirects());}}
    @Test public void validatesHeaderVoiceAndTextBeforeNetwork()throws Exception{
        assertThrows(IOException.class,()->new ElevenLabsClient.Request("안내","bad\nkey",ElevenLabsClient.FEMALE,u->{throw new AssertionError();},()->false));
        assertThrows(IOException.class,()->new ElevenLabsClient.Request("안내",KEY,"../bad",u->{throw new AssertionError();},()->false));
        assertThrows(IOException.class,()->new ElevenLabsClient.Request("x".repeat(2001),KEY,ElevenLabsClient.FEMALE,u->{throw new AssertionError();},()->false));
    }
    @Test public void stopBeforeRequestDoesNotOpenNetwork()throws Exception{try(ElevenLabsClient.Request r=new ElevenLabsClient.Request("안내",KEY,ElevenLabsClient.FEMALE,u->{throw new AssertionError();},()->true)){assertThrows(InterruptedIOException.class,r::execute);}}
    @Test public void capsDeclaredAudioSize()throws Exception{Connection c=new Connection(){public long getContentLengthLong(){return ElevenLabsClient.MAX_AUDIO+1;}};try(ElevenLabsClient.Request r=new ElevenLabsClient.Request("안내",KEY,ElevenLabsClient.FEMALE,u->c,()->false)){assertThrows(IOException.class,r::execute);}}
}
