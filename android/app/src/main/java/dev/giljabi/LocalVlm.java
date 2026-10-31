package dev.giljabi;

import android.content.Context;
import android.os.StatFs;
import com.example.minicpm_v_demo.LlamaEngine;
import java.io.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** One process-wide JNI owner. Native model operations never overlap across UI and service. */
final class LocalVlm {
    private static LocalVlm instance;
    static synchronized LocalVlm get(Context context){if(instance==null)instance=new LocalVlm(context.getApplicationContext());return instance;}
    final File modelFile,visionFile;
    final BundledModels bundle;
    final String bundleError;
    private final Context context;
    private LlamaEngine engine;
    private boolean initialized;
    private volatile boolean loaded;
    private LocalVlm(Context context){
        this.context=context;File model=new File(context.getFilesDir(),"model.gguf"),vision=new File(context.getFilesDir(),"mmproj.gguf");BundledModels bundled=null;String error=null;
        if(BuildConfig.BUNDLED_AI)try{
            bundled=new BundledModels(context.getAssets().open("local-ai/manifest.properties"));File directory=bundled.directory(new File(context.getFilesDir(),"bundled-ai"));model=new File(directory,"model.gguf");vision=new File(directory,"mmproj.gguf");
        }catch(IOException failure){error=failure.getMessage();}
        modelFile=model;visionFile=vision;bundle=bundled;bundleError=error;
    }
    boolean isLoaded(){return loaded;}
    private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean())throw new CancellationException();}
    synchronized void prepare(BooleanSupplier cancelled,Consumer<String> progress)throws Exception {
        check(cancelled);if(loaded)return;
        if(BuildConfig.BUNDLED_AI){
            if(bundle==null)throw new IOException("내장 AI 패키지 오류: "+bundleError);
            progress.accept("내장 AI 준비 중 · 첫 사용 시 시간이 걸립니다.");
            bundle.install(path->context.getAssets().open(path),new File(context.getFilesDir(),"bundled-ai"),()->new StatFs(context.getFilesDir().getPath()).getAvailableBytes(),
                (done,total)->progress.accept("내장 AI 준비 중 · "+done/1048576+" / "+total/1048576+"MB"),()->check(cancelled));check(cancelled);
        }
        if(!modelFile.isFile()||!visionFile.isFile())throw new IOException("언어 모델과 비전 모델을 먼저 준비하세요.");
        progress.accept("로컬 모델 불러오는 중...");
        if(engine==null)engine=new LlamaEngine();if(!initialized){engine.init(context.getApplicationInfo().nativeLibraryDir);initialized=true;}
        try{
            require(engine.load(modelFile.getAbsolutePath()),"언어 모델 로딩");check(cancelled);
            require(engine.loadMmproj(visionFile.getAbsolutePath(),1),"비전 모델 로딩");check(cancelled);
            engine.setMinicpmvVersionNative(0);engine.setEnableThinkingNative(false);require(engine.prepare(),"추론 준비");loaded=true;
        }catch(Throwable failure){engine.unload();loaded=false;throw failure;}
    }
    synchronized String describe(byte[] jpeg,String question,BooleanSupplier cancelled,Consumer<String> progress,Consumer<String> partial)throws Exception {
        prepare(cancelled,progress);check(cancelled);engine.fullReset();check(cancelled);
        require(engine.processSystemPrompt("Describe only visible evidence. Do not invent objects, motion, distance, or safety guarantees. If uncertain, say so."),"시스템 질문 처리");check(cancelled);
        require(engine.prefillImage(jpeg,jpeg.length),"이미지 처리");check(cancelled);require(engine.processUserPrompt(question,192),"질문 처리");
        StringBuilder answer=new StringBuilder();
        for(int i=0;i<384;i++){check(cancelled);String token=engine.generateNextToken();if(token==null)break;answer.append(token);partial.accept(answer.toString());}
        check(cancelled);String value=answer.toString().replaceAll("(?s)<think>.*?</think>","").trim();if(value.isEmpty())throw new IOException("빈 답변입니다. 질문 또는 모델 파일을 확인하세요.");return value;
    }
    synchronized void release(){if(engine!=null&&loaded){engine.unload();loaded=false;}}
    private void require(int code,String action)throws IOException {if(code!=0)throw new IOException(action+" 실패 ("+code+"). 모델 조합과 메모리를 확인하세요.");}
}
