"""Exercise pinned real Hermes native auth and profile history through its ASGI app.

Windows probe; use an existing Hermes interpreter with -I -S -B, no installation.
Starts the actual dashboard lifespan with upstream synthetic HMAC auth and two
fresh fixture homes. No Android browser, deployed Pi, real IdP or inference claim.
Exact read-only metadata subprocess attempts remain blocked and are recorded.
Python audit hooks bound this fixture but are not an OS security sandbox.
See docs/contracts/2026-11-07-native-auth-history-matrix.md and
docs/contracts/2026-11-12-current-stable-auth-release-inventory.md.
Loader/start-entry scope: docs/contracts/2026-11-12-native-loader-startup.md.
"""
from pathlib import Path
import argparse
import hashlib
import ipaddress
import json
import os
import subprocess
import sys
import traceback
import urllib.parse
import weakref

REVISIONS = {'stable-2bd378e': {'commit': '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19', 'blobs': {'hermes_cli/web_server.py': '22ac93e69cae5a8c2a0e045cc9944f8416107e18', 'hermes_cli/dashboard_auth/__init__.py': 'f52f683894b4272e85565f8b2778b4b77b170b73', 'hermes_cli/dashboard_auth/base.py': 'b8e71461adf30b4814634cb3fbcfdf9b49488e33', 'hermes_cli/dashboard_auth/routes.py': '97dfc55f91130da8924ff497b4560d9d5305b00e', 'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960', 'hermes_cli/dashboard_auth/middleware.py': '5dc4c382ef9d93557c5eb022a9fa5f0737bdbc6a', 'hermes_cli/dashboard_auth/cookies.py': 'd6e5bb541594277a99d7e8f3d71fd43b68b717bc', 'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a', 'hermes_cli/dashboard_auth/request_utils.py': 'aca6ad79247b6e66fc4dae99de19da1911c96543', 'hermes_cli/dashboard_auth/prefix.py': 'c8b86dc8b8e55415c4c7069d7c5993d025c627bb', 'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324', 'hermes_cli/dashboard_auth/registry.py': 'ae0d377f8a308e4a9550f99817cf59c277e8876a', 'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599', 'hermes_cli/version_info.py': '258d67b4df3bb18bedd7c77e2bd539c73043baa1', 'hermes_cli/source_check.py': '6d2f267d5fa59f982c142f2abd8c0711b5a2e29c', 'hermes_cli/_subprocess_compat.py': 'a2f985af6197eb80ba55b8f45d3c4fea8e3c4eff', 'hermes_cli/web_routers/sessions.py': 'df2fe8084547a3c0cfc87ff8e6cc1dd5c496902e', 'hermes_cli/web_routers/profiles.py': 'dedfdcb871aa06b621310321301a658871ded80c', 'hermes_cli/web_server_sessions.py': '650ba6470402f763314aab7145fcbc8afd8a8a8f', 'hermes_cli/web_server_cron.py': 'b777f511a8786d8c7ea788bc93276d58546f6b48', 'hermes_cli/profiles.py': '622077d545b3dd322ba9e967a3e8620b0b18e278', 'hermes_state.py': '6a7ae615405ee714c0a87f83835408e36bd645ea', 'hermes_state_sessions.py': 'f3fc4c2854367bc1a61786406090b71bdccf28f6'}}, 'prior-bf0203a': {'commit': 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', 'blobs': {'hermes_cli/web_server.py': '59700ba32c525efcc2b4039401f1b031b6c675c6', 'hermes_cli/dashboard_auth/__init__.py': '05941a68bdd3bcdcca49a0fe0eeee9e0018cb840', 'hermes_cli/dashboard_auth/base.py': '9e8c7a6b0c9d7b939b92c44cc8a92f497b0bcc86', 'hermes_cli/dashboard_auth/routes.py': 'be029a317fb45edfa9d4f66f16c33dff856e7c48', 'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960', 'hermes_cli/dashboard_auth/middleware.py': '2f360140b2e94df6457d33ba1d9d977463d545ef', 'hermes_cli/dashboard_auth/cookies.py': '1a52c8b16db105a514a36b3b38ee14d9c23232de', 'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a', 'hermes_cli/dashboard_auth/request_utils.py': '2eca3d145c8cba33e974604e3308a0caa0339922', 'hermes_cli/dashboard_auth/prefix.py': '1e61b5a72d5fef542532544890e0b4bd748fac74', 'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324', 'hermes_cli/dashboard_auth/registry.py': '18687691f8b79e551db1565b613e60fcda8afd35', 'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599', 'hermes_cli/version_info.py': 'a4b3b33b9fe7341a7ed6e927afaf24bb391d9988', 'hermes_cli/source_check.py': '5a67a576988ccaf49e1b0192de12606232a86a37', 'hermes_cli/_subprocess_compat.py': '8ec065c638016983089e6b174c89951e9e2e93a3', 'hermes_cli/web_routers/sessions.py': 'd6a5cb947a14855c260754307bcf7698fe520da2', 'hermes_cli/web_routers/profiles.py': '8c60f23c4c52117558ebcccf941c27ce86ca3803', 'hermes_cli/web_server_sessions.py': '5d2d82cb97003fc87116dd115a821538a845194d', 'hermes_cli/web_server_cron.py': '761693b5d8e9a9e64606da49784be7879bfdeb42', 'hermes_cli/profiles.py': '08b7eb1057f21f91175dc19074a98c24bd515def', 'hermes_state.py': '833f4b738f4698034b7da22276fbcf7bfd3f9768', 'hermes_state_sessions.py': 'a072ea5eec5bb9c74491052676e004c16eb2451a'}}, 'main-071d31f': {'commit': '071d31ffc69493ca1d7d368c163ce0b303d83387', 'blobs': {'gateway/drain_control.py': 'e005b94867986382089efef07030b41c91e3e2ce', 'gateway/run_api.py': 'cd6997cf21b25e8c51e8faae323f5745c2279c04', 'gateway/runtime_bootstrap.py': '97e860e480f877b014834032f2fff8e46d49d1f1', 'gateway/session_authorities.py': '0ff207262b4843c05601028c1aadbdda2bcfd22e', 'hermes_cli/_subprocess_compat.py': 'a2f985af6197eb80ba55b8f45d3c4fea8e3c4eff', 'hermes_cli/dashboard_auth/__init__.py': 'f52f683894b4272e85565f8b2778b4b77b170b73', 'hermes_cli/dashboard_auth/base.py': '6ddeb91fe8efbbde7c1274db37f761cee4e7f4ae', 'hermes_cli/dashboard_auth/cookies.py': 'd6e5bb541594277a99d7e8f3d71fd43b68b717bc', 'hermes_cli/dashboard_auth/middleware.py': '29e8ee32381fc976f93282776c08a9151cb05eba', 'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960', 'hermes_cli/dashboard_auth/native_http.py': 'd923d8812f360c534cb7758c7fc7f8cffc802f9a', 'hermes_cli/dashboard_auth/prefix.py': 'c8b86dc8b8e55415c4c7069d7c5993d025c627bb', 'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324', 'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a', 'hermes_cli/dashboard_auth/registry.py': '082f3731e315bc20f5331aec175af8bac34d17c5', 'hermes_cli/dashboard_auth/request_utils.py': 'aca6ad79247b6e66fc4dae99de19da1911c96543', 'hermes_cli/dashboard_auth/routes.py': '97dfc55f91130da8924ff497b4560d9d5305b00e', 'hermes_cli/gateway_connections.py': '7a2828d8fc689903b3166f45d4fd431897a66f32', 'hermes_cli/gateway_connections_normalize.py': '340523a3c48d6e272e323f967589e48eb6f3d613', 'hermes_cli/gateway_connections_oauth.py': 'd2088a348587fcb1972683d84cdb7d57007255c3', 'hermes_cli/profiles.py': '15a147c862ca6d60ab5175eb79efb569d5361772', 'hermes_cli/source_check.py': '5bc247aea6c67af9b6eccdfbd2bbd455401530fc', 'hermes_cli/ui_preferences.py': '966a59efac325574093543918f090c0a2f43a4d1', 'hermes_cli/version_info.py': '13aaee3631162f5c4d568d636e2567ec75b9dfd9', 'hermes_cli/web_routers/gateway_connections_reach.py': '924d910e122c5684e7f3b646c7fb6838e52efda9', 'hermes_cli/web_routers/profiles.py': 'dedfdcb871aa06b621310321301a658871ded80c', 'hermes_cli/web_routers/sessions.py': 'be61ae22c64628038396fbc135759f296889bd39', 'hermes_cli/web_server.py': '7001789f657d68778d826c1bc9e84be27969ee37', 'hermes_cli/web_server_cron.py': 'b777f511a8786d8c7ea788bc93276d58546f6b48', 'hermes_cli/web_server_sessions.py': '650ba6470402f763314aab7145fcbc8afd8a8a8f', 'hermes_cli/web_server_surface.py': 'd98e69371a5550cf152d4af634e3f3b3f915ca94', 'hermes_state.py': 'fe0249d6804ac5e6810a208f7a32e6dcf3e251f9', 'hermes_state_sessions.py': '476b3ef51d4304f3ef57d35576e2b98e73ca56fc', 'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599', 'utils.py': '344d24eb16e3188cfa952fe6072da0e63e67c62d', 'hermes_cli/web_server_boundary.py': 'd4aeb4e044f148e9d873309220059956c020cc86', 'gateway/run_idle_exit.py': '2ee4909acfa27c40f467ec42450dab9be97a1756'}}}

# Current stable: all original pins remain; admission races and new credential paths
# use the complete declared fixture dependencies at this exact clean source.
REVISIONS["stable-3b42157"] = {
    "blobs": {
        "agent/provider_registry.py": "54d892ff2a6414915b2fff5a469b114c0c906770",
        "agent/secret_scope.py": "2b88d2267a148a1e1dbb2b244d0a895b95e1b1f3",
        "gateway/drain_control.py": "e005b94867986382089efef07030b41c91e3e2ce",
        "gateway/run.py": "2de09616f04d2284f4071a670392f953c9f8ebc1",
        "gateway/run_api.py": "6695ea440944b214a13dd8d4d076ec79b7283ed9",
        "gateway/run_idle_exit.py": "2ee4909acfa27c40f467ec42450dab9be97a1756",
        "gateway/runtime_bootstrap.py": "97e860e480f877b014834032f2fff8e46d49d1f1",
        "gateway/session_authorities.py": "0ff207262b4843c05601028c1aadbdda2bcfd22e",
        "hermes_cli/_subprocess_compat.py": "a2f985af6197eb80ba55b8f45d3c4fea8e3c4eff",
        "hermes_cli/browser_credential.py": "c7580598b1ec49fc30ddb275d7307d40fcc3e178",
        "hermes_cli/dashboard_auth/__init__.py": "f52f683894b4272e85565f8b2778b4b77b170b73",
        "hermes_cli/dashboard_auth/audit.py": "37d8553e883ef8f9956a6cd994797240daa94310",
        "hermes_cli/dashboard_auth/base.py": "6ddeb91fe8efbbde7c1274db37f761cee4e7f4ae",
        "hermes_cli/dashboard_auth/cookies.py": "d6e5bb541594277a99d7e8f3d71fd43b68b717bc",
        "hermes_cli/dashboard_auth/middleware.py": "29e8ee32381fc976f93282776c08a9151cb05eba",
        "hermes_cli/dashboard_auth/native_flow.py": "f9483c8bea04c046dab89907108b2a42132bd960",
        "hermes_cli/dashboard_auth/native_http.py": "d923d8812f360c534cb7758c7fc7f8cffc802f9a",
        "hermes_cli/dashboard_auth/prefix.py": "c8b86dc8b8e55415c4c7069d7c5993d025c627bb",
        "hermes_cli/dashboard_auth/public_paths.py": "a4793ab7e815b584869231b501806070f0485324",
        "hermes_cli/dashboard_auth/refresh_singleflight.py": "6d94bd44201237dd56075cd0a1c50e1c12c9428a",
        "hermes_cli/dashboard_auth/registry.py": "082f3731e315bc20f5331aec175af8bac34d17c5",
        "hermes_cli/dashboard_auth/request_utils.py": "aca6ad79247b6e66fc4dae99de19da1911c96543",
        "hermes_cli/dashboard_auth/routes.py": "97dfc55f91130da8924ff497b4560d9d5305b00e",
        "hermes_cli/gateway_connections.py": "7a2828d8fc689903b3166f45d4fd431897a66f32",
        "hermes_cli/gateway_connections_normalize.py": "340523a3c48d6e272e323f967589e48eb6f3d613",
        "hermes_cli/gateway_connections_oauth.py": "d2088a348587fcb1972683d84cdb7d57007255c3",
        "hermes_cli/profiles.py": "c7d9c6b5f309eabb448d843163c296c22eeb99fc",
        "hermes_cli/source_check.py": "5bc247aea6c67af9b6eccdfbd2bbd455401530fc",
        "hermes_cli/ui_preferences.py": "966a59efac325574093543918f090c0a2f43a4d1",
        "hermes_cli/version_info.py": "13aaee3631162f5c4d568d636e2567ec75b9dfd9",
        "hermes_cli/web_routers/gateway_connections_reach.py": "8c01a4fe1c6608d7b394c49e42152f26e70b7bb2",
        "hermes_cli/web_routers/profiles.py": "c3a46ff68a7642f2302c7e563790b8ac23abbd9d",
        "hermes_cli/web_routers/sessions.py": "be61ae22c64628038396fbc135759f296889bd39",
        "hermes_cli/web_server.py": "2b13b58327fba7ed66630b092ed21352080684d3",
        "hermes_cli/web_server_boundary.py": "d4aeb4e044f148e9d873309220059956c020cc86",
        "hermes_cli/web_server_cron.py": "b777f511a8786d8c7ea788bc93276d58546f6b48",
        "hermes_cli/web_server_session_token.py": "665f2aeef4eb8641c5a2a342075c558b5938c708",
        "hermes_cli/web_server_sessions.py": "650ba6470402f763314aab7145fcbc8afd8a8a8f",
        "hermes_cli/web_server_surface.py": "d98e69371a5550cf152d4af634e3f3b3f915ca94",
        "hermes_cli/webapp_sign_in.py": "bdd33176a7eed6302af135b73b0aba7d17b3a49c",
        "hermes_state.py": "fe0249d6804ac5e6810a208f7a32e6dcf3e251f9",
        "hermes_state_sessions.py": "476b3ef51d4304f3ef57d35576e2b98e73ca56fc",
        "plugins/dashboard_auth/_shared.py": "ded9cb05bc279a7e814924ff8a16f273579caf48",
        "plugins/dashboard_auth/session_token/__init__.py": "bea000c6aa1dfd2bee982fccb29babd96719ee2e",
        "pm/__init__.py": "b738469c81615aa214f9f5eba2a409629e4ad734",
        "pm/client.py": "be154967c6aa932be053f10467c18dc7ff02c1e6",
        "pm/environments.py": "9c3844d790282e675ee40e69e1d7dcfa09225bca",
        "pm/environments_adopt.py": "4e48efd6303f2b9c02589fab777a79ff7d61e2d8",
        "pm/extras.py": "a5b7a81aa3ffa8a399b075452f3687f3ed90a317",
        "pm/package.py": "537761f099f94fa55b3937e943e25548c4a2c664",
        "pm/paths.py": "52d4f0a882d0b91c8ad3b40f9118725b3b3d612c",
        "tests/hermes_cli/conftest_dashboard_auth.py": "f06ec93f722b26530b69d81c8fed0edf870a1599",
        "tools/terminal_scope.py": "caeac5135e7e2f832a2d5ce06a0f752e894d2ee9",
        "tui_gateway/launch_profile_policy.py": "1127824ac984e8e65895871a9355f5d2f9261cec",
        "utils.py": "344d24eb16e3188cfa952fe6072da0e63e67c62d"
    },
    "commit": "3b421578588243f920886e6fe79a6faa87ea026e"
}
if not __debug__ or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
    raise SystemExit('Use -I -S -B without -O; assertions and isolated imports are required.')
if os.name != 'nt':
    raise SystemExit('This bounded probe currently supports Windows only; no cross-host acceptance is claimed.')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root', type=Path, required=True)
parser.add_argument('--work-root', type=Path, required=True)
parser.add_argument('--dependency-site', type=Path, required=True)
parser.add_argument('--revision', choices=tuple(REVISIONS), default='stable-2bd378e')
a = parser.parse_args()
SOURCE_COMMIT = REVISIONS[a.revision]['commit']
PINNED_BLOBS = REVISIONS[a.revision]['blobs']
source, root, site = a.source_root.resolve(), a.work_root.resolve(), a.dependency_site.resolve()
assert source.is_dir() and site.is_dir() and not root.exists(), 'Use existing dependencies and a fresh output root.'
assert source.drive.upper() == root.drive.upper() == 'E:', 'Source and temporary output must stay on E:.'
assert not source.is_relative_to(root) and not root.is_relative_to(source)
assert not root.is_relative_to(site) and not site.is_relative_to(root)
for name, expected in PINNED_BLOBS.items():
    path = source / name
    assert not path.is_symlink() and path.resolve().is_relative_to(source)
    raw = path.read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    assert actual == expected, 'Pinned module differs: ' + name
root.mkdir(parents=True, exist_ok=False)
user, temp = root / 'account', root / 'temp'
for path in (user, user / 'AppData/Local', user / 'AppData/Roaming', temp, root / 'hermes-home', root / 'runtime'):
    path.mkdir(parents=True, exist_ok=True)
# Retain only OS loader variables and Git's executable search path for two local
# read-only identity checks. No production Hermes environment or credentials.
git_env = {k: v for k, v in os.environ.items() if k.upper() in ('SYSTEMROOT', 'WINDIR', 'PATH', 'PATHEXT')}
git_env.update(TEMP=str(temp), TMP=str(temp), HOME=str(user), USERPROFILE=str(user),
               GIT_OPTIONAL_LOCKS='0', GIT_NO_LAZY_FETCH='1', GIT_CONFIG_NOSYSTEM='1',
               GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT='0')
def git_read(*args):
    result = subprocess.run(['git', '--no-optional-locks', '-c', 'core.fsmonitor=false',
                             '-C', str(source), *args], env=git_env, capture_output=True,
                            text=True, check=True, timeout=30)
    return result.stdout.strip()
assert git_read('rev-parse', 'HEAD') == SOURCE_COMMIT, 'Wrong source commit.'
assert not git_read('status', '--porcelain', '--untracked-files=all'), 'Source must be clean.'
system = {k: v for k, v in os.environ.items() if k.upper() in ('SYSTEMROOT', 'WINDIR')}
os.environ.clear()
os.environ.update(system)
os.environ.update(TEMP=str(temp), TMP=str(temp), TMPDIR=str(temp), HOME=str(user), USERPROFILE=str(user),
                  APPDATA=str(user / 'AppData/Roaming'), LOCALAPPDATA=str(user / 'AppData/Local'),
                  HERMES_HOME=str(root / 'hermes-home'), HERMES_RUNTIME_DIR=str(root / 'runtime'),
                  HERMES_DISABLE_LAZY_INSTALLS='1', PYTHONDONTWRITEBYTECODE='1', NO_PROXY='127.0.0.1,localhost')
os.environ['HERMES_TEST_ISOLATION'] = '1'
os.environ.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL=str(root/'empty-git-config'),GIT_OPTIONAL_LOCKS='0',GIT_NO_LAZY_FETCH='1',GIT_TERMINAL_PROMPT='0')
(root/'empty-git-config').write_text('',encoding='utf-8')
os.chdir(root)
sys.dont_write_bytecode = True
# Keep the verified short E alias for the Windows DLL loader; audit roots still
# resolve every read to the identical canonical dependency site.
assert a.dependency_site.is_absolute()
assert a.dependency_site.resolve() == site
sys.path[:0] = [str(source), str(a.dependency_site.absolute())]
read_roots = [root, source, site, Path(sys.base_prefix).resolve(), Path(__file__).resolve()]
denied, denial_details, loaded, report = [], [], [], {}

def inside(path, roots):
    return any((path.is_relative_to(x) for x in roots))

def filepath(value):
    return Path(os.fsdecode(value)).resolve() if isinstance(value, (str, bytes, os.PathLike)) else None

def refuse(event, target=None):
    denied.append(event)
    frames = []
    frame = sys._getframe(1)
    for _ in range(12):
        if frame is None:
            break
        file = Path(frame.f_code.co_filename)
        frames.append(dict(module=frame.f_globals.get('__name__', 'unknown'), function=frame.f_code.co_name, line=frame.f_lineno, file=file.relative_to(source).as_posix() if file.is_absolute() and file.is_relative_to(source) else file.name))
        frame = frame.f_back
    if len(denial_details) < 64:
        denial_details.append(dict(event=event, targetName=target.name if target is not None else None, targetPathSha256=hashlib.sha256(str(target).encode()).hexdigest() if target is not None else None, frames=frames))
    raise PermissionError('Native auth fixture boundary: ' + event)

expected_blocked_metadata_probes = []
VERSION_PROBES = {
    ('git', 'rev-parse', 'HEAD'),
    ('git', 'branch', '--show-current'),
    ('git', 'log', '-1', '--format=%ct', 'HEAD'),
    ('git', '--no-optional-locks', 'status', '--porcelain', '-uno'),
    ('git', 'tag', '--merged', 'HEAD', '--list', 'v[0-9]*'),
    ('git', 'describe', '--tags', '--long', '--match', 'v2[0-9][0-9][0-9].*', 'HEAD'),
    ('git', 'rev-parse', '--short=7', 'HEAD'),
}
def audit(event, args):
    if event == 'subprocess.Popen':
        argv, cwd = args[1], args[2]
        frame = sys._getframe(1)
        from_version_info = False
        for _ in range(10):
            if frame is None:
                break
            if frame.f_globals.get('__name__') == 'hermes_cli.version_info' and frame.f_code.co_name in ('_run_git', '_git_version_info'):
                from_version_info = True
            frame = frame.f_back
        version_probe = next((probe for probe in VERSION_PROBES if isinstance(argv, str) and argv == subprocess.list2cmdline(list(probe))), None)
        if isinstance(argv, (list, tuple)) and tuple(argv) in VERSION_PROBES:
            version_probe = tuple(argv)
        if version_probe is not None and cwd is not None and Path(cwd).resolve() == source and from_version_info:
            expected_blocked_metadata_probes.append(list(version_probe))
            raise PermissionError('Version metadata subprocess intentionally blocked in fixture')

        probes={('git','config',scope,'-z','--get-all','safe.directory') for scope in ('--system','--global')}
        source_probes={('git','rev-parse','HEAD'),('git','rev-parse','--abbrev-ref','HEAD'),('git','remote','get-url','origin'),('git','status','--porcelain')}
        words=next((p for p in probes|source_probes if argv==subprocess.list2cmdline(list(p)) or isinstance(argv,(list,tuple)) and tuple(argv)==p),None)
        expected_caller=False
        frame=sys._getframe(1)
        for _ in range(12):
            if frame is None:break
            module=frame.f_globals.get('__name__');function=frame.f_code.co_name
            if words in probes and module=='hermes_cli._subprocess_compat' and function=='_user_safe_directories' and cwd is None:expected_caller=True
            if words in source_probes and module=='hermes_cli.source_check' and function=='_git_run' and cwd is not None and Path(cwd).resolve()==source:expected_caller=True
            frame=frame.f_back
        if words is not None and expected_caller:
            expected_blocked_metadata_probes.append(list(words))
            raise PermissionError('Source metadata subprocess intentionally blocked in fixture')

    if event in ('subprocess.Popen', 'os.system', 'os.kill', 'os.killpg', 'os.exec', 'os.posix_spawn', 'sqlite3.enable_load_extension', 'sqlite3.load_extension', 'socket.sendto', 'socket.sendmsg', 'socket.gethostbyname', 'socket.gethostbyaddr'):
        refuse(event)
    if event == 'socket.getaddrinfo':
        try:
            allowed = ipaddress.ip_address(args[0]).is_loopback
        except ValueError:
            allowed = args[0] == 'localhost'
        if not allowed:
            refuse('nonloopback-resolution')
    if event in ('socket.bind', 'socket.connect'):
        address = args[1]
        if not isinstance(address, tuple) or len(address) < 2:
            refuse('unowned-socket-address')
        try:
            loopback = ipaddress.ip_address(address[0]).is_loopback
        except ValueError:
            loopback = False
        if not loopback:
            refuse('nonloopback-socket')
        if event == 'socket.bind':
            if address[1] != 0:
                refuse('fixed-port-bind')
            bound_sockets.add(args[0])
        else:
            ports = []
            for sock in tuple(bound_sockets):
                try:
                    ports.append(sock.getsockname()[1])
                except OSError:
                    pass
            if address[1] not in ports or address[1] == 0:
                refuse('connect-to-unowned-local-port')
        owned_socket_events.append(event)
    if event == 'open':
        path = filepath(args[0])
        mode = args[1] or ''
        flags = args[2] or 0
        writing = any((x in mode for x in 'wax+')) or bool(flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND))
        if not writing and str(args[0]) in missing_os_probes:
            expected = missing_os_probes[str(args[0])]
            if path == expected and path.resolve() == expected and (not path.exists()):
                if len(missing_os_probe_reads) < 128:
                    missing_os_probe_reads.append(str(args[0]))
                return
        if path and str(args[0]).lower() != os.devnull.lower() and (not inside(path, [root] if writing else read_roots)):
            refuse('file-write' if writing else 'file-read', path)
    if event == 'os.chdir' and (not Path(args[0]).resolve().is_relative_to(root)):
        refuse('chdir-outside-fixture')
    if event in ('os.mkdir', 'os.remove', 'os.rmdir', 'os.rename', 'os.symlink', 'os.link'):
        values = args[:2] if event in ('os.rename', 'os.symlink', 'os.link') else args[:1]
        if any(((path := filepath(v)) is not None and (not path.is_relative_to(root)) for v in values)):
            refuse(event)
    if event == 'sqlite3.connect' and str(args[0]) != ':memory:':
        value = str(args[0])
        if value.startswith('file:'):
            uri = urllib.parse.urlsplit(value)
            if uri.netloc not in ('', 'localhost'):
                refuse('sqlite-uri-authority')
            value = urllib.parse.unquote(uri.path)
            if value.startswith('/') and len(value) > 2 and (value[2] == ':'):
                value = value[1:]
        if not Path(value).resolve().is_relative_to(root):
            refuse('sqlite-outside-fixture')
