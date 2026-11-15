from dataclasses import replace
import hashlib
import http.client
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import threading
import time
import unittest
import uuid
from unittest.mock import patch

scripts=Path(__file__).resolve().parents[1];sys.path.insert(0,str(scripts))
spec=importlib.util.spec_from_file_location('private_backup_release',scripts/'private_backup_release.py');r=importlib.util.module_from_spec(spec);sys.modules[spec.name]=r;spec.loader.exec_module(r)


class FixtureServer:
    def __init__(self):
        self.private=True;self.repo_id=101;self.release_id=202;self.tag='fixture-backups';self.assets={};self.posts=[];self.requests=[];self.next_id=1
        self.fail_post=0;self.corrupt=False;self.redirect=None;self.starter=False
        state=self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*_):pass
            def reply(self,status,data,headers=None):
                body=data if isinstance(data,bytes) else json.dumps(data).encode();self.send_response(status)
                for k,v in (headers or {}).items():self.send_header(k,v)
                self.send_header('Content-Length',str(len(body)))
                try:self.end_headers();self.wfile.write(body)
                except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError):pass # Deliberate client aborts remain unknown at the caller.
            def row(self,item):return dict(id=item['id'],name=item['name'],size=len(item['body']),state='starter' if state.starter else 'uploaded',digest='sha256:'+hashlib.sha256(item['body']).hexdigest())
            def do_GET(self):
                state.requests.append(('GET',self.path,dict(self.headers)));base='/repos/fixture/backups'
                if self.path==base:return self.reply(200,dict(id=state.repo_id,full_name='fixture/backups',private=state.private))
                if self.path==base+'/releases/202':return self.reply(200,dict(id=state.release_id,tag_name=state.tag,upload_url='https://uploads.github.com'+base+'/releases/202/assets{?name,label}'))
                if self.path.startswith(base+'/releases/202/assets?'):return self.reply(200,[self.row(a) for a in state.assets.values()])
                match=re.fullmatch(re.escape(base)+'/releases/assets/([0-9]+)',self.path)
                if match:
                    item=next((a for a in state.assets.values() if a['id']==int(match[1])),None)
                    if item is None:return self.reply(404,{})
                    if self.headers.get('Accept')=='application/octet-stream':
                        if state.redirect:return self.reply(302,b'',{'Location':state.redirect})
                        body=item['body'];return self.reply(200,(b'X'+body[1:]) if state.corrupt else body)
                    return self.reply(200,self.row(item))
                if self.path.startswith('/fixture-download/'):
                    item=next(iter(state.assets.values()));return self.reply(200,item['body'])
                return self.reply(404,{})
            def do_POST(self):
                state.requests.append(('POST',self.path,dict(self.headers)));name=self.path.split('?name=',1)[-1]
                try:body=self.rfile.read(int(self.headers['Content-Length']))
                except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError):return # Explicit client-abort fixture.
                state.posts.append(name);item=dict(id=state.next_id,name=name,body=body);state.next_id+=1;state.assets[name]=item
                self.reply(502 if state.fail_post==len(state.posts) else 201,self.row(item))
        self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler);self.server.daemon_threads=True
        self.thread=threading.Thread(target=self.server.serve_forever,kwargs={'poll_interval':0.01},daemon=True);self.thread.start()
    def connect(self,host,timeout):
        if host not in {'api.github.com','uploads.github.com'}|r.ASSET_HOSTS:raise AssertionError('Fixture route escaped')
        return http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=timeout)
    def close(self):self.server.shutdown();self.server.server_close();self.thread.join(2)


