from pathlib import Path
import asyncio
import importlib.util
import json
import sys
import threading
import types
import unittest
from concurrent.futures import Future
from unittest import mock

scripts=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sdk_work_diagnostics',scripts/'native/sdk_work_diagnostics.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class WorkDiagnosticsTests(unittest.TestCase):
    def test_foreign_absolute_path_and_frame_locals_are_not_serialized(self):
        secret='synthetic-private-content-never-in-observation'
        result=m.frame_stack(sys._getframe(),Path('/reviewed-sdk'))
        encoded=json.dumps(result)
        self.assertNotIn(secret,encoded);self.assertNotIn(str(Path(__file__).parent),encoded)
        self.assertEqual('<outside-reviewed-sdk>',result['frames'][0]['module'])
    def test_reviewed_code_location_is_relative(self):
        root=Path(__file__).resolve().parent/'reviewed-sdk'
        code=compile('pass',str(root/'gateway/periodic_work.py'),'exec')
        self.assertEqual(dict(module='gateway/periodic_work.py',function='<module>',line=1),m.code_location(code,1,root))
    def test_frame_traversal_has_a_hard_bound(self):
        def nested(depth):return nested(depth-1) if depth else m.frame_stack(sys._getframe(),Path('/reviewed-sdk'))
        result=nested(40);self.assertEqual(m.MAX_FRAMES,len(result['frames']));self.assertTrue(result['truncated'])
    def test_opaque_awaitable_is_never_inspected_or_represented(self):
        class Private:
            def __getattribute__(self,name):raise AssertionError('Private attribute accessed')
            def __repr__(self):raise AssertionError('Private object represented')
        result=m.await_chain(Private(),Path('/reviewed-sdk'))
        self.assertEqual([],result['frames']);self.assertEqual('opaque-awaitable',result['leaf'])
    def test_registry_observation_keeps_native_future_state_without_cancelling(self):
        pending=Future();running=Future();running.set_running_or_notify_cancel();done=Future();done.set_result('private-result')
        registry=types.SimpleNamespace(_lock=threading.RLock(),_pending={pending,running,done})
        result=m.registry_states(registry,futures=True)
        self.assertEqual(3,result['pending']);self.assertEqual(1,sum(r['running'] for r in result['futures']))
        self.assertNotIn('private-result',json.dumps(result));self.assertFalse(pending.cancelled());self.assertEqual(3,len(registry._pending))
    def test_unavailable_registry_lock_is_unknown_and_never_forced(self):
        lock=mock.Mock();lock.acquire.return_value=False
        result=m.registry_states(types.SimpleNamespace(_lock=lock),futures=True)
        self.assertEqual(dict(state='LOCK_UNAVAILABLE'),result);lock.acquire.assert_called_once_with(timeout=.02);lock.release.assert_not_called()
    def test_async_snapshot_omits_task_names_and_coroutine_content(self):
        async def check():
            event=asyncio.Event()
            async def private_waiter():
                private_prompt='synthetic-secret-prompt'
                await event.wait()
            task=asyncio.create_task(private_waiter(),name='synthetic-secret-task-name');await asyncio.sleep(0)
            registry=types.SimpleNamespace(_lock=threading.RLock(),_pending=set())
            runner=types.SimpleNamespace(_executor_work=registry,_periodic_work=registry)
            try:
                result=m.observe_work(runner,Path('/reviewed-sdk'),dict(state='OBSERVED',busy=True,contract='gateway_live_work_v1',counters={'executor_jobs':1}))
                encoded=json.dumps(result)
                for secret in ('synthetic-secret-prompt','synthetic-secret-task-name'):self.assertNotIn(secret,encoded)
                self.assertGreaterEqual(len(result['tasks']),2);self.assertFalse(result['acceptance_permission']);self.assertFalse(task.done())
            finally:task.cancel();await asyncio.gather(task,return_exceptions=True)
        asyncio.run(check())
    def test_idle_unknown_or_non_boolean_busy_cannot_request_diagnostics(self):
        for state,busy in (('UNKNOWN',True),('OBSERVED',False),('OBSERVED',1)):
            with self.subTest(state=state,busy=busy),self.assertRaises(ValueError):m.observe_work(None,Path('/reviewed-sdk'),dict(state=state,busy=busy,contract='gateway_live_work_v1'))
