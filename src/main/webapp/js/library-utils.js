(function (global) {
  'use strict';

  function text(v) { return String(v == null ? '' : v).toLowerCase(); }
  function recentSet(state) { return new Set((state && state.getRecent ? state.getRecent() : []).map(x => Number(x.id))); }

  function filter(items, options, state) {
    options = options || {};
    const q = text(options.q).trim();
    const category = text(options.category).trim();
    const view = options.view || 'all';
    const recent = recentSet(state);
    const out = (items || []).filter(rec => {
      if (options.activeOnly && !rec.recording) return false;
      if (category && category !== 'all' && text(rec.category) !== category) return false;
      if (q) {
        const hay = [rec.title, rec.episode, rec.description, rec.category, rec.year, rec.channelName, rec.channelNumber].map(text).join(' ');
        if (!hay.includes(q)) return false;
      }
      if (view === 'continue' && !(state && state.getResume && state.getResume(rec.id, 0)) && !(Number(rec.serverResumeSeconds) > 15)) return false;
      if (view === 'favorites' && !(state && state.isFavorite && state.isFavorite(rec.id))) return false;
      if (view === 'recent' && !recent.has(Number(rec.id))) return false;
      if (view === 'unwatched' && rec.watched) return false;
      if (view === 'active' && !rec.recording) return false;
      return true;
    });
    const sort = options.sort || 'recent';
    out.sort((a,b) => {
      if (sort === 'title') return String(a.title||'').localeCompare(String(b.title||'')) || Number(b.start||0)-Number(a.start||0);
      if (sort === 'oldest') return Number(a.start||0)-Number(b.start||0);
      if (sort === 'progress') {
        const ar = state && state.getResume ? state.getResume(a.id,0) : null;
        const br = state && state.getResume ? state.getResume(b.id,0) : null;
        return Number(br && br.updatedAt || 0)-Number(ar && ar.updatedAt || 0);
      }
      return Number(b.start||0)-Number(a.start||0);
    });
    return out;
  }

  function categories(items) {
    return Array.from(new Set((items||[]).map(x => String(x.category||'').trim()).filter(Boolean))).sort((a,b)=>a.localeCompare(b));
  }

  function neighbors(items, currentId, title) {
    const list = (items||[]).filter(x => String(x.title||'') === String(title||'') && !x.recording)
      .slice().sort((a,b)=>Number(a.start||0)-Number(b.start||0));
    const idx = list.findIndex(x => Number(x.id) === Number(currentId));
    return {
      previous: idx > 0 ? list[idx-1] : null,
      next: idx >= 0 && idx < list.length-1 ? list[idx+1] : null,
      index: idx,
      count: list.length
    };
  }

  function progress(rec, state) {
    if (!rec || rec.recording || !state || !state.getResume) return 0;
    const r = state.getResume(rec.id, 0);
    if (r && r.duration > 0) return Math.max(0, Math.min(1, Number(r.position||0)/Number(r.duration||1)));
    const server = Number(rec.serverResumeSeconds)||0;
    const duration = Number(rec.duration||0)/1000;
    if (server > 15 && duration > 0) return Math.max(0, Math.min(1, server/duration));
    return 0;
  }

  global.SageLibraryUtils = {filter, categories, neighbors, progress};
})(typeof window !== 'undefined' ? window : globalThis);
