"""Disposable child for the pinned Hermes heartbeat lifecycle gate."""
from __future__ import annotations

import asyncio
from contextlib import closing
import json
import logging
import os
from pathlib import Path
import sqlite3
import sys
import threading
import time
from urllib.parse import unquote, urlsplit


GIT_VERSION_READS = {
    ("rev-parse", "HEAD"),
    ("branch", "--show-current"),
    ("log", "-1", "--format=%ct", "HEAD"),
    ("--no-optional-locks", "status", "--porcelain", "-uno"),
    ("tag", "--merged", "HEAD", "--list", "v[0-9]*"),
    ("describe", "--tags", "--long", "--match", "v2[0-9][0-9][0-9].*", "HEAD"),
    ("rev-parse", "--short=7", "HEAD"),
}


def checkout_version_read(executable, argv, cwd, env, source: Path) -> bool:
    return (executable == "git" and type(argv) is list and argv[:1] == ["git"]
            and all(type(item) is str for item in argv)
            and tuple(argv[1:]) in GIT_VERSION_READS and env is None
            and isinstance(cwd, (str, os.PathLike)) and Path(cwd).resolve() == source)


def same_account_process_stat(path: Path) -> bool:
    parts = path.parts
    if (len(parts) != 4 or parts[:2] != ("/", "proc") or parts[3] != "stat"
            or not parts[2].isascii() or not parts[2].isdigit()):
        return False
    try:
        return path.stat().st_uid == os.geteuid()
    except OSError:
        return False


def constrain_child(
    source: Path, fixture: Path, site: Path, interval: str, *,
    config: str = "updates:\n  check: false\n", recovering: bool = False,
) -> tuple[Path, list[str]]:
    home, account, temp = (fixture / name for name in ("profile", "account", "temp"))
    settings = home / "config.yaml"
    if recovering:
        for directory in (fixture, home, account, temp):
            assert directory.is_dir() and not directory.is_symlink()
        assert settings.is_file() and not settings.is_symlink()
        assert settings.read_text(encoding="utf8") == config
        assert (home / "state.db").is_file() and not (home / "state.db").is_symlink()
    else:
        fixture.mkdir(mode=0o700)
        for directory in (home, account, temp):
            directory.mkdir(mode=0o700)
        with settings.open("x", encoding="utf8", newline="\n") as stream:
            stream.write(config)
    system = {name: os.environ[name] for name in ("SYSTEMROOT", "WINDIR", "PATH", "COMSPEC")
              if name in os.environ}
    os.environ.clear()
    os.environ.update(system)
    os.environ.update(HERMES_HOME=str(home), HOME=str(account), USERPROFILE=str(account),
                      APPDATA=str(account), LOCALAPPDATA=str(account),
                      TEMP=str(temp), TMP=str(temp), TMPDIR=str(temp), XDG_CACHE_HOME=str(fixture / "cache"),
                      # Arm upstream's live-database test isolation for the disposable home.
                      HERMES_TEST_ISOLATION="1", HERMES_DISABLE_LAZY_INSTALLS="1",
                      HERMES_GATEWAY_HEARTBEAT_REFRESH_S=interval)
    if os.name == "posix":
        os.environ.update(PATH="/usr/bin:/bin", GIT_CONFIG_NOSYSTEM="1",
                          GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_SYSTEM=os.devnull,
                          GIT_OPTIONAL_LOCKS="0", GIT_NO_LAZY_FETCH="1", GIT_TERMINAL_PROMPT="0",
                          GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="core.fsmonitor", GIT_CONFIG_VALUE_0="false")
    os.chdir(fixture)
    sys.path[:0] = [str(source), str(site)]
    denials = []
    read_roots = (source, fixture, site, Path(sys.base_prefix).resolve(),
                  Path(__file__).resolve(), Path(sys.argv[0]).resolve())
    # psutil checks the init process birth time when it validates process identity.
    system_reads = {Path(name).resolve() for name in (
        "/etc/localtime", "/etc/mime.types", "/proc/stat", "/proc/1/cgroup",
        "/proc/1/stat", "/proc/self/mountinfo",
    )} if os.name == "posix" else set()
    if os.name == "nt":
        system_reads.update(Path(fixture.drive + name).resolve() for name in (
            "/proc/1/cgroup", "/proc/self/mountinfo", f"/proc/{os.getpid()}/stat",
        ))
    manifest = source.parent / "manifest.json"

    def refuse(event: str, target: str = "") -> None:
        detail = f"{event} {target}".strip()
        denials.append(detail)
        callers = []
        frame = sys._getframe(1)
        while frame is not None and len(callers) < 9:
            location = Path(frame.f_code.co_filename).name
            callers.append(f"{location}:{frame.f_lineno}:{frame.f_code.co_name}")
            frame = frame.f_back
        print(f"FIXTURE_BOUNDARY_DENIAL {detail} | {' -> '.join(callers)}", file=sys.stderr, flush=True)
        raise PermissionError(f"Disposable heartbeat boundary: {detail}")

    def audit(event, args):
        if event.startswith(("subprocess.", "socket.", "os.system", "os.startfile")):
            if event in ("socket.bind", "socket.connect"):
                # Windows asyncio uses a loopback TCP pair for its wakeup pipe.
                target = args[1]
                if isinstance(target, tuple) and target[0] in ("127.0.0.1", "::1"):
                    return
            if event == "subprocess.Popen":
                if os.name == "posix" and checkout_version_read(*args, source):
                    return
                program = Path(os.fsdecode(args[0])).name if args[0] is not None else ""
                argv = args[1]
                names = " ".join(str(arg)[:120] for arg in argv[:8]) if isinstance(argv, list) else program
                refuse(event, names)
            if event not in ("socket.__new__", "socket.gethostname"):
                refuse(event)
        if event == "open" and isinstance(args[0], (str, bytes, os.PathLike)):
            raw = os.fsdecode(args[0])
            path = Path(raw).resolve()
            mode, flags = args[1] or "", args[2] or 0
            writing = any(c in mode for c in "wax+") or bool(
                flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND)
            )
            if raw.lower() == os.devnull.lower():
                return
            if not writing and (path in system_reads or path == manifest and not path.exists()):
                return
            if not writing and os.name == "posix" and same_account_process_stat(path):
                return
            if not any(path.is_relative_to(root) for root in ((fixture,) if writing else read_roots)):
                refuse(event, str(path))
        if event == "sqlite3.connect" and args[0] != ":memory:":
            value = os.fsdecode(args[0])
            if value.startswith("file:"):
                uri = urlsplit(value)
                if uri.netloc:
                    refuse(event, "nonlocal file URI")
                value = unquote(uri.path)
                if os.name == "nt" and value[0:1] == "/" and value[2:3] == ":":
                    value = value[1:]
            if not Path(value).resolve().is_relative_to(fixture):
                refuse(event, "database outside fixture")
        if event in ("os.chdir", "os.mkdir", "os.remove", "os.rmdir", "os.rename", "os.link", "os.symlink"):
            targets = args[:2] if event in ("os.rename", "os.link", "os.symlink") else args[:1]
            if any(not Path(os.fsdecode(value)).resolve().is_relative_to(fixture) for value in targets):
                refuse(event, "filesystem mutation outside fixture")

    sys.addaudithook(audit)
    return home, denials


