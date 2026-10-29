import test from 'node:test';
import assert from 'node:assert/strict';
import { decode, SIZE } from '../static/yolo-core.js';

function output(boxes) {
  const n=boxes.length, data=new Float32Array(84*n);
  boxes.forEach((b,i)=>{b.slice(0,4).forEach((v,k)=>data[k*n+i]=v);data[(4+b[4])*n+i]=b[5];});
  return [data,[1,84,n]];
}

test('letterbox coordinates map back to a landscape camera frame',()=>{
  const [data,dims]=output([[SIZE/2,SIZE/2,SIZE/2,SIZE/4,0,.9]]);
  const result=decode(data,dims,832,416);
  assert.equal(result[0].label,'person');
  assert.deepEqual(result[0].box,[.25,.25,.75,.75]);
});
test('class-aware NMS removes duplicate people but keeps a bus',()=>{
  const [data,dims]=output([[208,208,200,200,0,.9],[208,208,200,200,0,.8],[208,208,200,200,5,.85],[80,80,40,40,1,.1]]);
  assert.deepEqual(decode(data,dims,416,416).map(d=>d.label),['person','bus']);
});
test('padding detections are discarded and boxes are clipped',()=>{
  const [data,dims]=output([[208,10,20,10,0,.9],[10,208,100,104,5,.8]]);
  const result=decode(data,dims,832,416);
  assert.equal(result.length,1);assert.equal(result[0].box[0],0);
});
test('unsupported model shape fails explicitly',()=>{
  assert.throws(()=>decode(new Float32Array(85),[1,85,1],416,416));
});
