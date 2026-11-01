# ESP 영상 실행 중 종료 대응 · 0.5.1

사용자 증상은 영상·YOLO 실행 직후 정상 영상이 나오다가 앱이 갑자기 종료되고 시스템이 앱에 버그가 있다는 안내를 띄우는 것입니다. 실제 휴대폰의 Java 스택·네이티브 tombstone·종료 이유는 아직 확보하지 못했습니다. 따라서 증상의 원인을 캐시 문제나 특정 코드 한 곳으로 확정하지 않습니다.

## 변경

- 수동 영상·서비스 영상·사진 교체에서 화면에 제출했던 이전 Bitmap의 즉시 `recycle()` 호출을 제거했습니다. Android RenderThread가 이전 프레임을 참조하는 동안 Java UI가 다음 프레임으로 넘어갈 수 있기 때문입니다. 화면에 제출한 비트맵은 렌더러 참조 수명과 GC에 맡기고, 사용하지 않은 드롭 프레임과 작업 전용 이미지는 계속 정리합니다.
- YOLO의 입력 Bitmap/Canvas·픽셀/float 배열·native-order 직접 버퍼·입력 텐서를 한 감지기 안에서 재사용합니다. 기존에는 매 프레임 이미지와 배열을 만들고 비직접 FloatBuffer를 입력 텐서에 넘겨 런타임이 직접 버퍼 복사본도 만들었습니다. 동기화된 감지/해제와 결과 close를 유지합니다. 입력 크기·letterbox·색 정규화·감지 임계값·NMS는 유지합니다.
- 영상 작업 스레드의 Java `OutOfMemoryError`와 `LinkageError`를 기록·안내하고 스트림을 닫습니다. 자동 모드도 이런 오류나 비통신 오류에는 반복 재연결하지 않습니다. 시스템의 메모리 강제 종료나 SIGSEGV를 Java catch로 막는다는 의미는 아닙니다.
- 앱 내부 `last-failure.txt`에 마지막 Java 오류를 제한된 크기로 저장하고 **앱 오류 기록 보기 · 복사**를 추가했습니다. Android 11 이상에서는 시스템의 최근 프로세스 종료 이유·메모리 사용 기록도 표시합니다. 실행 중 단계 저장은 간격을 두며, YOLO 초기화 직전에는 저장을 완료합니다. 기록은 자동 전송하지 않고, API 키 형식 문자열은 마스킹합니다. 앱 데이터 삭제·재설치 시 기록도 삭제될 수 있습니다.

## 검사와 한계

`PreviewLifetimeTest`는 Android native graphics 환경(Robolectric)에서 실제 Activity의 사진 교체 경로를 반복 실행하고 이전 비트맵이 계속 그려지는지 검사합니다. 이는 실제 Android GPU RenderThread에서 크래시를 재현한 시험은 아닙니다. `CameraFailureTest`는 연결 시간 초과만 재시도하고, 메모리/중첩 로딩 오류는 종료하도록 검사합니다. 기존 60개 테스트도 유지합니다.

개발 환경의 Linux x86-64에서 ONNX Runtime 1.22.0과 실제 `public/yolo11n.onnx`로 검정/흰색 입력 12프레임을 번갈아 실행했습니다. 재사용 직접 버퍼·동일 텐서의 출력과 매번 새 비직접 입력으로 만든 텐서의 출력이 모든 값에서 동일했습니다(최대 절대 오차 0.0, 출력 84×3549). 프레임별 입력 변경도 출력에 반영됐습니다. 이는 입력 메모리 공유의 호환성 확인이며 Android 런타임·실제 영상·성능 시험은 아닙니다. 재현 도구는 `scripts/YoloBufferReuseCheck.java`이고, ONNX Runtime 1.22.0 호스트 JAR를 classpath에 넣어 JDK source launcher로 실행할 수 있습니다.

GitHub 배포 작업은 모델을 실제 포함한 APK 빌드·JVM 테스트·Android Lint 성공 후에만 APK를 게시하며, `checks.json`으로 테스트 결과를 함께 제공합니다. 성공 여부와 실제 배포 파일의 체크섬은 릴리스 검증 후 기록합니다. 실제 휴대폰·ESP32·장시간 프레임 갱신·Android 네이티브 ONNX·블루투스·서보는 이 환경에서 아직 시험하지 않았습니다.

새 버전에서 같은 증상이 발생하면 앱을 다시 열고 **앱 오류 기록 보기 · 복사**의 내용을 전달해 주세요. Android 10 이하에서는 시스템 종료 이유 조회가 없어 앱이 저장한 Java 오류만 확인할 수 있습니다. 모델 파일·API 키·사진은 보고서에 넣지 않습니다.

## 공개 파일 검증

[GitHub Actions 41289201233](https://github.com/Siroko-0516/new_orphan/actions/runs/41289201233)의 `android-v0.5.1-full` 태그, 소스 `3473a9c`에서 full APK 빌드·검사와 업로드에 성공했습니다. [배포 검사 보고서](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.5.1-full/checks.json)는 테스트 **66개**, 실패·오류·건너뜀 0, Lint 오류 0을 기록합니다. 회귀 검사는 통과했지만 사용자의 실제 종료 스택과 같은 오류를 실기기에서 재현한 것은 아닙니다.

공개 URL에서 APK와 체크섬을 다시 내려받아 확인했습니다.

- 버전 `0.5.1-full`, versionCode 7, 크기 `1805299532` bytes.
- SHA-256: `1ee55edef4e926c52e98e0c21f25ce737d88523cc86ad2b698c96179f97f2b0d`. 게시된 체크섬과 일치.
- 인증서 SHA-256: `142ffbd8ce05a5ccac181516db32969a821fdab06bc460556113185dc2be6c7d`.
- APK v2 서명, 16KB ZIP 정렬, 포함한 ARM64 공유 라이브러리 전체의 PT_LOAD 정렬 16KB 이상 확인.
- 내장 MiniCPM 언어/비전 가중치의 크기·해시·무압축 저장이 고정 사양과 일치. YOLO ONNX 해시가 기존 소스와 일치.
- 최종 manifest에 오류 기록을 설치하는 `GiljabiApp`과 기존 `MonitorService` 포함.

이 APK는 이전 공개 0.5.0과 서명이 다릅니다. 기존 길잡이를 삭제한 뒤 설치해야 하며 API 키·설정·모델 복사본이 삭제됩니다. 원본 모델은 APK에 있어 첫 사용 시 다시 준비합니다. 앱 삭제 전 남겨야 할 정보는 따로 보관하세요.

[0.5.1 APK 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.5.1-full/giljabi-0.5.1-full-debug.apk).
