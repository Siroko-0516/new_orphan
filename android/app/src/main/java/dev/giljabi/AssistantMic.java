package dev.giljabi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.*;
import android.media.audiofx.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records until the next button; no VAD, silence timeout or speech interruption. */
final class AssistantMic implements AssistantInput {
    private static final Semaphore INPUT=new Semaphore(1);
    private final Context context;private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean started=new AtomicBoolean();
    private volatile boolean cancelled,finishRequested;
    private volatile AudioRecord recorder;
    AssistantMic(Context context,Listener listener){this.context=context.getApplicationContext();this.listener=listener;}
    @Override public void start(){if(started.compareAndSet(false,true))new Thread(this::run,"assistant-button-recording").start();}
    @Override public void finish(){finishRequested=true;stopRecorder();}
    private void post(Runnable action){main.post(()->{if(!cancelled)action.run();});}
    @SuppressLint("MissingPermission")
    private void run(){
        boolean locked=false,limit=false;
        AcousticEchoCanceler echo=null;NoiseSuppressor noise=null;AudioRecord audio=null;
        byte[] pcm=null;String failure=null;
        try{
            if(!INPUT.tryAcquire(3,TimeUnit.SECONDS))throw new IOException("마이크가 사용 중입니다. 잠시 후 다시 눌러주세요.");
            locked=true;if(cancelled)return;
            AudioManager manager=context.getSystemService(AudioManager.class);
            int minimum=AudioRecord.getMinBufferSize(AssistantWav.SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("마이크 녹음 형식을 준비하지 못했어요.");
            audio=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(AssistantWav.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(8192,minimum)).build();recorder=audio;
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("마이크를 준비하지 못했어요.");
            for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_INPUTS))if(headset(d)){audio.setPreferredDevice(d);break;}
            if(AcousticEchoCanceler.isAvailable()){echo=AcousticEchoCanceler.create(audio.getAudioSessionId());if(echo!=null)echo.setEnabled(true);}
            if(NoiseSuppressor.isAvailable()){noise=NoiseSuppressor.create(audio.getAudioSessionId());if(noise!=null)noise.setEnabled(true);}
            ByteArrayOutputStream capture=new ByteArrayOutputStream();
            if(!cancelled&&!finishRequested){
                audio.startRecording();
                if(audio.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("마이크 녹음을 시작하지 못했어요.");
                String route=headset(audio.getRoutedDevice())?"이어폰 마이크":"휴대폰 마이크";
                post(()->listener.ready(route));
                short[] frame=new short[512];byte[] bytes=new byte[1024];
                while(!cancelled&&!finishRequested&&capture.size()<AssistantWav.MAX_PCM){
                    int n=audio.read(frame,0,frame.length,AudioRecord.READ_BLOCKING);
                    if(n<=0){if(cancelled||finishRequested)break;throw new IOException("마이크 입력이 중단됐어요.");}
                    int samples=Math.min(n,(AssistantWav.MAX_PCM-capture.size())/2);
                    for(int i=0;i<samples;i++){bytes[i*2]=(byte)frame[i];bytes[i*2+1]=(byte)(frame[i]>>>8);}
                    capture.write(bytes,0,samples*2);
                }
            }
            if(cancelled)return;
            limit=capture.size()>=AssistantWav.MAX_PCM;
            pcm=capture.toByteArray();
            if(pcm.length<AssistantWav.SAMPLE_RATE*2/5)failure="녹음이 너무 짧아요. 버튼을 눌러 다시 녹음해주세요.";
        }catch(Exception error){if(!cancelled)failure=error instanceof SecurityException?"마이크 권한을 허용해주세요.":"마이크 입력을 확인해주세요. 이어폰 연결 또는 다른 앱의 마이크 사용을 확인하세요.";}
        finally{
            if(audio!=null){try{audio.stop();}catch(IllegalStateException ignored){}audio.release();}recorder=null;
            if(echo!=null)echo.release();if(noise!=null)noise.release();
            if(locked)INPUT.release();
        }
        // Release AudioRecord before posting: a new button turn can start immediately.
        if(cancelled)return;
        if(failure!=null){String message=failure;post(()->listener.error(message));}
        else if(pcm!=null){byte[] wav=AssistantWav.encode(pcm);boolean reachedLimit=limit;post(()->listener.finished(wav,reachedLimit));}
    }
    private void stopRecorder(){AudioRecord a=recorder;if(a!=null)try{a.stop();}catch(IllegalStateException ignored){}}
    private static boolean headset(AudioDeviceInfo d){if(d==null)return false;int t=d.getType();return t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_USB_HEADSET||Build.VERSION.SDK_INT>=31&&t==AudioDeviceInfo.TYPE_BLE_HEADSET;}
    @Override public void close(){cancelled=true;finishRequested=true;stopRecorder();}
}
