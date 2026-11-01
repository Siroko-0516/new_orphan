#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon -PbundledModelDir=../model-packs/minicpm-1b :app:assembleDebug :app:testDebugUnitTest :app:lintDebug 2>&1 | tee full-build.log
status=${PIPESTATUS[0]}
if (( status != 0 )); then
  python3 - <<'PY'
from pathlib import Path
text='\n'.join(Path('full-build.log').read_text(errors='replace').splitlines()[-90:])
print('::error title=Full APK build::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
fi
exit "$status"
