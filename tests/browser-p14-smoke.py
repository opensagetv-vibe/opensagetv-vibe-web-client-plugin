#!/usr/bin/env python3
"""P14 production-DOM diagnostics/profile/accessibility smoke; synthetic transport."""
from pathlib import Path
import json, os, re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'src/main/webapp';count=0
def passed(s):
 global count;count+=1;print('PASS '+s,flush=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':390,'height':640},service_workers='block');errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 html=re.sub(r'<script[^>]*>.*?</script>','',(WEB/'miniclient.html').read_text(),flags=re.S);html=re.sub(r'<link[^>]*>','',html)
 page.set_content(html);page.add_style_tag(content=(WEB/'css/miniclient.css').read_text())
 page.evaluate(r"""(()=>{const T=window.TEST={requests:[],reports:[],pending:[],stream:null,storage:{},session:{},blobs:[]};T.sessionStorage={getItem:k=>T.session[k]||null,setItem:(k,v)=>T.session[k]=String(v),removeItem:k=>delete T.session[k]};
 const realCreate=URL.createObjectURL.bind(URL);URL.createObjectURL=b=>{T.blobs.push(b);return realCreate(b)};HTMLAnchorElement.prototype.click=function(){};
 T.emit=e=>{if(T.stream)T.stream.enqueue(new TextEncoder().encode(JSON.stringify(e)+'\n'));else T.pending.push(e)};
 window.Hls=class{static version='p14-fixture';static isSupported(){return true}static Events={ERROR:'error',MANIFEST_PARSED:'manifest',FRAG_BUFFERED:'frag'};static ErrorTypes={NETWORK_ERROR:'network',MEDIA_ERROR:'media'};constructor(c){this.config=c;this.handlers={}}on(k,v){this.handlers[k]=v}loadSource(){}attachMedia(){}startLoad(){}destroy(){}};
 window.fetch=async(url,o={})=>{const u=new URL(url,location.href),q=new URLSearchParams(u.search);if(o.body)new URLSearchParams(o.body).forEach((v,k)=>q.set(k,v));const d=Object.fromEntries(q),a=d.action;T.requests.push({path:u.pathname,action:a,data:d});const j=x=>new Response(JSON.stringify(x),{headers:{'Content-Type':'application/json'}});
  if(u.pathname.endsWith('/api/support')){if(a==='status')return j({configured:true,writesEnabled:true,destinationName:'diagnostics',spoolFiles:1,spoolBytes:321});if(a==='testDestination')return j({ok:true,stage:'complete',detail:'Bounded write/read/hash/delete succeeded'});if(a==='retrySpool')return j({state:'retried',detail:'Moved 1 spooled report(s)'});if(a==='report'){T.reports.push(d);return new Response(new Blob(['PKfixture'],{type:'application/zip'}),{headers:{'Content-Type':'application/zip','X-SageTV-Report-Save':d.saveToServer==='true'?'external':'not-requested','X-SageTV-Report-Detail':'fixture'}})}}
  if(u.pathname.endsWith('/api/hls'))return j({session:d.session,mediaId:77,transport:'hls',running:true,playlistReady:true,hlsSeconds:12,durationMs:T.hlsDurationMs||9000,segments:4,encoder:'h264_qsv',accelerator:'qsv',hardware:true,hardwareDecode:false,softwareFallback:false,inputMode:'file',playbackEpoch:{sourceGeneration:2,seekGeneration:1,flushGeneration:0},streamSettings:{effective:{video:'h264 qsv',audio:'aac stereo',detail:'hardware encode / software decode'}}});
  if(u.pathname.endsWith('/api/subtitles'))return j({contractVersion:1,cues:[],queue:{pending:2,bytes:64,oldestPtsMs:1000,newestPtsMs:1500,oldestAgeMs:250},teletext:null,dvbBitmap:null,ordinary:null,dvdSpu:null});
  if(u.pathname.endsWith('/api/captions'))return j({cursor:0,packets:[]});
  if(a==='seek'){const v=document.querySelector('#videoPlane'),seq=(T.seekSeq=(T.seekSeq||0)+1),sec=(Number(d.timeMs)||0)/1000;v._time=0;T.emit({type:'videoStart',mediaId:77,hlsSession:'p14-seek-'+seq,playlistUrl:'fixture.m3u8',transport:'hls',startSeconds:sec,durationMs:T.hlsDurationMs||9000,paused:v._paused,seek:true,streamSettings:null});return j({ok:true});}if(a==='start')return j({session:'p14-native',clientId:'020000000014',alive:true});if(a==='stream')return new Response(new ReadableStream({start(c){T.stream=c;for(const e of T.pending)T.emit(e);T.pending=[]}}));if(a==='status')return j({session:'p14-native',alive:true,connection:{startupEvents:{ready:true}}});if(a==='streamSettings')return j({ok:true,active:null});return j({ok:true});};
 const v=document.querySelector('#videoPlane');v._paused=true;v._time=0;Object.defineProperties(v,{currentTime:{get:()=>v._time,set:x=>v._time=Number(x)||0},paused:{get:()=>v._paused},readyState:{get:()=>4},videoWidth:{get:()=>1280},videoHeight:{get:()=>720},buffered:{get:()=>({length:1,start:()=>0,end:()=>8})}});v.getVideoPlaybackQuality=()=>({totalVideoFrames:Math.floor(Date.now()/20)});v.play=()=>{v._paused=false;return Promise.resolve()};v.pause=()=>{v._paused=true};v.load=()=>{};v.removeAttribute=()=>{};
 })();""")
 for fn in ['miniclient-core.js','miniclient-streaming.js','miniclient-caption-authority.js','atsc-captions.js','miniclient-captions.js','miniclient-subtitles.js','miniclient-dvd-spu.js','miniclient-playback.js','miniclient-support.js']:
  page.evaluate((WEB/'js'/fn).read_text())
 page.evaluate("(function(location,localStorage,sessionStorage){\n"+(WEB/'js/miniclient.js').read_text()+"\n})({href:'https://fixture.test/miniclient.html',origin:'https://fixture.test',search:''},{getItem:k=>TEST.storage[k]||null,setItem:(k,v)=>TEST.storage[k]=String(v)},TEST.sessionStorage)")
 page.wait_for_function("TEST.stream && document.querySelector('#connectionStatus').textContent.startsWith('Connected')")
 page.locator('#settingsToggle').click();page.locator('#supportPreferences').evaluate('d=>d.open=true');page.locator('#supportPreferences summary').focus();page.keyboard.press('ArrowDown')
 assert page.evaluate("document.activeElement.id==='autoDiagnosticCapture'")
 passed('settings D-pad ArrowDown advances focus through the nested support controls')
 page.locator('#testDiagnosticDestination').scroll_into_view_if_needed();page.locator('#testDiagnosticDestination').click();page.wait_for_function("TEST.requests.some(r=>r.action==='testDestination')")
 req=page.evaluate("TEST.requests.filter(r=>r.action==='testDestination').slice(-1)[0]");assert req['data']['confirm']=='true' and req['data'].get('operationId')
 passed('server destination test is explicit/confirmed and reports bounded write-read-hash-delete success')
 page.locator('#retryDiagnosticSpool').click();page.wait_for_function("TEST.requests.some(r=>r.action==='retrySpool')")
 req=page.evaluate("TEST.requests.filter(r=>r.action==='retrySpool').slice(-1)[0]");assert req['data']['confirm']=='true' and req['data'].get('operationId')
 passed('bounded retry spool is an explicit separate action')
 page.locator('#exportProfile').click();page.wait_for_function("TEST.blobs.some(b=>b.type==='application/json')")
 profile=json.loads(page.evaluate("TEST.blobs.filter(b=>b.type==='application/json').slice(-1)[0].text()"));assert profile['profileScope']=='browser-saved-defaults' and 'administrator-server-settings' in profile['excludedScopes']
 passed('profile export is schema-scoped and excludes administrator/server identity state')
 # Narrow/tall accessibility: scrolling reaches the support/status controls and fixed header remains usable.
 card=page.locator('.mc-settings-card');box=card.bounding_box();assert box['x']>=0 and box['x']+box['width']<=390.5 and card.evaluate('(e)=>e.scrollHeight>=e.clientHeight')
 page.locator('#closeSettings').click();passed('support settings remain reachable in a narrow browser without horizontal truncation')
 # Start a short recording; the real bounded test must skip mutation, retain state and clear recovery marker.
 page.evaluate("TEST.emit({type:'videoStart',mediaId:77,hlsSession:'p14-video',playlistUrl:'fixture.m3u8',transport:'hls',startSeconds:0,durationMs:9000,paused:true,streamSettings:null})")
 page.wait_for_function("!document.querySelector('#videoBounds').hidden")
 page.locator('#remoteToggle').click();page.locator('#testVideoBtn').click();page.wait_for_function("document.querySelector('#videoInfoText').textContent.includes('mutation-safety')",timeout=5000)
 result=json.loads(page.locator('#videoInfoText').text_content());assert result['result']=='PASS' and result['steps'][-1]['skipped'] and result['afterRestore']['paused'] is True
 assert page.evaluate("TEST.sessionStorage.getItem('sagetvWebVideoTestRecovery.v1')") is None
 passed('Test Current Video safely skips short/unsafe mutation and restores pause/audio test state')
 # A normal long recording exercises pause/resume + reversible seek, and must return to its original state.
 page.locator('#closeVideoInfo').click()
 page.evaluate("TEST.hlsDurationMs=120000; const v=document.querySelector('#videoPlane'); v._paused=false; v._time=10; TEST.emit({type:'videoStart',mediaId:77,hlsSession:'p14-long',playlistUrl:'fixture.m3u8',transport:'hls',startSeconds:0,durationMs:120000,paused:false,streamSettings:null})")
 page.wait_for_function("TEST.requests.some(r=>r.path.endsWith('/api/hls')&&r.data.session==='p14-long')",timeout=5000)
 page.wait_for_timeout(100);page.evaluate("document.querySelector('#videoPlane')._paused=false")
 page.locator('#testVideoBtn').click();page.wait_for_function("document.querySelector('#videoInfoText').textContent.includes('reversible-seek')",timeout=7000)
 long_result=json.loads(page.locator('#videoInfoText').text_content());steps={x['name']:x for x in long_result['steps']};assert long_result['result']=='PASS' and steps['pause']['ok'] and steps['resume']['ok'] and steps['reversible-seek']['ok']
 assert long_result['afterRestore']['paused'] is False and abs(long_result['afterRestore']['timeMs']-10000)<1000 and page.evaluate("TEST.sessionStorage.getItem('sagetvWebVideoTestRecovery.v1')") is None
 passed('Test Current Video exercises cadence/pause/resume/reversible seek and restores the original long-recording state')
 page.locator('#closeVideoInfo').click();before=page.evaluate('TEST.reports.length');page.locator('#diagnosticsBtn').click();page.wait_for_function('(n)=>TEST.reports.length>n',arg=before)
 report_req=page.evaluate('TEST.reports.slice(-1)[0]');snap=json.loads(report_req['browserJson'])
 assert snap['version']=='3.2.30' and 'clientId' not in snap and snap['diagnostics']['effectiveTranscode']['encoder']=='h264_qsv'
 assert snap['diagnostics']['timestampEpoch']['sourceGeneration']==2 and report_req['saveToServer']=='false'
 assert 'smb://' not in json.dumps(report_req).lower() and 'password' not in json.dumps(report_req).lower()
 passed('manual ZIP report carries effective transcode/timestamp counters without client identity, SMB URL or credentials')
 # A browser/page interruption leaves a recovery marker. Same-media startup may restore it; different media must never receive the seek.
 marker={'mediaId':77,'timeMs':2500,'intentPaused':True,'muted':True,'volume':0.4,'playbackRate':1,'nativeSession':'retired','hlsSession':'retired'}
 page.evaluate('(m)=>TEST.sessionStorage.setItem("sagetvWebVideoTestRecovery.v1",JSON.stringify(m))',marker);before_seek=page.evaluate("TEST.requests.filter(r=>r.action==='seek').length")
 page.evaluate("TEST.emit({type:'videoStart',mediaId:77,hlsSession:'p14-recovery',playlistUrl:'fixture.m3u8',transport:'hls',startSeconds:0,durationMs:9000,paused:false,streamSettings:null})")
 page.wait_for_function("(n)=>TEST.requests.filter(r=>r.action==='seek').length>n",arg=before_seek,timeout=5000);seek=page.evaluate("TEST.requests.filter(r=>r.action==='seek').slice(-1)[0]");assert seek['data']['timeMs']=='2500' and seek['data']['expectedHlsSession']=='p14-recovery'
 page.wait_for_function("!TEST.sessionStorage.getItem('sagetvWebVideoTestRecovery.v1')",timeout=5000);passed('interrupted video-test state is recoverable only onto the matching recording')
 marker['mediaId']=999;page.evaluate('(m)=>TEST.sessionStorage.setItem("sagetvWebVideoTestRecovery.v1",JSON.stringify(m))',marker);before_seek=page.evaluate("TEST.requests.filter(r=>r.action==='seek').length")
 page.evaluate("TEST.emit({type:'videoStart',mediaId:77,hlsSession:'p14-different-media',playlistUrl:'fixture.m3u8',transport:'hls',startSeconds:0,durationMs:9000,paused:true,streamSettings:null})");page.wait_for_function("!TEST.sessionStorage.getItem('sagetvWebVideoTestRecovery.v1')",timeout=3000);assert page.evaluate("TEST.requests.filter(r=>r.action==='seek').length")==before_seek
 passed('stale interrupted-test restoration clears safely without seeking a different recording')
 # Optional terminal-failure capture defaults off; after opt-in it uses the configured server destination.
 page.locator('#settingsToggle').click();page.locator('#supportPreferences').evaluate('d=>d.open=true');assert not page.locator('#autoDiagnosticCapture').is_checked();page.locator('#autoDiagnosticCapture').check();page.locator('#closeSettings').click()
 before=page.evaluate('TEST.reports.length');page.evaluate("TEST.emit({type:'error',fatal:true,message:'fixture terminal failure'})");page.wait_for_function('(n)=>TEST.reports.length>n',arg=before)
 auto=page.evaluate('TEST.reports.slice(-1)[0]');assert auto['saveToServer']=='true' and json.loads(auto['browserJson'])['failureSnapshot']['reason']=='fixture terminal failure'
 passed('automatic failure capture is default-off, opt-in, and preserves the terminal failure snapshot')
 assert not errors,errors;browser.close()
print(f'Chromium P14 diagnostics/profiles/accessibility: {count} tests PASS')
