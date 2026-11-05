# 길잡이 · 필요한 변화만 안내하는 웹사이트

**포스터를 보고 방문하셨나요? [길잡이 프로젝트 소개·사용 방법·문의](inquiry-site/README.md)에서 확인하세요.**

휴대폰·PC 브라우저에서 카메라를 열고, 같은 상황은 반복해서 말하지 않는 보조 안내 웹 앱입니다. 기존 Android 앱은 [android/README.md](android/README.md)와 [APK 다운로드](downloads/README.md)에 그대로 있습니다.

- 휴대폰 후면·전면 카메라, 저장한 사진 분석, 조건이 맞는 ESP 영상 입력.
- **Gemini**는 이전·최근·최신 사진을 시간순으로 비교합니다. 같은 상황에는 분석만 하고 새 통로 방해물·분명한 위험 변화에 짧게 안내합니다.
- **로컬 AI**는 실제 브라우저 VLM과 기기 내 한국어 번역입니다. YOLO 물체 감지와 별도 기능이며, API 키나 이미지 서버가 필요하지 않습니다.
- **로컬 음성은 기본 한국어 TTS만**, Gemini는 기존 여자·남자 ElevenLabs 음성과 오류·지연 시 기본 TTS 대체를 사용합니다.
- YOLO 상자 개수·잠깐 소실·지팡이 스윙만으로 반복해서 말하지 않습니다. 5초가 지난 사진의 새 보행 음성은 시작하지 않습니다.
- 지원 브라우저에서는 선택형 HM-10 Web Bluetooth 연결·시험·새 통로 관측 신호를 사용할 수 있습니다. 기본으로 꺼져 있으며 장치는 직접 선택합니다.

## Cloudflare Pages에 올리기

공개 주소는 본인 Cloudflare 계정에서 배포가 완료될 때 생성됩니다. 현재 실행 환경에는 Cloudflare 계정 인증이 연결되어 있지 않아 계정의 프로젝트를 대신 생성하지 않습니다.

**가장 간단한 방법 — 업로드 ZIP**

