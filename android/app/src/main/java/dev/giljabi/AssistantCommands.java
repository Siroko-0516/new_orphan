package dev.giljabi;

import java.util.*;

/** Phone actions come only from recognized user speech, never from generated AI replies. */
final class AssistantCommands {
    enum Kind { CANCEL, CONFIRM, CALL, NEARBY, MAP, CHAT }
    static final class Command {
        final Kind kind;final String target;
        Command(Kind kind,String target){this.kind=kind;this.target=target;}
    }
    static String compact(String value){return value==null?"":value.toLowerCase(Locale.KOREAN).replaceAll("[\\s\\p{Punct}·❤️♥♡]","");}
    static Command parse(String speech,boolean awaitingConfirmation){
        String t=compact(speech);
        if(t.isEmpty())return new Command(Kind.CANCEL,"");
        if(t.matches(".*(?:취소|하지마|걸지마|그만|멈춰|아니야|아니요|하지말).*"))return new Command(Kind.CANCEL,"");
        if(awaitingConfirmation&&Arrays.asList("네","넵","예","응","어","맞아","맞아요","확인","전화해","전화해줘","걸어줘","연결해줘","해주세요").contains(t))return new Command(Kind.CONFIRM,"");
        if(t.matches("(?:지도|검색결과|병원지도)(?:를)?(?:열어|보여)(?:줘|주세요)?"))return new Command(Kind.MAP,"");
        if(!t.contains("전화번호")&&t.matches(".+?(?:전화|통화)(?:를)?(?:좀)?(?:걸어|해|연결해)(?:줘|주세요|주세요요)?")){
            int at=t.indexOf("전화");if(at<0)at=t.indexOf("통화");
            String target=t.substring(0,at).replaceFirst("(?:에게|한테|으로|로|좀)$","");
            if(!target.isEmpty()&&target.length()<=60)return new Command(Kind.CALL,target);
        }
        if(t.matches(".*(?:주변|근처|가까운|어디|있어|있나|있니|찾아|검색).*")){
            String[][] categories={{"병원","병원","의원"},{"약국","약국"},{"화장실","화장실"},{"편의점","편의점"},{"카페","카페","커피숍"},{"음식점","음식점","식당"}};
            for(String[] category:categories)for(int i=1;i<category.length;i++)if(t.contains(category[i]))return new Command(Kind.NEARBY,category[0]);
        }
        return new Command(Kind.CHAT,speech==null?"":speech.trim());
    }
    static String phone(String input){
        if(input==null||input.matches(".*[^0-9+() .-].*"))return "";
        String p=input.replaceAll("[() .-]","");
        return p.matches("\\+?[0-9]{7,15}")?p:"";
    }
    static boolean sameName(String stored,String requested){
        String a=name(stored),b=name(requested);
        if(a.equals(b))return !a.isEmpty();
        Set<String> mother=new HashSet<>(Arrays.asList("엄마","어머니","어머님","모친"));
        Set<String> father=new HashSet<>(Arrays.asList("아빠","아버지","아버님","부친"));
        return mother.contains(a)&&mother.contains(b)||father.contains(a)&&father.contains(b);
    }
    private static String name(String s){return compact(s).replaceAll("[^가-힣a-z0-9]","");}
    static String speechText(String raw){
        if(raw==null)return "";
        String clean=raw.replaceAll("https?://\\S+","").replaceAll("[`#*_]","").replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        if(clean.length()>280){int stop=clean.lastIndexOf('.',275);clean=clean.substring(0,stop>120?stop+1:275)+" 더 궁금하면 다시 물어봐 주세요.";}
        return clean;
    }
}
