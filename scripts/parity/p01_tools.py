#!/usr/bin/env python3
"""Read-only baseline, identity and source-lock tools for parity phase P01.

No tool installs a WAR, changes SageTV, clears settings, mounts a disc or writes
GitHub. A baseline build runs in an isolated copy. An unavailable external gate
returns 77/BLOCKED, not PASS. Source delta detection never advances the pin.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
from typing import Any
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PIN='f1ba340e05fe18eaaba939253a933c3dfe6d6caf'
WAR_SHA='069ec4cd1b080f896311e98a63a6c9168bdc9091039f4326e1506f54f965742d'
RUNTIME_PREFIXES=('src/','dist/','third_party/','tests/')


def digest(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''): h.update(block)
    return h.hexdigest()


def safe_relative(value: str) -> bool:
    p=PurePosixPath(value)
    return bool(value) and not p.is_absolute() and '..' not in p.parts and '\\' not in value and not re.match(r'^[A-Za-z]:',value) and not any(ord(c)<32 for c in value)


def manifest_errors(root: Path) -> list[str]:
    errors=[]; seen=set()
    for line in (root/'PROJECT_MANIFEST.sha256').read_text().splitlines():
        if not line.strip(): continue
        if '  ' not in line: errors.append('malformed manifest line'); continue
        expected,name=line.split('  ',1)
        if not re.fullmatch('[0-9a-f]{64}',expected) or not safe_relative(name): errors.append('unsafe or malformed manifest entry'); continue
        if name in seen: errors.append('duplicate entry: '+name); continue
        seen.add(name); path=root/name
        if path.is_symlink() or any(p.is_symlink() for p in path.parents if p!=root.parent): errors.append('symlink entry: '+name)
        elif not path.is_file(): errors.append('missing: '+name)
        elif digest(path)!=expected: errors.append('hash mismatch: '+name)
    return errors


def inventory(root: Path) -> dict[str,str]:
    return {p.relative_to(root).as_posix():digest(p) for p in sorted(root.rglob('*'))
            if p.is_file() and not p.is_symlink() and '.git' not in p.relative_to(root).parts
            and 'build' not in p.relative_to(root).parts and '__pycache__' not in p.relative_to(root).parts}


def parse_changelog(text: str) -> list[dict[str,Any]]:
    """Enumerate literal top-level bullets; deliberately does not auto-classify fixes."""
    lines=text.splitlines(); heading=''; entries=[]; active=None
    for n,line in enumerate(lines,1):
        if re.match(r'^#{1,6} ',line):
            if active is not None: active['end_line']=n-1;active=None
            heading=line.lstrip('# ').strip()
        if line.startswith('- '):
            if active is not None: active['end_line']=n-1
            active=dict(start_line=n,end_line=len(lines),section=heading,lines=[line[2:]])
            entries.append(active)
        elif active is not None: active['lines'].append(line)
    for e in entries:
        e['text']='\n'.join(e.pop('lines')).strip()
        e['text_sha256']=hashlib.sha256(e['text'].encode()).hexdigest()
    return entries


def source_delta(observed: str,expected: str=PIN) -> dict[str,Any]:
    if not re.fullmatch('[a-f0-9]{40}',observed): raise ValueError('A complete lowercase 40-character commit SHA is required')
    return dict(status='UNCHANGED' if observed==expected else 'REVIEW_REQUIRED',reference_commit=expected,observed_commit=observed,pin_modified=False,action='Continue frozen source scope' if observed==expected else 'Append newly reviewed fixes with new IDs; do not silently move the reference lock')


def preflight(asset_root: Path|None, jar: Path|None, stv: Path|None) -> dict[str,Any]:
    assets=[]
    for filename,version in [('mpegts.min.js','1.8.0'),('hls.min.js','1.7.3')]:
        matches=[] if asset_root is None or not asset_root.is_dir() else list(asset_root.rglob(filename))
        matches=[p for p in matches if p.is_file() and not p.is_symlink()]
        assets.append(dict(name=filename,expected_version=version,available=bool(matches),sha256=digest(matches[0]) if len(matches)==1 else None,ambiguous=len(matches)>1))
    # File presence does NOT prove vendor version, actual MSE playback, or server deployment.
    mse=dict(test_id='MSE-01',status='NOT_RUN' if all(a['available'] and not a['ambiguous'] for a in assets) else 'BLOCKED',assets=assets,actual_mse_exercised=False,reason='Run the actual plugin/vendor/browser acceptance path after asset identity and deployment are verified.')
    server=dict(test_id='SERVER-ID',status='BLOCKED',running_server_verified=False,core_sha256=None,stv_sha256=None,server_kind='UNVERIFIED',extensions_authorized=False,reason='Exact running stock Core and STV identity have not been established.')
    if jar is not None and jar.is_file():
        if not zipfile.is_zipfile(jar): raise ValueError('Provided Core file is not a JAR/ZIP')
        server['core_sha256']=digest(jar)
    if stv is not None and stv.is_file(): server['stv_sha256']=digest(stv)
    if server['core_sha256'] and server['stv_sha256']:
        server['status']='REFERENCE_FILES_ONLY'
        server['reason']='Hashes identify the provided files only; independently confirm they are the running stock server/STV. No connection or deployment was performed.'
    return dict(schema_version=1,observed_at=datetime.now(timezone.utc).isoformat(),mse=mse,server=server)


def run_baseline(root: Path, output: Path, browser: bool=True, timeout: int=900) -> dict[str,Any]:
    if output.resolve().is_relative_to(root.resolve()):
        raise ValueError('Evidence output must be outside the source root')
    errors=manifest_errors(root)
    if errors: raise ValueError('Baseline integrity failed: '+ '; '.join(errors[:5]))
    if output.exists() and any(output.iterdir()): raise ValueError('Evidence output must be new or empty')
    output.mkdir(parents=True,exist_ok=True)
    before=inventory(root); results=[]
    with tempfile.TemporaryDirectory(prefix='webplayer-p01-build-') as temp:
        copy=Path(temp)/root.name
        shutil.copytree(root,copy,ignore=shutil.ignore_patterns('.git','build','__pycache__'))
        env=os.environ.copy();env['RUN_BROWSER_TESTS']='1' if browser else '0'
        for name,args in [('build',['bash','scripts/build-local.sh']),('validation',['bash','scripts/validate.sh'])]:
            started=datetime.now(timezone.utc).isoformat(); log=output/(name+'.log')
            with log.open('wb') as stream:
                proc=subprocess.Popen(args,cwd=copy,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
                try: code=proc.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    # Cancel the entire build/test process group, not just its shell.
                    if os.name=='posix':
                        import signal
                        os.killpg(proc.pid,signal.SIGTERM)
                    else: proc.terminate()
                    try: proc.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        if os.name=='posix': os.killpg(proc.pid,signal.SIGKILL)
                        else: proc.kill()
                        proc.wait()
                    code=124
            results.append(dict(name=name,command=args,started_at=started,finished_at=datetime.now(timezone.utc).isoformat(),exit_code=code,log=log.name,log_sha256=digest(log)))
            if code: break
    after=inventory(root)
    report=dict(schema_version=1,status='PASS' if len(results)==2 and all(x['exit_code']==0 for x in results) and before==after else 'FAIL',runtime_version='3.2.4',war_sha256=digest(root/'dist/SageTVWebPlayer.war'),runtime_input_unchanged=before==after,browser_suite_requested=browser,results=results,real_vendor_mse=False,real_stock_server=False)
    (output/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    return report


def check_ledger(root: Path) -> list[str]:
    data=json.loads((root/'docs/parity/UPSTREAM_FIX_LEDGER.json').read_text())
    index=json.loads((root/'docs/parity/TASK_INDEX.json').read_text())
    tasks={t['id'] for p in index['phases'] for t in p['tasks']};seen=set();errors=[]
    for entry in data['entries']:
        if entry['id'] in seen: errors.append('duplicate fix '+entry['id'])
        seen.add(entry['id'])
        if entry['category'] not in ['required','retained','adaptation','optional_core','upstream_open','not_applicable','superseded']: errors.append('unclassified '+entry['id'])
        if not entry.get('reason'): errors.append('missing disposition reason '+entry['id'])
        if not entry.get('target_task_ids') or not set(entry['target_task_ids'])<=tasks: errors.append('invalid target '+entry['id'])
        if PIN not in entry['upstream_source']: errors.append('unpinned source '+entry['id'])
        if entry['implementation_status'] not in ['NOT_PORTED','PARTIAL_BASELINE','ADAPTATION_REQUIRED_NOT_VERIFIED','RETAINED_BASELINE','REFERENCE_ONLY','UPSTREAM_GATE_OPEN']: errors.append('unexpected implementation state '+entry['id'])
    checklist=(root/'TASKS.md').read_text()
    # Regex returns checkbox first: preserve duplicates/identity with a second map.
    pairs=re.findall(r'^- \[([ x])\] \*\*(WP\d{2}-\d{3})',checklist,re.M)
    by_id={task:box for box,task in pairs}
    if len(by_id)!=len(pairs) or set(by_id)!=tasks: errors.append('Task IDs/checklist mirror mismatch')
    for phase in index['phases']:
        for task in phase['tasks']:
            if (by_id.get(task['id'])=='x') != (task['status']=='DONE'):
                errors.append('Task checkbox/status mismatch: '+task['id'])
            if task['status']=='DONE':
                for path in task.get('evidence',[]):
                    if not safe_relative(path) or not (root/path).is_file(): errors.append('Missing task evidence: '+task['id'])
    intake=next(t for phase in index['phases'] for t in phase['tasks'] if t['id']=='WP01-001')
    if intake['status']=='DONE' and not data.get('exhaustive_intake_complete'):
        errors.append('Exhaustive intake marked done without completed reconciliation')
    return errors


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='action',required=True)
    b=sub.add_parser('baseline');b.add_argument('--root',type=Path,default=ROOT);b.add_argument('--output',required=True,type=Path);b.add_argument('--without-browser',action='store_true');b.add_argument('--timeout',type=int,default=900)
    p=sub.add_parser('preflight');p.add_argument('--assets',type=Path);p.add_argument('--core-jar',type=Path);p.add_argument('--stv',type=Path);p.add_argument('--output',type=Path)
    d=sub.add_parser('delta');d.add_argument('--observed-head',required=True);d.add_argument('--output',type=Path)
    v=sub.add_parser('verify');v.add_argument('--root',type=Path,default=ROOT)
    i=sub.add_parser('intake');i.add_argument('--changelog',required=True,type=Path);i.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    try:
        if args.action=='baseline': result=run_baseline(args.root.resolve(),args.output.resolve(),not args.without_browser,args.timeout);code=0 if result['status']=='PASS' else 1
        elif args.action=='preflight': result=preflight(args.assets,args.core_jar,args.stv);code=77
        elif args.action=='delta': result=source_delta(args.observed_head);code=0 if result['status']=='UNCHANGED' else 2
        elif args.action=='intake':
            raw=args.changelog.read_bytes();gitsha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
            if gitsha!='650e8a65d1407fa5a6529240078b41ada2f91afe': raise ValueError('Changelog does not match the frozen reference blob')
            result=dict(reference_commit=PIN,blob_sha=gitsha,status='REQUIRES_PER_ENTRY_REVIEW',entries=parse_changelog(raw.decode('utf-8')));code=2
        else:
            errors=manifest_errors(args.root)+check_ledger(args.root)
            result=dict(status='PASS' if not errors else 'FAIL',errors=errors);code=0 if not errors else 1
        text=json.dumps(result,indent=2)
        if args.action != 'baseline' and hasattr(args,'output') and args.output:
            if args.output.exists(): raise ValueError('Refusing to overwrite an evidence report')
            args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text+'\n')
        print(text);return code
    except (OSError,ValueError,KeyError,subprocess.SubprocessError) as exc:
        print(json.dumps(dict(status='FAIL',error=str(exc))));return 1

if __name__=='__main__': raise SystemExit(main())
