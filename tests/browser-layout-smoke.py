#!/usr/bin/env python3
"""Window-size/native-GFX regression checks against the shipped DOM/renderer.
Offline mock of the native server, not a SageMC screenshot or playback benchmark.
Uses Python Playwright only as a development test dependency.
"""
from pathlib import Path
import os, re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'src/main/webapp'
count=0

def passed(name):
    global count
    count+=1
    print('PASS '+name,flush=True)

with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':1912,'height':800},service_workers='block')
    errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S)
    html=re.sub(r'<link[^>]*>','',html)
    page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
    page.evaluate(r"""(()=>{
      const T=window.TEST={requests:[],pending:[],stream:null,width:0,height:0,instances:[],draws:0};
      T.gfx=(cmd,...numbers)=>{
        const bytes=new Uint8Array(4+numbers.length*4),v=new DataView(bytes.buffer);
        bytes[0]=cmd;bytes[1]=(numbers.length*4)>>16;bytes[2]=(numbers.length*4)>>8;bytes[3]=numbers.length*4;
        numbers.forEach((n,i)=>v.setUint32(4+i*4,n>>>0));
        return {type:'gfx',cmd,payload:btoa(String.fromCharCode(...bytes))};
      };
      T.emit=item=>{if(T.stream)T.stream.enqueue(new TextEncoder().encode(JSON.stringify(item)+'\n'));else T.pending.push(item);};
      T.draw=(w,h,first)=>{
        T.width=w;T.height=h;T.emit({type:'resize',width:w,height:h});
        if(first){const image=T.gfx(26,2,1);image.assignedHandle=77;T.emit(image);T.emit(T.gfx(32,77,0,8,0xffff00ff,0xffff00ff));}
        const fill=(x,y,w,h,c)=>T.emit(T.gfx(17,x,y,w,h,c,c,c,c));
        T.emit(T.gfx(31));fill(0,0,w,h,0xff16354f);fill(30,30,w/4,h-60,0xff324353);
        fill(0,0,20,20,0xffff0000);fill(w-20,0,20,20,0xff00ff00);
        fill(0,h-20,20,20,0xff0000ff);fill(w-20,h-20,20,20,0xffffff00);
        T.emit(T.gfx(24,w-70,h-50,40,20,77,0,0,2,1,0xffffffff));T.emit(T.gfx(30));T.draws++;
      };
      window.Hls=class {
        static version='fixture';static isSupported(){return true;}
        static Events={ERROR:'error',MANIFEST_PARSED:'manifest',FRAG_BUFFERED:'buffered'};
        static ErrorTypes={NETWORK_ERROR:'network',MEDIA_ERROR:'media'};
        constructor(config){this.config=config;this.handlers={};T.instances.push(this);}
        on(n,fn){this.handlers[n]=fn;}loadSource(){}attachMedia(){}startLoad(){}destroy(){}
      };
      window.fetch=async(url,options={})=>{
        const q=new URLSearchParams(new URL(url).search);
        if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));
        const d=Object.fromEntries(q),a=d.action;T.requests.push([a,d]);
        if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const item of T.pending)T.emit(item);T.pending=[];}}));
        if(a==='start'){T.draw(+d.width,+d.height,true);return new Response(JSON.stringify({session:'layout',clientId:'020000000022'}));}
        if(a==='resize'){T.draw(+d.width,+d.height,false);return new Response('{"ok":true}');}
        if(a==='status')return new Response(JSON.stringify({session:d.session,playlistReady:true,segments:1,hlsSeconds:120,running:true}));
        return new Response('{"ok":true}');
      };
    })();""")
    for name in ['miniclient-core.js','miniclient-streaming.js','atsc-captions.js','miniclient-captions.js','miniclient-playback.js']:
        page.evaluate((WEB/'js'/name).read_text())
    page.evaluate("(function(location,localStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:()=>null,setItem:()=>{}})")
    def settled():
        page.wait_for_function("""(()=>{const c=document.querySelector('#gfxCanvas'),ctx=c.getContext('2d');
          return c.width===TEST.width && c.height===TEST.height && ctx.getImageData(c.width-10,c.height-10,1,1).data[0]===255 && ctx.getImageData(c.width-10,c.height-10,1,1).data[1]===255;})()""")
        page.wait_for_timeout(300)
    def check_geometry(auto=True):
        settled()
        rects=page.evaluate("""(()=>{const c=document.querySelector('#gfxCanvas'),s=document.querySelector('#stage'),r=c.getBoundingClientRect(),sr=s.getBoundingClientRect();return {canvas:r.toJSON(),stage:sr.toJSON(),w:c.width,h:c.height,docWidth:document.documentElement.scrollWidth,docHeight:document.documentElement.scrollHeight};})()""")
        a,b=rects['canvas'],rects['stage']
        assert a['left']>=b['left']-1 and a['top']>=b['top']-1,rects
        assert a['right']<=b['right']+1 and a['bottom']<=b['bottom']+1,rects
        if auto:assert a['width']*a['height']>=b['width']*b['height']*.99,rects
        else:assert abs(a['width']/a['height']-16/9)<.001,rects
        assert rects['docWidth']<=page.viewport_size['width'] and rects['docHeight']<=page.viewport_size['height'],rects
        samples=page.evaluate("""(()=>{const c=document.querySelector('#gfxCanvas'),p=(x,y)=>Array.from(c.getContext('2d').getImageData(x,y,1,1).data);return [p(10,10),p(c.width-10,10),p(10,c.height-10),p(c.width-10,c.height-10),p(c.width-50,c.height-40)];})()""")
        assert samples==[[255,0,0,255],[0,255,0,255],[0,0,255,255],[255,255,0,255],[255,0,255,255]],samples
        return rects
    page.wait_for_function("TEST.width>0")
    rects=check_geometry();assert rects['canvas']['width']>1800 and rects['canvas']['height']>700,rects
    passed('1912x800 window uses full area below toolbar with all four GFX corners visible')
    assert page.evaluate("TEST.requests.find(x=>x[0]==='start')[1].width")=='1912'
    passed('initial server connection receives the actual usable UI dimensions')
    for width,height in [(1280,900),(390,844),(844,390),(2560,1440)]:
        page.set_viewport_size({'width':width,'height':height});page.wait_for_timeout(550)
        rects=check_geometry();assert rects['w']<=1920 and rects['h']<=1080
    passed('desktop, portrait, landscape and large windows resize without clipping or page overflow')
    assert len(page.evaluate("TEST.requests.filter(x=>x[0]==='resize')"))>=4
    passed('window changes send real resize API requests and redraw at the new native size')
    passed('image handle allocated only at connection remains valid after four native resizes')
    page.locator('#settingsToggle').click();page.locator('#uiResolution').select_option('1280x720');page.locator('#closeSettings').click();page.wait_for_timeout(500)
    rects=check_geometry(False);assert (rects['w'],rects['h'])==(1280,720)
    passed('fixed 720p option contains the entire 16:9 UI rather than zooming/cropping')
    page.locator('#settingsToggle').click();page.locator('#uiResolution').select_option('window');page.locator('#closeSettings').click();page.wait_for_timeout(500)
    page.set_viewport_size({'width':1912,'height':800});page.wait_for_timeout(500);rects=check_geometry()
    c=rects['canvas'];x=c['right']-4;y=c['bottom']-4
    page.mouse.click(x,y);page.wait_for_timeout(200)
    d=page.evaluate("TEST.requests.filter(x=>x[0]==='click').at(-1)[1]")
    assert abs(int(d['x'])-(rects['w']-4*rects['w']/c['width']))<2 and abs(int(d['y'])-(rects['h']-4*rects['h']/c['height']))<2,d
    passed('bottom-right pointer clicks map to the new native coordinates')
    page.locator('#remoteToggle').click();page.locator('#remoteHelp').click();page.locator('#renderOnlyBtn').click();page.wait_for_timeout(500);rects=check_geometry()
    assert rects['canvas']['height']>=799
    page.keyboard.press('Escape');page.wait_for_timeout(500);check_geometry()
    passed('render-only view and toolbar restoration renegotiate usable height')
    # Real fullscreen can be entered from the production button in Chromium.
    page.locator('#fullscreenBtn').click();page.wait_for_timeout(500);rects=check_geometry()
    assert page.evaluate('!!document.fullscreenElement')
    page.evaluate('document.exitFullscreen()');page.wait_for_timeout(500);check_geometry()
    passed('fullscreen entry and exit preserve the complete native drawing surface')
    out=ROOT/'build/test-results';out.mkdir(parents=True,exist_ok=True)
    page.screenshot(path=str(out/'miniclient-window-fit.png'))
    # A blocked PNG decode previously held media startup behind the GFX chain.
    page.evaluate("""(()=>{const original=window.createImageBitmap;
      window.createImageBitmap=()=>new Promise(resolve=>{TEST.finishDecode=()=>resolve(document.createElement('canvas'));});
      TEST.emit(TEST.gfx(34,88,4,0));
    })();""")
    page.wait_for_function('!!TEST.finishDecode')
    page.evaluate("TEST.emit({type:'videoStart',hlsSession:'independent',playlistUrl:'hls/independent/stream.m3u8',durationMs:300000})")
    page.wait_for_function('TEST.instances.length===1')
    assert page.evaluate("TEST.instances[0].config.maxBufferLength")==6
    page.evaluate("TEST.emit({type:'media',action:'pause'});TEST.instances[0].handlers.buffered();")
    page.wait_for_timeout(100)
    assert page.evaluate("TEST.instances[0].config.maxBufferLength")==180
    page.evaluate('TEST.finishDecode();TEST.emit({type:"videoStop"});')
    passed('video startup/control and reserve promotion do not wait behind a blocked GFX image decode')
    # Render a large stream batch and check that a macrotask runs before the last
    # frame is drawn. This tests cooperative yielding, not a separate GFX worker.
    page.evaluate("""(()=>{const T=TEST,g=T.gfx;T.draw(T.width,T.height,false);
      T.yieldObserved=null;T.emit(g(31));
      for(let i=0;i<3000;i++)T.emit(g(17,30,30,50,50,0xff123456,0xff123456,0xff123456,0xff123456));
      T.emit(g(17,30,30,50,50,0xfffedcba,0xfffedcba,0xfffedcba,0xfffedcba));T.emit(g(30));
      setTimeout(()=>{T.yieldObserved=document.querySelector('#gfxCanvas').getContext('2d').getImageData(40,40,1,1).data[0]!==254;},0);
    })();""")
    page.wait_for_function("document.querySelector('#gfxCanvas').getContext('2d').getImageData(40,40,1,1).data[0]===254")
    assert page.evaluate('TEST.yieldObserved')
    passed('large GFX batch yields to browser tasks before completion while preserving draw order')
    assert not errors,errors
    passed('no JavaScript exceptions in auto-resize, cache reuse or independent media scheduling')
    browser.close()
print(f'Chromium layout/scheduling: {count} tests PASS (mock native transport, no live SageMC/video)')