def count_rows(home: Path) -> int:
    db = home / "state.db"
    with closing(sqlite3.connect(db.as_uri() + "?mode=ro", uri=True)) as conn:
        return conn.execute("SELECT count(*) FROM gateway_heartbeats").fetchone()[0]


def run_contention(
    runner, server, home: Path, *, hold_at_close: bool, hold_through_stop: bool = False,
    expected_timeouts: tuple[float, ...] = (10.0,),
) -> dict:
    from persistence.sqlite._state.write import WriterMutexTimeout
    from persistence_assembly import close_all_stores
    from tests.fixtures.storage_holder import raw_holder

    holder = raw_holder(server._get_db())
    write = holder._execute_write
    entered, release, transaction_done = (threading.Event() for _ in range(3))
    competing, competing_done, saw_wait = (threading.Event() for _ in range(3))
    transactions = 0
    writer_timeouts = []
    row_while_held = None
    stop_returned_with_live_transaction = False

    def instrument_write(action, patience_s=None):
        if threading.current_thread().name != "hermes-gateway-heartbeat":
            if hold_at_close or not entered.is_set() or release.is_set():
                return write(action, patience_s=patience_s)
            competing.set()
            try:
                return write(action, patience_s=patience_s)
            except WriterMutexTimeout:
                writer_timeouts.append(patience_s)
                raise
            finally:
                competing_done.set()

        def hold(conn):
            nonlocal transactions
            assert conn.in_transaction
            result = action(conn)
            transactions += 1
            entered.set()
            if not release.wait(34 if hold_through_stop else 10):
                raise TimeoutError("Fixture did not release its heartbeat transaction")
            return result

        try:
            return write(hold, patience_s=patience_s)
        finally:
            transaction_done.set()

    def release_when_contended() -> None:
        try:
            if competing.wait(6) and not competing_done.wait(0.3):
                saw_wait.set()
        finally:
            release.set()

    holder._execute_write = instrument_write
    server._start_backend_heartbeat_refresher()
    assert count_rows(home) == 1 and entered.wait(5)
    assert holder._conn is not None and holder._conn.in_transaction
    start = time.monotonic()
    releaser = None
    try:
        if hold_at_close:
            ctx = runner._StopContext(deferred_count=lambda: 0, started_at=start)
            runner._stop_quiesce_and_close_session_dbs(10.0, ctx)
            elapsed = time.monotonic() - start
            assert elapsed < 4.0
            assert holder._conn is not None and holder._conn.in_transaction
            assert not transaction_done.is_set() and count_rows(home) == 1
            release.set()
            assert transaction_done.wait(5) and server._stop_backend_heartbeat_refresher(3)
            assert count_rows(home) == 0
            close_all_stores()
        elif hold_through_stop:
            # Observe the full upstream stop with the write still admitted, then
            # release the fixture transaction and inspect its original settlement.
            asyncio.run(asyncio.wait_for(runner.stop(), timeout=28))
            elapsed = time.monotonic() - start
            assert competing.is_set() and competing_done.is_set()
            assert tuple(writer_timeouts) == expected_timeouts, writer_timeouts
            assert not release.is_set() and not transaction_done.is_set()
            assert holder._conn is not None and holder._conn.in_transaction
            row_while_held = count_rows(home)
            assert row_while_held == 1
            stop_returned_with_live_transaction = True
            release.set()
            assert transaction_done.wait(5) and server._stop_backend_heartbeat_refresher(3)
            assert count_rows(home) == 0
            close_all_stores()
        else:
            releaser = threading.Thread(target=release_when_contended, daemon=True)
            releaser.start()
            asyncio.run(asyncio.wait_for(runner.stop(), timeout=12))
            elapsed = time.monotonic() - start
            assert saw_wait.is_set() and competing_done.is_set() and holder._conn is None
        assert transactions == 1 and count_rows(home) == 0
    finally:
        release.set()
        if releaser is not None:
            releaser.join(5)
            assert not releaser.is_alive()
    result = dict(registered=1, heartbeatTransactions=transactions, closeGateHeldOpen=hold_at_close,
                  observedCompetingWriter=saw_wait.is_set() or hold_through_stop, rowsAfterAction=0, elapsedSeconds=round(elapsed, 3))
    if hold_through_stop:
        result.update(rowAtStopReturn=row_while_held, competingWriterTimeouts=writer_timeouts,
                      stopReturnedWithLiveTransaction=stop_returned_with_live_transaction)
    return result


