package dev.giljabi;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class CameraRequestTest {
    private static final String ADDRESS="http://192.168.4.1:81/stream";
    private static final byte[] JPEG={(byte)255,(byte)216,3,(byte)255,(byte)217};
    private static class Connection extends HttpURLConnection {
        boolean disconnected;int code=200,opens;String type="multipart/x-mixed-replace";
        InputStream body=new ByteArrayInputStream(JPEG);
        Connection() throws Exception {super(new URL(ADDRESS));}
        @Override public int getResponseCode() throws IOException {opens++;return code;}
        @Override public String getContentType(){return type;}
        @Override public InputStream getInputStream() throws IOException {return body;}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    @Test public void deliversHeaderAndFirstFrameProgressAndClosesSession() throws Exception {
        Connection connection=new Connection();List<String> stages=new ArrayList<>();
        try(LocalCamera.Request pending=LocalCamera.request(ADDRESS,url->connection,stages::add);LocalCamera.Session session=pending.open()){
            assertTrue(session.multipart);assertArrayEquals(JPEG,session.next());assertEquals(2,stages.size());assertFalse(connection.getInstanceFollowRedirects());
        }
        assertTrue(connection.disconnected);
    }
    @Test public void nonSuccessHttpClosesConnection() throws Exception {
        Connection connection=new Connection();connection.code=503;
        try{LocalCamera.open(ADDRESS,url->connection);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("503"));}
        assertTrue(connection.disconnected);
    }
    @Test public void redirectIsRejectedRatherThanFollowed() throws Exception {
        Connection connection=new Connection();connection.code=302;
        try{LocalCamera.open(ADDRESS,url->connection);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("302"));}
        assertFalse(connection.getInstanceFollowRedirects());assertTrue(connection.disconnected);
    }
    @Test public void htmlResponseDoesNotPretendToBeCameraVideo() throws Exception {
        Connection connection=new Connection();connection.type="text/html";
        try{LocalCamera.open(ADDRESS,url->connection);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("JPEG"));}
        assertTrue(connection.disconnected);
    }
    @Test public void headerTimeoutIdentifiesResponseStage() throws Exception {
        Connection connection=new Connection(){@Override public int getResponseCode() throws IOException {throw new SocketTimeoutException("timeout");}};
        try{LocalCamera.open(ADDRESS,url->connection);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("HTTP 응답 시간 초과"));assertTrue(expected.getCause() instanceof SocketTimeoutException);}
        assertTrue(connection.disconnected);
    }
    private InputStream timeoutBody(boolean oneFrame){
        return new InputStream(){boolean emitted;
            @Override public int read() throws IOException {throw new SocketTimeoutException("timeout");}
            @Override public int read(byte[] b,int off,int len) throws IOException {
                if(oneFrame&&!emitted){emitted=true;System.arraycopy(JPEG,0,b,off,JPEG.length);return JPEG.length;}
                throw new SocketTimeoutException("timeout");
            }
        };
    }
    @Test public void timeoutBeforeFirstFrameHasDifferentMessageFromInterruptedVideo() throws Exception {
        Connection first=new Connection();first.body=timeoutBody(false);
        try(LocalCamera.Session session=LocalCamera.open(ADDRESS,url->first)){
            try{session.next();fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("첫 영상 프레임"));}
        }
        Connection later=new Connection();later.body=timeoutBody(true);
        try(LocalCamera.Session session=LocalCamera.open(ADDRESS,url->later)){
            assertArrayEquals(JPEG,session.next());
            try{session.next();fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("영상 수신이"));assertFalse(expected.getMessage().contains("첫 영상"));}
        }
    }
    @Test public void cancelBeforeOpeningDoesNotStartHttpRequest() throws Exception {
        Connection connection=new Connection();LocalCamera.Request request=LocalCamera.request(ADDRESS,url->connection,s->{});request.close();
        try{request.open();fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("중지"));}
        assertEquals(0,connection.opens);assertTrue(connection.disconnected);
    }
    @Test public void cancellationOwnsConnectionEvenWhileWaitingForHeaders() throws Exception {
        CountDownLatch started=new CountDownLatch(1),disconnectSignal=new CountDownLatch(1);
        Connection connection=new Connection(){
            @Override public int getResponseCode() throws IOException {
                started.countDown();try{if(!disconnectSignal.await(2,TimeUnit.SECONDS))throw new IOException("not cancelled");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}throw new IOException("closed");
            }
            @Override public void disconnect(){super.disconnect();disconnectSignal.countDown();}
        };
        LocalCamera.Request request=LocalCamera.request(ADDRESS,url->connection,s->{});ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            Future<?> task=worker.submit(()->{try{request.open();fail();}catch(IOException expected){assertTrue(connection.disconnected);}});
            assertTrue(started.await(2,TimeUnit.SECONDS));request.close();task.get(2,TimeUnit.SECONDS);
        }finally{request.close();worker.shutdownNow();}
    }
    @Test public void cancellationBeforeBodyIsPublishedAlsoClosesLateBody() throws Exception {
        boolean[] closed={false};AtomicReference<LocalCamera.Request> request=new AtomicReference<>();
        Connection connection=new Connection(){@Override public InputStream getInputStream(){request.get().close();return new ByteArrayInputStream(JPEG){@Override public void close(){closed[0]=true;}};}};
        request.set(LocalCamera.request(ADDRESS,url->connection,s->{}));
        try{request.get().open();fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("중지"));}
        assertTrue(closed[0]);
    }
}
