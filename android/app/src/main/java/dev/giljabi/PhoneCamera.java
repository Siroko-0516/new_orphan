package dev.giljabi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One bounded, latest-image Camera2 pipeline shared by preview and background monitoring. */
final class PhoneCamera implements VideoSource {
    private final Context context;
    private final int source;
    private final Consumer<LiveCamera.Frame> frame;
    private final Consumer<CameraFailure> error;
    private final Consumer<String> progress;
    private final HandlerThread thread=new HandlerThread("phone-camera");
    private final Handler handler;
    private final ExecutorService detector=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(true),processing=new AtomicBoolean();
    private final AtomicLong received=new AtomicLong();
    private final long started=SystemClock.elapsedRealtime();
    private long lastAccepted;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private YoloDetector yolo;
    private int[] pixels;
    private int sensor;
    private boolean front,firstPreview,opening;

    PhoneCamera(Context context,int source,Consumer<LiveCamera.Frame> frame,Consumer<String> progress,Consumer<CameraFailure> error){
        this.context=context.getApplicationContext();this.source=source;this.frame=frame;this.progress=progress;this.error=error;
        thread.start();handler=new Handler(thread.getLooper());handler.post(this::open);
    }
    private void open(){
        if(!running.get())return;
        try{
            if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("휴대폰 카메라 권한을 허용하세요.");
            CameraManager manager=context.getSystemService(CameraManager.class);
            int wanted=source==CameraSource.FRONT?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            String selected=null;CameraCharacteristics info=null;
            for(String id:manager.getCameraIdList()){
                CameraCharacteristics candidate=manager.getCameraCharacteristics(id);
                Integer facing=candidate.get(CameraCharacteristics.LENS_FACING);
                if(facing!=null&&facing==wanted){selected=id;info=candidate;break;}
            }
            if(selected==null)throw new IllegalStateException("선택한 휴대폰 카메라가 없습니다.");
            front=wanted==CameraCharacteristics.LENS_FACING_FRONT;
            Integer angle=info.get(CameraCharacteristics.SENSOR_ORIENTATION);sensor=angle==null?0:angle;
            StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size size=chooseSize(map==null?null:map.getOutputSizes(ImageFormat.YUV_420_888));
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(this::available,handler);
            CameraCharacteristics characteristics=info;
            progress.accept(CameraSource.label(source)+" 준비 중 · "+size.getWidth()+"×"+size.getHeight());
            opening=true;
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice opened){
                    opening=false;if(!running.get()){opened.close();thread.quitSafely();return;}device=opened;configure(characteristics);
                }
                @Override public void onDisconnected(CameraDevice camera){opening=false;camera.close();if(!running.get()){thread.quitSafely();return;}fail(new IOException("휴대폰 카메라 연결이 끊겼습니다."));}
                @Override public void onError(CameraDevice camera,int code){
                    opening=false;camera.close();if(!running.get()){thread.quitSafely();return;}
                    fail(code==ERROR_CAMERA_DISABLED?new SecurityException("기기 설정에서 카메라 사용이 차단됐습니다."):new IOException("휴대폰 카메라 오류 "+code+" · 다른 카메라 앱을 종료하세요."));
                }
            },handler);
        }catch(Exception|OutOfMemoryError|LinkageError failure){opening=false;fail(failure);}
    }
    static Size chooseSize(Size[] sizes){
        if(sizes==null||sizes.length==0)throw new IllegalArgumentException("YUV 카메라 출력을 지원하지 않습니다.");
        Size chosen=null;long score=Long.MAX_VALUE;
        for(Size size:sizes){
            // Bound conversion and inference memory even on cameras advertising huge still-image sizes.
            if(size.getWidth()>1280||size.getHeight()>1280)continue;
            long next=Math.abs((long)size.getWidth()*size.getHeight()-640L*480);
            if(Math.min(size.getWidth(),size.getHeight())<416)next+=1_000_000;
            if(next<score){score=next;chosen=size;}
        }
        if(chosen==null)throw new IllegalArgumentException("1280픽셀 이하 카메라 출력을 지원하지 않습니다.");
        return chosen;
    }
    private void configure(CameraCharacteristics info){
        try{
            device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession configured){
                    if(!running.get()){configured.close();return;}session=configured;
                    try{
                        CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                        int[] af=info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                        if(af!=null)for(int mode:af)if(mode==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE){request.set(CaptureRequest.CONTROL_AF_MODE,mode);break;}
                        Range<Integer>[] ranges=info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                        Range<Integer> fps=null;
                        if(ranges!=null)for(Range<Integer> range:ranges)if(range.getLower()<=15&&range.getUpper()>=15&&(fps==null||range.getUpper()<fps.getUpper()))fps=range;
                        if(fps!=null)request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fps);
                        configured.setRepeatingRequest(request.build(),null,handler);
                    }catch(Exception failure){fail(failure);}
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed){failed.close();fail(new IllegalStateException("휴대폰 카메라 영상 구성을 시작하지 못했습니다."));}
            },handler);
        }catch(Exception failure){fail(failure);}
    }
    private void available(ImageReader input){
        Image image=null;
        try{
            image=input.acquireLatestImage();if(image==null)return;
            long now=SystemClock.elapsedRealtime();received.incrementAndGet();
            if(!running.get()||now-lastAccepted<150||!processing.compareAndSet(false,true))return;
            lastAccepted=now;Image accepted=image;image=null;
            try{detector.execute(()->detect(accepted,now));}catch(RejectedExecutionException ignored){accepted.close();processing.set(false);}
        }catch(Exception failure){if(running.get())fail(failure);}finally{if(image!=null)image.close();}
    }
    private void detect(Image input,long receivedAt){
        Bitmap bitmap=null;
        try{
            if(!running.get())return;
            Rect crop=input.getCropRect();int width=crop.width(),height=crop.height();
            if(pixels==null||pixels.length!=width*height)pixels=new int[width*height];
            Image.Plane[] planes=input.getPlanes();
            Yuv420.convert(planes[0].getBuffer(),planes[0].getRowStride(),planes[0].getPixelStride(),planes[1].getBuffer(),planes[1].getRowStride(),planes[1].getPixelStride(),planes[2].getBuffer(),planes[2].getRowStride(),planes[2].getPixelStride(),crop.left,crop.top,width,height,pixels);
            bitmap=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
            WindowManager windows=context.getSystemService(WindowManager.class);
            int display=windows==null?Surface.ROTATION_0:windows.getDefaultDisplay().getRotation();
            int degrees=display==Surface.ROTATION_90?90:display==Surface.ROTATION_180?180:display==Surface.ROTATION_270?270:0;
            Matrix transform=new Matrix();transform.postRotate(CameraSource.rotation(sensor,degrees,front));if(front)transform.postScale(-1,1);
            Bitmap oriented=Bitmap.createBitmap(bitmap,0,0,width,height,transform,true);if(oriented!=bitmap){bitmap.recycle();bitmap=oriented;}
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,encoded))throw new IOException("휴대폰 영상 JPEG 변환 실패");
            byte[] jpeg=encoded.toByteArray();double fps=received.get()*1000.0/Math.max(1,SystemClock.elapsedRealtime()-started);
            if(!running.get())return;
            if(!firstPreview){firstPreview=true;Bitmap preview=bitmap.copy(Bitmap.Config.ARGB_8888,false);if(preview!=null)frame.accept(new LiveCamera.Frame(jpeg,preview,Collections.emptyList(),fps,-1,receivedAt));}
            if(yolo==null){CrashDiagnostics.checkpoint("휴대폰 YOLO 준비");yolo=new YoloDetector(context);}
            if(!running.get())return;
            long before=SystemClock.elapsedRealtime();List<YoloDetector.Box> boxes=yolo.detect(bitmap);
            if(running.get()){frame.accept(new LiveCamera.Frame(jpeg,bitmap,boxes,fps,SystemClock.elapsedRealtime()-before,receivedAt));bitmap=null;}
        }catch(Exception|OutOfMemoryError|LinkageError failure){fail(failure);}
        finally{input.close();if(bitmap!=null)bitmap.recycle();processing.set(false);}
    }
    private void fail(Throwable failure){
        if(!running.get())return;
        if(failure instanceof CameraAccessException){CameraAccessException cameraError=(CameraAccessException)failure;failure=cameraError.getReason()==CameraAccessException.CAMERA_DISABLED?new SecurityException("기기 설정에서 카메라 사용이 차단됐습니다.",cameraError):new IOException("휴대폰 카메라에 접근하지 못했습니다. 다른 카메라 앱을 종료하세요.",cameraError);}
        CrashDiagnostics.record("휴대폰 영상/YOLO",failure);
        try{error.accept(CameraFailure.from(failure));}finally{close();}
    }
    @Override public void close(){
        if(!running.getAndSet(false))return;
        handler.post(()->{
            if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}
            // Queue after the in-flight detector, so ImageReader cannot invalidate an acquired image.
            detector.execute(()->{if(reader!=null){reader.close();reader=null;}if(yolo!=null){try{yolo.close();}catch(Exception ignored){}yolo=null;}pixels=null;});
            detector.shutdown();if(!opening)thread.quitSafely();
        });
    }
}
