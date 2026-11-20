"""Verify real Linux heartbeat lifetimes at the two reviewed persistence pins."""
from __future__ import annotations

import argparse
from contextlib import closing
import importlib.util
import json
from pathlib import Path
import sqlite3
import subprocess


def load_canonical_gate():
    path = Path(__file__).with_name("canonical_shutdown_gate.py")
    spec = importlib.util.spec_from_file_location("canonical_shutdown_gate", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load_canonical_gate()
CHILD = Path(__file__).resolve().parent / "native/shutdown_heartbeat_fixture.py"
PRISTINE_CASES = {"stop-once": ("stop", "0"), "stop-periodic": ("stop", "0.5")}
CANDIDATE_CASES = {
    **PRISTINE_CASES, "exit-once": ("exit", "0"), "exit-periodic": ("exit", "0.5"),
    "close-gate-held": ("gate", "0.5"), "stop-released-contention": ("overlap", "0.5"),
    "stop-held-contention": ("held-stop", "0.5"),
}
RETURN_MARKER = "NATIVE_HEARTBEAT_ACTION_RETURNED"
REOPEN_WARNING = "was closed while a write was still in flight"
LEDGER_REFUSAL = "WARNING:tools.delegate_tool:resumable subagent record write failed"
HEARTBEAT_CLOSE_REFUSAL = "backend heartbeat writer/cleanup is not drained"
MUTEX_TIMEOUT = "database is locked (in-process state.db writer mutex exceeded write patience)"
EXPECTED_PIN_COMMITS = tuple(commit for commit in gate.REVIEWED_PINS if commit != gate.rpc.PIN)


def cases(variant: str) -> dict:
    if variant == "pristine":
        return PRISTINE_CASES
    if variant == "candidate":
        return CANDIDATE_CASES
    raise ValueError("Unreviewed heartbeat lifecycle variant")


def read_post_exit(db: Path) -> tuple[int, str]:
    if not db.is_file() or db.is_symlink():
        raise ValueError("Missing regular fixture database")
    with closing(sqlite3.connect(db.as_uri() + "?mode=ro", uri=True)) as conn:
        rows = conn.execute("SELECT count(*) FROM gateway_heartbeats").fetchone()[0]
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
    return rows, integrity


def held_stop_evidence(observation: dict, log: str) -> dict:
    seconds = observation.get("elapsedSeconds")
    if (observation.get("stopReturnedWithLiveTransaction") is not True
            or type(observation.get("rowAtStopReturn")) is not int or observation["rowAtStopReturn"] != 1
            or observation.get("competingWriterTimeouts") != [10.0]
            or type(seconds) not in (int, float) or not 10.0 <= seconds < 28.0):
        raise ValueError("Full stop lacks the held heartbeat or bounded ledger timeout observation")

    before, _, remaining = log.partition(LEDGER_REFUSAL)
    diagnostic, _, after = remaining.partition(HEARTBEAT_CLOSE_REFUSAL)
    if (log.count(LEDGER_REFUSAL) != 1 or log.count(HEARTBEAT_CLOSE_REFUSAL) != 1
            or "skipping SessionDB close/checkpoint" not in after
            or "Traceback" in before or "Traceback" in after
            or diagnostic.count("Traceback (most recent call last):") != 2
            or "mark_subagents_gateway_shutdown" not in diagnostic
            or diagnostic.splitlines().count("persistence.sqlite._state.write.WriterMutexTimeout: " + MUTEX_TIMEOUT) != 1
            or diagnostic.splitlines().count("persistence.core.errors.StoreContention: " + MUTEX_TIMEOUT) != 1):
        raise ValueError("Full stop lacks the expected ledger refusal and heartbeat close decision")
    return dict(stopReturnedWithLiveTransaction=True, rowAtStopReturn=1, ledgerWriteTimedOut=True,
                elapsedSeconds=seconds)


def check_case(folder: Path, variant: str, case: str) -> dict:
    action, interval = cases(variant)[case]
    terminal = gate.read_json(folder / "terminal.json")
    if terminal != {"state": "TERMINAL", "returnCode": 0}:
        raise ValueError(f"Heartbeat process not verified terminal: {case}")
    observation = gate.read_json(folder / "before-exit.json")
    if (observation["action"], observation["interval"], observation["registered"],
            observation["guardDenials"]) != (action, interval, 1, []):
        raise ValueError(f"Heartbeat observation differs from the intended case: {case}")
    log = (folder / "process.log").read_text(encoding="utf8")
    if (log.splitlines().count(RETURN_MARKER) != 1 or "FIXTURE_BOUNDARY_DENIAL" in log
            or (action != "held-stop" and "Traceback" in log) or "Backend heartbeat cleanup failed" in log):
        raise ValueError(f"Heartbeat action or exit produced a refusal: {case}")
    post = gate.read_json(folder / "post-exit.json")
    pristine = variant == "pristine"
    expected_rows = int(pristine and interval == "0")
    expected_during = int(pristine or action == "exit")
    if (observation["rowsAfterAction"], post["rows"], post["integrity"]) != (expected_during, expected_rows, "ok"):
        raise ValueError(f"Heartbeat rows or SQLite integrity did not match: {case}")
    reported_reopen = REOPEN_WARNING in log
    if reported_reopen != (pristine and interval != "0"):
        raise ValueError(f"Unexpected database reopen observation: {case}")
    if action in ("gate", "overlap", "held-stop"):
        if (observation["heartbeatTransactions"] != 1 or
                observation["closeGateHeldOpen"] is not (action == "gate") or
                observation["observedCompetingWriter"] is not (action != "gate")):
            raise ValueError(f"Missing exact contention outcome: {case}")
        if action == "gate" and not 0 <= observation["elapsedSeconds"] < 4:
            raise ValueError("Heartbeat close gate exceeded its bounded window")
    if action == "gate" and "skipping SessionDB close/checkpoint" not in log:
        raise ValueError("Close gate did not record the held write")
    result = dict(rowsAfterAction=expected_during, rowsAfterExit=expected_rows, sqliteIntegrity="ok",
                  reopenWarning=reported_reopen, logSha256=gate.snapshot.digest(log.encode()))
    if action == "held-stop":
        result.update(held_stop_evidence(observation, log))
    return result


def supported_runtime(python: Path, evidence: Path) -> Path:
    script = (
        "import json,sys,sysconfig; "
        "print(json.dumps(dict(executable=sys.executable,prefix=sys.prefix,"
        "site=sysconfig.get_path('purelib'),platformSite=sysconfig.get_path('platlib'))))"
    )
    observed = json.loads(subprocess.check_output([str(python), "-I", "-B", "-c", script], text=True))
    expected = gate.read_json(evidence / "runtime.json")
    if (observed["executable"], observed["prefix"]) != (expected["executable"], expected["prefix"]):
        raise ValueError("Lifecycle interpreter differs from the supported PM test environment")
    site = Path(observed["site"]).resolve(strict=True)
    if site != Path(observed["platformSite"]).resolve(strict=True) or not site.is_relative_to(Path(observed["prefix"]).resolve()):
        raise ValueError("Unexpected PM interpreter package directory")
    if expected["system"] != "Linux" or not site.is_dir():
        raise ValueError("Expected the existing native Linux PM side environment")
    return site


def run(source: Path, evidence: Path, scratch: Path, python: Path) -> None:
    gate.require_linux()
    if (scratch.exists() or any(a.is_relative_to(b) for a, b in (
            (scratch, evidence), (evidence, scratch), (scratch, source), (source, scratch)))):
        raise ValueError("Lifecycle scratch must be fresh and separate from source and artifact evidence")
    before = gate.read_json(evidence / "before.json")
    commit, variant = before["sourceCommit"], before["variant"]
    if commit not in EXPECTED_PIN_COMMITS:
        raise ValueError("The native fixture requires the reviewed public persistence interfaces")
    expected = gate.expected_map(gate.read_json(evidence / "base-map.json"), variant, commit)
    gate.inspect_source(source, expected, before["modes"], generated=True)
    gate.source_index(source, commit)
    site = supported_runtime(python, evidence)
    scratch.mkdir(mode=0o700)
    target = evidence / "lifecycle"
    target.mkdir(mode=0o700)
    child_hash = gate.snapshot.digest(CHILD.read_bytes())
    gate.write_new(target / "intent.json", dict(sourceCommit=commit, variant=variant,
                  sourceMapSha256=before["sourceMapSha256"], childSha256=child_hash))
    for case, (action, interval) in cases(variant).items():
        case_out = target / case
        case_out.mkdir(mode=0o700)
        fixture = scratch / case
        argv = [str(python), "-I", "-S", "-B", str(CHILD), str(source), str(fixture),
                str(site), action, interval]
        gate.write_new(case_out / "intent.json", dict(case=case, action=action, interval=interval))
        with (case_out / "process.log").open("xb") as log:
            child = subprocess.Popen(argv, cwd=scratch, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
            try:
                rc = child.wait(timeout=45)
            except subprocess.TimeoutExpired:
                gate.write_new(case_out / "terminal.json", dict(state="UNKNOWN_PROCESS_PRESERVED_NO_REPLAY", pid=child.pid))
                raise
        gate.write_new(case_out / "terminal.json", dict(state="TERMINAL", returnCode=rc))
        if rc == 0:
            gate.write_new(case_out / "before-exit.json", gate.read_json(fixture / "before-exit.json"))
            rows, integrity = read_post_exit(fixture / "profile/state.db")
            gate.write_new(case_out / "post-exit.json", dict(rows=rows, integrity=integrity))
    if child_hash != gate.snapshot.digest(CHILD.read_bytes()):
        raise ValueError("Lifecycle child changed during execution")
    results = {case: check_case(target / case, variant, case) for case in cases(variant)}
    gate.write_new(target / "result.json", results)
    print(f"Verified {len(results)} native heartbeat process exits at {commit} ({variant}).")


def verify_cohort(artifact: Path, commit: str, variant: str) -> dict:
    canonical = gate.accepted_evidence(artifact)
    lifecycle = artifact / "lifecycle"
    if canonical["sourceCommit"] != commit or canonical["variant"] != variant:
        raise ValueError("Canonical lifecycle artifact has the wrong pin or variant")
    expected_intent = dict(sourceCommit=commit, variant=variant, sourceMapSha256=canonical["sourceMapSha256"],
                           childSha256=gate.snapshot.digest(CHILD.read_bytes()))
    if gate.read_json(lifecycle / "intent.json") != expected_intent:
        raise ValueError("Lifecycle source or child digest mismatch")
    if {p.name for p in lifecycle.iterdir() if p.is_dir()} != set(cases(variant)):
        raise ValueError("Unexpected or missing lifecycle scenarios")
    checked = {}
    for case, (action, interval) in cases(variant).items():
        folder = lifecycle / case
        if gate.read_json(folder / "intent.json") != {"case": case, "action": action, "interval": interval}:
            raise ValueError("Lifecycle invocation evidence differs")
        checked[case] = check_case(folder, variant, case)
    if gate.read_json(lifecycle / "result.json") != checked:
        raise ValueError("Lifecycle results differ from the process evidence")
    return checked


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    execute = commands.add_parser("run")
    for name in ("source", "evidence", "scratch", "python"):
        execute.add_argument("--" + name, required=True, type=Path)
    compare = commands.add_parser("compare")
    for name in ("pristine", "candidate", "output"):
        compare.add_argument("--" + name, required=True, type=Path)
    compare.add_argument("--source-commit", choices=EXPECTED_PIN_COMMITS, required=True)
    args = parser.parse_args()
    if args.command == "run":
        run(args.source.resolve(strict=True), args.evidence.resolve(strict=True),
            args.scratch.resolve(), args.python.absolute())
    else:
        pristine = verify_cohort(args.pristine, args.source_commit, "pristine")
        candidate = verify_cohort(args.candidate, args.source_commit, "candidate")
        gate.write_new(args.output, dict(sourceCommit=args.source_commit, pristine=pristine, candidate=candidate))
        print(f"Verified paired Linux heartbeat lifecycle evidence for {args.source_commit}.")


if __name__ == "__main__":
    main()
