package dev.giljabi;

import java.text.Normalizer;
import java.util.*;

/** Remember meaning across cane sweeps, detector counts, and AI paraphrases. */
final class NarrationMemory {
    private String last="",lastMeaning="",sourceText="";private boolean lastUrgent;private long episode=Long.MIN_VALUE;
    private final LinkedHashSet<String> said=new LinkedHashSet<>();
    synchronized String previous(){return last;}
    synchronized String source(long currentEpisode){return episode==currentEpisode?sourceText:"";}
    synchronized void rememberSource(String text){sourceText=text;}
    synchronized boolean urgent(long currentEpisode){return episode==currentEpisode&&lastUrgent;}
    synchronized String previous(long currentEpisode){return episode==currentEpisode?last:"";}
    synchronized void reset(){last="";lastMeaning="";said.clear();episode=Long.MIN_VALUE;}
    synchronized boolean eligible(long currentEpisode,String state,String message){return eligible(currentEpisode,state,message,false);}
    synchronized boolean eligible(long currentEpisode,String state,String message,boolean urgent){
        String clean=clean(message);if(clean.isEmpty())return false;
        if(episode!=currentEpisode)return true;
        String meaning=meaning(clean)+(urgent?",urgent":""),key=meaning.isEmpty()?"text:"+clean:"meaning:"+meaning;
        return !( !meaning.isEmpty()&&(meaning.equals(lastMeaning)||said.contains(key)) || said.contains(key) || (!last.isEmpty()&&(meaning.isEmpty()||meaning.equals(lastMeaning))&&similar(clean,last)) );
    }
    static boolean equivalent(String a,String b){String ma=meaning(clean(a)),mb=meaning(clean(b));return !ma.isEmpty()&&ma.equals(mb)||similar(clean(a),b);}
    synchronized boolean offer(long currentEpisode,String state,String message){return offer(currentEpisode,state,message,false);}
    synchronized boolean offer(long currentEpisode,String state,String message,boolean urgent){
        if(!eligible(currentEpisode,state,message,urgent))return false;
        if(episode!=currentEpisode){said.clear();last="";lastMeaning="";episode=currentEpisode;}
        String clean=clean(message),meaning=meaning(clean)+(urgent?",urgent":""),key=meaning.isEmpty()?"text:"+clean:"meaning:"+meaning;
        last=message;sourceText=message;lastMeaning=meaning;lastUrgent=urgent;said.add(key);if(said.size()>32)said.remove(said.iterator().next());return true;
    }
    private static String clean(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\d]","");}
    private static String meaning(String text){
        TreeSet<String> types=new TreeSet<>();
        String[][] names={{"seat","의자","벤치","chair","bench"},{"table","테이블","식탁","table"},{"person","사람","보행자","person","people"},{"vehicle","자동차","차량","트럭","버스","car","vehicle","truck","bus"},{"cycle","자전거","오토바이","bicycle","motorcycle"},{"stairs","계단","stairs","steps"},{"curb","단차","턱","curb"},{"branch","나뭇가지","나무가지","나무가","branch","tree"},{"barrier","펜스","울타리","차단","barrier","fence"},{"pole","기둥","pole"},{"dog","강아지","개한마리","dog"},{"sign","간판","표지판","sign"}};
        for(String[] group:names)for(int i=1;i<group.length;i++)if(text.contains(group[i])){types.add(group[0]);break;}
        if(types.isEmpty())return "";
        if(text.contains("돌출")||text.contains("위쪽")||text.contains("천장")||text.contains("overhead"))types.add("upper");
        if(text.contains("가로막")||text.contains("막고")||text.contains("막는")||text.contains("막힌")||text.contains("막혀")||text.contains("차단"))types.add("blocking");
        if(types.contains("stairs")){if(text.contains("내려")||text.contains("하행"))types.add("down");if(text.contains("올라")||text.contains("상행"))types.add("up");}
        return String.join(",",types);
    }
    private static boolean similar(String a,String b){
        String clean=b.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\d]","");
        Set<String> aa=grams(a),bb=grams(clean);if(aa.isEmpty()||bb.isEmpty())return a.equals(clean);
        Set<String> both=new HashSet<>(aa);both.retainAll(bb);Set<String> union=new HashSet<>(aa);union.addAll(bb);
        return both.size()>=union.size()*.72;
    }
    private static Set<String> grams(String text){Set<String> r=new HashSet<>();for(int i=1;i<text.length();i++)r.add(text.substring(i-1,i+1));return r;}
}
