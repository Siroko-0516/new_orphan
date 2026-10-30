package dev.giljabi;

import android.content.Context;
import android.net.*;
import java.io.IOException;
import java.net.*;
import java.util.*;

/** Select the Wi-Fi with a directly reachable camera route, including unvalidated ESP APs. */
final class WifiCamera {
    static final class Choice {
        final LocalCamera.Connector connector;final String description;
        Choice(Network network,String description){connector=url->(HttpURLConnection)network.openConnection(url);this.description=description;}
    }
    static boolean sameSubnet(byte[] destination,byte[] local,int prefix){
        if(destination.length!=4||local.length!=4||prefix<1||prefix>32)return false;
        for(int bit=0;bit<prefix;bit++)if(((destination[bit/8]^local[bit/8])&(1<<(7-bit%8)))!=0)return false;
        return true;
    }
    static Choice choose(Context context,String address) throws IOException {
        URL url=new URL(address);if(!LocalCamera.isAllowed(url))throw new IOException("http://192.168.4.1:81/stream 같은 사설 IPv4 주소를 입력하세요.");
        String[] parts=url.getHost().split("\\.");byte[] bytes=new byte[4];for(int i=0;i<4;i++)bytes[i]=(byte)Integer.parseInt(parts[i]);
        InetAddress target=InetAddress.getByAddress(bytes);
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        if(manager==null)throw new IOException("네트워크 상태를 읽지 못했습니다.");
        Network best=null;int bestPrefix=-1;String selectedIp="";List<String> wifiIps=new ArrayList<>();
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);LinkProperties links=manager.getLinkProperties(network);
            if(caps==null||links==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
            int prefix=-1;String ip="";
            for(LinkAddress local:links.getLinkAddresses())if(local.getAddress() instanceof Inet4Address){
                ip=local.getAddress().getHostAddress();wifiIps.add(ip);
                if(sameSubnet(bytes,local.getAddress().getAddress(),local.getPrefixLength()))prefix=Math.max(prefix,local.getPrefixLength());
            }
            for(RouteInfo route:links.getRoutes())if(route.getDestination().getPrefixLength()>0&&route.matches(target))prefix=Math.max(prefix,route.getDestination().getPrefixLength());
            if(prefix>bestPrefix){best=network;bestPrefix=prefix;selectedIp=ip;}
        }
        if(best==null||bestPrefix<1){
            if(wifiIps.isEmpty())throw new IOException("카메라에 연결된 Wi-Fi를 찾지 못했습니다. 휴대폰을 ESP32-CAM Wi-Fi에 연결하고 인터넷 없이도 연결을 유지하세요.");
            throw new IOException("현재 Wi-Fi IP "+String.join(", ",wifiIps)+"에서는 카메라 "+url.getHost()+"로 연결되는 로컬 경로를 찾지 못했습니다. ESP32-CAM Wi-Fi를 선택하세요.");
        }
        return new Choice(best,"Wi-Fi "+selectedIp+" → ESP "+url.getHost());
    }
}
