"""Bind the reviewed Pi SDK snapshot to its permanent, explicit tools directory.

Copies source into a fresh sibling. Only install-stamp.json's runtimeDir changes;
all code, dependencies, provenance and native modes remain exact. No SDK runs.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re


def sibling(name):
    import importlib.util
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


snapshot = sibling('hermes_snapshot_heartbeat')
pinned = snapshot.pinned
BASE_MAP = (23628, 'e1af8f93ea6c094da8e6eb593926d3410568c333a874f7fb283a773f93e046a6')
BASE_STAMP = 'dccfbe161718204ea52289824d6542af96a384f21fbc64102e4d18d10fe9301c'
STAMP = 'install-stamp.json'


def reviewed_map(path):
    path = pinned.absolute_root(path)
    if not path.is_file() or path.stat().st_size > 8 * 1024 * 1024:
        raise ValueError('Invalid reviewed map')
    value = json.loads(path.read_text(encoding='utf8'), object_pairs_hook=snapshot.unique)
    if type(value) is not dict or (len(value), snapshot.digest(snapshot.canonical(value))) != BASE_MAP:
        raise ValueError('Unreviewed whole runtime source')
    for name, checksum in value.items():
        pinned.relative_path(name)
        if type(checksum) is not str or not re.fullmatch('[a-f0-9]{64}', checksum):
            raise ValueError('Invalid source-map entry')
    if value.get(STAMP) != BASE_STAMP:
        raise ValueError('Unreviewed base stamp')
    return value


def bind_stamp(raw, store):
    store = pinned.absolute_root(store)
    if not store.is_dir() or store.name != 'tools':
        raise ValueError('Existing ordinary tools directory required')
    if snapshot.digest(raw) != BASE_STAMP:
        raise ValueError('Base stamp bytes differ from review')
    before = json.loads(raw, object_pairs_hook=snapshot.unique)
    if (before.get('schemaVersion') != 2 or before.get('commit') != pinned.BASE_COMMIT or
            before.get('dirty') is not True or before.get('source') != 'local' or
            before.get('updateMechanism') != 'external' or before.get('runtimeDir') != '../tools'):
        raise ValueError('Unexpected source identity or store input')
    needle = b'"runtimeDir": "../tools"'
    if raw.count(needle) != 1:
        raise ValueError('Ambiguous/noncanonical runtime field')
    after = raw.replace(needle, b'"runtimeDir": ' + json.dumps(str(store)).encode('utf8'), 1)
    expected = dict(before, runtimeDir=str(store))
    if json.loads(after, object_pairs_hook=snapshot.unique) != expected:
        raise ValueError('Stamp changed beyond its explicit store binding')
    return after


def roots(base, output):
    base, output = pinned.absolute_root(base), pinned.absolute_root(output)
    if base == output or base.parent != output.parent:
        raise ValueError('Separate sibling sources required')
    return base, output


def verify(base, output, source_map):
    base, output = roots(base, output)
    expected = reviewed_map(source_map)
    modes, _ = snapshot.snapshot(base, expected)
    patched = dict(expected)
    patched[STAMP] = snapshot.digest(bind_stamp((base / STAMP).read_bytes(), base.parent / 'tools'))
    actual_modes, size = snapshot.snapshot(output, patched)
    if actual_modes != modes:
        raise ValueError('Runtime source modes changed')
    return dict(state='EXACT_RUNTIME_SOURCE_WITH_EXPLICIT_LOCAL_STORE_VERIFIED',
                base_commit=pinned.BASE_COMMIT, base_map_sha256=BASE_MAP[1],
                source_map_sha256=snapshot.digest(snapshot.canonical(patched)),
                files=len(patched), bytes=size, changed_files=[STAMP], source_sha256=patched,
                runtime_code_changed=False, dependency_inputs_changed=False,
                identity_fields_changed=False, sdk_executed=False, deployed=False,
                native_modes_preserved=True)


def prepare(base, output, evidence, source_map):
    base, output = roots(base, output)
    evidence = pinned.absolute_root(evidence)
    if output.exists() or evidence.exists():
        raise ValueError('Fresh output and evidence required; preserve partial attempts')
    if any(a.is_relative_to(b) for a, b in ((evidence, base), (base, evidence),
                                          (evidence, output), (output, evidence))):
        raise ValueError('Evidence and source roots must be separate')
    if not output.parent.is_dir() or not evidence.parent.is_dir():
        raise ValueError('Existing verified parents required')
    expected = reviewed_map(source_map)
    modes, _ = snapshot.snapshot(base, expected)
    stamp = bind_stamp((base / STAMP).read_bytes(), base.parent / 'tools')
    evidence.mkdir(mode=0o700)
    receipt = evidence / 'preparation.json'
    record = dict(state='EXPLICIT_LOCAL_STORE_SOURCE_COPY_INTENT', base_map_sha256=BASE_MAP[1],
                  sdk_executed=False, base_modified=False, deployed=False)
    def save():
        receipt.write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    save()
    try:
        output.mkdir(mode=0o700)
        for name, checksum in expected.items():
            raw = (base / name).read_bytes()
            if snapshot.digest(raw) != checksum:
                raise ValueError('Base changed while copying')
            target = output / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as stream:
                stream.write(stamp if name == STAMP else raw)
            if os.name == 'posix':
                target.chmod(modes[name])
        proof = verify(base, output, source_map)
        (evidence / 'source-proof.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf8')
        record.update(state=proof['state'], files=proof['files'], source_map_sha256=proof['source_map_sha256'])
    except BaseException as exc:
        record.update(state='FAILED_SOURCE_COPY_PRESERVED_NO_REPLAY', error_type=type(exc).__name__)
        raise
    finally:
        save()
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'verify'))
    parser.add_argument('--base-root', type=Path, required=True)
    parser.add_argument('--source-root', type=Path, required=True)
    parser.add_argument('--source-map', type=Path, required=True)
    parser.add_argument('--evidence-root', type=Path)
    args = parser.parse_args()
    if args.action == 'prepare':
        if args.evidence_root is None:
            parser.error('prepare requires fresh evidence')
        result = prepare(args.base_root, args.source_root, args.evidence_root, args.source_map)
    else:
        if args.evidence_root is not None:
            parser.error('verify writes no evidence')
        result = verify(args.base_root, args.source_root, args.source_map)
        result.pop('source_sha256')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
