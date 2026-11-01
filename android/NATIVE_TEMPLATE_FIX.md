# 확인된 로컬 AI 네이티브 종료 수정 · 0.5.3

## 확인된 원인

제공된 상세 네이티브 기록에서 `MonitorService.analyzeLatest → LocalVlm.describe → LlamaEngine.processSystemPrompt → common_chat_format_single` 경로가 확인됐습니다. C++ `std::invalid_argument`의 내용은 `Unable to generate parser for this template. Automatic parser generation failed`였으며, 처리되지 않은 예외가 JNI 경계를 넘어가 SIGABRT로 앱을 종료했습니다.

이는 해당 상세 로그의 종료 원인입니다. JPEG/YOLO가 다른 작업 스레드에서 저장한 마지막 단계 표시는 충돌한 함수 위치가 아니었습니다. 이전의 프레임 메모리 개선이 이 예외를 고친 것은 아닙니다. ESP 수신 15초 타임아웃도 별도 연결 오류로 다룹니다. 사용자 기기 식별 정보나 원본 로그는 이 저장소에 포함하지 않았습니다.

실제 GGUF에 들어 있는 Jinja 템플릿은 사용자 메시지가 없으면 `No user query found in messages`를 발생시킵니다. 기존 Android 브리지는 시스템 문장만 먼저 `common_chat_format_single(..., use_jinja=true)`에 넘겼습니다. 이 경로는 응답 파서 자동 생성까지 시도했고, 시스템 문장 단독 템플릿 조건에 맞지 않았습니다.

## 수정

- 시스템 지시·이미지 마커·질문을 한 대화로 구성하고 모델의 원래 템플릿을 한 번 렌더링합니다. 이미지 마커도 사용자 역할 안에 들어갑니다. 렌더링한 전체 입력을 MTMD에서 토큰화·평가합니다.
- 프롬프트 텍스트만 쓰는 이 호출은 `force_pure_content=true`로 응답 파서 자동 생성 경로를 사용하지 않습니다. 템플릿이 제공하는 assistant 시작 문구와 비활성 thinking 프리픽스를 유지합니다. 가짜 사용자 질문이나 모델 템플릿 변경으로 우회하지 않습니다.
- 시각 답변 종료 시 이미 생성된 텍스트를 저장하기 위해 템플릿을 다시 렌더링하지 않습니다.
- JNI 내보내기 함수 전체에 C++ 예외 경계를 두어 `std::exception`은 Java `IllegalStateException`, 할당 실패는 `OutOfMemoryError`로 전달합니다. JNI 문자열·바이트는 RAII로 해제하고, 준비 실패 후 null context를 정리하는 경로도 보호합니다. 모든 SIGSEGV·명시적 abort·시스템 강제 종료를 이 경계가 막는다는 의미는 아닙니다.

## 실제 모델 검사

고정 엔진 `e0eca9d8f57ac8f5a5ad6191a8007b93436c1e71`과 공개 0.5.1 APK에 내장된 실제 언어 GGUF를 사용했습니다. SHA-256은 `f18f594747e5317ab490cff9c06339c9cfb89acbe2f02ebc48a4e167bc23576d`입니다.

1. Linux x86-64에서 이전의 시스템 문장 단독 호출로 사용자 기록과 같은 `Unable to generate parser for this template` 예외를 재현했습니다.
2. 같은 모델에서 생산 코드의 전체 대화 렌더링을 3회 검사해 지시·질문·역할 프레임이 유지되고 예외가 발생하지 않음을 확인했습니다.
3. 생산 JNI 예외 경계를 별도 JNI 검사 라이브러리에서 실행해 invalid_argument·bad_alloc·알 수 없는 C++ 예외가 Java로 전달되고 정상 반환은 유지됨을 확인했습니다.
4. 실제 생산 JNI 코드, 실제 언어·비전 가중치와 공개 풍경 JPEG로 reset→전체 이미지/질문 prefill→토큰 생성→해제 경로를 3회 실행했습니다. 매회 사진에 대한 영어 문장을 생성했고 프로세스 exit code 0이었습니다. 예: “A rugged mountain valley with a winding river and forested slopes stretches beneath a bright blue sky, framed by a rocky outcrop in the foreground.”

호스트 3회 실제 사진 검사 전체 시간 약 33.37초, 최대 RSS 약 2.23GiB는 개발 환경 측정이며 삼성 휴대폰의 속도·메모리 측정이 아닙니다. 호스트 JNI 검사에는 Android log 출력만 대체하는 테스트용 shim을 사용했고 템플릿·모델·JNI 본문은 생산 코드를 사용했습니다. 실제 사용자 사진·API 키는 사용하지 않았습니다.

재현 도구는 `android/native-tests/`에 있습니다. 공개 APK 작업에도 실제 GGUF 템플릿 회귀 검사와 실제 JNI 예외 변환 검사를 추가해 성공한 경우에만 배포하도록 합니다. Android 기기의 지속 실행·램·네트워크·음성·BLE 검증은 별도입니다. [API 키 입력과 상세 로그 개선](KEY_AND_TRACE.md)도 이번 버전에 포함합니다.
