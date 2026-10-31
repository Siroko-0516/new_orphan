# 로컬 AI 모델을 APK에 포함하기

0.3.0 소스는 모델 내장 빌드와 경량 빌드를 구분합니다. 실제 Q4_K_M 언어 모델과 비전 mmproj를 포함한 **0.3.0-full** APK 빌드와 검증을 완료했습니다. 다운로드 경로는 [배포 안내](../downloads/README.md)를 확인하세요. `-PbundledModelDir` 없이 빌드하면 **0.3.0-lite**이며 MiniCPM 가중치를 포함하지 않습니다. 이 경량 APK를 모델 내장 완료본으로 배포하면 안 됩니다.

내장 빌드에는 MiniCPM-V 4.7 1B의 **언어 GGUF와 그 모델에 맞는 비전 mmproj GGUF 둘 다**, 모델 배포자의 가중치 라이선스가 필요합니다. JNI 엔진의 MIT 라이선스는 가중치 라이선스를 대신하지 않습니다. 언어 파일만 포함하면 이미지 분석이 준비되지 않습니다. 원본 게시자: <https://huggingface.co/openbmb/MiniCPM-V-4.7-1B-gguf>.

## 패키지 준비와 빌드

게시자의 고정 버전과 SHA-256을 기록한 `model-specs/minicpm-1b.json`을 사용해 준비합니다. Python 3.11 이상과 모델 호스트 인터넷 접속이 필요합니다.

```bash
cd android
python scripts/prepare_bundled_models.py --cache /path/to/model-cache --output model-packs/minicpm-1b
./gradlew -PbundledModelDir=../model-packs/minicpm-1b :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

이미 모델 파일을 확보했다면 아래 방식으로 준비해도 됩니다. 다른 파일을 사용할 경우 게시자의 모델·비전 조합과 라이선스를 확인해야 합니다. 파일 이름·GGUF 헤더·해시 검사만으로 추론 호환성을 입증하지는 못합니다.

```bash
cd android
python scripts/bundle_models.py \
  --model /path/to/MiniCPM-V-4_7-1B-Q4_K_M.gguf \
  --vision /path/to/mmproj-model-f16.gguf \
  --license /path/to/publisher-model-LICENSE \
  --source https://huggingface.co/openbmb/MiniCPM-V-4.7-1B-gguf/tree/EXACT_REVISION \
  --output model-packs/minicpm-1b
./gradlew -PbundledModelDir=../model-packs/minicpm-1b :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`bundledModelDir` 는 `app/` 기준 경로입니다. 절대 경로를 전달해도 됩니다.

결과는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 모델 파일은 `assets/local-ai/`에 포함되고, GGUF는 ZIP 압축 없이 저장합니다. Gradle이 두 가중치의 크기와 SHA-256을 확인하며 파일이 없으면 full 빌드가 실패합니다. 사용자에게 별도로 모델을 다운로드시키거나 서버 API로 대체하는 기능은 없습니다.

모델 준비 스크립트의 크기 상한은 두 파일 합계 1.9GB입니다. 이 조합은 672,003,680 바이트 언어 파일과 1,110,117,984 바이트 비전 파일이며, 로컬 검증 APK는 1,805,318,857 바이트(약 1.8GB)였습니다. APK 외에 앱 내부 모델 복사본 약 1.8GB도 필요하므로 다운로드·설치·초기 준비를 위해 여유 저장 공간 6GB 이상을 권장합니다. RAM 요구량과 저장 공간은 다릅니다.

APK는 GitHub 일반 Git 파일의 100MB 제한을 초과하므로 GitHub Releases를 사용합니다. `Android full offline APK` 워크플로는 `android-v*-full` 태그에 고정 모델을 준비하고 빌드·테스트·Lint 후 테스트 릴리스를 게시합니다. 수동 실행은 APK를 Actions artifact로 제공합니다. 가중치 자체는 Git에 커밋하지 않습니다. CI 서명은 로컬 빌드나 기존 공개 APK와 다를 수 있습니다.

## 첫 실행 동작

full 빌드는 언어·비전 파일 선택 UI를 표시하지 않습니다. **내장 AI 준비** 또는 사진 준비 후 **기기에서 사진 분석**을 누르면 앱에 포함된 모델을 내부 저장소에 준비하고 로드합니다. 이 과정과 이후 추론에 인터넷은 필요하지 않습니다. 큰 복사는 UI 스레드 밖에서 실행하고 진행률·저장 공간 부족·검증 실패를 표시합니다. 완료된 복사본은 다음 사용 때 재사용합니다.

두 파일의 크기, GGUF 헤더, SHA-256이 모두 일치해야 완료 표시를 기록합니다. 취소·불완전한 비전 파일·체크섬 불일치에서는 임시 파일을 제거하며 새 모델 조합을 사용하지 않습니다. 모델 준비 후의 네이티브 로딩 실패는 화면에 표시합니다. 모델을 APK에 포함하는 것은 RAM 요구량이나 한국어 설명 품질을 개선하지 않습니다. TTS는 여전히 Android에 설치된 오프라인 음성이 필요합니다.

## 검증 범위

JVM 테스트는 정상 설치·재사용, 크기/체크섬 실패, 두 번째 파일 복사 중 취소, 저장 공간 부족, 이전 버전 보존, 잘린 캐시 재설치를 검사합니다. 합성 GGUF 테스트 데이터는 파일 설치 로직 검사에만 사용하며 사용자에게 배포하지 않습니다.

실제 가중치를 포함한 ARM64 APK 빌드, JVM 테스트 36개 통과(실패·오류·건너뜀 0), Lint 오류 0(경고 있음)을 확인했습니다. APK 안 두 GGUF의 크기·SHA-256·무압축 저장, 가중치 라이선스·출처·YOLO 해시, v2 서명과 16KB ZIP 정렬을 검사했습니다. 같은 고정 엔진을 Linux x86-64에서 빌드해 실제 모델 두 파일로 사진 설명 생성까지 성공했습니다. 이는 호환성 확인이며 휴대폰 성능 측정이 아닙니다. [검증 기록](FULL_VALIDATION.md)을 참고하세요.

실제 휴대폰에서의 모델 로딩·추론·RAM·속도·카메라·BLE는 이 검증 범위에 포함되지 않습니다. 모델 내장만으로 3~4GB RAM 휴대폰에서 실행을 보장하지 않습니다.

YOLO는 기존 YOLO11n, 입력 416×416 letterbox를 유지합니다. 카메라 입력이 320×240이면 원본의 작은 물체 정보를 복원할 수 없습니다. 기본 COCO 80종에는 모든 보행 장애물이 포함되지 않으며 예를 들어 안전 고깔 전용 클래스는 없습니다. 카메라 부품 교체만으로 해상도·감지 정확도 개선을 보장하지 않습니다. 앱은 원본 입력 크기와 감지 처리 시간을 표시합니다.
