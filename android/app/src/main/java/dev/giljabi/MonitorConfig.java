package dev.giljabi;

import android.content.Intent;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

/** Contains settings only: no API key or image is put in a service Intent. */
final class MonitorConfig {
    final String address,question,model,bleAddress,serviceUuid,characteristicUuid,provider;
    final boolean cloud,speak,useYolo,notify;
    final long intervalMs;
    final int source;
    MonitorConfig(String address,String question,String model,boolean cloud,boolean speak,boolean useYolo,boolean notify,long intervalMs,String bleAddress,String serviceUuid,String characteristicUuid){
        this(address,question,model,cloud,speak,useYolo,notify,intervalMs,bleAddress,serviceUuid,characteristicUuid,CameraSource.ESP);
    }
    MonitorConfig(String address,String question,String model,boolean cloud,boolean speak,boolean useYolo,boolean notify,long intervalMs,String bleAddress,String serviceUuid,String characteristicUuid,int source){
        this(address,question,model,cloud,speak,useYolo,notify,intervalMs,bleAddress,serviceUuid,characteristicUuid,source,cloud?AiProvider.GEMINI:AiProvider.LOCAL);
    }
    MonitorConfig(String address,String question,String model,boolean cloud,boolean speak,boolean useYolo,boolean notify,long intervalMs,String bleAddress,String serviceUuid,String characteristicUuid,int source,String provider){
        this.provider=provider;
        this.source=source;
        this.address=address;this.question=question;this.model=model;this.cloud=cloud;this.speak=speak;this.useYolo=useYolo;this.notify=notify;this.intervalMs=intervalMs;this.bleAddress=bleAddress;this.serviceUuid=serviceUuid;this.characteristicUuid=characteristicUuid;
    }
    void validate()throws IOException {
        if(!CameraSource.valid(source))throw new IOException("카메라 종류를 확인하세요.");
        if(source==CameraSource.ESP&&!LocalCamera.isAllowed(new URL(address)))throw new IOException("ESP의 사설 IPv4 영상 주소를 입력하세요.");
        if(question==null||question.trim().isEmpty()||question.length()>12000)throw new IOException("질문은 1~12000자로 입력하세요.");
        if(intervalMs<1000||intervalMs>60000)throw new IOException("분석 시작 간격은 1~60초여야 합니다.");
        if(!AiProvider.valid(provider)||cloud==AiProvider.LOCAL.equals(provider))throw new IOException("AI 공급자 설정을 확인하세요.");
        if(cloud&&!(AiProvider.OPENAI.equals(provider)?OpenAiClient.validModel(model):GeminiClient.validModel(model)))throw new IOException("선택한 AI 모델 이름을 확인하세요.");
        if(!bleAddress.isEmpty()){
            if(!bleAddress.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))throw new IOException("BLE 기기 주소를 확인하세요.");
            try{UUID.fromString(serviceUuid);UUID.fromString(characteristicUuid);}catch(IllegalArgumentException error){throw new IOException("BLE UUID를 확인하세요.");}
        }
    }
    Intent write(Intent intent){return intent.putExtra("provider",provider).putExtra("source",source).putExtra("camera",address).putExtra("question",question).putExtra("model",model).putExtra("cloud",cloud).putExtra("speak",speak).putExtra("yolo",useYolo).putExtra("notify",notify).putExtra("interval",intervalMs).putExtra("ble",bleAddress).putExtra("service",serviceUuid).putExtra("characteristic",characteristicUuid);}
    static MonitorConfig read(Intent i){return new MonitorConfig(value(i,"camera","http://192.168.4.1:81/stream"),value(i,"question","Describe the visible scene."),value(i,"model",GeminiClient.DEFAULT_MODEL),i.getBooleanExtra("cloud",false),i.getBooleanExtra("speak",true),i.getBooleanExtra("yolo",true),i.getBooleanExtra("notify",false),i.getLongExtra("interval",5000),value(i,"ble",""),value(i,"service",BleLink.DEFAULT_SERVICE),value(i,"characteristic",BleLink.DEFAULT_CHARACTERISTIC),i.getIntExtra("source",CameraSource.ESP),value(i,"provider",i.getBooleanExtra("cloud",false)?AiProvider.GEMINI:AiProvider.LOCAL));}
    private static String value(Intent i,String name,String fallback){String s=i.getStringExtra(name);return s==null?fallback:s;}
}
