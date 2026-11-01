package dev.giljabi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One process-owned, on-device English -> Korean translator shared by UI and monitoring. */
final class KoreanTranslator {
    private static final long TRANSLATE_TIMEOUT_MS=5000;
    private static final Pattern LATIN_WORD=Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");
    private static KoreanTranslator instance;
    static synchronized KoreanTranslator get(Context context){
        if(instance==null){
            Handler main=new Handler(Looper.getMainLooper());
            instance=new KoreanTranslator(new MlKitBackend(),main::post,TRANSLATE_TIMEOUT_MS);
        }
        return instance;
    }

    interface Backend {
        Task<Boolean> ready();
        Task<Void> download();
        Task<String> translate(String text);
    }
    private static final class MlKitBackend implements Backend {
        private final Translator translator=Translation.getClient(new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.KOREAN).build());
        private final TranslateRemoteModel korean=new TranslateRemoteModel.Builder(TranslateLanguage.KOREAN).build();
        @Override public Task<Boolean> ready(){return RemoteModelManager.getInstance().isModelDownloaded(korean);}
        @Override public Task<Void> download(){return translator.downloadModelIfNeeded(new DownloadConditions.Builder().build());}
        @Override public Task<String> translate(String text){return translator.translate(text);}
    }
    private final Backend backend;
    private final Consumer<Runnable> ui;
    private final long timeoutMs;
    private final AtomicBoolean preparing=new AtomicBoolean();
    KoreanTranslator(Backend backend,Consumer<Runnable> ui,long timeoutMs){
        if(timeoutMs<1)throw new IllegalArgumentException("Translation timeout must be positive");
        this.backend=backend;this.ui=ui;this.timeoutMs=timeoutMs;
    }

    /** Only this explicitly invoked method is allowed to download model files. */
    void prepare(Consumer<String> status){
        if(!preparing.compareAndSet(false,true)){
            post(status,"오프라인 한국어 번역 모델을 이미 준비하고 있습니다.");return;
        }
        post(status,"한국어 번역 모델 내려받는 중 · 최초 1회 인터넷이 필요합니다.");
        try{
            backend.download().addOnCompleteListener(Runnable::run,task->{
                preparing.set(false);
                post(status,task.isSuccessful()?"오프라인 한국어 번역 준비 완료 · 이후 문장은 기기에서 번역합니다.":
                    "번역 모델을 준비하지 못했습니다. 인터넷 연결을 확인하고 오프라인 한국어 번역 준비를 다시 누르세요.");
            });
        }catch(RuntimeException failure){
            preparing.set(false);post(status,"번역 모델을 준비하지 못했습니다. 인터넷 연결을 확인하고 다시 시도하세요.");
        }
    }
    private void post(Consumer<String> status,String text){ui.accept(()->status.accept(text));}

    void requireReady(BooleanSupplier cancelled)throws IOException {
        check(cancelled);
        try{if(!Boolean.TRUE.equals(await(backend.ready(),System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs),cancelled)))throw prepareRequired();}
        catch(CancellationException failure){throw failure;}
        catch(IOException failure){throw failure;}
        catch(RuntimeException failure){throw new IOException("오프라인 한국어 번역 준비 상태를 확인하지 못했습니다.",failure);}
    }
    /** Called on the analysis worker; English is never returned as a successful Korean translation. */
    String translateIfNeeded(String text,BooleanSupplier cancelled,Consumer<String> progress)throws IOException {
        check(cancelled);
        if(!needsTranslation(text))return text;
        if(text.length()>2000)throw new IOException("번역할 문장이 너무 깁니다. 안내 문장을 짧게 해주세요.");
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try{
            if(!Boolean.TRUE.equals(await(backend.ready(),deadline,cancelled)))throw prepareRequired();
            check(cancelled);progress.accept("영어 설명을 기기에서 한국어로 번역 중...");
            String result=await(backend.translate(text),deadline,cancelled);
            if(result==null||result.trim().isEmpty()||!hasHangul(result)||needsTranslation(result))
                throw new IOException("한국어 번역 결과를 확인하지 못해 영어 음성 안내를 보류했습니다.");
            return result.trim();
        }catch(CancellationException failure){throw failure;}
        catch(IOException failure){throw failure;}
        catch(RuntimeException failure){throw new IOException("오프라인 한국어 번역을 실행하지 못했습니다. 번역 준비 상태를 확인하세요.",failure);}
    }
    private static IOException prepareRequired(){
        return new IOException("영어 설명을 읽으려면 먼저 ‘오프라인 한국어 번역 준비’를 누르세요. 최초 모델 준비에만 인터넷이 필요합니다.");
    }
    private static <T>T await(Task<T> task,long deadline,BooleanSupplier cancelled)throws IOException {
        while(!task.isComplete()){
            check(cancelled);
            if(System.nanoTime()>=deadline)throw new IOException("한국어 번역 시간이 초과돼 음성 안내를 보류했습니다.");
            try{Thread.sleep(20);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new CancellationException("번역을 중지했습니다.");}
        }
        check(cancelled);
        if(System.nanoTime()>=deadline)throw new IOException("한국어 번역 시간이 초과돼 음성 안내를 보류했습니다.");
        if(!task.isSuccessful())throw new IOException("오프라인 한국어 번역에 실패했습니다. ‘오프라인 한국어 번역 준비’를 다시 실행하세요.");
        return task.getResult();
    }
    private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean())throw new CancellationException("번역을 중지했습니다.");}
    private static boolean hasHangul(String text){
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c>='가'&&c<='힣')return true;}return false;
    }

    static boolean needsTranslation(String text){
        if(text==null)return false;String value=text.trim();
        if(value.isEmpty()||value.startsWith("{")||value.startsWith("[")||value.matches("(?i)https?://\\S+"))return false;
        int latin=0,hangul=0,digits=0,upper=0,words=0;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c>='A'&&c<='Z'){latin++;upper++;}else if(c>='a'&&c<='z')latin++;
            else if(c>='가'&&c<='힣')hangul++;else if(Character.isDigit(c))digits++;
        }
        Matcher matcher=LATIN_WORD.matcher(value);while(matcher.find())words++;
        if(words<2||latin<6)return false;
        // Serial numbers, board labels and IDs are not sentences to translate.
        if(digits>0&&upper>=latin*.8)return false;
        if(hangul==0)return value.indexOf(' ')>=0||value.indexOf('\n')>=0;
        return words>=4&&latin>hangul*3;
    }
}
