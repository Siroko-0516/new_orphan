package dev.giljabi;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Bounded user-triggered public map search, with one server failover. */
final class PlacesClient {
    static final String ENDPOINT="https://overpass-api.de/api/interpreter";
    static final String BACKUP="https://overpass.private.coffee/api/interpreter";
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"places-deadline");t.setDaemon(true);return t;});
    static final class HttpError extends IOException {final int code;HttpError(int code){super("장소 검색 서버 오류 (HTTP "+code+")");this.code=code;}}
    static boolean canUseFallback(IOException failure){return !(failure instanceof HttpError)||((HttpError)failure).code>=500;}
    static final class Request implements AutoCloseable {
        private volatile HttpURLConnection connection;private volatile InputStream input;
        private final GeminiClient.Connector connector;private final byte[] body;private final double lat,lon;private final BooleanSupplier cancelled;
        private volatile boolean closed;private ScheduledFuture<?> deadline;
        Request(String category,double lat,double lon,GeminiClient.Connector connector,BooleanSupplier cancelled)throws IOException{
            this.lat=lat;this.lon=lon;this.cancelled=cancelled;this.connector=connector;
            body=("data="+URLEncoder.encode(NearbyPlaces.query(category,lat,lon),"UTF-8")).getBytes(StandardCharsets.UTF_8);
            connection=open(ENDPOINT);
        }
        private HttpURLConnection open(String endpoint)throws IOException{
            HttpURLConnection c=connector.open(new URL(endpoint));c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(3000);c.setReadTimeout(5000);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8");c.setRequestProperty("User-Agent","Giljabi-Android/0.9 (user-triggered nearby search)");return c;
        }
        List<NearbyPlaces.Place> execute()throws IOException{
            deadline=DEADLINES.schedule(this::close,12000,TimeUnit.MILLISECONDS);
            try{
                try{return once();}catch(IOException failure){
                    check();
                    // Respect client/auth/rate-limit failures; no quota-bypassing retries.
                    if(failure instanceof HttpError&&((HttpError)failure).code<500)throw failure;
                    HttpURLConnection old=connection;if(old!=null)old.disconnect();
                    connection=open(BACKUP);check();return once();
                }
            }finally{close();}
        }
        private List<NearbyPlaces.Place> once()throws IOException{
            check();try(OutputStream output=connection.getOutputStream()){output.write(body);}check();
            int code=connection.getResponseCode();if(code!=200)throw new HttpError(code);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            input=connection.getInputStream();
            try(InputStream stream=input){byte[] block=new byte[8192];int n;while((n=stream.read(block))!=-1){check();if(bytes.size()+n>1024*1024)throw new IOException("지도 검색 결과가 너무 많습니다.");bytes.write(block,0,n);}}
            finally{input=null;}
            check();return NearbyPlaces.parse(bytes.toString("UTF-8"),lat,lon);
        }
        private void check()throws IOException{if(closed||cancelled.getAsBoolean())throw new IOException("지도 검색이 중지됐거나 응답 시간을 넘겼어요.");}
        @Override public void close(){closed=true;if(deadline!=null)deadline.cancel(false);HttpURLConnection c=connection;if(c!=null)c.disconnect();InputStream stream=input;if(stream!=null)try{stream.close();}catch(IOException ignored){}}
    }
}
