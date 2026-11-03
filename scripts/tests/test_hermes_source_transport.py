from pathlib import Path
import base64,hashlib,importlib.util,json,subprocess,unittest,io,sys
from unittest.mock import patch

root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('source_transport',root/'scripts/check_hermes_contract.py');checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class SourceTransportTest(unittest.TestCase):
    path='cron/fixture.py';pin='a'*40
    def response(self,data=b'',status=None):
        return subprocess.CompletedProcess([],1 if status else 0,data,(f'gh: Error (HTTP {status})'.encode() if status else b''))
    def listing(self,data):
        sha=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
        return self.response(json.dumps([{'path':self.path,'type':'file','sha':sha}]).encode())
    def test_failed_parallel_module_keeps_safe_source_context_and_exit_three(self):
        with patch.object(checker,'resolve',return_value=('main',self.pin)),patch.object(checker,'SOURCES',{'api':self.path}),patch.object(checker,'EXTRA_SOURCES',{}),patch.object(checker,'github_source',side_effect=checker.GitHubReadError(403)):
            with self.assertRaises(checker.SourceReadError) as caught:checker.snapshot('main')
        self.assertEqual('api',caught.exception.module);self.assertEqual(403,caught.exception.status)
        error=io.StringIO()
        with patch.object(checker,'snapshot',side_effect=caught.exception),patch.object(sys,'argv',['checker','--ref','main']),patch.object(sys,'stderr',error):
            self.assertEqual(3,checker.main())
        self.assertIn('Source module api, HTTP 403',error.getvalue())
    def test_contents_5xx_raw_fallback_is_bound_to_the_exact_directory_blob(self):
        data="def fixture():\n    return '你好'\n".encode()
        with patch.object(checker.subprocess,'run',side_effect=[self.response(status=500),self.listing(data),self.response(data)]) as run:
            self.assertEqual(data.decode(),checker.github_source(self.path,self.pin));self.assertEqual(3,run.call_count)
            self.assertEqual(['gh','api',f'repos/{checker.REPO}/contents/{self.path}?ref={self.pin}','-H','Accept: application/vnd.github.raw+json'],run.call_args_list[-1].args[0])
    def test_permissions_missing_paths_and_rate_limits_never_fall_back(self):
        for status in [401,403,404,429]:
            with self.subTest(status=status),patch.object(checker.subprocess,'run',return_value=self.response(status=status)) as run:
                with self.assertRaises(checker.GitHubReadError):checker.github_source(self.path,self.pin)
                self.assertEqual(1,run.call_count)
    def test_raw_content_must_match_the_pinned_directory_blob(self):
        with patch.object(checker.subprocess,'run',side_effect=[self.response(status=503),self.listing(b'expected'),self.response(b'other')]):
            with self.assertRaises(ValueError):checker.github_source(self.path,self.pin)
    def test_ambiguous_missing_or_failed_directory_identity_stops_before_raw_read(self):
        for rows in [[],[{'path':self.path,'type':'symlink','sha':'b'*40}],[{'path':self.path,'type':'file','sha':'b'*40}]*2]:
            with self.subTest(rows=rows),patch.object(checker.subprocess,'run',side_effect=[self.response(status=502),self.response(json.dumps(rows).encode())]) as run:
                with self.assertRaises(ValueError):checker.github_source(self.path,self.pin)
                self.assertEqual(2,run.call_count)
    def test_raw_source_size_and_utf8_bounds_are_not_bypassed(self):
        for data in [b'x'*2_000_001,b'\xff']:
            with self.subTest(size=len(data)),patch.object(checker.subprocess,'run',side_effect=[self.response(status=504),self.listing(data),self.response(data)]):
                with self.assertRaises(ValueError):checker.github_source(self.path,self.pin)
    def test_regular_json_representation_preserves_bytes_and_checks_actual_size(self):
        for data in [b'def fixture():\n    pass\n',b'x'*2_000_001]:
            payload={'encoding':'base64','size':1,'content':base64.b64encode(data).decode()}
            with self.subTest(size=len(data)),patch.object(checker.subprocess,'run',return_value=self.response(json.dumps(payload).encode())) as run:
                if len(data)>2_000_000:
                    with self.assertRaises(ValueError):checker.github_source(self.path,self.pin)
                else:self.assertEqual(data.decode(),checker.github_source(self.path,self.pin))
                self.assertEqual(1,run.call_count)

if __name__=='__main__':unittest.main()
