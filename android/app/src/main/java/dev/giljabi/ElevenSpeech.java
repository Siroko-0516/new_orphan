package dev.giljabi;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Cancellable speech owner. Cloud credentials remain on this phone and audio is temporary. */
final class ElevenSpeech implements AutoCloseable {
    private final Context context;
    private final Consumer<String> status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicLong generation=new AtomicLong();
    private volatile ElevenLabsClient.Request request;
    private MediaPlayer player;private File audioFile;
    ElevenSpeech(Context context,Consumer<String> status){this.context=context.getApplicationContext();this.status=status;}
    void speak(String text){
        stop();long chosen=generation.get();
        String voice=context.getSharedPreferences("ai-settings",Context.MODE_PRIVATE).getString("eleven_voice",ElevenLabsClient.FEMALE);
        status.accept("ElevenLabs 음성 생성 중...");
        io.execute(()->{
            File file=null;
            try{
                String key=new ApiKeyStore(context,true).read();
                try(CloudNetwork.Lease internet=CloudNetwork.connect(context,()->generation.get()!=chosen)){
                    if(generation.get()!=chosen)return;
                    byte[] audio;
                    try(ElevenLabsClient.Request pending=new ElevenLabsClient.Request(text,key,voice,internet.connector,()->generation.get()!=chosen)){
                        request=pending;if(generation.get()!=chosen)return;audio=pending.execute();
                    }finally{request=null;}
                    if(generation.get()!=chosen)return;
                    file=File.createTempFile("eleven-",".mp3",context.getCacheDir());try(OutputStream output=new FileOutputStream(file)){output.write(audio);}
                    File prepared=file;file=null;main.post(()->play(prepared,chosen));
                }
            }catch(Exception failure){main.post(()->{if(generation.get()==chosen)status.accept("ElevenLabs 음성 오류: "+failure.getMessage());});}
            finally{if(file!=null)file.delete();}
        });
    }
    private void play(File file,long chosen){
        if(generation.get()!=chosen){file.delete();return;}
        try{
            audioFile=file;player=new MediaPlayer();MediaPlayer active=player;
            active.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            active.setDataSource(file.getAbsolutePath());
            active.setOnPreparedListener(p->{if(generation.get()==chosen&&player==p){p.start();status.accept("ElevenLabs 음성 안내 중");}});
            active.setOnCompletionListener(p->{if(player==p){releasePlayer();status.accept("ElevenLabs 음성 안내 완료");}});
            active.setOnErrorListener((p,what,extra)->{if(player==p){releasePlayer();status.accept("ElevenLabs 오디오 재생 실패 ("+what+")");}return true;});
            active.prepareAsync();
        }catch(Exception failure){releasePlayer();status.accept("ElevenLabs 오디오 재생 실패: "+failure.getMessage());}
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}if(audioFile!=null){audioFile.delete();audioFile=null;}}
    void stop(){generation.incrementAndGet();ElevenLabsClient.Request active=request;if(active!=null)new Thread(active::close,"eleven-close").start();releasePlayer();}
    @Override public void close(){stop();io.shutdownNow();}
}
