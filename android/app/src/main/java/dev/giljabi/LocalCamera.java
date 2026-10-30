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
        ByteArrayOutputStream out=new ByteArrayOutputStream();int previous=-1;boolean started=false;byte[] buffer=new byte[4096];int count,read=0;
        while((count=in.read(buffer))!=-1){
            for(int i=0;i<count;i++){
                int b=buffer[i]&255;if(++read>limit)throw new IOException("카메라 응답이 너무 큽니다.");
                if(!started){if(previous==255&&b==216){started=true;out.write(255);out.write(216);}}
                else{out.write(b);if(previous==255&&b==217)return out.toByteArray();}
                previous=b;
            }
        }
        throw new IOException("완전한 JPEG 프레임이 없습니다. /capture 주소를 확인하세요.");
    }
    static byte[] fetch(String address) throws IOException {
        URL url=new URL(address);if(!isAllowed(url))throw new IOException("같은 네트워크의 사설 IPv4 카메라 주소를 입력하세요.");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("카메라 HTTP 오류: "+connection.getResponseCode());
            String type=connection.getContentType();if(type==null||!(type.toLowerCase().contains("image/jpeg")||type.toLowerCase().contains("multipart/")))throw new IOException("카메라가 JPEG 영상을 반환하지 않습니다.");
            try(InputStream in=connection.getInputStream()){return firstJpeg(in,2*1024*1024);}
        }finally{connection.disconnect();}
    }
}
