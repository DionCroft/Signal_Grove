"""Exercise two real configuration transitions on a disposable hosted user manager."""

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
from cutover_coordinator import ApprovedExecution, FORWARD, Plan, RECOVERY
from cutover_journal import CutoverJournal, Refused
from native_service_manager import BusAuthority, NativeServiceManager, UnitBinding
from retirement_guard import Owner

ROLES = ("gateway", "serve")
EXE = "/usr/bin/true"


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def systemctl(*args):
    result = subprocess.run(["systemctl", "--user", *args], check=True, capture_output=True, timeout=35)
    return result.stdout


def fixture_text(role, generation):
    return f"[Service]\nType=exec\nExecStart={EXE} {role}-{generation}\nKillMode=control-group\nRuntimeMaxSec=30\n"


def expected_configuration(path, role, generation):
    return digest(dict(unit=path.name, fragment=str(path), dropins=[], kill_mode="control-group",
                       service_type="exec", commands={"ExecStart": ((EXE, (EXE, f"{role}-{generation}"), False),),
                                                      "ExecStop": (), "ExecStopPost": ()}))


def authorize_prefix(journal, order, step):
    for prior in order[:order.index(step)]:
        journal.intent(prior)
        journal.verified(prior, digest({"fixturePrerequisite": prior}))
    journal.intent(step)


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
    run = uuid.uuid4().hex
    unit_home = private_directory(Path("/run/user") / str(os.geteuid())) / "systemd/user"
    unit_home.mkdir(parents=True, exist_ok=True)
    if unit_home.resolve() != unit_home or unit_home.stat().st_uid != os.geteuid():
        raise ValueError("The test manager's runtime unit directory must be locally owned")
    paths = {role: unit_home / f"signal-grove-acceptance-{run}-{role}.service" for role in ROLES}
    for role, path in paths.items():
        with path.open("x", encoding="ascii", newline="\n") as stream:
            stream.write(fixture_text(role, "old"))
    systemctl("daemon-reload")
    bus = native_authority()
    authority = BusAuthority(bus["uid"], *bus["busIdentity"], Owner(**bus["peerOwner"]),
                             bus["managerUnique"], Owner(**bus["managerOwner"]))
    plan = Plan(digest([run, "forward"]), digest(bus), digest([run, "preflight"]), digest([run, "recovery"]))
    bindings = tuple(UnitBinding(role, paths[role].name, expected_configuration(paths[role], role, "old"),
                        expected_configuration(paths[role], role, "new"), EXE, EXE) for role in ROLES)

    def replace_definitions(generation):
        for role, path in paths.items():
            staged = path.with_suffix(".fixture-tmp")
            with staged.open("x", encoding="ascii", newline="\n") as stream:
                stream.write(fixture_text(role, generation))
            staged.replace(path)

    def observe(manager, generation, reload_required):
        rows = tuple(manager.snapshot(role) for role in ROLES)
        for role, unit in zip(ROLES, rows, strict=True):
            assert unit.unit == paths[role].name and unit.load_state == "loaded"
            assert unit.active_state == "inactive" and unit.main_pid == unit.control_pid == unit.job_id == 0
            assert unit.configuration_sha256 == expected_configuration(paths[role], role, generation)
            assert unit.needs_daemon_reload is reload_required
        return rows

    def verify_reload(manager, journal, before, execution, expected_stage, generation):
        receipt = manager.reload_units(before, execution)
        assert receipt["step"] == expected_stage and receipt["operation"] == execution.operation
        after = observe(manager, generation, False)
        assert receipt["after_sha256"] == dict(zip(ROLES, (unit.sha256 for unit in after), strict=True))
        try:
            manager.reload_units(before, execution)
        except Refused:
            pass
        else:
            raise AssertionError("The spent reload was accepted again")
        journal.verified(expected_stage, digest(receipt))
        return receipt

    info = work.stat()
    with CutoverJournal(work, expected_identity=(info.st_dev, info.st_ino)) as journal, \
            NativeServiceManager(authority, plan, journal, bindings) as manager:
        for binding in bindings:
            manager._manager("LoadUnit", "s", (binding.unit,), "o")
            manager._manager("RefUnit", "s", (binding.unit,), "")
        observe(manager, "old", False)

        replace_definitions("new")
        before = observe(manager, "old", True)
        forward = ApprovedExecution(uuid.uuid4().hex, plan.packet_sha256, plan.authority_sha256,
                                    "forward", digest([run, "fixture-forward"]), time.monotonic() + 80)
        journal.begin(forward.operation, forward.packet_sha256, authorization_sha256=forward.binding_sha256)
        authorize_prefix(journal, FORWARD, "reload_units")
        applied = verify_reload(manager, journal, before, forward, "reload_units", "new")

        journal.unknown()
        replace_definitions("old")
        before = observe(manager, "new", True)
        recovery = ApprovedExecution(uuid.uuid4().hex, digest([run, journal.history_sha256]),
                                     plan.authority_sha256, "recovery", digest([run, "fixture-recovery"]),
                                     time.monotonic() + 80)
        journal.begin_recovery(recovery.operation, recovery.packet_sha256, original_operation=forward.operation,
                               original_plan_sha256=forward.packet_sha256, history_sha256=journal.history_sha256,
                               evidence_sha256=digest([run, "fixture-inactive-state"]),
                               authorization_sha256=recovery.binding_sha256)
        authorize_prefix(journal, RECOVERY, "rollback_reload")
        restored = verify_reload(manager, journal, before, recovery, "rollback_reload", "old")
        journal.complete(digest(restored))
        evidence = dict(source=source, state="verified", forward=applied, recovery=restored,
                        journal_sha256=journal.history_sha256, verified_units=[p.name for p in paths.values()],
                        prior_nonsystemd_checks="synthetic", target_services_started=False)
    assert native_authority() == bus
    for role, path in paths.items():
        assert path.is_file() and not path.is_symlink() and path.read_bytes() == fixture_text(role, "old").encode("ascii")
        path.unlink()
    systemctl("daemon-reload")
    report = {**evidence, "fixture_definitions_removed": True, "same_manager_owner": True}
    write_json(work / "result.json", report)
    print(json.dumps(report))


if __name__ == "__main__":
    main()
