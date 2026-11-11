"""Run the standard instrumentation suite on an owned, fresh GitHub Linux emulator.

Uses runner temporary storage. Never targets a developer's existing emulator/device.
Explicit model/import/family/download/recovery gates need staged licensed inputs,
network consent or prepared process/device state. Keep their exact inventory and
not-run status separate; never accept assumptions, partial runs or failed native
results as a standard-suite or model-acceptance pass.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time


# Closed inventory: adding a method to one of these classes requires an explicit
# gate review. All other tests remain in the standard suite; skips still fail it.
EXTERNAL_MODEL_GATES = {
    'org.hermesmobile.app.LocalModelPhysicalTest': {
        'methods': ('verifiedLfm3MetadataScanIsBoundedAndCancellable', 'verifiedLfm3SelectsReviewedChecksAndGeneratesChineseText', 'verifiedLfm3GeneratesCancelsRecoversAndUnloads'),
        'requires': ('actual Android phone', '64-bit process', 'explicitly staged verified licensed LFM3 model'),
    },
    'org.hermesmobile.app.LocalImportCommitIntegrationTest': {
        'methods': ('cancelledCommittedImportBecomesVisibleWithoutReopeningOrLoading',),
        'requires': ('64-bit process', 'explicitly staged publisher-verified licensed LFM3 model'),
    },
    'org.hermesmobile.app.LocalModelFamilyIntegrationTest': {
        'methods': ('verifiedQwenImportChatHistoryAndDelete', 'verifiedLfmQwenSwitchKeepsHistoryBoundToEachFile'),
        'requires': ('64-bit process', 'explicitly staged verified licensed LFM3 and Qwen models'),
    },
    'org.hermesmobile.app.LocalModelTransferIntegrationTest': {
        'methods': ('livePinnedDownloadCancelsAndExplicitlyDiscards', 'livePinnedDownloadCancelsAndExplicitlyResumes'),
        'requires': ('64-bit process', 'explicit public-model network and licence acknowledgement'),
    },
    'org.hermesmobile.app.LocalProcessRecoveryIntegrationTest': {
        'methods': ('prepareInterruptedNativeGeneration', 'coldRecoveryMarksInterruptedAndOnlyAnExplicitFreshSendGenerates', 'rebootKeepsCompletedAndInterruptedHistoryWithoutAutomaticLoad'),
        'requires': ('64-bit process', 'explicitly staged verified licensed Qwen model', 'externally prepared interruption and reboot state'),
    },
}


def external_model_gate_manifest() -> dict:
    return {
        'schema': 1, 'status': 'not_run', 'separate_acceptance_required': True,
        'automatic_model_download': False,
        'reason': 'Explicit prerequisites are not supplied by the standard suite.',
        'gates': [dict(class_name=name, methods=list(gate['methods']), requires=list(gate['requires']), status='not_run')
                  for name, gate in EXTERNAL_MODEL_GATES.items()],
    }


def instrumentation_record(output: str, returncode: int | None, *, elapsed: float = 0,
                           timed_out: bool = False, error_type: str | None = None) -> dict:
    """Bounded facts about this command, never a substitute for exact case verification."""
    passed = re.search(r'^OK \(([1-9][0-9]*) tests?\)', output, re.M)
    failure_marker = bool(re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed', output))
    status, started, positive, named_positive, negative = {}, 0, 0, set(), {}
    last_started, last_finished, declared = None, None, None
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, _, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if key in ('class', 'test'):
                status[key] = value[:512]
            elif key == 'numtests' and re.fullmatch(r'[0-9]{1,8}', value):
                declared = int(value)
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            value = line.partition(': ')[2]
            if re.fullmatch(r'-?[0-9]{1,6}', value):
                code = int(value)
                method = {key: status.get(key) for key in ('class', 'test')}
                if code == 1:
                    started += 1
                    last_started = method
                elif code == 0:
                    positive += 1
                    last_finished = method
                    if all(method.values()):
                        named_positive.add((method['class'], method['test']))
                elif code < 0:
                    key = str(code) if code in (-1, -2, -3, -4) else 'other_negative'
                    negative[key] = negative.get(key, 0) + 1
            status = {}
    success = returncode == 0 and passed is not None and not failure_marker and not negative and not timed_out and error_type is None
    outcome = ('timed_out' if timed_out else 'command_error' if error_type else
               'unknown_command_outcome' if returncode is None else 'passed' if success else 'failed')
    return {'schema': 1, 'status': outcome, 'command_returncode': returncode,
            'timed_out': timed_out, 'command_error_type': error_type,
            'elapsed_seconds': round(max(0, elapsed), 3),
            'ok_summary_tests': int(passed.group(1)) if passed else None,
            'failure_marker': failure_marker, 'negative_status_codes': negative,
            'declared_tests': declared, 'started_status_events': started,
            'positive_status_events': positive, 'unique_named_positive_methods': len(named_positive),
            'last_started': last_started, 'last_finished': last_finished,
            'waiting_for_device_message': '- waiting for device -' in output,
            'success_criteria_met': success,
            'scope': 'One command only. Exact distinct cases, APK identity and complete reports require separate verification.'}


def retain_instrumentation(log_path: Path, output: str, record: dict) -> bool:
    try:
        log_path.write_text(output, encoding='utf-8')
        log_path.with_name('instrumentation-result.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        return True
    except OSError as exc:
        print(f'Instrumentation evidence could not be retained: {type(exc).__name__}.', file=sys.stderr)
        return False


def run_instrumentation(device, log_path: Path, timeout: int = 6000) -> int:
    """One finite invocation; retain exit facts without retrying or accepting partial output."""
    started = time.monotonic()
    try:
        result = device('shell', 'am', 'instrument', '-w', '-r',
                        '-e', 'notClass', ','.join(EXTERNAL_MODEL_GATES),
                        'org.hermesmobile.app.debug.test/androidx.test.runner.AndroidJUnitRunner', timeout=timeout, check=False)
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout
        if isinstance(output, bytes):
            output = output.decode('utf-8', errors='replace')
        output = output or ''
        retain_instrumentation(log_path, output, instrumentation_record(output, None, elapsed=time.monotonic()-started, timed_out=True))
        raise
    except OSError as exc:
        retain_instrumentation(log_path, '', instrumentation_record('', None, elapsed=time.monotonic()-started, error_type=type(exc).__name__))
        raise
    record = instrumentation_record(result.stdout, result.returncode, elapsed=time.monotonic()-started)
    retained = retain_instrumentation(log_path, result.stdout, record)
    if not record['success_criteria_met']:
        raise RuntimeError('Instrumentation failed; inspect the retained log, command result and device state.')
    if not retained:
        raise RuntimeError('Instrumentation evidence could not be retained; no device pass is recorded.')
    return record['ok_summary_tests']


def retain_owned_device_state(process, device, path: Path) -> bool:
    """Observe the same owned process/serial once, before cleanup; never restart it."""
    record = {'schema': 1, 'before_cleanup': True}
    try:
        code = process.poll()
        record.update(emulator_returncode=code, emulator_alive=code is None)
    except OSError as exc:
        code = None
        record.update(emulator_returncode=None, emulator_alive=None, emulator_probe_error_type=type(exc).__name__)
    if code is not None:
        record['adb_get_state'] = {'skipped': 'owned_emulator_exited'}
    else:
        try:
            result = device('get-state', timeout=10, check=False)
            state = (result.stdout or '').strip()
            record['adb_get_state'] = {'returncode': result.returncode,
                'state': state if state in ('device', 'offline', 'unknown', 'unauthorized', 'bootloader', 'recovery', 'sideload') else 'empty' if not state else 'other'}
        except (OSError, subprocess.SubprocessError) as exc:
            record['adb_get_state'] = {'error_type': type(exc).__name__}
    try:
        path.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
        return True
    except OSError as exc:
        print(f'Owned-device evidence could not be retained: {type(exc).__name__}.', file=sys.stderr)
        return False


def finish_owned_device(process, device, work: Path) -> bool:
    retained = retain_owned_device_state(process, device, work/'device-state.json')
    try:
        (work/'test-runner.log').write_text(device('logcat', '-d', '-s', 'TestRunner:I', 'AndroidRuntime:E', timeout=15, check=False).stdout, encoding='utf-8')
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        device('pull', '/sdcard/Android/data/org.hermesmobile.app.debug/files/qa', work/'screenshots', timeout=30, check=False)
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        device('emu', 'kill', timeout=10, check=False)
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
    return retained



def install_sdk_packages(argv: list[str], env: dict, log_path: Path, timeout: int = 900) -> None:
    """Retry only transient package transport failures inside the original setup deadline.

    This never retries APK installation or instrumentation and never skips SDK checks.
    """
    deadline=time.monotonic()+timeout
    log_path.write_text('',encoding='utf-8')
    for attempt in range(1,4):
        remaining=deadline-time.monotonic()
        if remaining<=0:raise subprocess.TimeoutExpired(argv,timeout)
        print(f'SDK package setup attempt {attempt}/3.',flush=True)
        with log_path.open('a',encoding='utf-8') as log:
            log.write(f'\n--- SDK package setup attempt {attempt}/3 ---\n');log.flush()
            result=subprocess.run(argv,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=remaining,check=False)
        if result.returncode==0:return
        # Inspect only this attempt's bounded output; preserve every attempt in the artifact.
        with log_path.open('rb') as log:
            log.seek(max(0,log_path.stat().st_size-65536));tail=log.read().decode('utf-8',errors='replace')
        tail=tail.rsplit(f'--- SDK package setup attempt {attempt}/3 ---',1)[-1]
        transient=bool(re.search(r'failed to download package archive[^\n]*(?:closed|timed out|reset)|connection reset|HTTP (?:502|503|504)',tail,re.I))
        if not transient or attempt==3:raise subprocess.CalledProcessError(result.returncode,argv)
        remaining=deadline-time.monotonic()
        if remaining<=0:raise subprocess.TimeoutExpired(argv,timeout)
        print('SDK transport failed; retrying the same packages within the setup deadline.',flush=True)
        time.sleep(min(2**attempt,remaining))


def ensure_device_disk_space(sdk: Path, work: Path, env: dict, minimum_bytes: int = 8 * 1024**3) -> None:
    """Reserve first-boot headroom; newer emulators enforce at least 6 GiB userdata.

    Device jobs consume prebuilt APKs, so only their unused SDK NDK tree may be
    reclaimed. Never clean workspaces, other toolchains, or a developer machine.
    """
    if sys.platform!='linux' or env.get('GITHUB_ACTIONS')!='true':
        raise ValueError('Disk preparation is restricted to isolated GitHub Linux runners.')
    temp=Path(env['RUNNER_TEMP']).resolve(strict=True);work=work.resolve(strict=True);sdk=sdk.resolve(strict=True)
    if work.parent!=temp or work.name!='hermes-device-qa' or sdk!=Path(env['ANDROID_HOME']).resolve(strict=True) or sdk==Path(sdk.anchor):
        raise ValueError('Unexpected device-job storage scope.')
    free=shutil.disk_usage(work).free
    print(f'Emulator disk preflight: {free} bytes free; {minimum_bytes} required.',flush=True)
    if free>=minimum_bytes:return
    ndk=sdk/'ndk'
    if ndk.is_symlink() or not ndk.is_dir() or ndk.resolve(strict=True).parent!=sdk:
        raise ValueError('Insufficient emulator space; no safely reclaimable SDK NDK tree.')
    print('Reclaiming unused NDK installations in this isolated prebuilt-APK device job.',flush=True)
    subprocess.run(['sudo','-n','rm','-rf','--',str(ndk)],env=env,check=True,timeout=180)
    free=shutil.disk_usage(work).free
    print(f'Emulator disk preflight after NDK cleanup: {free} bytes free.',flush=True)
    if free<minimum_bytes:raise ValueError('Insufficient emulator disk space after bounded NDK cleanup.')


def wait_for_owned_device(process, probe, timeout: float = 180) -> None:
    """Observe this process and serial together; a dead emulator is not an ADB wait."""
    deadline=time.monotonic()+timeout
    while True:
        if process.poll() is not None:raise RuntimeError('Owned emulator exited before ADB became ready; inspect emulator.log.')
        remaining=deadline-time.monotonic()
        if remaining<=0:raise TimeoutError('Owned emulator did not become ready for ADB.')
        try:result=probe('get-state',timeout=min(10,remaining),check=False)
        except subprocess.TimeoutExpired:result=None
        if result is not None and result.returncode==0 and result.stdout.strip()=='device':return
        time.sleep(min(2,max(0,deadline-time.monotonic())))


def configure_owned_avd(avd_home: Path, name: str) -> None:
    """Prefer bounded userdata; emulator first-boot minimums may override this setting."""
    if name not in ('HermesCI26','HermesCI37'):
        raise ValueError("Only this helper's owned CI AVDs can be configured.")
    root=avd_home.resolve(strict=True)
    config=(root/(name+'.avd')/'config.ini').resolve(strict=True)
    if config.parent.parent!=root or config.name!='config.ini':
        raise ValueError('Owned AVD configuration escaped its isolated directory.')
    lines=config.read_text(encoding='utf-8').splitlines()
    lines=[line for line in lines if line.split('=',1)[0].strip()!='disk.dataPartition.size']
    config.write_text('\n'.join(lines+['disk.dataPartition.size=2048M'])+'\n',encoding='utf-8')


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api',type=int,choices=(26,37),required=True)
    parser.add_argument('--artifacts',type=Path,required=True)
    args=parser.parse_args()
    if sys.platform!='linux' or os.environ.get('GITHUB_ACTIONS')!='true':
        raise ValueError('This helper is restricted to isolated GitHub Linux runners.')
    temp=Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    work=temp/'hermes-device-qa';work.mkdir(exist_ok=True)
    (work/'external-model-gates.json').write_text(json.dumps(external_model_gate_manifest(),indent=2)+'\n',encoding='utf-8')
    env=dict(os.environ)
    for key,folder in [('ANDROID_USER_HOME','android-user'),('ANDROID_EMULATOR_HOME','android-user'),('ANDROID_AVD_HOME','avd'),('TMPDIR','tmp')]:
        target=work/folder;target.mkdir(exist_ok=True);env[key]=str(target)
    sdk=Path(env['ANDROID_HOME']).resolve(strict=True)
    adb=str(sdk/'platform-tools/adb');emulator=str(sdk/'emulator/emulator')
    if not os.access('/dev/kvm',os.R_OK|os.W_OK):
        raise ValueError('KVM is unavailable; device execution is not verified.')
    def run(argv,timeout=30,check=True):
        return subprocess.run([str(x) for x in argv],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=timeout,check=check)
    image=f'system-images;android-{"37.0" if args.api==37 else "26"};google_apis;x86_64'
    print(f'Installing the API {args.api} system image in the isolated runner.',flush=True)
    install_sdk_packages(['sdkmanager','emulator','platform-tools',image],env,work/'sdk.log')
    ensure_device_disk_space(sdk,work,env)
    print('System image ready; creating the owned emulator.',flush=True)
    name=f'HermesCI{args.api}'
    subprocess.run(['avdmanager','create','avd','--force','--name',name,'--package',image,'--device','pixel_2'],input='no\n',env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=90,check=True)
    configure_owned_avd(Path(env['ANDROID_AVD_HOME']),name)
    app=args.artifacts/'outputs/apk/debug/app-debug.apk'
    tests=args.artifacts/'outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if not app.is_file() or not tests.is_file():
        raise ValueError('Both verified debug and instrumentation APK artifacts are required.')
    serial='emulator-5554'
    def device(*argv,**kwargs):return run([adb,'-s',serial,*argv],**kwargs)
    with (work/'emulator.log').open('w') as log:
        process=subprocess.Popen([emulator,'-avd',name,'-port','5554','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader_indirect','-accel','on','-memory','3072','-cores','2'],env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            print('Waiting for the owned emulator to boot.',flush=True)
            run([adb,'start-server'])
            wait_for_owned_device(process,device)
            deadline=time.monotonic()+240
            while True:
                if process.poll() is not None:raise RuntimeError('Owned emulator exited before boot.')
                if device('shell','getprop','sys.boot_completed',check=False).stdout.strip()=='1':break
                if time.monotonic()>deadline:raise TimeoutError('Owned emulator did not finish booting.')
                time.sleep(2)
            actual=device('shell','getprop','ro.build.version.sdk').stdout.strip()
            if actual!=str(args.api):raise ValueError('The emulator API does not match the matrix entry.')
            print(f'Emulator booted with verified API {actual}; installing APKs.',flush=True)
            device('shell','svc','power','stayon','true')
            device('shell','settings','put','system','screen_off_timeout','2147483647')
            device('shell','settings','put','system','font_scale','1.3' if args.api==26 else '1.0')
            for setting in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
                device('shell','settings','put','global',setting,'0')
            device('shell','input','keyevent','KEYCODE_WAKEUP')
            device('shell','wm','dismiss-keyguard',check=False)
            device('install','-r',app,timeout=120)
            device('install','-r',tests,timeout=120)
            if args.api>=37:
                device('shell','pm','revoke','org.hermesmobile.app.debug','android.permission.ACCESS_LOCAL_NETWORK',check=False)
                device('shell','pm','clear-permission-flags','org.hermesmobile.app.debug','android.permission.ACCESS_LOCAL_NETWORK','user-set','user-fixed')
            print('Running the standard instrumentation suite; explicit external model gates are separate (100-minute limit).',flush=True)
            count=run_instrumentation(device,work/'instrumentation.log')
            summary=f'API {args.api}: standard instrumentation suite passed ({count} cases); external model gates not run here.'
            print(summary)
            with Path(env['GITHUB_STEP_SUMMARY']).open('a',encoding='utf-8') as report:report.write(summary+'\n')
        finally:
            device_state_retained = finish_owned_device(process, device, work)
    if not device_state_retained:
        raise RuntimeError('Owned-device evidence could not be retained; inspect cleanup output.')

    return 0


if __name__=='__main__':
    try:sys.exit(main())
    except (ValueError,RuntimeError,OSError,subprocess.SubprocessError) as exc:
        print(f'Device validation failed: {type(exc).__name__}: {exc}',file=sys.stderr)
        sys.exit(1)
