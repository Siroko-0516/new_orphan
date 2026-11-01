package dev.giljabi;

import java.util.Locale;

/** Cleans clipboard presentation characters only; never logs or echoes the credential. */
final class ApiKeyInput {
    static final class Parsed {
        final String value,problem;final boolean cleaned;
        Parsed(String value,String problem,boolean cleaned){this.value=value;this.problem=problem;this.cleaned=cleaned;}
    }
    static Parsed parse(String raw){
        if(raw==null)raw="";
        if(raw.length()>8192)return new Parsed("","입력 내용이 너무 깁니다. 코드 전체 대신 API 키만 복사하세요.",false);
        if(raw.matches("(?s).*[\\r\\n][\\s\\p{Z}]*[A-Za-z0-9_-]+[\\s\\p{Z}]*:.*"))return new Parsed("","HTTP 헤더 전체 대신 API 키 값만 복사하세요.",false);
        StringBuilder plain=new StringBuilder();
        for(int i=0;i<raw.length();){int c=raw.codePointAt(i);i+=Character.charCount(c);
            if(Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.getType(c)==Character.FORMAT)continue;
            plain.appendCodePoint(c);
        }
        String value=plain.toString();
        if(value.length()>=2){char a=value.charAt(0),b=value.charAt(value.length()-1);if(a==b&&(a=='\''||a=='"'||a=='`'))value=value.substring(1,value.length()-1);}
        boolean cleaned=!value.equals(raw);
        if(value.isEmpty())return new Parsed("",null,cleaned);
        if(value.indexOf('*')>=0||value.indexOf('\u2026')>=0||value.contains("..."))return new Parsed("","가려지거나 생략된 키입니다. AI Studio의 복사 버튼으로 원본 키 전체를 복사하세요.",cleaned);
        if(value.startsWith("GEMINI_API_KEY=")||value.startsWith("GOOGLE_API_KEY=")||value.startsWith("x-goog-api-key:"))return new Parsed("","설정 코드 전체가 붙여넣어졌습니다. API 키 값만 복사하세요.",cleaned);
        if(value.length()<10||value.length()>1024)return new Parsed("","키 길이를 확인하세요. AI Studio에서 원본 키 전체를 복사하세요.",cleaned);
        if(!GeminiClient.validKey(value)){
            for(int i=0;i<value.length();){int c=value.codePointAt(i);i+=Character.charCount(c);
                if(c<33||c>126)
                    return new Parsed("",String.format(Locale.ROOT,"키에 허용되지 않는 문자(U+%04X)가 있습니다. 코드 전체 대신 원본 API 키만 복사하세요.",c),cleaned);
            }
            return new Parsed("","API 키 입력 형식을 확인하세요.",cleaned);
        }
        return new Parsed(value,null,cleaned);
    }
}
