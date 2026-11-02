export const ELEVEN_VOICES = Object.freeze({
  female: 'lhTvHflPVOqgSWyuWQry',
  male: 'LS3HmRGCXV8wxCAhUbTt',
});

const cancelled = () => new DOMException('Speech cancelled', 'AbortError');

/** One cancellable narration at a time. No keys are persisted by this class. */
export class BrowserSpeech {
  constructor({ status = () => {}, onStart = () => {} } = {}) {
    this.status = status;
    this.onStart = onStart;
    this.sequence = 0;
    this.job = null;
    // Starting voice enumeration early avoids losing the user's activation later.
    globalThis.speechSynthesis?.getVoices();
  }

  speak(text, options = {}) {
    this._stop('superseded', false);
    const message = String(text || '').trim();
    if (!message) return Promise.resolve({ started: false, reason: 'empty' });
    const manual = options.manual === true;
    const remaining = options.remainingMs;
    const budget = typeof remaining === 'number' ? Math.max(0, remaining)
      : typeof remaining === 'function' || manual ? Infinity : 5000;
    const job = {
      id: ++this.sequence, text: message, options, manual,
      deadline: Number.isFinite(budget) ? Date.now() + budget : Infinity,
      controller: new AbortController(), started: false, provider: null,
      finished: false, timers: new Set(), audio: null, objectUrl: null,
    };
    this.job = job;
    const completion = new Promise(resolve => { job.resolve = resolve; });
    if (!this._valid(job)) {
      this._finish(job, { cancelled: true, reason: 'stale' });
      return completion;
    }
    job.poll = setInterval(() => {
      if (!this._valid(job)) this._cancel(job, 'stale');
    }, 100);
    void this._run(job);
    return completion;
  }

  stop() { this._stop('stopped', true); }

  testNative(text = '기본 음성 테스트입니다. 소리가 들리면 정상입니다.') {
    return this.speak(text, { cloud: false, manual: true });
  }

  _remaining(job) {
    let remaining = job.deadline - Date.now();
    if (typeof job.options.remainingMs === 'function') {
      try { remaining = Math.min(remaining, Number(job.options.remainingMs())); }
      catch { return 0; }
    }
    return Number.isNaN(remaining) ? 0 : remaining;
  }

  _valid(job) {
    if (this.job !== job || job.finished || job.controller.signal.aborted || this._remaining(job) <= 0) return false;
    try { return job.options.valid ? job.options.valid() === true : true; }
    catch { return false; }
  }

  _assert(job) {
    if (!this._valid(job)) throw cancelled();
  }

  _status(job, state, message, provider = job.provider) {
    if (!this._valid(job)) return;
    this.status(state, message, provider);
  }

  _timer(job, callback, milliseconds) {
    const timer = setTimeout(() => {
      job.timers.delete(timer);
      callback();
    }, Math.max(0, milliseconds));
    job.timers.add(timer);
    return timer;
  }

  _clearTimer(job, timer) {
    clearTimeout(timer);
    job.timers.delete(timer);
  }

  _started(job, provider) {
    if (!this._valid(job)) { this._cancel(job, 'stale'); return; }
    job.provider = provider;
    this._status(job, 'speaking', provider === 'native' ? '기본 한국어 음성 안내 중' : 'ElevenLabs 음성 안내 중', provider);
    if (job.started) return;
    job.started = true;
    // Deduplication belongs to the caller; only real playback commits narration.
    job.options.onStart?.();
    this.onStart?.();
  }

  async _run(job) {
    const cloud = job.options.cloud === true;
    const hasEleven = Boolean(job.options.elevenKey?.trim() || job.options.server);
    try {
      if (cloud && hasEleven) {
        try {
          await this._eleven(job);
        } catch (error) {
          if (!this._valid(job) || error.name === 'AbortError') throw cancelled();
          this._releaseAudio(job);
          await this._native(job, 'ElevenLabs 연결 또는 재생 실패. 기본 한국어 음성으로 전환합니다.');
        }
      } else {
        await this._native(job);
      }
      this._assert(job);
      this._status(job, 'idle', '음성 안내 완료');
      this._finish(job, { completed: true });
    } catch (error) {
      if (job.finished) return;
      if (!this._valid(job) || error.name === 'AbortError') {
        this._cancel(job, 'stale');
      } else {
        this._status(job, 'error', error.message || '음성을 재생하지 못했습니다.', job.provider || 'native');
        this._finish(job, { reason: 'playback-failed' });
      }
    }
  }

