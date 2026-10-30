package dev.giljabi;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** HM-10 UART writes. Public methods and application callbacks run on the main thread. */
@SuppressLint("MissingPermission") // Activity checks runtime permission before all BLE actions.
final class BleLink implements AutoCloseable {
    static final String DEFAULT_SERVICE="0000ffe0-0000-1000-8000-00805f9b34fb";
    static final String DEFAULT_CHARACTERISTIC="0000ffe1-0000-1000-8000-00805f9b34fb";
    static final class Device {
        final BluetoothDevice device;final String title;
        Device(BluetoothDevice device,String name){this.device=device;title=(name==null||name.isEmpty()?"이름 없음":name)+" · "+device.getAddress();}
        @Override public String toString(){return title;}
    }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Consumer<String> status;
    private final Consumer<List<Device>> found;
    private final Runnable stateChanged;
    private final Map<String,Device> devices=new LinkedHashMap<>();
    private final ArrayDeque<byte[]> writes=new ArrayDeque<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt current;
    private BluetoothGattCharacteristic writer;
    private UUID serviceId,characteristicId;
    private boolean writing;
    private final Runnable scanTimeout,connectionTimeout,writeTimeout;
    BleLink(Context context,Consumer<String> status,Consumer<List<Device>> found,Runnable stateChanged){
        this.context=context.getApplicationContext();this.status=status;this.found=found;this.stateChanged=stateChanged;
        scanTimeout=()->{stopScan();status.accept("BLE 검색 완료 · "+devices.size()+"개");};
        connectionTimeout=()->{disconnect();status.accept("BLE 연결 시간 초과 · 모듈 전원과 UUID를 확인하세요.");};
        writeTimeout=()->{disconnect();status.accept("BLE 쓰기 응답 시간 초과 · 재전송하지 않았습니다.");};
    }
    boolean isReady(){return current!=null&&writer!=null;}
    BluetoothAdapter adapter(){BluetoothManager manager=context.getSystemService(BluetoothManager.class);return manager==null?null:manager.getAdapter();}
    void scan(){
        disconnect();devices.clear();found.accept(new ArrayList<>());
        try{
            BluetoothAdapter adapter=adapter();
            if(adapter==null||!adapter.isEnabled()){status.accept("블루투스를 켠 뒤 다시 검색하세요.");return;}
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){status.accept("BLE 검색을 시작할 수 없습니다.");return;}
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            status.accept("BLE 기기 검색 중 · 12초");main.postDelayed(scanTimeout,12000);
        }catch(RuntimeException e){stopScan();status.accept("BLE 검색 실패: "+e.getMessage());}
    }
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult result){main.post(()->{
            if(scanner==null)return;
            try{
                BluetoothDevice device=result.getDevice();String address=device.getAddress();
                String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();if(name==null)name=device.getName();
                devices.put(address,new Device(device,name));found.accept(new ArrayList<>(devices.values()));
            }catch(SecurityException e){stopScan();status.accept("주변 기기 권한을 허용하고 다시 검색하세요.");}
        });}
        @Override public void onScanFailed(int code){main.post(()->{stopScan();status.accept("BLE 검색 오류: "+code);});}
    };
    void stopScan(){main.removeCallbacks(scanTimeout);BluetoothLeScanner old=scanner;scanner=null;if(old!=null)try{old.stopScan(scanCallback);}catch(RuntimeException ignored){}}
    void connect(Device device,String service,String characteristic){
        disconnect();
        try{
            serviceId=UUID.fromString(service.trim());characteristicId=UUID.fromString(characteristic.trim());
            current=device.device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(current==null)throw new IllegalStateException("GATT 연결을 만들 수 없습니다.");
            status.accept("BLE 연결 중 · "+device.title);main.postDelayed(connectionTimeout,15000);stateChanged.run();
        }catch(RuntimeException e){disconnect();status.accept("BLE 연결 실패: "+e.getMessage());}
    }
    private void onGatt(BluetoothGatt gatt,Runnable action){main.post(()->{if(current!=gatt)return;try{action.run();}catch(RuntimeException e){disconnect();status.accept("BLE 오류: "+e.getMessage());}});}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt gatt,int code,int state){onGatt(gatt,()->{
            if(code!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){disconnect();status.accept("BLE 연결 종료 · 상태 "+code);return;}
            if(state==BluetoothProfile.STATE_CONNECTED&&!gatt.discoverServices()){disconnect();status.accept("BLE 서비스 검색 실패");}
        });}
        @Override public void onServicesDiscovered(BluetoothGatt gatt,int code){onGatt(gatt,()->{
            BluetoothGattService service=gatt.getService(serviceId);
            BluetoothGattCharacteristic characteristic=service==null?null:service.getCharacteristic(characteristicId);
            if(code!=BluetoothGatt.GATT_SUCCESS||characteristic==null||(characteristic.getProperties()&(BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE))==0){
                disconnect();status.accept("쓰기 가능한 UART를 찾지 못했습니다. HM-10 종류와 UUID를 확인하세요.");return;
            }
            writer=characteristic;main.removeCallbacks(connectionTimeout);status.accept("BLE 연결됨 · 전송 준비 완료");stateChanged.run();
        });}
        @Override public void onCharacteristicWrite(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic,int code){onGatt(gatt,()->{
            if(!writing||writer==null||!characteristic.getUuid().equals(writer.getUuid()))return;
            main.removeCallbacks(writeTimeout);writing=false;
            if(code!=BluetoothGatt.GATT_SUCCESS){disconnect();status.accept("BLE 쓰기 실패 · 상태 "+code);return;}
            writes.poll();status.accept("BLE 쓰기 완료 · 아두이노 수신은 보드에서 확인하세요.");writeNext();
        });}
    };
    boolean send(String command){
        if(!isReady()){status.accept("먼저 HM-10을 연결하세요.");return false;}
        byte[] data=command.getBytes(StandardCharsets.US_ASCII);
        if(data.length>20||writes.size()>=4){status.accept("전송 대기 중 · 신호를 추가하지 않았습니다.");return false;}
        writes.add(data);writeNext();return isReady();
    }
    private void writeNext(){
        if(writing||writes.isEmpty()||!isReady())return;
        try{
            int type=(writer.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            writing=true;boolean accepted;
            if(Build.VERSION.SDK_INT>=33)accepted=current.writeCharacteristic(writer,writes.peek(),type)==BluetoothStatusCodes.SUCCESS;
            else{writer.setWriteType(type);writer.setValue(writes.peek());accepted=current.writeCharacteristic(writer);}
            if(!accepted){disconnect();status.accept("BLE 쓰기 요청을 시작하지 못했습니다.");return;}
            main.postDelayed(writeTimeout,5000);
        }catch(RuntimeException e){disconnect();status.accept("BLE 쓰기 실패: "+e.getMessage());}
    }
    void disconnect(){
        stopScan();main.removeCallbacks(connectionTimeout);main.removeCallbacks(writeTimeout);
        BluetoothGatt old=current;current=null;writer=null;writing=false;writes.clear();
        if(old!=null){try{old.disconnect();}catch(RuntimeException ignored){}try{old.close();}catch(RuntimeException ignored){}}stateChanged.run();
    }
    @Override public void close(){disconnect();}
}
