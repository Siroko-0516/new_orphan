const $ = id => document.getElementById(id);
let stream, running = false, generation = 0, timer, lastAnalysis = 0, lastSpoken = '', lastSpeechAt = 0;
let speechController, audio, audioUrl;
const capture = document.createElement('canvas');
const ctx = $('overlay').getContext('2d');
const names = {person:'사람', bicycle:'자전거', car:'자동차', motorcycle:'오토바이', bus:'버스', truck:'트럭', dog:'개', cat:'고양이', chair:'의자', bench:'벤치', 'traffic light':'신호등', 'stop sign':'정지 표지판', backpack:'가방', umbrella:'우산'};
function notice(message) { $('notice').textContent = message; $('notice').hidden = !message; }
async function api(path, body, signal) {
  const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body), signal});
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(typeof error.detail === 'string' ? error.detail : '요청을 처리하지 못했습니다.'); }
  return response;
}
function frame() {
  const v = $('video');
  capture.width = Math.min(800, v.videoWidth);
  capture.height = Math.round(capture.width * v.videoHeight / v.videoWidth);
  capture.getContext('2d').drawImage(v, 0, 0, capture.width, capture.height);
  return capture.toDataURL('image/jpeg', 0.72);
}
function draw(detections) {
  const canvas = $('overlay'), video = $('video');
  canvas.width = video.clientWidth; canvas.height = video.clientHeight;
  const scale = Math.min(canvas.width / video.videoWidth, canvas.height / video.videoHeight);
  const w = video.videoWidth * scale, h = video.videoHeight * scale;
  const ox = (canvas.width-w)/2, oy=(canvas.height-h)/2;
  ctx.font = '12px system-ui'; ctx.lineWidth = 2;
  for (const d of detections) {
    const [x1,y1,x2,y2] = d.box, label = `${names[d.label] || d.label} ${Math.round(d.confidence*100)}%`;
    ctx.strokeStyle = '#c7eb8a'; ctx.fillStyle = '#c7eb8a';
    ctx.strokeRect(ox+x1*w,oy+y1*h,(x2-x1)*w,(y2-y1)*h);
    const y = Math.max(18, oy+y1*h);
    ctx.fillRect(ox+x1*w,y-18,ctx.measureText(label).width+12,18);
    ctx.fillStyle='#20351c';ctx.fillText(label,ox+x1*w+6,y-5);
  }
  $('count').textContent = `${detections.length}개`;
  $('objects').replaceChildren();
  if (!detections.length) { const p = document.createElement('p'); p.className='empty'; p.textContent='현재 감지된 물체가 없습니다.'; $('objects').append(p); }
  for (const d of detections) {const row=document.createElement('div'); row.className='object'; const label=document.createElement('span'),score=document.createElement('span');label.textContent=names[d.label]||d.label;score.textContent=`${Math.round(d.confidence*100)}%`;row.append(label,score);$('objects').append(row);}
}
function cancelSpeech() {
  speechController?.abort();
  if (audio) { audio.pause(); audio = null; }
  if (audioUrl) { URL.revokeObjectURL(audioUrl); audioUrl = null; }
  window.speechSynthesis?.cancel();
}
function nativeSpeak(text) {
  if (!('speechSynthesis' in window)) { $('tts-led').textContent='미지원';notice('이 브라우저는 기본 음성을 지원하지 않습니다. 화면의 안내를 확인하세요.');return; }
  const utterance=new SpeechSynthesisUtterance(text);utterance.lang='ko-KR';utterance.rate=1;
  const voices=speechSynthesis.getVoices(); utterance.voice=voices.find(v=>v.lang.startsWith('ko'))||null;
  $('tts-led').textContent='안내 중';$('tts-mode').textContent='브라우저 기본 TTS';
  utterance.onend=()=>{$('tts-led').textContent='대기';};
  utterance.onerror=()=>{$('tts-led').textContent='실패';notice('음성을 재생하지 못했습니다. 기기의 음성 설정을 확인하세요.');};
  speechSynthesis.speak(utterance);
}
async function speak(text) {
  if (!$('voice').checked) return;
  cancelSpeech();
  const controller = new AbortController();speechController=controller;
  const timeout=setTimeout(()=>controller.abort('timeout'),9000);
  try {
    const response=await api('/api/speech',{text},controller.signal);
    const blob=await response.blob();
    if(controller.signal.aborted) return;
    audioUrl=URL.createObjectURL(blob);audio=new Audio(audioUrl);
    $('tts-mode').textContent='ElevenLabs';$('tts-led').textContent='안내 중';
    audio.onended=()=>{$('tts-led').textContent='대기';if(audioUrl)URL.revokeObjectURL(audioUrl);audioUrl=null;};
    await audio.play();
  } catch (error) {
    if(controller.signal.aborted && controller.signal.reason !== 'timeout') return;
    nativeSpeak(text);
  } finally {clearTimeout(timeout);}
}
let aiConfigured=false, assessmentBusy=false;
async function assessScene(image, detections, token) {
  assessmentBusy=true;
  try {
      lastAnalysis=Date.now();$('ai-led').textContent='분석 중';
      try {
        const verdict=await (await api('/api/assess',{image,detections},AbortSignal.timeout(22000))).json();
        if (!running || token!==generation) return;
        $('risk').className=`risk ${verdict.level}`;
        $('risk-title').textContent=({low:'특이 위험 미감지',medium:'주변을 살펴주세요',high:'주의가 필요합니다',unknown:'판단이 어렵습니다'})[verdict.level];
        $('risk-icon').textContent=verdict.level==='high'?'⚠':'◉';$('guidance').textContent=verdict.message;
        $('updated').textContent=`${new Date().toLocaleTimeString('ko-KR')} 분석 · 단일 장면 기준`;$('ai-led').textContent='연결됨';
        if(['medium','high'].includes(verdict.level) && Date.now()-lastSpeechAt>12000 && (verdict.message!==lastSpoken || Date.now()-lastSpeechAt>30000)) {
          lastSpoken=verdict.message;lastSpeechAt=Date.now();void speak(verdict.message);
        }
      } catch(error) {
        if (!running || token!==generation) return;
        $('ai-led').textContent='연결 실패';$('risk').className='risk unknown';$('risk-title').textContent='현재 판단 불가';$('guidance').textContent=error.message;
      }
  } finally { assessmentBusy=false; }
}
async function loop(token) {
  if (!running || token!==generation) return;
  try {
    const image=frame();
    const result=await (await api('/api/detect',{image},AbortSignal.timeout(12000))).json();
    if (!running || token!==generation) return;
    draw(result.detections);$('yolo-led').textContent='감지 중';
    if(aiConfigured && !assessmentBusy && Date.now()-lastAnalysis>6000) {
      void assessScene(image, result.detections, token);
    }
  } catch(error) {if(running && token===generation){notice(error.message);$('yolo-led').textContent='감지 실패';}}
  if(running && token===generation)timer=setTimeout(()=>loop(token),700);
}
function stop() {
  running=false;generation++;clearTimeout(timer);stream?.getTracks().forEach(t=>t.stop());stream=null;
  $('video').srcObject=null;$('video').classList.remove('active');$('placeholder').hidden=false;
  ctx.clearRect(0,0,$('overlay').width,$('overlay').height);$('start').disabled=false;$('stop').disabled=true;
  $('camera-state').textContent='중지됨';$('yolo-led').textContent='대기';$('ai-led').textContent='대기';
  $('risk').className='risk unknown';$('risk-title').textContent='안내 중지됨';$('guidance').textContent='다시 시작하면 주변 상황을 분석합니다.';
  cancelSpeech();$('tts-led').textContent='대기';
}
$('start').onclick=async()=>{
  $('start').disabled=true;notice('');
  try {
    if(!navigator.mediaDevices?.getUserMedia)throw new Error('카메라를 사용하려면 HTTPS 또는 localhost에서 접속하세요.');
    stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1280}},audio:false});
    const video=$('video');video.srcObject=stream;await video.play();
    video.classList.add('active');$('placeholder').hidden=true;$('stop').disabled=false;$('camera-state').textContent='실시간 감지 중';
    running=true;lastAnalysis=0;lastSpoken='';lastSpeechAt=0;void loop(++generation);
    if(!aiConfigured)notice('AI API 키가 설정되지 않아 YOLO 물체 감지만 실행합니다. 위험 판단은 제공되지 않습니다.');
  }catch(error){stop();notice(error.name==='NotAllowedError'?'카메라 권한이 필요합니다. 브라우저 설정에서 허용한 후 다시 시작하세요.':error.message);}
};
$('stop').onclick=stop;$('voice').onchange=()=>{if(!$('voice').checked)cancelSpeech();};
$('test-speech').onclick=()=>{void speak('길잡이 음성 안내입니다. 주변을 살피며 이동하세요.');};
document.addEventListener('visibilitychange',()=>{if(document.hidden&&running)stop();});
window.addEventListener('pagehide',stop);
fetch('/api/status').then(r=>{if(!r.ok)throw new Error();return r.json();}).then(s=>{aiConfigured=s.ai_configured;$('provider').textContent=`${s.provider==='gemini'?'Gemini':'GPT'} · ${aiConfigured?'연결 준비':'키 필요'}`;$('ai-led').textContent=aiConfigured?'준비':'키 필요';$('tts-mode').textContent=s.elevenlabs_configured?'ElevenLabs → 기본 TTS':'브라우저 기본 TTS';}).catch(()=>notice('서버에 연결하지 못했습니다. 서버 실행 상태를 확인하세요.'));
