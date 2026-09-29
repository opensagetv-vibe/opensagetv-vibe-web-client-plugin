#!/usr/bin/env python3
"""Real production DOM/CSS/icon/video-layout tests; synthetic native transport.

Bundled icon bytes and a synthetic H.264 fixture are decoded from data/blob URLs.
Browser navigation is disabled by the test environment; no HTTP/MSE claim is made. SageTV
and hls.js are mocked: these are not live SageMC, GPU or mpegts.js/MSE tests.
Optional --baseline-war demonstrates the old black-clear resize regression.
"""
from pathlib import Path
from contextlib import contextmanager
import argparse, base64, json, mimetypes, os, re, subprocess, tempfile, zipfile
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/test-results';OUT.mkdir(parents=True,exist_ok=True)
count=0

def passed(name):
    global count
    count+=1;print('PASS '+name,flush=True)

@contextmanager
def serve(web):
    # Retain the context shape for optional extraction of the old WAR.
    yield Path(web)

INIT=r"""(()=>{
 const T=window.TEST={requests:[],pending:[],stream:null,width:0,height:0,hls:[],realMedia:false};
 T.gfx=(cmd,...numbers)=>{const b=new Uint8Array(4+numbers.length*4),v=new DataView(b.buffer);b[0]=cmd;b[1]=numbers.length*4>>16;b[2]=numbers.length*4>>8;b[3]=numbers.length*4;numbers.forEach((n,i)=>v.setUint32(4+i*4,n>>>0));return {type:'gfx',cmd,payload:btoa(String.fromCharCode(...b))};};
 T.emit=x=>{if(T.stream)T.stream.enqueue(new TextEncoder().encode(JSON.stringify(x)+'\n'));else T.pending.push(x);};
 T.frame=()=>{T.emit(T.gfx(31));T.emit(T.gfx(18,0,0,T.width,T.height,0xff000000));T.emit(T.gfx(30));};
 T.resize=(w,h)=>{T.width=w;T.height=h;T.emit({type:'resize',width:w,height:h});};
 T.startVideo=()=>{T.emit({type:'videoStart',mediaId:314,hlsSession:'video-fixture',playlistUrl:'fixture.m3u8',durationMs:9000,paused:true});T.emit({type:'videoBounds',dst:[0,0,T.width,T.height],src:[0,0,640,360]});T.frame();};
 T.paint=(withOSD)=>{const w=T.width,h=T.height;T.emit(T.gfx(31));
  // The native picture area and opaque edge mattes used when the OSD hides.
  T.emit(T.gfx(18,w*.065,h*.02,w*.87,h*.96,0xff000000));
  for(const r of [[0,0,w*.065,h],[w*.935,0,w*.065,h],[0,0,w,h*.02],[0,h*.98,w,h*.02]])T.emit(T.gfx(17,...r,0xff000000,0xff000000,0xff000000,0xff000000));
  if(withOSD)T.emit(T.gfx(17,0,0,w,h*.18,0xa0000000,0xa0000000,0xa0000000,0xa0000000));
  T.emit(T.gfx(30));};
 window.Hls=class {static version='layout-fixture-only';static isSupported(){return true;}
  static Events={ERROR:'error',MANIFEST_PARSED:'manifest',FRAG_BUFFERED:'buffered'};static ErrorTypes={NETWORK_ERROR:'network',MEDIA_ERROR:'media'};
  constructor(config){this.config=config;this.handlers={};T.hls.push(this);}on(k,v){this.handlers[k]=v;}loadSource(){}
  attachMedia(el){if(T.realMedia){el.src=T.fixtureURL;el.load();}}startLoad(){}destroy(){}
 };
 const realFetch=window.fetch.bind(window);
 T.saved={};T.storage={getItem:k=>T.saved[k]||null,setItem:(k,v)=>{T.saved[k]=String(v);}};
 const realBlobURL=URL.createObjectURL.bind(URL);URL.createObjectURL=blob=>{if(blob.type==='application/json')T.exportBlob=blob;return realBlobURL(blob);};
 HTMLAnchorElement.prototype.click=function(){}; // inspect generated export bytes, not OS download policy

 window.fetch=async(url,o={})=>{const u=new URL(url,location.href);
  if(!u.pathname.startsWith('/api/'))return realFetch(url,o);
  const q=new URLSearchParams(u.search);if(o.body)new URLSearchParams(o.body).forEach((v,k)=>q.set(k,v));
  const d=Object.fromEntries(q),a=d.action;T.requests.push([a,d]);let out={ok:true};
  if(u.pathname==='/api/hls')out={playlistReady:true,segments:80,hlsSeconds:120,hlsFillRate:3,recentFillRate:3,running:true,endList:false,durationMs:9000,mediaId:314};
  else if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const item of T.pending)T.emit(item);T.pending=[];}}));
  else if(a==='start'){T.resize(+d.width,+d.height);T.frame();out={session:'native-fixture',clientId:'020000000324',alive:true};}
  else if(a==='resize'){T.resize(+d.width,+d.height);T.frame();}
  else if(u.pathname==='/api/support'&&a==='status')out={configured:false,writesEnabled:false,destinationName:'',spoolFiles:0,spoolBytes:0};
  else if(u.pathname==='/api/support'&&a==='report')return new Response(new Blob(['PK\u0003\u0004fixture'],{type:'application/zip'}),{headers:{'Content-Type':'application/zip','X-SageTV-Report-Save':'not-requested'}});
  else if(a==='status')out={alive:true,connection:{startupEvents:{ready:true,firstFrameCompleted:true}}};
  else if(a==='streamSettings')out={active:null};
  return new Response(JSON.stringify(out),{headers:{'Content-Type':'application/json'}});
 };
})();"""

