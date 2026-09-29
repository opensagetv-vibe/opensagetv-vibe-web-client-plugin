'use strict';
const assert=require('node:assert/strict'),C=require('../src/main/webapp/js/miniclient-core');
let tests=0;function test(n,f){f();console.log('PASS '+n);tests++;}
const sw=1598,sh=1080,screen=[0,0,sw,sh];

test('subtitle/DVD content geometry shares Fit Fill and Cover transforms',()=>{const fit=C.mediaContentRect(1000,600,720,576,'source');assert(Math.abs(fit.width/fit.height-1.25)<1e-9);assert.equal(fit.height,600);const fill=C.mediaContentRect(1000,600,720,576,'fill');assert.deepEqual(fill,{left:0,top:0,width:1000,height:600});const cover=C.mediaContentRect(1000,600,720,576,'cover');assert(cover.width>=1000&&cover.height>=600);});
test('forced 4:3 and 16:9 overlays remain centered',()=>{for(const [mode,ratio] of [['4x3',4/3],['16x9',16/9]]){const r=C.mediaContentRect(1000,700,720,576,mode);assert(Math.abs(r.width/r.height-ratio)<1e-9);assert(Math.abs((r.left*2+r.width)-1000)<1e-7);assert(Math.abs((r.top*2+r.height)-700)<1e-7);}});
test('new default uses browser-owned cover viewport',()=>{const d=C.normalizeSettings({});assert.equal(d.videoFit,'cover');assert.equal(d.videoViewport,'browser');assert.equal(d.displayPolicyVersion,2);});
test('explicit fit/stretch/native remain selectable',()=>{for(const fit of ['source','fill','cover','zoom'])assert.equal(C.normalizeSettings({videoFit:fit}).videoFit,fit);assert.equal(C.normalizeSettings({videoViewport:'native'}).videoViewport,'native');});
test('window canvas fills all CSS pixels despite native size cap',()=>assert.deepEqual(C.stageRect(2141,1447,sw,sh,true),{left:0,top:0,width:2141,height:1447}));
test('fixed UI preserves complete logical picture',()=>{const r=C.stageRect(1600,1000,1280,720,false);assert.equal(r.width,1600);assert.equal(r.height,900);assert.equal(r.top,50);});
test('inset near-full native video is expanded once, not fitted twice',()=>{const p=C.videoPresentation([106,20,1385,1025],sw,sh,'browser');assert.equal(p.kind,'fullscreen');assert.deepEqual(p.rect,screen);});
test('explicit small native preview remains native',()=>{const p=C.videoPresentation([100,100,400,220],sw,sh,'browser');assert.equal(p.kind,'preview');assert.deepEqual(p.rect,[100,100,400,220]);});
test('preview positioning uses same axes as UI pointer mapping',()=>{const p=C.videoPresentation([100,100,400,220],sw,sh,'browser');const r=C.videoLayerRect(p,{left:0,top:0,width:sw*2,height:sh*2},sw*2,sh*2,sw,sh);assert.deepEqual(r,{left:200,top:200,width:800,height:440});});
test('fullscreen video ignores fixed-UI letterbox offsets',()=>{const p=C.videoPresentation(screen,sw,sh,'browser');assert.deepEqual(C.videoLayerRect(p,{left:0,top:50,width:1600,height:900},1600,1000,sw,sh),{left:0,top:0,width:1600,height:1000});});
test('native override preserves exact destination even near full',()=>{const r=[100,20,1400,1010];assert.deepEqual(C.videoPresentation(r,sw,sh,'native').rect,r);});
test('invalid geometry falls back to sane screen rectangle',()=>{for(const r of [null,[0,0,0,0],[0,0,NaN,720]])assert.deepEqual(C.videoPresentation(r,sw,sh,'browser').rect,screen);});
test('fullscreen border mattes recognized without geometry mutation',()=>{const p=C.videoPresentation(screen,sw,sh,'browser');for(const r of [[0,0,100,sh],[sw-100,0,100,sh],[0,0,sw,30],[0,sh-30,sw,30]])assert(C.fullscreenMatte(0xff000000,r,screen,p,false));assert.deepEqual(p.rect,screen);});
test('translucent native OSD and colored panels are retained',()=>{const p=C.videoPresentation(screen,sw,sh,'browser');for(const c of [0x88000000,0xc0000000,0xff183040])assert(!C.fullscreenMatte(c,[0,0,sw,100],screen,p,false));});
test('menu suspension and native preview do not erase mattes',()=>{const p=C.videoPresentation(screen,sw,sh,'browser');assert(!C.fullscreenMatte(0xff000000,[0,0,100,sh],screen,p,true));assert(!C.fullscreenMatte(0xff000000,[0,0,100,sh],screen,C.videoPresentation([10,10,300,200],sw,sh,'browser'),false));});
test('small black UI button is never a fullscreen border matte',()=>assert(!C.fullscreenMatte(0xff000000,[0,0,40,40],screen,C.videoPresentation(screen,sw,sh,'browser'),false)));
console.log(`MiniClient display policy: ${tests} tests PASS`);
