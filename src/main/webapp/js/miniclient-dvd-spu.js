/* P09 native DVD SPU bitmap controller. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory();else root.SageMiniDvdSpu=factory();})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
class Controller{
 constructor(o){this.o=o;this.generation=0;this.timer=null;this.id='';this.received=0;this.clears=0;this.error='';this.queueInfo=null;}
 stop(){++this.generation;clearTimeout(this.timer);this.timer=null;this.id='';this.queueInfo=null;this.o.clear();}
 start(ev){this.stop();if(!ev||!ev.dvd||!ev.hlsSession)return;this.id=String(ev.hlsSession);this.poll(this.generation);}
 async poll(serial){if(serial!==this.generation||!this.id)return;let again=80;try{const now=Math.max(0,Math.floor(this.o.timeMs()));const data=await this.o.fetch({session:this.id,scope:'dvd',timeMs:now,max:256});if(serial!==this.generation)return;this.queueInfo=data.queue||this.queueInfo;for(const cue of Array.isArray(data.cues)?data.cues:[]){if(!cue||cue.owner!=='dvd_spu')continue;this.received++;if(cue.clear){this.clears++;this.o.clear();}if(cue.kind==='bitmap'&&Array.isArray(cue.rect)&&cue.format==='rgba-straight'&&typeof cue.data==='string'){const w=Number(cue.rect[2])|0,h=Number(cue.rect[3])|0,rgba=from64(cue.data);if(w>0&&h>0&&rgba.length===w*h*4)this.o.render(cue,rgba);}}if((data.cues||[]).length>=128)again=20;}catch(e){if(serial===this.generation){this.error=e.message||String(e);again=1000;}}finally{if(serial===this.generation&&this.id)this.timer=setTimeout(()=>this.poll(serial),again);}}
 snapshot(){return{active:!!this.id,received:this.received,clears:this.clears,queue:this.queueInfo,error:this.error};}
}
function from64(v){if(typeof Buffer!=='undefined'&&Buffer.from)return new Uint8Array(Buffer.from(v,'base64'));const r=atob(v),o=new Uint8Array(r.length);for(let i=0;i<r.length;i++)o[i]=r.charCodeAt(i)&255;return o;}
return Controller;
});
