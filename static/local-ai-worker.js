import {AutoProcessor,AutoModelForVision2Seq,RawImage,pipeline,env} from '@huggingface/transformers';
import {LOCAL_MODELS,LOCAL_DOWNLOAD_BYTES,LOCAL_MIN_GPU_BUFFER_BYTES,LOCAL_IMAGE_MODE,localCaptionPrompt,englishCaption,silentCaption,koreanTranslation,questionNeedsTranslation,translatedQuestion} from './local-ai-core.js';

// This ORT build belongs to Transformers.js; it must not use the site's separate YOLO ORT files.
env.backends.onnx.wasm.wasmPaths=new URL(/* @vite-ignore */ '../local-ort/',import.meta.url).href;
env.backends.onnx.wasm.numThreads=1;
env.allowLocalModels=false;
env.useBrowserCache=true;
let model,processor,translator,preparing,busy=false;
const downloads=new Map();
function progress(id,stage,event={}){
  const key=stage+':'+(event.file||event.name||'model');
  if(event.status==='progress')downloads.set(key,{loaded:event.loaded||0,total:event.total||0});
  let loaded=0,total=0;for(const value of downloads.values()){loaded+=value.loaded;total+=value.total;}
  self.postMessage({id,type:'progress',progress:{stage,status:event.status||'preparing',file:event.file||'',loaded:event.loaded||0,total:event.total||0,progress:event.progress??null,overallLoaded:loaded,overallTotal:Math.max(total,LOCAL_DOWNLOAD_BYTES)}});
}
async function prepare(id){
  if(model&&processor&&translator)return;
  if(preparing)return preparing;
  preparing=(async()=>{
    if(!self.navigator?.gpu)throw new Error('이 브라우저는 WebGPU를 지원하지 않습니다.');
    const adapter=await navigator.gpu.requestAdapter({powerPreference:'high-performance'});
    if(!adapter?.features.has('shader-f16'))throw new Error('WebGPU FP16을 지원하는 GPU가 필요합니다.');
    if(!(adapter.limits?.maxStorageBufferBindingSize>=LOCAL_MIN_GPU_BUFFER_BYTES&&adapter.limits?.maxBufferSize>=LOCAL_MIN_GPU_BUFFER_BYTES))throw new Error('GPU 버퍼 용량이 부족해 로컬 한국어 설명을 사용할 수 없습니다.');
    env.backends.onnx.webgpu.adapter=adapter;
    env.backends.onnx.webgpu.powerPreference='high-performance';
    progress(id,'사진 설명 모델');
    // Assign each resource as it settles so a later failure can dispose it.
    processor=await AutoProcessor.from_pretrained(LOCAL_MODELS.caption.id,{revision:LOCAL_MODELS.caption.revision,progress_callback:p=>progress(id,'사진 설명 모델',p)});
    model=await AutoModelForVision2Seq.from_pretrained(LOCAL_MODELS.caption.id,{revision:LOCAL_MODELS.caption.revision,device:'webgpu',dtype:'q4f16',progress_callback:p=>progress(id,'사진 설명 모델',p)});
    // One global image rather than the processor's expensive multi-crop default.
    processor.image_processor.do_image_splitting=false;
    processor.image_processor.size={longest_edge:512};
    progress(id,'한국어 번역 모델');
    // NLLB q4f16 graph cache inputs are float32. Keep the model default;
    // SmolVLM's own config independently selects float16 caches.
    translator=await pipeline('translation',LOCAL_MODELS.translation.id,{revision:LOCAL_MODELS.translation.revision,device:'webgpu',dtype:'q4f16',progress_callback:p=>progress(id,'한국어 번역 모델',p)});
    progress(id,'준비 완료',{status:'ready',progress:100});
  })();
  try{await preparing;}catch(error){await model?.dispose?.();await translator?.dispose?.();model=processor=translator=null;throw error;}finally{preparing=null;}
}
async function describe({bitmap,question,walking}){
  if(!model||!processor||!translator)throw new Error('기기 내 AI 모델이 준비되지 않았습니다.');
  const start=performance.now();let inputs,output;
  try{
    let englishQuestion=typeof question==='string'?question.trim():'',questionTranslationMs=0;
    if(!walking&&englishQuestion){
      if(englishQuestion.length>400)throw new Error('로컬 AI 사진 질문은 400자 이하로 입력해주세요.');
      if(questionNeedsTranslation(englishQuestion)){
        const translatingQuestion=performance.now();
        const translated=await translator(englishQuestion,{src_lang:'kor_Hang',tgt_lang:'eng_Latn',max_new_tokens:96,num_beams:1,do_sample:false});
        englishQuestion=translatedQuestion(translated?.[0]?.translation_text);questionTranslationMs=Math.round(performance.now()-translatingQuestion);
      }
    }
    const captionStart=performance.now();
    // The model uses a global 512px image. Avoid allocating several full-size
    // RGBA copies first when a manually selected photo is much larger.
    const scale=Math.min(1,512/Math.max(bitmap.width,bitmap.height));
    const canvas=new OffscreenCanvas(Math.max(1,Math.round(bitmap.width*scale)),Math.max(1,Math.round(bitmap.height*scale))),ctx=canvas.getContext('2d',{willReadFrequently:true});
    if(!ctx)throw new Error('사진 버퍼를 준비하지 못했습니다.');ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);
    const pixels=ctx.getImageData(0,0,canvas.width,canvas.height);
    const image=new RawImage(pixels.data,canvas.width,canvas.height,4);
    const prompt=processor.apply_chat_template([{role:'user',content:[{type:'image'},{type:'text',text:localCaptionPrompt({walking,question:englishQuestion})}]}],{add_generation_prompt:true});
    inputs=await processor(prompt,image,{do_image_splitting:false});
    output=await model.generate({...inputs,max_new_tokens:48,do_sample:false,repetition_penalty:1.1});
    const ids=output.tolist()[0].slice(inputs.input_ids.dims.at(-1));
    const english=englishCaption(processor.tokenizer.decode(ids,{skip_special_tokens:true}));
    const captionMs=Math.round(performance.now()-captionStart);
    if(walking&&silentCaption(english))return {silent:true,message:'',english,local:true,backend:'WebGPU',timings:{questionTranslationMs,captionMs,translationMs:0,totalMs:Math.round(performance.now()-start)}};
    const translating=performance.now();
    const translated=await translator(english,{src_lang:'eng_Latn',tgt_lang:'kor_Hang',max_new_tokens:96,num_beams:1,do_sample:false});
    const message=koreanTranslation(translated?.[0]?.translation_text);
    return {silent:false,message,english,local:true,backend:'WebGPU',timings:{questionTranslationMs,captionMs,translationMs:Math.round(performance.now()-translating),totalMs:Math.round(performance.now()-start)}};
  }finally{
    bitmap?.close();await output?.dispose?.();
    if(inputs)for(const value of Object.values(inputs))await value?.dispose?.();
  }
}
self.onmessage=async({data})=>{
  const {id,type}=data;
  if(busy){data.bitmap?.close();self.postMessage({id,error:'이전 로컬 AI 작업을 처리 중입니다. 요청을 쌓지 않습니다.'});return;}
  busy=true;
  try{
    if(type==='prepare'){await prepare(id);self.postMessage({id,result:{backend:'WebGPU',models:LOCAL_MODELS,downloadBytes:LOCAL_DOWNLOAD_BYTES,imageMode:LOCAL_IMAGE_MODE}});}
    else if(type==='describe')self.postMessage({id,result:await describe(data)});
    else throw new Error('지원하지 않는 로컬 AI 요청입니다.');
  }catch(error){self.postMessage({id,error:error?.message||'기기 내 AI 실행에 실패했습니다.',name:error?.name||'Error'});}
  finally{busy=false;}
};
