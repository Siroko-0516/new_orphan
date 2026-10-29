export const SIZE = 416;
export const LABELS = ['person','bicycle','car','motorcycle','airplane','bus','train','truck','boat','traffic light','fire hydrant','stop sign','parking meter','bench','bird','cat','dog','horse','sheep','cow','elephant','bear','zebra','giraffe','backpack','umbrella','handbag','tie','suitcase','frisbee','skis','snowboard','sports ball','kite','baseball bat','baseball glove','skateboard','surfboard','tennis racket','bottle','wine glass','cup','fork','knife','spoon','bowl','banana','apple','sandwich','orange','broccoli','carrot','hot dog','pizza','donut','cake','chair','couch','potted plant','bed','dining table','toilet','tv','laptop','mouse','remote','keyboard','cell phone','microwave','oven','toaster','sink','refrigerator','book','clock','vase','scissors','teddy bear','hair drier','toothbrush'];

function iou(a, b) {
  const area = box => Math.max(0, box[2]-box[0]) * Math.max(0, box[3]-box[1]);
  const intersection = area([Math.max(a[0],b[0]),Math.max(a[1],b[1]),Math.min(a[2],b[2]),Math.min(a[3],b[3])]);
  return intersection / (area(a)+area(b)-intersection || 1);
}

export function decode(data, dims, width, height, confidence = 0.4) {
  if (dims.length !== 3 || dims[0] !== 1 || dims[1] !== 84 || data.length !== dims[1]*dims[2]) {
    throw new Error('지원하지 않는 YOLO 모델 출력 형식입니다.');
  }
  const count = dims[2], scale = Math.min(SIZE/width, SIZE/height);
  const dx = (SIZE - width*scale)/2, dy = (SIZE-height*scale)/2;
  const clamp = v => Math.max(0, Math.min(1, v));
  const candidates=[];
  for (let i=0; i<count; i++) {
    let score=0, cls=0;
    for (let k=0; k<80; k++) { const p=data[(k+4)*count+i]; if(p>score){score=p;cls=k;} }
    if(score<confidence) continue;
    const x=data[i], y=data[count+i], w=data[2*count+i], h=data[3*count+i];
    const box=[clamp((x-w/2-dx)/scale/width),clamp((y-h/2-dy)/scale/height),clamp((x+w/2-dx)/scale/width),clamp((y+h/2-dy)/scale/height)];
    if(box.some(v=>!Number.isFinite(v)) || box[2]<=box[0] || box[3]<=box[1]) continue;
    candidates.push({label:LABELS[cls],confidence:score,box});
  }
  candidates.sort((a,b)=>b.confidence-a.confidence);
  const kept=[];
  for (const candidate of candidates) {
    if(!kept.some(k=>k.label===candidate.label && iou(k.box,candidate.box)>0.45)) kept.push(candidate);
    if(kept.length===40) break;
  }
  return kept;
}
