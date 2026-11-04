import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch,Mock

spec=importlib.util.spec_from_file_location('device_ci',Path(__file__).resolve().parents[1]/'ci_android_device.py')
device_ci=importlib.util.module_from_spec(spec);spec.loader.exec_module(device_ci)


class DeviceSdkSetupTest(unittest.TestCase):
    def test_complete_instrumentation_runs_once_and_requires_positive_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            log=Path(directory)/'instrumentation.log';device=Mock(return_value=Mock(returncode=0,stdout='OK (485 tests)\nINSTRUMENTATION_CODE: -1\n'))
            self.assertEqual(485,device_ci.run_instrumentation(device,log));self.assertEqual(device.return_value.stdout,log.read_text(encoding='utf-8'));device.assert_called_once();self.assertEqual(3000,device.call_args.kwargs['timeout']);self.assertFalse(device.call_args.kwargs['check'])

    def test_explicit_suite_deadline_is_forwarded_once_and_never_reset_after_timeout(self):
        with tempfile.TemporaryDirectory() as directory:
            log=Path(directory)/'instrumentation.log'
            failure=subprocess.TimeoutExpired(['owned-adb'],12,output='548 cases completed; next case still running\n')
            device=Mock(side_effect=failure)
            with self.assertRaises(subprocess.TimeoutExpired) as caught:device_ci.run_instrumentation(device,log,timeout=12)
            self.assertIs(failure,caught.exception);device.assert_called_once()
            self.assertEqual(12,device.call_args.kwargs['timeout']);self.assertEqual(failure.output,log.read_text(encoding='utf-8'))

    def test_instrumentation_failure_or_missing_positive_summary_never_passes(self):
        for code,output in [(1,'OK (485 tests)'),(0,'FAILURES!!!\nOK (485 tests)'),(0,'INSTRUMENTATION_FAILED'),(0,'shortMsg=Process crashed'),(0,'Process crashed'),(0,''),(0,'OK (0 tests)')]:
            with self.subTest(code=code,output=output),tempfile.TemporaryDirectory() as directory:
                log=Path(directory)/'instrumentation.log';device=Mock(return_value=Mock(returncode=code,stdout=output))
                with self.assertRaisesRegex(RuntimeError,'Instrumentation failed'):device_ci.run_instrumentation(device,log)
                self.assertEqual(output,log.read_text(encoding='utf-8'));device.assert_called_once()

    def test_instrumentation_timeout_retains_partial_output_without_retry_or_false_pass(self):
        for output in ['progress\n',b'progress\xff',None,'OK (485 tests)\n']:
            with self.subTest(output=output),tempfile.TemporaryDirectory() as directory:
                log=Path(directory)/'instrumentation.log';error=subprocess.TimeoutExpired(['owned-adb'],2100,output=output);device=Mock(side_effect=error)
                with self.assertRaises(subprocess.TimeoutExpired) as caught:device_ci.run_instrumentation(device,log)
                self.assertIs(error,caught.exception);self.assertEqual(output.decode('utf-8',errors='replace') if isinstance(output,bytes) else output or '',log.read_text(encoding='utf-8'));device.assert_called_once()

    def fixture_disk(self,directory):
        temp=Path(directory);work=temp/'hermes-device-qa';work.mkdir();sdk=temp/'android'/'sdk';sdk.mkdir(parents=True);(sdk/'ndk').mkdir();(sdk/'platform-tools').mkdir()
        return sdk,work,{'GITHUB_ACTIONS':'true','RUNNER_TEMP':str(temp),'ANDROID_HOME':str(sdk)}

    def test_sufficient_space_never_deletes_tools(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.sys,'platform','linux'),patch.object(device_ci.shutil,'disk_usage',return_value=Mock(free=9*1024**3)),patch.object(device_ci.subprocess,'run') as run:
            sdk,work,env=self.fixture_disk(directory);device_ci.ensure_device_disk_space(sdk,work,env);run.assert_not_called();self.assertTrue((sdk/'ndk').is_dir())

    def test_low_space_reclaims_only_ndk_then_requires_capacity(self):
        for after,success in [(9*1024**3,True),(7*1024**3,False)]:
            with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.sys,'platform','linux'),patch.object(device_ci.shutil,'disk_usage',side_effect=[Mock(free=7*1024**3),Mock(free=after)]),patch.object(device_ci.subprocess,'run') as run:
                sdk,work,env=self.fixture_disk(directory)
                if success:device_ci.ensure_device_disk_space(sdk,work,env)
                else:
                    with self.assertRaises(ValueError):device_ci.ensure_device_disk_space(sdk,work,env)
                self.assertEqual(['sudo','-n','rm','-rf','--',str(sdk.resolve()/'ndk')],run.call_args.args[0]);self.assertTrue(run.call_args.kwargs['check']);self.assertTrue((sdk/'platform-tools').is_dir())

    def test_disk_cleanup_refuses_developer_missing_ndk_and_unexpected_work_root(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.sys,'platform','linux'),patch.object(device_ci.shutil,'disk_usage',return_value=Mock(free=7*1024**3)),patch.object(device_ci.subprocess,'run') as run:
            sdk,work,env=self.fixture_disk(directory)
            with self.assertRaises(ValueError):device_ci.ensure_device_disk_space(sdk,work,dict(env,GITHUB_ACTIONS='false'))
            other=work.parent/'unrelated';other.mkdir()
            with self.assertRaises(ValueError):device_ci.ensure_device_disk_space(sdk,other,env)
            (sdk/'ndk').rmdir()
            with self.assertRaises(ValueError):device_ci.ensure_device_disk_space(sdk,work,env)
            run.assert_not_called()

    def test_dead_emulator_fails_before_blocking_adb_and_live_process_uses_original_deadline(self):
        process=Mock();process.poll.return_value=1;probe=Mock()
        with self.assertRaisesRegex(RuntimeError,'exited before ADB'):device_ci.wait_for_owned_device(process,probe)
        probe.assert_not_called()
        process.poll.return_value=None;probe.return_value=subprocess.CompletedProcess([],0,'device\n')
        device_ci.wait_for_owned_device(process,probe,3);self.assertEqual('get-state',probe.call_args.args[0]);self.assertLessEqual(probe.call_args.kwargs['timeout'],3)
        probe.return_value=subprocess.CompletedProcess([],1,'offline')
        with patch.object(device_ci.time,'monotonic',side_effect=[0,0,1,4]),patch.object(device_ci.time,'sleep'):
            with self.assertRaises(TimeoutError):device_ci.wait_for_owned_device(process,probe,3)

    def test_transient_adb_probe_timeout_observes_same_live_process_without_restart(self):
        process=Mock();process.poll.return_value=None;probe=Mock(side_effect=[subprocess.TimeoutExpired(['adb','get-state'],3),subprocess.CompletedProcess([],0,'device\n')])
        with patch.object(device_ci.time,'sleep'):device_ci.wait_for_owned_device(process,probe,3)
        self.assertEqual(2,probe.call_count);self.assertEqual(2,process.poll.call_count)

    def test_owned_ci_data_partition_replaces_all_template_defaults_without_touching_other_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);owned=root/'HermesCI26.avd';owned.mkdir();config=owned/'config.ini'
            config.write_text('disk.dataPartition.size=12G\nhw.ramSize=3072\n disk.dataPartition.size =12288M\n',encoding='utf-8')
            device_ci.configure_owned_avd(root,'HermesCI26');text=config.read_text(encoding='utf-8')
            self.assertEqual(1,text.count('disk.dataPartition.size'));self.assertIn('disk.dataPartition.size=2048M',text);self.assertIn('hw.ramSize=3072',text)
            with self.assertRaises(ValueError):device_ci.configure_owned_avd(root,'developer-personal-avd')
            self.assertFalse((root/'developer-personal-avd.avd').exists())

    def call(self,outputs):
        calls=[]
        def run(argv,**kwargs):
            text,code=outputs[min(len(calls),len(outputs)-1)];calls.append((list(argv),kwargs['timeout']))
            kwargs['stdout'].write(text+'\n');return subprocess.CompletedProcess(argv,code)
        return calls,run

    def test_closed_archive_retries_same_packages_and_preserves_failure_evidence(self):
        calls,run=self.call([('Failed to download package archive for system-images/android-37.0/google_apis/x86_64: closed',1),('Setup complete',0)])
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.subprocess,'run',side_effect=run),patch.object(device_ci.time,'sleep'):
            log=Path(directory)/'sdk.log';args=['sdkmanager','exact-fixture-package']
            device_ci.install_sdk_packages(args,{},log,timeout=10)
            self.assertEqual([args,args],[c[0] for c in calls]);self.assertTrue(all(0<t<=10 for _,t in calls))
            self.assertIn('closed',log.read_text());self.assertIn('Setup complete',log.read_text())

    def test_repeated_transient_failure_stops_after_three_attempts(self):
        calls,run=self.call([('Failed to download package archive for system-images/android-37.0/google_apis/x86_64: closed',1)])
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.subprocess,'run',side_effect=run),patch.object(device_ci.time,'sleep'):
            with self.assertRaises(subprocess.CalledProcessError):device_ci.install_sdk_packages(['sdkmanager','fixture'],{},Path(directory)/'sdk.log',timeout=10)
        self.assertEqual(3,len(calls))

    def test_configuration_and_license_refusals_are_not_retried(self):
        for message in ('License not accepted','Unknown package: fixture'):
            calls,run=self.call([(message,1)])
            with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.subprocess,'run',side_effect=run),patch.object(device_ci.time,'sleep') as sleep:
                with self.assertRaises(subprocess.CalledProcessError):device_ci.install_sdk_packages(['sdkmanager','fixture'],{},Path(directory)/'sdk.log',timeout=10)
                sleep.assert_not_called()
            self.assertEqual(1,len(calls))

    def test_timeout_is_not_reset_by_a_retry(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.subprocess,'run',side_effect=subprocess.TimeoutExpired(['sdkmanager'],10)) as run,patch.object(device_ci.time,'sleep') as sleep:
            with self.assertRaises(subprocess.TimeoutExpired):device_ci.install_sdk_packages(['sdkmanager','fixture'],{},Path(directory)/'sdk.log',timeout=10)
            self.assertEqual(1,run.call_count);sleep.assert_not_called()
        calls,run=self.call([('Failed to download package archive for system-images/android-37.0/google_apis/x86_64: closed',1)])
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci.subprocess,'run',side_effect=run),patch.object(device_ci.time,'sleep'),patch.object(device_ci.time,'monotonic',side_effect=[0,0,9.5,11]):
            with self.assertRaises(subprocess.TimeoutExpired):device_ci.install_sdk_packages(['sdkmanager','fixture'],{},Path(directory)/'sdk.log',timeout=10)
        self.assertEqual(1,len(calls))



if __name__=='__main__':unittest.main()
