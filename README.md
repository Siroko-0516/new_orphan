# 길잡이 · 카메라 안전 안내 웹앱

카메라 → **YOLO11n** 물체 감지 → **Gemini 또는 GPT** 장면 판단 → **ElevenLabs TTS** 음성 안내. ElevenLabs 미설정·실패·시간 초과 시 브라우저의 기본 TTS를 사용합니다.

## 시작하기

Python 3.12, [uv](https://docs.astral.sh/uv/), curl이 필요합니다. 이 버전은 서버 CPU에서 YOLO를 실행합니다.

```bash
cd /workspace/new_orphan
bash setup.sh
cp .env.example .env  # 처음 한 번만. 기존 .env를 덮어쓰지 마세요.
# .env에 사용할 제공자의 키를 로컬 편집기로 입력
MPLCONFIGDIR=/tmp/matplotlib .venv/bin/python -m uvicorn app:app --host 0.0.0.0 --port 8000
```

로컬 개발 주소는 `http://localhost:8000`입니다. 휴대폰에서 카메라를 사용하려면 **HTTPS**로 제공해야 합니다. 마이크 권한은 요청하지 않습니다. 이 프로토타입에는 사용자 인증이나 사용량 제한이 없으므로 공개 배포 전에 인증, 요청 제한, HTTPS를 추가하세요.

## 서버 환경 설정

API 키는 `.env` 또는 서버 환경 변수로만 설정합니다. 브라우저에 전달하지 않습니다. `.env`는 Git에서 제외됩니다. 키를 채팅이나 코드에 넣지 마세요.

| 변수 | 설명 |
| --- | --- |
| `AI_PROVIDER` | `gemini`(기본값) 또는 `openai` |
| `GEMINI_API_KEY` | Gemini를 사용할 때 필요 |
| `GEMINI_MODEL` | 기본값 `gemini-3.1-flash-lite` |
| `OPENAI_API_KEY` | GPT를 사용할 때 필요 |
| `OPENAI_MODEL` | 기본값 `gpt-4.1-mini` |
| `ELEVENLABS_API_KEY` | ElevenLabs 음성을 사용할 때 필요 |
| `ELEVENLABS_VOICE_ID` | ElevenLabs 계정에서 사용할 음성 ID |
| `YOLO_MODEL` | 기본값 `models/yolo11n.pt` |

키가 없으면 **YOLO 감지와 기본 TTS 테스트만** 사용할 수 있습니다. 이때 위험 판단은 제공하지 않습니다. 설정을 바꾼 뒤 서버를 다시 실행하세요. 필요한 외부 목적지는 `generativelanguage.googleapis.com`, `api.openai.com`, `api.elevenlabs.io`입니다. 설치에는 PyPI, PyTorch CPU 저장소, GitHub가 필요합니다.

## 동작

- 카메라 프레임은 최대 가로 800px JPEG로 전송합니다. 서버는 메모리에서 처리하고 영상 파일을 저장하지 않습니다. 장면 분석을 켠 경우 해당 프레임은 선택한 AI 제공자에게 전송되므로 그 제공자의 데이터 정책이 적용됩니다.
- YOLO는 700ms 대기 간격을 두고 순차 실행하며, 실제 속도는 서버 성능에 따라 달라집니다. AI는 최소 6초 간격으로 동일 프레임과 YOLO 결과를 받아 판단합니다. AI 대기 중에도 YOLO는 실행됩니다.
- `medium`/`high` 위험일 때만 자동 음성 안내합니다. 안내 간격은 최소 12초, 같은 문장은 최소 30초입니다.
- ElevenLabs 요청은 서버에서 8초 제한을 두고 실패하면 기본 TTS로 전환합니다. 브라우저/기기의 한국어 음성 설치 및 자동 재생 정책에 따라 음성이 지원되지 않을 수 있습니다. 먼저 **음성 테스트**로 확인하세요.
- 중지하거나 다른 탭으로 이동하면 카메라와 음성을 중지합니다. 이미 전송한 서버 요청은 완료될 수 있으나 이전 결과는 화면과 음성에 반영하지 않습니다.
- 화면의 위험 단계는 마지막으로 완료된 단일 장면 분석 결과입니다. AI 연결이 실패하면 판단 불가로 표시합니다.

## 검증

```bash
.venv/bin/python -m pytest -q
node --check static/app.js
```

API 테스트는 외부 제공자를 모의 처리하여 입력 검증, 두 AI 제공자의 이미지·YOLO 전달, 잘못된 AI 출력, 음성 요청 실패를 검증합니다. 실제 API 키를 사용한 서비스 연동 테스트와 구분됩니다.

현재 인스턴스에서는 공식 Ultralytics 버스 사진으로 실제 모델의 버스·사람 감지를 확인했고, Chromium의 가상 카메라로 웹 감지·중지 및 기본 TTS 호출을 확인했습니다. 실제 기기의 카메라와 스피커는 별도 확인이 필요합니다.

## 범위와 라이선스

보조 안내용 프로토타입입니다. 단일 카메라 이미지로 거리·속도·충돌을 확정할 수 없으며, 감지 누락과 AI 판단 지연이 발생할 수 있습니다. 교통 안전을 보장하는 장치로 사용할 수 없습니다. YOLO 기본 모델은 COCO의 80개 범주만 인식하며 구덩이, 계단 등의 위험을 포괄하지 않습니다.

Ultralytics 및 모델의 **AGPL-3.0 / Enterprise** 라이선스를 확인하고 배포 방식에 맞게 준수하세요. 참조: https://www.ultralytics.com/license
