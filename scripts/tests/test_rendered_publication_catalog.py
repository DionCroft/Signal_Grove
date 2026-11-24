from dataclasses import replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path,PurePosixPath
import sys
import tempfile
import unittest
import uuid

for name in ('cutover_journal','cutover_coordinator','plan_pi_runtime','native_file_publication','rendered_publication_catalog'):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).resolve().parents[1]/(name+'.py'))
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
from cutover_journal import Refused
from native_file_publication import DirectoryCreation,FILE_ROLES
from rendered_publication_catalog import RenderedCatalog,PolicyReview,capture_native

LAYOUT=dict(account_home='/home/owner',fleet_root='/home/owner/Projects/fleet',generation='next-v1',source_name='runtime-source')
LAUNCHER=b'#!/bin/sh\nexit 0\n';LAUNCHER_SHA=hashlib.sha256(LAUNCHER).hexdigest()


def definition(layout=LAYOUT,*,root=None,extra=None):
    profile=(str(root)+'/account/Projects/fleet/hermes') if root else layout['fleet_root']+'/hermes'
    policy=dict(schema=1,authority='fixture-authority',grants=[dict(subject='fixture-owner',profile_id=profile,device='FIXTURE',project_ids=['fixture'])])
    if extra:policy.update(extra)
    data=json.dumps(policy,separators=(',',':')).encode();review=PolicyReview('fixture-authority',('FIXTURE',),hashlib.sha256(data).hexdigest())
    return RenderedCatalog.for_owned_fixture(root,review,data,LAUNCHER_SHA) if root else RenderedCatalog.for_layout(layout,review,data,LAUNCHER_SHA)


class RenderedCatalogSchemaTests(unittest.TestCase):
    def test_ten_targets_are_the_exact_renderer_paths_and_complete_role_set(self):
        catalog=definition();targets={s.role:s.path for s in catalog.specs()};base='/home/owner/Projects/fleet';runtime=base+'/hermes/runtime-generations/next-v1/commands-signal-grove-v1'
        expected=dict(source_gateway=base+'/scripts/systemd/next-v1/hermes-gateway.service.conf',source_serve=base+'/scripts/systemd/next-v1/hermes-serve.service.conf',
            source_client=base+'/scripts/bin/next-v1/hermes-client',source_server=base+'/scripts/bin/next-v1/hermes-serve',runtime_client=runtime+'/hermes-client',runtime_server=runtime+'/hermes-serve',
            policy='/home/owner/.local/state/signal-grove-workspace/workspace-read-policy.json',gateway_link='/home/owner/.config/systemd/user/hermes-gateway.service.d/100-signal-grove.conf',
            serve_link='/home/owner/.config/systemd/user/hermes-serve.service.d/100-signal-grove.conf',client_link='/home/owner/.local/bin/hermes')
        self.assertEqual(targets,expected);self.assertEqual(tuple(targets),tuple(FILE_ROLES));self.assertEqual(catalog.scope,'production_review_only')

    def test_layout_cannot_add_credentials_routes_or_arbitrary_commands(self):
        for added in ('token','command','provider','target'):
            with self.subTest(added=added),self.assertRaises(ValueError):definition(dict(LAYOUT,**{added:'PRIVATE_FIXTURE'}))

    def test_fleet_must_be_a_dedicated_projects_child(self):
        for root in ('/home/owner','/home/owner/Projects','/home/owner/elsewhere/fleet','/home/owner/Projects/../outside'):
            with self.subTest(root=root),self.assertRaises(ValueError):definition(dict(LAYOUT,fleet_root=root))

    def test_payload_hash_is_not_implicit_policy_approval(self):
        catalog=definition()
        with self.assertRaises(ValueError):replace(catalog,policy_bytes=catalog.policy_bytes+b' ')
        self.assertNotIn('fixture-owner',repr(catalog));self.assertNotIn('fixture-owner',json.dumps(catalog.metadata()))

    def test_owned_roots_use_a_separate_fixed_fixture_layout_and_scope(self):
        root=PurePosixPath('/home/owner/staging/signal-grove-catalog-'+'a'*32);catalog=definition(root=root)
        self.assertEqual(catalog.scope,'owned_fixture_only');self.assertEqual(catalog.packet()['state'],'OWNED_RENDERED_FIXTURE_ONLY')
        for spec in catalog.specs():
            self.assertTrue(spec.path.startswith(str(root)+'/account/'));self.assertNotIn('/home/fixture',spec.value.decode() if type(spec.value) is bytes else spec.value)
        with self.assertRaises(ValueError):replace(catalog,layout=tuple(sorted(LAYOUT.items())))
        with self.assertRaises(ValueError):replace(catalog,owned_root='/home/owner')

    def test_reference_targets_and_wrapper_commands_come_only_from_renderer_outputs(self):
        specs={s.role:s for s in definition().specs()};base=LAYOUT['fleet_root'];commands=base+'/hermes/runtime-generations/next-v1/commands-signal-grove-v1'
        self.assertEqual(specs['gateway_link'].value,base+'/scripts/systemd/next-v1/hermes-gateway.service.conf')
        self.assertEqual(specs['client_link'].value,commands+'/hermes-client');self.assertIn((commands+'/hermes gateway run').encode(),specs['source_gateway'].value)
        self.assertEqual(specs['source_client'].value,specs['runtime_client'].value);self.assertEqual(specs['source_server'].value,specs['runtime_server'].value)

    def test_policy_review_requires_fixed_unique_native_device_names(self):
        good=definition().policy_review
        for devices in (('fixture',),('FIXTURE','FIXTURE'),(),['FIXTURE']):
            with self.subTest(devices=devices),self.assertRaises(ValueError):replace(good,devices=devices)

    def test_review_hash_changes_with_generation_or_source_binding(self):
        self.assertNotEqual(definition().sha256,definition(dict(LAYOUT,generation='next-v2')).sha256)
        self.assertNotEqual(definition().sha256,definition(dict(LAYOUT,source_name='other-runtime')).sha256)


