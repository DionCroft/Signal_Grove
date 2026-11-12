"""Isolated real-source channel schema/credential-routing compatibility proof.

No HTTP sign-in, platform account, production plugin installation or fleet
acceptance is claimed. Synthetic callbacks cannot install dependencies or send.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys

parser=argparse.ArgumentParser()
parser.add_argument('--source-root',type=Path,required=True)
parser.add_argument('--work-root',type=Path,required=True)
args=parser.parse_args();source=args.source_root.resolve();root=args.work_root.resolve()
assert not root.exists() and not root.is_relative_to(source)
env=os.environ.copy()
for key in ('GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE'):env.pop(key,None)
def git(*args):return subprocess.check_output(['git','--no-optional-locks','-c','core.fsmonitor=false','-C',str(source),*args],env=env).decode('utf8')
commit='3b421578588243f920886e6fe79a6faa87ea026e'
assert git('rev-parse','HEAD').strip()==commit and not git('status','--porcelain=v1','--untracked-files=all')
root.mkdir();home=root/'home';home.mkdir()
os.environ.update(HERMES_HOME=str(home),HERMES_TEST_ISOLATION='1',TEMP=str(root),TMP=str(root),PYTHONDONTWRITEBYTECODE='1')
sys.path.insert(0,str(source));violations=[]
def audit(event,args):
    if event in {'socket.connect','socket.bind','socket.sendto','socket.sendmsg','subprocess.Popen','os.system','os.exec','os.posix_spawn','os.kill','os.killpg'}:
        violations.append(event);raise PermissionError('Channel fixture denies network/process execution')
    if event=='open' and not isinstance(args[0],int):
        mode=args[1];flags=args[2];writing=(isinstance(mode,str) and any(c in mode for c in 'wax+')) or (isinstance(flags,int) and flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if writing and not Path(args[0]).resolve().is_relative_to(root):
            violations.append('outside-fixture-write');raise PermissionError('Outside fixture write')
    if event in {'os.mkdir','os.remove','os.rmdir','os.chmod','os.rename','os.link','os.symlink'}:
        paths=args[:2] if event in {'os.rename','os.link','os.symlink'} else args[:1]
        if any(not isinstance(p,(str,bytes,os.PathLike)) or not Path(os.fsdecode(p)).resolve().is_relative_to(root) for p in paths):
            violations.append('outside-fixture-mutation');raise PermissionError('Outside fixture mutation')
sys.addaudithook(audit)
from gateway.platform_registry import PlatformEntry,PlatformRegistry
from hermes_cli.config import _is_env_config_key
from hermes_cli.left_core_migration import legacy_plugin_alias,legacy_plugin_keys
from hermes_cli.web_server_messaging import _build_catalog_entry

outcomes=[];calls=dict(check=0,install=0,factory=0)
def check():calls['check']+=1;return False
def install():calls['install']+=1;raise AssertionError('Must not install')
def factory(config):calls['factory']+=1;return {'synthetic-fixture-adapter':True}
entry=PlatformEntry(name='fixture_platform_unknown',label='Fixture platform',adapter_factory=factory,check_fn=check,
    ensure_deps_fn=install,required_env=['FIXTURE_CHANNEL_TOKEN'],source='builtin',
    dashboard_label='Fixture discovered channel',dashboard_description='Fixture only',docs_url='https://docs.example.test/channel',
    dashboard_env_vars=('FIXTURE_CHANNEL_TOKEN','FIXTURE_CHANNEL_USERS'),
    platform_hint_tool_notes={'fixture_tool':'Synthetic note must not become a channel form field.'})
card=_build_catalog_entry(entry.name,entry)
assert card==dict(id=entry.name,name='Fixture discovered channel',description='Fixture only',docs_url='https://docs.example.test/channel',env_vars=('FIXTURE_CHANNEL_TOKEN','FIXTURE_CHANNEL_USERS'),required_env=('FIXTURE_CHANNEL_TOKEN',)),card
assert calls==dict(check=0,install=0,factory=0);outcomes.append('unknown-plugin-catalog-shape-and-order')
assert 'platform_hint_tool_notes' not in card and 'ensure_deps_fn' not in card;outcomes.append('new-tool-notes-and-install-callback-stay-out-of-card')
other=PlatformEntry(name='fixture_other',label='Other',adapter_factory=factory,check_fn=check)
assert other.platform_hint_tool_notes=={} and entry.platform_hint_tool_notes is not other.platform_hint_tool_notes;outcomes.append('tool-notes-have-independent-default-storage')
registry=PlatformRegistry();registry.register(entry)
with registry.deps_resolved_off_loop(False):assert registry.create_adapter(entry.name,object()) is None
assert calls==dict(check=0,install=0,factory=0);outcomes.append('failed-off-loop-dependency-verdict-never-reprobes-or-installs')
with registry.deps_resolved_off_loop(True):assert registry.create_adapter(entry.name,object())=={'synthetic-fixture-adapter':True}
assert calls==dict(check=0,install=0,factory=1);outcomes.append('successful-off-loop-verdict-reuses-only-synthetic-factory')
for key,expected in [('HONCHO_API_KEY',True),('honcho_api_key',True),('honcho.api_key',False),('HONCHO_HOME_CHANNEL',False)]:
    assert _is_env_config_key(key) is expected;outcomes.append('credential-routing-'+key)
assert legacy_plugin_alias('platforms/yuanbao')=='yuanbao';outcomes.append('legacy-bundle-alias-unchanged')
assert legacy_plugin_alias('yuanbao-platform')=='yuanbao';outcomes.append('legacy-manifest-alias-unchanged')
assert {'platforms/yuanbao','yuanbao-platform'}<=legacy_plugin_keys('yuanbao');outcomes.append('legacy-inverse-key-set-unchanged')
assert not violations,violations
paths=('hermes_cli/web_server_messaging.py','gateway/config.py','gateway/platform_registry.py','hermes_cli/config.py','hermes_cli/left_core_migration.py')
result=dict(state='REAL_CHANNEL_CATALOG_REGISTRY_AND_ENV_ROUTING_PASS',commit=commit,cases=len(outcomes),outcomes=outcomes,
    sourceHashes={name:hashlib.sha256((source/name).read_bytes()).hexdigest() for name in paths},syntheticCallbackCounts=calls,
    noNetworkOrDependencyInstall=True,productionChanged=False,
    scope='Actual pinned clean Hermes modules; explicit fixture platform entry, passive catalog and synthetic dependency callbacks. Not HTTP, real account, plugin lifecycle, Android/phone or full release acceptance.')
(root/'report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({k:v for k,v in result.items() if k not in {'outcomes','sourceHashes','scope'}}),flush=True)
