from pathlib import Path
import importlib.util
import sys
import unittest
from unittest import mock

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
spec=importlib.util.spec_from_file_location('sdk_gateway_lifecycle_fixture',scripts/'native/sdk_gateway_lifecycle_fixture.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class Clock:
    def __init__(self,value=0.):self.value=value
    def now(self):return self.value
    def pause(self,seconds):self.value+=seconds


class ObservationWindowTests(unittest.TestCase):
    def wait(self,sample,predicate,clock,*,started=0.,deadline=5.):
        return m.wait_for_status(sample,predicate,started=started,deadline=deadline,clock=clock.now,pause=clock.pause)
    def test_known_busy_requires_a_new_fresh_idle_reply(self):
        clock=Clock();samples=iter([{'busy':True},{'busy':True},{'busy':False}]);calls=[]
        def sample():calls.append(True);return next(samples)
        result,accepted=self.wait(sample,lambda row:not row['busy'],clock)
        self.assertEqual(clock.value,accepted)
        self.assertFalse(result['busy']);self.assertEqual(3,len(calls));self.assertAlmostEqual(.4,clock.value)
    def test_continuously_busy_cannot_extend_the_original_window(self):
        clock=Clock();calls=[]
        def sample():calls.append(True);return {'busy':True}
        with self.assertRaises(RuntimeError):self.wait(sample,lambda row:not row['busy'],clock)
        self.assertGreaterEqual(clock.value,5);self.assertLessEqual(len(calls),26)
    def test_idle_read_which_finishes_after_expiry_is_held(self):
        clock=Clock()
        def sample():clock.value=5.001;return {'busy':False}
        with self.assertRaises(RuntimeError):self.wait(sample,lambda row:not row['busy'],clock)
    def test_expired_recording_budget_does_not_dispatch_a_request(self):
        clock=Clock(5.001);sample=mock.Mock()
        with self.assertRaises(RuntimeError):self.wait(sample,lambda row:True,clock)
        sample.assert_not_called()
    def test_idle_at_the_exact_deadline_is_not_accepted(self):
        clock=Clock()
        def sample():clock.value=5.;return {'busy':False}
        with self.assertRaises(RuntimeError):self.wait(sample,lambda row:not row['busy'],clock)
    def test_unknown_observation_is_never_polled_again(self):
        clock=Clock();sample=mock.Mock(side_effect=RuntimeError('generated unknown observation'))
        with self.assertRaises(RuntimeError):self.wait(sample,lambda row:True,clock)
        sample.assert_called_once_with();self.assertEqual(0.,clock.value)
    def test_invalid_or_backwards_clock_cannot_make_an_expired_sample_fresh(self):
        for value in (-1.,float('nan'),float('inf'),True,'0'):
            clock=Clock();sample=mock.Mock(side_effect=lambda:dict(busy=False))
            def bad_sample():clock.value=value;return sample()
            with self.subTest(value=value),self.assertRaises(RuntimeError):self.wait(bad_sample,lambda row:True,clock)
    def test_invalid_or_widened_window_is_refused_before_native_io(self):
        for start,end in ((0.,5.001),(5.,5.),(-1.,4.),(False,5.),(0.,float('inf')),(float('nan'),5.)):
            sample=mock.Mock()
            with self.subTest(start=start,end=end),self.assertRaises(ValueError):self.wait(sample,lambda row:True,Clock(),started=start,deadline=end)
            sample.assert_not_called()
    def test_predicate_must_return_an_exact_boolean(self):
        with self.assertRaises(RuntimeError):self.wait(lambda:{'busy':False},lambda row:1,Clock())
    def test_initial_known_busy_is_preserved_until_a_fresh_idle_status(self):
        row=dict(fence_epoch=0,fence_owner=None,admission_frozen=False,busy=True,lifecycle='running',coverage='REGISTERED_WORK_ONLY',fleet_ready_to_switch=False)
        self.assertFalse(m.startup_is_idle(row));self.assertTrue(m.startup_is_idle({**row,'busy':False}));self.assertTrue(row['busy'])
    def test_foreign_frozen_unknown_or_wrong_lifecycle_does_not_become_a_wait(self):
        row=dict(fence_epoch=0,fence_owner=None,admission_frozen=False,busy=True,lifecycle='running',coverage='REGISTERED_WORK_ONLY',fleet_ready_to_switch=False)
        for field,value in (('fence_epoch',True),('fence_epoch',1),('fence_owner','other-owner'),('admission_frozen',True),('busy',1),('lifecycle','draining'),('coverage','FLEET'),('fleet_ready_to_switch',True)):
            with self.subTest(field=field),self.assertRaises(RuntimeError):m.startup_is_idle({**row,field:value})

    def test_predicate_or_receipt_work_cannot_accept_a_late_idle_sample(self):
        clock=Clock()
        def predicate(row):clock.value=5.001;return True
        with self.assertRaises(RuntimeError):self.wait(lambda:{'busy':False},predicate,clock)
