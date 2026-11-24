"""Exercise the real service stages through the coordinator on a disposable user manager."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from ci_native_policy import native_authority, private_directory, write_json
from cutover_coordinator import (
    ApprovedExecution, CutoverCoordinator, FORWARD, Observation, Plan, READ_ONLY, RECOVERY,
    RecoveryCase, required_checks,
)
from cutover_journal import CutoverJournal, Unknown
from native_service_manager import BusAuthority, NativeServiceManager, UnitBinding
from native_retirement_owner import read_process_owner
from retirement_guard import Owner

ROLES = ("gateway", "serve")
EXE = "/usr/bin/sleep"
DURATIONS = {"old": ("150", "151"), "new": ("160", "161")}
STOPS = {"stop_gateway": "gateway", "stop_serve": "serve"}
STARTS = {"start_gateway": "gateway", "start_serve": "serve"}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def systemctl(*args):
    return subprocess.run(["systemctl", "--user", *args], check=True, capture_output=True, timeout=35).stdout


def argv(role, generation):
    return EXE, DURATIONS[generation][ROLES.index(role)]


def fixture_text(role, generation):
    return (
        f"[Service]\nType=exec\nExecStart={' '.join(argv(role, generation))}\n"
        "KillMode=control-group\nRuntimeMaxSec=140\n"
    )


def expected_configuration(path, role, generation):
    return digest(dict(unit=path.name, fragment=str(path), dropins=[], kill_mode="control-group",
                       service_type="exec", commands={"ExecStart": ((EXE, argv(role, generation), False),),
                                                      "ExecStop": (), "ExecStopPost": ()}))


def snapshots(manager):
    return tuple(manager.snapshot(role) for role in ROLES)


def snapshot_digest(units):
    return digest([unit.sha256 for unit in units])


class ServiceOperations:
    """Only service actions and the fixture's unit definitions have native effects."""

    def __init__(self, manager, paths, work):
        self.manager, self.paths, self.work = manager, paths, work
        self.loaded = self.definitions = "old"
        self.running = set(ROLES)
        self.before = {}
        self.receipts = {}
        self.proofs = {}
        self.synthetic_steps = []
        self.fault_injected = False

    def replace_definitions(self, generation):
        assert not self.running
        for role, path in self.paths.items():
            assert path.is_file() and not path.is_symlink()
            assert path.read_bytes() == fixture_text(role, self.definitions).encode("ascii")
            staged = path.with_suffix(".fixture-tmp")
            with staged.open("x", encoding="ascii", newline="\n") as stream:
                stream.write(fixture_text(role, generation))
            staged.replace(path)
        self.definitions = generation

    def observe(self, step, phase, execution):
        states = snapshots(self.manager)
        for role, unit in zip(ROLES, states, strict=True):
            known = expected_configuration(self.paths[role], role, self.loaded)
            assert unit.unit == self.paths[role].name and unit.load_state == "loaded", (step, role, unit.load_state)
            assert unit.configuration_sha256 == known, (step, role, "commands", unit.configuration_sha256, known)
            assert unit.needs_daemon_reload == (self.definitions != self.loaded), (step, role, "reload flag")
            assert unit.job_id == unit.control_pid == 0, (step, role, "pending job or control process")
            assert unit.active_state == ("active" if role in self.running else "inactive"), (step, role, unit.active_state)
            if role in self.running:
                assert unit.owner is not None and unit.executable == EXE, (step, role, "owner", unit.executable)
                cmdline = Path("/proc", str(unit.main_pid), "cmdline").read_bytes()
                assert cmdline.split(b"\0") == [*(arg.encode() for arg in argv(role, self.loaded)), b""], (
                    step, role, "command line", cmdline)
            else:
                assert unit.main_pid == 0 and unit.owner is None, (step, role, "inactive owner")

        observed = snapshot_digest(states)
        receipt = self.receipts.get(step)
        if phase == "before":
            self.before[step] = states
        elif step not in READ_ONLY:
            assert receipt is not None or step in self.synthetic_steps, step
        if phase == "after" and receipt is not None:
            actual = receipt["after_sha256"]
            if isinstance(actual, dict):
                assert actual == dict(zip(ROLES, (unit.sha256 for unit in states), strict=True)), step
            else:
                role = next(role for role in ROLES if receipt["unit"] == self.paths[role].name)
                assert actual == states[ROLES.index(role)].sha256, step

        proof = dict(step=step, phase=phase, operation=execution.operation, snapshots=observed,
                     receipt=receipt, synthetic_prerequisites=True)
        proof_sha = digest(proof)
        if phase == "after":
            self.proofs[step] = proof
            with (self.work / "observations.jsonl").open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(proof, sort_keys=True) + "\n")
        # The checked unit state and known fixture effects support the native
        # observations. Other coordinator prerequisites are fixture assertions.
        return Observation(execution.operation, execution.packet_sha256, execution.authority_sha256,
                           execution.scope, step, phase, time.monotonic(), observed, proof_sha,
                           tuple((name, True) for name in sorted(required_checks(step, phase))))

    def perform(self, step, execution):
        base = step.removeprefix("rollback_")
        if base in STOPS or base in STARTS:
            stopping = base in STOPS
            role = (STOPS if stopping else STARTS)[base]
            self.receipts[step] = self.manager.change(
                role, "stop" if stopping else "start", self.before[step][ROLES.index(role)], execution)
            if stopping:
                self.running.remove(role)
            else:
                self.running.add(role)
        elif step in {"reload_units", "rollback_reload"}:
            self.receipts[step] = self.manager.reload_units(self.before[step], execution)
            self.loaded = self.definitions
        elif step in {"bind_cli", "rollback_runtime"}:
            generation = "new" if step == "bind_cli" else "old"
            self.replace_definitions(generation)
            self.synthetic_steps.append(step)
        elif step == "backup_post":
            self.fault_injected = True
            raise OSError("Intentional fixture fault after verified replacement startup")
        else:
            self.synthetic_steps.append(step)


