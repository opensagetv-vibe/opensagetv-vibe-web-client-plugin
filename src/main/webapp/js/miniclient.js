(function(){
'use strict';
const $=id=>document.getElementById(id), Core=window.SageMiniCore;
const stage=$('stage'),visible=$('gfxCanvas'),visibleCtx=visible.getContext('2d',{alpha:true});
const video=$('videoPlane'),videoBounds=$('videoBounds'),dvdStill=$('dvdStillFrame'),dvdStillCtx=dvdStill.getContext('2d'),dvbCanvas=$('dvbCaptions'),dvbCtx=dvbCanvas.getContext('2d',{alpha:true}),fileSubCanvas=$('fileSubtitleCanvas'),fileSubCtx=fileSubCanvas.getContext('2d',{alpha:true}),dvdSpuCanvas=$('dvdSpuCanvas'),dvdSpuCtx=dvdSpuCanvas.getContext('2d',{alpha:true}),statusEl=$('connectionStatus'),msgEl=$('osdMessage'),statsEl=$('stats');
const dvdFrameCache=document.createElement('canvas'),dvdFrameCacheCtx=dvdFrameCache.getContext('2d');
const remotePanel=$('remotePanel'),settingsPanel=$('settingsPanel');
let videoTestGeneration=0,dvdFrameGeneration=0,dvdFrozenGeneration=0,dvdFrameCacheValid=false,dvdFrameSamplerSerial=0,dvdFrameCaptures=0,dvdStillPromotions=0,dvdStillMisses=0;
const SETTINGS_KEY='sagetvWebMiniClientSettings.v1';
const Streaming=window.SageMiniStreaming,Support=window.SageMiniSupport||Object.freeze({redact:v=>v,buildProfile:(a,b,c)=>({kind:'sagetv-webplayer-profile',schemaVersion:1,savedDefaults:{client:a,streaming:b,captions:c}}),previewProfile:v=>v,captionSummary:()=>({}),effectiveTranscode:()=>({})}),CaptionAuthority=window.SageMiniCaptionAuthority||Object.freeze({
  normalize:()=>({mode:'off',cc1Type:'auto',cc1Language:'',cc1Service:1,cc2Type:'auto',cc2Language:'',cc2Service:2}),
  effective:()=>({mode:'off',local:'off',label:'Off (caption authority module unavailable)'})
}),STREAMING_KEY='sagetvWebStreaming.v1',CAPTION_AUTHORITY_KEY='sagetvWebCaptionAuthority.v1';
let streaming=loadStreaming(),captionAuthority=loadCaptionAuthority(),captionInventory=[],captionAutoResolveBusy=false,mpegtsScriptPromise=null;
let sageTvCaptionStateSeen=false,sageTvCaptionState=0;
let lastVideoTest=null,pendingProfileImport=null,diagnosticStatus=null,lastServerConnectionStatus=null;
let diagnosticOperationId=null;const VIDEO_TEST_RECOVERY_KEY='sagetvWebVideoTestRecovery.v1';
function loadCaptionAuthority(){try{return CaptionAuthority.normalize(JSON.parse(localStorage.getItem(CAPTION_AUTHORITY_KEY)||'{}'));}catch(_){return CaptionAuthority.normalize({});}}
function saveCaptionAuthority(){captionAuthority=CaptionAuthority.normalize(captionAuthority);try{localStorage.setItem(CAPTION_AUTHORITY_KEY,JSON.stringify(captionAuthority));}catch(_){}}
function loadStreaming(){try{const v=Object.assign({},JSON.parse(localStorage.getItem(STREAMING_KEY)||'{}'),{audioTrack:-1,subtitleTrack:-1});if(v.subtitleMode==='track')v.subtitleMode='auto';return Streaming.normalize(v);}catch(_){return Streaming.normalize({});}}
let settings=loadSettings(),session='',stopped=false,connectionGeneration=0,rendererGeneration=0;
let failureSnapshot=null,nativeRecoveryCount=0;
let eventController=null,eventChain=Promise.resolve(),inputChain=Promise.resolve(),inputPending=0,stateBusy=false;
let lastRequestedSize='',resizeInFlight=false,resizeAgain=false;
let renderQueued=0,renderQueuedBytes=0;
let resizeTimer=null,stateTimer=null,hlsScriptPromise=null,currentMenuHint='',menuEpoch=0,guideTimer=null;
let uiW=1280,uiH=720,currentTarget=0,mainSurface=makeCanvas(uiW,uiH);
let displayRect={left:0,top:0,width:1280,height:720},currentVideoBounds=[0,0,1280,720];
let nativeVideoBounds=[0,0,1280,720],nativeVideoSource=null,videoBoundsOrigin='initial',serverVideoAspect='source';
let videoPresentation=Core.videoPresentation(nativeVideoBounds,1280,720,'browser'),videoLayerRect=null;
let frame=0,gfxCount=0,imageCount=0,lastEventAt=0,lastError='',videoAspect=settings.videoFit;
let videoBoundsTrusted=false,lastTrustedVideoBounds=null,videoPresentationSuspended=false,frameHadClearRect=false;
let seeking=false,lastMove=0,lastBackAt=0,lastClick=null,repeatTimer=null,repeatInterval=null,pageRetired=false;
let unsupportedGfxCount=0,lastUnsupportedGfx=-1;
const repeatConsumed=new WeakMap(),surfaces=new Map([[0,mainSurface]]),images=new Map(),logs=[];
const COLORKEY=[8,0,16];
function loadSettings(){try{
  const saved=JSON.parse(localStorage.getItem(SETTINGS_KEY)||'{}');
  // One-time migration of the old implicit Source default for the requested
  // edge-to-edge policy. Future explicit Fit choices are never overwritten.
  if(saved.displayPolicyVersion!==2 && (!saved.videoFit||saved.videoFit==='source'))saved.videoFit='cover';
  const normalized=Core.normalizeSettings(saved);
  try{localStorage.setItem(SETTINGS_KEY,JSON.stringify(normalized));}catch(_){}
  return normalized;
}catch(_){return Core.normalizeSettings({});}}
function log(message){logs.push({at:new Date().toISOString(),message:String(message)});if(logs.length>200)logs.shift();}
function setError(e){lastError=e&&e.message?e.message:String(e);log(lastError);}
function saveSettings(){settings=Core.normalizeSettings(settings);try{localStorage.setItem(SETTINGS_KEY,JSON.stringify(settings));}catch(_){}applySettings();}
async function requestJson(path,action,params,method,timeoutMs){
  const u=new URL(path,location.href),body=new URLSearchParams();u.searchParams.set('action',action);
  Object.keys(params||{}).forEach(k=>{if(params[k]!==undefined&&params[k]!==null)body.set(k,String(params[k]));});
  const options={method:method||'GET',cache:'no-store'},controller=new AbortController();options.signal=controller.signal;
  if(options.method==='GET')body.forEach((v,k)=>u.searchParams.set(k,v));else{options.body=body;options.headers={'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'};}
  const timer=setTimeout(()=>controller.abort(),timeoutMs||15000);
  try{const r=await fetch(u.href,options);const t=await r.text();let j={};try{j=t?JSON.parse(t):{};}catch(_){j={error:t.slice(0,500)};}if(!r.ok){const error=new Error(j.error||('HTTP '+r.status));error.httpStatus=r.status;throw error;}return j;}
  finally{clearTimeout(timer);}
}
function api(action,params,method){return requestJson('api/miniclient',action,params,method,['recover','seek','start','fallback','streamSettings'].includes(action)?60000:15000);}
function hlsApi(action,params){return requestJson('api/hls',action,params,'GET',5000);}
function loadHlsJs(){
  if(window.Hls)return Promise.resolve(window.Hls);if(hlsScriptPromise)return hlsScriptPromise;
  hlsScriptPromise=new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src='vendor/hlsjs/hls.min.js';
    const timer=setTimeout(()=>{script.remove();hlsScriptPromise=null;reject(new Error('Timed out loading local hls.js vendor asset'));},15000);
    script.onload=()=>{clearTimeout(timer);if(window.Hls)resolve(window.Hls);else{hlsScriptPromise=null;reject(new Error('hls.js is unavailable'));}};
    script.onerror=()=>{clearTimeout(timer);hlsScriptPromise=null;reject(new Error('Could not load hls.js vendor asset'));};document.head.appendChild(script);
  });return hlsScriptPromise;
}
function loadMpegtsJs(){
  if(window.mpegts)return Promise.resolve(window.mpegts);if(mpegtsScriptPromise)return mpegtsScriptPromise;
  mpegtsScriptPromise=new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src='vendor/mpegts-1.8.0/mpegts.min.js';
    const timer=setTimeout(()=>{script.remove();mpegtsScriptPromise=null;reject(new Error('Timed out loading cached mpegts.js 1.8.0; prefetch or pre-seed the vendor asset'));},60000);
    script.onload=()=>{clearTimeout(timer);if(window.mpegts)resolve(window.mpegts);else{mpegtsScriptPromise=null;reject(new Error('mpegts.js did not load'));}};
    script.onerror=()=>{clearTimeout(timer);mpegtsScriptPromise=null;reject(new Error('Unable to load the mpegts.js vendor asset'));};document.head.appendChild(script);
  });return mpegtsScriptPromise;
}
const captions=new window.SageMiniCaptions({
  Decoder:window.SageAtscCaptions,timeMs:()=>Math.round((Number(video.currentTime)||0)*1000),
  fetch:params=>requestJson('api/captions','read',params,'GET',5000),
  subtitleFetch:params=>requestJson('api/subtitles','read',params,'GET',5000),
  render:renderLocalCaptions,renderBitmap:renderDvbBitmap,clearBitmap:clearDvbBitmap,error:s=>log('Caption side channel: '+s)
});
const OrdinarySubtitleController=window.SageMiniSubtitles||class{constructor(){this.mode='off';}start(){}stop(){}snapshot(){return{mode:'off',error:'ordinary subtitle module unavailable'};}};
const ordinarySubtitles=new OrdinarySubtitleController({
  timeMs:()=>playback?playback.absoluteTime():Math.round((Number(video.currentTime)||0)*1000),
  fetch:params=>requestJson('api/subtitles','read',params,'GET',5000),renderText:renderFileSubtitles,renderBitmap:renderFileSubtitleBitmap,clearBitmap:clearFileSubtitleBitmap,error:s=>log('Ordinary subtitles: '+s)
});
const DvdSpuController=window.SageMiniDvdSpu||class{start(){}stop(){}snapshot(){return{active:false,error:'DVD SPU module unavailable'};}};
const dvdSpu=new DvdSpuController({timeMs:()=>Math.round((Number(video.currentTime)||0)*1000),fetch:params=>requestJson('api/subtitles','read',params,'GET',5000),clear:()=>dvdSpuCtx.clearRect(0,0,dvdSpuCanvas.width,dvdSpuCanvas.height),render:(cue,rgba)=>renderDvdSpu(cue,rgba)});


function hideDvdStill(){dvdStill.hidden=true;dvdStillCtx.clearRect(0,0,dvdStill.width,dvdStill.height);}
function resetDvdFrameCache(clearVisible){dvdFrameSamplerSerial++;dvdFrameGeneration=0;dvdFrozenGeneration=0;dvdFrameCacheValid=false;dvdFrameCache.width=dvdFrameCache.height=1;if(clearVisible)hideDvdStill();}
function cacheDvdVideoFrame(generation,releasePriorStill){
  if(!playback||!playback.dvd||Number(playback.dvdGeneration)!==Number(generation)||!(video.videoWidth>0&&video.videoHeight>0)||video.readyState<2)return false;
  if(releasePriorStill&&dvdFrozenGeneration===Number(generation))return false;
  const w=Math.max(1,video.videoWidth|0),h=Math.max(1,video.videoHeight|0);
  if(dvdFrameCache.width!==w||dvdFrameCache.height!==h){dvdFrameCache.width=w;dvdFrameCache.height=h;}
  try{
    dvdFrameCacheCtx.drawImage(video,0,0,w,h);dvdFrameGeneration=Number(generation)||0;dvdFrameCacheValid=true;dvdFrameCaptures++;
    // Keep the previous menu background over a replacement cell until a real
    // decoded frame from the new generation has actually been copied. Merely
    // receiving loadeddata/playing is too early for one-picture DVD menu cells.
    if(releasePriorStill&&!dvdStill.hidden)hideDvdStill();
    return true;
  }catch(e){log('DVD frame cache: '+e);return false;}
}
function beginDvdFrameCache(generation){
  const gen=Number(generation)||0,serial=++dvdFrameSamplerSerial;dvdFrameGeneration=gen;dvdFrozenGeneration=0;dvdFrameCacheValid=false;dvdFrameCache.width=dvdFrameCache.height=1;
  const sample=()=>{
    if(serial!==dvdFrameSamplerSerial||!playback||!playback.dvd||Number(playback.dvdGeneration)!==gen)return;
    cacheDvdVideoFrame(gen,true);
    if(typeof video.requestVideoFrameCallback==='function')setTimeout(()=>{if(serial===dvdFrameSamplerSerial)video.requestVideoFrameCallback(sample);},180);
    else setTimeout(sample,180);
  };
  if(typeof video.requestVideoFrameCallback==='function')video.requestVideoFrameCallback(sample);else setTimeout(sample,25);
}
function promoteDvdStill(generation){
  const gen=Number(generation)||Number(playback&&playback.dvdGeneration)||0;
  // Stop the old-generation sampler before the player tears down this tiny HLS
  // cell. Otherwise an ended/black frame can overwrite the authored menu frame.
  dvdFrameSamplerSerial++;
  if((!dvdFrameCacheValid||dvdFrameGeneration!==gen)&&playback&&playback.dvd&&Number(playback.dvdGeneration)===gen)cacheDvdVideoFrame(gen,false);
  dvdFrozenGeneration=gen;
  if(!dvdFrameCacheValid||dvdFrameGeneration!==gen){dvdStillMisses++;log('DVD still promotion missed decoded frame for generation '+gen+'; retaining prior menu frame');return !dvdStill.hidden;}
  const W=Math.max(1,videoBounds.clientWidth||dvdFrameCache.width||720),H=Math.max(1,videoBounds.clientHeight||dvdFrameCache.height||480);
  dvdStill.width=W;dvdStill.height=H;dvdStillCtx.fillStyle='#000';dvdStillCtx.fillRect(0,0,W,H);
  const r=Core.mediaContentRect(W,H,dvdFrameCache.width,dvdFrameCache.height,videoAspect);
  try{dvdStillCtx.drawImage(dvdFrameCache,r.left,r.top,r.width,r.height);dvdStill.hidden=false;dvdStillPromotions++;return true;}catch(e){dvdStillMisses++;log('DVD still promotion: '+e);return false;}
}
video.addEventListener('loadeddata',()=>{if(playback&&playback.dvd)cacheDvdVideoFrame(playback.dvdGeneration,true);});
video.addEventListener('playing',()=>{if(playback&&playback.dvd)cacheDvdVideoFrame(playback.dvdGeneration,true);});
video.addEventListener('timeupdate',()=>{if(playback&&playback.dvd)cacheDvdVideoFrame(playback.dvdGeneration,true);});
video.addEventListener('ended',()=>{if(playback&&playback.dvd)cacheDvdVideoFrame(playback.dvdGeneration,false);});

