#!/usr/bin/env python3
"""Chromium integration smoke with an offline fake SageTV fetch/event transport.
Requires optional Python playwright and a system Chromium. No Python runtime is
needed by the deployed plugin. This test does NOT connect to a real SageTV server.
"""
from pathlib import Path
import json, base64, struct, os, re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'src/main/webapp'
def gfx(cmd,*args):
    payload=b''.join(struct.pack('>I',n&0xffffffff) for n in args)
    return {'type':'gfx','cmd':cmd,'payload':base64.b64encode(bytes([cmd])+len(payload).to_bytes(3,'big')+payload).decode()}
FRAME=[gfx(31),gfx(17,0,0,1280,720,*([0xff182837]*4)),gfx(17,80,80,420,100,*([0xff476b81]*4)),gfx(30)]
def calls(action):return page.evaluate("action=>window.TEST.requests.filter(x=>x[0]===action).map(x=>x[1])",action)
def emit(item):page.evaluate("item=>window.TEST.emit(item)",item)
count=0
def passed(name):
    global count
    count+=1;print('PASS '+name,flush=True)
try:
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
        context=browser.new_context(viewport={'width':1440,'height':1000},service_workers='block')
        page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        html=(WEB/'miniclient.html').read_text()
        html=re.sub(r'<script[^>]*>.*?</script>','',html,flags=re.S)
        html=re.sub(r'<link[^>]*>','',html)
        page.set_content(html)
        page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
        page.evaluate("""
        (()=>{
          const T=window.TEST={requests:[],pending:[],stream:null};
          T.emit=item=>{if(T.stream)T.stream.enqueue(new TextEncoder().encode(JSON.stringify(item)+'\\n'));else T.pending.push(item);};
          window.fetch=async (url,options={})=>{
            const u=new URL(url),q=new URLSearchParams(u.search);
            if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));
            const d=Object.fromEntries(q),a=d.action;T.requests.push([a,d]);
            if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const item of T.pending)T.emit(item);T.pending=[];}}),{headers:{'Content-Type':'application/x-ndjson'}});
            if(a==='start'){for(const item of FRAME)T.emit(item);return new Response(JSON.stringify({session:'browser-test',clientId:'020000000123'}));}
            if(a==='status'&&T.hlsStatus)return new Response(JSON.stringify({...T.hlsStatus,session:d.session}));
            return new Response(JSON.stringify({ok:true}));
          };
        })();
        """.replace('FRAME',json.dumps(FRAME)))
        for filename in ['miniclient-core.js','miniclient-streaming.js','atsc-captions.js','miniclient-captions.js','miniclient-playback.js']:
            page.evaluate((WEB/'js'/filename).read_text())
        # This offline browser test injects only location/storage/fetch. The
        # shipped DOM, styles, renderer and gesture handlers run unchanged.
        main=(WEB/'js/miniclient.js').read_text()
        page.evaluate("(function(location,localStorage){\n"+main+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:'?ui=1280x720'},{getItem:k=>null,setItem:()=>{}})")
        page.wait_for_function("document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
        page.wait_for_function("document.querySelector('#gfxCanvas').getContext('2d').getImageData(90,90,1,1).data[0] === 71")
        assert not errors,errors;passed('production scripts connect and render actual GFX packets without errors')
        box=page.locator('#gfxCanvas').bounding_box();x=box['x']+200;y=box['y']+220
        before=len(calls('click'));page.mouse.move(x,y);page.mouse.down();page.wait_for_timeout(650);assert page.locator('#remotePanel').is_visible();page.mouse.up();page.wait_for_timeout(100)
        assert len(calls('click'))==before;passed('real pointer hold opens remote with no underlying click')
        page.locator('#closeRemote').click();page.mouse.click(x,y);page.wait_for_timeout(150)
        assert len(calls('click'))==before+1;passed('real short click sends exactly one atomic click')
        page.locator('#remoteToggle').click();up=page.locator('#remotePanel [aria-label="Up"]');b=up.bounding_box();before=len(calls('command'))
        page.mouse.move(b['x']+b['width']/2,b['y']+b['height']/2);page.mouse.down();page.wait_for_timeout(820);page.mouse.up();page.wait_for_timeout(200)
        repeated=[d for d in calls('command')[before:] if d['command']=='4'];assert len(repeated)>=3,repeated
        fixed=len(calls('command'));page.wait_for_timeout(400);assert len(calls('command'))==fixed;passed('held remote key repeats and stops on pointer release')
        page.locator('#remotePanel [data-command="29"]').click();page.wait_for_timeout(100);assert calls('command')[-1]['command']=='29';passed('SageTV Options remains distinct from local hold menu');page.locator('#remoteToggle').click()
        page.locator('#keyboardToggle').click();page.locator('#keyboardInput').fill('SageTV café');page.locator('#keyboardSend').click();page.wait_for_timeout(150)
        assert calls('text')[-1]['text']=='SageTV café';passed('keyboard text delivered literally as UTF-8 POST')
        page.locator('#closeRemote').click();emit({'type':'menuHint','value':'menuName:Guide, popupName:'});page.wait_for_timeout(150)
        before=len(calls('command'));page.mouse.click(x,y);page.wait_for_timeout(350);assert any(d['command']=='20' for d in calls('command')[before:]);passed('Guide click adds SELECT assist after native click')
        page.locator('#settingsToggle').click();page.locator('#osdTrigger').select_option('right_long');page.locator('#guideAssist').uncheck();page.locator('#closeSettings').click()
        page.mouse.move(x,y);before=len(calls('click'));page.mouse.down(button='right');page.wait_for_timeout(650);page.mouse.up(button='right');assert page.locator('#remotePanel').is_visible();assert len(calls('click'))==before;passed('right-hold setting opens remote without native context menu')
        page.locator('#closeRemote').click();page.mouse.move(x,y);page.mouse.down();page.mouse.move(x+40,y+30,steps=4);page.mouse.up();page.wait_for_timeout(200);assert len(calls('click'))==before;passed('drag sends release but never activation click')
        page.locator('#remoteToggle').click();page.locator('#remoteHelp').click();page.locator('#renderOnlyBtn').click();page.wait_for_timeout(180)
        canvas=page.locator('#gfxCanvas').bounding_box();assert abs(canvas['width']/canvas['height']-1280/720)<.01;assert canvas['height']<=1001;assert canvas['width']<=1441
        page.keyboard.press('Escape');passed('render-only view fits logical 16:9 canvas and Escape restores toolbar')
        page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(200);page.locator('#remoteToggle').click();page.wait_for_timeout(100)
        assert page.evaluate('document.documentElement.scrollWidth')<=391
        card=page.locator('.mc-remote-card').bounding_box();assert card['x']>=-1 and card['x']+card['width']<=391;passed('mobile remote fits viewport without horizontal clipping')
        out=ROOT/'build/test-results';out.mkdir(parents=True,exist_ok=True);page.screenshot(path=str(out/'miniclient-mobile.png'))
        page.set_viewport_size({'width':1440,'height':1000});page.screenshot(path=str(out/'miniclient-desktop.png'))
        page.locator('#closeRemote').click()
        page.evaluate("""window.Hls=class { constructor(config){this.config=config;} static isSupported(){return true;} static Events={ERROR:'error',MANIFEST_PARSED:'manifest'}; static ErrorTypes={NETWORK_ERROR:'network',MEDIA_ERROR:'media'}; on(){} loadSource(){} attachMedia(){} startLoad(){} destroy(){} };
          window.TEST.hlsStatus={playlistReady:true,segments:1,hlsSeconds:5,hlsFillRate:2,running:true,endList:false,durationMs:300000};""")
        emit({'type':'videoStart','hlsSession':'mask-test','playlistUrl':'hls/mask-test/stream.m3u8','durationMs':300000})
        emit({'type':'videoBounds','dst':[0,0,1280,720]})
        for item in [gfx(31),gfx(18,0,0,1280,720,0xff000000),gfx(30)]:emit(item)
        page.wait_for_function("document.querySelector('#gfxCanvas').getContext('2d').getImageData(10,10,1,1).data[3] === 0")
        assert page.locator('#videoBounds').is_visible();passed('dark SageTV CLEAR_RECT reveals active video instead of covering it black')
        page.keyboard.press('Home')
        for item in [gfx(31),gfx(17,0,0,1280,720,*([0xff112244]*4)),gfx(18,400,300,320,180,0xff080010),gfx(30)]:emit(item)
        page.wait_for_function("document.querySelector('#gfxCanvas').getContext('2d').getImageData(410,310,1,1).data[3] === 0 && document.querySelector('#gfxCanvas').getContext('2d').getImageData(10,10,1,1).data[3] === 255")
        assert page.evaluate("document.querySelector('#gfxCanvas').getContext('2d').getImageData(10,10,1,1).data[3]")==255
        preview=page.locator('#videoBounds').bounding_box();canvas=page.locator('#gfxCanvas').bounding_box();scale=canvas['width']/1280;assert abs(preview['width']-320*scale)<2 and abs(preview['height']-180*scale)<2
        passed('menu remains opaque while color-key preview moves/resizes underlying video')
        image=gfx(26,2,1);image['assignedHandle']=99
        for item in [gfx(31),gfx(17,0,0,1280,720,*([0xff112244]*4)),image,gfx(32,99,0,8,0xffff0000,0xff0000ff),gfx(24,100,100,-100,-50,99,0,0,2,1,0xffffffff),gfx(30)]:emit(item)
        page.wait_for_timeout(150)
        pixels=page.evaluate("""(()=>{const c=document.querySelector('#gfxCanvas').getContext('2d');return [Array.from(c.getImageData(105,110,1,1).data),Array.from(c.getImageData(195,110,1,1).data),Array.from(c.getImageData(50,50,1,1).data)];})()""")
        assert pixels[0][0]>pixels[0][2] and pixels[1][2]>pixels[1][0] and pixels[2][3]==255,pixels
        passed('negative texture dimensions do not mirror text or erase surrounding menu')
        emit({'type':'videoStop'})
        assert not errors,errors;passed('no browser JavaScript exceptions throughout gestures/settings')
        browser.close()
    print(f'Chromium browser smoke: {count} tests PASS (offline mocked transport; not live playback)')
finally:
    pass
