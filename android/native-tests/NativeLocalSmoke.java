import com.example.minicpm_v_demo.LlamaEngine;
import java.nio.file.*;
class NativeLocalSmoke {
 static void require(int code){if(code!=0)throw new AssertionError("Native code="+code);}
 public static void main(String[] args)throws Exception{
  LlamaEngine engine=new LlamaEngine();engine.init(args[0]);require(engine.load(args[1]));require(engine.loadMmproj(args[2],1));engine.setMinicpmvVersionNative(0);engine.setEnableThinkingNative(false);require(engine.prepare());
  try{for(int i=0;i<3;i++){engine.fullReset();byte[] image=Files.readAllBytes(Path.of(args[3]));require(engine.prefillTurn("Describe only visible objects.","Describe this picture in one brief English sentence.",image,image.length,96));StringBuilder answer=new StringBuilder();for(int t=0;t<128;t++){String token=engine.generateNextToken();if(token==null)break;answer.append(token);}if(answer.toString().trim().isEmpty())throw new AssertionError("Empty answer");System.out.println("JNI_IMAGE_TURN="+i+" ANSWER="+answer);}}
  finally{engine.unload();engine.shutdown();}
  System.out.println("JNI_REAL_IMAGE_PASS");
 }
}
