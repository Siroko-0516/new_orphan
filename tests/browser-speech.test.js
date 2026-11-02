import test from 'node:test';
import assert from 'node:assert/strict';
import { BrowserSpeech, ELEVEN_VOICES } from '../static/browser-speech.js';

function setup(t, { voices = [{ lang: 'ko-KR', name: 'Korean' }], nativeAuto = true, audioMode = 'success' } = {}) {
  const originals = new Map();
  function replace(name, value) {
    originals.set(name, Object.getOwnPropertyDescriptor(globalThis, name));
    Object.defineProperty(globalThis, name, { configurable: true, writable: true, value });
  }
  const spoken = [], audio = [], statuses = [], callbacks = [];
  let cancels = 0;
  const events = new EventTarget();
  replace('speechSynthesis', {
    getVoices: () => voices, cancel: () => { cancels++; }, resume() {},
    addEventListener: events.addEventListener.bind(events),
    removeEventListener: events.removeEventListener.bind(events),
    speak(utterance) {
      spoken.push(utterance);
      if (nativeAuto) {
        setTimeout(() => utterance.onstart?.(), 1);
        setTimeout(() => utterance.onend?.(), 5);
      }
    },
  });
  replace('SpeechSynthesisUtterance', class { constructor(text) { this.text = text; } });
  replace('Audio', class {
    constructor(url) { this.url = url; audio.push(this); }
    pause() { this.paused = true; }
    removeAttribute() {}
    load() {}
    play() {
      if (audioMode === 'blocked') return Promise.reject(new Error('autoplay blocked'));
      if (audioMode === 'decode') {
        queueMicrotask(() => this.onerror?.());
        return Promise.reject(new Error('bad audio'));
      }
      setTimeout(() => this.onplaying?.(), 1);
      setTimeout(() => this.onended?.(), 5);
      return Promise.resolve();
    }
  });
  t.after(() => {
    for (const [name, descriptor] of originals) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else delete globalThis[name];
    }
  });
  const speech = new BrowserSpeech({
    status: (...args) => statuses.push(args),
    onStart: () => callbacks.push('start'),
  });
  t.after(() => speech.stop());
  return { speech, spoken, audio, statuses, callbacks, cancels: () => cancels };
}

test('local mode never calls the network and commits only on actual native start', async t => {
  const state = setup(t, { nativeAuto: false });
  t.mock.method(globalThis, 'fetch', () => { throw new Error('network forbidden'); });
  const promise = state.speech.speak('앞에 사람이 있습니다.', { cloud: false, elevenKey: 'must-not-be-used' });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(state.callbacks.length, 0);
  assert.equal(state.spoken[0].voice.lang, 'ko-KR');
  state.spoken[0].onstart();
  assert.equal(state.callbacks.length, 1);
  state.spoken[0].onend();
  assert.equal((await promise).provider, 'native');
});

test('direct ElevenLabs selects the fixed voice without exposing the key in status', async t => {
  const state = setup(t);
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.ok(url.endsWith(ELEVEN_VOICES.male));
    assert.equal(options.headers['xi-api-key'], 'personal-secret');
    assert.equal(JSON.parse(options.body).model_id, 'eleven_multilingual_v2');
    return new Response('audio', { headers: { 'Content-Type': 'audio/mpeg' } });
  });
  const result = await state.speech.speak('주변을 확인하세요.', { cloud: true, elevenKey: 'personal-secret', voice: 'male' });
  assert.equal(result.provider, 'elevenlabs');
  assert.equal(result.started, true);
  assert.equal(state.callbacks.length, 1);
  assert.equal(state.spoken.length, 0);
  assert.ok(!JSON.stringify(state.statuses).includes('personal-secret'));
});

test('configured server receives no browser key and uses selected voice', async t => {
  const state = setup(t);
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/speech');
    assert.deepEqual(JSON.parse(options.body), { text: '안내', apiKey: '', voice: 'female' });
    return new Response('audio', { headers: { 'Content-Type': 'audio/mpeg' } });
  });
  assert.equal((await state.speech.speak('안내', { cloud: true, server: true })).provider, 'elevenlabs');
});

