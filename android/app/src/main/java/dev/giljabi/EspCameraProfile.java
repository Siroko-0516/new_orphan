package dev.giljabi;

import android.graphics.BitmapFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Optional, verified source-resolution setup. Unsupported camera firmware keeps its settings. */
final class EspCameraProfile {
    static final int CLEAR=0,FAST=1,KEEP=2;
    static String configure(String address,int preset,LocalCamera.Connector connector,BooleanSupplier cancelled){
        if(preset==KEEP)return "ESP 기존 원본 설정 유지";
        int original=-1;URL base=null;
        try{
            URL stream=new URL(address);if(!LocalCamera.isAllowed(stream))return "ESP 기존 원본 설정 유지";
            int port=stream.getPort();if(port!=81&&port!=80&&port!=-1)return "ESP 사용자 지정 포트 · 기존 원본 설정 유지";
            base=new URL(stream.getProtocol(),stream.getHost(),port==81?80:port,"/");
            JSONObject status=new JSONObject(new String(get(new URL(base,"status"),connector,cancelled,32768),StandardCharsets.UTF_8));
            if(!status.has("framesize")||!status.has("pixformat")||!status.has("vflip")||!status.has("hmirror"))return "ESP 해상도 제어 미지원 · 기존 원본 설정 유지";
            original=status.getInt("framesize");
            // Current Giljabi firmware uses the modern enum: QVGA=6, VGA=9 or 10.
            // Start at 9, never above VGA; verify pixels rather than trusting enum values.
            if(original<6)return "ESP 구형 크기 규약 · 기존 원본 설정 유지";
            int target=preset==FAST?6:9;
            get(new URL(base,"control?var=framesize&val="+target),connector,cancelled,4096);
            int[] size=captureSize(base,connector,cancelled);
            if(preset==CLEAR&&size[0]==480&&size[1]==320){
                get(new URL(base,"control?var=framesize&val=10"),connector,cancelled,4096);
                size=captureSize(base,connector,cancelled);
            }
            int width=preset==FAST?320:640,height=preset==FAST?240:480;
            if(size[0]!=width||size[1]!=height)throw new IOException("Unexpected source dimensions");
            try{get(new URL(base,"control?var=quality&val="+(preset==FAST?20:18)),connector,cancelled,4096);}catch(IOException ignored){/* Resolution is verified; keep existing JPEG quality if unsupported. */}
            return "ESP 원본 "+width+"×"+height+(preset==FAST?" · 속도 우선":" · 선명도 우선");
        }catch(Exception unsupported){
            if(original>=0&&base!=null&&!cancelled.getAsBoolean())try{get(new URL(base,"control?var=framesize&val="+original),connector,cancelled,4096);}catch(Exception ignored){}
            return "ESP 자동 해상도 확인 불가 · 기존 원본 설정 유지";
        }
    }
    private static int[] captureSize(URL base,LocalCamera.Connector connector,BooleanSupplier cancelled)throws IOException{
        check(cancelled);
        try(LocalCamera.Request request=new LocalCamera.Request(new URL(base,"capture").toString(),connector,s->{},750,750);LocalCamera.Session session=request.open()){
            byte[] jpeg=session.next();check(cancelled);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid camera dimensions");return new int[]{bounds.outWidth,bounds.outHeight};
        }
    }
    private static void check(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean())throw new InterruptedIOException("Camera profile cancelled");}
    private static byte[] get(URL url,LocalCamera.Connector connector,BooleanSupplier cancelled,int limit)throws IOException{
        check(cancelled);HttpURLConnection c=connector.open(url);
        try{
            c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(750);c.setReadTimeout(750);c.setRequestMethod("GET");
            if(c.getResponseCode()!=200)throw new IOException("Camera control unavailable");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] block=new byte[2048];int n;while((n=in.read(block))!=-1){check(cancelled);if(out.size()+n>limit)throw new IOException("Oversized camera control response");out.write(block,0,n);}return out.toByteArray();
            }
        }finally{try{c.disconnect();}catch(RuntimeException ignored){}}
    }
}