class ReleaseSchemaTests(unittest.TestCase):
    def test_exact_repository_and_numeric_identity_are_required(self):
        target=r.ReleaseTarget('fixture/backups',101,202,'fixture-backups','a'*64)
        for change in (dict(repository='../other'),dict(repository='fixture/..'),dict(repository='fixture/backups\nother'),dict(repository_id=True),dict(release_id=0),dict(tag='x/y')):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(target,**change)
    def test_secret_repr_and_invalid_credentials_never_echo_values(self):
        value='fixture_secret_'+uuid.uuid4().hex;secret=r.GitHubCredential(value);self.assertNotIn(value,repr(secret));self.assertEqual(secret.binding_sha256,hashlib.sha256(value.encode()).hexdigest())
        with self.assertRaises(ValueError):r.GitHubCredential('bad\nheader')
    def test_unsafe_artifact_name_is_refused_before_open(self):
        with self.assertRaises(r.Refused):r.observe_artifact('/does-not-exist','../../secret')
    def test_duplicate_json_keys_are_not_accepted_as_repository_identity(self):
        with self.assertRaises(r.Unknown):r._read_json(b'{"private":false,"private":true}')
    def test_asset_ack_without_exact_uploaded_digest_is_not_completion(self):
        for row in ({'id':1,'name':'a','size':1,'state':'starter'},{'id':True}):
            with self.assertRaises(r.Unknown):r._asset(row,type('Fixture',(),dict(name='a',bytes=1,sha256='a'*64))())
    def test_github_asset_limit_refuses_oversize_before_file_or_network_io(self):
        name='hermes-backup-'+'a'*32+'-backup_pre.zip.age'
        def artifact(size):return r.EncryptedArtifact(name,size,'a'*64,(1,2,1000,1000,r.stat.S_IFREG|0o600,1,size,1,1))
        self.assertEqual(artifact(2*1024**3-1).bytes,r.MAX_ASSET_BYTES)
        with self.assertRaises(ValueError):artifact(2*1024**3)
        transport=r.GitHubHttpsTransport(r.GitHubCredential('fixture_secret_'+uuid.uuid4().hex))
        with patch.object(transport,'_connect') as connection,self.assertRaises(r.Refused):transport.json('uploads.github.com','POST','/x',time.monotonic()+5,body=io.BytesIO(),length=2*1024**3)
        connection.assert_not_called()


class GitHubTransportTests(unittest.TestCase):
    def setUp(self):
        self.server=FixtureServer();self.addCleanup(self.server.close);self.secret=r.GitHubCredential('fixture_secret_'+uuid.uuid4().hex);self.transport=r.GitHubHttpsTransport(self.secret);self.transport._connect=self.server.connect
    def test_production_connection_uses_normal_tls_validation(self):
        transport=r.GitHubHttpsTransport(self.secret)
        with patch.object(r.http.client,'HTTPSConnection') as factory:
            transport._connect('api.github.com',5);context=factory.call_args.kwargs['context'];self.assertTrue(context.check_hostname);self.assertEqual(context.verify_mode,r.ssl.CERT_REQUIRED)
    def test_fixed_request_headers_and_current_version_are_used(self):
        self.transport.json('api.github.com','GET','/repos/fixture/backups',time.monotonic()+5)
        headers=self.server.requests[0][2];self.assertEqual(headers['Host'],'api.github.com');self.assertEqual(headers['X-GitHub-Api-Version'],r.API_VERSION)
        self.assertEqual(headers['Authorization'],'Bearer '+self.secret._value)
    def test_failed_post_is_sent_once_even_if_server_keeps_asset(self):
        self.server.fail_post=1
        with self.assertRaises(r.Unknown):self.transport.json('uploads.github.com','POST','/repos/fixture/backups/releases/202/assets?name=x',time.monotonic()+5,body=io.BytesIO(b'abc'),length=3)
        self.assertEqual(self.server.posts,['x']);self.assertEqual(self.server.assets['x']['body'],b'abc')
    def test_http_route_and_header_injection_are_refused(self):
        for host,path in (('evil.example','/x'),('api.github.com','//evil'),('api.github.com','/x\r\nAuthorization: other')):
            with self.subTest(host=host),self.assertRaises(r.Refused):self.transport.json(host,'GET',path,time.monotonic()+5)
        self.assertEqual(self.server.requests,[])
    def test_deadline_refuses_before_a_request(self):
        with self.assertRaises(r.Unknown):self.transport.json('api.github.com','GET','/repos/fixture/backups',time.monotonic()-1)
        self.assertEqual(self.server.requests,[])
    def test_direct_binary_download_checks_exact_bytes_and_digest(self):
        self.server.assets['fixture']=dict(id=1,name='fixture',body=b'fixture encrypted bytes')
        artifact=type('Fixture',(),dict(bytes=23,sha256=hashlib.sha256(b'fixture encrypted bytes').hexdigest()))()
        with tempfile.TemporaryFile() as sink:
            result=self.transport.download('/repos/fixture/backups/releases/assets/1',artifact,sink,time.monotonic()+5)
            sink.seek(0);self.assertEqual(sink.read(),b'fixture encrypted bytes');self.assertEqual(result['bytes'],23)
    def test_upload_size_mismatch_is_unknown_without_retry(self):
        # A deterministic byte-sink verifies local size refusal without leaving
        # a deliberately incomplete request in a Windows server thread. The
        # ambiguous-upload tests separately exercise actual loopback TCP.
        with patch.object(self.transport,'_connect') as connection,self.assertRaises(r.Unknown):self.transport.json('uploads.github.com','POST','/x',time.monotonic()+5,body=io.BytesIO(b'four'),length=3)
        connection.assert_called_once();connection.return_value.send.assert_not_called();connection.return_value.getresponse.assert_not_called();connection.return_value.close.assert_called_once()


