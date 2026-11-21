"""Trial of real Hermes delegation and recovery using only synthetic input."""
from __future__ import annotations

import json
import logging
import os
from pathlib import Path
import sys
import threading
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
from shutdown_heartbeat_fixture import constrain_child, count_rows, run_contention


PARENT_ID = "shutdown-audit-parent"
GOAL = "Classify the interrupted synthetic delegation"
CONFIG = "updates:\n  check: false\ndelegation:\n  auto_resume: false\nagent:\n  cron_drain_timeout: 1\n"
CHECKPOINT = "Synthetic input persisted before shutdown"
RESULT = "delegation-probe.json"


def write_receipt(path, payload):
    with path.open("x", encoding="utf8", newline="\n") as stream:
        json.dump(payload, stream, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())


def ledger_snapshot(home, sid, did):
    from contextlib import closing
    import sqlite3

    db = home / "state.db"
    uri = db.as_uri() + "?mode=ro"
    with closing(sqlite3.connect(uri, uri=True)) as conn:
        state, owner, cause, raw = conn.execute(
            "SELECT state, owner_pid, stop_cause, record_json FROM resumable_subagents WHERE subagent_id=?", (sid,)
        ).fetchone()
        record = json.loads(raw)
        dstate, event, delivery = conn.execute(
            "SELECT state, event_json, delivery_state FROM async_delegations WHERE delegation_id=?", (did,)
        ).fetchone()
        units = conn.execute("SELECT count(*) FROM async_delegations").fetchone()[0]
        agent_count = conn.execute("SELECT count(*) FROM resumable_subagents").fetchone()[0]
        checkpoint = conn.execute(
            "SELECT count(*) FROM messages WHERE session_id=? AND content=?", (record["session_id"], CHECKPOINT)
        ).fetchone()[0]
        integrity = conn.execute("PRAGMA quick_check").fetchone()[0]
    return dict(childState=state, childStopCause=cause, childOwner=owner,
                childSession=record["session_id"], resumeMode=record.get("resume_mode"),
                lastOutcome=record.get("outcome"),
                event=json.loads(event) if event else None, delivery=delivery,
                unitState=dstate, units=units, agents=agent_count,
                retainedCheckpoint=checkpoint, sqliteIntegrity=integrity)


def produce(fixture: Path, home: Path, held: bool, denials: list[str]) -> None:
    from gateway.config import GatewayConfig
    from gateway.run import GatewayRunner
    from tests.fixtures.storage_seed import seed_messages, seed_session
    from tests.tools.test_delegate_continue import _ScriptedModel, _parent
    from tools.delegate_tool import delegate_task
    from tui_gateway import server

    assert server._launch_home().resolve() == home
    runner = GatewayRunner(GatewayConfig())
    assert runner._cron_drain_timeout == 1.0
    model = _ScriptedModel()
    started, interrupted, worker_release = (threading.Event() for _ in range(3))

    def build(**kwargs):
        child = model(**kwargs)
        child.interrupt.side_effect = lambda *_args, **_kw: interrupted.set()

        def run_conversation(*_args, **_kwargs):
            db = kwargs["session_db"]
            seed_session(db, child.session_id, "subagent", parent_session_id=kwargs["parent_session_id"])
            seed_messages(db, child.session_id, {"role": "user", "content": CHECKPOINT})
            started.set()
            assert worker_release.wait(44), "Parent must exit before a terminal result is emitted"
            return {"interrupted": True, "completed": False}

        child.run_conversation.side_effect = run_conversation
        return child

    parent = _parent(PARENT_ID)
    with patch("run_agent.AIAgent", side_effect=build):
        run_cwd = fixture / "run-cwd"
        run_cwd.mkdir(mode=0o700)
        os.chdir(run_cwd)
        dispatch = json.loads(delegate_task(tasks=[{"goal": GOAL}], background=True, parent_agent=parent))
        assert dispatch.get("status") == "dispatched", dispatch
        sid, = dispatch["subagent_ids"]
        did = dispatch["delegation_id"]
        try:
            assert started.wait(7), "Child did not reach its scripted checkpoint"
            before = ledger_snapshot(home, sid, did)
            assert before["childState"] == before["unitState"] == "running", before
            assert before["childOwner"] == os.getpid() and before["retainedCheckpoint"] == 1
            assert before["event"] is None and before["childStopCause"] is None
            witness = run_contention(runner, server, home, hold_at_close=False, hold_through_stop=held, expected_timeouts=(10.0, 10.0))
            assert interrupted.is_set() and not worker_release.is_set()
            after = ledger_snapshot(home, sid, did)
            assert after["childState"] == after["unitState"] == "running", after
            assert after["event"] is None and after["retainedCheckpoint"] == 1
            expected_cause = None if held else "gateway_shutdown"
            assert after["childStopCause"] == expected_cause, after
            assert len(model.builds) == 1 and count_rows(home) == 0
            assert not denials
            write_receipt(fixture / RESULT, dict(subagent=sid, delegation=did, held=held, owner=os.getpid(),
                          before=before, after=after, heartbeat=witness, syntheticBuilds=len(model.builds)))
            print("STOP_RETURNED_OWNER_ABORT_BOUNDARY", flush=True)
            # Emulate the owner disappearing after Stop but before the child can persist a terminal result.
            os._exit(0)
        finally:
            worker_release.set()


