package dev.giljabi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import android.telecom.TelecomManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Explicitly armed microphone/BLE owner; recording starts only on a user trigger. */
@SuppressLint("MissingPermission") // Every protected operation is checked before use; FGS starts from visible Activity.
public final class AssistantService extends Service {
    private static final String START="dev.giljabi.ASSISTANT_START",STOP="dev.giljabi.ASSISTANT_STOP",TRIGGER="dev.giljabi.ASSISTANT_TRIGGER",ASK="dev.giljabi.ASSISTANT_ASK",CHANNEL="giljabi-assistant";
    private static final int NOTICE=61;
    interface Observer {void state(Snapshot snapshot);}
    static final class Snapshot {
        final boolean enabled,ready,busy,mapRequested;final String address,state,heard,answer,bleStatus,inputRoute;final List<NearbyPlaces.Place> places;
        Snapshot(boolean e,boolean r,boolean b,String address,String state,String heard,String answer,String ble,String route,List<NearbyPlaces.Place> places,boolean map){enabled=e;ready=r;busy=b;this.address=address;this.state=state;this.heard=heard;this.answer=answer;bleStatus=ble;inputRoute=route;this.places=Collections.unmodifiableList(new ArrayList<>(places));mapRequested=map;}
    }
    private static volatile AssistantService instance;
    private static volatile Snapshot last=new Snapshot(false,false,false,"","음성비서 꺼짐","","","BLE 미연결","",Collections.emptyList(),false);
    private static final CopyOnWriteArrayList<Observer> OBSERVERS=new CopyOnWriteArrayList<>();
    static Snapshot snapshot(){return last;}
    static boolean active(){return last.enabled;}
    static boolean busy(){return last.busy;}
    static boolean ownsBle(String address){return last.enabled&&!address.isEmpty()&&address.equals(last.address);}
    static boolean ready(){return instance!=null&&instance.ble!=null&&instance.ble.isReady();}
    static boolean send(String value){return instance!=null&&instance.ble!=null&&instance.ble.send(value);}
    static boolean suppressGuidance(Context c){AudioManager a=c.getSystemService(AudioManager.class);return busy()||a!=null&&a.getMode()!=AudioManager.MODE_NORMAL;}
    static void observe(Observer o){OBSERVERS.addIfAbsent(o);o.state(last);}
    static void forget(Observer o){OBSERVERS.remove(o);}
    static void start(Context c,String address,String service,String characteristic)throws IOException{
        if(!address.isEmpty()&&!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address))throw new IOException("BLE 기기 주소를 확인하세요.");
        try{UUID.fromString(service);UUID.fromString(characteristic);}catch(IllegalArgumentException e){throw new IOException("BLE UUID를 확인하세요.");}
        if(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new IOException("마이크 권한을 먼저 허용해주세요.");
        try{ContextCompat.startForegroundService(c,new Intent(c,AssistantService.class).setAction(START).putExtra("address",address).putExtra("service",service).putExtra("characteristic",characteristic));}catch(RuntimeException e){throw new IOException("앱을 화면에 연 상태에서 음성비서를 시작해주세요.",e);}
    }
    static void stop(Context c){if(active())c.startService(new Intent(c,AssistantService.class).setAction(STOP));}
    static void trigger(Context c){if(active())c.startService(new Intent(c,AssistantService.class).setAction(TRIGGER));}
    static void ask(Context c,String value){if(active())c.startService(new Intent(c,AssistantService.class).setAction(ASK).putExtra("text",value));}
    static String takeMap(){AssistantService s=instance;if(s==null)return "";s.mapRequested=false;s.publish();return s.mapUrl;}

    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"assistant-request"));
    private volatile long epoch;private long inputSegment=-1,outputEpoch,lastButton=-10000;
    private volatile boolean alive=true,dialogue,speaking,waiting;private long microphoneEpoch;
    private BleLink ble;private String address="",serviceId=BleLink.DEFAULT_SERVICE,characteristicId=BleLink.DEFAULT_CHARACTERISTIC;
    private String state="음성비서 준비 중",heard="",answer="",bleStatus="BLE 미연결",inputRoute="";
    private AssistantMic microphone;private AssistantLocation location;private volatile GeminiClient.Request request;private volatile PlacesClient.Request placesRequest;private Future<?> task;
    private ElevenSpeech eleven;private TextToSpeech tts;private boolean ttsReady,foreground,focusHeld;
    private AudioManager audio;private AudioFocusRequest focus;private PowerManager.WakeLock wake;
    private Consumer<String> outputStarted;private String pendingNative;
    private List<NearbyPlaces.Place> places=Collections.emptyList();private String mapUrl="";private boolean mapRequested;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private static final class PendingCall {final AssistantContacts.Contact contact;final AssistantCallGuard guard=new AssistantCallGuard(SystemClock.elapsedRealtime());PendingCall(AssistantContacts.Contact c){contact=c;}}
    private PendingCall pendingCall;
    private final Runnable reconnect=new Runnable(){@Override public void run(){if(alive){connectBle();main.postDelayed(this,10000);}}};

    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"길잡이 버튼 음성비서",NotificationManager.IMPORTANCE_LOW));
        audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(value->{if(value==AudioManager.AUDIOFOCUS_LOSS||value==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)endDialogue("통화 또는 다른 오디오 사용으로 대화를 중지했어요.");},main).build();
        tts=new TextToSpeech(this,code->main.post(()->{ttsReady=code==TextToSpeech.SUCCESS;if(ttsReady&&pendingNative!=null){String message=pendingNative;pendingNative=null;nativeSpeak(message);}else if(!ttsReady&&pendingNative!=null){pendingNative=null;endDialogue("한국어 기본 음성을 준비해주세요.");}}));
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){main.post(()->{if(validOutput(id)&&outputStarted!=null)outputStarted.accept(answer);});}
            @Override public void onDone(String id){main.post(()->{if(validOutput(id))speechDone();});}
            @Override public void onError(String id){main.post(()->{if(validOutput(id))endDialogue("기본 한국어 음성을 재생하지 못했어요.");});}
        });
        eleven=new ElevenSpeech(this,message->{if(alive&&speaking){state=message;publish();}},this::nativeSpeak,message->{if(speaking&&outputStarted!=null)outputStarted.accept(message);},this::speechDone,true);
        ble=new BleLink(this,message->{bleStatus=message;publish();},devices->{},this::publish,command->{if("ASSIST".equals(command))button(true);});
        PowerManager pm=getSystemService(PowerManager.class);if(pm!=null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"giljabi:assistant");wake.setReferenceCounted(false);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?STOP:intent.getAction();
        if(STOP.equals(action)){shutdown();stopSelf();return START_NOT_STICKY;}
        if(TRIGGER.equals(action)){if(foreground)button(false);return START_NOT_STICKY;}
        if(ASK.equals(action)){if(foreground){String value=intent.getStringExtra("text");if(value!=null&&!value.trim().isEmpty()&&value.length()<=1000)typed(value.trim());}return START_NOT_STICKY;}
        if(!START.equals(action))return START_NOT_STICKY;
        try{
            String next=intent.getStringExtra("address");if(next==null)next="";
            if(!next.equals(address))ble.disconnect();address=next;serviceId=intent.getStringExtra("service");characteristicId=intent.getStringExtra("characteristic");
            int types=Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0;
            if(Build.VERSION.SDK_INT>=30)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if(Build.VERSION.SDK_INT>=29&&!address.isEmpty())types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if(Build.VERSION.SDK_INT>=29&&(permission(Manifest.permission.ACCESS_FINE_LOCATION)||permission(Manifest.permission.ACCESS_COARSE_LOCATION)))types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            ServiceCompat.startForeground(this,NOTICE,notification(),types);foreground=true;
            MonitorService.assistantTakesBle(address);state="음성비서 대기 · 보드 버튼이나 앱의 말하기를 누르세요.";
            main.removeCallbacks(reconnect);main.postDelayed(reconnect,300);publish();
        }catch(RuntimeException error){state="음성비서 시작 실패 · 앱을 열고 마이크·주변 기기 권한을 확인해주세요.";shutdown();stopSelf();}
        return START_NOT_STICKY;
    }
    private boolean permission(String name){return ContextCompat.checkSelfPermission(this,name)==PackageManager.PERMISSION_GRANTED;}
    private void connectBle(){
        if(address.isEmpty()||ble.isReady()||ble.isConnecting())return;
        if(Build.VERSION.SDK_INT>=31&&!permission(Manifest.permission.BLUETOOTH_CONNECT)){bleStatus="주변 기기 권한이 필요해요.";publish();return;}
        try{android.bluetooth.BluetoothAdapter adapter=ble.adapter();if(adapter==null||!adapter.isEnabled()){bleStatus="블루투스를 켜주세요.";publish();return;}ble.connect(new BleLink.Device(adapter.getRemoteDevice(address),"HM-10"),serviceId,characteristicId);}catch(RuntimeException error){bleStatus="HM-10 연결을 확인해주세요.";publish();}
    }
    private boolean prepareDialogue(){
        if(!alive||!permission(Manifest.permission.RECORD_AUDIO)){state="마이크 권한을 허용한 뒤 다시 시작해주세요.";publish();return false;}
        if(!new ApiKeyStore(this).hasKey()){state="Gemini API 키를 설정해주세요.";publish();return false;}
        if(!focusHeld){focusHeld=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!focusHeld){state="통화 또는 다른 앱의 오디오 사용이 끝난 뒤 다시 눌러주세요.";publish();return false;}}
        dialogue=true;if(wake!=null)wake.acquire(240000);MonitorService.pauseForAssistant();return true;
    }
    private void button(boolean hardware){
        long now=SystemClock.elapsedRealtime();if(hardware&&now-lastButton<600)return;lastButton=now;
        if(!prepareDialogue())return;
        cancelRequests();cancelOutput();inputSegment=-1;
        if(microphone!=null){microphone.rearm();state="듣고 있어요. 말씀해주세요.";publish();return;}
        state="마이크 준비 중 · 시작음 뒤에 말씀해주세요.";publish();
        try{ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,35);tone.startTone(ToneGenerator.TONE_PROP_BEEP,100);main.postDelayed(tone::release,180);}catch(RuntimeException ignored){}
        final long starting=epoch;
        main.postDelayed(()->{if(!alive||!dialogue||epoch!=starting)return;startMic();},180);
    }
    private void startMic(){
        if(microphone!=null)return;
        final long micId=++microphoneEpoch;
        AssistantMic mic=new AssistantMic(this,()->speaking,()->waiting,()->getSharedPreferences("ai-settings",MODE_PRIVATE).getBoolean("assistant_barge_in",true),new AssistantMic.Listener(){
            @Override public void voice(long id){if(!alive||!dialogue||microphoneEpoch!=micId)return;cancelRequests();cancelOutput();inputSegment=id;state="듣고 있어요.";publish();}
            @Override public void utterance(long id,byte[] wav){if(!alive||!dialogue||microphoneEpoch!=micId||id!=inputSegment)return;submitAudio(wav);}
            @Override public void idle(){if(dialogue&&microphoneEpoch==micId)endDialogue("잠시 말씀이 없어 듣기를 마쳤어요. 다시 버튼을 누르면 시작해요.");}
            @Override public void error(String message){if(dialogue&&microphoneEpoch==micId)endDialogue(message);}
            @Override public void route(String route,boolean barge){if(!dialogue||microphoneEpoch!=micId)return;inputRoute=route+(barge?" · 말로 끼어들기 켜짐":" · 답변 중에는 버튼으로 끊기");state="듣고 있어요. 말씀해주세요.";publish();}
        });microphone=mic;mic.start();
    }
    private void typed(String value){
        if(!prepareDialogue())return;cancelRequests();cancelOutput();
        startMic();heard=value;route(value,null,epoch);publish();
    }
    private String history(){StringBuilder b=new StringBuilder();for(String turn:history)b.append(turn).append('\n');return b.toString();}
    private void remember(String user,String reply){history.addLast("사용자: "+user+"\n답변: "+reply);while(history.size()>4)history.removeFirst();}
    private void submitAudio(byte[] wav){
        final long turn=epoch;waiting=true;state="말씀을 확인하고 있어요.";publish();
        final String previous=history();
        task=io.submit(()->{
            try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!valid(turn))){
                String model=getSharedPreferences("ai-settings",MODE_PRIVATE).getString("gemini_model",GeminiClient.DEFAULT_MODEL);
                try(GeminiClient.Request pending=GeminiClient.assistantRequest(model,new ApiKeyStore(this).read(),wav,null,previous,internet.connector,()->!valid(turn))){request=pending;String raw=pending.execute();GeminiClient.AssistantReply reply=GeminiClient.assistantReply(raw);main.post(()->{if(valid(turn)){heard=reply.transcript;if(heard.isEmpty()){speak("잘 듣지 못했어요. 다시 말씀해주세요.",null);return;}route(heard,reply.answer,turn);publish();}});}finally{request=null;}
            }catch(Exception error){fail(turn,error);}
        });
    }
    private void route(String utterance,String generated,long turn){
        waiting=false;
        AssistantCommands.Command command=AssistantCommands.parse(utterance,pendingCall!=null);
        if(command.kind==AssistantCommands.Kind.CANCEL){endDialogue("요청을 취소했어요.");return;}
        if(command.kind==AssistantCommands.Kind.CONFIRM){confirmCall();return;}
        pendingCall=null;
        if(command.kind==AssistantCommands.Kind.CALL){findContact(command.target,turn);return;}
        if(command.kind==AssistantCommands.Kind.NEARBY){findPlaces(command.target,turn);return;}
        if(command.kind==AssistantCommands.Kind.MAP){if(mapUrl.isEmpty())speak("먼저 주변 병원이나 약국을 검색해주세요.",null);else{mapRequested=true;state="앱 화면 또는 알림을 열면 지도로 연결해요.";speak("지도는 길잡이 앱 화면이나 실행 알림을 열어 확인할 수 있어요.",null);publish();}return;}
        if(generated!=null){String message=AssistantCommands.speechText(generated);if(message.isEmpty()||!message.matches("(?s).*[가-힣].*"))message="이 질문의 답변을 확인하지 못했어요. 조금 다르게 말씀해주세요.";remember(utterance,message);speak(message,null);return;}
        final String previous=history();waiting=true;state="Gemini가 답변 중이에요.";publish();
        task=io.submit(()->{try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!valid(turn))){String model=getSharedPreferences("ai-settings",MODE_PRIVATE).getString("gemini_model",GeminiClient.DEFAULT_MODEL);try(GeminiClient.Request pending=GeminiClient.assistantRequest(model,new ApiKeyStore(this).read(),null,utterance,previous,internet.connector,()->!valid(turn))){request=pending;GeminiClient.AssistantReply reply=GeminiClient.assistantReply(pending.execute());main.post(()->{if(valid(turn)){String message=reply.answer;if(message.isEmpty())message="답변을 확인하지 못했어요.";remember(utterance,message);speak(message,null);}});}finally{request=null;}}catch(Exception error){fail(turn,error);}});
    }
    private void findContact(String target,long turn){
        if(!permission(Manifest.permission.CALL_PHONE)){speak("앱의 연락처·전화 권한 버튼에서 통화 권한을 허용해주세요.",null);return;}
        waiting=true;state="휴대폰 연락처를 확인하고 있어요.";publish();
        task=io.submit(()->{try{AssistantContacts.Contact contact=AssistantContacts.find(this,target);main.post(()->{if(!valid(turn))return;PendingCall call=new PendingCall(contact);pendingCall=call;speak(contact.name+"에게 전화할까요? 맞으면 네, 아니면 취소라고 말씀해주세요.",message->{if(pendingCall==call)call.guard.playbackStarted();});});}catch(Exception error){fail(turn,error);}});
    }
    private void confirmCall(){
        PendingCall call=pendingCall;pendingCall=null;
        if(call==null||!call.guard.confirm(SystemClock.elapsedRealtime())){speak("전화 확인이 만료됐어요. 누구에게 전화할지 다시 말씀해주세요.",null);return;}
        String number=AssistantCommands.phone(call.contact.number);
        if(number.isEmpty()||!permission(Manifest.permission.CALL_PHONE)){speak("통화 권한이나 연락처 번호를 확인해주세요.",null);return;}
        endDialogue("전화 연결 요청 중 · "+call.contact.name);
        try{TelecomManager telecom=getSystemService(TelecomManager.class);if(telecom==null)throw new IllegalStateException();telecom.placeCall(Uri.fromParts("tel",number,null),new Bundle());state="전화 연결을 요청했어요. 실제 연결 상태는 휴대폰 전화 앱에서 확인하세요.";publish();}
        catch(RuntimeException error){if(prepareDialogue())speak("전화를 연결하지 못했어요. 통화 권한과 기본 통화 SIM 설정을 확인해주세요.",null);}
    }
    private void findPlaces(String category,long turn){
        waiting=true;state="현재 위치를 확인하고 있어요.";publish();
        location=new AssistantLocation(this,new AssistantLocation.Callback(){
            @Override public void found(Location fix){location=null;if(!valid(turn))return;state="주변 "+category+" 지도 데이터를 검색하고 있어요.";publish();
                task=io.submit(()->{try(CloudNetwork.Lease internet=CloudNetwork.connect(AssistantService.this,()->!valid(turn));PlacesClient.Request pending=new PlacesClient.Request(category,fix.getLatitude(),fix.getLongitude(),internet.connector,()->!valid(turn))){placesRequest=pending;List<NearbyPlaces.Place> found=pending.execute();main.post(()->{if(valid(turn)){places=found;mapUrl=found.isEmpty()?"https://www.google.com/maps/search/?api=1&query="+Uri.encode(category+" near "+fix.getLatitude()+","+fix.getLongitude()):found.get(0).url();String message=NearbyPlaces.spoken(category,found);remember(heard,message);speak(message,null);publish();}});}catch(Exception error){fail(turn,error);}finally{placesRequest=null;}});
            }
            @Override public void failed(String message){location=null;if(valid(turn))speak(message,null);}
        });location.start(this);
    }
    private boolean valid(long turn){return alive&&dialogue&&epoch==turn;}
    private void fail(long turn,Exception failure){main.post(()->{if(valid(turn))speak(failure.getMessage()==null?"요청을 처리하지 못했어요. 다시 말씀해주세요.":failure.getMessage(),null);});}
    private void cancelRequests(){epoch++;waiting=false;if(task!=null)task.cancel(true);task=null;GeminiClient.Request r=request;PlacesClient.Request p=placesRequest;request=null;placesRequest=null;if(r!=null)new Thread(r::close,"assistant-cancel").start();if(p!=null)new Thread(p::close,"places-cancel").start();if(location!=null){location.close();location=null;}}
    private void cancelOutput(){outputEpoch++;speaking=false;pendingNative=null;outputStarted=null;if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();}
    private void speak(String raw,Consumer<String> started){
        cancelOutput();if(!alive||!dialogue)return;
        waiting=false;answer=AssistantCommands.speechText(raw);if(answer.isEmpty())answer="다시 말씀해주세요.";
        if(!answer.matches("(?s).*[가-힣].*"))answer="한국어 답변을 준비하지 못했어요. 다시 말씀해주세요.";
        speaking=true;outputStarted=started;state="답변 중 · 다시 말하거나 버튼을 누르면 멈춰요.";publish();
        final long output=outputEpoch;
        if(new ApiKeyStore(this,true).hasKey())eleven.speak(answer,()->alive&&dialogue&&speaking&&outputEpoch==output,7000);else nativeSpeak(answer);
    }
    private boolean validOutput(String id){return alive&&dialogue&&speaking&&id.equals("assistant-"+outputEpoch);}
    private void nativeSpeak(String value){
        if(!alive||!dialogue||!speaking)return;
        if(!ttsReady){pendingNative=value;long expected=outputEpoch;main.postDelayed(()->{if(pendingNative!=null&&expected==outputEpoch)endDialogue("휴대폰의 한국어 기본 음성을 준비해주세요.");},5000);return;}
        Set<Voice> voices=tts.getVoices();Voice voice=voices==null?null:voices.stream().filter(v->v.getLocale().getLanguage().equals("ko")&&!v.isNetworkConnectionRequired()).findFirst().orElse(null);
        if(voice==null){endDialogue("한국어 기본 음성을 휴대폰 TTS 설정에서 준비해주세요.");return;}
        tts.setVoice(voice);if(tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"assistant-"+outputEpoch)!=TextToSpeech.SUCCESS)endDialogue("기본 음성을 시작하지 못했어요.");
    }
    private void speechDone(){if(!alive||!dialogue||!speaking)return;speaking=false;outputStarted=null;state=microphone!=null?"듣고 있어요. 이어서 말씀하거나 취소라고 말해주세요.":"답변 완료 · 버튼을 누르면 대화를 시작해요.";publish();if(microphone==null)endDialogue(state);}
    private void endDialogue(String message){cancelRequests();cancelOutput();pendingCall=null;dialogue=false;microphoneEpoch++;AssistantMic old=microphone;microphone=null;if(old!=null)old.close();inputSegment=-1;if(wake!=null&&wake.isHeld())wake.release();if(focusHeld){audio.abandonAudioFocusRequest(focus);focusHeld=false;}state=message;publish();}
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,610,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent trigger=PendingIntent.getService(this,611,new Intent(this,AssistantService.class).setAction(TRIGGER),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,612,new Intent(this,AssistantService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("길잡이 음성비서").setContentText(dialogue?"대화 모드 · 말하거나 버튼으로 끊기":"버튼 대기 · 누르면 듣기 시작").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(android.R.drawable.ic_btn_speak_now,"말하기 / 끊기",trigger).addAction(android.R.drawable.ic_media_pause,"비서 중지",stop).build();
    }
    private void publish(){if(instance!=this)return;last=new Snapshot(alive&&foreground,ble!=null&&ble.isReady(),dialogue,address,state,heard,answer,bleStatus,inputRoute,places,mapRequested);for(Observer o:OBSERVERS)o.state(last);if(foreground){NotificationManager n=getSystemService(NotificationManager.class);if(n!=null)n.notify(NOTICE,notification());}}
    private void shutdown(){if(!alive)return;endDialogue("음성비서 꺼짐");alive=false;foreground=false;main.removeCallbacksAndMessages(null);if(ble!=null)ble.close();if(eleven!=null)eleven.close();if(tts!=null){tts.shutdown();tts=null;}io.shutdownNow();ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);publish();}
    @Override public void onDestroy(){shutdown();if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
