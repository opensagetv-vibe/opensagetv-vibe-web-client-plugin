const CACHE='sagetv-webplayer-shell-v3.2.30';
const SHELL=['./','index.html','client.html','miniclient.html','css/app.css','css/miniclient.css','js/session-state.js','js/library-utils.js','js/atsc-captions.js','js/performance-watchdog.js','js/wasm-decoder-adapter.js','js/fmp4-fallback.js','js/commercial-skip.js','js/remote-navigation.js','js/app.js','js/advanced-ui.js','js/client-shell.js','js/miniclient-core.js','js/miniclient-streaming.js','js/miniclient-caption-authority.js','js/miniclient-captions.js','js/miniclient-subtitles.js','js/miniclient-dvd-spu.js','js/miniclient-playback.js','js/miniclient-support.js','js/miniclient.js','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','icons/vibe/help_outline.svg','icons/vibe/menu.svg','icons/vibe/info_outline.svg','icons/vibe/undo.svg','icons/vibe/arrow_upward.svg','icons/vibe/arrow_back.svg','icons/vibe/arrow_forward.svg','icons/vibe/arrow_downward.svg','icons/vibe/fiber_manual_record.svg','icons/vibe/exit_to_app.svg','icons/vibe/vertical_align_top.svg','icons/vibe/vertical_align_bottom.svg','icons/vibe/keyboard.svg','icons/vibe/skip_previous.svg','icons/vibe/fast_rewind.svg','icons/vibe/stop.svg','icons/vibe/pause.svg','icons/vibe/play_arrow.svg','icons/vibe/fast_forward.svg','icons/vibe/skip_next.svg','icons/vibe/aspect_ratio.svg','icons/vibe/vid_info.svg','icons/vibe/bug_report.svg','icons/vibe/video_test.svg','icons/vibe/equalizer.svg','icons/vibe/closed_caption.svg','icons/vibe/video_settings.svg','icons/vibe/audio_tune.svg','icons/vibe/home.png','icons/vibe/close.png'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('sagetv-webplayer-shell-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
// Network-first shell updates prevent an old cached script hiding a plugin upgrade.
self.addEventListener('fetch',e=>{
 const u=new URL(e.request.url);
 if(e.request.method!=='GET'||u.origin!==location.origin||/\/api\/|\/stream|\/live\.ts|\/transcode\.mp4|\/vendor\/|\/hls\/|\/continuous\//.test(u.pathname))return;
 const filename=u.pathname.slice(new URL(self.registration.scope).pathname.length);
 if(filename&&!SHELL.includes(filename))return;
 e.respondWith(fetch(e.request).then(resp=>{
   if(resp&&resp.ok){const copy=resp.clone();e.waitUntil(caches.open(CACHE).then(c=>c.put(e.request,copy)));}
   return resp;
 }).catch(()=>caches.match(e.request).then(cached=>cached||Response.error())));
});
