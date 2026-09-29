"""P01 tooling-only tests. These do not exercise the production vendor MSE path."""
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
def load(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts/parity'/f'{name}.py')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod);return mod
T=load('p01_tools');G=load('generate_fixtures')

class P01ToolsTests(unittest.TestCase):
    def test_manifest_rejects_path_traversal(self):
        for value in ['../outside','/etc/passwd','a/../../x','C:/x','a\\x','line\nname']:
            self.assertFalse(T.safe_relative(value),value)
    def test_baseline_rejects_output_inside_source(self):
        with self.assertRaisesRegex(ValueError, 'outside the source root'):
            T.run_baseline(ROOT,ROOT/'should-not-be-created')
        self.assertFalse((ROOT/'should-not-be-created').exists())
    def test_manifest_accepts_project_relative_path(self):
        self.assertTrue(T.safe_relative('src/main/webapp/js/miniclient.js'))
    def test_missing_vendor_is_blocked(self):
        p=T.preflight(None,None,None)
        self.assertEqual(p['mse']['status'],'BLOCKED');self.assertFalse(p['mse']['actual_mse_exercised'])
    def test_present_vendor_is_not_a_playback_pass(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d)
            for name in ['mpegts.min.js','hls.min.js']:(r/name).write_text('unverified test content')
            p=T.preflight(r,None,None)
            self.assertEqual(p['mse']['status'],'NOT_RUN');self.assertFalse(p['mse']['actual_mse_exercised'])
    def test_ambiguous_vendor_remains_blocked(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d)
            for sub in ['one','two']:
                (r/sub).mkdir();(r/sub/'mpegts.min.js').write_text('x')
            (r/'hls.min.js').write_text('x')
            self.assertEqual(T.preflight(r,None,None)['mse']['status'],'BLOCKED')
    def test_missing_server_never_implies_stock(self):
        p=T.preflight(None,None,None)['server']
        self.assertEqual(p['status'],'BLOCKED');self.assertEqual(p['server_kind'],'UNVERIFIED')
        self.assertFalse(p['running_server_verified']);self.assertFalse(p['extensions_authorized'])
    def test_matching_upstream_is_unchanged(self):
        self.assertEqual(T.source_delta(T.PIN)['status'],'UNCHANGED')
    def test_new_head_does_not_advance_pin(self):
        result=T.source_delta('1'*40)
        self.assertEqual(result['status'],'REVIEW_REQUIRED');self.assertEqual(result['reference_commit'],T.PIN);self.assertFalse(result['pin_modified'])
    def test_short_or_unsafe_head_rejected(self):
        for value in ['main','f1ba340','../../head','F'*40,'']:
            with self.assertRaises(ValueError):T.source_delta(value)
    def test_changelog_intake_keeps_top_level_and_continuations(self):
        e=T.parse_changelog('# Changes\n\n## v1\n- Fixed A\n  continued\n  - subfix\n\n- Fixed B\n\n## v0\n- Older C\n')
        self.assertEqual(len(e),3);self.assertIn('subfix',e[0]['text']);self.assertEqual(e[2]['section'],'v0')
        self.assertEqual(e[0]['start_line'],4);self.assertEqual(e[0]['end_line'],7)
    def test_review_ledger_has_no_dangling_task_ids(self):
        self.assertEqual(T.check_ledger(ROOT),[])
    def test_completed_intake_has_saved_literal_and_test_reconciliation(self):
        d=json.loads((ROOT/'docs/parity/UPSTREAM_FIX_LEDGER.json').read_text())
        self.assertTrue(d['exhaustive_intake_complete'])
        self.assertIsNone(d['remaining_intake_gate'])
        literal=json.loads((ROOT/'docs/parity/p01/LITERAL_CHANGELOG_RECONCILIATION.json').read_text())
        tests=json.loads((ROOT/'docs/parity/p01/UPSTREAM_RELEVANT_TESTS.json').read_text())
        self.assertEqual(0,literal['unclassified_literal_bullets'])
        self.assertEqual(0,tests['unclassified_relevant_test_files'])
    def test_fixture_generation_never_overwrites_media(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'keep.ts';p.write_bytes(b'user data')
            with self.assertRaises(ValueError):G.generate(Path(d),'ffmpeg','ffprobe',True)
            self.assertEqual(p.read_bytes(),b'user data')
    def test_crc_roundtrip(self):
        prefix=b'\0\xb0\r\0\1\xc1\0\0\0\1\xe1\0'
        self.assertEqual(G.crc32_mpeg(prefix+G.crc32_mpeg(prefix)),b'\0'*4)
    def test_pts_wrap_and_markers(self):
        for n in [0,90000,(1<<33)-1,1<<33]:
            b=G.pts(n);decoded=((b[0]&14)<<29)|(b[1]<<22)|((b[2]&254)<<14)|(b[3]<<7)|((b[4]&254)>>1)
            self.assertEqual(decoded,n% (1<<33));self.assertTrue(b[0]&1 and b[2]&1 and b[4]&1)
    def test_packetization_has_188_byte_alignment(self):
        for n in [1,176,184,185,4096]:
            b=G.packetize(300,b'x'*n,pcr90=90000)
            self.assertEqual(len(b)%188,0);self.assertTrue(all(b[i]==0x47 for i in range(0,len(b),188)))
    def test_teletext_has_fixed_header_and_valid_pes_multiple(self):
        b=G.pes(90000,b'\x10'+G.teletext_unit(0,erase=True)+G.teletext_unit(14,'GOLDEN'))
        self.assertEqual(b[8],36);self.assertEqual(len(b)%184,0)
        self.assertEqual(int.from_bytes(b[4:6],'big')+6,len(b))
    def test_teletext_text_odd_parity(self):
        u=G.teletext_unit(14,'Text 123')
        self.assertEqual(len(u),46)
        for b in u[6:]: self.assertEqual(G.reverse8(b).bit_count()%2,1)
    def test_dvb_depth_vectors_and_clear_not_teletext(self):
        for depth in [2,4,8]:
            b=G.dvb_vector(depth)
            self.assertTrue(b.startswith(b'\x20\0'));self.assertIn(b'\x0f\x80\0\1\0\0',b)
    def test_pgs_has_timestamped_display_and_clear(self):
        b=G.pgs_vector();self.assertTrue(b.startswith(b'PG'))
        cursor=0;types=[];stamps=[]
        while cursor<len(b):
            self.assertEqual(b[cursor:cursor+2],b'PG');stamps.append(int.from_bytes(b[cursor+2:cursor+6],'big'))
            types.append(b[cursor+10]);cursor+=13+int.from_bytes(b[cursor+11:cursor+13],'big')
        self.assertEqual(cursor,len(b));self.assertEqual(types,[0x16,0x14,0x15,0x80,0x16,0x80]);self.assertEqual(stamps[-1],270000)
    def test_generated_registry_does_not_claim_field_acceptance(self):
        with tempfile.TemporaryDirectory() as d:
            reg=G.generate(Path(d),'ffmpeg','ffprobe',True)
            self.assertFalse(reg['decoder_field_acceptance']);self.assertTrue(reg['source_media_never_read'])
            for f in reg['fixtures']:
                self.assertEqual(G.sha256(Path(d)/f['path']),f['sha256']);self.assertTrue(f['validation_boundary'])

if __name__=='__main__':unittest.main(verbosity=2)