@unittest.skipUnless(sys.platform=='linux','Native renderer capture/policy/path checks run on the actual Pi')
class NativeRenderedCatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-catalog-test-');self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve()/('signal-grove-catalog-'+uuid.uuid4().hex);self.root.mkdir(mode=0o700);self.catalog=definition(root=self.root)
        p=self.catalog.packet();account=Path(p['layout']['account_home']);self.commands=Path(p['command_directory']);self.commands.mkdir(parents=True,mode=0o700)
        self.launcher=self.commands/'hermes';self.launcher.write_bytes(LAUNCHER);self.launcher.chmod(0o755)
        client=account/'.local/bin';client.mkdir(parents=True,mode=0o700);(client/'hermes').write_bytes(b'original-owner-client');(client/'hermes').chmod(0o755)
        (account/'.config/systemd/user').mkdir(parents=True,mode=0o700)
        (account/'.local/state').mkdir(mode=0o700)
        for _,path in self.catalog.mode_paths():
            directory=Path(path);directory.mkdir(parents=True,exist_ok=True,mode=0o700);directory.chmod(0o775)

    def tree(self):
        return sorted((p.relative_to(self.root).as_posix(),p.lstat().st_ino,p.lstat().st_mode,p.lstat().st_size,p.lstat().st_mtime_ns,p.lstat().st_ctime_ns) for p in self.root.rglob('*'))

    def test_native_capture_is_read_only_and_keeps_all_ten_original_preimages(self):
        before=self.tree();captured=capture_native(self.catalog,self.catalog.sha256)
        self.assertEqual(self.tree(),before);self.assertEqual(len(captured.bindings),10);self.assertEqual(len(captured.modes),7)
        self.assertEqual(dict(captured.original_values)['client_link'],b'original-owner-client');self.assertEqual(len(captured.for_stage('publish_reviewed')),6)
        self.assertIs(type(captured.for_stage('install_policy')[0].parent),DirectoryCreation);captured.assert_launcher()

    def test_changed_launcher_refuses_before_any_directory_preparation(self):
        self.launcher.write_bytes(b'changed-launcher');before=self.tree()
        with self.assertRaises(Refused):capture_native(self.catalog,self.catalog.sha256)
        self.assertEqual(self.tree(),before)

    def test_reserved_dropin_name_is_not_adopted_or_overwritten(self):
        target=Path(next(s.path for s in self.catalog.specs() if s.role=='gateway_link'));target.parent.mkdir(mode=0o700);target.write_bytes(b'existing-owner-dropin');before=self.tree()
        with self.assertRaises(Refused):capture_native(self.catalog,self.catalog.sha256)
        self.assertEqual(self.tree(),before);self.assertEqual(target.read_bytes(),b'existing-owner-dropin')

    def test_unreviewed_writable_parent_cannot_be_added_to_the_seven_mode_roles(self):
        self.commands.chmod(0o775);before=self.tree()
        with self.assertRaises(Refused):capture_native(self.catalog,self.catalog.sha256)
        self.assertEqual(self.tree(),before)

    def test_policy_with_unknown_fields_fails_the_real_maintained_parser(self):
        bad=definition(root=self.root,extra=dict(credential='SYNTHETIC_PRIVATE_VALUE'));before=self.tree()
        with self.assertRaises(Refused) as caught:capture_native(bad,bad.sha256)
        self.assertNotIn('SYNTHETIC_PRIVATE_VALUE',str(caught.exception));self.assertEqual(self.tree(),before)

    def test_different_new_generation_source_is_preserved_for_review(self):
        target=Path(next(s.path for s in self.catalog.specs() if s.role=='source_gateway'));target.parent.mkdir(mode=0o700);target.write_bytes(b'custom-owner-source');target.chmod(0o644);before=self.tree()
        with self.assertRaises(Refused):capture_native(self.catalog,self.catalog.sha256)
        self.assertEqual(self.tree(),before);self.assertEqual(target.read_bytes(),b'custom-owner-source')

    def test_original_cli_permissions_must_be_exactly_supported_by_recovery(self):
        path=Path(next(s.path for s in self.catalog.specs() if s.role=='client_link'));path.chmod(0o644);before=self.tree()
        with self.assertRaises(Refused):capture_native(self.catalog,self.catalog.sha256)
        self.assertEqual(self.tree(),before);self.assertEqual(path.stat().st_mode&0o777,0o644)

    def test_wrong_review_definition_never_rebinds_to_current_state(self):
        before=self.tree()
        with self.assertRaises(Refused):capture_native(self.catalog,'f'*64)
        self.assertEqual(self.tree(),before)


if __name__=='__main__':unittest.main()
