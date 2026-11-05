"""Exercise real site UI, GitHub draft URLs and QR decoding without submitting inquiries."""
import asyncio
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import mimetypes
import os
from pathlib import Path
import shutil
import sys
import tempfile
import threading
from urllib.parse import parse_qs, urlparse, unquote
from PIL import Image
from playwright.async_api import async_playwright
import zxingcpp

ROOT = Path(__file__).resolve().parent
DIST = ROOT / 'dist'
OUTPUT = ROOT.parent / 'release-inquiry'
GUIDE = 'https://github.com/Siroko-0516/new_orphan/blob/main/inquiry-site/README.md'
HEADERS = dict(line.strip().split(': ', 1) for line in (ROOT / '_headers').read_text().splitlines() if line.startswith('  '))

class QuietServer(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def end_headers(self):
        for key, value in HEADERS.items():
            self.send_header(key, value)
        super().end_headers()

def decode(path, expected):
    result = zxingcpp.read_barcode(Image.open(path))
    assert result and result.text == expected, f'QR decode failed: {path}'

async def main():
    OUTPUT.mkdir(exist_ok=True)
    handler = functools.partial(QuietServer, directory=str(DIST))
    server = ThreadingHTTPServer(('127.0.0.1', 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = f'http://127.0.0.1:{server.server_port}/'
    errors, external, layouts, drafts = [], [], [], []
    report = {'site': 'giljabi-inquiry-site', 'real_inquiries_submitted': 0, 'paid_requests': 0, 'cloudflare_public_deployment_verified': False}
    browser_path = os.environ.get('PLAYWRIGHT_CHROMIUM_EXECUTABLE') or shutil.which('chromium') or shutil.which('google-chrome')

    async def route(request):
        url = urlparse(request.request.url)
        if url.hostname in ('127.0.0.1', 'localhost'):
            await request.continue_()
        elif url.hostname == 'inquiry.example.test':
            relative = unquote(url.path.removeprefix('/giljabi/')).lstrip('/') or 'index.html'
            file = (DIST / relative).resolve()
            if not file.is_relative_to(DIST.resolve()) or not file.is_file():
                await request.fulfill(status=404, body='Missing fixture asset')
                return
            await request.fulfill(status=200, body=file.read_bytes(), content_type=mimetypes.guess_type(str(file))[0] or 'application/octet-stream', headers=HEADERS)
        elif url.hostname == 'github.com' and url.path == '/Siroko-0516/new_orphan/issues/new':
            drafts.append(request.request.url)
            await request.fulfill(status=200, content_type='text/html', body='<html lang="ko"><title>문의 작성 시험</title><p>Test fixture: no inquiry submitted.</p></html>')
        else:
            external.append(request.request.url)
            await request.abort()

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(executable_path=browser_path, headless=True, args=['--no-sandbox', '--disable-dev-shm-usage'])
        try:
            for width, height in [(1440, 1000), (390, 844), (320, 740), (768, 1024)]:
                context = await browser.new_context(viewport={'width': width, 'height': height}, reduced_motion='reduce')
                await context.route('**/*', route)
                page = await context.new_page()
                page.on('pageerror', lambda error: errors.append(str(error)))
                await page.goto(base, wait_until='networkidle')
                await page.evaluate('document.fonts.ready')
                assert await page.evaluate('document.fonts.check(\'700 32px "Giljabi Site Sans"\', "길잡이")'), 'Bundled Korean font did not load'
                session = await context.new_cdp_session(page)
                await session.send('DOM.enable')
                await session.send('CSS.enable')
                document = await session.send('DOM.getDocument')
                for selector in ['#hero-title', '.system-illustration svg text']:
                    node = await session.send('DOM.querySelector', {'nodeId': document['root']['nodeId'], 'selector': selector})
                    fonts = await session.send('CSS.getPlatformFontsForNode', {'nodeId': node['nodeId']})
                    assert any(f['isCustomFont'] and f['glyphCount'] > 0 for f in fonts['fonts']), (selector, fonts)
                await session.detach()
                assert await page.title() == '길잡이 · 프로젝트 소개와 문의'
                assert await page.locator('h1').count() == 1
                assert await page.locator('main').count() == 1
                assert await page.locator('img').evaluate_all('(imgs) => imgs.every(i => i.complete && i.naturalWidth > 0)')
                size = await page.evaluate('({viewport: innerWidth, content: document.documentElement.scrollWidth})')
                assert size['content'] <= width, f'Horizontal overflow at {width}: {size}'
                assert await page.locator('#poster-url').input_value() == GUIDE
                for link in await page.locator('a[href^="#"]').evaluate_all('(links) => links.map(a => a.getAttribute("href"))'):
                    assert link == '#' or await page.locator(link).count() == 1, link
                assert await page.locator('#inquiry-title').get_attribute('required') is not None
                assert await page.locator('label[for="inquiry-title"]').count() == 1
                assert await page.locator('label[for="inquiry-body"]').count() == 1
                if width in (1440, 390):
                    await page.screenshot(path=str(OUTPUT / ('site-desktop.png' if width == 1440 else 'site-mobile.png')), full_page=True)
                summary = page.locator('.faq-list details').first.locator('summary')
                await summary.focus()
                await page.keyboard.press('Enter')
                assert await page.locator('.faq-list details').first.evaluate('(d) => d.open')
                layouts.append({'width': width, 'height': height, 'no_horizontal_overflow': True, 'images_loaded': True, 'keyboard_faq': True})
                if width == 390:
                    pages_before = len(context.pages)
                    await page.locator('#inquiry-form button[type="submit"]').click()
                    assert len(context.pages) == pages_before
                    assert await page.locator('#inquiry-title').evaluate('(i) => i.validity.valueMissing')
                    await page.select_option('#topic', '프로젝트·협업')
                    await page.fill('#inquiry-title', '포스터를 보고 문의합니다')
                    await page.fill('#inquiry-body', '프로젝트의 시연 방법을 알고 싶어요.')
                    async with page.expect_popup() as opened:
                        await page.locator('#inquiry-form button[type="submit"]').click()
                    popup = await opened.value
                    await popup.wait_for_load_state()
                    values = parse_qs(urlparse(popup.url).query)
                    assert values['template'] == ['project-inquiry.md']
                    assert values['title'] == ['[프로젝트·협업] 포스터를 보고 문의합니다']
                    assert '프로젝트의 시연 방법을 알고 싶어요.' in values['body'][0]
                    assert await page.locator('#form-status').inner_text()
                    await popup.close()
                    # Keep a worst-case Korean draft under common 8KB request-line limits.
                    await page.fill('#inquiry-title', '가' * 100)
                    await page.fill('#inquiry-body', '가' * 600)
                    async with page.expect_popup() as opened:
                        await page.locator('#inquiry-form button[type="submit"]').click()
                    popup = await opened.value
                    await popup.wait_for_load_state()
                    assert len(popup.url) < 8000, len(popup.url)
                    await popup.close()
                    report['inquiry_draft'] = {'fields_preserved': True, 'required_fields_enforced': True, 'github_submission_required': True, 'korean_draft_under_8kb': True, 'real_network': False}
                await context.close()

            for url, expected in [(base, GUIDE), ('https://inquiry.example.test/giljabi/', 'https://inquiry.example.test/giljabi/')]:
                context = await browser.new_context(viewport={'width': 390, 'height': 844}, reduced_motion='reduce')
                await context.route('**/*', route)
                page = await context.new_page()
                page.on('pageerror', lambda error: errors.append(str(error)))
                await page.goto(url, wait_until='networkidle')
                assert await page.locator('#poster-url').input_value() == expected
                await page.locator('#poster-tools summary').click()
                with tempfile.TemporaryDirectory(prefix='giljabi-qr-test-') as temporary:
                    async with page.expect_download() as download:
                        await page.click('#download-png')
                    saved = Path(temporary) / 'export.png'
                    await (await download.value).save_as(saved)
                    decode(saved, expected)
                    async with page.expect_download() as download:
                        await page.click('#download-svg')
                    svg = Path(temporary) / 'export.svg'
                    await (await download.value).save_as(svg)
                    assert '<svg' in svg.read_text() and '<path' in svg.read_text()
                    await page.set_content('<img alt="test QR" id="svg-test">')
                    await page.locator('#svg-test').evaluate('(img, svg) => { img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg); }', svg.read_text())
                    await page.wait_for_function('document.getElementById("svg-test").complete && document.getElementById("svg-test").naturalWidth > 0')
                    rendered = await page.locator('#svg-test').screenshot()
                    result = zxingcpp.read_barcode(Image.open(io.BytesIO(rendered)))
                    assert result and result.text == expected, 'SVG QR cannot be scanned'
                await context.close()
            report['qr_exports'] = {'local_fallback_to_public_guide': True, 'mock_https_uses_own_site_url': True, 'downloaded_png_independently_decoded': True, 'downloaded_svg_independently_decoded': True, 'decoder': 'ZXing C++ 2.3.0', 'https_fixture_is_not_a_deployed_site': True}

            context = await browser.new_context(java_script_enabled=False, reduced_motion='reduce')
            await context.route('**/*', route)
            page = await context.new_page()
            await page.goto(base)
            assert await page.locator('noscript').is_visible()
            await page.fill('#inquiry-title', '자바스크립트 없는 문의')
            await page.fill('#inquiry-body', '기본 문의 화면을 확인합니다.')
            async with page.expect_popup() as opened:
                await page.locator('#inquiry-form button[type="submit"]').click()
            popup = await opened.value
            await popup.wait_for_load_state()
            assert parse_qs(urlparse(popup.url).query)['body'] == ['기본 문의 화면을 확인합니다.']
            report['without_javascript'] = {'inquiry_draft_works': True, 'static_qr_available': True}
            await context.close()
        finally:
            await browser.close()
            server.shutdown()

    decode(ROOT / 'assets/poster-qr.png', GUIDE)
    assert not external, external
    assert not errors, errors
    report.update({'passed': True, 'layouts': layouts, 'page_errors': errors, 'unexpected_external_requests': external, 'cloudflare_headers_applied_locally': True, 'bundled_korean_font_loaded_and_used': True, 'diagram_uses_bundled_korean_font': True})
    (OUTPUT / 'browser-checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__':
    asyncio.run(main())
