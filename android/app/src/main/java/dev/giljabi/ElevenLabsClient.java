package dev.giljabi;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Same model and default voice settings as the original web speech endpoint. */
final class ElevenLabsClient {
    static final String FEMALE="lhTvHflPVOqgSWyuWQry",MALE="LS3HmRGCXV8wxCAhUbTt";
    static final String MODEL="eleven_multilingual_v2";
    static final int MAX_AUDIO=5*1024*1024;
    static boolean validVoice(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,128}");}
    static final class Request implements AutoCloseable {
        private final String text,key,voice;
        private final GeminiClient.Connector connector;
        private final BooleanSupplier cancelled;
        private final AtomicBoolean closed=new AtomicBoolean();
        private volatile HttpURLConnection connection;
        Request(String text,String key,String voice,GeminiClient.Connector connector,BooleanSupplier cancelled)throws IOException{
            if(text==null||text.trim().isEmpty()||text.length()>2000)throw new IOException("음성 안내 문장은 1~2000자여야 합니다.");
            if(!GeminiClient.validKey(key))throw new IOException("ElevenLabs 설정에서 개인 API 키를 입력하세요.");
            if(!validVoice(voice))throw new IOException("ElevenLabs 설정에서 예전에 쓰던 Voice ID를 입력하세요.");
            this.text=text;this.key=key;this.voice=voice;this.connector=connector;this.cancelled=cancelled;
        }
        byte[] execute()throws IOException{
            if(closed.get()||cancelled.getAsBoolean())throw new InterruptedIOException("음성 생성을 중지했습니다.");
            ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> timeout=timer.schedule(this::close,20,TimeUnit.SECONDS);
            try{
                HttpURLConnection active=connector.open(new URL("https://api.elevenlabs.io/v1/text-to-speech/"+voice+"?output_format=mp3_44100_128"));connection=active;
                if(closed.get()||cancelled.getAsBoolean())throw new InterruptedIOException("음성 생성을 중지했습니다.");
                active.setInstanceFollowRedirects(false);active.setConnectTimeout(10000);active.setReadTimeout(15000);active.setRequestMethod("POST");active.setDoOutput(true);
                active.setRequestProperty("xi-api-key",key);active.setRequestProperty("Content-Type","application/json; charset=utf-8");active.setRequestProperty("Accept","audio/mpeg");
                byte[] body;try{body=new JSONObject().put("text",text).put("model_id",MODEL).toString().getBytes(StandardCharsets.UTF_8);}catch(org.json.JSONException error){throw new IOException("음성 요청을 만들지 못했습니다.");}active.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=active.getOutputStream()){out.write(body);}
                int status=active.getResponseCode();
                if(status!=200)throw new IOException(problem(status));
                String type=active.getContentType();if(type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("audio/"))throw new IOException("ElevenLabs가 오디오 파일을 반환하지 않았습니다.");
                if(active.getContentLengthLong()>MAX_AUDIO)throw new IOException("ElevenLabs 음성 파일이 너무 큽니다.");
                try(InputStream input=active.getInputStream();ByteArrayOutputStream audio=new ByteArrayOutputStream()){
                    byte[] block=new byte[8192];int n;
                    while((n=input.read(block))!=-1){if(closed.get()||cancelled.getAsBoolean())throw new InterruptedIOException("음성 생성을 중지했습니다.");if(audio.size()+n>MAX_AUDIO)throw new IOException("ElevenLabs 음성 파일이 너무 큽니다.");audio.write(block,0,n);}
                    if(audio.size()<4)throw new IOException("ElevenLabs 음성 파일이 비어 있습니다.");return audio.toByteArray();
                }
            }finally{timeout.cancel(false);timer.shutdownNow();close();}
        }
        @Override public void close(){closed.set(true);HttpURLConnection active=connection;if(active!=null)active.disconnect();}
    }
    private static String problem(int status){
        switch(status){case 401:return "ElevenLabs API 키 인증에 실패했습니다.";case 403:return "ElevenLabs 음성 사용 권한을 확인하세요.";case 404:return "기존 ElevenLabs Voice ID를 찾을 수 없습니다.";case 429:return "ElevenLabs 사용량 또는 동시 요청 한도에 도달했습니다.";default:return "ElevenLabs 음성 생성 실패 (HTTP "+status+").";}
    }
}
