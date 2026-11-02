import test from 'node:test';
import assert from 'node:assert/strict';
import { WalkingSceneGate, NarrationMemory, PhotoHistory, MAX_SPEECH_AGE_MS, isFresh, buildWalkingPrompt, parseWalkingReply } from '../static/walking-guidance.js';

const box=(label,x1,y1,x2,y2,confidence=.91)=>({label,confidence,box:[x1,y1,x2,y2]});
const chair=(center=.5)=>box('chair',center-.1,.58,center+.1,.96);
const observe=(gate,sequence,time,items=[])=>gate.observe(sequence,items,time);
function chairScene(){const gate=new WalkingSceneGate();for(let i=1;i<=3;i++)observe(gate,i,(i-1)*200,[chair()]);const first=gate.decision(3);assert.equal(first.stateKey,'seat:lower:path');gate.accepted(first);return gate;}
function emptyScene(){const gate=new WalkingSceneGate();observe(gate,1,0);gate.accepted(gate.decision(1));return gate;}
const reply=(message,urgency='notice')=>parseWalkingReply(JSON.stringify({speak:true,message,urgency}));
const sentence='전방 통로에 의자가 있는 것 같아요.';
const jpeg='data:image/jpeg;base64,/9j/2Q==';

test('first image requests AI inspection without directly authorising speech',()=>{
  const gate=new WalkingSceneGate();observe(gate,1,0);
  const first=gate.decision(1);assert.equal(first.reason,'INITIAL');assert.equal(first.episode,1);assert.equal(first.capturedAt,0);assert.equal(first.speak,undefined);
  gate.accepted(first);assert.equal(gate.decision(1),null);
});
test('three-of-five confirmation tolerates intermittent misses',()=>{
  const gate=emptyScene();observe(gate,2,200,[chair()]);observe(gate,3,400);observe(gate,4,600,[chair()]);assert.equal(gate.decision(4),null);observe(gate,5,800);observe(gate,6,1000,[chair()]);assert.equal(gate.decision(6).reason,'PATH_CHANGE');
});
test('one detector false positive cannot become a corridor event',()=>{
  const gate=emptyScene();observe(gate,2,200,[chair()]);for(let i=3;i<15;i++){observe(gate,i,i*200);assert.equal(gate.decision(i),null);}
});
test('stationary objects and chair count changes remain one semantic state',()=>{
  const gate=new WalkingSceneGate();for(let i=1;i<=3;i++)observe(gate,i,i*200,[chair(.4),chair(.6)]);const first=gate.decision(3);gate.accepted(first);
  for(let i=4;i<=7;i++){observe(gate,i,i*200,[chair()]);assert.equal(gate.decision(i),null);}
  for(let i=8;i<=12;i++){observe(gate,i,i*200,[chair(.35),chair(.5),chair(.65)]);assert.equal(gate.decision(i),null);}
  assert.equal(gate.review(12).stateKey,first.stateKey);
});
test('a side chair is evidence for AI, not a path-change event',()=>{
  const gate=emptyScene();for(let i=2;i<=8;i++){observe(gate,i,i*200,[box('chair',.01,.5,.20,.95)]);assert.equal(gate.decision(i),null);}const review=gate.review(8);assert.equal(review.context.tracks[0].corridor,'side_or_background');
});
test('an upper central object is a potential cue, never a measured head-height hazard',()=>{
  const gate=emptyScene();for(let i=2;i<=4;i++)observe(gate,i,i*200,[box('stop sign',.4,.02,.6,.25)]);const snapshot=gate.decision(4);assert.equal(snapshot.reason,'PATH_CHANGE');assert.match(snapshot.stateKey,/upper:path/);assert.equal(snapshot.context.distance_measurement_available,false);assert.equal(snapshot.context.travel_heading_verified,false);assert.equal(snapshot.context.image_upper_band_is_not_head_height,true);
});
test('cane sweep center shifts and seat class flicker keep the same scene and ID',()=>{
  const gate=chairScene(),id=gate.review(3).context.tracks[0].id;const centers=[.35,.6,.38,.65,.4,.55];for(let i=0;i<centers.length;i++){const item=chair(centers[i]);if(i%2)item.label='bench';observe(gate,i+4,800+i*200,[item]);assert.equal(gate.decision(i+4),null);}assert.equal(gate.review(9).context.tracks[0].id,id);
});
test('twelve-second retained tracks are explicitly old and not currently visible',()=>{
  const gate=chairScene();observe(gate,4,12400);const review=gate.review(4);assert.equal(review.context.tracks[0].currently_visible,false);assert.equal(review.context.tracks[0].retained_only,true);assert.equal(review.context.tracks[0].age_ms,12000);assert.equal(review.stateKey,'seat:lower:path');
});
test('genuine new corridor episode needs retained-track expiry and confirmed absence',()=>{
  const gate=chairScene();observe(gate,4,10000);assert.equal(gate.review(4).episode,1);observe(gate,5,12401);observe(gate,6,13500);observe(gate,7,15500);assert.equal(gate.review(7).episode,2);gate.accepted(gate.review(7));for(let i=8;i<=10;i++)observe(gate,i,16000+(i-8)*200,[chair()]);const later=gate.decision(10);assert.equal(later.episode,2);assert.equal(later.reason,'PATH_CHANGE');
});
test('continuous explicit reviews do not consume initial or pending scene changes',()=>{
  const gate=new WalkingSceneGate();observe(gate,1,0);assert.equal(gate.review(1).reason,'VISUAL_REVIEW');assert.equal(gate.decision(1).reason,'INITIAL');gate.accepted(gate.decision(1));for(let i=2;i<=4;i++)observe(gate,i,i*200,[chair()]);gate.review(4);assert.equal(gate.decision(4).reason,'PATH_CHANGE');gate.accepted(gate.decision(4));observe(gate,5,1000,[chair()]);assert.equal(gate.review(5).reason,'VISUAL_REVIEW');assert.equal(gate.decision(5),null);
});
test('silent review timer does not create repeated path-change events',()=>{
  const gate=emptyScene();observe(gate,2,11999);assert.equal(gate.decision(2),null);observe(gate,3,12000);assert.equal(gate.decision(3).reason,'VISUAL_REVIEW');gate.accepted(gate.decision(3));observe(gate,4,12100);assert.equal(gate.decision(4),null);
});
test('sameContext excludes age while relevant requires a fresh frame',()=>{
  const gate=chairScene(),snapshot=gate.review(3);observe(gate,4,10000,[chair()]);assert.equal(gate.sameContext(snapshot),true);assert.equal(gate.relevant(snapshot,10000),false);assert.equal(gate.relevant(gate.review(4),10000),true);
});
test('new corridor semantics and reset invalidate old response context',()=>{
  const gate=chairScene(),before=gate.review(3);for(let i=4;i<=6;i++)observe(gate,i,i*200,[chair(),box('person',.35,.1,.65,.9)]);assert.equal(gate.sameContext(before),false);const latest=gate.review(6);gate.reset();observe(gate,7,2000,[chair()]);assert.equal(gate.sameContext(latest),false);assert.equal(gate.accepted(latest),false);
});
test('reconnect preserves quiet identity but invalidates missing camera context',()=>{
  const gate=chairScene(),before=gate.review(3);gate.reconnect();assert.equal(gate.review(),null);assert.equal(gate.sameContext(before),false);for(let i=10;i<=12;i++){observe(gate,i,1000+(i-10)*200,[chair()]);assert.equal(gate.decision(i),null);}assert.equal(gate.sameContext(before),true);
});
test('invalid detector boxes and out-of-order frames cannot manufacture evidence',()=>{
  const gate=emptyScene();const invalid=[box('chair',0,0,1,1,NaN),box('chair',NaN,0,.5,.9),box('chair',-.1,0,.5,.9),box('not a yolo class',.2,.4,.8,.9),box('chair',.1,.1,.105,.105)];for(let i=2;i<=5;i++)observe(gate,i,i*200,invalid);assert.equal(gate.decision(5),null);assert.equal(observe(gate,4,1200,[chair()]),false);assert.equal(observe(gate,6,900,[chair()]),false);
});
test('snapshots own detector and track boxes rather than retaining mutable inputs',()=>{
  const gate=chairScene(),source=chair();observe(gate,4,800,[source]);const snapshot=gate.review(4);source.box[0]=0;snapshot.detections[0].box[0]=0;snapshot.context.tracks[0].box[0]=0;assert.notEqual(gate.review(4).detections[0].box[0],0);assert.notEqual(gate.review(4).context.tracks[0].box[0],0);
});
test('freshness rejects stale, future, malformed and over-permissive ages',()=>{
  assert.equal(MAX_SPEECH_AGE_MS,5000);assert.equal(isFresh(1000,6000),true);assert.equal(isFresh(1000,6001),false);assert.equal(isFresh(6001,6000),false);assert.equal(isFresh(NaN,6000),false);assert.equal(isFresh(1000,7000,12000),false);
});

