(function (global) {
  'use strict';

  class CommercialSkipController {
    constructor(options) {
      this.getTime = options.getTime;
      this.seek = options.seek;
      this.onState = options.onState || function(){};
      this.enabled = false;
      this.markers = [];
      this.timer = null;
      this.lastSkippedEnd = -1;
      this.source = '';
      this.seekInFlight = false;
      this.lastSeekAt = 0;
    }
    setEnabled(v) { this.enabled = !!v; this.emit(); }
    setMarkers(markers, source) {
      this.seekInFlight=false;this.lastSeekAt=0;
      this.markers = (markers || []).map(m => ({start:Number(m.start)||0,end:Number(m.end)||0,type:Number(m.type)||0}))
        .filter(m => m.end > m.start && m.start >= 0).sort((a,b)=>a.start-b.start);
      this.source = source || '';
      this.lastSkippedEnd = -1;
      this.emit();
    }
    activeAt(t) {
      t = Number(t)||0;
      return this.markers.find(m => t >= m.start && t < m.end) || null;
    }
    nextAfter(t) { t=Number(t)||0; return this.markers.find(m => m.start > t) || null; }
    start() {
      if (this.timer) return;
      this.timer = setInterval(() => this.tick(), 250);
      this.emit();
    }
    stop() { if (this.timer) clearInterval(this.timer); this.timer=null; this.emit(); }
    async tick() {
      const t = Number(this.getTime()) || 0;
      const active = this.activeAt(t);
      const now=Date.now();
      if (active && this.enabled && !this.seekInFlight && now-this.lastSeekAt>750 && Math.abs(active.end - this.lastSkippedEnd) > 0.25) {
        this.lastSkippedEnd = active.end;this.seekInFlight=true;this.lastSeekAt=now;
        try { await this.seek(active.end + 0.15); } catch (_) {}
        finally {this.seekInFlight=false;}
      }
      this.emit(active, t);
    }
    async skipCurrent() {
      const active=this.activeAt(this.getTime());
      if (!active||this.seekInFlight) return false;
      this.lastSkippedEnd=active.end;this.seekInFlight=true;this.lastSeekAt=Date.now();
      try {await this.seek(active.end + 0.15);return true;} finally {this.seekInFlight=false;}
    }
    emit(active, t) {
      this.onState({enabled:this.enabled,count:this.markers.length,source:this.source,active:active||this.activeAt(Number(t)||Number(this.getTime())||0),next:this.nextAfter(Number(t)||Number(this.getTime())||0)});
    }
    snapshot() { return {enabled:this.enabled,count:this.markers.length,source:this.source,markers:this.markers.slice(),active:this.activeAt(this.getTime()),seekInFlight:this.seekInFlight,lastSkippedEnd:this.lastSkippedEnd}; }
  }

  global.SageCommercialSkip = { CommercialSkipController };
})(typeof window !== 'undefined' ? window : globalThis);