missing_os_probes = {}
assert os.name == 'nt'
for name in ('/proc/1/cgroup', '/proc/self/mountinfo', '/proc/version'):
    target = Path(root.drive + name.replace('/', '\\'))
    assert target.resolve() == target and (not target.exists())
    missing_os_probes[name] = target
missing_manifest = source.parent / 'manifest.json'
assert missing_manifest.resolve() == missing_manifest and (not missing_manifest.exists())
missing_os_probes[str(missing_manifest)] = missing_manifest
missing_os_probe_reads = []
bound_sockets = weakref.WeakSet()
owned_socket_events = []
cases = []
sys.addaudithook(audit)

try:
    import base64,time,urllib.parse
    import hermes_constants as hc
    assert hc.get_hermes_home().resolve()==root/'hermes-home'

    from hermes_state import SessionDB
    homes={'default':root/'hermes-home','worker':root/'hermes-home/profiles/worker'}
    original={}
    for profile,home in homes.items():
        home.mkdir(parents=True,exist_ok=True);(home/'SOUL.md').write_text('Synthetic '+profile+' identity only.',encoding='utf-8')
        scope_token=hc.set_hermes_home_override(str(home))
        try:
            db=SessionDB(db_path=home/'state.db')
            try:
                for sid in ('shared-fixture',profile+'-only-fixture'):
                    db.create_session(sid,source='cli')
                    db.append_message(sid,'user',profile+' private user fixture')
                    db.append_message(sid,'assistant',profile+' private reply fixture')
                original[profile]={sid:(db.get_session(sid),db.get_messages(sid)) for sid in ('shared-fixture',profile+'-only-fixture')}
            finally:db.close()
        finally:hc.reset_hermes_home_override(scope_token)

    from hermes_cli import web_server
    from hermes_cli.dashboard_auth import clear_providers,register_provider,native_flow
    from tests.hermes_cli.conftest_dashboard_auth import StubAuthProvider
    from fastapi.testclient import TestClient
    clear_providers();register_provider(StubAuthProvider());native_flow._reset_for_tests()
    web_server.app.state.bound_host='fixture.invalid';web_server.app.state.bound_port=443;web_server.app.state.auth_required=True
    with TestClient(web_server.app,base_url='https://fixture.invalid',follow_redirects=False,client=('198.51.100.7',50000)) as client:
        # This pilot deliberately starts the actual lifespan, not a copied router.
        reply=client.get('/api/auth/me');assert reply.status_code==401;cases.append('missing_bearer_refused')

        def walk():
            verifier=base64.urlsafe_b64encode(os.urandom(32)).rstrip(b'=').decode()
            challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
            reply=client.get('/auth/native/authorize',params=dict(provider='stub',code_challenge=challenge,code_challenge_method='S256',redirect_uri='http://127.0.0.1:53999/callback',state='native-fixture-state'))
            assert reply.status_code==302
            parts=urllib.parse.urlsplit(reply.headers['location'])
            reply=client.get(parts.path+'?'+parts.query);assert reply.status_code==302
            assert 'hermes_session_at' not in reply.headers.get('set-cookie','')
            query=urllib.parse.parse_qs(urllib.parse.urlsplit(reply.headers['location']).query);assert query['state']==['native-fixture-state']
            client.cookies.clear()
            return verifier,challenge,query['code'][0]
        verifier,challenge,code=walk();cases.extend(['real_native_authorize','native_callback_without_access_cookie'])
        reply=client.post('/auth/native/token',json=dict(code=code,code_verifier=verifier));assert reply.status_code==200
        tokens=reply.json();assert tokens['provider']=='stub' and tokens['user_id']=='stub-user-1';assert 'set-cookie' not in reply.headers
        cases.append('real_pkce_exchange')
        reply=client.get('/api/auth/me',headers={'Authorization':'Bearer '+tokens['access_token']});assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
        cases.append('cookieless_bearer_identity')
        reply=client.post('/auth/native/refresh',json=dict(refresh_token=tokens['refresh_token'],provider='stub'));assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
        cases.append('real_refresh_same_identity')

        # Every protected read uses a fresh explicit bearer header, never browser cookies.
        headers={'Authorization':'Bearer '+tokens['access_token']}
        reply=client.get('/api/profiles',headers=headers);assert reply.status_code==200
        profiles=reply.json()['profiles'];assert {'default','worker'}<={p['name'] for p in profiles};cases.append('real_profile_inventory')
        for profile in ('default','worker'):
            reply=client.get('/api/sessions',params={'profile':profile,'limit':20},headers=headers);assert reply.status_code==200
            rows=reply.json()['sessions'];assert {r['id'] for r in rows}=={'shared-fixture',profile+'-only-fixture'}
            assert all(row['profile']==profile for row in rows);cases.append(profile+'_scoped_history_inventory')
            reply=client.get('/api/sessions/shared-fixture/messages',params={'profile':profile,'limit':20},headers=headers);assert reply.status_code==200
            data=reply.json();assert data['profile']==profile and data['session_id']=='shared-fixture'
            assert [row['content'] for row in data['messages']]==[profile+' private user fixture',profile+' private reply fixture'];cases.append(profile+'_same_id_transcript_isolated')
            other='worker' if profile=='default' else 'default'
            reply=client.get('/api/sessions/'+other+'-only-fixture/messages',params={'profile':profile,'limit':20},headers=headers);assert reply.status_code==404
            cases.append(profile+'_foreign_only_id_refused')
        absent=root/'hermes-home/profiles/missing-fixture';assert not absent.exists()
        reply=client.get('/api/sessions',params={'profile':'missing-fixture'},headers=headers);assert reply.status_code==404 and not absent.exists();cases.append('missing_profile_not_created')
        reply=client.get('/api/auth/me',headers={'Authorization':'Bearer invalid-fixture-token'});assert reply.status_code==401 and 'user_id' not in reply.json();cases.append('invalid_bearer_refused_without_identity')
        params=dict(provider='stub',code_challenge=challenge,code_challenge_method='S256',redirect_uri='https://outside.example/callback',state='fixture')
        reply=client.get('/auth/native/authorize',params=params);assert reply.status_code==400;cases.append('nonloopback_redirect_refused_before_navigation')
        params.update(redirect_uri='http://127.0.0.1:53999/callback',code_challenge_method='plain')
        reply=client.get('/auth/native/authorize',params=params);assert reply.status_code==400;cases.append('plain_pkce_refused')
        reply=client.post('/auth/native/token',json=dict(code=code,code_verifier=verifier));assert reply.status_code==400;replay_error=reply.json();cases.append('used_code_replay_refused')
        good,_,wrong_code=walk()
        reply=client.post('/auth/native/token',json=dict(code=wrong_code,code_verifier='wrong-fixture-verifier'));assert reply.status_code==400 and reply.json()==replay_error
        reply=client.post('/auth/native/token',json=dict(code=wrong_code,code_verifier=good));assert reply.status_code==400 and reply.json()==replay_error;cases.append('wrong_verifier_consumes_code_without_oracle')
        reply=client.post('/auth/native/refresh',json=dict(refresh_token='invalid-fixture-refresh',provider='stub'));assert reply.status_code==401 and reply.json()['error']=='session_expired';cases.append('invalid_refresh_requires_signin')

        if a.revision in ('main-071d31f','stable-3b42157'):
            # Actual private-grant admission inside the already-running ASGI app. The
            # adapter supplies a synthetic captured socket peer, never from headers.
            # This is not daemon/control-socket or TCP-peer-capture acceptance.
            from types import SimpleNamespace
            from gateway.runtime_bootstrap import TicketStore
            from gateway.session_authorities import SessionAuthorities
            registry=SessionAuthorities(homes['default'],multiplexed=True)
            instance='synthetic-native-instance'
            for profile,home in homes.items():
                registry.add(home,SimpleNamespace(profile_id=str(home),instance_id=instance,retiring=False),name=profile)
            store=TicketStore(instance,frozenset(str(h) for h in homes.values()))
            runner=SimpleNamespace(session_authorities=registry,session_ticket_store=store,
                session_runtime_descriptor=dict(state='ready',instance_id=instance,served_profiles=registry.served_profiles()),_draining=False)
            web_server.app.state.gateway_runner=runner
            class CapturedPeer:
                def __init__(self,app):self.app=app
                async def __call__(self,scope,receive,send):
                    if scope['type']=='http':scope=dict(scope,**{'hermes.gateway_socket_peer':scope.get('client')})
                    await self.app(scope,receive,send)
            local=TestClient(CapturedPeer(web_server.app),base_url='https://fixture.invalid',follow_redirects=False,client=('127.0.0.1',50001))
            foreign=TestClient(CapturedPeer(web_server.app),base_url='https://fixture.invalid',follow_redirects=False,client=('198.51.100.8',50002))
            def mint(profile='default',purpose='native-http'):
                return store.mint(profile_id=str(homes[profile]),subject='synthetic-owner',purpose=purpose)
            def grant(token):return {'X-Hermes-Gateway-Ticket':token}
            try:
                token=mint();reply=local.get('/api/sessions',params={'profile':'default','limit':20},headers=grant(token))
                assert reply.status_code==200 and {v['id'] for v in reply.json()['sessions']}=={'shared-fixture','default-only-fixture'}
                cases.append('native_grant_passes_actual_gated_auth')
                reply=local.get('/api/sessions',params={'profile':'default'},headers=grant(token));assert reply.status_code==401
                cases.append('native_grant_replay_refused')
                reply=local.get('/api/sessions',params={'profile':'default'},headers={**headers,**grant('invalid-native-fixture')});assert reply.status_code==401
                cases.append('invalid_native_grant_cannot_fall_back_to_valid_bearer')
                reply=client.get('/api/sessions',params={'profile':'default'},headers={**grant(mint()),'X-Forwarded-For':'127.0.0.1'});assert reply.status_code==401
                cases.append('native_grant_without_captured_peer_refused')
                reply=foreign.get('/api/sessions',params={'profile':'default'},headers={**grant(mint()),'X-Forwarded-For':'127.0.0.1'});assert reply.status_code==401
                cases.append('forwarded_header_cannot_replace_actual_peer')
                for label,origin in [('empty',''),('null','null'),('external','https://outside.example')]:
                    reply=local.get('/api/sessions',params={'profile':'default'},headers={**grant(mint()),'Origin':origin});assert reply.status_code==401
                    cases.append('native_grant_'+label+'_origin_refused')
                reply=local.get('/api/sessions',params={'profile':'default'},headers=[('X-Hermes-Gateway-Ticket',mint()),('X-Hermes-Gateway-Ticket',mint())]);assert reply.status_code==401
                cases.append('duplicate_native_grant_headers_refused')
                for purpose in ('interactive','exposure','worker-adoption'):
                    reply=local.get('/api/sessions',params={'profile':'default'},headers=grant(mint(purpose=purpose)));assert reply.status_code==401
                    cases.append('native_grant_wrong_'+purpose+'_purpose_refused')
                reply=local.get('/api/sessions',params={'profile':'worker'},headers=grant(mint()));assert reply.status_code==403
                cases.append('native_grant_foreign_profile_refused')
                reply=local.get('/api/sessions',params=[('profile','default'),('profile','worker')],headers=grant(mint()));assert reply.status_code==403
                cases.append('native_grant_conflicting_profile_selectors_refused')
                reply=local.get('/api/sessions',params={'profile':'worker','limit':20},headers=grant(mint('worker')))
                assert reply.status_code==200 and {v['id'] for v in reply.json()['sessions']}=={'shared-fixture','worker-only-fixture'}
                reply=local.get('/api/sessions/shared-fixture/messages',params={'profile':'worker','limit':20},headers=grant(mint('worker')))
                assert reply.status_code==200 and [v['content'] for v in reply.json()['messages']]==['worker private user fixture','worker private reply fixture']
                cases.append('secondary_native_grant_reads_only_its_own_history')
                runner.session_runtime_descriptor['instance_id']='stale-synthetic-instance'
                reply=local.get('/api/sessions',params={'profile':'default'},headers=grant(mint()));assert reply.status_code==401
                runner.session_runtime_descriptor['instance_id']=instance;cases.append('native_grant_instance_mismatch_refused')
                runner._draining=True
                reply=local.get('/api/sessions',params={'profile':'default'},headers=grant(mint()));assert reply.status_code==401
                runner._draining=False;cases.append('native_grant_during_drain_refused')
                reply=local.get('/api/sessions',params={'profile':'default','ticket':mint()});assert reply.status_code==401
                cases.append('native_grant_in_query_cannot_authenticate')
                reply=local.get('/api/sessions',params={'profile':'default'},headers={'native_http_principal':'synthetic-owner'});assert reply.status_code==401
                cases.append('principal_named_header_cannot_stamp_request_state')

                # Exact remote-without-Origin routing changed in run_api. A refusing
                # downstream gate proves delegation only; it cannot mint an actor.
                import asyncio
                from gateway.run_api import GatewayRuntimeAPI
                async def routed(peer,token,extra=(),malformed=False):
                    reached=[];sent=[]
                    async def downstream(scope,receive,send):
                        reached.append(dict(peer=scope['hermes.gateway_socket_peer'],principal='hermes.session_authority' in scope))
                        await send({'type':'websocket.close','code':4401})
                    async def receive():return {'type':'websocket.connect'}
                    async def send(message):sent.append(message)
                    protocol='hermes-gateway-v1, hermes-gateway-ticket.'+token
                    if malformed:protocol='hermes-gateway-v1, hermes-gateway-ticket.'
                    scope=dict(type='websocket',path='/api/ws',client=(peer,50003),server=('fixture.invalid',443),scheme='wss',http_version='1.1',root_path='',query_string=b'',headers=[(b'host',b'fixture.invalid'),(b'sec-websocket-protocol',protocol.encode()),*extra],subprotocols=protocol.split(', '))
                    await GatewayRuntimeAPI(downstream,runner,web_server.app)(scope,receive,send)
                    return reached,sent
                for name,extra in [('without_origin',()),('with_origin',((b'origin',b'https://fixture.invalid'),)),('with_forwarded_loopback',((b'x-forwarded-for',b'127.0.0.1'),))]:
                    token=mint(purpose='interactive');reached,sent=asyncio.run(routed('198.51.100.9',token,extra))
                    assert reached==[{'peer':('198.51.100.9',50003),'principal':False}] and sent==[{'type':'websocket.close','code':4401}]
                    assert store.redeem(token,profile_id=None,purpose='interactive')['subject']=='synthetic-owner'
                    cases.append('remote_socket_'+name+'_delegates_to_existing_gate_without_native_redemption')
                token=mint();reached,sent=asyncio.run(routed('127.0.0.1',token))
                assert reached==[{'peer':('127.0.0.1',50003),'principal':False}] and sent==[{'type':'websocket.close','code':4401}]
                assert store.redeem(token,profile_id=None,purpose='native-http')['subject']=='synthetic-owner'
                cases.append('local_http_purpose_cannot_gain_interactive_socket_identity')
                token=mint();reached,sent=asyncio.run(routed('127.0.0.1',token,malformed=True))
                report['malformedSocketObservation']=dict(reached=reached,sent=sent)
                assert reached==[] and sent==[{'type':'websocket.close','code':4403,'reason':''}]
                assert store.redeem(token,profile_id=None,purpose='native-http')['subject']=='synthetic-owner'
                cases.append('malformed_local_socket_protocol_refused_before_downstream')
                if a.revision == 'stable-3b42157':
                    import gateway.run_api as run_api_module
                    # Admitted profile scopes require the same owner link and multiplex
                    # config a real served authority has; earlier refusal cases need neither.
                    runner.config=SimpleNamespace(multiplex_profiles=True)
                    for authority in registry:authority.runner=runner
                    original_loader=run_api_module.session_socket_handler
                    try:
                        async def admission(mode,profile):
                            runner._draining=False
                            runner.session_runtime_descriptor['state']='ready'
                            observed=[];sent=[]
                            token=mint(profile,purpose='interactive')
                            async def handle(ws,**identity):
                                assert hc.get_hermes_home().resolve()==homes[profile]
                                assert runner._idle_exit_clients==1
                                assert identity['operator'] is True
                                auth=identity['auth_identity']
                                assert auth['user_id']=='synthetic-owner' and auth['provider']=='local'
                                assert auth['profile_id']==str(homes[profile]) and auth['instance_id']==instance and auth['native_bootstrap'] is True
                                observed.append('exact_profile_identity_and_active_client')
                                await ws.accept(subprotocol=identity['subprotocol'])
                                await ws.close(code=1000)
                            async def load():
                                assert runner._idle_exit_clients==1
                                observed.append('client_counted_before_loader_yields')
                                await asyncio.sleep(0)
                                if mode=='drain':runner._draining=True
                                elif mode=='descriptor':runner.session_runtime_descriptor['state']='stopping'
                                elif mode=='failure':raise RuntimeError('Synthetic fixture handler load failure')
                                return handle
                            run_api_module.session_socket_handler=load
                            protocol='hermes-gateway-v1, hermes-gateway-ticket.'+token
                            scope=dict(type='websocket',path='/api/ws',client=('127.0.0.1',50003),server=('fixture.invalid',443),scheme='wss',http_version='1.1',root_path='',query_string=b'',headers=[(b'host',b'fixture.invalid'),(b'sec-websocket-protocol',protocol.encode())],subprotocols=protocol.split(', '))
                            async def receive():return {'type':'websocket.connect'}
                            async def send(message):sent.append(message)
                            try:
                                try:await GatewayRuntimeAPI(downstream_admission,runner,web_server.app)(scope,receive,send)
                                except RuntimeError as error:
                                    assert mode=='failure' and str(error)=='Synthetic fixture handler load failure'
                                    observed.append('load_failure_propagates_without_handler')
                                else:assert mode!='failure'
                                assert runner._idle_exit_clients==0
                                try:store.redeem(token,profile_id=None,purpose='interactive')
                                except PermissionError:pass
                                else:raise AssertionError('Consumed admission ticket was replayable')
                                if mode=='ready':
                                    assert observed==['client_counted_before_loader_yields','exact_profile_identity_and_active_client']
                                    assert [message['type'] for message in sent]==['websocket.accept','websocket.close'] and sent[-1]['code']==1000
                                elif mode in ('drain','descriptor'):
                                    assert observed==['client_counted_before_loader_yields'] and sent==[{'type':'websocket.close','code':1013,'reason':''}]
                                else:assert observed==['client_counted_before_loader_yields','load_failure_propagates_without_handler'] and not sent
                            finally:
                                runner._draining=False
                                runner.session_runtime_descriptor['state']='ready'
                        async def downstream_admission(scope,receive,send):raise AssertionError('Native interactive ticket reached downstream dashboard gate')
                        for profile in ('default','worker'):
                            asyncio.run(admission('ready',profile))
                            cases.append('async_native_loader_counts_client_and_binds_'+profile+'_identity')
                        for mode in ('drain','descriptor','failure'):
                            asyncio.run(admission(mode,'default'))
                            cases.append('async_native_loader_'+mode+'_does_not_invoke_handler_or_leak_client')
                    finally:
                        run_api_module.session_socket_handler=original_loader

            finally:
                local.close();foreign.close();del web_server.app.state.gateway_runner

            # Exercise the actual public callback and actual issuer-bound store, with
            # an explicit synthetic outbound token response. No remote request runs.
            from hermes_cli import gateway_connections as connection_store,gateway_connections_oauth as oauth
            from httpx import Response
            previous_surface=getattr(web_server.app.state,'ui_surface',None)
            web_server.app.state.ui_surface='webapp'
            connection=connection_store.save_connection(dict(kind='remote',label='Synthetic callback fixture',url='https://gateway-fixture.example/prefix',authMode='oauth'))[0]
            connection_id=connection['id'];base='https://gateway-fixture.example/prefix'
            other=connection_store.save_connection(dict(kind='remote',label='Other synthetic fixture',url='https://other-fixture.example',authMode='oauth'))[0]['id']
            post_calls=[];original_post=oauth._post_json
            def fake_post(url,body,extra):
                assert url==base+'/auth/native/token' and body=={'code':'synthetic-callback-code','code_verifier':'synthetic-callback-verifier'} and extra=={}
                post_calls.append('one_synthetic_token_exchange')
                return Response(200,json=dict(access_token='synthetic-stored-access',refresh_token='synthetic-stored-refresh',provider='stub',user_id='stub-callback-user',expires_at=4102444800))
            oauth._post_json=fake_post
            def flight(state,expires=None):
                assert state not in oauth._flights
                oauth._flights[state]=oauth._Flight(connection_id=connection_id,base_url=base,verifier='synthetic-callback-verifier',redirect_uri='http://127.0.0.1:53999'+oauth.CALLBACK_PATH,authorize_url='https://gateway-fixture.example/prefix/auth/native/authorize',expires_at=time.time()+60 if expires is None else expires)
            try:
                reply=client.get(oauth.CALLBACK_PATH,params={'code':'never-echo-this-code','state':'unknown-native-state'})
                assert reply.status_code==400 and reply.headers.get('cache-control')=='no-store' and not post_calls and 'never-echo-this-code' not in reply.text
                cases.append('public_saved_gateway_callback_unknown_state_refused')
                flight('expired-native-state',0)
                reply=client.get(oauth.CALLBACK_PATH,params={'code':'never-echo-this-code','state':'expired-native-state'})
                assert reply.status_code==400 and not post_calls and 'expired-native-state' not in oauth._flights
                cases.append('public_saved_gateway_callback_expired_state_consumed')
                flight('error-native-state')
                reply=client.get(oauth.CALLBACK_PATH,params={'state':'error-native-state','error':'denied','error_description':'<script>synthetic</script>'})
                assert reply.status_code==400 and '<script>' not in reply.text and '&lt;script&gt;' in reply.text and not post_calls and 'error-native-state' not in oauth._flights
                cases.append('public_saved_gateway_callback_error_escaped_and_state_consumed')
                flight('good-native-state')
                try:oauth.complete_sign_in(code='synthetic-callback-code',state='good-native-state',connection_id=other)
                except connection_store.ConnectionStoreError:pass
                else:raise AssertionError('Foreign connection accepted callback state')
                assert not post_calls and 'good-native-state' in oauth._flights
                cases.append('saved_gateway_callback_other_connection_refused_before_exchange')
                reply=client.get(oauth.CALLBACK_PATH,params={'code':'synthetic-callback-code','state':'good-native-state'})
                assert reply.status_code==200 and reply.headers.get('cache-control')=='no-store' and len(post_calls)==1 and 'good-native-state' not in oauth._flights
                assert all(value not in reply.text for value in ('synthetic-stored-access','synthetic-stored-refresh','synthetic-callback-verifier','synthetic-callback-code'))
                tokens=connection_store.read_tokens(connection_id)
                assert tokens['accessToken']=='synthetic-stored-access' and tokens['refreshToken']=='synthetic-stored-refresh' and tokens['baseUrl']==base
                cases.append('public_saved_gateway_callback_stores_only_for_exact_issuer')
                reply=client.get(oauth.CALLBACK_PATH,params={'code':'synthetic-callback-code','state':'good-native-state'})
                assert reply.status_code==400 and len(post_calls)==1
                cases.append('public_saved_gateway_callback_replay_does_not_exchange_again')
                flight('repointed-native-state')
                connection_store.save_connection(dict(connection,id=connection_id,url='https://repointed-fixture.example'))
                reply=client.get(oauth.CALLBACK_PATH,params={'code':'synthetic-callback-code','state':'repointed-native-state'})
                assert reply.status_code==400 and len(post_calls)==1 and 'repointed-native-state' not in oauth._flights and not connection_store.read_tokens(connection_id)
                cases.append('public_saved_gateway_callback_repointed_issuer_refused')
            finally:
                oauth._post_json=original_post;oauth._flights.clear();web_server.app.state.ui_surface=previous_surface

        if a.revision == 'stable-3b42157':
            from hermes_cli.dashboard_auth import list_providers
            from plugins.dashboard_auth.session_token import SessionTokenProvider
            from hermes_cli import browser_credential
            original_providers=list_providers()
            original_surface=getattr(web_server.app.state,'ui_surface',None)
            native_access=headers['Authorization'].removeprefix('Bearer ')
            assert native_access
            deployment='synthetic-deployment-token-fixture-only'
            assert all(p.name!='session-token' for p in original_providers)
            deployment_provider=SessionTokenProvider(token=deployment)
            try:
                register_provider(deployment_provider)
                web_server.app.state.ui_surface='dashboard'
                client.cookies.clear()
                reply=client.get('/api/auth/me',headers={'X-Hermes-Session-Token':deployment})
                assert reply.status_code==200 and reply.json()['user_id']=='deployment-token' and 'set-cookie' not in reply.headers
                cases.append('deployment_header_verifies_exact_registered_provider_without_cookie')
                reply=client.get('/api/auth/me',headers={'X-Hermes-Session-Token':'invalid-fixture'})
                assert reply.status_code==401 and 'user_id' not in reply.json()
                cases.append('deployment_header_wrong_token_refused')
                reply=client.get('/api/auth/me',headers={'X-Hermes-Session-Token':native_access})
                assert reply.status_code==401
                cases.append('deployment_header_other_provider_token_refused')
                reply=client.get('/api/auth/me',headers={'Authorization':'Bearer invalid-fixture','X-Hermes-Session-Token':deployment})
                assert reply.status_code==401
                cases.append('invalid_bearer_cannot_fall_back_to_valid_deployment_header')
                reply=client.get('/api/auth/me',headers={**headers,'X-Hermes-Session-Token':'invalid-fixture'})
                assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
                cases.append('valid_native_bearer_precedes_deployment_header')
                reply=client.get('/api/auth/me',headers={'Cookie':'hermes_session_at=invalid-fixture','X-Hermes-Session-Token':deployment})
                assert reply.status_code==401
                cases.append('invalid_access_cookie_prevents_deployment_header_fallback')
                cookie='hermes_session_at='+native_access+'; hermes_session_provider=stub'
                reply=client.get('/api/auth/me',headers={'Cookie':cookie,'X-Hermes-Session-Token':deployment})
                assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
                cases.append('valid_cookie_identity_precedes_deployment_header')
                reply=client.get('/api/auth/me',headers={'Cookie':'hermes_session_rt=invalid-fixture','X-Hermes-Session-Token':deployment})
                assert reply.status_code==401
                client.cookies.clear()
                cases.append('refresh_cookie_prevents_deployment_header_fallback')
                clear_providers()
                for provider in original_providers:register_provider(provider)
                reply=client.get('/api/auth/me',headers={'X-Hermes-Session-Token':deployment})
                assert reply.status_code==401
                cases.append('deployment_header_requires_registered_session_token_provider')
                register_provider(deployment_provider)
                for label in ('same-site','cross-site'):
                    reply=client.post('/auth/logout',headers={'Cookie':cookie,'Sec-Fetch-Site':label})
                    assert reply.status_code==403 and reply.json()['detail']=="Cookie-authenticated writes must come from this dashboard's own page." and 'set-cookie' not in reply.headers
                    cases.append('cookie_logout_'+label+'_fetch_metadata_refused')
                reply=client.post('/auth/logout',headers=[('Cookie',cookie),('Sec-Fetch-Site','same-origin'),('Sec-Fetch-Site','cross-site')])
                assert reply.status_code==403
                cases.append('duplicate_fetch_metadata_cannot_hide_cross_site_cookie_write')
                reply=client.get('/api/auth/me',headers={'Cookie':cookie,'Sec-Fetch-Site':'cross-site'})
                assert reply.status_code==200 and reply.json()['user_id']=='stub-user-1'
                cases.append('safe_cross_site_cookie_read_retains_verified_identity')
                reply=client.post('/auth/logout',headers={'Cookie':cookie,'Sec-Fetch-Site':'same-origin'})
                assert reply.status_code==302 and reply.headers['location']=='/login' and 'hermes_session_at=' in reply.headers.get('set-cookie','')
                client.cookies.clear()
                cases.append('same_origin_cookie_logout_keeps_normal_clear_redirect')
                reply=client.post('/auth/logout',headers={'Cookie':cookie})
                assert reply.status_code==302 and reply.headers['location']=='/login'
                client.cookies.clear()
                cases.append('non_browser_proxy_cookie_logout_without_fetch_metadata_preserved')
                web_server.app.state.ui_surface='webapp'
                reply=client.get('/api/auth/me',headers=headers)
                assert reply.status_code==401
                cases.append('webapp_unrecorded_provider_bearer_refused')
                reply=client.get('/api/auth/me',headers={'X-Hermes-Session-Token':deployment})
                assert reply.status_code==200 and reply.json()['user_id']=='deployment-token' and 'set-cookie' not in reply.headers
                cases.append('webapp_registered_deployment_header_verifies_without_browser_binding')
                reply=client.get('/api/auth/me',headers={'Authorization':'Bearer '+deployment})
                assert reply.status_code==200 and reply.json()['user_id']=='deployment-token'
                cases.append('webapp_registered_deployment_bearer_verifies_without_browser_binding')
                browser_token=browser_credential.current()
                assert browser_token!=deployment
                reply=client.get('/api/auth/me',headers={'X-Hermes-Session-Token':browser_token})
                assert reply.status_code==401
                cases.append('webapp_browser_credential_cannot_replace_deployment_header')
                reply=client.post('/auth/native/refresh',json=dict(refresh_token=deployment,provider='session-token'))
                assert reply.status_code==401 and reply.json()['error']=='session_expired'
                cases.append('deployment_provider_cannot_mint_native_refresh_session')
            finally:
                client.cookies.clear()
                clear_providers()
                for provider in original_providers:register_provider(provider)
                web_server.app.state.ui_surface=original_surface


    if a.revision == 'stable-3b42157':
        # Execute the real loader helpers with an explicitly synthetic module body.
        # Python's import machinery/lock and asyncio worker handoff remain real;
        # this does not exercise tui_gateway.ws's downstream handler or side effects.
        import asyncio,importlib.abc,importlib.util,threading,types
        import gateway.run_api as loader_api
        socket_name=loader_api._SESSION_SOCKET_MODULE
        assert socket_name=='tui_gateway.ws' and socket_name not in sys.modules
        parent=__import__('tui_gateway');old_child=getattr(parent,'ws',None)
        assert old_child is None
        async def synthetic_handle(*args,**kwargs):raise AssertionError('Synthetic handler must not execute')
        class FixtureImportFailure(RuntimeError):pass
        class SocketModuleLoader(importlib.abc.MetaPathFinder,importlib.abc.Loader):
            def __init__(self):self.entered=threading.Event();self.release=threading.Event();self.threads=[];self.modules=[];self.fail=False
            def reset(self):
                old=sys.modules.pop(socket_name,None)
                assert old is None or old in self.modules
                if hasattr(parent,'ws'):
                    assert parent.ws in self.modules;del parent.ws
                self.entered=threading.Event();self.release=threading.Event();self.fail=False
            def find_spec(self,fullname,path=None,target=None):
                if fullname==socket_name:return importlib.util.spec_from_loader(fullname,self)
                return None
            def create_module(self,spec):return None
            def exec_module(self,module):
                self.modules.append(module);self.threads.append(threading.get_ident());self.entered.set()
                assert self.release.wait(5),'Synthetic import release deadline'
                if self.fail:raise FixtureImportFailure('Synthetic import failure; no handler executed')
                module.handle_ws=synthetic_handle
        importer=SocketModuleLoader();sys.meta_path.insert(0,importer)
        try:
            async def real_loader_cases():
                loop_thread=threading.get_ident();task=None;attach=None
                try:
                    task=loader_api.preload_session_socket();assert isinstance(task,asyncio.Task) and task.get_name()=='gateway-ws-preload'
                    assert await asyncio.wait_for(asyncio.to_thread(importer.entered.wait,3),4)
                    assert importer.threads==[importer.threads[0]] and importer.threads[0]!=loop_thread and not task.done()
                    cases.append('real_preload_helper_imports_synthetic_module_off_loop')
                    await loader_api.await_session_socket_preload(task,timeout=0.01)
                    assert not task.done() and not task.cancelled()
                    cases.append('real_preload_timeout_preserves_shared_import_task')
                    attach=asyncio.create_task(loader_api.session_socket_handler())
                    # A concurrent attach must wait on the genuine Python import lock
                    # in another worker, keeping the event loop responsive.
                    for _ in range(3):await asyncio.sleep(0.01);assert not attach.done()
                    importer.release.set();await asyncio.wait_for(task,3)
                    assert await asyncio.wait_for(attach,3) is synthetic_handle
                    assert len(importer.threads)==1
                    cases.append('real_handler_helper_waits_for_preload_without_blocking_loop')
                    assert loader_api.preload_session_socket() is None
                    await loader_api.await_session_socket_preload(None)
                    assert await loader_api.session_socket_handler() is synthetic_handle and len(importer.threads)==1
                    cases.append('real_cached_preload_fast_path_does_not_reimport')
                finally:
                    importer.release.set()
                    for pending in (task,attach):
                        if pending is not None:
                            try:await asyncio.wait_for(asyncio.shield(pending),4)
                            except FixtureImportFailure:pass
                importer.reset();importer.fail=True;importer.release.set()
                failed=loader_api.preload_session_socket();await loader_api.await_session_socket_preload(failed,timeout=3)
                assert failed.done() and isinstance(failed.exception(),FixtureImportFailure) and socket_name not in sys.modules
                importer.fail=False
                assert await loader_api.session_socket_handler() is synthetic_handle
                assert len(importer.threads)==3
                cases.append('real_failed_preload_is_consumed_and_explicit_attach_can_load')
            asyncio.run(real_loader_cases())
        finally:
            importer.release.set();importer.reset();sys.meta_path.remove(importer)
            assert socket_name not in sys.modules and not hasattr(parent,'ws')

        # Exercise the actual public startup wrapper and real PM suppression.
        # The runtime body and installer are explicit stopping adapters: no
        # listener, package operation or inherited TTY input may run here.
        import builtins,concurrent.futures
        from pm import extras as startup_extras,client as pm_client
        assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==0
        original_runtime=web_server._start_server_runtime
        class StartupFixtureStop(RuntimeError):pass
        class PromptFixtureWitness(RuntimeError):pass
        def worker_suppression():
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                return pool.submit(startup_extras.interactive_install_prompts_suppressed).result(timeout=3)
        try:
            seen=[];value=object();argument=object()
            def runtime_success(*args,**kwargs):
                assert startup_extras.interactive_install_prompts_suppressed() and worker_suppression()
                seen.append((args,kwargs));return value
            web_server._start_server_runtime=runtime_success
            assert web_server.start_server(argument,port=0,open_browser=False) is value
            assert seen==[((argument,),{'port':0,'open_browser':False})] and startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==0
            cases.append('actual_start_entry_forwards_values_with_worker_visible_pm_suppression')
            def runtime_failure(*args,**kwargs):
                assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==1
                raise StartupFixtureStop('Synthetic runtime stopped before bind')
            web_server._start_server_runtime=runtime_failure
            try:web_server.start_server()
            except StartupFixtureStop:pass
            else:raise AssertionError('Startup exception was swallowed')
            assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==0
            cases.append('actual_start_entry_restores_pm_scope_after_runtime_error')
            with startup_extras.suppress_interactive_install_prompts():
                assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==1
                def nested_runtime():
                    assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==2 and worker_suppression()
                web_server._start_server_runtime=nested_runtime;web_server.start_server()
                assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==1
            assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==0
            cases.append('actual_start_entry_preserves_nested_process_wide_pm_scope')
            previous_available=startup_extras.available;previous_supported=startup_extras.extra_supported
            previous_sync=pm_client.sync_venv;previous_input=builtins.input;previous_in=sys.stdin;previous_out=sys.stdout
            prompt_calls=[];install_calls=[]
            class TtyStream:
                def __init__(self,stream):self.stream=stream
                def isatty(self):return True
                def __getattr__(self,key):return getattr(self.stream,key)
            def stopped_install(extras,*args,**kwargs):
                assert extras==['signal-grove-synthetic-absent-extra'];install_calls.append(tuple(extras))
                raise StartupFixtureStop('Synthetic installer stops before any package operation')
            def witnessed_prompt(*args,**kwargs):
                prompt_calls.append('synthetic-tty-prompt');raise PromptFixtureWitness('Input adapter; no actual stdin read')
            try:
                startup_extras.available=lambda extra:False
                startup_extras.extra_supported=lambda extra:True
                pm_client.sync_venv=stopped_install;builtins.input=witnessed_prompt
                sys.stdin=TtyStream(previous_in);sys.stdout=TtyStream(previous_out)
                web_server._start_server_runtime=lambda:startup_extras.ensure_import('signal-grove-synthetic-absent-extra')
                try:web_server.start_server()
                except StartupFixtureStop:pass
                else:raise AssertionError('Synthetic install boundary was not reached')
                assert not prompt_calls and len(install_calls)==1 and startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==0
                cases.append('actual_start_entry_suppresses_tty_prompt_before_synthetic_install_refusal')
                try:startup_extras.ensure_import('signal-grove-synthetic-absent-extra')
                except PromptFixtureWitness:pass
                else:raise AssertionError('Unscoped interactive contrast did not reach prompt adapter')
                assert prompt_calls==['synthetic-tty-prompt'] and len(install_calls)==1
                cases.append('actual_pm_unscoped_tty_contrast_prompts_without_installing')
            finally:
                sys.stdin=previous_in;sys.stdout=previous_out;builtins.input=previous_input
                startup_extras.available=previous_available;startup_extras.extra_supported=previous_supported;pm_client.sync_venv=previous_sync
        finally:
            web_server._start_server_runtime=original_runtime
            assert startup_extras._INTERACTIVE_INSTALL_PROMPT_SUPPRESSIONS==0
        report['loaderStartupFixture']=dict(realLoaderHelpers=True,realPythonImportLock=True,syntheticImportedModuleBody=True,realStartEntryAndPmSuppression=True,syntheticRuntimeBody=True,syntheticInstallerStop=True,actualPackageOperations=0,actualStdinReads=0,actualListenersStarted=0)


    for profile,home in homes.items():
        scope_token=hc.set_hermes_home_override(str(home))
        try:
            db=SessionDB(db_path=home/'state.db',read_only=True)
            try:after={sid:(db.get_session(sid),db.get_messages(sid)) for sid in ('shared-fixture',profile+'-only-fixture')}
            finally:db.close()
        finally:hc.reset_hermes_home_override(scope_token)
        assert after==original[profile]
    cases.append('browsing_preserves_exact_seeded_sessions_and_messages')

    assert len(cases)==len(set(cases))==(87 if a.revision=='stable-3b42157' else 52 if a.revision=='main-071d31f' else 21)
    assert not denied,denied
    report.update(state='PASS_REAL_DASHBOARD_NATIVE_AUTH_AND_SCOPED_HISTORY')
