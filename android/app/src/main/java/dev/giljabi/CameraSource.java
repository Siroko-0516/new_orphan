package dev.giljabi;

final class CameraSource {
    static final int ESP=0, BACK=1, FRONT=2;
    static final String[] LABELS={"ESP32-CAM", "휴대폰 후면 카메라", "휴대폰 전면 카메라"};
    static boolean valid(int source){return source>=ESP&&source<=FRONT;}
    static boolean phone(int source){return source==BACK||source==FRONT;}
    static String label(int source){return valid(source)?LABELS[source]:"카메라";}
    static int rotation(int sensor,int display,boolean front){return (sensor+(front?display:-display)+360)%360;}
}