@unittest.skipUnless(sys.platform=='linux','Native claimed journal, manager scope and file effects run on the Pi')
class NativeBackupPublicationTests(unittest.TestCase):
    def setUp(self):
        self.server=FixtureServer();self.addCleanup(self.server.close)
        for name in ('retirement_guard','native_retirement_owner'):
            spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m)
        spec=importlib.util.spec_from_file_location('operational_paths_fixture',scripts/'native/operational_paths_fixture.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
        self.fixture=helper.build(Path(os.environ['SIGNAL_GROVE_OPERATIONAL_RETAIN_ROOT']));self.core=self.fixture['modules'];self.guard=self.fixture['guard'];self.root=self.fixture['root']
        self.m={}
        for name in ('native_service_manager','private_backup_release'):
            spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);self.m[name]=m
        self.r=self.m['private_backup_release'];self.service=self.m['native_service_manager'];self.c=self.core['cutover_coordinator'];self.jmodule=self.core['cutover_journal']
        group=Path('/proc/self/cgroup').read_text().strip().split('::',1)[-1];self.op=re.search('signal-grove-acceptance-([a-f0-9]{32})-pm.service$',group)[1]
        self.plan=self.c.Plan('a'*64,'b'*64,'c'*64,'d'*64);ledger=self.fixture['paths']['journal'];self.ledger=ledger
        self.j=self.jmodule.CutoverJournal(ledger,expected_identity=(ledger.stat().st_dev,ledger.stat().st_ino),operational_paths=self.guard,expected_operational_sha256=self.guard.sha256);self.addCleanup(self.j.close)
        a=json.loads(os.environ['SIGNAL_GROVE_PM_BUS_AUTHORITY']);Owner=sys.modules['retirement_guard'].Owner
        authority=self.service.BusAuthority(a['uid'],a['busIdentity'][0],a['busIdentity'][1],Owner(**a['peerOwner']),a['managerUnique'],Owner(**a['managerOwner']))
        bindings=tuple(self.service.UnitBinding(role,'hermes-'+role+'.service','e'*64,'e'*64,'/fixture/not-used','/fixture/not-used') for role in ('gateway','serve'))
        self.manager=self.service.NativeServiceManager(authority,self.plan,self.j,bindings);self.addCleanup(self.manager.close)
        self.scope=self.manager.execution_scope(self.op,owned_fixture=True);self.deadline=(self.scope.active_enter_usec+self.scope.runtime_max_usec+self.scope.stop_timeout_usec)/1e6+5
        self.secret=self.r.GitHubCredential('fixture_secret_'+uuid.uuid4().hex);self.transport=self.r.GitHubHttpsTransport(self.secret);self.transport._connect=self.server.connect
    def prepare(self,stage='backup_pre',claim=True):
        recovery=stage.startswith('recovery_');packet='8'*64 if recovery else self.plan.packet_sha256;e=self.c.ApprovedExecution(self.op,packet,self.plan.authority_sha256,'recovery' if recovery else 'forward','f'*64,self.deadline)
        if recovery:
            original=uuid.uuid4().hex;self.j.begin(original,self.plan.packet_sha256,authorization_sha256='9'*64);self.j.intent('preflight');self.j.unknown()
            self.j.begin_recovery(self.op,packet,original_operation=original,original_plan_sha256=self.plan.packet_sha256,history_sha256=self.j.history_sha256,evidence_sha256='7'*64,authorization_sha256=e.binding_sha256)
        else:self.j.begin(self.op,packet,authorization_sha256=e.binding_sha256)
        order=self.c.RECOVERY if recovery else self.c.FORWARD
        for step in order[:order.index(stage)]:self.j.intent(step);self.j.verified(step,'6'*64) # Explicit synthetic non-publication prerequisites.
        self.j.intent(stage)
        if claim:self.j.claim_dispatch(self.op,stage,e.binding_sha256)
        directory=self.fixture['paths']['backup_output']/('publication-'+uuid.uuid4().hex);directory.mkdir(mode=0o700);name='hermes-backup-'+self.op+'-'+stage+'.zip.age'
        cipher=b'age-encryption.org/v1\nfixture encrypted body';(directory/name).write_bytes(cipher);(directory/(name+'.sha256')).write_text(hashlib.sha256(cipher).hexdigest()+'  '+name+'\n')
        spec=self.r.BackupPublicationSpec(self.r.ReleaseTarget('fixture/backups',101,202,'fixture-backups',self.secret.binding_sha256),self.core['native_file_publication'].anchor(directory),tuple(self.r.observe_artifact(directory,n) for n in (name,name+'.sha256')),self.scope.configuration_sha256,str(self.root))
        actor=self.r.NativeBackupPublication(self.plan,self.j,self.manager,self.guard,stage,spec,spec.sha256,self.transport);self.spec,self.execution,self.actor=spec,e,actor;return actor,e
    def test_pair_upload_and_download_are_verified_but_journal_step_is_not(self):
        actor,e=self.prepare();result=actor.publish(e);self.assertEqual(result['state'],'effects_observed');self.assertEqual(len(self.server.posts),2);self.assertFalse(result['fresh_backup_verified']);self.assertEqual(self.j.view['pending'],'backup_pre')
        self.assertTrue(all((Path(self.spec.directory.path)/row['downloaded_name']).is_file() for row in result['assets']))
    def test_public_repo_or_changed_release_identity_has_no_upload(self):
        actor,e=self.prepare();self.server.private=False
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(self.server.posts,[]);self.assertTrue((self.ledger/actor.last_receipt).is_file())
    def test_changed_repository_id_is_not_the_reviewed_private_target(self):
        actor,e=self.prepare();self.server.repo_id=999
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(self.server.posts,[])
    def test_existing_asset_is_preserved_without_clobber_or_delete(self):
        actor,e=self.prepare();a=self.spec.artifacts[0];self.server.assets[a.name]=dict(id=3,name=a.name,body=b'keep existing')
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(self.server.posts,[]);self.assertEqual(self.server.assets[a.name]['body'],b'keep existing')
    def test_ambiguous_upload_retains_remote_and_native_effects_without_replay(self):
        actor,e=self.prepare();self.server.fail_post=1
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(len(self.server.posts),1);self.assertEqual(len(self.server.assets),1);self.assertEqual(actor.state,'unknown')
        with self.assertRaises(self.jmodule.Refused):actor.publish(e)
        other=self.r.NativeBackupPublication(self.plan,self.j,self.manager,self.guard,'backup_pre',self.spec,self.spec.sha256,self.transport)
        with self.assertRaises(self.jmodule.Unknown):other.publish(e)
        self.assertEqual(len(self.server.posts),1);self.assertTrue((Path(self.spec.directory.path)/self.spec.artifacts[0].name).exists())
    def test_checksum_upload_failure_does_not_remove_the_verified_archive(self):
        actor,e=self.prepare();self.server.fail_post=2
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(len(self.server.posts),2);self.assertEqual(len(self.server.assets),2);self.assertEqual(self.j.view['pending'],'backup_pre')
    def test_download_mismatch_stays_unknown_and_preserves_its_partial_file(self):
        actor,e=self.prepare();self.server.corrupt=True
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(len(self.server.posts),1);self.assertEqual(len(list(Path(self.spec.directory.path).glob('downloaded-*'))),1)
    def test_queued_starter_ack_does_not_prove_an_uploaded_asset(self):
        actor,e=self.prepare();self.server.starter=True
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(len(self.server.posts),1)
    def test_changed_native_artifact_is_refused_before_network(self):
        actor,e=self.prepare();p=Path(self.spec.directory.path)/self.spec.artifacts[0].name;p.rename(p.with_name('retained-encrypted'));p.write_bytes(b'age-encryption.org/v1\nfixture encrypted body')
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(self.server.requests,[])
    def test_unclaimed_pending_stage_cannot_issue_a_publication_request(self):
        actor,e=self.prepare(claim=False)
        with self.assertRaises(self.jmodule.Refused):actor.publish(e)
        self.assertEqual(self.server.requests,[])
    def test_credential_binding_and_controller_mismatch_have_no_request(self):
        actor,e=self.prepare();changed=replace(self.spec,scope_configuration_sha256='1'*64)
        other=self.r.NativeBackupPublication(self.plan,self.j,self.manager,self.guard,'backup_pre',changed,changed.sha256,self.transport)
        with self.assertRaises(self.jmodule.Refused):other.publish(e)
        with self.assertRaises(self.jmodule.Refused):self.r.NativeBackupPublication(self.plan,self.j,self.manager,self.guard,'backup_pre',replace(self.spec,target=replace(self.spec.target,credential_binding_sha256='1'*64)),'1'*64,self.transport)
        self.assertEqual(self.server.requests,[])
    def test_asset_redirect_strips_credentials_and_presigned_url_is_not_evidence(self):
        actor,e=self.prepare();self.server.redirect='https://release-assets.githubusercontent.com/fixture-download/one?signature=fixture-only'
        # This server route returns the first body: use only the archive transfer
        # before the expected checksum mismatch; inspect both credential scopes.
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        cdn=[row for row in self.server.requests if row[2]['Host']=='release-assets.githubusercontent.com'];self.assertTrue(cdn)
        self.assertTrue(all('Authorization' not in row[2] and 'X-GitHub-Api-Version' not in row[2] for row in cdn))
        self.assertNotIn('signature=',(self.ledger/actor.last_receipt).read_text())
    def test_untrusted_redirect_is_not_followed(self):
        actor,e=self.prepare();self.server.redirect='http://127.0.0.1/private'
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertFalse(any(row[1]=='/private' for row in self.server.requests))
    def test_post_backup_uses_its_exact_forward_stage_and_names(self):
        actor,e=self.prepare('backup_post');self.assertEqual(actor.publish(e)['state'],'effects_observed');self.assertEqual(self.j.view['pending'],'backup_post')
    def test_recovery_pre_backup_uses_separate_recovery_authorization(self):
        actor,e=self.prepare('recovery_backup');self.assertEqual(actor.publish(e)['state'],'effects_observed');self.assertEqual(self.j.view['mode'],'recovery')
    def test_recovery_post_backup_uses_its_exact_reviewed_prefix(self):
        actor,e=self.prepare('recovery_backup_post');self.assertEqual(actor.publish(e)['state'],'effects_observed');self.assertEqual(self.j.view['pending'],'recovery_backup_post')
    def test_changed_operational_ancestor_refuses_before_a_receipt_or_http(self):
        actor,e=self.prepare();self.fixture['paths']['backup_output'].chmod(0o755)
        with self.assertRaises(self.jmodule.Unknown):actor.publish(e)
        self.assertEqual(self.server.requests,[]);self.assertIsNone(actor.last_receipt)
    def test_expired_execution_refuses_before_a_receipt_or_http(self):
        actor,e=self.prepare()
        with patch.object(self.r.time,'monotonic',return_value=e.deadline+1),self.assertRaises(self.jmodule.Refused):actor.publish(e)
        self.assertEqual(self.server.requests,[]);self.assertIsNone(actor.last_receipt)
    def test_previously_verified_sdk_age_ciphertext_survives_publication(self):
        # Only the generated fixture ciphertext is supplied, never its private
        # key or the live Hermes home. This proves transport, not a new backup.
        source=Path(os.environ['SIGNAL_GROVE_VERIFIED_FIXTURE_CIPHER']);expected=os.environ['SIGNAL_GROVE_VERIFIED_FIXTURE_CIPHER_SHA256']
        before=source.lstat();self.assertTrue(source.is_file());self.assertFalse(source.is_symlink())
        self.assertEqual(before.st_uid,os.geteuid());self.assertEqual(before.st_nlink,1);self.assertEqual(before.st_mode&0o777,0o600)
        cipher=source.read_bytes();self.assertEqual(hashlib.sha256(cipher).hexdigest(),expected)
        self.assertEqual(self.r._stamp(source.lstat()),self.r._stamp(before))
        actor,e=self.prepare();directory=Path(self.spec.directory.path);name=self.spec.artifacts[0].name
        (directory/name).write_bytes(cipher);(directory/(name+'.sha256')).write_text(expected+'  '+name+'\n')
        updated=replace(self.spec,artifacts=tuple(self.r.observe_artifact(directory,n) for n in (name,name+'.sha256')))
        actor=self.r.NativeBackupPublication(self.plan,self.j,self.manager,self.guard,'backup_pre',updated,updated.sha256,self.transport)
        result=actor.publish(e);self.assertEqual(len(self.server.posts),2);self.assertEqual(self.server.assets[name]['body'],cipher)
        for row in result['assets']:self.assertEqual((directory/row['downloaded_name']).read_bytes(),self.server.assets[row['name']]['body'])
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(),expected);self.assertEqual(self.r._stamp(source.lstat()),self.r._stamp(before))
        report=dict(state='PRIOR_SDK_AGE_CIPHERTEXT_LOOPBACK_PUBLICATION_VERIFIED',directory=str(directory),receipt=str(self.ledger/actor.last_receipt),
            encrypted_bytes=len(cipher),encrypted_sha256=expected,posts=len(self.server.posts),result=result,real_github_used=False,
            new_snapshot_created=False,live_home_read=False,fixture_key_read=False,synthetic_non_publication_prerequisites=True)
        with (self.root/'sdk-cipher-publication-result.json').open('x') as out:json.dump(report,out,indent=2)

if __name__=='__main__':unittest.main()
