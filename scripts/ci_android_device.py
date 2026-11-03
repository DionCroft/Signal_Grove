"""Run the full instrumentation APK on an owned, fresh GitHub Linux emulator.

Uses runner temporary storage. Never targets a developer's existing emulator/device.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import time


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


def configure_owned_avd(avd_home: Path, name: str) -> None:
    """Bound fresh CI userdata instead of inheriting a device template's 12 GiB default."""
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
            device('wait-for-device',timeout=180)
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
            print('Running the complete instrumentation suite.',flush=True)
            result=device('shell','am','instrument','-w','-r','org.hermesmobile.app.debug.test/androidx.test.runner.AndroidJUnitRunner',timeout=1500,check=False)
            (work/'instrumentation.log').write_text(result.stdout,encoding='utf-8')
            passed=re.search(r'^OK \(([1-9][0-9]*) tests?\)',result.stdout,re.M)
            failed=re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed',result.stdout)
            if result.returncode or not passed or failed:
                raise RuntimeError('Instrumentation failed; inspect the retained log and screenshots.')
            count=int(passed.group(1))
            summary=f'API {args.api}: full instrumentation suite passed ({count} cases).'
            print(summary)
            with Path(env['GITHUB_STEP_SUMMARY']).open('a',encoding='utf-8') as report:report.write(summary+'\n')
        finally:
            for filename,argv in [('test-runner.log',('logcat','-d','-s','TestRunner:I','AndroidRuntime:E'))]:
                try:(work/filename).write_text(device(*argv,timeout=15,check=False).stdout,encoding='utf-8')
                except (OSError,subprocess.TimeoutExpired):pass
            try:device('pull','/sdcard/Android/data/org.hermesmobile.app.debug/files/qa',work/'screenshots',timeout=30,check=False)
            except (OSError,subprocess.TimeoutExpired):pass
            try:device('emu','kill',timeout=10,check=False)
            except (OSError,subprocess.TimeoutExpired):pass
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
    return 0


if __name__=='__main__':
    try:sys.exit(main())
    except (ValueError,RuntimeError,OSError,subprocess.SubprocessError) as exc:
        print(f'Device validation failed: {type(exc).__name__}: {exc}',file=sys.stderr)
        sys.exit(1)
