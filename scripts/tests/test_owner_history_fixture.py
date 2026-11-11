"""Finite read-only boundary for the actual-owner closed-history fixture."""
import ast
import hashlib
import json
from pathlib import Path
import re
import unittest
import urllib.parse

source=Path(__file__).resolve().parents[1]/'compatibility_fixture_owner_workspace_tcp.py'
nodes=[n for n in ast.parse(source.read_text(encoding='utf-8')).body if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef)) and n.name in {'history_fixture_http_allowed','history_snapshot_digest','history_fixture_body'}]
assert len(nodes)==3
space={'re':re,'urllib':urllib,'hashlib':hashlib,'json':json};exec(compile(ast.Module(body=nodes,type_ignores=[]),str(source),'exec'),space)
class OwnerHistoryFixturePolicyTest(unittest.TestCase):
    def allowed(self,method='GET',path='/history/p/default/api/sessions',query=b'limit=1&offset=0'):
        return space['history_fixture_http_allowed'](method,path,query)
    def test_snapshot_helper_is_defined_before_parent_supervision_starts(self):
        tree=ast.parse(source.read_text(encoding='utf-8'))
        helper=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='history_snapshot_digest')
        parent=next(n for n in tree.body if isinstance(n,ast.If) and ast.unparse(n.test)=='not a.owned_child')
        self.assertLess(helper.lineno,parent.lineno)
    def test_snapshot_blob_bytes_are_kept_distinct_from_hex_text(self):
        digest=space['history_snapshot_digest']
        self.assertNotEqual(digest({'x':b'\x00\xff'}),digest({'x':'00ff'}))
        self.assertNotEqual(digest({'x':b'\x00\xff'}),digest({'x':b'\x00\xfe'}))
        self.assertEqual(digest({'x':b'\x00\xff','y':1}),digest({'y':1,'x':b'\x00\xff'}))
    def test_unknown_snapshot_objects_are_not_lossily_stringified(self):
        with self.assertRaises(TypeError):space['history_snapshot_digest']({'x':object()})
    def test_only_get_is_forwarded(self):
        for method in ('POST','PATCH','DELETE','PUT','HEAD','OPTIONS','get'):self.assertFalse(self.allowed(method=method))
        self.assertTrue(self.allowed())
    def test_only_exact_prefixes_and_profiles_are_allowed(self):
        for profile in ('default','field','not-served-fixture'):self.assertTrue(self.allowed(path='/history/p/'+profile+'/api/sessions'))
        for path in ('/api/sessions','/history/api/sessions','/history/p/other/api/sessions','/history/p/field/api/sessions/','/history/p/default/../field/api/sessions'):self.assertFalse(self.allowed(path=path))
    def test_only_bounded_inventory_pagination_is_allowed(self):
        for query in (b'limit=1&offset=1',b'limit=1&offset=2',b'limit=50&offset=0',b'offset=50&limit=50'):self.assertTrue(self.allowed(query=query))
        for query in (b'limit=0&offset=0',b'limit=500&offset=0',b'limit=1&offset=-1',b'limit=1&offset=999',b'limit=true&offset=0'):self.assertFalse(self.allowed(query=query))
    def test_exact_messages_are_read_only_and_bounded(self):
        query=b'inline_images=false&limit=500&offset=0&order=oldest'
        for sid in ('shared-closed-fixture','default-only-fixture','field-only-fixture'):
            self.assertTrue(self.allowed(path='/history/p/field/api/sessions/'+sid+'/messages',query=query))
        self.assertTrue(self.allowed(path='/history/p/field/api/sessions/shared-closed-fixture/messages',query=query.replace(b'offset=0',b'offset=500')))
        self.assertFalse(self.allowed(path='/history/p/field/api/sessions/foreign/messages',query=query))
    def test_no_work_endpoint_or_extra_profile_selector_can_be_forwarded(self):
        for suffix in ('chat','chat/stream','model','fork','export','messages/around'):
            self.assertFalse(self.allowed(path='/history/p/default/api/sessions/shared-closed-fixture/'+suffix))
        for query in (b'limit=1&offset=0&profile=field',b'limit=1&offset=0&full=1',b'limit=1&offset=0&source=api_server'):self.assertFalse(self.allowed(query=query))
    def test_duplicate_and_malformed_query_values_are_refused(self):
        for query in (b'limit=1&offset=0&offset=1',b'limit=1&offset',b'limit=1&offset=0&',b'limit=1&offset=0\xff'):
            self.assertFalse(self.allowed(query=query))
    def test_images_latest_order_and_arbitrary_offsets_are_refused(self):
        path='/history/p/default/api/sessions/shared-closed-fixture/messages';query=b'inline_images=false&limit=500&offset=0&order=oldest'
        for old,new in ((b'false',b'true'),(b'500',b'501'),(b'offset=0',b'offset=1'),(b'oldest',b'latest')):self.assertFalse(self.allowed(path=path,query=query.replace(old,new)))
    def test_non_bytes_query_cannot_reach_the_adapter(self):
        for value in (None,{},'limit=1&offset=0',True):self.assertFalse(self.allowed(query=value))

class OwnerHistoryBodyTest(unittest.IsolatedAsyncioTestCase):
    class Stream:
        def __init__(self,chunks):self.chunks=chunks;self.seen=0
        async def iter_chunked(self,size):
            assert size==65536
            for chunk in self.chunks:
                self.seen+=1
                yield chunk
    async def test_all_chunks_are_forwarded_without_partial_json(self):
        stream=self.Stream([b'{"data":',b'[1,2]',b'}'])
        self.assertEqual(await space['history_fixture_body'](stream),b'{"data":[1,2]}')
        self.assertEqual(stream.seen,3)
    async def test_oversize_stops_before_consuming_following_chunks(self):
        stream=self.Stream([b'x'*65536]*31+[b'not-read'])
        with self.assertRaisesRegex(RuntimeError,'Oversized'):await space['history_fixture_body'](stream)
        self.assertEqual(stream.seen,31)
    async def test_invalid_chunk_types_and_sizes_are_refused(self):
        for chunk in ('text',b'x'*65537):
            with self.assertRaisesRegex(RuntimeError,'Malformed'):await space['history_fixture_body'](self.Stream([chunk]))

if __name__=='__main__':unittest.main()
