"""Run policy tests in a fresh, finite native user-manager unit.

The caller prepares Python/Jeepney and an existing user manager. This driver never
uses sudo, changes a live service or supplies a production credential. All homes,
keys and HTTP services used by the controlled tests are disposable fixtures.
"""
from __future__ import annotations
import argparse
from dataclasses import asdict
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import struct
import subprocess
import sys
import time
import unittest
import uuid
import zipfile

NATIVE_CLASSES = (
    'test_native_operational_paths.NativeOperationalPathsTests',
    'test_native_pm_preparation.NativePmPreparationTests',
    'test_private_backup_release.NativeBackupPublicationTests',
    'test_native_backup_transaction.NativeBackupTransactionTests',
)
PROPERTIES = ('Type=exec', 'RuntimeMaxSec=900', 'TimeoutStopSec=5',
              'KillMode=control-group', 'Restart=no', 'UMask=0077', 'TasksMax=512')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as out:
        json.dump(value, out, indent=2, ensure_ascii=True)
        out.write('\n');out.flush();os.fsync(out.fileno())


def private_directory(path):
    path = Path(path)
    s = path.lstat()
    if (path.resolve() != path or not stat.S_ISDIR(s.st_mode)
            or s.st_uid != os.geteuid() or stat.S_IMODE(s.st_mode) != 0o700):
        raise ValueError('An exact owned private directory is required')
    return path


