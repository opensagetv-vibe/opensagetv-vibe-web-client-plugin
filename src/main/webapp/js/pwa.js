(function(){
  'use strict';
  let deferred=null;
  const button=document.getElementById('installApp');
  if('serviceWorker' in navigator){window.addEventListener('load',()=>navigator.serviceWorker.register('sw.js',{scope:'./'}).catch(()=>{}));}
  window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();deferred=e;if(button)button.hidden=false;});
  if(button)button.addEventListener('click',async()=>{if(!deferred)return;deferred.prompt();try{await deferred.userChoice;}catch(_){}deferred=null;button.hidden=true;});
  window.addEventListener('appinstalled',()=>{if(button)button.hidden=true;});
})();
