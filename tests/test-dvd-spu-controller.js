'use strict';
const assert=require('assert');const Controller=require('../src/main/webapp/js/miniclient-dvd-spu.js');
let rendered=0,cleared=0,calls=0;global.setTimeout=(fn)=>0;global.clearTimeout=()=>{};
const c=new Controller({timeMs:()=>1000,fetch:async()=>{calls++;return{cues:[{owner:'dvd_spu',kind:'bitmap',clear:true,canvas:{width:720,height:480},rect:[10,10,1,1],format:'rgba-straight',data:Buffer.from([1,2,3,255]).toString('base64')}]};},clear:()=>cleared++,render:(cue,rgba)=>{assert.equal(rgba.length,4);rendered++;}});
c.start({dvd:true,hlsSession:'abc'});setImmediate(()=>{assert.equal(calls,1);assert.equal(rendered,1);assert(cleared>=2);c.stop();console.log('dvd-spu-controller PASS');});
