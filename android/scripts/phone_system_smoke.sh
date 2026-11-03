#!/usr/bin/env bash
set -uo pipefail
# AVD only: no cloud credentials, GGUF, real SIM or real destination.
adb emu avd name
./gradlew --no-daemon -PcameraSmokeOnly=true -Pandroid.testInstrumentationRunnerArguments.class=dev.giljabi.PhoneSystemSmokeTest :app:connectedDebugAndroidTest 2>&1 | tee phone-system-smoke.log
status=${PIPESTATUS[0]}
mkdir -p phone-system-validation
if (( status == 0 )); then
  for item in phone-system-check.json phone-system-screen.png phone-system-window.xml; do
    if ! adb exec-out run-as dev.giljabi.camera cat "files/phone-system/$item" > "phone-system-validation/$item"; then status=1; fi
  done
fi
if (( status != 0 )); then
  adb shell dumpsys telecom > phone-system-validation/telecom-failure.txt
  adb exec-out screencap -p > phone-system-validation/phone-system-failure.png
fi
adb shell input keyevent KEYCODE_ENDCALL
exit "$status"
