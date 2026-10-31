# 0.4.0 · 로컬 / Gemini 선택 검증

2026-10-31 기준입니다. 실제 Android 기기에서의 실물 검증과 개발 환경의 검증을 구분합니다.

## 코드와 빌드

ARM64 full APK `0.4.0-full`, versionCode 5, Android 8 이상(minSdk 26). 로컬 빌드의 `assembleDebug`, `testDebugUnitTest`, `lintDebug`가 성공했습니다. JVM 테스트 **53개 통과**, 실패·오류·건너뜀 0. Android Lint 오류 0, 경고 있음.

기존 36개 테스트에 Gemini 클라이언트 12개, 실제 로컬 HTTP POST 1개, AES-GCM 암호화 검증 4개를 추가했습니다. 테스트 범위는 다음과 같습니다.

- 선택된 JPEG와 질문의 요청 본문, 키를 헤더에만 넣는 전송 형식.
- thought 파트 제외, 한국어 응답·제한·빈 응답·잘린 응답 처리.
- 429 할당량 오류, 리다이렉트 거부, 헤더 시간 초과, 응답 크기 제한.
- 취소 전 업로드 방지, 취소 뒤 늦게 도착한 응답 스트림 정리.
- 다른 난수 IV, 암호문 변조·다른 키·잘못된 형식의 복호화 실패.
- 실제 루프백 TCP/HTTP POST와 JSON 응답 읽기.

암호화 테스트는 JVM의 AES-GCM 연산을 검사하며 Android Keystore 하드웨어나 SharedPreferences 저장을 실기기에서 실행한 것은 아닙니다.

## 실제 Gemini API 확인

모델 목록 조회 HTTP 200에서 기본 모델 `gemini-3.1-flash-lite`의 generateContent 지원을 확인했습니다. 앱의 `GeminiClient.payload`로 엔진에 포함된 공개 풍경 시험 사진의 요청 본문을 만들고, 개발 환경의 기존 인증 바인딩으로 Google 공식 API에 POST했습니다. 응답 HTTP **200**, finishReason **STOP**이었으며 앱의 `GeminiClient.response`로 한국어 두 문장의 최종 답변을 읽었습니다.

개발 환경의 API 키 값은 코드·요청 본문·APK·GitHub CI에 저장하거나 포함하지 않았습니다. 시험은 Android 기기의 직접 네트워크나 Keystore 경로를 검증하지 않습니다. 응답 내용 전체의 사실성을 평가한 품질 벤치마크도 아닙니다.

## 로컬 APK 확인

로컬 APK: 1,805,275,276 바이트. SHA-256: `a841a7352e673dfaf7f98fabdc333a203506127cfebd7c6dd7829a510914beb0`.

두 내장 GGUF의 크기·SHA-256·ZIP 무압축 저장이 기존 고정 모델 명세와 일치했습니다. 앱 DEX에 로컬/Gemini 선택 UI, Keystore 사용과 Google API 키 헤더 설정이 들어 있고 테스트용 가짜 키가 들어 있지 않은 것을 확인했습니다. APK v2 서명과 16KB ZIP 정렬 검사가 통과했습니다. 배포 파일은 CI에서 따로 빌드·서명하므로 별도 해시와 인증서를 확인해야 합니다.

## 공개 배포 APK 확인

[CI 실행 41174982520](https://github.com/Siroko-0516/new_orphan/actions/runs/41174982520)은 `60cce55` 소스의 `android-v0.4.0-full` 태그를 빌드했습니다. 공개 릴리스 URL에서 APK와 체크섬을 실제로 내려받아 다음을 확인했습니다.

- APK 크기: 1,805,274,860 바이트.
- SHA-256: `42c9faca1081c75b6930e2152772848a048212e924c037d6446d0b57df1ded45` — 릴리스 체크섬과 일치.
- 버전 `0.4.0-full`, versionCode 5. APK v2 서명 유효, 16KB ZIP 정렬 통과.
- 인증서 SHA-256: `14530ca758dfea079e8af3f304471699a1b46b6cdf0e651b9d0a4766ef5fcca3`.
- 두 모델 크기·SHA-256·무압축 저장, 라이선스·명세·YOLO 해시 일치.
- 로컬/Gemini UI·암호화 키 저장 클라이언트 포함, 테스트용 가짜 키 미포함.
- 인터넷 경로 요청 권한과 TTS 서비스 가시성 선언 포함.

0.3.0 배포판의 인증서(`ba5264c3…`)와 다르므로 기존 앱 위에 업데이트 설치할 수 없습니다. 이 배포판은 기존 앱 삭제 후 설치가 필요하고 앱 내부 데이터·모델 복사본이 삭제됩니다. CI 캐시의 태그 간 키 재사용을 가정하지 않고 실제 인증서 비교 결과를 설치 안내에 적용했습니다.

다운로드: <https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.4.0-full/giljabi-0.4.0-full-debug.apk>.

## 실기기 확인이 필요한 부분

Android 화면의 실제 터치·상태 복원, 실제 Android Keystore 저장·삭제, ESP Wi-Fi와 모바일 데이터 동시 연결, VPN별 연결, 로컬 VLM 최대 RAM·속도, BLE와 아두이노, 오프라인 음성 출력은 아직 실기기에서 확인하지 않았습니다. 기본 로컬 모델과 앱의 수신 경로는 기존 버전에서 이어 쓰지만 이 기록이 실물 동작을 보장하지는 않습니다.
