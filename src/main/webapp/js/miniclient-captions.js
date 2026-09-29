/* Browser-local broadcast captions. CEA, Teletext and DVB bitmap subtitles use
 * the video clock, so presentation continues while SageMC hides its OSD. */
(function(root,factory){
  if(typeof module==='object'&&module.exports)module.exports=factory();
  else root.SageMiniCaptions=factory();
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  class Controller {
    constructor(options){this.o=options;this.generation=0;this.timer=null;this.pollTimer=null;this.decoder=null;this.teletext=false;this.dvb=false;this.stv=false;this.stvStateSeen=false;this.stvState=0;this.stvService='';this.queue=[];this.cursor=0;this.lastError='';this.received=0;this.clears=0;this.services=[];this.bitmapExpiry=0;this.queueInfo=null;}
    stop(){++this.generation;clearTimeout(this.timer);clearTimeout(this.pollTimer);this.timer=this.pollTimer=null;this.decoder=null;this.teletext=false;this.dvb=false;this.stv=false;this.stvStateSeen=false;this.stvState=0;this.stvService='';this.queue=[];this.cursor=0;this.services=[];this.bitmapExpiry=0;this.queueInfo=null;this.o.render('');if(this.o.clearBitmap)this.o.clearBitmap();}
    start(event){
      this.stop();this.lastError='';this.received=0;this.clears=0;
      const p=event.streamSettings&&event.streamSettings.requested||{},authority=event.captionAuthority||null;
      this.options=p;this.id=event.hlsSession;this.stv=!!(authority&&authority.mode==='stv');
      if(authority&&authority.mode==='off')return;
      if(!this.id||String(p.captions)==='off')return;
      if(String(p.captions)==='teletext'){this.teletext=true;this.pollTeletext(this.generation);return;}
      if(String(p.captions)==='dvb'){this.dvb=true;this.pollDvb(this.generation);return;}
      if(!['608','708'].includes(String(p.captions)))return;
      const serial=this.generation;
      const initialStvState=authority&&authority.stvStateSeen?Number(authority.stvState)||0:null;
      const service=this.stv?(initialStvState>0?'CC'+Math.max(1,Math.min(4,initialStvState)):'CC'+(Number(p.captionService)||1)):(p.captions==='708'?'708-'+p.captionService:'CC'+p.captionService);
      const enabled=!this.stv||initialStvState===null||initialStvState>0;
      this.decoder=new this.o.Decoder({enabled,service,onCaption:(text,svc,meta)=>{if(this.generation===serial)this.o.render(text,svc,meta);}});
      if(this.stv){this.stvStateSeen=initialStvState!==null;this.stvState=initialStvState===null?0:initialStvState;this.stvService=service;if(!enabled)this.o.render('');}
      const tick=()=>{
        if(serial!==this.generation||!this.decoder)return;
        const now=Math.max(0,this.o.timeMs());let count=0;
        while(this.queue.length&&this.queue[0][0]<=now+3000&&count++<300){const [pts,hex]=this.queue.shift();this.decoder.pushCcData(fromHex(hex),pts);}
        this.decoder.updateTime(Math.max(0,now-(Number(p.captionOffsetMs)||0)));
        this.timer=setTimeout(tick,100);
      };
      tick();this.poll(serial);
    }
    setSageTvState(value,seen=true){
      let state=Number.parseInt(value,10);if(!Number.isFinite(state)||state<0)state=0;
      this.stvStateSeen=!!seen;this.stvState=state;
      if(!this.stv||!this.decoder)return;
      if(this.stvStateSeen&&state===0){this.stvService='';if(typeof this.decoder.setEnabled==='function')this.decoder.setEnabled(false);this.o.render('');return;}
      const service='CC'+Math.max(1,Math.min(4,state||Number(this.options&&this.options.captionService)||1));
      this.stvService=service;if(typeof this.decoder.setService==='function')this.decoder.setService(service);if(typeof this.decoder.setEnabled==='function')this.decoder.setEnabled(true);
    }
    async poll(serial){
      if(serial!==this.generation||!this.decoder)return;
      let again=1000;
      try {
        const data=await this.o.fetch({session:this.id,cursor:this.cursor,untilMs:Math.floor(Math.max(0,this.o.timeMs())+10000)});
        if(serial!==this.generation||!this.decoder)return;
        const valid=(data.packets||[]).filter(p=>Array.isArray(p)&&p.length===2&&Number.isFinite(p[0])&&p[0]>=0&&typeof p[1]==='string'&&p[1].length<=186&&p[1].length%6===0&&/^[a-f0-9]*$/i.test(p[1]));
        this.cursor=Number(data.cursor)||0;this.received+=valid.length;
        this.queue.push(...valid);this.queue.sort((a,b)=>a[0]-b[0]);
        if(this.queue.length>12000){this.queue=[];this.stop();throw new Error('Caption queue limit exceeded; local captions stopped');}
        if(valid.length>=1024)again=100;
      }catch(e){
        if(serial===this.generation){this.lastError=e.message||String(e);if(this.o.error)this.o.error(this.lastError);again=3000;}
      }finally{if(serial===this.generation&&this.decoder)this.pollTimer=setTimeout(()=>this.poll(serial),again);}
    }
    async pollTeletext(serial){
      if(serial!==this.generation||!this.teletext)return;
      let again=350;
      try{
        const delay=Number(this.options&&this.options.captionOffsetMs)||0;
        const presentation=Math.max(0,Math.floor(this.o.timeMs()-delay));
        const data=await this.o.subtitleFetch({session:this.id,timeMs:presentation,max:256});
        if(serial!==this.generation||!this.teletext)return;
        const cues=Array.isArray(data.cues)?data.cues:[];
        for(const cue of cues){
          if(!cue||cue.kind!=='text'||cue.owner!=='local_broadcast')continue;
          this.received++;if(cue.clear||!cue.text){this.clears++;this.o.render('');}else this.o.render(String(cue.text),'teletext',{type:'teletext',ptsMs:Number(cue.ptsMs)||0});
        }
        this.queueInfo=data.queue||this.queueInfo;if(data.teletext&&Array.isArray(data.teletext.services))this.services=data.teletext.services;
        if(cues.length>=128)again=50;
      }catch(e){
        if(serial===this.generation){this.lastError=e.message||String(e);if(this.o.error)this.o.error('Teletext: '+this.lastError);again=2000;}
      }finally{if(serial===this.generation&&this.teletext)this.pollTimer=setTimeout(()=>this.pollTeletext(serial),again);}
    }
    async pollDvb(serial){
      if(serial!==this.generation||!this.dvb)return;
      let again=120;
      try{
        const delay=Number(this.options&&this.options.captionOffsetMs)||0;
        const presentation=Math.max(0,Math.floor(this.o.timeMs()-delay));
        if(this.bitmapExpiry>0&&presentation>=this.bitmapExpiry){this.bitmapExpiry=0;this.clears++;if(this.o.clearBitmap)this.o.clearBitmap();}
        const data=await this.o.subtitleFetch({session:this.id,timeMs:presentation,max:256});
        if(serial!==this.generation||!this.dvb)return;
        const cues=Array.isArray(data.cues)?data.cues:[];
        for(const cue of cues){
          if(!cue||cue.kind!=='bitmap'||cue.owner!=='local_dvb')continue;
          this.received++;
          if(cue.clear){this.clears++;this.bitmapExpiry=0;if(this.o.clearBitmap)this.o.clearBitmap();}
          const rect=Array.isArray(cue.rect)?cue.rect:[],w=Number(rect[2])|0,h=Number(rect[3])|0;
          if(w>0&&h>0&&cue.format==='rgba-straight'&&typeof cue.data==='string'){
            const rgba=fromBase64(cue.data);if(rgba.length!==w*h*4)throw new Error('Invalid DVB RGBA rectangle length');
            if(this.o.renderBitmap)this.o.renderBitmap(cue,rgba);
            const duration=Math.max(0,Number(cue.durationMs)||0);this.bitmapExpiry=duration>0?(Number(cue.ptsMs)||presentation)+duration:0;
          }
        }
        this.queueInfo=data.queue||this.queueInfo;if(data.dvbBitmap&&Array.isArray(data.dvbBitmap.services))this.services=data.dvbBitmap.services;
        if(cues.length>=128)again=25;else if(!cues.length)again=250;
      }catch(e){
        if(serial===this.generation){this.lastError=e.message||String(e);if(this.o.error)this.o.error('DVB bitmap: '+this.lastError);again=1500;}
      }finally{if(serial===this.generation&&this.dvb)this.pollTimer=setTimeout(()=>this.pollDvb(serial),again);}
    }
    snapshot(){return {mode:this.dvb?'dvb-bitmap-local':this.teletext?'teletext-local':this.decoder?(this.stv?'stv-controlled-browser-local':'browser-local'):'off',received:this.received,clears:this.clears,cursor:this.cursor,pending:this.queue.length,error:this.lastError,services:this.services,bitmapExpiry:this.bitmapExpiry,queue:this.queueInfo,stvControlled:this.stv,stvStateSeen:this.stvStateSeen,stvState:this.stvState,stvService:this.stvService,decoder:this.decoder?this.decoder.getStats():null};}
  }
  function fromHex(hex){const out=new Uint8Array(hex.length/2);for(let i=0;i<out.length;i++)out[i]=parseInt(hex.slice(i*2,i*2+2),16);return out;}
  function fromBase64(value){if(typeof Buffer!=='undefined'&&typeof Buffer.from==='function')return new Uint8Array(Buffer.from(value,'base64'));const raw=atob(value),out=new Uint8Array(raw.length);for(let i=0;i<raw.length;i++)out[i]=raw.charCodeAt(i)&255;return out;}
  return Controller;
});
