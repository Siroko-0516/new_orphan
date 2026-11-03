package dev.giljabi;

import android.content.Context;
import android.content.SharedPreferences;

final class AiProvider {
    static final String LOCAL="local",GEMINI="gemini",OPENAI="openai";
    static boolean valid(String value){return LOCAL.equals(value)||GEMINI.equals(value)||OPENAI.equals(value);}
    static String name(String value){return OPENAI.equals(value)?"GPT":GEMINI.equals(value)?"Gemini":"로컬 AI";}
    static String assistant(Context context){String p=context.getSharedPreferences("ai-settings",Context.MODE_PRIVATE).getString("assistant_provider",GEMINI);return OPENAI.equals(p)?OPENAI:GEMINI;}
    static ApiKeyStore keys(Context context,String provider){return new ApiKeyStore(context,OPENAI.equals(provider)?OPENAI:GEMINI);}
    static String model(Context context,String provider){SharedPreferences p=context.getSharedPreferences("ai-settings",Context.MODE_PRIVATE);return OPENAI.equals(provider)?p.getString("openai_model",OpenAiClient.DEFAULT_MODEL):p.getString("gemini_model",GeminiClient.DEFAULT_MODEL);}
}
