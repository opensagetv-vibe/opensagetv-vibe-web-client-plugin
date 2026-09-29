#!/usr/bin/env python3
"""Production browser UI + mocked HTTP for connection-loss and recovery; not live SageTV."""
from pathlib import Path
import json, os, re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'src/main/webapp'
count=0

def passed(name):
    global count
    count+=1
    print('PASS '+name, flush=True)

def fixture(browser, fallback=False, initial_dead=False):
    page=browser.new_page(viewport={'width':1000,'height':650},service_workers='block',accept_downloads=True)
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S)
    html=re.sub(r'<link[^>]*>','',html)
    page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
    page.evaluate(r'''(()=>{
      const T=window.TEST={requests:[],reports:[],stream:null,alive:true,id:0,blob:null};
      const nativeURL=URL.createObjectURL.bind(URL);URL.createObjectURL=blob=>{T.blob=blob;return nativeURL(blob);};
      T.emit=item=>T.stream.enqueue(new TextEncoder().encode(JSON.stringify(item)+'\n'));
      T.end=()=>{T.stream.close();T.stream=null;};
      window.fetch=async(url,options={})=>{
        const q=new URLSearchParams(new URL(url).search);
        if(options.body)new URLSearchParams(options.body).forEach((v,k)=>q.set(k,v));
        const d=Object.fromEntries(q),a=d.action,path=new URL(url).pathname;T.requests.push([a,d,path]);
        if(path.endsWith('/api/support')){if(a==='status')return new Response('{"configured":false,"writesEnabled":false}');if(a==='report'){T.reports.push(d);return new Response(new Blob(['PKfixture'],{type:'application/zip'}),{headers:{'Content-Type':'application/zip'}});}}
        if(a==='start')return new Response(JSON.stringify({session:'recovery-'+(++T.id),clientId:'020000000011',alive:!T.initialDead,error:T.initialDead?'SocketException: Connection reset':''}));
        if(a==='status')return new Response(JSON.stringify({alive:T.alive,error:T.alive?'':'SocketException: Connection reset',connection:{lastGfxCommand:'get property GFX_RESOLUTION',reconnectAttempts:3}}));
        if(a==='events')return new Response('[]');
        if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;}}));
        return new Response('{"ok":true}');
      };
    })();''')
    page.evaluate('(x)=>{TEST.initialDead=x.initialDead;if(x.fallback){TEST.alive=false;window.ReadableStream=undefined;}}',{'fallback':fallback,'initialDead':initial_dead})
    for name in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-dvd-spu.js','miniclient-playback.js','miniclient-support.js']:
        page.evaluate((WEB/'js'/name).read_text())
    main=(WEB/'js/miniclient.js').read_text()
    page.evaluate("(function(location,localStorage){\n"+main+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:'?ui=640x360'},{getItem:k=>k==='sagetvWebMiniClientSettings.v1'?'{\"renderOnly\":true}':null,setItem:()=>{}})")
    return page,errors

def emit(page,item):page.evaluate('x=>TEST.emit(x)',item)
def requests(page,action):return page.evaluate("a=>TEST.requests.filter(x=>x[0]===a && x[2].endsWith('/api/miniclient')).length",action)

with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
    page,errors=fixture(browser)
    page.wait_for_function('TEST.stream!==null')
    assert not page.locator('#toolbar').is_visible()
    emit(page,{'type':'error','fatal':True,'message':'GFX connection ended: SocketException: Connection reset'})
    page.wait_for_function("document.body.classList.contains('mc-connection-lost')")
    assert page.locator('#toolbar').is_visible() and page.locator('#reconnectBtn').is_enabled()
    assert page.locator('#connectionDiagnosticsBtn').is_visible()
    assert 'Connection reset' in page.locator('#osdMessage').inner_text()
    passed('fatal native reset reveals recovery/diagnostic buttons in render-only view')
    before=requests(page,'stream');page.wait_for_timeout(1000)
    assert requests(page,'stream')==before
    passed('terminal native failure stops the empty HTTP reconnect loop')
    page.locator('#connectionDiagnosticsBtn').click()
    page.wait_for_function('TEST.reports.length>0')
    report=json.loads(page.evaluate('TEST.reports.slice(-1)[0].browserJson'))
    assert 'Connection reset' in report['failureSnapshot']['reason']
    assert report['serverConnection']['connection']['lastGfxCommand']=='get property GFX_RESOLUTION'
    passed('one-click diagnostics contains failure snapshot and native connection trace')
    page.screenshot(path=str(ROOT/'docs/connection-recovery-desktop.png'))
    page.locator('#reconnectBtn').click()
    page.wait_for_function('TEST.id===2 && !document.body.classList.contains("mc-connection-lost")')
    assert requests(page,'stop')==1 and requests(page,'start')==2
    assert not errors,errors
    passed('explicit Reconnect stops old session and creates one replacement')
    page.close()

    page,errors=fixture(browser);page.wait_for_function('TEST.stream!==null')
    emit(page,{'type':'status','state':'gfx-reconnecting'})
    page.wait_for_function("document.querySelector('#connectionStatus').textContent.includes('Recovering')")
    emit(page,{'type':'status','state':'gfx-reconnected'})
    page.wait_for_function("document.querySelector('#connectionStatus').textContent.includes('GFX recovered')")
    assert requests(page,'start')==1 and requests(page,'stop')==0
    assert not page.evaluate("document.body.classList.contains('mc-connection-lost')")
    passed('successful native GFX recovery keeps the browser session instead of restarting playback')
    page.close()

    page,errors=fixture(browser);page.wait_for_function('TEST.stream!==null')
    page.evaluate('TEST.alive=false;TEST.end()')
    page.wait_for_function("document.body.classList.contains('mc-connection-lost')")
    assert requests(page,'status')==1 and requests(page,'stream')==1
    passed('HTTP EOF detects dead native session even when terminal event was missed')
    page.close()

    page,errors=fixture(browser);page.wait_for_function('TEST.stream!==null');page.evaluate('TEST.end()')
    page.wait_for_function("TEST.requests.filter(x=>x[0]==='stream').length===2")
    assert requests(page,'start')==1
    passed('transient HTTP EOF reconnects only HTTP while native session remains alive')
    page.close()

    page,errors=fixture(browser,fallback=True)
    page.wait_for_function("document.body.classList.contains('mc-connection-lost')")
    before=requests(page,'events');page.wait_for_timeout(300);assert requests(page,'events')==before
    passed('long-poll fallback also stops when native session is dead')
    page.close()

    page,errors=fixture(browser,initial_dead=True)
    page.wait_for_function("document.body.classList.contains('mc-connection-lost')")
    assert requests(page,'stream')==0 and page.locator('#connectionDiagnosticsBtn').is_visible()
    passed('startup failure returned by start is not shown as a connected session')
    page.set_viewport_size({'width':390,'height':700})
    page.wait_for_timeout(200)
    assert page.locator('#reconnectBtn').is_visible() and page.locator('#connectionDiagnosticsBtn').is_visible()
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    page.screenshot(path=str(ROOT/'docs/connection-recovery-mobile.png'))
    passed('mobile error recovery controls remain visible without horizontal clipping')
    page.close();browser.close()
print(f'Browser recovery: {count} tests PASS (production DOM/JavaScript, mocked HTTP)')
