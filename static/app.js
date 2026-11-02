import { BrowserYOLO } from './browser-yolo.js';
import { WalkingSceneGate, NarrationMemory, PhotoHistory, parseWalkingReply, isFresh } from './walking-guidance.js';
import { BrowserBle } from './browser-ble.js';
import { BrowserSpeech } from './browser-speech.js';
import { assessImages, normalizeKey, DEFAULT_GEMINI_MODEL } from './browser-ai.js';

const $ = id => document.getElementById(id);
const text = (id, value) => { if ($(id)?.textContent !== value) $(id).textContent = value; };
const names = { person:'사람', bicycle:'자전거', car:'자동차', motorcycle:'오토바이', bus:'버스', truck:'트럭', dog:'개', cat:'고양이', chair:'의자', bench:'벤치', couch:'소파', 'traffic light':'신호등', 'stop sign':'정지 표지판', backpack:'가방', umbrella:'우산', 'dining table':'테이블', 'potted plant':'화분' };
const detector = new BrowserYOLO();
const gate = new WalkingSceneGate();
const memory = new NarrationMemory();
const photos = new PhotoHistory({maxBytes:1_300_000});
const canvas = document.createElement('canvas');
const qualityCanvas = document.createElement('canvas'); qualityCanvas.width=24;qualityCanvas.height=24;
const overlay = $('overlay').getContext('2d');
let stream, source, timer, running=false, starting=false, generation=0, selectionEpoch=0, sequence=0;
let lastAnalysis=0, analysisController, analysisBusy=false, analysisRetryAt=0, manualImage='', manualUnclear=false, local, localLoading;
let selected='gemini', serverAvailable=false, serverGemini=false, serverEleven=false, model=DEFAULT_GEMINI_MODEL;
let settings={geminiKey:'',elevenKey:'',voice:'female',speech:'elevenlabs'};
let activeAnnouncement, installPrompt, wakeLock, cameraFailureCount=0;
let bleEpisode=null,bleWriting=false;const bleSeen=new Set();
const ble=new BrowserBle({status:(message,connected)=>{text('ble-status',message);$('ble-connect').disabled=connected||!ble.supported;$('ble-disconnect').disabled=!connected;$('ble-test').disabled=!connected;}});

function notice(message='') { text('notice',message);$('notice').hidden=!message; }
function result(message,title='상황 확인',urgency='notice') {
  text('guidance',message);text('risk-title',title);text('risk-icon',urgency==='urgent'?'!':'◉');
  $('risk').className=`risk ${urgency==='urgent'?'high':urgency==='none'?'unknown':'medium'}`;
}
const speech = new BrowserSpeech({status:(state,message,provider)=>{
  text('tts-led',({idle:'대기',loading:'음성 준비 중',speaking:'안내 중',error:'확인 필요'})[state]||state);
  text('tts-feedback',message);text('tts-mode',provider==='elevenlabs'?'ElevenLabs':'기본 한국어 음성');
}});

