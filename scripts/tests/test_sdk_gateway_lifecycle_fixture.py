from pathlib import Path
import importlib.util
import os
import socket
import sys
import tempfile
import unittest
from unittest import mock

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
spec=importlib.util.spec_from_file_location('sdk_gateway_lifecycle_fixture',scripts/'native/sdk_gateway_lifecycle_fixture.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class GatewayFixtureBoundaryTests(unittest.TestCase):
    def test_only_ephemeral_loopback_tcp_listeners_are_allowed(self):
        runtime=Path(tempfile.gettempdir()).resolve()/'gateway-fixture-runtime'
        self.assertTrue(m.fixture_bind_allowed(socket.AF_INET,socket.SOCK_STREAM,('127.0.0.1',0),runtime))
        self.assertTrue(m.fixture_bind_allowed(socket.AF_INET6,socket.SOCK_STREAM,('::1',0),runtime))
        for family,kind,address in ((socket.AF_INET,socket.SOCK_STREAM,('0.0.0.0',0)),(socket.AF_INET,socket.SOCK_STREAM,('127.0.0.1',8642)),(socket.AF_INET,socket.SOCK_DGRAM,('127.0.0.1',0)),(socket.AF_INET6,socket.SOCK_STREAM,('::',0))):
            with self.subTest(address=address):self.assertFalse(m.fixture_bind_allowed(family,kind,address,runtime))
    def test_unix_listener_cannot_escape_its_private_runtime(self):
        runtime=Path(tempfile.gettempdir()).resolve()/'gateway-fixture-runtime'
        self.assertTrue(m.fixture_unix_bind_allowed(str(runtime/'control.sock'),runtime))
        for address in (str(runtime.parent/'gateway-fixture-runtime-other/control.sock'),str(runtime/'..'/'foreign.sock'),'relative.sock',b'\x00foreign'):
            with self.subTest(address=address):self.assertFalse(m.fixture_unix_bind_allowed(address,runtime))
    def test_manager_credentials_and_foreign_homes_are_removed_before_sdk_import(self):
        root=Path(tempfile.gettempdir()).resolve()/'gateway-fixture';runtime=root/'runtime'
        with mock.patch.dict(os.environ,{'API_SERVER_KEY':'synthetic-manager-secret','HERMES_HOME':'foreign-home','INVOCATION_ID':'fixture-native-invocation'},clear=True):
            m.close_environment(root,runtime)
            self.assertNotIn('API_SERVER_KEY',os.environ);self.assertEqual(str(root/'hermes-home'),os.environ['HERMES_HOME']);self.assertEqual('fixture-native-invocation',os.environ['INVOCATION_ID'])
            self.assertEqual(str(root/'locks'),os.environ['HERMES_GATEWAY_LOCK_DIR']);self.assertEqual(str(runtime.parent),os.environ['XDG_RUNTIME_DIR'])
    def test_relative_home_or_runtime_cannot_redirect_the_fixture(self):
        root=Path(tempfile.gettempdir()).resolve()/'gateway-fixture'
        for home,runtime in ((Path('relative-home'),root),(root,Path('relative-runtime'))):
            with self.assertRaises(ValueError):m.fixture_environment(home,runtime)
    def test_liveness_probe_cannot_signal_or_target_another_process(self):
        self.assertTrue(m.fixture_signal_probe_allowed(4000,0,4000))
        for pid,signum in ((4001,0),(4000,15),(4000,9),(0,0),(-1,0),(True,0),(4000,False)):
            with self.subTest(pid=pid,signum=signum):self.assertFalse(m.fixture_signal_probe_allowed(pid,signum,4000))
    def test_timing_probe_cannot_mutate_units_or_run_an_unreviewed_command(self):
        unit='signal-grove-acceptance-'+('a'*32)+'-gateway.service';argv=['systemctl','--user','show',unit,'--property=TimeoutStopUSec'];diagnostic=('/usr/bin/timeout','5','bash','-c','reviewed fixture script')
        self.assertTrue(m.fixture_process_allowed('systemctl',argv,None,None,unit,diagnostic))
        self.assertTrue(m.fixture_process_allowed('systemctl',['systemctl','show',unit,'--property=TimeoutStopUSec'],None,None,unit,diagnostic))
        self.assertTrue(m.fixture_process_allowed(diagnostic[0],list(diagnostic),None,None,unit,diagnostic))
        for executable,args,cwd,env in (('systemctl',['systemctl','--user','stop',unit],None,None),('systemctl',['systemctl','--user','show','foreign.service','--property=TimeoutStopUSec'],None,None),('bash',['bash','-c','unreviewed'],None,None),('systemctl',argv,'foreign-directory',None),('systemctl',argv,None,{'API_SERVER_KEY':'synthetic'})):
            with self.subTest(executable=executable,args=args):self.assertFalse(m.fixture_process_allowed(executable,args,cwd,env,unit,diagnostic))
    def test_native_epoch_accepts_zero_start_ticks_with_an_exact_boot_uuid(self):
        boot='a'*8+'-'+('b'*4)+'-'+('c'*4)+'-'+('d'*4)+'-'+('e'*12)
        self.assertEqual(boot+':0',m.checked_instantiation_epoch(boot,'0'));self.assertEqual(boot+':1234',m.checked_instantiation_epoch(boot,'1234'))
    def test_epoch_cannot_adopt_partial_or_malformed_native_identity(self):
        boot='a'*8+'-'+('b'*4)+'-'+('c'*4)+'-'+('d'*4)+'-'+('e'*12)
        for value,ticks in (('', '0'),('a'*36,'0'),(boot.upper(),'0'),(boot,''),(boot,'-1'),(boot,'00'),(boot,False),(boot,'1.5'),(boot,str(2**64))):
            with self.subTest(boot=value,ticks=ticks),self.assertRaises(ValueError):m.checked_instantiation_epoch(value,ticks)
    def test_busy_owned_drain_requires_a_fresh_idle_observation(self):
        sample=dict(fence_owner='fixture-operation',fence_epoch=1,admission_frozen=True,busy=True,lifecycle='draining',coverage='REGISTERED_WORK_ONLY',fleet_ready_to_switch=False)
        self.assertFalse(m.drain_is_settled(sample,'fixture-operation'))
        self.assertTrue(m.drain_is_settled({**sample,'busy':False},'fixture-operation'))
        self.assertFalse(m.drain_is_settled({**sample,'busy':False,'lifecycle':'running'},'fixture-operation'))
    def test_foreign_or_unfrozen_drain_cannot_become_a_wait(self):
        sample=dict(fence_owner='fixture-operation',fence_epoch=1,admission_frozen=True,busy=True,lifecycle='draining',coverage='REGISTERED_WORK_ONLY',fleet_ready_to_switch=False)
        for field,value in (('fence_owner','foreign-operation'),('fence_epoch',True),('fence_epoch',2),('admission_frozen',False),('busy',0),('lifecycle','stopped'),('coverage','FLEET'),('fleet_ready_to_switch',True)):
            with self.subTest(field=field),self.assertRaises(RuntimeError):m.drain_is_settled({**sample,field:value},'fixture-operation')

    def test_unknown_observer_mode_cannot_silently_disable_diagnostics(self):
        for mode in (None,False,'','off','NONE',[]):
            with self.subTest(mode=mode),self.assertRaises(ValueError):m.checked_observer_mode(mode)
    def test_profiler_free_facade_runs_without_invoking_an_observer(self):
        calls=[]
        async def facade():
            self.assertIsNone(sys.getprofile());self.assertIsNone(m.threading.getprofile());return True
        self.assertTrue(m.run_gateway_facade(facade,lambda *args:calls.append(True),'none'));self.assertEqual([],calls)
    def test_profile_facade_observes_and_restores_its_own_hooks(self):
        calls=[]
        def observer(*args):calls.append(True)
        async def facade():
            self.assertIs(observer,sys.getprofile());self.assertIs(observer,m.threading.getprofile());return True
        self.assertTrue(m.run_gateway_facade(facade,observer,'profile'));self.assertTrue(calls)
        self.assertIsNone(sys.getprofile());self.assertIsNone(m.threading.getprofile())
    def test_profile_hooks_are_released_when_the_sdk_facade_fails(self):
        async def facade():raise RuntimeError('generated fixture failure')
        with self.assertRaises(RuntimeError):m.run_gateway_facade(facade,lambda *args:None,'profile')
        self.assertIsNone(sys.getprofile());self.assertIsNone(m.threading.getprofile())
    def test_foreign_profiler_cannot_be_adopted_or_replaced(self):
        facade=mock.Mock()
        with mock.patch.object(sys,'getprofile',return_value=object()),self.assertRaises(ValueError):m.run_gateway_facade(facade,lambda *args:None,'none')
        facade.assert_not_called()
