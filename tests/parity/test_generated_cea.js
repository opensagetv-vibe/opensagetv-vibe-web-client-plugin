// Exercises the existing 3.2.4 decoder against the generated MPEG-2 fixture.
// This is not a new Teletext/DVB implementation or a browser MSE test.
'use strict';
const fs = require('fs');
const path = require('path');
global.window = global;
require(path.join(__dirname, '../../src/main/webapp/js/atsc-captions.js'));
const media = process.argv[2] || path.join(__dirname, 'fixtures/mpeg2-cc-multiaudio.ts');
const cues = [];
try {
  const decoder = new SageAtscCaptions({onCaption: text => cues.push(text)});
  const bytes = fs.readFileSync(media);
  for (let offset = 0; offset < bytes.length; offset += 188 * 7) {
    decoder.push(bytes.subarray(offset, offset + 188 * 7));
  }
  for (let t = 0; t < 7000; t += 33) decoder.updateTime(t);
  const ok = cues.includes('HI');
  console.log(JSON.stringify({status: ok ? 'PASS' : 'FAIL', checks: 1,
    expectedTextObserved: ok, stats: decoder.getStats(),
    boundary: 'Existing CEA-608 parser and generated TS only; no vendor MSE or field playback.'}, null, 2));
  process.exitCode = ok ? 0 : 1;
} catch (error) {
  console.error(JSON.stringify({status: 'FAIL', error: String(error.message)}));
  process.exitCode = 1;
}
