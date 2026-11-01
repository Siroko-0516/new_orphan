package dev.giljabi;

import android.content.Context;
import android.os.StatFs;
import android.os.SystemClock;
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
    private final android.content.SharedPreferences settings;
    private boolean visionGpu;
    boolean gpuAuto(){return settings.getBoolean("local_gpu_auto",true);}
    boolean gpuRecovery(){return settings.getBoolean("vision_gpu_recovery",false);}
    synchronized void setGpuAuto(boolean enabled){release();settings.edit().putBoolean("local_gpu_auto",enabled).putBoolean("vision_gpu_recovery",false).putBoolean("vision_gpu_in_progress",false).commit();}
    private void gpuWork(boolean busy){settings.edit().putBoolean("vision_gpu_in_progress",busy).commit();}
    private LlamaEngine engine;
    private boolean initialized;
    private volatile boolean loaded;
    private volatile String timings="";
    String timings(){return timings;}
    private LocalVlm(Context context){
        this.context=context;settings=context.getSharedPreferences("ai-settings",Context.MODE_PRIVATE);if(settings.getBoolean("vision_gpu_in_progress",false))settings.edit().putBoolean("vision_gpu_recovery",true).putBoolean("vision_gpu_in_progress",false).commit();File model=new File(context.getFilesDir(),"model.gguf"),vision=new File(context.getFilesDir(),"mmproj.gguf");BundledModels bundled=null;String error=null;
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
            progress.accept("로컬 언어 모델 불러오는 중...");require(engine.load(modelFile.getAbsolutePath()),"언어 모델 로딩");check(cancelled);
            boolean tryGpu=gpuAuto()&&!gpuRecovery()&&android.os.Build.VERSION.SDK_INT>=28;
            if(tryGpu){gpuWork(true);try{progress.accept("이미지 GPU 가속 지원 확인 중...");tryGpu=engine.hasVisionGpu();}catch(Exception error){tryGpu=false;settings.edit().putBoolean("vision_gpu_recovery",true).commit();}finally{gpuWork(false);}}
            engine.setVisionGpuEnabled(tryGpu);
            progress.accept(tryGpu?"로컬 비전 모델 GPU 준비 중 · 첫 준비는 시간이 걸릴 수 있습니다.":"로컬 비전 모델 CPU 준비 중...");
            gpuWork(tryGpu);
            try{require(engine.loadMmproj(visionFile.getAbsolutePath(),1),"비전 모델 로딩");}
            catch(Exception failure){if(!tryGpu)throw failure;check(cancelled);engine.setVisionGpuEnabled(false);settings.edit().putBoolean("vision_gpu_recovery",true).commit();progress.accept("GPU 준비에 실패해 CPU로 다시 준비합니다.");require(engine.loadMmproj(visionFile.getAbsolutePath(),1),"CPU 비전 모델 로딩");}
            finally{gpuWork(false);}
            visionGpu=engine.isVisionGpuEnabled();check(cancelled);
            progress.accept("로컬 AI 연산 준비 중...");engine.setMinicpmvVersionNative(0);engine.setEnableThinkingNative(false);require(engine.prepare(),"추론 준비");loaded=true;CrashDiagnostics.checkpoint("로컬 AI CPU 기능: "+engine.systemInfo());
        }catch(Throwable failure){engine.unload();loaded=false;throw failure;}
    }
    synchronized String describe(byte[] jpeg,String question,BooleanSupplier cancelled,Consumer<String> progress,Consumer<String> partial)throws Exception {
        return infer(jpeg,question,cancelled,progress,partial,false);
    }
    synchronized String describeGuidance(byte[] jpeg,String question,BooleanSupplier cancelled,Consumer<String> progress)throws Exception {
        return infer(jpeg,question,cancelled,progress,text->{},true);
    }
    private String infer(byte[] jpeg,String question,BooleanSupplier cancelled,Consumer<String> progress,Consumer<String> partial,boolean guidance)throws Exception {
        for(int attempt=0;attempt<2;attempt++)try{
            long started=SystemClock.elapsedRealtime();prepare(cancelled,progress);check(cancelled);long prepared=SystemClock.elapsedRealtime();engine.beginTurn(guidance);check(cancelled);
            progress.accept(visionGpu?"로컬 AI 이미지 분석 중 · Vulkan GPU":"로컬 AI 이미지 분석 중 · CPU");
            gpuWork(visionGpu);
            try{require(engine.prefillTurn(guidance?"Describe only clearly visible evidence. Follow the requested JSON format and language. Never invent distance, height or safety guarantees.":"Describe only clearly visible evidence in the requested language. Never invent distance or safety guarantees.",question+(guidance?"\nReturn only the requested JSON.":"\nUse at most two short sentences in the requested language."),jpeg,jpeg.length,guidance?128:96),"이미지·질문 처리");}
            finally{gpuWork(false);}
            check(cancelled);long prefilled=SystemClock.elapsedRealtime();progress.accept("로컬 AI 설명 생성 중...");
            StringBuilder answer=new StringBuilder();
            for(int i=0;i<(guidance?128:96);i++){
                check(cancelled);String token=engine.generateNextToken();if(token==null)break;answer.append(token);String visible=LocalReply.clean(answer.toString());
                if(guidance){if(WalkingGuidance.completeJson(visible))break;}
                else{if(!visible.isEmpty())partial.accept(visible);if(LocalReply.complete(visible))break;}
            }
            check(cancelled);String value=LocalReply.clean(answer.toString());timings=String.format(java.util.Locale.ROOT,"%s · 준비 %.1f초 · 이미지 %.1f초 · 생성 %.1f초",visionGpu?"GPU":"CPU",(prepared-started)/1000.0,(prefilled-prepared)/1000.0,(SystemClock.elapsedRealtime()-prefilled)/1000.0);
            if(value.isEmpty())throw new IOException("빈 답변입니다. 질문 또는 모델 파일을 확인하세요.");return value;
        }catch(Exception failure){
            check(cancelled);
            if(attempt==0&&visionGpu){settings.edit().putBoolean("vision_gpu_recovery",true).commit();release();progress.accept("GPU 연산을 완료하지 못해 CPU로 다시 처리합니다.");}
            else throw failure;
        }
        throw new IOException("로컬 이미지 분석을 완료하지 못했습니다.");
    }
    synchronized void release(){if(engine!=null&&loaded){engine.unload();loaded=false;}visionGpu=false;}
    private void require(int code,String action)throws IOException {if(code!=0)throw new IOException(action+" 실패 ("+code+"). 모델 조합과 메모리를 확인하세요.");}
}
