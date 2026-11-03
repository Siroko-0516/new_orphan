#!/usr/bin/env bash
set -uo pipefail
# AVD only: no cloud credentials, GGUF, real SIM or real destination.
adb -s emulator-5554 emu avd name
mkdir -p phone-system-validation
peer_pid="";monitor_pid=""
cleanup() {
  if [[ -n "$monitor_pid" ]]; then kill "$monitor_pid" 2>/dev/null || true; fi
  adb -s emulator-5554 shell input keyevent KEYCODE_ENDCALL || true
  adb -s emulator-5556 emu kill || true
}
trap cleanup EXIT
"$ANDROID_HOME/emulator/emulator" -avd giljabi-phone -port 5556 -read-only -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics -gpu swiftshader_indirect -memory 2560 > phone-system-validation/peer-emulator.log 2>&1 &
peer_pid=$!
peer_ready=false
for attempt in $(seq 1 150); do
  if ! kill -0 "$peer_pid" 2>/dev/null; then tail -50 phone-system-validation/peer-emulator.log; exit 1; fi
  if [[ "$(adb -s emulator-5556 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then peer_ready=true; break; fi
  sleep 2
done
if [[ "$peer_ready" != true ]]; then tail -50 phone-system-validation/peer-emulator.log; exit 1; fi
adb -s emulator-5556 shell input keyevent KEYCODE_WAKEUP
adb -s emulator-5556 shell wm dismiss-keyguard
track_peer() {
  for attempt in $(seq 1 600); do
    peer_call="$(adb -s emulator-5556 emu gsm list 2>/dev/null)"
    if [[ "$peer_call" == *15555215554* ]]; then
      printf '%s\n' "$peer_call" > phone-system-validation/peer-gsm-check.txt
      adb -s emulator-5556 exec-out screencap -p > phone-system-validation/phone-peer-screen.png
      return 0
    fi
    sleep 0.5
  done
  return 1
}
track_peer &
monitor_pid=$!
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon -PcameraSmokeOnly=true -Pandroid.testInstrumentationRunnerArguments.class=dev.giljabi.PhoneSystemSmokeTest :app:connectedDebugAndroidTest 2>&1 | tee phone-system-smoke.log
status=${PIPESTATUS[0]}
if (( status == 0 )); then
  for item in phone-system-check.json phone-system-screen.png phone-system-window.xml; do
    if ! adb -s emulator-5554 exec-out run-as dev.giljabi.camera cat "files/phone-system/$item" > "phone-system-validation/$item"; then status=1; fi
  done
fi
if (( status != 0 )); then
  adb -s emulator-5554 shell dumpsys telecom > phone-system-validation/telecom-failure.txt
  adb -s emulator-5554 logcat -d -v brief -s Telecom:V TelephonyConnectionService:V TelecomFramework:V GsmCdmaPhone:V > phone-system-validation/phone-log-failure.txt
  adb -s emulator-5554 exec-out screencap -p > phone-system-validation/phone-system-failure.png
  python3 - <<'PY'
from pathlib import Path
import re
for name in ['telecom-failure.txt','phone-log-failure.txt']:
    content=Path('phone-system-validation',name).read_text(errors='replace')
    lines=[line for line in content.splitlines() if re.search('call|account|denied|reject|permission|block|sim|outgoing|error|exception',line,re.I)]
    message='\n'.join(lines[-40:])[-7000:]
    for start in range(0,len(message),3000):
        part=message[start:start+3000]
        print('::error title='+name+'::'+part.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
fi
exit "$status"
