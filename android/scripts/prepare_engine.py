"""Fetch the exact MIT-licensed native engine used by the official MiniCPM demo."""
from pathlib import Path
import hashlib
import io
import urllib.request
import zipfile
from patch_engine import patch

COMMIT = 'e0eca9d8f57ac8f5a5ad6191a8007b93436c1e71'
SHA256 = '3ce1d3938dcd52561295253eac8708311cff2996c6a4b34982353fe832f9a195'
ROOT = Path(__file__).resolve().parents[1]
target = ROOT / 'third_party' / 'llama.cpp-omni'
if target.exists():
    raise SystemExit('Engine directory exists; preserving it. See third_party/NOTICE.md for provenance.')
with urllib.request.urlopen(f'https://codeload.github.com/tc-mb/llama.cpp-omni/zip/{COMMIT}', timeout=180) as response:
    data = response.read()
if hashlib.sha256(data).hexdigest() != SHA256:
    raise SystemExit('Engine checksum mismatch; refusing to use the archive.')
prefix = f'llama.cpp-omni-{COMMIT}/'
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    for entry in archive.infolist():
        if not entry.filename.startswith(prefix):
            raise SystemExit('Unexpected archive path')
        relative = Path(entry.filename[len(prefix):])
        if '..' in relative.parts or relative.is_absolute():
            raise SystemExit('Unsafe archive path')
        out = target / relative
        if entry.is_dir():
            out.mkdir(parents=True, exist_ok=True)
        else:
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(archive.read(entry))
patch(target)
print('Verified engine ready with Android F_NOCACHE compatibility guards:', COMMIT)
