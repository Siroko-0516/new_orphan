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
  adb logcat -d -v brief -s Telecom:V TelephonyConnectionService:V TelecomFramework:V GsmCdmaPhone:V > phone-system-validation/phone-log-failure.txt
  adb exec-out screencap -p > phone-system-validation/phone-system-failure.png
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
adb shell input keyevent KEYCODE_ENDCALL
exit "$status"