test('photo history selects one to three chronological photos including the current image',()=>{
  const photos=new PhotoHistory();for(let t=0;t<=2000;t+=400)assert.equal(photos.add(jpeg,t),true);const chosen=photos.frames(2000);assert.ok(chosen.length<=3);assert.equal(chosen.at(-1).capturedAt,2000);assert.ok(chosen[0].capturedAt<2000);assert.deepEqual(chosen.map(p=>p.capturedAt),chosen.map(p=>p.capturedAt).toSorted((a,b)=>a-b));
});
test('previous reviewed frame is retained alongside newer cane-sweep pictures',()=>{
  const photos=new PhotoHistory();for(let t=0;t<=2000;t+=400)photos.add(jpeg,t);photos.markReviewed(1200);for(let t=2400;t<=4000;t+=400)photos.add(jpeg,t);const chosen=photos.frames(4000);assert.equal(chosen[0].capturedAt,1200);assert.equal(chosen.at(-1).capturedAt,4000);
});
test('photo payload caps prefer current image and do not silently reuse another timestamp',()=>{
  const photos=new PhotoHistory({maxBytes:8});for(let i=0;i<4;i++)photos.add(jpeg,i*1000);assert.ok(photos.frames(3000).length<=2);assert.equal(photos.frames(3000).at(-1).capturedAt,3000);assert.deepEqual(photos.frames(3500),[]);assert.equal(photos.add(jpeg,3000),false);assert.equal(new PhotoHistory({maxBytes:3}).add(jpeg,0),false);
});
test('photo limits, age pruning and reset bound retained camera content',()=>{
  const photos=new PhotoHistory({maxPhotos:1,maxStoredFrames:2});photos.add(jpeg,0);photos.markReviewed(0);photos.add(jpeg,1000);photos.add(jpeg,2000);assert.deepEqual(photos.frames(2000).map(p=>p.capturedAt),[2000]);photos.add(jpeg,20000);assert.deepEqual(photos.frames(20000).map(p=>p.capturedAt),[20000]);photos.reset();assert.deepEqual(photos.frames(20000),[]);assert.throws(()=>new PhotoHistory({maxPhotos:4}));assert.throws(()=>new PhotoHistory({maxStoredFrames:0}));
});

