#!/usr/bin/env python3
"""P08 production-DOM DVD lifecycle smoke with deterministic HLS transport."""
from pathlib import Path
import os,re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]; WEB=ROOT/'src/main/webapp'; count=0
def passed(s):
 global count;count+=1;print('PASS '+s,flush=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1280,'height':800});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S);html=re.sub(r'<link[^>]*>','',html)
 page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
 page.evaluate(r"""(()=>{const T=window.TEST={stream:null,pending:[],time:0,storage:{},hls:[],videoReady:true,vfc:null};T.emit=e=>T.stream?T.stream.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\n')):T.pending.push(e);
 const v=document.querySelector('#videoPlane');v._paused=false;Object.defineProperties(v,{currentTime:{get:()=>T.time,set:x=>T.time=x},paused:{get:()=>v._paused},readyState:{get:()=>T.videoReady?4:0},videoWidth:{get:()=>T.videoReady?720:0},videoHeight:{get:()=>T.videoReady?480:0},buffered:{get:()=>({length:1,start:()=>0,end:()=>Math.max(.55,T.time+.55)})}});v.play=()=>{v._paused=false;return Promise.resolve()};v.pause=()=>{v._paused=true};v.load=()=>{T.time=0};v.removeAttribute=()=>{};v.requestVideoFrameCallback=cb=>{T.vfc=cb;return 1};
 const orig=CanvasRenderingContext2D.prototype.drawImage;CanvasRenderingContext2D.prototype.drawImage=function(img,...a){if(img instanceof HTMLVideoElement){this.fillStyle='rgb(200,20,30)';this.fillRect(0,0,this.canvas.width,this.canvas.height);return;}return orig.call(this,img,...a)};
 class Hls{static version='P08-MOCK';static isSupported=()=>true;static Events={ERROR:'error',MANIFEST_PARSED:'manifest',FRAG_LOADING:'loading',FRAG_LOADED:'loaded',FRAG_BUFFERED:'buffered'};static ErrorTypes={NETWORK_ERROR:'network',MEDIA_ERROR:'media'};constructor(c){this.config=c;this.handlers={};this.loads=[];T.hls.push(this)}on(e,f){this.handlers[e]=f}loadSource(u){this.url=u}attachMedia(m){this.media=m;this.handlers.manifest?.();this.handlers.buffered?.()}startLoad(p){this.loads.push(p)}destroy(){this.destroyed=true}recoverMediaError(){}}window.Hls=Hls;
 window.fetch=async(url,options={})=>{const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action,j=x=>new Response(JSON.stringify(x));
 if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const x of T.pending)T.emit(x);T.pending=[]}}));if(a==='start')return j({session:'p08-browser'});if(a==='streamSettings')return j({ok:true,active:null});if(a==='status')return j({session:d.session,playlistReady:true,segments:1,hlsSeconds:.55,running:false,endList:true,dvd:true,dvdGeneration:2,error:''});
 if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});if(u.pathname.endsWith('/api/subtitles'))return j({contractVersion:1,cursor:0,cues:[]});return j({ok:true});};})();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-playback.js']:
  page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
 page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
 page.evaluate("TEST.emit({type:'videoStart',dvd:true,dvdGeneration:2,hlsSession:'88888888888888888888888888888888',playlistUrl:'hls/88888888888888888888888888888888/stream.m3u8',transport:'hls',streamUrl:'',startSeconds:0,durationMs:0,paused:false,streamSettings:null})")
 page.wait_for_function("TEST.hls.length===1")
 cfg=page.evaluate("(()=>({start:TEST.hls[0].loads.slice(),max:TEST.hls[0].config.maxBufferLength,maxmax:TEST.hls[0].config.maxMaxBufferLength}))()")
 assert cfg['start']==[0] and cfg['max']==12 and cfg['maxmax']==12,cfg
 passed('DVD HLS starts from one short cell and promotes to bounded 12-second reserve')
 page.wait_for_function("typeof TEST.vfc==='function'");page.evaluate("TEST.vfc(performance.now(),{});TEST.videoReady=false;TEST.emit({type:'dvd',action:'freeze',generation:2,stillCandidate:true})")
 page.wait_for_function("!document.querySelector('#dvdStillFrame').hidden")
 pixel=page.evaluate("[...document.querySelector('#dvdStillFrame').getContext('2d').getImageData(2,2,1,1).data]")
 assert pixel[0]==200 and pixel[1]==20 and pixel[2]==30 and pixel[3]==255,pixel
 passed('DVD generation boundary promotes the cached authored frame even after the video element has already lost its decodable frame')
 page.evaluate("TEST.videoReady=true;document.querySelector('#videoPlane').dispatchEvent(new Event('timeupdate'))")
 page.wait_for_timeout(30);assert not page.locator('#dvdStillFrame').is_hidden()
 passed('post-FLUSH media events cannot erase a frozen DVD menu background')
 page.evaluate("TEST.videoReady=false;TEST.emit({type:'videoStart',dvd:true,dvdGeneration:3,hlsSession:'99999999999999999999999999999999',playlistUrl:'hls/99999999999999999999999999999999/stream.m3u8',transport:'hls',streamUrl:'',startSeconds:0,durationMs:0,paused:false,streamSettings:null})")
 page.wait_for_function("TEST.hls.length===2")
 assert not page.locator('#dvdStillFrame').is_hidden()
 passed('prior DVD menu frame remains visible while a replacement HLS cell has not decoded a frame')
 page.evaluate("TEST.emit({type:'dvd',action:'freeze',generation:3,stillCandidate:true});TEST.emit({type:'dvd',action:'flush',generation:4})")
 page.wait_for_timeout(50)
 assert not page.locator('#dvdStillFrame').is_hidden()
 pixel2=page.evaluate("[...document.querySelector('#dvdStillFrame').getContext('2d').getImageData(2,2,1,1).data]")
 assert pixel2[0]==200 and pixel2[1]==20 and pixel2[2]==30 and pixel2[3]==255,pixel2
 passed('a DVD generation with no decoded frame retains the prior menu image instead of replacing it with black')
 page.evaluate("TEST.videoReady=true;TEST.emit({type:'videoStart',dvd:true,dvdGeneration:4,hlsSession:'77777777777777777777777777777777',playlistUrl:'hls/77777777777777777777777777777777/stream.m3u8',transport:'hls',streamUrl:'',startSeconds:0,durationMs:0,paused:false,streamSettings:null})")
 page.wait_for_function("TEST.hls.length===3")
 assert not page.locator('#dvdStillFrame').is_hidden()
 page.wait_for_function("typeof TEST.vfc==='function'");page.evaluate("TEST.vfc(performance.now(),{})")
 page.wait_for_function("document.querySelector('#dvdStillFrame').hidden")
 passed('first decoded frame from a replacement DVD generation releases the retained still')
 assert not errors,errors
 browser.close()
print(f'Chromium DVD P08: {count} tests PASS (mock HLS; production DOM/controller)')
