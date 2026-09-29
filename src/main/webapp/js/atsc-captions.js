(function (global) {
  'use strict';

  function concat(a, b) {
    if (!a || !a.length) return new Uint8Array(b);
    if (!b || !b.length) return new Uint8Array(a);
    const out = new Uint8Array(a.length + b.length);
    out.set(a, 0);
    out.set(b, a.length);
    return out;
  }

  function decodePts(p) {
    if (!p || p.length < 5) return null;
    // MPEG PES 33-bit PTS encoded as 0010/0011 + marker bits.
    return (((p[0] & 0x0e) * 536870912) +
      (p[1] * 4194304) +
      ((p[2] & 0xfe) * 16384) +
      (p[3] * 128) +
      ((p[4] & 0xfe) >> 1));
  }

  const BASIC_MAP = {
    0x2a: 'á', 0x5c: 'é', 0x5e: 'í', 0x5f: 'ó', 0x60: 'ú',
    0x7b: 'ç', 0x7c: '÷', 0x7d: 'Ñ', 0x7e: 'ñ', 0x7f: '█'
  };
  const SPECIAL_MAP = [
    '®', '°', '½', '¿', '™', '¢', '£', '♪', 'à', ' ', 'è', 'â', 'ê', 'î', 'ô', 'û'
  ];
  const EXTENDED_12 = [
    'Á','É','Ó','Ú','Ü','ü','‘','¡','*',"'",'—','©','℠','•','“','”',
    'À','Â','Ç','È','Ê','Ë','ë','Î','Ï','ï','Ô','Ù','ù','Û','«','»'
  ];
  const EXTENDED_13 = [
    'Ã','ã','Í','Ì','ì','Ò','ò','Õ','õ','{','}',"\\",'^','_','|','~',
    'Ä','ä','Ö','ö','ß','¥','¤','│','Å','å','Ø','ø','┌','┐','└','┘'
  ];

  class Cea608Channel {
    constructor(onCue) {
      this.onCue = onCue;
      this.mode = 'popon';
      this.displayed = '';
      this.nonDisplayed = '';
      this.rollRows = 2;
      this.lastControl = '';
      this.lastControlPts = -1;
      this.lastEmitted = null;
    }

    reset() {
      this.mode = 'popon';
      this.displayed = '';
      this.nonDisplayed = '';
      this.lastControl = '';
      this.lastControlPts = -1;
      this.lastEmitted = null;
    }

    targetText() {
      return this.mode === 'popon' ? this.nonDisplayed : this.displayed;
    }

    setTarget(text) {
      if (this.mode === 'popon') this.nonDisplayed = text;
      else this.displayed = text;
    }

    clean(text) {
      return text.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trimEnd();
    }

    emit(ptsMs) {
      const text = this.clean(this.displayed);
      if (text === this.lastEmitted) return;
      this.lastEmitted = text;
      this.onCue(ptsMs, text);
    }

    appendChar(ch, ptsMs) {
      let t = this.targetText();
      t += ch;
      this.setTarget(t);
      if (this.mode !== 'popon') this.emit(ptsMs);
    }

    control(b1, b2, ptsMs) {
      const key = `${b1}:${b2}`;
      if (this.lastControl === key && Math.abs(ptsMs - this.lastControlPts) < 700) {
        this.lastControl = '';
        return true;
      }
      this.lastControl = key;
      this.lastControlPts = ptsMs;

      if (b2 === 0x20) { // RCL
        this.mode = 'popon';
        return true;
      }
      if (b2 >= 0x25 && b2 <= 0x27) { // RU2/RU3/RU4
        this.mode = 'rollup';
        this.rollRows = b2 - 0x23;
        this.nonDisplayed = '';
        return true;
      }
      if (b2 === 0x29) { // RDC / paint-on
        this.mode = 'painton';
        return true;
      }
      if (b2 === 0x21) { // BS
        const t = this.targetText();
        this.setTarget(t.slice(0, -1));
        if (this.mode !== 'popon') this.emit(ptsMs);
        return true;
      }
      if (b2 === 0x2c) { // EDM
        this.displayed = '';
        this.emit(ptsMs);
        return true;
      }
      if (b2 === 0x2d) { // CR
        if (this.mode === 'rollup') {
          let lines = this.displayed.split('\n');
          lines.push('');
          while (lines.length > this.rollRows) lines.shift();
          this.displayed = lines.join('\n');
          this.emit(ptsMs);
        } else {
          this.appendChar('\n', ptsMs);
        }
        return true;
      }
      if (b2 === 0x2e) { // ENM
        this.nonDisplayed = '';
        return true;
      }
      if (b2 === 0x2f) { // EOC - swap pop-on memories
        const old = this.displayed;
        this.displayed = this.nonDisplayed;
        this.nonDisplayed = old;
        this.mode = 'popon';
        this.emit(ptsMs);
        return true;
      }
      return false;
    }

    pushPair(b1, b2, ptsMs) {
      b1 &= 0x7f;
      b2 &= 0x7f;
      if (!b1 && !b2) return;

      // Control code (channel bit may turn 0x14 into 0x1c; caller resolves channel).
      if ((b1 === 0x14 || b1 === 0x1c || b1 === 0x15 || b1 === 0x1d) && b2 >= 0x20 && b2 <= 0x2f) {
        this.control(b1, b2, ptsMs);
        return;
      }

      // Mid-row/PAC formatting. Keep layout simple in this prototype.
      if ((b1 >= 0x10 && b1 <= 0x1f) && b2 >= 0x40 && b2 <= 0x7f) {
        if (this.targetText() && !this.targetText().endsWith('\n')) this.appendChar('\n', ptsMs);
        return;
      }

      // Special North-American character set.
      if ((b1 === 0x11 || b1 === 0x19) && b2 >= 0x30 && b2 <= 0x3f) {
        this.appendChar(SPECIAL_MAP[b2 - 0x30] || '', ptsMs);
        return;
      }

      // Extended Spanish/French and Portuguese/German sets. They replace the
      // immediately preceding basic character, per CEA-608.
      if ((b1 === 0x12 || b1 === 0x1a) && b2 >= 0x20 && b2 <= 0x3f) {
        const t = this.targetText();
        if (t.length) this.setTarget(t.slice(0, -1));
        this.appendChar(EXTENDED_12[b2 - 0x20] || '', ptsMs);
        return;
      }
      if ((b1 === 0x13 || b1 === 0x1b) && b2 >= 0x20 && b2 <= 0x3f) {
        const t = this.targetText();
        if (t.length) this.setTarget(t.slice(0, -1));
        this.appendChar(EXTENDED_13[b2 - 0x20] || '', ptsMs);
        return;
      }

      const append = (b) => {
        if (b >= 0x20 && b <= 0x7f) this.appendChar(BASIC_MAP[b] || String.fromCharCode(b), ptsMs);
      };
      append(b1);
      append(b2);
    }
  }


  class Cea708Service {
    constructor(serviceNumber, onCue) {
      this.serviceNumber = serviceNumber;
      this.onCue = onCue;
      this.currentWindow = 0;
      this.windows = Array.from({length: 8}, (_, id) => this.makeWindow(id));
      this.lastKey = null;
    }

    makeWindow(id) {
      return {
        id, exists: false, visible: false, priority: 7,
        rowLock: false, colLock: false, relative: false,
        anchorV: 74, anchorH: 104, anchorId: 7,
        rowCount: 4, colCount: 32, penRow: 0, penCol: 0,
        rows: Array.from({length: 15}, () => Array(42).fill(' ')),
        pen: {italic: false, underline: false, size: 1, fg: '#fff', bg: 'rgba(0,0,0,.75)'},
        windowStyle: 0, penStyle: 0
      };
    }

    reset() {
      this.currentWindow = 0;
      this.windows = Array.from({length: 8}, (_, id) => this.makeWindow(id));
      this.lastKey = null;
    }

    selected() { return this.windows[this.currentWindow]; }
    clearWindow(w) {
      w.rows = Array.from({length: 15}, () => Array(42).fill(' '));
      w.penRow = 0; w.penCol = 0;
    }

    bitmap(value, fn) { for (let i = 0; i < 8; i++) if (value & (1 << i)) fn(this.windows[i], i); }

    append(ch) {
      const w = this.selected();
      if (!w.exists) { w.exists = true; w.visible = true; }
      if (ch === '\n') { w.penRow = Math.min(14, w.penRow + 1); w.penCol = 0; return; }
      const chars = Array.from(ch);
      chars.forEach(c => {
        const row = Math.max(0, Math.min(14, w.penRow));
        const col = Math.max(0, Math.min(41, w.penCol));
        w.rows[row][col] = c;
        w.penCol = Math.min(41, col + 1);
      });
    }

    windowText(w) {
      let last = -1;
      const rows = [];
      for (let r = 0; r < Math.min(15, Math.max(1, w.rowCount + 1)); r++) {
        const line = w.rows[r].slice(0, Math.min(42, Math.max(1, w.colCount + 1))).join('').replace(/\s+$/g, '');
        rows.push(line);
        if (line.trim()) last = r;
      }
      return last < 0 ? '' : rows.slice(0, last + 1).join('\n').replace(/^\n+|\n+$/g, '');
    }

    windowMeta(w) {
      const text = this.windowText(w);
      const denomV = w.relative ? 99 : 74;
      const denomH = w.relative ? 99 : 209;
      return {
        id: w.id, visible: w.visible, priority: w.priority, text,
        top: Math.max(0, Math.min(100, (w.anchorV / denomV) * 100)),
        left: Math.max(0, Math.min(100, (w.anchorH / denomH) * 100)),
        anchorId: w.anchorId, rows: w.rowCount + 1, columns: w.colCount + 1,
        italic: !!w.pen.italic, underline: !!w.pen.underline,
        fg: w.pen.fg, bg: w.pen.bg
      };
    }

    composedMeta() {
      const windows = this.windows.filter(w => w.exists && w.visible && this.windowText(w)).map(w => this.windowMeta(w));
      windows.sort((a, b) => a.priority - b.priority || a.id - b.id);
      return {type: '708', service: this.serviceNumber, windows};
    }

    emit(ptsMs) {
      const meta = this.composedMeta();
      const text = meta.windows.map(w => w.text).filter(Boolean).join('\n');
      const key = JSON.stringify(meta);
      if (key === this.lastKey) return;
      this.lastKey = key;
      this.onCue(ptsMs, text, meta);
    }

    colorFromByte(b, alphaBits) {
      const r = ((b >> 4) & 3) * 85, g = ((b >> 2) & 3) * 85, bl = (b & 3) * 85;
      const op = alphaBits == null ? 0 : alphaBits;
      const alpha = op === 3 ? 0 : op === 2 ? .45 : 1;
      return `rgba(${r},${g},${bl},${alpha})`;
    }

    decode(bytes, ptsMs) {
      for (let i = 0; i < bytes.length; i++) {
        const b = bytes[i];
        if (b >= 0x20 && b <= 0x7e) this.append(String.fromCharCode(b));
        else if (b === 0x7f) this.append('♪');
        else if (b >= 0xa0) this.append(String.fromCharCode(b));
        else if (b === 0x00 || b === 0x03) { /* NUL/ETX */ }
        else if (b === 0x08) { const w=this.selected(); if (w.penCol>0) {w.penCol--; w.rows[w.penRow][w.penCol]=' ';} }
        else if (b === 0x0c) this.clearWindow(this.selected());
        else if (b === 0x0d) this.append('\n');
        else if (b === 0x0e) this.selected().penCol = 0;
        else if (b === 0x10) {
          if (++i >= bytes.length) break;
          const x = bytes[i];
          const g2 = {0x20:' ',0x21:' ',0x25:'…',0x2a:'Š',0x2c:'Œ',0x30:'█',0x31:'‘',0x32:'’',0x33:'“',0x34:'”',0x35:'•',0x39:'™',0x3a:'š',0x3c:'œ',0x3d:'℠',0x3f:'Ÿ',0x76:'⅛',0x77:'⅜',0x78:'⅝',0x79:'⅞',0x7a:'│',0x7b:'┐',0x7c:'└',0x7d:'─',0x7e:'┘',0x7f:'┌'};
          if (x >= 0x20 && x <= 0x7f) { if (g2[x]) this.append(g2[x]); }
          else if (x <= 0x07) { /* C2 len 0 */ }
          else if (x <= 0x0f) i += 1;
          else if (x <= 0x17) i += 2;
          else if (x <= 0x1f) i += 3;
          else if (x >= 0x80 && x <= 0x87) i += 4;
          else if (x >= 0x88 && x <= 0x8f) i += 5;
          else if (x >= 0x90 && x <= 0x9f) i += 6;
        } else if (b === 0x18) {
          if (i + 2 < bytes.length) { this.append(String.fromCharCode((bytes[i+1] << 8) | bytes[i+2])); i += 2; }
        } else if (b >= 0x80 && b <= 0x87) this.currentWindow = b - 0x80;
        else if (b >= 0x88 && b <= 0x8c) {
          if (++i >= bytes.length) break;
          const mask=bytes[i];
          if (b===0x88) this.bitmap(mask,w=>this.clearWindow(w));
          else if (b===0x89) this.bitmap(mask,w=>{w.exists=true;w.visible=true;});
          else if (b===0x8a) this.bitmap(mask,w=>{w.visible=false;});
          else if (b===0x8b) this.bitmap(mask,w=>{w.visible=!w.visible;});
          else this.bitmap(mask,w=>{const nw=this.makeWindow(w.id);Object.assign(w,nw);});
        } else if (b === 0x8d) i += 1; // DLY: input timestamps already schedule display
        else if (b === 0x8e) { /* DLC */ }
        else if (b === 0x8f) this.reset();
        else if (b === 0x90) { // SPA
          if (i + 2 < bytes.length) { const w=this.selected(), p1=bytes[i+1], p2=bytes[i+2]; w.pen.size=p1&3; w.pen.underline=!!(p2&0x40); w.pen.italic=!!(p2&0x80); } i+=2;
        } else if (b === 0x91) { // SPC
          if (i + 3 < bytes.length) { const w=this.selected(), f=bytes[i+1], bg=bytes[i+2]; w.pen.fg=this.colorFromByte(f,(f>>6)&3); w.pen.bg=this.colorFromByte(bg,(bg>>6)&3); } i+=3;
        } else if (b === 0x92) { // SPL
          if (i + 2 < bytes.length) { const w=this.selected(); w.penRow=bytes[i+1]&0x0f; w.penCol=bytes[i+2]&0x3f; } i+=2;
        } else if (b === 0x97) i += 4; // SWA styling currently represented by safe defaults
        else if (b >= 0x98 && b <= 0x9f) {
          const id=b-0x98; if (i+6>=bytes.length) break;
          const p1=bytes[i+1], p2=bytes[i+2], p3=bytes[i+3], p4=bytes[i+4], p5=bytes[i+5], p6=bytes[i+6];
          const old=this.windows[id], w=old.exists?old:this.makeWindow(id);
          w.exists=true; w.priority=p1&7; w.colLock=!!(p1&0x08); w.rowLock=!!(p1&0x10); w.visible=!!(p1&0x20);
          w.anchorV=p2&0x7f; w.relative=!!(p2&0x80); w.anchorH=p3; w.rowCount=p4&0x0f; w.anchorId=(p4>>4)&0x0f; w.colCount=p5&0x3f; w.penStyle=p6&7; w.windowStyle=(p6>>3)&7;
          this.windows[id]=w; this.currentWindow=id; i+=6;
        }
        if (i >= bytes.length) break;
      }
      this.emit(ptsMs);
    }
  }

  class SageAtscCaptions {
    constructor(options) {
      options = options || {};
      this.onCaption = options.onCaption || function () {};
      this.onStats = options.onStats || function () {};
      this.enabled = options.enabled !== false;
      this.service = options.service || 'CC1';
      this.tsCarry = new Uint8Array(0);
      this.psi = new Map();
      this.pmtPid = null;
      this.videoPid = null;
      this.videoStreamType = null;
      this.currentPts90k = 0;
      this.basePts90k = null;
      this.lastPtsRaw = null;
      this.ptsWrapOffset = 0;
      this.esCarry = new Uint8Array(0);
      this.cues = [];
      this.cueCursor = -1;
      this.lastTimeMs = 0;
      this.currentRendered = '';
      this.currentRenderedMeta = null;
      this.fieldChannel = {0: 1, 1: 3};
      this.channels = {};
      for (let i = 1; i <= 4; i++) {
        this.channels[i] = new Cea608Channel((pts, text) => this.addCue(i, pts, text));
      }
      this.stats = {
        packets: 0,
        continuityErrors: 0,
        pmtPid: null,
        videoPid: null,
        videoStreamType: null,
        cea608Pairs: 0,
        cea708Triples: 0,
        captionServices: []
      };
      this.lastCc = new Map();
      this.dtvccPacket = null;
      this.services708 = new Map();
    }

    setEnabled(enabled) {
      this.enabled = !!enabled;
      if (!this.enabled) this.render('');
      else this.updateTime(this.lastTimeMs);
    }

    setService(service) {
      this.service = service || 'CC1';
      this.cueCursor = -1;
      this.currentRendered = '';
      this.updateTime(this.lastTimeMs);
    }

    resetForSeek() {
      this.tsCarry = new Uint8Array(0);
      this.esCarry = new Uint8Array(0);
      this.currentPts90k = 0;
      this.lastPtsRaw = null;
      this.ptsWrapOffset = 0;
      this.cues = [];
      this.cueCursor = -1;
      this.currentRendered = '';
      this.currentRenderedMeta = null;
      for (let i = 1; i <= 4; i++) this.channels[i].reset();
      this.dtvccPacket = null;
      this.services708.forEach(s => s.reset());
      this.render('');
    }

    addCue(channel, ptsMs, text, meta) {
      const service = typeof channel === 'string' ? channel : `CC${channel}`;
      this.cues.push({service, ptsMs: Math.max(0, ptsMs || 0), text, meta: meta || {type: '608', service}});
      if (this.cues.length > 5000) {this.cues.splice(0, 1000);this.cueCursor=Math.max(-1,this.cueCursor-1000);}
    }

    render(text) {
      if (!this.enabled) text = '';
      if (text === this.currentRendered) return;
      this.currentRendered = text;
      this.onCaption(text, this.service, this.currentRenderedMeta || {type: '608', service: this.service});
    }

    updateTime(timeMs) {
      timeMs = Number(timeMs || 0);
      if (timeMs < this.lastTimeMs - 1000) this.cueCursor = -1;
      this.lastTimeMs = timeMs;
      let best = null;
      // Cues are normally ordered; scan from current point and keep the latest selected-service cue.
      for (let i = Math.max(0, this.cueCursor); i < this.cues.length; i++) {
        const cue = this.cues[i];
        if (cue.ptsMs > timeMs + 80) break;
        this.cueCursor = i;
        if (cue.service === this.service) best = cue;
      }
      if (!best) {
        for (let i = Math.min(this.cueCursor, this.cues.length - 1); i >= 0; i--) {
          const cue = this.cues[i];
          if (cue.service === this.service && cue.ptsMs <= timeMs + 80) { best = cue; break; }
        }
      }
      if (best) { this.currentRenderedMeta = best.meta; this.render(best.text); }
    }

    getStats() {
      return Object.assign({}, this.stats, {
        queuedCues: this.cues.length,
        selectedService: this.service
      });
    }

    // A/53 triples extracted by the plugin before encoding; no dependency on
    // native MSE exposing SEI or on an encoder retaining broadcast captions.
    pushCcData(bytes, ptsMs) {
      if(!bytes||bytes.length>93||bytes.length%3!==0)return;
      for(let i=0;i+2<bytes.length;i+=3){
        const flags=bytes[i],type=flags&3;if(!(flags&4))continue;
        if(type<=1){this.stats.cea608Pairs++;this.push608(type,bytes[i+1],bytes[i+2],ptsMs);}
        else{this.stats.cea708Triples++;this.pushDtvcc(type,bytes[i+1],bytes[i+2],ptsMs);}
      }
    }

    push(bytes) {
      if (!bytes || !bytes.length) return;
      let data = concat(this.tsCarry, bytes);
      let pos = 0;

      // Find packet sync. Validate against a following packet when possible.
      while (pos < data.length && data[pos] !== 0x47) pos++;
      if (pos > 0) data = data.subarray(pos);

      let offset = 0;
      while (offset + 188 <= data.length) {
        if (data[offset] !== 0x47) {
          offset++;
          continue;
        }
        if (offset + 376 <= data.length && data[offset + 188] !== 0x47) {
          offset++;
          continue;
        }
        this.processPacket(data.subarray(offset, offset + 188));
        offset += 188;
      }
      this.tsCarry = new Uint8Array(data.subarray(offset));
      this.onStats(this.getStats());
    }


    get708Service(number) {
      if (!this.services708.has(number)) {
        this.services708.set(number, new Cea708Service(number, (pts, text, meta) => this.addCue(`708-${number}`, pts, text, meta)));
      }
      return this.services708.get(number);
    }

    pushDtvcc(type, b1, b2, ptsMs) {
      if (type === 3) {
        if (this.dtvccPacket && this.dtvccPacket.bytes.length) this.processDtvccPacket(this.dtvccPacket);
        const sizeCode = b1 & 0x3f;
        const totalBytes = sizeCode === 0 ? 128 : sizeCode * 2;
        this.dtvccPacket = {ptsMs, expected: Math.max(0, totalBytes - 1), bytes: [b2]};
      } else if (type === 2 && this.dtvccPacket) {
        this.dtvccPacket.bytes.push(b1, b2);
      }
      if (this.dtvccPacket && this.dtvccPacket.bytes.length >= this.dtvccPacket.expected) {
        const packet = this.dtvccPacket;
        this.dtvccPacket = null;
        this.processDtvccPacket(packet);
      }
    }

    processDtvccPacket(packet) {
      const data = packet.bytes.slice(0, packet.expected);
      let i = 0;
      while (i < data.length) {
        const h = data[i++];
        let service = (h >> 5) & 0x07;
        const blockSize = h & 0x1f;
        if (service === 0 && blockSize === 0) break;
        if (service === 7) {
          if (i >= data.length) break;
          service = data[i++] & 0x3f;
        }
        if (!service || i + blockSize > data.length) break;
        const block = data.slice(i, i + blockSize);
        this.get708Service(service).decode(block, packet.ptsMs);
        i += blockSize;
      }
    }

    processPacket(p) {
      this.stats.packets++;
      if (p[1] & 0x80) return;
      const pusi = !!(p[1] & 0x40);
      const pid = ((p[1] & 0x1f) << 8) | p[2];
      const afc = (p[3] >> 4) & 0x03;
      const cc = p[3] & 0x0f;
      const hasPayload = afc === 1 || afc === 3;

      if (hasPayload) {
        const last = this.lastCc.get(pid);
        if (last != null && pid !== 0x1fff && ((last + 1) & 0x0f) !== cc) this.stats.continuityErrors++;
        this.lastCc.set(pid, cc);
      }
      if (!hasPayload) return;

      let o = 4;
      if (afc === 3) {
        const len = p[o];
        if (len > 0 && o + 1 < 188 && (p[o + 1] & 0x80)) {
          this.lastCc.delete(pid);
          if (pid === this.videoPid) { this.esCarry = new Uint8Array(0); this.dtvccPacket = null; }
        }
        o += 1 + len;
      }
      if (o >= 188) return;
      const payload = p.subarray(o);

      if (pid === 0 || pid === this.pmtPid) {
        this.pushPsi(pid, payload, pusi);
      }
      if (this.videoPid != null && pid === this.videoPid) {
        this.pushVideoPes(payload, pusi);
      }
    }

    pushPsi(pid, payload, pusi) {
      let existing = this.psi.get(pid) || new Uint8Array(0);
      let start = 0;
      if (pusi) {
        if (!payload.length) return;
        const pointer = payload[0];
        start = 1 + pointer;
        existing = new Uint8Array(0);
      }
      if (start >= payload.length) return;
      existing = concat(existing, payload.subarray(start));
      while (existing.length >= 3) {
        const sectionLen = ((existing[1] & 0x0f) << 8) | existing[2];
        const total = 3 + sectionLen;
        if (existing.length < total) break;
        const section = existing.subarray(0, total);
        if (pid === 0 && section[0] === 0x00) this.parsePat(section);
        else if (pid === this.pmtPid && section[0] === 0x02) this.parsePmt(section);
        existing = existing.subarray(total);
      }
      this.psi.set(pid, new Uint8Array(existing));
    }

    parsePat(s) {
      const end = s.length - 4;
      for (let o = 8; o + 4 <= end; o += 4) {
        const program = (s[o] << 8) | s[o + 1];
        const pid = ((s[o + 2] & 0x1f) << 8) | s[o + 3];
        if (program !== 0) {
          this.pmtPid = pid;
          this.stats.pmtPid = pid;
          return;
        }
      }
    }

    parsePmt(s) {
      if (s.length < 16) return;
      const programInfoLen = ((s[10] & 0x0f) << 8) | s[11];
      let o = 12 + programInfoLen;
      const end = s.length - 4;
      const captionServices = [];
      while (o + 5 <= end) {
        const streamType = s[o];
        const pid = ((s[o + 1] & 0x1f) << 8) | s[o + 2];
        const esInfoLen = ((s[o + 3] & 0x0f) << 8) | s[o + 4];
        const descEnd = Math.min(end, o + 5 + esInfoLen);
        let d = o + 5;
        while (d + 2 <= descEnd) {
          const tag = s[d], len = s[d + 1];
          const body = s.subarray(d + 2, Math.min(descEnd, d + 2 + len));
          if (tag === 0x86 && body.length) {
            const count = body[0] & 0x1f;
            let x = 1;
            for (let i = 0; i < count && x + 5 < body.length; i++, x += 6) {
              const lang = String.fromCharCode(body[x], body[x + 1], body[x + 2]);
              const digital = !!(body[x + 3] & 0x80);
              const service = digital ? (body[x + 3] & 0x3f) : ((body[x + 3] & 1) ? 2 : 1);
              captionServices.push({lang, type: digital ? 'CEA-708' : 'CEA-608', service});
            }
          }
          d += 2 + len;
        }
        if (streamType === 0x02 && this.videoPid == null) {
          this.videoPid = pid;
          this.videoStreamType = streamType;
          this.stats.videoPid = pid;
          this.stats.videoStreamType = streamType;
        }
        o += 5 + esInfoLen;
      }
      if (captionServices.length) this.stats.captionServices = captionServices;
    }

    pushVideoPes(payload, pusi) {
      let es = payload;
      if (pusi && payload.length >= 9 && payload[0] === 0 && payload[1] === 0 && payload[2] === 1) {
        const flags = payload[7];
        const headerLen = payload[8];
        if ((flags & 0x80) && payload.length >= 14) {
          const pts = decodePts(payload.subarray(9, 14));
          if (pts != null) {
            if (this.lastPtsRaw != null && pts < this.lastPtsRaw - 0x100000000) this.ptsWrapOffset += 0x200000000;
            this.lastPtsRaw = pts;
            this.currentPts90k = pts + this.ptsWrapOffset;
            if (this.basePts90k == null) this.basePts90k = this.currentPts90k;
          }
        }
        const esStart = 9 + headerLen;
        if (esStart < payload.length) es = payload.subarray(esStart);
        else return;
      }
      this.scanElementary(es, this.currentPts90k);
    }

    scanElementary(chunk, pts90k) {
      const data = concat(this.esCarry, chunk);
      let keepFrom = Math.max(0, data.length - 64);
      for (let i = 0; i + 12 <= data.length; i++) {
        if (data[i] !== 0 || data[i + 1] !== 0 || data[i + 2] !== 1 || data[i + 3] !== 0xb2) continue;
        if (i + 11 > data.length) { keepFrom = i; break; }
        if (data[i + 4] !== 0x47 || data[i + 5] !== 0x41 || data[i + 6] !== 0x39 || data[i + 7] !== 0x34 || data[i + 8] !== 0x03) continue;
        const flags = data[i + 9];
        const count = flags & 0x1f;
        const needed = i + 11 + count * 3;
        if (needed > data.length) { keepFrom = i; break; }
        if (flags & 0x40) {
          const ptsMs = (pts90k && this.basePts90k != null) ? Math.max(0, (pts90k - this.basePts90k) / 90) : 0;
          let x = i + 11;
          for (let n = 0; n < count; n++, x += 3) {
            const ccHeader = data[x];
            const valid = !!(ccHeader & 0x04);
            const type = ccHeader & 0x03;
            if (!valid) continue;
            const b1 = data[x + 1], b2 = data[x + 2];
            if (type <= 1) {
              this.stats.cea608Pairs++;
              this.push608(type, b1, b2, ptsMs);
            } else {
              this.stats.cea708Triples++;
              this.pushDtvcc(type, b1, b2, ptsMs);
            }
          }
        }
        i = needed - 1;
      }
      this.esCarry = new Uint8Array(data.subarray(keepFrom));
      if (this.esCarry.length > 1024) this.esCarry = this.esCarry.subarray(this.esCarry.length - 1024);
    }

    push608(field, raw1, raw2, ptsMs) {
      const b1 = raw1 & 0x7f, b2 = raw2 & 0x7f;
      if ((b1 === 0x14 || b1 === 0x15 || b1 === 0x1c || b1 === 0x1d) && b2 >= 0x20 && b2 <= 0x7f) {
        const secondChannel = b1 === 0x1c || b1 === 0x1d;
        this.fieldChannel[field] = field === 0 ? (secondChannel ? 2 : 1) : (secondChannel ? 4 : 3);
      }
      const ch = this.fieldChannel[field] || (field === 0 ? 1 : 3);
      this.channels[ch].pushPair(raw1, raw2, ptsMs);
    }
  }

  global.SageAtscCaptions = SageAtscCaptions;
})(window);
