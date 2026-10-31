# 0.5.0 · 자동 장면 설명 / 백그라운드 실행 검증

## 로컬 빌드

Android ARM64 full APK, versionName `0.5.0-full`, versionCode 6, minSdk 26 / targetSdk 35. 실제 모델 두 파일을 포함한 `assembleDebug`, `testDebugUnitTest`, `lintDebug`가 성공했습니다. JVM 테스트 **60개**, 실패·오류·건너뜀 0. Android Lint 오류 0, 경고 있음. APK v2 서명과 16KB ZIP 정렬 통과.

APK 안 언어 모델 `672003680` bytes / SHA-256 `f18f594747e5317ab490cff9c06339c9cfb89acbe2f02ebc48a4e167bc23576d`, 비전 모델 `1110117984` bytes / SHA-256 `fb9cc047a3cdad5317fd8433a4aeda2d22f00f02ca4bd93b34ad84165b25a900`을 스트리밍 해시로 확인했습니다. 두 가중치는 무압축 저장됩니다. 출처는 기존 고정 모델 사양과 같습니다.

빌드된 manifest에 비공개 `MonitorService`, `stopWithTask=false`, `connectedDevice|mediaPlayback` foreground service type, 관련 foreground service·알림·wake/Wi-Fi 권한이 들어 있음을 확인했습니다. 이 구성 확인은 Android 시스템에서의 서비스 실행 시험이 아닙니다.

## 테스트와 구현 검토

추가한 `ContinuousGateTest` 7개는 중복 분석 차단, 새 프레임만 사용, 최소 시작 간격과 오래된/미래 시각 프레임 거부, 모드 변경 후 이전 결과 차단, 이전 작업 완료까지 새 작업 대기, 이전 작업의 늦은 완료가 새 작업을 해제하지 않음, 오류 backoff, 카메라 재연결이 일시 중지를 풀지 않음, 중지·재시작의 이전 결과 차단을 확인합니다. 기존 Gemini 요청·취소·오류·응답 제한, API 키 암호화, 모델 설치 복구, MJPEG·YOLO·새 물체 추적 테스트도 통과했습니다.

서비스가 ESP 수신·YOLO·반복 AI·선택된 BLE·TTS를 소유하며 Activity가 `onStop`에서 닫는 대상은 수동 모드 자원입니다. Activity와 서비스는 하나의 `LocalVlm` JNI 소유자를 공유하고 네이티브 작업을 직렬화합니다. 결과와 진행 메시지는 유효한 모드 세대에서만 표시합니다. 최신 프레임 하나와 UI 전달 작업 하나만 보관하고 구독 화면이 없으면 UI용 비트맵 복사본을 만들지 않습니다. 프레임의 실제 수신 시각으로 오래된 입력을 거부합니다.

Gemini 요청에만 암호화 저장소에서 읽은 개인 키를 사용하며 키·이미지는 서비스 Intent나 알림에 넣지 않습니다. 자동 실행은 사용자가 앱 화면에서 시작하고 재부팅·강제 중지 후 자동 재시작하지 않습니다. 종료 시 스트림·BLE·HTTP·음성·모델·wake/Wi-Fi lock을 정리하며 긴 로컬 연산은 작업 스레드에서 완료한 뒤 해제합니다.

## 실기기 확인 필요

실제 Android 휴대폰과 ESP32/HM-10이 개발 환경에 연결되어 있지 않습니다. Android 서비스 시작·알림 중지, 화면 끄기·다른 앱 전환·최근 앱에서 제거·장시간 유지, 제조사 절전, 메모리 회수, Wi-Fi/모바일 데이터 동시 사용, Android Keystore, 카메라 FPS·AI 속도·RAM·발열, BLE 재연결·보드 수신, TTS는 확인하지 않았습니다. 실제 기기의 실행·성능 보장으로 해석하면 안 됩니다.

이전 0.4.0에서 수행한 개발 서버의 실제 Gemini API HTTP 200 확인은 [당시 검증 기록](DUAL_AI_VALIDATION.md)에 있습니다. 이번 서비스의 Android 동작 검증을 대신하지 않습니다.

## 배포 상태

설치 파일은 개발 환경의 `/workspace/shared/downloads/giljabi-0.5.0-full-debug.apk`에 준비했습니다. 로컬 APK의 서명은 기존 GitHub 0.4.0 배포와 다릅니다. 기존 앱 삭제 후 설치해야 하며 개인 설정·API 키·모델 복사본이 삭제됩니다. 모델 원본은 APK에 있어 첫 사용 시 다시 준비합니다.

공개 GitHub 업로드는 자동 승인 검토가 차단했습니다. 수정 소스와 바이너리를 해당 공개 저장소에 게시하는 사용자 승인이 필요하다는 사유이며, 원격 push·릴리스·공개 다운로드는 아직 수행하지 않았습니다. `android-v0.5.0-full` 공개 릴리스가 존재한다고 안내하지 않습니다.
