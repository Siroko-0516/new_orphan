package dev.giljabi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.*;
import android.media.audiofx.*;
import android.os.*;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Button-armed recording with Silero speech detection and a 512ms pre-roll for interruptions. */
final class AssistantMic implements AutoCloseable {
    interface Listener {void voice(long id);void utterance(long id,byte[] wav);void idle();void error(String message);void route(String route,boolean automaticInterruption);}
    private static final Semaphore INPUT=new Semaphore(1);
    private final Context context;private final Listener listener;private final BooleanSupplier speaking,waiting,autoBarge;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicLong delivery=new AtomicLong();
    private volatile boolean running=true,rearm;private volatile AudioRecord recorder;private long segment;
    AssistantMic(Context c,BooleanSupplier speaking,BooleanSupplier waiting,BooleanSupplier autoBarge,Listener listener){this.context=c.getApplicationContext();this.speaking=speaking;this.waiting=waiting;this.autoBarge=autoBarge;this.listener=listener;}
    void start(){new Thread(this::run,"assistant-microphone").start();}
    void rearm(){delivery.incrementAndGet();rearm=true;}
    private void post(long token,Runnable action){main.post(()->{if(running&&delivery.get()==token)action.run();});}
    @SuppressLint("MissingPermission")
    private void run(){
        boolean locked=false,changedRoute=false,sco=false;int oldMode=AudioManager.MODE_NORMAL;
        AcousticEchoCanceler echo=null;NoiseSuppressor noise=null;OrtSession vad=null;AudioRecord audio=null;
        AudioManager manager=context.getSystemService(AudioManager.class);
        try{
            if(!INPUT.tryAcquire(3,TimeUnit.SECONDS))throw new IOException("마이크가 사용 중입니다. 잠시 후 다시 눌러주세요.");locked=true;if(!running)return;
            oldMode=manager.getMode();manager.setMode(AudioManager.MODE_IN_COMMUNICATION);changedRoute=true;
            if(Build.VERSION.SDK_INT>=31){
                AudioDeviceInfo chosen=null;
                for(AudioDeviceInfo d:manager.getAvailableCommunicationDevices())if(headset(d)){chosen=d;break;}
                if(chosen!=null)manager.setCommunicationDevice(chosen);
            }else if(manager.isBluetoothScoAvailableOffCall()){manager.startBluetoothSco();manager.setBluetoothScoOn(true);sco=true;}
            if(!running)return;
            OrtEnvironment env=OrtEnvironment.getEnvironment();
            try(InputStream input=context.getAssets().open("silero_vad.onnx");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)bytes.write(block,0,n);
                try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);vad=env.createSession(bytes.toByteArray(),options);}
            }
            int buffer=Math.max(8192,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(buffer).build();recorder=audio;
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("마이크를 준비하지 못했습니다.");
            for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_INPUTS))if(headset(d)){audio.setPreferredDevice(d);break;}
            if(AcousticEchoCanceler.isAvailable()){echo=AcousticEchoCanceler.create(audio.getAudioSessionId());if(echo!=null)echo.setEnabled(true);}
            if(NoiseSuppressor.isAvailable()){noise=NoiseSuppressor.create(audio.getAudioSessionId());if(noise!=null)noise.setEnabled(true);}
            audio.startRecording();if(audio.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("마이크 녹음을 시작하지 못했습니다.");
            short[] frame=new short[512];float[] input=new float[576],state=new float[256];ArrayDeque<byte[]> pre=new ArrayDeque<>();ByteArrayOutputStream capture=new ByteArrayOutputStream();VoiceTurnGate gate=new VoiceTurnGate(SystemClock.elapsedRealtime());long began=SystemClock.elapsedRealtime();String lastRoute="";
            while(running){
                int at=0;while(at<512&&running){int n=audio.read(frame,at,512-at,AudioRecord.READ_BLOCKING);if(n<=0){if(!running)return;throw new IOException("마이크 입력이 중단됐습니다.");}at+=n;}
                if(!running)break;
                long frameDelivery=delivery.get();
                boolean hasHeadset=headset(audio.getRoutedDevice());boolean canBarge=autoBarge.getAsBoolean()&&(hasHeadset||echo!=null&&echo.getEnabled());
                String route=hasHeadset?"이어폰 마이크":"휴대폰 마이크";String routeKey=route+canBarge;if(!lastRoute.equals(routeKey)){lastRoute=routeKey;post(delivery.get(),()->listener.route(route,canBarge));}
                long now=SystemClock.elapsedRealtime();if(now-began>180000){post(delivery.get(),listener::idle);break;}
                if(rearm){rearm=false;gate.reset(now);pre.clear();capture.reset();Arrays.fill(input,0);Arrays.fill(state,0);}
                for(int i=0;i<512;i++)input[64+i]=frame[i]/32768f;
                float probability;
                try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,576});OnnxTensor h=OnnxTensor.createTensor(env,FloatBuffer.wrap(state),new long[]{2,1,128});OnnxTensor sr=OnnxTensor.createTensor(env,LongBuffer.wrap(new long[]{16000}),new long[0]);OrtSession.Result result=vad.run(new HashMap<String,OnnxTensor>(){{put("input",x);put("state",h);put("sr",sr);}})){
                    probability=((OnnxTensor)result.get(0)).getFloatBuffer().get(0);((OnnxTensor)result.get(1)).getFloatBuffer().get(state);
                }
                System.arraycopy(input,512,input,0,64);
                byte[] pcm=new byte[1024];for(int i=0;i<512;i++){pcm[i*2]=(byte)frame[i];pcm[i*2+1]=(byte)(frame[i]>>>8);}
                pre.addLast(pcm);while(pre.size()>16)pre.removeFirst();
                if(waiting.getAsBoolean())gate.keepAlive(now);
                VoiceTurnGate.Event event=gate.step(probability,now,speaking.getAsBoolean(),canBarge);
                if(event==VoiceTurnGate.Event.START){capture.reset();for(byte[] chunk:pre)capture.write(chunk,0,chunk.length);long id=++segment;post(frameDelivery,()->listener.voice(id));}
                else if(gate.recording()||event==VoiceTurnGate.Event.END)capture.write(pcm,0,pcm.length);
                if(event==VoiceTurnGate.Event.END){byte[] wav=AssistantWav.encode(capture.toByteArray());capture.reset();long id=segment;post(frameDelivery,()->listener.utterance(id,wav));}
                else if(event==VoiceTurnGate.Event.IDLE){post(frameDelivery,listener::idle);break;}
            }
        }catch(Exception failure){if(running)post(delivery.get(),()->listener.error("마이크 입력을 확인해주세요. 권한·이어폰 연결 또는 다른 앱의 마이크 사용을 확인하세요."));}
        finally{
            if(audio!=null){try{audio.stop();}catch(IllegalStateException ignored){}audio.release();}recorder=null;
            if(echo!=null)echo.release();if(noise!=null)noise.release();if(vad!=null)try{vad.close();}catch(OrtException ignored){}
            if(changedRoute){try{if(Build.VERSION.SDK_INT>=31)manager.clearCommunicationDevice();else if(sco){manager.setBluetoothScoOn(false);manager.stopBluetoothSco();}if(manager.getMode()==AudioManager.MODE_IN_COMMUNICATION)manager.setMode(oldMode);}catch(RuntimeException ignored){}}
            if(locked)INPUT.release();
        }
    }
    private static boolean headset(AudioDeviceInfo d){if(d==null)return false;int t=d.getType();return t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_USB_HEADSET||Build.VERSION.SDK_INT>=31&&t==AudioDeviceInfo.TYPE_BLE_HEADSET;}
    @Override public void close(){delivery.incrementAndGet();running=false;AudioRecord a=recorder;if(a!=null)try{a.stop();}catch(IllegalStateException ignored){}}
}
