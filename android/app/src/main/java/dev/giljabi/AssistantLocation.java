package dev.giljabi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import androidx.core.content.ContextCompat;
import java.util.*;

/** One user-requested fix; continuous background location is not collected. */
final class AssistantLocation implements AutoCloseable {
    interface Callback {void found(Location location);void failed(String message);}
    private final LocationManager manager;private final Handler main=new Handler(Looper.getMainLooper());private final Callback callback;
    private boolean closed;private Location candidate;
    AssistantLocation(Context context,Callback callback){manager=context.getSystemService(LocationManager.class);this.callback=callback;}
    private final LocationListener listener=new LocationListener(){@Override public void onLocationChanged(Location location){accept(location);}@Override public void onStatusChanged(String p,int s,Bundle b){}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}};
    private final Runnable timeout=this::onTimeout;
    private void onTimeout(){if(!closed){if(usable(candidate)){accept(candidate);return;}close();callback.failed("현재 위치 신호를 받지 못했어요. 휴대폰 위치 기능을 켜고 창가나 실외에서 다시 요청해주세요.");}}
    @SuppressLint("MissingPermission") void start(Context context){
        if(closed)return;
        boolean fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(manager==null||!fine&&!coarse){close();callback.failed("위치 권한이 필요해요. 길잡이의 위치·연락처·전화 권한 설정에서 위치를 허용해주세요.");return;}
        try{
            if(Build.VERSION.SDK_INT>=28&&!manager.isLocationEnabled()){close();callback.failed("휴대폰의 위치 기능이 꺼져 있어요. 위치를 켠 뒤 다시 요청해주세요.");return;}
            for(String provider:manager.getProviders(true))try{Location last=manager.getLastKnownLocation(provider);if(usable(last)&&(candidate==null||quality(last)<quality(candidate)))candidate=new Location(last);}catch(SecurityException ignored){}
            if(candidate!=null&&ageSeconds(candidate)<=30&&(!fine||candidate.getAccuracy()<=100)){accept(candidate);return;}
            boolean subscribed=false;
            for(String provider:Arrays.asList(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER,"fused")){
                if(!fine&&provider.equals(LocationManager.GPS_PROVIDER))continue;
                try{if(manager.getAllProviders().contains(provider)&&manager.isProviderEnabled(provider)){manager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());subscribed=true;}}catch(SecurityException ignored){}
            }
            if(!subscribed){onTimeout();return;}
            main.postDelayed(timeout,candidate==null?10000:4000);
        }catch(RuntimeException error){close();callback.failed("위치 권한과 휴대폰 위치 기능을 확인해주세요.");}
    }
    static boolean usable(Location l){return l!=null&&Double.isFinite(l.getLatitude())&&Double.isFinite(l.getLongitude())&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&l.hasAccuracy()&&l.getAccuracy()>=0&&l.getAccuracy()<=3000&&SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()>=0&&SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()<=120_000_000_000L;}
    static long ageSeconds(Location l){return Math.max(0,(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1_000_000_000L);}
    private static double quality(Location l){return l.getAccuracy()+ageSeconds(l)*2;}
    private void accept(Location l){if(!closed&&usable(l)){close();callback.found(new Location(l));}}
    @Override public void close(){closed=true;main.removeCallbacks(timeout);if(manager!=null)try{manager.removeUpdates(listener);}catch(RuntimeException ignored){}}
}
