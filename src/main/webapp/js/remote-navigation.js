(function (global) {
  'use strict';
  let enabled = false;
  let gamepadTimer = null;
  let previousButtons = [];

  function focusables() {
    return Array.from(document.querySelectorAll('button:not([disabled]),a[href],select:not([disabled]),input:not([disabled]):not([type="hidden"]),[tabindex]:not([tabindex="-1"])'))
      .filter(el => el.offsetParent !== null && !el.closest('[hidden]'));
  }
  function rectCenter(el) { const r=el.getBoundingClientRect(); return {x:r.left+r.width/2,y:r.top+r.height/2}; }
  function move(dir) {
    const items=focusables(); if (!items.length) return false;
    let cur=document.activeElement; if (!items.includes(cur)) { items[0].focus(); return true; }
    const c=rectCenter(cur); let best=null,bestScore=Infinity;
    for (const el of items) {
      if (el===cur) continue; const p=rectCenter(el); const dx=p.x-c.x,dy=p.y-c.y;
      if (dir==='left' && dx>=-2) continue; if (dir==='right'&&dx<=2)continue; if(dir==='up'&&dy>=-2)continue; if(dir==='down'&&dy<=2)continue;
      const primary=(dir==='left'||dir==='right')?Math.abs(dx):Math.abs(dy);
      const cross=(dir==='left'||dir==='right')?Math.abs(dy):Math.abs(dx);
      const score=primary + cross*2.2;
      if(score<bestScore){bestScore=score;best=el;}
    }
    if(best){best.focus({preventScroll:true});best.scrollIntoView({block:'nearest',inline:'nearest'});return true;} return false;
  }
  function closeTopLayer(){const d=document.querySelector('dialog[open]');if(d){d.close();return true;}if(document.fullscreenElement){document.exitFullscreen().catch(()=>{});return true;}return false;}
  function keydown(e){
    if(!enabled)return;
    const tag=document.activeElement&&document.activeElement.tagName;
    if(tag==='TEXTAREA'||(tag==='INPUT' && !['button','checkbox','range'].includes((document.activeElement.type||'').toLowerCase())))return;
    const map={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down'};
    if(map[e.key]){if(move(map[e.key])){e.preventDefault();e.stopImmediatePropagation();}}
    else if(e.key==='Escape'||e.key==='BrowserBack'||e.key==='Backspace'){if(closeTopLayer()){e.preventDefault();e.stopImmediatePropagation();}}
  }
  function dispatchKey(key){document.dispatchEvent(new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true}));}
  function pollGamepads(){
    if(!enabled||!navigator.getGamepads)return;
    const pads=Array.from(navigator.getGamepads()).filter(Boolean); const p=pads[0]; if(!p)return;
    const current=p.buttons.map(b=>b.pressed);
    const pressed=i=>current[i]&&!previousButtons[i];
    if(pressed(12))dispatchKey('ArrowUp'); if(pressed(13))dispatchKey('ArrowDown'); if(pressed(14))dispatchKey('ArrowLeft'); if(pressed(15))dispatchKey('ArrowRight');
    if(pressed(0)){const el=document.activeElement;if(el&&typeof el.click==='function')el.click();}
    if(pressed(1))dispatchKey('Escape');
    previousButtons=current;
  }
  function setEnabled(v){enabled=!!v;document.documentElement.classList.toggle('ten-foot',enabled);if(enabled&&!gamepadTimer)gamepadTimer=setInterval(pollGamepads,120);if(!enabled&&gamepadTimer){clearInterval(gamepadTimer);gamepadTimer=null;} }
  document.addEventListener('keydown',keydown,true);
  global.SageRemoteNavigation={setEnabled,isEnabled:()=>enabled,move};
})(typeof window !== 'undefined' ? window : globalThis);
