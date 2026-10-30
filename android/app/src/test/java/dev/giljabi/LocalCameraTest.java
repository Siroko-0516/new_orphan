package dev.giljabi;

import org.junit.Test;
import java.io.*;
import java.net.URL;
import static org.junit.Assert.*;

public class LocalCameraTest {
    @Test public void allowsOnlyPrivateLiteralCameraAddresses() throws Exception {
        assertTrue(LocalCamera.isAllowed(new URL("http://192.168.4.1/capture")));
        assertTrue(LocalCamera.isAllowed(new URL("http://10.0.0.1:81/stream")));
        assertTrue(LocalCamera.isAllowed(new URL("https://172.16.0.1/capture")));
        assertFalse(LocalCamera.isAllowed(new URL("https://example.com/capture")));
        assertFalse(LocalCamera.isAllowed(new URL("http://127.0.0.1/capture")));
        assertFalse(LocalCamera.isAllowed(new URL("http://010.0.0.1/capture")));
        assertFalse(LocalCamera.isAllowed(new URL("http://172.32.0.1/capture")));
        assertFalse(LocalCamera.isAllowed(new URL("http://user:pass@192.168.4.1/capture")));
    }
    @Test public void extractsFirstFrameAcrossStreamBoundaries() throws Exception {
        byte[] data={1,2,(byte)255,(byte)216,3,4,(byte)255,(byte)217,9,(byte)255,(byte)216,1};
        InputStream chunked=new ByteArrayInputStream(data){@Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(1,len));}};
        assertArrayEquals(new byte[]{(byte)255,(byte)216,3,4,(byte)255,(byte)217},LocalCamera.firstJpeg(chunked,100));
    }
    @Test(expected=IOException.class) public void rejectsIncompleteImage() throws Exception {
        LocalCamera.firstJpeg(new ByteArrayInputStream(new byte[]{(byte)255,(byte)216,1}),100);
    }
    @Test(expected=IOException.class) public void boundsStreamReads() throws Exception {
        LocalCamera.firstJpeg(new ByteArrayInputStream(new byte[100]),20);
    }
    @Test public void retainsAllFramesWhenOneSocketReadContainsSeveralFrames() throws Exception {
        byte[] first={(byte)255,(byte)216,3,(byte)255,(byte)217};
        byte[] second={(byte)255,(byte)216,4,5,(byte)255,(byte)217};
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        encoded.write("--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes("US-ASCII"));encoded.write(first);
        encoded.write("\r\n--frame\r\n\r\n".getBytes("US-ASCII"));encoded.write(second);
        LocalCamera.JpegReader reader=new LocalCamera.JpegReader(new ByteArrayInputStream(encoded.toByteArray()),100);
        assertArrayEquals(first,reader.next());assertArrayEquals(second,reader.next());
    }
    @Test public void appliesSizeLimitPerFrameRatherThanEntireLiveConnection() throws Exception {
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();byte[] jpeg={(byte)255,(byte)216,3,(byte)255,(byte)217};
        for(int i=0;i<100;i++)encoded.write(jpeg);
        LocalCamera.JpegReader reader=new LocalCamera.JpegReader(new ByteArrayInputStream(encoded.toByteArray()),10);
        for(int i=0;i<100;i++)assertArrayEquals(jpeg,reader.next());
    }
    @Test public void rejectsPublicDestinationBeforeOpeningConnection() throws Exception {
        final boolean[] called={false};
        try{LocalCamera.open("http://example.com/stream",url->{called[0]=true;throw new IOException();});fail("Public camera destination accepted");}
        catch(IOException expected){assertFalse(called[0]);}
    }
}
