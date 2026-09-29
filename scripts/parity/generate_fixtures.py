#!/usr/bin/env python3
"""Generate public, synthetic parity test inputs; never read or modify user media.

Output is a new, dedicated directory. No upload, mount, SageTV API, or install
operation is performed. Encoded subtitle vectors are test inputs, NOT proof that
WebPlayer supports/rendered their codec. FFmpeg-version-dependent media hashes
are recorded per generation; reproducible vector hashes are stable.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
from typing import Any

PIN = "f1ba340e05fe18eaaba939253a933c3dfe6d6caf"
HAMMING = (0xA8,0x0B,0x26,0x85,0x92,0x31,0x1C,0xBF,0x40,0xE3,0xCE,0x6D,0x7A,0xD9,0xF4,0x57)
A53 = bytes.fromhex('000001b2474139340344fffc942efc9420fc4849fc942fff')


def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024), b''): h.update(chunk)
    return h.hexdigest()


def reverse8(value: int) -> int:
    return int(f'{value & 255:08b}'[::-1],2)


def crc32_mpeg(data: bytes) -> bytes:
    crc=0xffffffff
    for b in data:
        crc ^= b << 24
        for _ in range(8):
            crc=((crc << 1) ^ (0x04c11db7 if crc & 0x80000000 else 0)) & 0xffffffff
    return struct.pack('>I',crc)


def psi(table: int, body: bytes) -> bytes:
    size=len(body)+4
    section=bytes((table,0xb0|(size>>8),size&255))+body
    return b'\0'+section+crc32_mpeg(section)


def pts(value: int) -> bytes:
    value &= (1<<33)-1
    return bytes((0x21|((value>>29)&14),(value>>22)&255,((value>>14)&254)|1,(value>>7)&255,((value<<1)&254)|1))


def pes(value: int, payload: bytes) -> bytes:
    if payload.startswith(b'\x10'):
        # DVB Teletext uses a 45-byte PES header and a 184-byte multiple.
        # This makes the vector acceptable to the independent libzvbi decoder.
        pad=(-(len(payload)+45)) % 184
        if pad==1: pad+=184
        if pad: payload+=bytes((255,pad-2))+b'\xff'*(pad-2)
        body=b'\x80\x80\x24'+pts(value)+b'\xff'*31+payload
    else:
        body=b'\x80\x80\x05'+pts(value)+payload
    return b'\0\0\x01\xbd'+struct.pack('>H',len(body))+body


def packetize(pid: int, payload: bytes, continuity: int = 0, pcr90: int|None = None) -> bytes:
    output=bytearray()
    while payload:
        first=not output
        use_pcr=first and pcr90 is not None
        capacity=176 if use_pcr else 184
        part,payload=payload[:capacity],payload[capacity:]
        start=0x40 if first else 0
        head=bytes((0x47,start|(pid>>8),pid&255,0x10|(continuity&15)))
        if len(part)<184:
            pad=183-len(part)
            head=head[:3]+bytes((0x30|(continuity&15),))
            if use_pcr:
                clock=(((int(pcr90)&((1<<33)-1))<<15)|0x7e00).to_bytes(6,'big')
                adaptation=bytes((pad,0x10))+clock+b'\xff'*(pad-7)
            else:
                adaptation=bytes((pad,))+(b'\0'+b'\xff'*(pad-1) if pad else b'')
            output.extend(head+adaptation+part)
        else: output.extend(head+part)
        continuity += 1
    return bytes(output)


def transport(streams: list[tuple[int,bytes]], payloads: list[tuple[int,int,bytes]]) -> bytes:
    pmt_pid=0x100
    pat=psi(0,b'\0\x01\xc1\0\0'+struct.pack('>HH',1,0xe000|pmt_pid))
    body=b'\0\x01\xc1\0\0'+struct.pack('>H',0xe000|streams[0][0])+b'\xf0\0'
    for pid,descriptor in streams:
        body+=struct.pack('>BHH',6,0xe000|pid,0xf000|len(descriptor))+descriptor
    data=packetize(0,pat)+packetize(pmt_pid,psi(2,body))
    counters: dict[int,int]={}
    for pid,t,payload in payloads:
        chunk=packetize(pid,pes(t,payload),counters.get(pid,0),t)
        counters[pid]=counters.get(pid,0)+len(chunk)//188
        data+=chunk
    # Four sync points let the existing Vibe framing algorithm lock.
    return data+b''.join(packetize(0x1fff,b'\xff'*184,i) for i in range(8))


def teletext_unit(row: int,text: str='',erase: bool=False) -> bytes:
    addr=reverse8(row<<3) # magazine 8, page 888
    raw=bytearray((0xf5,0xe4,HAMMING[addr>>4],HAMMING[addr&15]))
    if row==0:
        # Header nibbles follow Vibe's pinned golden-vector bit ordering.
        nibbles=[1,1,0,1 if erase else 0,0,1,8,0]
        raw.extend(HAMMING[n] for n in nibbles)
        raw.extend(reverse8(0x20) for _ in range(32))
    else:
        for c in text[:40].ljust(40):
            value=ord(c)&127
            if value.bit_count()%2==0: value|=128
            raw.append(reverse8(value))
    assert len(raw)==44
    return b'\x03\x2c'+raw


def dvb_segment(kind: int,payload: bytes,page: int=1) -> bytes:
    return bytes((15,kind))+struct.pack('>HH',page,len(payload))+payload


def dvb_vector(depth: int=2) -> bytes:
    field={2:b'\x10\x55\0\xf0',4:b'\x11\x77\x77\0\xf0',8:b'\x12\1\1\1\1\0\0\xf0'}[depth]
    depth_id={2:1,4:2,8:3}[depth]
    display=dvb_segment(0x14,b'\0'+struct.pack('>HH',719,575))
    page=dvb_segment(0x10,b'\x05\x04\x01\0'+struct.pack('>HH',100,400))
    region=dvb_segment(0x11,bytes((1,8))+struct.pack('>HH',6,2)+bytes((32|(depth_id<<2),0,0,0))+struct.pack('>HHH',1,0,0))
    index=7 if depth==4 else 1
    flag={2:0x81,4:0x41,8:0x21}[depth]
    clut=dvb_segment(0x12,bytes((0,0,index,flag,255,128,128,0)))
    obj=dvb_segment(0x13,struct.pack('>HBHH',1,0,len(field),0)+field)
    return b'\x20\0'+display+page+region+clut+obj+dvb_segment(0x80,b'')+b'\xff'


def pgs_vector() -> bytes:
    def seg(t: int,kind: int,payload: bytes) -> bytes:
        return b'PG'+struct.pack('>IIBH',t,t,kind,len(payload))+payload
    base=struct.pack('>HHBHBBBB',720,576,0x10,0,0x80,0,0,1)
    comp=base+struct.pack('>HBBHH',1,0,0,100,400)
    rle=b'\1\1\1\1\0\0'*2
    obj=struct.pack('>HBB',1,0,0xc0)+(len(rle)+4).to_bytes(3,'big')+struct.pack('>HH',4,2)+rle
    clear=struct.pack('>HHBHBBBB',720,576,0x10,1,0,0,0,0)
    return seg(90000,0x16,comp)+seg(90000,0x14,bytes((0,0,1,255,128,128,255)))+seg(90000,0x15,obj)+seg(90000,0x80,b'')+seg(270000,0x16,clear)+seg(270000,0x80,b'')


def generate(out: Path, ffmpeg: str, ffprobe: str, vectors_only: bool=False) -> dict[str,Any]:
    if out.exists() and any(out.iterdir()):
        raise ValueError('Output directory must be new or empty; existing media is never overwritten.')
    out.mkdir(parents=True,exist_ok=True)
    records=[]
    def add(fid: str,name: str,data: bytes|None,kind: str,expect: dict[str,Any],boundary: str):
        path=out/name
        if data is not None: path.write_bytes(data)
        records.append(dict(id=fid,path=name,kind=kind,origin='generated synthetic fixture',sha256=sha256(path),bytes=path.stat().st_size,expected=expect,validation_boundary=boundary,allowed_operations=['read','copy to disposable test directory','seek/pause/replay in isolated tests'],redistributable=True))
    text='1\n00:00:00,500 --> 00:00:02,500\nUnicode: café, español, 日本語\n\n2\n00:00:01,500 --> 00:00:03,000\n<script>window.PARITY_INJECTED=true</script>\n\n3\n00:00:03,200 --> 00:00:04,200\nClear at 4.2 seconds.\n'
    add('F-TEXT-SRT','unicode-overlap.srt',text.encode(),'text',dict(cues=3,overlap=[1.5,2.5],script_execution=False),'Text and hostile markup input only; no current browser subtitle support implied.')
    vtt='WEBVTT\n\n00:00.500 --> 00:02.500 line:80% position:50% align:center\nFirst cue — Unicode ✓\n\n00:01.500 --> 00:03.000\n&lt;img src=x onerror=alert(1)&gt;\n'
    add('F-TEXT-VTT','unicode-position.vtt',vtt.encode(),'text',dict(cues=2,overlap=[1.5,2.5],script_execution=False),'Input validity and hashes only; P06 will test rendering.')
    descriptor=b'\x56\x05eng\x10\x88'
    payloads=[(0x12c,90000,b'\x10'+teletext_unit(0,erase=True)+teletext_unit(14,'P01 TELETEXT VECTOR')),(0x12c,180000,b'\x10'+teletext_unit(0,erase=True)+teletext_unit(14,'SECOND PAGE UPDATE')),(0x12c,270000,b'\x10'+teletext_unit(0,erase=True))]
    ttx=transport([(0x12c,descriptor)],payloads)
    for size in (188,192,204):
        raw=ttx if size==188 else b''.join((b'\0'*4+p if size==192 else p+b'\0'*16) for p in (ttx[i:i+188] for i in range(0,len(ttx),188)))
        add(f'F-TTX-{size}',f'teletext-{size}.ts',raw,'transport-parser-vector',dict(packet_size=size,pid=300,page=888,language='eng',cue_source_pts=[90000,180000,270000],texts=['P01 TELETEXT VECTOR','SECOND PAGE UPDATE',''],presentation_origin='P02 must map source PTS to media time explicitly'),'Synthetic subtitle-only TS: no video and no field or MSE playback claim.')
    for depth in (2,4,8):
        payload=dvb_vector(depth)
        desc=b'\x59\x08eng\x10\0\1\0\1'
        raw=transport([(0x150,desc)],[(0x150,90000,payload),(0x150,270000,b'\x20\0'+dvb_segment(0x10,b'\x05\x14')+dvb_segment(0x80,b'')+b'\xff')])
        add(f'F-DVB-{depth}',f'dvb-{depth}bpp.ts',raw,'transport-parser-vector',dict(pid=336,language='eng',composition_page=1,ancillary_page=1,bit_depth=depth,canvas=[720,576],rect=[100,400,6,2],opaque_rect=[100,400,4,2],rgba_sha256=hashlib.sha256((b'\xff'*16+b'\0'*8)*2).hexdigest(),clear_source_pts=270000),'Golden-input construction only: exact pixel/alpha decoder output must be independently verified in P04. Contains no Teletext descriptor.')
    add('F-PGS','white-rectangle.sup',pgs_vector(),'subtitle-parser-vector',dict(canvas=[720,576],rect=[100,400,4,2],show_source_pts=90000,clear_source_pts=270000,opaque_pixels=8),'PGS input construction only; P06 must verify decoder/render output and file embedding.')
    add('F-EMPTY','empty.ts',b'','negative',dict(startup_should_fail=True),'Expected-safe-failure input, never an A/V pass.')
    add('F-TRUNCATED','truncated.ts',ttx[:73],'negative',dict(incomplete_ts_header_or_packet=True,should_not_emit_cues=True),'Malformed containment input; never an A/V pass.')
    commands=[]
    def run(args: list[str],name: str,timeout: int=90):
        cmd=[ffmpeg,'-hide_banner','-v','error','-nostdin','-y']+args
        result=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout,check=False)
        (out/(name+'.log')).write_bytes(result.stdout)
        commands.append(dict(id=name,arguments=args,exit_code=result.returncode))
        if result.returncode: raise RuntimeError(f'FFmpeg {name} failed; see local log')
    if not vectors_only:
        run(['-f','lavfi','-i','testsrc2=size=320x180:rate=30','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','8','-c:v','libx264','-preset','ultrafast','-threads','1','-pix_fmt','yuv420p','-g','30','-bf','0','-c:a','aac','-b:a','96k','-f','mpegts',str(out/'av-h264-aac.ts')],'h264')
        add('F-MSE-TS','av-h264-aac.ts',None,'av',dict(video='h264',audio='aac',duration_seconds=8),'Real FFmpeg output; browser MSE acceptance is a separate blocked gate.')
        (out/'hls').mkdir()
        run(['-i',str(out/'av-h264-aac.ts'),'-c','copy','-hls_time','1','-hls_list_size','0','-hls_playlist_type','vod','-hls_segment_filename',str(out/'hls/segment%03d.ts'),str(out/'hls/index.m3u8')],'hls')
        add('F-MSE-HLS','hls/index.m3u8',None,'playlist',dict(video='h264',audio='aac',endlist=True),'Actual remuxed HLS; no real hls.js MSE pass is claimed.')
        for segment in sorted((out/'hls').glob('*.ts')):
            add('F-MSE-HLS-'+segment.stem,segment.relative_to(out).as_posix(),None,'av-segment',{},'Constituent of F-MSE-HLS.')
        run(['-f','lavfi','-i','testsrc2=size=160x96:rate=15','-f','lavfi','-i','sine=frequency=220:sample_rate=48000','-t','240','-c:v','libx264','-preset','ultrafast','-threads','1','-crf','34','-g','15','-bf','0','-c:a','aac','-b:a','48k','-f','mpegts',str(out/'long-reserve-240s.ts')],'long-reserve',180)
        add('F-RESERVE','long-reserve-240s.ts',None,'av',dict(duration_seconds=240,exceeds_default_reserve_seconds=180),'240-second generated input, not a three-hour soak or sustained browser proof.')
        run(['-f','lavfi','-i','testsrc2=size=320x180:rate=30','-t','6','-c:v','mpeg2video','-g','15','-bf','0','-f','mpeg2video',str(out/'temporary.m2v')],'mpeg2')
        es=(out/'temporary.m2v').read_bytes(); annotated=bytearray(); picture=False; count=0
        for i,b in enumerate(es):
            if es[i:i+3]==b'\0\0\1' and i+3<len(es):
                code=es[i+3]
                if code==0: picture=True
                elif 1<=code<=0xaf and picture: annotated.extend(A53);picture=False;count+=1
            annotated.append(b)
        (out/'temporary-cc.m2v').write_bytes(annotated)
        run(['-fflags','+genpts','-r','30','-i',str(out/'temporary-cc.m2v'),'-f','lavfi','-i','sine=frequency=220:sample_rate=48000','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-f','lavfi','-i','sine=frequency=880:sample_rate=48000','-t','6','-map','0:v','-map','1:a','-map','2:a','-map','3:a','-c:v','copy','-c:a','ac3','-metadata:s:a:0','language=nar','-metadata:s:a:0','title=Audio description','-metadata:s:a:1','language=eng','-metadata:s:a:2','language=spa','-f','mpegts',str(out/'mpeg2-cc-multiaudio.ts')],'multiaudio')
        add('F-CEA-AUDIO','mpeg2-cc-multiaudio.ts',None,'av',dict(video='mpeg2video',audio='ac3',languages=['nar','eng','spa'],source_a53_insertions=count,cea608_text='HI'),'Synthetic A/53 pattern adapted from existing StreamingRuntimeSmoke; no real broadcast language/CEA metadata claim.')
        (out/'temporary.m2v').unlink();(out/'temporary-cc.m2v').unlink()
        for record in records:
            if record['kind'] in ('av','playlist'):
                probe=subprocess.run([ffprobe,'-v','error','-show_streams','-show_format','-of','json',str(out/record['path'])],capture_output=True,timeout=30,check=True)
                info=json.loads(probe.stdout); info.get('format',{}).pop('filename',None)
                record['probe']=info
        av=next(x for x in records if x['id']=='F-CEA-AUDIO')
        languages=[s.get('tags',{}).get('language') for s in av['probe']['streams'] if s.get('codec_type')=='audio']
        if languages!=['nar','eng','spa']: raise RuntimeError('Unexpected generated audio identities')
        long=next(x for x in records if x['id']=='F-RESERVE')
        if float(long['probe']['format']['duration'])<239: raise RuntimeError('Reserve fixture too short')
    registry=dict(schema_version=1,reference_commit=PIN,synthetic_only=True,source_media_never_read=True,decoder_field_acceptance=False,ffmpeg_version=None if vectors_only else subprocess.check_output([ffmpeg,'-version'],text=True).splitlines()[0],commands=commands,fixtures=records)
    # Strip ephemeral absolute output paths from the portable command record.
    for command in commands: command['arguments']=[a.replace(str(out),'<OUTPUT>') for a in command['arguments']]
    (out/'fixtures.generated.json').write_text(json.dumps(registry,indent=2,ensure_ascii=False)+'\n')
    return registry


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--vectors-only',action='store_true')
    parser.add_argument('--ffmpeg',default=shutil.which('ffmpeg') or 'ffmpeg')
    parser.add_argument('--ffprobe',default=shutil.which('ffprobe') or 'ffprobe')
    args=parser.parse_args()
    try:
        data=generate(args.output.resolve(),args.ffmpeg,args.ffprobe,args.vectors_only)
        print(json.dumps(dict(status='GENERATED',fixtures=len(data['fixtures']),field_acceptance=False)))
        return 0
    except (OSError,ValueError,RuntimeError,subprocess.SubprocessError) as exc:
        print(f'Fixture generation failed: {exc}')
        return 1

if __name__=='__main__': raise SystemExit(main())
