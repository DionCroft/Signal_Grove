"""Finite actual-Hermes room fixture boundary, never an execution or profile-private oracle."""
import ast
import hashlib
from pathlib import Path
import unittest

_tree=ast.parse((Path(__file__).resolve().parents[1]/'compatibility_fixture_native_auth_tcp.py').read_text())
_function=next(n for n in _tree.body if isinstance(n,ast.FunctionDef) and n.name=='hosted_room_fixture_rpc_allowed')
_namespace={}
exec(compile(ast.Module(body=[_function],type_ignores=[]),'<local-room-fixture-boundary>','exec'),_namespace)
allowed=_namespace['hosted_room_fixture_rpc_allowed']

class HostedRoomFixtureBoundaryTest(unittest.TestCase):
    def test_capabilities_have_exact_positions_profile_and_fields(self):
        for profile in ('default','worker'):
            for count in (0,2):self.assertTrue(allowed('groups.capabilities',{'profile':profile},count))
        self.assertFalse(allowed('groups.capabilities',{'profile':'worker'},1))
        for params in ({},{'profile':'missing-fixture'},{'profile':'worker','extra':True},[],None):
            self.assertFalse(allowed('groups.capabilities',params,0))

    def test_lists_keep_fixed_limit_cursors_and_real_boolean_filter(self):
        for profile in ('default','worker'):
            for offset in (0,100):
                for flag in (False,True):self.assertTrue(allowed('groups.list',dict(profile=profile,limit=100,offset=offset,include_disbanded=flag),1))
        params=dict(profile='worker',limit=100,offset=0,include_disbanded=False)
        for key,values in [('profile',['missing-fixture',None]),('limit',[True,100.0,99,101]),('offset',[True,0.0,-1,101]),('include_disbanded',[0,1,'false',None])]:
            for value in values:self.assertFalse(allowed('groups.list',dict(params,**{key:value}),1),(key,value))
        self.assertFalse(allowed('groups.list',dict(params,extra=True),1))

    def test_details_keep_exact_synthetic_identity_and_no_context_write(self):
        for identity in ('fixture-shared-room','fixture-disbanded-room'):
            for flag in (False,True):self.assertTrue(allowed('groups.state',dict(profile='worker',room_id=identity,include_disbanded=flag),1))
        params=dict(profile='worker',room_id='fixture-shared-room',include_disbanded=False)
        for changed in [dict(params,room_id='foreign-room'),dict(params,include_disbanded=1),dict(params,event_id='write'),dict(params,purpose='write')]:
            self.assertFalse(allowed('groups.state',changed,1))

    def test_no_work_or_other_reads_or_unbounded_repetition_are_admitted(self):
        params=dict(profile='worker',limit=100,offset=0,include_disbanded=False)
        for count in (-1,3,100,False,1.0):self.assertFalse(allowed('groups.list',params,count))
        for method in ('groups.create','groups.context','groups.rename','groups.disband','groups.send','groups.stop','groups.approve','groups.peer.invite','groups.log','session.resume','session.submit','worker.dispatch','files.read','unknown'):
            for count in (0,1,2):self.assertFalse(allowed(method,params,count))

    def test_old_canonical_boundary_is_byte_independent_and_unchanged(self):
        function=next(n for n in _tree.body if isinstance(n,ast.FunctionDef) and n.name=='canonical_fixture_rpc_allowed')
        self.assertEqual('9af86022f9c9169cdf735d55ec87ba58f8241f1ad0d28126ac89e24ac282629b',hashlib.sha256(ast.dump(function,include_attributes=False).encode()).hexdigest())

    def test_only_room_fixture_omits_background_startup_without_replacing_handlers(self):
        call=next(n for n in ast.walk(_tree) if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and n.func.attr=='Config')
        choice=next(k.value for k in call.keywords if k.arg=='lifespan')
        self.assertIsInstance(choice,ast.IfExp)
        self.assertEqual("a.surface == 'rooms'",ast.unparse(choice.test))
        self.assertEqual('off',ast.literal_eval(choice.body))
        self.assertEqual('on',ast.literal_eval(choice.orelse))

    def test_room_source_pins_are_exact_immutable_present_module_sets(self):
        value=next(n.value for n in _tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='_ROOM_READ_PINS' for t in n.targets))
        pins=ast.literal_eval(value)
        self.assertEqual({'stable-2bd378e':15,'prior-bf0203a':12,'main-21ae0e1':16},{k:len(v) for k,v in pins.items()})
        required={'tui_gateway/methods_groups.py','gateway/hosted_rooms.py','gateway/hosted_rooms_common.py','tui_gateway/contracts/groups_bot_relay.py','tui_gateway/hosted_room_service.py'}
        for files in pins.values():
            self.assertTrue(required<=set(files))
            for path,digest in files.items():
                self.assertNotIn('..',path.split('/'));self.assertRegex(digest,r'^[0-9a-f]{40}$')

if __name__=='__main__':unittest.main()
