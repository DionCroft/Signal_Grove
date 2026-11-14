"""Verify the bound SDK inputs and separately inventory native CPython caches.

Source-copy verification stays strict. This read-only post-execution check accepts
only timestamp bytecode which recompiles exactly from those reviewed inputs.
It does not import or execute the cached code, delete outputs, or accept pytest
caches, generated modules, launchers or other additional runtime files.
"""
from __future__ import annotations

import argparse
import importlib.util
import io
import json
import marshal
import os
from pathlib import Path, PurePosixPath
import platform
import re
import stat
import struct
import sys
import types


def sibling(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


binding = sibling('hermes_runtime_stamp')
snapshot = binding.snapshot
pinned = binding.pinned
MAX_CACHE_BYTES = 8 * 1024 * 1024
MAX_CACHE_TOTAL = 128 * 1024 * 1024
MAX_CACHE_FILES = 5000


def code_identity(code):
    """Include filenames, line/exception tables and all nested code constants."""
    def constant(value):
        if isinstance(value, types.CodeType):
            return ('code', code_identity(value))
        if isinstance(value, tuple):
            return ('tuple', tuple(constant(item) for item in value))
        if isinstance(value, frozenset):
            return ('frozenset', frozenset(constant(item) for item in value))
        return (type(value), value)
    return tuple((name, constant(getattr(code, name))) for name in sorted(dir(code))
                 if name.startswith('co_') and not callable(getattr(code, name)))


def verify_cache(path, source, raw):
    if len(raw) < 17 or len(raw) > MAX_CACHE_BYTES or raw[:4] != importlib.util.MAGIC_NUMBER:
        raise ValueError('Invalid native bytecode header or size')
    flags, mtime, size = struct.unpack('<III', raw[4:16])
    info = source.stat()
    if flags != 0 or mtime != (int(info.st_mtime) & 0xffffffff) or size != (info.st_size & 0xffffffff):
        raise ValueError('Bytecode is not the current source timestamp/size')
    stream = io.BytesIO(raw[16:])
    try:
        code = marshal.load(stream)
    except (EOFError, ValueError, TypeError) as exc:
        raise ValueError('Malformed native bytecode') from exc
    if not isinstance(code, types.CodeType) or stream.read(1):
        raise ValueError('Bytecode payload or trailing data is unexpected')
    expected = compile(source.read_bytes(), str(source), 'exec', dont_inherit=True, optimize=0)
    if code_identity(code) != code_identity(expected):
        raise ValueError('Bytecode differs from freshly compiled reviewed source')


def inspect_runtime(source, expected, modes):
    source = pinned.absolute_root(source)
    hashes, actual_modes, caches, directories = {}, {}, {}, set()
    total, cache_total = 0, 0
    cache_name = re.compile(r'(.+)\.' + re.escape(sys.implementation.cache_tag) + r'\.pyc')
    for directory, dirs, files in os.walk(source, followlinks=False):
        root = Path(directory)
        pinned.ordinary(root)
        for name in dirs:
            child = root / name
            pinned.ordinary(child)
            directories.add(child.relative_to(source).as_posix())
        for name in files:
            path = root / name
            pinned.ordinary(path)
            info = path.lstat()
            if not stat.S_ISREG(info.st_mode):
                raise ValueError('Nonregular runtime entry')
            rel = path.relative_to(source).as_posix()
            pinned.relative_path(rel)
            mode = stat.S_IMODE(info.st_mode)
            limit = pinned.MAX_FILE_BYTES if rel in expected else MAX_CACHE_BYTES
            with path.open('rb') as stream:
                raw = stream.read(limit + 1)
            if len(raw) > limit:
                raise ValueError('Runtime file byte bound exceeded')
            if rel in expected:
                total += len(raw)
                if total > pinned.MAX_TOTAL_BYTES:
                    raise ValueError('Source byte bound exceeded')
                hashes[rel] = snapshot.digest(raw)
                actual_modes[rel] = mode
                continue
            relative = PurePosixPath(rel)
            match = cache_name.fullmatch(relative.name)
            owner = relative.parent.parent / ((match[1] if match else '') + '.py')
            cache_modes = (0o666,) if os.name == 'nt' else (0o600, 0o644)
            if (relative.parent.name != '__pycache__' or not match or owner.as_posix() not in expected
                    or mode not in cache_modes):
                raise ValueError('Unexpected runtime output: ' + rel)
            cache_total += len(raw)
            if cache_total > MAX_CACHE_TOTAL or len(caches) >= MAX_CACHE_FILES:
                raise ValueError('Bytecode inventory bound exceeded')
            # Reject changed input before using its bytes as the compilation oracle.
            if snapshot.digest((source / owner).read_bytes()) != expected[owner.as_posix()]:
                raise ValueError('Bytecode owner differs from reviewed source')
            verify_cache(path, source / owner, raw)
            caches[rel] = dict(sha256=snapshot.digest(raw), bytes=len(raw), mode=mode,
                               source=owner.as_posix())
    all_names = set(hashes) | set(caches)
    allowed_dirs = {p.as_posix() for name in all_names for p in PurePosixPath(name).parents
                    if p.as_posix() != '.'}
    if hashes != expected or actual_modes != modes or directories != allowed_dirs:
        raise ValueError('Source bytes/modes or runtime directory namespace changed')
    return dict(source_map_sha256=snapshot.digest(snapshot.canonical(hashes)),
                source_files=len(hashes), source_bytes=total, native_modes_preserved=True,
                generated=caches, generated_map_sha256=snapshot.digest(snapshot.canonical(caches)),
                generated_bytes=cache_total, cached_code_executed_by_verifier=False)


def verify(base, source, source_map):
    if (platform.system() != 'Linux' or os.name != 'posix' or
            sys.implementation.name != 'cpython' or sys.version_info[:3] != (3, 14, 9)):
        raise ValueError('Actual pinned Linux CPython 3.14.9 required')
    base, source = binding.roots(base, source)
    expected = binding.reviewed_map(source_map)
    modes, _ = snapshot.snapshot(base, expected)
    expected[binding.STAMP] = snapshot.digest(binding.bind_stamp((base / binding.STAMP).read_bytes(),
                                                                base.parent / 'tools'))
    return dict(state='REVIEWED_INPUTS_AND_SEPARATE_NATIVE_BYTECODE_VERIFIED',
                base_map_sha256=binding.BASE_MAP[1], **inspect_runtime(source, expected, modes))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-root', type=Path, required=True)
    parser.add_argument('--source-root', type=Path, required=True)
    parser.add_argument('--source-map', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.base_root, args.source_root, args.source_map)))


if __name__ == '__main__':
    main()
