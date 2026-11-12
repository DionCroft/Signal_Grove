"""Portable protocol checks. Windows native acceptance is a separate required job."""
import importlib.util
from pathlib import Path
import socket
import sys
import unittest
from unittest.mock import Mock,patch
spec=importlib.util.spec_from_file_location('workspace_provider_policy',Path(__file__).resolve().parents[2]/'server_extensions/worker_workspaces/workspace_provider.py')
p=importlib.util.module_from_spec(spec);sys.modules[spec.name]=p;spec.loader.exec_module(p)


class WorkspaceBoundaryTest(unittest.TestCase):
    def test_relative_paths_cannot_escape_expand_or_address_windows_devices(self):
        for value in ['../outside','a/../outside','/absolute','a//b','a/./b','C:/file','a\\b','file.txt:secret','trailing.','trailing ','CON','com1.txt','LPT²','name?','a\x00b',False,None]:
            with self.subTest(value=value),self.assertRaises(p.WorkspaceError):p.relative(value)
        self.assertEqual(('source','源 文件.md'),p.relative('source/源 文件.md'))
        self.assertEqual(('%2e%2e','file.txt'),p.relative('%2e%2e/file.txt')) # no percent-decoding or path expansion
    def test_secret_names_and_directories_cannot_be_selected(self):
        for value in ['.env','.env.production','folder/.SSH/key','mcp-tokens/token.json','auth.json','pairing/code','.git/config']:
            with self.subTest(value=value),self.assertRaises(p.WorkspaceError) as error:p.relative(value)
            self.assertEqual('DENIED',error.exception.code)
    def test_fixed_rpc_rejects_commands_hosts_absolute_roots_and_wrong_schema_before_lookup(self):
        lookup=Mock();provider=p.WorkspaceProvider(lookup,scope_id='1'*64)
        for request in [None,[],{},dict(schema=True,scope_id='1'*64,operation='prepare',project_id='fixture'),dict(schema=1,scope_id='1'*64,operation='start',project_id='fixture'),dict(schema=1,scope_id='1'*64,operation='prepare',project_id='fixture',root='other'),dict(schema=1,scope_id='1'*64,operation='prepare',project_id='fixture',command='shell'),dict(schema=1,scope_id='1'*64,operation='list',project_id='fixture',host='other')]:
            with self.subTest(request=request),self.assertRaises(p.WorkspaceError):provider.dispatch(request)
        lookup.assert_not_called()
    def test_invalid_project_ids_and_lookup_errors_are_redacted(self):
        lookup=Mock(side_effect=RuntimeError('private secret path'));provider=p.WorkspaceProvider(lookup,scope_id='1'*64)
        for project in ['../other','',False,'x'*81]:
            with self.subTest(project=project),self.assertRaises(p.WorkspaceError):provider.dispatch(dict(schema=1,scope_id='1'*64,operation='prepare',project_id=project))
        lookup.assert_not_called()
        with self.assertRaises(p.WorkspaceError) as error:provider.dispatch(dict(schema=1,scope_id='1'*64,operation='prepare',project_id='fixture'))
        self.assertEqual('UNAVAILABLE',str(error.exception));self.assertNotIn('private',repr(error.exception))
    def test_unsupported_platform_does_not_fall_back_to_unfenced_file_reads(self):
        project=p.RegisteredProject('fixture',socket.gethostname().upper(),r'E:\Projects\Fixture','v1','1'*64);provider=p.WorkspaceProvider(lambda scope,ident:project,scope_id='1'*64)
        with patch.object(p.sys,'platform','linux'),self.assertRaises(p.WorkspaceError) as error:provider.dispatch(dict(schema=1,scope_id='1'*64,operation='prepare',project_id='fixture'))
        self.assertEqual('UNSUPPORTED',error.exception.code)
    def test_cross_profile_scope_refuses_before_lookup_or_native_file_io(self):
        lookup=Mock();provider=p.WorkspaceProvider(lookup,scope_id='1'*64)
        with self.assertRaises(p.WorkspaceError) as error:provider.dispatch(dict(schema=1,scope_id='2'*64,operation='prepare',project_id='fixture'))
        self.assertEqual('CHANGED',error.exception.code);lookup.assert_not_called()
    def test_secret_bearing_bindings_and_paths_do_not_enter_representations(self):
        binding=p.RegisteredProject('fixture','HOST',r'E:\private-root','private-registry-digest','1'*64);meta=p.Meta('private-path',0,1,2,3,4,1);grant=p.Grant(binding,'private-path',(1,2),3,'private-token')
        for value in [binding,meta,grant]:self.assertNotIn('private',repr(value))
    def test_lexical_windows_roots_require_concrete_non_device_registration(self):
        for root in [r'\rooted',r'E:relative','E:'+'\\',r'\\?\E:\Projects\Fixture',r'\\.\PhysicalDrive0',r'E:\Projects\..\Other',r'E:\Projects\.ssh']:
            with self.subTest(root=root),self.assertRaises(p.WorkspaceError):p._root(root)
        self.assertEqual('E:\\Projects\\Fixture',str(p._root(r'E:\Projects\Fixture')))


if __name__=='__main__':unittest.main()
