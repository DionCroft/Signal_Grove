from dataclasses import replace
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
spec=importlib.util.spec_from_file_location('backup_sdk_snapshot',scripts/'backup_sdk_snapshot.py');m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)

class BackupSourceSchemaTests(unittest.TestCase):
    def test_duplicate_and_nonfinite_private_protocol_fields_are_refused(self):
        for data in ('{"x":1,"x":2}','{"x":NaN}','{"x":Infinity}'):
            with self.subTest(data=data),self.assertRaises(ValueError):m.unique_json(data)
    def test_source_inventory_binds_literal_unique_sorted_members(self):
        root=m.DirectoryAnchor('/fixture/source',1,2,1000,1000,0o700);good=m.SourceInventory(root,(('a.py','a'*64),));self.assertEqual(len(good.files),1)
        for files in ([('a.py','a'*64)],(('a.py','a'*64),('a.py','b'*64)),(('../a','a'*64),),(('a\\b','a'*64),),(('b','a'*64),('a','a'*64))):
            with self.subTest(files=files),self.assertRaises(ValueError):m.SourceInventory(root,files)
    def test_native_input_requires_single_owner_regular_nonwritable_inode(self):
        identity=(1,2,1000,1000,stat.S_IFREG|0o600,1,3,4,5);good=m.NativeInput('/fixture/key','a'*64,identity);self.assertEqual(good.bytes,3)
        for index,value in ((1,0),(2,0),(4,stat.S_IFLNK|0o777),(4,stat.S_IFREG|0o666),(5,2),(6,True)):
            row=list(identity);row[index]=value
            with self.subTest(index=index,value=value),self.assertRaises(ValueError):replace(good,identity=tuple(row))
    def test_builtin_memory_empty_documents_are_explicitly_accepted(self):
        for value in (None,{}, {'memory':None},{'memory':{}},{'memory':{'provider':None}},{'memory':{'provider':''}}):m.require_builtin_memory_document(value)
    def test_reviewed_code_metadata_cannot_be_substituted_for_exclusive_keys(self):
        for mode in (0o600,0o711):
            identity=(1,2,1000,1000,stat.S_IFREG|mode,4,3,4,5);code=m.ReviewedCodeInput('/fixture/module.py','a'*64,identity);self.assertEqual(code.identity[5],4)
            with self.assertRaises(ValueError):m.NativeInput(code.path,code.sha256,identity)
        for mode in (0o660,0o777,0o4711):
            with self.assertRaises(ValueError):m.ReviewedCodeInput('/fixture/module.py','a'*64,(1,2,1000,1000,stat.S_IFREG|mode,4,3,4,5))
    def test_active_or_malformed_memory_is_not_silently_empty(self):
        for value in ([],False,{'memory':[]},{'memory':False},{'memory':{'provider':False}},{'memory':{'provider':0}},{'memory':{'provider':'honcho'}},{'memory':{'provider':'${MEMORY_PROVIDER}'}}):
            with self.subTest(value=value),self.assertRaises(m.Refused):m.require_builtin_memory_document(value)
    def test_loader_compiles_exact_reviewed_source_without_cache_execution(self):
        code=b'reviewed_value = 42\n';image=m.NativeInput('/fixture/module.py',hashlib.sha256(code).hexdigest(),(1,2,1000,1000,stat.S_IFREG|0o644,1,len(code),4,5));record={}
        loader=m._SourceLoader('fixture','/fixture/module.py',image.sha256,record)
        with patch.object(m,'read_code',return_value=(code,image)) as reader:compiled=loader.get_code('fixture')
        scope={};exec(compiled,scope);self.assertEqual(scope['reviewed_value'],42);self.assertEqual(record,{image.path:image.sha256});reader.assert_called_once_with('/fixture/module.py')
    def test_loader_rejects_changed_source_before_compilation_or_execution(self):
        code=b'raise RuntimeError("must not execute")\n';image=m.NativeInput('/fixture/module.py',hashlib.sha256(code).hexdigest(),(1,2,1000,1000,stat.S_IFREG|0o644,1,len(code),4,5));record={}
        with patch.object(m,'read_code',return_value=(code,image)),self.assertRaises(ImportError):m._SourceLoader('fixture',image.path,'a'*64,record).get_code('fixture')
        self.assertEqual(record,{})

