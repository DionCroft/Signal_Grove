"""Materialize/verify the exact experimental Hermes heartbeat source candidate.

This never edits the base checkout or deploys anything. It reads immutable Git
blobs rather than checkout/archive bytes (which can undergo EOL conversion).
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import stat
import subprocess

BASE_COMMIT = "3b421578588243f920886e6fe79a6faa87ea026e"
PATCH_SHA256 = "21e42e06ebf1f1c7c2c6eda122a64894b76485d7b3a95c8640e164f057091190"
PATCH_FILES = {
    "tui_gateway/session_reaper.py": {
        "base_git_blob": "c4caa6035fd39e672f3e72f3b52e84303db51a99",
        "base_sha256": "a1730c646063b6471bf9c8aaec74518129fc182b370a17752c3babdea54d6e6d",
        "patched_sha256": "3d64f53525d56ac75cbbadf1c54f93b8bfa0c3e3e2ab80249177e4f672b6cf3e",
    },
    "gateway/run_shutdown.py": {
        "base_git_blob": "7a9ee828b6b4224b883fe95966a2f1731eedfc9c",
        "base_sha256": "8338afd2374a59a79209db7c996453215941704105d76fe1dad8bbd309f1a342",
        "patched_sha256": "fbc5e3fdeb32003a00ae84d2f36f1f7a6bb6e6dbea2d8552dc5e238f2ffea4ff",
    },
}
PATCH_NAME = "hermes-3b42157-heartbeat-shutdown.patch"
MAX_FILES = 30000
MAX_FILE_BYTES = 32 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024


def git_blob(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def relative_path(name: str) -> PurePosixPath:
    """Git names must have one unambiguous regular-file spelling on Windows."""
    path = PurePosixPath(name)
    reserved = {"CON", "PRN", "AUX", "NUL", *("COM" + str(i) for i in range(1, 10)),
                *("LPT" + str(i) for i in range(1, 10))}
    if (not name or not path.parts or path.is_absolute() or PureWindowsPath(name).drive or
            "\\" in name or ":" in name or any(ord(c) < 32 for c in name) or
            any(c in name for c in '<>"|?*') or
            name != path.as_posix() or any(p in (".", "..") or p.casefold() == ".git" for p in path.parts) or
            any(p.endswith((".", " ")) or p.split(".", 1)[0].upper() in reserved for p in path.parts)):
        raise ValueError("Unsafe or ambiguous source path")
    return path


def ordinary(path: Path) -> None:
    info = path.lstat()
    if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
        raise ValueError("Source/scratch reparse points are not accepted")


def absolute_root(value: Path) -> Path:
    if not value.is_absolute():
        raise ValueError("Absolute E-drive source/scratch roots are required on Windows")
    for path in (value, *value.parents):
        if path.exists():
            ordinary(path)
    resolved = value.resolve()
    if os.name == "nt" and resolved.drive.upper() != "E:":
        raise ValueError("SDK test sources and scratch must stay on E:")
    return resolved


def git_env(scratch: Path) -> dict:
    env = {k: v for k, v in os.environ.items() if k.upper() in ("SYSTEMROOT", "WINDIR", "PATH", "PATHEXT")}
    env.update(TEMP=str(scratch), TMP=str(scratch), TMPDIR=str(scratch),
               GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
               GIT_OPTIONAL_LOCKS="0", GIT_NO_LAZY_FETCH="1", GIT_NO_REPLACE_OBJECTS="1", GIT_TERMINAL_PROMPT="0")
    return env


def git(base: Path, *args: str, input_data: bytes | None = None) -> bytes:
    command = ["git", "--no-optional-locks", "-c", "core.fsmonitor=false", "-c",
               "safe.directory=" + str(base), "-C", str(base), *args]
    result = subprocess.run(command, env=git_env(base.parent), input=input_data,
                            capture_output=True, check=True, timeout=180)
    return result.stdout


def base_tree(base: Path) -> dict[str, str]:
    if git(base, "rev-parse", "HEAD").decode().strip() != BASE_COMMIT:
        raise ValueError("Only the exact reviewed immutable base is supported")
    if git(base, "status", "--porcelain=v1", "--untracked-files=all").strip():
        raise ValueError("Base checkout must remain clean")
    rows = {}
    folded = set()
    for entry in git(base, "ls-tree", "-r", "-z", BASE_COMMIT).split(b"\0"):
        if not entry:
            continue
        header, raw_name = entry.split(b"\t", 1)
        mode, kind, oid = header.decode().split()
        name = raw_name.decode("utf-8")
        relative_path(name)
        if mode not in ("100644", "100755") or kind != "blob" or not re.fullmatch(r"[0-9a-f]{40}", oid):
            raise ValueError("Only regular immutable Git blobs are accepted")
        if name.casefold() in folded:
            raise ValueError("Case-colliding source paths are not accepted")
        folded.add(name.casefold())
        rows[name] = oid
    if not rows or len(rows) > MAX_FILES:
        raise ValueError("Source inventory bound exceeded")
    for name, row in PATCH_FILES.items():
        if rows.get(name) != row["base_git_blob"]:
            raise ValueError("Patch base-file identity does not match")
    return rows


def candidate_files(source: Path) -> set[str]:
    found = set()
    directories = set()
    for directory, dirs, files in os.walk(source, followlinks=False):
        ordinary(Path(directory))
        for name in dirs:
            child = Path(directory) / name
            ordinary(child)
            directories.add(child.relative_to(source).as_posix())
        for name in files:
            path = Path(directory) / name
            ordinary(path)
            if not stat.S_ISREG(path.lstat().st_mode):
                raise ValueError("Candidate contains a non-regular file")
            rel = path.relative_to(source).as_posix()
            relative_path(rel)
            found.add(rel)
            if len(found) > MAX_FILES:
                raise ValueError("Candidate inventory bound exceeded")
    expected_dirs = {parent.as_posix() for name in found for parent in PurePosixPath(name).parents if parent.as_posix() != "."}
    if directories != expected_dirs:
        raise ValueError("Candidate contains additional empty source directories")
    return found


def verify_candidate(base: Path, source: Path) -> dict:
    base, source = absolute_root(base), absolute_root(source)
    if not base.is_dir() or not source.is_dir() or source.is_relative_to(base) or base.is_relative_to(source):
        raise ValueError("Base and derived source must be separate directories")
    rows = base_tree(base)
    if candidate_files(source) != set(rows):
        raise ValueError("Candidate contains missing or additional source files")
    total = 0
    hashes, patched = {}, {}
    for name, oid in rows.items():
        with (source / name).open("rb") as stream:
            raw = stream.read(MAX_FILE_BYTES + 1)
        total += len(raw)
        if len(raw) > MAX_FILE_BYTES or total > MAX_TOTAL_BYTES:
            raise ValueError("Candidate byte bound exceeded")
        digest = hashlib.sha256(raw).hexdigest()
        if name in PATCH_FILES:
            if digest != PATCH_FILES[name]["patched_sha256"]:
                raise ValueError("Declared patch output hash does not match")
            patched[name] = git_blob(raw)
        elif git_blob(raw) != oid:
            raise ValueError("Unreviewed source change: " + name)
        hashes[name] = digest
    return dict(base_commit=BASE_COMMIT, patch_sha256=PATCH_SHA256,
                base_git_blobs=rows, patched_git_blobs=patched, source_sha256=hashes,
                files=len(rows), bytes=total,
                source_kind="EXPERIMENTAL_EXACT_BASE_PLUS_TWO_REVIEWED_CHANGES",
                unmodified_upstream_accepted=False)


def patch_bytes(path: Path) -> bytes:
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != PATCH_SHA256:
        raise ValueError("Reviewed patch bytes do not match")
    return raw


def prepare_candidate(base: Path, output: Path, patch: Path) -> dict:
    base, output = absolute_root(base), absolute_root(output)
    if output.exists() or output.is_relative_to(base) or base.is_relative_to(output):
        raise ValueError("A fresh separate candidate output is required; never retry a partial root")
    rows = base_tree(base)
    reviewed = patch_bytes(patch)
    output.mkdir(parents=True, exist_ok=False)
    source = output / "source"
    source.mkdir()
    receipt = output / "preparation.json"
    record = dict(state="EXACT_GIT_BLOB_MATERIALIZATION_STARTED", base_commit=BASE_COMMIT,
                  patch_sha256=PATCH_SHA256, files=len(rows), source=str(source),
                  base_modified=False, sdk_executed=False, deployed=False)
    receipt.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    try:
        stream = io.BytesIO(git(base, "cat-file", "--batch", input_data=b"".join((oid + "\n").encode() for oid in rows.values())))
        total = 0
        for name, oid in rows.items():
            header = stream.readline(256).decode().split()
            if len(header) != 3 or header[:2] != [oid, "blob"]:
                raise ValueError("Unexpected Git batch header")
            size = int(header[2])
            if size < 0 or size > MAX_FILE_BYTES:
                raise ValueError("Source file byte bound exceeded")
            total += size
            if total > MAX_TOTAL_BYTES:
                raise ValueError("Source total byte bound exceeded")
            raw = stream.read(size)
            if len(raw) != size or stream.read(1) != b"\n" or git_blob(raw) != oid:
                raise ValueError("Source blob stream failed integrity validation")
            if name in PATCH_FILES and hashlib.sha256(raw).hexdigest() != PATCH_FILES[name]["base_sha256"]:
                raise ValueError("Patch input hash does not match")
            target = source.joinpath(*relative_path(name).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        if stream.read(1):
            raise ValueError("Unexpected data after the Git blob stream")
        staged_patch = output / PATCH_NAME
        staged_patch.write_bytes(reviewed)
        git(source, "apply", "--no-index", "--check", str(staged_patch))
        git(source, "apply", "--no-index", str(staged_patch))
        proof = verify_candidate(base, source)
        (output / "source-proof.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
        record.update(state="EXACT_DERIVED_SOURCE_VERIFIED_NOT_DEPLOYED", bytes=proof["bytes"])
    except BaseException as exc:
        record.update(state="FAILED_PREPARATION_RETAINED_NO_RETRY", error_type=type(exc).__name__)
        raise
    finally:
        receipt.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    return record


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "verify"))
    parser.add_argument("--base-root", required=True, type=Path)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.action == "prepare":
        if args.output is None or args.source_root is not None:
            parser.error("prepare requires only --base-root and --output")
        patch = Path(__file__).resolve().parents[1] / "upstream_patches" / PATCH_NAME
        record = prepare_candidate(args.base_root, args.output, patch)
    else:
        if args.source_root is None or args.output is not None:
            parser.error("verify requires only --base-root and --source-root")
        proof = verify_candidate(args.base_root, args.source_root)
        record = {k: v for k, v in proof.items() if k not in ("base_git_blobs", "patched_git_blobs", "source_sha256")}
    print(json.dumps(record))


if __name__ == "__main__":
    main()
