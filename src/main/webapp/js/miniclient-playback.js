/* Browser TS/HLS lifecycle for the real SageTV MiniClient. No DOM globals are
 * required: production dependencies are injected and used by the regression tests.
 */
(function(root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./miniclient-core.js'),require('./miniclient-streaming.js'));
  else root.SageMiniPlayback = factory(root.SageMiniCore,root.SageMiniStreaming);
})(typeof globalThis !== 'undefined' ? globalThis : this, function(Core, Streaming) {
  'use strict';
  class Playback {
    constructor(options) {
      this.o = options; this.video = options.video;
      this.clock = options.clock || {now: () => Date.now(), setTimeout: (fn, ms) => setTimeout(fn, ms), clearTimeout: id => clearTimeout(id)};
      this.generation = 0; this.hls = null; this.tsPlayer=null;this.transport='hls';this.streamSettings=null;this.tsStats=null;this.tsVersion='';this.tsFallbackPending=false;this.mpeg2FallbackPending=false; this.id = ''; this.status = null;
      this.startSeconds = 0; this.durationMs = 0; this.intentPaused = false;
      this.rebuffering = false; this.blocked = false; this.recovering = false;
      this.attached = false; this.budget = new Core.RecoveryBudget(6);
      this.timer = null; this.inFlightStatus = null; this.lastError = '';
      this.startedAt = 0; this.firstFrameMs = null; this.lastTime = 0;
      this.lastPlayProgress = 0; this.lastProduced = 0; this.lastProduceProgress = 0;
      this.starvedAt = 0; this.localNetworkRetries = 0; this.localMediaRetries = 0;
      this.playPending = false; this.stopped = true; this.recoveryWaitUntil = 0;
      this.bufferPhase = 'startup'; this.bufferTargetSeconds = 6; this.hlsVersion = '';this.appliedBufferKey='';this.dvd=false;this.dvdGeneration=0;
      this.lastAppendAt = 0; this.lastLoadAt = 0; this.lastLoaderWake = 0;
      this.loaderWakeups = 0; this.fragmentLoading = false; this.quotaLimited = false;
      this.lastStableAbsoluteMs = 0; this.clockRegressions = 0; this.clockRegressionActive=false; this.lastFailureClass = '';
      this.lastInputBytes = 0; this.lastInputProgress = 0; this.lastDecodeProgress = 0;
    }
    settings() { const s=Core.normalizeSettings(this.o.settings());if(this.dvd){s.startSegments=1;if(s.bufferPreset==='stable')s.bufferPreset='auto';}return s; }
    hlsConfig(steady) { const cfg=Core.hlsConfig(this.settings(),steady);if(this.dvd){const cap=steady?12:6;cfg.maxBufferLength=Math.min(cap,cfg.maxBufferLength);cfg.maxMaxBufferLength=Math.min(cap,cfg.maxMaxBufferLength);cfg.backBufferLength=Math.min(6,cfg.backBufferLength);cfg.liveBackBufferLength=Math.min(6,cfg.liveBackBufferLength);}return cfg; }
    valid(serial) { return serial === this.generation && !!this.id && !this.stopped; }
    notify(message) { if (this.o.message) this.o.message(message || ''); }
    error(error) { this.lastError = error && error.message ? error.message : String(error); if (this.o.error) this.o.error(this.lastError); }
    snapshot() {
      return {id:this.id, status:this.status,transport:this.transport,streamSettings:this.streamSettings,tsStats:this.tsStats,tsVersion:this.tsVersion, startSeconds:this.startSeconds, durationMs:this.durationMs,
        timeMs:this.absoluteTime(), ahead:Core.bufferedAhead(this.video), paused:this.intentPaused,
        rebuffering:this.rebuffering, blocked:this.blocked, recovering:this.recovering,
        recoveries:this.budget.count, firstFrameMs:this.firstFrameMs, error:this.lastError,
        attached:this.attached, starting:!!this.id && !this.attached,
        dvd:this.dvd,dvdGeneration:this.dvdGeneration,
        bufferPhase:this.tsPlayer ? 'continuous reserve' : this.hls ? this.bufferPhase : 'browser-managed',
        bufferTargetSeconds:this.tsPlayer ? this.bufferTargetSeconds : this.hls ? Math.min(this.bufferTargetSeconds, Number(this.hls.config.maxMaxBufferLength)||this.bufferTargetSeconds) : 0,
        configuredBufferSeconds:this.settings().maxBufferSeconds, loaderWakeups:this.loaderWakeups,
        clockRegressions:this.clockRegressions,lastFailureClass:this.lastFailureClass,
        lastAppendAgeMs:this.lastAppendAt ? Math.max(0,this.clock.now()-this.lastAppendAt) : null,
        fragmentLoading:this.fragmentLoading, quotaLimited:this.quotaLimited,
        hlsVersion:this.hlsVersion, workerEnabled:!!this.tsPlayer||!!(this.hls && this.hls.config.enableWorker),
        workerRequested:!!this.tsPlayer||!!(this.hls && this.hls.config.enableWorker),
        workerObserved:this.tsPlayer?'not observable by mpegts.js API':this.hls?'not observable by hls.js API':'none'};
    }
    absoluteTime() {
      // A video element retains its previous currentTime until load() resets it.
      // During the new startup, report the new seek offset, not the old position.
      const computed=Math.max(0,Math.round((this.startSeconds+(this.attached?Number(this.video.currentTime)||0:0))*1000));
      // Browser/MSE implementations can transiently report a stale pre-FLUSH or
      // pre-append timestamp. Reverse playback is not supported by this client,
      // so an unexplained >1.5 s regression inside one media generation is not
      // a real user seek. Keep the last proven clock until progress catches up.
      if(this.attached && computed+1500<this.lastStableAbsoluteMs && !this.video.seeking){
        if(!this.clockRegressionActive){this.clockRegressions++;this.clockRegressionActive=true;}return this.lastStableAbsoluteMs;
      }
      if(computed>this.lastStableAbsoluteMs||!this.attached)this.lastStableAbsoluteMs=computed;
      if(computed+1500>=this.lastStableAbsoluteMs)this.clockRegressionActive=false;
      return computed;
    }
    classifyFailure(reason,status){
      const text=String(reason||'').toLowerCase();
      if(/quota|buffer full|append/.test(text))return 'browser-quota';
      if(/network|loading|http|segment/.test(text))return 'transport';
      if(/decode|media error/.test(text))return 'browser-decode';
      if(/ffmpeg|encoder|transcod/.test(text))return 'encoder';
      if(/probe|metadata/.test(text))return 'probe';
      if(/source|input|read|producer/.test(text))return 'source-read';
      if(status&&status.running)return 'producer-stall';
      return 'unknown';
    }
    state() {
      return {expectedHlsSession:this.id, timeMs:this.absoluteTime(),
        videoWidth:this.video.videoWidth || 0, videoHeight:this.video.videoHeight || 0,
        volume:this.video.volume, paused:this.intentPaused, ended:!!this.video.ended};
    }
    stop() {
      ++this.generation; this.stopped = true; this.attached = false;
      if (this.timer !== null) this.clock.clearTimeout(this.timer); this.timer = null;
      if (this.hls) { try { this.hls.destroy(); } catch (_) {} this.hls = null; }
      if(this.tsPlayer){try{this.tsPlayer.destroy();}catch(_){}this.tsPlayer=null;}
      this.tsStats=null;this.tsFallbackPending=false;this.mpeg2FallbackPending=false;
      this.id = ''; this.status = null; this.inFlightStatus = null;this.dvd=false;this.dvdGeneration=0;
      this.rebuffering = false; this.recovering = false; this.playPending = false; this.recoveryWaitUntil = 0;
      try { this.video.pause(); this.video.removeAttribute('src'); this.video.load(); } catch (_) {}
    }
    async pollStatus(serial) {
      serial = serial == null ? this.generation : serial;
      if (!this.valid(serial)) return null;
      const id = this.id;
      if (this.inFlightStatus && this.inFlightStatus.serial === serial) return this.inFlightStatus.promise;
      const entry = {serial};
      entry.promise = (async () => {
        try {
          const st = await this.o.status(id);
          if (!this.valid(serial) || id !== this.id || (st.session && st.session !== id)) return null;
          this.status = st;
          if (Number(st.durationMs) > 0) this.durationMs = Number(st.durationMs);
          const produced = Number(st.hlsSeconds) || 0;
          if (produced > this.lastProduced + 0.1) {
            this.lastProduced = produced; this.lastProduceProgress = this.lastDecodeProgress = this.clock.now();
          }
          const inputBytes=Number(st.bytesInput)||0;
          if(inputBytes>this.lastInputBytes){this.lastInputBytes=inputBytes;this.lastInputProgress=this.clock.now();}
          if (st.error) this.error(st.error);
          return st;
        } catch (e) { if (this.valid(serial)) this.error(e); return null; }
        finally { if (this.inFlightStatus === entry) this.inFlightStatus = null; }
      })();
      this.inFlightStatus = entry;
      return entry.promise;
    }
    async waitServerBuffer(serial) {
      const deadline = this.clock.now() + 45000;
      while (this.valid(serial) && this.clock.now() < deadline) {
        const st = await this.pollStatus(serial);
        if (!this.valid(serial)) return false;
        if (st) {
          this.notify('Starting video… ' + (Number(st.hlsSeconds) || 0).toFixed(1) + 's produced · ' +
            (st.segments || 0) + '/' + Core.requiredStartSegments(st, this.settings()) + ' segments');
          if (Core.startupReady(st, this.settings())) return true;
          if (!st.running && st.error) throw new Error(st.error);
        }
        await new Promise(resolve => this.clock.setTimeout(resolve, 250));
      }
      if (!this.valid(serial)) return false;
      throw new Error('No playable HLS startup buffer after 45 seconds. Check FFmpeg in Diagnostics.');
    }
    async start(event) {
      const keepPaused = (event.seek || event.recovery) ? this.intentPaused : !!event.paused;
      this.stop(); this.stopped = false;
      const serial = this.generation;
      this.id = String(event.hlsSession || '');this.dvd=!!event.dvd;this.dvdGeneration=Number(event.dvdGeneration)||0;
      this.startSeconds = Math.max(0, Number(event.startSeconds) || 0);
      this.durationMs = Math.max(0, Number(event.durationMs) || 0);
      this.intentPaused = keepPaused; this.blocked = false; this.lastError = '';
      if (!event.recovery) { this.budget.reset(); this.localNetworkRetries = 0; this.localMediaRetries = 0; }
      this.lastTime = 0; this.lastProduced = 0; this.starvedAt = 0;
      this.lastStableAbsoluteMs=Math.round(this.startSeconds*1000);this.clockRegressions=0;this.clockRegressionActive=false;this.lastFailureClass='';
      this.lastInputBytes=0;this.lastInputProgress=this.lastDecodeProgress=this.clock.now();
      this.startedAt = this.lastPlayProgress = this.lastProduceProgress = this.clock.now();
      this.firstFrameMs = null;this.bufferPhase='startup';this.bufferTargetSeconds=6;
      this.lastAppendAt=this.lastLoadAt=this.lastLoaderWake=this.clock.now();
      this.loaderWakeups=0;this.fragmentLoading=false;this.quotaLimited=false;this.hlsVersion='';this.appliedBufferKey='';
      this.transport=event.transport||'hls';this.streamSettings=event.streamSettings||null;this.tsVersion='';
      if(this.transport==='mpegts'){await this.startTs(event,serial);return;}
      if (!this.id || !event.playlistUrl) { this.error('Missing HLS session or playlist URL'); return; }
      this.notify('Starting video…');
      // Fetch the local vendor asset concurrently with FFmpeg's first segment,
      // not after waiting for a fixed ten seconds of produced video.
      const nativePreferred = !!this.o.nativePreferred;
      const library = nativePreferred ? Promise.resolve(null) : Promise.resolve().then(() => this.o.loadHls()).catch(e => { return {loadError:e}; });
      try {
        if (!await this.waitServerBuffer(serial)) return;
        const Hls = await library;
        if (!this.valid(serial)) return; // also guard the asynchronous script load
        const url = this.o.resolveUrl(event.playlistUrl);
        if (Hls && !Hls.loadError && Hls.isSupported()) {
          const instance = new Hls(this.hlsConfig(false)); this.hls = instance;this.hlsVersion=Hls.version||'';
          const active = () => this.valid(serial) && this.hls === instance;
          instance.on(Hls.Events.ERROR, (name, data) => {
            if (!active() || !data) return;
            this.error('HLS ' + (data.type || '') + ' ' + (data.details || ''));
            if (/frag.*(?:error|timeout)/i.test(data.details||'')) this.fragmentLoading=false;
            if (data.details==='bufferFullError') {
              // Do not re-expand a buffer that the browser just rejected. The
              // time target is a reserve goal, not permission to exhaust MSE RAM.
              this.quotaLimited=true;
              const cap=Math.max(6,Math.min(this.bufferTargetSeconds,instance.config.maxMaxBufferLength||this.bufferTargetSeconds)/2);
              instance.config.maxBufferLength=instance.config.maxMaxBufferLength=cap;
              this.bufferTargetSeconds=cap;
            }
            if (!data.fatal) return;
            if (data.type === Hls.ErrorTypes.NETWORK_ERROR && this.localNetworkRetries++ < 2) {
              try { instance.startLoad(Number(this.video.currentTime) || 0); } catch (e) { this.error(e); }
            } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR && this.localMediaRetries++ < 2) {
              try { instance.recoverMediaError(); } catch (e) { this.error(e); }
            } else { this.recover('Fatal HLS error'); }
          });
          const listen=(event,fn)=>{if(event)instance.on(event,(name,data)=>{if(active())fn(data||{});});};
          listen(Hls.Events.FRAG_LOADING,()=>{this.fragmentLoading=true;this.lastLoadAt=this.clock.now();});
          listen(Hls.Events.FRAG_LOADED,()=>{this.fragmentLoading=false;this.lastLoadAt=this.clock.now();});
          listen(Hls.Events.FRAG_BUFFERED,()=>{
            this.fragmentLoading=false;this.lastAppendAt=this.clock.now();
            // One playable fragment is enough to start. From here the loader
            // keeps running toward the much larger independent reserve target.
            this.promoteBuffer();this.maybePlay();
          });
          instance.on(Hls.Events.MANIFEST_PARSED, () => { if (active()) this.maybePlay(); });
          instance.loadSource(url); this.attached = true; instance.attachMedia(this.video);
          // Explicit zero also applies to the loader, not only liveSync config.
          instance.startLoad(0);
        } else if (this.video.canPlayType('application/vnd.apple.mpegurl')) {
          this.video.src = url; this.attached = true; this.video.load();
          this.maybePlay();
        } else {
          throw (Hls && Hls.loadError) || new Error('This browser has no supported HLS player');
        }
        if (this.valid(serial)) this.scheduleHealth(serial);
      } catch (e) {
        if (this.valid(serial)) { this.error(e); this.notify('Playback failed: ' + this.lastError); }
      }
    }
    async startTs(event,serial) {
      try {
        if(!this.id||!event.streamUrl)throw new Error('Missing continuous-stream session URL');
        this.notify('Starting continuous MPEG-TS…');
        const library=await this.o.loadMpegts();
        if(!this.valid(serial))return;
        if(!library||!library.getFeatureList().mseLivePlayback)throw new Error('This browser has no compatible MPEG-TS/MSE player');
        const cfg=Streaming.mpegtsConfig(this.settings().maxBufferSeconds);
        const instance=library.createPlayer({type:'mpegts',isLive:false,url:this.o.resolveUrl(event.streamUrl)},cfg);
        this.tsPlayer=instance;this.tsVersion=library.version||'1.8.0';this.bufferTargetSeconds=cfg.lazyLoadMaxDuration;
        const active=()=>this.valid(serial)&&this.tsPlayer===instance;
        instance.on(library.Events.ERROR,(type,detail,info)=>{
          if(!active())return;
          this.error('MPEG-TS '+type+' '+detail+(info&&info.msg?' '+info.msg:''));
          this.fallbackTs(this.lastError);
        });
        instance.on(library.Events.STATISTICS_INFO,data=>{if(active()){this.tsStats=data;this.lastLoadAt=this.clock.now();}});
        instance.on(library.Events.MEDIA_INFO,data=>{if(active()){this.tsMediaInfo=data;this.maybePlay();}});
        instance.attachMediaElement(this.video);this.attached=true;instance.load();this.maybePlay();
        this.scheduleHealth(serial);
      }catch(e){if(this.valid(serial)){this.error(e);await this.fallbackTs(this.lastError);}}
    }
    async fallbackTs(reason) {
      if(!this.id||this.tsFallbackPending||this.transport!=='mpegts'||this.stopped)return false;
      const requested=this.streamSettings&&this.streamSettings.requested||{};
      if(requested.allowFallback===false||requested.allowFallback==='false'||!this.o.fallback){
        this.intentPaused=true;this.video.pause();this.notify('MPEG-TS failed: '+reason+'. Automatic fallback is disabled.');return false;
      }
      const serial=this.generation,id=this.id;
      this.tsFallbackPending=true;this.recovering=true;
      this.notify('MPEG-TS fallback → HLS at '+Core.timeText(this.absoluteTime())+'…');
      try{
        const result=await this.o.fallback({timeMs:this.absoluteTime(),expectedHlsSession:id});
        if(this.valid(serial)) {
          if(result&&result.stale){this.error('Ignored stale MPEG-TS fallback');this.tsFallbackPending=false;}
          this.recovering=false;this.recoveryWaitUntil=this.clock.now()+10000;
        }
        return true;
      }catch(e){
        if(this.valid(serial)){this.recovering=false;this.error(e);this.intentPaused=true;this.video.pause();this.notify('Fallback failed: '+this.lastError);}
        return false;
      }
    }
    explicitVideoCopyActive() {
      const ss=this.streamSettings||{},requested=ss.requested||{},effective=ss.effective||{};
      return this.transport==='hls'&&requested.videoMode==='copy'&&effective.video==='copy';
    }
    async fallbackVideoCopy(reason) {
      if(!this.id||this.mpeg2FallbackPending||!this.explicitVideoCopyActive()||this.stopped)return false;
      const requested=this.streamSettings&&this.streamSettings.requested||{};
      if(requested.allowFallback===false||requested.allowFallback==='false'||!this.o.fallback){
        this.intentPaused=true;this.video.pause();this.notify('Copied video was not decoded: '+reason+'. Automatic fallback is disabled.');return false;
      }
      const serial=this.generation,id=this.id;this.mpeg2FallbackPending=true;this.recovering=true;
      this.notify('Copied video is not decodable by this browser; restarting as H.264 at '+Core.timeText(this.absoluteTime())+'…');
      try{
        const result=await this.o.fallback({timeMs:this.absoluteTime(),expectedHlsSession:id});
        if(this.valid(serial)){
          if(result&&result.stale){this.error('Ignored stale Copy compatibility fallback');this.mpeg2FallbackPending=false;}
          this.recovering=false;this.recoveryWaitUntil=this.clock.now()+10000;
        }
        return true;
      }catch(e){
        if(this.valid(serial)){this.recovering=false;this.error(e);this.intentPaused=true;this.video.pause();this.notify('Copy compatibility fallback failed: '+this.lastError);}
        return false;
      }
    }
    promoteBuffer() {
      if (!this.hls || this.bufferPhase==='reserve') return;
      this.bufferPhase='reserve';this.updateBufferSettings();
    }
    updateBufferSettings() {
      if (!this.hls) return;
      // Only explicit settings changes / first-fragment promotion call this.
      // A periodic health check must NOT undo hls.js memory-pressure reductions.
      const settings=this.settings(),key=this.bufferPhase+':'+settings.maxBufferSeconds;
      if(key===this.appliedBufferKey)return;this.appliedBufferKey=key;
      const cfg=this.hlsConfig(this.bufferPhase==='reserve');
      if(this.quotaLimited) cfg.maxBufferLength=cfg.maxMaxBufferLength=Math.min(cfg.maxBufferLength,this.bufferTargetSeconds);
      Object.assign(this.hls.config,{
        maxBufferLength:cfg.maxBufferLength,maxMaxBufferLength:cfg.maxMaxBufferLength,
        backBufferLength:cfg.backBufferLength,liveBackBufferLength:cfg.liveBackBufferLength,
        maxBufferSize:cfg.maxBufferSize
      });
      this.bufferTargetSeconds=cfg.maxBufferLength;
      // hls.js runs its loader continuously; increasing its target is enough.
      // Do not startLoad() on every update: that aborts healthy fragment requests.
    }
    maintainBuffer(status) {
      if(!this.hls||!this.attached||this.recovering||this.stopped)return;
      const now=this.clock.now(),ahead=Core.bufferedAhead(this.video),relative=Number(this.video.currentTime)||0;
      if(ahead>0.25&&this.bufferPhase==='startup')this.promoteBuffer();
      const goal=Math.min(this.bufferTargetSeconds,Number(this.hls.config.maxMaxBufferLength)||this.bufferTargetSeconds);
      const available=Math.max(0,(Number(status&&status.hlsSeconds)||0)-relative);
      // Wake the loader BEFORE starvation, also while paused or autoplay-blocked.
      // A healthy in-flight fragment gets 20 s (the ordinary request timeout);
      // otherwise 8 s with no append is enough to detect stopped top-up.
      const hasMore=available>ahead+1.0;
      if(!hasMore||ahead>=goal-1||now-this.lastAppendAt<8000||now-this.lastLoaderWake<8000)return;
      if(this.fragmentLoading&&now-this.lastLoadAt<20000)return;
      if(!this.fragmentLoading&&now-this.lastLoadAt<8000)return;
      this.lastLoaderWake=now;this.lastLoadAt=now;this.fragmentLoading=false;this.loaderWakeups++;
      this.error('Refill wakeup '+this.loaderWakeups+': browser '+ahead.toFixed(1)+'s / '+goal+'s; server '+available.toFixed(1)+'s');
      try{this.hls.startLoad(relative);}catch(e){this.error(e);}
    }
    maybePlay(userGesture) {
      if (!this.id || !this.attached || this.intentPaused || this.recovering || this.playPending) return;
      if (userGesture) this.blocked = false;
      if (this.blocked || this.rebuffering) return;
      const serial = this.generation;
      this.playPending = true;
      let p;
      try { p = this.video.play(); }
      catch (e) { p = Promise.reject(e); }
      Promise.resolve(p).then(() => {
        if (this.valid(serial)) this.notify('');
      }).catch(e => {
        if (!this.valid(serial)) return;
        // A seek/stop interrupts play() normally. It is not a decoder failure.
        if (e && e.name === 'AbortError') return;
        if (e && e.name === 'NotAllowedError') {
          this.blocked = true;
          this.notify('Browser blocked autoplay. Press the Play video button to start audio/video.');
        } else { this.error(e); }
      }).finally(() => { if (this.valid(serial)) this.playPending = false; });
    }
    control(action, value, userGesture) {
      if (action === 'play') {
        this.intentPaused = false; this.blocked = false;
        this.lastPlayProgress = this.clock.now(); this.starvedAt = 0;
        if (Core.bufferedAhead(this.video) >= Core.resumeTarget(this.settings(), this.status, this.video.currentTime)) this.rebuffering = false;
        this.maybePlay(userGesture);
      } else if (action === 'pause' || action === 'stop') {
        this.intentPaused = true; this.rebuffering = false; this.video.pause();
        if (action === 'stop') { this.stop(); this.notify(''); }
      } else if (action === 'mute') this.video.muted = !!value;
      else if (action === 'volume') this.video.volume = Math.max(0, Math.min(1, Number(value) || 0));
      else if (action === 'rate') {
        const rate = Number(value);
        if (rate >= 0.25 && rate <= 4) { try { this.video.playbackRate = rate; } catch (_) {} }
      } else if (action === 'flush' && this.hls) this.hls.startLoad(Number(this.video.currentTime) || 0);
    }
    progress() {
      if (!this.attached) return;
      const t = Number(this.video.currentTime) || 0;
      if (Math.abs(t - this.lastTime) >= 0.05) {
        this.lastTime = t; this.lastPlayProgress = this.clock.now(); this.starvedAt = 0;this.promoteBuffer();
        if (this.firstFrameMs === null && t > 0) this.firstFrameMs = this.clock.now() - this.startedAt;
      }
    }
    scheduleHealth(serial) {
      if (!this.valid(serial)) return;
      this.timer = this.clock.setTimeout(async () => {
        this.timer = null;
        try { await this.hlsHealth(serial); } catch (e) { if (this.valid(serial)) this.error(e); }
        if (this.valid(serial)) this.scheduleHealth(serial);
      }, 750);
    }
    async hlsHealth(serial) {
      serial = serial == null ? this.generation : serial;
      if (!this.valid(serial) || this.recovering || this.clock.now() < this.recoveryWaitUntil) return;
      this.maintainBuffer(this.status);
      const st = await this.pollStatus(serial);
      if (!this.valid(serial) || !st || this.recovering) return;
      this.progress();this.maintainBuffer(st);
      if(this.explicitVideoCopyActive()&&!this.mpeg2FallbackPending&&!this.blocked&&!this.intentPaused&&
          this.clock.now()-this.startedAt>8000&&(Number(this.video.currentTime)||0)>0.5&&
          (!this.video.videoWidth||!this.video.videoHeight)){
        await this.fallbackVideoCopy('audio/timeline advanced but the browser produced no video dimensions');return;
      }
      if(this.transport==='mpegts' && !this.firstFrameMs && this.clock.now()-this.startedAt>45000 && !this.blocked && !this.intentPaused){await this.fallbackTs('No initial video progress after 45 seconds');return;}
      if (this.intentPaused || this.blocked) return;
      const now = this.clock.now(), ahead = Core.bufferedAhead(this.video);
      const relative = Number(this.video.currentTime) || 0;
      const producedAhead = Math.max(0, (Number(st.hlsSeconds) || 0) - relative);
      if (ahead < 0.25 && !this.video.ended) {
        if (!this.starvedAt) this.starvedAt = now;
        if (st.running || producedAhead > 0.5) {
          this.rebuffering = true;
          if (!this.video.paused) this.video.pause();
          this.notify('Buffering… browser ' + ahead.toFixed(1) + 's · server ahead ' + producedAhead.toFixed(1) + 's');
        }
      }
      if (this.rebuffering && ahead >= Core.resumeTarget(this.settings(), st, relative)) {
        this.rebuffering = false; this.starvedAt = 0;
        this.maybePlay(); // intentPaused is checked, never an undefined `paused`
      } else if (!this.rebuffering && this.video.paused && !this.video.ended && ahead >= 0.25) this.maybePlay();
      const depleted = ahead < 0.35 && producedAhead < 1.5;
      if (depleted && Core.incompleteEnd(st, relative, this.durationMs, this.startSeconds)) {
        await this.recover('Producer ended before the expected media end'); return;
      }
      if (depleted && !st.running && !st.endList && now - this.lastProduceProgress > 3000) {
        await this.recover('FFmpeg stopped without completing its output'); return;
      }
      // If playable media already exists on the server, handle the browser
      // loader first. A producer waiting at a growing file's EOF is not proof
      // of producer failure, and restarting it discards a healthy reserve.
      if (this.starvedAt && now - this.starvedAt > 12000 && producedAhead > 2 && this.hls) {
        if(now-this.lastLoaderWake<3000)return; // let a proactive wakeup settle
        this.starvedAt = now;
        if (this.localNetworkRetries++ < 2) { this.lastLoaderWake=now;this.hls.startLoad(relative); }
        else await this.recover('Browser stopped loading available HLS segments');
        return;
      }
      if(this.transport==='mpegts'&&this.starvedAt&&now-this.starvedAt>15000&&producedAhead>2){
        await this.fallbackTs('MPEG-TS stopped buffering available server output');return;
      }
      // Producer recovery is reserved for no playable reserve AND no progress.
      if (this.starvedAt && now - this.starvedAt > 12000 && now - this.lastProduceProgress > 12000 && now-this.lastInputProgress>12000 && st.running && producedAhead < 1.5) {
        await this.recover('FFmpeg stream stopped advancing with no source-read progress'); return;
      }
    }
    async ended() {
      const serial = this.generation;
      if (!this.valid(serial) || this.intentPaused || this.recovering || this.clock.now() < this.recoveryWaitUntil) return;
      const st = await this.pollStatus(serial);
      if (!this.valid(serial)) return;
      if (st && Core.incompleteEnd(st, Number(this.video.currentTime) || 0, this.durationMs, this.startSeconds)) {
        await this.recover('Browser reached a premature HLS end');
      } else if (st && st.running) {
        // A temporary EVENT tail must continue loading; it is not media EOF.
        if (this.hls) this.hls.startLoad(Number(this.video.currentTime) || 0);
        this.maybePlay();
      } else if (this.o.mediaUpdate) this.o.mediaUpdate();
    }
    async recover(reason) {
      if (!this.id || this.recovering || this.stopped || this.clock.now() < this.recoveryWaitUntil) return false;
      if (!this.budget.take()) {
        this.intentPaused = true; this.video.pause();
        this.notify('Playback recovery stopped after 6 attempts. Open Diagnostics or restart the recording.');
        return false;
      }
      const serial = this.generation, id = this.id, timeMs = this.absoluteTime();
      this.recovering = true;
      this.lastFailureClass=this.classifyFailure(reason,this.status);
      this.error('['+this.lastFailureClass+'] '+reason + ' (' + this.budget.count + '/' + this.budget.limit + ')');
      this.notify('Recovering playback at ' + Core.timeText(timeMs) + '…');
      try {
        // Position + expected HLS id travel in ONE request. A prior asynchronous
        // state report cannot race this seek or restart a newly selected video.
        const result = await this.o.recover({timeMs, expectedHlsSession:id});
        if (this.valid(serial)) {
          this.recovering = false;
          // videoStart may already have arrived on the event stream. Otherwise
          // let it arrive before the next starvation check can recover again.
          this.starvedAt = this.clock.now(); this.lastProduceProgress = this.clock.now();
          this.recoveryWaitUntil = this.clock.now() + 10000;
          if (result && result.stale) this.error('Ignored stale recovery after the media changed');
        }
        return true;
      } catch (e) {
        if (this.valid(serial)) {
          this.recovering = false; this.starvedAt = this.clock.now(); this.lastProduceProgress = this.clock.now();
          this.recoveryWaitUntil = this.clock.now() + 3000;
          this.error(e); this.notify('Recovery failed: ' + this.lastError);
        }
        return false;
      }
    }
  }
  return Playback;
});
