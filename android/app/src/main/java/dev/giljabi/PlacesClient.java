package dev.giljabi;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/** User-triggered public map search; no map API key is needed. */
final class PlacesClient {
    static final String ENDPOINT="https://overpass-api.de/api/interpreter";
    static final class Request implements AutoCloseable {
        private final HttpURLConnection connection;private final byte[] body;private final double lat,lon;private final BooleanSupplier cancelled;private volatile boolean closed;
        Request(String category,double lat,double lon,GeminiClient.Connector connector,BooleanSupplier cancelled)throws IOException{
            this.lat=lat;this.lon=lon;this.cancelled=cancelled;
            body=("data="+URLEncoder.encode(NearbyPlaces.query(category,lat,lon),"UTF-8")).getBytes(StandardCharsets.UTF_8);
            connection=connector.open(new URL(ENDPOINT));connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setConnectTimeout(10000);connection.setReadTimeout(20000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8");connection.setRequestProperty("User-Agent","Giljabi-Android/0.9 (user-triggered nearby search)");
        }
        List<NearbyPlaces.Place> execute()throws IOException{
            try{
                check();try(OutputStream output=connection.getOutputStream()){output.write(body);}check();
                int code=connection.getResponseCode();if(code!=200)throw new IOException("지도 검색에 연결하지 못했습니다. 잠시 후 다시 요청하세요. (HTTP "+code+")");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()){byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1){check();if(bytes.size()+n>1024*1024)throw new IOException("지도 검색 결과가 너무 많습니다.");bytes.write(block,0,n);}}
                check();return NearbyPlaces.parse(bytes.toString("UTF-8"),lat,lon);
            }finally{close();}
        }
        private void check()throws IOException{if(closed||cancelled.getAsBoolean())throw new IOException("검색을 중지했습니다.");}
        @Override public void close(){closed=true;connection.disconnect();}
    }
}