  async _eleven(job) {
    this._assert(job);
    job.provider = 'elevenlabs';
    this._status(job, 'loading', 'ElevenLabs 음성 생성 중');
    const timeout = Math.min(job.manual ? 7000 : 4000, this._remaining(job) - 150);
    if (timeout <= 0) throw new Error('음성 생성 시간이 부족합니다.');
    const fetchController = new AbortController();
    const startDeadline = Date.now() + timeout;
    const abort = () => fetchController.abort();
    job.controller.signal.addEventListener('abort', abort, { once: true });
    let timedOut = false;
    const timer = this._timer(job, () => { timedOut = true; fetchController.abort(); }, timeout);
    let blob;
    try {
      const key = job.options.elevenKey?.trim();
      const voice = job.options.voice === 'male' ? 'male' : 'female';
      const direct = !job.options.server && Boolean(key);
      const serverUrl = globalThis.document?.baseURI
        ? new URL('api/speech', document.baseURI).href : '/api/speech';
      const response = await fetch(direct
        ? `https://api.elevenlabs.io/v1/text-to-speech/${ELEVEN_VOICES[voice]}`
        : serverUrl, {
        method: 'POST', signal: fetchController.signal,
        headers: direct
          ? { 'Content-Type': 'application/json', Accept: 'audio/mpeg', 'xi-api-key': key }
          : { 'Content-Type': 'application/json' },
        body: JSON.stringify(direct
          ? { text: job.text, model_id: 'eleven_multilingual_v2' }
          : { text: job.text, apiKey: key || '', voice }),
      });
      this._assert(job);
      if (!response.ok) throw new Error('ElevenLabs 음성 요청에 실패했습니다.');
      const contentType = response.headers.get('content-type') || '';
      if (!contentType.startsWith('audio/')) throw new Error('ElevenLabs 음성 응답 형식이 잘못됐습니다.');
      blob = await response.blob();
      this._assert(job);
      if (timedOut || !blob.size) throw new Error('ElevenLabs 음성을 받지 못했습니다.');
    } catch (error) {
      if (!this._valid(job)) throw cancelled();
      if (timedOut) throw new Error('ElevenLabs 응답 시간이 초과됐습니다.');
      throw error;
    } finally {
      this._clearTimer(job, timer);
      job.controller.signal.removeEventListener('abort', abort);
    }
    this._assert(job);
    job.objectUrl = URL.createObjectURL(blob);
    job.audio = new Audio(job.objectUrl);
    await new Promise((resolve, reject) => {
      const audio = job.audio;
      const abortPlayback = () => { cleanup(); reject(cancelled()); };
      const cleanup = () => {
        this._clearTimer(job, startTimer);
        job.controller.signal.removeEventListener('abort', abortPlayback);
        audio.onplaying = audio.onended = audio.onerror = null;
      };
      const startTimer = this._timer(job, () => {
        cleanup(); reject(new Error('오디오 재생이 시작되지 않았습니다.'));
      }, Math.min(1500, this._remaining(job), Math.max(0, startDeadline - Date.now())));
      audio.onplaying = () => {
        this._clearTimer(job, startTimer);
        this._started(job, 'elevenlabs');
      };
      audio.onended = () => { cleanup(); resolve(); };
      audio.onerror = () => { cleanup(); reject(new Error('오디오 파일을 재생하지 못했습니다.')); };
      job.controller.signal.addEventListener('abort', abortPlayback, { once: true });
      Promise.resolve(audio.play()).catch(() => {
        cleanup(); reject(new Error('브라우저가 오디오 재생을 차단했습니다.'));
      });
    });
  }

