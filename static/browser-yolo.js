export class BrowserYOLO {
  constructor() {
    this.worker=new Worker(new URL('./yolo-worker.js',import.meta.url),{type:'module'});
    this.sequence=0;this.pending=new Map();
    this.worker.onmessage=({data})=>{
      const task=this.pending.get(data.id);if(!task)return;
      clearTimeout(task.timer);this.pending.delete(data.id);
      if(data.error)task.reject(new Error(data.error));else task.resolve(data.result);
    };
    this.worker.onerror=()=>this.fail(new Error('기기의 YOLO 실행이 중단됐습니다. 새로고침 후 다시 시도하세요.'));
  }
  fail(error) {
    for(const task of this.pending.values()){clearTimeout(task.timer);task.reject(error);}
    this.pending.clear();this.worker.terminate();this.broken=true;
  }
  request(type, bitmap) {
    if(this.broken)return Promise.reject(new Error('모델 실행이 중단됐습니다. 새로고침하세요.'));
    return new Promise((resolve,reject)=>{
      const id=++this.sequence;
      const timer=setTimeout(()=>this.fail(new Error('모델 실행 시간이 초과됐습니다. 새로고침하세요.')),type==='init'?120000:30000);
      this.pending.set(id,{resolve,reject,timer});
      this.worker.postMessage({id,type,bitmap},bitmap?[bitmap]:[]);
    });
  }
  load(){return this.request('init');}
  async detect(canvas){return this.request('detect',await createImageBitmap(canvas));}
}
