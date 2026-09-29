(function (global) {
  'use strict';

  function finite(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }

  function findNumber(obj, names, depth) {
    if (!obj || typeof obj !== 'object' || depth < 0) return null;
    for (const name of names) {
      if (Object.prototype.hasOwnProperty.call(obj, name)) {
        const n = finite(obj[name]);
        if (n != null) return n;
      }
    }
    if (depth === 0) return null;
    for (const key of Object.keys(obj)) {
      const value = obj[key];
      if (value && typeof value === 'object') {
        const n = findNumber(value, names, depth - 1);
        if (n != null) return n;
      }
    }
    return null;
  }

  class PerformanceWatchdog {
    constructor(options) {
      options = options || {};
      this.expectedFps = Math.max(1, Number(options.expectedFps) || 29.97);
      this.badRatio = Math.max(0.2, Math.min(0.95, Number(options.badRatio) || 0.72));
      this.badSamplesRequired = Math.max(2, Number(options.badSamplesRequired) || 6);
      this.warmupSamples = Math.max(0, Number(options.warmupSamples) || 8);
      this.maxHistory = Math.max(30, Number(options.maxHistory) || 180);
      this.reset(this.expectedFps);
    }

    reset(expectedFps) {
      if (expectedFps) this.expectedFps = Math.max(1, Number(expectedFps) || this.expectedFps);
      this.samples = 0;
      this.consecutiveBad = 0;
      this.consecutiveGood = 0;
      this.lastVideoStutter = null;
      this.history = [];
      this.last = null;
    }

    update(stats, timeMs) {
      stats = stats || {};
      this.samples++;
      const decodeFps = findNumber(stats, ['videoDecodeFramerate','videoDecodeFps','decodeFps'], 3);
      const renderFps = findNumber(stats, ['videoRenderFramerate','videoRenderFps','renderFps'], 3);
      const videoStutter = findNumber(stats, ['videoStutter','videoStutters','droppedVideoFrames'], 3);
      const audioStutter = findNumber(stats, ['audioStutter','audioStutters'], 3);
      const avDelta = findNumber(stats, ['A_V','avDelta','avSync','audioVideoDelta'], 3);
      const bandwidth = findNumber(stats, ['bandwidth','networkBandwidth'], 3);
      const effectiveFps = renderFps != null && renderFps > 0 ? renderFps : decodeFps;
      const ratio = effectiveFps != null && effectiveFps > 0 ? effectiveFps / this.expectedFps : null;
      const stutterDelta = videoStutter != null && this.lastVideoStutter != null ? Math.max(0, videoStutter - this.lastVideoStutter) : 0;
      if (videoStutter != null) this.lastVideoStutter = videoStutter;

      let bad = false;
      const reasons = [];
      if (ratio != null && ratio < this.badRatio) {
        bad = true;
        reasons.push(`render/decode ${effectiveFps.toFixed(1)} fps < ${(this.expectedFps*this.badRatio).toFixed(1)} fps`);
      }
      if (stutterDelta >= 3) {
        bad = true;
        reasons.push(`${stutterDelta} new video stutters`);
      }
      if (avDelta != null && Math.abs(avDelta) > 1000) {
        bad = true;
        reasons.push(`A/V delta ${Math.round(avDelta)} ms`);
      }

      const warmed = this.samples > this.warmupSamples;
      if (warmed && bad) {
        this.consecutiveBad++;
        this.consecutiveGood = 0;
      } else if (warmed) {
        this.consecutiveGood++;
        if (this.consecutiveGood >= 2) this.consecutiveBad = Math.max(0, this.consecutiveBad - 1);
      }

      let state = 'warming';
      if (warmed) {
        if (this.consecutiveBad >= this.badSamplesRequired) state = 'bad';
        else if (this.consecutiveBad >= Math.max(2, Math.ceil(this.badSamplesRequired/2))) state = 'warning';
        else state = 'good';
      }

      const sample = {
        at: Date.now(),
        timeMs: Number(timeMs) || 0,
        expectedFps: this.expectedFps,
        decodeFps,
        renderFps,
        effectiveFps,
        ratio,
        videoStutter,
        audioStutter,
        stutterDelta,
        avDelta,
        bandwidth,
        warmed,
        bad,
        reasons,
        consecutiveBad: this.consecutiveBad,
        state,
        recommendation: state === 'bad' ? 'compatibility' : 'continue'
      };
      this.last = sample;
      this.history.push(sample);
      if (this.history.length > this.maxHistory) this.history.splice(0, this.history.length - this.maxHistory);
      return sample;
    }

    snapshot() {
      return {
        expectedFps: this.expectedFps,
        samples: this.samples,
        consecutiveBad: this.consecutiveBad,
        last: this.last,
        history: this.history.slice()
      };
    }
  }

  global.SagePerformanceWatchdog = PerformanceWatchdog;
})(typeof window !== 'undefined' ? window : globalThis);
