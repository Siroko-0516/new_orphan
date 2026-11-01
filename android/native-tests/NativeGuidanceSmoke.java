package dev.giljabi;

import com.example.minicpm_v_demo.LlamaEngine;
import java.nio.file.*;
import java.util.Locale;

/** Public fixture only. Validates structured output, not scene accuracy or phone speed. */
public final class NativeGuidanceSmoke {
    private static void require(int status,String phase){if(status!=0)throw new AssertionError(phase+" native status="+status);}
    public static void main(String[] args)throws Exception {
        if(args.length!=4&&args.length!=5)throw new IllegalArgumentException("native-dir model.gguf mmproj.gguf public-fixture.jpg");
        long start=System.nanoTime();LlamaEngine engine=new LlamaEngine();engine.init(args[0]);engine.setVisionGpuEnabled(false);
        try{
            require(engine.load(args[1]),"model");require(engine.loadMmproj(args[2],1),"vision");engine.setMinicpmvVersionNative(0);engine.setEnableThinkingNative(false);require(engine.prepare(),"prepare");
            byte[] jpeg=Files.readAllBytes(Path.of(args[3]));engine.beginTurn(true);
            String question=args.length==5?Files.readString(Path.of(args[4])):WalkingGuidance.localPrompt("none");
            long prepared=System.nanoTime();
            require(engine.prefillTurn("Describe only clearly visible evidence. Follow the requested JSON format and language. Never invent distance, height or safety guarantees.",question+"\nReturn only the requested JSON.",jpeg,jpeg.length,128),"prefill");
            long prefilled=System.nanoTime();StringBuilder answer=new StringBuilder();
            for(int i=0;i<128;i++){String piece=engine.generateNextToken();if(piece==null)break;answer.append(piece);if(WalkingGuidance.completeJson(answer.toString()))break;}
            String raw=answer.toString().trim();WalkingGuidance.Reply reply=WalkingGuidance.parse(raw);
            System.out.println("NATIVE_GUIDANCE_REPLY="+raw.replace("\r"," ").replace("\n"," "));
            System.out.printf(Locale.ROOT,"HOST_SECONDS prepare=%.3f image=%.3f generation=%.3f%n",(prepared-start)/1e9,(prefilled-prepared)/1e9,(System.nanoTime()-prefilled)/1e9);
            engine.beginTurn(false);long captionStart=System.nanoTime();
            require(engine.prefillTurn("Describe only clearly visible objects. Do not invent distance or safety guarantees.","Describe this picture in one brief English sentence.",jpeg,jpeg.length,64),"English prefill");
            StringBuilder caption=new StringBuilder();for(int i=0;i<64;i++){String piece=engine.generateNextToken();if(piece==null)break;caption.append(piece);}
            String english=caption.toString().replaceAll("\\s+"," ").trim();
            if(english.isEmpty())throw new AssertionError("Production JNI returned no English caption");
            System.out.println("NATIVE_ENGLISH_CAPTION="+english);
            System.out.printf(Locale.ROOT,"HOST_CAPTION_SECONDS=%.3f%n",(System.nanoTime()-captionStart)/1e9);
            if(!reply.valid)throw new AssertionError("Actual MiniCPM reply did not satisfy the typed guidance protocol");
            boolean useful=reply.speak&&WalkingGuidance.localUseful(reply.message,"no_confirmed_path_cues");if(useful)throw new AssertionError("Public landscape fixture must not become a walking obstacle warning");System.out.println("NATIVE_GUIDANCE_JSON_PASS speak="+useful+" urgent="+reply.urgent);
        }finally{engine.unload();engine.shutdown();}
    }
}