def recover(fixture, home):
    from tools.async_delegation import recover_abandoned_delegations
    from tools.delegate_tool_auto_resume import enabled

    prior = json.loads((fixture / RESULT).read_text(encoding="utf8"))
    sid, did = prior["subagent"], prior["delegation"]
    assert not (fixture / "run-cwd").exists()
    assert os.getpid() != prior["owner"] and enabled() is False
    before = ledger_snapshot(home, sid, did)
    assert before == prior["after"]
    with patch("run_agent.AIAgent", side_effect=AssertionError("Recovery tried to build a child")) as factory:
        first = recover_abandoned_delegations()
        after = ledger_snapshot(home, sid, did)
        second = recover_abandoned_delegations()
        final = ledger_snapshot(home, sid, did)
    assert first == 1 and second == 0 and after == final
    assert factory.call_count == 0
    assert after["childState"] == "resumable" and after["resumeMode"] == "AUTO_ON_BOOT"
    assert after["unitState"] == "unknown" and after["event"]["status"] == "unknown"
    assert after["event"]["resumable_subagent_ids"] == [sid]
    assert after["childSession"] == before["childSession"] and after["retainedCheckpoint"] == 1
    assert after["childStopCause"] == before["childStopCause"]
    assert after["units"] == after["agents"] == 1 and after["sqliteIntegrity"] == "ok"
    assert count_rows(home) == 0
    write_receipt(fixture / "recovery-result.json", dict(pid=os.getpid(), first=first, second=second, state=after,
                  newModelCalls=factory.call_count, autoResumeEnabled=False))
    print("NATIVE_RECOVERY_RETURNED_NO_REPLAY", flush=True)


def main():
    assert __debug__ and sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode
    sys.stdout.reconfigure(encoding='utf8')
    sys.stderr.reconfigure(encoding='utf8')
    source, fixture, site = (Path(path).resolve() for path in sys.argv[1:4])
    action, = sys.argv[4:]
    assert action in ("released", "held", "recover")
    assert fixture.parent.is_dir() and not source.is_relative_to(fixture)
    assert not fixture.is_relative_to(source) and site.is_relative_to(Path(sys.prefix).resolve())
    home, denials = constrain_child(source, fixture, site, "0.5", config=CONFIG, recovering=action == "recover")
    logging.basicConfig(stream=sys.stderr, level=logging.INFO, force=True)
    if action == "recover":
        recover(fixture, home)
        assert not denials
    else:
        produce(fixture, home, action == "held", denials)


if __name__ == "__main__":
    main()