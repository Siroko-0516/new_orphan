package dev.giljabi;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.text.InputType;
import android.app.AlertDialog;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int PICK_MODEL=10, PICK_VISION=11, PICK_PHOTO=12, TAKE_PHOTO=13, BLE_PERMISSIONS=20, ENABLE_BLUETOOTH=21,NOTIFICATION_PERMISSION=22,CAMERA_PERMISSION=23,ASSISTANT_PERMISSIONS=24,ASSISTANT_ACTION_PERMISSIONS=25;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final List<View> controls=new ArrayList<>();
    private TextView status,result,filesLabel,voiceStatus;
    private DetectionView preview;
    private EditText prompt,ip;
    private CheckBox speak,yoloEnabled,notifyObjects,gpuAuto;
    private YoloDetector yolo;
    private Spinner language,cameraSource;
    private Runnable cameraPermissionAction;
    private Button espDiagnose,espPhoto;
    private RadioGroup aiModes;
    private RadioButton localMode,cloudMode,gptMode;
    private TextView providerHelp;
    private LinearLayout localPanel;
    private SharedPreferences settings;
    private ApiKeyStore apiKeys,elevenKeys,openAiKeys;
    private ElevenSpeech eleven;
    private Button elevenSettingsButton,translationPrepare;
    private volatile CloudRequest cloudRequest;
    private static final String DEFAULT_EN="Describe the main visible objects and their positions. Mention only directly visible obstacles. If the image is unclear, say you cannot confirm. Do not give general advice about walking or white canes. Do not guess distance, motion or collision risk. Use two short sentences in English.";
    private static final String DEFAULT_KO="사진에 뚜렷하게 보이는 주변 물체와 통로 상황을 자연스러운 한국어 두 문장으로 짧게 설명하세요. 한자와 영어를 섞지 마세요. 사진이 불분명하면 확인하기 어렵다고 답하세요. 보이지 않는 계단·단차를 만들거나 흰지팡이 성능·보행에 관한 일반적인 조언을 하지 마세요. 거리·움직임·충돌은 추측하지 마세요.";
    private Button analyze,load,release,stop;
    private File modelFile,visionFile;
    private BundledModels bundledModels;
    private String bundledError;
    private byte[] imageBytes;
    // Never recycle a bitmap submitted to DetectionView: RenderThread may still use it.
    private Bitmap imageBitmap;
    private Uri cameraUri;
    private TextToSpeech tts;
    private LocalVlm localVlm;
    private boolean loaded,busy;
    private volatile boolean destroyed;
    private TextView liveStatus,bleStatus;
    private Spinner bleDevices;
    private EditText serviceUuid,characteristicUuid;
    private Button liveStart,liveStop,bleScan,bleConnect,bleDisconnect,bleTest;
    private Button autoStart,autoStop,autoRetry,cloudSettingsButton,openAiSettingsButton;
    private Spinner autoInterval;
    private TextView autoStatus;
    private boolean observingMonitor,monitorWasActive,applyingMonitorState;
    private static final long[] INTERVALS={1000,2000,3000,5000,10000};
    private TextView assistantStatus,assistantHeard,assistantAnswer,assistantRoute,assistantLocationText;
    private Spinner assistantProvider;
    private Button assistantLocate,assistantMap;
    private Button assistantStart,assistantStop,assistantTalk,assistantAsk;private EditText assistantQuestion;
    private LinearLayout assistantPlaces;private String assistantPlaceKey="";private Runnable assistantPermissionAction;
    private final AssistantService.Observer assistantObserver=this::onAssistantState;
    private final BoardService.Observer boardObserver=this::onBoardState;
    private BleLink ble;
    private final List<BleLink.Device> foundDevices=new ArrayList<>();
    private final ObstacleTracker tracker=new ObstacleTracker();
    private final AtomicBoolean framePosted=new AtomicBoolean();
    private VideoSource live;
    private volatile LocalCamera.Request cameraRequest;
    private int liveGeneration,imageRevision;
    private boolean imageFromLive;
    private List<YoloDetector.Box> displayedBoxes=Collections.emptyList();
    private final MonitorService.Observer monitorObserver=new MonitorService.Observer(){
        @Override public void state(MonitorService.Snapshot snapshot){onMonitorState(snapshot);}
        @Override public void frame(MonitorService.ViewFrame frame){onMonitorFrame(frame);}
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        settings=getSharedPreferences("ai-settings",Context.MODE_PRIVATE);apiKeys=new ApiKeyStore(this);elevenKeys=new ApiKeyStore(this,true);openAiKeys=new ApiKeyStore(this,AiProvider.OPENAI);eleven=new ElevenSpeech(this,message->{if(!destroyed&&!MonitorService.active()&&voiceStatus!=null)voiceStatus.setText(message);},text->{if(!destroyed&&!MonitorService.active())say(text);});
        localVlm=LocalVlm.get(this);modelFile=localVlm.modelFile;visionFile=localVlm.visionFile;bundledModels=localVlm.bundle;bundledError=localVlm.bundleError;
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,28,24,24);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(24,28+insets.getSystemWindowInsetTop(),24,24+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=text(root,"길잡이 · 카메라",22);title.setTextColor(Color.rgb(35,80,55));
        text(root,"장면 설명 AI 선택",18);
        aiModes=new RadioGroup(this);aiModes.setOrientation(RadioGroup.HORIZONTAL);root.addView(aiModes);
        localMode=new RadioButton(this);localMode.setId(View.generateViewId());localMode.setText("로컬 AI · 오프라인");RadioGroup.LayoutParams localLayout=new RadioGroup.LayoutParams(0,-2);localLayout.weight=1;aiModes.addView(localMode,localLayout);controls.add(localMode);
        cloudMode=new RadioButton(this);cloudMode.setId(View.generateViewId());cloudMode.setText("클라우드 AI · Gemini");RadioGroup.LayoutParams cloudLayout=new RadioGroup.LayoutParams(0,-2);cloudLayout.weight=1;aiModes.addView(cloudMode,cloudLayout);controls.add(cloudMode);
        gptMode=new RadioButton(this);gptMode.setId(View.generateViewId());gptMode.setText("GPT · OpenAI");RadioGroup.LayoutParams gptLayout=new RadioGroup.LayoutParams(0,-2);gptLayout.weight=1;aiModes.addView(gptMode,gptLayout);controls.add(gptMode);
        String savedProvider=settings.getString("vision_provider",settings.getBoolean("cloud",false)?AiProvider.GEMINI:AiProvider.LOCAL);
        aiModes.check(AiProvider.OPENAI.equals(savedProvider)?gptMode.getId():AiProvider.GEMINI.equals(savedProvider)?cloudMode.getId():localMode.getId());
        providerHelp=text(root,"",14);
        cloudSettingsButton=button(root,"Gemini API 키 · 모델 설정",v->cloudSettings());
        openAiSettingsButton=button(root,"GPT · OpenAI API 키 · 모델 설정",v->openAiSettings());
        elevenSettingsButton=button(root,"ElevenLabs API 키 · 고정 음성 설정",v->elevenSettings());
        text(root,"영상 입력 선택",18);
        cameraSource=new Spinner(this);cameraSource.setContentDescription("영상 입력 선택");cameraSource.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,CameraSource.LABELS));cameraSource.setSelection(Math.max(0,Math.min(2,settings.getInt("camera_source",CameraSource.ESP))));root.addView(cameraSource);controls.add(cameraSource);
        ip=new EditText(this);ip.setSingleLine(true);ip.setText("http://192.168.4.1:81/stream");root.addView(ip);controls.add(ip);
        text(root,"보행 상황 분석 · 필요한 변화만 음성 안내",18);
        autoInterval=new Spinner(this);autoInterval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"상황 확인 최소 간격 1초","상황 확인 최소 간격 2초","상황 확인 최소 간격 3초","상황 확인 최소 간격 5초","상황 확인 최소 간격 10초"}));autoInterval.setSelection(1);root.addView(autoInterval);controls.add(autoInterval);
        autoStart=button(root,"실시간 AI + 백그라운드 시작",v->startMonitor());
        autoStop=button(root,"실시간 / 백그라운드 중지",v->MonitorService.stop(this));
        autoRetry=button(root,"장면 설명 재시도",v->MonitorService.retry(this));
        autoStatus=text(root,"이전·최신 사진을 계속 확인하고, 보행에 필요한 새 상황만 자연스럽게 안내합니다. 흰지팡이의 좌우 움직임과 잠깐의 감지 소실은 같은 상황으로 유지합니다. 카메라만으로 실제 거리는 측정하지 않습니다.",14);
        voiceStatus=text(root,"로컬 · 기본 한국어 음성 / Gemini · ElevenLabs, 실패하면 기본 음성",13);
        liveStart=button(root,"ESP 영상 + YOLO 시작",v->startLive());
        liveStop=button(root,"영상 중지",v->stopLive());
        liveStatus=text(root,"휴대폰을 ESP32-CAM Wi-Fi에 연결하세요. YOLO는 모델 선택 없이 시작할 수 있습니다.",14);
        espDiagnose=button(root,"ESP 연결 진단 · 사진/영상 확인",v->diagnoseCamera());
        button(root,"앱 오류 기록 보기 · 복사",v->showDiagnostics());
        preview=new DetectionView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
        bleStatus=text(root,"아두이노 · HM-10 BLE 미연결",14);
        bleDevices=new Spinner(this);root.addView(bleDevices);
        ble=new BleLink(this,message->{if(!BoardService.active())bleStatus.setText(message);},devices->{
            Object old=bleDevices.getSelectedItem();String selected=old==null?"":old.toString();
            foundDevices.clear();foundDevices.addAll(devices);
            bleDevices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,foundDevices));
            for(int i=0;i<foundDevices.size();i++)if(foundDevices.get(i).toString().equals(selected))bleDevices.setSelection(i);
        },()->{tracker.reset();if(status!=null)updateControls();});
        bleScan=button(root,"BLE 기기 검색",v->scanBle());
        bleConnect=button(root,"선택한 BLE 기기 연결",v->{Object selected=bleDevices.getSelectedItem();if(selected instanceof BleLink.Device)try{ble.stopScan();BoardService.connect(this,((BleLink.Device)selected).device.getAddress(),serviceUuid.getText().toString(),characteristicUuid.getText().toString());}catch(Exception error){bleStatus.setText(error.getMessage());}else bleStatus.setText("기기를 검색하고 HM-10을 선택하세요.");});
        bleDisconnect=button(root,"BLE 연결 해제",v->BoardService.disconnect(this));
        bleTest=button(root,"아두이노 TEST 신호 보내기",v->{if(!BoardService.send("TEST\n"))bleStatus.setText("BLE 연결 상태를 확인한 뒤 TEST를 다시 눌러주세요.");});
        text(root,"AI나 음성비서를 중지해도 BLE 연결은 유지합니다. 끊으려면 앱 또는 보드 연결 알림의 BLE 연결 해제를 누르세요.",13);
        notifyObjects=new CheckBox(this);notifyObjects.setText("AI의 새 보행 경고를 아두이노 서보 신호로 전송");root.addView(notifyObjects);
        notifyObjects.setOnCheckedChangeListener((v,enabled)->tracker.reset());
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        button(root,"BLE UUID 설정 열기/닫기",v->advanced.setVisibility(advanced.getVisibility()==View.GONE?View.VISIBLE:View.GONE));root.addView(advanced);
        serviceUuid=new EditText(this);serviceUuid.setSingleLine(true);serviceUuid.setHint("Service UUID");serviceUuid.setText(BleLink.DEFAULT_SERVICE);advanced.addView(serviceUuid);
        characteristicUuid=new EditText(this);characteristicUuid.setSingleLine(true);characteristicUuid.setHint("Write characteristic UUID");characteristicUuid.setText(BleLink.DEFAULT_CHARACTERISTIC);advanced.addView(characteristicUuid);
        text(root,"버튼 음성비서 · 전화 / 주변 장소 / 질문",19);
        text(root,"처음 시작할 때 마이크·위치·연락처·전화 권한을 요청합니다. 위치·전화 권한은 해당 기능에만 사용하고, 거절한 기능은 실행하지 않습니다.",13);
        text(root,"음성비서 AI 선택",14);
        assistantProvider=new Spinner(this);assistantProvider.setContentDescription("음성비서 AI 선택");assistantProvider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Gemini 음성비서","GPT 음성비서 · OpenAI API"}));assistantProvider.setSelection(AiProvider.OPENAI.equals(AiProvider.assistant(this))?1:0);root.addView(assistantProvider);controls.add(assistantProvider);
        text(root,"사진·보행 AI와 음성비서 AI는 따로 선택합니다. 현재 위치와 주변 장소 이름·거리는 음성으로 안내합니다. 전화는 누구에게 걸지 묻고, 대상 이름을 읽어 다시 확인한 뒤 긍정 답변일 때만 발신합니다.",13);
        assistantProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){settings.edit().putString("assistant_provider",position==1?AiProvider.OPENAI:AiProvider.GEMINI).apply();}});
        text(root,"물리 버튼이나 앱 버튼을 한 번 누르면 녹음 시작, 말을 마치고 다시 누르면 선택한 AI로 전송합니다. 답변 중 누르면 답변을 끊고 새 녹음을 시작합니다. 주소·주변 장소 조회 시 좌표를 공개 지도 서비스로 전송하며, 연락처 번호는 휴대폰에서만 확인합니다.",13);
        assistantStatus=text(root,"음성비서 꺼짐",15);assistantRoute=text(root,"",13);assistantHeard=text(root,"",14);assistantAnswer=text(root,"",16);
        assistantLocationText=text(root,"",13);
        assistantStart=button(root,"음성비서 시작 · 선택한 HM-10 연결",v->startAssistant());
        assistantStop=button(root,"음성비서 중지",v->AssistantService.stop(this));
        assistantTalk=button(root,"버튼 누르고 말하기",v->AssistantService.trigger(this));
        button(root,"위치·연락처·전화 권한 설정",v->requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE},ASSISTANT_ACTION_PERMISSIONS));
        assistantLocate=button(root,"현재 위치 확인 · AI 서버 없이 조회",v->AssistantService.ask(this,"내 위치 알려줘"));
        assistantMap=button(root,"확인한 위치 / 주변 장소 지도 열기",v->{String url=AssistantService.mapLink();if(!url.isEmpty())openAssistantMap(url);});
        text(root,"이어폰 마이크를 우선 사용합니다. 녹음은 버튼으로 시작하고 전송하며, 조용해지거나 다시 말하는 것으로 끝내거나 답변을 끊지 않습니다. 답변 중에는 녹음하지 않습니다. 최대 60초 녹음 후에도 버튼을 눌러야 전송합니다. 질문·답변 중 일반 카메라 음성은 멈추고 AI 경고 서보 신호는 유지합니다.",13);
        assistantQuestion=new EditText(this);assistantQuestion.setHint("직접 질문 · 예: 주변 병원 있어? / 엄마에게 전화해줘");assistantQuestion.setSingleLine(true);root.addView(assistantQuestion);
        assistantAsk=button(root,"입력한 말 보내기",v->{String value=assistantQuestion.getText().toString().trim();if(!value.isEmpty()){AssistantService.ask(this,value);assistantQuestion.setText("");}});
        assistantPlaces=new LinearLayout(this);assistantPlaces.setOrientation(LinearLayout.VERTICAL);root.addView(assistantPlaces);
        text(root,"© OpenStreetMap contributors · 장소 이름과 좌표는 지도 데이터 기준입니다. 직선거리는 실제 이동 경로와 다르며 영업·진료 여부는 별도로 확인하세요.",12);
        localPanel=new LinearLayout(this);localPanel.setOrientation(LinearLayout.VERTICAL);root.addView(localPanel);
        text(localPanel,"로컬 AI · MiniCPM-V 4.7 1B",19);
        filesLabel=text(localPanel,"",13);refreshFiles();
        if(!BuildConfig.BUNDLED_AI){
            button(localPanel,"1. 언어 모델 GGUF 선택",v->pick(PICK_MODEL,"*/*"));
            button(localPanel,"2. 비전 mmproj GGUF 선택",v->pick(PICK_VISION,"*/*"));
        }
        gpuAuto=new CheckBox(this);gpuAuto.setText("이미지 GPU 가속 자동 · 지원하지 않으면 CPU");gpuAuto.setChecked(localVlm.gpuAuto());localPanel.addView(gpuAuto);controls.add(gpuAuto);
        gpuAuto.setOnCheckedChangeListener((view,enabled)->{if(enabled==localVlm.gpuAuto()&&!localVlm.gpuRecovery())return;runJob(()->{localVlm.setGpuAuto(enabled);ui(()->setStatus(enabled?"다음 로컬 분석에서 GPU 지원 여부를 확인합니다.":"로컬 이미지를 CPU로 처리합니다."));});});
        translationPrepare=button(root,"오프라인 한국어 번역 준비 · 최초 인터넷",v->KoreanTranslator.get(this).prepare(message->ui(()->voiceStatus.setText(message))));
        text(root,"로컬 한국어 안내 전에 번역 준비를 한 번 눌러주세요. 영어 장면 설명을 기기에서 한국어로 바꿉니다. 번역 모델은 이 버튼으로 한 번 내려받으며 사진·문장은 번역 서버로 보내지 않습니다.",13);
        load=button(localPanel,BuildConfig.BUNDLED_AI?"내장 AI 준비":"3. 모델 불러오기",v->loadModel());
        release=button(localPanel,"모델 메모리 해제",v->runJob(()->{unload();ui(()->setStatus("모델 메모리를 해제했습니다."));}));
        button(root,"사진 선택",v->pick(PICK_PHOTO,"image/*"));
        button(root,"카메라 촬영",v->takePhoto());
        espPhoto=button(root,"ESP32 사진 한 장 받기",v->{String address=ip.getText().toString().trim();stopLive();runJob(()->{byte[] jpeg=fetchCameraFrame(address);checkCancelled();setPicture(decodeImage(new ByteArrayInputStream(jpeg),1));});});
        language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"English","한국어"}));root.addView(language);controls.add(language);
        prompt=new EditText(this);prompt.setMinLines(3);prompt.setText("Describe the main visible objects and their positions. Mention only directly visible obstacles. If the image is unclear, say you cannot confirm. Do not give general advice about walking or white canes. Do not guess distance, motion or collision risk. Use two short sentences.");root.addView(prompt);controls.add(prompt);
        language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){if(!MonitorService.active()){settings.edit().putInt("output_language",position).apply();prompt.setText(position==0?DEFAULT_EN:DEFAULT_KO);}}
        });
        yoloEnabled=new CheckBox(this);yoloEnabled.setText("YOLO 보조 감지 사용 (기기 내 실행)");yoloEnabled.setChecked(true);root.addView(yoloEnabled);controls.add(yoloEnabled);
        speak=new CheckBox(this);speak.setText("분석 후 음성 안내 · 클라우드는 ElevenLabs / 로컬은 기본 TTS");speak.setChecked(true);root.addView(speak);
        button(root,"오프라인 한국어 음성 시험",v->say("길잡이 오프라인 음성 테스트입니다."));
        analyze=button(root,"기기에서 사진 분석",v->analyze());
        stop=new Button(this);stop.setText("중지 요청");stop.setEnabled(false);root.addView(stop);stop.setOnClickListener(v->{cancelled.set(true);abortCameraRequest();abortCloudRequest();if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();setStatus("중지 요청됨 · 클라우드 연결은 닫고, 로컬 연산은 현재 처리 후 중지합니다.");});
        status=text(root,BuildConfig.BUNDLED_AI?"AI 모델이 앱에 포함돼 있습니다. 사진 분석을 누르면 자동으로 준비합니다.":"경량 빌드 · 장면 설명용 모델을 따로 선택해야 합니다.",14);result=text(root,"",18);result.setTextIsSelectable(true);
        text(root,"YOLO·기본 음성은 기기에서 실행됩니다. 클라우드 AI 사진은 선택한 Google 또는 OpenAI로 전송됩니다. 새 물체 알림은 거리나 충돌 위험을 측정하지 않습니다.",12);
        tts=new TextToSpeech(this,code->{if(code!=TextToSpeech.SUCCESS)ui(()->setStatus("기본 TTS 초기화 실패 · 화면 결과를 확인하세요."));});
        language.setSelection(settings.getInt("output_language",1));prompt.setText(language.getSelectedItemPosition()==0?DEFAULT_EN:DEFAULT_KO);
        aiModes.setOnCheckedChangeListener((group,id)->{
            boolean cloud=cloudSelected();settings.edit().putBoolean("cloud",cloud).putString("vision_provider",visionProvider()).apply();
            if(applyingMonitorState)return;
            String question=prompt.getText().toString();if(question.equals(DEFAULT_EN)||question.equals(DEFAULT_KO)){prompt.setText(language.getSelectedItemPosition()==0?DEFAULT_EN:DEFAULT_KO);}
            result.setText("");if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();setStatus(cloud?AiProvider.name(visionProvider())+" 선택됨 · 사진과 API 키를 준비하고 분석을 누르세요.":"로컬 AI 선택됨 · 사진을 준비하고 분석을 누르세요.");
            if(MonitorService.active())updateMonitor();else if(cloud)jobs.execute(()->{unload();ui(this::updateControls);});
            updateControls();
        });
        if(cloudSelected())setStatus(AiProvider.name(visionProvider())+" 선택됨 · 사진과 API 키를 준비하고 분석을 누르세요.");
        cameraSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(!applyingMonitorState&&!MonitorService.active()){stopLive();settings.edit().putInt("camera_source",position).apply();liveStatus.setText(CameraSource.phone(position)?"휴대폰 카메라 선택됨 · 영상 + YOLO 또는 실시간 AI 시작을 누르세요. 자동 실행 중에는 카메라 사용 알림이 표시됩니다.":"휴대폰을 ESP32-CAM Wi-Fi에 연결하세요.");}
                updateControls();
            }
        });
        updateControls();
    }
    private int selectedSource(){return cameraSource.getSelectedItemPosition();}
    private boolean requireCamera(Runnable action){
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)return true;
        cameraPermissionAction=action;requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);return false;
    }
    private void showDiagnostics(){
        runJob(()->{String report=CrashDiagnostics.report(this);ui(()->{
            TextView details=new TextView(this);details.setText(report);details.setTextIsSelectable(true);details.setPadding(24,12,24,12);
            ScrollView scroll=new ScrollView(this);scroll.addView(details);
            new AlertDialog.Builder(this).setTitle("앱 오류 기록").setView(scroll).setPositiveButton("복사",(dialog,which)->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("길잡이 오류 기록",report));
            }).setNegativeButton("닫기",null).show();
        });});
    }
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,12,0,12);parent.addView(t);return t;}
    private Button button(LinearLayout parent,String title,View.OnClickListener action){Button b=new Button(this);b.setText(title);b.setOnClickListener(action);parent.addView(b);controls.add(b);return b;}
    private void ui(Runnable action){runOnUiThread(()->{if(!destroyed)action.run();});}
    private void setStatus(String value){status.setText(value);}
    private String visionProvider(){return aiModes!=null&&gptMode!=null&&aiModes.getCheckedRadioButtonId()==gptMode.getId()?AiProvider.OPENAI:aiModes!=null&&cloudMode!=null&&aiModes.getCheckedRadioButtonId()==cloudMode.getId()?AiProvider.GEMINI:AiProvider.LOCAL;}
    private boolean cloudSelected(){return !AiProvider.LOCAL.equals(visionProvider());}
    private ApiKeyStore visionKeys(){return AiProvider.OPENAI.equals(visionProvider())?openAiKeys:apiKeys;}
    private void visionSettings(){if(AiProvider.OPENAI.equals(visionProvider()))openAiSettings();else cloudSettings();}
    private String cloudModel(){return AiProvider.model(this,visionProvider());}
    private void refreshProvider(){
        boolean cloud=cloudSelected();localPanel.setVisibility(cloud?View.GONE:View.VISIBLE);
        String provider=visionProvider();
        String description=cloud?AiProvider.name(provider)+" · "+cloudModel()+"\n이전·최신 사진을 "+(AiProvider.OPENAI.equals(provider)?"OpenAI":"Google")+"로 전송해 비교하고 필요한 변화만 안내합니다. 인터넷·개인 API 키와 API 사용량이 필요합니다. ChatGPT 구독과 OpenAI API 요금은 별개입니다. 클라우드 음성은 기존 ElevenLabs를 우선 사용하고 실패하면 기본 TTS로 대체합니다.\n"+(visionKeys().hasKey()?"개인 API 키 저장됨":"선택한 AI의 API 키 · 모델 설정에서 키를 입력하세요."):"로컬 AI · 인터넷 없이 휴대폰에서 분석합니다. 사진을 외부로 전송하지 않고 기본 TTS만 사용합니다. 한국어 설명은 언어가 섞일 수 있습니다.";
        if(!description.contentEquals(providerHelp.getText()))providerHelp.setText(description);
        elevenSettingsButton.setVisibility(View.VISIBLE);
        analyze.setText(cloud?AiProvider.name(provider)+"로 사진 분석":"기기에서 사진 분석");
    }
    private void cloudSettings(){
        if(busy)return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(32,8,32,8);
        text(panel,apiKeys.hasKey()?"키가 저장돼 있습니다. 새 키를 입력하면 교체하고, 비워두면 기존 키를 유지합니다.":"본인의 Gemini API 키를 입력하세요. 키는 이 휴대폰의 앱 저장소에 암호화해 보관합니다.",14);
        EditText keyInput=new EditText(this);keyInput.setHint("개인 Gemini API 키");keyInput.setSingleLine(true);keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);keyInput.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());keyInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);keyInput.setSaveEnabled(false);keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);panel.addView(keyInput);
        CheckBox reveal=new CheckBox(this);reveal.setText("입력한 키 표시");panel.addView(reveal);
        reveal.setOnCheckedChangeListener((view,checked)->{keyInput.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());keyInput.setSelection(keyInput.length());});
        text(panel,"붙여넣기의 공백·줄바꿈·보이지 않는 서식 문자는 저장 전에 정리합니다. 키의 실제 인증 여부는 Gemini에 요청할 때 확인됩니다.",13);
        EditText modelInput=new EditText(this);modelInput.setSingleLine(true);modelInput.setHint("Gemini 모델 이름");modelInput.setText(settings.getString("gemini_model",GeminiClient.DEFAULT_MODEL));panel.addView(modelInput);
        text(panel,"기본 모델: "+GeminiClient.DEFAULT_MODEL+". API 키를 채팅으로 보내지 말고 이 입력칸에 입력하세요. Gemini 분석에는 인터넷과 사용 가능한 API 할당량이 필요합니다.",13);
        TextView connectionResult=text(panel,"모델 목록 확인은 사용 가능한 이름을 조회합니다. 연결 검사는 짧은 생성 요청 1회로 실제 응답을 확인합니다.",13);
        Button modelList=button(panel,"Google 모델 목록 확인",null);
        Button connectionTest=button(panel,"선택 모델 연결 검사",null);
        modelList.setOnClickListener(v->{
            if(busy)return;ApiKeyInput.Parsed pasted=ApiKeyInput.parse(keyInput.getText().toString());if(pasted.problem!=null){keyInput.setError(pasted.problem);return;}String entered=pasted.value;
            connectionResult.setText("Google 모델 목록 확인 중...");modelList.setEnabled(false);connectionTest.setEnabled(false);
            runJob(()->{try(CloudNetwork.Lease internet=CloudNetwork.connect(this,cancelled::get);GeminiModels.Request request=new GeminiModels.Request(entered.isEmpty()?apiKeys.read():entered,internet.connector,cancelled::get)){
                List<String> models=request.execute();ui(()->{connectionResult.setText("목록 조회 성공 · 실제 생성 가능 여부는 연결 검사로 확인하세요.");if(!isFinishing())new AlertDialog.Builder(this).setTitle("Google에서 조회한 모델").setItems(models.toArray(new String[0]),(d,index)->modelInput.setText(models.get(index))).setNegativeButton("닫기",null).show();});
            }catch(Exception error){ui(()->connectionResult.setText(error.getMessage()));}finally{ui(()->{modelList.setEnabled(true);connectionTest.setEnabled(true);});}});
        });
        connectionTest.setOnClickListener(v->{
            if(busy)return;ApiKeyInput.Parsed pasted=ApiKeyInput.parse(keyInput.getText().toString());if(pasted.problem!=null){keyInput.setError(pasted.problem);return;}String entered=pasted.value,selected=modelInput.getText().toString().trim();if(!GeminiClient.validModel(selected)){modelInput.setError("모델 이름을 확인하세요.");return;}
            connectionResult.setText("선택한 모델에 짧은 생성 요청 중...");modelList.setEnabled(false);connectionTest.setEnabled(false);long started=SystemClock.elapsedRealtime();
            runJob(()->{try(CloudNetwork.Lease internet=CloudNetwork.connect(this,cancelled::get);GeminiClient.Request request=GeminiClient.probeRequest(selected,entered.isEmpty()?apiKeys.read():entered,internet.connector,cancelled::get)){
                cloudRequest=request;request.execute();ui(()->connectionResult.setText(String.format(Locale.KOREAN,"연결·생성 성공 · %s · %.1f초. 아래 저장을 누르면 이 모델을 적용합니다.",selected,(SystemClock.elapsedRealtime()-started)/1000.0)));
            }catch(Exception error){ui(()->connectionResult.setText(error.getMessage()));}finally{cloudRequest=null;ui(()->{modelList.setEnabled(true);connectionTest.setEnabled(true);});}});
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Gemini 설정").setView(panel).setPositiveButton("저장",null).setNegativeButton("취소",null)
            .setNeutralButton("저장된 키 삭제",(d,w)->runJob(()->{apiKeys.clear();ui(()->{setStatus("저장된 Gemini API 키를 삭제했습니다.");if(MonitorService.active()&&AiProvider.GEMINI.equals(visionProvider()))updateMonitor();updateControls();});})).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(apiKeys.hasKey());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                ApiKeyInput.Parsed pasted=ApiKeyInput.parse(keyInput.getText().toString());String key=pasted.value,model=modelInput.getText().toString().trim();
                if(!GeminiClient.validModel(model)){modelInput.setError("gemini-로 시작하는 모델 이름을 입력하세요.");return;}
                if(pasted.problem!=null){keyInput.setError(pasted.problem);return;}
                if(key.isEmpty()&&!apiKeys.hasKey()){keyInput.setError("개인 Gemini API 키를 입력하세요.");return;}
                keyInput.setText("");dialog.dismiss();
                runJob(()->{if(!key.isEmpty())apiKeys.save(key);settings.edit().putString("gemini_model",model).apply();ui(()->{setStatus(pasted.cleaned&&!key.isEmpty()?"붙여넣기 서식을 정리해 Gemini API 키를 저장했습니다.":"Gemini 설정 저장 완료.");if(MonitorService.active()&&AiProvider.GEMINI.equals(visionProvider()))updateMonitor();updateControls();});});
            });
        });
        dialog.show();
    }
    private void openAiSettings(){
        if(busy)return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(32,16,32,8);
        text(panel,"본인의 OpenAI API 키를 이 기기에 암호화해 저장합니다. ChatGPT 구독과 API 요금은 별개입니다. GPT 선택 시 사진·음성·질문이 OpenAI로 전송되며 API 사용료가 발생합니다.",14);
        EditText keyInput=new EditText(this);keyInput.setHint(openAiKeys.hasKey()?"저장된 키 유지 · 바꾸려면 새 키 입력":"개인 OpenAI API 키");keyInput.setSingleLine(true);keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);keyInput.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());keyInput.setSaveEnabled(false);keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);keyInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);panel.addView(keyInput);
        EditText modelInput=new EditText(this);modelInput.setSingleLine(true);modelInput.setHint("GPT 모델 이름");modelInput.setText(settings.getString("openai_model",OpenAiClient.DEFAULT_MODEL));panel.addView(modelInput);
        text(panel,"기본: "+OpenAiClient.DEFAULT_MODEL+". 음성은 OpenAI 전사 API로 받아 앱의 전화·위치 기능이나 GPT 대화로 연결합니다. 모델 목록과 연결 검사로 계정에서 사용 가능한 모델을 확인하세요.",13);
        TextView checkResult=text(panel,"",13);
        Button list=button(panel,"OpenAI 모델 목록 확인",null),probe=button(panel,"선택 GPT 모델 연결 검사",null);
        list.setOnClickListener(v->{
            if(busy)return;ApiKeyInput.Parsed parsed=ApiKeyInput.parse(keyInput.getText().toString());if(parsed.problem!=null){keyInput.setError(parsed.problem);return;}String entered=parsed.value;
            checkResult.setText("OpenAI 모델 목록 확인 중...");list.setEnabled(false);probe.setEnabled(false);
            runJob(()->{try(CloudNetwork.Lease internet=CloudNetwork.connect(this,cancelled::get);CloudRequest request=OpenAiClient.modelsRequest(entered.isEmpty()?openAiKeys.read():entered,internet.connector,cancelled::get)){
                cloudRequest=request;List<String> models=OpenAiClient.models(request.execute());ui(()->{checkResult.setText("모델 목록 조회 성공 · 생성·사진 지원은 연결 검사와 실제 요청으로 확인하세요.");if(!isFinishing())new AlertDialog.Builder(this).setTitle("OpenAI 계정의 GPT 모델").setItems(models.toArray(new String[0]),(d,index)->modelInput.setText(models.get(index))).setNegativeButton("닫기",null).show();});
            }catch(Exception error){ui(()->checkResult.setText(error.getMessage()));}finally{cloudRequest=null;ui(()->{list.setEnabled(true);probe.setEnabled(true);});}});
        });
        probe.setOnClickListener(v->{
            if(busy)return;ApiKeyInput.Parsed parsed=ApiKeyInput.parse(keyInput.getText().toString());if(parsed.problem!=null){keyInput.setError(parsed.problem);return;}String entered=parsed.value,model=modelInput.getText().toString().trim();if(!OpenAiClient.validModel(model)){modelInput.setError("gpt-로 시작하는 모델 이름을 확인하세요.");return;}
            checkResult.setText("짧은 생성 요청으로 연결 검사 중 · API 사용량이 발생할 수 있어요.");list.setEnabled(false);probe.setEnabled(false);
            runJob(()->{try(CloudNetwork.Lease internet=CloudNetwork.connect(this,cancelled::get);CloudRequest request=OpenAiClient.textRequest(model,entered.isEmpty()?openAiKeys.read():entered,"한국어로 연결 확인이라고만 답하세요.","",internet.connector,cancelled::get)){
                cloudRequest=request;request.execute();ui(()->checkResult.setText("GPT 연결·생성 성공 · 아래 저장을 누르면 적용합니다."));
            }catch(Exception error){ui(()->checkResult.setText(error.getMessage()));}finally{cloudRequest=null;ui(()->{list.setEnabled(true);probe.setEnabled(true);});}});
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("GPT · OpenAI API 설정").setView(panel).setPositiveButton("저장",null).setNegativeButton("취소",null).setNeutralButton("저장된 키 삭제",(d,w)->runJob(()->{openAiKeys.clear();ui(()->{setStatus("저장된 OpenAI API 키를 삭제했어요.");if(MonitorService.active()&&AiProvider.OPENAI.equals(visionProvider()))updateMonitor();updateControls();});})).create();
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(openAiKeys.hasKey());dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            ApiKeyInput.Parsed parsed=ApiKeyInput.parse(keyInput.getText().toString());String key=parsed.value,model=modelInput.getText().toString().trim();
            if(parsed.problem!=null){keyInput.setError(parsed.problem);return;}if(!OpenAiClient.validModel(model)){modelInput.setError("GPT 모델 이름을 확인하세요.");return;}if(key.isEmpty()&&!openAiKeys.hasKey()){keyInput.setError("개인 OpenAI API 키를 입력하세요.");return;}
            keyInput.setText("");dialog.dismiss();runJob(()->{if(!key.isEmpty())openAiKeys.save(key);settings.edit().putString("openai_model",model).apply();ui(()->{setStatus("OpenAI GPT 설정을 저장했어요.");if(MonitorService.active()&&AiProvider.OPENAI.equals(visionProvider()))updateMonitor();updateControls();});});
        });});dialog.show();
    }
    private void elevenSettings(){
        if(busy)return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(32,8,32,8);
        text(panel,"클라우드 AI·음성비서는 ElevenLabs, 로컬 장면 AI는 휴대폰 기본 음성을 사용합니다. 음성 모델은 기존 eleven_multilingual_v2로 고정합니다. 설명 문장이 ElevenLabs로 전송되며 서비스 사용량이 발생합니다.",14);
        EditText key=new EditText(this);key.setHint(elevenKeys.hasKey()?"새 키 입력 · 비우면 기존 키 유지":"개인 ElevenLabs API 키");key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);panel.addView(key);
        Spinner voice=new Spinner(this);voice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"기존 여자 음성","기존 남자 음성"}));voice.setSelection(ElevenLabsClient.MALE.equals(settings.getString("eleven_voice",ElevenLabsClient.FEMALE))?1:0);panel.addView(voice);
        text(panel,"제공한 여자·남자 Voice ID 두 개만 사용하며 선택한 음성을 계속 유지합니다. 키는 이 휴대폰에 암호화해 저장합니다.",13);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ElevenLabs 설정").setView(panel).setPositiveButton("저장",null).setNegativeButton("취소",null).setNeutralButton("저장된 키 삭제",(d,w)->runJob(()->{elevenKeys.clear();ui(()->setStatus("ElevenLabs API 키를 삭제했습니다."));})).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String raw=key.getText().toString();
            if(raw.contains("ELEVENLABS_API_KEY=")||raw.contains("xi-api-key:")){key.setError("설정 코드 대신 API 키 값만 입력하세요.");return;}
            ApiKeyInput.Parsed parsed=ApiKeyInput.parse(raw);
            if(parsed.problem!=null){key.setError(parsed.problem.replace("AI Studio","ElevenLabs"));return;}
            if(parsed.value.isEmpty()&&!elevenKeys.hasKey()){key.setError("개인 ElevenLabs API 키를 입력하세요.");return;}
            String selected=voice.getSelectedItemPosition()==1?ElevenLabsClient.MALE:ElevenLabsClient.FEMALE;key.setText("");dialog.dismiss();
            runJob(()->{if(!parsed.value.isEmpty())elevenKeys.save(parsed.value);settings.edit().putString("eleven_voice",selected).apply();ui(()->{setStatus("ElevenLabs 설정 저장 완료.");if(MonitorService.active())updateMonitor();});});
        }));dialog.show();
    }
    private void refreshFiles(){
        if(BuildConfig.BUNDLED_AI){filesLabel.setText(bundledError!=null?"내장 AI 패키지 오류: "+bundledError:"AI 모델 내장 · 첫 사용 준비 후 인터넷 없이 분석합니다.");return;}
        filesLabel.setText("언어 모델: "+(modelFile.exists()?modelFile.length()/1048576+"MB":"미선택")+" / 비전: "+(visionFile.exists()?visionFile.length()/1048576+"MB":"미선택"));
    }
    private MonitorConfig monitorConfig(){
        Object selected=bleDevices.getSelectedItem();String address="";
        if(BoardService.active())address=BoardService.snapshot().address;
        if(address.isEmpty()&&selected instanceof BleLink.Device&&(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED))address=((BleLink.Device)selected).device.getAddress();
        if(address.isEmpty()&&AssistantService.active())address=AssistantService.snapshot().address;
        if(address.isEmpty()&&MonitorService.active()&&MonitorService.snapshot().config!=null)address=MonitorService.snapshot().config.bleAddress;
        return new MonitorConfig(ip.getText().toString().trim(),prompt.getText().toString().trim(),cloudModel(),cloudSelected(),speak.isChecked(),yoloEnabled.isChecked(),notifyObjects.isChecked(),INTERVALS[autoInterval.getSelectedItemPosition()],address,serviceUuid.getText().toString().trim(),characteristicUuid.getText().toString().trim(),selectedSource(),visionProvider());
    }
    private void startMonitor(){
        if(busy||MonitorService.active())return;
        if(CameraSource.phone(selectedSource())&&!requireCamera(this::startMonitor))return;
        if(cloudSelected()&&!visionKeys().hasKey()){visionSettings();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!settings.getBoolean("notification_asked",false)){
            settings.edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);return;
        }
        try{
            MonitorConfig config=monitorConfig();config.validate();if(!config.bleAddress.isEmpty()&&!BoardService.active())BoardService.connect(this,config.bleAddress,config.serviceUuid,config.characteristicUuid);stopLive();ble.stopScan();if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();
            jobs.execute(this::closePhotoYolo);MonitorService.start(this,config);onMonitorState(MonitorService.snapshot());
        }catch(Exception error){setStatus("실시간 시작 실패: "+error.getMessage());updateControls();}
    }
    private void updateMonitor(){try{MonitorService.update(this,monitorConfig());}catch(Exception error){setStatus("설정 적용 실패: "+error.getMessage());}}
    private void onMonitorState(MonitorService.Snapshot snapshot){
        if(destroyed)return;
        if(snapshot.active&&!monitorWasActive&&snapshot.config!=null){
            MonitorConfig c=snapshot.config;applyingMonitorState=true;
            aiModes.check(AiProvider.OPENAI.equals(c.provider)?gptMode.getId():c.cloud?cloudMode.getId():localMode.getId());cameraSource.setSelection(c.source);ip.setText(c.address);prompt.setText(c.question);speak.setChecked(c.speak);yoloEnabled.setChecked(c.useYolo);notifyObjects.setChecked(c.notify);
            serviceUuid.setText(c.serviceUuid);characteristicUuid.setText(c.characteristicUuid);
            for(int i=0;i<INTERVALS.length;i++)if(INTERVALS[i]==c.intervalMs)autoInterval.setSelection(i);
            applyingMonitorState=false;
        }
        if(snapshot.active||monitorWasActive){autoStatus.setText(snapshot.status);setStatus(snapshot.status);voiceStatus.setText(snapshot.speechStatus);if(!snapshot.result.isEmpty())result.setText(snapshot.result);}
        monitorWasActive=snapshot.active;updateControls();
    }
    private void onMonitorFrame(MonitorService.ViewFrame frame){
        if(destroyed||!observingMonitor||!MonitorService.active()){frame.bitmap.recycle();return;}
        imageBitmap=frame.bitmap;imageBytes=frame.jpeg;displayedBoxes=frame.boxes;imageFromLive=frame.detectionMs>=0;imageRevision++;
        preview.update(imageBitmap,displayedBoxes);
        liveStatus.setText(String.format(Locale.ROOT,"실시간 입력 %d×%d · 수신 %.1f FPS · YOLO %dms · 물체 %d개",frame.bitmap.getWidth(),frame.bitmap.getHeight(),frame.fps,frame.detectionMs,frame.boxes.size()));
    }
    private void updateControls(){
        MonitorService.Snapshot state=MonitorService.snapshot();boolean active=state.active;loaded=localVlm.isLoaded();
        for(View v:controls)v.setEnabled(!busy&&!active);
        boolean canPrepare=bundledModels!=null||(!BuildConfig.BUNDLED_AI&&modelFile.exists()&&visionFile.exists());
        load.setEnabled(!busy&&!active&&!loaded&&canPrepare);
        release.setEnabled(!busy&&!active&&loaded);analyze.setEnabled(!busy&&!active&&(cloudSelected()||loaded||bundledModels!=null)&&imageBytes!=null);stop.setEnabled(busy);
        liveStart.setEnabled(!busy&&!active&&live==null);liveStop.setEnabled(live!=null);ip.setEnabled(!busy&&!active&&live==null&&selectedSource()==CameraSource.ESP);
        cameraSource.setEnabled(!busy&&!active&&live==null);
        espDiagnose.setEnabled(!busy&&!active&&selectedSource()==CameraSource.ESP);espPhoto.setEnabled(!busy&&!active&&selectedSource()==CameraSource.ESP);
        liveStart.setText(CameraSource.label(selectedSource())+" 영상 + YOLO 시작");
        bleDevices.setEnabled(!BoardService.active()&&!busy);serviceUuid.setEnabled(!BoardService.active()&&!busy);characteristicUuid.setEnabled(!BoardService.active()&&!busy);
        bleScan.setEnabled(!BoardService.active()&&!busy);bleConnect.setEnabled(!BoardService.active()&&!busy);bleDisconnect.setEnabled(BoardService.active());bleTest.setEnabled(BoardService.ready());
        speak.setEnabled(!active&&!busy);notifyObjects.setEnabled(!active&&!busy);
        localMode.setEnabled(!busy&&!state.stopping);cloudMode.setEnabled(!busy&&!state.stopping);cloudSettingsButton.setEnabled(!busy&&!state.stopping);elevenSettingsButton.setEnabled(!busy&&!state.stopping);translationPrepare.setEnabled(!busy&&!state.stopping);
        gptMode.setEnabled(!busy&&!state.stopping);openAiSettingsButton.setEnabled(!busy&&!state.stopping);
        AssistantService.Snapshot assistant=AssistantService.snapshot();assistantProvider.setEnabled(!busy&&!assistant.busy);assistantLocate.setEnabled(!busy&&assistant.enabled);assistantMap.setEnabled(!busy&&!assistant.mapUrl.isEmpty());
        assistantStart.setEnabled(!busy&&!assistant.enabled);assistantStop.setEnabled(assistant.enabled);assistantTalk.setEnabled(assistant.enabled&&assistant.buttonEnabled);assistantAsk.setEnabled(assistant.enabled&&!busy);
        autoStart.setEnabled(!busy&&!active);autoStop.setEnabled(active&&!state.stopping);autoRetry.setEnabled(active&&!state.stopping&&state.paused);
        if(busy||live!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshProvider();
    }
    private byte[] fetchCameraFrame(String address) throws IOException {return fetchCameraFrame(address,false);}
    private byte[] fetchCameraFrame(String address,boolean requireStream) throws IOException {
        WifiCamera.Choice choice=WifiCamera.choose(this,address);
        ui(()->liveStatus.setText(choice.description+"\n카메라 응답 확인 중..."));
        try(LocalCamera.Request pending=LocalCamera.request(address,choice.connector,message->ui(()->liveStatus.setText(choice.description+"\n"+message)))){
            cameraRequest=pending;checkCancelled();
            try(LocalCamera.Session session=pending.open()){
                if(requireStream&&!session.multipart)throw new IOException("사진용 주소입니다. 실시간 영상 주소 :81/stream을 입력하세요.");
                byte[] jpeg=session.next();checkCancelled();return jpeg;
            }
        }finally{cameraRequest=null;}
    }
    private void abortCameraRequest(){LocalCamera.Request pending=cameraRequest;if(pending!=null)new Thread(pending::close,"camera-request-close").start();}
    private void abortCloudRequest(){CloudRequest pending=cloudRequest;if(pending!=null)new Thread(pending::close,"cloud-request-close").start();}
    private void diagnoseCamera(){
        if(busy||MonitorService.active())return;String address=ip.getText().toString().trim();stopLive();
        runJob(()->{
            URL stream=new URL(address);
            if(!LocalCamera.isAllowed(stream))throw new IOException("사설 IPv4 카메라 주소를 입력하세요.");
            int capturePort=stream.getPort()==81?80:stream.getPort();
            String snapshot=new URL(stream.getProtocol(),stream.getHost(),capturePort,"/capture").toString();
            long started=SystemClock.elapsedRealtime();
            byte[] photo;
            try{photo=fetchCameraFrame(snapshot);}catch(IOException e){throw new IOException("사진 수신 실패: "+e.getMessage(),e);}
            setPicture(decodeImage(new ByteArrayInputStream(photo),1));
            long photoMs=SystemClock.elapsedRealtime()-started;checkCancelled();
            byte[] frame;
            try{frame=fetchCameraFrame(address,true);}catch(IOException e){throw new IOException("사진은 정상("+photoMs+"ms), 영상 주소는 실패: "+e.getMessage(),e);}
            checkCancelled();setPicture(decodeImage(new ByteArrayInputStream(frame),1));
            long totalMs=SystemClock.elapsedRealtime()-started;
            ui(()->{liveStatus.setText("연결 진단 완료 · 사진 "+photoMs+"ms / 영상 첫 프레임 "+(totalMs-photoMs)+"ms\n크롬 카메라 탭을 닫고 ESP 영상 + YOLO 시작을 누르세요.");setStatus("ESP 사진과 영상에서 각각 JPEG 수신 확인. 지속 전송 속도 검증은 별도입니다.");});
        });
    }
    private void startLive(){
        if(busy||MonitorService.active()||live!=null)return;
        if(CameraSource.phone(selectedSource())&&!requireCamera(this::startLive))return;
        String address=ip.getText().toString().trim();
        try{
            int generation=++liveGeneration;tracker.reset();
            if(CameraSource.phone(selectedSource())){
                live=new PhoneCamera(this,selectedSource(),frame->onLiveFrame(generation,frame),message->ui(()->{if(generation==liveGeneration)liveStatus.setText(message);}),failure->ui(()->{if(generation==liveGeneration){stopLive();liveStatus.setText("영상 중지: "+failure.message);}}));
                updateControls();return;
            }
            WifiCamera.Choice choice=WifiCamera.choose(this,address);
            liveStatus.setText(choice.description+"\nESP 영상 연결 중...");
            live=new LiveCamera(this,address,choice.connector,jpeg->decodeImage(new ByteArrayInputStream(jpeg),1),frame->onLiveFrame(generation,frame),message->ui(()->{if(generation==liveGeneration)liveStatus.setText(choice.description+"\n"+message);}),message->ui(()->{if(generation==liveGeneration){stopLive();liveStatus.setText("영상 중지: "+message.message);}}));
            updateControls();
        }catch(Exception e){liveStatus.setText(e.getMessage());}
    }
    private void onLiveFrame(int generation,LiveCamera.Frame frame){
        if(destroyed||!framePosted.compareAndSet(false,true)){frame.bitmap.recycle();return;}
        runOnUiThread(()->{
            framePosted.set(false);
            if(destroyed||live==null||generation!=liveGeneration){frame.bitmap.recycle();return;}
            imageBitmap=frame.bitmap;imageBytes=frame.jpeg;displayedBoxes=frame.boxes;imageFromLive=frame.detectionMs>=0;imageRevision++;
            preview.update(imageBitmap,displayedBoxes);
            if(frame.detectionMs<0){liveStatus.setText("첫 영상 수신 확인 · YOLO 불러오는 중...");updateControls();return;}
            YoloDetector.Box event=tracker.update(frame.boxes,SystemClock.elapsedRealtime());
            String signal="";

            liveStatus.setText(String.format(Locale.ROOT,"입력 %d×%d · 수신 %.1f FPS · YOLO %dms · 물체 %d개%s%s",frame.bitmap.getWidth(),frame.bitmap.getHeight(),frame.receiveFps,frame.detectionMs,frame.boxes.size(),signal,frame.bitmap.getWidth()<416?"\n낮은 입력 해상도에서는 작은 물체 인식이 어려울 수 있습니다.":""));
            updateControls();
        });
    }
    private void stopLive(){liveGeneration++;VideoSource old=live;live=null;if(old!=null)old.close();tracker.reset();if(liveStatus!=null)liveStatus.setText("영상 중지됨");if(status!=null)updateControls();}
    private boolean hasBlePermissions(){
        if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void scanBle(){
        if(BoardService.active()){bleStatus.setText("기기를 바꾸려면 BLE 연결을 먼저 해제해주세요.");return;}
        if(!hasBlePermissions()){
            requestPermissions(Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION},BLE_PERMISSIONS);return;
        }
        try{
            BluetoothAdapter adapter=ble.adapter();if(adapter==null){bleStatus.setText("이 기기는 블루투스를 지원하지 않습니다.");return;}
            if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_BLUETOOTH);return;}
            if(Build.VERSION.SDK_INT<31){LocationManager location=getSystemService(LocationManager.class);if(location!=null&&!location.isProviderEnabled(LocationManager.GPS_PROVIDER)&&!location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){bleStatus.setText("Android 11 이하에서는 위치 서비스를 켜고 다시 BLE 검색을 눌러주세요.");return;}}
            ble.scan();
        }catch(SecurityException e){bleStatus.setText("주변 기기 권한을 허용하세요.");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==ASSISTANT_PERMISSIONS){Runnable next=assistantPermissionAction;assistantPermissionAction=null;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)){if(next!=null)next.run();}else assistantStatus.setText("음성비서에 마이크·주변 기기 권한이 필요합니다.");return;}if(request==ASSISTANT_ACTION_PERMISSIONS){assistantStatus.setText("허용한 권한을 적용했습니다. 위치·연락처·통화는 허용된 기능만 사용할 수 있습니다.");if(AssistantService.active())startAssistant();return;}if(request==CAMERA_PERMISSION){Runnable pending=cameraPermissionAction;cameraPermissionAction=null;if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){if(pending!=null)pending.run();}else setStatus("카메라 권한이 필요합니다. 휴대폰 설정 → 앱 → 길잡이 → 권한에서 허용하세요.");return;}if(request==NOTIFICATION_PERMISSION){startMonitor();return;}if(request==BLE_PERMISSIONS){if(hasBlePermissions())scanBle();else bleStatus.setText("BLE 사용에 필요한 권한이 허용되지 않았습니다.");}}
    private interface Task {void run() throws Exception;}
    private void runJob(Task task){
        if(busy)return;busy=true;cancelled.set(false);updateControls();
        jobs.execute(()->{
            try{task.run();}catch(CancellationException ignored){ui(()->setStatus("작업을 중지했습니다."));}
            catch(Throwable e){ui(()->setStatus(cancelled.get()?"작업을 중지했습니다.":"실패: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}
            finally{ui(()->{busy=false;refreshFiles();updateControls();});}
        });
    }
    private void checkCancelled(){if(cancelled.get()||destroyed)throw new CancellationException();}
    private void pick(int request,String type){stopLive();Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,request);}
    private void takePhoto(){
        if(!requireCamera(this::takePhoto))return;
        stopLive();
        File folder=new File(getCacheDir(),"photos");folder.mkdirs();File photo=new File(folder,"camera.jpg");
        cameraUri=FileProvider.getUriForFile(this,getPackageName()+".photos",photo);
        Intent capture=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);capture.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(capture,TAKE_PHOTO);}catch(Exception e){setStatus("카메라 앱을 열지 못했습니다. 사진 선택을 사용하세요.");}
    }
    @Override protected void onActivityResult(int request,int response,Intent data){
        super.onActivityResult(request,response,data);if(response!=RESULT_OK)return;
        if(request==ENABLE_BLUETOOTH){scanBle();return;}
        Uri uri=request==TAKE_PHOTO?cameraUri:(data==null?null:data.getData());if(uri==null)return;
        if(request==PICK_MODEL||request==PICK_VISION){File target=request==PICK_MODEL?modelFile:visionFile;runJob(()->importModel(uri,target));}
        else runJob(()->{
            int orientation=1;
            try(InputStream in=getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            Bitmap bitmap;
            try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=decodeImage(in,orientation);}
            setPicture(bitmap);
        });
    }
    private void importModel(Uri uri,File target) throws Exception {
        unload();ui(()->setStatus("앱 저장소에 모델을 복사 중입니다. 원본 용량만큼 여유 저장 공간이 필요합니다."));
        long size=-1;try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))size=cursor.getLong(0);}
        if(size>new StatFs(getFilesDir().getPath()).getAvailableBytes()-64L*1024*1024)throw new IOException("모델 복사에 필요한 저장 공간이 부족합니다.");
        File temp=new File(target.getPath()+".part");
        try(InputStream input=getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(temp)){
            if(input==null)throw new IOException("파일을 읽을 수 없습니다.");
            byte[] magic=new byte[4];new DataInputStream(input).readFully(magic);if(!Arrays.equals(magic,new byte[]{'G','G','U','F'}))throw new IOException("GGUF 파일이 아닙니다.");output.write(magic);
            byte[] buffer=new byte[1024*1024];int count;long copied=4,last=0;
            while((count=input.read(buffer))!=-1){checkCancelled();output.write(buffer,0,count);copied+=count;if(copied-last>64L*1024*1024){last=copied;long mb=copied/1048576;ui(()->setStatus("모델 복사 중 · "+mb+"MB"));}}
            checkCancelled();
        }catch(Throwable e){temp.delete();throw e;}
        Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        ui(()->setStatus("파일 준비 완료. 맞는 언어 모델과 비전 파일을 모두 선택한 뒤 불러오세요."));
    }
    private void loadModel(){runJob(this::prepareModel);}
    private void prepareModel() throws Exception {
        localVlm.prepare(()->cancelled.get()||destroyed,message->ui(()->setStatus(message)));loaded=localVlm.isLoaded();
        ui(()->setStatus("모델 준비 완료 · 사진을 선택하세요."));
    }
    private void closePhotoYolo(){if(yolo!=null){try{yolo.close();}catch(Exception ignored){}yolo=null;}}
    private void unload(){localVlm.release();loaded=false;closePhotoYolo();}
    private void analyze(){
        boolean cloud=cloudSelected();
        if(busy||MonitorService.active()||(!cloud&&!loaded&&bundledModels==null)||imageBitmap==null||imageBytes==null)return;
        if(cloud&&!visionKeys().hasKey()){visionSettings();return;}
        String question=prompt.getText().toString().trim();if(question.isEmpty()){setStatus("질문을 입력하세요.");return;}
        String selectedModel=cloudModel(),selectedProvider=visionProvider();
        boolean korean=language.getSelectedItemPosition()==1,read=speak.isChecked(),useYolo=yoloEnabled.isChecked(),cached=imageFromLive;
        final Bitmap snapshot=imageBitmap.copy(Bitmap.Config.ARGB_8888,false);
        if(snapshot==null){setStatus("분석용 사진을 준비하지 못했습니다.");return;}
        List<YoloDetector.Box> observations=new ArrayList<>(displayedBoxes);int revision=imageRevision;
        runJob(()->{try{
            checkCancelled();String unclear=ImageEvidence.sample(snapshot).unclearMessage();if(unclear!=null){ui(()->{result.setText(unclear);setStatus("입력 영상 확인 · 단색/노출 상태로 AI 추론을 생략했습니다.");if(read&&!AssistantService.suppressGuidance(this)){if(cloud)eleven.speak(unclear);else say(unclear);}});return;}if(!cloud&&korean)KoreanTranslator.get(this).requireReady(()->cancelled.get()||destroyed);if(cloud)unload();else prepareModel();checkCancelled();
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();if(!snapshot.compress(Bitmap.CompressFormat.JPEG,90,encoded))throw new IOException("분석용 사진 변환 실패");byte[] jpeg=encoded.toByteArray();
            long start=SystemClock.elapsedRealtime();ui(()->{result.setText("");setStatus(cloud?AiProvider.name(selectedProvider)+" 분석 준비 중...":"사진 분석 중 · CPU 실행");});
            String yoloResult="";
            if(useYolo){
                checkCancelled();List<YoloDetector.Box> boxes;
                if(cached)boxes=observations;
                else{if(yolo==null)yolo=new YoloDetector(this);boxes=yolo.detect(snapshot);}
                yoloResult=YoloDetector.brief(boxes);checkCancelled();
                List<YoloDetector.Box> detectedBoxes=boxes;
                ui(()->{if(!cancelled.get()&&revision==imageRevision){displayedBoxes=detectedBoxes;preview.update(imageBitmap,detectedBoxes);}});
                String detected=yoloResult;ui(()->{if(!cancelled.get())setStatus("YOLO: "+detected+"\n장면 분석 중...");});
            }
            String fullQuestion=question+(useYolo?"\nYOLO auxiliary observations (may be wrong, boxes are normalized image coordinates): "+yoloResult:"");
            String generated=cloud?cloudAnswer(jpeg,fullQuestion,selectedModel,selectedProvider):localAnswer(jpeg,fullQuestion,korean);String value=SceneLanguage.grounded(korean?KoreanTranslator.get(this).translateIfNeeded(generated,()->cancelled.get()||destroyed,message->ui(()->setStatus(message))):generated);checkCancelled();
            long elapsed=SystemClock.elapsedRealtime()-start;ui(()->{if(cancelled.get())return;result.setText(value);setStatus(String.format(Locale.ROOT,"분석 완료 · %.1f초 · %s",elapsed/1000.0,cloud?AiProvider.name(selectedProvider)+" · "+selectedModel:"로컬 실행 · "+localVlm.timings()));if(read&&!AssistantService.suppressGuidance(this)){if(cloud)eleven.speak(value);else say(value);}});
        }finally{snapshot.recycle();}});
    }
    private String localAnswer(byte[] jpeg,String question,boolean korean) throws Exception {
        String modelQuestion=korean?"Describe the clearly visible objects in this picture in one brief factual English sentence. Do not give general advice or invent distance."+(question.startsWith(DEFAULT_KO)?"":"\nUser question: "+question):question;
        return localVlm.describe(jpeg,modelQuestion,()->cancelled.get()||destroyed,message->ui(()->setStatus(message)),partial->ui(()->{if(!cancelled.get()&&(!korean||!KoreanTranslator.needsTranslation(partial)))result.setText(partial);}));
    }
    private String cloudAnswer(byte[] jpeg,String question,String model,String provider) throws Exception {
        String key=AiProvider.keys(this,provider).read();checkCancelled();ui(()->setStatus(AiProvider.name(provider)+"용 인터넷 연결 확인 중..."));
        try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->cancelled.get()||destroyed)){
            checkCancelled();ui(()->setStatus(AiProvider.name(provider)+" 사진 분석 중 · "+internet.description));
            try(CloudRequest pending=CloudAi.vision(provider,model,key,new byte[][]{jpeg},question,false,internet.connector,()->cancelled.get()||destroyed)){
                cloudRequest=pending;checkCancelled();return pending.execute();
            }finally{cloudRequest=null;}
        }
    }
    private void say(String value){
        if(tts==null||AssistantService.suppressGuidance(this))return;Locale locale=value.matches("(?s).*[가-힣].*")?Locale.KOREAN:Locale.ENGLISH;
        Set<Voice> voices=tts.getVoices();Voice selected=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(locale.getLanguage())).findFirst().orElse(null);
        if(selected==null){voiceStatus.setText("이 언어의 오프라인 TTS 음성을 기기 설정에서 준비하세요.");return;}
        voiceStatus.setText("휴대폰 오프라인 음성 안내 중");tts.setVoice(selected);tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"scene");
    }
    private Bitmap decodeImage(InputStream input,int orientation)throws IOException {return PhotoCodec.decode(input,orientation);}
    private void setPicture(Bitmap bitmap) throws IOException {
        checkCancelled();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes))throw new IOException("사진 변환 실패");byte[] jpeg=bytes.toByteArray();
        runOnUiThread(()->{if(destroyed||cancelled.get()){bitmap.recycle();return;}imageBitmap=bitmap;imageBytes=jpeg;imageFromLive=false;imageRevision++;displayedBoxes=Collections.emptyList();preview.update(bitmap,displayedBoxes);setStatus("사진 준비 완료 · "+bitmap.getWidth()+"×"+bitmap.getHeight());});
    }
    private void startAssistant(){
        if(destroyed)return;
        ArrayList<String> missing=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.RECORD_AUDIO);
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(!settings.getBoolean("assistant_actions_asked",false)){
            settings.edit().putBoolean("assistant_actions_asked",true).apply();
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){missing.add(Manifest.permission.ACCESS_FINE_LOCATION);missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
            if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.READ_CONTACTS);
            if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.CALL_PHONE);
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!settings.getBoolean("assistant_notification_asked",false)){missing.add(Manifest.permission.POST_NOTIFICATIONS);settings.edit().putBoolean("assistant_notification_asked",true).apply();}
        if(!missing.isEmpty()){assistantPermissionAction=this::startAssistant;requestPermissions(missing.toArray(new String[0]),ASSISTANT_PERMISSIONS);return;}
        try{MonitorConfig current=monitorConfig();ble.stopScan();AssistantService.start(this,current.bleAddress,current.serviceUuid,current.characteristicUuid);}catch(Exception failure){assistantStatus.setText(failure.getMessage());}
    }
    private void onAssistantState(AssistantService.Snapshot snapshot){
        if(destroyed||assistantStatus==null)return;
        assistantStatus.setText(snapshot.state);assistantHeard.setText(snapshot.heard.isEmpty()?"":"들은 말: "+snapshot.heard);assistantAnswer.setText(snapshot.answer);assistantRoute.setText(snapshot.inputRoute);
        assistantStart.setEnabled(!snapshot.enabled);assistantStop.setEnabled(snapshot.enabled);assistantTalk.setText(snapshot.buttonLabel);assistantTalk.setEnabled(snapshot.enabled&&snapshot.buttonEnabled);assistantAsk.setEnabled(snapshot.enabled);
        assistantProvider.setEnabled(!busy&&!snapshot.busy);assistantLocate.setEnabled(snapshot.enabled&&!busy);assistantMap.setEnabled(!snapshot.mapUrl.isEmpty()&&!busy);assistantLocationText.setText(snapshot.locationText);
        if(snapshot.busy){if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();}
        StringBuilder key=new StringBuilder();for(NearbyPlaces.Place place:snapshot.places)key.append(place.url()).append(place.name);
        if(!assistantPlaceKey.equals(key.toString())){assistantPlaceKey=key.toString();assistantPlaces.removeAllViews();for(NearbyPlaces.Place place:snapshot.places)button(assistantPlaces,place.name+" · 지도 열기",v->openAssistantMap(place.url()));}
        if(snapshot.mapRequested&&observingMonitor){String url=AssistantService.takeMap();if(!url.isEmpty())openAssistantMap(url);}
        updateControls();
    }
    private void openAssistantMap(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException error){assistantStatus.setText("지도를 열 수 있는 앱을 설치해주세요.");}}
    private void onBoardState(BoardService.Snapshot snapshot){if(destroyed||bleStatus==null)return;bleStatus.setText(snapshot.status);if(snapshot.enabled){serviceUuid.setText(snapshot.service);characteristicUuid.setText(snapshot.characteristic);}updateControls();}
    @Override protected void onStart(){super.onStart();observingMonitor=true;MonitorService.observe(monitorObserver);AssistantService.observe(assistantObserver);BoardService.observe(boardObserver);}
    @Override protected void onStop(){observingMonitor=false;MonitorService.forget(monitorObserver);AssistantService.forget(assistantObserver);BoardService.forget(boardObserver);cancelled.set(true);abortCameraRequest();abortCloudRequest();stopLive();if(ble!=null)ble.close();if(tts!=null)tts.stop();if(eleven!=null)eleven.stop();super.onStop();}
    @Override protected void onDestroy(){
        observingMonitor=false;MonitorService.forget(monitorObserver);AssistantService.forget(assistantObserver);BoardService.forget(boardObserver);destroyed=true;cancelled.set(true);if(eleven!=null)eleven.close();if(tts!=null){tts.stop();tts.shutdown();}
        abortCameraRequest();abortCloudRequest();stopLive();if(ble!=null)ble.close();
        jobs.execute(()->{if(!MonitorService.active())unload();else closePhotoYolo();});jobs.shutdown();super.onDestroy();
    }
}
