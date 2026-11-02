import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  optimizeDeps: { entries: ['index.html'] },
  resolve: { conditions: ['onnxruntime-web-use-extern-wasm', 'module', 'browser', 'development|production'] },
  worker: { format: 'es' },
  server: { proxy: { '/api': 'http://127.0.0.1:8000' } },
  build: { target: 'es2022' },
});
