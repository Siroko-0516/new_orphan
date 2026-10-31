# 길잡이 테스트 APK

## 최신 · 로컬 AI / Gemini 선택 · 0.4.0-full

**[giljabi-0.4.0-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.4.0-full/giljabi-0.4.0-full-debug.apk)** · [릴리스와 SHA-256](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.4.0-full)

앱 상단에서 **로컬 AI · 오프라인 / 클라우드 AI · Gemini** 두 가지 중 하나를 선택합니다. 로컬 모델 두 파일은 계속 APK에 포함되어 별도 선택·다운로드가 필요 없습니다. 설치 파일은 약 1.8GB이며 내부 여유 공간 6GB 이상을 권장합니다.

1. 인터넷 연결에서 APK를 휴대폰에 내려받습니다. **이번 0.4.0 배포 APK는 이전 0.3.0 배포 APK와 서명이 달라 기존 앱을 삭제한 뒤 설치해야 합니다.** 삭제하면 앱 내부 데이터와 준비된 모델 복사본도 삭제됩니다. 새 APK에 모델이 들어 있으므로 첫 준비 후 다시 사용할 수 있습니다.
2. 로컬은 **로컬 AI** 선택 → 사진 준비 → **기기에서 사진 분석**. 인터넷과 API 키가 필요 없습니다.
3. Gemini는 앱의 **Gemini API 키 · 모델 설정**에 본인 키 입력 → **클라우드 AI · Gemini** 선택 → 사진 준비 → **Gemini로 사진 분석**. 사진·질문이 Google로 전송되고 인터넷·할당량이 필요하며 요금이 발생할 수 있습니다. 키를 채팅에 보내지 마세요.
4. ESP Wi-Fi에 연결한 상태에서 Gemini도 쓰려면 모바일 데이터 등 별도 인터넷 연결을 켜야 합니다. 카메라 Wi-Fi 연결은 유지합니다.

기본 Gemini 모델은 `gemini-3.1-flash-lite`입니다. 모드 선택은 저장하고, 분석 버튼을 누른 때에만 사진 한 장을 처리합니다. 실패 시 다른 모드로 자동 전환하지 않습니다. YOLO·BLE·기본 오프라인 음성은 휴대폰에서 실행합니다.

빌드·Lint·JVM 테스트 53개와 실제 Gemini 사진 요청(개발 환경에서 HTTP 200·한국어 응답)을 확인했습니다. 배포 파일의 APK 서명·체크섬·내장 모델도 확인했습니다. 실제 Android Keystore, ESP Wi-Fi/모바일 데이터 동시 연결과 휴대폰 RAM·속도·BLE·음성은 실기기 확인이 필요합니다. [사용 안내](../android/AI_MODES.md) · [검증 기록](../android/DUAL_AI_VALIDATION.md).

## 이전 로컬 AI 모델 내장 · 0.3.0-full

**[giljabi-0.3.0-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.3.0-r3-full/giljabi-0.3.0-full-debug.apk)** · [릴리스 안내와 SHA-256](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.3.0-r3-full)

Android 8 이상 ARM64용입니다. MiniCPM-V 4.7 1B Q4_K_M 언어 모델과 해당 비전 mmproj를 모두 포함하므로 **모델 파일을 따로 받거나 선택하지 않습니다.** 설치 파일은 약 **1.8GB**이고 첫 AI 준비 시 앱 내부에 약 1.8GB가 추가로 필요합니다. 다운로드·설치·준비를 위해 여유 저장 공간 **6GB 이상**을 권장합니다.

1. 인터넷이 되는 연결에서 APK를 내려받고 휴대폰의 **내 파일 → 다운로드 → APK**로 설치합니다. 이전 테스트 앱과 서명 충돌로 설치가 안 되면 기존 앱을 삭제한 뒤 설치하세요. 삭제하면 기존 앱 내부 데이터·모델 복사본이 삭제됩니다. full 버전은 원본 모델을 다시 선택할 필요가 없습니다.
2. 휴대폰을 ESP32-CAM Wi-Fi에 연결하고, 앱에서 `http://192.168.4.1:81/stream` → **ESP 영상 + YOLO 시작**을 누릅니다. 브라우저의 별도 카메라 스트림은 닫습니다.
3. **기기에서 사진 분석**을 누르면 앱에 포함된 AI를 준비한 뒤 그때의 사진 한 장을 설명합니다. 첫 준비에 시간이 걸리며 이후에는 완료된 복사본을 재사용합니다. 분석을 자동 반복하지는 않습니다.
4. 아두이노 연결은 **BLE 기기 검색 → HM-10 선택 → 연결 → TEST 신호**로 확인합니다. **새 물체 감지 시 아두이노 신호 전송**을 켜면 감지 확인 후 `OBSTACLE\n`을 전송합니다.

모델 준비·추론은 기기 내에서 실행하며 서버 API 키가 필요하지 않습니다. 한국어 설명은 실험적이고, 오프라인 TTS는 휴대폰에 해당 음성이 설치되어 있어야 합니다. 실제 휴대폰의 속도·최대 RAM·카메라·BLE는 실기기 확인이 필요하며, 3~4GB RAM에서의 실행을 보장하지 않습니다. [기능·빌드 범위](../android/MODEL_BUNDLE.md) · [로컬 빌드 검증](../android/FULL_VALIDATION.md).

APK가 100MB를 초과하므로 이 폴더의 Git 파일 대신 GitHub Releases에서 제공합니다. 해당 릴리스의 APK 옆 `.sha256`이 **배포 파일**의 해시이며, 개발 환경에서 따로 빌드한 APK의 해시와 다를 수 있습니다.