def load(browser,web,settings=None,size=(1600,1000)):
    c=browser.new_context(viewport={'width':size[0],'height':size[1]},service_workers='block')
    p=c.new_page();errs=[];p.on('pageerror',lambda e:errs.append(str(e)));p.set_default_timeout(8000)
    html=(web/'miniclient.html').read_text()
    html=re.sub(r'<script[^>]*>.*?</script>','',html,flags=re.S);html=re.sub(r'<link[^>]*>','',html)
    def asset(match):
        name=match.group(1);raw=(web/name).read_bytes();mime=mimetypes.guess_type(name)[0] or 'application/octet-stream'
        return 'src="data:'+mime+';base64,'+base64.b64encode(raw).decode()+'"'
    html=re.sub(r'src="(icons/[^"]+)"',asset,html)
    p.set_content(html);p.add_style_tag(content=(web/'css/miniclient.css').read_text());p.evaluate(INIT)
    if settings is not None:p.evaluate('s=>TEST.storage.setItem("sagetvWebMiniClientSettings.v1",JSON.stringify(s))',settings)
    if (OUT/'display-fixture.mp4').exists():
        p.evaluate('s=>{const a=Uint8Array.from(atob(s),c=>c.charCodeAt(0));TEST.fixtureURL=URL.createObjectURL(new Blob([a],{type:"video/mp4"}));}',base64.b64encode((OUT/'display-fixture.mp4').read_bytes()).decode())
    for filename in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-dvd-spu.js','miniclient-playback.js','miniclient-support.js']:
        p.evaluate((web/'js'/filename).read_text())
    main=(web/'js/miniclient.js').read_text()
    p.evaluate('(function(location,localStorage){\n'+main+'\n})({href:"https://fixture.test/miniclient.html",origin:"https://fixture.test",search:""},TEST.storage)')
    p.wait_for_function("window.TEST && window.TEST.stream && document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
    p.wait_for_timeout(230)
    return c,p,errs

def bounds(p,id):return p.locator('#'+id).bounding_box()
def same(a,b):return all(abs(a[k]-b[k])<1.1 for k in ('x','y','width','height'))
def requests(p,actions):return p.evaluate('(actions)=>TEST.requests.filter(r=>actions.includes(r[0]))',actions)
def video_start(p):p.evaluate('TEST.startVideo()');p.wait_for_function("!document.querySelector('#videoBounds').hidden");p.wait_for_timeout(120)
def emit(p,event):p.evaluate('e=>TEST.emit(e)',event);p.wait_for_timeout(80)
def photo(p,name):p.screenshot(path=str(OUT/name))
def open_remote(p):
    if not p.locator('#remotePanel').is_visible():p.locator('#remoteToggle').click()

def test_current(browser,origin):
    c,p,errs=load(browser,origin)
    assert same(bounds(p,'stage'),bounds(p,'gfxCanvas'))
    passed('automatic UI fills browser content below toolbar without added borders')
    open_remote(p)
    p.wait_for_function("[...document.querySelectorAll('.vibe-icon img')].every(i=>i.complete && i.naturalWidth>0)")
    icons=p.locator('.vibe-icon img');assert icons.count()==30
    assert p.evaluate("[...document.querySelectorAll('.vibe-icon')].every(b=>!!b.getAttribute('aria-label') && !!b.title)")
    passed('all 30 bundled Android icon bytes decode with accessible labels')
    b=bounds(p,'stage');home=bounds(p,'nav_home');help_=bounds(p,'remoteHelp');close=bounds(p,'closeRemote');up=bounds(p,'nav_up');down=bounds(p,'nav_down');left=bounds(p,'nav_left');ok=bounds(p,'nav_select');right=bounds(p,'nav_right');keyboard=bounds(p,'keyboardToggle');play=bounds(p,'nav_media_play')
    assert home['x']<b['width']*.1 and close['x']>b['width']*.9 and help_['x']<close['x']
    assert up['y']<ok['y']<down['y'] and left['x']<ok['x']<right['x']
    assert keyboard['x']<play['x'] and keyboard['y']>=down['y']
    assert p.evaluate("getComputedStyle(document.querySelector('.mc-remote-card')).backgroundColor")=='rgba(0, 0, 0, 0)'
    assert p.locator('#remoteTools').is_hidden()
    passed('transparent edge overlay has original Home/help, D-pad, transport and tool groups')
    photo(p,'vibe-overlay-desktop-3.2.12.png')
    p.locator('#closeRemote').click();video_start(p);before=bounds(p,'videoBounds');assert same(before,bounds(p,'stage'))
    p.evaluate('TEST.paint(false)');p.wait_for_timeout(120);assert same(before,bounds(p,'videoBounds'))
    passed('native inset black CLEAR_RECT no longer changes fullscreen video bounds')
    assert p.evaluate("document.querySelector('#gfxCanvas').getContext('2d').getImageData(10,100,1,1).data[3]")==0
    passed('full-playback edge mattes reveal video instead of adding server black bars')
    p.evaluate('TEST.paint(true)');p.wait_for_timeout(100)
    alpha=p.evaluate("document.querySelector('#gfxCanvas').getContext('2d').getImageData(100,50,1,1).data[3]")
    assert 150<=alpha<=170,alpha
    for _ in range(5):p.evaluate('TEST.paint(false);TEST.paint(true);TEST.paint(false)')
    p.wait_for_timeout(150);assert same(before,bounds(p,'videoBounds'))
    passed('OSD show/hide preserves translucent native panels without moving video')
    total=requests(p,['start','stop','seek','recover','fallback','command']);open_remote(p);assert same(before,bounds(p,'videoBounds'));p.locator('#hideRemote').click();assert same(before,bounds(p,'videoBounds'))
    assert total==requests(p,['start','stop','seek','recover','fallback','command'])
    passed('opening and dismissing browser overlay never pauses/restarts/seeks native playback')
    emit(p,{'type':'aspect','value':'Source'})
    assert p.locator('#videoPlane').evaluate('(v)=>v.style.objectFit')=='cover'
    passed('native Source aspect notification does not override selected Fill browser mode')
    open_remote(p);p.locator('#aspectToggle').click();assert p.locator('#videoPlane').evaluate('(v)=>v.style.objectFit')=='contain'
    p.locator('#aspectToggle').click();assert p.locator('#videoPlane').evaluate('(v)=>v.style.objectFit')=='fill'
    p.locator('#aspectToggle').click();assert p.locator('#videoPlane').evaluate('(v)=>v.style.objectFit')=='cover'
    passed('aspect icon cycles Fill, Fit and Stretch without transcoder restart')
    p.locator('#audioSettings').click();assert p.locator('#settingsPanel').is_visible();assert p.locator('#stream_audioCodec').evaluate('(v)=>document.activeElement===v');p.locator('#closeSettings').click()
    open_remote(p);p.locator('#captionSettings').click();assert p.locator('#captionAuthorityMode').evaluate('(v)=>document.activeElement===v');p.locator('#closeSettings').click()
    open_remote(p);p.locator('#remoteSettings').click();assert p.locator('#stream_videoMode').evaluate('(v)=>document.activeElement===v');p.locator('#closeSettings').click()
    passed('video, audio and caption icons open the working browser settings sections')
    open_remote(p);p.locator('#videoInfoBtn').click();assert 'Browser playback and plugin status' in p.locator('#videoInfoText').text_content();p.keyboard.press('Escape');assert p.locator('#videoInfoPanel').is_hidden() and p.locator('#remotePanel').is_visible()
    p.locator('#testVideoBtn').click();p.wait_for_function("document.querySelector('#videoInfoText').textContent.includes('mutation-safety')",timeout=5000);assert 'short duration' in p.locator('#videoInfoText').text_content()
    p.locator('#closeVideoInfo').click();p.locator('#remoteStats').click();assert p.locator('#stats').is_visible() and p.locator('#remotePanel').is_hidden()
    p.locator('#statsToggle').click();passed('info, scoped playback check, stats and nested Escape are functional')
    open_remote(p)
    before_reports=len(requests(p,['report']));p.locator('#diagnosticsBtn').click();p.wait_for_function('(n)=>TEST.requests.filter(r=>r[0]==="report").length>n',arg=before_reports)
    payload=requests(p,['report'])[-1][1];report=json.loads(payload['browserJson']);assert report['version']=='3.2.30' and report['videoLayout']['presentation']['kind']=='fullscreen'
    assert 'clientId' not in report and payload['saveToServer']=='false'
    passed('diagnostic ZIP request contains redacted browser snapshot, placement and P14 version')
    p.locator('#remoteHelp').click();assert p.locator('#seekRange').is_visible();p.locator('#remoteTools [data-command="18"]').click()
    p.locator('#closeRemoteTools').click();p.locator('#keyboardToggle').click();p.locator('#keyboardInput').fill('Vibe café');p.locator('#keyboardSend').click();p.wait_for_timeout(60)
    assert requests(p,['text'])[-1][1]['text']=='Vibe café';p.keyboard.press('Escape');p.locator('#closeRemote').click()
    passed('keypad/timeline extras remain under Help and keyboard sends literal text')
    # Near-full source and explicit menu preview are intentionally different.
    w,h=p.evaluate('[TEST.width,TEST.height]');small=[100,100,350,200]
    emit(p,{'type':'videoBounds','dst':small});p.evaluate('TEST.emit(TEST.gfx(31));TEST.emit(TEST.gfx(18,100,100,350,200,0xff080010));TEST.emit(TEST.gfx(30))');p.wait_for_timeout(80)
    preview=bounds(p,'videoBounds');canvas=bounds(p,'gfxCanvas')
    assert abs(preview['width']-350*canvas['width']/w)<1 and abs(preview['x']-canvas['x']-100*canvas['width']/w)<1
    emit(p,{'type':'menuHint','value':'menuName:Guide, popupName:'});assert same(preview,bounds(p,'videoBounds'))
    passed('small native/color-key previews still match native coordinates through menu changes')
    emit(p,{'type':'videoBounds','dst':[0,0,w,h]});assert same(bounds(p,'videoBounds'),bounds(p,'stage'))
    p.locator('#settingsToggle').click();p.locator('#videoViewport').select_option('native');p.locator('#closeSettings').click()
    emit(p,{'type':'videoBounds','dst':[80,40,w-160,h-80]});assert bounds(p,'videoBounds')['width']<bounds(p,'stage')['width']-100
    p.locator('#settingsToggle').click();p.locator('#videoViewport').select_option('browser');p.locator('#closeSettings').click();assert same(bounds(p,'videoBounds'),bounds(p,'stage'))
    passed('native placement override is available and browser placement restores edge-to-edge')
    # Test actual pointer mapping after independent X/Y canvas sizing.
    stage=bounds(p,'stage');n=len(requests(p,['click']));p.mouse.click(stage['x']+stage['width']*.5,stage['y']+stage['height']*.5);p.wait_for_timeout(100)
    click=requests(p,['click'])[n][1];assert abs(float(click['x'])-w/2)<=1 and abs(float(click['y'])-h/2)<=1
    passed('mouse coordinates use the same full-window UI transform')
    # Every interactive icon is reachable, never off-screen or overlapping peers.
    for size in [(1920,1080),(390,844),(700,390),(320,568),(2141,1503)]:
        p.set_viewport_size({'width':size[0],'height':size[1]});p.wait_for_timeout(330);open_remote(p)
        assert same(bounds(p,'stage'),bounds(p,'gfxCanvas')) and same(bounds(p,'stage'),bounds(p,'videoBounds'))
        rects=p.locator('.vibe-icon').evaluate_all('(bs)=>bs.map(b=>({id:b.id,x:b.getBoundingClientRect().x,y:b.getBoundingClientRect().y,w:b.offsetWidth,h:b.offsetHeight}))')
        for a in rects:
            assert a['x']>=-1 and a['y']>=0 and a['x']+a['w']<=size[0]+1 and a['y']+a['h']<=size[1]+1,(size,a)
        for i,a in enumerate(rects):
            for b in rects[i+1:]:
                overlap=min(a['x']+a['w'],b['x']+b['w'])-max(a['x'],b['x'])>1 and min(a['y']+a['h'],b['y']+b['h'])-max(a['y'],b['y'])>1
                assert not overlap,(size,a['id'],b['id'])
        if size==(390,844):photo(p,'vibe-overlay-mobile-3.2.12.png')
        p.locator('#closeRemote').click()
    passed('desktop, phone, short-landscape and capped-native layouts stay in bounds without icon overlap')
    open_remote(p);p.locator('#remoteHelp').click();p.locator('#renderOnlyBtn').click();p.wait_for_timeout(350)
    assert same(bounds(p,'stage'),bounds(p,'videoBounds')) and abs(bounds(p,'stage')['height']-1503)<1
    p.keyboard.press('Escape');p.wait_for_timeout(350);assert same(bounds(p,'stage'),bounds(p,'videoBounds'))
    passed('render-only enter/exit reflows video and UI to the actual browser height')
    assert not errs,errs;passed('no JavaScript exceptions throughout production UI/layout test')
    c.close()
    # Old default migration must run once and then preserve explicit Fit.
    c,p,errs=load(browser,origin,{'videoFit':'source','uiResolution':'window'})
    saved=p.evaluate("JSON.parse(TEST.storage.getItem('sagetvWebMiniClientSettings.v1'))");assert saved['videoFit']=='cover' and saved['displayPolicyVersion']==2
    p.locator('#settingsToggle').click();p.locator('#videoFit').select_option('source');p.locator('#closeSettings').click()
    # New init script would reapply old settings each navigation, so inspect a new page
    # whose config includes the marked migrated profile instead.
    marked=p.evaluate("JSON.parse(TEST.storage.getItem('sagetvWebMiniClientSettings.v1'))");c.close()
    c,p,errs=load(browser,origin,marked);assert p.locator('#videoFit').input_value()=='source';c.close()
    passed('one-time default migration does not overwrite a subsequent explicit Fit choice')
    c,p,errs=load(browser,origin,{'videoFit':'cover','displayPolicyVersion':2,'uiResolution':'1280x720'})
    video_start(p);assert same(bounds(p,'stage'),bounds(p,'videoBounds')) and not same(bounds(p,'stage'),bounds(p,'gfxCanvas'));c.close()
    passed('fixed 16:9 UI mode does not limit independent fullscreen video area')
    # HTML media actually decodes a local H.264 recording, independent of the mock
    # HLS controller. No screenshot from the user's copyrighted recording used.
    if (OUT/'display-fixture.mp4').exists():
        c,p,errs=load(browser,origin,size=(1200,900));p.evaluate('TEST.realMedia=true');video_start(p)
        p.wait_for_function("document.querySelector('#videoPlane').videoWidth===640 && document.querySelector('#videoPlane').readyState>=2")
        p.evaluate("document.querySelector('#videoPlane').currentTime=1");open_remote(p);p.locator('#nav_media_play').click();p.wait_for_timeout(180);open_remote(p);p.locator('#nav_media_pause').click();p.locator('#closeRemote').click()
        p.wait_for_timeout(150);p.evaluate('TEST.paint(false)');p.wait_for_timeout(150)
        photo(p,'video-fill-3.2.12.png');open_remote(p);photo(p,'vibe-overlay-video-3.2.12.png');p.locator('#closeRemote').click()
        bb=bounds(p,'videoBounds');p.evaluate('TEST.paint(true)');p.wait_for_timeout(100);assert same(bb,bounds(p,'videoBounds'));p.evaluate('TEST.paint(false)')
        p.locator('#settingsToggle').click();p.locator('#videoFit').select_option('source');p.locator('#closeSettings').click();p.wait_for_timeout(120);photo(p,'video-fit-3.2.12.png')
        p.locator('#settingsToggle').click();p.locator('#videoFit').select_option('fill');p.locator('#closeSettings').click();p.wait_for_timeout(120);photo(p,'video-stretch-3.2.12.png')
        assert not errs,errs;c.close();passed('real H.264 fixture decodes with Fill/Fit/Stretch and stable OSD geometry')
    else:print('SKIP real-media layout fixture: FFmpeg not available',flush=True)

def baseline(browser,war):
    with tempfile.TemporaryDirectory(prefix='vibe-baseline-') as tmp:
        web=Path(tmp)
        with zipfile.ZipFile(war) as z:
            for n in z.namelist():
                if n.startswith(('js/','css/','icons/')) or n=='miniclient.html':z.extract(n,web)
        with serve(web) as origin:
            c,p,errs=load(browser,origin);video_start(p);before=bounds(p,'videoBounds');p.evaluate('TEST.paint(false)');p.wait_for_timeout(150);after=bounds(p,'videoBounds')
            assert not same(before,after),(before,after)
            print('REPRODUCED untouched v3.2.3: black-clear paint resized video from '+json.dumps(before)+' to '+json.dumps(after),flush=True)
            c.close()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline-war',type=Path);args=parser.parse_args()
    ffmpeg=os.getenv('TEST_FFMPEG','/usr/bin/ffmpeg')
    if Path(ffmpeg).is_file():
        subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=size=640x360:rate=24','-t','4','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p','-movflags','+faststart',str(OUT/'display-fixture.mp4')],check=True,timeout=30)
    with sync_playwright() as pw:
        browser=pw.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
        if args.baseline_war:baseline(browser,args.baseline_war)
        with serve(ROOT/'src/main/webapp') as origin:test_current(browser,origin)
        browser.close()
    print(f'Vibe overlay/display Chromium: {count} tests PASS (synthetic native/vendor transport; actual bundled assets and local video)',flush=True)
if __name__=='__main__':main()
