import importlib.util
import json
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
            self.assertEqual(485,device_ci.run_instrumentation(device,log));self.assertEqual(device.return_value.stdout,log.read_text(encoding='utf-8'));device.assert_called_once();self.assertEqual(5700,device.call_args.kwargs['timeout']);self.assertFalse(device.call_args.kwargs['check'])
            args=device.call_args.args
            self.assertEqual(('notClass','org.hermesmobile.app.LocalModelPhysicalTest'),args[args.index('-e')+1:args.index('-e')+3])
            self.assertEqual(1,args.count('notClass'))

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




class DeviceOutcomeDiagnosticsTest(unittest.TestCase):
    def metadata(self, log):
        return json.loads(log.with_name('instrumentation-result.json').read_text(encoding='utf-8'))

    def test_incomplete_stream_preserves_exit_status_and_last_started_case(self):
        output = ('INSTRUMENTATION_STATUS: numtests=2\nINSTRUMENTATION_STATUS: class=Fixture\n'
                  'INSTRUMENTATION_STATUS: test=first\nINSTRUMENTATION_STATUS_CODE: 1\n'
                  'INSTRUMENTATION_STATUS: class=Fixture\nINSTRUMENTATION_STATUS: test=first\nINSTRUMENTATION_STATUS_CODE: 0\n'
                  'INSTRUMENTATION_STATUS: class=Fixture\nINSTRUMENTATION_STATUS: test=second\nINSTRUMENTATION_STATUS_CODE: 1\n- waiting for device -\n')
        for code in (0, 1, 255):
            with self.subTest(code=code), tempfile.TemporaryDirectory() as directory:
                log=Path(directory)/'instrumentation.log';device=Mock(return_value=subprocess.CompletedProcess([],code,output))
                with self.assertRaisesRegex(RuntimeError,'Instrumentation failed'):device_ci.run_instrumentation(device,log)
                record=self.metadata(log);self.assertEqual(code,record['command_returncode'])
                self.assertEqual(2,record['started_status_events']);self.assertEqual(1,record['positive_status_events']);self.assertEqual(1,record['unique_named_positive_methods'])
                self.assertEqual(2,record['declared_tests']);self.assertEqual({'class':'Fixture','test':'second'},record['last_started'])
                self.assertTrue(record['waiting_for_device_message']);self.assertFalse(record['success_criteria_met']);self.assertIsNone(record['ok_summary_tests'])
                self.assertEqual({},record['negative_status_codes']);device.assert_called_once()

    def test_timeout_metadata_never_accepts_even_a_positive_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            log=Path(directory)/'instrumentation.log';failure=subprocess.TimeoutExpired(['owned-adb'],12,output=b'OK (2 tests)\npartial\xff')
            device=Mock(side_effect=failure)
            with self.assertRaises(subprocess.TimeoutExpired) as caught:device_ci.run_instrumentation(device,log,timeout=12)
            self.assertIs(failure,caught.exception);record=self.metadata(log)
            self.assertEqual('timed_out',record['status']);self.assertTrue(record['timed_out']);self.assertIsNone(record['command_returncode'])
            self.assertEqual(2,record['ok_summary_tests']);self.assertFalse(record['success_criteria_met']);self.assertIn('partial',log.read_text());device.assert_called_once()

    def test_negative_method_verdict_cannot_hide_behind_positive_summary(self):
        for code in (-1,-2,-3,-4,-99):
            with self.subTest(code=code),tempfile.TemporaryDirectory() as directory:
                log=Path(directory)/'instrumentation.log';output=f'INSTRUMENTATION_STATUS_CODE: {code}\nOK (1 test)\n'
                device=Mock(return_value=subprocess.CompletedProcess([],0,output))
                with self.assertRaisesRegex(RuntimeError,'Instrumentation failed'):device_ci.run_instrumentation(device,log)
                record=self.metadata(log);self.assertEqual({str(code) if code in (-1,-2,-3,-4) else 'other_negative':1},record['negative_status_codes'])
                self.assertFalse(record['success_criteria_met']);device.assert_called_once()

    def test_command_exception_is_recorded_without_retry_or_replacement(self):
        with tempfile.TemporaryDirectory() as directory:
            log=Path(directory)/'instrumentation.log';failure=OSError('fixture-command-error');device=Mock(side_effect=failure)
            with self.assertRaises(OSError) as caught:device_ci.run_instrumentation(device,log)
            self.assertIs(failure,caught.exception);record=self.metadata(log)
            self.assertEqual('command_error',record['status']);self.assertEqual('OSError',record['command_error_type']);self.assertIsNone(record['command_returncode']);device.assert_called_once()

    def test_evidence_write_failure_never_passes_or_replaces_a_timeout(self):
        for timeout in (False,True):
            with self.subTest(timeout=timeout),tempfile.TemporaryDirectory() as directory,patch.object(device_ci.Path,'write_text',side_effect=OSError('fixture-storage-error')):
                log=Path(directory)/'instrumentation.log';failure=subprocess.TimeoutExpired(['owned-adb'],12,output='partial')
                device=Mock(side_effect=failure) if timeout else Mock(return_value=subprocess.CompletedProcess([],0,'OK (1 test)'))
                if timeout:
                    with self.assertRaises(subprocess.TimeoutExpired) as caught:device_ci.run_instrumentation(device,log)
                    self.assertIs(failure,caught.exception)
                else:
                    with self.assertRaisesRegex(RuntimeError,'evidence could not be retained'):device_ci.run_instrumentation(device,log)
                device.assert_called_once()

    def test_owned_state_observes_original_process_once_and_bounds_adb_output(self):
        for output,state in [('device\n','device'),('unbounded-private-fixture-output','other')]:
            with self.subTest(state=state),tempfile.TemporaryDirectory() as directory:
                process=Mock();process.poll.return_value=None;device=Mock(return_value=subprocess.CompletedProcess([],0,output));path=Path(directory)/'device-state.json'
                self.assertTrue(device_ci.retain_owned_device_state(process,device,path));record=json.loads(path.read_text())
                self.assertTrue(record['before_cleanup']);self.assertTrue(record['emulator_alive']);self.assertIsNone(record['emulator_returncode'])
                self.assertEqual(state,record['adb_get_state']['state']);self.assertNotIn('private-fixture',path.read_text())
                process.poll.assert_called_once();device.assert_called_once_with('get-state',timeout=10,check=False)
                process.kill.assert_not_called();process.terminate.assert_not_called();process.wait.assert_not_called()

    def test_dead_or_timed_out_owned_device_is_not_restarted_or_retried(self):
        for exited in (False,True):
            with self.subTest(exited=exited),tempfile.TemporaryDirectory() as directory:
                process=Mock();process.poll.return_value=7 if exited else None;device=Mock(side_effect=subprocess.TimeoutExpired(['get-state'],10));path=Path(directory)/'device-state.json'
                self.assertTrue(device_ci.retain_owned_device_state(process,device,path));record=json.loads(path.read_text())
                if exited:
                    self.assertEqual(7,record['emulator_returncode']);self.assertEqual({'skipped':'owned_emulator_exited'},record['adb_get_state']);device.assert_not_called()
                else:
                    self.assertEqual({'error_type':'TimeoutExpired'},record['adb_get_state']);device.assert_called_once()
                process.kill.assert_not_called();process.terminate.assert_not_called()

    def test_cleanup_follows_missing_diagnostics_without_masking_original_failure(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci,'retain_owned_device_state',return_value=False):
            process=Mock();process.wait.return_value=0;device=Mock(return_value=subprocess.CompletedProcess([],0,'fixture-log'))
            failure=RuntimeError('original-instrumentation-failure')
            with self.assertRaises(RuntimeError) as caught:
                try:raise failure
                finally:retained=device_ci.finish_owned_device(process,device,Path(directory))
            self.assertIs(failure,caught.exception);self.assertFalse(retained)
            self.assertEqual(['logcat','pull','emu'],[call.args[0] for call in device.call_args_list]);process.wait.assert_called_once_with(timeout=15);process.kill.assert_not_called()

    def test_cleanup_timeouts_still_reap_only_the_owned_process(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(device_ci,'retain_owned_device_state',return_value=True):
            process=Mock();process.wait.side_effect=[subprocess.TimeoutExpired(['owned-emulator'],15),0]
            device=Mock(side_effect=[subprocess.TimeoutExpired(['logcat'],15),OSError('fixture-pull-error'),subprocess.CompletedProcess([],0,'')])
            self.assertTrue(device_ci.finish_owned_device(process,device,Path(directory)))
            self.assertEqual(['logcat','pull','emu'],[call.args[0] for call in device.call_args_list]);process.kill.assert_called_once_with();process.terminate.assert_not_called()
            self.assertEqual([15,10],[call.kwargs['timeout'] for call in process.wait.call_args_list])


if __name__=='__main__':unittest.main()
