from dataclasses import replace
import importlib.util
from pathlib import Path
import sys
import unittest

for name in ('retirement_guard','native_retirement_owner','cutover_journal','cutover_coordinator','native_service_manager'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'));m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m)
from retirement_guard import Owner
from cutover_coordinator import ApprovedExecution
from cutover_journal import Refused,Unknown
from native_service_manager import NativeExecutionScope,execution_configuration,SCOPE_COMMANDS

OP='a'*32;OWNER=Owner(4000,50,'00000000-0000-4000-8000-000000000001');UNIT='signal-grove-cutover-'+OP+'.service'

def scope():return NativeExecutionScope(OP,UNIT,OWNER,'b'*32,'/fixture/'+UNIT,'c'*64,1000_000_000,120_000_000,5_000_000)

class ExecutionScopeTests(unittest.TestCase):
    def test_exact_operation_unit_and_cgroup_are_required(self):
        for change in (dict(operation='bad'),dict(unit='hermes-gateway.service'),dict(cgroup='/fixture/other.service'),dict(invocation='')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(scope(),**change)
    def test_infinite_zero_boolean_or_excessive_lifetimes_are_refused(self):
        for change in (dict(runtime_max_usec=2**64-1),dict(runtime_max_usec=0),dict(runtime_max_usec=True),dict(stop_timeout_usec=11_000_000),dict(active_enter_usec=False)):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(scope(),**change)
    def test_termination_grace_is_inside_the_exact_approval_window(self):
        e=ApprovedExecution(OP,'a'*64,'b'*64,'forward','d'*64,1125)
        scope().require_window(e,OWNER,1100)
        with self.assertRaises(Refused):scope().require_window(replace(e,deadline=1124.9),OWNER,1100)
        with self.assertRaises(Refused):scope().require_window(e,OWNER,1125)
    def test_different_operation_owner_or_non_native_clock_is_refused(self):
        e=ApprovedExecution(OP,'a'*64,'b'*64,'forward','d'*64,1125)
        for execution,owner,now in ((replace(e,operation='e'*32),OWNER,1100),(e,replace(OWNER,start_ticks=51),1100),(e,OWNER,True)):
            with self.subTest(now=now),self.assertRaises(Refused):scope().require_window(execution,owner,now)
    def test_configuration_requires_one_start_and_no_auxiliary_hook(self):
        row=['/fixture/python',['/fixture/python','-B','controller.py'],False,0,0,0,0,0,0,0]
        commands={n:[] for n in SCOPE_COMMANDS};commands['ExecStart']=[row]
        first=execution_configuration(UNIT,'exec',commands)
        commands['ExecStopPost']=[row]
        with self.assertRaises(Refused):execution_configuration(UNIT,'exec',commands)
        self.assertEqual(len(first),64)
    def test_runtime_counters_do_not_hide_changed_start_arguments(self):
        row=['/fixture/python',['/fixture/python','controller.py'],False,0,0,0,0,0,0,0];commands={n:[] for n in SCOPE_COMMANDS};commands['ExecStart']=[row]
        first=execution_configuration(UNIT,'exec',commands);row[3:]=[1,2,3,4,4000,1,0]
        self.assertEqual(first,execution_configuration(UNIT,'exec',commands));row[1]=['/fixture/python','other.py']
        self.assertNotEqual(first,execution_configuration(UNIT,'exec',commands))

if __name__=='__main__':unittest.main()
