#!/usr/bin/env python3
"""P03 production DOM/controller Teletext smoke with deterministic server responses.
No real SageTV server or vendor MSE claim is made here.
"""
from pathlib import Path
import os,re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'src/main/webapp';count=0
def passed(s):
 global count;count+=1;print('PASS '+s,flush=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1280,'height':800});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S);html=re.sub(r'<link[^>]*>','',html);page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
 page.evaluate(r"""(()=>{const T=window.TEST={requests:[],pending:[],stream:null,time:0,subtitlePolls:0,storage:{}};T.emit=e=>T.stream?T.stream.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\n')):T.pending.push(e);
 T.source={tracks:[{index:0,type:'video',codec:'h264',width:1280,height:720},{index:6,type:'subtitle',codec:'dvb_teletext',serviceKind:'teletext',sourcePid:300,teletextType:2,teletextPage:888,language:'eng',evidence:'observed-pmt'}]};
 T.active={requested:{profileSchemaVersion:'3',captions:'teletext',captionService:'1',captionPage:'888',captionLanguage:'eng',captionOffsetMs:'0'},effective:{video:'h264',audio:'aac',captionTransport:'original-source DVB Teletext decoder'},source:T.source};
 window.mpegts={version:'1.8.0-MOCK',Events:{ERROR:'e',MEDIA_INFO:'i',STATISTICS_INFO:'s'},getFeatureList:()=>({mseLivePlayback:true}),createPlayer:(src,cfg)=>({handlers:{},on(k,f){this.handlers[k]=f},attachMediaElement(v){this.v=v},load(){},destroy(){}})};
 const v=document.querySelector('#videoPlane');v._paused=false;Object.defineProperties(v,{currentTime:{get:()=>T.time,set:x=>T.time=x},paused:{get:()=>v._paused},readyState:{get:()=>4},videoWidth:{get:()=>1280},videoHeight:{get:()=>720},buffered:{get:()=>({length:1,start:()=>0,end:()=>T.time+20})}});v.play=()=>{v._paused=false;return Promise.resolve()};v.pause=()=>{v._paused=true};v.load=()=>{};
 window.fetch=async(url,options={})=>{const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action;T.requests.push({path:u.pathname,data:d,method:options.method||'GET'});const j=x=>new Response(JSON.stringify(x));
 if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const x of T.pending)T.emit(x);T.pending=[]}}));if(a==='start')return j({session:'p03-browser'});if(a==='streamSettings')return j({ok:true,active:T.active});if(a==='status')return j({transport:'mpegts',running:true,hlsSeconds:30,streamSettings:T.active});if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});
 if(u.pathname.endsWith('/api/subtitles')){T.subtitlePolls++;const tm=Number(d.timeMs)||0;let cues=[];if(T.subtitlePolls===1)cues=[{kind:'text',owner:'local_broadcast',ptsMs:0,text:'P03 PAGE 888',clear:false}];else if(tm>=1000&&T.subtitlePolls<6)cues=[{kind:'text',owner:'local_broadcast',ptsMs:1000,text:'SECOND PAGE',clear:false}];else if(tm>=2000)cues=[{kind:'text',owner:'local_broadcast',ptsMs:2000,text:'',clear:true}];return j({contractVersion:1,cues,teletext:{active:true,selectedTrack:21504,services:[{trackId:21504,pid:300,language:'eng',type:2,page:888}]}})}return j({ok:true});};})();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-playback.js']:page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
 page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
 page.locator('#settingsToggle').click();page.wait_for_function("document.querySelector('#stream_teletextService').options.length===2")
 assert 'Page 888' in page.locator('#stream_teletextService option').nth(1).inner_text();passed('live typed inventory populates explicit Teletext page/PID/language selector')
 page.locator('#captionAuthorityMode').select_option('cc1');page.locator('#cc1Type').select_option('teletext');page.locator('#stream_teletextService').select_option('300:888:eng');assert page.locator('#stream_captionPage').input_value()=='888';assert page.locator('#stream_captionLanguage').input_value()=='eng';page.locator('#saveStreaming').click();page.wait_for_function("document.querySelector('#streamingResult').textContent.startsWith('Saved')");passed('Teletext service selection updates durable page/language preference')
 page.locator('#closeSettings').click();page.evaluate("TEST.emit({type:'videoStart',hlsSession:'33333333333333333333333333333333',transport:'mpegts',streamUrl:'continuous/33333333333333333333333333333333/stream.ts',durationMs:300000,streamSettings:TEST.active})")
 page.wait_for_function("document.querySelector('#localCaptions').textContent.includes('P03 PAGE 888')");passed('production browser overlay renders local Teletext text cue')
 before=page.evaluate('TEST.subtitlePolls');page.wait_for_timeout(800);after=page.evaluate('TEST.subtitlePolls');assert after>before;passed('Teletext clock/poll continues while SageTV OSD is hidden and idle')
 page.evaluate('TEST.time=2.2');page.wait_for_function("document.querySelector('#localCaptions').textContent===''",timeout=3000);passed('timed Teletext clear removes overlay without a new GFX/OSD frame')
 assert not errors,errors;browser.close()
print(f'Chromium Teletext P03: {count} tests PASS (mock native/vendor transport; production DOM/controller)')
