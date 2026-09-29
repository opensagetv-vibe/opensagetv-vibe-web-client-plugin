#!/usr/bin/env python3
"""Verify bundled icons against the recorded upstream asset inventory.

SVG pathData becomes SVG d verbatim with unchanged path order/colors.
The two PNGs are byte-identical upstream resources (Git blob SHA checked).
No network or font dependency is involved.
"""
from pathlib import Path
import hashlib, json, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'src/main/webapp'
man=json.loads((ROOT/'third_party/vibe-icons.json').read_text())
html=(WEB/'miniclient.html').read_text();worker=(WEB/'sw.js').read_text()
for asset in man['assets']:
    name=asset['output'];data=(WEB/name).read_bytes()
    assert hashlib.sha256(data).hexdigest()==asset['sha256'],name+' SHA-256 mismatch'
    if name.endswith('.png'):
        assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==asset['blobSha'],name+' Git blob mismatch'
    else:
        root=ET.fromstring(data);ns='{http://www.w3.org/2000/svg}'
        assert root.attrib['viewBox']=='0 0 24 24',name
        paths=root.findall(ns+'path')
        assert [p.attrib['d'] for p in paths]==asset['paths'],name+' shape mismatch'
        assert [p.attrib['fill'] for p in paths]==asset['colors'],name+' color mismatch'
        assert len(list(root))==len(paths),name+' unexpected SVG elements'
    assert 'src="'+name+'"' in html,name+' missing from overlay'
    assert repr(name) in worker,name+' missing from offline shell'
assert len(man['assets'])==30 and len({a['output'] for a in man['assets']})==30
print('PASS 30 Vibe Android assets: recorded shapes/colors, SHA-256, PNG Git blobs, HTML and offline shell references')
