#!/usr/bin/env python3
"""P13 production DOM smoke: unified YUV, key repeat and page lifecycle."""
from pathlib import Path
import os,re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'src/main/webapp';count=0
def passed(s):
 global count;count+=1;print('PASS '+s,flush=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1280,'height':800},service_workers='block');errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S);html=re.sub(r'<link[^>]*>','',html)
 page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
 page.evaluate(r"""(()=>{const T=window.TEST={requests:[],streams:{},pending:{},starts:[],beacons:[],storage:{}};
 T.gfx=(cmd,...nums)=>{const b=new Uint8Array(4+nums.length*4),v=new DataView(b.buffer);b[0]=cmd;nums.forEach((n,i)=>v.setInt32(4+i*4,n));return {type:'gfx',cmd,assignedHandle:0,payload:btoa(String.fromCharCode(...b))};};
 T.gfxBytes=(cmd,nums,raw)=>{const b=new Uint8Array(4+nums.length*4+raw.length),v=new DataView(b.buffer);b[0]=cmd;nums.forEach((n,i)=>v.setInt32(4+i*4,n));b.set(raw,4+nums.length*4);return {type:'gfx',cmd,assignedHandle:0,payload:btoa(String.fromCharCode(...b))};};
 T.emit=(id,e)=>{const c=T.streams[id];if(c)c.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\n'));else(T.pending[id]||(T.pending[id]=[])).push(e);};
 Object.defineProperty(navigator,'sendBeacon',{configurable:true,value:(url,body)=>{T.beacons.push({url:String(url),body:String(body)});return true;}});
 window.fetch=async(url,options={})=>{const u=new URL(url),q=new URLSearchParams(u.search);if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action;T.requests.push({path:u.pathname,action:a,data:d});const j=x=>new Response(JSON.stringify(x));
  if(a==='start'){const id='p13-'+(T.starts.length+1);T.starts.push(d);return j({session:id,clientId:d.clientId,alive:true});}
  if(a==='stream'){const id=d.session;return new Response(new ReadableStream({start(c){T.streams[id]=c;for(const e of T.pending[id]||[])T.emit(id,e);T.pending[id]=[];}}));}
  if(a==='status')return j({session:d.session,alive:true,running:false,threading:{producerMonitor:false,feeder:false,stderrReader:false}});
  if(a==='streamSettings')return j({ok:true,active:null});
  if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});if(u.pathname.endsWith('/api/subtitles'))return j({contractVersion:1,cues:[]});return j({ok:true});
 };
 const v=document.querySelector('#videoPlane');v._paused=true;Object.defineProperties(v,{currentTime:{get:()=>0,set:()=>{}},paused:{get:()=>v._paused},readyState:{get:()=>0},videoWidth:{get:()=>0},videoHeight:{get:()=>0},buffered:{get:()=>({length:0})}});v.play=()=>{v._paused=false;return Promise.resolve()};v.pause=()=>{v._paused=true};v.load=()=>{};
 })();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-dvd-spu.js','miniclient-playback.js']:
  page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=v})")
 page.wait_for_function('TEST.starts.length===1')
 assert page.evaluate("TEST.starts[0].unifiedGraphics")=='false';passed('unified graphics is OFF in the production connection by default')
 page.locator('#settingsToggle').click();page.locator('#unifiedGraphics').check();page.wait_for_function('TEST.starts.length===2')
 assert page.evaluate("TEST.starts[1].unifiedGraphics")=='true';passed('browser opt-in reconnects and scopes UNIFIED capability to the replacement session')
 page.locator('#closeSettings').click();sid=page.evaluate("'p13-'+TEST.starts.length")
 # YUV red-ish 2x1 image, then draw it enlarged.
 page.evaluate("""id=>{let e=TEST.gfx(26,2,1,256);e.assignedHandle=91;TEST.emit(id,e);TEST.emit(id,TEST.gfxBytes(32,[91,0,2],new Uint8Array([82,82])));TEST.emit(id,TEST.gfxBytes(32,[91,1,2],new Uint8Array([90,240])));TEST.emit(id,TEST.gfx(31));TEST.emit(id,TEST.gfx(24,0,0,40,20,91,0,0,2,1,0xffffffff));TEST.emit(id,TEST.gfx(30));}""",sid)
 page.wait_for_function("(()=>{const d=document.querySelector('#gfxCanvas').getContext('2d').getImageData(10,10,1,1).data;return d[0]>200&&d[1]<100&&d[2]<100&&d[3]===255})()")
 passed('format-256 Y plus interleaved UV rows render through the production Canvas handle path')
 # Browser owns D-pad repeat and cancels on keyup.
 page.evaluate("window.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}))");page.wait_for_timeout(650);page.evaluate("window.dispatchEvent(new KeyboardEvent('keyup',{key:'ArrowDown',bubbles:true}))")
 before=page.evaluate("TEST.requests.filter(r=>r.action==='command'&&r.data.command==='5').length")
 assert before>=2,before;page.wait_for_timeout(450);after=page.evaluate("TEST.requests.filter(r=>r.action==='command'&&r.data.command==='5').length");assert after==before,(before,after)
 passed('physical D-pad repeat is bounded by browser ownership and stops on keyup')
 # pagehide retires only the current session; bfcache pageshow reconnects cleanly.
 current='p13-'+str(page.evaluate('TEST.starts.length'))
 page.evaluate("window.dispatchEvent(new PageTransitionEvent('pagehide',{persisted:true}))")
 page.wait_for_function('TEST.beacons.length===1');assert current in page.evaluate('TEST.beacons[0].body');passed('pagehide retires its own native session with a session-scoped beacon')
 page.evaluate("window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true}))")
 page.wait_for_function('TEST.starts.length===3');assert page.evaluate("TEST.starts[2].unifiedGraphics")=='true';passed('bfcache pageshow opens a fresh generation and preserves explicit unified preference')
 # Network-first shell contains every MiniClient module used by the page.
 sw=(WEB/'sw.js').read_text()
 for name in ['miniclient-caption-authority.js','miniclient-subtitles.js','miniclient-dvd-spu.js']:
  assert name in sw,name
 passed('service-worker shell includes all current MiniClient modules and no longer restores partial old script sets')
 assert not errors,errors;browser.close()
print(f'Chromium P13 GFX/input/lifecycle: {count} tests PASS')
