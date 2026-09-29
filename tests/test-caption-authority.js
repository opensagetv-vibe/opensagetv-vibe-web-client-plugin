'use strict';
const assert=require('assert');const A=require('../src/main/webapp/js/miniclient-caption-authority.js');
const tracks=[
 {index:1,codec:'eia_608',language:'eng',evidence:'metadata'},
 {index:2,codec:'dvb_teletext',serviceKind:'teletext',language:'eng',teletextPage:888,sourcePid:301,evidence:'observed-pmt',availability:'observed'},
 {index:3,codec:'dvb_teletext',serviceKind:'teletext',language:'spa',teletextPage:889,sourcePid:302,evidence:'observed-pmt',availability:'observed'},
 {index:4,codec:'eia_708',language:'eng',accessibilityChannel:1,evidence:'metadata'}
];
assert.equal(A.resolve(tracks,1,'auto','',1).index,2,'CC1 prefers observed English Teletext over synthetic/metadata CEA');
assert.equal(A.resolve(tracks,2,'auto','',2).index,3,'CC2 selects distinct described non-English service');
assert.equal(A.resolve(tracks,1,'cea608','spa',1),null,'CEA-608 language is not invented');
assert.equal(A.effective({mode:'stv'},tracks).local,'off');
assert.equal(A.effective({mode:'dvb'},tracks).local,'dvb');
assert.equal(A.effective({mode:'cc1',cc1Type:'teletext'},tracks).local,'teletext');
console.log('caption authority policy: 6 PASS');
