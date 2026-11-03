# 길잡이 테스트 APK

## 최신 · 현재 위치·전화·지도 연결 / GPT 추가 · 0.9.3-full

**[giljabi-0.9.3-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.3-full/giljabi-0.9.3-full-debug.apk)** · [릴리스·검사 결과](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.9.3-full)

- **“내 위치가 어디야”**를 휴대폰 위치 조회로 연결하고 실제 주소/좌표·정확도·확인 시각과 지도 링크를 표시합니다.
- 연락처 전화는 확인 안내 후 **“네”**를 받은 경우에만 시스템 전화 API로 발신을 요청합니다. 권한·취소·만료와 한 번만 발신하는 흐름을 검사합니다.
- 지도 검색 서버가 실패해도 실제 좌표의 지도 검색 링크를 준비합니다. 병원 정보와 거리를 만들어내지 않습니다.
- **GPT 사진·보행 분석과 음성비서**를 추가합니다. 본인의 별도 OpenAI API 키가 필요하며 ChatGPT 구독과 API 결제는 별개입니다.
- 로컬·Gemini·기존 음성·카메라·BLE와 버튼 녹음 방식은 유지합니다. **UNO 정식 v5는 그대로 사용합니다.**
- **공개 0.9.2와 서명이 달라 기존 앱 삭제 후 설치해야 합니다.** 삭제하면 저장한 API 키·설정·준비한 모델이 초기화됩니다. 개인 키를 다시 입력할 준비를 하고 설치하세요.
- 단위 검사 **295개**, lint 오류 0, Android 15 위치·녹음·카메라 검사와 UNO·네이티브 검사 통과. 공개 APK의 체크섬·서명·16KB ZIP/ELF 정렬과 모델 해시도 확인했습니다. 실제 SIM·GPS 수신과 개인 OpenAI 키의 응답은 기기 확인이 필요합니다.

APK는 1,828,272,025바이트(약 1.83GB), SHA-256은 `ca00af824ec7e62f175f4da4decedad96f542193cebe0aae8054e9d0d5260c74`입니다.

[위치·전화·지도·GPT 사용법](../android/PHONE_LOCATION_GPT.md) · [검증 기록](../android/PHONE_LOCATION_GPT_VALIDATION.json).

추가 컴퓨터 검사: 실제 Android 전화 서비스의 [발신 화면](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.3-full/phone-system-screen.png)과 앱의 [GPS 좌표·지도 링크 화면](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.3-full/location-system-screen.png)을 확인했습니다. GPS는 주입한 테스트 좌표이며 실제 위성 수신과 통신망 통화는 검증하지 않았습니다.

## 이전 · 버튼으로 녹음 시작·전송·답변 중단 · 0.9.2-full

**[giljabi-0.9.2-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.2-full/giljabi-0.9.2-full-debug.apk)** · [릴리스·검사 결과](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.9.2-full)

