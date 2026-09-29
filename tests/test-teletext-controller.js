const assert=require('assert');const Controller=require('../src/main/webapp/js/miniclient-captions.js');
const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
  let now=0,renders=[],calls=0;const responses=[
    {cues:[{kind:'text',owner:'local_broadcast',ptsMs:0,text:'PAGE 888',clear:false}],teletext:{services:[{page:888,pid:300,language:'eng'}]}},
    {cues:[]},
    {cues:[{kind:'text',owner:'local_broadcast',ptsMs:1000,text:'SECOND',clear:false}]},
    {cues:[{kind:'text',owner:'local_broadcast',ptsMs:2000,text:'',clear:true}]}
  ];
  const c=new Controller({Decoder:function(){throw new Error('CEA decoder must not be constructed in Teletext mode');},timeMs:()=>now,fetch:async()=>({packets:[],cursor:0}),subtitleFetch:async p=>{calls++;assert(Number.isFinite(p.timeMs));return responses.shift()||{cues:[]};},render:t=>renders.push(t),error:e=>{throw new Error(e);}});
  c.start({hlsSession:'abc',streamSettings:{requested:{captions:'teletext',captionOffsetMs:0}}});await wait(420);assert(renders.includes('PAGE 888'));assert(c.snapshot().mode==='teletext-local');assert(c.snapshot().services[0].page===888);console.log('PASS Teletext controller renders service while no SageTV OSD event is present');
  now=1000;await wait(420);assert(renders.includes('SECOND'));console.log('PASS Teletext controller advances from independent browser video clock');
  const before=calls;await wait(420);assert(calls>before);console.log('PASS Teletext polling continues while UI/OSD is idle');
  now=2000;await wait(420);assert(renders[renders.length-1]==='');console.log('PASS Teletext clear cue removes local text');
  c.stop();const stopped=calls;await wait(450);assert.strictEqual(calls,stopped);console.log('PASS stopping playback cancels Teletext poll lifecycle');
  console.log('P03 browser Teletext controller: 5 PASS');
})().catch(e=>{console.error(e);process.exit(1)});
