package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** User-triggered OSM address/place lookup. No model-generated addresses or invented POIs. */
final class MapLookupClient {
    static final String BASE="https://nominatim.openstreetmap.org/";
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"address-deadline");t.setDaemon(true);return t;});
    private static long lastRequest;
    private static void coordinates(double lat,double lon)throws IOException{if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IOException("현재 위치를 확인하지 못했어요.");}
    static URL reverse(double lat,double lon)throws IOException{
        coordinates(lat,lon);return new URL(BASE+"reverse?format=jsonv2&addressdetails=1&accept-language=ko&zoom=18&lat="+lat+"&lon="+lon);
    }
    static URL nearby(String category,double lat,double lon,boolean clinic)throws IOException{
        coordinates(lat,lon);String phrase;
        switch(category){case "병원":phrase=clinic?"[clinic]":"[hospital]";break;case "약국":phrase="[pharmacy]";break;case "화장실":phrase="[toilets]";break;case "편의점":phrase="[convenience]";break;case "카페":phrase="[cafe]";break;case "음식점":phrase="[restaurant]";break;default:throw new IOException("지원하는 장소 종류를 확인해주세요.");}
        double dy=3000/111320.0,dx=dy/Math.max(.01,Math.cos(Math.toRadians(lat)));
        String box=Math.max(-180,lon-dx)+","+Math.min(90,lat+dy)+","+Math.min(180,lon+dx)+","+Math.max(-90,lat-dy);
        return new URL(BASE+"search?format=jsonv2&addressdetails=1&namedetails=1&extratags=1&accept-language=ko&bounded=1&limit=5&viewbox="+box+"&q="+URLEncoder.encode(phrase,"UTF-8"));
    }
    private static String clean(String value){String s=value==null?"":value.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();return s.length()>180?s.substring(0,180):s;}
    static String address(String body)throws IOException{
        try{
            JSONObject object=new JSONObject(body);if(object.has("error"))return "";
            JSONObject fields=object.optJSONObject("address");LinkedHashSet<String> parts=new LinkedHashSet<>();
            if(fields!=null)for(String key:new String[]{"state","province","city","town","county","municipality","city_district","borough","suburb","quarter","neighbourhood","village","hamlet","road","house_number"}){String s=clean(fields.optString(key,""));if(!s.isEmpty())parts.add(s);}
            if(fields!=null&&parts.isEmpty())return "";
            String value=String.join(" ",parts);if(value.isEmpty())value=clean(object.optString("display_name",""));
            return clean(value);
        }catch(JSONException error){throw new IOException("현재 위치의 주소 응답을 확인하지 못했어요.",error);}
    }
    static List<NearbyPlaces.Place> places(String body,String category,double lat,double lon)throws IOException{
        try{
            JSONArray rows=new JSONArray(body);List<NearbyPlaces.Place> found=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int i=0;i<Math.min(20,rows.length());i++){
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                String type=row.optString("type","");if(category.equals("병원")&&!Arrays.asList("hospital","clinic","doctors","doctor").contains(type))continue;
                JSONObject names=row.optJSONObject("namedetails"),extra=row.optJSONObject("extratags");
                String name=clean(names==null?row.optString("name",""):names.optString("name:ko",row.optString("name","")));
                if(name.isEmpty())name=clean(row.optString("display_name","").split(",",2)[0]);if(name.isEmpty())continue;
                double y=row.optDouble("lat",Double.NaN),x=row.optDouble("lon",Double.NaN);if(!Double.isFinite(y)||!Double.isFinite(x)||Math.abs(y)>90||Math.abs(x)>180)continue;
                double distance=NearbyPlaces.distance(lat,lon,y,x);if(distance>3200)continue;
                String identity=name+":"+Math.round(y*10000)+":"+Math.round(x*10000);if(!seen.add(identity))continue;
                String phone=extra==null?"":AssistantCommands.phone(extra.optString("phone",extra.optString("contact:phone","")));
                found.add(new NearbyPlaces.Place(name,phone,y,x,distance));
            }
            found.sort(Comparator.comparingDouble(p->p.distance));return new ArrayList<>(found.subList(0,Math.min(3,found.size())));
        }catch(JSONException error){throw new IOException("주변 장소의 검색 응답을 확인하지 못했어요.",error);}
    }
    static final class Request implements CloudRequest {
        private final GeminiClient.Connector connector;private final URL url;private final BooleanSupplier cancelled;
        private volatile HttpURLConnection connection;private volatile InputStream input;private volatile boolean closed;private ScheduledFuture<?> deadline;
        Request(URL url,GeminiClient.Connector connector,BooleanSupplier cancelled)throws IOException{if(!url.getProtocol().equals("https")||!url.getHost().equals("nominatim.openstreetmap.org"))throw new IOException("주소 조회 주소를 확인해주세요.");this.url=url;this.connector=connector;this.cancelled=cancelled;}
        private void check()throws IOException{if(closed||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("위치 검색이 취소됐거나 응답 시간이 지났어요.");}
        private void waitForSlot()throws IOException{
            while(true){check();synchronized(MapLookupClient.class){long now=System.nanoTime(),remaining=1_000_000_000L-(now-lastRequest);if(lastRequest==0||remaining<=0){lastRequest=now;return;}}
                try{Thread.sleep(25);}catch(InterruptedException stopped){Thread.currentThread().interrupt();check();}
            }
        }
        @Override public String execute()throws IOException{
            deadline=DEADLINES.schedule(this::close,7000,TimeUnit.MILLISECONDS);
            try{
                waitForSlot();check();connection=connector.open(url);check();connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setConnectTimeout(2500);connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent","Giljabi-Android/0.9.4 (https://github.com/Siroko-0516/new_orphan)");connection.setRequestProperty("Accept-Language","ko");
                int code=connection.getResponseCode();if(code!=200)throw new IOException(code==429?"주소·장소 조회가 잠시 제한됐어요. 조금 뒤 다시 말씀해주세요.":"주소·장소 조회 서비스가 응답하지 않았어요.");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();input=connection.getInputStream();
                try(InputStream stream=input){byte[] block=new byte[8192];for(int n;(n=stream.read(block))!=-1;){check();if(bytes.size()+n>256*1024)throw new IOException("위치 검색 응답이 너무 커요.");bytes.write(block,0,n);}}
                check();return bytes.toString(StandardCharsets.UTF_8.name());
            }finally{close();}
        }
        @Override public void close(){closed=true;if(deadline!=null)deadline.cancel(false);InputStream stream=input;if(stream!=null)try{stream.close();}catch(IOException ignored){}HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    }
}
