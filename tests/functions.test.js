import test from 'node:test';
import assert from 'node:assert/strict';
import { onRequest, parseWalkingReply, walkingPrompt, FIXED_VOICES } from '../functions/api/[[path]].js';

function request(path,body,env={},headers={}) {
  return onRequest({request:new Request(`https://demo.example${path}`,body===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(body)}),env});
}
const image='data:image/jpeg;base64,/9j/AA==';
const mp3=Uint8Array.from([255,251,144,100]);
const walking={mode:'walking',images:[image],apiKey:'personal-test-key',frameAgeMs:0};
const silence={speak:false,message:'',urgency:'none'};
const notice={speak:true,message:'전방 통로에 의자가 있는 것 같아요.',urgency:'notice'};
function geminiReply(value){return Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:typeof value==='string'?value:JSON.stringify(value)}]}}]});}

test('status never exposes secrets',async()=>{
  const response=await request('/api/status',undefined,{GEMINI_API_KEY:'secret-value'});
  const text=await response.text();assert.ok(!text.includes('secret-value'));assert.equal(JSON.parse(text).ai_configured,true);
});

test('selected provider overrides server default and uses its own key',async t=>{
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    assert.equal(url,'https://api.openai.com/v1/chat/completions');
    assert.equal(options.headers.Authorization,'Bearer openai-test');
    return Response.json({choices:[{message:{content:'{"level":"unknown","message":"판단 불가"}'}}]});
  });
  const response=await request('/api/assess',{image,provider:'openai'},{AI_PROVIDER:'gemini',GEMINI_API_KEY:'gemini-test',OPENAI_API_KEY:'openai-test'});
  assert.equal(response.status,200);
});
test('missing selected key never silently falls back to another provider',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('Must not call remote AI');});
  assert.equal((await request('/api/assess',{image,provider:'openai'},{GEMINI_API_KEY:'test'})).status,503);
  assert.equal((await request('/api/assess',{image,provider:'minicpm'},{GEMINI_API_KEY:'test'})).status,400);
});
test('no keys keeps local-only mode and speech fallback',async()=>{
  assert.equal((await request('/api/assess',{image})).status,503);
  assert.equal((await request('/api/speech',{text:'안내'})).status,503);
});
test('rejects foreign origins, oversized bodies and invalid detection boxes',async()=>{
  assert.equal((await request('/api/assess',{image},{},{Origin:'https://other.example'})).status,403);
  assert.equal((await request('/api/assess',{image:'x'.repeat(2_100_001)})).status,400);
  assert.equal((await request('/api/assess',{image,detections:[{label:'person',confidence:.9,box:[0,0,2,1]}]})).status,400);
});
for(const provider of ['gemini','openai'])test(`${provider} passes image + YOLO through the server and validates JSON`,async t=>{
  const verdict={level:'medium',message:'주변을 확인하세요.'};
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    const data=JSON.parse(options.body);assert.ok(options.body.includes('person'));
    if(provider==='gemini'){
      assert.ok(url.includes('gemini-3.1-flash-lite'));assert.equal(data.contents[0].parts[1].inline_data.data,'/9j/AA==');
      return Response.json({candidates:[{content:{parts:[{thought:true,text:'ignore'},{text:JSON.stringify(verdict)}]}}]});
    }
    assert.equal(data.messages[1].content[0].image_url.url,image);
    return Response.json({choices:[{message:{content:JSON.stringify(verdict)}}]});
  });
  const response=await request('/api/assess',{image,detections:[{label:'person',confidence:.9,box:[0,0,1,1]}]},{AI_PROVIDER:provider,GEMINI_API_KEY:'test',OPENAI_API_KEY:'test'});
  assert.equal(response.status,200);assert.deepEqual(await response.json(),verdict);
});
test('quota errors remain understandable without exposing provider text',async t=>{
  t.mock.method(globalThis,'fetch',async()=>Response.json({secret:'do-not-expose'},{status:429}));
  const response=await request('/api/assess',{image},{GEMINI_API_KEY:'test'});
  assert.equal(response.status,502);const text=await response.text();assert.match(text,/할당량/);assert.ok(!text.includes('do-not-expose'));
});
test('invalid AI levels cannot be displayed as safe',async t=>{
  t.mock.method(globalThis,'fetch',async()=>Response.json({candidates:[{content:{parts:[{text:'{"level":"safe","message":"안전"}'}]}}]}));
  assert.equal((await request('/api/assess',{image},{GEMINI_API_KEY:'test'})).status,502);
});
test('ElevenLabs failures trigger fallback and successes return audio',async t=>{
  const env={ELEVENLABS_API_KEY:'test',ELEVENLABS_VOICE_ID:'voice'};
  const mock=t.mock.method(globalThis,'fetch',async()=>new Response('',{status:401}));
  assert.equal((await request('/api/speech',{text:'안내'},env)).status,502);
  mock.mock.mockImplementation(async()=>new Response(mp3,{headers:{'Content-Type':'audio/mpeg'}}));
  const response=await request('/api/speech',{text:'안내'},env);assert.equal(response.headers.get('content-type'),'audio/mpeg');
});

