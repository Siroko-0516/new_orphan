package dev.giljabi;

import android.content.Context;
import android.net.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Per-request Internet route; ESP camera remains on its separate local Wi-Fi connection. */
final class CloudNetwork {
    static final class Lease implements AutoCloseable {
        final GeminiClient.Connector connector;final String description;
        private final ConnectivityManager manager;private final ConnectivityManager.NetworkCallback callback;
        private final AtomicBoolean closed=new AtomicBoolean();
        Lease(ConnectivityManager manager,Network network,ConnectivityManager.NetworkCallback callback,String description){
            this.manager=manager;this.callback=callback;this.description=description;connector=url->(HttpURLConnection)network.openConnection(url);
        }
        @Override public void close(){if(closed.compareAndSet(false,true)&&callback!=null)try{manager.unregisterNetworkCallback(callback);}catch(IllegalArgumentException ignored){}}
    }
    private static boolean internet(NetworkCapabilities caps){return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    private static String label(NetworkCapabilities caps){return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN 인터넷":caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"모바일 데이터":"인터넷 연결";}
    static Lease connect(Context context,BooleanSupplier cancelled) throws IOException {
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        if(manager==null)throw new IOException("휴대폰 인터넷 상태를 읽지 못했습니다.");
        if(cancelled.getAsBoolean())throw new IOException("Gemini 연결을 중지했습니다.");
        Network active=manager.getActiveNetwork();NetworkCapabilities activeCaps=active==null?null:manager.getNetworkCapabilities(active);
        // Keep an active VPN's policy; do not route around it via an underlying cellular network.
        if(activeCaps!=null&&activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){
            if(!activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))throw new IOException("VPN의 인터넷 연결을 확인하세요.");
            return new Lease(manager,active,null,label(activeCaps));
        }
        if(internet(activeCaps))return new Lease(manager,active,null,label(activeCaps));
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            if(internet(caps))return new Lease(manager,network,null,label(caps));
        }
        CountDownLatch ready=new CountDownLatch(1);AtomicReference<Network> chosen=new AtomicReference<>();
        ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){if(internet(caps)){chosen.compareAndSet(null,network);ready.countDown();}}
            @Override public void onUnavailable(){ready.countDown();}
        };
        boolean registered=false,handedOff=false;
        try{
            manager.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),callback,12000);registered=true;
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(14);
            while(System.nanoTime()<end){if(cancelled.getAsBoolean())throw new IOException("Gemini 연결을 중지했습니다.");if(ready.await(100,TimeUnit.MILLISECONDS))break;}
            Network network=chosen.get();if(network==null)throw new IOException("인터넷 연결이 없습니다. ESP Wi-Fi를 쓰면서 Gemini도 사용하려면 휴대폰 모바일 데이터를 켜세요. 연결이 없으면 로컬 AI를 선택할 수 있습니다.");
            handedOff=true;return new Lease(manager,network,callback,"모바일 데이터");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Gemini 연결을 중지했습니다.",e);}
        catch(SecurityException e){throw new IOException("인터넷 연결을 사용할 수 없습니다. 휴대폰 네트워크 설정을 확인하세요.",e);}
        finally{if(registered&&!handedOff)try{manager.unregisterNetworkCallback(callback);}catch(IllegalArgumentException ignored){}}
    }
}
