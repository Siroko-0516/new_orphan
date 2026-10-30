package dev.giljabi;

import android.content.Context;
import android.graphics.*;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Same YOLO11n ONNX model and normalized box convention as the web app. CPU only. */
final class YoloDetector implements AutoCloseable {
    static final int SIZE=416;
    private final OrtEnvironment environment=OrtEnvironment.getEnvironment();
    private final OrtSession session;
    YoloDetector(Context context) throws Exception {
        byte[] model;
        try(InputStream in=context.getAssets().open("yolo11n.onnx");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);model=out.toByteArray();
        }
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
            session=environment.createSession(model,options);
        }
    }
    List<Box> detect(Bitmap image) throws Exception {
        float scale=Math.min((float)SIZE/image.getWidth(),(float)SIZE/image.getHeight());
        Bitmap input=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(input);canvas.drawColor(Color.rgb(114,114,114));
        float dx=(SIZE-image.getWidth()*scale)/2,dy=(SIZE-image.getHeight()*scale)/2;
        canvas.drawBitmap(image,null,new RectF(dx,dy,dx+image.getWidth()*scale,dy+image.getHeight()*scale),new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] pixels=new int[SIZE*SIZE];input.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE);input.recycle();
        float[] tensor=new float[3*SIZE*SIZE];int plane=SIZE*SIZE;
        for(int i=0;i<plane;i++){int c=pixels[i];tensor[i]=((c>>16)&255)/255f;tensor[plane+i]=((c>>8)&255)/255f;tensor[2*plane+i]=(c&255)/255f;}
        try(OnnxTensor data=OnnxTensor.createTensor(environment,FloatBuffer.wrap(tensor),new long[]{1,3,SIZE,SIZE});
            OrtSession.Result result=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),data))){
            float[][] output=((float[][][])result.get(0).getValue())[0];
            return decode(output,image.getWidth(),image.getHeight());
        }
    }
    String describe(Bitmap image) throws Exception {return describe(detect(image));}
    static String describe(List<Box> boxes){
        StringBuilder description=new StringBuilder();
        for(Box b:boxes){if(description.length()>0)description.append("; ");description.append(String.format(Locale.ROOT,"%s %.0f%% box=[%.2f,%.2f,%.2f,%.2f]",LABELS[b.label],b.score*100,b.xy[0],b.xy[1],b.xy[2],b.xy[3]));}
        return description.length()==0?"No objects detected (does not mean the scene is safe).":description.toString();
    }
    static final class Box {
        final int label;final float score;final float[] xy;
        Box(int label,float score,float[] xy){this.label=label;this.score=score;this.xy=xy;}
    }
    static List<Box> decode(float[][] output,int width,int height){
        if(output.length!=84)throw new IllegalArgumentException("Unsupported YOLO output");
        int n=output[0].length;float scale=Math.min((float)SIZE/width,(float)SIZE/height),dx=(SIZE-width*scale)/2,dy=(SIZE-height*scale)/2;
        List<Box> candidates=new ArrayList<>(),kept=new ArrayList<>();
        for(int i=0;i<n;i++){
            int label=0;float score=0;for(int k=0;k<80;k++)if(output[k+4][i]>score){score=output[k+4][i];label=k;}
            if(score<.4f)continue;
            float x=output[0][i],y=output[1][i],w=output[2][i],h=output[3][i];
            float[] box={clamp((x-w/2-dx)/scale/width),clamp((y-h/2-dy)/scale/height),clamp((x+w/2-dx)/scale/width),clamp((y+h/2-dy)/scale/height)};
            boolean valid=true;for(float f:box)valid&=Float.isFinite(f);
            if(valid&&box[2]>box[0]&&box[3]>box[1])candidates.add(new Box(label,score,box));
        }
        candidates.sort((a,b)->Float.compare(b.score,a.score));
        for(Box candidate:candidates){boolean duplicate=false;for(Box b:kept)if(b.label==candidate.label&&iou(b.xy,candidate.xy)>.45f){duplicate=true;break;}if(!duplicate)kept.add(candidate);if(kept.size()==20)break;}
        return kept;
    }
    static float clamp(float n){return Math.max(0,Math.min(1,n));}
    static float area(float[] a){return Math.max(0,a[2]-a[0])*Math.max(0,a[3]-a[1]);}
    static float iou(float[] a,float[] b){float intersection=area(new float[]{Math.max(a[0],b[0]),Math.max(a[1],b[1]),Math.min(a[2],b[2]),Math.min(a[3],b[3])});float union=area(a)+area(b)-intersection;return union>0?intersection/union:0;}
    @Override public void close() throws OrtException {session.close();}
    static final String[] LABELS={"person","bicycle","car","motorcycle","airplane","bus","train","truck","boat","traffic light","fire hydrant","stop sign","parking meter","bench","bird","cat","dog","horse","sheep","cow","elephant","bear","zebra","giraffe","backpack","umbrella","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball","kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket","bottle","wine glass","cup","fork","knife","spoon","bowl","banana","apple","sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair","couch","potted plant","bed","dining table","toilet","tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven","toaster","sink","refrigerator","book","clock","vase","scissors","teddy bear","hair drier","toothbrush"};
}
