import { BrowserYOLO } from './browser-yolo.js';
const $ = id => document.getElementById(id);
const detector = new BrowserYOLO();
let stream, running = false, generation = 0, timer, lastAnalysis = 0, lastSpoken = '', lastSpeechAt = 0;
let speechController, audio, audioUrl, activeUtterance, speechEpoch=0, lastLocalSpeechAt=0;
let elevenlabsConfigured=false, lastScene='', lastSceneAt=0;
const capture = document.createElement('canvas');
const ctx = $('overlay').getContext('2d');
const names = {person:'사람', bicycle:'자전거', car:'자동차', motorcycle:'오토바이', bus:'버스', truck:'트럭', dog:'개', cat:'고양이', chair:'의자', bench:'벤치', 'traffic light':'신호등', 'stop sign':'정지 표지판', backpack:'가방', umbrella:'우산'};
function notice(message) { $('notice').textContent = message; $('notice').hidden = !message; }
async function api(path, body, signal) {
  const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body), signal});
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(typeof error.detail === 'string' ? error.detail : '요청을 처리하지 못했습니다.'); }
  return response;
}
function captureFrame() {
  const v = $('video');
  capture.width = Math.min(800, v.videoWidth);
  capture.height = Math.round(capture.width * v.videoHeight / v.videoWidth);
  capture.getContext('2d').drawImage(v, 0, 0, capture.width, capture.height);
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
  speechEpoch++;
  speechController?.abort();
  speechController=null;
  if (audio) { audio.pause(); audio = null; }
  if (audioUrl) { URL.revokeObjectURL(audioUrl); audioUrl = null; }
  window.speechSynthesis?.cancel();
  activeUtterance=null;
}
function nativeSpeak(text, reason='') {
  if (!('speechSynthesis' in window)) { $('tts-led').textContent='미지원';notice('이 브라우저는 기본 음성을 지원하지 않습니다. 화면의 안내를 확인하세요.');return; }
  const utterance=new SpeechSynthesisUtterance(text);utterance.lang='ko-KR';utterance.rate=1;
  activeUtterance=utterance;
  const epoch=speechEpoch;
  const voices=speechSynthesis.getVoices(); utterance.voice=voices.find(v=>v.lang.startsWith('ko'))||null;
  $('tts-led').textContent='안내 중';$('tts-mode').textContent=reason?'기본 TTS · ElevenLabs 실패':'브라우저 기본 TTS';
  $('tts-feedback').textContent=reason||'기기의 한국어 음성으로 재생합니다.';
  utterance.onend=()=>{if(epoch!==speechEpoch)return;activeUtterance=null;$('tts-led').textContent='대기';};
  utterance.onerror=event=>{
    if(epoch!==speechEpoch)return;activeUtterance=null;
    if(['canceled','interrupted'].includes(event.error))return;
    $('tts-led').textContent='실패';
    $('tts-feedback').textContent=event.error==='not-allowed'?'브라우저가 자동 음성을 차단했습니다. 기본 음성 테스트 버튼을 직접 눌러주세요.':'기본 음성을 재생하지 못했습니다. 기기 음량과 한국어 음성 설치를 확인하세요.';
  };
  speechSynthesis.speak(utterance);
}
async function speak(text) {
  if (!$('voice').checked) return;
  cancelSpeech();
  if(!elevenlabsConfigured){nativeSpeak(text);return;}
  const controller = new AbortController();speechController=controller;
  const epoch=speechEpoch;
  $('tts-led').textContent='음성 생성 중';$('tts-feedback').textContent='ElevenLabs 음성을 요청하고 있습니다.';
  const timeout=setTimeout(()=>controller.abort('timeout'),12000);
  let fellBack=false;
  try {
    const response=await api('/api/speech',{text},controller.signal);
    const blob=await response.blob();
    if(controller.signal.aborted) return;
    audioUrl=URL.createObjectURL(blob);audio=new Audio(audioUrl);
    $('tts-mode').textContent='ElevenLabs';$('tts-led').textContent='안내 중';$('tts-feedback').textContent='ElevenLabs 음성을 재생합니다.';
    const fallback=message=>{
      if(epoch!==speechEpoch||controller.signal.aborted||fellBack)return;
      fellBack=true;
      audio?.pause();if(audioUrl)URL.revokeObjectURL(audioUrl);audioUrl=null;audio=null;
      nativeSpeak(text,message);
    };
    audio.onerror=()=>fallback('받은 음성을 재생하지 못해 기본 TTS로 전환했습니다.');
    audio.onended=()=>{if(epoch!==speechEpoch)return;$('tts-led').textContent='대기';if(audioUrl)URL.revokeObjectURL(audioUrl);audioUrl=null;};
    await audio.play();
  } catch (error) {
    if(fellBack)return;
    if(controller.signal.aborted && controller.signal.reason !== 'timeout') return;
    if(epoch!==speechEpoch)return;
    audio?.pause();if(audioUrl)URL.revokeObjectURL(audioUrl);audioUrl=null;audio=null;
    nativeSpeak(text,controller.signal.reason==='timeout'?'ElevenLabs 응답이 늦어 기본 TTS로 전환했습니다.':`${error.name==='NotAllowedError'?'브라우저가 오디오 자동 재생을 차단했습니다.':error.message} 기본 TTS로 전환합니다.`);
  } finally {clearTimeout(timeout);if(speechController===controller)speechController=null;}
}
let aiConfigured=false, assessmentBusy=false;
function alertUser(text, immediate=false) {
  if(!$('voice').checked)return;
  const now=Date.now();
  if(immediate){
    if(activeUtterance||speechController||audio&&!audio.paused)return;
    if(now-lastLocalSpeechAt<12000||(text===lastSpoken&&now-lastLocalSpeechAt<30000))return;
    lastLocalSpeechAt=now;lastSpoken=text;cancelSpeech();nativeSpeak(text);return;
  }
  if(now-lastSpeechAt<12000 || (text===lastSpoken && now-lastSpeechAt<30000))return;
  lastSpoken=text;lastSpeechAt=now;
  void speak(text);
}
function localAlert(detections) {
  const important=new Set(['person','bicycle','car','motorcycle','bus','truck','dog','chair','bench']);
  const obstacle=detections.find(d=>important.has(d.label)&&d.box[0]<0.6&&d.box[2]>0.4&&(d.box[2]-d.box[0])*(d.box[3]-d.box[1])>0.12);
  $('local-alert').hidden=!obstacle;
  if(obstacle){
    const message=`화면 중앙에 ${names[obstacle.label]||obstacle.label} 감지. 주변을 확인하세요.`;
    $('local-alert').textContent=`빠른 물체 알림 · ${message}`;
    alertUser(message,true);
  }
}
async function assessScene(image, detections, token) {
  assessmentBusy=true;
  try {
      lastAnalysis=Date.now();$('ai-led').textContent='분석 중';
      try {
        const verdict=await (await api('/api/assess',{image,detections},AbortSignal.timeout(35000))).json();
        if (!running || token!==generation || !$('ai-optin').checked) return;
        $('risk').className=`risk ${verdict.level}`;
        $('risk-title').textContent=({low:'특이 위험 미감지',medium:'주변을 살펴주세요',high:'주의가 필요합니다',unknown:'판단이 어렵습니다'})[verdict.level];
        $('risk-icon').textContent=verdict.level==='high'?'⚠':'◉';$('guidance').textContent=verdict.message;
        $('updated').textContent=`${new Date().toLocaleTimeString('ko-KR')} 분석 · 단일 장면 기준`;$('ai-led').textContent='연결됨';
        alertUser(verdict.message);
      } catch(error) {
        if (!running || token!==generation || !$('ai-optin').checked) return;
        $('ai-led').textContent='연결 실패';$('risk').className='risk unknown';$('risk-title').textContent='현재 판단 불가';$('guidance').textContent=error.message;
      }
  } finally { assessmentBusy=false; }
}
async function loop(token) {
  if (!running || token!==generation) return;
  const loopStart=performance.now();
  try {
    captureFrame();
    const result=await detector.detect(capture);
    if (!running || token!==generation) return;
    draw(result.detections);$('yolo-led').textContent=`${result.ms}ms`;
    $('camera-state').textContent=`기기 내 감지 · ${result.backend}`;
    localAlert(result.detections);
    const signature=[...new Set(result.detections.map(d=>`${d.label}:${d.box.map(v=>Math.round(v*4)).join('.')}`))].sort().join(',');
    if(aiConfigured && $('ai-optin').checked && !assessmentBusy && Date.now()-lastAnalysis>10000 && (signature!==lastScene || Date.now()-lastSceneAt>30000)) {
      lastScene=signature;lastSceneAt=Date.now();
      void assessScene(capture.toDataURL('image/jpeg',0.65), result.detections, token);
    }
  } catch(error) {if(running && token===generation){notice(error.message);$('yolo-led').textContent='감지 실패';}}
  if(running && token===generation)timer=setTimeout(()=>loop(token),Math.max(0,200-(performance.now()-loopStart)));
}
function stop() {
  running=false;generation++;clearTimeout(timer);stream?.getTracks().forEach(t=>t.stop());stream=null;
  $('video').srcObject=null;$('video').classList.remove('active');$('placeholder').hidden=false;
  ctx.clearRect(0,0,$('overlay').width,$('overlay').height);$('start').disabled=false;$('stop').disabled=true;
  $('camera-state').textContent='중지됨';$('yolo-led').textContent='대기';$('ai-led').textContent='대기';
  $('risk').className='risk unknown';$('risk-title').textContent='안내 중지됨';$('guidance').textContent='다시 시작하면 주변 상황을 분석합니다.';
  $('local-alert').hidden=true;$('objects').replaceChildren();$('count').textContent='0개';
  cancelSpeech();$('tts-led').textContent='대기';
}
$('start').onclick=async()=>{
  const token=++generation;running=true;
  $('start').disabled=true;$('stop').disabled=false;notice('');
  try {
    if(!navigator.mediaDevices?.getUserMedia)throw new Error('카메라를 사용하려면 HTTPS 또는 localhost에서 접속하세요.');
    $('camera-state').textContent='YOLO 모델 로딩 중';$('yolo-led').textContent='로딩 중';
    notice('첫 실행은 모델과 실행 파일을 내려받아 시간이 걸릴 수 있습니다. 이후에는 브라우저 캐시를 사용합니다.');
    await detector.load();
    if(!running||token!==generation)return;
    const acquired=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:960}},audio:false});
    if(!running||token!==generation){acquired.getTracks().forEach(t=>t.stop());return;}
    stream=acquired;
    const video=$('video');video.srcObject=stream;await video.play();
    if(!running||token!==generation)return;
    video.classList.add('active');$('placeholder').hidden=true;$('stop').disabled=false;$('camera-state').textContent='실시간 감지 중';
    lastAnalysis=0;lastSceneAt=0;lastSpoken='';lastSpeechAt=0;lastLocalSpeechAt=0;void loop(token);
    notice(!aiConfigured?'기기 내 YOLO 감지와 빠른 물체 알림을 사용합니다. AI 장면 판단은 서버 키 설정 후 켤 수 있습니다.':'');
  }catch(error){if(token===generation){stop();notice(error.name==='NotAllowedError'?'카메라 권한이 필요합니다. 브라우저 설정에서 허용한 후 다시 시작하세요.':error.message);}}
};
$('stop').onclick=stop;$('voice').onchange=()=>{if(!$('voice').checked)cancelSpeech();};
$('test-speech').onclick=()=>{void speak('길잡이 음성 안내입니다. 주변을 살피며 이동하세요.');};
$('test-native').onclick=()=>{
  if(!$('voice').checked){$('tts-feedback').textContent='먼저 음성 안내를 켜주세요.';return;}
  cancelSpeech();nativeSpeak('기본 음성 테스트입니다. 소리가 들리면 정상입니다.');
};
window.speechSynthesis?.getVoices();
$('ai-optin').onchange=()=>{
  lastAnalysis=0;lastSceneAt=0;
  if(!$('ai-optin').checked){$('ai-led').textContent='꺼짐';$('risk').className='risk unknown';$('risk-title').textContent='AI 설명 꺼짐';$('guidance').textContent='물체 감지와 빠른 물체 알림은 기기에서 계속됩니다.';cancelSpeech();}
};
document.addEventListener('visibilitychange',()=>{if(document.hidden&&running)stop();});
window.addEventListener('pagehide',stop);
fetch('/api/status').then(r=>{if(!r.ok)throw new Error();return r.json();}).then(s=>{aiConfigured=s.ai_configured;elevenlabsConfigured=s.elevenlabs_configured;$('ai-optin').disabled=!aiConfigured;$('provider').textContent=`${s.provider==='gemini'?'Gemini':'GPT'} · ${aiConfigured?'사용 가능':'키 필요'}`;$('ai-led').textContent=aiConfigured?'꺼짐':'키 필요';$('tts-mode').textContent=elevenlabsConfigured?'ElevenLabs → 기본 TTS':'브라우저 기본 TTS';}).catch(()=>{ $('ai-optin').disabled=true;$('provider').textContent='기기 내 모드';$('ai-led').textContent='미연결';notice('AI 서버에 연결하지 못했습니다. YOLO 감지와 기본 음성은 기기에서 사용할 수 있습니다.');});
