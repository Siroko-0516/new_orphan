package dev.giljabi;

/** Short visual observations; no inferred distance, motion or collision prediction. */
final class FastObstacleNotice {
    static String describe(YoloDetector.Box box){
        float center=(box.xy[0]+box.xy[2])/2;String position=center<.33f?"왼쪽":center>.67f?"오른쪽":"중앙";
        String name;
        switch(box.label){
            case 0:name="사람";break;case 1:name="자전거";break;case 2:name="자동차";break;case 3:name="오토바이";break;
            case 5:name="버스";break;case 6:name="기차";break;case 7:name="트럭";break;case 13:name="벤치";break;
            case 15:name="고양이";break;case 16:name="개";break;case 28:name="가방";break;case 56:name="의자";break;
            case 57:name="소파";break;case 58:name="화분";break;case 59:name="침대";break;case 60:name="식탁";break;
            default:name="물체";
        }
        return "화면 "+position+"에 "+name+" 감지.";
    }
}
