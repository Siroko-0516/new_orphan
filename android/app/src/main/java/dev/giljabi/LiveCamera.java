package dev.giljabi;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Reader continually drains MJPEG; detector consumes only the newest pending frame. */
final class LiveCamera implements AutoCloseable {
    interface Decoder {Bitmap decode(byte[] jpeg) throws Exception;}
    static final class Frame {
        final byte[] jpeg;final Bitmap bitmap;final List<YoloDetector.Box> boxes;
        final double receiveFps;final long detectionMs;
        Frame(byte[] jpeg,Bitmap bitmap,List<YoloDetector.Box> boxes,double receiveFps,long detectionMs){this.jpeg=jpeg;this.bitmap=bitmap;this.boxes=boxes;this.receiveFps=receiveFps;this.detectionMs=detectionMs;}
    }
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService detector=Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<byte[]> newest=new AtomicReference<>();
    private final AtomicBoolean running=new AtomicBoolean(true),scheduled=new AtomicBoolean();
    private final AtomicLong received=new AtomicLong();
    private final Context context;private final Decoder decode;private final Consumer<Frame> frame;private final Consumer<String> error;
    private final long started=SystemClock.elapsedRealtime();
    private volatile LocalCamera.Session session;private YoloDetector yolo;
    LiveCamera(Context context,String address,LocalCamera.Connector connector,Decoder decode,Consumer<Frame> frame,Consumer<String> error){
        this.context=context.getApplicationContext();this.decode=decode;this.frame=frame;this.error=error;
        reader.execute(()->{
            try(LocalCamera.Session opened=LocalCamera.open(address,connector)){
                session=opened;if(!opened.multipart)throw new IOException("실시간 감지는 /capture가 아닌 :81/stream 주소를 사용하세요.");
                while(running.get()){
                    byte[] jpeg=opened.reader.next();if(!running.get())break;
                    newest.set(jpeg);received.incrementAndGet();schedule(0);
                }
            }catch(Exception e){fail(e);}finally{session=null;}
        });
    }
    private void schedule(long delay){
        if(!running.get()||!scheduled.compareAndSet(false,true))return;
        try{detector.schedule(this::detect,delay,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){scheduled.set(false);}
    }
    private void detect(){
        Bitmap image=null;
        try{
            if(!running.get())return;byte[] jpeg=newest.getAndSet(null);if(jpeg==null)return;
            if(yolo==null)yolo=new YoloDetector(context);
            if(!running.get())return;image=decode.decode(jpeg);
            long start=SystemClock.elapsedRealtime();List<YoloDetector.Box> boxes=yolo.detect(image);
            long now=SystemClock.elapsedRealtime();
            if(running.get()){
                ByteArrayOutputStream encoded=new ByteArrayOutputStream();
                if(!image.compress(Bitmap.CompressFormat.JPEG,90,encoded))throw new IOException("사진 변환 실패");
                frame.accept(new Frame(encoded.toByteArray(),image,boxes,received.get()*1000.0/Math.max(1,now-started),now-start));image=null;
            }
        }catch(Exception e){fail(e);}finally{
            if(image!=null)image.recycle();scheduled.set(false);
            if(running.get()&&newest.get()!=null)schedule(150);
        }
    }
    private void fail(Exception e){if(running.get())error.accept(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());close();}
    @Override public void close(){
        if(!running.getAndSet(false))return;newest.set(null);
        LocalCamera.Session active=session;if(active!=null)new Thread(active::close,"camera-close").start();
        reader.shutdownNow();
        try{detector.execute(()->{if(yolo!=null){try{yolo.close();}catch(Exception ignored){}yolo=null;}});}catch(RejectedExecutionException ignored){}
        detector.shutdown();
    }
}
