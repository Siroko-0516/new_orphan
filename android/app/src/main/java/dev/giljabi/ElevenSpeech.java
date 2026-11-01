package dev.giljabi;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Cancellable cloud speech with a single, context-checked native-speech fallback. */
final class ElevenSpeech implements AutoCloseable {
    static final long DEFAULT_WAIT_MS=7000;
    private final Context context;
    private final Consumer<String> status,fallback,started;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicLong generation=new AtomicLong();
    private volatile ElevenLabsClient.Request request;
    private volatile boolean closed;
    private Attempt current;
    private MediaPlayer player;private File audioFile;
    private static final class Attempt {
        final long generation;final String text;final SpeechFallbackGate gate;
        Runnable timeout,validate;
        Attempt(long generation,String text,BooleanSupplier valid){this.generation=generation;this.text=text;gate=new SpeechFallbackGate(valid);}
    }
    ElevenSpeech(Context context,Consumer<String> status){this(context,status,null,null);}
    ElevenSpeech(Context context,Consumer<String> status,Consumer<String> fallback){this(context,status,fallback,null);}
    ElevenSpeech(Context context,Consumer<String> status,Consumer<String> fallback,Consumer<String> started){this.context=context.getApplicationContext();this.status=status;this.fallback=fallback;this.started=started;}
    void speak(String text){speak(text,()->true,DEFAULT_WAIT_MS);}
    void speak(String text,BooleanSupplier valid){speak(text,valid,DEFAULT_WAIT_MS);}
    void speak(String text,BooleanSupplier valid,long waitMs){
        if(closed)return;long chosen=generation.incrementAndGet();
        if(Looper.myLooper()!=main.getLooper()){main.post(()->begin(text,valid,waitMs,chosen));return;}
        begin(text,valid,waitMs,chosen);
    }
    private void begin(String text,BooleanSupplier valid,long waitMs,long chosen){
        if(closed||generation.get()!=chosen)return;
        if(current!=null)cancel(current);else{releasePlayer();closeRequest();}
        Attempt attempt=new Attempt(chosen,text,()->!closed&&generation.get()==chosen&&valid!=null&&valid.getAsBoolean());
        current=attempt;if(!attempt.gate.active()){cancel(attempt);return;}
        String voice=context.getSharedPreferences("ai-settings",Context.MODE_PRIVATE).getString("eleven_voice",ElevenLabsClient.FEMALE);
        status.accept("ElevenLabs 음성 생성 중...");
        attempt.timeout=()->fail(attempt,"ElevenLabs 응답이 늦어");
        main.postDelayed(attempt.timeout,Math.max(100,Math.min(20000,waitMs)));
        attempt.validate=new Runnable(){@Override public void run(){
            if(current!=attempt)return;
            if(!attempt.gate.current()){cancel(attempt);return;}
            if(attempt.gate.active())main.postDelayed(this,100);
        }};
        main.postDelayed(attempt.validate,100);
        try{io.execute(()->fetch(attempt,voice));}catch(RejectedExecutionException stopped){cancel(attempt);}
    }
    private void fetch(Attempt attempt,String voice){
        File file=null;
        try{
            if(!attempt.gate.active())return;
            String key=new ApiKeyStore(context,true).read();
            if(!attempt.gate.active())return;
            try(CloudNetwork.Lease internet=CloudNetwork.connect(context,()->!attempt.gate.active())){
                if(!attempt.gate.active())return;
                byte[] audio;
                try(ElevenLabsClient.Request pending=new ElevenLabsClient.Request(attempt.text,key,voice,internet.connector,()->!attempt.gate.active())){
                    request=pending;if(!attempt.gate.active())return;audio=pending.execute();
                }finally{request=null;}
                if(!attempt.gate.active())return;
                if(!Mp3Audio.hasHeader(audio))throw new IOException("Invalid MP3 header");
                file=File.createTempFile("eleven-",".mp3",context.getCacheDir());try(OutputStream output=new FileOutputStream(file)){output.write(audio);}
                if(!attempt.gate.active())return;
                File prepared=file;file=null;main.post(()->play(prepared,attempt));
            }
        }catch(Exception failure){main.post(()->fail(attempt,"ElevenLabs 음성 생성에 실패해"));}
        finally{if(file!=null)file.delete();}
    }
    private void play(File file,Attempt attempt){
        if(current!=attempt||!attempt.gate.active()){file.delete();return;}
        try{
            audioFile=file;player=new MediaPlayer();MediaPlayer active=player;
            active.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            active.setDataSource(file.getAbsolutePath());
            active.setOnPreparedListener(p->{
                if(current!=attempt||player!=p)return;
                if(attempt.gate.hasStarted())return;
                if(!attempt.gate.active()){cancel(attempt);return;}
                try{p.start();}catch(RuntimeException failure){fail(attempt,"ElevenLabs 재생에 실패해");return;}
                if(!attempt.gate.claimStart()){if(!attempt.gate.current())cancel(attempt);return;}
                removeTimeout(attempt);
                // Commit the spoken context after actual playback starts, not after HTTP success.
                if(started!=null)try{started.accept(attempt.text);}catch(RuntimeException ignored){/* Do not replay already-started audio as a fallback. */}
                if(current==attempt&&attempt.gate.current())status.accept("ElevenLabs 음성 안내 중");
            });
            active.setOnCompletionListener(p->{
                if(current!=attempt||player!=p)return;
                boolean completed=attempt.gate.complete();removeCallbacks(attempt);releasePlayer();current=null;
                if(completed)status.accept("ElevenLabs 음성 안내 완료");
            });
            active.setOnErrorListener((p,what,extra)->{if(current==attempt&&player==p)fail(attempt,"ElevenLabs 재생에 실패해");return true;});
            if(!attempt.gate.active()){cancel(attempt);return;}active.prepareAsync();
        }catch(Exception failure){fail(attempt,"ElevenLabs 재생에 실패해");}
    }
    private void fail(Attempt attempt,String reason){
        if(current!=attempt)return;
        if(!attempt.gate.fallback()){if(!attempt.gate.current())cancel(attempt);return;}
        removeCallbacks(attempt);releasePlayer();closeRequest();
        if(!attempt.gate.current())return;
        if(fallback==null){status.accept("ElevenLabs 음성을 재생하지 못했습니다.");return;}
        status.accept(reason+" 휴대폰 기본 음성으로 안내합니다.");
        // A status observer may synchronously stop/change mode, so recheck before fallback.
        if(current!=attempt||!attempt.gate.current())return;
        try{fallback.accept(attempt.text);}catch(RuntimeException failure){if(attempt.gate.current())status.accept("휴대폰 기본 음성을 시작하지 못했습니다.");}
    }
    private void removeTimeout(Attempt attempt){if(attempt.timeout!=null)main.removeCallbacks(attempt.timeout);}
    private void removeCallbacks(Attempt attempt){removeTimeout(attempt);if(attempt.validate!=null)main.removeCallbacks(attempt.validate);}
    private void cancel(Attempt attempt){
        attempt.gate.cancel();removeCallbacks(attempt);
        if(current==attempt){current=null;releasePlayer();closeRequest();}
    }
    private void releasePlayer(){
        if(player!=null){try{player.release();}catch(RuntimeException ignored){}player=null;}
        if(audioFile!=null){audioFile.delete();audioFile=null;}
    }
    private void closeRequest(){ElevenLabsClient.Request active=request;if(active!=null)new Thread(active::close,"eleven-close").start();}
    void stop(){
        long stopped=generation.incrementAndGet();
        if(Looper.myLooper()!=main.getLooper()){main.post(()->stopOnMain(stopped));return;}stopOnMain(stopped);
    }
    private void stopOnMain(long stopped){if(current!=null){if(current.generation<stopped)cancel(current);}else{releasePlayer();closeRequest();}}
    @Override public void close(){closed=true;stop();io.shutdownNow();}
}
