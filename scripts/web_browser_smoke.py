"""Browser smoke: real local YOLO with public fake-camera fixture; mocked AI/audio.
Requires Python Playwright, Chromium, ffmpeg and a running built site.
No personal images/keys or paid provider calls are used.
"""
import asyncio, json, os, shutil, subprocess, tempfile, time, urllib.request
from pathlib import Path
from urllib.parse import urlparse
from playwright.async_api import async_playwright

BASE=os.getenv('GILJABI_WEB_URL','http://127.0.0.1:8000/').rstrip('/')+'/'
MESSAGE='앞쪽 통로에 버스와 사람이 보여요.'
DUMMY_GEMINI_KEY='personal-e2e-key-not-real-123456'
DUMMY_ELEVEN_KEY='eleven-e2e-key-not-real-123456'
STATUS={'walking_protocol':1,'provider':'gemini','providers':{'gemini':True},'models':{'gemini':'gemini-3.1-flash-lite'},'elevenlabs_configured':False}
SILENT={'speak':False,'message':'','urgency':'none'}
MEANINGFUL={'speak':True,'message':MESSAGE,'urgency':'notice'}
INIT=r'''
window.__qa={speech:[],workerFrames:0,backends:[],maxDetections:0,gum:[],aiStates:[],ttsStates:[],blockedSpeechRequests:0,workerErrors:[]};
const OriginalWorker=window.Worker;
window.Worker=class extends OriginalWorker{
 constructor(...args){super(...args);this.addEventListener('message',event=>{
  if(event.data?.error)window.__qa.workerErrors.push(event.data.error);
  const value=event.data?.result;if(value?.backend)window.__qa.backends.push(value.backend);
  if(Array.isArray(value?.detections)){window.__qa.workerFrames++;window.__qa.maxDetections=Math.max(window.__qa.maxDetections,value.detections.length);}
 });}
};
const originalGum=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
navigator.mediaDevices.getUserMedia=async constraints=>{window.__qa.gum.push(constraints);return originalGum(constraints);};
window.SpeechSynthesisUtterance=class{constructor(text){this.text=text;}};
const synth=window.speechSynthesis;
synth.getVoices=()=>[{lang:'ko-KR',name:'QA Korean',localService:true}];
synth.speak=utterance=>{window.__qa.speech.push({text:utterance.text,at:Date.now()});setTimeout(()=>utterance.onstart?.({}),5);setTimeout(()=>utterance.onend?.({}),45);};
synth.cancel=()=>{};
document.addEventListener('DOMContentLoaded',()=>{
 const led=document.getElementById('ai-led');if(led)new MutationObserver(()=>window.__qa.aiStates.push(led.textContent)).observe(led,{subtree:true,characterData:true,childList:true});
 const tts=document.getElementById('tts-feedback');if(tts)new MutationObserver(()=>window.__qa.ttsStates.push(tts.textContent)).observe(tts,{subtree:true,characterData:true,childList:true});
});
'''

