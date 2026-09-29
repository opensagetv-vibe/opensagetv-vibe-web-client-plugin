(function (global) {
  'use strict';

  const PREFIX = 'sagetv.webplayer.v2';
  const LEGACY = 'sagetv.webplayer.v1';
  const ACTIVE_PROFILE_KEY = PREFIX + '.activeProfile';
  const PROFILES_KEY = PREFIX + '.profiles';
  const DEFAULTS = Object.freeze({
    captionsEnabled:true, captionService:'CC1', autoFallback:true, volume:1, rate:1,
    preferredAudioLang:'auto', resumeEnabled:true, startLiveAtEdge:true, autoReconnectLive:true,
    offlineOnly:false, autoPlayNext:false, autoSkipCommercials:false, tenFootMode:false,
    syncSageProgress:false, autoPlayQueue:true, favoriteChannelsOnly:false
  });

  const memory = {};
  function storage() {
    try {
      if (global.localStorage) {
        const k=PREFIX+'.probe'; global.localStorage.setItem(k,'1'); global.localStorage.removeItem(k); return global.localStorage;
      }
    } catch (_) {}
    return {getItem(k){return Object.prototype.hasOwnProperty.call(memory,k)?memory[k]:null;},setItem(k,v){memory[k]=String(v);},removeItem(k){delete memory[k];}};
  }
  function readJson(key,fallback){try{const raw=storage().getItem(key);if(!raw)return fallback;const v=JSON.parse(raw);return v==null?fallback:v;}catch(_){return fallback;}}
  function writeJson(key,value){try{storage().setItem(key,JSON.stringify(value));return true;}catch(_){return false;}}
  function slug(name){name=String(name||'').trim().toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'').slice(0,32);return name||'default';}
  function ensureProfiles(){let list=readJson(PROFILES_KEY,[]);if(!Array.isArray(list))list=[];if(!list.some(x=>x&&x.id==='default'))list.unshift({id:'default',name:'Default'});list=list.filter((x,i,a)=>x&&x.id&&a.findIndex(y=>y.id===x.id)===i).slice(0,12);writeJson(PROFILES_KEY,list);return list;}
  function getProfiles(){return ensureProfiles().map(x=>({id:x.id,name:x.name||x.id}));}
  function getActiveProfile(){const list=ensureProfiles();let id=storage().getItem(ACTIVE_PROFILE_KEY)||'default';if(!list.some(x=>x.id===id))id='default';return id;}
  function setActiveProfile(id){id=slug(id);if(!ensureProfiles().some(x=>x.id===id))throw new Error('Unknown profile: '+id);storage().setItem(ACTIVE_PROFILE_KEY,id);return id;}
  function createProfile(name){name=String(name||'').trim();if(!name)throw new Error('Profile name is required');let id=slug(name),list=ensureProfiles();if(list.some(x=>x.id===id))throw new Error('A profile with that name already exists');list.push({id,name:name.slice(0,40)});writeJson(PROFILES_KEY,list);return {id,name};}
  function removeProfile(id){id=slug(id);if(id==='default')return false;let list=ensureProfiles().filter(x=>x.id!==id);writeJson(PROFILES_KEY,list);['settings','resume','recent','favorites','queue','channels'].forEach(k=>storage().removeItem(profileKey(k,id)));if(getActiveProfile()===id)setActiveProfile('default');return true;}
  function profileKey(suffix,id){return PREFIX+'.profile.'+(id||getActiveProfile())+'.'+suffix;}
  function maybeMigrateLegacy(){if(getActiveProfile()!=='default')return;const target=profileKey('settings','default');if(storage().getItem(target))return;const map={settings:'settings',resume:'resume',recent:'recent',favorites:'favorites'};Object.keys(map).forEach(k=>{const raw=storage().getItem(LEGACY+'.'+k);if(raw)storage().setItem(profileKey(map[k],'default'),raw);});}
  function clamp(v,min,max,fallback){v=Number(v);return Number.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
  function cleanSettings(s){s=Object.assign({},DEFAULTS,s||{});return{
    captionsEnabled:s.captionsEnabled!==false,captionService:typeof s.captionService==='string'&&s.captionService?s.captionService:'CC1',autoFallback:s.autoFallback!==false,
    volume:clamp(s.volume,0,1,1),rate:[.5,.75,1,1.25,1.5,2].includes(Number(s.rate))?Number(s.rate):1,
    preferredAudioLang:typeof s.preferredAudioLang==='string'&&s.preferredAudioLang?s.preferredAudioLang.toLowerCase():'auto',resumeEnabled:s.resumeEnabled!==false,
    startLiveAtEdge:s.startLiveAtEdge!==false,autoReconnectLive:s.autoReconnectLive!==false,offlineOnly:s.offlineOnly===true,autoPlayNext:s.autoPlayNext===true,
    autoSkipCommercials:s.autoSkipCommercials===true,tenFootMode:s.tenFootMode===true,syncSageProgress:s.syncSageProgress===true,
    autoPlayQueue:s.autoPlayQueue!==false,favoriteChannelsOnly:s.favoriteChannelsOnly===true
  };}
  function getSettings(){maybeMigrateLegacy();return cleanSettings(readJson(profileKey('settings'),{}));}
  function updateSettings(patch){const next=cleanSettings(Object.assign({},getSettings(),patch||{}));writeJson(profileKey('settings'),next);return next;}

  function resumeKey(id,segment){return String(id)+':'+String(segment==null?0:segment);}
  function getResumeMap(){maybeMigrateLegacy();const x=readJson(profileKey('resume'),{});return x&&typeof x==='object'?x:{};}
  function getResume(id,segment){const x=getResumeMap()[resumeKey(id,segment)];if(!x||!(Number(x.position)>0))return null;return{position:Number(x.position)||0,duration:Number(x.duration)||0,updatedAt:Number(x.updatedAt)||0,title:x.title||''};}
  function clearResume(id,segment){const all=getResumeMap();delete all[resumeKey(id,segment)];writeJson(profileKey('resume'),all);}
  function saveResume(id,segment,position,duration,title){position=Number(position)||0;duration=Number(duration)||0;if(!(id>0)||position<15){clearResume(id,segment);return null;}if(duration>0&&(position>=duration-45||position/duration>=.95)){clearResume(id,segment);return null;}const all=getResumeMap(),entry={position,duration,updatedAt:Date.now(),title:title||''};all[resumeKey(id,segment)]=entry;const keys=Object.keys(all).sort((a,b)=>(all[b].updatedAt||0)-(all[a].updatedAt||0));while(keys.length>200)delete all[keys.pop()];writeJson(profileKey('resume'),all);return entry;}

  function numberList(key,max){const list=readJson(profileKey(key),[]);return Array.isArray(list)?list.map(Number).filter(x=>Number.isFinite(x)&&x>0).slice(0,max||500):[];}
  function setNumberList(key,list,max){writeJson(profileKey(key),list.map(Number).filter(x=>Number.isFinite(x)&&x>0).slice(0,max||500));}
  function getFavorites(){maybeMigrateLegacy();return numberList('favorites',500);}
  function isFavorite(id){return getFavorites().includes(Number(id));}
  function setFavorite(id,enabled){id=Number(id);if(!(id>0))return false;let list=getFavorites().filter(x=>x!==id);if(enabled)list.unshift(id);setNumberList('favorites',list,500);return !!enabled;}
  function toggleFavorite(id){return setFavorite(id,!isFavorite(id));}

  function getQueue(){return numberList('queue',200);}
  function isQueued(id){return getQueue().includes(Number(id));}
  function setQueued(id,enabled){id=Number(id);if(!(id>0))return false;let list=getQueue().filter(x=>x!==id);if(enabled)list.push(id);setNumberList('queue',list,200);return !!enabled;}
  function toggleQueue(id){return setQueued(id,!isQueued(id));}
  function moveQueue(id,delta){id=Number(id);let q=getQueue(),i=q.indexOf(id);if(i<0)return q;let j=Math.max(0,Math.min(q.length-1,i+Number(delta||0)));if(i!==j){q.splice(i,1);q.splice(j,0,id);setNumberList('queue',q,200);}return q;}
  function shiftQueue(id){let q=getQueue();if(id!=null)q=q.filter(x=>x!==Number(id));else q.shift();setNumberList('queue',q,200);return q;}
  function clearQueue(){setNumberList('queue',[],200);}

  function getChannelFavorites(){const list=readJson(profileKey('channels'),[]);return Array.isArray(list)?list.map(String).filter(Boolean).slice(0,500):[];}
  function isChannelFavorite(key){return getChannelFavorites().includes(String(key));}
  function setChannelFavorite(key,enabled){key=String(key||'');if(!key)return false;let list=getChannelFavorites().filter(x=>x!==key);if(enabled)list.unshift(key);writeJson(profileKey('channels'),list.slice(0,500));return !!enabled;}
  function toggleChannelFavorite(key){return setChannelFavorite(key,!isChannelFavorite(key));}

  function getRecent(){maybeMigrateLegacy();const list=readJson(profileKey('recent'),[]);return Array.isArray(list)?list.slice(0,50):[];}
  function markPlayed(item){if(!item||!(Number(item.id)>0))return;const row={id:Number(item.id),segment:Number(item.segment)||0,title:item.title||'',episode:item.episode||'',at:Date.now()};const list=getRecent().filter(x=>Number(x.id)!==row.id||Number(x.segment)!==row.segment);list.unshift(row);writeJson(profileKey('recent'),list.slice(0,50));}

  function exportProfile(){return{schema:2,exportedAt:new Date().toISOString(),profile:getActiveProfile(),settings:getSettings(),resume:getResumeMap(),favorites:getFavorites(),recent:getRecent(),queue:getQueue(),channelFavorites:getChannelFavorites()};}
  function importProfile(data){if(!data||typeof data!=='object')throw new Error('Invalid settings file');if(data.settings)writeJson(profileKey('settings'),cleanSettings(data.settings));if(data.resume&&typeof data.resume==='object')writeJson(profileKey('resume'),data.resume);if(Array.isArray(data.favorites))setNumberList('favorites',data.favorites,500);if(Array.isArray(data.recent))writeJson(profileKey('recent'),data.recent.slice(0,50));if(Array.isArray(data.queue))setNumberList('queue',data.queue,200);if(Array.isArray(data.channelFavorites))writeJson(profileKey('channels'),data.channelFavorites.map(String).filter(Boolean).slice(0,500));return exportProfile();}

  ensureProfiles(); maybeMigrateLegacy();
  global.SageSessionState={version:'2.0.0',defaults:DEFAULTS,getSettings,updateSettings,getResume,saveResume,clearResume,getFavorites,isFavorite,setFavorite,toggleFavorite,getRecent,markPlayed,
    getQueue,isQueued,setQueued,toggleQueue,moveQueue,shiftQueue,clearQueue,getChannelFavorites,isChannelFavorite,setChannelFavorite,toggleChannelFavorite,
    getProfiles,getActiveProfile,setActiveProfile,createProfile,removeProfile,exportProfile,importProfile};
})(typeof window!=='undefined'?window:globalThis);
