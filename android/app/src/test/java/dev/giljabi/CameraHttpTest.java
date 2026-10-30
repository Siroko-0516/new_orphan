package dev.giljabi;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real local TCP/HTTP transport, not an ESP or Android-device test. */
public class CameraHttpTest {
    @Test public void readsSuccessiveFramesOverOneRealHttpConnection() throws Exception {
        byte[] first={(byte)255,(byte)216,3,(byte)255,(byte)217},second={(byte)255,(byte)216,4,(byte)255,(byte)217};
        ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch nextFrame=new CountDownLatch(1);
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            server.setSoTimeout(3000);
            Future<?> served=worker.submit(()->{
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(3000);BufferedReader headers=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                    String line;while((line=headers.readLine())!=null&&!line.isEmpty()){}
                    OutputStream out=socket.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace;boundary=frame\r\nConnection: close\r\n\r\n--frame\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.write(first);out.flush();
                    if(!nextFrame.await(3,TimeUnit.SECONDS))throw new IOException("Client did not consume first frame");
                    out.write("\r\n--frame\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.write(second);out.flush();
                }catch(Exception e){throw new RuntimeException(e);}
            });
            // Only the test connector maps the validated camera URL to this loopback test server.
            String local="http://127.0.0.1:"+server.getLocalPort()+"/stream";
            try(LocalCamera.Session camera=LocalCamera.open("http://192.168.4.1:81/stream",url->(HttpURLConnection)new URL(local).openConnection())){
                assertTrue(camera.multipart);assertArrayEquals(first,camera.next());nextFrame.countDown();assertArrayEquals(second,camera.next());
            }
            served.get(3,TimeUnit.SECONDS);
        }finally{nextFrame.countDown();worker.shutdownNow();}
    }
}