function renderDvdSpu(cue,rgba){const c=cue&&cue.canvas||{},r=cue&&cue.rect||[],cw=Number(c.width)|0,ch=Number(c.height)|0,w=Number(r[2])|0,h=Number(r[3])|0;if(cw<1||ch<1||w<1||h<1||rgba.length!==w*h*4)return;if(dvdSpuCanvas.width!==cw||dvdSpuCanvas.height!==ch){dvdSpuCanvas.width=cw;dvdSpuCanvas.height=ch;}try{dvdSpuCtx.putImageData(new ImageData(new Uint8ClampedArray(rgba),w,h),Number(r[0])|0,Number(r[1])|0);}catch(e){setError(e);}syncDvbCanvasLayout();}
function clearDvbBitmap(){dvbCtx.clearRect(0,0,dvbCanvas.width,dvbCanvas.height);}
function clearFileSubtitleBitmap(){fileSubCtx.clearRect(0,0,fileSubCanvas.width,fileSubCanvas.height);}
function renderFileSubtitleBitmap(cue,rgba){const canvas=cue&&cue.canvas||{},rect=cue&&cue.rect||[],cw=Number(canvas.width)|0,ch=Number(canvas.height)|0,w=Number(rect[2])|0,h=Number(rect[3])|0;if(cw<1||ch<1||w<1||h<1||rgba.length!==w*h*4)return;if(fileSubCanvas.width!==cw||fileSubCanvas.height!==ch){fileSubCanvas.width=cw;fileSubCanvas.height=ch;}try{fileSubCtx.putImageData(new ImageData(new Uint8ClampedArray(rgba),w,h),Number(rect[0])|0,Number(rect[1])|0);}catch(e){setError(e);}syncDvbCanvasLayout();}
function renderFileSubtitles(cues){const root=$('fileSubtitles');root.replaceChildren();for(const cue of cues||[]){const d=document.createElement('div');d.className='file-subtitle-cue';d.textContent=cue.text;const line=Number.isFinite(cue.line)&&cue.line>=0?Math.max(0,Math.min(100,cue.line)):94,pos=Number.isFinite(cue.position)?Math.max(0,Math.min(100,cue.position)):50;d.style.left=pos+'%';d.style.top=line+'%';d.style.textAlign=['start','left'].includes(cue.align)?'left':['end','right'].includes(cue.align)?'right':'center';root.appendChild(d);}}
function renderDvbBitmap(cue,rgba){
  const canvas=cue&&cue.canvas||{},rect=cue&&cue.rect||[];const cw=Number(canvas.width)|0,ch=Number(canvas.height)|0,w=Number(rect[2])|0,h=Number(rect[3])|0;
  if(cw<1||ch<1||w<1||h<1||rgba.length!==w*h*4)return;
  if(dvbCanvas.width!==cw||dvbCanvas.height!==ch){dvbCanvas.width=cw;dvbCanvas.height=ch;}
  try{dvbCtx.putImageData(new ImageData(new Uint8ClampedArray(rgba),w,h),Number(rect[0])|0,Number(rect[1])|0);}catch(e){setError(e);}
  syncDvbCanvasLayout();
}
function syncDvbCanvasLayout(){
  const W=videoBounds.clientWidth||0,H=videoBounds.clientHeight||0;if(!(W>0&&H>0))return;
  const fallbackW=dvbCanvas.width||W,fallbackH=dvbCanvas.height||H,r=Core.mediaContentRect(W,H,video.videoWidth||fallbackW,video.videoHeight||fallbackH,videoAspect);
  for(const c of [dvbCanvas,fileSubCanvas,dvdSpuCanvas]){c.style.left=r.left+'px';c.style.top=r.top+'px';c.style.width=r.width+'px';c.style.height=r.height+'px';}const fs=$('fileSubtitles');fs.style.left=r.left+'px';fs.style.top=r.top+'px';fs.style.width=r.width+'px';fs.style.height=r.height+'px';fs.style.right='auto';fs.style.bottom='auto';
}
function renderLocalCaptions(text,service,meta){
  const root=$('localCaptions');root.replaceChildren();if(!text)return;
  if(meta&&meta.type==='708'&&Array.isArray(meta.windows)&&meta.windows.length){
    for(const w of meta.windows){const d=document.createElement('div');d.className='caption-708-window';d.textContent=w.text;
      d.style.left=Math.max(0,Math.min(100,Number(w.left)||0))+'%';d.style.top=Math.max(0,Math.min(100,Number(w.top)||0))+'%';
      d.style.color=w.fg||'#fff';d.style.background=w.bg||'rgba(0,0,0,.8)';if(w.italic)d.style.fontStyle='italic';if(w.underline)d.style.textDecoration='underline';
      const anchor=Number(w.anchorId)||0;d.style.transform='translate('+[0,-50,-100][anchor%3]+'%,'+[0,-50,-100][Math.floor(anchor/3)%3]+'%)';root.appendChild(d);
    }
  }else{const d=document.createElement('div');d.className='caption-608';d.textContent=text;root.appendChild(d);}
}
const playback=new window.SageMiniPlayback({
  video,settings:()=>settings,status:id=>hlsApi('status',{session:id}),loadHls:loadHlsJs,loadMpegts:loadMpegtsJs,
  fallback:params=>api('fallback',Object.assign({session},params),'POST'),
  nativePreferred:!!video.canPlayType('application/vnd.apple.mpegurl')&&/iP(hone|ad|od)|Macintosh/.test(navigator.userAgent)&&/Safari/.test(navigator.userAgent)&&!/Chrome|Chromium|Edg/.test(navigator.userAgent),
  resolveUrl:path=>{const u=new URL(path,location.href);if(u.origin!==location.origin)throw new Error('Cross-origin HLS URL refused');return u.href;},
  recover:params=>api('recover',Object.assign({session},params),'POST'),message,error:setError,
  mediaUpdate:()=>{if(session)api('mediaUpdate',{session},'POST').catch(setError);}
});
function makeCanvas(w,h){const c=document.createElement('canvas');c.width=Math.max(1,w|0);c.height=Math.max(1,h|0);return c;}
function message(s){msgEl.textContent=s||'';}
function setStatus(s){statusEl.textContent=s||'';}
function int32(b,o){return ((b[o]&255)<<24)|((b[o+1]&255)<<16)|((b[o+2]&255)<<8)|(b[o+3]&255);}
function u32(b,o){return int32(b,o)>>>0;}
function base64Bytes(s){const raw=atob(s||'');const b=new Uint8Array(raw.length);for(let i=0;i<raw.length;i++)b[i]=raw.charCodeAt(i);return b;}
function argb(v){v=v>>>0;return {a:(v>>>24)&255,r:(v>>>16)&255,g:(v>>>8)&255,b:v&255};}
function cssArgb(v){const c=argb(v);return `rgba(${c.r},${c.g},${c.b},${(c.a/255).toFixed(5)})`;}
function isColorKey(v){const c=argb(v);return c.r===COLORKEY[0]&&c.g===COLORKEY[1]&&c.b===COLORKEY[2];}
function targetCanvas(){return surfaces.get(currentTarget)||mainSurface;}
function targetCtx(){return targetCanvas().getContext('2d',{alpha:true});}
function argOffset(n){return 4+n;}
function getI(b,n){return int32(b,argOffset(n));}
function currentScalePoint(ev){return Core.canvasPoint(ev.clientX,ev.clientY,visible.getBoundingClientRect(),uiW,uiH);}

function layoutStage(){
  const sr=stage.getBoundingClientRect();
  displayRect=Core.stageRect(sr.width,sr.height,uiW,uiH,settings.uiResolution==='window'&&!new URLSearchParams(location.search).has('ui'));
  const d=displayRect;
  visible.style.left=d.left+'px';visible.style.top=d.top+'px';
  visible.style.width=d.width+'px';visible.style.height=d.height+'px';
  applyVideoBounds();
}
function resetRenderer(w,h){
  rendererGeneration++;
  uiW=Math.max(1,w|0);uiH=Math.max(1,h|0);
  visible.width=uiW;visible.height=uiH;document.documentElement.style.setProperty('--ui-aspect',String(uiW/uiH));
  mainSurface=makeCanvas(uiW,uiH);surfaces.clear();surfaces.set(0,mainSurface);images.clear();currentTarget=0;
  visibleCtx.clearRect(0,0,uiW,uiH);currentVideoBounds=[0,0,uiW,uiH];nativeVideoBounds=currentVideoBounds.slice();nativeVideoSource=null;videoBoundsOrigin='initial';videoBoundsTrusted=false;lastTrustedVideoBounds=null;videoPresentationSuspended=false;frame=0;gfxCount=0;imageCount=0;layoutStage();
}
function startFrame(){currentTarget=0;frameHadClearRect=false;const c=mainSurface.getContext('2d',{alpha:true});c.save();c.setTransform(1,0,0,1,0,0);c.globalCompositeOperation='copy';c.clearRect(0,0,uiW,uiH);c.restore();}
function flip(){if(playback.id&&!frameHadClearRect)videoPresentationSuspended=true;visibleCtx.save();visibleCtx.setTransform(1,0,0,1,0,0);visibleCtx.globalCompositeOperation='copy';visibleCtx.clearRect(0,0,uiW,uiH);visibleCtx.drawImage(mainSurface,0,0);visibleCtx.restore();frame++;if(frame===1&&!playback.id)message('');}
function clip(ctx,x,y,w,h,fn){ctx.save();if(w>0&&h>0){ctx.beginPath();ctx.rect(x,y,w,h);ctx.clip();}try{fn();}finally{ctx.restore();}}
function fillStyle(ctx,x,y,w,h,tl,tr,br,bl){
  if(tl===tr&&tl===br&&tl===bl)return cssArgb(tl);
  let g;
  if(tl===tr&&bl===br){g=ctx.createLinearGradient(x,y,x,y+h);g.addColorStop(0,cssArgb(tl));g.addColorStop(1,cssArgb(bl));return g;}
  if(tl===bl&&tr===br){g=ctx.createLinearGradient(x,y,x+w,y);g.addColorStop(0,cssArgb(tl));g.addColorStop(1,cssArgb(tr));return g;}
  g=ctx.createLinearGradient(x,y,x+w,y+h);g.addColorStop(0,cssArgb(tl));g.addColorStop(.5,cssArgb(tr));g.addColorStop(1,cssArgb(br));return g;
}
function roundedPath(ctx,x,y,w,h,r){r=Math.max(0,Math.min(Math.abs(w)/2,Math.abs(h)/2,r||0));ctx.beginPath();ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r);ctx.closePath();}
function clearOrFill(ctx,x,y,w,h,color){if(clearVideoMask(ctx,[x,y,w,h],color,true))return;if(isColorKey(color)||((color>>>24)&255)===0){ctx.clearRect(x,y,w,h);}else{ctx.fillStyle=cssArgb(color);ctx.fillRect(x,y,w,h);}}

async function decodeCompressed(bytes){
  const blob=new Blob([bytes]);
  if('createImageBitmap' in window){try{return await createImageBitmap(blob);}catch(_){} }
  return await new Promise((resolve,reject)=>{const url=URL.createObjectURL(blob),im=new Image();im.onload=()=>{URL.revokeObjectURL(url);resolve(im);};im.onerror=()=>{URL.revokeObjectURL(url);reject(new Error('Could not decode SageTV image'));};im.src=url;});
}
function imageCanvas(handle){const x=images.get(handle)||surfaces.get(handle);if(!x)return null;return x.canvas||x;}
function allocateImage(handle,w,h,format){
  const c=makeCanvas(w,h),entry={canvas:c,width:w,height:h,format:format||0};
  if(format===256){try{entry.unified=new Core.UnifiedYuvImage(w,h);}catch(e){log('Unified YUV allocation rejected: '+e);return null;}}
  images.set(handle,entry);imageCount++;return c;
}
function optionalI(b,n,fallback){return b.length>=argOffset(n+4)?getI(b,n):fallback;}
function noteUnsupportedGfx(cmd){unsupportedGfxCount++;lastUnsupportedGfx=cmd;log('GFX opcode '+cmd+' was received but is negotiated off / unsupported');}

