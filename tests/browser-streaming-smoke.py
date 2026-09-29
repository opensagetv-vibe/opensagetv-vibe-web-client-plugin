#!/usr/bin/env python3
"""Production DOM/profile/CC integration, with mock SageTV and mock mpegts.js.
This deliberately does NOT claim end-to-end vendor/video/GPU validation.
"""
from pathlib import Path
import json, os, re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'src/main/webapp'
count=0

def passed(s):
    global count
    count+=1;print('PASS '+s,flush=True)

with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':1440,'height':1000});errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S)
    html=re.sub(r'<link[^>]*>','',html);page.set_content(html)
    page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
    page.evaluate(r"""(()=>{
      const T=window.TEST={requests:[],storage:{},pending:[],owners:[],stream:null,active:null,cursor:0};
      T.emit=e=>T.stream?T.stream.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\n')):T.pending.push(e);
      T.source={tracks:[{index:0,type:'video',codec:'mpeg2video',width:1920,height:1080},
        {index:1,type:'audio',codec:'ac3',language:'eng',channels:6,title:'Main'},
        {index:2,type:'audio',codec:'ac3',language:'spa',channels:2,title:'Spanish'}]};
      T.active={requested:{captions:'608',captionService:'1',captionOffsetMs:'0'},
        effective:{video:'h264',audio:'aac',videoTargetKbps:4500,audioTargetKbps:128,audioStreamIndex:2,reason:'requested transcode'},source:T.source};
      window.mpegts={version:'1.8.0-MOCK',Events:{ERROR:'error',MEDIA_INFO:'info',STATISTICS_INFO:'stats'},
        getFeatureList:()=>({mseLivePlayback:true}),createPlayer:(source,config)=>{
          const o={source,config,handlers:{},on(k,f){this.handlers[k]=f;},attachMediaElement(v){this.video=v;},
            load(){},destroy(){this.destroyed=true;},play(){return this.video.play();},pause(){return this.video.pause();}};
          T.owners.push(o);return o;
        }};
      const v=document.querySelector('#videoPlane');v._paused=true;v._time=0;v._ahead=20;
      Object.defineProperties(v,{
        currentTime:{get:()=>v._time,set:x=>v._time=x},paused:{get:()=>v._paused},readyState:{get:()=>4},
        videoWidth:{get:()=>1280},videoHeight:{get:()=>720},
        buffered:{get:()=>({length:1,start:()=>0,end:()=>v._time+v._ahead})}
      });
      v.play=()=>{v._paused=false;v.dispatchEvent(new Event('playing'));return Promise.resolve();};
      v.pause=()=>{v._paused=true;};v.load=()=>{v._time=0;};
      window.fetch=async(url,options={})=>{
        const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));
        const d=Object.fromEntries(q),a=d.action;T.requests.push({path:u.pathname,method:options.method||'GET',data:d});
        const json=(x,status=200)=>new Response(JSON.stringify(x),{status});
        if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const item of T.pending)T.emit(item);T.pending=[];}}));
        if(a==='start')return json({session:'settings-test',clientId:'020000000abc'});
        if(u.pathname.endsWith('/api/assets'))return json({streamingReady:true,errors:[]});
        if(u.pathname.endsWith('/api/captions')){
          if(Number(d.cursor)>0)return json({cursor:1,packets:[]});
          return json({cursor:1,packets:[[0,'fc142efc1420fc4849fc142f']]});
        }
        if(a==='streamSettings')return json({ok:true,active:T.active});
        if(a==='status')return json({session:d.session,transport:'mpegts',running:true,hlsSeconds:120,durationMs:300000,encoder:'libx264',streamSettings:T.active});
        return json({ok:true});
      };
    })();""")
    for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-playback.js']:
        page.evaluate((WEB/'js'/fn).read_text())
    page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
    page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
    start=page.evaluate("TEST.requests.find(x=>x.data.action==='start').data")
    assert start['stream_player']=='auto' and start['stream_videoMode']=='auto' and start['stream_videoKbps']=='8000',start
    passed('native connect sends explicit browser streaming profile and capabilities')
    page.locator('#settingsToggle').click();page.wait_for_function("document.querySelector('#stream_audioTrack').options.length===3")
    page.locator('#stream_videoMode').select_option('copy');page.wait_for_function("TEST.storage['sagetvWebStreaming.v1']&&JSON.parse(TEST.storage['sagetvWebStreaming.v1']).videoMode==='copy'")
    assert page.evaluate("JSON.parse(TEST.storage['sagetvWebStreaming.v1']).videoMode")=='copy'
    page.locator('#closeSettings').click();page.locator('#settingsToggle').click();page.wait_for_function("document.querySelector('#stream_videoMode').value==='copy'")
    passed('Copy video-processing selection persists immediately across settings close/reopen')
    assert page.locator('#stream_audioTrack option').nth(2).get_attribute('value')=='2'
    assert 'Spanish' in page.locator('#stream_audioTrack option').nth(2).inner_text()
    assert '4500 kbps' in page.locator('#streamingActual').inner_text()
    passed('source-derived language/codec/channel tracks and effective transcode values displayed')
    page.locator('#stream_player').select_option('mpegts');page.locator('#stream_videoMode').select_option('transcode');page.wait_for_function("JSON.parse(TEST.storage['sagetvWebStreaming.v1']).videoMode==='transcode'")
    page.locator('#stream_encoder').select_option('nvenc');page.locator('#stream_videoKbps').fill('4500')
    page.locator('#stream_resolution').select_option('720');page.locator('#stream_fps').select_option('29.97')
    page.locator('#stream_audioTrack').select_option('2');page.locator('#stream_audioCodec').select_option('aac')
    page.locator('#stream_audioKbps').select_option('128');page.locator('#stream_audioChannels').select_option('2')
    page.locator('#stream_audioLanguage').fill('es');page.locator('#stream_audioOffsetMs').fill('250')
    page.locator('#captionAuthorityMode').select_option('cc1');page.locator('#cc1Type').select_option('cea608');page.locator('#cc1Service').fill('2')
    page.locator('#saveStreaming').click();page.wait_for_function("JSON.parse(TEST.storage['sagetvWebStreaming.v1']).audioLanguage==='es'&&JSON.parse(TEST.storage['sagetvWebStreaming.v1']).audioTrack===-1")
    request=page.evaluate("TEST.requests.filter(x=>x.data.action==='streamSettings'&&x.method==='POST').at(-1).data")
    for k,v in {'videoKbps':'4500','resolution':'720','fps':'29.97','encoder':'nvenc','audioTrack':'2','audioCodec':'aac','audioKbps':'128','audioOffsetMs':'250','captions':'608','captionService':'2'}.items():
        assert request['stream_'+k]==v,(k,request)
    assert request['applyNow']=='false'
    saved=page.evaluate("JSON.parse(TEST.storage['sagetvWebStreaming.v1'])")
    assert saved['audioTrack']==-1 and saved['audioLanguage']=='es'
    passed('Save wires all video/audio/CC options; explicit track is not persisted for other recordings')
    page.locator('#resetStreaming').click()
    assert page.locator('#stream_videoKbps').input_value()=='8000'
    assert page.evaluate("JSON.parse(TEST.storage['sagetvWebStreaming.v1']).videoKbps")==4500
    passed('Load defaults edits draft only, without overwriting saved profile')
    page.locator('#preparePlayerAssets').click();page.wait_for_function("document.querySelector('#streamingResult').textContent.startsWith('Player assets cached')")
    asset=page.evaluate("TEST.requests.filter(x=>x.path.endsWith('/api/assets')).at(-1)")
    assert asset['method']=='POST' and asset['data']['family']=='streaming'
    passed('Prepare assets requests only version-pinned streaming library family')
    page.locator('#closeSettings').click()
    page.evaluate("TEST.emit({type:'videoStart',hlsSession:'11111111111111111111111111111111',transport:'mpegts',streamUrl:'continuous/11111111111111111111111111111111/stream.ts',durationMs:300000,streamSettings:TEST.active})")
    page.wait_for_function('TEST.owners.length===1')
    owner=page.evaluate('({source:TEST.owners[0].source,config:TEST.owners[0].config})')
    assert owner['config']['lazyLoadMaxDuration']==180 and not owner['source']['isLive'] and owner['config']['enableWorkerForMSE']
    assert '/continuous/' in owner['source']['url'] and owner['config']['seekParamStart']=='bstart'
    passed('production playback adapter starts continuous TS with 180-second lazy reserve and byte resume')
    page.wait_for_function("document.querySelector('#localCaptions').textContent.includes('HI')")
    assert page.locator('#localCaptions .caption-608').count()==1
    passed('real production CEA decoder and video-clock controller display side-channel CC')
    page.locator('#settingsToggle').click();page.locator('#stream_videoKbps').fill('5000');page.locator('#stream_audioTrack').select_option('2')
    page.evaluate("TEST.emit({type:'videoPause'});document.querySelector('#videoPlane')._time=42;")
    page.locator('#applyStreaming').click();page.wait_for_function("document.querySelector('#streamingResult').textContent.startsWith('Applied')")
    request=page.evaluate("TEST.requests.filter(x=>x.data.action==='streamSettings'&&x.method==='POST').at(-1).data")
    assert request['applyNow']=='true' and request['timeMs']=='42000' and request['expectedHlsSession']=='1'*32 and request['stream_videoKbps']=='5000'
    passed('Apply now carries selected quality/track, current timestamp and stale-session guard')
    page.locator('#captionAuthorityMode').select_option('cc1');page.locator('#cc1Type').select_option('cea608');page.locator('#cc1Service').fill('5');page.locator('#saveStreaming').click()
    page.wait_for_function("document.querySelector('#streamingResult').textContent.includes('CEA-608')")
    assert 'CEA-608' in page.locator('#streamingResult').inner_text()
    passed('invalid CEA-608 service rejected visibly instead of submitting a nonfunctional selector')
    page.locator('#cc1Service').fill('1')
    out=ROOT/'build/test-results';out.mkdir(parents=True,exist_ok=True)
    page.locator('#streamingPreferences').scroll_into_view_if_needed();page.screenshot(path=str(out/'streaming-settings-desktop.png'))
    page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(100)
    assert page.evaluate('document.documentElement.scrollWidth')<=391
    for selector in ['#streamingPreferences','#stream_videoKbps','#stream_audioTrack']:
        b=page.locator(selector).bounding_box();assert b['x']>=-1 and b['x']+b['width']<=391,(selector,b)
    page.locator('#applyStreaming').scroll_into_view_if_needed();assert page.locator('#applyStreaming').is_visible()
    page.screenshot(path=str(out/'streaming-settings-mobile.png'))
    passed('streaming form and long track descriptions fit mobile viewport; all controls scroll into view')
    page.locator('#closeSettings').click();page.evaluate("TEST.emit({type:'videoStop'});")
    page.wait_for_function("TEST.owners[0].destroyed&&document.querySelector('#localCaptions').textContent===''")
    passed('stop destroys TS owner and clears caption output')
    assert not errors,errors;passed('no uncaught JavaScript errors in production settings, playback adapter and caption UI')
    browser.close()
print(f'Chromium streaming settings: {count} tests PASS (mock vendor/SageTV, real DOM/CC decoder)')
