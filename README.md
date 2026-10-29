# 길잡이 · 브라우저 카메라 안전 안내

**YOLO11n은 사용자 기기에서 실행**하며, 기본 음성 안내는 브라우저 TTS를 사용합니다. 카메라와 YOLO를 위한 Python 서버나 GPU 서버는 필요하지 않습니다.

```text
카메라 → 브라우저 YOLO (WASM) → 감지 상자 + 빠른 물체 알림 + 기본 TTS
                           ↓ AI 설명을 직접 켠 경우만
                  서버 함수 → Gemini / GPT → 장면 설명 + TTS
```

## 설치 없이 웹주소로 사용하기: Cloudflare Pages

아직 공개 사이트는 배포되지 않았습니다. 본인 계정에 다음과 같이 배포하세요. **Workers가 아닌 Pages의 Git 연동 프로젝트**를 선택합니다.

1. [Cloudflare 대시보드](https://dash.cloudflare.com/)의 Workers & Pages에서 Pages 프로젝트를 생성하고 GitHub를 연결합니다. UI 명칭은 바뀔 수 있습니다.
2. `Siroko-0516/new_orphan`, 브랜치 `main`을 선택합니다.
3. 프레임워크는 Vite 또는 없음, **빌드 명령 `npm run build`**, **출력 폴더 `dist`**, Node.js 22 이상을 설정합니다.
4. 먼저 키 없이 배포하면 기기 내 YOLO 감지와 기본 TTS를 사용할 수 있습니다. Pages가 제공하는 **HTTPS 주소**에서 카메라 권한을 허용하세요.
5. AI 설명이 필요하면 프로젝트 설정의 **서버 측 비밀 변수(Secret)**로 `GEMINI_API_KEY`를 등록하고 다시 배포합니다. Codex의 키는 Cloudflare로 자동 전달되지 않습니다.

키에 `VITE_` 접두사를 붙이지 마세요. 해당 변수는 브라우저에 노출됩니다. `.env`, `.dev.vars`, 실제 키를 GitHub에 올리지 마세요. API 함수는 `functions/api/[[path]].js`이며 키를 브라우저로 반환하지 않습니다.

Cloudflare의 최신 무료 플랜 한도와 Gemini 계정별 무료 할당량을 확인하세요. GPT와 ElevenLabs는 별도 요금이 발생할 수 있습니다. 무료나 속도를 보장하는 구조는 아닙니다. 개인용 배포에는 Cloudflare Access 등으로 접근을 제한하고 제공자 측 할당량도 제한하세요. 현재 앱에는 사용자 인증이나 사용자별 서버 사용량 제한이 없습니다. 같은 출처 검사는 인증을 대신하지 않습니다.

## 동작과 비용 제어

- 최초 실행 시 YOLO 모델 약 10MiB와 ONNX Runtime 실행 파일을 내려받습니다. 이후 브라우저 캐시를 사용할 수 있습니다.
- 감지는 Web Worker의 WASM 백엔드로 실행합니다. 일부 모바일 WebGPU 환경에서 화면 상단에 50% 보트 상자가 반복 표시되는 현상이 보고되어 WebGPU 자동 선택을 비활성화했습니다. 해당 기기의 원인은 아직 확정하지 않았습니다. 최대 초당 5회를 시도하며 실제 속도는 기기에 따라 달라집니다.
- **AI 설명은 기본으로 꺼져 있습니다.** 키 없이도 감지와 빠른 물체 알림이 작동합니다. 이 모드에서는 영상 프레임을 서버로 보내지 않습니다.
- 중앙을 크게 차지하는 사람·차량 등의 감지 상자가 있으면 기본 TTS로 먼저 알립니다. 상자 위치·크기의 단순 규칙이며 거리, 충돌, 위험도 판단이 아닙니다.
- AI 설명을 켜면 범주나 대략적 위치가 바뀔 때 최소 10초 간격으로 감지한 프레임과 YOLO 결과를 보냅니다. 변화가 없으면 30초 간격입니다. AI 대기 중에도 YOLO는 실행됩니다.
- AI 설명은 위험 단계와 관계없이 읽습니다. AI와 빠른 물체 알림은 각각 최소 12초, 같은 문장은 최소 30초 간격이며 빠른 물체 알림은 진행 중인 음성을 끊지 않습니다.
- ElevenLabs 설정이 있으면 AI 설명의 음성에 사용하며, 실패·시간 초과 시 기본 TTS로 전환합니다. 빠른 물체 알림은 지연을 줄이기 위해 항상 기본 TTS를 씁니다.
- 소리가 안 나면 **기본 음성 테스트**를 직접 눌러 기기 음성을 먼저 확인하세요. 별도의 음성 상태 문구에 ElevenLabs 인증·음성 ID 오류, 자동 재생 차단, 기본 TTS 실패가 표시됩니다. 브라우저의 음소거와 OS 한국어 음성 지원도 확인해야 합니다.
- AI 장면 분석은 30초, ElevenLabs 음성 생성은 10초 제한을 사용합니다. AI 시간 초과와 음성 시간 초과는 구분해서 표시합니다.
- 중지하거나 다른 탭으로 가면 카메라와 음성을 중지합니다. 이미 보낸 AI 요청은 완료될 수 있으나 이전 결과는 반영하지 않습니다.
- 앱은 영상 파일을 저장하지 않습니다. AI 설명을 켜면 장면이 서버와 선택한 제공자에게 전송되며 해당 제공자의 데이터 정책이 적용됩니다. 기본 TTS의 오프라인 지원은 OS·브라우저의 음성 엔진에 따라 다릅니다.

## 서버 변수 (선택 사항)

| 변수 | 설명 |
| --- | --- |
| AI_PROVIDER | gemini(기본) 또는 openai |
| GEMINI_API_KEY | Gemini 키, 서버 Secret |
| GEMINI_MODEL | 기본 gemini-3.1-flash-lite |
| OPENAI_API_KEY | GPT 키, 서버 Secret |
| OPENAI_MODEL | 기본 gpt-4.1-mini |
| ELEVENLABS_API_KEY | 선택 사항, 서버 Secret |
| ELEVENLABS_VOICE_ID | 사용할 음성 ID |

## 개발과 검증

Node.js 22 이상이 필요합니다.

```bash
npm ci
npm run build
npm run preview
npm test
```

로컬 Pages 서버는 8788 포트입니다. localhost는 카메라를 허용하고 외부 기기에서는 HTTPS가 필요합니다. 로컬 AI 설정은 `.dev.vars.example`을 `.dev.vars`로 복사한 뒤 편집하세요. `.dev.vars`는 Git에서 제외됩니다.

기존 Python API는 선택적 호환 경로로 유지합니다. 기존 `.venv`가 있다면 `npm run build` 후 `.venv/bin/python -m uvicorn app:app --port 8000`으로 빌드된 앱을 제공할 수 있습니다. `npm run dev`는 Vite 개발 서버를 열고 `/api`를 이 Python 서버로 프록시합니다. **Cloudflare 배포에는 Python, setup.sh, PyTorch가 필요하지 않습니다.**

JavaScript 테스트는 좌표 복원·NMS와 서버 함수의 이미지 전달, 오류·할당량 처리, TTS 대체 응답을 검증합니다. 기존 Python API 테스트는 `.venv/bin/python -m pytest -q`로 실행합니다.

## 모델과 라이선스

`public/yolo11n.onnx`는 [공식 YOLO11n 가중치](https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11n.pt)를 416×416, ONNX opset 17, batch 1, NMS 미포함으로 변환했습니다. 해시와 출처는 `public/model-provenance.json`에 있습니다. 모델을 다시 만들려면 Ultralytics 8.4.31, ONNX 1.17.0에서 다음을 실행하고 결과를 해당 경로로 복사하세요.

```python
from ultralytics import YOLO
YOLO('models/yolo11n.pt').export(format='onnx', imgsz=416, opset=17, simplify=False, dynamic=False, batch=1)
```

Ultralytics 및 모델의 **AGPL-3.0 / Enterprise** 라이선스를 확인하고 준수하세요: https://www.ultralytics.com/license . ONNX Runtime은 MIT 라이선스입니다.

보조 안내용 시제품입니다. COCO 80개 범주만 감지하며 계단, 구덩이 등의 위험을 포괄하지 않습니다. 감지 누락, AI 오판과 지연이 발생하며 안전이나 실시간 충돌 방지를 보장하지 않습니다.
