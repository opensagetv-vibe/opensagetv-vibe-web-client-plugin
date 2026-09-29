const fs=require('fs'),vm=require('vm');
const code=fs.readFileSync(__dirname+'/../src/main/webapp/js/library-utils.js','utf8');
const ctx={console};ctx.globalThis=ctx;ctx.window=ctx;vm.runInNewContext(code,ctx);
const U=ctx.SageLibraryUtils;if(!U)throw new Error('missing utils');
const items=[
 {id:1,title:'Show',episode:'One',category:'Drama',start:100,recording:false,watched:false,duration:1000},
 {id:2,title:'Show',episode:'Two',category:'Drama',start:200,recording:false,watched:true,duration:1000},
 {id:3,title:'News',episode:'',category:'News',start:300,recording:true,watched:false,duration:1000},
 {id:4,title:'Documentary',episode:'',category:'Documentary',start:50,recording:false,watched:false,duration:100000,serverResumeSeconds:30}
];
const state={getResume:id=>id===1?{position:100,duration:1000,updatedAt:20}:null,isFavorite:id=>id===2,getRecent:()=>[{id:2}]};
if(U.filter(items,{view:'continue'},state).map(x=>x.id).sort().join(',')!=='1,4')throw new Error('continue filter');
if(U.filter(items,{view:'favorites'},state).map(x=>x.id).join(',')!=='2')throw new Error('favorites filter');
if(U.filter(items,{view:'unwatched'},state).some(x=>x.id===2))throw new Error('unwatched filter');
if(U.categories(items).join(',')!=='Documentary,Drama,News')throw new Error('categories');
const n=U.neighbors(items,1,'Show');if(!n.next||n.next.id!==2||n.previous)throw new Error('series neighbors');
if(Math.round(U.progress(items[0],state)*100)!==10)throw new Error('progress');
if(Math.round(U.progress(items[3],state)*100)!==30)throw new Error('server progress');
console.log('library utils PASS');
