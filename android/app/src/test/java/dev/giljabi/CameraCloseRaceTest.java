package dev.giljabi;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reproduce Android's chunked-input Unbalanced enter/exit during concurrent stop/read. */
public class CameraCloseRaceTest {
    private static final String ADDRESS="http://192.168.4.1:81/stream";
    private static final byte[] JPEG={(byte)255,(byte)216,1,(byte)255,(byte)217};
    private static class Connection extends HttpURLConnection {
        InputStream input;Runnable disconnect=()->{};
        Connection(InputStream input)throws Exception{super(new URL(ADDRESS));this.input=input;}
        public void connect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public String getContentType(){return "multipart/x-mixed-replace";}public InputStream getInputStream(){return input;}public void disconnect(){disconnect.run();}
    }
    @Test public void cancellationDefersBodyCloseToReaderAndContainsAndroidDisconnectFailure()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean reading=new AtomicBoolean();AtomicInteger closes=new AtomicInteger();AtomicReference<Thread> readOwner=new AtomicReference<>(),closeOwner=new AtomicReference<>();
        InputStream body=new InputStream(){
            public int read(){throw new AssertionError("buffered reads expected");}
            public int read(byte[] b,int off,int len)throws IOException{reading.set(true);readOwner.set(Thread.currentThread());entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IOException("test release timeout");System.arraycopy(JPEG,0,b,off,JPEG.length);return JPEG.length;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}finally{reading.set(false);}}
            public void close(){if(reading.get())throw new IllegalStateException("Unbalanced enter/exit");closes.incrementAndGet();closeOwner.set(Thread.currentThread());}
        };
        Connection connection=new Connection(body);connection.disconnect=()->{if(reading.get())throw new IllegalStateException("Unbalanced enter/exit");};
        LocalCamera.Request request=LocalCamera.request(ADDRESS,u->connection,s->{});ExecutorService io=Executors.newSingleThreadExecutor();
        try(LocalCamera.Session session=request.open()){
            Future<?> task=io.submit(()->{try{session.next();fail("Cancelled frame was delivered");}catch(IOException stopped){assertTrue(stopped.getMessage().contains("중지"));}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));for(int i=0;i<30;i++)request.close();assertEquals(0,closes.get());release.countDown();task.get(2,TimeUnit.SECONDS);assertEquals(1,closes.get());assertSame(readOwner.get(),closeOwner.get());
        }finally{release.countDown();request.close();io.shutdownNow();}
    }
    @Test public void runtimeFailureDuringBodyCloseCannotCrashTheCallingThread()throws Exception{
        AtomicInteger closes=new AtomicInteger();Connection c=new Connection(new ByteArrayInputStream(JPEG){public void close(){closes.incrementAndGet();throw new IllegalStateException("Unbalanced enter/exit");}});c.disconnect=()->{throw new IllegalStateException("Unbalanced enter/exit");};LocalCamera.Request request=LocalCamera.request(ADDRESS,u->c,s->{});request.open();request.close();request.close();assertEquals(1,closes.get());
    }
}
