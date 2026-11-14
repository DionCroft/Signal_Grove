"""Reject ambiguous runtime identity, source drift and replayed output roots."""
from contextlib import ExitStack
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('runtime_stamp', Path(__file__).resolve().parents[1] / 'hermes_runtime_stamp.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class RuntimeStampTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='sg-runtime-stamp-')
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.base = self.root / 'reviewed'
        self.base.mkdir()
        self.output = self.root / 'derived'
        self.evidence = self.root / 'evidence'
        self.source_map = self.root / 'map.json'
        self.store = self.root / 'tools'
        self.store.mkdir()
        self.identity = dict(schemaVersion=2, commit=m.pinned.BASE_COMMIT, dirty=True,
                             source='local', updateMechanism='external', runtimeDir='../tools',
                             builtAt='original', branch='reviewed-custom', baseVersion='0.23.1')
        self.stamp = (json.dumps(self.identity, indent=2) + '\n').encode()
        source = {'install-stamp.json': self.stamp, 'custom.py': b'custom bytes\n',
                  'run.sh': b'#!/bin/sh\nexit 0\n'}
        for name, raw in source.items():
            path = self.base / name
            path.write_bytes(raw)
            if os.name == 'posix':
                path.chmod(0o755 if name == 'run.sh' else 0o644)
        self.expected = {n: m.snapshot.digest(raw) for n, raw in source.items()}
        self.source_map.write_text(json.dumps(self.expected), encoding='utf8')
        stack = ExitStack()
        self.addCleanup(stack.close)
        stack.enter_context(mock.patch.object(m, 'BASE_MAP', (len(source), m.snapshot.digest(m.snapshot.canonical(self.expected)))))
        stack.enter_context(mock.patch.object(m, 'BASE_STAMP', m.snapshot.digest(self.stamp)))
        stack.enter_context(mock.patch.object(m.snapshot, 'EXECUTABLES', (1, m.snapshot.digest(b'run.sh\n'))))

    def prepare(self):
        return m.prepare(self.base, self.output, self.evidence, self.source_map)

    def test_only_runtime_location_changes_under_restrictive_umask(self):
        old = os.umask(0o077) if os.name == 'posix' else None
        try:
            self.prepare()
        finally:
            if old is not None:
                os.umask(old)
        result = m.verify(self.base, self.output, self.source_map)
        actual = json.loads((self.output / m.STAMP).read_bytes())
        self.assertEqual(actual, dict(self.identity, runtimeDir=str(self.store)))
        self.assertEqual((self.base / m.STAMP).read_bytes(), self.stamp)
        self.assertEqual((self.output / 'custom.py').read_bytes(), b'custom bytes\n')
        self.assertFalse(result['sdk_executed'] or result['deployed'] or result['identity_fields_changed'])

    def test_unknown_whole_source_map_refuses_before_writes(self):
        self.source_map.write_text(json.dumps(dict(self.expected, other='0'*64)), encoding='utf8')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists() or self.evidence.exists())

    def test_duplicate_map_key_is_rejected(self):
        text=self.source_map.read_text(encoding='utf8')
        self.source_map.write_text(text[:-1]+', "custom.py": "'+self.expected['custom.py']+'"}',encoding='utf8')
        with self.assertRaises(ValueError):
            self.prepare()

    def test_source_and_empty_shadow_namespace_drift_are_rejected(self):
        (self.base/'shadow').mkdir()
        with self.assertRaises(ValueError):
            self.prepare()
        (self.base/'shadow').rmdir()
        (self.base/'custom.py').write_bytes(b'new owner work\n')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_existing_partial_source_or_evidence_cannot_be_replayed(self):
        for target in (self.output,self.evidence):
            target.mkdir()
            sentinel=target/'uncertain';sentinel.write_bytes(b'keep')
            with self.assertRaises(ValueError):
                self.prepare()
            self.assertEqual(sentinel.read_bytes(),b'keep')
            sentinel.unlink();target.rmdir()

    def test_nested_evidence_and_non_sibling_source_are_rejected(self):
        with self.assertRaises(ValueError):
            m.prepare(self.base,self.output,self.base/'evidence',self.source_map)
        other=self.root/'different';other.mkdir()
        with self.assertRaises(ValueError):
            m.prepare(self.base,other/'derived',self.evidence,self.source_map)

    def test_unknown_original_stamp_bytes_are_refused(self):
        with self.assertRaises(ValueError):
            m.bind_stamp(self.stamp+b' ',self.store)

    def test_untruthful_identity_or_changed_store_input_is_refused(self):
        for key,value in (('dirty',False),('source','ci'),('updateMechanism','self'),('runtimeDir','elsewhere'),('commit','0'*40)):
            raw=(json.dumps(dict(self.identity,**{key:value}),indent=2)+'\n').encode()
            with mock.patch.object(m,'BASE_STAMP',m.snapshot.digest(raw)),self.assertRaises(ValueError):
                m.bind_stamp(raw,self.store)

    def test_nonexistent_or_non_tools_store_is_refused(self):
        for store in (self.root/'absent',self.root/'different'):
            if store.name=='different':store.mkdir()
            with self.assertRaises(ValueError):
                m.bind_stamp(self.stamp,store)

    def test_symlinked_store_is_not_an_alias_for_the_reviewed_store(self):
        if os.name=='posix':
            other=self.root/'other';other.mkdir()
            self.store.rmdir();self.store.symlink_to(other,target_is_directory=True)
            with self.assertRaises(ValueError):
                m.bind_stamp(self.stamp,self.store)
        else:
            with self.assertRaises(ValueError):
                m.bind_stamp(self.stamp,Path('relative/tools'))

    def test_metadata_copy_cannot_hide_a_runtime_code_change(self):
        self.prepare();(self.output/'custom.py').write_bytes(b'changed\n')
        with self.assertRaises(ValueError):
            m.verify(self.base,self.output,self.source_map)

    def test_store_binding_cannot_hide_changed_identity_or_a_relative_path(self):
        self.prepare();path=self.output/m.STAMP
        for patch in ({'dirty':False},{'runtimeDir':'../tools'}):
            path.write_text(json.dumps({**self.identity,'runtimeDir':str(self.store),**patch}),encoding='utf8')
            with self.assertRaises(ValueError):
                m.verify(self.base,self.output,self.source_map)


if __name__=='__main__':
    unittest.main()