async function gfx(ev){
  const myRenderer=rendererGeneration;
  const b=base64Bytes(ev.payload);const cmd=ev.cmd|0;gfxCount++;lastEventAt=Date.now();
  switch(cmd){
    case 1: resetRenderer(uiW,uiH); message('SageTV graphics initialized…'); break;
    case 2: message('SageTV graphics deinitialized'); break;
    case 16:{const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12),t=Math.max(1,getI(b,16));c.save();c.lineWidth=t;c.strokeStyle=fillStyle(c,x,y,w,h,getI(b,20),getI(b,24),getI(b,28),getI(b,32));c.strokeRect(x+t/2,y+t/2,Math.max(0,w-t),Math.max(0,h-t));c.restore();break;}
    case 17:{const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12),tl=getI(b,16);if(tl===getI(b,20)&&tl===getI(b,24)&&tl===getI(b,28)&&clearVideoMask(c,[x,y,w,h],tl,false))break;if(isColorKey(tl)&&tl===getI(b,20)&&tl===getI(b,24)&&tl===getI(b,28))c.clearRect(x,y,w,h);else{c.save();c.fillStyle=fillStyle(c,x,y,w,h,tl,getI(b,20),getI(b,24),getI(b,28));c.fillRect(x,y,w,h);c.restore();}break;}
    case 18:{if(currentTarget===0)frameHadClearRect=true;const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12);clearOrFill(c,x,y,w,h,getI(b,16));break;}
    case 19:{const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12),t=Math.max(1,getI(b,16));clip(c,getI(b,36),getI(b,40),getI(b,44),getI(b,48),()=>{c.beginPath();c.ellipse(x+w/2,y+h/2,Math.abs(w/2),Math.abs(h/2),0,0,Math.PI*2);c.lineWidth=t;c.strokeStyle=fillStyle(c,x,y,w,h,getI(b,20),getI(b,24),getI(b,28),getI(b,32));c.stroke();});break;}
    case 20:{const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12);clip(c,getI(b,32),getI(b,36),getI(b,40),getI(b,44),()=>{c.beginPath();c.ellipse(x+w/2,y+h/2,Math.abs(w/2),Math.abs(h/2),0,0,Math.PI*2);c.fillStyle=fillStyle(c,x,y,w,h,getI(b,16),getI(b,20),getI(b,24),getI(b,28));c.fill();});break;}
    case 21:{const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12),t=Math.max(1,getI(b,16)),r=Math.max(0,getI(b,20));clip(c,getI(b,40),getI(b,44),getI(b,48),getI(b,52),()=>{roundedPath(c,x,y,w,h,r);c.lineWidth=t;c.strokeStyle=fillStyle(c,x,y,w,h,getI(b,24),getI(b,28),getI(b,32),getI(b,36));c.stroke();});break;}
    case 22:{const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12),r=Math.max(0,getI(b,16));clip(c,getI(b,36),getI(b,40),getI(b,44),getI(b,48),()=>{roundedPath(c,x,y,w,h,r);c.fillStyle=fillStyle(c,x,y,w,h,getI(b,20),getI(b,24),getI(b,28),getI(b,32));c.fill();});break;}
    case 23: break; // GFX_TEXTMODE is disabled; SageTV rasterizes text into images.
    case 24:{
      const c=targetCtx(),x=getI(b,0),y=getI(b,4),w=getI(b,8),h=getI(b,12),handle=getI(b,16),sx=getI(b,20),sy=getI(b,24),sw=getI(b,28),sh=getI(b,32),blend=getI(b,36)>>>0,src=imageCanvas(handle);if(!src)break;
      // Match the Vibe Android MiniClient renderer exactly here. SageTV can send
      // negative destination dimensions as rendering flags. Android takes abs()
      // for geometry and does NOT mirror the texture; a negative height selects
      // the unblended/copy path. Treating the signs as Canvas scale flips makes
      // rasterized SageMC text appear backwards and framebuffer compositions
      // upside-down. Browser canvases already use SageTV's top-left coordinates,
      // so they also do not need the OpenGL framebuffer Y inversion used by GDX.
      const dw=Math.abs(w),dh=Math.abs(h),bc=argb(blend);if(!(dw>0&&dh>0&&sw!==0&&sh!==0))break;
      c.save();
      try{
        c.globalAlpha=bc.a/255;
        if(h<0){
          // Android uses GL_ONE/GL_ZERO when height is negative. Constrain Canvas
          // 'copy' to the destination rectangle so pixels elsewhere are retained.
          c.beginPath();c.rect(x,y,dw,dh);c.clip();c.globalCompositeOperation='copy';
        }else c.globalCompositeOperation='source-over';
        c.drawImage(src,sx,sy,sw,sh,x,y,dw,dh);
      }catch(_){}
      c.restore();break;}
    case 25:{const c=targetCtx(),x1=getI(b,0),y1=getI(b,4),x2=getI(b,8),y2=getI(b,12),a=getI(b,16),z=getI(b,20);c.save();if(a===z)c.strokeStyle=cssArgb(a);else{const g=c.createLinearGradient(x1,y1,x2,y2);g.addColorStop(0,cssArgb(a));g.addColorStop(1,cssArgb(z));c.strokeStyle=g;}c.beginPath();c.moveTo(x1,y1);c.lineTo(x2,y2);c.stroke();c.restore();break;}
    case 26:{const h=ev.assignedHandle|0,format=optionalI(b,8,0);if(h)allocateImage(h,getI(b,0),getI(b,4),format);else if(format===256)noteUnsupportedGfx(cmd);break;}
    case 27:{const h=getI(b,0);images.delete(h);if(h!==0)surfaces.delete(h);break;}
    case 28: case 29: case 36: break;
    case 30: flip();break;
    case 31: startFrame();break;
    case 32:{
      const handle=getI(b,0),line=getI(b,4),len=getI(b,8),entry=images.get(handle);if(!entry||!entry.canvas)break;let p=argOffset(12);if(entry.unified){const count=Math.min(len,Math.max(0,b.length-p)),row=entry.unified.loadLine(line,b,p,count);if(row>=0&&entry.unified.ySeen[row]&&entry.unified.uvSeen[row])entry.canvas.getContext('2d').putImageData(new ImageData(entry.unified.rowRgba(row),entry.width,1),0,row);break;}if(line<0||line>=entry.canvas.height)break;const px=Math.min(entry.canvas.width,Math.floor(len/4));const data=new Uint8ClampedArray(px*4);for(let i=0;i<px&&p+3<b.length;i++,p+=4){data[i*4+0]=b[p+1];data[i*4+1]=b[p+2];data[i*4+2]=b[p+3];data[i*4+3]=b[p];}entry.canvas.getContext('2d').putImageData(new ImageData(data,px,1),0,line);break;}
    case 33: break;
    case 34:{
      const original=getI(b,0),len=Math.min(getI(b,4),Math.max(0,b.length-argOffset(8))),handle=(ev.assignedHandle|0)||original;if(len<=0)break;const img=await decodeCompressed(b.slice(argOffset(8),argOffset(8)+len));if(myRenderer!==rendererGeneration){if(img.close)img.close();return;}const c=makeCanvas(img.width||1,img.height||1);c.getContext('2d').drawImage(img,0,0);if(img.close)try{img.close();}catch(_){}images.set(handle,{canvas:c,width:c.width,height:c.height});imageCount++;break;}
    case 35:{
      const srcHandle=getI(b,0),destArg=getI(b,4),dw=getI(b,8),dh=getI(b,12),src=imageCanvas(srcHandle),handle=(ev.assignedHandle|0)||destArg;if(!src||!handle)break;const c=makeCanvas(dw,dh);c.getContext('2d').drawImage(src,0,0,src.width,src.height,0,0,dw,dh);images.set(handle,{canvas:c,width:dw,height:dh});break;}
    case 37:{const h=ev.assignedHandle|0,w=getI(b,0),hh=getI(b,4);if(h){const c=makeCanvas(w,hh);surfaces.set(h,c);images.set(h,{canvas:c,width:w,height:hh,surface:true});}break;}
    case 38:{const h=getI(b,0);currentTarget=surfaces.has(h)?h:0;break;}
    case 40: noteUnsupportedGfx(cmd);break; // diffuse textures are not advertised
    case 41: case 42: case 43: noteUnsupportedGfx(cmd);break; // transforms/batches are not advertised
    case 44: noteUnsupportedGfx(cmd);break; // offline image cache is not advertised
    case 45:{const h=getI(b,0),w=getI(b,4),hh=getI(b,8),format=optionalI(b,12,0);if(format===256&&!settings.unifiedGraphics){noteUnsupportedGfx(cmd);break;}allocateImage(h,w,hh,format);break;}
    case 46:{const h=getI(b,0),w=getI(b,4),hh=getI(b,8),format=optionalI(b,12,0);if(format===256&&!settings.unifiedGraphics){noteUnsupportedGfx(cmd);break;}if(!images.has(h))allocateImage(h,w,hh,format);break;}
    case 130:{if(b.length>=44){const dx=getI(b,20),dy=getI(b,24),dw=getI(b,28),dh=getI(b,32);applyVideoBounds([dx,dy,dw,dh],true,'gfx-video-property');}break;}
    default: break;
  }
}


