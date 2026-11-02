package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Lists model IDs from Google; no guessed model replacement or automatic quota retries. */
final class GeminiModels {
    static List<String> parse(String json)throws IOException{
        try{JSONArray models=new JSONObject(json).getJSONArray("models");ArrayList<String> ids=new ArrayList<>();
            for(int i=0;i<models.length();i++){JSONObject m=models.optJSONObject(i);if(m==null)continue;String name=m.optString("name","");if(!name.startsWith("models/"))continue;String id=name.substring(7);if(!GeminiClient.validModel(id)||id.matches(".*(?:tts|image|transcribe|robotics|computer-use|customtools|live|audio).*"))continue;
                JSONArray methods=m.optJSONArray("supportedGenerationMethods");boolean generate=false;if(methods!=null)for(int j=0;j<methods.length();j++)if("generateContent".equals(methods.optString(j)))generate=true;if(generate&&!ids.contains(id))ids.add(id);
            }
            ids.sort(Comparator.comparingInt(GeminiModels::rank).thenComparing(Comparator.naturalOrder()));return ids;
        }catch(JSONException e){throw new IOException("Gemini 모델 목록을 읽지 못했습니다.",e);}
    }
    private static int rank(String id){return id.equals(GeminiClient.DEFAULT_MODEL)?0:id.contains("flash")&&id.contains("lite")?1:id.contains("flash")?2:3;}
    static final class Request implements AutoCloseable{
        private final HttpURLConnection connection;private final BooleanSupplier cancelled;private volatile boolean closed;
        Request(String key,GeminiClient.Connector connector,BooleanSupplier cancelled)throws IOException{
            if(!GeminiClient.validKey(key))throw new IOException("Gemini API 키를 먼저 입력하거나 저장하세요.");this.cancelled=cancelled;
            connection=connector.open(new URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"));connection.setRequestMethod("GET");connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("x-goog-api-key",key);
        }
        List<String> execute()throws IOException{
            try{check();int code=connection.getResponseCode();check();if(code!=200){byte[] error=new byte[0];try(InputStream in=connection.getErrorStream()){if(in!=null)error=GeminiClient.readError(in);}throw new GeminiClient.ApiException(code,error);}
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){check();if(out.size()+n>GeminiClient.MAX_RESPONSE)throw new IOException("모델 목록이 너무 큽니다.");out.write(bytes,0,n);}}check();List<String> result=parse(out.toString("UTF-8"));if(result.isEmpty())throw new IOException("사진 분석에 선택할 Gemini 모델을 찾지 못했습니다.");return result;
            }finally{close();}
        }
        private void check()throws IOException{if(closed||cancelled.getAsBoolean())throw new IOException("모델 목록 확인을 중지했습니다.");}
        @Override public void close(){closed=true;connection.disconnect();}
    }
}