## ESP 원본 영상 속도 확인용 펌웨어

크롬에서 ESP 영상 자체가 끊기는 경우에는 [CameraWebServerFast-v1.zip](CameraWebServerFast-v1.zip)을 전부 압축 해제하고 내부 `CameraWebServerFast/CameraWebServerFast.ino`를 Arduino IDE로 엽니다. 이는 **ESP32-CAM에 업로드하는 카메라 코드**이며 Android APK나 별도 아두이노 HM-10 수신 코드가 아닙니다.

구형 AI Thinker ESP32-CAM용으로 매 부팅 QVGA 320×240 / JPEG quality 20, PSRAM이 있으면 버퍼 2개와 최신 프레임 획득, 자체 AP 모드를 사용합니다. 스트리밍 중 3초마다 시리얼 모니터에 `STREAM` FPS·프레임 크기·프레임 대기·처리/HTTP 쓰기 시간을 출력합니다. 실제 FPS 향상을 보장하지 않습니다. [설치·측정·출처와 검증 범위](../arduino/CameraWebServerFast/README.md)를 확인하세요. 이 환경에서 ESP32 보드 컴파일과 실물 성능 측정은 실행하지 않았고, ZIP 구성·무결성·동봉한 공식 파일의 SHA-256 일치를 확인했습니다.

## 이전 Android 앱 · 0.2.0

이전 파일은 **[giljabi-0.2.0-debug.apk](giljabi-0.2.0-debug.apk)**이며 Android 8 이상 ARM64 기기용 디버그 빌드입니다. YOLO 가중치는 포함되지만 MiniCPM 언어 GGUF·비전 mmproj 파일은 포함되지 않습니다. 모델 내장 APK는 위의 0.3.0-full을 사용하세요.

휴대폰에서 APK를 내려받고 **내 파일 → 다운로드 → APK**로 설치하세요. PC의 VS Code에서 여는 파일이 아닙니다. 학교 관리 기기는 담당자에게 설치 허용을 받아야 합니다.

**0.1.0과 이번 0.2.0은 디버그 서명 키가 다릅니다.** 0.1.0이 설치돼 있으면 기존 앱 삭제 후 설치해야 합니다. 삭제하면 앱 내부에 복사한 모델 파일도 삭제되므로 원본 GGUF·mmproj를 보관하고 새 앱에서 다시 선택하세요. 원본 파일은 앱이 삭제하지 않습니다.

1. 휴대폰을 ESP32-CAM Wi-Fi에 연결 → 앱에서 `http://192.168.4.1:81/stream` → **ESP 영상 + YOLO 시작**. MiniCPM을 준비하지 않아도 감지할 수 있습니다.
2. **BLE 기기 검색** → HM-10 선택 → 연결 → **아두이노 TEST 신호 보내기**. [UNO/Nano용 아두이노 수신 예제](../arduino/hm10_receiver/README.md)를 별도 아두이노에 업로드하고 확인하세요. ESP 카메라 펌웨어에 덮어쓰는 코드가 아닙니다.
3. TEST 수신 확인 후 **새 물체 감지 시 아두이노 신호 전송**을 켜면 `OBSTACLE\n`을 씁니다. 물체 확인 3프레임, 중복 억제, 2초 간격을 적용합니다. 실제 충돌 위험 판단은 아닙니다.
4. 장면 설명도 쓰려면 언어 모델과 그 모델용 mmproj 파일을 선택하고 불러온 뒤 **기기에서 사진 분석**을 누릅니다. 최신 장면 한 장을 고정해 분석합니다. 기기에 설치된 오프라인 TTS 음성으로 읽습니다.

자세한 범위·설치·라이선스는 [Android 설명서](../android/README.md)를 참고하세요. Gemini/GPT·ElevenLabs·별도 Supertonic·백그라운드 실행은 이 버전에 포함하지 않습니다.

## 검증 결과와 한계

0.2.0의 ARM64 APK 빌드, v2 APK 서명, Android Lint(오류 0, 경고 있음), JVM 테스트 **15개 통과(실패·건너뜀 0)**, 패키지 내 YOLO 모델 해시 및 네이티브 공유 라이브러리 의존성을 확인했습니다. 연속 MJPEG 보존과 크기 제한, YOLO 좌표/NMS, 새 물체 확인·중복 억제·누락·재연결·신호 간격을 검사했습니다.

**실제 휴대폰의 ESP 영상 수신·ONNX/MiniCPM 추론·TTS, 실제 HM-10 연결 및 아두이노 수신은 이 빌드 환경에서 검증하지 않았습니다.** 실제 기기에서 연결·성능·메모리를 확인해야 합니다. 앱의 BLE 쓰기 완료는 아두이노의 수신 ACK가 아닙니다.

APK는 `android/` 소스에서 빌드했습니다. 모델 추론은 CPU를 사용하며 보이는 답변이 정확하다고 보장하지 않습니다. 기본 TTS는 기기에 설치된 오프라인 음성만 사용합니다. 포함된 오픈소스 라이선스는 APK의 assets/licenses와 소스에서 확인할 수 있습니다.

함께 제공한 `.sha256` 파일은 다운로드 무결성 확인용입니다. Windows에서는 다음 명령의 해시를 비교할 수 있습니다.

```bat
certutil -hashfile giljabi-0.2.0-debug.apk SHA256
```

APK를 다시 빌드하려면 GitHub Actions의 **Android test APK** 워크플로를 수동 실행하거나 Android Studio에서 `android/`를 엽니다. 다른 빌드 환경의 디버그 서명 키가 다르면 기존 앱을 그대로 업데이트하지 못할 수 있습니다.
