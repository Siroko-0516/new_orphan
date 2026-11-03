package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlacesClientTest {
    static class Connection extends HttpURLConnection {
        final int code;int requests;boolean closed;
        Connection(URL url,int code){super(url);this.code=code;}
        public OutputStream getOutputStream(){requests++;return new ByteArrayOutputStream();}
        public int getResponseCode(){return code;}
        public InputStream getInputStream(){return new ByteArrayInputStream("{\"elements\":[]}".getBytes(StandardCharsets.UTF_8));}
        public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
    }
    @Test public void unavailableServerFailsOverOnce()throws Exception{
        List<Connection> calls=new ArrayList<>();try(PlacesClient.Request r=new PlacesClient.Request("병원",37.5,127,url->{Connection c=new Connection(url,calls.isEmpty()?503:200);calls.add(c);return c;},()->false)){assertTrue(r.execute().isEmpty());}
        assertEquals(2,calls.size());assertEquals(PlacesClient.ENDPOINT,calls.get(0).getURL().toString());assertEquals(PlacesClient.BACKUP,calls.get(1).getURL().toString());assertTrue(calls.get(0).closed&&calls.get(1).closed);
    }
    @Test public void rateLimitsAreNotRetriedOnAnotherServer()throws Exception{List<Connection> calls=new ArrayList<>();PlacesClient.Request r=new PlacesClient.Request("병원",37.5,127,url->{Connection c=new Connection(url,429);calls.add(c);return c;},()->false);assertThrows(IOException.class,r::execute);assertEquals(1,calls.size());assertTrue(calls.get(0).closed);}
    @Test public void cancelledSearchNeverUploadsLocation()throws Exception{Connection c=new Connection(new URL(PlacesClient.ENDPOINT),200);PlacesClient.Request r=new PlacesClient.Request("병원",37.5,127,url->c,()->true);assertThrows(IOException.class,r::execute);assertEquals(0,c.requests);assertTrue(c.closed);}
}
