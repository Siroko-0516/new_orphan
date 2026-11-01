import ai.onnxruntime.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
class YoloBufferReuseCheck {
 public static void main(String[] args) throws Exception {
  int n=3*416*416;
  OrtEnvironment env=OrtEnvironment.getEnvironment();
  try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
   options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
   try(OrtSession session=env.createSession(args[0],options)){
    FloatBuffer buffer=ByteBuffer.allocateDirect(n*Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    try(OnnxTensor reused=OnnxTensor.createTensor(env,buffer,new long[]{1,3,416,416})){
     String name=session.getInputNames().iterator().next();float[] input=new float[n];float previous=0;
     for(int frame=0;frame<12;frame++){
      Arrays.fill(input,frame%2==0?0f:1f);buffer.clear();buffer.put(input);buffer.rewind();
      try(OnnxTensor fresh=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,416,416});
          OrtSession.Result a=session.run(Collections.singletonMap(name,reused));
          OrtSession.Result b=session.run(Collections.singletonMap(name,fresh))){
       float[][] aa=((float[][][])a.get(0).getValue())[0],bb=((float[][][])b.get(0).getValue())[0];double diff=0;
       for(int c=0;c<aa.length;c++)for(int i=0;i<aa[c].length;i++)diff=Math.max(diff,Math.abs(aa[c][i]-bb[c][i]));
       if(!(diff<1e-5))throw new AssertionError("reuse changed YOLO output: "+diff);
       if(frame>0&&aa[0][0]==previous)throw new AssertionError("tensor data did not change between frames");
       previous=aa[0][0];System.out.println("frame "+frame+": reusable vs fresh max diff="+diff+", output="+aa.length+"x"+aa[0].length);
      }
     }
    }
   }
  }
 }
}
