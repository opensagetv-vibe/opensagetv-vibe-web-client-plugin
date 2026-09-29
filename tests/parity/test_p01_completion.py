import hashlib, json, pathlib, unittest

ROOT=pathlib.Path(__file__).resolve().parents[2]

class P01CompletionTest(unittest.TestCase):
    def test_literal_and_test_census(self):
        lit=json.loads((ROOT/'docs/parity/p01/LITERAL_CHANGELOG_RECONCILIATION.json').read_text())
        self.assertEqual(1066,lit['literal_top_level_bullets'])
        self.assertEqual(0,lit['unclassified_literal_bullets'])
        tests=json.loads((ROOT/'docs/parity/p01/UPSTREAM_RELEVANT_TESTS.json').read_text())
        self.assertEqual(162,tests['all_test_files_in_tree'])
        self.assertEqual(102,tests['relevant_test_files'])
        self.assertEqual(0,tests['unclassified_relevant_test_files'])
    def test_task_index_and_external_boundary(self):
        idx=json.loads((ROOT/'docs/parity/TASK_INDEX.json').read_text())
        p1=next(p for p in idx['phases'] if p['id']=='P01')
        by={t['id']:t for t in p1['tasks']}
        self.assertEqual('DONE',by['WP01-001']['status'])
        self.assertEqual('EXTERNAL_ACCEPTANCE_PENDING',by['WP01-004']['status'])
        self.assertEqual('DONE',by['WP01-006']['status'])
        self.assertEqual('COMPLETE_WITH_EXTERNAL_VENDOR_ACCEPTANCE_PENDING',p1['status'])
    def test_runtime_war_unchanged(self):
        war=ROOT/'dist/SageTVWebPlayer.war'
        self.assertEqual('069ec4cd1b080f896311e98a63a6c9168bdc9091039f4326e1506f54f965742d',hashlib.sha256(war.read_bytes()).hexdigest())
    def test_server_identity_redacted(self):
        d=json.loads((ROOT/'docs/parity/p01/SERVER_IDENTITY.json').read_text())
        self.assertEqual('9.2.10.1054',d['observed_server']['sagetv_version'])
        self.assertTrue(d['observed_server']['active_stv'].endswith('/STVs/SageTV7/SageTV7.xml'))
        self.assertFalse(d['sensitive_data_copied'])

if __name__=='__main__': unittest.main()
