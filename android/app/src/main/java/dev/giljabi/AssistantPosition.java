package dev.giljabi;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.*;
import java.util.*;

import java.util.function.Consumer;

/** Addresses come from Android's geocoder, never from an AI guess. */
final class AssistantPosition implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Consumer<String> callback;
    private boolean closed;
    AssistantPosition(Consumer<String> callback){this.callback=callback;}
    void start(Context context,Location fix){
        main.postDelayed(()->deliver(""),6000);
        if(!Geocoder.isPresent()){deliver("");return;}
        Geocoder geocoder=new Geocoder(context,Locale.KOREAN);
        if(Build.VERSION.SDK_INT>=33){
            try{geocoder.getFromLocation(fix.getLatitude(),fix.getLongitude(),1,new Geocoder.GeocodeListener(){
                @Override public void onGeocode(List<Address> addresses){main.post(()->deliver(address(addresses)));}
                @Override public void onError(String error){main.post(()->deliver(""));}
            });}catch(RuntimeException error){deliver("");}
        }else new Thread(()->{String value="";try{value=address(geocoder.getFromLocation(fix.getLatitude(),fix.getLongitude(),1));}catch(Exception ignored){}String result=value;main.post(()->deliver(result));},"assistant-address").start();
    }
    static String address(List<Address> addresses){
        if(addresses==null||addresses.isEmpty())return "";
        Address a=addresses.get(0);String line=a.getAddressLine(0);
        if(line==null||line.trim().isEmpty()){
            LinkedHashSet<String> pieces=new LinkedHashSet<>();
            for(String s:new String[]{a.getAdminArea(),a.getLocality(),a.getSubLocality(),a.getThoroughfare()})if(s!=null&&!s.trim().isEmpty())pieces.add(s.trim());
            line=String.join(" ",pieces);
        }
        line=line.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();return line.substring(0,Math.min(160,line.length()));
    }
    static String summary(Location fix){String time=new java.text.SimpleDateFormat("HH:mm:ss",Locale.KOREAN).format(new Date(System.currentTimeMillis()-AssistantLocation.ageSeconds(fix)*1000));return String.format(Locale.KOREAN,"확인한 위치: %.5f, %.5f · 추정 정확도 %d미터 · 확인 시각 %s",fix.getLatitude(),fix.getLongitude(),Math.max(1,Math.round(fix.getAccuracy())),time);}
    static String url(Location fix){return "https://www.google.com/maps/search/?api=1&query="+fix.getLatitude()+","+fix.getLongitude();}
    private void deliver(String result){if(closed)return;close();callback.accept(result);}
    @Override public void close(){closed=true;main.removeCallbacksAndMessages(null);}
}
