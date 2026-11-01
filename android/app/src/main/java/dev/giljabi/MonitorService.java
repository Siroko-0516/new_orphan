package dev.giljabi;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.net.wifi.WifiManager;
import android.os.*;
import android.speech.tts.*;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** User-started foreground owner for ESP video, latest-frame AI, speech and optional BLE. */
public final class MonitorService extends Service {
    private static final String CHANNEL="giljabi-monitor",START="dev.giljabi.MONITOR_START",UPDATE="dev.giljabi.MONITOR_UPDATE",STOP="dev.giljabi.MONITOR_STOP",RETRY="dev.giljabi.MONITOR_RETRY",TEST="dev.giljabi.MONITOR_TEST";
    private static final int NOTIFICATION=50;
    private static final class ConfigurationException extends IOException {ConfigurationException(IOException cause){super(cause.getMessage(),cause);}}
    interface Observer {void state(Snapshot snapshot);void frame(ViewFrame frame);}
    static final class Snapshot {
        final boolean active,stopping,paused,bleReady;final MonitorConfig config;final String status,result,bleStatus;
        Snapshot(boolean active,boolean stopping,boolean paused,boolean bleReady,MonitorConfig config,String status,String result,String bleStatus){this.active=active;this.stopping=stopping;this.paused=paused;this.bleReady=bleReady;this.config=config;this.status=status;this.result=result;this.bleStatus=bleStatus;}
    }
    static final class ViewFrame {
        final Bitmap bitmap;final byte[] jpeg;final List<YoloDetector.Box> boxes;final double fps;final long detectionMs;
        ViewFrame(Bitmap bitmap,FramePacket packet){this.bitmap=bitmap;jpeg=packet.jpeg;boxes=packet.boxes;fps=packet.fps;detectionMs=packet.detectionMs;}
    }
    private static final CopyOnWriteArrayList<Observer> OBSERVERS=new CopyOnWriteArrayList<>();
    private static volatile Snapshot last=new Snapshot(false,false,false,false,null,"실시간 AI 중지됨","","BLE 미연결");
    private static volatile MonitorService instance;
    static Snapshot snapshot(){return last;}
    static boolean active(){return last.active;}
    static void observe(Observer observer){OBSERVERS.addIfAbsent(observer);observer.state(last);MonitorService current=instance;if(current!=null)current.queueFrame();}
    static void forget(Observer observer){OBSERVERS.remove(observer);}
    static void start(Context context,MonitorConfig config)throws IOException {
        config.validate();synchronized(MonitorService.class){if(last.active)throw new IOException("이미 실시간 실행 중입니다.");last=new Snapshot(true,false,false,false,config,"실시간 AI 시작 중...","","BLE 준비 중");}
        try{ContextCompat.startForegroundService(context,config.write(new Intent(context,MonitorService.class).setAction(START)));}
        catch(RuntimeException failure){last=new Snapshot(false,false,false,false,config,"백그라운드 실행을 시작하지 못했습니다. 앱을 화면에 연 상태에서 시작하세요.","","BLE 미연결");throw new IOException(last.status,failure);}
    }
    static void update(Context context,MonitorConfig config)throws IOException {config.validate();if(active()&&!last.stopping)context.startService(config.write(new Intent(context,MonitorService.class).setAction(UPDATE)));}
    static void stop(Context context){if(active())context.startService(new Intent(context,MonitorService.class).setAction(STOP));}
    static void retry(Context context){if(active()&&!last.stopping)context.startService(new Intent(context,MonitorService.class).setAction(RETRY));}
    static void test(Context context){if(active()&&!last.stopping)context.startService(new Intent(context,MonitorService.class).setAction(TEST));}

