# 길잡이 테스트 APK

`giljabi-0.1.0-debug.apk`는 Android 8 이상 ARM64 기기용 디버그 빌드입니다. 약 25MB이며 YOLO 가중치는 포함되지만 MiniCPM GGUF·비전 가중치는 포함되지 않습니다. 실제 기기에서의 전체 추론과 ESP32 연결 검증은 아직 하지 않았습니다.

다운로드 후 앱에서 언어 모델과 그 모델에 맞는 mmproj 파일을 가져오세요. 자세한 범위·설치·라이선스·검증 한계는 [Android 설명서](../android/README.md)를 참고하세요. 학교 관리 기기는 담당자에게 설치 허용을 받아야 합니다.

APK는 `android/` 소스에서 빌드했습니다. 모델 추론은 CPU를 사용하며 보이는 답변이 정확하다고 보장하지 않습니다. 기본 TTS는 기기에 설치된 오프라인 음성만 사용합니다. 포함된 오픈소스 라이선스는 APK의 assets/licenses와 소스에서 확인할 수 있습니다.

함께 제공한 `.sha256` 파일은 다운로드 무결성 확인용입니다. Windows에서는 다음 명령의 해시를 비교할 수 있습니다.

```bat
certutil -hashfile giljabi-0.1.0-debug.apk SHA256
```

APK를 다시 빌드하려면 GitHub Actions의 **Android test APK** 워크플로를 수동 실행하거나 Android Studio에서 `android/`를 엽니다. 다른 빌드 환경의 디버그 서명 키가 다르면 기존 앱을 그대로 업데이트하지 못할 수 있습니다.