async function localRuntime() {
  if (!localLoading) localLoading=import('./local-ai.js').then(({BrowserLocalAI})=>{local=new BrowserLocalAI();return local;});
  return localLoading;
}
function showSettings(message) {
  $('settings-panel').open=true;
  text('settings-feedback',message);
  $('settings-panel').scrollIntoView({behavior:'smooth',block:'nearest'});
}
function clearAnalysis() {
  selectionEpoch++;analysisController?.abort();analysisController=null;analysisBusy=false;
  speech.stop();activeAnnouncement=null;gate.reset();memory.reset();photos.reset();lastAnalysis=0;analysisRetryAt=0;
}
function chooseModel(provider, close=true) {
  if(!['none','gemini','local'].includes(provider))return;
  clearAnalysis();if(selected==='local'&&provider!=='local')local?.stop();selected=provider;
  const label=({none:'물체 감지만',gemini:'Gemini',local:'로컬 AI'})[provider];
  text('selected-model',label);text('provider',label);text('ai-led',provider==='none'?'꺼짐':'준비');
  $('ai-optin').checked=provider!=='none';
  for(const button of document.querySelectorAll('[data-provider]'))button.setAttribute('aria-pressed',String(button.dataset.provider===provider));
  result(provider==='none'?'물체 위치는 화면에서 확인해요. 감지 결과를 음성으로 반복하지 않아요.':provider==='local'?'준비한 로컬 AI로 기기에서 사진을 분석해요. 음성은 기본 한국어 TTS를 사용해요.':'이전·최신 장면을 비교하고 필요한 변화가 있을 때 알려드려요.','분석 대기','none');
  text('mode-summary',provider==='local'?'로컬 AI는 기본 한국어 음성만 사용해요.':'Gemini는 선택 음성을 사용하고, ElevenLabs 실패 시 기본 음성으로 바꿔요.');
  $('analyze-photo').disabled=!manualImage||provider==='none';
  if(close)$('model-dialog').close();
}
function saveSettings() {
  try {
    const next={geminiKey:normalizeKey($('gemini-key').value),elevenKey:normalizeKey($('elevenlabs-key').value),voice:$('elevenlabs-voice').value,speech:$('speech-provider').value};
    clearAnalysis();settings=next;
    text('settings-feedback','현재 페이지에 적용했어요. 키는 저장하지 않으며 새로고침하면 사라져요.');
    updateConnection();
  }catch(error){text('settings-feedback',error.message);}
}
function updateConnection() {
  text('gemini-state',settings.geminiKey||serverGemini?'사진을 비교하고 필요한 변화만 알려줘요.':'설정에 개인 Gemini 키를 입력해주세요.');
  text('tts-mode',selected==='local'||settings.speech==='native'?'기본 한국어 음성':'ElevenLabs → 기본 한국어 음성');
}
function draw(detections) {
  const element=source;
  const sw=element?.videoWidth||element?.naturalWidth||canvas.width, sh=element?.videoHeight||element?.naturalHeight||canvas.height;
  const target=$('overlay');target.width=target.clientWidth;target.height=target.clientHeight;
  const scale=Math.min(target.width/sw,target.height/sh),w=sw*scale,h=sh*scale,ox=(target.width-w)/2,oy=(target.height-h)/2;
  overlay.font='12px system-ui';overlay.lineWidth=2;
  for(const item of detections){const [x1,y1,x2,y2]=item.box;overlay.strokeStyle='#c7eb8a';overlay.fillStyle='#c7eb8a';overlay.strokeRect(ox+x1*w,oy+y1*h,(x2-x1)*w,(y2-y1)*h);overlay.fillText(names[item.label]||item.label,ox+x1*w+3,Math.max(15,oy+y1*h-5));}
  text('count',`${detections.length}개`);$('objects').replaceChildren();
  if(!detections.length){const row=document.createElement('p');row.className='empty';row.textContent='현재 감지 정보가 없어요. 통로가 안전하다는 뜻은 아니에요.';$('objects').append(row);}
  for(const item of detections.slice(0,20)){const row=document.createElement('div');row.className='object';const label=document.createElement('span'),confidence=document.createElement('span');label.textContent=names[item.label]||item.label;confidence.textContent=`${Math.round(item.confidence*100)}%`;row.append(label,confidence);$('objects').append(row);}
}
function captureFrame() {
  const width=source?.videoWidth||source?.naturalWidth, height=source?.videoHeight||source?.naturalHeight;
  if(!width||!height)return false;
  canvas.width=Math.min(width,640);canvas.height=Math.round(canvas.width*height/width);
  canvas.getContext('2d').drawImage(source,0,0,canvas.width,canvas.height);return true;
}
function unclearFrame(frame=canvas) {
  const q=qualityCanvas.getContext('2d',{willReadFrequently:true});q.drawImage(frame,0,0,24,24);
  const pixels=q.getImageData(0,0,24,24).data;let sum=0,square=0;
  for(let i=0;i<pixels.length;i+=4){const gray=.299*pixels[i]+.587*pixels[i+1]+.114*pixels[i+2];sum+=gray;square+=gray*gray;}
  const n=pixels.length/4,mean=sum/n,variance=square/n-mean*mean;
  return mean<10||mean>247||variance<20;
}
function usefulLocalCaption(english, decision) {
  if(!english||/^\s*(?:none|no (?:visible )?(?:obstacle|obstruction))/i.test(english))return false;
  const hazard=/\b(?:stairs?|curb|pothole|hole|overhead|hanging|protruding)\b/i.test(english);
  const blockage=/\b(?:blocks?|blocked|blocking|obstruct\w*|walkway|pathway|path ahead|doorway)\b/i.test(english);
  const current=decision.context.tracks.some(t=>t.currently_visible&&t.corridor==='potential_path_cue');
  return hazard||blockage&&current;
}
async function narrate(reply,decision,session,selection,manual=false) {
  if(!$('voice').checked||!reply.speak)return;
  const reservation=manual?null:memory.reserve(decision.episode,decision.stateKey,reply);
  if(!manual&&!reservation)return;
  const announcement={started:false,decision,session,selection};activeAnnouncement=announcement;
  const valid=()=>activeAnnouncement===announcement&&session===generation&&selection===selectionEpoch&&$('voice').checked&&(manual||running&&gate.sameContext(decision)&&Date.now()-decision.capturedAt<=(announcement.started?12000:5000));
  const outcome=await speech.speak(reply.message,{
    cloud:selected==='gemini'&&settings.speech==='elevenlabs',elevenKey:settings.elevenKey,voice:settings.voice,
    server:serverAvailable&&(serverEleven||!!settings.elevenKey),manual,valid,
    remainingMs:manual?Infinity:()=>Math.max(0,(announcement.started?12000:5000)-(Date.now()-decision.capturedAt)),
    onStart:()=>{announcement.started=true;if(reservation)memory.commitReservation(reservation);},
  });
  if(reservation&&!outcome.started)memory.release(reservation);
  if(activeAnnouncement===announcement)activeAnnouncement=null;
}
async function analyze(image,decision,session,{manual=false}={}) {
  const selection=selectionEpoch,provider=selected;
  if(provider==='none')return;
  const controller=new AbortController();analysisController=controller;analysisBusy=true;
  const started=Date.now();lastAnalysis=started;text('ai-led','분석 중');
  try {
    let reply,timing='';
    if(manual&&manualUnclear){reply={valid:true,speak:true,message:'사진이 단색이거나 어두워 주변 내용을 구분하기 어려워요.',urgency:'notice'};}
    else if(provider==='gemini') {
      if(!settings.geminiKey&&!serverGemini){showSettings('개인 Gemini API 키를 입력하고 적용해주세요.');text('ai-led','키 설정 필요');analysisRetryAt=Infinity;return;}
      const frames=manual?[{image,capturedAt:started}]:photos.frames(decision.capturedAt);
      reply=await assessImages({images:frames.map(p=>p.image),apiKey:settings.geminiKey,model,server:serverAvailable,context:decision?.context||{},previousSpoken:manual?'':memory.previous(decision.episode),question:manual?$('photo-question').value:'',capturedAt:manual?started:decision.capturedAt,manual,signal:controller.signal});
    }else{
      const engine=await localRuntime();
      if(!engine.ready){showSettings('로컬 AI 준비 버튼으로 모델을 먼저 준비해주세요.');text('ai-led','로컬 준비 필요');analysisRetryAt=Infinity;return;}
      const value=await engine.describe(image,{signal:controller.signal,question:manual?$('photo-question').value:'',walking:!manual});
      timing=` · 이미지 ${(value.timings.captionMs/1000).toFixed(1)}초 · 번역 ${(value.timings.translationMs/1000).toFixed(1)}초 · WebGPU`;
      if(!manual&&(value.silent||!usefulLocalCaption(value.english,decision)))reply={valid:true,speak:false,message:'',urgency:'none'};
      else reply=parseWalkingReply(JSON.stringify({speak:true,message:value.message,urgency:'notice'}),{manual});
      if(!reply.valid)throw new Error('한국어 설명을 확인하지 못해 음성을 보류했어요.');
    }
    if(controller.signal.aborted||session!==generation||selection!==selectionEpoch||!manual&&!running)return;
    if(!manual){gate.accepted(decision);photos.markReviewed(decision.capturedAt);}
    const elapsed=((Date.now()-started)/1000).toFixed(1);
    text('updated',`${new Date().toLocaleTimeString('ko-KR')} · 분석 ${elapsed}초${timing}`);
    if(!manual&&!isFresh(decision.capturedAt)) {text('ai-led','이전 결과 음성 생략');text('camera-feedback','분석이 늦어 지난 장면의 음성은 읽지 않고 최신 사진을 확인해요.');return;}
    if(!manual&&!gate.sameContext(decision)){text('ai-led','상황 변화 · 다음 사진 대기');return;}
    if(!reply.speak){text('ai-led','조용히 확인 중');result('새로 안내할 내용이 없어 조용히 확인하고 있어요.','분석 중 · 새 안내 없음','none');return;}
    if(!manual&&!memory.eligible(decision.episode,decision.stateKey,reply)){text('ai-led','같은 안내 반복 생략');return;}
    result(reply.message,manual?'사진 설명':reply.urgency==='urgent'?'주의가 필요한 변화':'새로운 상황 안내',reply.urgency);
    text('ai-led','확인 완료');void narrate(reply,decision,session,selection,manual);
  }catch(error){
    if(controller.signal.aborted||session!==generation||selection!==selectionEpoch)return;
    text('ai-led','확인 필요');notice(error.name==='TimeoutError'?'AI 분석이 지연됐어요. 최신 장면으로 다시 확인해요.':error.message);
    analysisRetryAt=Date.now()+10000;
  }finally{if(analysisController===controller){analysisController=null;analysisBusy=false;}if(manual)$('analyze-photo').disabled=!manualImage;}
}
async function loop(session) {
  if(!running||session!==generation)return;
  const started=performance.now();
  try {
    if(!captureFrame()){timer=setTimeout(()=>loop(session),200);return;}
    const capturedAt=Date.now(),image=canvas.toDataURL('image/jpeg',.72);
    const detections=(await detector.detect(canvas)).detections;
    if(!running||session!==generation)return;
    cameraFailureCount=0;draw(detections);text('yolo-led',`${Math.round(performance.now()-started)}ms · 기기 내 감지`);
    gate.observe(++sequence,detections,capturedAt);photos.add(image,capturedAt);
    const decision=gate.decision(sequence)||gate.review(sequence);
    if(ble.connected&&$('ble-notify').checked&&!bleWriting){if(bleEpisode!==decision.episode){bleEpisode=decision.episode;bleSeen.clear();}const cues=decision.stateKey.split('|').filter(cue=>cue!=='no_confirmed_path_cues'&&!bleSeen.has(cue));if(cues.length){bleWriting=true;void ble.send('OBSTACLE\n').then(sent=>{if(sent&&session===generation)for(const cue of cues)bleSeen.add(cue);}).finally(()=>bleWriting=false);}}
    if(selected!=='none'&&!analysisBusy&&Date.now()>=analysisRetryAt&&Date.now()-lastAnalysis>=Number($('analysis-interval').value)*1000){
      if(unclearFrame()){text('ai-led','영상 확인 어려움');result('영상이 단색이거나 어두워 주변을 구분하기 어려워요.','카메라 방향을 확인해주세요.','none');lastAnalysis=Date.now();}
      else void analyze(image,decision,session);
    }
  }catch(error){
    if(running&&session===generation){
      notice(error.name==='SecurityError'?'ESP 영상의 교차 출처 접근이 차단됐어요. 카메라 서버의 CORS·HTTPS 설정이 필요해요.':error.message);
      if(++cameraFailureCount>=3||error.name==='SecurityError'){stop('카메라 연결을 확인해주세요.');return;}
    }
  }
  if(running&&session===generation)timer=setTimeout(()=>loop(session),Math.max(50,250-(performance.now()-started)));
}
async function start() {
  if(running||starting)return;
  const session=++generation;running=true;starting=true;clearAnalysis();
  $('start').disabled=true;$('stop').disabled=false;$('camera-source').disabled=true;bleSeen.clear();bleEpisode=null;notice('');
  $('photo-preview').hidden=true;$('esp-preview').hidden=true;
  try {
    text('camera-state','카메라 준비 중');
    if($('camera-source').value==='esp'){
      const url=new URL($('esp-url').value);if(!['http:','https:'].includes(url.protocol))throw new Error('HTTP 또는 HTTPS 카메라 주소를 입력해주세요.');
      if(location.protocol==='https:'&&url.protocol==='http:')throw new Error('HTTPS 웹사이트에서는 HTTP ESP 영상을 연결할 수 없어요. HTTPS·CORS를 지원하는 카메라 연결이 필요해요. 휴대폰 카메라는 바로 사용할 수 있어요.');
      const esp=$('esp-preview');source=esp;esp.crossOrigin='anonymous';
      await new Promise((resolve,reject)=>{const id=setTimeout(()=>reject(new Error('ESP 영상을 받지 못했어요. 주소와 같은 Wi-Fi 연결을 확인해주세요.')),12000);esp.onload=()=>{clearTimeout(id);resolve();};esp.onerror=()=>{clearTimeout(id);reject(new Error('ESP 영상 연결을 확인해주세요.'));};esp.src=url.href;});
      if(!running||session!==generation)return;esp.hidden=false;
    }else{
      if(!navigator.mediaDevices?.getUserMedia)throw new Error('HTTPS 주소 또는 localhost에서 카메라를 사용할 수 있어요.');
      const acquired=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:$('camera-source').value==='front'?'user':'environment'},width:{ideal:960}},audio:false});
      if(!running||session!==generation){acquired.getTracks().forEach(t=>t.stop());return;}
      stream=acquired;const video=$('video');source=video;video.srcObject=stream;await video.play();if(!running||session!==generation)return;video.classList.add('active');
    }
    if(!running||session!==generation)return;
    $('placeholder').hidden=true;text('camera-state','카메라 연결됨');text('camera-feedback','이 탭에서 주변을 확인해요. 화면을 잠그거나 다른 탭으로 이동하면 중지해요.');
    text('yolo-led','모델 준비 중');await detector.load();
    if(!running||session!==generation)return;
    try{wakeLock=await navigator.wakeLock?.request('screen');}catch{/* Optional device support. */}
    void loop(session);
  }catch(error){if(session===generation){stop('카메라 연결 전');notice(error.name==='NotAllowedError'?'카메라 사용을 허용한 뒤 다시 시작해주세요.':error.message);}}
  finally{if(session===generation)starting=false;}
}
function stop(message='안내를 중지했어요.') {
  running=false;starting=false;generation++;clearTimeout(timer);clearAnalysis();
  local?.stop();stream?.getTracks().forEach(track=>track.stop());stream=null;
  $('video').pause();$('video').srcObject=null;$('video').classList.remove('active');
  $('esp-preview').removeAttribute('src');$('esp-preview').hidden=true;source=null;
  wakeLock?.release().catch(()=>{});wakeLock=null;
  $('placeholder').hidden=!!manualImage;$('photo-preview').hidden=!manualImage;
  $('start').disabled=false;$('stop').disabled=true;$('camera-source').disabled=false;
  result(message,'안내 중지','none');text('updated','');text('camera-state',message);text('camera-feedback','사용할 카메라를 고른 뒤 시작해주세요.');text('ai-led','중지됨');text('yolo-led','대기');
  $('overlay').getContext('2d').clearRect(0,0,$('overlay').width,$('overlay').height);
  $('objects').replaceChildren();text('count','0개');
}

