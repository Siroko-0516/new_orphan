package dev.giljabi;

import java.io.IOException;
import java.util.function.BooleanSupplier;

final class CloudAi {
    static CloudRequest vision(String provider,String model,String key,byte[][] images,String question,boolean walking,GeminiClient.Connector connector,BooleanSupplier cancelled)throws IOException{
        if(AiProvider.OPENAI.equals(provider))return OpenAiClient.visionRequest(model,key,images,question,walking,connector,cancelled);
        if(!AiProvider.GEMINI.equals(provider))throw new IOException("클라우드 AI 공급자를 선택하세요.");
        if(walking)return new GeminiClient.Request(model,key,images,question,connector,cancelled);
        return new GeminiClient.Request(model,key,images[0],question,connector,cancelled);
    }
    static int status(Throwable error){return error instanceof GeminiClient.ApiException?((GeminiClient.ApiException)error).status:error instanceof OpenAiClient.ApiException?((OpenAiClient.ApiException)error).status:0;}
}
