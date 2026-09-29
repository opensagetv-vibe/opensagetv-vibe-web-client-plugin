'use strict';
const assert=require('assert');
const Controller=require('../src/main/webapp/js/miniclient-captions.js');
class FakeDecoder{
  constructor(o){this.enabled=!!o.enabled;this.service=o.service;this.onCaption=o.onCaption;this.setEnabledCalls=[];this.setServiceCalls=[];}
  setEnabled(v){this.enabled=!!v;this.setEnabledCalls.push(this.enabled);}
  setService(v){this.service=v;this.setServiceCalls.push(v);}
  pushCcData(){}
  updateTime(){}
  getStats(){return{service:this.service,enabled:this.enabled};}
}
(async()=>{
  const rendered=[];
  const c=new Controller({
    Decoder:FakeDecoder,timeMs:()=>0,
    fetch:async()=>({cursor:0,packets:[]}),subtitleFetch:async()=>({cues:[]}),
    render:t=>rendered.push(String(t||'')),clearBitmap:()=>{}
  });
  c.start({hlsSession:'h1',captionAuthority:{mode:'stv',stvStateSeen:true,stvState:1},streamSettings:{requested:{captions:'608',captionService:1,captionOffsetMs:0}}});
  assert(c.decoder,'STV authority must start the browser CEA decoder');
  assert.equal(c.decoder.enabled,true);assert.equal(c.decoder.service,'CC1');
  let snap=c.snapshot();assert.equal(snap.mode,'stv-controlled-browser-local');assert.equal(snap.stvState,1);assert.equal(snap.stvService,'CC1');
  c.setSageTvState(0,true);assert.equal(c.decoder.enabled,false);assert.equal(rendered.at(-1),'');
  c.setSageTvState(2,true);assert.equal(c.decoder.enabled,true);assert.equal(c.decoder.service,'CC2');
  snap=c.snapshot();assert.equal(snap.stvState,2);assert.equal(snap.stvService,'CC2');
  c.stop();

  const fallback=new Controller({Decoder:FakeDecoder,timeMs:()=>0,fetch:async()=>({cursor:0,packets:[]}),subtitleFetch:async()=>({cues:[]}),render:()=>{},clearBitmap:()=>{}});
  fallback.start({hlsSession:'h2',captionAuthority:{mode:'stv',stvStateSeen:false,stvState:0},streamSettings:{requested:{captions:'608',captionService:1,captionOffsetMs:0}}});
  assert.equal(fallback.decoder.enabled,true,'property-absent peers retain CC1 fallback');
  assert.equal(fallback.decoder.service,'CC1');
  fallback.stop();
  console.log('STV-controlled browser captions: 10 PASS');
})().catch(e=>{console.error(e);process.exit(1);});