  async _koreanVoice(job) {
    const synth = globalThis.speechSynthesis;
    if (!synth || !globalThis.SpeechSynthesisUtterance) throw new Error('이 브라우저는 기본 음성을 지원하지 않습니다.');
    const choose = () => synth.getVoices().find(voice => /^ko(?:-|_)/i.test(voice.lang) || voice.lang === 'ko');
    const current = choose();
    if (current) return current;
    return new Promise((resolve, reject) => {
      const cleanup = () => {
        this._clearTimer(job, timer);
        synth.removeEventListener?.('voiceschanged', changed);
        job.controller.signal.removeEventListener('abort', abort);
      };
      const changed = () => {
        const voice = choose();
        if (voice) { cleanup(); resolve(voice); }
      };
      const abort = () => { cleanup(); reject(cancelled()); };
      const timer = this._timer(job, () => {
        cleanup(); reject(new Error('한국어 기본 음성이 없습니다. 기기에서 한국어 TTS를 설치하세요.'));
      }, Math.min(800, this._remaining(job)));
      synth.addEventListener?.('voiceschanged', changed);
      job.controller.signal.addEventListener('abort', abort, { once: true });
      changed();
    });
  }

  async _native(job, fallbackMessage = '') {
    this._assert(job);
    job.provider = 'native';
    this._status(job, 'loading', fallbackMessage || '기본 한국어 음성 준비 중', 'native');
    const voice = await this._koreanVoice(job);
    this._assert(job);
    const utterance = new SpeechSynthesisUtterance(job.text);
    utterance.lang = 'ko-KR';
    utterance.voice = voice;
    utterance.rate = 1;
    job.utterance = utterance; // Retain the utterance until its end/error event.
    await new Promise((resolve, reject) => {
      const cleanup = () => {
        this._clearTimer(job, startTimer);
        job.controller.signal.removeEventListener('abort', abort);
        utterance.onstart = utterance.onend = utterance.onerror = null;
      };
      const abort = () => { cleanup(); reject(cancelled()); };
      const startTimer = this._timer(job, () => {
        cleanup(); reject(new Error('음성이 시작되지 않았습니다. 기본 음성 테스트 버튼을 직접 눌러주세요.'));
      }, Math.min(1500, this._remaining(job)));
      utterance.onstart = () => {
        this._clearTimer(job, startTimer);
        this._started(job, 'native');
      };
      utterance.onend = () => { cleanup(); resolve(); };
      utterance.onerror = event => {
        cleanup();
        reject(event.error === 'canceled' || event.error === 'interrupted' ? cancelled()
          : new Error(event.error === 'not-allowed'
            ? '브라우저가 음성을 차단했습니다. 기본 음성 테스트 버튼을 직접 눌러주세요.'
            : '기본 한국어 음성을 재생하지 못했습니다. 기기 음량과 음성 설정을 확인하세요.'));
      };
      job.controller.signal.addEventListener('abort', abort, { once: true });
      globalThis.speechSynthesis.resume?.();
      globalThis.speechSynthesis.speak(utterance);
    });
  }

  _releaseAudio(job) {
    if (job.audio) {
      job.audio.onplaying = job.audio.onended = job.audio.onerror = null;
      job.audio.pause();
      job.audio.removeAttribute?.('src');
      job.audio.load?.();
      job.audio = null;
    }
    if (job.objectUrl) { URL.revokeObjectURL(job.objectUrl); job.objectUrl = null; }
  }

  _finish(job, result) {
    if (job.finished) return;
    job.finished = true;
    clearInterval(job.poll);
    for (const timer of job.timers) clearTimeout(timer);
    job.timers.clear();
    job.controller.abort();
    this._releaseAudio(job);
    if (job.utterance) {
      job.utterance.onstart = job.utterance.onend = job.utterance.onerror = null;
      globalThis.speechSynthesis?.cancel();
      job.utterance = null;
    }
    if (this.job === job) this.job = null;
    job.resolve({ started: job.started, provider: job.provider, ...result });
  }

  _cancel(job, reason) { this._finish(job, { cancelled: true, reason }); }

  _stop(reason, announce) {
    if (this.job) this._cancel(this.job, reason);
    // Clear any speech queued by this page before a new meaningful announcement.
    globalThis.speechSynthesis?.cancel();
    if (announce) this.status('idle', '음성 안내 중지', null);
  }
}