    private static final class FramePacket {
        final Bitmap bitmap;final byte[] jpeg;final List<YoloDetector.Box> boxes;final long sequence,receivedAt,detectionMs;final double fps;
        FramePacket(LiveCamera.Frame frame,long sequence,long receivedAt){bitmap=frame.bitmap;jpeg=frame.jpeg;boxes=frame.boxes;this.sequence=sequence;this.receivedAt=receivedAt;detectionMs=frame.detectionMs;fps=frame.receiveFps;}
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final ContinuousGate gate=new ContinuousGate();
    private final Object modeLock=new Object(),frameLock=new Object();
    private final AtomicLong sequence=new AtomicLong();
    private final AtomicBoolean framePosted=new AtomicBoolean();
    private volatile MonitorConfig config;
    private volatile FramePacket latest;
    private volatile GeminiClient.Request request;
    private volatile boolean monitoring,stopping;
    private volatile int cameraGeneration;
    private ScheduledFuture<?> repeat;
    private VideoSource camera;
    private BleLink ble;
    private final ObstacleTracker tracker=new ObstacleTracker();
    private TextToSpeech tts;private boolean ttsReady,cleanupQueued,cleanupComplete,destroyed,foreground;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifiLock;
    private String status="실시간 AI 시작 중...",result="",bleStatus="BLE 미연결",lastSpoken="",terminalError="";
    private long lastNotice,lastTrackedSequence=-1;
    private final Runnable reconnect=()->openCamera();
    private final Runnable renew=new Runnable(){@Override public void run(){if(monitoring&&!stopping){if(wake!=null)wake.acquire(TimeUnit.MINUTES.toMillis(30));main.postDelayed(this,TimeUnit.MINUTES.toMillis(10));}}};
    private final Runnable reconnectBle=new Runnable(){@Override public void run(){if(monitoring&&!stopping){connectBle();main.postDelayed(this,10000);}}};

    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationManager notifications=getSystemService(NotificationManager.class);if(notifications!=null)notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"길잡이 실시간 분석",NotificationManager.IMPORTANCE_LOW));
        ble=new BleLink(this,message->{bleStatus=message;publish();},devices->{},()->{tracker.reset();publish();});
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?STOP:intent.getAction();
        if(STOP.equals(action)){if(!monitoring&&!stopping&&config==null){cleanupQueued=true;cleanupComplete=true;worker.shutdown();stopSelf();}else stopMonitoring();return START_NOT_STICKY;}
        if(TEST.equals(action)){if(monitoring&&!stopping)ble.send("TEST\n");return START_NOT_STICKY;}
        if(RETRY.equals(action)){if(monitoring&&!stopping){synchronized(modeLock){gate.reset();}closeRequest();status="장면 설명 재시작 대기 중...";publish();}return START_NOT_STICKY;}
        try{
            MonitorConfig next=MonitorConfig.read(intent);next.validate();
            if(UPDATE.equals(action)&&monitoring&&!stopping){
                if(next.source!=config.source||!next.address.equals(config.address))throw new IOException("영상 입력을 바꾸려면 먼저 실시간 실행을 중지하세요.");
                synchronized(modeLock){config=next;gate.reset();}closeRequest();if(tts!=null)tts.stop();lastSpoken="";result="";status=(next.cloud?"Gemini":"로컬 AI")+"로 전환 중 · 이전 분석 처리 후 최신 프레임을 사용합니다.";publish();return START_NOT_STICKY;
            }
            if(!START.equals(action)||monitoring||stopping||cleanupQueued)return START_NOT_STICKY;
            config=next;
            int types=Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0;
            if(Build.VERSION.SDK_INT>=29&&(next.source==CameraSource.ESP||!next.bleAddress.isEmpty()))types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if(Build.VERSION.SDK_INT>=30&&CameraSource.phone(next.source))types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            ServiceCompat.startForeground(this,NOTIFICATION,notification(),types);foreground=true;monitoring=true;gate.start();acquireLocks();
            tts=new TextToSpeech(getApplicationContext(),code->{ttsReady=code==TextToSpeech.SUCCESS;if(!ttsReady){status="음성 초기화 실패 · 화면 결과로 확인하세요.";publish();}});
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            repeat=worker.scheduleWithFixedDelay(this::analyzeLatest,0,250,TimeUnit.MILLISECONDS);openCamera();main.post(reconnectBle);publish();
        }catch(Exception failure){terminalError="실시간 실행 실패: "+message(failure);stopMonitoring();}
        return START_NOT_STICKY;
    }
    private void acquireLocks(){
        PowerManager power=getSystemService(PowerManager.class);if(power!=null){wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"giljabi:monitor");wake.setReferenceCounted(false);wake.acquire(TimeUnit.MINUTES.toMillis(30));main.postDelayed(renew,TimeUnit.MINUTES.toMillis(10));}
        WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);if(wifi!=null&&config.source==CameraSource.ESP){wifiLock=wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"giljabi:esp-camera");wifiLock.setReferenceCounted(false);wifiLock.acquire();}
    }
    private void openCamera(){
        if(!monitoring||stopping)return;main.removeCallbacks(reconnect);int generation=++cameraGeneration;
        try{
            if(CameraSource.phone(config.source)){
                status=CameraSource.label(config.source)+" 시작 중";publish();
                camera=new PhoneCamera(this,config.source,frame->acceptFrame(generation,frame),progress->main.post(()->{if(monitoring&&!stopping&&generation==cameraGeneration){status=progress;publish();}}),error->main.post(()->cameraError(generation,error)));
                return;
            }
            WifiCamera.Choice choice=WifiCamera.choose(this,config.address);status="ESP 연결 중 · "+choice.description;publish();
            camera=new LiveCamera(this,config.address,choice.connector,jpeg->PhotoCodec.decode(new ByteArrayInputStream(jpeg),1),frame->acceptFrame(generation,frame),
                progress->main.post(()->{if(monitoring&&!stopping&&generation==cameraGeneration){status=progress;publish();}}),
                error->main.post(()->cameraError(generation,error)));
        }catch(Exception failure){cameraError(generation,CameraFailure.from(failure));}
    }
    private void cameraError(int generation,CameraFailure error){
        if(!monitoring||stopping||generation!=cameraGeneration)return;
        if(!error.retryable){terminalError=error.message;stopMonitoring();return;}cameraGeneration++;VideoSource old=camera;camera=null;if(old!=null)old.close();clearFrame();
        synchronized(modeLock){gate.invalidateFrames();}closeRequest();status=CameraSource.label(config.source)+" 연결 끊김 · 5초 후 재연결: "+error.message;publish();main.removeCallbacks(reconnect);main.postDelayed(reconnect,5000);
    }
    private void acceptFrame(int generation,LiveCamera.Frame frame){
        synchronized(frameLock){
            if(!monitoring||stopping||generation!=cameraGeneration){frame.bitmap.recycle();return;}
            FramePacket old=latest;latest=new FramePacket(frame,sequence.incrementAndGet(),frame.receivedAt);if(old!=null)old.bitmap.recycle();
        }queueFrame();
    }
    private void queueFrame(){
        if(!framePosted.compareAndSet(false,true))return;
        main.post(()->{
            framePosted.set(false);if(!monitoring||stopping)return;FramePacket packet=latest;if(packet==null)return;
            if(packet.detectionMs>=0&&packet.sequence>lastTrackedSequence){lastTrackedSequence=packet.sequence;YoloDetector.Box event=tracker.update(packet.boxes,SystemClock.elapsedRealtime());if(event!=null&&config.notify&&ble.isReady())ble.send("OBSTACLE\n");}
            for(Observer observer:OBSERVERS){
                ViewFrame view=null;
                synchronized(frameLock){FramePacket current=latest;if(current!=null)try{Bitmap copy=current.bitmap.copy(Bitmap.Config.ARGB_8888,false);if(copy!=null)view=new ViewFrame(copy,current);}catch(OutOfMemoryError ignored){}}
                if(view!=null)try{observer.frame(view);}catch(RuntimeException failure){CrashDiagnostics.record("실시간 화면 갱신",failure);}
            }
        });
    }
    private void analyzeLatest(){
        if(!monitoring||stopping)return;FramePacket frame=latest;if(frame==null||frame.detectionMs<0)return;
        MonitorConfig chosen;ContinuousGate.Ticket ticket;
        synchronized(modeLock){chosen=config;ticket=gate.begin(frame.sequence,frame.receivedAt,SystemClock.elapsedRealtime(),chosen.intervalMs);}
        if(ticket==null)return;long started=SystemClock.elapsedRealtime();
        try{
            String question=chosen.question+(chosen.useYolo?"\nYOLO auxiliary observations (may be wrong, normalized boxes): "+YoloDetector.describe(frame.boxes):"");
            String answer;
            if(chosen.cloud){
                LocalVlm.get(this).release();check(ticket);String key;try{key=new ApiKeyStore(this).read();}catch(IOException error){throw new ConfigurationException(error);}
                try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!gate.current(ticket))){
                    check(ticket);progress(ticket,"Gemini 자동 분석 중 · "+internet.description);
                    try(GeminiClient.Request pending=new GeminiClient.Request(chosen.model,key,frame.jpeg,question,internet.connector,()->!gate.current(ticket))){request=pending;check(ticket);answer=pending.execute();}finally{request=null;}
                }
            }else answer=LocalVlm.get(this).describe(frame.jpeg,question,()->!gate.current(ticket),text->progress(ticket,text),partial->{});
            check(ticket);long elapsed=SystemClock.elapsedRealtime()-started,age=SystemClock.elapsedRealtime()-frame.receivedAt;
            main.post(()->{
                if(!monitoring||stopping||!gate.current(ticket))return;result=answer;status=String.format(Locale.ROOT,"%s 자동 분석 · %.1f초 · 사용 프레임 수신 %.1f초 전",chosen.cloud?"Gemini":"로컬 AI",elapsed/1000.0,age/1000.0);if(chosen.speak)speak(answer);publish();
            });
        }catch(CancellationException ignored){}
        catch(Throwable failure){
            if(gate.current(ticket)){
                boolean pause=!chosen.cloud||failure instanceof ConfigurationException||failure instanceof OutOfMemoryError||(failure instanceof GeminiClient.ApiException&&((GeminiClient.ApiException)failure).status<500);
                if(pause)gate.pause(ticket);else gate.defer(ticket,SystemClock.elapsedRealtime()+10000);
                if(!chosen.cloud)LocalVlm.get(this).release();
                String error=(pause?"장면 설명 일시 중지 · 설정 수정 또는 재시도: ":"장면 설명 연결 오류 · 10초 후 최신 프레임으로 재시도: ")+message(failure);
                main.post(()->{if(gate.current(ticket)){status=error;publish();}});
            }
        }finally{gate.finish(ticket);}
    }
    private void progress(ContinuousGate.Ticket ticket,String text){main.post(()->{if(monitoring&&!stopping&&gate.current(ticket)){status=text;publish();}});}
    private void check(ContinuousGate.Ticket ticket){if(!gate.current(ticket))throw new CancellationException();}
    private void closeRequest(){GeminiClient.Request current=request;if(current!=null)new Thread(current::close,"monitor-gemini-close").start();}
    private void connectBle(){
        if(config==null||config.bleAddress.isEmpty()||ble.isReady()||ble.isConnecting())return;
        if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){bleStatus="아두이노 연결에 주변 기기 권한이 필요합니다.";publish();return;}
        try{BluetoothAdapter adapter=ble.adapter();if(adapter==null||!adapter.isEnabled()){bleStatus="아두이노 연결을 위해 블루투스를 켜세요.";publish();return;}
            ble.connect(new BleLink.Device(adapter.getRemoteDevice(config.bleAddress),"HM-10"),config.serviceUuid,config.characteristicUuid);
        }catch(RuntimeException error){bleStatus="BLE 연결 실패: "+message(error);publish();}
    }
    private void speak(String text){
        if(tts==null||!ttsReady)return;String normalized=text.replaceAll("\\s+"," ").trim();if(normalized.equals(lastSpoken))return;
        Locale locale=text.matches("(?s).*[가-힣].*")?Locale.KOREAN:Locale.ENGLISH;Set<Voice> voices=tts.getVoices();
        Voice voice=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(locale.getLanguage())).max(Comparator.comparingInt(Voice::getQuality)).orElse(null);
        if(voice==null){status+=" · 오프라인 음성이 없습니다.";return;}tts.setVoice(voice);tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"monitor-scene");lastSpoken=normalized;
    }
    private Notification notification(){
        String title="길잡이 · "+(config!=null&&config.cloud?"Gemini":"로컬 AI")+(stopping?" 중지 처리 중":" 실시간 실행 중");
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MonitorService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle(title).setContentText(status).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(android.R.drawable.ic_media_pause,"중지",stop).build();
    }
    private void publish(){
        if(instance!=this)return;last=new Snapshot(monitoring||stopping,stopping,gate.isPaused(),ble!=null&&ble.isReady(),config,status,result,bleStatus);
        for(Observer observer:OBSERVERS)observer.state(last);
        if(foreground&&SystemClock.elapsedRealtime()-lastNotice>=1000){lastNotice=SystemClock.elapsedRealtime();NotificationManager manager=getSystemService(NotificationManager.class);if(manager!=null&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED))manager.notify(NOTIFICATION,notification());}
    }
    private void clearFrame(){synchronized(frameLock){FramePacket old=latest;latest=null;if(old!=null)old.bitmap.recycle();}}
    private void stopMonitoring(){
        if(cleanupQueued)return;cleanupQueued=true;stopping=true;monitoring=false;gate.stop();cameraGeneration++;main.removeCallbacks(reconnect);main.removeCallbacks(reconnectBle);main.removeCallbacks(renew);
        if(repeat!=null)repeat.cancel(false);closeRequest();if(camera!=null){camera.close();camera=null;}if(ble!=null)ble.close();if(tts!=null)tts.stop();status=terminalError.isEmpty()?"실시간 중지 처리 중 · 현재 로컬 연산이 끝나면 종료합니다.":terminalError;publish();
        worker.execute(()->{try{LocalVlm.get(this).release();}finally{main.post(this::finishStop);}});
    }
    private void finishStop(){
        clearFrame();if(tts!=null){tts.shutdown();tts=null;}if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();if(wake!=null&&wake.isHeld())wake.release();
        foreground=false;cleanupComplete=true;ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);worker.shutdown();if(destroyed)markStopped();else stopSelf();
    }
    private void markStopped(){stopping=false;status=terminalError.isEmpty()?"실시간 AI 중지됨":terminalError;publish();if(instance==this)instance=null;}
    @Override public void onDestroy(){destroyed=true;stopMonitoring();if(cleanupComplete)markStopped();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static String message(Throwable error){String text=error.getMessage();return text==null?error.getClass().getSimpleName():text;}
}
