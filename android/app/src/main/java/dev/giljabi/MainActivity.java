package dev.giljabi;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
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
import androidx.core.content.FileProvider;
import com.example.minicpm_v_demo.LlamaEngine;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int PICK_MODEL=10, PICK_VISION=11, PICK_PHOTO=12, TAKE_PHOTO=13;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final List<View> controls=new ArrayList<>();
    private TextView status,result,filesLabel;
    private ImageView preview;
    private EditText prompt,ip;
    private CheckBox speak,yoloEnabled;
    private YoloDetector yolo;
    private Spinner language;
    private Button analyze,load,release,stop;
    private File modelFile,visionFile;
    private byte[] imageBytes;
    private Bitmap imageBitmap;
    private Uri cameraUri;
    private TextToSpeech tts;
    private LlamaEngine engine;
    private boolean initialized,loaded,busy,destroyed;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        modelFile=new File(getFilesDir(),"model.gguf");visionFile=new File(getFilesDir(),"mmproj.gguf");
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,28,24,24);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(24,28+insets.getSystemWindowInsetTop(),24,24+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=text(root,"길잡이 · 로컬 사진 분석",22);title.setTextColor(Color.rgb(35,80,55));
        text(root,"MiniCPM-V 4.7 1B · CPU 실행\n모델과 비전 파일을 직접 선택하세요. 한국어 표현은 실험적입니다.",14);
        filesLabel=text(root,"",13);refreshFiles();
        button(root,"1. 언어 모델 GGUF 선택",v->pick(PICK_MODEL,"*/*"));
        button(root,"2. 비전 mmproj GGUF 선택",v->pick(PICK_VISION,"*/*"));
        load=button(root,"3. 모델 불러오기",v->loadModel());
        release=button(root,"모델 메모리 해제",v->runJob(()->{unload();ui(()->setStatus("모델 메모리를 해제했습니다."));}));
        button(root,"사진 선택",v->pick(PICK_PHOTO,"image/*"));
        button(root,"카메라 촬영",v->takePhoto());
        ip=new EditText(this);ip.setSingleLine(true);ip.setHint("ESP32 사진 주소 http://192.168.4.1/capture");root.addView(ip);controls.add(ip);
        button(root,"ESP32 사진 한 장 받기",v->{String address=ip.getText().toString().trim();runJob(()->{byte[] jpeg=LocalCamera.fetch(address);checkCancelled();setPicture(decodeImage(new ByteArrayInputStream(jpeg),1));});});
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(700);root.addView(preview);
        language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"English · 설명 품질 확인","한국어 · 실험"}));root.addView(language);controls.add(language);
        prompt=new EditText(this);prompt.setMinLines(3);prompt.setText("Describe the main visible objects and their positions. Mention visible obstacles. Do not guess distance, motion or collision risk. Use two short sentences.");root.addView(prompt);controls.add(prompt);
        language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){prompt.setText(position==0?"Describe the main visible objects and their positions. Mention visible obstacles. Do not guess distance, motion or collision risk. Use two short sentences in English.":"사진의 주요 물체, 화면상 위치, 눈에 보이는 장애물을 한국어 두 문장으로 설명하세요. 한자와 영어를 섞지 마세요. 거리·움직임·충돌은 추측하지 마세요.");}
        });
        yoloEnabled=new CheckBox(this);yoloEnabled.setText("YOLO 보조 감지 사용 (기기 내 실행)");yoloEnabled.setChecked(true);root.addView(yoloEnabled);controls.add(yoloEnabled);
        speak=new CheckBox(this);speak.setText("분석 후 기본 음성으로 읽기");speak.setChecked(true);root.addView(speak);
        button(root,"오프라인 한국어 음성 시험",v->say("길잡이 오프라인 음성 테스트입니다."));
        analyze=button(root,"기기에서 사진 분석",v->analyze());
        stop=new Button(this);stop.setText("중지 요청");stop.setEnabled(false);root.addView(stop);stop.setOnClickListener(v->{cancelled.set(true);if(tts!=null)tts.stop();setStatus("중지 요청됨 · 현재 네이티브 연산이 끝나면 중지합니다.");});
        status=text(root,"사진과 모델을 준비하세요.",14);result=text(root,"",18);result.setTextIsSelectable(true);
        text(root,"사진 분석은 기기에서 실행됩니다. ESP32 연결은 같은 네트워크의 사설 IPv4 주소만 허용합니다. 이 버전은 사진 단위 분석이며 실시간 스트림·Gemini·ElevenLabs는 아직 연결하지 않았습니다. 결과는 틀릴 수 있으며 안전을 보장하지 않습니다.",12);
        tts=new TextToSpeech(this,code->{if(code!=TextToSpeech.SUCCESS)ui(()->setStatus("기본 TTS 초기화 실패 · 화면 결과를 확인하세요."));});
        updateControls();
    }
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,12,0,12);parent.addView(t);return t;}
    private Button button(LinearLayout parent,String title,View.OnClickListener action){Button b=new Button(this);b.setText(title);b.setOnClickListener(action);parent.addView(b);controls.add(b);return b;}
    private void ui(Runnable action){runOnUiThread(()->{if(!destroyed)action.run();});}
    private void setStatus(String value){status.setText(value);}
    private void refreshFiles(){filesLabel.setText("언어 모델: "+(modelFile.exists()?modelFile.length()/1048576+"MB":"미선택")+" / 비전: "+(visionFile.exists()?visionFile.length()/1048576+"MB":"미선택"));}
    private void updateControls(){
        for(View v:controls)v.setEnabled(!busy);
        load.setEnabled(!busy&&!loaded&&modelFile.exists()&&visionFile.exists());
        release.setEnabled(!busy&&loaded);analyze.setEnabled(!busy&&loaded&&imageBytes!=null);stop.setEnabled(busy);
        if(busy)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private interface Task {void run() throws Exception;}
    private void runJob(Task task){
        if(busy)return;busy=true;cancelled.set(false);updateControls();
        jobs.execute(()->{
            try{task.run();}catch(CancellationException ignored){ui(()->setStatus("작업을 중지했습니다."));}
            catch(Throwable e){ui(()->setStatus("실패: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}
            finally{ui(()->{busy=false;refreshFiles();updateControls();});}
        });
    }
    private void checkCancelled(){if(cancelled.get()||destroyed)throw new CancellationException();}
    private void pick(int request,String type){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,request);}
    private void takePhoto(){
        File folder=new File(getCacheDir(),"photos");folder.mkdirs();File photo=new File(folder,"camera.jpg");
        cameraUri=FileProvider.getUriForFile(this,getPackageName()+".photos",photo);
        Intent capture=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);capture.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(capture,TAKE_PHOTO);}catch(Exception e){setStatus("카메라 앱을 열지 못했습니다. 사진 선택을 사용하세요.");}
    }
    @Override protected void onActivityResult(int request,int response,Intent data){
        super.onActivityResult(request,response,data);if(response!=RESULT_OK)return;
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
    private void loadModel(){runJob(()->{
        ui(()->setStatus("모델을 불러오는 중입니다. 기기 메모리가 부족하면 종료될 수 있습니다."));
        if(engine==null)engine=new LlamaEngine();if(!initialized){engine.init(getApplicationInfo().nativeLibraryDir);initialized=true;}
        try{
            require(engine.load(modelFile.getAbsolutePath()),"언어 모델 로딩");checkCancelled();
            require(engine.loadMmproj(visionFile.getAbsolutePath(),1),"비전 모델 로딩");checkCancelled();
            engine.setMinicpmvVersionNative(0);engine.setEnableThinkingNative(false);require(engine.prepare(),"추론 준비");loaded=true;
            ui(()->setStatus("모델 준비 완료 · 사진을 선택하세요."));
        }catch(Throwable e){engine.unload();loaded=false;throw e;}
    });}
    private void require(int code,String action){if(code!=0)throw new IllegalStateException(action+" 실패 ("+code+"). 파일 조합과 엔진 호환성을 확인하세요.");}
    private void unload(){if(loaded&&engine!=null){engine.unload();loaded=false;}if(yolo!=null){try{yolo.close();}catch(Exception ignored){}yolo=null;}}
    private void analyze(){
        String question=prompt.getText().toString().trim();if(question.isEmpty()){setStatus("질문을 입력하세요.");return;}
        boolean read=speak.isChecked(),useYolo=yoloEnabled.isChecked();runJob(()->{
            long start=SystemClock.elapsedRealtime();ui(()->{result.setText("");setStatus("사진 분석 중 · CPU 실행");});
            String yoloResult="";
            if(useYolo){
                if(yolo==null)yolo=new YoloDetector(this);
                checkCancelled();yoloResult=yolo.describe(imageBitmap);checkCancelled();
                String detected=yoloResult;ui(()->{if(!cancelled.get())setStatus("YOLO: "+detected+"\n장면 분석 중...");});
            }
            engine.fullReset();checkCancelled();
            require(engine.processSystemPrompt("Describe only visible evidence. Do not invent objects, motion, distance, or safety guarantees. If uncertain, say so."),"시스템 질문 처리");checkCancelled();
            require(engine.prefillImage(imageBytes,imageBytes.length),"이미지 처리");checkCancelled();
            String fullQuestion=question+(useYolo?"\nYOLO auxiliary observations (may be wrong, boxes are normalized image coordinates): "+yoloResult:"");
            require(engine.processUserPrompt(fullQuestion,192),"질문 처리");
            StringBuilder answer=new StringBuilder();
            for(int i=0;i<384;i++){checkCancelled();String token=engine.generateNextToken();if(token==null)break;answer.append(token);String partial=answer.toString();ui(()->{if(!cancelled.get())result.setText(partial);});}
            checkCancelled();String value=answer.toString().replaceAll("(?s)<think>.*?</think>","").trim();if(value.isEmpty())throw new IOException("빈 답변입니다. 질문 또는 모델 파일을 확인하세요.");
            long elapsed=SystemClock.elapsedRealtime()-start;ui(()->{if(cancelled.get())return;result.setText(value);setStatus(String.format(Locale.ROOT,"분석 완료 · %.1f초 · 로컬 실행",elapsed/1000.0));if(read)say(value);});
        });
    }
    private void say(String value){
        if(tts==null)return;Locale locale=value.matches("(?s).*[가-힣].*")?Locale.KOREAN:Locale.ENGLISH;
        Set<Voice> voices=tts.getVoices();Voice selected=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(locale.getLanguage())).findFirst().orElse(null);
        if(selected==null){setStatus("분석 완료 · 이 언어의 오프라인 TTS 음성을 기기 설정에서 준비하세요.");return;}
        tts.setVoice(selected);tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"scene");
    }
    private Bitmap decodeImage(InputStream input,int orientation) throws IOException {
        if(input==null)throw new IOException("사진을 읽을 수 없습니다.");
        // Bounded encoded data; decode sampled pixels rather than a full camera-resolution bitmap.
        ByteArrayOutputStream encodedStream=new ByteArrayOutputStream();byte[] block=new byte[16384];int size;
        while((size=input.read(block))!=-1){if(encodedStream.size()+size>20*1024*1024)throw new IOException("20MB 이하 사진을 선택하세요.");encodedStream.write(block,0,size);}
        byte[] encoded=encodedStream.toByteArray();
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        if(options.outWidth<=0||options.outHeight<=0||Math.max(options.outWidth,options.outHeight)>30000)throw new IOException("지원하지 않는 사진입니다.");
        options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1536)options.inSampleSize*=2;
        Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);if(decoded==null)throw new IOException("사진 디코딩 실패");
        Matrix matrix=new Matrix();if(orientation==6)matrix.postRotate(90);else if(orientation==3)matrix.postRotate(180);else if(orientation==8)matrix.postRotate(270);else if(orientation==2)matrix.postScale(-1,1);else if(orientation==4)matrix.postScale(1,-1);else if(orientation==5){matrix.postRotate(90);matrix.postScale(-1,1);}else if(orientation==7){matrix.postRotate(270);matrix.postScale(-1,1);}
        Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);if(rotated!=decoded)decoded.recycle();
        float scale=Math.min(1f,768f/Math.max(rotated.getWidth(),rotated.getHeight()));Bitmap resized=Bitmap.createScaledBitmap(rotated,Math.max(1,Math.round(rotated.getWidth()*scale)),Math.max(1,Math.round(rotated.getHeight()*scale)),true);if(resized!=rotated)rotated.recycle();return resized;
    }
    private void setPicture(Bitmap bitmap) throws IOException {
        checkCancelled();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes))throw new IOException("사진 변환 실패");imageBytes=bytes.toByteArray();
        ui(()->{Bitmap old=imageBitmap;imageBitmap=bitmap;preview.setImageBitmap(bitmap);if(old!=null&&old!=bitmap)old.recycle();setStatus("사진 준비 완료 · "+bitmap.getWidth()+"×"+bitmap.getHeight());});
    }
    @Override protected void onStop(){cancelled.set(true);if(tts!=null)tts.stop();super.onStop();}
    @Override protected void onDestroy(){
        destroyed=true;cancelled.set(true);if(tts!=null){tts.stop();tts.shutdown();}
        jobs.execute(()->{unload();if(initialized&&engine!=null)engine.shutdown();});jobs.shutdown();super.onDestroy();
    }
}