$('start').onclick=start;$('stop').onclick=()=>stop();
$('choose-model').onclick=()=>$('model-dialog').showModal();
for(const button of document.querySelectorAll('[data-provider]'))button.onclick=()=>chooseModel(button.dataset.provider);
$('camera-source').onchange=()=>{$('esp-settings').hidden=$('camera-source').value!=='esp';};
$('save-settings').onclick=saveSettings;
$('clear-keys').onclick=()=>{$('gemini-key').value='';$('elevenlabs-key').value='';settings.geminiKey='';settings.elevenKey='';clearAnalysis();text('settings-feedback','현재 페이지의 키를 지웠어요.');updateConnection();};
$('voice').onchange=()=>{if(!$('voice').checked){speech.stop();activeAnnouncement=null;} $('guidance').setAttribute('aria-live',$('voice').checked?'off':'polite');};
$('test-native').onclick=()=>{activeAnnouncement=null;void speech.testNative();};
$('test-speech').onclick=()=>{activeAnnouncement=null;void speech.speak('길잡이 음성 안내입니다. 필요한 변화가 있을 때 알려드려요.',{cloud:selected==='gemini'&&settings.speech==='elevenlabs',elevenKey:settings.elevenKey,voice:settings.voice,server:serverAvailable&&(serverEleven||!!settings.elevenKey),manual:true});};
$('prepare-local').onclick=async()=>{
  $('prepare-local').disabled=true;
  try{const engine=await localRuntime();await engine.prepare(progress=>text('local-progress',typeof progress==='string'?progress:`${progress.stage||'준비 중'} · ${Math.min(100,Math.round((progress.overallLoaded||0)/(progress.overallTotal||1)*100))}% · ${(Number(progress.overallLoaded||0)/1e6).toFixed(0)}MB`));text('local-progress','로컬 AI와 한국어 번역을 준비했어요. 사진은 기기에서 처리해요.');analysisRetryAt=0;lastAnalysis=0;}
  catch(error){text('local-progress',error.message);}
  finally{$('prepare-local').disabled=false;}
};
$('photo-input').onchange=async()=>{
  const file=$('photo-input').files?.[0];if(!file)return;
  stop('사진 확인 중');manualImage='';$('analyze-photo').disabled=true;
  try{
    if(file.size>25*1024*1024)throw new Error('25MB 이하의 사진을 선택해주세요.');
    const image=await createImageBitmap(file);if(!image.width||!image.height){image.close();throw new Error('사진을 읽지 못했어요.');}
    const photo=document.createElement('canvas'),scale=Math.min(1,800/Math.max(image.width,image.height));photo.width=Math.max(1,Math.round(image.width*scale));photo.height=Math.max(1,Math.round(image.height*scale));photo.getContext('2d').drawImage(image,0,0,photo.width,photo.height);image.close();
    manualUnclear=unclearFrame(photo);manualImage=photo.toDataURL('image/jpeg',.8);$('photo-preview').src=manualImage;$('photo-preview').hidden=false;$('placeholder').hidden=true;$('analyze-photo').disabled=selected==='none';
    text('camera-state','사진 준비됨');notice('');
  }catch(error){notice(error.message||'사진을 읽지 못했어요. 다른 사진을 선택해주세요.');}
};
$('analyze-photo').onclick=()=>{if(!manualImage||analysisBusy)return;if(running)stop('사진 설명으로 전환했어요.');$('analyze-photo').disabled=true;void analyze(manualImage,null,generation,{manual:true});};
window.addEventListener('pagehide',()=>{stop();ble.disconnect();});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&(running||analysisBusy))stop('다른 화면으로 이동해 중지했어요.');});
window.addEventListener('beforeinstallprompt',event=>{event.preventDefault();installPrompt=event;$('install-app').hidden=false;});
$('install-app').onclick=async()=>{if(installPrompt){await installPrompt.prompt();installPrompt=null;$('install-app').hidden=true;}};

