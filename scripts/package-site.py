"""Produce Cloudflare upload ZIPs without bundling personal credentials or models."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'
OUTPUT = ROOT / 'release-web'
OUTPUT.mkdir(exist_ok=True)
if not (DIST / 'index.html').is_file():
    raise SystemExit('Run npm run build first')
for item in DIST.rglob('*'):
    if item.is_file() and item.stat().st_size > 25 * 1024 * 1024:
        raise SystemExit(f'Cloudflare Pages file limit exceeded: {item.relative_to(DIST)}')

environment = dict(os.environ, WRANGLER_LOG_PATH=str(Path(tempfile.gettempdir()) / 'giljabi-pages-build.log'), WRANGLER_SEND_METRICS='false')
worker = DIST / '_worker.js'
if worker.is_dir():
    shutil.rmtree(worker)
elif worker.exists():
    worker.unlink()
with tempfile.TemporaryDirectory(prefix='giljabi-worker-') as temporary:
    subprocess.run(['node', 'node_modules/wrangler/bin/wrangler.js', 'pages', 'functions', 'build', 'functions', '--outdir=' + temporary, '--minify', '--compatibility-date=2025-10-01'], cwd=ROOT, env=environment, check=True)
    shutil.copytree(temporary, worker)
subprocess.run(['node', '--check', str(worker / 'index.js')], check=True)
report = {}
for name, include_worker in [('giljabi-web-cloudflare.zip', True), ('giljabi-web-static.zip', False)]:
    target = OUTPUT / name
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for item in sorted(DIST.rglob('*')):
            if not item.is_file() or not include_worker and (item.relative_to(DIST).parts[0] == '_worker.js' or item.name == '_routes.json'):
                continue
            archive.write(item, item.relative_to(DIST))
    with target.open('rb') as source:
        digest = hashlib.file_digest(source, 'sha256').hexdigest()
    (OUTPUT / (name + '.sha256')).write_text(digest + '  ' + name + '\n')
    report[name] = {'bytes': target.stat().st_size, 'sha256': digest, 'functions_included': include_worker}
(OUTPUT / 'checks.json').write_text(json.dumps(report, indent=2) + '\n')
if (ROOT / 'WEB_VALIDATION.json').is_file():
    shutil.copyfile(ROOT / 'WEB_VALIDATION.json', OUTPUT / 'validation.json')
print(json.dumps(report, indent=2))
