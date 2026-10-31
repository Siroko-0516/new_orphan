# 0.3.0-full 검증 기록

2026-10-31, 실제 모델 내장 ARM64 로컬 빌드 기준입니다. GitHub Actions의 배포 APK는 CI에서 별도로 빌드·서명하므로 로컬 APK와 파일 해시·서명 키가 다를 수 있습니다.

- 버전: `0.3.0-full`, versionCode 4, Android 8 이상(`minSdk 26`), `arm64-v8a`.
- 언어 모델: `MiniCPM-V-4_7-1B-Q4_K_M.gguf`, 672,003,680 바이트.
- 비전 모델: `mmproj-model-f16.gguf`, 1,110,117,984 바이트.
- 게시자 고정 버전: `345adbfa02c4a4671e61b7c75502dc9bf24fa93d`.
- 모델 SHA-256과 라이선스 출처: [고정 모델 명세](model-specs/minicpm-1b.json).

## APK와 코드 검사

`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 성공. JVM 테스트 36개, 실패·오류·건너뜀 0. Android Lint 오류 0, 경고 있음.

실제 APK의 두 GGUF를 스트림으로 읽어 고정 명세의 크기와 SHA-256 일치를 확인했습니다. GGUF는 ZIP 무압축 저장입니다. 가중치 Apache-2.0 라이선스 해시, 내장 모델 명세, YOLO ONNX 원본 해시도 일치했습니다. APK v2 서명 검증과 `zipalign -c -P 16 4` 검사가 통과했습니다.

실제 APK의 ZIP 입력 스트림을 사용해 `BundledModels` 설치 코드로 1.78GB 모델 쌍을 준비하고, 완료 표시와 두 파일 크기 및 다음 실행의 캐시 재사용을 검사했습니다. 이 통합 확인은 JVM 최대 힙 128MiB에서 실행했습니다. Android `AssetManager`나 휴대폰 저장 장치 성능을 직접 측정한 것은 아닙니다.

로컬 APK 크기: 1,805,318,857 바이트. 로컬 APK SHA-256: `bd2c25a7eb4ed9107b27d97cfc80e9c00f74f8021a9e88a06ce6ffe0cc4554ae`. 로컬 디버그 인증서 SHA-256: `cf315b31860e2aaafebfd66bbbceb30e1097f5b5bb0ea02a231b758bd85f4e4a`. 이는 CI 배포 APK의 인증서나 해시를 대신하지 않습니다.

## 공개 배포 파일 확인

[CI 실행 41172640532](https://github.com/Siroko-0516/new_orphan/actions/runs/41172640532)은 `fb57566` 소스의 `android-v0.3.0-r3-full` 태그를 빌드했습니다. 공개 릴리스 URL에서 APK와 체크섬 파일을 실제로 다운로드해 다음을 확인했습니다.

- 파일 크기: 1,805,261,888 바이트.
- SHA-256: `bf4bd78506d175831eaf1e0993d257ccc180d7edc08f19c03d0ef71050627d5e` — 릴리스 체크섬과 일치.
- v2 APK 서명 유효. 배포 인증서 SHA-256: `ba5264c3aef305841c6f9a147cc51a186e258f595b1435d9923e6b2998b54219`.
- 배포 APK 안의 두 GGUF 크기·SHA-256·무압축 저장, 모델 라이선스·명세·YOLO 해시 일치.
- 16KB ZIP 정렬 검사 통과. Android Manifest에 TTS 서비스 가시성 선언 포함.
- 중단된 초기 모델 복사 후 저장 공간을 다시 확인하는 설치 코드 포함.

다운로드 주소: <https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.3.0-r3-full/giljabi-0.3.0-full-debug.apk>.

## 실제 가중치 추론 검사

동일 고정 엔진 `e0eca9d8f57ac8f5a5ad6191a8007b93436c1e71`을 Linux x86-64 CPU용으로 빌드해, 엔진에 포함된 풍경 사진 fixture `tools/server/webui/src/stories/fixtures/assets/1.jpg`를 분석했습니다. 실제 모델 두 파일의 로딩, 이미지 처리, 텍스트 생성이 종료 코드 0으로 완료됐습니다.

출력:

> A rocky outcrop sits in the foreground, framing a deep valley. A river winds through the green slopes beneath a bright sky.

이는 모델 조합과 엔진의 기본 호환성 확인이며 설명의 모든 사실을 검증한 벤치마크가 아닙니다. 이 시험은 Android 빌드와 동시에 실행했고 CLI의 이미지 처리 설정도 앱과 다르므로 서버 측 측정 시간을 휴대폰 예상 속도로 제시하지 않습니다.

## 확인하지 않은 범위

실제 Android 기기의 설치·첫 모델 준비·MiniCPM/ONNX 추론·최대 RAM·생성 속도·오프라인 TTS·ESP32 수신·HM-10/아두이노 실물 통신은 확인하지 않았습니다. 한국어 혼합 출력 문제를 모델 내장이 해결하지는 않습니다. 3~4GB RAM 기기에서의 실행은 실기기 측정이 필요합니다.
