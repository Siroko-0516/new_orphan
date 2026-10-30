package dev.giljabi;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
        Request request=request(address,connector,value->{});
        try{return request.open();}catch(IOException|RuntimeException e){request.close();throw e;}
    }
    static byte[] fetch(String address) throws IOException {return fetch(address,url->(HttpURLConnection)url.openConnection());}
    static byte[] fetch(String address,Connector connector) throws IOException {try(Session session=open(address,connector)){return session.next();}}
    static Request request(String address,Connector connector,Consumer<String> progress) throws IOException {
        return new Request(address,connector,progress,10000,15000);
    }
    /** Owns the HTTP connection before headers arrive, so cancellation also closes pending opens. */
    static final class Request implements AutoCloseable {
        private final HttpURLConnection connection;
        private final Consumer<String> progress;
        private final AtomicBoolean closed=new AtomicBoolean();
        private volatile InputStream input;
        Request(String address,Connector connector,Consumer<String> progress,int connectMs,int readMs) throws IOException {
            URL url=new URL(address);if(!isAllowed(url))throw new IOException("사설 IPv4 카메라 주소를 입력하세요.");
            this.progress=progress;connection=connector.open(url);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setConnectTimeout(connectMs);connection.setReadTimeout(readMs);
            connection.setRequestProperty("Connection","close");
        }
        Session open() throws IOException {
            checkOpen();progress.accept("ESP HTTP 응답 대기 중...");
            try{
                int code=connection.getResponseCode();checkOpen();
                if(code!=200)throw new IOException("ESP HTTP 오류 "+code+" · 카메라 주소와 펌웨어를 확인하세요.");
                String type=connection.getContentType();
                if(type==null||!(type.toLowerCase(java.util.Locale.ROOT).contains("image/jpeg")||type.toLowerCase(java.util.Locale.ROOT).contains("multipart/")))throw new IOException("ESP 응답은 받았지만 JPEG 영상이 아닙니다. 주소를 확인하세요.");
                input=connection.getInputStream();
                if(closed.get()){try{input.close();}catch(IOException ignored){}checkOpen();}
                progress.accept("ESP 응답 확인 · 첫 사진 수신 대기 중...");
                return new Session(this,input,type.toLowerCase(java.util.Locale.ROOT).contains("multipart/"));
            }catch(SocketTimeoutException e){close();throw new IOException("ESP HTTP 응답 시간 초과 · 크롬의 카메라 영상 탭을 모두 닫고, ESP Wi-Fi 연결과 카메라 주소를 확인하세요.",e);}
            catch(IOException|RuntimeException e){close();throw e;}
        }
        private void checkOpen() throws IOException {if(closed.get())throw new IOException("카메라 연결을 중지했습니다.");}
        @Override public void close(){
            if(!closed.compareAndSet(false,true))return;
            connection.disconnect();InputStream active=input;if(active!=null)try{active.close();}catch(IOException ignored){}
        }
    }
    static final class Session implements AutoCloseable {
        final JpegReader reader;final boolean multipart;
        private final Request request;private long frames;
        Session(Request request,InputStream input,boolean multipart){this.request=request;this.multipart=multipart;reader=new JpegReader(input,2*1024*1024);}
        byte[] next() throws IOException {
            try{byte[] jpeg=reader.next();frames++;return jpeg;}
            catch(SocketTimeoutException e){throw new IOException((frames==0?"ESP 응답은 받았지만 첫 영상 프레임이 15초 동안 오지 않았습니다.":"ESP 영상 수신이 15초 동안 멈췄습니다.")+" 다른 카메라 탭과 전원·Wi-Fi 상태를 확인하세요.",e);}
        }
        @Override public void close(){request.close();}
    }
}
