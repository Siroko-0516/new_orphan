package dev.giljabi;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
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
    private static final int PICK_MODEL=10, PICK_VISION=11, PICK_PHOTO=12, TAKE_PHOTO=13, BLE_PERMISSIONS=20, ENABLE_BLUETOOTH=21,NOTIFICATION_PERMISSION=22;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final List<View> controls=new ArrayList<>();
    private TextView status,result,filesLabel;
    private DetectionView preview;
    private EditText prompt,ip;
    private CheckBox speak,yoloEnabled,notifyObjects;
    private YoloDetector yolo;
    private Spinner language;
    private RadioGroup aiModes;
    private RadioButton localMode,cloudMode;
    private TextView providerHelp;
    private LinearLayout localPanel;
    private SharedPreferences settings;
    private ApiKeyStore apiKeys;
    private volatile GeminiClient.Request cloudRequest;
    private static final String DEFAULT_EN="Describe the main visible objects and their positions. Mention visible obstacles. Do not guess distance, motion or collision risk. Use two short sentences in English.";
    private static final String DEFAULT_KO="사진의 주요 물체, 화면상 위치, 눈에 보이는 장애물을 한국어 두 문장으로 설명하세요. 한자와 영어를 섞지 마세요. 거리·움직임·충돌은 추측하지 마세요.";
    private Button analyze,load,release,stop;
    private File modelFile,visionFile;
    private BundledModels bundledModels;
    private String bundledError;
    private byte[] imageBytes;
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
    private Button autoStart,autoStop,autoRetry,cloudSettingsButton;
    private Spinner autoInterval;
    private TextView autoStatus;
    private boolean observingMonitor,monitorWasActive,applyingMonitorState;
    private static final long[] INTERVALS={3000,5000,10000,20000};
    private BleLink ble;
    private final List<BleLink.Device> foundDevices=new ArrayList<>();
    private final ObstacleTracker tracker=new ObstacleTracker();
    private final AtomicBoolean framePosted=new AtomicBoolean();
    private LiveCamera live;
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
        settings=getSharedPreferences("ai-settings",Context.MODE_PRIVATE);apiKeys=new ApiKeyStore(this);
        localVlm=LocalVlm.get(this);modelFile=localVlm.modelFile;visionFile=localVlm.visionFile;bundledModels=localVlm.bundle;bundledError=localVlm.bundleError;
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,28,24,24);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(24,28+insets.getSystemWindowInsetTop(),24,24+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=text(root,"길잡이 · ESP 카메라",22);title.setTextColor(Color.rgb(35,80,55));
        text(root,"장면 설명 AI 선택",18);
        aiModes=new RadioGroup(this);aiModes.setOrientation(RadioGroup.HORIZONTAL);root.addView(aiModes);
        localMode=new RadioButton(this);localMode.setId(View.generateViewId());localMode.setText("로컬 AI · 오프라인");RadioGroup.LayoutParams localLayout=new RadioGroup.LayoutParams(0,-2);localLayout.weight=1;aiModes.addView(localMode,localLayout);controls.add(localMode);
        cloudMode=new RadioButton(this);cloudMode.setId(View.generateViewId());cloudMode.setText("클라우드 AI · Gemini");RadioGroup.LayoutParams cloudLayout=new RadioGroup.LayoutParams(0,-2);cloudLayout.weight=1;aiModes.addView(cloudMode,cloudLayout);controls.add(cloudMode);
        aiModes.check(settings.getBoolean("cloud",false)?cloudMode.getId():localMode.getId());
        providerHelp=text(root,"",14);
        cloudSettingsButton=button(root,"Gemini API 키 · 모델 설정",v->cloudSettings());
        ip=new EditText(this);ip.setSingleLine(true);ip.setText("http://192.168.4.1:81/stream");root.addView(ip);controls.add(ip);
        text(root,"자동 장면 설명 · 백그라운드 실행",18);
        autoInterval=new Spinner(this);autoInterval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"최소 시작 간격 3초","최소 시작 간격 5초","최소 시작 간격 10초","최소 시작 간격 20초"}));autoInterval.setSelection(1);root.addView(autoInterval);controls.add(autoInterval);
        autoStart=button(root,"실시간 AI + 백그라운드 시작",v->startMonitor());
        autoStop=button(root,"실시간 / 백그라운드 중지",v->MonitorService.stop(this));
        autoRetry=button(root,"장면 설명 재시도",v->MonitorService.retry(this));
        autoStatus=text(root,"이전 분석이 끝나면 최신 프레임을 사용합니다. 실행 알림의 중지 버튼으로도 종료할 수 있습니다.",14);
        liveStart=button(root,"ESP 영상 + YOLO 시작",v->startLive());
        liveStop=button(root,"ESP 영상 중지",v->stopLive());
        liveStatus=text(root,"휴대폰을 ESP32-CAM Wi-Fi에 연결하세요. YOLO는 모델 선택 없이 시작할 수 있습니다.",14);
        button(root,"ESP 연결 진단 · 사진/영상 확인",v->diagnoseCamera());
        preview=new DetectionView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
        bleStatus=text(root,"아두이노 · HM-10 BLE 미연결",14);
        bleDevices=new Spinner(this);root.addView(bleDevices);
        ble=new BleLink(this,message->bleStatus.setText(message),devices->{
            Object old=bleDevices.getSelectedItem();String selected=old==null?"":old.toString();
            foundDevices.clear();foundDevices.addAll(devices);
            bleDevices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,foundDevices));
            for(int i=0;i<foundDevices.size();i++)if(foundDevices.get(i).toString().equals(selected))bleDevices.setSelection(i);
        },()->{tracker.reset();if(status!=null)updateControls();});
        bleScan=button(root,"BLE 기기 검색",v->scanBle());
        bleConnect=button(root,"선택한 BLE 기기 연결",v->{Object selected=bleDevices.getSelectedItem();if(selected instanceof BleLink.Device)ble.connect((BleLink.Device)selected,serviceUuid.getText().toString(),characteristicUuid.getText().toString());else bleStatus.setText("기기를 검색하고 HM-10을 선택하세요.");});
        bleDisconnect=button(root,"BLE 연결 해제",v->{ble.disconnect();bleStatus.setText("BLE 연결 해제됨");});
        bleTest=button(root,"아두이노 TEST 신호 보내기",v->{if(MonitorService.active())MonitorService.test(this);else ble.send("TEST\n");});
        notifyObjects=new CheckBox(this);notifyObjects.setText("새 물체 감지 시 아두이노 신호 전송");root.addView(notifyObjects);
        notifyObjects.setOnCheckedChangeListener((v,enabled)->tracker.reset());
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        button(root,"BLE UUID 설정 열기/닫기",v->advanced.setVisibility(advanced.getVisibility()==View.GONE?View.VISIBLE:View.GONE));root.addView(advanced);
        serviceUuid=new EditText(this);serviceUuid.setSingleLine(true);serviceUuid.setHint("Service UUID");serviceUuid.setText(BleLink.DEFAULT_SERVICE);advanced.addView(serviceUuid);
        characteristicUuid=new EditText(this);characteristicUuid.setSingleLine(true);characteristicUuid.setHint("Write characteristic UUID");characteristicUuid.setText(BleLink.DEFAULT_CHARACTERISTIC);advanced.addView(characteristicUuid);
        localPanel=new LinearLayout(this);localPanel.setOrientation(LinearLayout.VERTICAL);root.addView(localPanel);
        text(localPanel,"로컬 AI · MiniCPM-V 4.7 1B",19);
        filesLabel=text(localPanel,"",13);refreshFiles();
        if(!BuildConfig.BUNDLED_AI){
            button(localPanel,"1. 언어 모델 GGUF 선택",v->pick(PICK_MODEL,"*/*"));
            button(localPanel,"2. 비전 mmproj GGUF 선택",v->pick(PICK_VISION,"*/*"));
        }
        load=button(localPanel,BuildConfig.BUNDLED_AI?"내장 AI 준비":"3. 모델 불러오기",v->loadModel());
        release=button(localPanel,"모델 메모리 해제",v->runJob(()->{unload();ui(()->setStatus("모델 메모리를 해제했습니다."));}));
        button(root,"사진 선택",v->pick(PICK_PHOTO,"image/*"));
        button(root,"카메라 촬영",v->takePhoto());
        button(root,"ESP32 사진 한 장 받기",v->{String address=ip.getText().toString().trim();stopLive();runJob(()->{byte[] jpeg=fetchCameraFrame(address);checkCancelled();setPicture(decodeImage(new ByteArrayInputStream(jpeg),1));});});
        language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"English","한국어"}));root.addView(language);controls.add(language);
        prompt=new EditText(this);prompt.setMinLines(3);prompt.setText("Describe the main visible objects and their positions. Mention visible obstacles. Do not guess distance, motion or collision risk. Use two short sentences.");root.addView(prompt);controls.add(prompt);
        language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){if(!MonitorService.active())prompt.setText(position==0?DEFAULT_EN:DEFAULT_KO);}
        });
        yoloEnabled=new CheckBox(this);yoloEnabled.setText("YOLO 보조 감지 사용 (기기 내 실행)");yoloEnabled.setChecked(true);root.addView(yoloEnabled);controls.add(yoloEnabled);
        speak=new CheckBox(this);speak.setText("분석 후 기본 음성으로 읽기");speak.setChecked(true);root.addView(speak);
        button(root,"오프라인 한국어 음성 시험",v->say("길잡이 오프라인 음성 테스트입니다."));
        analyze=button(root,"기기에서 사진 분석",v->analyze());
        stop=new Button(this);stop.setText("중지 요청");stop.setEnabled(false);root.addView(stop);stop.setOnClickListener(v->{cancelled.set(true);abortCameraRequest();abortCloudRequest();if(tts!=null)tts.stop();setStatus("중지 요청됨 · Gemini 연결은 닫고, 로컬 연산은 현재 처리 후 중지합니다.");});
        status=text(root,BuildConfig.BUNDLED_AI?"AI 모델이 앱에 포함돼 있습니다. 사진 분석을 누르면 자동으로 준비합니다.":"경량 빌드 · 장면 설명용 모델을 따로 선택해야 합니다.",14);result=text(root,"",18);result.setTextIsSelectable(true);
        text(root,"YOLO·기본 음성은 기기에서 실행됩니다. Gemini를 선택해 분석하면 사진이 Google로 전송됩니다. 새 물체 알림은 거리나 충돌 위험을 측정하지 않습니다.",12);
        tts=new TextToSpeech(this,code->{if(code!=TextToSpeech.SUCCESS)ui(()->setStatus("기본 TTS 초기화 실패 · 화면 결과를 확인하세요."));});
        language.setSelection(cloudSelected()?1:0);prompt.setText(cloudSelected()?DEFAULT_KO:DEFAULT_EN);
        aiModes.setOnCheckedChangeListener((group,id)->{
            boolean cloud=cloudSelected();settings.edit().putBoolean("cloud",cloud).apply();
            if(applyingMonitorState)return;
            String question=prompt.getText().toString();if(question.equals(DEFAULT_EN)||question.equals(DEFAULT_KO)){language.setSelection(cloud?1:0);prompt.setText(cloud?DEFAULT_KO:DEFAULT_EN);}
            result.setText("");if(tts!=null)tts.stop();setStatus(cloud?"Gemini 선택됨 · 사진과 API 키를 준비하고 분석을 누르세요.":"로컬 AI 선택됨 · 사진을 준비하고 분석을 누르세요.");
            if(MonitorService.active())updateMonitor();else if(cloud)jobs.execute(()->{unload();ui(this::updateControls);});
            updateControls();
        });
        if(cloudSelected())setStatus("Gemini 선택됨 · 사진과 API 키를 준비하고 분석을 누르세요.");
        updateControls();
    }
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,12,0,12);parent.addView(t);return t;}
    private Button button(LinearLayout parent,String title,View.OnClickListener action){Button b=new Button(this);b.setText(title);b.setOnClickListener(action);parent.addView(b);controls.add(b);return b;}
    private void ui(Runnable action){runOnUiThread(()->{if(!destroyed)action.run();});}
    private void setStatus(String value){status.setText(value);}
    private boolean cloudSelected(){return aiModes!=null&&cloudMode!=null&&aiModes.getCheckedRadioButtonId()==cloudMode.getId();}
    private String cloudModel(){return settings.getString("gemini_model",GeminiClient.DEFAULT_MODEL);}
    private void refreshProvider(){
        boolean cloud=cloudSelected();localPanel.setVisibility(cloud?View.GONE:View.VISIBLE);
        String description=cloud?"Gemini · "+cloudModel()+"\n자동 실행 중에는 최신 사진을 반복해서 Google로 전송합니다. 사진 분석 버튼은 한 장만 전송합니다. 인터넷·개인 API 키가 필요하며 사용량 요금이 생길 수 있습니다.\n"+(apiKeys.hasKey()?"개인 API 키 저장됨":"Gemini API 키 · 모델 설정에서 키를 입력하세요."):"로컬 AI · 인터넷 없이 휴대폰에서 분석합니다. 사진을 외부로 전송하지 않습니다. 한국어 설명은 언어가 섞일 수 있습니다.";
        if(!description.contentEquals(providerHelp.getText()))providerHelp.setText(description);
        analyze.setText(cloud?"Gemini로 사진 분석":"기기에서 사진 분석");
    }
    private void cloudSettings(){
        if(busy)return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(32,8,32,8);
        text(panel,apiKeys.hasKey()?"키가 저장돼 있습니다. 새 키를 입력하면 교체하고, 비워두면 기존 키를 유지합니다.":"본인의 Gemini API 키를 입력하세요. 키는 이 휴대폰의 앱 저장소에 암호화해 보관합니다.",14);
        EditText keyInput=new EditText(this);keyInput.setHint("개인 Gemini API 키");keyInput.setSingleLine(true);keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);keyInput.setSaveEnabled(false);keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);panel.addView(keyInput);
        EditText modelInput=new EditText(this);modelInput.setSingleLine(true);modelInput.setHint("Gemini 모델 이름");modelInput.setText(cloudModel());panel.addView(modelInput);
        text(panel,"기본 모델: "+GeminiClient.DEFAULT_MODEL+". API 키를 채팅으로 보내지 말고 이 입력칸에 입력하세요. Gemini 분석에는 인터넷과 사용 가능한 API 할당량이 필요합니다.",13);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Gemini 설정").setView(panel).setPositiveButton("저장",null).setNegativeButton("취소",null)
            .setNeutralButton("저장된 키 삭제",(d,w)->runJob(()->{apiKeys.clear();ui(()->{setStatus("저장된 Gemini API 키를 삭제했습니다.");if(MonitorService.active()&&cloudSelected())updateMonitor();updateControls();});})).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(apiKeys.hasKey());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String key=keyInput.getText().toString().trim(),model=modelInput.getText().toString().trim();
                if(!GeminiClient.validModel(model)){modelInput.setError("gemini-로 시작하는 모델 이름을 입력하세요.");return;}
                if(!key.isEmpty()&&!GeminiClient.validKey(key)){keyInput.setError("공백 없이 API 키 문자열만 입력하세요.");return;}
                if(key.isEmpty()&&!apiKeys.hasKey()){keyInput.setError("개인 Gemini API 키를 입력하세요.");return;}
                keyInput.setText("");dialog.dismiss();
                runJob(()->{if(!key.isEmpty())apiKeys.save(key);settings.edit().putString("gemini_model",model).apply();ui(()->{setStatus("Gemini 설정 저장 완료.");if(MonitorService.active()&&cloudSelected())updateMonitor();updateControls();});});
            });
        });
        dialog.show();
    }
    private void refreshFiles(){
        if(BuildConfig.BUNDLED_AI){filesLabel.setText(bundledError!=null?"내장 AI 패키지 오류: "+bundledError:"AI 모델 내장 · 첫 사용 준비 후 인터넷 없이 분석합니다.");return;}
        filesLabel.setText("언어 모델: "+(modelFile.exists()?modelFile.length()/1048576+"MB":"미선택")+" / 비전: "+(visionFile.exists()?visionFile.length()/1048576+"MB":"미선택"));
    }
    private MonitorConfig monitorConfig(){
        Object selected=bleDevices.getSelectedItem();String address="";
        if(selected instanceof BleLink.Device&&(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED))address=((BleLink.Device)selected).device.getAddress();
        if(address.isEmpty()&&MonitorService.active()&&MonitorService.snapshot().config!=null)address=MonitorService.snapshot().config.bleAddress;
        return new MonitorConfig(ip.getText().toString().trim(),prompt.getText().toString().trim(),cloudModel(),cloudSelected(),speak.isChecked(),yoloEnabled.isChecked(),notifyObjects.isChecked(),INTERVALS[autoInterval.getSelectedItemPosition()],address,serviceUuid.getText().toString().trim(),characteristicUuid.getText().toString().trim());
    }
    private void startMonitor(){
        if(busy||MonitorService.active())return;
        if(cloudSelected()&&!apiKeys.hasKey()){cloudSettings();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!settings.getBoolean("notification_asked",false)){
            settings.edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);return;
        }
        try{
            MonitorConfig config=monitorConfig();config.validate();stopLive();ble.close();if(tts!=null)tts.stop();
            jobs.execute(this::closePhotoYolo);MonitorService.start(this,config);onMonitorState(MonitorService.snapshot());
        }catch(Exception error){setStatus("실시간 시작 실패: "+error.getMessage());updateControls();}
    }
    private void updateMonitor(){try{MonitorService.update(this,monitorConfig());}catch(Exception error){setStatus("설정 적용 실패: "+error.getMessage());}}
    private void onMonitorState(MonitorService.Snapshot snapshot){
        if(destroyed)return;
        if(snapshot.active&&!monitorWasActive&&snapshot.config!=null){
            MonitorConfig c=snapshot.config;applyingMonitorState=true;
            aiModes.check(c.cloud?cloudMode.getId():localMode.getId());ip.setText(c.address);prompt.setText(c.question);speak.setChecked(c.speak);yoloEnabled.setChecked(c.useYolo);notifyObjects.setChecked(c.notify);
            serviceUuid.setText(c.serviceUuid);characteristicUuid.setText(c.characteristicUuid);
            for(int i=0;i<INTERVALS.length;i++)if(INTERVALS[i]==c.intervalMs)autoInterval.setSelection(i);
            applyingMonitorState=false;
        }
        if(snapshot.active||monitorWasActive){autoStatus.setText(snapshot.status);setStatus(snapshot.status);bleStatus.setText(snapshot.bleStatus);if(!snapshot.result.isEmpty())result.setText(snapshot.result);}
        monitorWasActive=snapshot.active;updateControls();
    }
    private void onMonitorFrame(MonitorService.ViewFrame frame){
        if(destroyed||!observingMonitor||!MonitorService.active()){frame.bitmap.recycle();return;}
        Bitmap old=imageBitmap;imageBitmap=frame.bitmap;imageBytes=frame.jpeg;displayedBoxes=frame.boxes;imageFromLive=frame.detectionMs>=0;imageRevision++;
        preview.update(imageBitmap,displayedBoxes);if(old!=null&&old!=imageBitmap)old.recycle();
        liveStatus.setText(String.format(Locale.ROOT,"실시간 입력 %d×%d · 수신 %.1f FPS · YOLO %dms · 물체 %d개",frame.bitmap.getWidth(),frame.bitmap.getHeight(),frame.fps,frame.detectionMs,frame.boxes.size()));
    }
    private void updateControls(){
        MonitorService.Snapshot state=MonitorService.snapshot();boolean active=state.active;loaded=localVlm.isLoaded();
        for(View v:controls)v.setEnabled(!busy&&!active);
        boolean canPrepare=bundledModels!=null||(!BuildConfig.BUNDLED_AI&&modelFile.exists()&&visionFile.exists());
        load.setEnabled(!busy&&!active&&!loaded&&canPrepare);
        release.setEnabled(!busy&&!active&&loaded);analyze.setEnabled(!busy&&!active&&(cloudSelected()||loaded||bundledModels!=null)&&imageBytes!=null);stop.setEnabled(busy);
        liveStart.setEnabled(!busy&&!active&&live==null);liveStop.setEnabled(live!=null);ip.setEnabled(!busy&&!active&&live==null);
        bleDevices.setEnabled(!active&&!busy);serviceUuid.setEnabled(!active&&!busy);characteristicUuid.setEnabled(!active&&!busy);
        bleScan.setEnabled(!active&&!busy);bleConnect.setEnabled(!active&&!busy);bleDisconnect.setEnabled(!active&&!busy);bleTest.setEnabled(active?state.bleReady&&!state.stopping:ble.isReady());
        speak.setEnabled(!active&&!busy);notifyObjects.setEnabled(!active&&!busy);
        localMode.setEnabled(!busy&&!state.stopping);cloudMode.setEnabled(!busy&&!state.stopping);cloudSettingsButton.setEnabled(!busy&&!state.stopping);
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
    private void abortCloudRequest(){GeminiClient.Request pending=cloudRequest;if(pending!=null)new Thread(pending::close,"gemini-request-close").start();}
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
        if(busy||MonitorService.active()||live!=null)return;String address=ip.getText().toString().trim();
        try{
            WifiCamera.Choice choice=WifiCamera.choose(this,address);int generation=++liveGeneration;tracker.reset();
            liveStatus.setText(choice.description+"\nESP 영상 연결 중...");
            live=new LiveCamera(this,address,choice.connector,jpeg->decodeImage(new ByteArrayInputStream(jpeg),1),frame->onLiveFrame(generation,frame),message->ui(()->{if(generation==liveGeneration)liveStatus.setText(choice.description+"\n"+message);}),message->ui(()->{if(generation==liveGeneration){stopLive();liveStatus.setText("영상 중지: "+message);}}));
            updateControls();
        }catch(Exception e){liveStatus.setText(e.getMessage());}
    }
    private void onLiveFrame(int generation,LiveCamera.Frame frame){
        if(destroyed||!framePosted.compareAndSet(false,true)){frame.bitmap.recycle();return;}
        runOnUiThread(()->{
            framePosted.set(false);
            if(destroyed||live==null||generation!=liveGeneration){frame.bitmap.recycle();return;}
            Bitmap old=imageBitmap;imageBitmap=frame.bitmap;imageBytes=frame.jpeg;displayedBoxes=frame.boxes;imageFromLive=frame.detectionMs>=0;imageRevision++;
            preview.update(imageBitmap,displayedBoxes);if(old!=null&&old!=imageBitmap)old.recycle();
            if(frame.detectionMs<0){liveStatus.setText("ESP 첫 사진 수신 확인 · YOLO 불러오는 중...");updateControls();return;}
            YoloDetector.Box event=tracker.update(frame.boxes,SystemClock.elapsedRealtime());
            String signal="";
            if(event!=null&&notifyObjects.isChecked()&&ble.isReady()&&ble.send("OBSTACLE\n"))signal=" · 새 물체 신호 요청: "+YoloDetector.LABELS[event.label];
            liveStatus.setText(String.format(Locale.ROOT,"입력 %d×%d · 수신 %.1f FPS · YOLO %dms · 물체 %d개%s%s",frame.bitmap.getWidth(),frame.bitmap.getHeight(),frame.receiveFps,frame.detectionMs,frame.boxes.size(),signal,frame.bitmap.getWidth()<416?"\n낮은 입력 해상도에서는 작은 물체 인식이 어려울 수 있습니다.":""));
            updateControls();
        });
    }
    private void stopLive(){liveGeneration++;LiveCamera old=live;live=null;if(old!=null)old.close();tracker.reset();if(liveStatus!=null)liveStatus.setText("ESP 영상 중지됨");if(status!=null)updateControls();}
    private boolean hasBlePermissions(){
        if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void scanBle(){
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
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==NOTIFICATION_PERMISSION){startMonitor();return;}if(request==BLE_PERMISSIONS){if(hasBlePermissions())scanBle();else bleStatus.setText("BLE 사용에 필요한 권한이 허용되지 않았습니다.");}}
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
        if(cloud&&!apiKeys.hasKey()){cloudSettings();return;}
        String question=prompt.getText().toString().trim();if(question.isEmpty()){setStatus("질문을 입력하세요.");return;}
        String selectedModel=cloudModel();
        boolean read=speak.isChecked(),useYolo=yoloEnabled.isChecked(),cached=imageFromLive;
        final Bitmap snapshot=imageBitmap.copy(Bitmap.Config.ARGB_8888,false);
        if(snapshot==null){setStatus("분석용 사진을 준비하지 못했습니다.");return;}
        List<YoloDetector.Box> observations=new ArrayList<>(displayedBoxes);int revision=imageRevision;
        runJob(()->{try{
            checkCancelled();if(cloud)unload();else prepareModel();checkCancelled();
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();if(!snapshot.compress(Bitmap.CompressFormat.JPEG,90,encoded))throw new IOException("분석용 사진 변환 실패");byte[] jpeg=encoded.toByteArray();
            long start=SystemClock.elapsedRealtime();ui(()->{result.setText("");setStatus(cloud?"Gemini 분석 준비 중...":"사진 분석 중 · CPU 실행");});
            String yoloResult="";
            if(useYolo){
                checkCancelled();List<YoloDetector.Box> boxes;
                if(cached)boxes=observations;
                else{if(yolo==null)yolo=new YoloDetector(this);boxes=yolo.detect(snapshot);}
                yoloResult=YoloDetector.describe(boxes);checkCancelled();
                List<YoloDetector.Box> detectedBoxes=boxes;
                ui(()->{if(!cancelled.get()&&revision==imageRevision){displayedBoxes=detectedBoxes;preview.update(imageBitmap,detectedBoxes);}});
                String detected=yoloResult;ui(()->{if(!cancelled.get())setStatus("YOLO: "+detected+"\n장면 분석 중...");});
            }
            String fullQuestion=question+(useYolo?"\nYOLO auxiliary observations (may be wrong, boxes are normalized image coordinates): "+yoloResult:"");
            String value=cloud?cloudAnswer(jpeg,fullQuestion,selectedModel):localAnswer(jpeg,fullQuestion);checkCancelled();
            long elapsed=SystemClock.elapsedRealtime()-start;ui(()->{if(cancelled.get())return;result.setText(value);setStatus(String.format(Locale.ROOT,"분석 완료 · %.1f초 · %s",elapsed/1000.0,cloud?"Gemini · "+selectedModel:"로컬 실행"));if(read)say(value);});
        }finally{snapshot.recycle();}});
    }
    private String localAnswer(byte[] jpeg,String question) throws Exception {
        return localVlm.describe(jpeg,question,()->cancelled.get()||destroyed,message->ui(()->setStatus(message)),partial->ui(()->{if(!cancelled.get())result.setText(partial);}));
    }
    private String cloudAnswer(byte[] jpeg,String question,String model) throws Exception {
        String key=apiKeys.read();checkCancelled();ui(()->setStatus("Gemini용 인터넷 연결 확인 중..."));
        try(CloudNetwork.Lease internet=CloudNetwork.connect(this,()->cancelled.get()||destroyed)){
            checkCancelled();ui(()->setStatus("Gemini 사진 분석 중 · "+internet.description));
            try(GeminiClient.Request pending=new GeminiClient.Request(model,key,jpeg,question,internet.connector,()->cancelled.get()||destroyed)){
                cloudRequest=pending;checkCancelled();return pending.execute();
            }finally{cloudRequest=null;}
        }
    }
    private void say(String value){
        if(tts==null)return;Locale locale=value.matches("(?s).*[가-힣].*")?Locale.KOREAN:Locale.ENGLISH;
        Set<Voice> voices=tts.getVoices();Voice selected=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(locale.getLanguage())).findFirst().orElse(null);
        if(selected==null){setStatus("분석 완료 · 이 언어의 오프라인 TTS 음성을 기기 설정에서 준비하세요.");return;}
        tts.setVoice(selected);tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"scene");
    }
    private Bitmap decodeImage(InputStream input,int orientation)throws IOException {return PhotoCodec.decode(input,orientation);}
    private void setPicture(Bitmap bitmap) throws IOException {
        checkCancelled();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes))throw new IOException("사진 변환 실패");byte[] jpeg=bytes.toByteArray();
        runOnUiThread(()->{if(destroyed||cancelled.get()){bitmap.recycle();return;}Bitmap old=imageBitmap;imageBitmap=bitmap;imageBytes=jpeg;imageFromLive=false;imageRevision++;displayedBoxes=Collections.emptyList();preview.update(bitmap,displayedBoxes);if(old!=null&&old!=bitmap)old.recycle();setStatus("사진 준비 완료 · "+bitmap.getWidth()+"×"+bitmap.getHeight());});
    }
    @Override protected void onStart(){super.onStart();observingMonitor=true;MonitorService.observe(monitorObserver);}
    @Override protected void onStop(){observingMonitor=false;MonitorService.forget(monitorObserver);cancelled.set(true);abortCameraRequest();abortCloudRequest();stopLive();if(ble!=null)ble.close();if(tts!=null)tts.stop();super.onStop();}
    @Override protected void onDestroy(){
        observingMonitor=false;MonitorService.forget(monitorObserver);destroyed=true;cancelled.set(true);if(tts!=null){tts.stop();tts.shutdown();}
        abortCameraRequest();abortCloudRequest();stopLive();if(ble!=null)ble.close();
        jobs.execute(()->{if(!MonitorService.active())unload();else closePhotoYolo();});jobs.shutdown();super.onDestroy();
    }
}