test('available same-origin server takes precedence over a personal key under a subpath', async t => {
  const state = setup(t);
  const original = Object.getOwnPropertyDescriptor(globalThis, 'document');
  Object.defineProperty(globalThis, 'document', { configurable: true, value: { baseURI: 'https://example.com/project/' } });
  t.after(() => {
    if (original) Object.defineProperty(globalThis, 'document', original);
    else delete globalThis.document;
  });
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, 'https://example.com/project/api/speech');
    assert.deepEqual(JSON.parse(options.body), { text: '안내', apiKey: 'personal-secret', voice: 'male' });
    assert.equal(options.headers['xi-api-key'], undefined);
    return new Response('audio', { headers: { 'Content-Type': 'audio/mpeg' } });
  });
  const result = await state.speech.speak('안내', { cloud: true, server: true, elevenKey: 'personal-secret', voice: 'male' });
  assert.equal(result.provider, 'elevenlabs');
});

for (const mode of ['http', 'blocked', 'decode']) test(`ElevenLabs ${mode} failure falls back exactly once`, async t => {
  const state = setup(t, { audioMode: mode });
  t.mock.method(globalThis, 'fetch', async () => mode === 'http'
    ? new Response('', { status: 401 })
    : new Response('audio', { headers: { 'Content-Type': 'audio/mpeg' } }));
  const result = await state.speech.speak('주의하세요.', { cloud: true, server: true });
  assert.equal(result.provider, 'native');
  assert.equal(state.spoken.length, 1);
  assert.equal(state.callbacks.length, 1);
});

test('stop during a cloud request never causes fallback or dedup commit', async t => {
  const state = setup(t);
  t.mock.method(globalThis, 'fetch', (_, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError')));
  }));
  const promise = state.speech.speak('오래된 안내', { cloud: true, server: true });
  state.speech.stop();
  assert.equal((await promise).cancelled, true);
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(state.spoken.length, 0);
  assert.equal(state.callbacks.length, 0);
});

test('freshness invalidation stops a native announcement already in progress', async t => {
  const state = setup(t, { nativeAuto: false });
  let valid = true;
  const promise = state.speech.speak('지금 장면', { cloud: false, valid: () => valid });
  await new Promise(resolve => setTimeout(resolve, 0));
  state.spoken[0].onstart();
  valid = false;
  const result = await promise;
  assert.equal(result.reason, 'stale');
  assert.equal(result.started, true);
  assert.ok(state.cancels() >= 2);
});

test('dynamic freshness may extend after actual start and controls the end of playback', async t => {
  const state = setup(t, { nativeAuto: false });
  const realNow = Date.now;
  let now = realNow(), started = false;
  const capturedAt = now;
  t.mock.method(Date, 'now', () => now);
  const remaining = () => capturedAt + (started ? 12000 : 5000) - now;
  const promise = state.speech.speak('장면 설명을 마저 읽습니다.', {
    cloud: false, remainingMs: remaining, valid: () => remaining() > 0,
    onStart: () => { started = true; },
  });
  await new Promise(resolve => setTimeout(resolve, 0));
  state.spoken[0].onstart();
  now = capturedAt + 6000;
  await new Promise(resolve => setTimeout(resolve, 120));
  assert.ok(state.spoken[0].onend, 'playback must survive the original five-second deadline');
  state.spoken[0].onend();
  const result = await promise;
  assert.equal(result.completed, true);
  assert.equal(result.cancelled, undefined);
});

test('the network wait respects the remaining photo lifetime and then falls back', async t => {
  const state = setup(t);
  t.mock.method(globalThis, 'fetch', (_, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('timeout', 'AbortError')));
  }));
  const result = await state.speech.speak('신선한 장면', { cloud: true, server: true, remainingMs: 400 });
  assert.equal(result.provider, 'native');
  assert.equal(state.spoken.length, 1);
});

test('a Korean voice is required: never auto-select English', async t => {
  const state = setup(t, { voices: [{ lang: 'en-US', name: 'English' }] });
  const result = await state.speech.testNative();
  assert.equal(result.started, false);
  assert.equal(state.spoken.length, 0);
  assert.ok(state.statuses.some(([name, message]) => name === 'error' && message.includes('한국어')));
});
