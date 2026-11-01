package dev.giljabi;

import java.util.Locale;

/** Reject unsupported claims about an aid's capabilities; this app only observes an image. */
final class SceneLanguage {
    static String grounded(String text){
        String compact=text.toLowerCase(Locale.ROOT).replaceAll("\\s+","");
        boolean caneClaim=compact.contains("지팡이")&&compact.contains("감지")&&(compact.contains("못")||compact.contains("없")||compact.contains("불가"));
        caneClaim|=compact.contains("cane")&&compact.contains("detect")&&(compact.contains("cannot")||compact.contains("can't")||compact.contains("unable"));
        return caneClaim?"사진만으로 지팡이의 감지 여부를 판단할 수 없습니다. 주변 물체를 명확히 확인할 수 있는 영상이 필요합니다.":text;
    }
}
