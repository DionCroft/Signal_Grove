"""Runtime caches cannot hide source drift, executable additions or altered code."""
import importlib.util
import marshal
import os
from pathlib import Path
import struct
import sys
import tempfile
import types
import unittest

spec = importlib.util.spec_from_file_location('runtime_inventory', Path(__file__).resolve().parents[1] / 'hermes_runtime_inventory.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class RuntimeInventoryTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='sg-runtime-inventory-')
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.source = self.root / 'sample.py'
        self.raw = b'def outer(value):\n    def inner():\n        return value + 1\n    return inner\n'
        self.source.write_bytes(self.raw)
        self.expected = {'sample.py': m.snapshot.digest(self.raw)}
        self.modes = {'sample.py': self.source.stat().st_mode & 0o7777}
        self.cache = self.root / '__pycache__' / ('sample.' + sys.implementation.cache_tag + '.pyc')

    def code(self):
        return compile(self.raw, str(self.source), 'exec', dont_inherit=True, optimize=0)

    def write_cache(self, *, code=None, flags=0, mtime=None, size=None, tail=b''):
        info = self.source.stat()
        raw = importlib.util.MAGIC_NUMBER + struct.pack('<III', flags,
                (int(info.st_mtime) & 0xffffffff) if mtime is None else mtime,
                info.st_size if size is None else size) + marshal.dumps(self.code() if code is None else code) + tail
        self.cache.parent.mkdir(exist_ok=True)
        self.cache.write_bytes(raw)
        if os.name == 'posix':
            self.cache.chmod(0o600)
        return raw

    def inspect(self):
        return m.inspect_runtime(self.root, self.expected, self.modes)

    def test_source_only_has_empty_output_inventory(self):
        self.assertEqual(self.inspect()['generated'], {})

    def test_exact_nested_native_bytecode_is_separate_from_source(self):
        raw = self.write_cache()
        result = self.inspect()
        self.assertEqual(result['source_files'], 1)
        self.assertEqual(result['generated_bytes'], len(raw))
        self.assertEqual(next(iter(result['generated'].values()))['source'], 'sample.py')
        self.assertFalse(result['cached_code_executed_by_verifier'])

    def test_changed_source_cannot_be_its_own_cache_oracle(self):
        self.source.write_bytes(b'changed = True\n')
        self.write_cache(code=compile(self.source.read_bytes(), str(self.source), 'exec'))
        with self.assertRaises(ValueError):
            self.inspect()

    def test_removed_source_is_rejected(self):
        self.source.unlink()
        with self.assertRaises(ValueError):
            self.inspect()

    def test_additional_python_module_is_rejected(self):
        (self.root / 'shadow.py').write_bytes(b'x=1\n')
        with self.assertRaises(ValueError):
            self.inspect()

    def test_empty_shadow_directory_is_rejected(self):
        (self.root / 'shadow').mkdir()
        with self.assertRaises(ValueError):
            self.inspect()

    def test_forged_code_with_matching_timestamp_is_rejected(self):
        self.write_cache(code=compile(b'raise RuntimeError("must not execute")', str(self.source), 'exec'))
        with self.assertRaises(ValueError):
            self.inspect()

    def test_nested_code_filename_mismatch_is_rejected(self):
        def change(code):
            return code.replace(co_consts=tuple(change(item) if isinstance(item, types.CodeType) else item
                                                for item in code.co_consts), co_filename='/different/source.py')
        original = self.code()
        changed = original.replace(co_consts=tuple(change(item) if isinstance(item, types.CodeType) else item
                                                   for item in original.co_consts))
        self.write_cache(code=changed)
        with self.assertRaises(ValueError):
            self.inspect()

    def test_line_table_mismatch_is_rejected(self):
        self.write_cache(code=self.code().replace(co_linetable=b''))
        with self.assertRaises(ValueError):
            self.inspect()

    def test_trailing_marshal_data_is_rejected(self):
        self.write_cache(tail=b'unknown')
        with self.assertRaises(ValueError):
            self.inspect()

    def test_wrong_magic_is_rejected(self):
        raw = self.write_cache()
        self.cache.write_bytes(b'FAKE' + raw[4:])
        with self.assertRaises(ValueError):
            self.inspect()

    def test_wrong_timestamp_is_rejected(self):
        self.write_cache(mtime=(int(self.source.stat().st_mtime) + 1) & 0xffffffff)
        with self.assertRaises(ValueError):
            self.inspect()

    def test_wrong_size_is_rejected(self):
        self.write_cache(size=len(self.raw) + 1)
        with self.assertRaises(ValueError):
            self.inspect()

    def test_hash_mode_flags_are_not_a_timestamp_cache(self):
        for flags in (1, 2, 3, 4):
            with self.subTest(flags=flags):
                self.write_cache(flags=flags)
                with self.assertRaises(ValueError):
                    self.inspect()

    def test_unsupported_cache_names_are_rejected(self):
        self.write_cache()
        for name in ('sample.cpython-foreign.pyc', 'sample.' + sys.implementation.cache_tag + '.opt-1.pyc',
                     'sample.' + sys.implementation.cache_tag + '-pytest-9.1.1.pyc',
                     'orphan.' + sys.implementation.cache_tag + '.pyc'):
            with self.subTest(name=name):
                other = self.cache.with_name(name)
                other.write_bytes(self.cache.read_bytes())
                try:
                    with self.assertRaises(ValueError):
                        self.inspect()
                finally:
                    other.unlink()

    def test_malformed_or_noncode_payload_is_rejected(self):
        raw = self.write_cache()
        for payload in (b'', b'bad marshal', marshal.dumps({'not': 'code'})):
            with self.subTest(payload=payload):
                self.cache.write_bytes(raw[:16] + payload)
                with self.assertRaises(ValueError):
                    self.inspect()

    def test_cache_byte_bound_is_enforced(self):
        raw = self.write_cache()
        previous = m.MAX_CACHE_BYTES
        try:
            m.MAX_CACHE_BYTES = len(raw) - 1
            with self.assertRaises(ValueError):
                self.inspect()
        finally:
            m.MAX_CACHE_BYTES = previous

    @unittest.skipUnless(os.name == 'posix', 'Actual POSIX executable bits required')
    def test_executable_cache_is_rejected(self):
        self.write_cache()
        self.cache.chmod(0o755)
        with self.assertRaises(ValueError):
            self.inspect()

    @unittest.skipUnless(os.name == 'posix', 'Actual POSIX symlink required')
    def test_symlinked_cache_is_rejected(self):
        raw = self.write_cache()
        self.cache.unlink()
        target = self.root.parent / (self.root.name + '-cache')
        target.write_bytes(raw)
        self.addCleanup(target.unlink)
        self.cache.symlink_to(target)
        with self.assertRaises(ValueError):
            self.inspect()

    @unittest.skipUnless(os.name == 'posix', 'Actual POSIX source modes required')
    def test_source_mode_change_is_rejected(self):
        self.source.chmod(0o755 if self.modes['sample.py'] != 0o755 else 0o644)
        with self.assertRaises(ValueError):
            self.inspect()


if __name__ == '__main__':
    unittest.main()
