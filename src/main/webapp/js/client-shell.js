(() => {
  'use strict';
  const buttons=[...document.querySelectorAll('[data-page]')],pages=[...document.querySelectorAll('[data-client-page]')];
  function show(name){
    pages.forEach(p=>p.classList.toggle('active',p.dataset.clientPage===name));
    buttons.forEach(b=>b.classList.toggle('active',b.dataset.page===name));
    try{localStorage.setItem('sagetv.fullClientPage',name);}catch(_){}
    if(name==='guide'&&window.SageWebPlayerApp)SageWebPlayerApp.loadGuide();
    if(name==='dvr'&&window.SageWebPlayerApp)SageWebPlayerApp.loadDvr();
    if(name==='home'&&window.SageAdvancedUI)SageAdvancedUI.refreshHome();
    if(name==='favorites'&&window.SageAdvancedUI)SageAdvancedUI.loadFavorites();
  }
  buttons.forEach(b=>b.addEventListener('click',()=>show(b.dataset.page)));
  let initial='recordings';try{initial=localStorage.getItem('sagetv.fullClientPage')||initial;}catch(_){}
  if(!pages.some(p=>p.dataset.clientPage===initial))initial='recordings';show(initial);
  // Clicking Watch/Play automatically exposes the player page without making it
  // modal or losing the recording/guide state.
  document.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;const t=(b.textContent||'').trim().toLowerCase();if(t==='watch'||t==='play'||t==='watch live'||t==='go live'||t==='play recording'||t==='watch recording live')setTimeout(()=>show('player'),50);});
})();
