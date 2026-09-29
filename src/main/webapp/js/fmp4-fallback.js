(function(global){
  'use strict';

  // Compatibility playback uses FFmpeg HLS, served by the existing SageTV Jetty
  // web server.  This module deliberately keeps the browser and the transcoder
  // decoupled: FFmpeg may run ahead, hls.js/native HLS consumes the playlist, and
  // a starvation watchdog pauses/rebuffers instead of allowing playback to fall
  // off the end of the generated window.
  let video=null, hls=null, current=null, sessionId='', statusTimer=null, healthTimer=null, lastStatus=null;
  let streamState='idle', lastError='', nativeHls=false, hlsJsLoaded=false, hlsLoadPromise=null;
  let waitingSince=0,lastServerSeconds=0,lastServerProgressAt=0,recoveryCount=0,recovering=false;
  const START_BUFFER_SECONDS=12;
  const RESUME_BUFFER_SECONDS=6;
  const STALL_RESTART_MS=18000;
  const MAX_RECOVERIES=5;

  function ensure(container){
    if(!video){
      video=document.createElement('video');
      video.controls=false;video.playsInline=true;video.className='compat-video';
      video.setAttribute('x-webkit-airplay','allow');video.disableRemotePlayback=false;video.preload='auto';
      video.addEventListener('waiting',()=>{if(!waitingSince)waitingSince=Date.now();streamState='buffering';});
      video.addEventListener('stalled',()=>{if(!waitingSince)waitingSince=Date.now();streamState='buffering';});
      video.addEventListener('playing',()=>{waitingSince=0;streamState='streaming';});
      video.addEventListener('canplay',()=>{if(bufferAhead()>=1)waitingSince=0;});
    }
    if(video.parentNode!==container){container.innerHTML='';container.appendChild(video);}return video;
  }
  async function jsonFetch(url,options){const r=await fetch(url,Object.assign({cache:'no-store'},options||{}));const text=await r.text();let data={};try{data=text?JSON.parse(text):{};}catch(_){data={error:text||`HTTP ${r.status}`};}if(!r.ok)throw new Error(data.error||`HTTP ${r.status}`);return data;}
  function apiUrl(action,opts){const u=new URL('api/hls',location.href);u.searchParams.set('action',action);if(opts)Object.keys(opts).forEach(k=>{if(opts[k]!==undefined&&opts[k]!==null)u.searchParams.set(k,String(opts[k]));});return u.href;}
  function loadHlsJs(){if(global.Hls){hlsJsLoaded=true;return Promise.resolve(global.Hls);}if(hlsLoadPromise)return hlsLoadPromise;hlsLoadPromise=new Promise((resolve,reject)=>{const s=document.createElement('script');s.src='vendor/hlsjs/hls.min.js';s.async=true;s.onload=()=>{if(global.Hls){hlsJsLoaded=true;resolve(global.Hls);}else reject(new Error('hls.js loaded but window.Hls is unavailable'));};s.onerror=()=>reject(new Error('Could not load pinned hls.js from SageTV vendor cache'));document.head.appendChild(s);});return hlsLoadPromise;}
  function destroyClient(){if(hls){try{hls.destroy();}catch(_){}hls=null;}if(video){try{video.pause();}catch(_){}try{video.removeAttribute('src');video.load();}catch(_){}}nativeHls=false;}
  function stopTimers(){if(statusTimer){clearInterval(statusTimer);statusTimer=null;}if(healthTimer){clearInterval(healthTimer);healthTimer=null;}}
  function bufferAhead(){if(!video||!video.buffered||!video.buffered.length)return 0;try{for(let i=0;i<video.buffered.length;i++){if(video.currentTime>=video.buffered.start(i)-.25&&video.currentTime<=video.buffered.end(i)+.25)return Math.max(0,video.buffered.end(i)-video.currentTime);}return 0;}catch(_){return 0;}}
  async function pollStatus(){
    if(!sessionId)return;
    try{
      lastStatus=await jsonFetch(apiUrl('status',{session:sessionId}));
      if(lastStatus.error)lastError=lastStatus.error;
      const ss=Number(lastStatus.hlsSeconds)||0;
      if(ss>lastServerSeconds+.20){lastServerSeconds=ss;lastServerProgressAt=Date.now();}
      if(lastStatus.running)streamState=lastStatus.playlistReady?(bufferAhead()<.5?'buffering':'streaming'):'starting';
      else if(lastStatus.endList)streamState='complete';else if(lastStatus.error)streamState='error';else streamState='stopped';
    }catch(e){lastError=e.message||String(e);}
  }
  function startStatusTimer(){stopTimers();lastServerProgressAt=Date.now();pollStatus();statusTimer=setInterval(pollStatus,1000);healthTimer=setInterval(healthTick,1000);}
  async function waitForServerBuffer(minSeconds,timeoutMs){
    const deadline=Date.now()+Math.max(1000,timeoutMs||30000);let last=null;
    while(Date.now()<deadline&&sessionId){
      try{last=await jsonFetch(apiUrl('status',{session:sessionId}));lastStatus=last;const s=Number(last.hlsSeconds)||0;if(last.playlistReady&&(s>=minSeconds||last.endList||!last.running))return last;if(last.error&&!last.running)throw new Error(last.error);}catch(e){lastError=e.message||String(e);}
      await new Promise(r=>setTimeout(r,300));
    }
    return last;
  }
  function playElement(){const p=video.play();if(p&&p.catch)return p.catch(e=>{lastError=e.message||String(e);throw e;});return Promise.resolve();}

  async function recoverFromStarvation(reason){
    if(recovering||!current||recoveryCount>=MAX_RECOVERIES)return;
    recovering=true;recoveryCount++;
    const abs=absoluteTime();lastError=`${reason}; recovery ${recoveryCount}/${MAX_RECOVERIES}`;streamState='recovering';
    try{
      // Re-start the FFmpeg/HLS session from the actual absolute playback time.
      // This is intentionally independent of the outer player controller so a
      // transient server/feed failure cannot strand the browser at the end of a
      // short HLS window.
      current.start=Math.max(0,abs-.25);
      await play(current,{internalRecovery:true});
    }catch(e){lastError=e.message||String(e);streamState='error';}finally{recovering=false;}
  }
  async function healthTick(){
    if(!current||!video||recovering)return;
    const ahead=bufferAhead(),s=lastStatus||{},now=Date.now();
    if(ahead>=RESUME_BUFFER_SECONDS&&video.paused&&waitingSince){try{if(hls)hls.startLoad(video.currentTime);await playElement();waitingSince=0;}catch(_){}}
    if(ahead<.5&&s.running){if(!waitingSince)waitingSince=now;streamState='rebuffering';try{if(!video.paused)video.pause();}catch(_){}try{if(hls)hls.startLoad(video.currentTime);}catch(_){} }
    const starved=waitingSince&&now-waitingSince>STALL_RESTART_MS;
    const producerStalled=s.running&&now-lastServerProgressAt>STALL_RESTART_MS;
    const prematureStop=!s.running&&!s.endList&&current&&absoluteTime()+2<(Number(current.duration)||0);
    const shortEnd=s.endList&&current&&absoluteTime()+2<(Number(current.duration)||0)&&Number(s.hlsSeconds||0)<Math.max(5,(Number(current.duration)||0)-Number(current.start||0)-2);
    if(starved&&(producerStalled||prematureStop||shortEnd))await recoverFromStarvation(producerStalled?'FFmpeg/HLS producer stopped advancing':'HLS source ended before SageTV recording duration');
  }

  async function attachHls(url){
    nativeHls=false;const canNative=!!video.canPlayType('application/vnd.apple.mpegurl');const mse=typeof global.MediaSource!=='undefined';
    if(canNative&&(!mse||/iP(hone|ad|od)|Safari/i.test(navigator.userAgent)&&!/Chrome|Chromium|Edg/i.test(navigator.userAgent))){
      nativeHls=true;streamState='native-hls';video.src=url;video.load();await new Promise((resolve,reject)=>{let done=false;const ok=()=>{if(done)return;done=true;cleanup();resolve();};const bad=()=>{if(done)return;done=true;cleanup();reject(new Error('Native HLS could not load the compatibility playlist'));};const cleanup=()=>{video.removeEventListener('loadedmetadata',ok);video.removeEventListener('canplay',ok);video.removeEventListener('error',bad);};video.addEventListener('loadedmetadata',ok);video.addEventListener('canplay',ok);video.addEventListener('error',bad);setTimeout(ok,10000);});await playElement();return;
    }
    const Hls=await loadHlsJs();if(!Hls.isSupported()){if(canNative){nativeHls=true;video.src=url;video.load();await playElement();return;}throw new Error('This browser does not support MediaSource HLS playback');}
    streamState='hlsjs-starting';
    hls=new Hls({lowLatencyMode:false,startPosition:0,liveSyncDurationCount:6,liveMaxLatencyDurationCount:240,maxLiveSyncPlaybackRate:1.0,maxBufferLength:180,maxMaxBufferLength:360,backBufferLength:90,liveBackBufferLength:90,maxBufferHole:2,maxSeekHole:3,manifestLoadingMaxRetry:60,levelLoadingMaxRetry:60,fragLoadingMaxRetry:48,manifestLoadingRetryDelay:500,levelLoadingRetryDelay:500,fragLoadingRetryDelay:500,fragLoadingMaxRetryTimeout:30000,enableWorker:true});
    await new Promise((resolve,reject)=>{let ready=false;const fail=(event,data)=>{if(!data)return;lastError=`hls.js ${data.type||'error'} ${data.details||''}`.trim();if(data.details==='bufferStalledError'||data.details==='bufferSeekOverHole'){if(!waitingSince)waitingSince=Date.now();streamState='buffering';try{hls.startLoad(video.currentTime);}catch(_){}return;}if(!data.fatal)return;if(data.type===Hls.ErrorTypes.NETWORK_ERROR){try{hls.startLoad(video.currentTime);}catch(_){}return;}if(data.type===Hls.ErrorTypes.MEDIA_ERROR){try{hls.recoverMediaError();}catch(_){}return;}if(!ready)reject(new Error(lastError));};hls.on(Hls.Events.ERROR,fail);hls.on(Hls.Events.MANIFEST_PARSED,()=>{ready=true;streamState='streaming';resolve();});hls.on(Hls.Events.FRAG_BUFFERED,()=>{if(bufferAhead()>=1)waitingSince=0;});hls.loadSource(url);hls.attachMedia(video);setTimeout(()=>{if(!ready)reject(new Error(lastError||'Timed out waiting for HLS manifest'));},25000);});await playElement();
  }

  async function stopServerSession(id){if(!id)return;try{await jsonFetch(apiUrl('stop',{session:id}),{method:'POST'});}catch(_){}}
  async function play(o,flags){
    if(!o||!o.container)throw new Error('Compatibility container is required');
    const internal=flags&&flags.internalRecovery;
    const old=sessionId;sessionId='';stopTimers();destroyClient();if(old)await stopServerSession(old);
    current=Object.assign({},o);lastError='';lastStatus=null;streamState='starting';if(!internal)recoveryCount=0;waitingSince=0;lastServerSeconds=0;lastServerProgressAt=Date.now();
    const v=ensure(o.container),start=Math.max(0,Number(o.start)||0);
    const data=await jsonFetch(apiUrl('start',{id:o.id,segment:o.segment||0,audio:o.audio||0,start}),{method:'POST'});sessionId=data.session||'';lastStatus=data;if(!sessionId||!data.playlistUrl)throw new Error('Server did not create an HLS compatibility session');startStatusTimer();
    // Do not let playback immediately catch an encoder that is only operating at
    // roughly real-time speed.  Build a meaningful server-side cushion first.
    await waitForServerBuffer(Math.min(START_BUFFER_SECONDS,Math.max(3,(Number(o.duration)||START_BUFFER_SECONDS)-start)),45000);
    const url=new URL(data.playlistUrl,location.href).href;try{await attachHls(url);}catch(e){lastError=e.message||String(e);streamState='error';await stopServerSession(sessionId);throw e;}return v;
  }
  async function stop(keepElement){stopTimers();const old=sessionId;sessionId='';destroyClient();if(old)stopServerSession(old);current=null;lastStatus=null;streamState='idle';lastError='';waitingSince=0;recovering=false;if(!keepElement&&video&&video.parentNode){try{video.parentNode.removeChild(video);}catch(_){}}}
  async function seek(seconds){if(!current)return;current.start=Math.max(0,Number(seconds)||0);return play(current);}
  function pause(){if(video)video.pause();streamState='paused';waitingSince=0;}
  async function resume(){if(video){if(hls)try{hls.startLoad(video.currentTime);}catch(_){}await playElement();streamState='streaming';}}
  function setVolume(v){if(video)video.volume=Math.max(0,Math.min(1,Number(v)||0));}
  function element(){return video;}
  function absoluteTime(){return Math.max(0,Number(current&&current.start)||0)+(video?Math.max(0,Number(video.currentTime)||0):0);}
  function getBufferInfo(){let start=0,end=0,ahead=0;if(video&&video.buffered&&video.buffered.length){try{let idx=video.buffered.length-1;for(let i=0;i<video.buffered.length;i++)if(video.currentTime>=video.buffered.start(i)-.25&&video.currentTime<=video.buffered.end(i)+.25){idx=i;break;}start=video.buffered.start(idx);end=video.buffered.end(idx);ahead=Math.max(0,end-(Number(video.currentTime)||0));}catch(_){}}const s=lastStatus||{};return{start,end,ahead,absoluteOffset:Number(current&&current.start)||0,absoluteTime:absoluteTime(),bytesReceived:Number(s.bytesProduced)||0,state:streamState,mse:!!hls,nativeHls,hlsJs:!!hls,hlsJsLoaded,appendQueue:0,error:lastError||s.error||'',session:sessionId,serverSegments:Number(s.segments)||0,serverSeconds:Number(s.hlsSeconds)||0,serverBytes:Number(s.bytesProduced)||0,bytesInput:Number(s.bytesInput)||0,ffmpegRunning:!!s.running,playlistReady:!!s.playlistReady,endList:!!s.endList,encoder:s.encoder||'',accelerator:s.accelerator||'',hardware:!!s.hardware,hardwareDecode:!!s.hardwareDecode,softwareFallback:!!s.softwareFallback,inputMode:s.inputMode||'',stderrTail:s.stderrTail||'',recoveryCount};}

  global.SageFmp4Fallback={play,stop,seek,pause,resume,setVolume,element,getBufferInfo,absoluteTime};
})(window);
