package dev.giljabi;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** The single GATT owner. AI, speech and Activity lifetimes only observe this service. */
public class BoardService extends Service {
    static final String CONNECT="dev.giljabi.BOARD_CONNECT",DISCONNECT="dev.giljabi.BOARD_DISCONNECT";
    private static final String CHANNEL="giljabi-board";
    private static final int NOTICE=62;
    interface Observer {void state(Snapshot snapshot);}
    static final class Snapshot {
        final boolean enabled,ready,connecting,buttonsReady;
        final String address,service,characteristic,status;
        Snapshot(boolean enabled,boolean ready,boolean connecting,boolean buttons,BoardTarget target,String status){this.enabled=enabled;this.ready=ready;this.connecting=connecting;buttonsReady=buttons;address=target==null?"":target.address;service=target==null?BleLink.DEFAULT_SERVICE:target.service;characteristic=target==null?BleLink.DEFAULT_CHARACTERISTIC:target.characteristic;this.status=status;}
    }
    private static volatile BoardService instance;
    private static volatile Snapshot last=new Snapshot(false,false,false,false,null,"BLE 미연결");
    private static final CopyOnWriteArrayList<Observer> OBSERVERS=new CopyOnWriteArrayList<>();
    static Snapshot snapshot(){return last;}
    static boolean active(){return last.enabled;}
    static boolean ready(){return last.ready;}
    static void observe(Observer observer){OBSERVERS.addIfAbsent(observer);observer.state(last);}
    static void forget(Observer observer){OBSERVERS.remove(observer);}
    static void connect(Context context,String address,String service,String characteristic)throws IOException{
        BoardTarget target=new BoardTarget(address,service,characteristic);
        if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new IOException("주변 기기 권한을 허용해주세요.");
        BoardService s=instance;if(s!=null&&s.running&&target.equals(s.target)){s.connectNow();return;}
        try{ContextCompat.startForegroundService(context,new Intent(context,BoardService.class).setAction(CONNECT).putExtra("address",target.address).putExtra("service",target.service).putExtra("characteristic",target.characteristic));}
        catch(RuntimeException error){throw new IOException("앱을 화면에 열고 BLE 연결을 눌러주세요.",error);}
    }
    static void disconnect(Context context){if(active())context.startService(new Intent(context,BoardService.class).setAction(DISCONNECT));}
    static boolean send(String command){
        BoardService s=instance;if(s==null||!s.running||s.link==null||!s.link.isReady())return false;
        if(!"TEST\n".equals(command)&&!"OBSTACLE\n".equals(command))return false;
        return s.link.send(command);
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private BoardTransport link;private BoardTarget target;
    private boolean running,foreground,closed,updating;private String status="BLE 연결 준비 중";
    private long lastNotice;
    private final Runnable retry=()->connectNow();
    BoardTransport createTransport(Consumer<String> status,Runnable changed,Consumer<String> received){return new BleBoardTransport(this,status,changed,received);}
    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationManager manager=getSystemService(NotificationManager.class);if(manager!=null)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"길잡이 보드 연결",NotificationManager.IMPORTANCE_LOW));
        initializeTransport();
    }
    private void initializeTransport(){
        link=createTransport(message->{status=message;publish();},this::changed,command->{if("ASSIST".equals(command)){boolean handled=AssistantService.boardButton();status=handled?"버튼 신호 수신 · 음성비서에 전달했어요.":"버튼 신호 수신 · 음성비서를 시작하면 질문할 수 있어요.";publish();}});
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||DISCONNECT.equals(intent.getAction())){shutdown("BLE 연결 해제됨");stopSelf();return START_NOT_STICKY;}
        if(!CONNECT.equals(intent.getAction()))return START_NOT_STICKY;
        try{
            BoardTarget next=new BoardTarget(intent.getStringExtra("address"),intent.getStringExtra("service"),intent.getStringExtra("characteristic"));
            if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new IOException("주변 기기 권한을 허용해주세요.");
            if(closed){closed=false;target=null;initializeTransport();}
            if(!next.equals(target)){main.removeCallbacks(retry);updating=true;if(target!=null)link.close();target=next;updating=false;}
            running=true;
            ServiceCompat.startForeground(this,NOTICE,notification(),Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE:0);foreground=true;
            connectNow();publish();
        }catch(Exception error){shutdown("BLE 연결 시작 실패 · "+(error.getMessage()==null?"권한·기기 선택을 확인해주세요.":error.getMessage()));stopSelf();}
        return START_NOT_STICKY;
    }
    private void connectNow(){
        if(!running||closed||target==null||link==null||link.isReady()||link.isConnecting())return;
        main.removeCallbacks(retry);
        try{
            if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException();
            link.connect(target);
        }catch(SecurityException error){status="BLE 주변 기기 권한을 확인해주세요.";publish();main.postDelayed(retry,10000);}
        catch(RuntimeException error){status="BLE 재연결 대기 · 모듈 전원과 휴대폰 블루투스를 확인해주세요.";publish();main.postDelayed(retry,10000);}
    }
    private void changed(){
        if(updating)return;main.removeCallbacks(retry);publish();
        if(running&&!closed&&link!=null&&!link.isReady()&&!link.isConnecting())main.postDelayed(retry,10000);
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,620,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,621,new Intent(this,BoardService.class).setAction(DISCONNECT),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("길잡이 보드 연결").setContentText(link!=null&&link.isReady()?"연결 유지 중 · AI를 중지해도 연결합니다.":"BLE 연결 확인 중").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(android.R.drawable.ic_menu_close_clear_cancel,"BLE 연결 해제",stop).build();
    }
    private void publish(){
        if(instance!=this)return;last=new Snapshot(running&&!closed,link!=null&&link.isReady(),link!=null&&link.isConnecting(),link!=null&&link.buttonsReady(),target,status);
        for(Observer observer:OBSERVERS)observer.state(last);
        if(foreground&&SystemClock.elapsedRealtime()-lastNotice>=1000&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)){lastNotice=SystemClock.elapsedRealtime();NotificationManager n=getSystemService(NotificationManager.class);if(n!=null)n.notify(NOTICE,notification());}
    }
    private void shutdown(String message){
        if(closed)return;running=false;foreground=false;closed=true;main.removeCallbacksAndMessages(null);if(link!=null)link.close();status=message;ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);publish();
    }
    @Override public void onDestroy(){shutdown("BLE 연결 서비스 종료됨");if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
