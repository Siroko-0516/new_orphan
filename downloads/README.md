# 길잡이 테스트 APK

## ESP 원본 영상 속도 확인용 펌웨어

크롬에서 ESP 영상 자체가 끊기는 경우에는 [CameraWebServerFast-v1.zip](CameraWebServerFast-v1.zip)을 전부 압축 해제하고 내부 `CameraWebServerFast/CameraWebServerFast.ino`를 Arduino IDE로 엽니다. 이는 **ESP32-CAM에 업로드하는 카메라 코드**이며 Android APK나 별도 아두이노 HM-10 수신 코드가 아닙니다.

구형 AI Thinker ESP32-CAM용으로 매 부팅 QVGA 320×240 / JPEG quality 20, PSRAM이 있으면 버퍼 2개와 최신 프레임 획득, 자체 AP 모드를 사용합니다. 스트리밍 중 3초마다 시리얼 모니터에 `STREAM` FPS·프레임 크기·프레임 대기·처리/HTTP 쓰기 시간을 출력합니다. 실제 FPS 향상을 보장하지 않습니다. [설치·측정·출처와 검증 범위](../arduino/CameraWebServerFast/README.md)를 확인하세요. 이 환경에서 ESP32 보드 컴파일과 실물 성능 측정은 실행하지 않았고, ZIP 구성·무결성·동봉한 공식 파일의 SHA-256 일치를 확인했습니다.

## Android 앱

최신 파일은 **[giljabi-0.2.0-debug.apk](giljabi-0.2.0-debug.apk)**이며 Android 8 이상 ARM64 기기용 디버그 빌드입니다. YOLO 가중치는 포함되지만 MiniCPM 언어 GGUF·비전 mmproj 파일은 포함되지 않습니다.

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
