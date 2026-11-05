'use strict';

(() => {
  const guide = 'https://github.com/Siroko-0516/new_orphan/blob/main/inquiry-site/README.md';
  const form = document.getElementById('inquiry-form');
  form.addEventListener('submit', (event) => {
    if (!form.reportValidity()) { event.preventDefault(); return; }
    const topic = document.getElementById('topic').value;
    const title = document.getElementById('inquiry-title');
    const body = document.getElementById('inquiry-body');
    // Open a real GitHub draft. No message is sent by this website.
    const draft = new URL(form.action);
    draft.searchParams.set('template', 'project-inquiry.md');
    draft.searchParams.set('title', `[${topic}] ${title.value.trim()}`);
    draft.searchParams.set('body', `## 문의 종류\n${topic}\n\n## 문의 내용\n${body.value.trim()}\n\n---\n길잡이 프로젝트 소개·문의 페이지에서 작성했습니다.`);
    const link = document.createElement('a');
    link.href = draft.href;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    document.body.append(link);
    link.click();
    link.remove();
    event.preventDefault();
    document.getElementById('form-status').textContent = 'GitHub 작성 화면을 열었습니다. 로그인 후 내용을 확인하고 최종 제출해주세요. 새 탭이 열리지 않았다면 브라우저의 팝업 설정을 확인해주세요.';
  });

  const urlInput = document.getElementById('poster-url');
  const context = document.getElementById('qr-context');
  const status = document.getElementById('qr-status');
  const isPublic = location.protocol === 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
  const target = isPublic ? new URL('./', location.href).href : guide;
  urlInput.value = target;
  context.textContent = isPublic
    ? '이 QR은 현재 공개 사이트로 연결됩니다. 인쇄 전에 휴대폰으로 한 번 스캔해주세요.'
    : '로컬 미리보기의 QR은 공개 GitHub 안내 페이지로 연결됩니다. 공개 사이트에서는 해당 사이트 주소로 바뀝니다.';

  let matrix;
  let svg;
  try {
    matrix = qrcode(0, 'M');
    matrix.addData(target, 'Byte');
    matrix.make();
    svg = matrix.createSvgTag({cellSize: 8, margin: 32, scalable: true});
    const qr = document.getElementById('qr-preview');
    qr.innerHTML = svg; // Generated QR paths only; target text is encoded in modules, never HTML.
    qr.querySelector('svg').setAttribute('role', 'img');
    qr.querySelector('svg').setAttribute('aria-label', '길잡이 소개와 문의 페이지로 연결되는 QR 코드');
  } catch {
    status.textContent = 'QR 생성기를 불러오지 못했습니다. 링크를 복사하거나 기본 QR 파일을 저장해주세요.';
    document.getElementById('download-png').disabled = true;
  }

  function save(blob, name) {
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = name;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
  }

  document.getElementById('copy-link').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(target);
      status.textContent = '연결 주소를 복사했습니다.';
    } catch {
      urlInput.focus();
      urlInput.select();
      status.textContent = '주소를 선택했습니다. 길게 누르거나 Ctrl+C / ⌘C로 복사해주세요.';
    }
  });
  document.getElementById('download-svg').addEventListener('click', () => {
    if (!svg) { location.href = './assets/poster-qr.svg'; return; }
    save(new Blob([svg], {type: 'image/svg+xml;charset=utf-8'}), 'giljabi-poster-qr.svg');
    status.textContent = '인쇄용 SVG QR 이미지를 저장합니다.';
  });
  document.getElementById('download-png').addEventListener('click', () => {
    if (!matrix) return;
    const count = matrix.getModuleCount();
    const quiet = 4;
    const scale = Math.ceil(1200 / (count + quiet * 2));
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = (count + quiet * 2) * scale;
    const draw = canvas.getContext('2d');
    draw.fillStyle = '#fff';
    draw.fillRect(0, 0, canvas.width, canvas.height);
    draw.fillStyle = '#173f35';
    for (let y = 0; y < count; y++) {
      for (let x = 0; x < count; x++) {
        if (matrix.isDark(y, x)) draw.fillRect((x + quiet) * scale, (y + quiet) * scale, scale, scale);
      }
    }
    canvas.toBlob((blob) => {
      if (blob) { save(blob, 'giljabi-poster-qr.png'); status.textContent = '고해상도 PNG QR 이미지를 저장합니다.'; }
      else status.textContent = 'QR 이미지를 저장하지 못했습니다. SVG 저장을 이용해주세요.';
    }, 'image/png');
  });
})();
