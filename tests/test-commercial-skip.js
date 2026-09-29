const fs=require('fs'),vm=require('vm');
const code=fs.readFileSync(__dirname+'/../src/main/webapp/js/commercial-skip.js','utf8');
const ctx={console,setInterval:()=>1,clearInterval:()=>{},Promise,Date};ctx.globalThis=ctx;ctx.window=ctx;vm.runInNewContext(code,ctx);
const C=ctx.SageCommercialSkip&&ctx.SageCommercialSkip.CommercialSkipController;if(!C)throw new Error('controller missing');
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};
(async()=>{
 let time=0,seeks=[],last=null;
 const c=new C({getTime:()=>time,seek:async s=>{seeks.push(s);time=s},onState:s=>{last=s}});
 c.setMarkers([{start:10,end:20,type:0},{start:40,end:50,type:0}],'x.edl');
 if(c.markers.length!==2||c.activeAt(11).end!==20||c.nextAfter(21).start!==40)throw new Error('marker parsing');
 c.setEnabled(true);time=12;await c.tick();
 if(seeks.length!==1||Math.abs(seeks[0]-20.15)>.01)throw new Error('auto skip');
 time=45;const ok=await c.skipCurrent();
 if(!ok||seeks.length!==2||Math.abs(seeks[1]-50.15)>.01)throw new Error('manual skip');
 if(!last||last.count!==2)throw new Error('state callback');
 if(c.snapshot().seekInFlight)throw new Error('seek ownership not released');
 // A 250 ms timer can fire repeatedly while an asynchronous server seek is in
 // flight. Exactly one seek owns that EDL boundary.
 const d=deferred();time=12;seeks=[];c.lastSkippedEnd=-1;c.lastSeekAt=0;
 c.seek=async s=>{seeks.push(s);await d.promise;time=s};
 const a=c.tick(),b=c.tick();await Promise.resolve();
 if(seeks.length!==1)throw new Error('duplicate Comskip seek while first seek in flight');
 d.resolve();await Promise.all([a,b]);
 if(c.snapshot().seekInFlight)throw new Error('in-flight skip did not clear');
 console.log('commercial skip PASS');
})().catch(e=>{console.error(e);process.exit(1)});
