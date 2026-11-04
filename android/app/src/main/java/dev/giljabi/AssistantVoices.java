package dev.giljabi;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.*;

final class AssistantVoices {
    static List<Voice> korean(Set<Voice> voices,Voice preferred){
        List<Voice> result=new ArrayList<>();if(voices!=null)for(Voice voice:voices){
            Set<String> features=voice.getFeatures();
            if(voice.getLocale().getLanguage().equals("ko")&&!voice.isNetworkConnectionRequired()&&(features==null||!features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))result.add(voice);
        }
        result.sort(Comparator.comparingInt((Voice v)->preferred!=null&&v.getName().equals(preferred.getName())?0:1).thenComparing(Comparator.comparingInt(Voice::getQuality).reversed()).thenComparingInt(Voice::getLatency).thenComparing(Voice::getName));return result;
    }
}
