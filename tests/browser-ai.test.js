import test from 'node:test';
import assert from 'node:assert/strict';
import {assessImages,normalizeKey,validateImages} from '../static/browser-ai.js';
const jpeg='data:image/jpeg;base64,/9j/AA==';
test('key paste normalizes quotes/formatting without accepting non-header characters',()=>{
  assert.equal(normalizeKey(' "abc\u200b def" '),'abcdef');
  assert.throws(()=>normalizeKey('한글-key'));
});
test('image transport bounds chronological frame count and total encoded bytes',()=>{
  assert.deepEqual(validateImages([jpeg,jpeg]),[jpeg,jpeg]);
  assert.throws(()=>validateImages([jpeg,jpeg,jpeg,jpeg]));
  assert.throws(()=>validateImages(['data:image/png;base64,abc']));
  assert.throws(()=>validateImages([jpeg+'A'.repeat(2_000_000)]));
});
test('static-site direct Gemini request keeps personal key out of URL and uses minimal thinking',async t=>{
  const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);
  let call;
  globalThis.fetch=async(url,options)=>{call={url,options,body:JSON.parse(options.body)};return Response.json({candidates:[{content:{parts:[{text:'{"speak":false,"message":"","urgency":"none"}'}]}}]});};
  const reply=await assessImages({images:[jpeg,jpeg],apiKey:'personal-test-key',capturedAt:Date.now()});
  assert.equal(reply.speak,false);assert.equal(call.options.headers['x-goog-api-key'],'personal-test-key');
  assert.ok(!String(call.url).includes('personal-test-key'));
  assert.equal(call.body.contents[0].parts.filter(p=>p.inline_data).length,2);
  assert.equal(call.body.generationConfig.thinkingConfig.thinkingLevel,'MINIMAL');
});
test('server transport supports a site subpath and a manually requested screen description',async t=>{
  const original=globalThis.fetch,doc=globalThis.document;t.after(()=>{globalThis.fetch=original;globalThis.document=doc;});
  globalThis.document={baseURI:'https://example.test/giljabi/'};let url,body;
  globalThis.fetch=async(u,options)=>{url=String(u);body=JSON.parse(options.body);return Response.json({speak:true,message:'컴퓨터 화면에 문서가 열려 있어요.',urgency:'notice'});};
  const reply=await assessImages({images:[jpeg],server:true,manual:true,context:{tracks:[]}});
  assert.equal(url,'https://example.test/giljabi/api/assess');assert.equal(body.manual,true);assert.equal(typeof body.context,'string');assert.equal(reply.valid,true);
});
test('invalid model output and missing personal key never become narration',async t=>{
  const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);let calls=0;
  globalThis.fetch=async()=>{calls++;return Response.json({candidates:[{content:{parts:[{text:'{"speak":true,"message":"앞으로 2미터 이동하세요.","urgency":"notice"}'}]}}]});};
  await assert.rejects(assessImages({images:[jpeg]}),/Gemini API 키/);assert.equal(calls,0);
  await assert.rejects(assessImages({images:[jpeg],apiKey:'test'}),/답변/);
});
