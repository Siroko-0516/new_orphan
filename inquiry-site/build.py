"""Build a standalone introduction/contact site; no keys or web-app models."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent
FILES = ['index.html', 'site.css', 'site.js', '_headers', 'robots.txt', '.nojekyll']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', action='store_true', help='Also make Cloudflare upload ZIP and poster QR downloads')
    options = parser.parse_args()
    output = ROOT / 'dist'
    if output.exists():
        shutil.rmtree(output)
    output.mkdir()
    for name in FILES:
        shutil.copy2(ROOT / name, output / name)
    document = (output / 'index.html').read_text()
    marker = '<!--SYSTEM_DIAGRAM-->'
    if marker not in document:
        raise SystemExit('Missing system diagram insertion point')
    diagram = (ROOT / 'assets/cane-system.svg').read_text().replace('<svg ', '<svg aria-hidden="true" ', 1)
    (output / 'index.html').write_text(document.replace(marker, diagram))
    shutil.copytree(ROOT / 'assets', output / 'assets')
    files = [p for p in output.rglob('*') if p.is_file()]
    for file in files:
        if file.stat().st_size > 25 * 1024 * 1024:
            raise SystemExit('Cloudflare Pages file limit exceeded: ' + file.name)
    report = {'files': len(files), 'bytes': sum(p.stat().st_size for p in files), 'standalone': True, 'backend_required': False, 'secrets_required': False}
    if options.release:
        release = ROOT.parent / 'release-inquiry'
        release.mkdir(exist_ok=True)
        archive = release / 'giljabi-inquiry-site.zip'
        with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as package:
            for file in sorted(files):
                package.write(file, file.relative_to(output))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        (release / (archive.name + '.sha256')).write_text(digest + '  ' + archive.name + '\n')
        for extension in ['png', 'svg']:
            shutil.copy2(output / ('assets/poster-qr.' + extension), release / ('giljabi-poster-qr.' + extension))
        shutil.copy2(ROOT / 'UPLOAD.txt', release / 'Cloudflare-Pages-upload.txt')
        report['zip'] = {'name': archive.name, 'bytes': archive.stat().st_size, 'sha256': digest}
        (release / 'checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__':
    main()
