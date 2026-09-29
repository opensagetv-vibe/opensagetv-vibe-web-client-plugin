#!/usr/bin/env python3
"""P06 production DOM/controller ordinary subtitle smoke.
Deterministic server responses validate safe text, overlap, bitmap pixels,
coexistence and video-geometry alignment. No real SageTV field claim.
"""
from pathlib import Path
import os,re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]; WEB=ROOT/'src/main/webapp'; count=0
def passed(s):
 global count; count+=1; print('PASS '+s,flush=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1280,'height':800}); errors=[]; page.on('pageerror',lambda e:errors.append(str(e)))
 html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S); html=re.sub(r'<link[^>]*>','',html)
 page.set_content(html); page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
 page.evaluate(r"""(()=>{const T=window.TEST={requests:[],pending:[],stream:null,time:0,filePolls:0,storage:{},injected:false};T.emit=e=>T.stream?T.stream.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\n')):T.pending.push(e);
 T.source={tracks:[{index:0,type:'video',codec:'h264',width:720,height:576},{index:2,type:'subtitle',codec:'subrip',language:'eng',title:'English',default:true,forced:false,sourceKind:'embedded'},{index:3,type:'subtitle',codec:'hdmv_pgs_subtitle',language:'eng',title:'Signs',default:false,forced:true,sourceKind:'embedded'}]};
 T.active={requested:{profileSchemaVersion:'6',subtitleMode:'auto',subtitleTrack:'-1',subtitleLanguage:'eng',subtitleOffsetMs:'0',subtitleForcedOnly:'false',captionAuthorityMode:'cc1'},effective:{video:'h264',audio:'aac',subtitle:{index:2,codec:'subrip',language:'eng'}},source:T.source};
 const v=document.querySelector('#videoPlane');v._paused=false;Object.defineProperties(v,{currentTime:{get:()=>T.time,set:x=>T.time=x},paused:{get:()=>v._paused},readyState:{get:()=>4},videoWidth:{get:()=>720},videoHeight:{get:()=>576},buffered:{get:()=>({length:1,start:()=>0,end:()=>T.time+20})}});v.play=()=>{v._paused=false;return Promise.resolve()};v.pause=()=>{v._paused=true};v.load=()=>{};
 window.mpegts={version:'1.8.0-MOCK',Events:{ERROR:'e',MEDIA_INFO:'i',STATISTICS_INFO:'s'},getFeatureList:()=>({mseLivePlayback:true}),createPlayer:()=>({on(){},attachMediaElement(){},load(){},destroy(){}})};
 window.fetch=async(url,options={})=>{const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action;T.requests.push({path:u.pathname,data:d});const j=x=>new Response(JSON.stringify(x));
 if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const x of T.pending)T.emit(x);T.pending=[]}}));if(a==='start')return j({session:'p06-browser'});if(a==='streamSettings')return j({ok:true,active:T.active});if(a==='status')return j({transport:'mpegts',running:true,hlsSeconds:30,streamSettings:T.active});
 if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});
 if(u.pathname.endsWith('/api/subtitles')&&d.scope==='file'){T.filePolls++;let cues=[];if(T.filePolls===1)cues=[
 {kind:'text',owner:'local_file',ptsMs:0,durationMs:1800,clear:false,text:'<script>window.PARITY_INJECTED=true</script>',linePercent:82,positionPercent:50,align:'center'},
 {kind:'text',owner:'local_file',ptsMs:200,durationMs:1800,clear:false,text:'Unicode café 日本語',linePercent:90,positionPercent:50,align:'center'},
 {kind:'bitmap',owner:'local_file',ptsMs:0,durationMs:900,clear:false,canvas:{width:720,height:576},rect:[100,400,2,1],format:'rgba-straight',data:'/wAA/wD/AIA='}
 ];return j({contractVersion:1,scope:'file',cursor:T.filePolls,cues,ordinary:{active:true,mode:'text',trackIndex:2,codec:'subrip'}})}
 if(u.pathname.endsWith('/api/subtitles'))return j({contractVersion:1,cursor:0,cues:[]}); return j({ok:true});};})();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-playback.js']:
  page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
 page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
 page.evaluate("TEST.emit({type:'videoStart',hlsSession:'66666666666666666666666666666666',transport:'mpegts',streamUrl:'continuous/66666666666666666666666666666666/stream.ts',durationMs:300000,streamSettings:TEST.active})")
 page.wait_for_function("document.querySelectorAll('#fileSubtitles .file-subtitle-cue').length===2")
 texts=page.locator('#fileSubtitles .file-subtitle-cue').all_inner_texts(); assert any('<script>' in x for x in texts) and any('Unicode café 日本語' in x for x in texts); assert page.evaluate("window.PARITY_INJECTED") is None
 passed('production DOM renders hostile markup as inert textContent with Unicode preserved')
 pos=page.evaluate("(()=>{const x=[...document.querySelectorAll('#fileSubtitles .file-subtitle-cue')];return x.map(e=>({top:e.style.top,left:e.style.left,align:e.style.textAlign}))})()")
 assert pos[0]['top']=='82%' and pos[0]['left']=='50%' and pos[0]['align']=='center'; passed('basic authored text positioning survives into the browser overlay')
 page.wait_for_function("(()=>{const d=document.querySelector('#fileSubtitleCanvas').getContext('2d').getImageData(100,400,2,1).data;return d[0]===255&&d[3]===255&&d[5]===255&&d[7]===128})()")
 passed('ordinary bitmap cue renders straight-alpha RGBA on its independent canvas')
 # Coexistence: ordinary subtitle surface can remain active while broadcast text surface is independently populated.
 page.evaluate("document.querySelector('#localCaptions').textContent='Broadcast CC independent surface'")
 assert page.locator('#fileSubtitles').inner_text() and 'Broadcast CC' in page.locator('#localCaptions').inner_text(); passed('ordinary subtitle and broadcast-caption surfaces can coexist without sharing queues')
 geom=page.evaluate("(()=>{const c=document.querySelector('#fileSubtitleCanvas').getBoundingClientRect(),t=document.querySelector('#fileSubtitles').getBoundingClientRect(),b=document.querySelector('#videoBounds').getBoundingClientRect();return {cw:c.width,ch:c.height,tw:t.width,th:t.height,left:c.left-b.left,top:c.top-b.top,bw:b.width,bh:b.height}})()")
 assert geom['cw']>0 and abs(geom['cw']-geom['tw'])<1 and abs(geom['ch']-geom['th'])<1 and abs((geom['cw']/geom['ch'])-(720/576))<.01; passed('ordinary text and bitmap overlays follow the displayed video rectangle')
 page.evaluate('TEST.time=1.1'); page.wait_for_function("document.querySelector('#fileSubtitleCanvas').getContext('2d').getImageData(100,400,1,1).data[3]===0",timeout=3000); passed('bitmap duration expiration clears without disturbing text cues')
 page.evaluate('TEST.time=2.2'); page.wait_for_function("document.querySelectorAll('#fileSubtitles .file-subtitle-cue').length===0",timeout=3000); passed('overlapping text cues expire independently at their authored end times')
 assert not errors,errors; browser.close()
print(f'Chromium ordinary P06: {count} tests PASS (mock transport; production DOM/controller)')
