package dev.giljabi;

/** Keep a complete first sentence instead of reading the model's later explanations. */
final class WalkingSpeechText {
    static final float RATE=1.2f;
    static final long CLOUD_WAIT_MS=1800;
    static String brief(String text){
        String clean=text==null?"":text.trim().replaceAll("\\s+"," ");
        clean=clean.replaceFirst("^(?:네[,.!]\\s*|알겠습니다[.!]\\s*|물론입니다[.!]\\s*|(?i:yes|sure)[,.!]\\s*)","");
        for(int i=0;i<clean.length();i++){
            char c=clean.charAt(i);
            if(".!?。！？".indexOf(c)<0)continue;
            if(c=='.'&&i>0&&i+1<clean.length()&&Character.isDigit(clean.charAt(i-1))&&Character.isDigit(clean.charAt(i+1)))continue;
            boolean koreanEnd=c=='.'&&i>0&&clean.charAt(i-1)>='\uac00'&&clean.charAt(i-1)<='\ud7a3';
            if(koreanEnd||i+1==clean.length()||Character.isWhitespace(clean.charAt(i+1))||c=='。'||c=='！'||c=='？')return clean.substring(0,i+1);
        }
        return clean;
    }
}
