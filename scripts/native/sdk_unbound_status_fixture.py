"""Actual maintained SDK turn-scope/status component in one generated home.

The corrected SDK lives in a separately declared source copy. The original
observer is loaded under a separate name; original staged SDK files remain intact. This does not exercise the gateway wire path, project
execution, inference, fleet admission or production deployment.
"""
from contextlib import closing
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import sys
from types import SimpleNamespace

ORIGINAL_STATUS='42554afb1c28dc582ab0004a78c8a6be9cf566f518ffd98834cba7b5b74af6f2'
CANDIDATE_STATUS='52eb79ddc1daec1f7834526fdab89292cd4a923a4e76ddc2a01f44d2dcdaa707'


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def home_image(home):
    """Whole generated-home file content and schema, before/after observation."""
    result={p.relative_to(home).as_posix():digest(p) for p in home.rglob('*') if p.is_file()}
    return result


def run(root,source,original_status):
    root,source,original_status=Path(root).resolve(strict=True),Path(source).resolve(strict=True),Path(original_status).resolve(strict=True)
    if (sys.platform!='linux' or not sys.flags.isolated or not sys.dont_write_bytecode
        or os.geteuid()==0 or not original_status.is_relative_to(root)
        or os.environ.get('HERMES_HOME')!=str(root/'profile-a')
        or digest(source/'agent/project_control_status.py')!=CANDIDATE_STATUS
        or digest(original_status)!=ORIGINAL_STATUS):
        raise ValueError('Reviewed isolated candidate and generated home required')
    sys.path.insert(0,str(source))
    from agent import project_control_status as fixed
    from agent import project_peer_request as peer
    from agent.project_control_store import Ledger,process_birth
    from agent.project_session_scope import turn_scope,passthrough_active
    from contextgraph.sessions import SessionRegistry
    for module in (fixed,peer,sys.modules['agent.project_control_store'],sys.modules['agent.project_session_scope']):
        if not Path(module.__file__).resolve().is_relative_to(source):raise ValueError('Actual reviewed SDK origin required')
    spec=importlib.util.spec_from_file_location('signal_grove_original_unbound_status',original_status)
    original=importlib.util.module_from_spec(spec);spec.loader.exec_module(original)
    homes={name:root/name for name in ('profile-a','profile-b')}
    for home in homes.values():
        if not home.is_dir():raise ValueError('Prepared generated profile required')
        with SessionRegistry(home/'session-registry.db'):pass
    a,b=homes.values();ledger=Ledger(a/'project-control.db');other=Ledger(b/'project-control.db');other.close()
    # readonly describes the returned configuration object, not cold-loader I/O.
    # Keep the actual SDK's default initialization outside read-only observations.
    warm_before=home_image(a)
    from hermes_cli.config import load_config_readonly
    configured=load_config_readonly()
    assert configured['project_control']['enabled'] is True and configured['project_control']['enrollment_mode']=='enrolled_sessions'
    warm_after=home_image(a);added=set(warm_after)-set(warm_before)
    assert all(warm_after.get(n)==h for n,h in warm_before.items())
    backups=[n for n in added if n.startswith('backups/config/config.yaml.good.')]
    assert added=={'SOUL.md',*backups} and len(backups)==1
    assert warm_after['SOUL.md']=='36c1f5a2e92cd1d018311eaf4c8f1e8886672eae78212e033c681d0e3d5d506f'
    assert warm_after[backups[0]]==warm_before['config.yaml']
    observations=[]
    def observe(label,home,expected,*,old_count=None):
        before=home_image(home);value=fixed.observe(home);after=home_image(home)
        assert before==after,'Read-only observation changed generated files'
        assert value['fleet_ready_to_switch'] is False and value['responding_pid']==os.getpid()
        if expected is None:
            assert value['state']=='UNKNOWN' and value['reason']=='NATIVE_ADMISSION_UNPROVEN' and 'fence' not in value
        else:
            assert value['state']=='OBSERVED' and value['fence']['active_turns']==expected
            assert all(value['fence'][key]==0 for key in ('active_tools','processes_awaiting_reconciliation','unverified_operations'))
        if old_count is not None:
            prior=original.observe(home);assert prior['state']=='OBSERVED' and prior['fence']['active_turns']==old_count
            assert home_image(home)==before
        observations.append(dict(label=label,profile=home.name,result=value,files_unchanged=True,original_active_turns=old_count))
        return value
    def actual_owned_unbound(expected):
        rows=ledger.db.execute("SELECT owner_pid,owner_birth,phase FROM unbound_turns WHERE phase!='FINISHED'").fetchall()
        assert len(rows)==expected and all(tuple(row)==(os.getpid(),process_birth(os.getpid()),'ACTIVE') for row in rows)
        assert ledger.db.execute("SELECT count(*) FROM turn_admissions").fetchone()[0]==0
        assert ledger.fence_status()['active_turns']==expected
    try:
        observe('older-complete-ledger-with-no-unbound-tables',a,0,old_count=0)
        token=peer.current_request.set({'fixture_context_only':True})
        try:
            before=home_image(a)
            with turn_scope(SimpleNamespace(session_id='fixture-peer-requires-strict-path')) as strict:
                assert strict is True
            try:peer.require_enabled(False)
            except PermissionError:pass
            else:raise AssertionError('Peer context bypassed the native-enabled guard')
            assert home_image(a)==before
        finally:peer.current_request.reset(token)
        observe('peer-context-requires-strict-admission-without-unbound-receipt',a,0)
        with turn_scope(SimpleNamespace(session_id='fixture-unbound-outer')) as enrolled:
            assert enrolled is False and passthrough_active('fixture-unbound-outer') is True
            actual_owned_unbound(1);observe('actual-outer-turn-active',a,1,old_count=0)
            va=observations[-1]['result'];vb=observe('other-profile-remains-idle',b,0)
            assert va['home_digest']!=vb['home_digest']
            with turn_scope(SimpleNamespace(session_id='fixture-unbound-child',_parent_session_id='fixture-unbound-outer')) as child_enrolled:
                assert child_enrolled is False and passthrough_active('fixture-unbound-child') is True
                actual_owned_unbound(2);observe('actual-parent-and-child-active',a,2,old_count=0)
            actual_owned_unbound(1);observe('child-finishes-outer-stays-active',a,1,old_count=0)
            fence=ledger.freeze('fixture-unbound-status-freeze');assert fence['epoch']==1 and fence['active_turns']==1
            frozen=observe('freeze-does-not-hide-existing-turn',a,1,old_count=0);assert frozen['fence']['frozen'] is True
            before=home_image(a)
            try:
                with turn_scope(SimpleNamespace(session_id='fixture-unbound-refused')):raise AssertionError('Admission passed frozen fence')
            except RuntimeError:pass
            else:raise AssertionError('Missing actual SDK admission refusal')
            assert home_image(a)==before
            try:ledger.thaw('fixture-unbound-status-freeze',1)
            except RuntimeError:pass
            else:raise AssertionError('Native fence released while a turn remained active')
            assert home_image(a)==before
            observe('refused-new-admission-preserves-existing-turn',a,1,old_count=0)
        assert passthrough_active() is False
        actual_owned_unbound(0);value=observe('existing-turn-completes-while-frozen',a,0,old_count=0)
        assert value['fence']['frozen'] is True and value['fence']['owner']=='fixture-unbound-status-freeze' and value['fence']['epoch']==1
        assert ledger.db.execute("SELECT count(*) FROM unbound_turns WHERE phase='FINISHED'").fetchone()[0]==2
    finally:ledger.close()
    # Each error fixture is a distinct generated home, never an existing profile.
    for name,ddl in (
        ('incomplete-turn-table',"DROP TABLE unbound_turn_sessions"),
        ('incomplete-session-table',"DROP TABLE unbound_turns"),
        ('missing-phase-column',"DROP TABLE unbound_turns; CREATE TABLE unbound_turns (receipt TEXT PRIMARY KEY)")):
        home=root/name;home.mkdir(mode=0o700);(home/'config.yaml').write_bytes((a/'config.yaml').read_bytes())
        with closing(sqlite3.connect((a/'project-control.db').as_uri()+'?mode=ro',uri=True)) as src,closing(sqlite3.connect(home/'project-control.db')) as dst:
            src.backup(dst);dst.executescript(ddl);dst.commit()
        observe(name,home,None)
    missing=root/'missing-ledger';missing.mkdir(mode=0o700);(missing/'config.yaml').write_bytes((a/'config.yaml').read_bytes())
    observe('missing-ledger-never-created',missing,None);assert not (missing/'project-control.db').exists()
    with closing(sqlite3.connect(a/'project-control.db',isolation_level=None)) as writer:
        writer.execute('BEGIN EXCLUSIVE')
        try:observe('exclusive-lock-is-unknown-not-idle',a,None)
        finally:writer.execute('ROLLBACK')
    observe('read-after-exclusive-lock-released',a,0)
    (a/'project-control.db').chmod(0o400)
    observe('readonly-ledger-no-schema-or-counter-writes',a,0)
    assert original.observe(a)['fence']['active_turns']==0 and digest(original_status)==ORIGINAL_STATUS and digest(source/'agent/project_control_status.py')==CANDIDATE_STATUS
    return dict(state='ACTUAL_UNBOUND_TURN_STATUS_COMPONENT_VERIFIED',observations=observations,
        actual_turns_started=2,actual_finished_turns=2,frozen_new_admission_refused=True,active_thaw_refused=True,
        readonly_observations=len(observations),cold_config_initialization=dict(added=sorted(added),existing_files_unchanged=True,backup_matches_config=True),sdk_functions_patched=False,original_observer_unchanged=True,corrected_sdk_copy_qualified=True,peer_context_strict_branch_preserved=True,
        gateway_wire_accepted=False,concurrent_snapshot_race_accepted=False,fleet_accepted=False,live_changes=False)
