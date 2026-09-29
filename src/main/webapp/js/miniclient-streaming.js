/* Web equivalents of Vibe Android streaming/track preferences. Android-only
 * codecs, subtitle decoders and server caption authority are not faked here. */
(function(root,factory){
  if(typeof module==='object'&&module.exports)module.exports=factory();
  else root.SageMiniStreaming=factory();
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const DEFAULTS=Object.freeze({profileSchemaVersion:8,player:'auto',videoMode:'auto',encoder:'auto',videoKbps:8000,keyFrameSeconds:1,bFrames:0,resolution:'source',fps:'source',deinterlace:'auto',
    audioCodec:'auto',audioKbps:192,audioChannels:'2',audioTrack:-1,audioLanguage:'',audioFallback:'default',audioOffsetMs:0,
    captionAuthority:'off',captions:'off',captionService:1,captionPage:888,captionPid:-1,captionCompositionPage:-1,captionLanguage:'',captionOffsetMs:0,subtitleMode:'off',subtitleTrack:-1,subtitleLanguage:'',subtitleOffsetMs:0,subtitleForcedOnly:false,allowFallback:true,hevcSupported:false,ac3Supported:false});
  const choices={player:['auto','mpegts','hls'],videoMode:['auto','transcode','copy','mpeg2copy'],encoder:['auto','software','qsv','nvenc','vaapi','amf','videotoolbox'],
    resolution:['source','480','720','1080','2160'],fps:['source','23.976','24','25','29.97','30','50','59.94','60'],deinterlace:['auto','on','off'],
    audioCodec:['auto','aac','ac3','copy'],audioChannels:['source','1','2','6'],audioFallback:['default','first','strict'],captionAuthority:['off','cc1','cc2','stv','dvb'],captions:['off','608','708','teletext','dvb'],subtitleMode:['off','auto','track']};
  const ranges={profileSchemaVersion:[1,8],videoKbps:[250,50000],keyFrameSeconds:[1,10],bFrames:[0,3],audioKbps:[64,640],audioTrack:[-1,127],audioOffsetMs:[-4000,4000],captionService:[1,63],captionPage:[100,899],captionPid:[-1,8191],captionCompositionPage:[-1,65535],captionOffsetMs:[-5000,5000],subtitleTrack:[-1,4095],subtitleOffsetMs:[-10000,10000]};
  function normalize(input){
    const o=Object.assign({},DEFAULTS,input||{});
    if(String(o.videoMode)==='mpeg2copy')o.videoMode='copy'; // migrate v3.2.18/early-3.2.19 profiles
    for(const k of Object.keys(choices)){o[k]=String(o[k]);if(!choices[k].includes(o[k]))throw new Error('Invalid streaming '+k);}
    for(const k of Object.keys(ranges)){const n=Number(o[k]),r=ranges[k];if(!Number.isInteger(n)||n<r[0]||n>r[1])throw new Error(k+' must be '+r[0]+'..'+r[1]);o[k]=n;}
    for(const k of ['subtitleForcedOnly','allowFallback','hevcSupported','ac3Supported']){
      if(o[k]!==true&&o[k]!==false&&o[k]!=='true'&&o[k]!=='false')throw new Error('Invalid streaming '+k);o[k]=o[k]===true||o[k]==='true';
    }
    o.audioLanguage=String(o.audioLanguage).trim();o.captionLanguage=String(o.captionLanguage).trim();o.subtitleLanguage=String(o.subtitleLanguage).trim();
    if(o.audioLanguage.length>35||!/^$|^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$/.test(o.audioLanguage))throw new Error('Use an audio language such as en, eng, or en-US');if(o.captionLanguage.length>35||!/^$|^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$/.test(o.captionLanguage))throw new Error('Use a caption language such as en, eng, or en-US');if(o.subtitleLanguage.length>35||!/^$|^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$/.test(o.subtitleLanguage))throw new Error('Use a subtitle language such as en, eng, or en-US');o.profileSchemaVersion=8;
    if(o.captions==='608'&&o.captionService>4)throw new Error('CEA-608 channel must be 1..4');
    const clean={};for(const k of Object.keys(DEFAULTS))clean[k]=o[k];return clean;
  }
  function toParams(input,capabilities){
    const o=normalize(Object.assign({},input,capabilities||{})),p={};
    for(const k of Object.keys(DEFAULTS))p['stream_'+k]=o[k];return p;
  }
  function browserCapabilities(root){
    const M=root&&(root.MediaSource||root.ManagedMediaSource),check=s=>{try{return !!(M&&M.isTypeSupported(s));}catch(_){return false;}};
    return {hevcSupported:check('video/mp4; codecs="hvc1.1.6.L93.B0"'),ac3Supported:check('audio/mp4; codecs="ac-3"')};
  }
  function mpegtsConfig(bufferSeconds){
    const target=Math.max(30,Math.min(600,Number(bufferSeconds)||180));
    return {
      // isLive disables upstream lazy loading; DVR needs a growing, byte-resumable
      // non-live source even while the SageTV recording itself is still active.
      isLive:false,enableWorker:true,enableWorkerForMSE:true,
      enableStashBuffer:false,stashInitialSize:32*1024,
      lazyLoad:true,lazyLoadMaxDuration:target,lazyLoadRecoverDuration:Math.max(6,target-15),
      autoCleanupSourceBuffer:true,autoCleanupMaxBackwardDuration:60,autoCleanupMinBackwardDuration:30,
      liveBufferLatencyChasing:false,liveSync:false,seekType:'param',seekParamStart:'bstart',seekParamEnd:'bend',
      accurateSeek:false,fixAudioTimestampGap:true,statisticsInfoReportInterval:1000
    };
  }
  return {DEFAULTS,normalize,toParams,browserCapabilities,mpegtsConfig};
});
