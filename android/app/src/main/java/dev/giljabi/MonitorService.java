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
        final boolean active,stopping,paused,bleReady;final MonitorConfig config;final String status,result,bleStatus,speechStatus;
        Snapshot(boolean active,boolean stopping,boolean paused,boolean bleReady,MonitorConfig config,String status,String result,String bleStatus){this(active,stopping,paused,bleReady,config,status,result,bleStatus,"");}
        Snapshot(boolean active,boolean stopping,boolean paused,boolean bleReady,MonitorConfig config,String status,String result,String bleStatus,String speechStatus){this.speechStatus=speechStatus;this.active=active;this.stopping=stopping;this.paused=paused;this.bleReady=bleReady;this.config=config;this.status=status;this.result=result;this.bleStatus=bleStatus;}
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
        final Bitmap bitmap;final byte[] jpeg;final List<YoloDetector.Box> boxes;final long sequence,receivedAt,detectionMs;final double fps;final SceneSignature signature;
        FramePacket(LiveCamera.Frame frame,long sequence,long receivedAt){signature=frame.detectionMs>=0?ImageEvidence.sample(frame.bitmap):null;bitmap=frame.bitmap;jpeg=frame.jpeg;boxes=frame.boxes;this.sequence=sequence;this.receivedAt=receivedAt;detectionMs=frame.detectionMs;fps=frame.receiveFps;}
    }
    private static final class AnalysisFrame {
        final byte[] jpeg;final long sequence,receivedAt;final WalkingSceneGate.Decision decision;
        AnalysisFrame(FramePacket frame,WalkingSceneGate.Decision decision){jpeg=frame.jpeg;sequence=frame.sequence;receivedAt=frame.receivedAt;this.decision=decision;}
    }
    private final MotionFrameWindow<AnalysisFrame> visionFrames=new MotionFrameWindow<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(task->new Thread(task,"scene-ai"));
    private final ContinuousGate gate=new ContinuousGate();
    private final WalkingSceneGate sceneChanges=new WalkingSceneGate();
    private final NarrationMemory narration=new NarrationMemory();
    private final NarrationMemory motorMemory=new NarrationMemory();
    private static final class GuidePhoto {final byte[] jpeg;final long at;GuidePhoto(byte[] jpeg,long at){this.jpeg=jpeg;this.at=at;}}
    private final ArrayDeque<GuidePhoto> recentPhotos=new ArrayDeque<>();
    private GuidePhoto previousReview;
    private String pendingOffline;private java.util.function.BooleanSupplier pendingOfflineValid,activeSpeechValid=()->false;
    private static final class Announcement {
        final WalkingSceneGate.Decision decision;final String message,original;final ContinuousGate.Ticket ticket;final boolean urgent;volatile boolean started;String nativeId;
        java.util.function.BooleanSupplier valid;boolean cloudVoice;long capturedAt;
        Announcement(WalkingSceneGate.Decision decision,String message,ContinuousGate.Ticket ticket,boolean urgent,String original){this.original=original;this.decision=decision;this.message=message;this.ticket=ticket;this.urgent=urgent;}
    }
    private final GuidanceSpeechQueue<Announcement> speechQueue=new GuidanceSpeechQueue<>();
    private Announcement announcement;private long speechSequence;private boolean offlineSpeaking;
    private final Runnable nativeSpeechCheck=new Runnable(){public void run(){Announcement current=announcement;if(current==null)return;if(!activeSpeechValid.getAsBoolean()){if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();completeSpeech(current);return;}main.postDelayed(this,100);}};
    private final Object modeLock=new Object(),frameLock=new Object();
    private final AtomicLong sequence=new AtomicLong();
    private final AtomicBoolean framePosted=new AtomicBoolean();
    private volatile MonitorConfig config;
    private volatile FramePacket latest;
    private volatile CloudRequest request;
    private volatile boolean monitoring,stopping;
    private volatile int cameraGeneration;
    private ScheduledFuture<?> repeat;
    private VideoSource camera;
    private boolean boardWasReady;
    private final BoardService.Observer boardObserver=s->{if(s.ready&&!boardWasReady)motorMemory.reset();boardWasReady=s.ready;bleStatus=s.status;publish();};
    private final ObstacleTracker tracker=new ObstacleTracker();
    private ElevenSpeech eleven;
    private TextToSpeech tts;private boolean ttsReady,cleanupQueued,cleanupComplete,destroyed,foreground;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifiLock;
    private String status="실시간 AI 시작 중...",result="",bleStatus="BLE 미연결",lastSpoken="",terminalError="";
    private String speechStatus="";
    private long lastNotice,lastTrackedSequence=-1;
    private final Runnable reconnect=()->openCamera();
    private final Runnable renew=new Runnable(){@Override public void run(){if(monitoring&&!stopping){if(wake!=null)wake.acquire(TimeUnit.MINUTES.toMillis(30));main.postDelayed(this,TimeUnit.MINUTES.toMillis(10));}}};

    @Override public void onCreate(){
        super.onCreate();instance=this;eleven=new ElevenSpeech(this,message->{if(monitoring&&!stopping){speechStatus=message;publish();}},text->{if(monitoring&&!stopping)speakOffline(text,activeSpeechValid);},this::speechStarted,null,false,WalkingSpeechText.RATE);
        NotificationManager notifications=getSystemService(NotificationManager.class);if(notifications!=null)notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"길잡이 실시간 분석",NotificationManager.IMPORTANCE_LOW));
        BoardService.observe(boardObserver);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?STOP:intent.getAction();
        if(STOP.equals(action)){if(!monitoring&&!stopping&&config==null){cleanupQueued=true;cleanupComplete=true;worker.shutdown();stopSelf();}else stopMonitoring();return START_NOT_STICKY;}
        if(TEST.equals(action)){if(monitoring&&!stopping)sendBoard("TEST\n");return START_NOT_STICKY;}
        if(RETRY.equals(action)){if(monitoring&&!stopping){synchronized(modeLock){gate.reset();sceneChanges.reset();narration.reset();motorMemory.reset();synchronized(frameLock){visionFrames.clear();}}closeRequest();stopGuidance();status="장면 설명 재시작 대기 중...";publish();}return START_NOT_STICKY;}
        try{
            MonitorConfig next=MonitorConfig.read(intent);next.validate();
            if(UPDATE.equals(action)&&monitoring&&!stopping){
                if(next.source!=config.source||!next.address.equals(config.address))throw new IOException("영상 입력을 바꾸려면 먼저 실시간 실행을 중지하세요.");
                synchronized(modeLock){config=next;gate.reset();sceneChanges.reset();narration.reset();motorMemory.reset();synchronized(frameLock){visionFrames.clear();}}closeRequest();stopGuidance();lastSpoken="";result="";status=AiProvider.name(next.provider)+"로 전환 중 · 이전 분석 처리 후 최신 프레임을 사용합니다.";publish();return START_NOT_STICKY;
            }
            if(!START.equals(action)||monitoring||stopping||cleanupQueued)return START_NOT_STICKY;
            config=next;
            int types=Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0;
            if(Build.VERSION.SDK_INT>=29&&next.source==CameraSource.ESP)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if(Build.VERSION.SDK_INT>=30&&CameraSource.phone(next.source))types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            ServiceCompat.startForeground(this,NOTIFICATION,notification(),types);foreground=true;monitoring=true;gate.start();sceneChanges.reset();narration.reset();motorMemory.reset();acquireLocks();
            tts=new TextToSpeech(getApplicationContext(),code->main.post(()->{ttsReady=code==TextToSpeech.SUCCESS;if(!ttsReady){speechStatus="오프라인 음성 초기화 실패 · 화면 결과로 확인하세요.";publish();}else if(pendingOffline!=null){String text=pendingOffline;java.util.function.BooleanSupplier valid=pendingOfflineValid;pendingOffline=null;pendingOfflineValid=null;speakOffline(text,valid);}}));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){main.post(()->{if(announcement==null||!id.equals(announcement.nativeId))return;if(!activeSpeechValid.getAsBoolean()){Announcement expired=announcement;if(tts!=null)tts.stop();completeSpeech(expired);return;}speechStarted(announcement.message);});}
                @Override public void onDone(String id){main.post(()->finishNative(id,false));}
                @Override public void onError(String id){main.post(()->finishNative(id,true));}
                @Override public void onStop(String id,boolean interrupted){main.post(()->finishNative(id,false));}
            });
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            repeat=worker.scheduleWithFixedDelay(this::analyzeLatest,0,250,TimeUnit.MILLISECONDS);openCamera();publish();
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
            camera=new LiveCamera(this,config.address,choice.connector,jpeg->PhotoCodec.decodeEsp(new ByteArrayInputStream(jpeg)),frame->acceptFrame(generation,frame),
                progress->main.post(()->{if(monitoring&&!stopping&&generation==cameraGeneration){status=progress;publish();}}),
                error->main.post(()->cameraError(generation,error)));
        }catch(Exception failure){cameraError(generation,CameraFailure.from(failure));}
    }
    private void cameraError(int generation,CameraFailure error){
        if(!monitoring||stopping||generation!=cameraGeneration)return;
        if(!error.retryable){terminalError=error.message;stopMonitoring();return;}cameraGeneration++;VideoSource old=camera;camera=null;if(old!=null)old.close();clearFrame();
        synchronized(modeLock){gate.invalidateFrames();sceneChanges.reconnect();}closeRequest();status=CameraSource.label(config.source)+" 연결 끊김 · 5초 후 재연결: "+error.message;publish();main.removeCallbacks(reconnect);main.postDelayed(reconnect,5000);
    }
    private void acceptFrame(int generation,LiveCamera.Frame frame){
        synchronized(frameLock){
            if(!monitoring||stopping||generation!=cameraGeneration){frame.bitmap.recycle();return;}
            FramePacket old=latest;latest=new FramePacket(frame,sequence.incrementAndGet(),frame.receivedAt);
            if(frame.detectionMs>=0){
                sceneChanges.observe(latest.sequence,latest.signature,frame.boxes,frame.receivedAt);
                AnalysisFrame analysis=new AnalysisFrame(latest,sceneChanges.review(latest.sequence));
                visionFrames.offer(analysis,analysis.receivedAt,latest.signature.unclearMessage()==null);
                if(recentPhotos.isEmpty()||frame.receivedAt-recentPhotos.peekLast().at>=400){recentPhotos.addLast(new GuidePhoto(frame.jpeg,frame.receivedAt));while(recentPhotos.size()>3)recentPhotos.removeFirst();}
            }
            if(old!=null)old.bitmap.recycle();
        }queueFrame();
    }
    private void queueFrame(){
        if(!framePosted.compareAndSet(false,true))return;
        main.post(()->{
            framePosted.set(false);if(!monitoring||stopping)return;FramePacket packet=latest;if(packet==null)return;
            if(packet.detectionMs>=0&&packet.sequence>lastTrackedSequence)lastTrackedSequence=packet.sequence;

            for(Observer observer:OBSERVERS){
                ViewFrame view=null;
                synchronized(frameLock){FramePacket current=latest;if(current!=null)try{Bitmap copy=current.bitmap.copy(Bitmap.Config.ARGB_8888,false);if(copy!=null)view=new ViewFrame(copy,current);}catch(OutOfMemoryError ignored){}}
                if(view!=null)try{observer.frame(view);}catch(RuntimeException failure){CrashDiagnostics.record("실시간 화면 갱신",failure);}
            }
        });
    }
    private byte[][] reviewPhotos(AnalysisFrame frame){
        ArrayList<GuidePhoto> photos=new ArrayList<>();
        if(previousReview!=null&&frame.receivedAt-previousReview.at<=4000&&previousReview.at<frame.receivedAt)photos.add(previousReview);
        if(photos.isEmpty())for(GuidePhoto recent:recentPhotos)if(frame.receivedAt-recent.at>=350&&frame.receivedAt-recent.at<=1500){photos.add(recent);break;}
        photos.add(new GuidePhoto(frame.jpeg,frame.receivedAt));
        long bytes=0;for(GuidePhoto photo:photos)bytes+=photo.jpeg.length;
        while(bytes>GeminiClient.MAX_IMAGE&&photos.size()>1){bytes-=photos.remove(0).jpeg.length;}
        byte[][] images=new byte[photos.size()][];for(int i=0;i<images.length;i++)images[i]=photos.get(i).jpeg;return images;
    }
    private void analyzeLatest(){
        if(!monitoring||stopping)return;
        AnalysisFrame frame;MonitorConfig chosen;ContinuousGate.Ticket ticket;WalkingSceneGate.Decision decision;byte[][] images;
        synchronized(modeLock){
            chosen=config;
            synchronized(frameLock){
                frame=visionFrames.latest(SystemClock.elapsedRealtime());if(frame==null)return;
                WalkingSceneGate.Decision event=sceneChanges.decision(frame.sequence);decision=event==null?frame.decision:event;
                images=reviewPhotos(frame);
            }
            if(decision==null)return;
            ticket=gate.begin(frame.sequence,frame.receivedAt,SystemClock.elapsedRealtime(),chosen.intervalMs);
        }
        if(ticket==null)return;long started=SystemClock.elapsedRealtime();
        try{
            boolean sameReview=narration.sameReviewScope(decision.episode,decision.memoryKey);
            String question=chosen.cloud?WalkingGuidance.prompt(chosen.question,"review_reason="+decision.reason+"; last_spoken_urgent="+(sameReview&&narration.urgent(decision.episode))+"\n"+decision.context,narration.previous(decision.episode),Math.max(0,started-frame.receivedAt),sameReview):WalkingGuidance.localPrompt(sameReview?narration.source(decision.episode):"");
            String unclear=decision.signature.unclearMessage();
            if(unclear!=null){
                synchronized(modeLock){if(gate.current(ticket))sceneChanges.accepted(decision);}
                main.post(()->{if(gate.current(ticket)){status="영상이 불분명해 보행 안내를 보류합니다.";result=unclear;publish();}});return;
            }
            String answer;
            if(chosen.cloud){
                LocalVlm.get(this).release();check(ticket);String key;try{key=AiProvider.keys(this,chosen.provider).read();}catch(IOException error){throw new ConfigurationException(error);}
                try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!gate.current(ticket))){
                    check(ticket);progress(ticket,AiProvider.name(chosen.provider)+"가 이전·최신 사진의 보행 상황을 확인 중 · "+internet.description);
                    try(CloudRequest pending=CloudAi.vision(chosen.provider,chosen.model,key,images,question,true,internet.connector,()->!gate.current(ticket))){request=pending;check(ticket);answer=pending.execute();}finally{request=null;}
                }
            }else {KoreanTranslator.get(this).requireReady(()->!gate.current(ticket));answer=LocalVlm.get(this).describeGuidance(frame.jpeg,question,()->!gate.current(ticket),text->progress(ticket,text));}
            check(ticket);WalkingGuidance.Reply parsed=WalkingGuidance.parse(answer);
            synchronized(modeLock){if(gate.current(ticket))sceneChanges.accepted(decision);}
            synchronized(frameLock){previousReview=new GuidePhoto(frame.jpeg,frame.receivedAt);}
            if(!parsed.valid){progress(ticket,"AI 답변 형식을 확인하지 못해 음성 안내를 보류했습니다.");return;}
            if(!chosen.cloud&&parsed.speak&&!WalkingGuidance.localUseful(parsed.message,decision.stateKey)){progress(ticket,"현재 로컬 설명이 보행 상황에 필요한 내용인지 확인되지 않아 음성을 보류했습니다.");return;}
            if(!parsed.speak){progress(ticket,"보행 상황 확인 완료 · 새로 안내할 내용이 없어 조용히 확인 중입니다.");return;}
            if(!WalkingGuidance.fresh(frame.receivedAt,SystemClock.elapsedRealtime())){progress(ticket,"분석이 늦어 이전 장면의 음성 안내를 생략했습니다."+(!chosen.cloud?" · "+LocalVlm.get(this).timings():""));return;}
            String original=WalkingSpeechText.brief(parsed.message);
            String message=WalkingSpeechText.brief(KoreanTranslator.get(this).translateIfNeeded(original,()->!gate.current(ticket),text->progress(ticket,text)));
            WalkingGuidance.Reply translated=WalkingGuidance.parse(new org.json.JSONObject().put("speak",true).put("message",message).put("urgency",parsed.urgent?"urgent":"notice").toString());
            if(!translated.valid||!translated.message.matches("(?s).*[가-힣].*")){progress(ticket,"한국어 안내 문장을 준비하지 못해 음성을 보류했습니다.");return;}
            long elapsed=SystemClock.elapsedRealtime()-started;
            main.post(()->{
                if(!monitoring||stopping||!gate.current(ticket))return;
                Announcement next=new Announcement(decision,translated.message,ticket,translated.urgent,original);
                java.util.function.BooleanSupplier valid=()->monitoring&&!stopping&&gate.current(ticket)&&sceneChanges.relevant(decision)&&
                    WalkingGuidance.speechAge(frame.receivedAt,SystemClock.elapsedRealtime(),next.started);
                if(!valid.getAsBoolean()){status="상황이 달라졌거나 오래된 설명이라 음성 안내를 생략했습니다.";publish();return;}
                if(announcement!=null&&!announcement.started&&(!translated.urgent||announcement.urgent)&&activeSpeechValid.getAsBoolean()&&NarrationMemory.equivalent(announcement.message,translated.message,announcement.decision.memoryKey,decision.memoryKey)){status="같은 상황의 음성 안내를 준비하거나 읽고 있습니다.";publish();return;}
                if(chosen.notify&&motorMemory.eligible(decision.episode,decision.memoryKey,translated.message,translated.urgent)&&sendBoard("OBSTACLE\n"))motorMemory.offer(decision.episode,decision.memoryKey,translated.message,translated.urgent);
                if(!narration.eligible(decision.episode,decision.memoryKey,translated.message,translated.urgent)){status="같은 보행 상황은 반복해서 안내하지 않습니다.";publish();return;}
                result=translated.message;status=String.format(Locale.ROOT,"%s 보행 상황 확인 · %.1f초",AiProvider.name(chosen.provider),elapsed/1000.0)+(!chosen.cloud?" · "+LocalVlm.get(this).timings():"");
                if(chosen.speak&&!AssistantService.suppressGuidance(this)){
                    next.valid=valid;next.cloudVoice=chosen.cloud;next.capturedAt=frame.receivedAt;
                    Announcement ready=speechQueue.offer(next,next.urgent,valid);
                    if(ready!=null)beginSpeech(ready);else speechStatus="현재 짧은 안내를 마친 뒤 최신 안내를 확인합니다.";
                }publish();
            });
        }catch(CancellationException ignored){}
        catch(Throwable failure){
            if(gate.current(ticket)){
                CrashDiagnostics.record(AiProvider.name(chosen.provider)+" 보행 분석",failure);
                int http=CloudAi.status(failure);boolean pause=!chosen.cloud||failure instanceof ConfigurationException||failure instanceof OutOfMemoryError||(http>0&&http<500);
                if(pause)gate.pause(ticket);else gate.defer(ticket,SystemClock.elapsedRealtime()+10000);
                if(!chosen.cloud)LocalVlm.get(this).release();
                String error=(pause?"보행 설명 일시 중지 · 설정 수정 또는 재시도: ":"보행 설명 연결 오류 · 10초 후 최신 사진으로 재시도: ")+message(failure);
                main.post(()->{if(gate.current(ticket)){status=error;publish();}});
            }
        }finally{gate.finish(ticket);}
    }
    private void progress(ContinuousGate.Ticket ticket,String text){main.post(()->{if(monitoring&&!stopping&&gate.current(ticket)){status=text;publish();}});}
    private void check(ContinuousGate.Ticket ticket){if(!gate.current(ticket))throw new CancellationException();}
    private void closeRequest(){CloudRequest current=request;if(current!=null)new Thread(current::close,"monitor-cloud-close").start();}
    static void pauseForAssistant(){MonitorService s=instance;if(s!=null)s.stopGuidance();}
    private void stopGuidance(){speechQueue.clear();announcement=null;activeSpeechValid=()->false;pendingOffline=null;pendingOfflineValid=null;offlineSpeaking=false;main.removeCallbacks(nativeSpeechCheck);if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();}
    private boolean sendBoard(String command){return BoardService.send(command);}
    private void speechStarted(String text){
        Announcement current=announcement;
        if(current==null||!current.message.equals(text)||!activeSpeechValid.getAsBoolean())return;
        current.started=true;narration.offer(current.decision.episode,current.decision.memoryKey,text,current.urgent);narration.rememberSource(current.original);
    }
    private void finishNative(String id,boolean failed){
        if(announcement==null||!id.equals(announcement.nativeId))return;
        Announcement completed=announcement;
        if(failed){speechStatus="기본 음성 재생을 시작하지 못했습니다.";publish();}
        completeSpeech(completed);
    }
    private void completeSpeech(Announcement completed){
        if(completed==null||announcement!=completed)return;
        announcement=null;offlineSpeaking=false;activeSpeechValid=()->false;pendingOffline=null;pendingOfflineValid=null;main.removeCallbacks(nativeSpeechCheck);
        Announcement next=speechQueue.finish(completed);if(next!=null)beginSpeech(next);
    }
    private void beginSpeech(Announcement next){
        announcement=next;
        if(!next.valid.getAsBoolean()){completeSpeech(next);return;}
        if(eleven!=null)eleven.stop();if(tts!=null)tts.stop();pendingOffline=null;pendingOfflineValid=null;offlineSpeaking=false;main.removeCallbacks(nativeSpeechCheck);activeSpeechValid=next.valid;main.postDelayed(nativeSpeechCheck,100);
        if(next.cloudVoice){
            long remaining=WalkingGuidance.MAX_SPEECH_AGE_MS-(SystemClock.elapsedRealtime()-next.capturedAt)-300;
            if(remaining<=300){speakOffline(next.message,next.valid);return;}
            eleven.speak(next.message,next.valid,Math.min(WalkingSpeechText.CLOUD_WAIT_MS,remaining),()->completeSpeech(next));
        }else speakOffline(next.message,next.valid);
    }
    private void speakOffline(String text,java.util.function.BooleanSupplier valid){
        if(valid==null||!valid.getAsBoolean())return;
        if(tts==null||!ttsReady){pendingOffline=text;pendingOfflineValid=valid;speechStatus="기본 음성 준비 중 · 준비되면 현재 안내를 읽습니다.";return;}
        Locale locale=Locale.KOREAN;Set<Voice> voices=tts.getVoices();
        Voice voice=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(locale.getLanguage())).max(Comparator.comparingInt(Voice::getQuality)).orElse(null);
        if(voice==null){speechStatus="한국어 기본 음성이 없습니다. 휴대폰 TTS 음성을 준비하세요.";publish();completeSpeech(announcement);return;}
        tts.setVoice(voice);tts.setSpeechRate(WalkingSpeechText.RATE);String id="walking-guidance-"+(++speechSequence);if(announcement!=null)announcement.nativeId=id;
        int queued=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,id);
        if(queued!=TextToSpeech.SUCCESS){speechStatus="기본 음성 재생 요청이 실패했습니다.";offlineSpeaking=false;publish();completeSpeech(announcement);return;}
        offlineSpeaking=true;main.removeCallbacks(nativeSpeechCheck);main.postDelayed(nativeSpeechCheck,100);speechStatus="기본 한국어 음성 준비 중";publish();
    }
    private Notification notification(){
        String title="길잡이 · "+(config==null?"로컬 AI":AiProvider.name(config.provider))+(stopping?" 중지 처리 중":" 실시간 실행 중");
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MonitorService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle(title).setContentText(status).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(android.R.drawable.ic_media_pause,"중지",stop).build();
    }
    private void publish(){
        if(instance!=this)return;last=new Snapshot(monitoring||stopping,stopping,gate.isPaused(),BoardService.ready(),config,status,result,bleStatus,speechStatus);
        for(Observer observer:OBSERVERS)observer.state(last);
        if(foreground&&SystemClock.elapsedRealtime()-lastNotice>=1000){lastNotice=SystemClock.elapsedRealtime();NotificationManager manager=getSystemService(NotificationManager.class);if(manager!=null&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED))manager.notify(NOTIFICATION,notification());}
    }
    private void clearFrame(){synchronized(frameLock){FramePacket old=latest;latest=null;visionFrames.clear();recentPhotos.clear();previousReview=null;if(old!=null)old.bitmap.recycle();}}
    private void stopMonitoring(){
        if(cleanupQueued)return;cleanupQueued=true;stopping=true;monitoring=false;gate.stop();cameraGeneration++;main.removeCallbacks(reconnect);main.removeCallbacks(renew);
        try{stopGuidance();}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · 음성 중지",failure);}if(repeat!=null)repeat.cancel(false);closeRequest();VideoSource old=camera;camera=null;if(old!=null)try{old.close();}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · 카메라 정리",failure);}status=terminalError.isEmpty()?"실시간 중지 처리 중 · 현재 로컬 연산이 끝나면 종료합니다.":terminalError;publish();
        worker.execute(()->{try{LocalVlm.get(this).release();}catch(Throwable failure){CrashDiagnostics.record("실시간 중지 · 로컬 모델 해제",failure);}finally{main.post(this::finishStop);}});
    }
    private void finishStop(){
        clearFrame();
        try{if(eleven!=null)eleven.close();}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · 클라우드 음성 정리",failure);}finally{eleven=null;}
        try{if(tts!=null)tts.shutdown();}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · 기본 음성 정리",failure);}finally{tts=null;}
        try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · Wi-Fi 잠금 정리",failure);}
        try{if(wake!=null&&wake.isHeld())wake.release();}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · 화면 잠금 정리",failure);}
        foreground=false;cleanupComplete=true;try{ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);}catch(RuntimeException failure){CrashDiagnostics.record("실시간 중지 · 실행 알림 정리",failure);}finally{worker.shutdown();if(destroyed)markStopped();else stopSelf();}
    }
    private void markStopped(){stopping=false;status=terminalError.isEmpty()?"실시간 AI 중지됨":terminalError;publish();if(instance==this)instance=null;}
    @Override public void onDestroy(){destroyed=true;BoardService.forget(boardObserver);stopMonitoring();if(cleanupComplete)markStopped();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static String message(Throwable error){String text=error.getMessage();return text==null?error.getClass().getSimpleName():text;}
}
