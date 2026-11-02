/** Browser-local model identities and result guards. No server inference or API key is used. */
export const LOCAL_MODELS=Object.freeze({
  caption:Object.freeze({id:'HuggingFaceTB/SmolVLM-256M-Instruct',revision:'7e3e67edbbed1bf9888184d9df282b700a323964',license:'Apache-2.0',bytes:188843109}),
  translation:Object.freeze({id:'Xenova/nllb-200-distilled-600M',revision:'261c31d1a5732c67cdd16d80e8d6088507c7ccea',license:'CC-BY-NC-4.0',attribution:'Meta AI, NLLB Team; ONNX conversion by Xenova. Noncommercial use only.',source:'https://huggingface.co/facebook/nllb-200-distilled-600M',bytes:1253954737}),
});
export const LOCAL_DOWNLOAD_BYTES=LOCAL_MODELS.caption.bytes+LOCAL_MODELS.translation.bytes;
// Verified NLLB embedding: float16[256206,1024] = 524,709,888 bytes.
export const LOCAL_MIN_GPU_BUFFER_BYTES=512*1024*1024;
export const LOCAL_IMAGE_MODE='single global 512px image, image splitting disabled';

export function localCaptionPrompt({walking=false,question=''}={}) {
  // The small English VLM does poorly with long Korean instructions or cane priming.
  if(walking)return 'Describe a clearly visible object blocking a walkway, or extending over it, in ONE short English sentence. If no obstruction or walkway is clear from the photo, answer exactly "none". Do not give directions, measurements or safety claims.';
  const custom=typeof question==='string'?question.trim():'';
  if(custom&&/^[\x20-\x7e\s]{1,400}$/.test(custom))return custom+' Answer in one short English sentence about visible evidence only; no advice or measurements.';
  return 'Describe only what is visibly in this image in one short English sentence. Do not give advice or measurements.';
}
export function englishCaption(text) {
  if(typeof text!=='string')throw new Error('로컬 AI가 사진 설명을 반환하지 않았습니다.');
  const clean=text.replace(/^(?:assistant\s*:|answer\s*:)/i,'').replace(/<[^>]{1,80}>/g,'').replace(/\s+/g,' ').trim();
  if(!clean||clean.length>600)throw new Error('로컬 AI 설명이 비어 있거나 너무 깁니다.');
  return clean;
}
export function silentCaption(text) {return /^(?:none|unclear|cannot tell)[.!]?$/i.test(text.trim());}
export function questionNeedsTranslation(text) {return /[가-힣ㄱ-ㅎㅏ-ㅣ]/.test(String(text||''));}
export function translatedQuestion(text) {
  const clean=englishCaption(text).replace(/[‘’]/g,"'").replace(/[“”]/g,'"').replace(/[–—]/g,'-');
  if(clean.length>400||questionNeedsTranslation(clean)||!/^[\x20-\x7e\s]+$/.test(clean))throw new Error('한국어 질문을 번역하지 못했습니다. 질문을 짧게 바꿔주세요.');
  return clean;
}
export function koreanTranslation(text) {
  if(typeof text!=='string')throw new Error('한국어 번역 결과가 없습니다.');
  const clean=text.replace(/\s+/g,' ').trim();
  const hangul=(clean.match(/[가-힣]/g)||[]).length,latin=(clean.match(/[a-z]/gi)||[]).length;
  if(/(?:^|\s)([^\s]{1,24})(?:\s+\1){3,}(?=\s|$)/u.test(clean))throw new Error('번역이 반복되어 음성 안내를 보류했습니다.');
  if(!clean||clean.length>600||hangul<2||latin>Math.max(12,hangul*1.5))throw new Error('한국어 번역을 확인하지 못했습니다. 영어 안내는 자동으로 읽지 않습니다.');
  return clean;
}
export function abortError(message='로컬 AI 작업을 중지했습니다.') {const error=new Error(message);error.name='AbortError';return error;}
