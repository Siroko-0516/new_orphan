package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Fixed Google HTTPS endpoint; keys are headers, and one explicit analysis sends one photo. */
final class GeminiClient {
    static final String DEFAULT_MODEL="gemini-3.1-flash-lite";
    static final int MAX_IMAGE=2*1024*1024,MAX_RESPONSE=1024*1024;
    interface Connector {HttpURLConnection open(URL url) throws IOException;}
    static final class ApiException extends IOException {
        final int status;
        ApiException(int status){super(httpError(status));this.status=status;}
    }
    static boolean validModel(String model){return model!=null&&model.matches("gemini-[A-Za-z0-9._-]{1,100}");}
    static boolean validKey(String key){return key!=null&&key.matches("[!-~]{10,1024}");}
    static byte[] payload(byte[] jpeg,String question) throws IOException {return payload(new byte[][]{jpeg},question,false);}
    static byte[] walkingPayload(byte[][] images,String question)throws IOException{return walkingPayload(images,question,DEFAULT_MODEL);}
    static byte[] walkingPayload(byte[][] images,String question,String model)throws IOException{
        byte[] bytes=payload(images,question,true);
        if(!model.startsWith("gemini-3"))return bytes;
        try{JSONObject body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));body.getJSONObject("generationConfig").put("thinkingConfig",new JSONObject().put("thinkingLevel","MINIMAL"));return body.toString().getBytes(StandardCharsets.UTF_8);}
        catch(JSONException error){throw new IOException("Gemini 보행 분석 요청을 만들지 못했습니다.",error);}
    }
    private static byte[] payload(byte[][] images,String question,boolean walking)throws IOException{
        if(images==null||images.length<1||images.length>3)throw new IOException("사진은 1~3장이어야 합니다.");
        long total=0;
        for(byte[] jpeg:images){if(jpeg==null||jpeg.length<4||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216)throw new IOException("Gemini에는 JPEG 사진을 사용하세요.");total+=jpeg.length;}
        if(total>MAX_IMAGE)throw new IOException("Gemini 사진 합계는 2MB 이하여야 합니다.");
        if(question==null||question.trim().isEmpty()||question.length()>12000)throw new IOException("질문은 1~12000자로 입력하세요.");
        try{
            JSONArray parts=new JSONArray();
            for(int i=0;i<images.length;i++){
                if(walking)parts.put(new JSONObject().put("text","Cane camera frame "+(i+1)+" of "+images.length+"; chronological order, last is newest."));
                parts.put(new JSONObject().put("inlineData",new JSONObject().put("mimeType","image/jpeg").put("data",Base64.getEncoder().encodeToString(images[i]))));
            }
            parts.put(new JSONObject().put("text",question));
            JSONObject generation=new JSONObject().put("temperature",0.1).put("maxOutputTokens",walking?256:768);
            if(walking)generation.put("responseMimeType","application/json").put("responseSchema",new JSONObject().put("type","OBJECT")
                .put("properties",new JSONObject().put("speak",new JSONObject().put("type","BOOLEAN")).put("message",new JSONObject().put("type","STRING")).put("urgency",new JSONObject().put("type","STRING").put("enum",new JSONArray().put("none").put("notice").put("urgent"))))
                .put("required",new JSONArray().put("speak").put("message").put("urgency")));
            JSONObject body=new JSONObject()
                .put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text","Describe only directly visible evidence in the requested language. Automatic walking guidance uses natural Korean. A cane-mounted camera sweeps; image position is not a measured travel path. Do not invent distances, physical height, safety, or cane capabilities. Treat YOLO observations as fallible context. For automatic walking guidance obey the requested JSON and speak=false when nothing useful changed."))))
                .put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",parts))).put("generationConfig",generation);
            return body.toString().getBytes(StandardCharsets.UTF_8);
        }catch(JSONException e){throw new IOException("Gemini 요청을 만들지 못했습니다.",e);}
    }
    static String response(byte[] bytes) throws IOException {
        try{
            JSONObject body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            JSONObject feedback=body.optJSONObject("promptFeedback");
            String blocked=feedback==null?"":feedback.optString("blockReason","");
            if(!blocked.isEmpty()&&!blocked.equals("BLOCK_REASON_UNSPECIFIED"))throw new IOException("Gemini가 이 사진·질문의 답변을 제한했습니다.");
            JSONArray candidates=body.optJSONArray("candidates");JSONObject candidate=candidates==null?null:candidates.optJSONObject(0);
            if(candidate==null)throw new IOException("Gemini 답변이 비어 있습니다. 사진·질문·모델 설정을 확인하세요.");
            String finish=candidate.optString("finishReason","");
            if(finish.equals("SAFETY")||finish.equals("RECITATION")||finish.equals("PROHIBITED_CONTENT"))throw new IOException("Gemini가 답변을 제한했습니다.");
            JSONObject content=candidate.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");StringBuilder text=new StringBuilder();
            if(parts!=null)for(int i=0;i<parts.length();i++){
                JSONObject part=parts.optJSONObject(i);if(part==null||part.optBoolean("thought",false))continue;
                Object value=part.opt("text");if(value instanceof String)text.append((String)value);
            }
            String answer=text.toString().trim();
            if(answer.isEmpty())throw new IOException("Gemini의 최종 답변이 없습니다. 질문을 줄이거나 모델 설정을 확인하세요.");
            if(answer.length()>16000)throw new IOException("Gemini 답변이 너무 깁니다.");
            return answer+(finish.equals("MAX_TOKENS")?"\n(출력 길이 제한으로 답변이 끝났습니다.)":"");
        }catch(JSONException e){throw new IOException("Gemini 응답 형식을 읽지 못했습니다.",e);}
    }
    static final class Request implements AutoCloseable {
        private final HttpURLConnection connection;
        private final byte[] body;
        private final BooleanSupplier cancelled;
        private final AtomicBoolean closed=new AtomicBoolean();
        private volatile InputStream input;
        Request(String model,String key,byte[] jpeg,String question,Connector connector,BooleanSupplier cancelled) throws IOException {
            this(model,key,payload(jpeg,question),connector,cancelled);
        }
        Request(String model,String key,byte[][] images,String question,Connector connector,BooleanSupplier cancelled)throws IOException{
            this(model,key,walkingPayload(images,question,model),connector,cancelled);
        }
        private Request(String model,String key,byte[] body,Connector connector,BooleanSupplier cancelled)throws IOException{
            if(!validModel(model))throw new IOException("Gemini 모델 이름을 확인하세요.");
            if(!validKey(key))throw new IOException("Gemini 설정에서 API 키 입력 형식을 확인하세요.");
            this.cancelled=cancelled;check();this.body=body;
            URL endpoint=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
            connection=connector.open(endpoint);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setConnectTimeout(10000);connection.setReadTimeout(45000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("x-goog-api-key",key);
        }
        String execute() throws IOException {
            try{
                check();
                try(OutputStream output=connection.getOutputStream()){
                    for(int at=0;at<body.length;at+=16384){check();output.write(body,at,Math.min(16384,body.length-at));}
                }
                check();int code=connection.getResponseCode();check();
                if(code!=200)throw new ApiException(code);
                input=connection.getInputStream();check();
                ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
                while((n=input.read(block))!=-1){check();if(result.size()+n>MAX_RESPONSE)throw new IOException("Gemini 응답이 너무 큽니다.");result.write(block,0,n);}
                check();return response(result.toByteArray());
            }catch(SocketTimeoutException e){throw new IOException("Gemini 연결·응답 시간 초과. 휴대폰 인터넷과 모바일 데이터를 확인하세요.",e);}
            finally{close();}
        }
        private void check() throws IOException {if(closed.get()||cancelled.getAsBoolean())throw new IOException("Gemini 요청을 중지했습니다.");}
        @Override public void close(){if(closed.compareAndSet(false,true))connection.disconnect();InputStream stream=input;if(stream!=null)try{stream.close();}catch(IOException ignored){}}
    }
    static String httpError(int code){
        if(code==401||code==403)return "Gemini 인증·접근 오류 (HTTP "+code+"). API 키와 Gemini API 사용 권한을 확인하세요.";
        if(code==429)return "Gemini 사용량·요청 한도 초과 (HTTP 429). 잠시 뒤 다시 시도하거나 할당량을 확인하세요.";
        if(code==404)return "Gemini 모델을 찾지 못했습니다 (HTTP 404). 설정의 모델 이름을 확인하세요.";
        if(code>=300&&code<400)return "Gemini 연결 주소가 변경되어 요청을 중단했습니다 (HTTP "+code+").";
        return "Gemini 요청 실패 (HTTP "+code+"). 인터넷·모델 설정을 확인하세요.";
    }
}
