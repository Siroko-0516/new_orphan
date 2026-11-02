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
    private boolean closed;
    AssistantLocation(Context context,Callback callback){manager=context.getSystemService(LocationManager.class);this.callback=callback;}
    private final LocationListener listener=new LocationListener(){@Override public void onLocationChanged(Location location){accept(location);}@Override public void onStatusChanged(String p,int s,Bundle b){}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}};
    private final Runnable timeout=this::onTimeout;
    private void onTimeout(){if(!closed){close();callback.failed("정확한 현재 위치를 받지 못했어요. 휴대폰 위치 기능과 정확한 위치 권한을 확인해주세요.");}}
    @SuppressLint("MissingPermission") void start(Context context){
        if(manager==null||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){close();callback.failed("주변 검색에는 정확한 위치 권한이 필요해요. 앱의 위치 권한 버튼에서 허용해주세요.");return;}
        try{
            Location best=null;for(String provider:manager.getProviders(true)){Location last=manager.getLastKnownLocation(provider);if(usable(last)&&(best==null||last.getAccuracy()<best.getAccuracy()))best=last;}if(best!=null){accept(best);return;}
            boolean subscribed=false;
            for(String provider:Arrays.asList(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER,"fused"))if(manager.getAllProviders().contains(provider)&&manager.isProviderEnabled(provider)){manager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());subscribed=true;}
            if(!subscribed){close();callback.failed("휴대폰의 위치 기능을 켜주세요.");return;}
            main.postDelayed(timeout,20000);
        }catch(RuntimeException error){close();callback.failed("위치 권한과 휴대폰 위치 기능을 확인해주세요.");}
    }
    static boolean usable(Location l){return l!=null&&Double.isFinite(l.getLatitude())&&Double.isFinite(l.getLongitude())&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&l.hasAccuracy()&&l.getAccuracy()>=0&&l.getAccuracy()<=100&&SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()>=0&&SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()<=30_000_000_000L;}
    private void accept(Location l){if(!closed&&usable(l)){close();callback.found(new Location(l));}}
    @Override public void close(){closed=true;main.removeCallbacks(timeout);if(manager!=null)try{manager.removeUpdates(listener);}catch(RuntimeException ignored){}}
}