async def fixture_context(browser, scenario, voice=False):
    context=await browser.new_context(viewport={'width':390,'height':844},permissions=['camera'],service_workers='block')
    await context.add_init_script(INIT)
    state={'scenario':scenario,'assess':[],'requests':[],'external':[],'page_errors':[],'console_errors':[],'speech_api_calls':0,'direct_calls':0,'mocked_eleven_calls':0,'key_header_verified':False,'key_absent_from_url_and_body':True,'console':[]}
    async def route_handler(route):
        url=route.request.url;path=urlparse(url).path
        if path.endswith('/api/status'):
            await route.fulfill(status=404 if scenario=='static' else 200,content_type='application/json',body=json.dumps({} if scenario=='static' else STATUS));return
        if path.endswith('/api/assess'):
            body=route.request.post_data_json or {};state['assess'].append({'manual':body.get('manual'),'frameAgeMs':body.get('frameAgeMs'),'images':len(body.get('images',[])),'previousSpoken':body.get('previousSpoken','')})
            index=len(state['assess'])
            if scenario=='stale' and index==1:await asyncio.sleep(6.3)
            reply=MEANINGFUL if scenario in ('repeat','eleven-fallback') or scenario=='stale' and index==1 else SILENT
            try:await route.fulfill(status=200,content_type='application/json',body=json.dumps(reply,ensure_ascii=False))
            except Exception:pass
            return
        if 'generativelanguage.googleapis.com' in url and scenario=='static':
            headers={'Access-Control-Allow-Origin':BASE.rstrip('/'),'Access-Control-Allow-Headers':'content-type,x-goog-api-key','Access-Control-Allow-Methods':'POST,OPTIONS'}
            if route.request.method=='OPTIONS':
                await route.fulfill(status=204,headers=headers);return
            assert route.request.method=='POST',route.request.method
            body=route.request.post_data or ''
            assert route.request.headers.get('x-goog-api-key')==DUMMY_GEMINI_KEY,'Personal key missing from header'
            assert DUMMY_GEMINI_KEY not in url and DUMMY_GEMINI_KEY not in body,'Key exposed in URL or request body'
            state['direct_calls']+=1;state['key_header_verified']=True
            reply={'candidates':[{'content':{'parts':[{'text':json.dumps(SILENT)}]}}]}
            await route.fulfill(status=200,content_type='application/json',headers=headers,body=json.dumps(reply));return
        if path.endswith('/api/speech') and scenario=='eleven-fallback':
            body=json.loads(route.request.post_data or '{}')
            assert body['apiKey']==DUMMY_ELEVEN_KEY and body['voice']=='female'
            state['mocked_eleven_calls']+=1
            await route.fulfill(status=503,content_type='application/json',body=json.dumps({'detail':'Speech fixture failed','fallback':True}));return
        if 'api.elevenlabs.io' in url and scenario=='eleven-fallback':
            headers={'Access-Control-Allow-Origin':BASE.rstrip('/'),'Access-Control-Allow-Headers':'content-type,xi-api-key,accept','Access-Control-Allow-Methods':'POST,OPTIONS'}
            if route.request.method=='OPTIONS':
                await route.fulfill(status=204,headers=headers);return
            assert url.endswith('/v1/text-to-speech/lhTvHflPVOqgSWyuWQry'),'Wrong fixed female voice'
            assert route.request.headers.get('xi-api-key')==DUMMY_ELEVEN_KEY,'Eleven key missing from header'
            assert DUMMY_ELEVEN_KEY not in url and DUMMY_ELEVEN_KEY not in (route.request.post_data or ''),'Eleven key exposed in URL or body'
            state['mocked_eleven_calls']+=1
            await route.fulfill(status=503,content_type='application/json',headers=headers,body='{}');return
        if path.endswith('/api/speech') or 'api.elevenlabs.io' in url or 'generativelanguage.googleapis.com' in url:
            state['speech_api_calls']+=1;await route.abort();return
        if urlparse(url).hostname not in ('127.0.0.1','localhost') and not url.startswith(('blob:','data:')):
            state['external'].append(url);await route.abort();return
        state['requests'].append(path);await route.continue_()
    await context.route('**/*',route_handler)
    page=await context.new_page()
    page.on('pageerror',lambda error:state['page_errors'].append(str(error)))
    page.on('console',lambda message:state['console'].append(message.text))
    page.on('console',lambda message:state['console_errors'].append(message.text) if message.type=='error' else None)
    await page.goto(BASE,wait_until='networkidle')
    await page.wait_for_function("document.getElementById('gemini-state').textContent.includes('개인 Gemini')" if scenario=='static' else "document.getElementById('gemini-state').textContent.includes('사진을 비교')")
    if await page.locator('#voice').is_checked()!=voice:
        await page.locator('.voice-toggle').click()
    assert await page.locator('#voice').is_checked()==voice
    await page.locator('#settings-panel').evaluate('(node)=>node.open=true')
    if scenario=='static':await page.fill('#gemini-key',DUMMY_GEMINI_KEY)
    if scenario=='eleven-fallback':await page.fill('#elevenlabs-key',DUMMY_ELEVEN_KEY)
    await page.select_option('#speech-provider','elevenlabs' if scenario=='eleven-fallback' else 'native')
    await page.click('#save-settings')
    await page.locator('#settings-panel').evaluate('(node)=>node.open=false')
    await page.evaluate('window.scrollTo(0,0)')
    return context,page,state

async def start_camera(page):
    await page.click('#start')
    for attempt in range(90):
        await page.wait_for_timeout(500)
        state=await page.evaluate('({qa:window.__qa,camera:document.getElementById("camera-state").textContent,yolo:document.getElementById("yolo-led").textContent,notice:document.getElementById("notice").textContent,stopped:!document.getElementById("start").disabled})')
        if state['qa']['workerFrames']>=3:break
        if attempt in (3,9,19,39,79):print('CAMERA_WAIT',json.dumps(state,ensure_ascii=False),flush=True)
        if state['stopped'] or state['qa']['workerErrors']:
            await page.screenshot(path='/tmp/giljabi-web-e2e-failure.png',full_page=True)
            raise AssertionError('Camera/YOLO stopped: '+json.dumps(state,ensure_ascii=False))
    else:
        await page.screenshot(path='/tmp/giljabi-web-e2e-failure.png',full_page=True)
        raise AssertionError('No real YOLO frames: '+json.dumps(state,ensure_ascii=False))
    await page.wait_for_function("document.getElementById('yolo-led').textContent.includes('ms')",timeout=15000)
    video=await page.locator('#video').evaluate('(v)=>({width:v.videoWidth,height:v.videoHeight,tracks:v.srcObject?.getTracks().map(t=>({id:t.id,state:t.readyState}))||[],active:v.classList.contains("active")})')
    assert video['width']>0 and video['height']>0 and video['active'],video
    return video

