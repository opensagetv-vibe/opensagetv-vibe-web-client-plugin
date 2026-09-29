#!/usr/bin/env python3
"""Independent FFmpeg decoder checks for synthetic P01 inputs, not browser proof."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


def main() -> int:
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--fixtures',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--ffmpeg',default=shutil.which('ffmpeg') or 'ffmpeg');a=p.parse_args()
    if a.output.exists(): p.error('Refusing to overwrite evidence')
    tests=[]
    def check(name,ok,boundary):
        tests.append(dict(name=name,status='PASS' if ok else 'FAIL',boundary=boundary))
    with tempfile.TemporaryDirectory(prefix='p01-decoder-') as d:
        tmp=Path(d)
        registry=json.loads((a.fixtures/'fixtures.generated.json').read_text())
        for f in registry['fixtures']:
            actual=hashlib.sha256((a.fixtures/f['path']).read_bytes()).hexdigest()
            check('Hash '+f['id'],actual==f['sha256'],'Input integrity only.')
        for size in [188,192,204]:
            sub=tmp/f'ttx-{size}.srt'
            cmd=[a.ffmpeg,'-hide_banner','-v','error','-nostdin','-y','-txt_format','text','-txt_page','888','-txt_duration','1000','-f','mpegts','-i',str(a.fixtures/f'teletext-{size}.ts'),'-map','0:s:0','-c:s','srt','-f','srt',str(sub)]
            r=subprocess.run(cmd,capture_output=True,timeout=30)
            text=sub.read_text() if sub.exists() else ''
            ok=r.returncode==0 and 'P01 TELETEXT VECTOR' in text and 'SECOND PAGE UPDATE' in text
            check('libzvbi page888 '+str(size),ok,'Independent FFmpeg/libzvbi text decoding, using a 1000 ms decoder duration; not browser cue scheduling or broadcast field acceptance.')
        for filename in ['dvb-2bpp.ts','dvb-4bpp.ts','dvb-8bpp.ts','white-rectangle.sup']:
            args=['-f','mpegts'] if filename.endswith('.ts') else []
            cmd=[a.ffmpeg,'-hide_banner','-v','error','-nostdin','-copyts','-f','lavfi','-i','color=black:s=720x576:r=1:d=5']+args+['-i',str(a.fixtures/filename),'-filter_complex','[0:v][1:s]overlay=eof_action=pass,format=rgb24[out]','-map','[out]','-frames:v','5','-threads','1','-f','rawvideo','pipe:1']
            r=subprocess.run(cmd,capture_output=True,timeout=30)
            frame_bytes=720*576*3
            ok=r.returncode==0 and len(r.stdout)==5*frame_bytes
            active_frames=[]
            if ok:
                empty=bytes(frame_bytes)
                golden=bytearray(frame_bytes)
                for y in (400,401):
                    golden[(y*720+100)*3:(y*720+104)*3]=b'\xff'*12
                for frame in range(5):
                    pixels=r.stdout[frame*frame_bytes:(frame+1)*frame_bytes]
                    if pixels == golden:
                        active_frames.append(frame)
                    elif pixels != empty:
                        ok=False
                # FFmpeg's 1 fps framesync applies these subtitle frames on the
                # following video sampling boundary. This gate verifies exact
                # pixels and ordered show/clear, not browser PTS alignment.
                ok = ok and len(active_frames)==2 and active_frames[1]==active_frames[0]+1 and active_frames[0]>0 and active_frames[-1]<4
            check('Independent bitmap pixels/show/clear '+filename,ok,'Actual FFmpeg subtitle decode: exact RGB pixels, consecutive visible frames, preceding/following clear frames. One-fps framesync is not a browser clock synchronization test.')
            tests[-1]['visible_frame_indices']=active_frames
            if not ok: tests[-1]['decoder_error']=r.stderr.decode(errors='replace')[:2000]
    report=dict(schema_version=1,status='PASS' if all(t['status']=='PASS' for t in tests) else 'FAIL',checks=len(tests),runtime_version_unchanged=True,webplayer_feature_implemented=False,real_vendor_mse=False,tests=tests)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(dict(status=report['status'],checks=len(tests))))
    return 0 if report['status']=='PASS' else 1

if __name__=='__main__':raise SystemExit(main())
