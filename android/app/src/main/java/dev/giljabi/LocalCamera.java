package dev.giljabi;

import java.io.*;
import java.net.*;

/** Only a literal RFC1918 IPv4 camera address is accepted; redirects are not followed. */
final class LocalCamera {
    static boolean isAllowed(URL url){
        if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))return false;
        if(url.getUserInfo()!=null)return false;
        String[] parts=url.getHost().split("\\.");if(parts.length!=4)return false;
        int[] n=new int[4];try{for(int i=0;i<4;i++){if(!parts[i].matches("0|[1-9][0-9]{0,2}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}}catch(NumberFormatException e){return false;}
        return n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168);
    }
    static byte[] firstJpeg(InputStream in,int limit) throws IOException {
        return new JpegReader(in,limit).next();
    }
    /** One buffered reader is retained for the whole MJPEG connection, including unread frames. */
    static final class JpegReader {
        private final InputStream input;
        private final int limit;
        JpegReader(InputStream input,int limit){this.input=new BufferedInputStream(input,8192);this.limit=limit;}
        byte[] next() throws IOException {
            ByteArrayOutputStream out=new ByteArrayOutputStream();int previous=-1,read=0,b;boolean started=false;
            while((b=input.read())!=-1){
                if(++read>limit)throw new IOException("카메라 프레임이 너무 큽니다.");
                if(!started){if(previous==255&&b==216){started=true;out.write(255);out.write(216);}}
                else{out.write(b);if(previous==255&&b==217)return out.toByteArray();}
                previous=b;
            }
            throw new IOException("완전한 JPEG 프레임을 받지 못했습니다. 카메라 주소와 전원을 확인하세요.");
        }
    }
    interface Connector { HttpURLConnection open(URL url) throws IOException; }
    static Session open(String address,Connector connector) throws IOException {
        URL url=new URL(address);if(!isAllowed(url))throw new IOException("같은 네트워크의 사설 IPv4 카메라 주소를 입력하세요.");
        HttpURLConnection connection=connector.open(url);connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("카메라 HTTP 오류: "+connection.getResponseCode());
            String type=connection.getContentType();if(type==null||!(type.toLowerCase(java.util.Locale.ROOT).contains("image/jpeg")||type.toLowerCase(java.util.Locale.ROOT).contains("multipart/")))throw new IOException("카메라가 JPEG 영상을 반환하지 않습니다.");
            return new Session(connection,connection.getInputStream(),type.toLowerCase(java.util.Locale.ROOT).contains("multipart/"));
        }catch(IOException|RuntimeException e){connection.disconnect();throw e;}
    }
    static byte[] fetch(String address) throws IOException {return fetch(address,url->(HttpURLConnection)url.openConnection());}
    static byte[] fetch(String address,Connector connector) throws IOException {try(Session session=open(address,connector)){return session.reader.next();}}
    static final class Session implements AutoCloseable {
        final JpegReader reader;final boolean multipart;
        private final HttpURLConnection connection;private final InputStream input;
        Session(HttpURLConnection connection,InputStream input,boolean multipart){this.connection=connection;this.input=input;this.multipart=multipart;reader=new JpegReader(input,2*1024*1024);}
        @Override public void close(){connection.disconnect();try{input.close();}catch(IOException ignored){}}
    }
}