function clearVideoMask(ctx,rect,color,isClear){
  if(!settings.videoMaskAssist||!playback.id||currentTarget!==0)return false;
  const alpha=(color>>>24)&255;
  if(alpha>=13&&alpha<250&&!isColorKey(color))return false; // translucent OSD is not a video hole
  const screen=[0,0,uiW,uiH],hit=Core.videoMask(color,rect,screen,currentVideoBounds,lastTrustedVideoBounds,isClear,videoPresentationSuspended,videoBoundsTrusted);
  const matte=Core.fullscreenMatte(color,rect,screen,videoPresentation,videoPresentationSuspended);
  if(!hit&&!matte)return false;
  const mask=hit||Core.rectIntersection(rect,screen);
  // A clear is a paint operation, NOT SETVIDEORECT. Only the explicit color
  // key may establish a small legacy preview. Ordinary black clearing and
  // letterbox mattes never shrink or pan the fullscreen HTML video element.
  if(isColorKey(color))applyVideoBounds(mask,true,'color-key');
  else if(isClear&&mask[2]>=uiW*.85&&mask[3]>=uiH*.85&&videoBoundsOrigin==='color-key')
    applyVideoBounds(screen,true,'clear-fullscreen');
  videoPresentationSuspended=false;
  ctx.clearRect(mask[0]-1,mask[1]-1,mask[2]+2,mask[3]+2);return true;
}
function applyVideoBounds(rect,trusted,origin){
  if(Array.isArray(rect)&&rect.length>=4&&trusted!==undefined){
    const raw=rect.slice(0,4).map(Number),valid=raw.every(Number.isFinite)&&raw[2]>0&&raw[3]>0;
    videoBoundsTrusted=!!trusted&&valid;
    if(valid){nativeVideoBounds=raw;lastTrustedVideoBounds=raw.slice();videoBoundsOrigin=origin||'native';}
    else if(!playback.id)nativeVideoBounds=[0,0,uiW,uiH];
  }
  videoPresentation=Core.videoPresentation(nativeVideoBounds,uiW,uiH,settings.videoViewport);
  currentVideoBounds=videoPresentation.rect.slice();
  const sr=stage.getBoundingClientRect();
  videoLayerRect=Core.videoLayerRect(videoPresentation,displayRect,sr.width,sr.height,uiW,uiH);
  const d=videoLayerRect;
  videoBounds.hidden=!playback.id;
  videoBounds.style.left=d.left+'px';videoBounds.style.top=d.top+'px';
  videoBounds.style.width=d.width+'px';videoBounds.style.height=d.height+'px';
  videoBounds.dataset.presentation=videoPresentation.kind;
  video.style.objectFit=videoAspect==='fill'?'fill':(videoAspect==='cover'||videoAspect==='zoom')?'cover':'contain';
  video.style.objectPosition='50% 50%';video.style.width='100%';video.style.height='100%';
  if(videoAspect==='4x3'||videoAspect==='16x9'){
    const ratio=videoAspect==='4x3'?4/3:16/9,wantedWidth=Math.min(d.width,d.height*ratio);
    video.style.width=wantedWidth+'px';video.style.height=(wantedWidth/ratio)+'px';video.style.objectFit='fill';
  }
  syncDvbCanvasLayout();
}
function destroyVideo(){captions.stop();ordinarySubtitles.stop();dvdSpu.stop();playback.stop();resetDvdFrameCache(true);videoBoundsTrusted=false;lastTrustedVideoBounds=null;videoPresentationSuspended=false;videoBounds.hidden=true;updateStats();}
function startVideo(ev){
  captions.stop();ordinarySubtitles.stop();dvdSpu.stop();
  if(ev&&ev.dvd)beginDvdFrameCache(ev.dvdGeneration);else resetDvdFrameCache(true);
  ev.captionAuthority=CaptionAuthority.effective(captionAuthority,captionInventory);
  if(ev.captionAuthority&&ev.captionAuthority.mode==='stv'){ev.captionAuthority=Object.assign({},ev.captionAuthority,{stvStateSeen:sageTvCaptionStateSeen,stvState:sageTvCaptionState});}
  const started=playback.start(ev);applyVideoBounds();showStreamActual(ev.streamSettings);
  return started.then(async()=>{if(playback.id===ev.hlsSession){captions.start(ev);if(captionAuthority.mode==='stv'&&typeof captions.setSageTvState==='function')captions.setSageTvState(sageTvCaptionState,sageTvCaptionStateSeen);ordinarySubtitles.start(ev);dvdSpu.start(ev);await recoverInterruptedVideoTest(ev);}});
}
function absoluteMediaTime(){return playback.absoluteTime();}
async function browserState(){
  if(!session||stopped||!playback.id||stateBusy)return;stateBusy=true;
  const id=session,serial=connectionGeneration;
  try{await api('state',Object.assign({session:id},playback.state()),'POST');}
  catch(e){if(serial===connectionGeneration)setError(e);}finally{stateBusy=false;}
}
function nativeConnectionClosed(reason){
  if(stopped)return;
  failureSnapshot={at:new Date().toISOString(),reason:String(reason||'SageTV closed the native connection'),playback:playback.snapshot()};
  stopped=true;clearInterval(stateTimer);clearTimeout(guideTimer);gesture.cancel();clearRepeats();
  if(eventController)eventController.abort();
  destroyVideo();setError(failureSnapshot.reason);setStatus('Disconnected · use Reconnect');
  message(lastError+' — Download diagnostics before using Reconnect.');
  document.body.classList.add('mc-connection-lost');
  $('reconnectBtn').disabled=false;$('connectionDiagnosticsBtn').hidden=false;
  if(settings.autoDiagnosticCapture)setTimeout(()=>automaticDiagnosticCapture('automatic-terminal-failure').catch(e=>log('Automatic diagnostic capture: '+(e.message||e))),0);
}
async function processEvent(ev){
  if(!ev)return;lastEventAt=Date.now();
  if(ev.type==='gfx'){await gfx(ev);return;}
  if(ev.type==='videoStart'){startVideo(ev).catch(setError);return;}
  if(ev.type==='videoStop'){destroyVideo();return;}
  if(ev.type==='videoBounds'){nativeVideoSource=ev.src||null;applyVideoBounds(ev.dst,true,'native');return;}
  if(ev.type==='dvd'){if(ev.action==='freeze')promoteDvdStill(ev.generation);return;}
  if(ev.type==='media'){
    playback.control(ev.action,ev.value);if(ev.action==='stop')videoBounds.hidden=true;
    browserState();return;
  }
  if(ev.type==='menuHint'){currentMenuHint=String(ev.value||'');menuEpoch++;layoutStage();return;}
  if(ev.type==='aspect'){
    serverVideoAspect=String(ev.value||'').toLowerCase();log('Server video aspect: '+serverVideoAspect+'; browser mode: '+settings.videoFit);applyVideoBounds();return;
  }
  if(ev.type==='gfxUnsupported'){unsupportedGfxCount++;lastUnsupportedGfx=Number(ev.cmd);log('Server reported '+String(ev.name||('GFX '+ev.cmd))+' as '+String(ev.disposition||'unsupported'));return;}
  if(ev.type==='captionState'){
    const parsed=Number.parseInt(ev.value,10);sageTvCaptionStateSeen=true;sageTvCaptionState=Number.isFinite(parsed)&&parsed>=0?parsed:0;
    if(captions&&typeof captions.setSageTvState==='function')captions.setSageTvState(sageTvCaptionState,true);
    log('captionState: '+String(ev.value||'')+' → local STV-controlled CC '+(sageTvCaptionState>0?Math.min(4,sageTvCaptionState):'off'));
    if(captionAuthority.mode==='stv')updateCaptionAuthorityForm();return;
  }
  if(ev.type==='channel'||ev.type==='uiAspect'){log(ev.type+': '+String(ev.value||''));return;}
  if(ev.type==='resize'){resizeLocal(ev.width,ev.height);return;}
  if(ev.type==='status'){
    log('Native status: '+ev.state);
    if(ev.state==='closed'){nativeConnectionClosed(ev.error||lastError||'SageTV closed the native connection');return;}
    if(ev.state==='gfx-reconnecting'){setStatus('Recovering native GFX connection…');message('Recovering SageTV graphics connection…');return;}
    if(ev.state==='gfx-reconnected'){nativeRecoveryCount++;setStatus('Connected · GFX recovered');message('');return;}
    setStatus((ev.state||'connected')+(ev.clientId?' · '+ev.clientId:''));
    if(ev.state==='connected')message('Waiting for SageTV / SageMC…');return;
  }
  if(ev.type==='error'){if(ev.fatal){nativeConnectionClosed(ev.message);return;}setError(ev.message||'MiniClient error');message(lastError);return;}
}
function sleep(ms){return new Promise(resolve=>setTimeout(resolve,ms));}
async function eventLoop(serial,id){
  const controller=new AbortController();eventController=controller;
  const active=()=>serial===connectionGeneration&&session===id&&!stopped;
  let workStarted=performance.now(),workCount=0;
  const consume=ev=>{
    // Playback control must not wait behind PNG decoding or thousands of GFX
    // commands. These methods preserve media-event order through generations.
    if(['videoStart','videoStop','media','captionState','status','error'].includes(ev.type)){
      if(active())processEvent(ev).catch(setError);return;
    }
    const bytes=typeof ev.payload==='string'?ev.payload.length:256;
    renderQueued++;renderQueuedBytes+=bytes;
    eventChain=eventChain.then(async()=>{
      if(!active())return;
      if(++workCount>=128||performance.now()-workStarted>=8){
        await sleep(0);workCount=0;workStarted=performance.now();
      }
      if(active())await processEvent(ev);
    }).catch(setError).finally(()=>{renderQueued--;renderQueuedBytes-=bytes;});
  };
  while(active()){
    let reader=null;
    try{
      if(!window.ReadableStream||!window.TextDecoder){
        const events=await requestJson('api/miniclient','events',{session:id,max:500,wait:25000},'GET',30000);
        if(active()&&Array.isArray(events)){
          events.forEach(consume);await eventChain;
          if(active()&&events.length===0){
            const status=await requestJson('api/miniclient','status',{session:id},'GET',3000);
            if(active()&&status.alive===false)nativeConnectionClosed(status.error||'Native SageTV connection ended');
          }
        }continue;
      }
      const url=new URL('api/miniclient',location.href);url.searchParams.set('action','stream');url.searchParams.set('session',id);
      const response=await fetch(url.href,{cache:'no-store',signal:controller.signal});
      if(response.status===404){nativeConnectionClosed('MiniClient session no longer exists; reconnect to SageTV.');break;}
      if(!response.ok)throw new Error('MiniClient stream HTTP '+response.status);
      reader=response.body.getReader();const decoder=new TextDecoder();let pending='';
      while(active()){
        const part=await reader.read();if(part.done)break;
        pending+=decoder.decode(part.value,{stream:true});
        if(pending.length>32*1024*1024)throw new Error('Oversized MiniClient event');
        let newline;
        while((newline=pending.indexOf('\n'))>=0){
          const line=pending.slice(0,newline).trim();pending=pending.slice(newline+1);if(!line)continue;
          let event;try{event=JSON.parse(line);}catch(_){continue;}
          if(event.type!=='keepalive')consume(event);
        }
        // Read ahead so media controls arriving in the next network chunk are
        // not blocked by image decode. Bound outstanding GFX work / memory.
        while(active()&&(renderQueued>4096||renderQueuedBytes>8*1024*1024))await sleep(4);
      }
      if(active()){
        // EOF of HTTP is not proof that the native SageTV socket can recover.
        // Inspect the owning session before opening another empty HTTP stream.
        const status=await requestJson('api/miniclient','status',{session:id},'GET',3000);
        if(active()&&status.alive===false){nativeConnectionClosed(status.error||'Native SageTV connection ended');break;}
        if(active()){setStatus('Reconnecting event stream…');await sleep(700);}
      }
    }catch(e){if(active()&&e.name!=='AbortError'){
      if(e.httpStatus===404){nativeConnectionClosed('MiniClient session no longer exists; reconnect to SageTV.');break;}
      setError(e);setStatus('Reconnecting event stream…');await sleep(700);
    }}
    finally{if(reader)try{await reader.cancel();}catch(_){} }
  }
  if(eventController===controller)eventController=null;
}
function stableClientId(){
  const qp=new URLSearchParams(location.search).get('clientId');
  if(/^[0-9A-Fa-f:-]{12,17}$/.test(qp||''))return qp.replace(/[^0-9A-Fa-f]/g,'').slice(0,12).toUpperCase();
  let id='';try{id=localStorage.getItem('sagetvWebMiniClientId')||'';}catch(_){}
  if(/^[0-9A-F]{12}$/i.test(id))return id.toUpperCase();
  const bytes=new Uint8Array(6);crypto.getRandomValues(bytes);bytes[0]=(bytes[0]&0xFC)|0x02;
  id=[...bytes].map(v=>v.toString(16).padStart(2,'0')).join('').toUpperCase();
  try{localStorage.setItem('sagetvWebMiniClientId',id);}catch(_){}return id;
}
function desiredSize(){
  const q=new URLSearchParams(location.search).get('ui'),m=(q||'').match(/^(\d{3,4})x(\d{3,4})$/i);
  if(m)return {w:Math.max(320,Math.min(1920,Number(m[1])))&~1,h:Math.max(240,Math.min(1080,Number(m[2])))&~1};
  const rect=stage.getBoundingClientRect();
  return Core.logicalSize(rect.width,rect.height,settings.uiResolution);
}
async function connect(){
  const serial=++connectionGeneration,old=session;session='';stopped=false;failureSnapshot=null;sageTvCaptionStateSeen=false;sageTvCaptionState=0;
  document.body.classList.remove('mc-connection-lost');$('connectionDiagnosticsBtn').hidden=true;lastError='';
  if(eventController)eventController.abort();gesture.cancel();clearRepeats();clearTimeout(guideTimer);clearInterval(stateTimer);
  destroyVideo();message('Connecting to SageTV MiniClient…');setStatus('Connecting…');$('reconnectBtn').disabled=true;
  const sz=desiredSize();lastRequestedSize=sz.w+'x'+sz.h;resetRenderer(sz.w,sz.h);
  try{
    if(old)try{await api('stop',{session:old},'POST');}catch(_){}
    if(serial!==connectionGeneration)return;
    const server=new URLSearchParams(location.search).get('server')||'';
    const response=await api('start',Object.assign({server,clientId:stableClientId(),width:sz.w,height:sz.h,unifiedGraphics:!!settings.unifiedGraphics},Streaming.toParams(streaming,Streaming.browserCapabilities(window))),'POST');
    if(serial!==connectionGeneration){api('stop',{session:response.session},'POST').catch(()=>{});return;}
    session=response.session;
    if(response.alive===false){nativeConnectionClosed(response.error||'SageTV closed the connection during initialization');return;}
    setStatus('Connected · '+(response.clientId||stableClientId()));message('Waiting for SageTV / SageMC…');
    eventLoop(serial,session).catch(setError);stateTimer=setInterval(browserState,500);
    if(settings.fullscreenOnConnect)setRenderOnly(true);
    requestResize();
  }catch(e){if(serial===connectionGeneration)nativeConnectionClosed('MiniClient connection failed: '+(e.message||e));}
  finally{if(serial===connectionGeneration)$('reconnectBtn').disabled=false;}
}
function resizeLocal(w,h){
  w=Math.max(320,Math.min(1920,Number(w)||uiW));h=Math.max(240,Math.min(1080,Number(h)||uiH));
  if(w===uiW&&h===uiH){layoutStage();return;}
  // A native resize does not unload SageTV image handles. Clearing the entire
  // image map here loses cached menu text/textures that the server may reuse.
  const old=mainSurface,oldW=uiW,oldH=uiH;
  nativeVideoBounds=[nativeVideoBounds[0]*w/oldW,nativeVideoBounds[1]*h/oldH,nativeVideoBounds[2]*w/oldW,nativeVideoBounds[3]*h/oldH];
  if(lastTrustedVideoBounds)lastTrustedVideoBounds=nativeVideoBounds.slice();
  uiW=w;uiH=h;mainSurface=makeCanvas(w,h);
  mainSurface.getContext('2d').drawImage(old,0,0,oldW,oldH,0,0,w,h);surfaces.set(0,mainSurface);
  visible.width=w;visible.height=h;visibleCtx.drawImage(mainSurface,0,0);
  document.documentElement.style.setProperty('--ui-aspect',String(w/h));
  layoutStage();log('Native UI resized to '+w+'x'+h+'; cached image handles retained');
}
async function syncWindowSize(){
  layoutStage();if(!session)return;
  if(resizeInFlight){resizeAgain=true;return;}
  const sz=desiredSize(),key=sz.w+'x'+sz.h;if(key===lastRequestedSize)return;
  const id=session,serial=connectionGeneration;resizeInFlight=true;
  try{
    // Server emits an ordered resize event; do not invalidate the renderer
    // immediately while preceding drawing packets are still being processed.
    const sent=await queueInput('resize',{width:sz.w,height:sz.h});
    if(sent&&id===session&&serial===connectionGeneration)lastRequestedSize=key;
  }finally{resizeInFlight=false;if(resizeAgain){resizeAgain=false;requestResize();}}
}
function requestResize(){clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>syncWindowSize().catch(setError),120);}
function queueInput(action,params){
  if(!session||stopped)return Promise.resolve(false);
  if(inputPending>64){setError('Input queue is full; check the server connection');return Promise.resolve(false);}
  const id=session,serial=connectionGeneration;inputPending++;
  const task=inputChain.then(async()=>{
    if(stopped||id!==session||serial!==connectionGeneration)return false;
    const result=await api(action,Object.assign({session:id},params),'POST');return result.ok!==false;
  });
  inputChain=task.catch(setError).finally(()=>{inputPending--;});return task.catch(()=>false);
}
function sendCommand(command){
  if(playback.id&&[75,28,18,15,42,88,86,84,85,41,29,40,67,74,89].includes(command)){
    videoPresentationSuspended=true;
    const id=session,serial=connectionGeneration;
    [0,150,500].forEach(delay=>setTimeout(()=>{if(id===session&&serial===connectionGeneration)queueInput('repaint',{x:0,y:0,width:uiW,height:uiH});},delay));
  }
  if(command===6)playback.control('pause');
  else if(command===7)playback.control('play',null,true);
  else if(command===59)playback.control(playback.intentPaused?'play':'pause',null,true);
  else if(command===89){playback.control('pause');videoBounds.hidden=true;}
  return queueInput('command',{command});
}
function sendKey(code,mods,ch){return queueInput('key',{code:code||0,modifiers:mods||0,char:ch||0});}
function sendMouse(type,point,button,clicks){return queueInput('mouse',{type,x:point.x,y:point.y,button:button||0,modifiers:0,clicks:clicks==null?1:clicks});}
function sendBack(){const now=Date.now();if(now-lastBackAt<250)return;lastBackAt=now;sendCommand(75);}
const keyRepeat=new Core.CommandRepeat({send:sendCommand});
function sendCanvasClick(point,button){
  const now=Date.now(),mode=settings.clickMode;let includeClick=true;
  if(button===1&&mode==='press_release')includeClick=false;
  if(button===1&&mode==='double_select'){
    includeClick=!!lastClick&&lastClick.button===button&&now-lastClick.at<=700&&Math.hypot(point.x-lastClick.x,point.y-lastClick.y)<=10;
    lastClick=includeClick?null:{x:point.x,y:point.y,button,at:now};
  }
  const hint=currentMenuHint,epoch=menuEpoch,serial=connectionGeneration;
  clearTimeout(guideTimer);
  queueInput('click',{x:point.x,y:point.y,button,includeClick}).then(sent=>{
    if(!sent||!includeClick||button!==1||!settings.guideAssist||!Core.menuIsGuide(hint))return;
    guideTimer=setTimeout(()=>{
      if(serial===connectionGeneration&&epoch===menuEpoch&&currentMenuHint===hint&&settings.guideAssist&&Core.menuIsGuide(currentMenuHint))sendCommand(20);
    },180);
  });
}
const gesture=new Core.PointerGesture({settings:()=>settings,click:sendCanvasClick,open:()=>openRemote(),back:sendBack,
  move:point=>{const now=Date.now();if(now-lastMove<40||inputPending>0)return;lastMove=now;sendMouse(133,point,0,0);},
  dragStart:(point,button)=>sendMouse(130,point,button),
  dragMove:(point,button)=>{const now=Date.now();if(now-lastMove<40||inputPending>2)return;lastMove=now;sendMouse(134,point,button);},
  dragEnd:(point,button)=>sendMouse(131,point,button)
});
visible.addEventListener('pointerdown',e=>{stage.focus();if(gesture.down(e,currentScalePoint(e))){e.preventDefault();try{visible.setPointerCapture(e.pointerId);}catch(_){}}});
visible.addEventListener('pointermove',e=>gesture.move(e,currentScalePoint(e)));
visible.addEventListener('pointerup',e=>{if(gesture.up(e,currentScalePoint(e)))e.preventDefault();try{visible.releasePointerCapture(e.pointerId);}catch(_){}});
visible.addEventListener('pointercancel',()=>gesture.cancel());
visible.addEventListener('lostpointercapture',e=>{if(gesture.active&&gesture.active.id===e.pointerId)gesture.cancel();});
visible.addEventListener('click',e=>gesture.fallbackClick(e,currentScalePoint(e)));
visible.addEventListener('contextmenu',e=>e.preventDefault());
visible.addEventListener('wheel',e=>{e.preventDefault();sendMouse(135,currentScalePoint(e),0,e.deltaY>0?1:-1);},{passive:false});
['mousedown','mouseup','auxclick'].forEach(name=>visible.addEventListener(name,e=>{
  if(e.button!==3||!settings.mouseBack)return;e.preventDefault();e.stopPropagation();if(name==='mousedown')sendBack();
},true));
function clearRepeats(){clearTimeout(repeatTimer);clearInterval(repeatInterval);repeatTimer=null;repeatInterval=null;}
function hideRemoteDrawers(){
  videoTestGeneration++;
  for(const id of ['remoteTools','keyboardPanel','videoInfoPanel'])$(id).hidden=true;
  $('keyboardToggle').setAttribute('aria-expanded','false');$('remoteHelp').setAttribute('aria-expanded','false');
}
function openRemote(){
  hideRemoteDrawers();remotePanel.hidden=false;remotePanel.setAttribute('aria-hidden','false');
  $('remoteToggle').setAttribute('aria-expanded','true');
  (playback.id?$('nav_media_pause'):$('nav_options')).focus({preventScroll:true});layoutStage();updateStats();
}
function closeRemote(){
  clearRepeats();hideRemoteDrawers();remotePanel.hidden=true;remotePanel.setAttribute('aria-hidden','true');
  $('remoteToggle').setAttribute('aria-expanded','false');stage.focus({preventScroll:true});layoutStage();
}
function openSettings(section){
  closeRemote();settingsPanel.hidden=false;fillStreamingForm();refreshStreamTracks().catch(setError);refreshDiagnosticDestination().catch(setError);
  const field=typeof section==='string'?$(section):null;
  if(field){const details=field.closest&&field.closest('details');if(details)details.open=true;field.scrollIntoView({block:'center'});field.focus({preventScroll:true});}
  else $('closeSettings').focus({preventScroll:true});
}
function closeSettings(){settingsPanel.hidden=true;stage.focus({preventScroll:true});layoutStage();}
function showDrawer(id,focusId){hideRemoteDrawers();$(id).hidden=false;$(focusId).focus({preventScroll:true});}
function layoutSnapshot(){
  const r=stage.getBoundingClientRect();
  return {stage:{width:r.width,height:r.height},nativeBounds:nativeVideoBounds.slice(),source:nativeVideoSource,
    origin:videoBoundsOrigin,presentation:videoPresentation,cssRect:videoLayerRect,
    serverAspect:serverVideoAspect,browserAspect:videoAspect,objectFit:video.style.objectFit};
}
function showVideoInformation(){
  showDrawer('videoInfoPanel','closeVideoInfo');
  $('videoInfoText').textContent=JSON.stringify({scope:'Browser playback and plugin status; not Android device diagnostics',
    layout:layoutSnapshot(),video:{width:video.videoWidth,height:video.videoHeight,readyState:video.readyState,
      networkState:video.networkState,error:video.error?{code:video.error.code,message:video.error.message}:null},
    playback:playback.snapshot(),captions:captions.snapshot(),ordinarySubtitles:ordinarySubtitles.snapshot(),dvdSpu:dvdSpu.snapshot()},null,2);
}
async function waitForTest(predicate,timeoutMs){const end=Date.now()+timeoutMs;while(Date.now()<end){if(predicate())return true;await sleep(100);}return !!predicate();}
function saveVideoTestRecovery(value){try{if(value)sessionStorage.setItem(VIDEO_TEST_RECOVERY_KEY,JSON.stringify(value));else sessionStorage.removeItem(VIDEO_TEST_RECOVERY_KEY);}catch(_){}}
function readVideoTestRecovery(){try{const raw=sessionStorage.getItem(VIDEO_TEST_RECOVERY_KEY);return raw?JSON.parse(raw):null;}catch(_){return null;}}
async function recoverInterruptedVideoTest(ev){const before=readVideoTestRecovery();if(!before)return;const sameMedia=before.mediaId!=null&&ev&&ev.mediaId!=null&&Number(before.mediaId)===Number(ev.mediaId);try{video.muted=!!before.muted;video.volume=Math.max(0,Math.min(1,Number(before.volume)));video.playbackRate=Number(before.playbackRate)||1;}catch(_){}if(!sameMedia){log('Interrupted video test recovery cleared without seek because the media changed.');saveVideoTestRecovery(null);return;}before.nativeSession=session;before.hlsSession=playback.id;await restoreVideoTest(before);log('Interrupted video test restored on the matching recording.');}
async function restoreVideoTest(before){
  if(!before)return;try{video.muted=!!before.muted;video.volume=Math.max(0,Math.min(1,Number(before.volume)));video.playbackRate=Number(before.playbackRate)||1;}catch(_){}
  if(session===before.nativeSession&&playback.id&&Math.abs(playback.absoluteTime()-before.timeMs)>1500){try{await queueInput('seek',{timeMs:before.timeMs,expectedHlsSession:playback.id});await waitForTest(()=>Math.abs(playback.absoluteTime()-before.timeMs)<1000,3500);}catch(e){log('Video test restore seek: '+e);}}
  if(before.intentPaused)playback.control('pause');else playback.control('play',null,true);saveVideoTestRecovery(null);
}
async function testCurrentVideo(){
  showDrawer('videoInfoPanel','closeVideoInfo');const generation=++videoTestGeneration,id=playback.id,box=$('videoInfoText');
  if(!id){box.textContent='Not tested: no active video. This test never starts a recording by itself.';return;}
  const snap=playback.snapshot(),status=snap.status||{},before={nativeSession:session,hlsSession:id,mediaId:status.mediaId==null?null:Number(status.mediaId),timeMs:snap.timeMs,paused:video.paused,intentPaused:playback.intentPaused,muted:video.muted,volume:video.volume,playbackRate:video.playbackRate,frames:video.getVideoPlaybackQuality?video.getVideoPlaybackQuality().totalVideoFrames:null};
  saveVideoTestRecovery(before);const steps=[],started=Date.now();box.textContent='Running bounded current-video test… Original position, pause/mute, volume and rate will be restored.';
  try{
    const t0=Number(video.currentTime)||0,f0=before.frames;await sleep(1500);if(generation!==videoTestGeneration)throw new Error('cancelled');
    const t1=Number(video.currentTime)||0,f1=video.getVideoPlaybackQuality?video.getVideoPlaybackQuality().totalVideoFrames:null;
    steps.push({name:'cadence',ok:before.intentPaused?null:(t1-t0>.2||(f0!=null&&f1>f0)),detail:before.intentPaused?'paused at invocation':{secondsAdvanced:t1-t0,framesAdvanced:f0==null||f1==null?null:f1-f0,bufferAhead:Core.bufferedAhead(video)}});
    const unsafe=!!snap.dvd||!!status.activeRecording||!(snap.durationMs>10000);
    if(unsafe)steps.push({name:'mutation-safety',ok:true,skipped:true,reason:snap.dvd?'DVD/title/menu session':status.activeRecording?'live/growing recording':'unknown or short duration'});
    else{
      const wasPaused=playback.intentPaused;playback.control('pause');await sleep(200);steps.push({name:'pause',ok:video.paused&&playback.intentPaused});if(!wasPaused){playback.control('play',null,true);await sleep(300);steps.push({name:'resume',ok:!playback.intentPaused});}
      const margin=2500,target=Math.min(Math.max(margin,before.timeMs+2000),Math.max(margin,snap.durationMs-margin));
      if(Math.abs(target-before.timeMs)>=1000){const expected=playback.id,delivered=await queueInput('seek',{timeMs:target,expectedHlsSession:expected});const moved=delivered&&await waitForTest(()=>{const observed=playback.absoluteTime();return playback.id&&Math.abs(observed-target)<1000&&Math.abs(observed-before.timeMs)>=750;},4000);steps.push({name:'reversible-seek',ok:!!moved,targetMs:target,observedMs:playback.absoluteTime()});}
      else steps.push({name:'reversible-seek',ok:true,skipped:true,reason:'position too close to media boundary'});
    }
    lastVideoTest={schemaVersion:1,result:steps.every(x=>x.ok!==false)?'PASS':'ATTENTION',boundedMs:Date.now()-started,scope:'Explicit browser/plugin current-video test. Unsafe live/DVD mutation is skipped.',before,after:{timeMs:playback.absoluteTime(),bufferAhead:Core.bufferedAhead(video)},steps,subtitle:Support.captionSummary(captionAuthority,CaptionAuthority.effective(captionAuthority,captionInventory),captionInventory,captions.snapshot(),ordinarySubtitles.snapshot(),dvdSpu.snapshot())};
  }catch(e){lastVideoTest={schemaVersion:1,result:e&&e.message==='cancelled'?'CANCELLED':'ERROR',boundedMs:Date.now()-started,error:e.message||String(e),before,steps};}
  finally{await restoreVideoTest(before);if(generation===videoTestGeneration&&!$('videoInfoPanel').hidden){lastVideoTest.afterRestore={timeMs:playback.absoluteTime(),paused:playback.intentPaused,muted:video.muted,volume:video.volume,playbackRate:video.playbackRate};box.textContent=JSON.stringify(Support.redact(lastVideoTest),null,2);}log('Current video test: '+(lastVideoTest&&lastVideoTest.result));}
}
$('hideRemote').addEventListener('click',closeRemote);
$('remoteHelp').addEventListener('click',()=>{showDrawer('remoteTools','closeRemoteTools');$('remoteHelp').setAttribute('aria-expanded','true');});
$('closeRemoteTools').addEventListener('click',()=>{hideRemoteDrawers();$('remoteHelp').focus({preventScroll:true});});
$('closeKeyboard').addEventListener('click',()=>{hideRemoteDrawers();$('keyboardToggle').focus({preventScroll:true});});
$('closeVideoInfo').addEventListener('click',()=>{videoTestGeneration++;hideRemoteDrawers();$('videoInfoBtn').focus({preventScroll:true});});
$('allSettings').addEventListener('click',()=>openSettings());
$('audioSettings').addEventListener('click',()=>openSettings('stream_audioCodec'));
$('captionSettings').addEventListener('click',()=>{openSettings('captionAuthorityMode');fillCaptionAuthorityForm();});
$('videoInfoBtn').addEventListener('click',showVideoInformation);
$('testVideoBtn').addEventListener('click',()=>testCurrentVideo().catch(setError));
$('remoteStats').addEventListener('click',()=>{settings.showStats=!settings.showStats;saveSettings();closeRemote();});
$('aspectToggle').addEventListener('click',()=>{
  const modes=['cover','source','fill'];settings.videoFit=modes[(modes.indexOf(settings.videoFit)+1)%modes.length];
  saveSettings();log('Browser display mode: '+settings.videoFit);
});
remotePanel.addEventListener('pointerdown',e=>{
  const button=e.target.closest('button[data-command][data-repeat="true"]');if(!button||e.button!==0)return;
  clearRepeats();repeatConsumed.set(button,Date.now()+1000);sendCommand(Number(button.dataset.command));
  try{button.setPointerCapture(e.pointerId);}catch(_){}
  repeatTimer=setTimeout(()=>{sendCommand(Number(button.dataset.command));repeatConsumed.set(button,Date.now()+1000);repeatInterval=setInterval(()=>{repeatConsumed.set(button,Date.now()+1000);sendCommand(Number(button.dataset.command));},150);},420);
});
remotePanel.addEventListener('click',e=>{
  if(e.target===remotePanel){closeRemote();return;}
  const button=e.target.closest('[data-command]');if(!button)return;
  if(e.detail!==0&&(repeatConsumed.get(button)||0)>Date.now())return;
  if(button.dataset.dismiss==='true')closeRemote();
  sendCommand(Number(button.dataset.command));
});
['pointerup','pointercancel','lostpointercapture'].forEach(name=>remotePanel.addEventListener(name,clearRepeats));
window.addEventListener('pointerup',clearRepeats);
window.addEventListener('blur',()=>{gesture.cancel();clearRepeats();keyRepeat.cancel();});
document.addEventListener('visibilitychange',()=>{if(document.hidden){gesture.cancel();clearRepeats();keyRepeat.cancel();}else{layoutStage();requestResize();browserState();if(session)queueInput('repaint',{x:0,y:0,width:uiW,height:uiH});}});
$('remoteToggle').addEventListener('click',()=>remotePanel.hidden?openRemote():closeRemote());
$('closeRemote').addEventListener('click',closeRemote);$('settingsToggle').addEventListener('click',openSettings);$('remoteSettings').addEventListener('click',()=>openSettings('stream_videoMode'));$('closeSettings').addEventListener('click',closeSettings);
settingsPanel.addEventListener('click',e=>{if(e.target===settingsPanel)closeSettings();});
function fillCaptionAuthorityForm(){
  const a=CaptionAuthority.normalize(captionAuthority);for(const k of ['mode','cc1Type','cc1Language','cc1Service','cc2Type','cc2Language','cc2Service']){const id=k==='mode'?'captionAuthorityMode':k,el=$(id);if(el)el.value=String(a[k]);}
  updateCaptionAuthorityForm();
}
function readCaptionAuthorityForm(){return CaptionAuthority.normalize({mode:$('captionAuthorityMode').value,cc1Type:$('cc1Type').value,cc1Language:$('cc1Language').value,cc1Service:$('cc1Service').value,cc2Type:$('cc2Type').value,cc2Language:$('cc2Language').value,cc2Service:$('cc2Service').value});}
function updateCaptionAuthorityForm(){
  let a;try{a=readCaptionAuthorityForm();}catch(_){a=captionAuthority;}const eff=CaptionAuthority.effective(a,captionInventory);
  const stvState=a.mode==='stv'?(sageTvCaptionStateSeen?(sageTvCaptionState>0?' · STV selected CC'+Math.min(4,sageTvCaptionState):' · STV captions off'):' · waiting for STV caption state'):'';
  $('captionAuthorityEffective').textContent='Effective caption source: '+eff.label+stvState+(eff.missing?' (not currently discovered)':'');
  const slots=$('virtualCaptionSlots');if(slots)slots.hidden=!['cc1','cc2'].includes(a.mode);
  if(typeof updateCaptionControls==='function')updateCaptionControls();
}
function applyCaptionAuthorityToStreaming(candidate){
  captionAuthority=readCaptionAuthorityForm();saveCaptionAuthority();candidate.captionAuthority=captionAuthority.mode;const eff=CaptionAuthority.effective(captionAuthority,captionInventory);
  if(captionAuthority.mode==='stv'){candidate.captions='608';candidate.captionService=1;return candidate;}
  if(captionAuthority.mode==='dvb'){candidate.captions='dvb';return candidate;}
  if(captionAuthority.mode==='off'){candidate.captions='off';return candidate;}
  if(eff.missing){
    const key=captionAuthority.mode==='cc2'?'cc2':'cc1',type=captionAuthority[key+'Type'];
    candidate.captions=type==='cea708'?'708':type==='teletext'?'teletext':'608';
    candidate.captionService=(type==='cea608'||type==='cea708')?Number(captionAuthority[key+'Service']||1):(captionAuthority.mode==='cc2'?2:1);
    candidate.captionLanguage=captionAuthority[key+'Language']||candidate.captionLanguage||'';
    return candidate;
  }
  candidate.captions=eff.local;candidate.captionService=eff.service||1;
  if(eff.track){if(eff.local==='teletext'){candidate.captionPage=Number(eff.track.teletextPage)||888;candidate.captionLanguage=eff.track.language||'';}if(eff.local==='dvb'){candidate.captionPid=Number(eff.track.sourcePid);candidate.captionCompositionPage=Number(eff.track.compositionPageId);candidate.captionLanguage=eff.track.language||'';}}
  return candidate;
}
['captionAuthorityMode','cc1Type','cc1Language','cc1Service','cc2Type','cc2Language','cc2Service'].forEach(id=>{const el=$(id);if(el)el.addEventListener('change',updateCaptionAuthorityForm);});

