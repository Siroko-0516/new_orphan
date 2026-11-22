package dev.giljabi;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Cancellation must not close a chunked response concurrently or crash its caller. */
public class CloudCloseRaceTest {
    private void race(String kind)throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean reading=new AtomicBoolean();AtomicInteger closes=new AtomicInteger(),concurrentCloses=new AtomicInteger();
        InputStream body=new InputStream(){
            public int read(){throw new AssertionError("Expected buffered input");}
            public int read(byte[] b,int off,int len)throws IOException{reading.set(true);entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IOException("test timeout");for(int i=0;i<4;i++)b[off+i]=1;return 4;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}finally{reading.set(false);}}
            public void close(){if(reading.get()){concurrentCloses.incrementAndGet();throw new IllegalStateException("Unbalanced enter/exit");}closes.incrementAndGet();}
        };
        GeminiClient.Connector connector=url->new HttpURLConnection(url){
            public OutputStream getOutputStream(){return new ByteArrayOutputStream();}public int getResponseCode(){return 200;}public String getContentType(){return "audio/mpeg";}public InputStream getInputStream(){return body;}
            public void disconnect(){if(reading.get())throw new IllegalStateException("Unbalanced enter/exit");}public void connect(){}public boolean usingProxy(){return false;}
        };
        byte[] jpeg={(byte)255,(byte)216,1,(byte)255,(byte)217};String key="test-key-not-real-for-cancellation";CloudRequest request;
        if("gpt".equals(kind))request=OpenAiClient.visionRequest(OpenAiClient.DEFAULT_MODEL,key,new byte[][]{jpeg},"설명",false,connector,()->false);
        else if("gemini".equals(kind))request=new GeminiClient.Request(GeminiClient.DEFAULT_MODEL,key,jpeg,"설명",connector,()->false);
        else{ElevenLabsClient.Request eleven=new ElevenLabsClient.Request("짧은 안내",key,ElevenLabsClient.FEMALE,connector,()->false);request=new CloudRequest(){public String execute()throws IOException{eleven.execute();return "";}public void close(){eleven.close();}};}
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            Future<?> pending=worker.submit(()->{try{request.execute();fail("Cancelled request returned an answer");}catch(IOException expected){assertTrue(expected.getMessage().contains("중지"));}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));request.close();request.close();assertEquals(0,closes.get());release.countDown();pending.get(2,TimeUnit.SECONDS);assertEquals(0,concurrentCloses.get());assertEquals(1,closes.get());
        }finally{release.countDown();request.close();worker.shutdownNow();}
    }
    @Test public void gptCancellationUsesOwnerCleanup()throws Exception{race("gpt");}
    @Test public void geminiCancellationUsesOwnerCleanup()throws Exception{race("gemini");}
    @Test public void elevenCancellationUsesOwnerCleanup()throws Exception{race("eleven");}
}
