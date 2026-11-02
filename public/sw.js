const CACHE='giljabi-web-v1';
const base=new URL('./',self.location.href);
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll([base.href,new URL('manifest.webmanifest',base).href,new URL('icon.svg',base).href])));self.skipWaiting();});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('giljabi-web-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',event=>{
  const request=event.request,url=new URL(request.url);
  if(request.method!=='GET'||url.origin!==base.origin||url.pathname.includes('/api/')||request.headers.has('authorization'))return;
  const isAppAsset=url.pathname.startsWith(base.pathname)&&(/\/(?:assets|ort|local-ort)\//.test(url.pathname)||/\/(?:yolo11n\.onnx|icon\.svg|manifest\.webmanifest)$/.test(url.pathname));
  if(request.mode==='navigate')event.respondWith(fetch(request).then(response=>{if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(base.href,copy));}return response;}).catch(()=>caches.match(base.href)));
  else if(isAppAsset)event.respondWith(caches.match(request).then(cached=>cached||fetch(request).then(response=>{if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(request,copy));}return response;})));
});