$('ble-connect').onclick=()=>{void ble.connect();};$('ble-disconnect').onclick=()=>ble.disconnect();$('ble-test').onclick=()=>{void ble.send('TEST\n');};
$('ble-connect').disabled=!ble.supported;if(!ble.supported)text('ble-status','이 브라우저에서는 BLE 연결을 지원하지 않아요. 카메라·AI 안내는 사용할 수 있어요.');
chooseModel('gemini',false);$('guidance').setAttribute('aria-live','off');
fetch(new URL('api/status',document.baseURI)).then(response=>{if(!response.ok)throw new Error();return response.json();}).then(status=>{
  serverAvailable=status.walking_protocol===1;serverGemini=!!status.providers?.gemini;serverEleven=!!status.elevenlabs_configured;
  model=status.models?.gemini||DEFAULT_GEMINI_MODEL;updateConnection();
}).catch(()=>{serverAvailable=false;updateConnection();});
localRuntime().then(async engine=>{
  const support=await engine.constructor.supported();const supported=support.supported;
  document.querySelector('[data-provider="local"]').disabled=!supported;$('prepare-local').disabled=!supported;
  text('local-state',supported?'지원 기기 · 처음 모델 준비 후 사용할 수 있어요.':support.reason||'이 브라우저에서는 기기 내 AI를 사용할 수 없어요.');
  if(!supported)text('local-progress',support.reason||'WebGPU를 지원하는 최신 Chrome·Edge가 필요해요.');
}).catch(()=>{text('local-state','로컬 AI 실행 파일을 불러오지 못했어요. 새로고침해주세요.');});
if('serviceWorker' in navigator)window.addEventListener('load',()=>navigator.serviceWorker.register(new URL('sw.js',document.baseURI)).catch(()=>{}));
