#!/usr/bin/env bash
set -uo pipefail
# This test build has no cloud key or GGUF; all camera frames stay inside the emulator.
./gradlew --no-daemon -PcameraSmokeOnly=true :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:lintDebug 2>&1 | tee camera-smoke.log
status=${PIPESTATUS[0]}
if (( status != 0 )); then
  python3 - <<'PY'
from pathlib import Path
text='\n'.join(Path('camera-smoke.log').read_text(errors='replace').splitlines()[-65:])
print('::error title=Android camera verification::'+text.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
fi
exit "$status"
