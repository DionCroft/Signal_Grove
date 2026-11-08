"""The fixture emits a finite immutable observation plan, never a command surface."""
import ast
from pathlib import Path
import unittest
_tree=ast.parse((Path(__file__).resolve().parents[1]/'compatibility_fixture_native_auth_tcp.py').read_text())
_node=next(n for n in _tree.body if isinstance(n,ast.FunctionDef) and n.name=='canonical_viewer_activity')
_namespace={}
exec(compile(ast.Module(body=[_node],type_ignores=[]),'<local-fixed-activity-plan>','exec'),_namespace)
plan=_namespace['canonical_viewer_activity']

class ViewerFixtureActivityBoundaryTest(unittest.TestCase):
    def test_only_four_fixed_non_work_events_can_be_generated(self):
        opening=plan('opening');live=plan('live')
        self.assertEqual(['status.update','reasoning.delta','message.delta'],[x[0] for x in opening])
        self.assertEqual(['status.update'],[x[0] for x in live])
        self.assertEqual(4,len(opening+live))
        for kind,payload in opening+live:
            self.assertEqual({'text','kind'} if kind=='status.update' else {'text'},set(payload))
            self.assertLess(len(payload['text']),100)
            self.assertNotIn('command',payload);self.assertNotIn('session_id',payload)

    def test_unknown_phases_cannot_expand_the_observation_surface(self):
        for value in ('submit','tool.run','worker.dispatch','',None,0,[],{}):
            with self.subTest(value=value):self.assertRaises(ValueError,plan,value)

    def test_mutating_a_returned_payload_cannot_poison_the_next_plan(self):
        first=plan('opening');first[0][1]['text']='changed';first.append(('session.submit',{}))
        fresh=plan('opening');self.assertEqual(3,len(fresh));self.assertEqual('Fixture public status one',fresh[0][1]['text'])

if __name__=='__main__':unittest.main()
