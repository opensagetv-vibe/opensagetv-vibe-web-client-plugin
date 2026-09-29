global.window = global;
require(__dirname + '/../src/main/webapp/js/atsc-captions.js');
let output='';
const p = new SageAtscCaptions({service:'708-1',onCaption:t=>output=t});
function pkt(pid,pusi,payload,cc=0){const a=new Uint8Array(188);a.fill(0xff);a[0]=0x47;a[1]=(pusi?0x40:0)|((pid>>8)&0x1f);a[2]=pid&255;a[3]=0x10|(cc&15);a.set(payload.subarray(0,184),4);return a;}
const pat=Uint8Array.from([0,0x00,0xb0,0x0d,0,1,0xc1,0,0,0,1,0xe1,0,0,0,0,0]);
const pmt=Uint8Array.from([0,0x02,0xb0,0x12,0,1,0xc1,0,0,0xe1,1,0xf0,0,0x02,0xe1,1,0xf0,0,0,0,0,0]);
const pts=Uint8Array.from([0x21,0,1,0,1]);
// two cc triples: type3 packet start (header size code 2, first content byte service1 block-size2), then type2 data 'OK'
const ud=Uint8Array.from([0,0,1,0xb2,0x47,0x41,0x39,0x34,0x03,0x42,0xff,0xff,0x02,0x22,0xfe,0x4f,0x4b]);
const pes=new Uint8Array(14+ud.length);pes.set([0,0,1,0xe0,0,0,0x80,0x80,5],0);pes.set(pts,9);pes.set(ud,14);
const all=new Uint8Array(188*3);all.set(pkt(0,true,pat),0);all.set(pkt(0x100,true,pmt),188);all.set(pkt(0x101,true,pes),376);
p.push(all);p.updateTime(0);console.log(p.getStats());console.log('OUT=',JSON.stringify(output));if(output!=='OK')process.exit(2);
