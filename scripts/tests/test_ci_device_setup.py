import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('device_ci',Path(__file__).resolve().parents[1]/'ci_android_device.py')
device_ci=importlib.util.module_from_spec(spec);spec.loader.exec_module(device_ci)


class DeviceSdkSetupTest(unittest.TestCase):
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
