package dev.giljabi;

import java.io.IOException;

/** Expected stream failures and resource/loader failures require different recovery. */
final class CameraFailure {
    final String message;
    final boolean retryable;
    private CameraFailure(String message,boolean retryable){this.message=message;this.retryable=retryable;}
    static CameraFailure from(Throwable error){
        Throwable cause=error;
        for(int i=0;i<12&&cause!=null;i++,cause=cause.getCause()){
            if(cause instanceof OutOfMemoryError)return new CameraFailure("영상·YOLO 메모리가 부족해 영상을 중지했습니다. 모델 메모리를 해제하고 다시 시작하세요. 오류 기록에서 종료 이유를 확인할 수 있습니다.",false);
            if(cause instanceof LinkageError)return new CameraFailure("이 기기에서 YOLO 실행 라이브러리를 불러오지 못했습니다. 오류 기록을 복사해 알려주세요.",false);
        }
        String detail=error.getMessage();
        return new CameraFailure("영상/YOLO 오류: "+(detail==null?error.getClass().getSimpleName():detail),error instanceof IOException);
    }
}
