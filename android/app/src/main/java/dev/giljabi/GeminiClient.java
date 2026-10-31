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
    static boolean validModel(String model){return model!=null&&model.matches("gemini-[A-Za-z0-9._-]{1,100}");}
    static boolean validKey(String key){return key!=null&&key.matches("[A-Za-z0-9_-]{10,256}");}
    static byte[] payload(byte[] jpeg,String question) throws IOException {
        if(jpeg==null||jpeg.length<4||jpeg.length>MAX_IMAGE||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216)throw new IOException("Gemini에는 2MB 이하 JPEG 사진을 사용하세요.");
        if(question==null||question.trim().isEmpty()||question.length()>12000)throw new IOException("질문은 1~12000자로 입력하세요.");
        try{
            JSONArray parts=new JSONArray().put(new JSONObject().put("inlineData",new JSONObject().put("mimeType","image/jpeg").put("data",Base64.getEncoder().encodeToString(jpeg)))).put(new JSONObject().put("text",question));
            JSONObject body=new JSONObject()
                .put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text","Describe only visible evidence in the language requested by the user. Do not invent objects, distance, motion, or collision risk. If uncertain, say so. YOLO observations may be wrong."))))
                .put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",parts)))
                .put("generationConfig",new JSONObject().put("temperature",0.1).put("maxOutputTokens",768));
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
            if(!validModel(model))throw new IOException("Gemini 모델 이름을 확인하세요.");
            if(!validKey(key))throw new IOException("Gemini API 키를 설정하세요. 공백 없이 키 문자열만 입력하세요.");
            this.cancelled=cancelled;check();body=payload(jpeg,question);
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
                if(code!=200)throw new IOException(httpError(code));
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
