package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiHttpTest {
    @Test public void completeHttpPostCarriesTheSelectedPhotoAndReadsReply()throws Exception {
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            server.setSoTimeout(3000);
            Future<?> served=worker.submit(()->{
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(3000);InputStream input=socket.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int tail=0,b;
                    while((b=input.read())!=-1){header.write(b);tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;if(header.size()>8192)throw new IOException("Header too large");}
                    String[] lines=header.toString(StandardCharsets.US_ASCII).split("\r\n");assertEquals("POST /gemini HTTP/1.1",lines[0]);
                    Map<String,String> fields=new HashMap<>();for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)fields.put(lines[i].substring(0,colon).toLowerCase(Locale.ROOT),lines[i].substring(colon+1).trim());}
                    assertEquals(GeminiClientTest.KEY,fields.get("x-goog-api-key"));int length=Integer.parseInt(fields.get("content-length"));assertTrue(length<10000);
                    byte[] sent=new byte[length];new DataInputStream(input).readFully(sent);JSONObject body=new JSONObject(new String(sent,StandardCharsets.UTF_8));
                    assertEquals("image/jpeg",body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("mimeType"));
                    byte[] reply=GeminiClientTest.ANSWER.getBytes(StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(reply);out.flush();
                }catch(Exception error){throw new RuntimeException(error);}
            });
            URL local=new URL("http://127.0.0.1:"+server.getLocalPort()+"/gemini");
            try(GeminiClient.Request request=new GeminiClient.Request(GeminiClient.DEFAULT_MODEL,GeminiClientTest.KEY,GeminiClientTest.JPEG,"설명",endpoint->{assertEquals("generativelanguage.googleapis.com",endpoint.getHost());return (HttpURLConnection)local.openConnection();},()->false)){
                assertEquals("앞에 의자가 있습니다.",request.execute());
            }
            served.get(3,TimeUnit.SECONDS);
        }finally{worker.shutdownNow();}
    }
}