- **버튼 한 번 → 녹음 시작 / 말을 마치고 다시 한 번 → 녹음 전송.** 답변 중 버튼은 이전 답변·요청을 취소하고 새 녹음을 시작합니다.
- 말소리로 자동 중단하지 않으며, 무음으로 전송하지 않습니다. 답변 후에는 마이크를 닫고 다음 버튼을 기다립니다. 최대 60초 녹음 후에도 버튼을 눌러야 전송합니다.
- 앱·알림·보드 버튼에 같은 동작을 적용합니다. 기존 카메라·로컬/Gemini·음성·전화·GPS·독립 BLE 연결을 유지합니다.
- **[UNO 정식 v5](https://raw.githubusercontent.com/Siroko-0516/new_orphan/main/downloads/Giljabi-UNO-HM10-SG90-v5.zip)는 그대로 사용합니다.** 임시 모터 점검 코드가 아닌지 확인하세요.
- 단위 검사 **263개**, lint 오류 0, Android 15 실제 녹음·완료·취소 후 재시작과 기존 카메라·네이티브 검사 통과. 공개 APK의 서명·정렬·모델 해시와 버튼 코드 포함을 다시 확인했습니다.
- 파일은 **1,828,252,997바이트(약 1.83GB)**입니다. **공개 0.9.1과 서명이 달라 삭제 후 설치해야 하며 키·설정·준비한 모델이 초기화됩니다.**

[버튼 음성비서 사용법](../android/ASSISTANT.md) · [공개 파일·검증 데이터](../android/BUTTON_TURNS_VALIDATION.json). 실제 보드·이어폰·전화 통합 동작은 기기에서 확인합니다.

SHA-256: `3ae9295687838b0ea3b93ad834ef1dddb68bb92314d2f3c84c87cd1474e8f476`.

## 이전 · AI 중지 후 BLE 연결 유지 · 0.9.1-full

**[giljabi-0.9.1-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.1-full/giljabi-0.9.1-full-debug.apk)** · [릴리스·체크섬](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.9.1-full)

- AI·음성비서 중지나 화면 이동에 BLE 연결까지 닫히던 경로를 수정했습니다. 별도 보드 연결 서비스가 연결을 유지하며 앱/보드 연결 알림의 **BLE 연결 해제**로 종료합니다. 강제 종료·전원 단절 등은 별도입니다.
- 카메라·비서 실행 상태와 관계없이 TEST를 실제 연결 중인 보드로 보냅니다. BLE 연결과 TEST에는 AI 키나 마이크 권한이 필요하지 않습니다.
- **BLE 검색 → HMSoft 선택 → 선택한 BLE 기기 연결 → TEST**로 먼저 확인하세요. 음성비서는 별도로 시작합니다. 기존 로컬 모델·Gemini·음성·카메라 입력은 포함합니다.
- 단위 검사 **258개**, lint 오류 0, Android 15 서비스 수명·카메라·VAD 및 기존 네이티브 검사 통과. 공개 APK의 체크섬·v2 서명·16KB 정렬·내장 모델 해시를 다시 확인했습니다. 실물 무선 연결·서보 동작은 별도 확인이 필요합니다.
- 파일은 **1,828,257,709바이트(약 1.83GB)**입니다. **공개 0.9.0과 서명이 달라 삭제 후 설치해야 하며 키·설정·준비한 모델이 초기화됩니다.**

[연결 사용법·검사 범위](../android/BLE_CONNECTION.md) · [검증 데이터](../android/BLE_CONNECTION_VALIDATION.json) · [UNO v5 · 버튼 음성비서 / 연결 알림 처리](https://raw.githubusercontent.com/Siroko-0516/new_orphan/main/downloads/Giljabi-UNO-HM10-SG90-v5.zip) · [SG90 D2 단독 시험](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.1-full/Giljabi-SG90-D2-Test-v1.zip).

SHA-256: `e4613a7c880dd852fef613c51cea5616c86ff746ca15aec5cda2efd3cf24567d`.

## 이전 · 버튼 음성비서 / 답변 끊고 듣기 / Gemini 연결 검사 · 0.9.0-full

**[giljabi-0.9.0-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.9.0-full/giljabi-0.9.0-full-debug.apk)** · [릴리스·체크섬](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.9.0-full)

- HM-10의 `ASSIST` 버튼 알림으로 듣기를 시작합니다. 답변 중 버튼/말소리로 끊고 다시 질문할 수 있으며 이전 요청·음성을 취소합니다. 자동 끼어들기는 이어폰·반향 제거 상태에 따라 실제 기기에서 확인해야 합니다.
- 저장된 연락처 전화는 실제 음성 확인 후에만 요청합니다. GPS와 실제 OpenStreetMap 기록으로 주변 병원·약국 등을 검색하며 별도 유료 지도 키는 필요하지 않습니다.
- **503 서버 혼잡을 인터넷·모델 오류와 구분**합니다. Gemini 설정의 **Google 모델 목록 확인 → 선택 모델 연결 검사 → 저장**으로 확인하세요. 모델 목록 성공과 생성 성공은 다르며 서버 혼잡 자체를 앱이 없애지는 못합니다.
- Gemini/음성비서는 기존 ElevenLabs 고정 여자·남자 음성을 우선 사용하고 실패하면 기본 TTS로 대체합니다. **로컬 AI는 계속 기본 TTS만 사용합니다.** 카메라 입력·모델 내장·조용한 보행 판단을 유지하며 새 AI 보행 경고에 서보 신호를 보냅니다.
- 단위 검사 **246개**, lint 오류 0, Android 15 카메라·실제 VAD 모델·비서 대기 서비스 검사와 기존 GGUF 네이티브 검사를 통과했습니다. 실제 보드·이어폰·위치·SIM 통화의 통합 동작과 지도 서버 응답은 실기 확인이 필요합니다. 로컬 AI 2초 이하 달성은 미측정입니다.
- 파일은 **1,828,254,669바이트(약 1.83GB)**이며 여유 공간 6GB 이상을 권장합니다. 공개 APK의 체크섬·v2 서명·16KB 정렬·내장 모델 해시를 다시 확인했습니다.
- **공개 0.8.2와 서명이 달라 기존 앱을 삭제한 후 설치해야 합니다. 삭제하면 키·설정·준비된 모델이 초기화됩니다.**
- 설치 후 **Gemini 개인 키 저장 → BLE 검색·HM-10 선택 → 음성비서 시작**을 누르고 필요한 마이크·주변 기기·위치·연락처·전화 권한을 허용합니다. 무선이어폰은 휴대폰에 별도로 연결합니다.

[음성비서 사용법·검증 범위](../android/ASSISTANT.md) · [핀 수정 UNO v3 ZIP](https://raw.githubusercontent.com/Siroko-0516/new_orphan/main/downloads/Giljabi-UNO-HM10-SG90-v3.zip) · [친구용 배선 설명](../arduino/hm10_sg90_button/README.md) · [검증 데이터](../android/ASSISTANT_VALIDATION.json).

SHA-256: `11f4ef1e56e133db29986c36b01b3ea6b6ed04927df123f4ea3e95a32421ffaf`.

## 이전 · 보행 상황 판단 / 이미지 GPU / 한국어 / 음성 대체 · 0.8.2-full

**[giljabi-0.8.2-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.8.2-full/giljabi-0.8.2-full-debug.apk)** · [릴리스·체크섬](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.8.2-full)

- YOLO ‘화면 어디 감지’ 음성을 제거했습니다. Gemini는 이전·최신 사진을 계속 분석하되 같은 상황이면 말하지 않고, 보행에 필요한 새 방해물·분명한 악화가 있을 때 짧게 안내합니다. 지팡이 스윙·잠깐의 감지 소실·개수 변동으로 반복하는 것을 억제합니다.
- **로컬 AI는 기본 한국어 TTS만 사용합니다.** Gemini는 기존 ElevenLabs 여자/남자 음성 우선이며 오류·지연에는 기본 TTS로 대체합니다. 중지·지난 상황에는 대체 음성도 내지 않습니다.
- 로컬 비전 인코더에 선택형 Vulkan GPU와 CPU 대체를 추가했습니다. 긴 한국어 지시를 따라 쓰던 경로는 짧은 영어 분석·기기 내 한국어 번역과 JSON 문법 제약으로 바꿨습니다. 앱에서 **오프라인 한국어 번역 준비 · 최초 인터넷**을 한 번 눌러 주세요.
- **휴대폰에서 2초 이하 달성은 아직 미측정**입니다. 5초가 지난 사진의 새 보행 음성은 읽지 않습니다. 카메라만으로 실제 2~3m·진행 방향·머리 높이를 측정하지 않으며 모든 위험 탐지를 보장하지 않습니다.
- ESP·휴대폰 후면/전면·수동 사진·BLE·백그라운드·내장 모델을 유지한 ARM64 APK입니다. 파일 1,826,247,214바이트, 약 1.83GB. 여유 공간 6GB 이상을 권장합니다.
- **공개 0.7.0과 서명이 달라 기존 앱을 삭제한 후 설치해야 합니다. 삭제하면 키·설정·준비된 모델이 초기화됩니다.**
- 단위 검사 **202개**, lint 오류 0, Android 15 카메라·화면 밖 실행, 실제 GGUF 네이티브 JSON·영어 설명 검사 통과. 공개 APK를 다시 받아 모델 해시·v2 서명·16KB 정렬·GPU/CPU 라이브러리를 확인했습니다.

[사용법·검증 범위](../android/WALKING_GUIDANCE.md) · [검증 데이터](../android/WALKING_GUIDANCE_VALIDATION.json). SHA-256: `19b231c8b7fa3bb875160bbc5238168e69ba2a064fdb1fd96ad71a03b255f99c`.

## 이전 · 변화 안내 / 로컬 가속 / ElevenLabs · 0.7.0-full

**[giljabi-0.7.0-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.7.0-full/giljabi-0.7.0-full-debug.apk)** · [릴리스·체크섬](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.7.0-full)

- 같은 장면은 반복 분석하지 않고, 시작 시 한 번과 확인된 큰 변화에 AI 설명을 요청합니다. 새 장애물은 YOLO가 여러 프레임에서 확인해 짧게 안내하며 긴 AI 분석을 기다리지 않습니다.
- 단색·매우 어둡거나 밝은 영상의 AI 추론을 생략하고 확인이 어렵다고 표시합니다. 장면이 바뀐 뒤의 늦은 설명은 음성 안내에서 제외합니다.
- ARM dot-product·FP16·i8mm·KleidiAI와 짧은 출력·보조 입력 축소·더미 예열 제거를 적용했습니다. **실제 휴대폰의 최적화 후 속도와 전체 로컬 AI 1~2초 완료는 아직 미검증**입니다.
- Gemini AI 설명은 ElevenLabs의 기존 여자(기본)/남자 음성, 로컬 AI 설명과 빠른 YOLO 알림은 휴대폰 오프라인 음성입니다. **ElevenLabs API 키 · 고정 음성 설정**에 개인 키를 입력합니다.
- ESP·휴대폰 후면/전면·로컬/Gemini 선택·사진·기본 TTS·BLE·백그라운드를 유지하며 모델 두 파일을 내장한 약 1.8GB ARM64 APK입니다.
- **공개 0.6.1과 서명이 달라 기존 앱 삭제 후 설치해야 합니다.** 삭제하면 개인 키·설정·준비된 모델이 초기화됩니다. 내장 모델은 다시 준비하며 Gemini·ElevenLabs 키는 앱 설정에 입력합니다.
- 단위 검사 123개, Android 15 카메라·백그라운드 검사, 네이티브 회귀 검사 통과. 실제 ElevenLabs 여자·남자 음성 생성·MP3 디코딩 확인. 공개 APK 모델 해시·서명·16KB 정렬과 가속 라이브러리 확인.

[사용법·속도와 정확도 한계·검증](../android/QUICK_GUIDANCE.md) · [검증 데이터](../android/QUICK_GUIDANCE_VALIDATION.json).

## 이전 · 휴대폰 후면·전면 카메라 추가 · 0.6.1-full

**[giljabi-0.6.1-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.6.1-full/giljabi-0.6.1-full-debug.apk)** · [릴리스·체크섬](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.6.1-full)

**영상 입력 선택 → 휴대폰 후면 카메라 / 휴대폰 전면 카메라**를 선택하고 카메라 권한을 허용하면 ESP 없이 사용할 수 있습니다. **영상 + YOLO 시작**으로 감지하거나 AI를 고른 뒤 **실시간 AI + 백그라운드 시작**으로 자동 설명합니다. ESP 입력·로컬 MiniCPM / Gemini·음성·HM-10 BLE·사진 분석과 0.5.3의 네이티브 템플릿 수정도 유지합니다.

- Android 8 이상 ARM64, 실제 로컬 모델 두 파일 포함 **약 1.8GB**. 첫 모델 준비를 위해 내부 여유 공간 6GB 이상을 권장합니다.
- **기존 공개 0.5.3과 서명이 달라 기존 길잡이 삭제 후 설치해야 합니다.** 삭제하면 개인 API 키·설정·준비된 모델 복사본이 삭제됩니다. 내장 모델은 다시 준비합니다.
- 휴대폰 입력에는 ESP Wi-Fi·주소가 필요 없습니다. 로컬 AI는 인터넷 없이, Gemini는 개인 키·인터넷으로 실행합니다. Gemini 자동 설명은 사진을 Google로 반복 전송합니다.
- 자동 실행은 앱이 보이는 상태에서 시작하고 앱 또는 실행 알림에서 중지합니다. 입력을 바꾸려면 먼저 중지합니다. YOLO 전용 영상은 앱 화면을 벗어나면 중지합니다.
- Android 15 에뮬레이터의 실제 Camera2·YOLO, 후면·전면·홈 화면·화면 꺼짐·중지 후 재개방 검사 통과. 단위 검사 97개와 lint 오류 0, 실제 GGUF 네이티브 회귀 검사 통과. 실제 휴대폰의 장시간 실행·속도·BLE 실물 수신은 별도 확인이 필요합니다.
- 공개 APK 직접 다운로드 후 모델 해시·v2 서명·16KB ZIP/ELF 정렬 확인. SHA-256: `a7e0b035ff732cb275abb81234ed272354b77878ac338f26efa4b3cd143df61b`.

[사용법·검증 범위](../android/PHONE_CAMERA.md) · [검증 데이터](../android/PHONE_CAMERA_VALIDATION.json).

## 이전 · 로컬 AI 종료 원인 수정 / 키 입력 개선 · 0.5.3-full

**[giljabi-0.5.3-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.5.3-full/giljabi-0.5.3-full-debug.apk)** · [릴리스·체크섬](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.5.3-full)

상세 호출 기록으로 확인한 MiniCPM 템플릿 오류를 수정했습니다. 자동 분석 중 시스템 문장만 먼저 렌더링해 `Unable to generate parser for this template` C++ 예외가 발생했고, 잡히지 않은 예외가 앱을 SIGABRT로 종료했습니다. 이제 시스템·사진·질문을 한 대화로 렌더링하고 불필요한 응답 파서 생성을 사용하지 않습니다. JNI 예외도 Java로 전달합니다.

API 키 붙여넣기 정리와 이유별 입력 오류 표시도 포함합니다. 공백·줄바꿈·보이지 않는 서식 문자를 정리하고 마스킹된 키·설정 코드·길이·문자 문제를 구분합니다. 저장 자체가 Google 인증 성공을 의미하지 않으며 실제 요청에서 확인합니다. 키를 채팅으로 보내지 마세요.

- Android 8 이상 ARM64, 실제 로컬 모델 두 파일을 포함한 약 **1.8GB** APK입니다. 설치·첫 준비를 위해 내부 여유 공간 6GB 이상을 권장합니다.
- 이번 APK는 이전 공개 0.5.1과 서명이 달라 기존 앱 삭제 후 설치해야 합니다. 삭제하면 설정·API 키·모델 복사본도 삭제되고, 내장 모델은 첫 사용 시 다시 준비합니다.
- ESP Wi-Fi 연결 → 앱에서 `http://192.168.4.1:81/stream` 사용. 자동 분석·백그라운드는 **실시간 AI + 백그라운드 시작**, 중지는 앱 또는 실행 알림에서 합니다.
- 로컬은 인터넷 없이, Gemini는 개인 키와 인터넷 연결로 실행합니다. Gemini 자동 실행 중에는 사진을 반복 전송하며 데이터·API 요금이 발생할 수 있습니다.
- 실제 GGUF로 이전 오류 재현·수정 경로 검사, JNI 예외 변환 검사를 통과했습니다. 개발 서버에서 생산 JNI와 실제 모델 두 파일로 사진 설명을 3회 생성하고 정상 종료했습니다. 실제 Android 기기의 장시간 실행·속도·메모리는 별도 확인이 필요합니다.
- **ESP 수신 15초 타임아웃은 별도 연결 문제**이며 이 템플릿 수정으로 무선 통신까지 해결됐다는 뜻은 아닙니다. 남는 오류는 **앱 오류 기록 보기 · 복사**로 확인합니다. Android 12 이상은 시스템이 제공한 네이티브 abort 메시지·호출 기록도 읽습니다.

[확인된 원인·수정·실제 모델 검사](../android/NATIVE_TEMPLATE_FIX.md) · [API 키 입력·상세 기록](../android/KEY_AND_TRACE.md) · [자동 실행 안내](../android/BACKGROUND.md).

## 앱이 네이티브 오류로 종료될 때 · PC 로그 수집

앱의 종료 요약이 `네이티브 오류 종료`와 status 6(SIGABRT)을 표시하면 `Abort message`와 backtrace가 추가로 필요합니다. 앱의 마지막 저장 단계만으로 JPEG·YOLO·화면 렌더러 중 어느 코드가 중단됐는지 확정하지 않습니다.

- [Windows 로그 수집 도구 ZIP](giljabi-native-crash-collector.zip) · [소스와 사용법](../android/scripts/native-crash-collector/README.md)
- [Google 공식 ADB platform-tools ZIP](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)

공식 platform-tools를 압축 해제하고 도구의 CMD 파일을 adb.exe 옆에 복사합니다. 휴대폰 USB 디버깅을 켜고 연결·허용한 뒤 CMD를 실행하면 길잡이의 마지막 네이티브 충돌에서 메시지와 호출 스택을 추려 `giljabi-native.txt`에 저장합니다. 현재 설치와 로그를 그대로 유지하며 앱 데이터나 로그 버퍼를 지우지 않습니다. 기록은 자동 전송하지 않습니다. 원본 `giljabi-crash-local.txt`에는 다른 앱의 기록도 있을 수 있어 전체를 공유하지 마세요. 실제 Windows와 휴대폰에서 이 도구 실행은 아직 검증하지 못했습니다.

## 이전 · ESP 영상 종료 오류 대응 · 0.5.1-full

**[giljabi-0.5.1-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.5.1-full/giljabi-0.5.1-full-debug.apk)** · [릴리스와 SHA-256](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.5.1-full)

영상·YOLO 실행 중 종료 문제를 조사하며 이전 화면 이미지의 즉시 해제를 제거하고 YOLO 입력 메모리를 재사용하도록 수정했습니다. 실제 기기의 오류 스택을 아직 확보하지 못해 모든 종료 원인 해결을 확정하지는 않습니다.

- **이전 공개 0.5.0과 서명이 달라 기존 길잡이 삭제 후 설치해야 합니다.** 삭제하면 API 키·설정·준비된 모델 복사본이 삭제됩니다. 모델은 APK에 있어 첫 사용 시 다시 준비합니다.
- 로컬 모델을 모두 포함한 약 **1.8GB** APK입니다. Android 8 이상 ARM64용이며 내부 여유 공간 6GB 이상을 권장합니다.
- ESP Wi-Fi 연결 → 앱에서 `http://192.168.4.1:81/stream` → **ESP 영상 + YOLO 시작**으로 확인합니다. 브라우저의 별도 카메라 스트림은 닫습니다.
- 계속 종료되면 앱 재실행 → **앱 오류 기록 보기 · 복사**를 사용합니다. Android 11 이상은 시스템의 최근 종료 이유도 표시합니다. 기록은 자동 전송하지 않으며, 삭제·재설치 시 이전 기록이 사라질 수 있습니다.
- 로컬/Gemini 자동 분석·백그라운드·HM-10 BLE는 유지합니다. 자동 모드는 **실시간 AI + 백그라운드 시작**, 종료는 앱 또는 알림의 **중지**입니다. Gemini는 개인 키·인터넷이 필요하고 자동 실행 중 사진을 반복 전송해 요금이 발생할 수 있습니다.

빌드·테스트 **66개 통과**, Lint 오류 0과 공개 APK의 서명·체크섬·내장 모델을 확인했습니다. 실제 휴대폰에서 장시간 동작 확인은 필요합니다. [수정·검사 상세](../android/STREAM_CRASH_FIX.md) · [자동 실행 안내](../android/BACKGROUND.md).

## 이전 · 자동 AI 분석 / 백그라운드 실행 · 0.5.0-full

**[giljabi-0.5.0-full-debug.apk 다운로드](https://github.com/Siroko-0516/new_orphan/releases/download/android-v0.5.0-full/giljabi-0.5.0-full-debug.apk)** · [릴리스와 SHA-256](https://github.com/Siroko-0516/new_orphan/releases/tag/android-v0.5.0-full)

로컬 MiniCPM 모델 두 파일을 포함한 약 **1.8GB** APK입니다. Android 8 이상 ARM64용이며 설치·첫 AI 준비에 내부 여유 공간 6GB 이상을 권장합니다.

1. APK를 휴대폰에 내려받아 설치합니다. 이번 APK는 이전 공개 0.4.0 및 개발 환경의 로컬 0.5.0 APK와 서명이 달라 기존 앱을 삭제한 뒤 설치해야 합니다. 삭제하면 설정·저장한 API 키·준비된 모델 복사본도 삭제됩니다. 내장 모델은 첫 사용 시 다시 준비됩니다.
2. 휴대폰을 ESP32-CAM Wi-Fi에 연결하고 앱 주소를 `http://192.168.4.1:81/stream`으로 둡니다. 브라우저 카메라 스트림 탭을 닫습니다.
3. 앱 상단에서 **로컬 AI · 오프라인 / 클라우드 AI · Gemini** 중 하나를 선택합니다. Gemini는 앱에 본인 API 키를 입력하고 ESP Wi-Fi와 별도로 모바일 데이터 등 인터넷 연결이 필요합니다.
4. 질문·언어·음성·최소 분석 시작 간격을 정하고 **실시간 AI + 백그라운드 시작**을 누릅니다. ESP 영상·YOLO·선택한 AI의 반복 설명이 함께 실행됩니다. 화면을 벗어나도 서비스가 이어서 처리하도록 구성했습니다.
5. 종료는 앱의 **실시간 / 백그라운드 중지** 또는 실행 알림의 **중지**를 누릅니다. 알림을 허용하면 실행 상태와 중지 버튼을 알림창에서 사용할 수 있습니다.

한 번에 한 장면만 분석하고, 이전 분석이 끝난 뒤 새 프레임을 사용합니다. 기본 5초 간격은 5초마다 결과가 나온다는 뜻이 아닙니다. 실행 중 로컬/Gemini를 바꾸면 이전 결과를 버리고 새 모드로 계속 처리합니다. **Gemini 자동 실행 중에는 화면이 꺼져 있어도 사진·질문·YOLO 관측을 Google로 반복 전송하며 데이터·API 요금이 발생할 수 있습니다.** 다른 모드로 자동 전환하지 않습니다.

HM-10도 쓰려면 자동 실행 전 BLE 검색·선택·연결·TEST 수신을 확인하고 새 물체 신호 체크박스를 켭니다. 서비스가 선택한 BLE 연결을 유지·재시도하고 YOLO 이벤트에 `OBSTACLE\n`을 보냅니다. 강제 중지·재부팅 뒤 자동 실행하지 않으며 제조사 절전·메모리 회수로 종료될 수 있습니다.

빌드·Lint·JVM 테스트 **60개**와 내장 모델 해시·APK 서명을 검사했습니다. 실제 Android의 화면 끄기·장시간 실행·동시 네트워크·RAM·AI 속도·BLE·TTS는 실기기 확인이 필요합니다. [설정·중지·제약](../android/BACKGROUND.md) · [검증 기록](../android/BACKGROUND_VALIDATION.md).

## 이전 · 로컬 AI / Gemini 선택 · 0.4.0-full

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

크롬에서 ESP 영상 자체가 끊기는 경우에는 [CameraWebServerFast-v2.zip](CameraWebServerFast-v2.zip)을 전부 압축 해제하고 내부 `CameraWebServerFast/CameraWebServerFast.ino`를 Arduino IDE로 엽니다. 이는 **ESP32-CAM에 업로드하는 카메라 코드**이며 Android APK나 별도 아두이노 HM-10 수신 코드가 아닙니다.

구형 AI Thinker ESP32-CAM용으로 매 부팅 QVGA 320×240 / JPEG quality 20 / 상하·좌우 반전 켜짐, PSRAM이 있으면 버퍼 2개와 최신 프레임 획득, 자체 AP 모드를 사용합니다. 스트리밍 중 3초마다 시리얼 모니터에 `STREAM` FPS·프레임 크기·프레임 대기·처리/HTTP 쓰기 시간을 출력합니다. 실제 FPS 향상을 보장하지 않습니다. [설치·측정·출처와 검증 범위](../arduino/CameraWebServerFast/README.md)를 확인하세요. 이 환경에서 ESP32 보드 컴파일과 실물 성능 측정은 실행하지 않았고, ZIP 구성·무결성·동봉한 공식 파일의 SHA-256 일치를 확인했습니다.

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