def main() -> None:
    if not (__debug__ and sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
        raise SystemExit("Expected -I -S -B with assertions enabled")
    source, fixture, site = (Path(path).resolve() for path in sys.argv[1:4])
    action, interval = sys.argv[4:]
    assert action in ("stop", "exit", "gate", "overlap", "held-stop") and interval in ("0", "0.5")
    assert all(path.is_dir() for path in (source, fixture.parent, site))
    assert not fixture.is_relative_to(source) and not source.is_relative_to(fixture)
    assert site.is_relative_to(Path(sys.prefix).resolve())
    home, denials = constrain_child(source, fixture, site, interval)
    print(f"FIXTURE_PROCESS pid={os.getpid()} parent={os.getppid()}", flush=True)
    from gateway.config import GatewayConfig
    from gateway.run import GatewayRunner
    from tui_gateway import server

    assert Path(server.__file__).resolve().is_relative_to(source)
    assert server._launch_home().resolve() == home
    logging.basicConfig(stream=sys.stderr, level=logging.INFO, force=True)
    runner = GatewayRunner(GatewayConfig()) if action != "exit" else None
    if action in ("gate", "overlap", "held-stop"):
        result = run_contention(runner, server, home, hold_at_close=action == "gate",
                                hold_through_stop=action == "held-stop")
    else:
        server._start_backend_heartbeat_refresher()
        assert count_rows(home) == 1
        if action == "stop":
            asyncio.run(asyncio.wait_for(runner.stop(), timeout=15))
        result = dict(registered=1, rowsAfterAction=count_rows(home))
    assert not denials
    result.update(action=action, interval=interval, guardDenials=denials)
    with (fixture / "before-exit.json").open("x", encoding="utf8", newline="\n") as stream:
        json.dump(result, stream, indent=2)
    print("NATIVE_HEARTBEAT_ACTION_RETURNED", flush=True)


if __name__ == "__main__":
    main()