@unittest.skipUnless(sys.platform=='linux','Native SDK source selection and protected inputs run on Linux')
class NativeBackupSourceTests(unittest.TestCase):
    def setUp(self):
        retain=os.environ.get('SIGNAL_GROVE_SNAPSHOT_RETAIN_ROOT');self.temp=None
        if retain:
            import uuid
            self.root=Path(retain)/('source-'+uuid.uuid4().hex);self.root.mkdir(mode=0o700)
        else:
            self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-source-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.root.chmod(0o700)
    def write(self,name,data):
        p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True,mode=0o700);p.write_bytes(data);p.chmod(0o600);return p
    def sdk(self,external=()):return SimpleNamespace(_collect_memory_provider_external_paths=lambda:list(external))
    def config(self,value=None):return SimpleNamespace(load_config=lambda:{} if value is None else value)
    def test_exact_file_observation_preserves_content_and_identity(self):
        p=self.write('input',b'bounded');before=p.lstat();data,image=m.read_input(p);self.assertEqual(data,b'bounded');self.assertEqual(image,m.observe_input(p));self.assertEqual(m.stamp(p.lstat()),m.stamp(before))
    def test_symlink_hardlink_and_group_writable_inputs_are_refused(self):
        p=self.write('input',b'preserve');link=self.root/'link';link.symlink_to(p)
        with self.assertRaises(m.Refused):m.read_input(link)
        link.unlink();os.link(p,link)
        with self.assertRaises(m.Refused):m.read_input(p)
        link.unlink();p.chmod(0o660)
        with self.assertRaises(m.Refused):m.read_input(p)
        self.assertEqual(p.read_bytes(),b'preserve')
    def test_observation_bound_refuses_without_modifying_the_large_input(self):
        p=self.write('input',b'1234')
        with self.assertRaises(m.Refused):m.observe_input(p,limit=3)
        self.assertEqual(p.read_bytes(),b'1234')
    def test_reviewed_inventory_detects_changed_source_bytes(self):
        p=self.write('module.py',b'value=1\n');inventory=m.SourceInventory(m.anchor(self.root),(('module.py',m.observe_input(p).sha256),));before=inventory.observe();p.write_bytes(b'value=2\n')
        with self.assertRaises(m.Refused):inventory.observe()
        self.assertTrue(before)
    def test_pm_hardlinked_code_is_read_without_repair_and_alias_changes_refuse(self):
        p=self.write('module.py',b'value=42\n');alias=self.root/'cache-alias';os.link(p,alias);before=m.stamp(p.lstat());image=m.observe_code(p)
        self.assertEqual(image.identity[5],2);self.assertEqual(m.stamp(p.lstat()),before)
        with self.assertRaises(m.Refused):m.observe_input(p)
        scope={};exec(m._SourceLoader('fixture',str(p),image.sha256,{}).get_code('fixture'),scope);self.assertEqual(scope['value'],42)
        alias.write_bytes(b'value=99\n')
        with self.assertRaises(ImportError):m._SourceLoader('fixture',str(p),image.sha256,{}).get_code('fixture')
    def test_loader_ignores_a_poisoned_bytecode_cache(self):
        p=self.write('module.py',b'value=42\n');cache=self.root/'__pycache__';cache.mkdir(mode=0o700);(cache/'module.cpython-314.pyc').write_bytes(b'poisoned cache must not load')
        image=m.observe_input(p);scope={};exec(m._SourceLoader('fixture',str(p),image.sha256,{}).get_code('fixture'),scope);self.assertEqual(scope['value'],42)
    def test_configured_external_provider_refuses_even_if_sdk_helper_returns_empty(self):
        self.write('config.yaml',b'{"memory":{"provider":"honcho"}}')
        with self.assertRaises(m.Refused):m._memory(self.sdk(),self.root,SimpleNamespace(safe_load=json.loads),self.config())
    def test_named_profile_provider_is_not_lost_by_root_only_sdk_detection(self):
        self.write('config.yaml',b'{}');self.write('profiles/garden/config.yaml',b'{"memory":{"provider":"external"}}')
        with self.assertRaises(m.Refused):m._memory(self.sdk(),self.root,SimpleNamespace(safe_load=json.loads),self.config())
    def test_merged_provider_or_declared_external_state_also_refuses(self):
        self.write('config.yaml',b'{}');yaml=SimpleNamespace(safe_load=json.loads)
        with self.assertRaises(m.Refused):m._memory(self.sdk(),self.root,yaml,self.config({'memory':{'provider':'external'}}))
        with self.assertRaises(m.Refused):m._memory(self.sdk((self.root,)),self.root,yaml,self.config())
    def test_valid_builtin_memory_selection_binds_root_and_profile_configs(self):
        root=self.write('config.yaml',b'{"memory":{"provider":null}}');profile=self.write('profiles/garden/config.yaml',b'{}')
        result=m._memory(self.sdk(),self.root,SimpleNamespace(safe_load=json.loads),self.config())
        self.assertEqual(result,{'config.yaml':m.observe_input(root).sha256,'profiles/garden/config.yaml':m.observe_input(profile).sha256})
    def test_invalid_yaml_cannot_fall_through_to_sdk_empty_provider(self):
        self.write('config.yaml',b'{broken')
        with self.assertRaises(ValueError):m._memory(self.sdk(),self.root,SimpleNamespace(safe_load=json.loads),self.config())

if __name__=='__main__':unittest.main()