test('strict protocol accepts silence, a notice and one urgency escalation',()=>{
  assert.deepEqual(parseWalkingReply('{"speak":false,"message":"","urgency":"none"}'),{valid:true,speak:false,message:'',urgency:'none'});assert.equal(reply(sentence).valid,true);assert.equal(reply(sentence,'urgent').urgency,'urgent');assert.equal(parseWalkingReply('{"message":"전방에 의자가 보여요.","speak":true}').urgency,'notice');
});
test('every allowed field order is supported without accepting duplicates',()=>{
  for(const keys of [['speak','message','urgency'],['speak','urgency','message'],['message','speak','urgency'],['message','urgency','speak'],['urgency','speak','message'],['urgency','message','speak']]){const values={speak:true,message:sentence,urgency:'notice'};assert.equal(parseWalkingReply(JSON.stringify(Object.fromEntries(keys.map(k=>[k,values[k]])))).valid,true);}
  assert.equal(parseWalkingReply('{"speak":true,"speak":false,"message":""}').valid,false);
});
test('invalid JSON shapes, lenient syntax, extra fields and contradictory urgency stay silent',()=>{
  const invalid=['[]','```json\n{"speak":false,"message":""}\n```','{speak:false,message:""}','{"speak":"true","message":"의자"}','{"speak":true,"message":null}','{"speak":true,"message":"의자","extra":1}','{"speak":true,"message":"의자","urgency":"high"}','{"speak":true,"message":"의자","urgency":"none"}','{"speak":false,"message":"","urgency":"urgent"}'];for(const raw of invalid){const parsed=parseWalkingReply(raw);assert.equal(parsed.valid,false,raw);assert.equal(parsed.speak,false,raw);}
});
test('numeric and written meter/step claims are rejected rather than fabricated',()=>{
  for(const message of ['전방 2m에 의자가 있어요.','전방 약 두 미터에 의자가 있어요.','두 걸음 앞에 의자가 있어요.','Three paces ahead is a chair.','２ｍ 앞에 의자가 있어요.','2\u200bm 앞에 의자가 있어요.'])assert.equal(reply(message).valid,false,message);
});
test('raw YOLO narration, guessed body height, aid capabilities and route commands are rejected',()=>{
  for(const message of ['화면 중앙에 의자 감지.','Chair detected.','머리 높이에 물체가 있어요.','흰지팡이로 감지할 수 없는 단차입니다.','왼쪽으로 가세요.','Proceed.','Turn right.','안전한 통로입니다.','There are no obstacles.'])assert.equal(reply(message).valid,false,message);
  assert.equal(reply('전방 통로의 위쪽에 매달린 물체가 보이는 것 같아요.').valid,true);
});
test('protocol bounds message size, controls and malformed unicode',()=>{
  assert.equal(reply('가'.repeat(300)).valid,true);assert.equal(reply('가'.repeat(301)).valid,false);assert.equal(reply('문자\u0000오류').valid,false);assert.equal(reply('\ud800').valid,false);assert.equal(reply('의자가 보여요. 🚶').valid,true);assert.equal(parseWalkingReply(null).valid,false);
});
test('prompt requests semantic silence, cane context and chronological frame evidence without depth claims',()=>{
  const text=buildWalkingPrompt({question:'주변을 설명해줘',context:{camera_mount:'white_cane_sweep'},previousGuidance:sentence,capturedAt:1000,now:1200,frames:[{capturedAt:800},{capturedAt:1000}]});assert.match(text,/스윙/);assert.match(text,/speak=false/);assert.match(text,/urgency/);const data=JSON.parse(text.slice(text.lastIndexOf('\n')+1));assert.equal(data.previous_spoken_guidance,sentence);assert.equal(data.distance_measurement_available,false);assert.equal(data.travel_heading_verified,false);assert.equal(data.latest_frame_is_fresh,true);assert.deepEqual(data.frames.map(f=>f.age_ms),[400,200]);
});

