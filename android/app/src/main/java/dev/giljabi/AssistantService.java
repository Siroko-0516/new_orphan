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

/** Explicitly armed microphone; the separate BoardService owns BLE. */
@SuppressLint("MissingPermission") // Every protected operation is checked before use; FGS starts from visible Activity.
public class AssistantService extends Service {
    private static final String START="dev.giljabi.ASSISTANT_START",STOP="dev.giljabi.ASSISTANT_STOP",TRIGGER="dev.giljabi.ASSISTANT_TRIGGER",ASK="dev.giljabi.ASSISTANT_ASK",CHANNEL="giljabi-assistant";
    private static final int NOTICE=61;
    interface Observer {void state(Snapshot snapshot);}
    static final class Snapshot {
        final boolean enabled,ready,busy,mapRequested,buttonEnabled;final String address,state,heard,answer,bleStatus,inputRoute,buttonLabel,locationText,mapUrl;final List<NearbyPlaces.Place> places;
        Snapshot(boolean e,boolean r,boolean b,String address,String state,String heard,String answer,String ble,String route,List<NearbyPlaces.Place> places,boolean map,String label,boolean canPress,String location,String mapLink){enabled=e;ready=r;busy=b;this.address=address;this.state=state;this.heard=heard;this.answer=answer;bleStatus=ble;inputRoute=route;this.places=Collections.unmodifiableList(new ArrayList<>(places));mapRequested=map;buttonLabel=label;buttonEnabled=canPress;locationText=location;mapUrl=mapLink;}
    }
    private static volatile AssistantService instance;
    private static volatile Snapshot last=new Snapshot(false,false,false,"","음성비서 꺼짐","","","BLE 미연결","",Collections.emptyList(),false,"버튼 누르고 말하기",true,"","");
    private static final CopyOnWriteArrayList<Observer> OBSERVERS=new CopyOnWriteArrayList<>();
    static Snapshot snapshot(){return last;}
    static boolean active(){return last.enabled;}
    static boolean busy(){return last.busy;}
    static boolean boardButton(){AssistantService s=instance;if(s==null||!s.alive||!s.foreground)return false;s.button(true);return true;}
    static boolean suppressGuidance(Context c){AudioManager a=c.getSystemService(AudioManager.class);return busy()||a!=null&&a.getMode()!=AudioManager.MODE_NORMAL;}
    static void observe(Observer o){OBSERVERS.addIfAbsent(o);o.state(last);}
    static void forget(Observer o){OBSERVERS.remove(o);}
    static void start(Context c,String address,String service,String characteristic)throws IOException{
        if(!address.isEmpty()&&!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address))throw new IOException("BLE 기기 주소를 확인하세요.");
        try{UUID.fromString(service);UUID.fromString(characteristic);}catch(IllegalArgumentException e){throw new IOException("BLE UUID를 확인하세요.");}
        if(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new IOException("마이크 권한을 먼저 허용해주세요.");
        if(!address.isEmpty()&&!BoardService.active())BoardService.connect(c,address,service,characteristic);
        try{ContextCompat.startForegroundService(c,new Intent(c,AssistantService.class).setAction(START).putExtra("address",address).putExtra("service",service).putExtra("characteristic",characteristic));}catch(RuntimeException e){throw new IOException("앱을 화면에 연 상태에서 음성비서를 시작해주세요.",e);}
    }
    static void stop(Context c){if(active())c.startService(new Intent(c,AssistantService.class).setAction(STOP));}
    static void trigger(Context c){if(active())c.startService(new Intent(c,AssistantService.class).setAction(TRIGGER));}
    static void ask(Context c,String value){if(active())c.startService(new Intent(c,AssistantService.class).setAction(ASK).putExtra("text",value));}
    static String takeMap(){AssistantService s=instance;if(s==null)return "";s.mapRequested=false;s.publish();return s.mapUrl;}
    static String mapLink(){AssistantService s=instance;return s==null?"":s.mapUrl;}

    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"assistant-request"));
    private volatile long epoch;private long outputEpoch,lastButton=-10000;
    private volatile boolean alive=true,dialogue,speaking,waiting;private long microphoneEpoch;
    private final AssistantButtonState turns=new AssistantButtonState();
    private byte[] recordedWav;
    private String address="";
    private final BoardService.Observer boardObserver=s->{address=s.address;bleStatus=s.status;publish();};
    private String state="음성비서 준비 중",heard="",answer="",bleStatus="BLE 미연결",inputRoute="";
    private AssistantInput microphone;private AssistantLocation location;private AssistantPosition positionLookup;private volatile CloudRequest request;private volatile PlacesClient.Request placesRequest;private Future<?> task;
    private ElevenSpeech eleven;private TextToSpeech tts;private boolean ttsReady,foreground,focusHeld;
    private AudioManager audio;private AudioFocusRequest focus;private PowerManager.WakeLock wake;
    private boolean communicationRoute,scoRoute;private int previousAudioMode;
    private Consumer<String> outputStarted;private String pendingNative;
    private List<NearbyPlaces.Place> places=Collections.emptyList();private String mapUrl="",locationText="";private boolean mapRequested;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private static final class PendingCall {final AssistantContacts.Contact contact;final AssistantCallGuard guard=new AssistantCallGuard(SystemClock.elapsedRealtime());PendingCall(AssistantContacts.Contact c){contact=c;}}
    private PendingCall pendingCall;

    @Override public void onCreate(){
        super.onCreate();instance=this;
        NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"길잡이 버튼 음성비서",NotificationManager.IMPORTANCE_LOW));
        audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(value->{if(value==AudioManager.AUDIOFOCUS_LOSS||value==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)endDialogue("통화 또는 다른 오디오 사용으로 대화를 중지했어요.");},main).build();
        tts=new TextToSpeech(this,code->main.post(()->{ttsReady=code==TextToSpeech.SUCCESS;if(ttsReady&&pendingNative!=null){String message=pendingNative;pendingNative=null;nativeSpeak(message);}else if(!ttsReady&&pendingNative!=null){pendingNative=null;endDialogue("한국어 기본 음성을 준비해주세요.");}}));
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){main.post(()->{if(validOutput(id))playbackStarted();});}
            @Override public void onDone(String id){main.post(()->{if(validOutput(id))speechDone();});}
            @Override public void onError(String id){main.post(()->{if(validOutput(id))endDialogue("기본 한국어 음성을 재생하지 못했어요.");});}
        });
        eleven=new ElevenSpeech(this,message->{if(alive&&speaking){state=message;publish();}},this::nativeSpeak,message->playbackStarted(),this::speechDone,true);
        BoardService.observe(boardObserver);
        PowerManager pm=getSystemService(PowerManager.class);if(pm!=null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"giljabi:assistant");wake.setReferenceCounted(false);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?STOP:intent.getAction();
        if(STOP.equals(action)){shutdown();stopSelf();return START_NOT_STICKY;}
        if(TRIGGER.equals(action)){if(foreground)button(false);return START_NOT_STICKY;}
        if(ASK.equals(action)){if(foreground){String value=intent.getStringExtra("text");if(value!=null&&!value.trim().isEmpty()&&value.length()<=1000)typed(value.trim());}return START_NOT_STICKY;}
        if(!START.equals(action))return START_NOT_STICKY;
        try{
            int types=Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0;
            if(Build.VERSION.SDK_INT>=30)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if(Build.VERSION.SDK_INT>=29&&(permission(Manifest.permission.ACCESS_FINE_LOCATION)||permission(Manifest.permission.ACCESS_COARSE_LOCATION)))types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            ServiceCompat.startForeground(this,NOTICE,notification(),types);foreground=true;
            if(!dialogue)state="음성비서 대기 · 버튼을 눌러 녹음을 시작하세요.";publish();
        }catch(RuntimeException error){state="음성비서 시작 실패 · 앱을 열고 마이크·주변 기기 권한을 확인해주세요.";shutdown();stopSelf();}
        return START_NOT_STICKY;
    }
    private boolean permission(String name){return ContextCompat.checkSelfPermission(this,name)==PackageManager.PERMISSION_GRANTED;}
    private boolean prepareDialogue(){return prepareDialogue(true);}
    private boolean prepareDialogue(boolean cloudNeeded){
        if(!alive||!permission(Manifest.permission.RECORD_AUDIO)){state="마이크 권한을 허용한 뒤 다시 시작해주세요.";publish();return false;}
        if(cloudNeeded&&!hasAssistantKey()){state=AiProvider.name(AiProvider.assistant(this))+" API 키를 설정해주세요.";publish();return false;}
        if(!focusHeld){focusHeld=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!focusHeld){state="통화 또는 다른 앱의 오디오 사용이 끝난 뒤 다시 눌러주세요.";publish();return false;}}
        try{beginCommunicationRoute();}catch(RuntimeException error){state="이어폰·휴대폰 오디오 연결을 확인해주세요.";publish();return false;}
        dialogue=true;if(wake!=null)wake.acquire(240000);MonitorService.pauseForAssistant();return true;
    }
    boolean hasAssistantKey(){return AiProvider.keys(this,AiProvider.assistant(this)).hasKey();}
    private void beginCommunicationRoute(){
        if(communicationRoute)return;
        previousAudioMode=audio.getMode();audio.setMode(AudioManager.MODE_IN_COMMUNICATION);communicationRoute=true;
        if(Build.VERSION.SDK_INT>=31){
            for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices()){
                int t=d.getType();if(t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_USB_HEADSET||t==AudioDeviceInfo.TYPE_BLE_HEADSET){audio.setCommunicationDevice(d);break;}
            }
        }else if(audio.isBluetoothScoAvailableOffCall()){audio.startBluetoothSco();audio.setBluetoothScoOn(true);scoRoute=true;}
    }
    private void releaseDialogueAudio(){
        if(communicationRoute){
            communicationRoute=false;
            try{if(Build.VERSION.SDK_INT>=31)audio.clearCommunicationDevice();else if(scoRoute){audio.setBluetoothScoOn(false);audio.stopBluetoothSco();}if(audio.getMode()==AudioManager.MODE_IN_COMMUNICATION)audio.setMode(previousAudioMode);}catch(RuntimeException ignored){}
            scoRoute=false;
        }
        if(wake!=null&&wake.isHeld())wake.release();
        if(focusHeld){audio.abandonAudioFocusRequest(focus);focusHeld=false;}
    }
    private void button(boolean hardware){
        long now=SystemClock.elapsedRealtime();if(hardware&&now-lastButton<600)return;lastButton=now;
        AssistantButtonState.Action action=turns.press();
        if(action==AssistantButtonState.Action.IGNORE)return;
        if(action==AssistantButtonState.Action.FINISH){state="녹음을 마치고 전송할 준비 중이에요.";publish();if(microphone!=null)microphone.finish();return;}
        if(action==AssistantButtonState.Action.SEND){byte[] wav=recordedWav;recordedWav=null;if(wav!=null)submitAudio(wav);else endDialogue("녹음이 없어요. 다시 버튼을 눌러주세요.");return;}
        cancelRequests();cancelOutput();closeInput();
        if(!prepareDialogue()){endDialogue(state);return;}
        state="마이크 준비 중 · 시작음 뒤에 말하고, 끝나면 버튼을 다시 누르세요.";publish();
        startMic();
    }
    AssistantInput createMicrophone(AssistantInput.Listener listener){return new AssistantMic(this,listener);}
    private void startMic(){
        if(microphone!=null)return;
        final long micId=++microphoneEpoch;
        AssistantInput mic=createMicrophone(new AssistantInput.Listener(){
            @Override public void ready(String route){
                if(!alive||!dialogue||microphoneEpoch!=micId||turns.phase()!=AssistantButtonState.Phase.RECORDING)return;
                inputRoute=route+" · 버튼으로 녹음 시작 / 전송";state="녹음 중 · 말씀을 마치면 버튼을 다시 눌러 전송하세요.";publish();
                try{ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_VOICE_CALL,35);tone.startTone(ToneGenerator.TONE_PROP_BEEP,100);main.postDelayed(tone::release,180);}catch(RuntimeException ignored){}
            }
            @Override public void finished(byte[] wav,boolean reachedLimit){
                if(!alive||!dialogue||microphoneEpoch!=micId)return;
                microphone=null;inputRoute="";
                if(turns.recorded())submitAudio(wav);
                else if(turns.phase()==AssistantButtonState.Phase.READY){recordedWav=wav;state="60초 녹음이 끝났어요. 버튼을 누르면 전송해요.";publish();}
            }
            @Override public void error(String message){if(dialogue&&microphoneEpoch==micId)endDialogue(message);}
        });microphone=mic;mic.start();
    }
    private void closeInput(){microphoneEpoch++;AssistantInput old=microphone;microphone=null;recordedWav=null;inputRoute="";if(old!=null)old.close();}
    private void typed(String value){
        cancelRequests();cancelOutput();closeInput();
        if(!prepareDialogue(AssistantCommands.parse(value,pendingCall!=null).kind==AssistantCommands.Kind.CHAT)){endDialogue(state);return;}
        turns.waiting();heard=value;route(value,null,epoch);publish();
    }
    private String history(){StringBuilder b=new StringBuilder();for(String turn:history)b.append(turn).append('\n');return b.toString();}
    private void remember(String user,String reply){history.addLast("사용자: "+user+"\n답변: "+reply);while(history.size()>4)history.removeFirst();}
    void submitAudio(byte[] wav){
        String provider=AiProvider.assistant(this);
        turns.waiting();final long turn=epoch;waiting=true;state="녹음을 전송하고 있어요. "+AiProvider.name(provider)+"가 말씀을 확인해요.";publish();
        final String previous=history();
        task=io.submit(()->{
            try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!valid(turn))){
                if(AiProvider.OPENAI.equals(provider)){
                    try(CloudRequest pending=OpenAiClient.transcribeRequest(AiProvider.keys(this,provider).read(),wav,internet.connector,()->!valid(turn))){request=pending;String transcript=pending.execute();main.post(()->{if(valid(turn)){heard=transcript;route(transcript,null,turn);publish();}});}finally{request=null;}
                }else{
                    String model=AiProvider.model(this,provider);
                    try(GeminiClient.Request pending=GeminiClient.assistantRequest(model,AiProvider.keys(this,provider).read(),wav,null,previous,internet.connector,()->!valid(turn))){request=pending;String raw=pending.execute();GeminiClient.AssistantReply reply=GeminiClient.assistantReply(raw);main.post(()->{if(valid(turn)){heard=reply.transcript;if(heard.isEmpty()){speak("잘 듣지 못했어요. 다시 말씀해주세요.",null);return;}route(heard,reply.answer,turn);publish();}});}finally{request=null;}
                }
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
        if(command.kind==AssistantCommands.Kind.CALL_HELP){speak("연락처에 저장된 이름을 말해주세요. 예를 들어, 엄마에게 전화해줘라고 말하면 확인 후 전화를 요청해요.",null);return;}
        if(command.kind==AssistantCommands.Kind.LOCATION){findCurrentLocation(turn);return;}
        if(command.kind==AssistantCommands.Kind.NEARBY){findPlaces(command.target,turn);return;}
        if(command.kind==AssistantCommands.Kind.MAP){if(mapUrl.isEmpty())speak("먼저 내 위치를 알려달라고 하거나 주변 병원을 검색해주세요.",null);else{mapRequested=true;state="앱 화면 또는 알림을 열면 지도로 연결해요.";speak("현재 위치의 지도 검색을 준비했어요. 길잡이 화면이나 알림을 열어 확인할 수 있어요.",null);publish();}return;}
        if(generated!=null){String message=AssistantCommands.speechText(generated);if(message.isEmpty()||!message.matches("(?s).*[가-힣].*"))message="이 질문의 답변을 확인하지 못했어요. 조금 다르게 말씀해주세요.";remember(utterance,message);speak(message,null);return;}
        final String previous=history(),provider=AiProvider.assistant(this);waiting=true;state=AiProvider.name(provider)+"가 답변을 준비하고 있어요.";publish();
        task=io.submit(()->{
            try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!valid(turn))){
                String model=AiProvider.model(this,provider),key=AiProvider.keys(this,provider).read(),reply;
                if(AiProvider.OPENAI.equals(provider))try(CloudRequest pending=OpenAiClient.textRequest(model,key,utterance,previous,internet.connector,()->!valid(turn))){request=pending;reply=pending.execute();}
                else try(GeminiClient.Request pending=GeminiClient.assistantRequest(model,key,null,utterance,previous,internet.connector,()->!valid(turn))){request=pending;reply=GeminiClient.assistantReply(pending.execute()).answer;}
                String result=reply;main.post(()->{if(valid(turn)){String message=result.isEmpty()?"답변을 확인하지 못했어요.":result;remember(utterance,message);speak(message,null);}});
            }catch(Exception error){fail(turn,error);}finally{request=null;}
        });
    }
    private void findContact(String target,long turn){
        if(!permission(Manifest.permission.CALL_PHONE)){speak("앱의 연락처·전화 권한 버튼에서 통화 권한을 허용해주세요.",null);return;}
        waiting=true;state="휴대폰 연락처를 확인하고 있어요.";publish();
        task=io.submit(()->{try{AssistantContacts.Contact contact=findPhoneContact(target);main.post(()->{if(!valid(turn))return;PendingCall call=new PendingCall(contact);pendingCall=call;speak(contact.name+"에게 전화할까요? 버튼을 눌러 네 또는 취소라고 말한 뒤 다시 눌러주세요.",message->{if(pendingCall==call)call.guard.playbackStarted();});});}catch(Exception error){fail(turn,error);}});
    }
    AssistantContacts.Contact findPhoneContact(String target)throws IOException{return AssistantContacts.find(this,target);}
    void placePhoneCall(String number){TelecomManager telecom=getSystemService(TelecomManager.class);if(telecom==null)throw new IllegalStateException("No telephony service");telecom.placeCall(Uri.fromParts("tel",number,null),new Bundle());}
    void playbackStarted(){if(alive&&dialogue&&speaking&&outputStarted!=null)outputStarted.accept(answer);}
    private void confirmCall(){
        PendingCall call=pendingCall;pendingCall=null;
        if(call==null||!call.guard.confirm(SystemClock.elapsedRealtime())){speak("전화 확인이 만료됐어요. 누구에게 전화할지 다시 말씀해주세요.",null);return;}
        String number=AssistantCommands.phone(call.contact.number);
        if(number.isEmpty()||!permission(Manifest.permission.CALL_PHONE)){speak("통화 권한이나 연락처 번호를 확인해주세요.",null);return;}
        endDialogue("전화 연결 요청 중 · "+call.contact.name);
        try{placePhoneCall(number);state="전화 연결을 요청했어요. 실제 연결 상태는 휴대폰 전화 앱에서 확인하세요.";publish();}
        catch(RuntimeException error){if(prepareDialogue())speak("전화를 연결하지 못했어요. 통화 권한과 기본 통화 SIM 설정을 확인해주세요.",null);}
    }
    void locate(AssistantLocation.Callback callback){location=new AssistantLocation(this,callback);location.start(this);}
    void resolveAddress(Location fix,Consumer<String> callback){positionLookup=new AssistantPosition(callback);positionLookup.start(this,fix);}
    private void findCurrentLocation(long turn){
        mapRequested=false;mapUrl="";locationText="";places=Collections.emptyList();waiting=true;state="휴대폰의 현재 위치를 확인하고 있어요.";publish();
        locate(new AssistantLocation.Callback(){
            @Override public void found(Location fix){
                location=null;if(!valid(turn))return;
                locationText=AssistantPosition.summary(fix);mapUrl=AssistantPosition.url(fix);places=Collections.emptyList();
                state="현재 좌표 확인 · 주소를 확인하고 있어요.";publish();
                resolveAddress(fix,address->{positionLookup=null;if(!valid(turn))return;
                    speak(address.isEmpty()?"휴대폰의 위치를 확인했어요. 주소를 불러오지 못했지만, 지도 열어줘라고 말하면 확인한 위치를 지도에서 볼 수 있어요.":(AssistantLocation.ageSeconds(fix)>30?"휴대폰이 최근 확인한 위치는 대략 ":"현재 위치는 대략 ")+address+" 근처예요.",null);});
            }
            @Override public void failed(String message){location=null;if(valid(turn)){locationText="";mapUrl="";speak(message,null);}}
        });
    }
    private void findPlaces(String category,long turn){
        mapRequested=false;mapUrl="";locationText="";places=Collections.emptyList();waiting=true;state="현재 위치를 확인하고 있어요.";publish();
        locate(new AssistantLocation.Callback(){
            @Override public void found(Location fix){location=null;if(!valid(turn))return;locationText=AssistantPosition.summary(fix);try{mapUrl=NearbyPlaces.searchUrl(category,fix.getLatitude(),fix.getLongitude());}catch(IOException ignored){mapUrl="";}state="주변 "+category+" 지도 데이터를 검색하고 있어요.";publish();
                task=io.submit(()->{try{List<NearbyPlaces.Place> found=searchPlaces(category,fix,turn);main.post(()->{if(valid(turn)){places=found;if(!found.isEmpty())mapUrl=found.get(0).url();String message=NearbyPlaces.spoken(category,found);speak(message,null);publish();}});}catch(Exception error){main.post(()->{if(valid(turn)){places=Collections.emptyList();speak("지도 검색 서버가 응답하지 않아 병원이나 장소 목록을 확인하지 못했어요. 현재 위치의 "+category+" 지도 검색은 준비했어요. 지도 열어줘라고 말하거나 앱의 지도 버튼을 눌러주세요.",null);}});}finally{placesRequest=null;}});
            }
            @Override public void failed(String message){location=null;if(valid(turn))speak(message,null);}
        });
    }
    List<NearbyPlaces.Place> searchPlaces(String category,Location fix,long turn)throws Exception{
        try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->!valid(turn));PlacesClient.Request pending=new PlacesClient.Request(category,fix.getLatitude(),fix.getLongitude(),internet.connector,()->!valid(turn))){placesRequest=pending;return pending.execute();}
    }
    private boolean valid(long turn){return alive&&dialogue&&epoch==turn;}
    private void fail(long turn,Exception failure){main.post(()->{if(valid(turn))speak(failure.getMessage()==null?"요청을 처리하지 못했어요. 다시 말씀해주세요.":failure.getMessage(),null);});}
    private void cancelRequests(){epoch++;waiting=false;if(task!=null)task.cancel(true);task=null;CloudRequest r=request;PlacesClient.Request p=placesRequest;request=null;placesRequest=null;if(r!=null)new Thread(r::close,"assistant-cancel").start();if(p!=null)new Thread(p::close,"places-cancel").start();if(location!=null){location.close();location=null;}if(positionLookup!=null){positionLookup.close();positionLookup=null;}}
    private void cancelOutput(){outputEpoch++;speaking=false;pendingNative=null;outputStarted=null;if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();}
    void speak(String raw,Consumer<String> started){
        cancelOutput();if(!alive||!dialogue)return;
        waiting=false;answer=AssistantCommands.speechText(raw);if(answer.isEmpty())answer="다시 말씀해주세요.";
        if(!answer.matches("(?s).*[가-힣].*"))answer="한국어 답변을 준비하지 못했어요. 다시 말씀해주세요.";
        turns.speaking();speaking=true;outputStarted=started;state="답변 중 · 버튼을 누르면 멈추고 새 녹음을 시작해요.";publish();
        playAnswer(outputEpoch);
    }
    void playAnswer(long output){
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
    void speechDone(){
        if(!alive||!dialogue||!speaking)return;
        // Keep a spoken phone confirmation pending across the next button turn.
        cancelRequests();cancelOutput();closeInput();turns.idle();dialogue=false;releaseDialogueAudio();
        state="답변 완료 · 버튼을 누르면 다음 질문을 녹음해요.";publish();
    }
    private void endDialogue(String message){cancelRequests();cancelOutput();closeInput();pendingCall=null;turns.idle();dialogue=false;releaseDialogueAudio();state=message;publish();}
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,610,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent trigger=PendingIntent.getService(this,611,new Intent(this,AssistantService.class).setAction(TRIGGER),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,612,new Intent(this,AssistantService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("길잡이 음성비서").setContentText(turns.label()).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(android.R.drawable.ic_btn_speak_now,turns.label(),trigger).addAction(android.R.drawable.ic_media_pause,"비서 중지",stop).build();
    }
    private void publish(){if(instance!=this)return;last=new Snapshot(alive&&foreground,BoardService.ready(),dialogue,address,state,heard,answer,bleStatus,inputRoute,places,mapRequested,turns.label(),turns.enabled(),locationText,mapUrl);for(Observer o:OBSERVERS)o.state(last);if(foreground){NotificationManager n=getSystemService(NotificationManager.class);if(n!=null)n.notify(NOTICE,notification());}}
    private void shutdown(){if(!alive)return;endDialogue("음성비서 꺼짐");alive=false;foreground=false;BoardService.forget(boardObserver);main.removeCallbacksAndMessages(null);if(eleven!=null)eleven.close();if(tts!=null){tts.shutdown();tts=null;}io.shutdownNow();ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE);publish();}
    @Override public void onDestroy(){shutdown();if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
