"""Prepare the reviewed heartbeat delta over an exact reviewed whole SDK snapshot.

Both pristine and maintained bases are explicit, immutable source inputs. This
copies source only; it never imports SDK code, builds dependencies or activates it.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat


def sibling(name):
    path = Path(__file__).resolve().with_name(name + '.py')
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


pinned = sibling('hermes_heartbeat_candidate')
REVIEWED = {
    'pristine-stable3b': (23586, '52a2dddf97a3fa39251a666fa8819f05b3185d249f9e14005f66247ba217fbf2'),
    'maintained-stable3b-read-v1': (23628, '1bc98a59131cdcc7b30d5d520cc88bffa27d0fbc22f6c4d6026738c0b9e05644'),
}
EXECUTABLES = (124, '1046faf30db8d177eee53c763238964e2ef1d4eac1040743e2070d9d8041f716')


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate source-map key')
        result[key] = value
    return result


def reviewed_map(path, kind):
    if kind not in REVIEWED:
        raise ValueError('Unreviewed source kind')
    path = pinned.absolute_root(path)
    if not path.is_file() or path.stat().st_size > 8 * 1024 * 1024:
        raise ValueError('Invalid source map')
    value = json.loads(path.read_text(encoding='utf8'), object_pairs_hook=unique)
    count, expected = REVIEWED[kind]
    if type(value) is not dict or len(value) != count or digest(canonical(value)) != expected:
        raise ValueError('Whole source map differs from the exact reviewed snapshot')
    folded = set()
    for name, checksum in value.items():
        pinned.relative_path(name)
        if name.casefold() in folded or type(checksum) is not str or not re.fullmatch('[a-f0-9]{64}', checksum):
            raise ValueError('Invalid or ambiguous source-map entry')
        folded.add(name.casefold())
    for name, row in pinned.PATCH_FILES.items():
        if value.get(name) != row['base_sha256']:
            raise ValueError('Lifecycle patch target differs from reviewed stable3b bytes')
    return value


def snapshot(source, expected, *, pristine_git=False):
    source = pinned.absolute_root(source)
    if pristine_git:
        actual = sibling('verify_canonical_rpc').source_snapshot(source)
        if actual != expected:
            raise ValueError('Pristine Git snapshot differs from review')
    elif pinned.candidate_files(source) != set(expected):
        raise ValueError('Missing, extra or shadow source entries')
    hashes, modes = {}, {}
    total = 0
    for name in expected:
        path = source / name
        pinned.ordinary(path)
        if not stat.S_ISREG(path.lstat().st_mode):
            raise ValueError('Nonregular SDK source')
        with path.open('rb') as stream:
            raw = stream.read(pinned.MAX_FILE_BYTES + 1)
        total += len(raw)
        if len(raw) > pinned.MAX_FILE_BYTES or total > pinned.MAX_TOTAL_BYTES:
            raise ValueError('SDK source byte bound exceeded')
        hashes[name] = digest(raw)
        modes[name] = stat.S_IMODE(path.stat().st_mode)
    if hashes != expected:
        raise ValueError('SDK working bytes differ from the reviewed snapshot')
    if os.name == 'posix':
        if any(mode not in (0o600, 0o644, 0o755) for mode in modes.values()):
            raise ValueError('Unexpected SDK file mode')
        names = sorted(name for name, mode in modes.items() if mode & 0o111)
        if (len(names), digest(('\n'.join(names) + '\n').encode())) != EXECUTABLES:
            raise ValueError('SDK executable identities differ from review')
    return modes, total


def verify(base, source, source_map, kind):
    base, source = pinned.absolute_root(base), pinned.absolute_root(source)
    if base == source or base.parent != source.parent:
        raise ValueError('Separate sibling sources are required for relative runtime paths')
    expected = reviewed_map(source_map, kind)
    modes, _ = snapshot(base, expected, pristine_git=kind == 'pristine-stable3b')
    patched = dict(expected)
    for name, row in pinned.PATCH_FILES.items():
        patched[name] = row['patched_sha256']
    candidate_modes, total = snapshot(source, patched)
    if os.name == 'posix' and candidate_modes != modes:
        raise ValueError('Derived SDK modes changed')
    return dict(state='EXACT_SNAPSHOT_PLUS_TWO_LIFECYCLE_CHANGES_VERIFIED', base_kind=kind,
                base_commit=pinned.BASE_COMMIT, base_map_sha256=digest(canonical(expected)),
                patch_sha256=pinned.PATCH_SHA256, files=len(patched), bytes=total,
                changed_files=sorted(pinned.PATCH_FILES), source_sha256=patched,
                source_map_sha256=digest(canonical(patched)),
                native_posix_modes_verified=os.name == 'posix', executable_count=EXECUTABLES[0],
                sdk_executed=False, dependencies_changed=False, base_modified=False,
                unmodified_upstream_accepted=False, deployed=False)


def prepare(base, output, evidence, source_map, kind, patch):
    base, output, evidence = [pinned.absolute_root(path) for path in (base, output, evidence)]
    if output.exists() or evidence.exists() or base.parent != output.parent:
        raise ValueError('Fresh sibling source and fresh evidence roots are required; never retry a partial root')
    if any(x.is_relative_to(y) for x, y in ((evidence, base), (evidence, output), (base, evidence), (output, evidence))):
        raise ValueError('Source/evidence roots must be separate')
    if not output.parent.is_dir() or not evidence.parent.is_dir():
        raise ValueError('Verified source/evidence parents must already exist')
    expected = reviewed_map(source_map, kind)
    modes, _ = snapshot(base, expected, pristine_git=kind == 'pristine-stable3b')
    reviewed_patch = pinned.patch_bytes(patch)
    evidence.mkdir(mode=0o700)
    receipt = evidence / 'preparation.json'
    record = dict(state='EXACT_SNAPSHOT_MATERIALIZATION_STARTED', base_kind=kind,
                  base_map_sha256=digest(canonical(expected)), patch_sha256=pinned.PATCH_SHA256,
                  source=str(output), base_modified=False, sdk_executed=False, deployed=False)
    def save():
        receipt.write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    save()
    try:
        output.mkdir(mode=0o700)
        for name, checksum in expected.items():
            raw = (base / name).read_bytes()
            if digest(raw) != checksum:
                raise ValueError('Source changed while copying')
            target = output / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as stream:
                stream.write(raw)
            if os.name == 'posix':
                target.chmod(modes[name])
        staged_patch = evidence / pinned.PATCH_NAME
        staged_patch.write_bytes(reviewed_patch)
        pinned.git(output, 'apply', '--no-index', '--check', str(staged_patch))
        pinned.git(output, 'apply', '--no-index', str(staged_patch))
        if os.name == 'posix':
            # Git may replace the two files using the caller's restrictive
            # umask. Restore their original modes before the exact comparison.
            for name in pinned.PATCH_FILES:
                pinned.ordinary(output / name)
                (output / name).chmod(modes[name])
        proof = verify(base, output, source_map, kind)
        (evidence / 'source-proof.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf8')
        record.update(state=proof['state'], files=proof['files'], source_map_sha256=proof['source_map_sha256'])
    except BaseException as exc:
        record.update(state='FAILED_DERIVATIVE_PRESERVED_NO_REPLAY', error_type=type(exc).__name__)
        raise
    finally:
        save()
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'verify'))
    parser.add_argument('--base-kind', required=True, choices=tuple(REVIEWED))
    parser.add_argument('--base-root', required=True, type=Path)
    parser.add_argument('--source-map', required=True, type=Path)
    parser.add_argument('--source-root', required=True, type=Path)
    parser.add_argument('--evidence-root', type=Path)
    args = parser.parse_args()
    if args.action == 'prepare':
        if args.evidence_root is None:
            parser.error('prepare requires a fresh evidence root')
        patch = Path(__file__).resolve().parents[1] / 'upstream_patches' / pinned.PATCH_NAME
        result = prepare(args.base_root, args.source_root, args.evidence_root, args.source_map, args.base_kind, patch)
    else:
        if args.evidence_root is not None:
            parser.error('verify does not write an evidence root')
        result = verify(args.base_root, args.source_root, args.source_map, args.base_kind)
        result.pop('source_sha256')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
