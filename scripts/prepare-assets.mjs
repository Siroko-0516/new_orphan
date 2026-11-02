import { mkdir, readdir, copyFile, readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { dirname } from 'node:path';

const provenance=JSON.parse(await readFile('public/model-provenance.json','utf8'));
const hash=createHash('sha256').update(await readFile('public/yolo11n.onnx')).digest('hex');
if(hash!==provenance.sha256)throw new Error('YOLO 모델 체크섬이 일치하지 않습니다. 원본을 복구하세요.');
await mkdir('public/ort', { recursive: true });
const source = 'node_modules/onnxruntime-web/dist';
for (const name of await readdir(source)) {
  if (name.startsWith('ort-wasm') && /\.(wasm|mjs)$/.test(name)) {
    await copyFile(`${source}/${name}`, `public/ort/${name}`);
  }
}
// Transformers uses a different ORT build. Mixing its WASM with YOLO's breaks
// GPU/model initialisation; ship each runtime's own matching files.
const require = createRequire(import.meta.url);
const transformerRequire = createRequire(require.resolve('@huggingface/transformers'));
const localOrt = dirname(transformerRequire.resolve('onnxruntime-web'));
await mkdir('public/local-ort', { recursive: true });
for (const name of await readdir(localOrt)) {
  if (name.startsWith('ort-wasm') && /\.(wasm|mjs)$/.test(name)) {
    await copyFile(`${localOrt}/${name}`, `public/local-ort/${name}`);
  }
}
