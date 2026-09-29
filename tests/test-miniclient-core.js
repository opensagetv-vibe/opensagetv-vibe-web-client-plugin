'use strict';
const assert=require('node:assert/strict');
const C=require('../src/main/webapp/js/miniclient-core.js');
class Clock {
 constructor(){this.t=1000;this.seq=0;this.jobs=new Map();}
 now=()=>this.t;
 setTimeout=(fn,ms)=>{const id=++this.seq;this.jobs.set(id,{at:this.t+ms,fn});return id;};
 clearTimeout=id=>this.jobs.delete(id);
 setInterval=(fn,ms)=>{const id=++this.seq;this.jobs.set(id,{at:this.t+ms,fn,repeat:ms});return id;};
 clearInterval=id=>this.jobs.delete(id);
 advance(ms){const end=this.t+ms;for(;;){let item=null;for(const [id,j] of this.jobs)if(j.at<=end&&(!item||j.at<item.j.at))item={id,j};if(!item)break;this.t=item.j.at;this.jobs.delete(item.id);item.j.fn();if(item.j.repeat&& !this.jobs.has(item.id))this.jobs.set(item.id,{at:this.t+item.j.repeat,fn:item.j.fn,repeat:item.j.repeat});}this.t=end;}
}
module.exports=Clock;
if(require.main===module){
 let count=0;function test(name,fn){fn();console.log('PASS '+name);count++;}
 const point={x:100,y:80};
 function fixture(trigger='left_long'){
  const clock=new Clock(),calls=[];let cfg={osdTrigger:trigger};
  const g=new C.PointerGesture({clock,settings:()=>cfg,click:(p,b)=>calls.push(['click',b]),open:()=>calls.push(['open']),back:()=>calls.push(['back']),dragStart:()=>calls.push(['down']),dragMove:()=>calls.push(['move']),dragEnd:()=>calls.push(['up'])});
  const ev=(button=0,x=100,y=80,id=1)=>({button,clientX:x,clientY:y,pointerId:id,isPrimary:true});
  return {clock,calls,g,ev};
 }

 test('unified graphics stays opt-in in browser settings',()=>{assert.equal(C.normalizeSettings({}).unifiedGraphics,false);assert.equal(C.normalizeSettings({unifiedGraphics:true}).unifiedGraphics,true);});
 test('unified YUV rows convert only after both planes arrive',()=>{const yuv=new C.UnifiedYuvImage(2,1);assert.equal(yuv.loadLine(0,new Uint8Array([82,82]),0,2),0);assert.deepEqual(Array.from(yuv.rowRgba(0)),[0,0,0,0,0,0,0,0]);yuv.loadLine(1,new Uint8Array([90,240]),0,2);const px=Array.from(yuv.rowRgba(0));assert(px[0]>200&&px[1]<100&&px[2]<100&&px[3]===255,px);assert.equal(yuv.complete(),true);});
 test('keyboard command repeat is owned and cancels on release',()=>{const clock=new Clock(),calls=[],r=new C.CommandRepeat({clock,send:c=>calls.push(c)});assert(r.press('ArrowDown',5));assert(!r.press('ArrowDown',5));assert.deepEqual(calls,[5]);clock.advance(419);assert.deepEqual(calls,[5]);clock.advance(1);assert.deepEqual(calls,[5,5]);clock.advance(150);assert.deepEqual(calls,[5,5,5]);r.release('ArrowDown');clock.advance(1000);assert.deepEqual(calls,[5,5,5]);});
 test('canvas point mapping clamps one shared UI transform',()=>{assert.deepEqual(C.canvasPoint(210,120,{left:10,top:20,width:400,height:200},800,400),{x:400,y:200});assert.deepEqual(C.canvasPoint(-20,999,{left:10,top:20,width:400,height:200},800,400),{x:0,y:399});});
 test('fast startup stays one segment regardless of reserve or producer rate',()=>{for(const r of [0,.5,.99,1,1.49,2,20])for(const maxBufferSeconds of [30,180,600])assert.equal(C.requiredStartSegments({hlsFillRate:r},{maxBufferSeconds}),1);assert.equal(C.requiredStartSegments({}, {bufferPreset:'stable'}),3);});
 test('buffer settings clamp and validate',()=>{const cfg=C.normalizeSettings({maxBufferSeconds:Infinity,startSegments:-2,osdTrigger:'bad',mouseBack:'false'});assert.equal(cfg.maxBufferSeconds,180);assert.equal(cfg.startSegments,0);assert.equal(cfg.osdTrigger,'left_long');assert.equal(cfg.mouseBack,true);assert.equal(C.requiredStartSegments({}, {startSegments:5}),5);});
 test('short finished clip can start with one segment',()=>{assert.equal(C.startupReady({playlistReady:true,segments:1,endList:true},{startSegments:4}),true);assert.equal(C.startupReady({playlistReady:true,segments:0,endList:true},{}),false);});
 test('separate startup and ongoing reserve targets',()=>{const cfg=C.hlsConfig({});assert.equal(cfg.startPosition,0);assert.equal(cfg.maxBufferLength,6);assert.equal(C.hlsConfig({},true).maxBufferLength,180);assert.equal(C.hlsConfig({bufferPreset:'lowdelay'},true).maxBufferLength,180);assert.equal(C.hlsConfig({maxBufferSeconds:600},true).maxMaxBufferLength,600);});
 test('recording buffering cannot force live-edge jumps or rate catch-up',()=>{const cfg=C.hlsConfig({});assert.equal(cfg.liveMaxLatencyDurationCount,Infinity);assert.equal(cfg.maxLiveSyncPlaybackRate,1);assert.equal(cfg.initialLiveManifestSize,1);assert.equal(cfg.lowLatencyMode,false);});
 test('bounded back buffer and worker-enabled transmuxer',()=>{const cfg=C.hlsConfig({maxBufferSeconds:600},true);assert.equal(cfg.backBufferLength,30);assert.equal(cfg.liveBackBufferLength,30);assert.equal(cfg.maxBufferSize,128*1024*1024);assert.equal(cfg.enableWorker,true);});
 test('auto resolution follows usable window and caps rendering load',()=>{assert.deepEqual(C.logicalSize(1912,750,'window'),{w:1912,h:750});assert.deepEqual(C.logicalSize(3840,2160,'window'),{w:1920,h:1080});assert.deepEqual(C.logicalSize(390,760,'window'),{w:390,h:760});assert.deepEqual(C.logicalSize(100,100,'window'),{w:320,h:240});assert.equal(C.normalizeSettings({}).uiResolution,'window');});
 test('fixed rendering resolution does not crop desktop or portrait windows',()=>{for(const [w,h] of [[1912,750],[390,760],[800,800],[3840,1000]]){const r=C.fitRect(w,h,1280,720);assert(Math.abs(r.width/r.height-16/9)<1e-9);assert(r.left>=-1e-8&&r.top>=-1e-8);assert(r.left+r.width<=w+1e-8&&r.top+r.height<=h+1e-8);assert(Math.abs(r.width-w)<1e-8||Math.abs(r.height-h)<1e-8);}assert.deepEqual(C.logicalSize(1912,750,'1280x720'),{w:1280,h:720});});
 test('short click sends one click and suppresses browser duplicate',()=>{const f=fixture();f.g.down(f.ev(),point);f.clock.advance(100);f.g.up(f.ev(),point);f.g.fallbackClick(f.ev(),point);assert.deepEqual(f.calls,[['click',1]]);});
 test('hold opens at 575ms, never clicks underlying item',()=>{const f=fixture();f.g.down(f.ev(),point);f.clock.advance(574);assert.equal(f.calls.length,0);f.clock.advance(1);f.g.up(f.ev(),point);f.g.fallbackClick(f.ev(),point);assert.deepEqual(f.calls,[['open']]);});
 test('touch primary pointer supports hold',()=>{const f=fixture();const ev={...f.ev(),pointerType:'touch'};f.g.down(ev,point);f.clock.advance(600);f.g.up(ev,point);assert.deepEqual(f.calls,[['open']]);});
 test('secondary touch cannot steal hold',()=>{const f=fixture();f.g.down(f.ev(),point);assert.equal(f.g.down({...f.ev(0,100,80,2),isPrimary:false},point),false);f.g.up(f.ev(0,100,80,2),point);f.clock.advance(600);assert.deepEqual(f.calls,[['open']]);});
 test('drag cancels hold and releases without activating',()=>{const f=fixture();f.g.down(f.ev(),point);f.g.move(f.ev(0,113),point);f.clock.advance(800);f.g.up(f.ev(0,113),point);assert.deepEqual(f.calls,[['down'],['move'],['up']]);});
 test('12px tolerance uses CSS rather than canvas pixels',()=>{const f=fixture();f.g.down(f.ev(),point);f.g.move(f.ev(0,111),{x:900,y:80});f.clock.advance(600);assert.deepEqual(f.calls,[['open']]);});
 test('cancelled hold and synthetic click cannot select',()=>{const f=fixture();f.g.down(f.ev(),point);f.g.cancel();f.clock.advance(600);f.g.up(f.ev(),point);f.g.fallbackClick(f.ev(),point);assert.equal(f.calls.length,0);});
 test('cancelled drag still releases',()=>{const f=fixture();f.g.down(f.ev(),point);f.g.move(f.ev(0,130),point);f.g.cancel();assert.deepEqual(f.calls,[['down'],['move'],['up']]);});
 test('right hold opens remote; short right click remains native',()=>{const f=fixture('right_long');f.g.down(f.ev(2),point);f.clock.advance(600);f.g.up(f.ev(2),point);f.g.down(f.ev(2),point);f.g.up(f.ev(2),point);assert.deepEqual(f.calls,[['open'],['click',3]]);});
 test('left double-click opens without prior activation',()=>{const f=fixture('left_double');for(let i=0;i<2;i++){f.g.down(f.ev(),point);f.g.up(f.ev(),point);f.clock.advance(100);}f.clock.advance(800);assert.deepEqual(f.calls,[['open']]);});
 test('single click with double-click trigger delivered after window',()=>{const f=fixture('left_double');f.g.down(f.ev(),point);f.g.up(f.ev(),point);f.clock.advance(419);assert.equal(f.calls.length,0);f.clock.advance(1);assert.deepEqual(f.calls,[['click',1]]);});
 test('right double-click opens and middle-click trigger works',()=>{const f=fixture('right_double');for(let i=0;i<2;i++){f.g.down(f.ev(2),point);f.g.up(f.ev(2),point);f.clock.advance(100);}assert.deepEqual(f.calls,[['open']]);const m=fixture('center_click');m.g.down(m.ev(1),point);m.g.up(m.ev(1),point);assert.deepEqual(m.calls,[['open']]);});
 test('mouse X-button back is a command, not a click',()=>{const f=fixture();f.g.down(f.ev(3),point);f.g.up(f.ev(3),point);assert.deepEqual(f.calls,[['back']]);});
 test('Guide hint parser avoids unrelated menu names',()=>{assert(C.menuIsGuide('menuName:Guide, popupName:'));assert(C.menuIsGuide('other:x; menuName: Guide'));assert(!C.menuIsGuide('menuName:GuideOptions'));});
 test('ENDLIST does not recover before normal buffered end',()=>{assert(!C.incompleteEnd({running:false,endList:true,hlsSeconds:300},2,300000,0));assert(!C.incompleteEnd({running:false,endList:true,hlsSeconds:300},300,300000,0));assert(C.incompleteEnd({running:false,endList:true,hlsSeconds:5},5,300000,0));});
 test('bounded recovery budget does not silently reset',()=>{const b=new C.RecoveryBudget(6);for(let i=0;i<6;i++)assert(b.take());assert(!b.take());b.reset();assert(b.take());});
 test('video-mask assist accepts color key and clips trusted preview',()=>{assert.deepEqual(C.videoMask(0xff080010,[800,400,480,320],[0,0,1280,720],null,null,false,true,false),[800,400,480,320]);});
 test('dark clear can reveal fullscreen video, not tiny black UI buttons',()=>{assert(C.videoMask(0xff000000,[0,0,1280,720],[0,0,1280,720],null,null,true,true,false));assert.equal(C.videoMask(0xff000000,[0,0,20,20],[0,0,1280,720],null,null,true,true,false),null);});
 test('menu navigation suppresses stale black-fill video hole',()=>{assert.equal(C.videoMask(0xff000000,[0,0,1280,720],[0,0,1280,720],[0,0,1280,720],null,false,true,true),null);});
 console.log('MiniClient core: '+count+' tests PASS');
}