async def stop_camera(page):
    await page.evaluate('window.__qa.stoppingTracks=document.getElementById("video").srcObject?.getTracks()||[]')
    await page.click('#stop')
    await page.wait_for_function("!document.getElementById('video').srcObject && !document.getElementById('start').disabled")
    assert await page.locator('#stop').is_disabled()
    assert await page.locator('#camera-source').is_enabled()
    assert await page.evaluate('window.__qa.stoppingTracks.every(t=>t.readyState==="ended")')

async def run(browser):
    report=[]
    # Actual camera capture + real ONNX/WASM inference, silent provider and mobile render.
    context,page,s=await fixture_context(browser,'camera',False)
    try:first=await start_camera(page)
    except Exception:
        print('FIRST_SCENARIO_DIAGNOSTICS',json.dumps(s,ensure_ascii=False),flush=True)
        raise
    await page.wait_for_function("document.getElementById('ai-led').textContent.includes('조용히')",timeout=15000)
    assert s['assess'],s
    assert await page.evaluate('window.__qa.maxDetections')>0,'Real bus fixture did not yield any detections'
    layout=await page.evaluate('({width:innerWidth,scroll:document.documentElement.scrollWidth,start:document.getElementById("start").getBoundingClientRect().toJSON()})')
    assert layout['scroll']<=layout['width'],layout
    await page.screenshot(path='/tmp/giljabi-web-functional-mobile.png',full_page=True)
    await stop_camera(page)
    await page.select_option('#camera-source','front')
    before=await page.evaluate('window.__qa.workerFrames')
    await page.click('#start')
    await page.wait_for_function('(n)=>window.__qa.workerFrames>=n+2',arg=before,timeout=30000)
    second=await page.locator('#video').evaluate('(v)=>({tracks:v.srcObject.getTracks().map(t=>t.id)})')
    qa=await page.evaluate('window.__qa')
    assert qa['gum'][-1]['video']['facingMode']['ideal']=='user',qa['gum']
    assert first['tracks'][0]['id']!=second['tracks'][0],(first,second)
    await stop_camera(page)
    assert not qa['speech'],qa['speech']
    assert any(path.endswith('.onnx') for path in s['requests']),s['requests']
    assert any(path.endswith('.wasm') for path in s['requests']),s['requests']
    assert 'WASM' in qa['backends'],qa
    s.update({'camera':first,'front_requested':True,'reopened_track_id':second['tracks'][0],'worker_frames':qa['workerFrames'],'max_positive_detections':qa['maxDetections'],'mobile_no_overflow':True,'speech':qa['speech']})
    report.append(s);await context.close()

    # Detectors are positive, voice is enabled, model is none: no raw class narration.
    context,page,s=await fixture_context(browser,'detector',True)
    await page.click('#choose-model');await page.click('[data-provider="none"]')
    await start_camera(page);await page.wait_for_timeout(6500)
    qa=await page.evaluate('window.__qa')
    assert qa['maxDetections']>0,qa
    assert not qa['speech'],'Detector-only mode produced raw speech: '+str(qa['speech'])
    assert not s['assess'],'Detector-only mode called AI'
    await stop_camera(page)
    s.update({'worker_frames':qa['workerFrames'],'max_positive_detections':qa['maxDetections'],'speech':qa['speech'],'no_raw_detector_tts':True});report.append(s);await context.close()

    # A provider repeatedly says the same message. Real playback starts once, then memory suppresses it.
    context,page,s=await fixture_context(browser,'repeat',True)
    await start_camera(page)
    await page.wait_for_function('window.__qa.speech.length===1',timeout=15000)
    await page.wait_for_timeout(10500)
    qa=await page.evaluate('window.__qa')
    assert len(s['assess'])>=3,s['assess']
    assert len(qa['speech'])==1,qa['speech']
    assert qa['speech'][0]['text']==MESSAGE,qa['speech']
    assert all('감지' not in row['text'] for row in qa['speech'])
    assert any(row['previousSpoken']==MESSAGE for row in s['assess'][1:]),s['assess']
    await stop_camera(page)
    s.update({'worker_frames':qa['workerFrames'],'speech':qa['speech'],'same_situation_not_repeated':True});report.append(s);await context.close()

    # First result arrives after the 5s freshness budget. It may not render/spawn speech.
    context,page,s=await fixture_context(browser,'stale',True)
    await start_camera(page)
    await page.wait_for_function("window.__qa.aiStates.some(s=>s.includes('이전 결과 음성 생략'))",timeout=20000)
    await page.wait_for_timeout(1500)
    qa=await page.evaluate('window.__qa')
    assert not qa['speech'],qa['speech']
    assert MESSAGE not in await page.locator('#guidance').inner_text()
    await stop_camera(page)
    s.update({'worker_frames':qa['workerFrames'],'speech':qa['speech'],'stale_result_withheld':True,'ai_states':qa['aiStates']});report.append(s);await context.close()
    # The fixed ElevenLabs voice request fails; actual app falls back to Korean native.
    context,page,s=await fixture_context(browser,'eleven-fallback',True)
    await start_camera(page)
    await page.wait_for_function('window.__qa.speech.length===1',timeout=15000)
    qa=await page.evaluate('window.__qa')
    assert s['mocked_eleven_calls']==1,s
    assert len(qa['speech'])==1 and qa['speech'][0]['text']==MESSAGE,qa['speech']
    assert any('기본 한국어 음성으로 전환' in text for text in qa['ttsStates']),qa['ttsStates']
    assert DUMMY_ELEVEN_KEY not in json.dumps(s['console']+s['page_errors']),'Eleven key printed'
    await stop_camera(page)
    s.update({'worker_frames':qa['workerFrames'],'speech':qa['speech'],'eleven_503_native_fallback':True,'tts_states':qa['ttsStates']})
    report.append(s);await context.close()

    # A static host has no /api/* backend. Personal Gemini key stays in the header.
    context,page,s=await fixture_context(browser,'static',False)
    await page.wait_for_function("document.getElementById('gemini-state').textContent.includes('사진을 비교')")
    await start_camera(page)
    await page.wait_for_function("document.getElementById('ai-led').textContent.includes('조용히')",timeout=15000)
    qa=await page.evaluate('window.__qa')
    assert qa['maxDetections']>0,qa
    assert s['direct_calls']>0 and s['key_header_verified'],s
    assert not s['assess'],'Static host unexpectedly called backend assess'
    assert not qa['speech'],qa['speech']
    assert DUMMY_GEMINI_KEY not in json.dumps(s['console']+s['console_errors']+s['page_errors']),'Key printed to console'
    await stop_camera(page)
    # Verify session-only key disappears after refresh, while backend remains absent.
    await page.reload(wait_until='networkidle')
    await page.wait_for_function("document.getElementById('gemini-state').textContent.includes('개인 Gemini')")
    assert await page.locator('#gemini-key').input_value()=='','Key persisted across reload'
    s.update({'worker_frames':qa['workerFrames'],'max_positive_detections':qa['maxDetections'],'speech':qa['speech'],'static_direct_gemini':True,'key_removed_on_reload':True})
    report.append(s);await context.close()
    for item in report:
        item.pop('console',None)
        assert item['speech_api_calls']==0,item
        assert not item['external'],item['external']
        assert not item['page_errors'],item['page_errors']
    return report

async def main():
    with tempfile.TemporaryDirectory(prefix='giljabi-e2e-') as temp:
        root=Path(temp);image=root/'bus.jpg';video=root/'bus.y4m'
        with urllib.request.urlopen('https://raw.githubusercontent.com/ultralytics/ultralytics/main/ultralytics/assets/bus.jpg',timeout=20) as reply:image.write_bytes(reply.read())
        subprocess.run(['ffmpeg','-v','error','-loop','1','-i',str(image),'-vf','scale=360:480','-t','1','-r','5','-pix_fmt','yuv420p','-f','yuv4mpegpipe',str(video)],check=True)
        async with async_playwright() as p:
            browser=await p.chromium.launch(executable_path=os.getenv('GILJABI_CHROMIUM') or shutil.which('chromium'),headless=True,args=['--no-sandbox','--disable-dev-shm-usage','--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--use-file-for-fake-video-capture='+str(video)])
            try:
                report=await run(browser)
                print(json.dumps({'passed':True,'fixture_source':'Ultralytics public bus.jpg; fake video; real local YOLO WASM','scenarios':report},ensure_ascii=False,indent=2))
            finally:await browser.close()

if __name__=='__main__':asyncio.run(main())
