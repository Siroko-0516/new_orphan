import * as ort from 'onnxruntime-web/wasm';
import { SIZE, decode } from './yolo-core.js';

let session;
const backend = 'WASM';
ort.env.wasm.wasmPaths = new URL('/ort/', self.location.origin).href;
ort.env.wasm.numThreads = 1;
const canvas = new OffscreenCanvas(SIZE, SIZE);
const context = canvas.getContext('2d', { willReadFrequently: true });

self.onmessage = async ({ data: { id, type, bitmap } }) => {
  let tensor, output;
  try {
    if(type==='init') {
      if(!session) {
        // Some mobile WebGPU drivers produce invalid but non-throwing results.
        // Use the CPU/WASM path until GPU inference has parity validation.
        session=await ort.InferenceSession.create('/yolo11n.onnx',{executionProviders:['wasm']});
      }
      self.postMessage({id,result:{backend}});
      return;
    }
    if(!session) throw new Error('먼저 YOLO 모델을 불러와야 합니다.');
    const start=performance.now(), width=bitmap.width, height=bitmap.height;
    const scale=Math.min(SIZE/width,SIZE/height);
    context.fillStyle='rgb(114,114,114)';context.fillRect(0,0,SIZE,SIZE);
    context.drawImage(bitmap,(SIZE-width*scale)/2,(SIZE-height*scale)/2,width*scale,height*scale);
    const pixels=context.getImageData(0,0,SIZE,SIZE).data;
    const plane=SIZE*SIZE, input=new Float32Array(3*plane);
    for(let i=0;i<plane;i++){input[i]=pixels[i*4]/255;input[plane+i]=pixels[i*4+1]/255;input[2*plane+i]=pixels[i*4+2]/255;}
    tensor=new ort.Tensor('float32',input,[1,3,SIZE,SIZE]);
    output=await session.run({[session.inputNames[0]]:tensor});
    const result=output[session.outputNames[0]];
    const detections=decode(result.data,result.dims,width,height);
    self.postMessage({id,result:{detections,ms:Math.round(performance.now()-start),backend}});
  } catch(error) {
    self.postMessage({id,error:type==='init'?'YOLO 모델을 불러오지 못했습니다. 새로고침하거나 다른 브라우저에서 다시 시도하세요.':error.message});
  } finally {
    bitmap?.close();tensor?.dispose();
    if(output)for(const value of Object.values(output))value.dispose();
  }
};
