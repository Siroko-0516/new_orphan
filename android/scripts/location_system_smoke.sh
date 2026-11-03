#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon -PcameraSmokeOnly=true -Pandroid.testInstrumentationRunnerArguments.class=dev.giljabi.LocationSmokeTest,dev.giljabi.LocationUiSmokeTest :app:connectedDebugAndroidTest 2>&1 | tee location-system-smoke.log
status=${PIPESTATUS[0]}
mkdir -p location-system-validation
if (( status == 0 )); then
  for item in location-system-check.json location-system-screen.png; do
    if ! adb exec-out cat "/data/local/tmp/giljabi-$item" > "location-system-validation/$item" || [[ ! -s "location-system-validation/$item" ]]; then status=1; fi
  done
fi
if (( status != 0 )); then
  adb exec-out screencap -p > location-system-validation/location-system-failure.png
  python3 - <<'PY'
from pathlib import Path
import re,html
for file in Path('app/build/outputs/androidTest-results').rglob('TEST-*.xml'):
    content=file.read_text(errors='replace')
    for item in re.findall(r'<failure\b.*?</failure>',content,re.S):
        message=html.unescape(re.sub('<[^>]+>','',item))[-3500:]
        print('::error::'+message.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
fi
exit "$status"
