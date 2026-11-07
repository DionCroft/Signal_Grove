"""Sidecar responses need complete pinned source identities and bounded probe execution."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from test_hermes_contract import baseline, checker, sources

class SidecarResponseReviewTest(unittest.TestCase):
    def test_real_policy_keeps_f0_and_requires_both_dispatch_and_registry_sources(self):
        value=json.loads((Path(__file__).resolve().parents[2]/'docs/contracts/hermes-baseline.json').read_text(encoding='utf-8'))
        self.assertEqual('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',value['upstream']['commit'])
        self.assertEqual('tui_gateway/server_requests.py',checker.RUNTIME_GUARD_CONTRACT_SOURCES['gateway_server_requests'])
        self.assertEqual('f8121b2a95f9df11325f981d73f4c95fc062d2d311a29149bb46b39c13926cb2',value['upstream']['contract']['handlers']['gateway_ws:_dispatch_request'])
        entries=value['reviewed_handler_variants']['gateway_ws:_dispatch_request']
        reviewed=[e for e in entries if e['commit']=='aa389eba3df3c5839cf00a8d5941a83bbe3435cb']
        self.assertEqual(1,len(reviewed))
        self.assertEqual('aa59f2ac28285aebb7b01d7616aa0e9ab40539831e656aaef1567df9652ec8ec',reviewed[0]['sha256'])
        self.assertEqual({'gateway_ws': 'f1f3750accad549937a0d34d3c4b0eafdffbb04fc3ea46022415bfbe5f361e2a', 'gateway_server_requests': '47f1bac23ec50e36ebb09f9e7d9365fe703e11aca88f658c37d7a29413df6473'},reviewed[0]['required_sources'])
        self.assertEqual('10e64a801daf59efd1e60d085c548649ca998901ca1eb7c3a49cfd61f7e03f20',value['upstream']['contract']['source_hashes']['gateway_server_requests'])
        same_ast=[e for e in entries if e['sha256']==reviewed[0]['sha256']]
        self.assertEqual(2,len(same_ast))
        modes={e['commit']:e for e in same_ast}
        self.assertEqual({'aa389eba3df3c5839cf00a8d5941a83bbe3435cb','e826a14a467fa74a718b0010487a8eb12421f879'},set(modes))
        next_sources=dict(reviewed[0]['required_sources'])
        next_sources['gateway_server_requests']='04b0fc8f1302ec1efcd36027f155f64dd8654bda5576ce4c37667a7e47eccadd'
        self.assertEqual(next_sources,modes['e826a14a467fa74a718b0010487a8eb12421f879']['required_sources'])


    def test_unchanged_dispatch_ast_cannot_hide_changed_registry_code(self):
        original=sources()
        original['gateway_ws']='async def _dispatch_request(req):\n    return await authority(req)\n'
        original['gateway_server_requests']="raise RuntimeError('Never execute upstream')\ndef resolve_pinned_response(frame, transport):\n    return False\n"
        changed=dict(original)
        changed['gateway_ws']='async def _dispatch_request(req):\n    return None\n'
        changed['gateway_server_requests']=changed['gateway_server_requests'].replace('return False','return True')
        old,new=checker.extract(original),checker.extract(changed)
        value=baseline(old);key='gateway_ws:_dispatch_request';value['mobile_used_handlers'].append(key)
        value['reviewed_handler_variants']={key:[{'sha256':new['handlers'][key],'commit':'b'*40,'reason':'Synthetic exact source vector','required_sources':{s:new['source_hashes'][s] for s in ('gateway_ws','gateway_server_requests')}}]}
        self.assertFalse(checker.compare(value,{'commit':'a'*40,'contract':old})['mobile_review_required'])
        self.assertFalse(checker.compare(value,{'commit':'b'*40,'contract':new})['mobile_review_required'])
        for suffix in ('\n# Same AST, different helper bytes.\n',"\ndef changed_ownership():\n    return 'another socket'\n"):
            mutated=dict(changed);mutated['gateway_server_requests']+=suffix
            current=checker.extract(mutated)
            self.assertEqual(new['handlers'][key],current['handlers'][key])
            result=checker.compare(value,{'commit':'b'*40,'contract':current})
            self.assertTrue(result['mobile_review_required']);self.assertIn(key,result['mobile_relevant_changes']['changed_used_handlers'])

    def test_probe_refuses_optimized_or_wrong_source_before_creating_output(self):
        script=Path(__file__).resolve().parents[1]/'compatibility_probe_sidecar_responses.py'
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/'source';(source/'tui_gateway').mkdir(parents=True)
            for name in ('ws.py','server_requests.py'):(source/'tui_gateway'/name).write_text('invalid source')
            for revision,optimized in ((r,o) for r in ('aa','e826') for o in (True,False)):
                work=root/(revision+('-optimized' if optimized else '-wrong-source'))
                command=[sys.executable,'-I','-B']+(['-O'] if optimized else [])+[str(script),'--revision',revision,'--source-root',str(source),'--work-root',str(work)]
                result=subprocess.run(command,capture_output=True,text=True,timeout=20)
                self.assertNotEqual(0,result.returncode);self.assertFalse(work.exists())
                self.assertIn('without -O' if optimized else 'Wrong pinned source bytes',result.stderr)
