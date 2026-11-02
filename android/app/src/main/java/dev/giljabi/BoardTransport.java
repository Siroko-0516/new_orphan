package dev.giljabi;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import java.util.function.Consumer;

interface BoardTransport extends AutoCloseable {
    boolean isReady();boolean isConnecting();boolean buttonsReady();
    void connect(BoardTarget target);
    boolean send(String value);
    @Override void close();
}

final class BleBoardTransport implements BoardTransport {
    private final BleLink link;
    BleBoardTransport(Context context,Consumer<String> status,Runnable changed,Consumer<String> received){link=new BleLink(context,status,ignored->{},changed,received);}
    @Override public boolean isReady(){return link.isReady();}
    @Override public boolean isConnecting(){return link.isConnecting();}
    @Override public boolean buttonsReady(){return link.buttonsReady();}
    @android.annotation.SuppressLint("MissingPermission") // BoardService checks CONNECT before calling.
    @Override public void connect(BoardTarget target){BluetoothAdapter adapter=link.adapter();if(adapter==null||!adapter.isEnabled())throw new IllegalStateException("블루투스를 켜주세요.");link.connect(new BleLink.Device(adapter.getRemoteDevice(target.address),"HM-10"),target.service,target.characteristic);}
    @Override public boolean send(String value){return link.send(value);}
    @Override public void close(){link.close();}
}
