# 길잡이 네이티브 종료 로그 수집 · Windows

Android 16의 `REASON_CRASH_NATIVE` / status 6(SIGABRT)을 더 조사하려면 `Abort message`와 네이티브 backtrace가 필요합니다. 0.5.1 앱의 마지막 단계 표시는 5초 간격 저장이며 정확한 충돌 명령 위치가 아닙니다.

1. PC에서 [Google 공식 platform-tools ZIP](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)을 받아 압축을 풉니다.
2. 이 도구의 `collect-giljabi-crash.cmd`를 **adb.exe가 있는 platform-tools 폴더 안에** 복사합니다.
3. 휴대폰 **설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호 7회**로 개발자 옵션을 열고 **설정 → 개발자 옵션 → USB 디버깅**을 켭니다. 이미 켜져 있다면 그대로 사용합니다.
4. USB 데이터 케이블로 PC와 휴대폰을 연결하고 잠금을 풉니다. USB 디버깅 허용 창을 확인합니다.
5. CMD 파일을 실행합니다. `adb devices` 목록에서 기기 상태가 `device`여야 합니다. `unauthorized`이면 휴대폰의 허용 창을 확인합니다. 목록이 비어 있으면 데이터 케이블·Windows USB 드라이버를 확인합니다.
6. 최근 길잡이 종료 기록이 남아 있으면 그대로 아무 키를 누릅니다. 기록이 없다면 휴대폰에서 **ESP 영상 + YOLO 시작**으로 같은 종료를 한 번 재현한 뒤 PC 창으로 돌아와 아무 키를 누릅니다.
7. 메모장으로 열린 **giljabi-native.txt**의 내용을 전달합니다. 이 파일에는 길잡이가 포함된 마지막 네이티브 종료 블록에서 Abort message·signal·호출 스택 등의 줄을 추렸습니다.

도구는 기존 앱을 지우거나 재설치하지 않고 크래시 버퍼도 지우지 않습니다. 로그를 외부로 자동 전송하지 않습니다. `giljabi-crash-local.txt`에는 로컬 원본 크래시 버퍼가 들어 있어 다른 앱의 종료 기록도 포함될 수 있으므로 전체를 공유하지 말고 추린 파일을 확인한 후 전달하세요. 로그 버퍼가 이미 지워졌거나 덮어써졌다면 한 번 더 재현 후 수집해야 합니다. Windows PC와 실제 기기에서의 이 CMD 도구 실행은 개발 환경에서 아직 확인하지 못했습니다.

도구 없이 직접 명령을 쓰려면 platform-tools 폴더에서 다음을 실행합니다.

```bat
adb devices
adb logcat -b crash -d -v threadtime -t 1200 > giljabi-crash-local.txt
```

직접 수집한 경우 파일에서 `>>> dev.giljabi.camera <<<`가 있는 종료 블록을 찾고 해당 블록의 `Abort message:` 및 `backtrace:` 아래 약 30줄을 전달하세요. 다른 앱의 로그는 제외합니다.