def native_authority():
    # Same pinned protocol/runtime and native process observer as the adapter.
    from jeepney import HeaderFields, MessageType
    from jeepney.bus_messages import message_bus
    from jeepney.io.blocking import DBusConnection, prep_socket
    from native_retirement_owner import read_process_owner
    from importlib.metadata import version
    if version('jeepney') != '0.9.0':
        raise ValueError('Pinned Jeepney 0.9.0 required')
    uid = os.geteuid();parent = private_directory(Path('/run/user')/str(uid))
    bus = parent/'bus';before = bus.lstat()
    if (not stat.S_ISSOCK(before.st_mode) or before.st_uid != uid
            or before.st_nlink != 1 or bus.resolve() != bus):
        raise ValueError('Native user-bus ownership is unproven')
    raw = prep_socket(str(bus), timeout=2)
    connection = None
    try:
        peer_pid, peer_uid, _ = struct.unpack('3i', raw.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
        if peer_uid != uid:
            raise ValueError('Native bus peer has a different owner')
        connection = DBusConnection(raw, enable_fds=False)
        def call(message, signature):
            reply = connection.send_and_get_reply(message, timeout=3)
            if (reply.header.message_type != MessageType.method_return
                    or reply.header.fields.get(HeaderFields.sender) != 'org.freedesktop.DBus'
                    or reply.header.fields.get(HeaderFields.signature, '') != signature
                    or len(reply.body) != 1):
                raise ValueError('User-bus authority reply refused')
            return reply.body[0]
        unique = call(message_bus.GetNameOwner('org.freedesktop.systemd1'), 's')
        if not re.fullmatch(r':[0-9]+\.[0-9]+', unique):
            raise ValueError('Exact manager connection required')
        manager_pid = call(message_bus.GetConnectionUnixProcessID(unique), 'u')
        peer = read_process_owner(peer_pid);manager = read_process_owner(manager_pid)
        if (peer is None or manager is None or Path('/proc', str(peer_pid)).stat().st_uid != uid
                or Path('/proc', str(manager_pid)).stat().st_uid != uid
                or bus.lstat() != before):
            raise ValueError('Native manager incarnation changed')
        return dict(uid=uid, busIdentity=[before.st_dev, before.st_ino],
                    managerUnique=unique, peerOwner=asdict(peer), managerOwner=asdict(manager))
    finally:
        if connection is not None:connection.close()
        else:raw.close()


def generated_cipher(root, age, keygen):
    """Real age round trip of generated ZIP bytes; no SDK/restore claim."""
    root = private_directory(root)
    key = root/'fixture.age';cipher = root/'fixture.zip.age'
    env = dict(PATH='/usr/bin:/bin', HOME=str(root), LANG='C.UTF-8')
    with key.open('xb') as out:
        subprocess.run([str(keygen)], stdout=out, stderr=subprocess.PIPE,
                       env=env, timeout=15, check=True)
    recipient = subprocess.check_output([str(keygen), '-y', str(key)], env=env, timeout=15).decode().strip()
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('fixture.txt', b'Generated protocol fixture, never production data.\n')
    plaintext = data.getvalue()
    with cipher.open('xb') as out:
        subprocess.run([str(age), '--encrypt', '-r', recipient], input=plaintext,
                       stdout=out, stderr=subprocess.PIPE, env=env, timeout=15, check=True)
    decoded = subprocess.check_output([str(age), '--decrypt', '-i', str(key), str(cipher)], env=env, timeout=15)
    if decoded != plaintext:raise ValueError('Generated age round trip failed')
    return dict(path=str(cipher), sha256=sha(cipher), kind='generated-age-protocol',
                plaintext_sha256=hashlib.sha256(decoded).hexdigest(),
                sdk_snapshot=False, live_home_read=False, production_key_read=False)


def case_ids(suite):
    if isinstance(suite, unittest.TestSuite):
        return [case for child in suite for case in case_ids(child)]
    return [suite.id()]


class ObservedResult(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.started=[];self.stopped=[];self.successes=[]
    def startTest(self, test):
        self.started.append(test.id());super().startTest(test)
    def stopTest(self, test):
        self.stopped.append(test.id());super().stopTest(test)
    def addSuccess(self, test):
        self.successes.append(test.id());super().addSuccess(test)


def result_record(planned, result):
    exact = (len(planned)==len(set(planned)) and result.started==planned
             and result.stopped==planned and result.successes==planned
             and result.testsRun==len(planned))
    success = (bool(planned) and exact and result.wasSuccessful() and not result.skipped
               and not result.expectedFailures and not result.unexpectedSuccesses)
    return dict(state='ALL_POLICY_METHODS_VERIFIED' if success else 'POLICY_METHODS_NOT_ACCEPTED',
                expected=planned, started=result.started, stopped=result.stopped,
                successful=result.successes, testsRun=result.testsRun,
                failures=[test.id() for test,_ in result.failures],
                errors=[test.id() for test,_ in result.errors],
                skipped=[dict(case=test.id(),reason=reason) for test,reason in result.skipped],
                expectedFailures=[test.id() for test,_ in result.expectedFailures],
                unexpectedSuccesses=[test.id() for test in result.unexpectedSuccesses],
                exact_positive_coverage=success)


def inside_unit(root, native_only):
    root = private_directory(root);operation = json.loads((root/'input.json').read_text())['operation']
    expected_unit = 'signal-grove-acceptance-'+operation+'-pm.service'
    if (not re.fullmatch('[a-f0-9]{32}', operation)
            or not Path('/proc/self/cgroup').read_text().strip().endswith('/'+expected_unit)):
        raise ValueError('Exact finite main unit required')
    scripts = Path(__file__).resolve().parent
    sys.path[:0] = [str(scripts.parent), str(scripts), str(scripts/'tests')]
    authority = native_authority()
    write_json(root/'authority.json', authority)
    cipher = generated_cipher(root/'cipher', root/'programs/age', root/'programs/age-keygen')
    write_json(root/'cipher-proof.json', cipher)
    os.environ.update(SIGNAL_GROVE_PM_BUS_AUTHORITY=json.dumps(authority,separators=(',',':')),
                      SIGNAL_GROVE_PM_RETAIN_ROOT=str(root/'pm'),
                      SIGNAL_GROVE_OPERATIONAL_RETAIN_ROOT=str(root/'operational'),
                      SIGNAL_GROVE_TRANSACTION_RETAIN_ROOT=str(root/'transactions'),
                      SIGNAL_GROVE_AGE_PROGRAM=str(root/'programs/age'),
                      SIGNAL_GROVE_AGE_KEYGEN=str(root/'programs/age-keygen'),
                      SIGNAL_GROVE_VERIFIED_FIXTURE_CIPHER=cipher['path'],
                      SIGNAL_GROVE_VERIFIED_FIXTURE_CIPHER_SHA256=cipher['sha256'],
                      SIGNAL_GROVE_VERIFIED_FIXTURE_KIND=cipher['kind'])
    loader = unittest.TestLoader()
    suite = loader.loadTestsFromNames(NATIVE_CLASSES) if native_only else loader.discover(str(scripts/'tests'))
    planned = case_ids(suite)
    if loader.errors or not planned:raise ValueError('Complete policy inventory unavailable')
    write_json(root/'inventory.json',dict(scope='controlled_native_only' if native_only else 'complete_policy_suite',cases=planned))
    result = unittest.TextTestRunner(verbosity=2, resultclass=ObservedResult).run(suite)
    report = result_record(planned,result)
    if native_authority()!=authority:raise ValueError('User-manager authority changed during tests')
    report.update(operation=operation, unit=expected_unit, scope='controlled_native_only' if native_only else 'complete_policy_suite',
                  cipher_kind=cipher['kind'], real_github_used=False, live_home_read=False,
                  production_key_read=False, actual_sdk_backup_accepted=False)
    write_json(root/'result.json',report)
    return 0 if report['exact_positive_coverage'] else 1


def unit_command(python, script, root, operation, native_only, uid):
    unit = 'signal-grove-acceptance-'+operation+'-pm.service'
    env = dict(PATH='/usr/bin:/bin', HOME=str(root/'home'), LANG='C.UTF-8',
               USER=os.environ.get('USER',''), LOGNAME=os.environ.get('LOGNAME',''),
               XDG_RUNTIME_DIR='/run/user/'+str(uid),
               DBUS_SESSION_BUS_ADDRESS='unix:path=/run/user/'+str(uid)+'/bus',
               TMPDIR=str(root/'tmp'), TEMP=str(root/'tmp'), TMP=str(root/'tmp'), PYTHONDONTWRITEBYTECODE='1')
    command = ['systemd-run','--user','--quiet','--wait','--pipe','--unit='+unit,
               *('--property='+p for p in PROPERTIES),'--working-directory='+str(root),
               '/usr/bin/env','-i',*(k+'='+v for k,v in env.items()),
               str(python),'-I','-B',str(script),'--inside-unit','--work-root',str(root)]
    # env -i replaces itself; Python remains the native unit's main PID.
    if native_only:command.append('--native-only')
    return command


def run(root, age, keygen, native_only):
    if sys.platform!='linux' or os.geteuid()==0:raise ValueError('Unprivileged native Linux user required')
    root=Path(root)
    if not root.is_absolute() or root.parent.resolve()!=root.parent or root.exists() or root.is_symlink():
        raise ValueError('Fresh absolute fixture root required; inspect existing outcomes before another run')
    root.mkdir(mode=0o700);private_directory(root)
    for name in ('home','tmp','programs','cipher','pm','operational','transactions'):(root/name).mkdir(mode=0o700)
    for original,name in ((age,'age'),(keygen,'age-keygen')):
        original=Path(original).resolve()
        if not original.is_file():raise ValueError('age and age-keygen are required')
        target=root/'programs'/name
        with original.open('rb') as source,target.open('xb') as out:shutil.copyfileobj(source,out)
        target.chmod(0o755)
    operation=uuid.uuid4().hex
    command=unit_command(Path(sys.executable).absolute(),Path(__file__).resolve(),root,operation,native_only,os.geteuid())
    write_json(root/'input.json',dict(operation=operation,command=command,age_sha256=sha(root/'programs/age'),
               keygen_sha256=sha(root/'programs/age-keygen'),driver_sha256=sha(__file__),
               source_commit=os.environ.get('GITHUB_SHA'),native_only=native_only))
    # No retry or automatic replacement unit. The manager owns the finite deadline.
    with (root/'stdout.log').open('xb') as out,(root/'stderr.log').open('xb') as err:
        completed=subprocess.run(command,stdout=out,stderr=err,check=False)
    unit='signal-grove-acceptance-'+operation+'-pm.service'
    snapshot=subprocess.run(['systemctl','--user','show',unit,'--property=LoadState,ActiveState,SubState,MainPID,Result,NRestarts,ControlGroup'],capture_output=True,timeout=10)
    (root/'unit-observation.log').write_bytes(snapshot.stdout+snapshot.stderr)
    observed=dict(line.split('=',1) for line in snapshot.stdout.decode().splitlines() if '=' in line)
    stopped=observed.get('MainPID')=='0' and observed.get('ActiveState') in ('inactive','failed') and observed.get('NRestarts')=='0'
    group=observed.get('ControlGroup','')
    if group:
        cgroup=Path('/sys/fs/cgroup')/group.lstrip('/')
        stopped=stopped and (not cgroup.exists() or not (cgroup/'cgroup.procs').read_bytes().split())
    report=json.loads((root/'result.json').read_text()) if (root/'result.json').is_file() else None
    accepted=completed.returncode==0 and stopped and report is not None and report['exact_positive_coverage'] is True
    write_json(root/'summary.json',dict(state='NATIVE_POLICY_SUITE_VERIFIED' if accepted else 'NATIVE_POLICY_SUITE_NOT_ACCEPTED',
                operation=operation,unit=unit,unit_exit_code=completed.returncode,unit_stopped=stopped,
                unit_observation=observed,testsRun=report['testsRun'] if report else None,
                result_sha256=sha(root/'result.json') if report else None,
                stdout_sha256=sha(root/'stdout.log'),stderr_sha256=sha(root/'stderr.log'),
                native_only=native_only,real_github_used=False,live_home_read=False,production_key_read=False,
                actual_sdk_backup_accepted=False))
    print((root/'summary.json').read_text())
    if not accepted:
        # Bounded diagnostics; exact full streams remain for artifact/recovery review.
        print((root/'stderr.log').read_bytes()[-16000:].decode(errors='replace'),file=sys.stderr)
    return 0 if accepted else 1


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-root',required=True,type=Path)
    parser.add_argument('--age',type=Path,default=Path('/usr/bin/age'))
    parser.add_argument('--keygen',type=Path,default=Path('/usr/bin/age-keygen'))
    parser.add_argument('--inside-unit',action='store_true')
    parser.add_argument('--native-only',action='store_true',help='Run only the four controlled native classes; not full policy acceptance')
    args=parser.parse_args();os.umask(0o077)
    return inside_unit(args.work_root,args.native_only) if args.inside_unit else run(args.work_root,args.age,args.keygen,args.native_only)


if __name__=='__main__':raise SystemExit(main())