1. [웹 릴리스](https://github.com/Siroko-0516/new_orphan/releases/tag/web-v1.0.0)에서 **`giljabi-web-static.zip`**을 내려받습니다.
2. Cloudflare의 **Workers & Pages → Pages 프로젝트 만들기 → 직접 업로드**에서 ZIP을 선택해 배포합니다. Workers 프로젝트가 아닌 Pages를 선택합니다.
3. 발급된 HTTPS 주소를 열어 **연결·음성 설정**에 개인 Gemini·ElevenLabs 키를 입력하고 **현재 페이지에 적용**을 누릅니다. 카메라 사용을 허용하고 시작합니다.

키는 페이지 메모리에서만 사용하며 localStorage·쿠키·서비스 워커 캐시에 저장하지 않습니다. 새로고침하면 지워집니다. 정적 ZIP에서는 필요한 사진이 Google로, 선택한 음성 문장이 ElevenLabs로 직접 전송됩니다. 로컬 AI는 키 없이 준비할 수 있습니다.

**GitHub 연동 배포**

Cloudflare Pages Git 연동에서 저장소 `Siroko-0516/new_orphan`, 브랜치 `main`, 빌드 명령 **`npm run build`**, 출력 폴더 **`dist`**, Node.js **22 이상**을 선택합니다. 저장소의 Pages Functions도 함께 배포됩니다. 필요하면 서버 Secret으로 `GEMINI_API_KEY`, `ELEVENLABS_API_KEY`를 등록할 수 있습니다. 사이트에 입력한 개인 키가 우선합니다. 키를 `VITE_` 변수나 Git 파일에 넣지 마세요. 공개 서버 키를 설정하는 경우 제공자 사용량이 계정에 부과되며 현재 사용자 인증·사용자별 할당량 기능은 없습니다.

`giljabi-web-cloudflare.zip`은 Functions가 포함된 Wrangler 배포용이며, 대시보드의 간단한 직접 업로드에는 static ZIP을 사용합니다. 프로젝트 소스에서 CLI 배포하려면 인증된 본인 계정으로 `npx wrangler pages deploy dist --project-name <프로젝트명>`을 실행합니다.

## 사용법

1. 안내 방식에서 Gemini / 로컬 AI / 물체 감지만을 고릅니다.
2. Gemini는 설정에 본인 키를 적용합니다. ElevenLabs는 기존 고정 여자·남자 음성만 선택하며 실패하면 기본 TTS로 대체합니다. 기본 음성 시험은 직접 눌러 브라우저 음성 권한을 확인합니다.
3. 로컬은 **로컬 AI 준비**를 직접 눌러 최초 모델을 받습니다. 지원 GPU·브라우저가 필요합니다. 준비가 끝나면 사진 분석과 한국어 번역을 기기에서 처리합니다.
4. 카메라를 시작합니다. 기본 최소 분석 시작 간격은 2초지만 이전 작업이 완료돼야 다음 분석을 시작하므로 2초마다 결과를 보장하지 않습니다. 같은 안내를 주기적으로 반복하지 않습니다.
5. 저장한 사진을 선택해 사진 한 장 분석도 할 수 있습니다. 이 수동 분석에는 보행 음성의 5초 유효기간을 적용하지 않습니다.

홈 화면에 추가할 수 있습니다. **브라우저는 다른 탭·잠금 화면으로 이동하면 카메라와 안내를 중지합니다.** Android 앱의 백그라운드 서비스와 다릅니다. HTTPS 사이트에서 HTTP ESP 영상은 혼합 콘텐츠 차단 및 CORS 제약을 받으므로 HTTPS·CORS 연결이 필요합니다. 휴대폰 카메라는 HTTPS 권한만 허용하면 됩니다. Web Bluetooth는 지원 기기의 Chrome 계열에서만 사용할 수 있고, Safari 등에서는 카메라·AI 기능만 사용합니다. BLE 쓰기 완료가 보드 수신 ACK를 의미하지 않습니다.

## 로컬 AI와 한계

Android의 MiniCPM JNI·Vulkan 코드를 브라우저에서 실행하는 방식이 아닙니다. 브라우저용 **SmolVLM-256M-Instruct**가 짧은 영어 설명을 만들고 **NLLB-200 distilled 600M**이 한국어로 번역합니다. 고정 버전의 ONNX q4f16 가중치와 실제 번역 모델을 사용합니다. 최초 가중치는 약 **1.44GB**이며 실행 파일·설정을 포함해 **약 1.5GB** 다운로드가 필요합니다. 지원 기기의 WebGPU FP16과 충분한 저장 버퍼·메모리가 필요하며, 기능 검사에서 지원하지 않으면 로컬 선택을 비활성화합니다. 다운로드·GPU 초기화는 처음에 오래 걸릴 수 있습니다.

로컬 번역 모델 NLLB는 **CC BY-NC 4.0 비상업적 사용** 조건입니다. 출처·라이선스·변형은 [모델 안내](public/MODEL_LICENSES.html), 고정 리비전과 가중치 크기는 [모델 정의](static/local-ai-core.js)에 있습니다. 사진은 로컬 모델 서버로 업로드하지 않으며, 처음 모델을 받을 때 Hugging Face에 연결하고 브라우저 모델 캐시를 사용합니다. 브라우저 기본 TTS의 실제 음성 제공 방식은 OS·브라우저 설정에 따릅니다.

**실제 휴대폰에서 2초 이하 처리나 모든 장애물 발견은 검증하지 않았습니다.** 작은 VLM은 잘못 설명할 수 있습니다. 일반 풍경 설명을 장애물 안내로 읽지 않는 추가 검사와 실제 발화 시작 이후의 의미 중복 억제를 적용합니다. 거리·진행 방향·머리 높이를 실측하지 않으며 2~3m 수치, 안전한 경로, 통행 가능을 보장하는 문장을 만들지 않습니다. YOLO는 COCO 80종 범주여서 계단·작은 단차 등의 위험을 포괄하지 않습니다.

## 개발과 검증

```bash
npm ci
npm test
npm run build
npm run preview
```

Node.js 22 이상을 사용합니다. Pages 로컬 미리보기는 `http://localhost:8788`입니다. Vite 개발 서버 `npm run dev`는 `/api`를 선택형 Python 서버 `127.0.0.1:8000`으로 프록시하며, 서버가 없어도 개인 키 직접 연결 모드를 사용할 수 있습니다. Python API는 `requirements.txt` 환경에서 `python -m uvicorn app:app --port 8000`으로 실행합니다. Cloudflare 배포에는 Python·PyTorch 서버가 필요하지 않습니다.

```bash
python -m pytest -q
npm run package:site
```

`package:site`는 Python 3.11 이상 표준 라이브러리로 업로드 ZIP·체크섬을 생성합니다. 기본 빌드 `npm run build`는 Python을 사용하지 않습니다. 생성 파일은 `release-web/`에 있고 모델 가중치·개인 키를 포함하지 않습니다. 모든 배포 자산은 Cloudflare의 파일당 25MiB 제한을 검사합니다. 서로 다른 버전의 YOLO와 Transformers ONNX Runtime 실행 파일을 구분해 준비합니다.

브라우저 검사는 실제 ONNX/WASM YOLO·후면/전면 획득·중지, 감지 음성 없음, 반복 응답 1회 발화, 늦은 응답 억제, ElevenLabs 오류 대체, 정적 사이트의 개인 키 직접 연결·새로고침 초기화를 검사합니다. 브라우저 검사를 재현하려면 Python Playwright·Chromium·ffmpeg와 실행 중인 빌드 사이트를 준비하고 `python scripts/web_browser_smoke.py`를 실행합니다. `GILJABI_WEB_URL`로 주소를 바꿀 수 있습니다.

이때 카메라는 공개 이미지의 가상 장치, AI 응답·음성 재생 이벤트는 시험용 대체입니다. 실제 Gemini 서버 요청도 단색 사진에서 `speak=false`를 반환함을 확인했습니다. 실제 스피커·BLE 장치·휴대폰 GPU 속도는 별도 확인이 필요합니다. 검증 세부 사항은 [WEB_VALIDATION.json](WEB_VALIDATION.json)에 기록합니다.

## 모델과 라이선스

[Ultralytics YOLO11n](https://github.com/ultralytics/ultralytics)은 AGPL-3.0 / Enterprise입니다. 현재 ONNX는 공식 가중치를 Ultralytics 8.4.31, ONNX 1.17.0으로 416px/opset17/batch1/NMS미포함 변환했으며 [출처·해시](public/model-provenance.json)를 확인합니다. ONNX Runtime은 MIT, Transformers.js·SmolVLM은 Apache-2.0입니다. NLLB 가중치는 CC BY-NC 4.0이므로 비상업적 사용에 한합니다.