function fillStreamingForm(value=streaming){
  for(const k of Object.keys(Streaming.DEFAULTS)){const el=$('stream_'+k);if(!el)continue;if(el.type==='checkbox')el.checked=!!value[k];else el.value=String(value[k]);}
  if(typeof updateCaptionControls==='function')updateCaptionControls();
}
function readStreamingForm(){
  const v=Object.assign({},streaming);for(const k of Object.keys(Streaming.DEFAULTS)){const el=$('stream_'+k);if(el)v[k]=el.type==='checkbox'?el.checked:el.value;}
  return Streaming.normalize(v);
}
function showStreamActual(active){
  const el=$('streamingActual');if(!active){el.textContent='No active recording.';return;}
  const e=active.effective||{},source=active.source||{};
  captionInventory=Array.isArray(source.tracks)?source.tracks:[];updateCaptionAuthorityForm();
  const authEff=CaptionAuthority.effective(captionAuthority,captionInventory),requested=active.requested||{};
  if(session&&playback.id&&!captionAutoResolveBusy&&['cc1','cc2'].includes(captionAuthority.mode)&&!authEff.missing&&String(requested.captions||'off')!==String(authEff.local)){captionAutoResolveBusy=true;setTimeout(()=>commitStreaming(true).finally(()=>{captionAutoResolveBusy=false;}),0);}
  el.textContent='Actual video: '+(e.video||'starting')+' · audio: '+(e.audio||'—')+' · stream index: '+(e.audioStreamIndex==null?'auto':e.audioStreamIndex)+
    '\nVideo target: '+(e.videoTargetKbps==null?'not applicable / copy':e.videoTargetKbps+' kbps')+' · audio target: '+(e.audioTargetKbps==null?'copy':e.audioTargetKbps+' kbps')+
    '\nAudio: '+(e.audioLanguage||'unknown')+(e.audioTitle?' · '+e.audioTitle:'')+' · delay '+(e.audioDelayMs||0)+' ms'+
    '\nPipeline: decode '+(e.decode||'—')+(e.decodeAccelerator?' ('+e.decodeAccelerator+')':'')+' · filters '+(e.filters||'—')+' · encode '+(e.effectiveEncoder||e.video||'—')+(e.hardwareEncode?' HW':'')+(e.endToEndGpu?' · end-to-end GPU':' · mixed/software processing')+
    (e.fallbackReason?'\nFallback: '+e.fallbackReason:'')+'\n'+(e.reason||'')+(source.warning?'\n'+source.warning:'');
  const select=$('stream_audioTrack'),value=select.value||'-1';select.replaceChildren(new Option('Automatic / preferred language','-1'));
  for(const t of source.tracks||[])if(t.type==='audio')select.add(new Option('#'+t.index+' · '+(t.language||'unknown language')+' · '+t.codec+' · '+t.channels+' ch'+(t.title?' · '+t.title:''),String(t.index)));
  select.value=[...select.options].some(o=>o.value===value)?value:'-1';
  const sub=$('stream_subtitleTrack'),subValue=sub.value||'-1';sub.replaceChildren(new Option('Automatic / preferred language','-1'));
  for(const t of source.tracks||[]){const c=String(t.codec||'').toLowerCase(),ordinary=t.type==='subtitle'&&!t.serviceKind&&(c.includes('subrip')||c.includes('webvtt')||c==='ass'||c==='ssa'||c.includes('pgs')||c.includes('dvd_subtitle')||c.includes('vobsub'));if(ordinary)sub.add(new Option('#'+t.index+' · '+(t.language||'unknown language')+' · '+t.codec+(t.forced?' · forced':'')+(t.default?' · default':'')+(t.sourceKind==='sidecar'?' · sidecar':'')+(t.title?' · '+t.title:''),String(t.index)));}
  sub.value=[...sub.options].some(o=>o.value===subValue)?subValue:'-1';
  updateSubtitleControls();
  const tele=$('stream_teletextService'),teleValue=tele.value||'';tele.replaceChildren(new Option('Auto / preferred page',''));
  for(const t of source.tracks||[])if(t.serviceKind==='teletext'){const v=[t.sourcePid,t.teletextPage,t.language||''].join(':');tele.add(new Option('Page '+t.teletextPage+' · '+(t.language||'unknown language')+' · PID 0x'+Number(t.sourcePid).toString(16),v));}
  if([...tele.options].some(o=>o.value===teleValue))tele.value=teleValue;
  const dvb=$('stream_dvbService'),wantedDvb=[String(streaming.captionPid),String(streaming.captionCompositionPage),streaming.captionLanguage||''].join(':');dvb.replaceChildren(new Option('Auto / preferred language',''));
  for(const t of source.tracks||[])if(t.serviceKind==='dvb-subtitle'){const v=[t.sourcePid,t.compositionPageId,t.language||''].join(':');dvb.add(new Option((t.language||'unknown language')+' · PID 0x'+Number(t.sourcePid).toString(16)+' · page '+t.compositionPageId+(t.hearingImpaired?' · hearing impaired':''),v));}
  const current=[String($('stream_captionPid').value),String($('stream_captionCompositionPage').value),$('stream_captionLanguage').value||''].join(':');if([...dvb.options].some(o=>o.value===current))dvb.value=current;else if([...dvb.options].some(o=>o.value===wantedDvb))dvb.value=wantedDvb;
}
async function refreshStreamTracks(){
  if(!session){showStreamActual(null);return;}
  const id=session,activeId=playback.id;const response=await api('streamSettings',{session:id},'GET');
  if(id===session&&activeId===playback.id)showStreamActual(response.active);
}
let streamingApplyTimer=null,streamingApplySerial=0;
function queueStreamingApply(reason){
  if(!session||!playback.id)return;
  const serial=++streamingApplySerial;if(streamingApplyTimer)clearTimeout(streamingApplyTimer);
  $('streamingResult').textContent='Pending '+reason+'…';
  streamingApplyTimer=setTimeout(()=>{streamingApplyTimer=null;if(serial!==streamingApplySerial)return;commitStreaming(true,serial);},450);
}
async function commitStreaming(now,serial){
  const result=$('streamingResult'),buttons=[$('saveStreaming'),$('applyStreaming')];buttons.forEach(b=>b.disabled=true);
  try{
    const candidate=applyCaptionAuthorityToStreaming(readStreamingForm()),params=Streaming.toParams(candidate,Streaming.browserCapabilities(window));
    if(now&&(!session||!playback.id))throw new Error('Start a recording first, or save these settings for the next recording.');
    if(session){
      const response=await api('streamSettings',Object.assign({session,applyNow:now,timeMs:playback.absoluteTime(),expectedHlsSession:playback.id},params),'POST');
      if(response.stale)throw new Error('Recording changed before settings were applied. Refresh tracks and try again.');
      if(serial!=null&&serial!==streamingApplySerial)return;
      showStreamActual(response.active);
    }
    streaming=Streaming.normalize(Object.assign({},candidate,{audioTrack:-1,subtitleTrack:-1,subtitleMode:candidate.subtitleMode==='track'?'auto':candidate.subtitleMode}));
    try{localStorage.setItem(STREAMING_KEY,JSON.stringify(streaming));}catch(_){}
    if(serial==null||serial===streamingApplySerial)result.textContent=now?'Applied. Restarted at the current reported position; pause intent and native GFX session were retained.':'Saved for the next recording. Existing playback is unchanged.';
  }catch(e){result.textContent=e.message||String(e);setError(e);}
  finally{buttons.forEach(b=>b.disabled=false);}
}
$('saveStreaming').addEventListener('click',()=>commitStreaming(false));$('applyStreaming').addEventListener('click',()=>commitStreaming(true));
for(const id of ['stream_audioOffsetMs','stream_videoKbps']){const el=$(id);if(el)el.addEventListener('input',()=>queueStreamingApply(id==='stream_audioOffsetMs'?'audio delay':'video quality'));}
function commitPrimaryStreamingChoice(reason){
  // Video processing is a primary playback choice. Persist it as soon as the
  // user selects it so closing/reopening Settings cannot silently restore the
  // old value. If media is active, use the existing bounded Apply
  // path so only the media stream restarts and the MiniClient/GFX session stays.
  if(session&&playback.id)queueStreamingApply(reason);
  else{$('streamingResult').textContent='Saving '+reason+'…';commitStreaming(false);}
}
{const el=$('stream_videoMode');if(el)el.addEventListener('change',()=>commitPrimaryStreamingChoice('video processing'));}
for(const id of ['stream_audioTrack','stream_audioCodec','stream_audioChannels','stream_encoder','stream_resolution','stream_fps','stream_deinterlace','stream_keyFrameSeconds','stream_bFrames']){const el=$(id);if(el)el.addEventListener('change',()=>queueStreamingApply('stream setting'));}
$('refreshTracks').addEventListener('click',()=>refreshStreamTracks().catch(e=>{$('streamingResult').textContent=e.message;}));
$('resetStreaming').addEventListener('click',()=>{fillStreamingForm(Streaming.normalize({}));$('streamingResult').textContent='Defaults loaded into the form. Save or Apply now to use them.';});
$('preparePlayerAssets').addEventListener('click',async()=>{
  const button=$('preparePlayerAssets'),result=$('streamingResult');button.disabled=true;
  result.textContent='Preparing pinned mpegts.js and hls.js assets in the server cache…';
  try {
    const data=await requestJson('api/assets','prefetch',{family:'streaming'},'POST',130000);
    if(data.errors&&data.errors.length)throw new Error(data.errors.join('; '));
    if(!data.streamingReady)throw new Error('Player assets are not ready. Check the server cache and internet access.');
    result.textContent='Player assets cached on the server. Future playback starts use the local cache.';
  }catch(e){result.textContent=e.message||String(e);setError(e);}
  finally{button.disabled=false;}
});
function updateSubtitleControls(){const mode=$('stream_subtitleMode').value;$('stream_subtitleTrack').disabled=mode!=='track';$('stream_subtitleLanguage').disabled=mode==='off'||mode==='track';$('stream_subtitleForcedOnly').disabled=mode==='off';$('stream_subtitleOffsetMs').disabled=mode==='off';}
$('stream_subtitleMode').addEventListener('change',updateSubtitleControls);$('stream_subtitleTrack').addEventListener('change',()=>{if($('stream_subtitleTrack').value!=='-1')$('stream_subtitleMode').value='track';updateSubtitleControls();});
function updateCaptionControls(){
  let mode=$('stream_captions').value;
  try{const a=readCaptionAuthorityForm();if(a.mode==='dvb')mode='dvb';else if(a.mode==='cc1'||a.mode==='cc2'){const k=a.mode==='cc2'?'cc2':'cc1',t=a[k+'Type'];mode=t==='teletext'?'teletext':t==='cea708'?'708':t==='cea608'?'608':'off';}else mode='off';}catch(_){}
  const cea=$('stream_captionService');cea.disabled=mode==='teletext'||mode==='dvb'||mode==='off';cea.max=mode==='608'?'4':'63';if(Number(cea.value)>Number(cea.max))cea.value='1';$('stream_teletextService').disabled=mode!=='teletext';$('stream_captionPage').disabled=mode!=='teletext';$('stream_dvbService').disabled=mode!=='dvb';$('stream_captionPid').disabled=mode!=='dvb';$('stream_captionCompositionPage').disabled=mode!=='dvb';$('stream_captionLanguage').disabled=mode!=='teletext'&&mode!=='dvb';
}
$('stream_captions').addEventListener('change',updateCaptionControls);
fillCaptionAuthorityForm();
$('stream_teletextService').addEventListener('change',()=>{const v=$('stream_teletextService').value;if(!v)return;const parts=v.split(':');if(parts.length>=2)$('stream_captionPage').value=parts[1];if(parts.length>=3&&parts[2])$('stream_captionLanguage').value=parts[2];});
$('stream_dvbService').addEventListener('change',()=>{const v=$('stream_dvbService').value;if(!v){$('stream_captionPid').value='-1';$('stream_captionCompositionPage').value='-1';return;}const parts=v.split(':');$('stream_captionPid').value=parts[0]||'-1';$('stream_captionCompositionPage').value=parts[1]||'-1';if(parts[2])$('stream_captionLanguage').value=parts[2];});
function downloadBlob(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
async function refreshDiagnosticDestination(){try{diagnosticStatus=await requestJson('api/support','status',{},'GET',4000);const e=$('diagnosticDestinationStatus');if(e)e.textContent=diagnosticStatus.configured?(diagnosticStatus.writesEnabled?'Server diagnostic destination configured ('+(diagnosticStatus.destinationName||'mounted path')+'). Spool '+(diagnosticStatus.spoolFiles||0)+' file(s).':'Destination configured but server writes are disabled.'):'No server diagnostic destination configured. Browser downloads still work.';return diagnosticStatus;}catch(e){const el=$('diagnosticDestinationStatus');if(el)el.textContent='Diagnostic destination status unavailable: '+(e.message||e);throw e;}}
function diagnosticSnapshot(reason){const cap=captions.snapshot(),ord=ordinarySubtitles.snapshot(),spu=dvdSpu.snapshot(),eff=CaptionAuthority.effective(captionAuthority,captionInventory),ps=playback.snapshot(),st=ps.status||{};return Support.redact({schemaVersion:1,version:'3.2.30',generatedAt:new Date().toISOString(),reason:reason||'manual',userAgent:navigator.userAgent,nativeSession:session,hlsSession:playback.id||failureSnapshot&&failureSnapshot.playback&&failureSnapshot.playback.id||'',ui:{width:uiW,height:uiH,display:displayRect,viewport:{width:innerWidth,height:innerHeight},requested:lastRequestedSize,frame,gfxCount,images:images.size,lastEventAt,menuHint:currentMenuHint,rendererThread:'main (time-sliced)',renderQueued,renderQueuedBytes,unsupportedGfxCount,lastUnsupportedGfx,unifiedGraphicsRequested:!!settings.unifiedGraphics},videoLayout:layoutSnapshot(),failureSnapshot,serverConnection:lastServerConnectionStatus,nativeRecoveryCount,settings,streaming,browserCapabilities:Streaming.browserCapabilities(window),playback:ps,diagnostics:{captions:Support.captionSummary(captionAuthority,eff,captionInventory,cap,ord,spu),broadcastQueue:cap.queue||null,fileQueue:ord.queue||null,dvdSpuQueue:spu.queue||null,effectiveTranscode:Support.effectiveTranscode(st),timestampEpoch:st.playbackEpoch||null,dvd:st.dvd||null,dvdMenuFrame:{generation:dvdFrameGeneration,frozenGeneration:dvdFrozenGeneration,cacheValid:dvdFrameCacheValid,captures:dvdFrameCaptures,promotions:dvdStillPromotions,misses:dvdStillMisses,visible:!dvdStill.hidden}},lastVideoTest,logs:logs.slice(-200)});}
async function refreshServerConnectionDiagnostic(){if(!session)return;try{const st=Support.redact(await requestJson('api/miniclient','status',{session},'GET',3000));if(st&&typeof st==='object'){delete st.clientId;delete st.session;lastServerConnectionStatus=st;}}catch(e){log('Diagnostic native status: '+(e.message||e));}}
async function fetchDiagnosticZip(reason,saveToServer){await refreshServerConnectionDiagnostic();const report=diagnosticSnapshot(reason),failureHls=failureSnapshot&&failureSnapshot.playback&&failureSnapshot.playback.id||'',body=new URLSearchParams({browserJson:JSON.stringify(report),videoTestJson:JSON.stringify(Support.redact(lastVideoTest)),session:session||'',hlsSession:playback.id||failureHls,saveToServer:String(!!saveToServer)}),u=new URL('api/support',location.href);u.searchParams.set('action','report');const r=await fetch(u.href,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body,cache:'no-store'});if(!r.ok)throw new Error((await r.text()).slice(0,500)||('HTTP '+r.status));const state=r.headers.get('X-SageTV-Report-Save')||'not-requested',detail=r.headers.get('X-SageTV-Report-Detail')||'';return{blob:await r.blob(),state,detail};}
async function downloadDiagnosticReport(reason){const out=await fetchDiagnosticZip(reason,settings.saveDiagnosticsToServer);downloadBlob(out.blob,'SageTV-WebPlayer-diagnostics-'+new Date().toISOString().replace(/[:.]/g,'-')+'.zip');log('Diagnostic report downloaded; server save '+out.state+(out.detail?': '+out.detail:''));return out;}
async function saveDiagnosticReportToServer(reason){const out=await fetchDiagnosticZip(reason,true);log('Diagnostic server capture '+out.state+(out.detail?': '+out.detail:''));return out;}
async function automaticDiagnosticCapture(reason){try{if(!diagnosticStatus)await refreshDiagnosticDestination();}catch(_){}if(diagnosticStatus&&diagnosticStatus.configured&&diagnosticStatus.writesEnabled)return saveDiagnosticReportToServer(reason);const out=await downloadDiagnosticReport(reason);log('Automatic diagnostic capture used browser download because no enabled server destination is configured.');return out;}
function exportProfile(){const profile=Support.buildProfile(settings,streaming,captionAuthority);downloadBlob(new Blob([JSON.stringify(profile,null,2)+'\n'],{type:'application/json'}),'SageTV-WebPlayer-profile-v1.json');}
function showProfilePreview(preview){const box=$('profilePreview');if(!box)return;box.textContent=JSON.stringify({schemaVersion:preview.schemaVersion,migrations:preview.migrations,unknownKeys:preview.unknownKeys,willApply:{client:Object.keys(preview.client||{}),streaming:Object.keys(preview.streaming||{}),captions:Object.keys(preview.captions||{})},note:'Client identity and per-session audio/subtitle/PID selections are never imported.'},null,2);$('applyProfile').disabled=false;}
async function loadProfileFile(file){const text=await file.text();pendingProfileImport=Support.previewProfile(text);showProfilePreview(pendingProfileImport);}
function applyImportedProfile(){if(!pendingProfileImport)return;const priorUnified=settings.unifiedGraphics;settings=Core.normalizeSettings(Object.assign({},settings,pendingProfileImport.client||{}));const candidate=Object.assign({},streaming,pendingProfileImport.streaming||{},{audioTrack:-1,subtitleTrack:-1,captionPid:-1,captionCompositionPage:-1});streaming=Streaming.normalize(candidate);captionAuthority=CaptionAuthority.normalize(Object.assign({},captionAuthority,pendingProfileImport.captions||{}));try{localStorage.setItem(STREAMING_KEY,JSON.stringify(streaming));}catch(_){}saveCaptionAuthority();saveSettings();fillStreamingForm();fillCaptionAuthorityForm();pendingProfileImport=null;$('applyProfile').disabled=true;$('profilePreview').textContent='Profile applied. Unrelated existing preferences were preserved.';if(priorUnified!==settings.unifiedGraphics&&session)connect();}
function applySettings(){
  Object.keys(Core.DEFAULTS).forEach(key=>{const el=$(key);if(!el)return;if(el.type==='checkbox')el.checked=settings[key];else el.value=String(settings[key]);});
  statsEl.hidden=!settings.showStats;$('statsToggle').setAttribute('aria-pressed',String(settings.showStats));
  $('remoteStats').setAttribute('aria-pressed',String(settings.showStats));
  const fitLabel={cover:'Fill browser',source:'Fit',fill:'Stretch',zoom:'Zoom','4x3':'4:3','16x9':'16:9'}[settings.videoFit];
  $('aspectToggle').title='Video display: '+fitLabel+' — click to cycle Fill, Fit, Stretch';
  $('aspectToggle').setAttribute('aria-label',$('aspectToggle').title);
  video.muted=settings.mute;videoAspect=settings.videoFit;applyVideoBounds();
  playback.updateBufferSettings();requestResize();
}
Object.keys(Core.DEFAULTS).forEach(key=>{const el=$(key);if(!el)return;el.addEventListener('change',()=>{settings[key]=el.type==='checkbox'?el.checked:el.value;saveSettings();});});
$('unifiedGraphics').addEventListener('change',()=>{if(session){message('Unified graphics setting changed; reconnecting SageTV graphics…');connect();}});
$('resetSettings').addEventListener('click',()=>{settings=Core.normalizeSettings({});saveSettings();setRenderOnly(false);});
function newDiagnosticOperationId(){try{if(crypto&&typeof crypto.randomUUID==='function')return crypto.randomUUID();}catch(_){}return 'support-'+Date.now()+'-'+Math.random().toString(16).slice(2);}
async function runDiagnosticServerAction(action,timeoutMs){if(diagnosticOperationId)throw new Error('A server diagnostic action is already running');const id=newDiagnosticOperationId();diagnosticOperationId=id;$('cancelDiagnosticAction').disabled=false;try{return await requestJson('api/support',action,{confirm:true,operationId:id},'POST',timeoutMs);}finally{if(diagnosticOperationId===id)diagnosticOperationId=null;$('cancelDiagnosticAction').disabled=true;refreshDiagnosticDestination().catch(()=>{});}}
$('exportProfile').addEventListener('click',exportProfile);$('importProfile').addEventListener('click',()=>$('profileFile').click());$('profileFile').addEventListener('change',e=>{const f=e.target.files&&e.target.files[0];if(f)loadProfileFile(f).catch(setError);e.target.value='';});$('applyProfile').addEventListener('click',applyImportedProfile);$('testDiagnosticDestination').addEventListener('click',async()=>{const b=$('testDiagnosticDestination');b.disabled=true;try{const r=await runDiagnosticServerAction('testDestination',10000);$('diagnosticDestinationStatus').textContent=r.ok?'Server destination test passed: '+r.detail:'Server destination test '+(r.stage==='cancelled'?'cancelled':'failed at '+r.stage)+': '+r.detail;}catch(e){setError(e);$('diagnosticDestinationStatus').textContent=e.message||String(e);}finally{b.disabled=false;}});$('retryDiagnosticSpool').addEventListener('click',async()=>{const b=$('retryDiagnosticSpool');b.disabled=true;try{const r=await runDiagnosticServerAction('retrySpool',15000);$('diagnosticDestinationStatus').textContent=r.detail||r.state;}catch(e){setError(e);}finally{b.disabled=false;}});$('cancelDiagnosticAction').addEventListener('click',async()=>{const id=diagnosticOperationId;if(!id)return;$('cancelDiagnosticAction').disabled=true;try{const r=await requestJson('api/support','cancel',{operationId:id},'POST',3000);$('diagnosticDestinationStatus').textContent=r.cancelled?'Cancellation requested; the current bounded file step will finish safely.':'The server action already finished.';}catch(e){setError(e);}});
$('statsToggle').addEventListener('click',()=>{settings.showStats=!settings.showStats;saveSettings();});
$('keyboardToggle').addEventListener('click',()=>{showDrawer('keyboardPanel','keyboardInput');$('keyboardToggle').setAttribute('aria-expanded','true');});
function sendText(){const field=$('keyboardInput'),text=field.value;if(!text)return;queueInput('text',{text}).then(sent=>{if(sent&&field.value===text)field.value='';});field.focus();}
$('keyboardSend').addEventListener('click',sendText);$('keyboardInput').addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();sendText();}});
$('keyboardBackspace').addEventListener('click',()=>sendKey(8,0,8));$('keyboardEnter').addEventListener('click',()=>sendKey(13,0,13));
function setRenderOnly(enabled){document.body.classList.toggle('mc-render-only',!!enabled);settings.renderOnly=!!enabled;try{localStorage.setItem(SETTINGS_KEY,JSON.stringify(settings));}catch(_){}closeRemote();window.scrollTo(0,0);requestResize();}
$('renderOnlyBtn').addEventListener('click',()=>setRenderOnly(!document.body.classList.contains('mc-render-only')));
$('fullscreenBtn').addEventListener('click',async()=>{try{if(!document.fullscreenElement)await $('app').requestFullscreen();else await document.exitFullscreen();}catch(_){setRenderOnly(true);}stage.focus();});
document.addEventListener('fullscreenchange',()=>{document.body.classList.toggle('mc-fullscreen',!!document.fullscreenElement);requestResize();});
$('reconnectBtn').addEventListener('click',connect);
const sageKeys={ArrowLeft:2,ArrowRight:3,ArrowUp:4,ArrowDown:5,Enter:20,Escape:75,Backspace:75,PageUp:55,PageDown:56,Home:28,' ':59,MediaPlayPause:59,MediaPlay:7,MediaPause:6,MediaStop:89,MediaTrackNext:61,MediaTrackPrevious:62};
window.addEventListener('keydown',e=>{
  if(e.key==='Escape'){
    if(!settingsPanel.hidden){e.preventDefault();closeSettings();return;}
    if(!remotePanel.hidden){e.preventDefault();if(['remoteTools','keyboardPanel','videoInfoPanel'].some(id=>!$(id).hidden)){hideRemoteDrawers();$('nav_options').focus({preventScroll:true});}else closeRemote();return;}
    if(document.body.classList.contains('mc-render-only')){e.preventDefault();setRenderOnly(false);return;}
  }
  const drawer=!remotePanel.hidden?['remoteTools','keyboardPanel','videoInfoPanel'].map($).find(el=>!el.hidden):null;
  const modal=!settingsPanel.hidden?settingsPanel:drawer||(!remotePanel.hidden?remotePanel:null);
  if(modal===settingsPanel&&['ArrowUp','ArrowDown'].includes(e.key)&&e.target&&!/INPUT|TEXTAREA|SELECT/.test(e.target.tagName)){const focusable=[...settingsPanel.querySelectorAll('button,input,select,summary,a[href]')].filter(el=>!el.disabled&&el.getClientRects().length);const i=focusable.indexOf(document.activeElement);if(i>=0&&focusable.length){e.preventDefault();focusable[(i+(e.key==='ArrowDown'?1:-1)+focusable.length)%focusable.length].focus({preventScroll:false});return;}}
  if(e.key==='Tab'&&modal){
    const focusable=[...modal.querySelectorAll('button,input,select,summary,a[href]')].filter(el=>!el.disabled&&el.getClientRects().length);
    if(!focusable.length)return;const first=focusable[0],last=focusable[focusable.length-1];
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}return;
  }
  if(!settingsPanel.hidden||drawer)return;
  if(e.target&&(/INPUT|TEXTAREA|SELECT/.test(e.target.tagName)||(/BUTTON|A/.test(e.target.tagName)&&(!remotePanel.contains(e.target)||e.key==='Enter'||e.key===' '))))return;
  if(e.ctrlKey||e.metaKey||e.altKey)return;
  if(settings.inputMode==='text'){
    if(e.key==='Tab')return;
    if(e.key.length===1||['Enter','Backspace','Delete','ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){
      e.preventDefault();sendKey(e.keyCode||e.which||0,e.shiftKey?1:0,e.key.length===1?e.key.charCodeAt(0):e.key==='Enter'?13:e.key==='Backspace'?8:0);
    }return;
  }
  let command=sageKeys[e.key];if(command==null&&/^[0-9]$/.test(e.key))command=30+Number(e.key);
  if(command==null)command={g:18,i:24,o:29,r:25,m:26,s:89,l:42,t:15}[String(e.key||'').toLowerCase()];
  if(command!=null){e.preventDefault();if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','PageUp','PageDown'].includes(e.key)){keyRepeat.press(e.key,command);}else if(!e.repeat)sendCommand(command);return;}
  if(e.key&&e.key.length===1){e.preventDefault();sendKey(e.keyCode||e.which||0,e.shiftKey?1:0,e.key.charCodeAt(0));}
});
window.addEventListener('keyup',e=>keyRepeat.release(e.key));
$('autoplayBtn').addEventListener('click',()=>{playback.control('play',null,true);sendCommand(7);});
$('seekRange').addEventListener('input',()=>{seeking=true;$('timelineTime').textContent=Core.timeText(Number($('seekRange').value)*1000)+' / '+Core.timeText(playback.durationMs);});
$('seekRange').addEventListener('change',()=>{
  const timeMs=Math.max(0,Number($('seekRange').value)*1000);seeking=false;
  if(!session||!playback.id)return;gesture.cancel();
  queueInput('seek',{timeMs,expectedHlsSession:playback.id}).then(sent=>{if(!sent)setError('Seek was not delivered');});
});
function updateStats(){
  const snap=playback.snapshot(),st=snap.status||{};
  statsEl.hidden=!settings.showStats;$('autoplayBtn').hidden=!snap.blocked;
  const phase=snap.recovering?'recovering':snap.paused?'paused':snap.rebuffering?'buffering':snap.starting?'starting':snap.id?'playing':'idle';
  $('bufferStatus').textContent=snap.id?snap.ahead.toFixed(1)+'s buffer · '+phase:'';
  const produced=Number(st.hlsSeconds)||0,relative=Math.max(0,snap.timeMs/1000-snap.startSeconds),serverAhead=Math.max(0,produced-relative);
  const targetText=snap.hlsVersion||snap.workerEnabled?snap.bufferTargetSeconds+'s target':'browser-managed target';
  const threads=st.threading||{},threadText=key=>threads[key]?'active':'idle';
  const ranges=[];try{for(let i=0;i<Math.min(video.buffered.length,6);i++)ranges.push(video.buffered.start(i).toFixed(1)+'–'+video.buffered.end(i).toFixed(1));}catch(_){}
  statsEl.textContent=`MiniClient 3.2.30 · ${uiW}×${uiH} native → ${Math.round(displayRect.width)}×${Math.round(displayRect.height)} displayed · ${phase}\n`+
    `Time ${Core.timeText(snap.timeMs)} / ${Core.timeText(snap.durationMs)} · browser ahead ${snap.ahead.toFixed(1)}s / ${targetText} · ${snap.bufferPhase}\n`+
    `Server ahead ${serverAhead.toFixed(1)}s · produced ${produced.toFixed(1)}s · segments ${st.segments||0} · fill ${Number(st.recentFillRate||0).toFixed(2)}× recent / ${Number(st.hlsFillRate||0).toFixed(2)}× average\n`+
    `FFmpeg ${st.encoder||'—'} · ${st.encoder==='copy'?'video copied (no encoding/decoding)':(st.hardware?'hardware':'software')+' encode / '+(st.hardwareDecode?'hardware':'software')+' decode'+(st.decodeAccelerator?' ('+st.decodeAccelerator+')':'')+' / '+(st.hardwareFilters?'hardware':'software')+' filters'} · ${st.inputMode||'—'}\n`+
    `First segment ${st.firstSegmentMs==null?'—':(st.firstSegmentMs/1000).toFixed(2)+'s'} · first progress ${snap.firstFrameMs==null?'—':(snap.firstFrameMs/1000).toFixed(2)+'s'} · recoveries ${snap.recoveries}/6\n`+
    `Frames ${frame} · GFX ${gfxCount} · images ${images.size} · video ${video.videoWidth||0}×${video.videoHeight||0} · ranges ${ranges.join(', ')||'none'}\n`+
    `Loader ${snap.loaderWakeups} refill wakeups · ${snap.transport==='mpegts'?'mpegts.js '+snap.tsVersion:'HLS '+(snap.hlsVersion||'native')} · worker request ${snap.workerRequested?'on':'off'} · observed ${snap.workerObserved} · GFX main thread (time-sliced)\n`+
    `Producer monitor ${threadText('producerMonitor')} · feeder ${threadText('feeder')} · stderr ${threadText('stderrReader')} · last segment ${st.lastSegmentAgeMs==null?'—':(st.lastSegmentAgeMs/1000).toFixed(1)+'s ago'}\n`+
    `Producer ${st.running?'running':'stopped'} · ENDLIST ${!!st.endList} · ${st.activeRecording?'growing recording':'recorded media'}`+
    `\nAudio ${(st.streamSettings&&st.streamSettings.effective&&st.streamSettings.effective.audio)||'—'} · local CC ${captions.snapshot().mode} · packets ${captions.snapshot().received}`+
    (lastError?'\n'+lastError:'');
  const range=$('seekRange');range.disabled=!snap.id||!snap.durationMs;
  if(!seeking){range.max=String(Math.max(1,snap.durationMs/1000));range.value=String(Math.min(snap.durationMs/1000,snap.timeMs/1000));$('timelineTime').textContent=snap.id?Core.timeText(snap.timeMs)+' / '+Core.timeText(snap.durationMs):'No video';}
  $('timelineBuffer').textContent=`Browser ${snap.ahead.toFixed(1)}s / ${targetText} · server ${serverAhead.toFixed(1)}s ahead · ${st.encoder||'waiting for FFmpeg'}${st.activeRecording?' · recording is growing':''}`;
}
$('diagnosticsBtn').addEventListener('click',async()=>{const button=$('diagnosticsBtn');button.disabled=true;try{await downloadDiagnosticReport('manual-download');}catch(e){setError(e);}finally{button.disabled=false;}});
$('connectionDiagnosticsBtn').addEventListener('click',()=>downloadDiagnosticReport('connection-failure').catch(setError));
video.addEventListener('resize',layoutStage);
video.addEventListener('loadedmetadata',()=>{layoutStage();browserState();if(session)api('mediaUpdate',{session},'POST').catch(setError);});
video.addEventListener('timeupdate',()=>playback.progress());video.addEventListener('playing',()=>playback.promoteBuffer());video.addEventListener('canplay',()=>playback.maybePlay());
if('ResizeObserver' in window)new ResizeObserver(()=>{$('localCaptions').style.fontSize=Math.max(8,Math.min(36,videoBounds.clientHeight*.047))+'px';syncDvbCanvasLayout();}).observe(videoBounds);
video.addEventListener('volumechange',browserState);video.addEventListener('ended',()=>playback.ended().catch(setError));
window.addEventListener('resize',requestResize);if(window.visualViewport)window.visualViewport.addEventListener('resize',requestResize);if('ResizeObserver' in window)new ResizeObserver(requestResize).observe(stage);
function retirePageSession(reason){
  if(pageRetired)return;pageRetired=true;stopped=true;connectionGeneration++;gesture.cancel();clearRepeats();keyRepeat.cancel();if(eventController)eventController.abort();captions.stop();ordinarySubtitles.stop();dvdSpu.stop();playback.stop();
  const id=session;session='';log('Page session retired: '+reason);if(id)try{navigator.sendBeacon(new URL('api/miniclient?action=stop',location.href),new URLSearchParams({session:id}));}catch(_){}
}
window.addEventListener('pagehide',()=>retirePageSession('pagehide'));
window.addEventListener('beforeunload',()=>retirePageSession('beforeunload'));
window.addEventListener('pageshow',e=>{if(e.persisted&&pageRetired){pageRetired=false;stopped=false;connect();}});
if('serviceWorker' in navigator)navigator.serviceWorker.register('sw.js').then(reg=>reg.update()).catch(()=>{});
fillStreamingForm();applySettings();if(settings.renderOnly)setRenderOnly(true);setInterval(updateStats,750);refreshDiagnosticDestination().catch(()=>{});connect();
})();
