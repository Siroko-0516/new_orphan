"""Reproduce bundled OFL UI fonts. Optional tool: fonttools[woff]==4.60.1."""
from pathlib import Path
import argparse
import hashlib
import json
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--regular', default='/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
    parser.add_argument('--bold', default='/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc')
    parser.add_argument('--font-number', type=int, default=1, help='Korean face index in Noto Sans CJK TTC')
    parser.add_argument('--license', default='/usr/share/doc/fonts-noto-cjk/copyright')
    options = parser.parse_args()
    text = ''.join((ROOT / name).read_text() for name in ['index.html', 'site.css', 'site.js', 'assets/cane-system.svg'])
    codepoints = set(map(ord, text)) | set(range(32, 127))
    output = ROOT / 'assets/fonts'
    output.mkdir(exist_ok=True)
    provenance = {'upstream': 'https://github.com/notofonts/noto-cjk', 'license': 'SIL Open Font License 1.1', 'tool': 'fonttools[woff] 4.60.1', 'modifications': 'Korean UI/ASCII glyph subset, WOFF2 conversion, family renamed Giljabi Site Sans', 'fonts': []}
    copyrights = set()
    for weight, source in [('regular', options.regular), ('bold', options.bold)]:
        path = Path(source)
        font = TTFont(path, fontNumber=options.font_number)
        copyrights.add(font['name'].getDebugName(0) or '')
        names = {1: 'Giljabi Site Sans', 2: weight.title(), 3: 'GiljabiSiteSans-' + weight.title(), 4: 'Giljabi Site Sans ' + weight.title(), 6: 'GiljabiSiteSans-' + weight.title(), 16: 'Giljabi Site Sans', 17: weight.title()}
        for record in font['name'].names:
            if record.nameID in names:
                record.string = names[record.nameID].encode(record.getEncoding())
        if 'CFF ' in font:
            cff = font['CFF '].cff
            cff.fontNames[0] = names[6]
            cff.topDictIndex[0].FamilyName = names[1]
            cff.topDictIndex[0].FullName = names[4]
        settings = subset.Options()
        settings.name_IDs = ['*']
        settings.name_languages = ['*']
        processor = subset.Subsetter(options=settings)
        processor.populate(unicodes=codepoints)
        processor.subset(font)
        missing = [hex(c) for c in codepoints if 0xAC00 <= c <= 0xD7A3 and c not in font.getBestCmap()]
        if missing:
            raise SystemExit('Missing Korean glyphs: ' + ', '.join(missing))
        font.flavor = 'woff2'
        target = output / ('site-' + weight + '.woff2')
        font.save(target)
        provenance['fonts'].append({'weight': weight, 'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'source_version': font['name'].getDebugName(5), 'file': target.name, 'bytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'all_site_korean_glyphs_present': True})
        font.close()
    license_text = Path(options.license).read_text().split('\nLicense: SIL-1.1\n')[-1].split('\nLicense: GPL-3+\n')[0]
    license_text = '\n'.join('' if line.strip() == '.' else line.removeprefix(' ') for line in license_text.splitlines())
    (output / 'LICENSE.txt').write_text('\n'.join(sorted(copyrights)) + '\n\n' + license_text + '\n\nSubset webfonts renamed Giljabi Site Sans. Original attribution and OFL retained.\n')
    (output / 'provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(provenance, ensure_ascii=False))

if __name__ == '__main__':
    main()
