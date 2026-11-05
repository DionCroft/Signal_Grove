"""Read-only export of test metadata from the exact installed debug APK pair."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess

PACKAGE = "org.hermesmobile.app.debug"
MAX_FILE = 262144
MAX_TOTAL = 16 * 1024 * 1024
TOP_FIELDS = set("schema phase scope elapsed_ms uptime_ms interactive cpu_lease_requested cpu_lease_held cpu_lease_error_class case class method case_elapsed_ms case_uptime_ms case_nano_ms elapsed_minus_uptime_ms snapshot_delay_ms cpu_lease_limit_ms threads_truncated threads tests failures ignored cpu_lease_held_before_release snapshots".split())

def run_path(run):
    if not re.fullmatch(r"run-[0-9]+", run):
        raise ValueError("Invalid run identifier")
    return "cache/device-test-diagnostics/" + run

def metadata_path(name, prefix):
    if not name.startswith(prefix + "/"):
        raise ValueError("Unexpected diagnostic path")
    leaf = name[len(prefix) + 1:]
    if not re.fullmatch(r"(?:run-(?:start|finish)|case-[0-9]+-(?:start|finish)|snapshot-[0-9]+-[0-9]+-(?:deadline|failure))\.json", leaf):
        raise ValueError("Unexpected diagnostic filename")
    return leaf

def decode_metadata(raw):
    if len(raw) > MAX_FILE:
        raise ValueError("Diagnostic file exceeds bound")
    data = json.loads(raw)
    if not isinstance(data, dict) or set(data) - TOP_FIELDS or data.get("schema") != 1 or data.get("scope") != "instrumentation metadata only":
        raise ValueError("Unsupported diagnostic schema")
    threads = data.get("threads", [])
    if not isinstance(threads, list) or len(threads) > 32:
        raise ValueError("Invalid thread metadata")
    for thread in threads:
        if not isinstance(thread, dict) or set(thread) != {"id", "state", "frames_truncated", "frames"} or len(thread["frames"]) > 24:
            raise ValueError("Unexpected thread fields")
        for frame in thread["frames"]:
            if not isinstance(frame, dict) or set(frame) - {"class", "method", "file", "line"}:
                raise ValueError("Unexpected stack fields")
            if any(c in frame.get("file", "") for c in "/\\\r\n"):
                raise ValueError("Stack filenames must be basenames")
    return data

def installed_path(text):
    lines = text.strip().splitlines()
    if len(lines) != 1 or not re.fullmatch(r"package:/data/app/[A-Za-z0-9_./~+=-]+/base\.apk", lines[0]):
        raise ValueError("Expected one installed universal APK")
    if any(part in (".", "..") for part in lines[0][8:].split("/")):
        raise ValueError("Installed APK path contains dot segments")
    return lines[0][8:]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["adb", "serial", "run-id", "app-sha256", "test-sha256", "output"]:
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--expected-tests", type=int, required=True)
    args = parser.parse_args()
    if args.expected_tests < 1 or any(not re.fullmatch(r"[0-9a-f]{64}", h) for h in [args.app_sha256, args.test_sha256]):
        raise ValueError("Positive case count and lowercase SHA-256 values required")
    prefix = run_path(args.run_id)
    output = Path(args.output).resolve()
    if (os.name == "nt" and output.drive.upper() != "E:") or output.exists():
        raise ValueError("Use a new output directory; Windows exports must stay on E:")
    def adb(*parts):
        return subprocess.check_output([args.adb, "-s", args.serial, *parts], stderr=subprocess.PIPE, timeout=30)
    identities = {}
    for package, expected in [(PACKAGE, args.app_sha256), (PACKAGE + ".test", args.test_sha256)]:
        path = installed_path(adb("shell", "pm", "path", package).decode())
        actual = adb("shell", "sha256sum", path).decode().split()[0]
        if actual != expected:
            raise ValueError("Installed APK differs from expected artifact")
        identities[package] = actual
    names = adb("exec-out", "run-as", PACKAGE, "find", prefix, "-type", "f").decode().splitlines()
    if not names or len(names) > 4096 or len(set(names)) != len(names):
        raise ValueError("Invalid diagnostic inventory")
    leaves = {name: metadata_path(name, prefix) for name in names}
    output.mkdir(parents=True)
    records = {}; total = 0; finish = None
    for name, leaf in leaves.items():
        raw = adb("exec-out", "run-as", PACKAGE, "cat", name)
        data = decode_metadata(raw); total += len(raw)
        if total > MAX_TOTAL:
            raise ValueError("Diagnostic run exceeds export bound")
        (output / leaf).write_bytes(raw)
        records[leaf] = {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
        if leaf == "run-finish.json":
            finish = data
    complete = bool(finish and finish.get("tests") == args.expected_tests and finish.get("failures") == 0 and finish.get("ignored") == 0)
    manifest = {"schema": 1, "scope": "Listener metadata; separately verify terminal instrumentation log", "apk_sha256": identities, "run": args.run_id, "expected_tests": args.expected_tests, "listener_reports_expected_success": complete, "files": records}
    (output / "collection.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(records)} bounded metadata files; installed APK identities verified.")
    return 0 if complete else 1

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print("Diagnostic export failed: " + type(error).__name__)
        raise SystemExit(1)
