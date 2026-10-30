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
}
