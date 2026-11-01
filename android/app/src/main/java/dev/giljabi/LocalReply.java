package dev.giljabi;

/** Do not show thinking text; stop after the two brief sentences requested by the app. */
final class LocalReply {
    static String clean(String raw){return raw.replaceAll("(?s)<think>.*?</think>","").replaceAll("(?s)<think>.*$","").trim();}
    static boolean complete(String text){
        if(text.length()<24)return false;int count=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(c!='.'&&c!='!'&&c!='?'&&c!='。'&&c!='！'&&c!='？')continue;
            if(c=='.'&&i>0&&i+1<text.length()&&Character.isDigit(text.charAt(i-1))&&Character.isDigit(text.charAt(i+1)))continue;
            if(i+1<text.length()&&text.charAt(i+1)==c)continue;
            if(++count>=2)return true;
        }
        return false;
    }
}