test('speech and assessment timeouts are identified separately',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new DOMException('timeout','TimeoutError');});
  const env={GEMINI_API_KEY:'test',ELEVENLABS_API_KEY:'test',ELEVENLABS_VOICE_ID:'voice'};
  for(const [path,body,stage] of [['/api/assess',{image},'assessment'],['/api/speech',{text:'안내'},'speech']]){
    const response=await request(path,body,env);assert.equal(response.status,504);
    const result=await response.json();assert.equal(result.stage,stage);
    assert.match(result.detail,stage==='speech'?/ElevenLabs/:/장면 분석/);
  }
});

test('invalid ElevenLabs voice ID produces an actionable error without upstream secrets',async t=>{
  t.mock.method(globalThis,'fetch',async()=>Response.json({secret:'private'},{status:404}));
  const response=await request('/api/speech',{text:'안내'},{ELEVENLABS_API_KEY:'test',ELEVENLABS_VOICE_ID:'bad'});
  const result=await response.json();assert.equal(result.stage,'speech');assert.match(result.detail,/음성 ID/);assert.ok(!JSON.stringify(result).includes('private'));
});

test('walking accepts personal key without server credentials and sends chronological images with MINIMAL thinking',async t=>{
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    assert.match(url,/gemini-3\.1-flash-lite/);assert.equal(options.headers['x-goog-api-key'],'personal-test-key');assert.equal(options.redirect,'error');
    const payload=JSON.parse(options.body);assert.equal(payload.generationConfig.thinkingConfig.thinkingLevel,'MINIMAL');assert.deepEqual(payload.generationConfig.responseSchema.required,['speak','message','urgency']);
    const parts=payload.contents[0].parts;assert.equal(parts.filter(p=>p.inline_data).length,3);assert.match(parts[1].text,/frame 1 of 3/);assert.match(parts[5].text,/frame 3 of 3/);
    assert.match(parts[0].text,/직전|이전 안내/);assert.match(parts[0].text,/already spoken/);assert.match(parts[0].text,/same path/);assert.ok(!options.body.includes('personal-test-key'));
    return geminiReply(notice);
  });
  const response=await request('/api/assess',{...walking,images:[image,image,image],previousSpoken:'already spoken',context:'same path'});
  assert.equal(response.status,200);assert.deepEqual(await response.json(),notice);
});
test('walking strips pasted whitespace invisible characters and paired quotes from user key',async t=>{
  t.mock.method(globalThis,'fetch',async(url,options)=>{assert.equal(options.headers['x-goog-api-key'],'personal-test-key');return geminiReply(silence);});
  const response=await request('/api/assess',{...walking,apiKey:' \u200b"personal-\ntest-key" '});assert.equal(response.status,200);assert.deepEqual(await response.json(),silence);
});
test('walking silence is a valid result without an invented level or spoken text',async t=>{
  t.mock.method(globalThis,'fetch',async()=>geminiReply(silence));const response=await request('/api/assess',walking);assert.equal(response.status,200);assert.deepEqual(await response.json(),silence);
});
test('stale automatic frames return quiet before credentials or provider calls',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('must stay local');});const response=await request('/api/assess',{...walking,apiKey:'invalid-short',frameAgeMs:5001});assert.equal(response.status,200);assert.deepEqual(await response.json(),silence);
});
test('manual photo describes ordinary content regardless of previous speech or snapshot age',async t=>{
  const caption={speak:true,message:'책상 위에 컴퓨터 화면과 컵이 보여요.',urgency:'notice'};
  t.mock.method(globalThis,'fetch',async(url,options)=>{const prompt=JSON.parse(options.body).contents[0].parts[0].text;assert.match(prompt,/직접 선택한 사진/);assert.match(prompt,/평범한 사물/);assert.match(prompt,/사용자 질문/);return geminiReply(caption);});
  const response=await request('/api/assess',{...walking,manual:true,frameAgeMs:600000,question:'어떤 물건이 있어요?',previousSpoken:caption.message});assert.equal(response.status,200);assert.deepEqual(await response.json(),caption);
});
test('manual reply cannot silently discard a user-requested ordinary photo',async t=>{
  t.mock.method(globalThis,'fetch',async()=>geminiReply(silence));assert.equal((await request('/api/assess',{...walking,manual:true})).status,502);
});
test('walking input enforces image count decoded total and bounded context',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('invalid input must not call provider');});
  for(const fields of [{images:[]},{images:[image,image,image,image]},{images:['data:image/jpeg;base64,AAAAAA==']},{context:'x'.repeat(6001)},{question:'x'.repeat(12001)},{previousSpoken:'x'.repeat(301)},{manual:'true'},{frameAgeMs:1.5},{frameAgeMs:-1},{frameAgeMs:600001},{frameAgeMs:null}])assert.equal((await request('/api/assess',{...walking,...fields})).status,400);
  const data=Buffer.alloc(700000);data[0]=255;data[1]=216;const large='data:image/jpeg;base64,'+data.toString('base64');assert.equal((await request('/api/assess',{...walking,images:[large,large,large]})).status,400);
});
test('walking accepts the exact two MiB decoded boundary',async t=>{
  t.mock.method(globalThis,'fetch',async()=>geminiReply(silence));const data=Buffer.alloc(1024*1024);data[0]=255;data[1]=216;const large='data:image/jpeg;base64,'+data.toString('base64');assert.equal((await request('/api/assess',{...walking,images:[large,large]})).status,200);
});
test('structured walking JSON rejects duplicate and extra keys wrong types enums and wrappers',()=>{
  for(const text of [JSON.stringify({...notice,extra:1}),'{"speak":true,"speak":false,"message":"","urgency":"none"}',JSON.stringify({...notice,speak:'true'}),JSON.stringify({...notice,urgency:'critical'}),JSON.stringify({...notice,urgency:'none'}),'```json\n'+JSON.stringify(notice)+'\n```',JSON.stringify(notice)+' trailing','{speak:true,message:"안내",urgency:"notice"}',JSON.stringify({...silence,message:'변화가 없어요.'}),JSON.stringify({...silence,urgency:'urgent'})])assert.throws(()=>parseWalkingReply(text));
});
test('walking rejects fabricated distance steps detector chatter unsafe routes and aid claims',()=>{
  for(const message of ['전방 2m에 의자가 보여요.','두 걸음 앞에 의자가 있어요.','화면 중앙에 의자가 보여요.','의자 감지.','지팡이로 감지하지 못하므로 위험해요.','정면 통로가 비어 있어요.','오른쪽으로 가세요.','머리 높이에 간판이 있어요.'])assert.throws(()=>parseWalkingReply(JSON.stringify({...notice,message})));
  assert.deepEqual(parseWalkingReply(JSON.stringify({...notice,urgency:'urgent',message:'전방 통로가 의자로 더 가려진 것 같아요.'})).urgency,'urgent');
});
test('manual parsing allows ordinary screen content but retains unsupported-distance guards',()=>{
  assert.equal(parseWalkingReply(JSON.stringify({...notice,message:'컴퓨터 화면이 보여요.'}),{manual:true}).speak,true);assert.throws(()=>parseWalkingReply(JSON.stringify({...notice,message:'2m 앞에 컴퓨터가 있어요.'}),{manual:true}));
});
test('Gemini 2 models omit unsupported thinkingLevel while Gemini3 uses MINIMAL',async t=>{
  t.mock.method(globalThis,'fetch',async(url,options)=>{assert.match(url,/gemini-2\.5-flash/);assert.equal(JSON.parse(options.body).generationConfig.thinkingConfig,undefined);return geminiReply(notice);});assert.equal((await request('/api/assess',{...walking,model:'gemini-2.5-flash'})).status,200);
});
test('invalid personal keys and models never reach a provider or leak values',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('must not call provider');});for(const fields of [{apiKey:'숨겨진-키-personal'},{apiKey:'x'},{apiKey:'GEMINI_API_KEY=private-value'},{model:'../../other'},{model:false}]){const response=await request('/api/assess',{...walking,...fields});assert.equal(response.status,400);const text=await response.text();for(const value of Object.values(fields))if(typeof value==='string')assert.ok(!text.includes(value));}
});
test('personal Eleven key uses fixed female and male voices and original multilingual model',async t=>{
  let calls=0;t.mock.method(globalThis,'fetch',async(url,options)=>{const voice=calls++===0?'female':'male';assert.match(url,new RegExp(FIXED_VOICES[voice]));assert.equal(options.headers['xi-api-key'],'personal-eleven-key');assert.equal(options.redirect,'error');assert.deepEqual(JSON.parse(options.body),{text:'전방에 의자가 보여요.',model_id:'eleven_multilingual_v2'});return new Response(mp3,{headers:{'Content-Type':'audio/mpeg'}});});
  for(const voice of ['female','male']){const response=await request('/api/speech',{text:'전방에 의자가 보여요.',apiKey:'personal-eleven-key',voice});assert.equal(response.status,200);assert.equal(response.headers.get('content-type'),'audio/mpeg');}
});
test('speech failures invalid audio and missing credentials explicitly request default TTS fallback',async t=>{
  const mock=t.mock.method(globalThis,'fetch',async()=>new Response('not mp3',{headers:{'Content-Type':'audio/mpeg'}}));
  for(const kind of ['bad-header','bad-type','auth','quota','timeout']){
    mock.mock.mockImplementation(async()=>kind==='bad-header'?new Response('not mp3',{headers:{'Content-Type':'audio/mpeg'}}):kind==='bad-type'?new Response('json'):kind==='auth'?Response.json({secret:'never-echo'},{status:401}):kind==='quota'?Response.json({secret:'never-echo'},{status:429}):Promise.reject(new DOMException('private timeout data','TimeoutError')));
    const response=await request('/api/speech',{text:'안내',apiKey:'personal-eleven-key',voice:'female'});const result=await response.json();assert.equal(result.fallback,true);assert.equal(result.stage,'speech');assert.ok(!JSON.stringify(result).includes('never-echo'));assert.ok(!JSON.stringify(result).includes('private timeout data'));
  }
  const missing=await request('/api/speech',{text:'안내'});assert.equal(missing.status,503);assert.equal((await missing.json()).fallback,true);
});
test('arbitrary Eleven voices are rejected before private credentials are sent',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('must not call provider');});const response=await request('/api/speech',{text:'안내',apiKey:'personal-eleven-key',voice:'unapproved-voice'});assert.equal(response.status,400);assert.equal((await response.json()).fallback,true);
});
test('fixed voice names do not accept coerced array values',async()=>{const response=await request('/api/speech',{text:'안내',apiKey:'personal-eleven-key',voice:['female']});assert.equal(response.status,400);assert.equal((await response.json()).fallback,true);});
test('walking prompt carries context but never declares metric depth or ignores cane sweep',()=>{
  const prompt=walkingPrompt({question:'주변을 알려줘',context:'same chair',previousSpoken:'전방에 의자가 있어요.',frameAgeMs:50});assert.match(prompt,/좌우 스윙/);assert.match(prompt,/위쪽/);assert.match(prompt,/distance_measurement_available":false/);assert.match(prompt,/previous_spoken_guidance/);assert.match(prompt,/urgency=urgent/);
});

test('personal speech key defaults to fixedfemale even when serverlegacyvoice differs',async t=>{
  t.mock.method(globalThis,'fetch',async(url)=>{assert.match(url,new RegExp(FIXED_VOICES.female));assert.ok(!url.includes('legacy-server-voice'));return new Response(mp3,{headers:{'Content-Type':'audio/mpeg'}});});
  assert.equal((await request('/api/speech',{text:'안내',apiKey:'personal-eleven-key'},{ELEVENLABS_VOICE_ID:'legacy-server-voice'})).status,200);
});
