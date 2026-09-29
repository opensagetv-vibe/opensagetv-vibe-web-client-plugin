(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const State = window.SageSessionState;
  const Library = window.SageLibraryUtils;
  let media = null;
  let segment = 0;
  let rawUrl = null;
  let captionParser = null;
  let mode = null;
  let statsTimer = null;
  let compatBufferTimer = null;
  let transcoder = {available:false};
  let compatOffset = 0;
  let suppressSeek = false;
  let wakeLock = null;
  let watchdog = null;
  let autoFallbackArmed = false;
  let autoFallbackInProgress = false;
  let serverInfo = null;
  let assetInfo = null;
  let wasmProbe = null;
  let lastDecoderStats = null;
  let lastCaptionStats = null;
  let currentTracks = {audio:[],video:[],subtitle:[]};
  let lastResumeSaveAt = 0;
  let liveReconnectTimer = null;
  let liveReconnectAttempts = 0;
  let lastProgressAt = Date.now();
  let lastProgressSeconds = 0;
  let paused = false;
  let libraryItems = [];
  let lastLibraryResult = [];
  const diagnosticEvents = [];
  let guideStart = Date.now() - 30 * 60 * 1000;
  let guideItems = [];
  let commercialController = null;
  let commercialInfo = {available:false,count:0,markers:[],source:''};
  let lastCommercialRefreshAt = 0;
  let dvrInfo = {scheduled:[],conflicts:[]};
  let lastServerSyncAt = 0;

  function diagEvent(type, data) {
    diagnosticEvents.push({at:new Date().toISOString(),type,data:data == null ? null : sanitize(data)});
    if (diagnosticEvents.length > 250) diagnosticEvents.splice(0, diagnosticEvents.length - 250);
  }
  function sanitize(value) {
    try { return JSON.parse(JSON.stringify(value, (k,v)=>typeof v==='bigint'?v.toString():v)); }
    catch (_) { return String(value); }
  }
  function setStatus(text, error) {
    $('playerStatus').textContent = text;
    $('playerStatus').classList.toggle('error', !!error);
    if (error) diagEvent('error', text);
  }
  function setMode(next, label) {
    mode = next;
    const b = $('modeBadge'); b.textContent = label || (next || 'IDLE'); b.dataset.mode = next || '';
    const stage = $('playerStage');
    stage.classList.remove('native','wasm','compat','playing');
    if (next === 'native') stage.classList.add('native','playing');
    if (next === 'wasm') stage.classList.add('wasm','playing');
    if (next === 'compat') stage.classList.add('compat','playing');
    if (!next) $('stageMessage').textContent = media ? 'Ready.' : 'Load a recording to begin.';
  }
  function formatTime(sec) {
    sec = Math.max(0, Math.floor(Number(sec)||0)); const h=Math.floor(sec/3600),m=Math.floor((sec%3600)/60),s=sec%60;
    return h ? `${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}` : `${m}:${String(s).padStart(2,'0')}`;
  }
  function currentFile() { return media && media.files ? media.files.find(x=>Number(x.segment)===Number(segment)) : null; }
  function likelyNeedsWasm() { const f=(media&&media.format||'').toLowerCase(); return /mpeg2-video|mpeg-2 video|mpeg2/.test(f) || /dolby digital|ac-3|ac3/.test(f); }
  function expectedFps() { const m=(media&&media.format||'').match(/@([0-9]+(?:\.[0-9]+)?)fps/i); return m?Number(m[1]):29.97; }
  function playbackRawUrl(liveEdge) {
    if (!media) return null;
    if (media.recording) return `live.ts?id=${encodeURIComponent(media.id)}&${liveEdge?'start=live':'start=begin'}`;
    return `stream.ts?id=${encodeURIComponent(media.id)}&segment=${encodeURIComponent(segment)}`;
  }
  function getSettings() { return State ? State.getSettings() : {}; }
  function persistSetting(name, value) { if (State) State.updateSettings({[name]:value}); }
  function playbackDurationSeconds() { return media && !media.recording ? Math.max(0,Number(media.duration||0)/1000) : 0; }
  function resumeEntry() { return media && !media.recording && State ? State.getResume(media.id, segment) : null; }
  function desiredStartSeconds() { const s=getSettings(); if(s.resumeEnabled===false)return 0; const r=resumeEntry(); if(r)return Math.max(0,Number(r.position)||0); return media?Math.max(0,Number(media.serverResumeSeconds)||0):0; }
  function updateResumeUi() {
    const bar=$('resumeBar'), text=$('resumeText');
    if (!media || media.recording || !$('resumeEnabled').checked) { bar.hidden=true; return; }
    const r=resumeEntry();
    const server=media?Math.max(0,Number(media.serverResumeSeconds)||0):0;
    if (!r && !(server>15)) { bar.hidden=true; return; }
    const pos=r?Number(r.position)||0:server, dur=r?Number(r.duration)||playbackDurationSeconds():playbackDurationSeconds(), source=r?'browser':'SageTV';
    bar.hidden=false; text.textContent=`${source} resume available at ${formatTime(pos)}${dur?` of ${formatTime(dur)}`:''}. Play buttons will start there.`;
  }
  function saveResume(seconds, force) {
    if (!State || !media || media.recording || !$('resumeEnabled').checked) return;
    const now=Date.now(); if(!force && now-lastResumeSaveAt<5000)return; lastResumeSaveAt=now;
    State.saveResume(media.id,segment,seconds,playbackDurationSeconds(),media.title||''); updateResumeUi();
  }
  function clearResume() { if(State&&media)State.clearResume(media.id,segment); updateResumeUi(); }
  function markPlayed() { if(State&&media)State.markPlayed({id:media.id,segment,title:media.title,episode:media.episode}); }

  async function jsonFetch(url, options) {
    const r=await fetch(url,Object.assign({cache:'no-store'},options||{})); let j;
    try{j=await r.json();}catch(_){throw new Error(`HTTP ${r.status}`)}
    if(!r.ok)throw new Error(j.error||`HTTP ${r.status}`); return j;
  }
  async function checkServer() {
    try { serverInfo=await jsonFetch('api/health'); const j=serverInfo; $('health').textContent=`OK — plugin ${j.version}\nsagex: ${j.sagex}\nlibmedia: ${j.libmediaVersion}\nassets: ${j.decoderAssetsCached}/${j.decoderAssetsExpected}\nFFmpeg fallback: ${j.transcoderAvailable?'available':'not configured'}`; }
    catch(e){serverInfo={error:e.message};$('health').textContent=`Unavailable — ${e.message}`;}
  }
  async function checkAssets() {
    try { assetInfo=await jsonFetch('api/assets'); const j=assetInfo; $('assetStatus').textContent=`Pinned libmedia ${j.libmediaVersion}\n${j.cachedCount}/${j.expectedCount} files cached\n${(j.cachedBytes/1048576).toFixed(1)} MiB\n${j.ready?'Offline-ready':'On-demand cache; server internet may be needed'}`; return j; }
    catch(e){assetInfo={error:e.message};$('assetStatus').textContent=`Asset cache error: ${e.message}`;return null;}
  }
  async function checkTranscoder() {
    try { transcoder=await jsonFetch('api/transcoder'); $('transcoderStatus').textContent=transcoder.available?`Available\n${transcoder.hardware?'Hardware':'Software'}: ${transcoder.videoEncoder||'unknown'}${transcoder.accelerator?` (${transcoder.accelerator})`:''}\nDecode: ${transcoder.hardwareDecode?'hardware'+(transcoder.decodeAccelerator?` (${transcoder.decodeAccelerator})`:''):'software'}\nVideo processing: ${transcoder.hardwareFilters?'hardware':'software when required'}\n${transcoder.detail}`:`Not configured\n${transcoder.detail}`; }
    catch(e){transcoder={available:false,error:e.message};$('transcoderStatus').textContent=`Status error: ${e.message}`;}
    updateButtons();
  }

  $('prefetchAssets').addEventListener('click', async () => {
    const btn=$('prefetchAssets'); btn.disabled=true; $('assetStatus').textContent='Downloading pinned decoder assets to the SageTV cache…';
    try { assetInfo=await jsonFetch('api/assets',{method:'POST'}); const j=assetInfo; $('assetStatus').textContent=`${j.cachedCount}/${j.expectedCount} cached (${(j.cachedBytes/1048576).toFixed(1)} MiB)\n${j.ready?'Offline-ready':'Some downloads failed'}${j.errors&&j.errors.length?'\n'+j.errors.join('\n'):''}`; diagEvent('asset-prefetch',{ready:j.ready,cachedCount:j.cachedCount,expectedCount:j.expectedCount}); }
    catch(e){$('assetStatus').textContent=`Prefetch failed: ${e.message}`;diagEvent('asset-prefetch-error',e.message);} finally {btn.disabled=false;}
  });

  async function libraryAction(id, action, extra) {
    const q=new URLSearchParams({id:String(id),action:String(action)});Object.entries(extra||{}).forEach(([k,v])=>q.set(k,String(v)));
    return jsonFetch(`api/library?${q.toString()}`,{method:'POST'});
  }
  async function syncSageProgress(seconds, force) {
    if(!media||media.recording||!$('syncSageProgress').checked)return false;
    const now=Date.now();if(!force&&now-lastServerSyncAt<30000)return false;lastServerSyncAt=now;
    seconds=Math.max(0,Number(seconds)||0);const duration=playbackDurationSeconds();
    try{
      if(duration>0&&(seconds>=duration-45||seconds/duration>=.95)){
        await libraryAction(media.id,'watched');media.watched=true;media.watchedCompletely=true;media.serverResumeSeconds=duration;diagEvent('sage-progress-watched',{seconds,duration});
      }else if(seconds>=5){
        await libraryAction(media.id,'progress',{seconds:seconds.toFixed(3)});media.serverResumeSeconds=Math.max(Number(media.serverResumeSeconds)||0,seconds);diagEvent('sage-progress-sync',{seconds});
      }
      return true;
    }catch(e){diagEvent('sage-progress-error',e.message||String(e));return false;}
  }
  async function setSageWatched(rec, watched) {
    try{await libraryAction(rec.id,watched?'watched':'unwatched');rec.watched=!!watched;rec.watchedCompletely=!!watched;if(!watched)rec.serverResumeSeconds=0;if(media&&Number(media.id)===Number(rec.id)){media.watched=rec.watched;media.watchedCompletely=rec.watchedCompletely;if(!watched)media.serverResumeSeconds=0;}await loadRecordings();setStatus(watched?'Marked watched in SageTV.':'Marked unwatched in SageTV.');return true;}catch(e){setStatus(`SageTV watched update failed: ${e.message}`,true);return false;}
  }
  async function deleteRecording(rec, withoutPrejudice) {
    if(!rec||rec.recording)return false;
    const suffix=withoutPrejudice?' This will not affect SageTV Intelligent Recording.':' SageTV may use the deletion as Intelligent Recording feedback.';
    if(!window.confirm(`Permanently delete “${rec.title||'this recording'}” from SageTV and disk?${suffix}`))return false;
    try{await libraryAction(rec.id,withoutPrejudice?'deleteWithoutPrejudice':'delete',{confirm:'DELETE'});if(media&&Number(media.id)===Number(rec.id)){await stopPlayback({keepResume:false});media=null;rawUrl=null;updateButtons();$('mediaSummary').innerHTML='';$('mediaInfo').textContent='Recording deleted.';}if(State)State.clearResume(rec.id,0);await loadRecordings();await loadDvr();setStatus('Recording permanently deleted by SageTV.');return true;}catch(e){setStatus(`Delete failed: ${e.message}`,true);return false;}
  }

  function renderDvrList(id, items, conflict) {
    const root=$(id);root.innerHTML='';if(!items||!items.length){root.textContent=conflict?'No unresolved conflicts.':'No upcoming recordings.';return;}
    items.slice(0,80).forEach(x=>{const row=document.createElement('article');row.className='dvr-item'+(conflict?' conflict':'');row.dataset.airingId=String(x.airingId||'');const when=`${new Date(x.start).toLocaleString()} – ${new Date(x.end).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}`;row.innerHTML=`<div><strong>${escapeHtml(x.title||'(untitled)')}</strong>${x.episode?`<div>${escapeHtml(x.episode)}</div>`:''}<div class="small">${escapeHtml([x.channelNumber,x.channelName,when,x.category].filter(Boolean).join(' · '))}</div></div>`;const actions=document.createElement('div');actions.className='dvr-actions';if(x.mediaId>0){const p=document.createElement('button');p.textContent='Play';p.onclick=async()=>{if(await loadMedia(x.mediaId,0,true))startAuto();};actions.appendChild(p);}if(x.manualRecord){const opt=document.createElement('button');opt.textContent='Options';opt.onclick=()=>window.SageAdvancedUI&&SageAdvancedUI.editRecordingOptions(x);actions.appendChild(opt);const c=document.createElement('button');c.textContent='Cancel';c.onclick=()=>cancelScheduled(x);actions.appendChild(c);}else if(!conflict){const f=document.createElement('span');f.className='mini-badge';f.textContent='Favorite/automatic';actions.appendChild(f);}row.appendChild(actions);root.appendChild(row);});
  }
  async function loadDvr(){
    $('scheduledList').textContent='Loading…';$('conflictList').textContent='Loading…';
    try{dvrInfo=await jsonFetch('api/dvr');renderDvrList('scheduledList',dvrInfo.scheduled||[],false);renderDvrList('conflictList',dvrInfo.conflicts||[],true);$('dvrSummary').textContent=`${dvrInfo.scheduledCount||0} upcoming recordings · ${dvrInfo.conflictCount||0} unresolved conflict${Number(dvrInfo.conflictCount)===1?'':'s'}`;}
    catch(e){$('scheduledList').textContent=`DVR schedule failed: ${e.message}`;$('conflictList').textContent='Unavailable.';$('dvrSummary').textContent='DVR schedule unavailable.';}
  }
  async function cancelScheduled(x){if(!x||!x.airingId)return;if(!window.confirm(`Cancel the manual recording for “${x.title||'this airing'}”?`))return;try{await jsonFetch(`api/dvr?airingId=${encodeURIComponent(x.airingId)}&action=cancel`,{method:'POST'});setStatus('Manual recording cancelled in SageTV.');await Promise.all([loadDvr(),loadGuide(),loadRecordings()]);}catch(e){setStatus(`Cancel failed: ${e.message}`,true);}}
  $('refreshDvr').onclick=loadDvr;

  function refreshCategoryOptions() {
    const sel=$('recordingCategory'), current=sel.value||'all';
    const values=Library?Library.categories(libraryItems):[]; sel.innerHTML='<option value="all">All categories</option>';
    values.forEach(v=>{const o=document.createElement('option');o.value=v;o.textContent=v;sel.appendChild(o);});
    sel.value=Array.from(sel.options).some(o=>o.value===current)?current:'all';
  }
  function libraryFilterOptions() {
    return {q:$('recordingSearch').value.trim(),view:$('recordingView').value,category:$('recordingCategory').value,sort:$('recordingSort').value,activeOnly:$('activeOnly').checked};
  }
  function updateLibrarySummary(items) {
    const active=libraryItems.filter(x=>x.recording).length;
    $('librarySummary').textContent=`${items.length} recording${items.length===1?'':'s'}${active?` · ${active} recording now`:''}`;
  }
  function thumbnail(rec) {
    const wrap=document.createElement('div'); wrap.className='recording-thumb-wrap';
    const fallback=document.createElement('div'); fallback.className='recording-thumb-fallback'; fallback.textContent=rec.title||'SageTV'; wrap.appendChild(fallback);
    if(rec.thumbnailUrl){const img=document.createElement('img');img.className='recording-thumb';img.loading='lazy';img.alt='';img.src=rec.thumbnailUrl;img.onerror=()=>img.remove();wrap.appendChild(img);}
    const badges=document.createElement('div');badges.className='recording-badges';
    if(rec.recording){const b=document.createElement('span');b.className='mini-badge live';b.textContent='● LIVE';badges.appendChild(b);}
    if(rec.watched){const b=document.createElement('span');b.className='mini-badge watched';b.textContent='✓ WATCHED';badges.appendChild(b);}
    if(rec.category){const b=document.createElement('span');b.className='mini-badge';b.textContent=rec.category;badges.appendChild(b);}
    if(badges.childNodes.length)wrap.appendChild(badges);
    const progress=Library?Library.progress(rec,State):0;if(progress>0){const track=document.createElement('div');track.className='progress-track';const fill=document.createElement('div');fill.className='progress-fill';fill.style.width=`${Math.round(progress*100)}%`;track.appendChild(fill);wrap.appendChild(track);}
    return wrap;
  }
  function showRecordingDetails(rec) {
    if(!rec)return;
    const d=$('detailsDialog'),c=$('detailsContent');
    const when=rec.start?new Date(rec.start).toLocaleString():'';
    const resume=State&&State.getResume?State.getResume(rec.id,0):null;
    const serverResume=Number(rec.serverResumeSeconds)||0;
    const meta=[rec.category,rec.year,[rec.channelNumber,rec.channelName].filter(Boolean).join(' '),when,rec.watched?'SageTV watched':null,rec.recording?'RECORDING NOW':null,resume?`browser resume ${formatTime(resume.position)}`:serverResume>15?`SageTV resume ${formatTime(serverResume)}`:null].filter(Boolean).join(' · ');
    c.innerHTML=`<div class="details-body"><div>${rec.thumbnailUrl?`<img src="${escapeHtml(rec.thumbnailUrl)}" alt="">`:''}</div><div class="details-meta"><h2>${escapeHtml(rec.title||'')}</h2><div>${escapeHtml(rec.episode||'')}</div><div class="muted">${escapeHtml(meta)}</div><div class="muted">${escapeHtml(rec.description||'')}</div><div class="details-actions"><button id="detailsPlay">${rec.recording?'Watch live':'Play'}</button><button id="detailsFavorite">${State&&State.isFavorite&&State.isFavorite(rec.id)?'★ Favorite':'☆ Favorite'}</button><button id="detailsQueue">${State&&State.isQueued&&State.isQueued(rec.id)?'✓ Queued':'＋ Queue'}</button><button id="detailsWatched">${rec.watched?'Mark unwatched':'Mark watched'}</button><button id="detailsOpenRaw">Open raw stream</button>${rec.recording?'':`<button id="detailsDelete" class="danger">Delete recording</button><button id="detailsDeleteNoPrejudice" class="danger subtle">Delete as bad recording</button>`}</div></div></div>`;
    $('detailsPlay').onclick=async()=>{d.close();if(await loadMedia(rec.id,0,true))startPreferred();};
    $('detailsFavorite').onclick=()=>{if(State&&State.toggleFavorite){State.toggleFavorite(rec.id);$('detailsFavorite').textContent=State.isFavorite(rec.id)?'★ Favorite':'☆ Favorite';applyLibraryFilters();updateCurrentFavoriteButton();}};
    $('detailsQueue').onclick=()=>{if(State&&State.toggleQueue){State.toggleQueue(rec.id);$('detailsQueue').textContent=State.isQueued(rec.id)?'✓ Queued':'＋ Queue';if(window.SageAdvancedUI)SageAdvancedUI.renderQueue();}};
    $('detailsWatched').onclick=async()=>{if(await setSageWatched(rec,!rec.watched)){d.close();showRecordingDetails(libraryItems.find(x=>Number(x.id)===Number(rec.id))||rec);}};
    $('detailsOpenRaw').onclick=()=>window.open(`stream.ts?id=${encodeURIComponent(rec.id)}&segment=0`,'_blank','noopener');
    if($('detailsDelete'))$('detailsDelete').onclick=async()=>{if(await deleteRecording(rec,false))d.close();};
    if($('detailsDeleteNoPrejudice'))$('detailsDeleteNoPrejudice').onclick=async()=>{if(await deleteRecording(rec,true))d.close();};
    if(typeof d.showModal==='function')d.showModal();else d.setAttribute('open','');
  }

  function renderRecordings(items) {
    const list=$('recordingList'); list.innerHTML=''; lastLibraryResult=items||[]; updateLibrarySummary(lastLibraryResult);
    if(!lastLibraryResult.length){list.textContent='No recordings found.';return;}
    lastLibraryResult.forEach(rec=>{
      const row=document.createElement('div');row.className='simple-recording-row';row.dataset.mediaId=String(rec.id);
      const info=document.createElement('div');
      const title=document.createElement('div');title.className='simple-recording-title';title.textContent=rec.title||'(untitled)';info.appendChild(title);
      if(rec.episode){const ep=document.createElement('div');ep.className='simple-recording-episode';ep.textContent=rec.episode;info.appendChild(ep);}
      const meta=document.createElement('div');meta.className='simple-recording-meta';
      const when=rec.start?new Date(rec.start).toLocaleString():'';
      const parts=[[rec.channelNumber,rec.channelName].filter(Boolean).join(' '),when,rec.recording?'RECORDING NOW':null,rec.watched?'Watched':null].filter(Boolean);
      meta.textContent=parts.join(' · ');info.appendChild(meta);
      const watch=document.createElement('button');watch.className='simple-watch-button';watch.textContent='Watch';watch.onclick=async()=>{if(await loadMedia(rec.id,0,true))startPreferred();};
      row.append(info,watch);list.appendChild(row);
    });
  }
  function escapeHtml(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function applyLibraryFilters(){const items=Library?Library.filter(libraryItems,libraryFilterOptions(),State):libraryItems.slice();renderRecordings(items);}
  async function loadRecordings(){
    $('recordingList').textContent='Loading…';const limit=Math.max(1,Math.min(500,Number($('recordingLimit').value)||150));
    try{const j=await jsonFetch(`api/recordings?limit=${limit}`);libraryItems=j.recordings||[];refreshCategoryOptions();applyLibraryFilters();updateSeriesUi();}
    catch(e){$('recordingList').textContent=`Recordings failed: ${e.message}`;$('librarySummary').textContent='Library unavailable.';}
  }
  $('refreshRecordings').onclick=loadRecordings;
  ['recordingSearch','recordingView','recordingCategory','recordingSort','activeOnly'].forEach(id=>$(id).addEventListener(id==='recordingSearch'?'input':'change',applyLibraryFilters));
  $('recordingLimit').addEventListener('change',loadRecordings);

  function guideRows(items) {
    const map=new Map();
    (items||[]).forEach(x=>{const key=`${x.channelNumber||''}|${x.channelName||''}`;if(!map.has(key))map.set(key,{channelNumber:x.channelNumber||'',channelName:x.channelName||'',items:[]});map.get(key).items.push(x);});
    return Array.from(map.values());
  }
  function renderGuide() {
    const root=$('guideGrid'); root.innerHTML='';
    const q=$('guideSearch').value.trim().toLowerCase();
    const favoriteOnly=$('favoriteChannelsOnly')&&$('favoriteChannelsOnly').checked;
    const filtered=guideItems.filter(x=>(!favoriteOnly||!State||!State.isChannelFavorite||State.isChannelFavorite(`${x.channelNumber||''}|${x.channelName||''}`))&&(!q||`${x.title} ${x.episode} ${x.description} ${x.category} ${x.channelName} ${x.channelNumber}`.toLowerCase().includes(q)));
    const rows=guideRows(filtered);
    if(!rows.length){root.textContent='No guide entries in this window.';return;}
    rows.forEach(row=>{
      const el=document.createElement('div');el.className='guide-row';
      const ch=document.createElement('div');ch.className='guide-channel';const first=row.items[0]||{};const key=`${row.channelNumber||''}|${row.channelName||''}`;const logo=first.channelLogoUrl?`<img class="guide-logo" src="${escapeHtml(first.channelLogoUrl)}" alt="" onerror="this.style.display='none'">`:'';ch.innerHTML=`${logo}<strong>${escapeHtml(row.channelNumber||'')}</strong><span>${escapeHtml(row.channelName||'')}</span>`;const star=document.createElement('button');star.className='channel-favorite';const updateStar=()=>{const on=State&&State.isChannelFavorite&&State.isChannelFavorite(key);star.textContent=on?'★':'☆';star.title=on?'Remove favorite channel':'Favorite channel';};updateStar();star.onclick=()=>{if(State&&State.toggleChannelFavorite)State.toggleChannelFavorite(key);updateStar();if($('favoriteChannelsOnly').checked)renderGuide();};ch.appendChild(star);
      const programs=document.createElement('div');programs.className='guide-programs';
      row.items.forEach(a=>{
        const card=document.createElement('article');card.className='guide-program'+(a.currentlyAiring?' now':'')+(a.manualRecord?' recording':'');
        const time=`${new Date(a.start).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}–${new Date(a.end).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}`;
        card.innerHTML=`<div class="guide-time">${escapeHtml(time)}${a.category?` · ${escapeHtml(a.category)}`:''}</div><div class="guide-title">${escapeHtml(a.title||'(untitled)')}</div><div class="guide-episode">${escapeHtml(a.episode||'')}</div><div class="guide-description">${escapeHtml(a.description||'')}</div>`;
        const actions=document.createElement('div');actions.className='guide-actions';
        if(a.mediaId>0){const play=document.createElement('button');play.textContent=a.currentlyAiring?'Watch recording live':'Play recording';play.onclick=async()=>{if(await loadMedia(a.mediaId,0,true))startAuto();};actions.appendChild(play);}
        if(a.currentlyAiring){const watch=document.createElement('button');watch.textContent=a.mediaId>0?'Go live':'Watch live';watch.onclick=()=>guideAction(a,'watch');actions.appendChild(watch);}
        const rec=document.createElement('button');rec.textContent=a.manualRecord?'Cancel recording':'Record';rec.onclick=()=>guideAction(a,a.manualRecord?'cancel':'record');actions.appendChild(rec);
        card.appendChild(actions);programs.appendChild(card);
      });
      el.append(ch,programs);root.appendChild(el);
    });
  }
  async function loadGuide(){
    const hours=Number($('guideHours').value)||4; $('guideGrid').textContent='Loading…';
    try{const j=await jsonFetch(`api/guide?start=${Math.round(guideStart)}&hours=${hours}`);guideItems=j.airings||[];$('guideRange').textContent=`${new Date(j.start).toLocaleString()} – ${new Date(j.end).toLocaleString()} · ${guideItems.length} programs`;renderGuide();updateButtons();}
    catch(e){$('guideGrid').textContent=`Guide failed: ${e.message}`;$('guideRange').textContent='Guide unavailable.';}
  }
  async function guideAction(a,action){
    try{const j=await jsonFetch(`api/guide?airingId=${encodeURIComponent(a.airingId)}&action=${encodeURIComponent(action)}`,{method:'POST'});diagEvent('guide-action',{airingId:a.airingId,action,mediaId:j.mediaId});
      if(action==='watch'){if(j.mediaId>0){await loadGuide();if(await loadMedia(j.mediaId,0,true))startWasm({liveEdge:true,autoSelected:true});}else{setStatus('SageTV started the manual recording, but the MediaFile is not visible yet. Refresh the guide in a few seconds.',true);}}
      else{await Promise.all([loadGuide(),loadRecordings(),loadDvr()]);setStatus(action==='record'?'Recording scheduled in SageTV.':'Manual recording cancelled in SageTV.');}
    }catch(e){setStatus(`Guide action failed: ${e.message}`,true);}
  }
  $('refreshGuide').onclick=loadGuide;$('guideSearch').addEventListener('input',renderGuide);$('guideHours').addEventListener('change',loadGuide);$('favoriteChannelsOnly').addEventListener('change',e=>{persistSetting('favoriteChannelsOnly',e.target.checked);renderGuide();});
  $('guideNow').onclick=()=>{guideStart=Date.now()-30*60*1000;loadGuide();};$('guideBack').onclick=()=>{guideStart-=2*60*60*1000;loadGuide();};$('guideForward').onclick=()=>{guideStart+=2*60*60*1000;loadGuide();};
  function compareChannels(a,b){const pa=parseFloat(String(a.channelNumber||'').replace('-','.')),pb=parseFloat(String(b.channelNumber||'').replace('-','.'));if(Number.isFinite(pa)&&Number.isFinite(pb)&&pa!==pb)return pa-pb;return String(a.channelNumber||a.channelName||'').localeCompare(String(b.channelNumber||b.channelName||''),undefined,{numeric:true});}
  function liveGuideAirings(){const seen=new Set(),out=[];guideItems.filter(x=>x.currentlyAiring).sort(compareChannels).forEach(x=>{const k=`${x.channelNumber||''}|${x.channelName||''}`;if(!seen.has(k)){seen.add(k);out.push(x);}});return out;}
  async function channelStep(direction){const live=liveGuideAirings();if(!media||!media.recording||live.length<2)return;let idx=live.findIndex(x=>String(x.channelNumber||'')===String(media.channelNumber||'')&&String(x.channelName||'')===String(media.channelName||''));if(idx<0)idx=0;const target=live[(idx+(direction<0?-1:1)+live.length)%live.length];setStatus(`Tuning SageTV live channel ${target.channelNumber||''} ${target.channelName||''}…`);diagEvent('channel-step',{direction,airingId:target.airingId,channelNumber:target.channelNumber,channelName:target.channelName});await guideAction(target,'watch');}
  $('channelDown').onclick=()=>channelStep(-1);$('channelUp').onclick=()=>channelStep(1);

  function currentLibraryRecord(){return media?libraryItems.find(x=>Number(x.id)===Number(media.id)):null;}
  function seriesNeighbors(){return media&&Library?Library.neighbors(libraryItems,media.id,media.title):{previous:null,next:null,index:-1,count:0};}
  function updateCurrentFavoriteButton(){
    const b=$('favoriteCurrent'),ready=!!media;b.disabled=!ready;if(!ready){b.classList.remove('active');b.textContent='☆ Favorite';return;}
    const on=!!(State&&State.isFavorite&&State.isFavorite(media.id));b.classList.toggle('active',on);b.textContent=on?'★ Favorite':'☆ Favorite';
  }
  function updateSeriesUi(){
    const n=seriesNeighbors(),status=$('seriesStatus');
    $('previousEpisode').disabled=!media||!n.previous;$('nextEpisode').disabled=!media||!n.next;
    if(!media||n.count<2){status.hidden=true;return;}
    status.hidden=false;status.textContent=`Series: ${media.title} · recording ${n.index>=0?n.index+1:'?'} of ${n.count}${n.previous?` · previous: ${n.previous.episode||new Date(n.previous.start).toLocaleDateString()}`:''}${n.next?` · next: ${n.next.episode||new Date(n.next.start).toLocaleDateString()}`:''}`;
  }
  function mediaArtwork(){return media&&media.thumbnailUrl?[{src:new URL(media.thumbnailUrl,location.origin).href,sizes:'640x360',type:'image/png'}]:[];}
  function updateMediaSessionMetadata(){
    if(!('mediaSession' in navigator)||!media)return;
    try{navigator.mediaSession.metadata=new MediaMetadata({title:media.title||'SageTV',artist:media.episode||[media.channelNumber,media.channelName].filter(Boolean).join(' '),album:'SageTV',artwork:mediaArtwork()});}catch(_){}
  }
  function updateMediaSessionPosition(seconds){
    if(!('mediaSession' in navigator)||!navigator.mediaSession.setPositionState||!media||media.recording)return;
    const duration=playbackDurationSeconds();if(!(duration>0))return;seconds=Math.max(0,Math.min(duration,Number(seconds)||0));
    try{navigator.mediaSession.setPositionState({duration,playbackRate:Number($('rate').value)||1,position:Math.min(seconds,Math.max(0,duration-.01))});}catch(_){}
  }
  async function seekToSeconds(sec){
    if(!media||media.recording)return;sec=Math.max(0,Math.min(playbackDurationSeconds(),Number(sec)||0));suppressSeek=true;
    try{if(mode==='wasm')await SageWasmDecoder.seek(sec*1000);else if(mode==='native')$('nativeVideo').currentTime=sec;else if(mode==='compat')await startCompat(sec);updateTime(sec);saveResume(sec,true);}
    finally{suppressSeek=false;}
  }
  async function playNeighbor(direction){
    const n=seriesNeighbors(),target=direction<0?n.previous:n.next;if(!target)return false;
    diagEvent('series-navigation',{direction,targetId:target.id,title:target.title,episode:target.episode});
    if(await loadMedia(target.id,0,true)){await startAuto();return true;}return false;
  }
  function setupMediaSession(){
    if(!('mediaSession' in navigator))return;
    const safe=(name,fn)=>{try{navigator.mediaSession.setActionHandler(name,fn);}catch(_){} };
    safe('play',async()=>{if(mode==='wasm')await SageWasmDecoder.resume();else if(mode==='native')await $('nativeVideo').play();else if(mode==='compat')await SageFmp4Fallback.resume();paused=false;navigator.mediaSession.playbackState='playing';});
    safe('pause',async()=>{saveResume(currentPlaybackSeconds(),true);if(mode==='wasm')await SageWasmDecoder.pause();else if(mode==='native')$('nativeVideo').pause();else if(mode==='compat')SageFmp4Fallback.pause();paused=true;navigator.mediaSession.playbackState='paused';});
    safe('seekbackward',d=>seekToSeconds(currentPlaybackSeconds()-(Number(d&&d.seekOffset)||15)));
    safe('seekforward',d=>seekToSeconds(currentPlaybackSeconds()+(Number(d&&d.seekOffset)||30)));
    safe('seekto',d=>d&&Number.isFinite(d.seekTime)&&seekToSeconds(d.seekTime));
    safe('nexttrack',()=>playNeighbor(1));safe('previoustrack',()=>playNeighbor(-1));
  }

  async function probeRaw(url) {
    if(media&&media.recording) return {source:'live-follower',bytes:0,status:200};
    const r=await fetch(url,{cache:'no-store',headers:{Range:'bytes=0-1879'}}); if(!(r.ok||r.status===206))throw new Error(`raw stream HTTP ${r.status}`);
    const ab=await r.arrayBuffer(); return {source:r.headers.get('X-SageTV-Stream-Source')||'unknown',bytes:ab.byteLength,status:r.status};
  }
  function commercialState(state){
    const bar=$('commercialBar'),text=$('commercialText'),skip=$('skipCommercial');
    if(!state||!state.count){bar.hidden=true;skip.disabled=true;return;}
    bar.hidden=false;bar.classList.toggle('active',!!state.active);skip.disabled=!state.active||!!(media&&media.recording);
    if(state.active)text.textContent=`Commercial break ${formatTime(state.active.start)}–${formatTime(state.active.end)}${media&&media.recording?' · skipping is available after the recording becomes VOD':state.enabled?' · auto-skip enabled':''}`;
    else if(state.next)text.textContent=`${state.count} Comskip break${state.count===1?'':'s'} · next at ${formatTime(state.next.start)}${state.enabled?' · auto-skip enabled':''}`;
    else text.textContent=`${state.count} Comskip break${state.count===1?'':'s'} · no more marked breaks`;
  }
  async function loadCommercials(){
    if(!media||!commercialController)return;lastCommercialRefreshAt=Date.now();
    try{commercialInfo=await jsonFetch(`api/commercials?id=${encodeURIComponent(media.id)}&segment=${encodeURIComponent(segment)}`);commercialController.setMarkers(commercialInfo.markers||[],commercialInfo.source||'');diagEvent('commercial-markers',{count:commercialInfo.count,source:commercialInfo.source});}
    catch(e){commercialInfo={available:false,count:0,markers:[],source:'',error:e.message};commercialController.setMarkers([], '');}
  }

  async function loadMedia(id,seg,scroll) {
    await stopPlayback({keepResume:true}); segment=Number(seg)||0; $('mediaId').value=id; $('segment').value=segment; $('mediaInfo').textContent='Loading…'; $('mediaSummary').innerHTML='';
    try {
      media=await jsonFetch(`api/media?id=${encodeURIComponent(id)}`); const f=media.files.find(x=>Number(x.segment)===segment);
      if(!f)throw new Error(`Segment ${segment} does not exist`);
      if(!f.streamAccessible){
        const ds=media.disc&&media.disc.source;
        if(ds&&ds.interactiveNative)throw new Error(`This SageTV MediaFile is ${String(ds.kind||'DVD').replace(/_/g,' ')}. Use the SageTV MiniClient/STV native DVD path; raw recording streaming is not substituted for interactive disc playback. ${ds.note||''}`.trim());
        if(ds&&ds.stage==='unsupported-scope')throw new Error(ds.note||'This disc type is outside the supported DVD-Video scope.');
        throw new Error(f.streamError||'Server cannot access media segment');
      }
      rawUrl=playbackRawUrl(false); $('rawLink').href=rawUrl; $('mediaInfo').textContent=JSON.stringify(media,null,2);
      const img=media.thumbnailUrl?`<img class="media-summary-thumb" src="${escapeHtml(media.thumbnailUrl)}" alt="" onerror="this.style.display='none'">`:'';
      const discSource=media.disc&&media.disc.source;
      const discLabel=discSource&&discSource.kind&&discSource.kind!=='other'?`Disc: ${String(discSource.kind).replace(/_/g,' ')}`:null;
      const metadata=[media.category,media.year,[media.channelNumber,media.channelName].filter(Boolean).join(' '),discLabel,media.watched?'SageTV watched':null,media.recording?'RECORDING NOW':null].filter(Boolean).join(' · ');
      $('mediaSummary').className='media-summary rich-summary';$('mediaSummary').innerHTML=`${img}<div class="media-summary-text"><div class="title">${escapeHtml(media.title||'')}</div><div>${escapeHtml(media.episode||'')}</div><div class="metadata-line">${escapeHtml(metadata)}</div><div class="muted">${escapeHtml(media.description||'')}</div><div class="muted">${escapeHtml(media.format||'')}</div></div>`;
      const p=await probeRaw(rawUrl); setStatus(`Original stream ready via ${p.source}. No server transcoding is active.`);
      diagEvent('media-loaded',{id:media.id,segment,recording:media.recording,format:media.format,streamSource:p.source});
      await loadCommercials();
      configureDuration(); updateButtons(); updateResumeUi(); updateCurrentFavoriteButton(); updateSeriesUi(); if(window.SageAdvancedUI)SageAdvancedUI.renderQueue(); updateMediaSessionMetadata(); history.replaceState(null,'',`${location.pathname}?id=${media.id}`); if(scroll)$('playerStage').scrollIntoView({behavior:'smooth',block:'center'});return true;
    } catch(e) { media=null;rawUrl=null;if(commercialController)commercialController.setMarkers([], '');$('mediaInfo').textContent=`Error: ${e.message}`;setStatus(`Cannot load recording: ${e.message}`,true);updateButtons();updateResumeUi();updateCurrentFavoriteButton();updateSeriesUi();return false; }
  }
  $('mediaForm').addEventListener('submit',e=>{e.preventDefault();loadMedia(Number($('mediaId').value),Number($('segment').value||0),true);});

  function configureDuration(){
    const live=!!(media&&media.recording), seconds=live?0:playbackDurationSeconds();
    $('seek').disabled=live||!seconds; $('seek').min=0; $('seek').max=seconds||1; $('seek').value=0; $('timeNow').textContent='0:00'; $('timeTotal').textContent=live?'LIVE':formatTime(seconds); $('liveEdge').disabled=!live;
  }
  function updateButtons(){
    const ready=!!media; $('playAuto').disabled=!ready; $('playWasm').disabled=!ready||!window.WebAssembly; $('playNative').disabled=!ready; $('playCompat').disabled=!ready||!transcoder.available||!!(media&&media.recording); $('favoriteCurrent').disabled=!ready;$('queueCurrent').disabled=!ready;const liveCount=liveGuideAirings().length;const canSurf=!!(media&&media.recording&&liveCount>1);$('channelDown').disabled=!canSurf;$('channelUp').disabled=!canSurf;
    updateCurrentFavoriteButton(); updateSeriesUi();
  }

  function renderCaption(text,service,meta){
    const root=$('captionOverlay');root.innerHTML='';if(!$('captionsEnabled').checked||!text)return;
    if(meta&&meta.type==='708'&&Array.isArray(meta.windows)&&meta.windows.length){
      meta.windows.forEach(w=>{const d=document.createElement('div');d.className='caption-708-window';d.textContent=w.text;d.style.left=`${w.left}%`;d.style.top=`${w.top}%`;d.style.color=w.fg||'#fff';d.style.background=w.bg||'rgba(0,0,0,.7)';if(w.italic)d.style.fontStyle='italic';if(w.underline)d.style.textDecoration='underline';const a=Number(w.anchorId)||0,hx=a%3,vy=Math.floor(a/3);d.style.transform=`translate(${hx===0?'0':hx===1?'-50%':'-100%'},${vy===0?'0':vy===1?'-50%':'-100%'})`;root.appendChild(d);});
    } else { const d=document.createElement('div');d.className='caption-608';d.textContent=text;root.appendChild(d); }
  }
  function ensureCaptionService(value,label){if(!Array.from($('captionService').options).some(o=>o.value===value)){const o=document.createElement('option');o.value=value;o.textContent=label;$('captionService').appendChild(o);}}
  function captionStats(stats){ if(!stats)return; lastCaptionStats=sanitize(stats); (stats.captionServices||[]).forEach(s=>ensureCaptionService(s.type==='CEA-708'?`708-${s.service}`:`CC${s.service}`,`${s.type} ${s.service}${s.lang?` (${s.lang})`:''}`)); $('captionStats').textContent=[`TS packets: ${stats.packets}`,`PMT PID: ${stats.pmtPid??'?'}`,`MPEG-2 PID: ${stats.videoPid??'?'}`,`CEA-608 pairs: ${stats.cea608Pairs}`,`CEA-708 triples: ${stats.cea708Triples}`,`Continuity errors: ${stats.continuityErrors}`,`Queued caption state changes: ${stats.queuedCues||0}`,`Service: ${stats.selectedService}`].join('\n'); }
  function newCaptionParser(){captionParser=new SageAtscCaptions({enabled:$('captionsEnabled').checked,service:$('captionService').value,onCaption:renderCaption,onStats:captionStats});return captionParser;}

  function trackLanguage(t){return String(t&&t.metadata&&t.metadata.language||'').toLowerCase().split(/[-_]/)[0];}
  function populateAudio(tracks){
    currentTracks=tracks||{audio:[],video:[],subtitle:[]}; const s=$('audioTrack');s.innerHTML='';const a=currentTracks.audio||[];
    if(!a.length){const o=document.createElement('option');o.textContent='Default audio';s.appendChild(o);s.disabled=true;return;}
    a.forEach((t,i)=>{const o=document.createElement('option');o.value=t.id;o.textContent=`${i+1}. ${t.label}`;s.appendChild(o)});s.disabled=a.length<2;
  }
  async function applyPreferredAudio(tracks){
    const lang=String($('preferredAudioLang').value||'auto').toLowerCase(); if(lang==='auto'||!tracks||!tracks.audio||tracks.audio.length<2)return;
    const target=tracks.audio.find(t=>trackLanguage(t)===lang||trackLanguage(t).startsWith(lang)); if(!target)return;
    try{await SageWasmDecoder.selectAudio(Number(target.id));$('audioTrack').value=String(target.id);diagEvent('preferred-audio-applied',{lang,streamId:Number(target.id)});}catch(e){diagEvent('preferred-audio-failed',e.message||String(e));}
  }

  function initWatchdog(tracks) {
    let fps=tracks&&tracks.expectedFps?Number(tracks.expectedFps):0; if(!(fps>1&&fps<240))fps=expectedFps();
    watchdog=new SagePerformanceWatchdog({expectedFps:fps,warmupSamples:8,badSamplesRequired:6,badRatio:.72,maxHistory:180}); $('performanceStatus').dataset.state='warming'; $('performanceStatus').textContent=`Warming up performance monitor… expected ${fps.toFixed(2)} fps.`;
  }
  function clearLiveReconnectTimer(){if(liveReconnectTimer){clearTimeout(liveReconnectTimer);liveReconnectTimer=null;}}
  function scheduleLiveReconnect(reason){
    if(!media||!media.recording||!$('autoReconnectLive').checked||mode!=='wasm'||liveReconnectTimer||document.visibilityState==='hidden')return;
    if(liveReconnectAttempts>=5){setStatus('Live playback reconnect limit reached. Press Go Live to try again.',true);diagEvent('live-reconnect-limit',{reason});return;}
    const attempt=++liveReconnectAttempts, delay=Math.min(8000,1500*Math.pow(2,attempt-1));
    setStatus(`Live stream interrupted (${reason}). Reconnecting to live edge in ${(delay/1000).toFixed(1)}s — attempt ${attempt}/5…`);diagEvent('live-reconnect-scheduled',{reason,attempt,delay});
    liveReconnectTimer=setTimeout(async()=>{liveReconnectTimer=null;if(!media||!media.recording||!$('autoReconnectLive').checked)return;await startWasm({liveEdge:true,autoSelected:autoFallbackArmed,reconnecting:true});},delay);
  }
  async function handlePlaybackEnded(){
    if($('syncSageProgress').checked&&media&&!media.recording)await syncSageProgress(playbackDurationSeconds(),true);
    clearResume();setStatus('Playback ended.');diagEvent('playback-ended',{});if('mediaSession' in navigator)navigator.mediaSession.playbackState='none';applyLibraryFilters();
    if(media&&!media.recording&&State&&State.getQueue&&$('autoPlayQueue')&&$('autoPlayQueue').checked){let q=State.getQueue();if(q.includes(Number(media.id)))State.shiftQueue(media.id);q=State.getQueue();if(q.length){const nextId=q[0],next=libraryItems.find(x=>Number(x.id)===Number(nextId));setStatus(`Playback ended. Starting queued recording: ${(next&&next.title)||nextId}…`);diagEvent('autoplay-queue',{id:nextId});if(await loadMedia(nextId,0,true)){await startAuto();if(window.SageAdvancedUI)SageAdvancedUI.renderQueue();return;}}}
    if($('autoPlayNext').checked&&!media.recording){const n=seriesNeighbors();if(n.next){setStatus(`Playback ended. Starting next recording: ${n.next.episode||n.next.title}…`);diagEvent('autoplay-next',{id:n.next.id});await playNeighbor(1);}}
  }

  async function startWasm(options) {
    options=options||{}; clearLiveReconnectTimer(); if(!options.reconnecting)liveReconnectAttempts=0;
    await stopPlayback({preserveReconnect:!!options.reconnecting,keepResume:true});
    const liveEdge=media&&media.recording ? (options.liveEdge===true || (options.liveEdge==null && $('startLiveAtEdge').checked)) : false;
    rawUrl=playbackRawUrl(liveEdge); setMode('wasm','ORIGINAL + WASM'); $('stageMessage').textContent='Starting browser software decoder…'; const parser=newCaptionParser(); autoFallbackArmed=!!options.autoSelected; autoFallbackInProgress=false; lastProgressAt=Date.now();lastProgressSeconds=0;
    if($('offlineOnly').checked && assetInfo && !assetInfo.ready){setMode(null);setStatus('Local-cache-only decoder mode is enabled, but not all pinned decoder assets are cached. Use “Cache all decoder assets” first.',true);return false;}
    try {
      const result=await SageWasmDecoder.play({
        url:new URL(rawUrl,location.href).href,container:$('wasmPlayer'),captionParser:parser,isLive:!!media.recording,allowCdnFallback:!$('offlineOnly').checked,
        onStatus:t=>setStatus(`${t}\nServer transcode: OFF`),
        onTracks:t=>{populateAudio(t);if(!watchdog)initWatchdog(t);},
        onTime:ms=>updateTime(ms/1000),
        onEnded:handlePlaybackEnded,
        onError:e=>{const msg=e&&e.message?e.message:String(e);setStatus(`Decoder error: ${msg}`,true);diagEvent('decoder-error',msg);scheduleLiveReconnect(msg);}
      });
      populateAudio(result.tracks); initWatchdog(result.tracks); await applyPreferredAudio(result.tracks); SageWasmDecoder.setVolume(Number($('volume').value)); if(!media.recording)SageWasmDecoder.setPlaybackRate(Number($('rate').value));
      const start=media.recording?0:(options.startSeconds!=null?Number(options.startSeconds):desiredStartSeconds()); if(start>0){try{await SageWasmDecoder.seek(start*1000);updateTime(start);diagEvent('resume-seek',{seconds:start});}catch(e){diagEvent('resume-seek-failed',e.message||String(e));}}
      setStatus(`Playing ORIGINAL SageTV transport stream. MPEG-2/AC-3 decoded in this browser with libmedia ${SageWasmDecoder.libmediaVersion}. Server transcode: OFF. Assets: ${result.assetMode}. Execution: ${result.threads?'Wasm threads':result.workerEnabled?'Web Workers':'single-thread fallback'}${media.recording?(liveEdge?' · live edge':' · from recording start'):''}.`);
      diagEvent('wasm-start',{assetMode:result.assetMode,workerEnabled:result.workerEnabled,threads:result.threads,liveEdge,startSeconds:start,tracks:result.tracks&&{audio:result.tracks.audio.length,video:result.tracks.video.length,expectedFps:result.tracks.expectedFps}});
      paused=false; markPlayed(); enableSessionControls(); startStats(); requestWakeLock(); if('mediaSession' in navigator)navigator.mediaSession.playbackState='playing'; applyLibraryFilters(); return true;
    } catch(e) { const msg=e.message||String(e);setStatus(`Original Wasm playback failed: ${msg}`,true);diagEvent('wasm-failed',msg);scheduleLiveReconnect(msg);if(!media||!media.recording||!$('autoReconnectLive').checked)setMode(null);return false; }
  }

  async function waitMetadata(video){if(video.readyState>=1)return;await Promise.race([new Promise(r=>video.addEventListener('loadedmetadata',r,{once:true})),new Promise(r=>setTimeout(r,2500))]);}
  async function startNative(options) {
    options=options||{}; await stopPlayback({keepResume:true}); rawUrl=playbackRawUrl(media&&media.recording&&$('startLiveAtEdge').checked); setMode('native','ORIGINAL NATIVE'); const v=$('nativeVideo');v.src=rawUrl;v.load();
    v.ontimeupdate=()=>updateTime(v.currentTime);v.onended=handlePlaybackEnded;v.onerror=()=>{const msg=v.error&&v.error.message?v.error.message:'unsupported source/codec';setStatus(`Native playback error: ${msg}`,true);scheduleLiveReconnect(msg);};
    try {await waitMetadata(v);const start=media.recording?0:(options.startSeconds!=null?Number(options.startSeconds):desiredStartSeconds());if(start>0&&Number.isFinite(v.duration))v.currentTime=Math.min(start,Math.max(0,v.duration-1));v.volume=Number($('volume').value);v.playbackRate=Number($('rate').value);await v.play();setStatus('Browser accepted the ORIGINAL stream natively. Server transcode: OFF.');diagEvent('native-start',{startSeconds:start});paused=false;markPlayed();enableSessionControls();requestWakeLock();if('mediaSession' in navigator)navigator.mediaSession.playbackState='playing';applyLibraryFilters();return true;} catch(e){setMode(null);setStatus(`Native browser playback rejected the original stream: ${e.message}`,true);diagEvent('native-failed',e.message);return false;}
  }

  async function startCompat(startSeconds, options) {
    options=options||{};if(!transcoder.available||media.recording)return false;await stopPlayback({keepResume:true});compatOffset=Number(startSeconds)||0;setMode('compat','COMPATIBILITY');
    try{const v=await SageFmp4Fallback.play({container:$('fallbackPlayer'),id:media.id,segment,audio:Math.max(0,$('audioTrack').selectedIndex),start:compatOffset,duration:playbackDurationSeconds()});v.ontimeupdate=()=>{updateTime(SageFmp4Fallback.absoluteTime?SageFmp4Fallback.absoluteTime():compatOffset+v.currentTime);updateCompatBufferUi();};v.onprogress=updateCompatBufferUi;v.onwaiting=updateCompatBufferUi;v.onplaying=updateCompatBufferUi;v.onended=()=>{const now=SageFmp4Fallback.absoluteTime?SageFmp4Fallback.absoluteTime():compatOffset+Number(v.currentTime||0),dur=playbackDurationSeconds();if(dur>0&&now<dur-5){setStatus(`Compatibility HLS ended early at ${formatTime(now)} of ${formatTime(dur)}. Restarting from the current position…`);diagEvent('compat-early-end',{now,duration:dur});setTimeout(()=>startCompat(now,{automatic:true,recovery:true}),100);}else handlePlaybackEnded();};v.onerror=()=>{updateCompatBufferUi();setStatus('Compatibility HLS playback error; the HLS watchdog will attempt recovery before giving up.',true);};v.volume=Number($('volume').value);v.playbackRate=Number($('rate').value);startCompatBufferTimer();setStatus(`${options.automatic?'Automatic fallback: ':''}Compatibility mode is continuously transcoding to H.264/AAC HLS segments. Jetty serves the playlist/segments and the browser keeps requesting them. Original recording is unchanged. Timeline uses the full SageTV recording duration.`);diagEvent(options.automatic?'auto-fallback-start':'compat-start',{startSeconds:compatOffset,duration:playbackDurationSeconds()});paused=false;markPlayed();enableSessionControls();requestWakeLock();if('mediaSession' in navigator)navigator.mediaSession.playbackState='playing';applyLibraryFilters();return true;}catch(e){clearCompatBufferTimer();setMode(null);setStatus(`Compatibility playback failed: ${e.message}`,true);diagEvent('compat-failed',e.message);return false;}
  }

  async function startPreferred(){
    if(!media)return;
    const start=media.recording?0:desiredStartSeconds();
    if(transcoder.available&&!media.recording){
      setStatus('Starting default Compatibility (FFmpeg) playback…');
      if(await startCompat(start,{preferred:true}))return true;
      setStatus('Compatibility failed; trying original playback paths…',true);
    }
    await startAuto();
    return !!mode;
  }

  async function startAuto(){
    if(!media)return;const start=media.recording?0:desiredStartSeconds();setStatus('Auto: selecting the least-invasive compatible playback path…');diagEvent('auto-start',{id:media.id,startSeconds:start});
    if(likelyNeedsWasm()&&window.WebAssembly){if(await startWasm({autoSelected:true,startSeconds:start}))return;if(await startNative({startSeconds:start}))return;}
    if(!likelyNeedsWasm()){if(await startNative({startSeconds:start}))return;if(window.WebAssembly&&await startWasm({autoSelected:true,startSeconds:start}))return;}
    if(transcoder.available&&!media.recording&&await startCompat(start,{automatic:true}))return;
    if(!likelyNeedsWasm()&&window.WebAssembly===false)setStatus('No compatible playback path: native failed and WebAssembly is unavailable.',true);else setStatus('Auto exhausted available playback paths. See decoder/server diagnostics.',true);
  }

  async function stopPlayback(options){
    options=options||{}; clearCompatBufferTimer(); updateCompatBufferUi(); if(!options.preserveReconnect)clearLiveReconnectTimer(); if(statsTimer){clearInterval(statsTimer);statsTimer=null;}
    const sec=currentPlaybackSeconds(); if(options.keepResume!==false&&sec>0)saveResume(sec,true);if(sec>0&&media&&!media.recording&&$('syncSageProgress').checked)await syncSageProgress(sec,true);
    try{await SageWasmDecoder.stop();}catch(_){}try{SageFmp4Fallback.stop();}catch(_){}const v=$('nativeVideo');v.pause();v.removeAttribute('src');v.load();$('wasmPlayer').innerHTML='';$('fallbackPlayer').innerHTML='';$('captionOverlay').innerHTML='';captionParser=null;watchdog=null;lastDecoderStats=null;paused=false;mode=null;autoFallbackArmed=false;autoFallbackInProgress=false;releaseWakeLock();setMode(null);disableSessionControls();if('mediaSession' in navigator)navigator.mediaSession.playbackState='none';
  }
  function currentPlaybackSeconds(){if(mode==='wasm')return Math.max(0,SageWasmDecoder.getCurrentTime()/1000);if(mode==='native')return Math.max(0,Number($('nativeVideo').currentTime)||0);if(mode==='compat'){if(SageFmp4Fallback.absoluteTime)return Math.max(0,SageFmp4Fallback.absoluteTime());const v=SageFmp4Fallback.element();return Math.max(0,compatOffset+(v?Number(v.currentTime)||0:0));}return 0;}
  function enableSessionControls(){['pause','resume','stop','mute'].forEach(id=>$(id).disabled=false);const vod=!!media&&!media.recording;$('skipBack').disabled=!vod;$('skipForward').disabled=!vod;updateButtons();updateSeriesUi();}
  function disableSessionControls(){['pause','resume','stop','mute','skipBack','skipForward','channelDown','channelUp'].forEach(id=>$(id).disabled=true);}
  function performanceText(p){if(!p)return'Waiting for performance data.';const fps=p.effectiveFps==null?'n/a':p.effectiveFps.toFixed(1),ratio=p.ratio==null?'n/a':`${Math.round(p.ratio*100)}%`;return[`State: ${p.state.toUpperCase()}`,`Expected: ${p.expectedFps.toFixed(2)} fps`,`Decode/render: ${fps} fps (${ratio})`,`Video stutters: ${p.videoStutter==null?'n/a':p.videoStutter}${p.stutterDelta?` (+${p.stutterDelta})`:''}`,`Audio stutters: ${p.audioStutter==null?'n/a':p.audioStutter}`,`A/V delta: ${p.avDelta==null?'n/a':Math.round(p.avDelta)+' ms'}`,p.reasons&&p.reasons.length?`Reasons: ${p.reasons.join('; ')}`:'Reasons: none'].join('\n');}
  function startStats(){
    if(statsTimer)clearInterval(statsTimer);statsTimer=setInterval(async()=>{
      const s=SageWasmDecoder.stats();if(s){lastDecoderStats=sanitize(s);$('decoderStats').textContent=JSON.stringify(lastDecoderStats,null,2);}if(captionParser)captionStats(captionParser.getStats());
      if(mode==='wasm'&&watchdog&&s){const p=watchdog.update(s,SageWasmDecoder.getCurrentTime());$('performanceStatus').dataset.state=p.state;$('performanceStatus').textContent=performanceText(p);if(p.state==='bad'&&autoFallbackArmed&&$('autoFallback').checked&&!autoFallbackInProgress){if(transcoder.available&&!media.recording){autoFallbackInProgress=true;const sec=Math.max(0,SageWasmDecoder.getCurrentTime()/1000);diagEvent('performance-fallback-trigger',{atSeconds:sec,performance:p});setStatus(`This device cannot sustain real-time MPEG-2 software decode (${p.effectiveFps?p.effectiveFps.toFixed(1):'low'} fps). Switching to compatibility playback at ${formatTime(sec)}…`);setTimeout(()=>startCompat(sec,{automatic:true}),50);}else{autoFallbackArmed=false;setStatus(`Software decoding is below real-time performance, but no compatibility transcoder is available${media.recording?' for an active recording':''}. Playback will continue with the original stream.`,true);diagEvent('performance-no-fallback',p);}}}
      if(mode==='wasm'&&media&&media.recording&&$('autoReconnectLive').checked&&document.visibilityState==='visible'&&Date.now()-lastProgressAt>12000){scheduleLiveReconnect('no playback progress for 12 seconds');}
      if(media&&media.recording&&Date.now()-lastCommercialRefreshAt>30000)loadCommercials();
    },1000);
  }

  function clearCompatBufferTimer(){if(compatBufferTimer){clearInterval(compatBufferTimer);compatBufferTimer=null;}}
  function updateCompatBufferUi(){
    const text=$('bufferInfo'),bar=$('bufferProgress');if(!text||!bar)return;
    if(mode!=='compat'||!window.SageFmp4Fallback||!SageFmp4Fallback.getBufferInfo){text.textContent='Buffer: idle';bar.max=1;bar.value=0;return;}
    const b=SageFmp4Fallback.getBufferInfo(),total=playbackDurationSeconds(),base=Number(b.absoluteOffset!=null?b.absoluteOffset:compatOffset)||0,absStart=base+(Number(b.start)||0),absEnd=base+(Number(b.end)||0),mb=(Number(b.serverBytes||b.bytesReceived)||0)/1048576;
    bar.max=total>0?total:1;bar.value=total>0?Math.min(total,Math.max(0,absEnd)):0;
    const range=(b.end>b.start)?`${formatTime(absStart)}–${formatTime(absEnd)}`:'waiting';
    const player=b.nativeHls?'native HLS':b.hlsJs?'hls.js':'HLS';
    const server=(Number(b.serverSegments)||0)>0?` · server ${b.serverSegments} seg / ${(Number(b.serverSeconds)||0).toFixed(1)}s`:'';
    const encoder=b.encoder?` · ${b.encoder}${b.hardware?' HW':''}`:'';
    text.textContent=`Buffer: ${range} · ${(Number(b.ahead)||0).toFixed(1)}s ahead${server} · ${mb.toFixed(1)} MiB · ${b.state} · ${player}${encoder}${b.softwareFallback?' · software fallback':''}${b.recoveryCount?` · recovery ${b.recoveryCount}`:''}${b.error?` · ${b.error}`:''}`;
  }
  function startCompatBufferTimer(){clearCompatBufferTimer();updateCompatBufferUi();compatBufferTimer=setInterval(updateCompatBufferUi,500);}

  function updateTime(seconds){
    seconds=Math.max(0,Number(seconds)||0);$('timeNow').textContent=formatTime(seconds);if(!$('seek').disabled&&!suppressSeek)$('seek').value=Math.min(Number($('seek').max),seconds);updateMediaSessionPosition(seconds);
    if(seconds>lastProgressSeconds+0.05||seconds<lastProgressSeconds-1){lastProgressSeconds=seconds;lastProgressAt=Date.now();if(liveReconnectAttempts>0&&Date.now()-lastProgressAt<1000){} }
    if(media&&!media.recording&&mode){saveResume(seconds,false);if($('syncSageProgress').checked)syncSageProgress(seconds,false);}
  }

  $('playAuto').onclick=startAuto;
  $('playWasm').onclick=()=>startWasm({autoSelected:false,startSeconds:desiredStartSeconds()});
  $('playNative').onclick=()=>startNative({startSeconds:desiredStartSeconds()});
  $('playCompat').onclick=()=>startCompat(desiredStartSeconds());
  $('stop').onclick=()=>stopPlayback({keepResume:true});
  $('pause').onclick=async()=>{try{saveResume(currentPlaybackSeconds(),true);if(mode==='wasm')await SageWasmDecoder.pause();else if(mode==='native')$('nativeVideo').pause();else if(mode==='compat')SageFmp4Fallback.pause();paused=true;if('mediaSession' in navigator)navigator.mediaSession.playbackState='paused';setStatus('Paused.');}catch(e){setStatus(`Pause failed: ${e.message}`,true)}};
  $('resume').onclick=async()=>{try{if(mode==='wasm')await SageWasmDecoder.resume();else if(mode==='native')await $('nativeVideo').play();else if(mode==='compat')await SageFmp4Fallback.resume();paused=false;if('mediaSession' in navigator)navigator.mediaSession.playbackState='playing';setStatus('Resumed.');}catch(e){setStatus(`Resume failed: ${e.message}`,true)}};
  $('liveEdge').onclick=()=>{if(media&&media.recording)startWasm({liveEdge:true,autoSelected:false});};
  $('clearResume').onclick=async()=>{const hadServer=media&&Number(media.serverResumeSeconds)>15;clearResume();if(hadServer&&$('syncSageProgress').checked){try{await libraryAction(media.id,'unwatched');media.serverResumeSeconds=0;media.watched=false;media.watchedCompletely=false;setStatus('Browser and SageTV resume/watched state cleared.');await loadRecordings();}catch(e){setStatus(`Browser resume cleared, but SageTV state could not be cleared: ${e.message}`,true);}}else{setStatus(hadServer?'Browser resume cleared. SageTV resume remains; enable SageTV progress sync to clear it too.':'Saved browser resume position cleared.');}updateResumeUi();applyLibraryFilters();};
  $('skipBack').onclick=()=>seekToSeconds(currentPlaybackSeconds()-15);
  $('skipForward').onclick=()=>seekToSeconds(currentPlaybackSeconds()+30);
  $('previousEpisode').onclick=()=>playNeighbor(-1);
  $('nextEpisode').onclick=()=>playNeighbor(1);
  $('favoriteCurrent').onclick=()=>{if(media&&State&&State.toggleFavorite){State.toggleFavorite(media.id);updateCurrentFavoriteButton();applyLibraryFilters();}};
  $('toggleCaptions').onclick=()=>{$('captionsEnabled').checked=!$('captionsEnabled').checked;$('captionsEnabled').dispatchEvent(new Event('change'));};
  $('mute').onclick=()=>{const slider=$('volume'),cur=Number(slider.value)||0;if(cur>0){$('mute').dataset.previous=String(cur);slider.value='0';$('mute').textContent='Unmute';}else{slider.value=$('mute').dataset.previous||String(getSettings().volume||1);$('mute').textContent='Mute';}slider.dispatchEvent(new Event('input'));};

  $('seek').addEventListener('input',e=>{$('timeNow').textContent=formatTime(e.target.value);});
  $('seek').addEventListener('change',async e=>{try{await seekToSeconds(Number(e.target.value)||0);}catch(err){setStatus(`Seek failed: ${err.message}`,true)}});
  $('audioTrack').addEventListener('change',async e=>{if(mode==='wasm'&&e.target.value){try{const id=Number(e.target.value);await SageWasmDecoder.selectAudio(id);const t=(currentTracks.audio||[]).find(x=>Number(x.id)===id);const lang=trackLanguage(t);if(lang){$('preferredAudioLang').value=Array.from($('preferredAudioLang').options).some(o=>o.value===lang)?lang:'auto';persistSetting('preferredAudioLang',$('preferredAudioLang').value);}setStatus(`Audio stream ${e.target.value} selected.`);diagEvent('audio-change',{streamId:id,lang});}catch(err){setStatus(`Audio switch failed: ${err.message}`,true)}}});
  $('captionService').addEventListener('change',e=>{persistSetting('captionService',e.target.value);if(captionParser)captionParser.setService(e.target.value);diagEvent('caption-service',e.target.value)});
  $('captionsEnabled').addEventListener('change',e=>{persistSetting('captionsEnabled',e.target.checked);if(captionParser)captionParser.setEnabled(e.target.checked);if(!e.target.checked)$('captionOverlay').innerHTML=''});
  $('autoFallback').addEventListener('change',e=>persistSetting('autoFallback',e.target.checked));
  $('resumeEnabled').addEventListener('change',e=>{persistSetting('resumeEnabled',e.target.checked);updateResumeUi();});
  $('startLiveAtEdge').addEventListener('change',e=>persistSetting('startLiveAtEdge',e.target.checked));
  $('autoReconnectLive').addEventListener('change',e=>persistSetting('autoReconnectLive',e.target.checked));
  $('offlineOnly').addEventListener('change',e=>persistSetting('offlineOnly',e.target.checked));
  $('autoPlayNext').addEventListener('change',e=>persistSetting('autoPlayNext',e.target.checked));
  $('autoSkipCommercials').addEventListener('change',e=>{persistSetting('autoSkipCommercials',e.target.checked);if(commercialController)commercialController.setEnabled(e.target.checked);});
  $('tenFootMode').addEventListener('change',e=>{persistSetting('tenFootMode',e.target.checked);if(window.SageRemoteNavigation)SageRemoteNavigation.setEnabled(e.target.checked);});
  $('syncSageProgress').addEventListener('change',e=>{persistSetting('syncSageProgress',e.target.checked);lastServerSyncAt=0;setStatus(e.target.checked?'SageTV progress sync enabled for completed recordings.':'SageTV progress sync disabled; browser-local resume remains active.');});
  $('autoPlayQueue').addEventListener('change',e=>persistSetting('autoPlayQueue',e.target.checked));
  $('skipCommercial').onclick=()=>commercialController&&commercialController.skipCurrent();
  $('preferredAudioLang').addEventListener('change',e=>persistSetting('preferredAudioLang',e.target.value));
  $('volume').addEventListener('input',e=>{const v=Number(e.target.value);persistSetting('volume',v);if(mode==='wasm')SageWasmDecoder.setVolume(v);else if(mode==='native')$('nativeVideo').volume=v;else if(mode==='compat')SageFmp4Fallback.setVolume(v)});
  $('rate').addEventListener('change',e=>{const r=Number(e.target.value);persistSetting('rate',r);if(media&&media.recording)return;if(mode==='wasm')SageWasmDecoder.setPlaybackRate(r);else if(mode==='native')$('nativeVideo').playbackRate=r;else if(mode==='compat'&&SageFmp4Fallback.element())SageFmp4Fallback.element().playbackRate=r;});
  $('fullscreen').onclick=async()=>{try{if(!document.fullscreenElement)await $('playerStage').requestFullscreen();else await document.exitFullscreen();}catch(_){} };

  async function requestWakeLock(){try{if(navigator.wakeLock)wakeLock=await navigator.wakeLock.request('screen')}catch(_){} }
  function releaseWakeLock(){try{if(wakeLock)wakeLock.release()}catch(_){}wakeLock=null;}
  function browserDiagnostics(){
    const out={userAgent:navigator.userAgent,platform:navigator.platform||'',language:navigator.language||'',hardwareConcurrency:navigator.hardwareConcurrency||null,deviceMemory:navigator.deviceMemory||null,secureContext:!!window.isSecureContext,crossOriginIsolated:!!window.crossOriginIsolated,sharedArrayBuffer:!!window.SharedArrayBuffer,worker:!!window.Worker,webAssembly:!!window.WebAssembly,screen:{width:screen.width,height:screen.height,availWidth:screen.availWidth,availHeight:screen.availHeight,pixelRatio:window.devicePixelRatio||1}};
    try{const c=document.createElement('canvas'),gl=c.getContext('webgl2')||c.getContext('webgl');if(gl){const ext=gl.getExtension('WEBGL_debug_renderer_info');out.webgl={version:gl.getParameter(gl.VERSION),renderer:ext?gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER)};}}catch(_){} return out;
  }
  function capabilityText(){const v=document.createElement('video');return `Native TS: ${v.canPlayType('video/mp2t')||'no'} · native MPEG-2/AC-3: ${v.canPlayType('video/mp2t; codecs="mp2v, ac-3"')||'no'} · WebAssembly: ${window.WebAssembly?'yes':'no'} · WebAudio: ${(window.AudioContext||window.webkitAudioContext)?'yes':'no'} · secure: ${window.isSecureContext?'yes':'no'} · isolated: ${window.crossOriginIsolated?'yes':'no'}`;}
  async function probeWasm(){try{wasmProbe=await SageWasmDecoder.probe();$('capability').textContent=`${capabilityText()} · Wasm SIMD: ${wasmProbe.simd?'yes':'no'} · atomic: ${wasmProbe.atomic?'yes':'no'} · workers: ${wasmProbe.worker?'yes':'no'} · Wasm threads: ${wasmProbe.wasmThreads?'yes':'no'}`;renderDeviceIdle();}catch(e){wasmProbe={error:e.message};$('capability').textContent=`${capabilityText()} · Wasm probe failed: ${e.message}`;renderDeviceIdle();}}
  function renderDeviceIdle(){if(mode==='wasm')return;const b=browserDiagnostics(),p=wasmProbe||{};$('performanceStatus').dataset.state='warming';$('performanceStatus').textContent=[`Device: ${/iphone|ipad|ipod/i.test(b.userAgent)?'iOS/iPadOS':b.platform||'browser'}`,`CPU threads reported: ${b.hardwareConcurrency||'unknown'}`,`Secure context: ${b.secureContext?'yes':'no'}`,`Cross-origin isolated: ${b.crossOriginIsolated?'yes':'no'}`,`Web Workers: ${p.worker?'yes':'no'}`,`Wasm SIMD: ${p.simd?'yes':'no'}`,`Wasm threads: ${p.wasmThreads?'enabled':'not available'}`,`Decoder policy: ${$('offlineOnly').checked?'local cache only':'local cache with pinned CDN fallback'}`,(!b.secureContext?'Tip: Jetty HTTPS enables SharedArrayBuffer/Wasm threads on compatible browsers.':'')].filter(Boolean).join('\n');}
  function buildDiagnosticReport(){return{schema:'sagetv-webplayer-diagnostics-6',generatedAt:new Date().toISOString(),page:location.href,mode,server:serverInfo,assets:assetInfo,transcoder,media,segment,rawUrl,settings:getSettings(),resume:resumeEntry(),profile:State&&State.getActiveProfile?State.getActiveProfile():'default',favorites:State&&State.getFavorites?State.getFavorites():[],queue:State&&State.getQueue?State.getQueue():[],favoriteChannels:State&&State.getChannelFavorites?State.getChannelFavorites():[],recent:State?State.getRecent():[],library:{loaded:libraryItems.length,view:libraryFilterOptions()},guide:{start:guideStart,loaded:guideItems.length},dvr:{scheduled:(dvrInfo.scheduled||[]).length,conflicts:(dvrInfo.conflicts||[]).length},commercials:commercialController?commercialController.snapshot():commercialInfo,liveReconnect:{attempts:liveReconnectAttempts,lastProgressAt:new Date(lastProgressAt).toISOString()},browser:browserDiagnostics(),wasmProbe,wasmSession:window.SageWasmDecoder?SageWasmDecoder.session():null,decoderStats:lastDecoderStats,captions:lastCaptionStats,performance:watchdog?watchdog.snapshot():null,compatBuffer:window.SageFmp4Fallback&&SageFmp4Fallback.getBufferInfo?SageFmp4Fallback.getBufferInfo():null,events:diagnosticEvents.slice()};}
  function reportText(){return JSON.stringify(buildDiagnosticReport(),null,2);}
  $('downloadDiagnostics').onclick=()=>{const blob=new Blob([reportText()],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);const id=media&&media.id?`-${media.id}`:'';a.download=`sagetv-webplayer-diagnostics${id}-${new Date().toISOString().replace(/[:.]/g,'-')}.json`;document.body.appendChild(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove();},1000);diagEvent('diagnostics-download',{});};
  $('copyDiagnostics').onclick=async()=>{const text=reportText();try{if(navigator.clipboard&&window.isSecureContext){await navigator.clipboard.writeText(text);}else{const t=document.createElement('textarea');t.value=text;t.style.position='fixed';t.style.opacity='0';document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();}setStatus('Diagnostic report copied to the clipboard.');}catch(e){setStatus(`Could not copy diagnostics: ${e.message}`,true);}};

  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&mode==='wasm'){SageWasmDecoder.resume().catch(()=>{});requestWakeLock();if(media&&media.recording&&Date.now()-lastProgressAt>12000)scheduleLiveReconnect('return from background with stale live stream');}});
  document.addEventListener('keydown',async e=>{
    if(['INPUT','SELECT','TEXTAREA'].includes(document.activeElement&&document.activeElement.tagName))return;
    if(e.code==='Space'){e.preventDefault();try{if(paused){if(mode==='wasm')await SageWasmDecoder.resume();else if(mode==='native')await $('nativeVideo').play();else if(mode==='compat')await SageFmp4Fallback.resume();paused=false;}else{saveResume(currentPlaybackSeconds(),true);if(mode==='wasm')await SageWasmDecoder.pause();else if(mode==='native')$('nativeVideo').pause();else if(mode==='compat')SageFmp4Fallback.pause();paused=true;}}catch(_){}}
    else if(e.key==='ArrowRight'&&media&&!media.recording){const n=Math.min(playbackDurationSeconds(),currentPlaybackSeconds()+30);$('seek').value=n;$('seek').dispatchEvent(new Event('change'));}
    else if(e.key==='ArrowLeft'&&media&&!media.recording){const n=Math.max(0,currentPlaybackSeconds()-15);$('seek').value=n;$('seek').dispatchEvent(new Event('change'));}
    else if(e.key.toLowerCase()==='f')$('fullscreen').click();
    else if(e.key.toLowerCase()==='c'){$('captionsEnabled').checked=!$('captionsEnabled').checked;$('captionsEnabled').dispatchEvent(new Event('change'));}
    else if(e.key.toLowerCase()==='m'){$('volume').value=Number($('volume').value)>0?0:getSettings().volume||1;$('volume').dispatchEvent(new Event('input'));}
  });
  window.addEventListener('beforeunload',()=>saveResume(currentPlaybackSeconds(),true));
  $('segment').addEventListener('change',()=>{if(media)loadMedia(media.id,Number($('segment').value||0),false)});

  function applySavedSettings(){
    const s=getSettings();$('captionsEnabled').checked=s.captionsEnabled!==false;$('captionService').value=s.captionService||'CC1';$('autoFallback').checked=s.autoFallback!==false;$('volume').value=String(s.volume==null?1:s.volume);$('rate').value=String(s.rate||1);$('preferredAudioLang').value=Array.from($('preferredAudioLang').options).some(o=>o.value===s.preferredAudioLang)?s.preferredAudioLang:'auto';$('resumeEnabled').checked=s.resumeEnabled!==false;$('startLiveAtEdge').checked=s.startLiveAtEdge!==false;$('autoReconnectLive').checked=s.autoReconnectLive!==false;$('offlineOnly').checked=s.offlineOnly===true;$('autoPlayNext').checked=s.autoPlayNext===true;$('autoSkipCommercials').checked=s.autoSkipCommercials===true;$('tenFootMode').checked=s.tenFootMode===true;$('syncSageProgress').checked=s.syncSageProgress===true;$('autoPlayQueue').checked=s.autoPlayQueue!==false;$('favoriteChannelsOnly').checked=s.favoriteChannelsOnly===true;if(window.SageRemoteNavigation)SageRemoteNavigation.setEnabled(s.tenFootMode===true);
  }
  async function init(){
    commercialController=new SageCommercialSkip.CommercialSkipController({getTime:currentPlaybackSeconds,seek:seekToSeconds,onState:commercialState});commercialController.setEnabled(getSettings().autoSkipCommercials===true);commercialController.start();
    applySavedSettings();setupMediaSession();disableSessionControls();updateButtons();await Promise.all([checkServer(),checkAssets(),checkTranscoder(),probeWasm()]);await loadRecordings();
    const id=new URLSearchParams(location.search).get('id');if(id&&/^\d+$/.test(id))loadMedia(Number(id),0,false);
  }
  window.SageWebPlayerApp={loadMedia,startPreferred,startAuto,startWasm,seekToSeconds,currentPlaybackSeconds,getMedia:()=>media,loadGuide,loadDvr,loadRecordings,showRecordingDetails,getRecordings:()=>libraryItems.slice(),getGuide:()=>guideItems.slice()};
  init();
})();
