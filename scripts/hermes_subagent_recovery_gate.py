"""Run and verify two-process delegation recovery at the reviewed Hermes pins."""
from __future__ import annotations

import argparse
from contextlib import closing
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import subprocess


def load_heartbeat_gate():
    path = Path(__file__).resolve().with_name("hermes_native_heartbeat_gate.py")
    spec = importlib.util.spec_from_file_location("hermes_native_heartbeat_gate", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


heartbeat = load_heartbeat_gate()
gate = heartbeat.gate
CHILD = Path(__file__).resolve().parent / "native/shutdown_delegation_fixture.py"
SUPPORT = CHILD.with_name("shutdown_heartbeat_fixture.py")
STOP = "STOP_RETURNED_OWNER_ABORT_BOUNDARY"
RECOVER = "NATIVE_RECOVERY_RETURNED_NO_REPLAY"
CASES = ("released", "held")


def launch(python, source, site, folder, phase, marker, *, exited_owner=None):
    log_path = folder / f"{phase}.log"
    command = [str(python), "-I", "-S", "-B", str(CHILD), str(source),
               str(folder / "fixture"), str(site), phase]
    if exited_owner is not None:
        command.append(str(exited_owner))
    with log_path.open("xb") as log:
        proc = subprocess.Popen(command, cwd=folder, stdin=subprocess.DEVNULL,
                                stdout=log, stderr=subprocess.STDOUT)
        try:
            code = proc.wait(timeout=60)
        except subprocess.TimeoutExpired:
            gate.write_new(folder / f"{phase}-terminal.json",
                           dict(state="UNKNOWN_PROCESS_PRESERVED", pid=proc.pid))
            raise
    digest = gate.snapshot.digest(log_path.read_bytes())
    gate.write_new(folder / f"{phase}-terminal.json",
                   dict(state="TERMINAL", pid=proc.pid, returnCode=code, logSha256=digest))
    verified_log(folder, phase, marker)
    return proc.pid


def verified_log(folder, phase, marker):
    raw = (folder / f"{phase}.log").read_bytes()
    terminal = gate.read_json(folder / f"{phase}-terminal.json")
    log = raw.decode("utf8")
    if (terminal["state"] != "TERMINAL" or terminal["returnCode"] != 0
            or terminal["logSha256"] != gate.snapshot.digest(raw)
            or log.splitlines().count(marker) != 1 or "FIXTURE_BOUNDARY_DENIAL" in log
            or heartbeat.REOPEN_WARNING in log):
        raise ValueError(f"Missing clean terminal observation for {phase}")
    return log


def verify_case(folder, case):
    held = case == "held"
    stop_log = verified_log(folder, case, STOP)
    restart_log = verified_log(folder, "recover", RECOVER)
    attempts = 2 if held else 0
    if (stop_log.count(heartbeat.LEDGER_REFUSAL) != attempts
            or stop_log.count("persistence.sqlite._state.write.WriterMutexTimeout: " + heartbeat.MUTEX_TIMEOUT) != attempts
            or stop_log.count("persistence.core.errors.StoreContention: " + heartbeat.MUTEX_TIMEOUT) != attempts
            or ("skipping SessionDB close/checkpoint" in stop_log) != held
            or stop_log.count("Traceback (most recent call last):") != 2 * attempts
            or "Traceback" in restart_log
            or "deleg_at_start=1, deleg_now=1" not in stop_log):
        raise ValueError("Unexpected stop or recovery diagnostics")

    fixture = folder / "fixture"
    stop = gate.read_json(fixture / "delegation-probe.json")
    recovery = gate.read_json(fixture / "recovery-result.json")
    before, after, recovered = stop["before"], stop["after"], recovery["state"]
    cause = None if held else "gateway_shutdown"
    if (stop["held"] is not held or stop["syntheticBuilds"] != 1
            or recovery["pid"] == stop["owner"]
            or before["childOwner"] != stop["owner"]
            or before["childStopCause"] is not None
            or after != {**before, "childStopCause": cause}
            or before["childState"] != "running" or before["unitState"] != "running"
            or before["event"] is not None
            or (recovery["first"], recovery["second"], recovery["newModelCalls"],
                recovery["autoResumeEnabled"]) != (1, 0, 0, False)
            or recovered["childSession"] != before["childSession"]
            or recovered["childStopCause"] != cause
            or recovered["childState"] != "resumable"
            or recovered["resumeMode"] != "AUTO_ON_BOOT"
            or recovered["lastOutcome"] != ("unknown" if held else "interrupted")
            or recovered["event"]["resumable_subagent_ids"] != [stop["subagent"]]
            or recovered["event"]["delegation_id"] != stop["delegation"]
            or recovered["event"]["status"] != "unknown" or recovered["unitState"] != "unknown"
            or recovered["delivery"] != "pending"):
        raise ValueError("Recovery did not classify exactly the original delegation")

    witness = stop["heartbeat"]
    if (witness["observedCompetingWriter"] is not True or witness["heartbeatTransactions"] != 1
            or witness["rowsAfterAction"] != 0
            or (held and (witness["competingWriterTimeouts"] != [10.0, 10.0]
                          or witness["stopReturnedWithLiveTransaction"] is not True
                          or witness["rowAtStopReturn"] != 1))):
        raise ValueError("Missing held/released writer evidence")

    db = fixture / "profile/state.db"
    gate.pinned.ordinary(db)
    if not db.is_file():
        raise ValueError("Missing fixture database")
    with closing(sqlite3.connect(db.as_uri() + "?mode=ro", uri=True)) as conn:
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
        units = conn.execute("SELECT delegation_id, state, delivery_state FROM async_delegations").fetchall()
        child = conn.execute("SELECT subagent_id, state, stop_cause, record_json FROM resumable_subagents").fetchall()
        rows = conn.execute("SELECT count(*) FROM gateway_heartbeats").fetchone()[0]
        checkpoint = conn.execute("SELECT count(*) FROM messages WHERE session_id=? AND content=?",
                                  (before["childSession"], "Synthetic input persisted before shutdown")).fetchone()[0]
    if (integrity != "ok" or rows != 0 or checkpoint != 1
            or units != [(stop["delegation"], "unknown", "pending")]
            or len(child) != 1 or child[0][:3] != (stop["subagent"], "resumable", cause)
            or json.loads(child[0][3])["session_id"] != before["childSession"]):
        raise ValueError("Post-exit SQLite state differs from the recovery receipt")
    return dict(childOutcome=recovered["lastOutcome"], delegationOutcome="unknown",
                retainedCheckpoint=checkpoint, sqliteIntegrity=integrity, replays=0,
                competingWriterTimeouts=attempts, elapsedSeconds=witness["elapsedSeconds"])


def inspect_candidate(source, expected):
    if os.name != "nt":
        return gate.inspect_source(source, expected, generated=True)

    # A Windows archive has no POSIX execute bits. Require the complete
    # pinned byte map, with no cache files, extra paths, or reparse points.
    files = gate.pinned.candidate_files(source)
    if files != expected.keys():
        raise ValueError("Windows SDK file inventory differs from the pin")
    total = 0
    for name in sorted(files):
        with (source / name).open("rb") as stream:
            raw = stream.read(gate.pinned.MAX_FILE_BYTES + 1)
        total += len(raw)
        if len(raw) > gate.pinned.MAX_FILE_BYTES or total > gate.pinned.MAX_TOTAL_BYTES:
            raise ValueError("Windows SDK inventory byte bound exceeded")
        if gate.snapshot.digest(raw) != expected[name]:
            raise ValueError(f"Windows SDK source changed: {name}")
    return None


def run(source, commit, base_map, scratch, python):
    if scratch.exists() or source.is_relative_to(scratch) or scratch.is_relative_to(source):
        raise ValueError("Use fresh scratch separate from the SDK source")
    expected = gate.expected_map(gate.read_json(base_map), "candidate", commit)
    original = inspect_candidate(source, expected)
    digest = lambda path: gate.snapshot.digest(path.read_bytes())
    child_hash, support_hash = digest(CHILD), digest(SUPPORT)
    runtime = json.loads(subprocess.check_output(
        [str(python), "-I", "-B", "-c",
         "import json,sys,sysconfig; print(json.dumps([sys.prefix,sysconfig.get_path('purelib')]))"],
        text=True))
    root, site = (Path(p).resolve(strict=True) for p in runtime)
    if not site.is_dir() or not site.is_relative_to(root):
        raise ValueError("An isolated Python test environment is required")
    scratch.mkdir(mode=0o700)
    gate.write_new(scratch / "intent.json", dict(
        sourceCommit=commit, sourceMapSha256=gate.snapshot.digest(gate.snapshot.canonical(expected)),
        childSha256=child_hash, supportSha256=support_hash, patchSha256=gate.REVIEWED_PINS[commit].patch_digest))
    results = {}
    try:
        for case in CASES:
            folder = scratch / case
            folder.mkdir(mode=0o700)
            stopped = launch(python, source, site, folder, case, STOP)
            exited_owner = None
            if os.name == "posix":
                original_owner = gate.read_json(folder / "fixture/delegation-probe.json")["owner"]
                if type(original_owner) is not int or original_owner <= 1 or original_owner != stopped:
                    raise ValueError("Original delegation owner differs from the reaped process")
                exited_owner = original_owner
            # The owner used this empty directory. Remove it before recovery so
            # the no-repository scenario needs no optional Git subprocess.
            (folder / "fixture/run-cwd").rmdir()
            launch(python, source, site, folder, "recover", RECOVER, exited_owner=exited_owner)
            results[case] = verify_case(folder, case)
            print(f"{case}: {results[case]}", flush=True)
    finally:
        if inspect_candidate(source, expected) != original:
            raise ValueError("SDK source or generated output changed during recovery")
        if (digest(CHILD), digest(SUPPORT)) != (child_hash, support_hash):
            raise ValueError("Recovery fixtures changed during execution")
    gate.write_new(scratch / "result.json", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "base-map", "scratch", "python"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", choices=heartbeat.EXPECTED_PIN_COMMITS, required=True)
    args = parser.parse_args()
    run(args.source.resolve(strict=True), args.source_commit, args.base_map.resolve(strict=True),
        args.scratch.resolve(), args.python.absolute())


if __name__ == "__main__":
    main()
