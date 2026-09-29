#!/usr/bin/env python3
"""P04 production DOM/controller DVB bitmap smoke with deterministic server responses.
No real SageTV server, real UK recording, or vendor MSE claim is made here.
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
 T.source={tracks:[{index:0,type:'video',codec:'h264',width:720,height:576},{index:4,type:'subtitle',codec:'dvb_subtitle',serviceKind:'dvb-subtitle',sourcePid:336,compositionPageId:1,ancillaryPageId:1,language:'eng',hearingImpaired:false,evidence:'observed-pmt'}]};
 T.active={requested:{profileSchemaVersion:'4',captions:'dvb',captionPid:'336',captionCompositionPage:'1',captionLanguage:'eng',captionOffsetMs:'0'},effective:{video:'h264',audio:'aac',captionTransport:'original-source DVB bitmap decoder'},source:T.source};
 window.mpegts={version:'1.8.0-MOCK',Events:{ERROR:'e',MEDIA_INFO:'i',STATISTICS_INFO:'s'},getFeatureList:()=>({mseLivePlayback:true}),createPlayer:(src,cfg)=>({handlers:{},on(k,f){this.handlers[k]=f},attachMediaElement(v){this.v=v},load(){},destroy(){}})};
 const v=document.querySelector('#videoPlane');v._paused=false;Object.defineProperties(v,{currentTime:{get:()=>T.time,set:x=>T.time=x},paused:{get:()=>v._paused},readyState:{get:()=>4},videoWidth:{get:()=>720},videoHeight:{get:()=>576},buffered:{get:()=>({length:1,start:()=>0,end:()=>T.time+20})}});v.play=()=>{v._paused=false;return Promise.resolve()};v.pause=()=>{v._paused=true};v.load=()=>{};
 window.fetch=async(url,options={})=>{const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action;T.requests.push({path:u.pathname,data:d,method:options.method||'GET'});const j=x=>new Response(JSON.stringify(x));
 if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const x of T.pending)T.emit(x);T.pending=[]}}));if(a==='start')return j({session:'p04-browser'});if(a==='streamSettings')return j({ok:true,active:T.active});if(a==='status')return j({transport:'mpegts',running:true,hlsSeconds:30,streamSettings:T.active});if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});
 if(u.pathname.endsWith('/api/subtitles')){T.subtitlePolls++;const cues=T.subtitlePolls===1?[{kind:'bitmap',owner:'local_dvb',ptsMs:0,durationMs:700,clear:true,canvas:{width:720,height:576},rect:[100,400,2,1],format:'rgba-straight',data:'/wAA/wD/AIA='}]:[];return j({contractVersion:1,cues,dvbBitmap:{active:true,selectedTrack:22784,services:[{trackId:22784,pid:336,language:'eng',subtitlingType:16,compositionPageId:1,ancillaryPageId:1,hearingImpaired:false}]}})}return j({ok:true});};})();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-playback.js']:page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
 page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
 page.locator('#settingsToggle').click();page.wait_for_function("document.querySelector('#stream_dvbService').options.length===2")
 assert 'PID 0x150' in page.locator('#stream_dvbService option').nth(1).inner_text();passed('live typed inventory populates explicit DVB PID/page/language selector')
 page.locator('#captionAuthorityMode').select_option('dvb');page.locator('#stream_dvbService').select_option('336:1:eng');assert page.locator('#stream_captionPid').input_value()=='336';assert page.locator('#stream_captionCompositionPage').input_value()=='1';page.locator('#saveStreaming').click();page.wait_for_function("document.querySelector('#streamingResult').textContent.startsWith('Saved')");passed('DVB service selection keeps physical PID and composition page distinct')
 page.locator('#closeSettings').click();page.evaluate("TEST.emit({type:'videoStart',hlsSession:'44444444444444444444444444444444',transport:'mpegts',streamUrl:'continuous/44444444444444444444444444444444/stream.ts',durationMs:300000,streamSettings:TEST.active})")
 page.wait_for_function("(()=>{const d=document.querySelector('#dvbCaptions').getContext('2d').getImageData(100,400,2,1).data;return d[0]===255&&d[3]===255&&d[5]===255&&d[7]===128})()")
 passed('production browser canvas renders straight-alpha DVB RGBA pixels at source coordinates')
 geom=page.evaluate("(()=>{const c=document.querySelector('#dvbCaptions').getBoundingClientRect(),v=document.querySelector('#videoPlane').getBoundingClientRect(),b=document.querySelector('#videoBounds').getBoundingClientRect();return {cw:c.width,ch:c.height,vw:v.width,vh:v.height,bw:b.width,bh:b.height,left:c.left-b.left,top:c.top-b.top}})()")
 assert geom['cw']>0 and geom['ch']>0 and abs((geom['cw']/geom['ch'])-(720/576))<0.01 and abs((geom['left']*2+geom['cw'])-geom['bw'])<2 and abs((geom['top']*2+geom['ch'])-geom['bh'])<2;passed('DVB bitmap surface follows the source aspect/cover transform while leaving the existing video bounds unchanged')
 page.evaluate('TEST.time=1.0');page.wait_for_function("document.querySelector('#dvbCaptions').getContext('2d').getImageData(100,400,1,1).data[3]===0",timeout=3000);passed('DVB duration timeout clears bitmap without a new video/GFX segment')
 assert page.locator('#localCaptions').inner_text()=='';passed('DVB bitmap path does not activate the text-caption renderer')
 assert not errors,errors;browser.close()
print(f'Chromium DVB P04: {count} tests PASS (mock native/vendor transport; production DOM/controller)')
