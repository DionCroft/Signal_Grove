"""Finite owner-local TCP/procfs fixture; synthetic auth, no SDK/live endpoint."""
from pathlib import Path
import importlib.util
import json
import os
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from retirement_guard import Owner, RetirementGuard, Refused
from pinned_retirement_http import PinnedRetirementHttp
from native_retirement_owner import LinuxSocketOwner, read_process_owner


def run(output):
    output = Path(output)
    output.mkdir(mode=0o700)
    spec = importlib.util.spec_from_file_location('pinned_http_wire_fixture', ROOT / 'tests/test_pinned_retirement_http.py')
    wire_module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(wire_module)
    owner = read_process_owner(os.getpid())
    parent = read_process_owner(os.getppid())
    assert type(owner) is Owner and type(parent) is Owner and owner != parent
    expected = [dict(ok=True, pid=owner.pid, role='serve', protocolVersion=1),
                dict(ok=True, idle=True), dict(ok=True, idle=True, token=wire_module.PERMIT), dict(ok=True)]
    with wire_module.Wire([wire_module.reply(json.dumps(row).encode('utf8')) for row in expected]) as wire:
        deadline = time.monotonic() + 2
        while wire.connection is None and time.monotonic() < deadline:
            time.sleep(.01)
        assert wire.connection is not None
        native = LinuxSocketOwner(owner)
        assert native(wire.client) == owner
        assert LinuxSocketOwner(parent)(wire.client) is None
        assert LinuxSocketOwner(Owner(owner.pid, owner.start_ticks + 1, owner.boot_id))(wire.client) is None
        assert wire.requests == []
        # Actual parent identity plus same peer address/port is insufficient:
        # that process does not own the reverse established socket's inode.
        events = []
        journal = output / 'journal.jsonl'
        with journal.open('x', encoding='utf8') as stream:
            os.chmod(journal, 0o600)
            def record(event):
                text = json.dumps(event, sort_keys=True)
                assert wire_module.ACCESS not in text and wire_module.PERMIT not in text
                stream.write(text + '\n')
                stream.flush()
                os.fsync(stream.fileno())
                events.append(dict(event))
            adapter = PinnedRetirementHttp(owner, wire.client, wire_module.ACCESS, native)
            try:
                guard = RetirementGuard(owner, adapter, lambda: native(wire.client), record)
                assert guard.inspect() is True
                permit = guard.prepare()
                guard.commit(permit)
                assert guard.state == 'committed'
                try:
                    guard.commit(permit)
                    raise AssertionError('A spent permit reached the wire')
                except Refused:
                    pass
                assert len(wire.requests) == 4 and wire.accepts == 1
                assert native(wire.client) == owner
            finally:
                adapter.close()
        assert native(wire.client) is None
        assert all(x[2]['authorization'] == 'Bearer ' + wire_module.ACCESS for x in wire.requests)
        result = dict(state='NATIVE_KERNEL_SOCKET_AND_SINGLE_CONNECTION_GUARD_PASS',
                      owner=dict(pid=owner.pid, start_ticks=owner.start_ticks, boot_id=owner.boot_id),
                      wrongNativeParentRefused=True, reusedPidTicksRefused=True,
                      credentialsSentBeforeNativeProof=False, acceptedConnections=wire.accepts,
                      requests=4, positiveProtocolExchanges=4, spentPermitRequests=0,
                      closedSocketOwnerUnknown=True, journalRows=len(events),
                      syntheticProvider=True, sdkImported=any(x.startswith('hermes_cli') for x in sys.modules),
                      liveEndpointUsed=False, liveChanges=False)
    assert not result['sdkImported'] and not wire.thread.is_alive()
    result['fixtureThreadEnded'] = True
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    return result