def main():
    if (sys.platform != "linux" or os.geteuid() == 0 or os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted"):
        raise ValueError("A disposable, unprivileged GitHub-hosted Linux test manager is required")
    source = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[a-f0-9]{40}", source):
        raise ValueError("Exact hosted source identity is required")
    work = Path(os.environ["RUNNER_TEMP"]).resolve(strict=True) / "signal-grove-reload-fixture"
    work.mkdir(mode=0o700)
    private_directory(work)
    seed = uuid.uuid4().hex
    unit_home = private_directory(Path("/run/user") / str(os.geteuid())) / "systemd/user"
    unit_home.mkdir(parents=True, exist_ok=True)
    if unit_home.resolve() != unit_home or unit_home.stat().st_uid != os.geteuid():
        raise ValueError("The test manager's runtime unit directory must be locally owned")
    paths = {role: unit_home / f"signal-grove-acceptance-{seed}-{role}.service" for role in ROLES}
    for role, path in paths.items():
        with path.open("x", encoding="ascii", newline="\n") as stream:
            stream.write(fixture_text(role, "old"))
    systemctl("daemon-reload")
    systemctl("start", *(path.name for path in paths.values()))
    bus = native_authority()
    authority = BusAuthority(bus["uid"], *bus["busIdentity"], Owner(**bus["peerOwner"]),
                             bus["managerUnique"], Owner(**bus["managerOwner"]))
    bindings = tuple(UnitBinding(role, paths[role].name, expected_configuration(paths[role], role, "old"),
                        expected_configuration(paths[role], role, "new"), EXE, EXE) for role in ROLES)
    review = Plan(digest([seed, "forward"]), digest(bus), digest([seed, "preflight"]), digest([seed, "recovery"]))
    info = work.stat()
    with CutoverJournal(work, expected_identity=(info.st_dev, info.st_ino)) as journal:
        with NativeServiceManager(authority, review, journal, bindings) as observation:
            old = snapshots(observation)
        plan = Plan(review.packet_sha256, review.authority_sha256, snapshot_digest(old), review.rollback_packet_sha256)
        with NativeServiceManager(authority, plan, journal, bindings) as manager:
            operations = ServiceOperations(manager, paths, work)
            forward = ApprovedExecution(uuid.uuid4().hex, plan.packet_sha256, plan.authority_sha256, "forward",
                                        digest([seed, "fixture-forward"]), time.monotonic() + 85)
            checked = operations.observe("preflight", "before", forward)
            assert checked.snapshot_sha256 == plan.preflight_snapshot_sha256, "Reviewed fixture preflight drifted"
            try:
                CutoverCoordinator(plan, journal, operations).run(forward)
            except Unknown:
                assert operations.fault_injected and journal.view["pending"] == "backup_post"
            else:
                raise AssertionError("Expected the fixture fault after confirmed replacement startup")
            replaced = snapshots(manager)
            assert all(before.invocation != after.invocation for before, after in zip(old, replaced, strict=True))
            assert operations.proofs["verify_new"]["snapshots"] == snapshot_digest(replaced)

            recovery_packet = digest([seed, journal.history_sha256])
            case = RecoveryCase(recovery_packet, plan.rollback_packet_sha256, forward.operation, forward.packet_sha256,
                                journal.history_sha256, plan.packet_sha256, forward.binding_sha256, snapshot_digest(replaced))
            recovery = ApprovedExecution(uuid.uuid4().hex, recovery_packet, plan.authority_sha256, "recovery",
                                         digest([seed, "fixture-recovery"]), time.monotonic() + 85)
            result = CutoverCoordinator(plan, journal, operations).recover(recovery, case)
            restored = snapshots(manager)
            assert result["completed_steps"] == list(RECOVERY)
            assert operations.proofs["verify_rollback"]["snapshots"] == snapshot_digest(restored)
            assert operations.loaded == operations.definitions == "old"
            assert all(len({a.invocation, b.invocation, c.invocation}) == 3
                       for a, b, c in zip(old, replaced, restored, strict=True))
            expected_native = (*FORWARD[6:8], *FORWARD[14:17], *RECOVERY[4:6], *RECOVERY[8:11])
            assert tuple(operations.receipts) == expected_native
            report = dict(source=source, state="verified", originalOperation=forward.operation,
                          forwardHold="backup_post", recovery=result, receipts=operations.receipts,
                          journal_sha256=journal.history_sha256, sameManager=native_authority() == bus,
                          synthetic_prerequisites=True, verified_units=[path.name for path in paths.values()])

    systemctl("stop", *(path.name for path in paths.values()))
    assert all(read_process_owner(unit.main_pid) != unit.owner for unit in restored)
    for role, path in paths.items():
        assert path.is_file() and not path.is_symlink() and path.read_bytes() == fixture_text(role, "old").encode("ascii")
        path.unlink()
    systemctl("daemon-reload")
    report["fixtureCleanupVerified"] = True
    assert report["sameManager"]
    write_json(work / "result.json", report)
    print(json.dumps(report))


if __name__ == "__main__":
    main()
