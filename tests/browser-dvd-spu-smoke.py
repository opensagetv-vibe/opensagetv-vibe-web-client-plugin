#!/usr/bin/env python3
from pathlib import Path
import os,re,base64
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'src/main/webapp';count=0
def ok(s):
 global count;count+=1;print('PASS '+s,flush=True)
rgba=base64.b64encode(bytes([240,20,20,255])).decode()
with sync_playwright() as p:
 b=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox']);page=b.new_page(viewport={'width':1280,'height':800});errs=[];page.on('pageerror',lambda e:errs.append(str(e)))
 html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S);html=re.sub(r'<link[^>]*>','',html);page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
 page.evaluate("""(()=>{const T=window.TEST={stream:null,pending:[],time:0,storage:{},spuSent:false,hls:[]};T.emit=e=>T.stream?T.stream.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\\n')):T.pending.push(e);const v=document.querySelector('#videoPlane');v._paused=false;Object.defineProperties(v,{currentTime:{get:()=>T.time,set:x=>T.time=x},paused:{get:()=>v._paused},readyState:{get:()=>4},videoWidth:{get:()=>720},videoHeight:{get:()=>480},buffered:{get:()=>({length:1,start:()=>0,end:()=>1})}});v.play=()=>Promise.resolve();v.pause=()=>{};v.load=()=>{};v.removeAttribute=()=>{};class Hls{static isSupported=()=>true;static version='P09';static Events={ERROR:'error',MANIFEST_PARSED:'manifest',FRAG_LOADING:'loading',FRAG_LOADED:'loaded',FRAG_BUFFERED:'buffered'};static ErrorTypes={NETWORK_ERROR:'network',MEDIA_ERROR:'media'};constructor(c){this.config=c;this.handlers={};T.hls.push(this)}on(e,f){this.handlers[e]=f}loadSource(){}attachMedia(){this.handlers.manifest?.();this.handlers.buffered?.()}startLoad(){}destroy(){}}window.Hls=Hls;window.fetch=async(url,options={})=>{const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action,j=x=>new Response(JSON.stringify(x));if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const x of T.pending)T.emit(x);T.pending=[]}}));if(a==='start')return j({session:'p09'});if(a==='streamSettings')return j({ok:true,active:null});if(a==='status')return j({session:d.session,playlistReady:true,segments:1,hlsSeconds:1,running:false,endList:true,dvd:true,dvdGeneration:1,error:''});if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});if(u.pathname.endsWith('/api/subtitles')){if(d.scope==='dvd'&&!T.spuSent){T.spuSent=true;return j({cues:[{owner:'dvd_spu',kind:'bitmap',clear:true,canvas:{width:720,height:480},rect:[100,100,1,1],format:'rgba-straight',data:'"""+rgba+"""'}]});}return j({cues:[]});}return j({ok:true});};})();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-dvd-spu.js','miniclient-playback.js']:page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
 page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
 page.evaluate("TEST.emit({type:'videoStart',dvd:true,dvdGeneration:1,hlsSession:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',playlistUrl:'hls/a/stream.m3u8',transport:'hls',startSeconds:0,durationMs:0,paused:false,streamSettings:null})")
 page.wait_for_function("TEST.spuSent===true")
 pix=page.evaluate("[...document.querySelector('#dvdSpuCanvas').getContext('2d').getImageData(100,100,1,1).data]")
 assert pix==[240,20,20,255],pix;ok('DVD SPU RGBA cue paints on dedicated production canvas')
 pe=page.evaluate("getComputedStyle(document.querySelector('#dvdSpuCanvas')).pointerEvents");assert pe=='none',pe;ok('DVD SPU overlay never steals pointer or long-press input')
 geom=page.evaluate("(()=>{const a=document.querySelector('#dvdSpuCanvas').style,b=document.querySelector('#dvbCaptions').style;return[a.left,a.top,a.width,a.height,b.left,b.top,b.width,b.height]})()")
 assert geom[:4]==geom[4:],geom;ok('DVD SPU uses same Fit/Fill/Stretch video rectangle as bitmap captions')
 assert not errs,errs;b.close()
print(f'Chromium DVD P09: {count} tests PASS')
