import test from 'node:test';
import assert from 'node:assert/strict';
import {LOCAL_MODELS,LOCAL_DOWNLOAD_BYTES,LOCAL_MIN_GPU_BUFFER_BYTES,localCaptionPrompt,englishCaption,koreanTranslation,silentCaption,questionNeedsTranslation,translatedQuestion} from '../static/local-ai-core.js';
import {BrowserLocalAI} from '../static/local-ai.js';

test('local VLM and real Korean translator have fixed immutable revisions and honest model size',()=>{
  for(const model of Object.values(LOCAL_MODELS))assert.match(model.revision,/^[a-f0-9]{40}$/);
  assert.equal(LOCAL_DOWNLOAD_BYTES,1442797846);assert.equal(LOCAL_MODELS.translation.license,'CC-BY-NC-4.0');assert.equal(LOCAL_MIN_GPU_BUFFER_BYTES,536870912);
});
test('Korean photo questions are translated before the small English image model',()=>{
  assert.equal(questionNeedsTranslation('사진에 무엇이 있나요?'),true);assert.equal(questionNeedsTranslation('What is in the photo?'),false);
  assert.equal(translatedQuestion('What’s in this photo?'),"What's in this photo?");assert.throws(()=>translatedQuestion('사진에 무엇이 있나요?'));
});
test('local caption prompt avoids cane noun priming and accepts only bounded English custom questions',()=>{
  assert.doesNotMatch(localCaptionPrompt({walking:true}),/cane/i);assert.match(localCaptionPrompt({walking:true}),/none/);
  assert.doesNotMatch(localCaptionPrompt({question:'한국어로 설명하세요'}),/한국어/);
  assert.match(localCaptionPrompt({question:'What is visible?'}),/What is visible/);
});
test('English-only and severely mixed translations cannot become Korean automatic speech',()=>{
  assert.throws(()=>koreanTranslation('A chair is visible ahead.'));
  assert.throws(()=>koreanTranslation('한국어 A chair is visible ahead and a person stands next to it.'));
  assert.throws(()=>koreanTranslation('강 강 강 강 강 강 강 강 강 강'));
  assert.equal(koreanTranslation('앞쪽에 의자가 보입니다.'),'앞쪽에 의자가 보입니다.');
});
test('caption silence is not translated into a fabricated all-clear message',()=>{
  assert.equal(silentCaption('none.'),true);assert.equal(silentCaption('A chair is visible.'),false);
  assert.equal(englishCaption('Assistant: A chair is visible.'),'A chair is visible.');assert.throws(()=>englishCaption(''));
});
test('unsupported environment is reported rather than falling back to slow CPU pretending to be GPU',async()=>{
  const supported=await BrowserLocalAI.supported();assert.equal(supported.supported,false);assert.equal(supported.downloadBytes,LOCAL_DOWNLOAD_BYTES);
});
test('GPU memory binding limits are checked before downloading the large Korean model',async()=>{
  const savedNavigator=Object.getOwnPropertyDescriptor(globalThis,'navigator'),savedSecure=Object.getOwnPropertyDescriptor(globalThis,'isSecureContext');
  let limit=128*1024*1024;
  Object.defineProperty(globalThis,'isSecureContext',{value:true,configurable:true});
  Object.defineProperty(globalThis,'navigator',{value:{gpu:{requestAdapter:async()=>({features:new Set(['shader-f16']),limits:{maxStorageBufferBindingSize:limit,maxBufferSize:limit}})}},configurable:true});
  try{
    assert.equal((await BrowserLocalAI.supported()).supported,false);
    limit=LOCAL_MIN_GPU_BUFFER_BYTES;assert.equal((await BrowserLocalAI.supported()).supported,true);
  }finally{
    if(savedNavigator)Object.defineProperty(globalThis,'navigator',savedNavigator);else delete globalThis.navigator;
    if(savedSecure)Object.defineProperty(globalThis,'isSecureContext',savedSecure);else delete globalThis.isSecureContext;
  }
});

test('local worker cancellation stops inference, retains idle models, and fences pending image conversion',async()=>{
  const savedWorker=globalThis.Worker,savedBitmap=globalThis.createImageBitmap,savedSupported=BrowserLocalAI.supported;
  const workers=[];
  class MockWorker {
    constructor(){this.messages=[];this.terminated=false;workers.push(this);}
    postMessage(data){this.messages.push(data);if(data.type==='prepare')queueMicrotask(()=>this.onmessage({data:{id:data.id,result:{backend:'WebGPU'}}}));}
    terminate(){this.terminated=true;}
  }
  const bitmap=()=>({width:320,height:240,closed:false,close(){this.closed=true;}});
  const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
  globalThis.Worker=MockWorker;BrowserLocalAI.supported=async()=>({supported:true});globalThis.createImageBitmap=async()=>bitmap();
  const engine=new BrowserLocalAI();
  try{
    await Promise.all([engine.prepare(),engine.prepare()]);assert.equal(workers.length,1);assert.equal(engine.ready,true);
    engine.stop();assert.equal(engine.ready,true);assert.equal(workers[0].terminated,false);
    const inference=engine.describe({});await tick();
    await assert.rejects(engine.describe({}),/쌓지 않습니다/);
    const rejected=assert.rejects(inference,{name:'AbortError'});engine.stop();await rejected;
    assert.equal(workers[0].terminated,true);assert.equal(engine.ready,false);
    await engine.prepare();
    let finishCapture;globalThis.createImageBitmap=()=>new Promise(resolve=>{finishCapture=resolve;});
    const capture=engine.describe({}),capturedBitmap=bitmap();const canceled=assert.rejects(capture,{name:'AbortError'});
    engine.stop();finishCapture(capturedBitmap);await canceled;
    assert.equal(workers[1].messages.filter(message=>message.type==='describe').length,0);assert.equal(capturedBitmap.closed,true);
    // A delayed error from a terminated worker must not kill its replacement.
    await engine.prepare();workers[0].onerror();assert.equal(engine.ready,true);assert.equal(workers[2].terminated,false);
  }finally{engine.close();globalThis.Worker=savedWorker;globalThis.createImageBitmap=savedBitmap;BrowserLocalAI.supported=savedSupported;}
});

test('stop during GPU capability lookup cannot restart preparation after cancellation',async()=>{
  const savedSupported=BrowserLocalAI.supported;let finish;
  BrowserLocalAI.supported=()=>new Promise(resolve=>{finish=resolve;});
  const engine=new BrowserLocalAI();
  try{
    const preparing=engine.prepare(),canceled=assert.rejects(preparing,{name:'AbortError'});
    assert.equal(engine.busy,true);engine.stop();finish({supported:true});await canceled;
    assert.equal(engine.worker,null);assert.equal(engine.ready,false);
  }finally{engine.close();BrowserLocalAI.supported=savedSupported;}
});
