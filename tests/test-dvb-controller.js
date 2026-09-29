const assert=require('assert');const Controller=require('../src/main/webapp/js/miniclient-captions.js');
const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
  let now=0,calls=0,clears=0,draws=[];const rgba=Buffer.from([255,255,255,255,0,0,0,0]).toString('base64');
  const responses=[
    {cues:[{kind:'bitmap',owner:'local_dvb',ptsMs:0,durationMs:500,clear:true,canvas:{width:4,height:2},rect:[1,1,2,1],format:'rgba-straight',data:rgba}],dvbBitmap:{services:[{pid:336,compositionPageId:1,language:'eng'}]}},
    {cues:[]},{cues:[]}
  ];
  const c=new Controller({Decoder:function(){throw new Error('CEA decoder must not be constructed in DVB mode');},timeMs:()=>now,fetch:async()=>({packets:[],cursor:0}),subtitleFetch:async p=>{calls++;assert(Number.isFinite(p.timeMs));return responses.shift()||{cues:[]};},render:t=>{if(t)throw new Error('DVB must not use text renderer');},renderBitmap:(cue,pixels)=>draws.push({cue,pixels:Array.from(pixels)}),clearBitmap:()=>clears++,error:e=>{throw new Error(e);}});
  c.start({hlsSession:'dvb1',streamSettings:{requested:{captions:'dvb',captionOffsetMs:0}}});await wait(180);
  assert.strictEqual(c.snapshot().mode,'dvb-bitmap-local');assert.strictEqual(draws.length,1);assert.deepStrictEqual(draws[0].cue.rect,[1,1,2,1]);assert.deepStrictEqual(draws[0].pixels,[255,255,255,255,0,0,0,0]);assert.strictEqual(c.snapshot().services[0].pid,336);console.log('PASS DVB controller renders straight-alpha RGBA cue on the bitmap path');
  const initialClears=clears;now=600;await wait(320);assert(clears>initialClears);console.log('PASS DVB cue timeout clears stale bitmap without waiting for a new video segment');
  const before=calls;c.stop();await wait(320);assert.strictEqual(calls,before);console.log('PASS stopping playback cancels DVB bitmap poll lifecycle');
  console.log('P04 browser DVB controller: 3 PASS');
})().catch(e=>{console.error(e);process.exit(1)});
