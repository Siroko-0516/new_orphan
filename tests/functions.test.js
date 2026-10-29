import test from 'node:test';
import assert from 'node:assert/strict';
import { onRequest } from '../functions/api/[[path]].js';

function request(path,body,env={},headers={}) {
  return onRequest({request:new Request(`https://demo.example${path}`,body===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(body)}),env});
}
const image='data:image/jpeg;base64,/9j/AA==';

test('status never exposes secrets',async()=>{
  const response=await request('/api/status',undefined,{GEMINI_API_KEY:'secret-value'});
  const text=await response.text();assert.ok(!text.includes('secret-value'));assert.equal(JSON.parse(text).ai_configured,true);
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
  mock.mock.mockImplementation(async()=>new Response('audio'));
  const response=await request('/api/speech',{text:'안내'},env);assert.equal(response.headers.get('content-type'),'audio/mpeg');
});
