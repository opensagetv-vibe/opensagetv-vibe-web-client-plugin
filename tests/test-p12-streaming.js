'use strict';
const S=require('../src/main/webapp/js/miniclient-streaming.js');
let p=0;
function ok(v,n){if(!v)throw new Error(n);p++;}
const d=S.normalize({});
ok(d.profileSchemaVersion===8,'schema 8');
ok(d.audioOffsetMs===0 && d.audioFallback==='default','audio defaults');
let x=S.normalize({audioOffsetMs:4000,keyFrameSeconds:10,bFrames:3,audioFallback:'strict'});
ok(x.audioOffsetMs===4000&&x.keyFrameSeconds===10&&x.bFrames===3,'upper bounds');
x=S.normalize({audioOffsetMs:-4000});ok(x.audioOffsetMs===-4000,'negative delay');
x=S.normalize({videoMode:'mpeg2copy'});ok(x.videoMode==='copy','legacy MPEG-2 copy profile migrates to Copy');
for(const bad of [{audioOffsetMs:4001},{keyFrameSeconds:0},{bFrames:4},{audioFallback:'bogus'}]){let threw=false;try{S.normalize(bad);}catch(_){threw=true;}ok(threw,'reject '+JSON.stringify(bad));}
console.log('P12 streaming profile: '+p+' PASS');