except BaseException as error:
    report.update(state='FAILED_INSPECT_EXACT_PILOT',errorType=type(error).__name__)
    (root/'traceback-private.txt').write_text(traceback.format_exc(),encoding='utf-8')
finally:
    modules={}
    for name,module in list(sys.modules.items()):
        value=getattr(module,'__file__',None)
        if value:
            path=Path(value).resolve()
            if path.is_relative_to(source) and path.is_file():modules[path.relative_to(source).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    report.update(revision=a.revision,sourceCommit=SOURCE_COMMIT,cases=cases,projectModules=modules,deniedEvents=denied,denialDetails=denial_details,expectedBlockedMetadataProbes=expected_blocked_metadata_probes,missingOsProbeReads=missing_os_probe_reads,ownedSocketEvents=owned_socket_events,scope=('Actual stable dashboard ASGI/auth/profile history, deployment-header/cookie refusal, async admission/client accounting, real loader helpers/import lock and public start-entry/PM suppression. Synthetic IdP, captured peer, downstream handler/authority, imported socket-module body, runtime body and stopping installer/input adapters; no actual package operation, stdin read, live listener, daemon/private control channel/TCP capture, full canonical socket, Android, Pi, real IdP or inference certification. Audit hooks are not an OS sandbox.' if a.revision=='stable-3b42157' else 'Actual dashboard ASGI/auth/profile history, private-grant middleware/TicketStore, socket-routing boundary and saved-gateway callback/store. Synthetic IdP, captured-peer adapter, refusing downstream socket gate, authority records and outbound token response. No daemon/private control channel/TCP capture, remote accepted socket, Android, Pi, real IdP or inference certification. Audit hooks are not an OS sandbox.' if a.revision=='main-071d31f' else 'Actual dashboard ASGI/lifespan/auth/profile history with an upstream synthetic HMAC provider. No Android, Pi, real IdP or inference certification. Audit hooks are not an OS sandbox.'))
    (root/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(state=report['state'],cases=cases,modules=len(modules),deniedEvents=denied,errorType=report.get('errorType'))))
    if report['state'].startswith('FAILED'):raise SystemExit(1)
