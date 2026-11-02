길잡이 웹사이트 버전입니다. 휴대폰 후면·전면 카메라, 조용한 Gemini 보행 상황 비교, 실제 브라우저 로컬 AI와 한국어 번역, 사진 한 장 분석을 제공합니다. 로컬은 기본 한국어 TTS만, Gemini는 기존 ElevenLabs 음성과 실패 시 기본 TTS를 사용합니다.

**Cloudflare Pages 대시보드에서 직접 업로드하려면 `giljabi-web-static.zip`을 사용하세요.** Workers & Pages → Pages 프로젝트 만들기 → 직접 업로드에서 ZIP을 선택하면 됩니다. 공개 HTTPS 주소는 본인 Cloudflare 계정에서 배포를 마칠 때 생성됩니다. 이 ZIP은 서버 비밀 키 없이 사이트 설정에 본인 키를 입력해 사용합니다. 키는 페이지 메모리에만 두고 새로고침하면 지웁니다.

GitHub 연동 배포는 저장소 `Siroko-0516/new_orphan`, 브랜치 `main`, 빌드 명령 `npm run build`, 출력 폴더 `dist`, Node.js 22 이상을 선택합니다. 이 방식은 저장소의 Pages Functions도 함께 배포합니다. Functions 포함 ZIP인 `giljabi-web-cloudflare.zip`은 Wrangler 배포용입니다.

로컬 AI는 WebGPU·FP16을 지원하는 브라우저와 약 1.5GB의 최초 모델 다운로드가 필요합니다. Android의 MiniCPM과 다른 브라우저용 SmolVLM·NLLB 모델이며 기본 YOLO 물체 감지를 로컬 AI라고 부르지 않습니다. 실행 시 사진 분석·한국어 번역은 기기에서 처리합니다. 로컬 NLLB 번역 모델은 CC BY-NC 4.0 비상업적 사용 조건입니다. 실제 휴대폰 속도와 2초 이하 처리, 모든 장애물 탐지는 보장하지 않습니다. 로컬의 사용자 질문 답변은 제한적이며 일반 사진 설명을 제공합니다.

카메라에는 HTTPS가 필요하며 다른 탭이나 잠금 화면으로 이동하면 중지합니다. HTTP ESP 영상은 HTTPS 사이트에서 차단될 수 있고 CORS 설정이 필요합니다. 실제 거리·진행 방향·머리 높이를 측정하지 않습니다. API 키와 모델은 ZIP에 포함하지 않습니다. [사용법과 검증 범위](https://github.com/Siroko-0516/new_orphan/blob/main/README.md).
