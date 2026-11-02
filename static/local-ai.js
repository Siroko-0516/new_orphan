import {LOCAL_MODELS,LOCAL_DOWNLOAD_BYTES,LOCAL_MIN_GPU_BUFFER_BYTES,LOCAL_IMAGE_MODE,abortError,koreanTranslation} from './local-ai-core.js';
export {LOCAL_MODELS,LOCAL_DOWNLOAD_BYTES,LOCAL_MIN_GPU_BUFFER_BYTES,LOCAL_IMAGE_MODE} from './local-ai-core.js';

/** Real browser VLM + offline Korean NMT. prepare() explicitly downloads/caches ~1.44GB. */
export class BrowserLocalAI {
  constructor(){this.worker=null;this.sequence=0;this.generation=0;this.pending=new Map();this._ready=false;this._busy=false;this._preparing=false;this._preparePromise=null;this.progress=null;}
  get ready(){return this._ready;}
  get busy(){return this._busy||this._preparing;}
  static async supported(){
    const result={supported:false,reason:'',backend:'WebGPU',downloadBytes:LOCAL_DOWNLOAD_BYTES};
    if(!globalThis.isSecureContext){result.reason='기기 내 AI는 HTTPS 또는 localhost에서 사용할 수 있습니다.';return result;}
    if(!globalThis.navigator?.gpu){result.reason='이 브라우저는 WebGPU를 지원하지 않습니다. YOLO 물체 감지는 별도로 사용할 수 있습니다.';return result;}
    try{
      const adapter=await navigator.gpu.requestAdapter({powerPreference:'high-performance'});
      if(!adapter){result.reason='사용 가능한 WebGPU 장치를 찾지 못했습니다.';return result;}
      if(!adapter.features.has('shader-f16')){result.reason='이 장치는 로컬 AI에 필요한 WebGPU FP16을 지원하지 않습니다.';return result;}
      if(!(adapter.limits?.maxStorageBufferBindingSize>=LOCAL_MIN_GPU_BUFFER_BYTES&&adapter.limits?.maxBufferSize>=LOCAL_MIN_GPU_BUFFER_BYTES)){result.reason='GPU 버퍼 용량이 부족해 이 기기에서 로컬 한국어 설명을 사용할 수 없습니다.';return result;}
      result.supported=true;return result;
    }catch{result.reason='브라우저의 GPU 장치를 초기화하지 못했습니다.';return result;}
  }
  _start(){
    if(this.worker)return;
    const worker=new Worker(new URL('./local-ai-worker.js',import.meta.url),{type:'module'});this.worker=worker;
    worker.onmessage=({data})=>{
      if(this.worker!==worker)return;
      const task=this.pending.get(data.id);if(!task)return;
      if(data.type==='progress'){this.progress=data.progress;task.onProgress?.(data.progress);return;}
      clearTimeout(task.timer);task.signal?.removeEventListener('abort',task.onAbort);this.pending.delete(data.id);
      if(task.type==='describe')this._busy=false;
      if(data.error){
        const error=Object.assign(new Error(data.error),{name:data.name||'Error'});
        // A rejected ORT run can poison its shared inference chain. Start a
        // fresh worker on the next explicit preparation instead of reusing it.
        this._terminate(error);
        task.reject(error);return;
      }
      if(task.type==='prepare')this._ready=true;
      task.resolve(data.result);
    };
    worker.onerror=()=>{if(this.worker===worker)this._terminate(new Error('기기 내 AI 실행이 중단됐습니다. 브라우저 메모리와 GPU 지원을 확인하고 다시 준비하세요.'));};
  }
  _request(type,payload={},options={}){
    if(options.generation!==undefined&&options.generation!==this.generation)return Promise.reject(abortError());
    this._start();const id=++this.sequence;
    return new Promise((resolve,reject)=>{
      const onAbort=()=>this.stop();
      const timer=setTimeout(()=>this._terminate(new Error(type==='prepare'?'로컬 AI 모델 준비 시간이 초과됐습니다. 연결과 저장 공간을 확인하세요.':'이 기기에서 로컬 AI 처리가 오래 걸려 중지했습니다.')),type==='prepare'?10*60*1000:60000);
      this.pending.set(id,{type,resolve,reject,timer,onProgress:options.onProgress,signal:options.signal,onAbort});
      options.signal?.addEventListener('abort',onAbort,{once:true});
      if(options.signal?.aborted){onAbort();return;}
      try{this.worker.postMessage({id,type,...payload},options.transfer||[]);}
      catch(error){clearTimeout(timer);options.signal?.removeEventListener('abort',onAbort);this.pending.delete(id);reject(error);}
    });
  }
  async prepare(onProgress){
    if(this._ready)return {backend:'WebGPU',models:LOCAL_MODELS,downloadBytes:LOCAL_DOWNLOAD_BYTES,imageMode:LOCAL_IMAGE_MODE};
    if(this._preparePromise)return this._preparePromise;
    this._preparing=true;const generation=this.generation;
    this._preparePromise=(async()=>{
      const capability=await BrowserLocalAI.supported();
      if(generation!==this.generation)throw abortError();
      if(!capability.supported)throw new Error(capability.reason);
      return this._request('prepare',{}, {onProgress,generation});
    })().finally(()=>{if(generation===this.generation){this._preparing=false;this._preparePromise=null;}});
    return this._preparePromise;
  }
  async describe(image,{signal,cancel,question='',walking=false}={}){
    if(!this._ready)throw new Error('먼저 기기 내 AI 모델을 준비하세요.');
    if(this._busy)throw new Error('이전 로컬 AI 분석을 처리 중입니다. 요청을 쌓지 않습니다.');
    if(signal?.aborted||cancel?.())throw abortError();
    this._busy=true;let bitmap;const generation=this.generation;
    const onAbort=()=>this.stop();signal?.addEventListener('abort',onAbort,{once:true});
    try{
      let source=image;
      if(typeof source==='string'){
        if(!/^data:image\/(?:jpeg|png|webp);base64,/i.test(source)||source.length>8*1024*1024)throw new Error('로컬 AI에는 제한된 크기의 사진 데이터만 사용할 수 있습니다.');
        source=await(await fetch(source)).blob();
      }
      bitmap=await createImageBitmap(source);
      if(!bitmap.width||!bitmap.height||bitmap.width>4096||bitmap.height>4096)throw new Error('로컬 AI 사진 크기를 줄여주세요.');
      if(generation!==this.generation||signal?.aborted||cancel?.())throw abortError();
      const result=await this._request('describe',{bitmap,question,walking},{signal,transfer:[bitmap],generation});bitmap=null;
      if(generation!==this.generation||signal?.aborted||cancel?.())throw abortError();
      if(!result.silent)result.message=koreanTranslation(result.message);
      return result;
    }finally{bitmap?.close();signal?.removeEventListener('abort',onAbort);if(generation===this.generation)this._busy=false;}
  }
  _terminate(error=abortError()){
    const worker=this.worker;this.worker=null;this.generation++;worker?.terminate();this._ready=false;this._busy=false;this._preparing=false;this._preparePromise=null;
    for(const task of this.pending.values()){clearTimeout(task.timer);task.signal?.removeEventListener('abort',task.onAbort);task.reject(error);}
    this.pending.clear();
  }
  stop(){if(this._busy||this._preparing||this.pending.size)this._terminate(abortError());}
  close(){this._terminate(abortError());}
}
