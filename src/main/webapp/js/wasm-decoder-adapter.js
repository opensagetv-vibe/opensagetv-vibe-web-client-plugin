(function (global) {
  'use strict';

  const LIBMEDIA_VERSION = '1.3.1';
  const LOCAL = {
    cheap: 'vendor/libmedia/cheap-polyfill.js',
    avplayer: 'vendor/libmedia/umd/avplayer.js',
    wasmBase: 'vendor/libmedia'
  };
  const CDN = {
    cheap: `https://cdn.jsdelivr.net/gh/zhaohappy/libmedia@v${LIBMEDIA_VERSION}/dist/cheap-polyfill.js`,
    avplayer: `https://cdn.jsdelivr.net/npm/@libmedia/avplayer@${LIBMEDIA_VERSION}/dist/umd/avplayer.js`,
    wasmBase: `https://cdn.jsdelivr.net/gh/zhaohappy/libmedia@v${LIBMEDIA_VERSION}/dist`
  };

  let runtimePromise = null;
  let runtimeOrigin = null;
  let active = null;
  let simdSupport = null;
  let atomicSupport = null;
  let assetMode = 'local';
  let selectedThreads = null;

  function absolute(url) { return new URL(url, location.href).href; }
  function isIOS() { return /iphone|ipad|ipod/i.test(navigator.userAgent); }

  function loadScript(url) {
    url = absolute(url);
    return new Promise((resolve, reject) => {
      const existing = Array.from(document.scripts).find(s => s.src === url);
      if (existing && existing.dataset.loaded === '1') return resolve();
      const s = existing || document.createElement('script');
      if (!existing) {
        s.src = url;
        s.async = true;
        if (!url.startsWith(location.origin)) s.crossOrigin = 'anonymous';
        document.head.appendChild(s);
      }
      s.onload = () => { s.dataset.loaded = '1'; resolve(); };
      s.onerror = () => reject(new Error(`Could not load ${url}`));
    });
  }

  async function supportsSimd() {
    if (simdSupport != null) return simdSupport;
    if (!global.WebAssembly || !WebAssembly.compile) return (simdSupport = false);
    const asm = 'AGFzbQEAAAABBQFgAAF7AhIBA2VudgZtZW1vcnkCAwGAgAIDAgEACgoBCABBAP0ABAAL';
    try {
      const bytes = Uint8Array.from(atob(asm), c => c.charCodeAt(0));
      await WebAssembly.compile(bytes);
      return (simdSupport = true);
    } catch (_) { return (simdSupport = false); }
  }

  async function supportsAtomic() {
    if (atomicSupport != null) return atomicSupport;
    if (!global.WebAssembly || !WebAssembly.compile) return (atomicSupport = false);
    const asm = 'AGFzbQEAAAABBgFgAX8BfwISAQNlbnYGbWVtb3J5AgMBgIACAwIBAAcJAQVsb2FkOAAACgoBCAAgAP4SAAAL';
    try {
      const bytes = Uint8Array.from(atob(asm), c => c.charCodeAt(0));
      await WebAssembly.compile(bytes);
      return (atomicSupport = true);
    } catch (_) { return (atomicSupport = false); }
  }

  function threadPreference() {
    const v = new URLSearchParams(location.search).get('threads');
    return v === 'off' || v === 'on' ? v : 'auto';
  }

  async function canUseWasmThreads() {
    const pref = threadPreference();
    if (pref === 'off') return false;
    const capable = !!(global.isSecureContext && global.crossOriginIsolated && global.SharedArrayBuffer && global.Atomics && await supportsAtomic());
    return pref === 'on' ? capable : capable;
  }

  async function ensureRuntime(onStatus, preferredMode, allowCdnFallback) {
    if (global.AVPlayer) return global.AVPlayer;
    if (runtimePromise) return runtimePromise;
    runtimePromise = (async () => {
      selectedThreads = await canUseWasmThreads();
      global.CHEAP_DISABLE_THREAD = !selectedThreads;
      if (/iphone|ipad|ipod|android/i.test(navigator.userAgent)) global.CHEAP_HEAP_MAXIMUM = 16384;
      if (preferredMode === 'cdn') {
        if (onStatus) onStatus(`Loading pinned CDN decoder runtime${selectedThreads ? ' with Wasm threads' : ''}…`);
        await loadScript(CDN.cheap);
        await loadScript(CDN.avplayer);
        runtimeOrigin = 'cdn';
        assetMode = 'cdn';
      } else {
        try {
          if (onStatus) onStatus(`Loading self-hosted decoder runtime from SageTV${selectedThreads ? ' with Wasm threads' : ''}…`);
          await loadScript(LOCAL.cheap);
          await loadScript(LOCAL.avplayer);
          runtimeOrigin = 'local-cache';
          assetMode = 'local';
        } catch (localError) {
          if (allowCdnFallback === false) throw localError;
          if (onStatus) onStatus(`Local decoder cache not ready (${localError.message}). Trying pinned CDN runtime…`);
          await loadScript(CDN.cheap);
          await loadScript(CDN.avplayer);
          runtimeOrigin = 'cdn';
          assetMode = 'cdn';
        }
      }
      if (!global.AVPlayer) throw new Error('libmedia loaded but window.AVPlayer was not created');
      return global.AVPlayer;
    })();
    try { return await runtimePromise; }
    catch (e) { runtimePromise = null; throw e; }
  }

  function codecName(codecId) {
    const map = {
      2: 'mpeg2video', 27: 'h264', 173: 'hevc', 196: 'vvc', 12: 'mpeg4', 225: 'av1',
      139: 'vp8', 167: 'vp9', 7: 'mjpeg', 86018: 'aac', 86019: 'ac3', 86056: 'eac3',
      86020: 'dca', 86017: 'mp3', 86076: 'opus', 86028: 'flac', 86021: 'vorbis'
    };
    return map[Number(codecId)] || null;
  }

  function base() { return assetMode === 'cdn' ? CDN.wasmBase : absolute(LOCAL.wasmBase); }

  async function wasmSuffix() {
    if (await supportsSimd()) return '-simd';
    if (selectedThreads && await supportsAtomic()) return '-atomic';
    return '';
  }

  async function getWasm(type, codecId) {
    const suffix = await wasmSuffix();
    const b = base();
    if (type === 'decoder') {
      const name = codecName(codecId);
      return name ? `${b}/decode/${name}${suffix}.wasm` : null;
    }
    if (type === 'resampler') return `${b}/resample/resample${suffix}.wasm`;
    if (type === 'stretchpitcher') return `${b}/stretchpitch/stretchpitch${suffix}.wasm`;
    return null;
  }

  function streamLabel(s, kind) {
    const m = s.metadata || {}, cp = s.codecpar || {};
    const lang = m.language ? ` ${m.language}` : '';
    const title = m.title ? ` — ${m.title}` : '';
    if (kind === 'audio') {
      const channels = cp.chLayout && cp.chLayout.nbChannels ? ` ${cp.chLayout.nbChannels}ch` : (cp.channels ? ` ${cp.channels}ch` : '');
      const rate = cp.sampleRate ? ` @${cp.sampleRate}Hz` : '';
      return `ID ${s.id}${lang} codec ${cp.codecId}${channels}${rate}${title}`;
    }
    return `ID ${s.id}${lang} codec ${cp.codecId}${title}`;
  }

  function ratioValue(v) {
    if (v == null) return 0;
    if (typeof v === 'number') return Number.isFinite(v) ? v : 0;
    if (typeof v === 'bigint') return Number(v);
    if (Array.isArray(v) && v.length >= 2) {
      const d = Number(v[1]); return d ? Number(v[0]) / d : 0;
    }
    if (typeof v === 'object') {
      const n = Number(v.num != null ? v.num : v.numerator);
      const d = Number(v.den != null ? v.den : v.denominator);
      return d ? n / d : 0;
    }
    return Number(v) || 0;
  }

  function inspectTracks(player) {
    const ctx = player.getFormatContext && player.getFormatContext();
    const streams = ctx && Array.isArray(ctx.streams) ? ctx.streams : [];
    const result = {
      audio: streams.filter(s => s.codecpar && Number(s.codecpar.codecType) === 1).map(s => ({id:Number(s.id),label:streamLabel(s,'audio'),metadata:s.metadata||{},codecpar:s.codecpar||{}})),
      subtitle: streams.filter(s => s.codecpar && Number(s.codecpar.codecType) === 3).map(s => ({id:Number(s.id),label:streamLabel(s,'subtitle'),metadata:s.metadata||{},codecpar:s.codecpar||{}})),
      video: streams.filter(s => s.codecpar && Number(s.codecpar.codecType) === 0).map(s => ({id:Number(s.id),label:streamLabel(s,'video'),metadata:s.metadata||{},codecpar:s.codecpar||{}}))
    };
    result.expectedFps = result.video.length ? (ratioValue(result.video[0].codecpar.framerate) || ratioValue(result.video[0].codecpar.frameRate) || 0) : 0;
    return result;
  }

  function makeCaptionedLoader(AVPlayer, url, captionParser, isLive) {
    return new (class extends AVPlayer.IOLoader.CustomIOLoader {
      constructor() {
        super();
        this.inner = new AVPlayer.IOLoader.FetchIOLoader({isLive: !!isLive, preload: 5 * 1024 * 1024, retryCount: 6, retryInterval: 0.5});
      }
      get ext() { return 'ts'; }
      get name() { return url; }
      get minBuffer() { return isLive ? 1 : 3; }
      async open() { return this.inner.open({url}, {from:0,to:-1}); }
      async read(buffer) {
        const n = await this.inner.read(buffer);
        if (n > 0 && captionParser) captionParser.push(buffer.subarray(0, n));
        return n;
      }
      async seek(pos) {
        if (captionParser) captionParser.resetForSeek();
        if (isLive) return -38;
        return this.inner.seek(pos);
      }
      async size() { return isLive ? 0n : this.inner.size(); }
      async stop() { return this.inner.stop(); }
    })();
  }

  async function stop() {
    if (!active) return;
    const old = active; active = null;
    try { if (old.player) await old.player.destroy(); } catch (_) {}
    try { if (old.container) old.container.innerHTML = ''; } catch (_) {}
  }

  async function createAndPlay(options, mode, workerEnabled) {
    assetMode = mode || assetMode;
    const onStatus = options.onStatus || function(){};
    const AVPlayer = await ensureRuntime(onStatus, mode, options.allowCdnFallback !== false);
    await stop();
    const isLive = !!options.isLive;
    const captionParser = options.captionParser || null;
    const loader = makeCaptionedLoader(AVPlayer, options.url, captionParser, isLive);

    if (!AVPlayer.audioContext) {
      const AC = global.AudioContext || global.webkitAudioContext;
      if (!AC) throw new Error('WebAudio AudioContext is not available in this browser');
      AVPlayer.audioContext = new AC();
      AVPlayer.audioContext.createBufferSource();
    }
    if (AVPlayer.audioContext.state !== 'running') { try { await AVPlayer.audioContext.resume(); } catch (_) {} }

    options.container.innerHTML = '';
    const player = new AVPlayer({
      container: options.container,
      getWasm,
      checkUseMSE: () => false,
      enableHardware: false,
      enableWebCodecs: false,
      enableWebGPU: false,
      enableWorker: !!workerEnabled,
      enableAudioWorklet: true,
      enableJitterBuffer: isLive,
      jitterBufferMax: isLive ? 4 : 0,
      jitterBufferMin: isLive ? 1 : 0,
      lowLatency: isLive,
      preLoadTime: isLive ? 1 : 3
    });
    active = {player,loader,captionParser,container:options.container,isLive,url:options.url,currentTimeMs:0,workerEnabled:!!workerEnabled};

    if (player.on && AVPlayer.Events) {
      player.on(AVPlayer.Events.ERROR, e => { if (options.onError) options.onError(e); onStatus(`Decoder error: ${e && e.message ? e.message : e}`); });
      player.on(AVPlayer.Events.FIRST_VIDEO_RENDERED, () => onStatus('First software-decoded video frame rendered.'));
      player.on(AVPlayer.Events.FIRST_AUDIO_RENDERED, () => onStatus('First software-decoded audio frame rendered.'));
      player.on(AVPlayer.Events.TIME, pts => {
        const ms = Number(pts || 0);
        if (active && active.player === player) active.currentTimeMs = ms;
        if (captionParser) captionParser.updateTime(ms);
        if (options.onTime) options.onTime(ms);
      });
      player.on(AVPlayer.Events.STREAM_UPDATE, () => { if (options.onTracks) options.onTracks(inspectTracks(player)); });
      player.on(AVPlayer.Events.ENDED, () => { if (options.onEnded) options.onEnded(); });
    }

    const execution = selectedThreads ? 'Wasm threads' : (workerEnabled ? 'Web Workers' : 'single-thread fallback');
    onStatus(`Opening ORIGINAL MPEG-TS (${assetMode}, ${await supportsSimd() ? 'Wasm SIMD' : 'Wasm baseline'}, ${execution})…`);
    await player.load(loader, {ext:'ts',isLive,maxProbeDuration:isLive ? 3 : 7});
    const tracks = inspectTracks(player);
    if (options.onTracks) options.onTracks(tracks);
    onStatus(`TS demux: ${tracks.video.length} video, ${tracks.audio.length} audio. Starting MPEG/AC-3 software decode…`);
    await player.play({audio:true,video:true,subtitle:false,audioMasterForce:true});
    try { await player.resume(); } catch (_) {}
    return {tracks,player,assetMode,runtimeOrigin,duration:getDuration(),workerEnabled:!!workerEnabled,threads:selectedThreads};
  }

  async function play(options) {
    options = options || {};
    const canWorker = typeof global.Worker === 'function' && options.enableWorker !== false;
    const attempts = [];
    attempts.push({mode: options.assetMode || 'local', worker: canWorker});
    if (canWorker) attempts.push({mode: options.assetMode || 'local', worker: false});
    if ((options.assetMode || 'local') === 'local' && options.allowCdnFallback !== false) {
      attempts.push({mode:'cdn', worker:false});
    }
    let lastError = null;
    for (let i = 0; i < attempts.length; i++) {
      const a = attempts[i];
      try {
        if (i > 0 && options.onStatus) options.onStatus(`Retrying decoder with ${a.mode} assets and ${a.worker ? 'workers' : 'single-thread worker fallback'}…`);
        return await createAndPlay(options, a.mode, a.worker);
      } catch (e) {
        lastError = e;
        try { await stop(); } catch (_) {}
      }
    }
    throw lastError || new Error('No Wasm playback attempt succeeded');
  }

  async function pause() { if (active && active.player) return active.player.pause(); }
  async function resume() { if (!active || !active.player) return; try { await active.player.resume(); } catch (_) {} return active.player.play(); }
  async function seek(ms) { if (active && active.player && !active.isLive) return active.player.seek(BigInt(Math.max(0, Math.round(ms)))); }
  async function selectAudio(id) { if (active && active.player) return active.player.selectAudio(Number(id)); }
  async function selectSubtitle(id) { if (active && active.player) return active.player.selectSubtitle(Number(id)); }
  function setVolume(v) { if (active && active.player) active.player.setVolume(Number(v)); }
  function setPlaybackRate(v) { if (active && active.player && !active.isLive) active.player.setPlaybackRate(Number(v)); }
  function getDuration() { if (!active || !active.player) return 0; try { return Number(active.player.getDuration() || 0); } catch (_) { return 0; } }
  function getCurrentTime() { return active ? Number(active.currentTimeMs || 0) : 0; }
  function stats() { if (!active || !active.player) return null; try { return active.player.getStats(); } catch (_) { return null; } }
  function session() {
    return active ? {
      isLive:active.isLive,
      url:active.url,
      assetMode,
      runtimeOrigin,
      duration:getDuration(),
      currentTimeMs:getCurrentTime(),
      workerEnabled:active.workerEnabled,
      wasmThreads:!!selectedThreads
    } : null;
  }

  global.SageWasmDecoder = {
    version:'1.3.0', libmediaVersion:LIBMEDIA_VERSION, available:!!global.WebAssembly,
    async probe() {
      const simd = await supportsSimd();
      const atomic = await supportsAtomic();
      const threads = await canUseWasmThreads();
      return {
        available:!!global.WebAssembly,
        wasm:!!global.WebAssembly,
        simd,
        atomic,
        audioContext:!!(global.AudioContext||global.webkitAudioContext),
        worker:typeof global.Worker === 'function',
        secureContext:!!global.isSecureContext,
        crossOriginIsolated:!!global.crossOriginIsolated,
        sharedArrayBuffer:!!global.SharedArrayBuffer,
        threadPreference:threadPreference(),
        wasmThreads:threads,
        runtimeLoaded:!!global.AVPlayer,
        runtimeOrigin,
        assetMode,
        ios:isIOS()
      };
    },
    play,pause,resume,stop,seek,selectAudio,selectSubtitle,setVolume,setPlaybackRate,getDuration,getCurrentTime,stats,session
  };
})(window);