test('speech eligibility does not mark text as spoken before actual playback',()=>{
  const memory=new NarrationMemory(),candidate=reply(sentence);assert.equal(memory.eligible(1,'seat:lower:path',candidate),true);assert.equal(memory.previous(1),'');assert.equal(memory.eligible(1,'seat:lower:path',candidate),true);assert.equal(memory.commit(1,'seat:lower:path',candidate),true);assert.equal(memory.previous(1),sentence);assert.equal(memory.eligible(1,'seat:lower:path',candidate),false);
});
test('speech reservations suppress duplicate pending synthesis but keep previous speech empty',()=>{
  const memory=new NarrationMemory(),candidate=reply(sentence),pending=memory.reserve(1,'seat:lower:path',candidate);assert.ok(pending);assert.equal(memory.previous(1),'');assert.equal(memory.reserve(1,'seat:lower:path',candidate),null);assert.equal(memory.eligible(1,'seat:lower:path',candidate),false);assert.equal(memory.release(pending),true);assert.equal(memory.eligible(1,'seat:lower:path',candidate),true);assert.equal(memory.previous(1),'');
});
test('playback-start commits exactly once and stale reservation callbacks cannot consume newer speech',()=>{
  const memory=new NarrationMemory(),notice=memory.reserve(1,'seat:lower:path',reply(sentence)),urgent=memory.reserve(1,'seat:lower:path',reply(sentence,'urgent'));assert.ok(urgent);assert.equal(memory.isCurrentReservation(notice),false);assert.equal(memory.release(notice),false);assert.equal(memory.commitReservation(notice),false);assert.equal(memory.commitReservation(urgent),true);assert.equal(memory.previous(1),sentence);assert.equal(memory.commitReservation(urgent),false);
});
test('same semantic chair survives paraphrases, detector counts and material adjectives',()=>{
  const memory=new NarrationMemory();memory.commit(1,'none',reply(sentence));for(const message of ['앞쪽 통로에 의자가 보여요.','전방 통로에 의자 두 개가 있어요.','통로에 나무 의자가 있는 것 같아요.','여러 가지 의자가 통로에 보이는 것 같아요.'])assert.equal(memory.eligible(1,'seat:lower:path',reply(message)),false,message);
});
test('urgency escalates once and never periodically downgrades or repeats',()=>{
  const memory=new NarrationMemory();assert.equal(memory.commit(1,'seat',reply(sentence)),true);assert.equal(memory.eligible(1,'seat',reply(sentence,'urgent')),true);assert.equal(memory.commit(1,'seat',reply(sentence,'urgent')),true);assert.equal(memory.eligible(1,'seat',reply(sentence,'urgent')),false);assert.equal(memory.eligible(1,'seat',reply(sentence)),false);
});
test('genuinely new hazard topics and worsening obstruction remain eligible',()=>{
  const memory=new NarrationMemory();memory.commit(1,'seat',reply(sentence));assert.equal(memory.eligible(1,'seat',reply('위쪽에 나뭇가지가 통로로 돌출되어 있는 것 같아요.')),true);assert.equal(memory.eligible(1,'seat',reply('의자가 전방 통로를 막고 있는 것 같아요.')),true);
});
test('new episode clears prior spoken context before asking AI, not only after it speaks',()=>{
  const memory=new NarrationMemory();memory.commit(1,'seat',reply(sentence));assert.equal(memory.previous(2),'');assert.equal(memory.eligible(2,'seat',reply(sentence)),true);assert.equal(memory.previous(1),sentence);memory.commit(2,'seat',reply(sentence));assert.equal(memory.previous(2),sentence);
});
test('stairs direction changes are meaningful while side-to-side image motion is not',()=>{
  const memory=new NarrationMemory();memory.commit(1,'stairs',reply('전방 통로에 올라가는 계단이 보여요.'));assert.equal(memory.eligible(1,'stairs',reply('전방 통로에 내려가는 계단이 보여요.')),true);const seat=new NarrationMemory();seat.commit(1,'seat',reply('왼쪽 통로에 의자가 보여요.'));assert.equal(seat.eligible(1,'seat',reply('오른쪽 통로에 의자가 보여요.')),false);
});
test('removing one already-announced object does not create a fresh warning',()=>{
  const memory=new NarrationMemory();memory.commit(1,'seat|person',reply('전방 통로에 사람과 의자가 있는 것 같아요.'));assert.equal(memory.eligible(1,'seat',reply(sentence)),false);
});
test('reset cancels pending reservations and invalid or silent replies cannot consume memory',()=>{
  const memory=new NarrationMemory(),pending=memory.reserve(1,'seat',reply(sentence));memory.reset();assert.equal(memory.commitReservation(pending),false);assert.equal(memory.eligible(1,'seat',{valid:true,speak:false,message:'',urgency:'none'}),false);assert.equal(memory.commit(1,'seat',{valid:false,speak:true,message:sentence,urgency:'notice'}),false);assert.equal(memory.previous(1),'');
});
test('English word boundaries do not infer cars from careful or signs from designs',()=>{
  const memory=new NarrationMemory();memory.commit(1,'seat',reply('There is a chair beside the path.'));assert.equal(memory.eligible(1,'seat',reply('Be careful about the chair.')),false);
});


test('manual photo descriptions may name a computer screen while walking narration cannot',()=>{
  const raw=JSON.stringify({speak:true,message:'컴퓨터 화면에 문서와 사진이 보입니다.',urgency:'notice'});
  assert.equal(parseWalkingReply(raw).valid,false);
  assert.equal(parseWalkingReply(raw,{manual:true}).valid,true);
  assert.equal(parseWalkingReply(JSON.stringify({speak:true,message:'사진 속 모니터 화면에 글자가 보입니다.'}),{manual:true}).valid,true);
});
test('manual flag preserves strict protocol and distance, route, assurance and cane guards',()=>{
  for(const message of ['두 미터 앞에 의자가 있어요.','왼쪽으로 가세요.','안전한 통로입니다.','흰지팡이로 감지할 수 없는 단차입니다.']){
    assert.equal(parseWalkingReply(JSON.stringify({speak:true,message,urgency:'notice'}),{manual:true}).valid,false,message);
  }
  assert.equal(parseWalkingReply('컴퓨터 화면입니다.',{manual:true}).valid,false);
  assert.equal(parseWalkingReply('{"speak":true,"message":"컴퓨터 화면입니다.","extra":true}',{manual:true}).valid,false);
});
