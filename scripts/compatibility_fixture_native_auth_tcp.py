"""Serve a bounded pinned Hermes TCP fixture for native auth/project client conformance.

Windows only: existing interpreter, -I -S -B, fresh E fixture, synthetic upstream
IdP. This temporary test listener is not an application backend or deployment.
It binds only an owned ephemeral loopback socket and stops within10..600 seconds.
See docs/contracts/2026-11-07-native-management-tcp.md and
docs/contracts/2026-11-07-project-gateway-client.md for controls and limits.
The projects surface adds authenticated legacy project reads, never agent turns.
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

REVISIONS = {'stable-2bd378e': {'commit': '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19',
                    'blobs': {'hermes_cli/web_server.py': '22ac93e69cae5a8c2a0e045cc9944f8416107e18',
                              'hermes_cli/dashboard_auth/__init__.py': 'f52f683894b4272e85565f8b2778b4b77b170b73',
                              'hermes_cli/dashboard_auth/base.py': 'b8e71461adf30b4814634cb3fbcfdf9b49488e33',
                              'hermes_cli/dashboard_auth/routes.py': '97dfc55f91130da8924ff497b4560d9d5305b00e',
                              'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960',
                              'hermes_cli/dashboard_auth/middleware.py': '5dc4c382ef9d93557c5eb022a9fa5f0737bdbc6a',
                              'hermes_cli/dashboard_auth/cookies.py': 'd6e5bb541594277a99d7e8f3d71fd43b68b717bc',
                              'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a',
                              'hermes_cli/dashboard_auth/request_utils.py': 'aca6ad79247b6e66fc4dae99de19da1911c96543',
                              'hermes_cli/dashboard_auth/prefix.py': 'c8b86dc8b8e55415c4c7069d7c5993d025c627bb',
                              'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324',
                              'hermes_cli/dashboard_auth/registry.py': 'ae0d377f8a308e4a9550f99817cf59c277e8876a',
                              'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599',
                              'hermes_cli/version_info.py': '258d67b4df3bb18bedd7c77e2bd539c73043baa1',
                              'hermes_cli/source_check.py': '6d2f267d5fa59f982c142f2abd8c0711b5a2e29c',
                              'hermes_cli/_subprocess_compat.py': 'a2f985af6197eb80ba55b8f45d3c4fea8e3c4eff',
                              'hermes_cli/web_routers/sessions.py': 'df2fe8084547a3c0cfc87ff8e6cc1dd5c496902e',
                              'hermes_cli/web_routers/profiles.py': 'dedfdcb871aa06b621310321301a658871ded80c',
                              'hermes_cli/web_server_sessions.py': '650ba6470402f763314aab7145fcbc8afd8a8a8f',
                              'hermes_cli/web_server_cron.py': 'b777f511a8786d8c7ea788bc93276d58546f6b48',
                              'hermes_cli/profiles.py': '622077d545b3dd322ba9e967a3e8620b0b18e278',
                              'hermes_state.py': '6a7ae615405ee714c0a87f83835408e36bd645ea',
                              'hermes_state_sessions.py': 'f3fc4c2854367bc1a61786406090b71bdccf28f6',
                              'hermes_cli/web_routers/mcp.py': 'fefae2dc77afbd4373a68f6fbbe81b746222a21a',
                              'hermes_cli/web_server_mcp.py': 'be974e229bb00c929f04c1f281d2df05f3ce1984',
                              'hermes_cli/mcp_config.py': '3bb246863c39569492b5718c8f65a1a0cce07c05',
                              'tui_gateway/mcp_rpc_helpers.py': '162973eb3d3e74153eeec65585bb1003e2db2066',
                              'hermes_cli/projects_db.py': '39bfc043e85ce96ee74a27370d03daf5914424c6',
                              'hermes_cli/sqlite_util.py': '952a49b9a72420157e0e97223da510c0068d0ca3',
                              'hermes_cli/web_routers/chat_ws.py': 'fc1bf403f7f75eed7e1bb47e0fdf313817bd410d',
                              'hermes_cli/web_server_chat.py': '12a34123035b703191de02045972070f534904dd',
                              'hermes_cli/web_server_boundary.py': 'd4aeb4e044f148e9d873309220059956c020cc86',
                              'hermes_cli/dashboard_auth/ws_tickets.py': 'e7b1098c7308d431aa03309c071700dcdd9cd3b2',
                              'tui_gateway/ws.py': 'b928d8a158180fc546da3ae5281e96080e70e79c',
                              'tui_gateway/server.py': '0a8b9ba486d00a84a071290b87b83f3092b33eab',
                              'tui_gateway/rpc_dispatch.py': '4197945a90eb61d94a62fd693d3c6810046312e8',
                              'tui_gateway/method_ctx.py': '593b5e3ed83e06af03ac93a42777776bcf59fb7c',
                              'tui_gateway/methods_projects.py': 'ca073cfeab027f41fe7d81cba503d9b18c1767dd',
                              'tui_gateway/contracts/projects_pets.py': '3b4283b4d41df49430bc50fbbdc087f531568a8b',
                              'tui_gateway/session_reaper.py': 'c4caa6035fd39e672f3e72f3b52e84303db51a99',
                              'tui_gateway/change_watcher.py': '26b0be489ab0700c884897bca965b584e4d0e5be',
                              'tui_gateway/methods_display_watch.py': '4a17a39d79f612e23516fa0f71a96e062ec32e13',
                              'tui_gateway/launch_profile_policy.py': '1127824ac984e8e65895871a9355f5d2f9261cec'}},
 'prior-bf0203a': {'commit': 'bf0203a794a07aac3ff764972ab9087baa4e4f4e',
                   'blobs': {'hermes_cli/web_server.py': '59700ba32c525efcc2b4039401f1b031b6c675c6',
                             'hermes_cli/dashboard_auth/__init__.py': '05941a68bdd3bcdcca49a0fe0eeee9e0018cb840',
                             'hermes_cli/dashboard_auth/base.py': '9e8c7a6b0c9d7b939b92c44cc8a92f497b0bcc86',
                             'hermes_cli/dashboard_auth/routes.py': 'be029a317fb45edfa9d4f66f16c33dff856e7c48',
                             'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960',
                             'hermes_cli/dashboard_auth/middleware.py': '2f360140b2e94df6457d33ba1d9d977463d545ef',
                             'hermes_cli/dashboard_auth/cookies.py': '1a52c8b16db105a514a36b3b38ee14d9c23232de',
                             'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a',
                             'hermes_cli/dashboard_auth/request_utils.py': '2eca3d145c8cba33e974604e3308a0caa0339922',
                             'hermes_cli/dashboard_auth/prefix.py': '1e61b5a72d5fef542532544890e0b4bd748fac74',
                             'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324',
                             'hermes_cli/dashboard_auth/registry.py': '18687691f8b79e551db1565b613e60fcda8afd35',
                             'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599',
                             'hermes_cli/version_info.py': 'a4b3b33b9fe7341a7ed6e927afaf24bb391d9988',
                             'hermes_cli/source_check.py': '5a67a576988ccaf49e1b0192de12606232a86a37',
                             'hermes_cli/_subprocess_compat.py': '8ec065c638016983089e6b174c89951e9e2e93a3',
                             'hermes_cli/web_routers/sessions.py': 'd6a5cb947a14855c260754307bcf7698fe520da2',
                             'hermes_cli/web_routers/profiles.py': '8c60f23c4c52117558ebcccf941c27ce86ca3803',
                             'hermes_cli/web_server_sessions.py': '5d2d82cb97003fc87116dd115a821538a845194d',
                             'hermes_cli/web_server_cron.py': '761693b5d8e9a9e64606da49784be7879bfdeb42',
                             'hermes_cli/profiles.py': '08b7eb1057f21f91175dc19074a98c24bd515def',
                             'hermes_state.py': '833f4b738f4698034b7da22276fbcf7bfd3f9768',
                             'hermes_state_sessions.py': 'a072ea5eec5bb9c74491052676e004c16eb2451a',
                             'hermes_cli/web_routers/mcp.py': 'dfbcb32f5949c64a29ab7bcc988edeedf42d8ac3',
                             'hermes_cli/web_server_mcp.py': 'be974e229bb00c929f04c1f281d2df05f3ce1984',
                             'hermes_cli/mcp_config.py': '3bb246863c39569492b5718c8f65a1a0cce07c05',
                             'tui_gateway/mcp_rpc_helpers.py': '162973eb3d3e74153eeec65585bb1003e2db2066',
                             'hermes_cli/projects_db.py': '39bfc043e85ce96ee74a27370d03daf5914424c6',
                             'hermes_cli/sqlite_util.py': '952a49b9a72420157e0e97223da510c0068d0ca3',
                             'hermes_cli/web_routers/chat_ws.py': '966754b47a3bd365c8332d7a64fa2515a65738ab',
                             'hermes_cli/web_server_chat.py': 'b614133ae756086c615096b6d71953e29f2ca4c5',
                             'hermes_cli/web_server_boundary.py': '80a1eea39e4639fe9506823e2fbffb781fb13bb0',
                             'hermes_cli/dashboard_auth/ws_tickets.py': 'e7b1098c7308d431aa03309c071700dcdd9cd3b2',
                             'tui_gateway/ws.py': '7d7cb5a0ff1de0d0b43c05d5160e17b9dff8dd5e',
                             'tui_gateway/server.py': 'b9ab08d4b480137f1194aa37831f51e4dfc915f8',
                             'tui_gateway/rpc_dispatch.py': 'b2a2587c42dab0b09a24b66e842910f36abfb539',
                             'tui_gateway/method_ctx.py': '593b5e3ed83e06af03ac93a42777776bcf59fb7c',
                             'tui_gateway/methods_projects.py': 'ca073cfeab027f41fe7d81cba503d9b18c1767dd',
                             'tui_gateway/contracts/projects_pets.py': '2f17d6b9cdc6fae78875454a9971f64f2f50ad67',
                             'tui_gateway/session_reaper.py': 'e156a3f111f16fcd3080117a69fb0cf73ca5b0c7',
                             'tui_gateway/change_watcher.py': '26b0be489ab0700c884897bca965b584e4d0e5be',
                             'tui_gateway/methods_display_watch.py': '4a17a39d79f612e23516fa0f71a96e062ec32e13',
                             'tui_gateway/launch_profile_policy.py': '1127824ac984e8e65895871a9355f5d2f9261cec'}}}
REVISIONS['main-21ae0e1'] = {'commit': '21ae0e1238fdf466fa52a85a6ebf75d6899e49da',
 'blobs': {'gateway/owner_identity.py': 'bffdf405527e53f3bd1b608c88d609b1dab34803',
           'gateway/session_authority.py': '052252b70edc38b71b6a8679f91660d6009893aa',
           'gateway/session_contract.py': 'accd1d9e2109e34c19263b90d730fad400128e6c',
           'gateway/session_controls.py': '92c19bbfed5f4bb21a3520f9172b0248180a3731',
           'gateway/session_local_migration.py': '82a647465420180829beeb081a6271bcdc48272f',
           'gateway/session_local_recovery.py': '9b00f41786c47fef23500a9eca70f60c667ad9d4',
           'gateway/session_picker_fields.py': '9bbbc5f41500083e74bbda532c5e66aa49622ef0',
           'gateway/session_plugin_rpc.py': 'a1bc32a566f31254e7b9d761d57da7f00bb7d1b0',
           'gateway/session_policy.py': '502eed0642e1ac5062df880240071844d6754ffa',
           'gateway/session_skin.py': 'abb696b5c959ab01ba91fa0d5d0ab6f6b4076151',
           'gateway/session_stored_reads.py': 'f6f454a0a2492cd349f211c1ef77fd6374e1514e',
           'gateway/session_transcript_rows.py': 'e3cacd910e2d5c946704404b98f573bb86ec82bc',
           'hermes_cli/_subprocess_compat.py': 'a2f985af6197eb80ba55b8f45d3c4fea8e3c4eff',
           'hermes_cli/dashboard_auth/__init__.py': 'f52f683894b4272e85565f8b2778b4b77b170b73',
           'hermes_cli/dashboard_auth/base.py': '6ddeb91fe8efbbde7c1274db37f761cee4e7f4ae',
           'hermes_cli/dashboard_auth/cookies.py': 'd6e5bb541594277a99d7e8f3d71fd43b68b717bc',
           'hermes_cli/dashboard_auth/middleware.py': '29e8ee32381fc976f93282776c08a9151cb05eba',
           'hermes_cli/dashboard_auth/native_flow.py': 'f9483c8bea04c046dab89907108b2a42132bd960',
           'hermes_cli/dashboard_auth/prefix.py': 'c8b86dc8b8e55415c4c7069d7c5993d025c627bb',
           'hermes_cli/dashboard_auth/public_paths.py': 'a4793ab7e815b584869231b501806070f0485324',
           'hermes_cli/dashboard_auth/refresh_singleflight.py': '6d94bd44201237dd56075cd0a1c50e1c12c9428a',
           'hermes_cli/dashboard_auth/registry.py': '082f3731e315bc20f5331aec175af8bac34d17c5',
           'hermes_cli/dashboard_auth/request_utils.py': 'aca6ad79247b6e66fc4dae99de19da1911c96543',
           'hermes_cli/dashboard_auth/routes.py': '97dfc55f91130da8924ff497b4560d9d5305b00e',
           'hermes_cli/dashboard_auth/ws_tickets.py': 'e7b1098c7308d431aa03309c071700dcdd9cd3b2',
           'hermes_cli/mcp_config.py': '3bb246863c39569492b5718c8f65a1a0cce07c05',
           'hermes_cli/profiles.py': '15a147c862ca6d60ab5175eb79efb569d5361772',
           'hermes_cli/projects_db.py': '39bfc043e85ce96ee74a27370d03daf5914424c6',
           'hermes_cli/source_check.py': '5bc247aea6c67af9b6eccdfbd2bbd455401530fc',
           'hermes_cli/sqlite_util.py': '952a49b9a72420157e0e97223da510c0068d0ca3',
           'hermes_cli/version_info.py': '13aaee3631162f5c4d568d636e2567ec75b9dfd9',
           'hermes_cli/web_routers/chat_ws.py': 'fc1bf403f7f75eed7e1bb47e0fdf313817bd410d',
           'hermes_cli/web_routers/mcp.py': 'fefae2dc77afbd4373a68f6fbbe81b746222a21a',
           'hermes_cli/web_routers/profiles.py': 'dedfdcb871aa06b621310321301a658871ded80c',
           'hermes_cli/web_routers/sessions.py': 'be61ae22c64628038396fbc135759f296889bd39',
           'hermes_cli/web_server.py': '7001789f657d68778d826c1bc9e84be27969ee37',
           'hermes_cli/web_server_boundary.py': 'd4aeb4e044f148e9d873309220059956c020cc86',
           'hermes_cli/web_server_chat.py': '9e0ebe7491a7b5a222345ba9a676bc86aa4ffd00',
           'hermes_cli/web_server_cron.py': 'b777f511a8786d8c7ea788bc93276d58546f6b48',
           'hermes_cli/web_server_mcp.py': 'be974e229bb00c929f04c1f281d2df05f3ce1984',
           'hermes_cli/web_server_sessions.py': '650ba6470402f763314aab7145fcbc8afd8a8a8f',
           'hermes_state.py': 'fe0249d6804ac5e6810a208f7a32e6dcf3e251f9',
           'hermes_state_local.py': 'fd39d4c1bf10c36b5aa06664950dfa7b88d96ae6',
           'hermes_state_local_lineage.py': 'd6c26730b277d97c50298fde75f1e5d9f96f5d8b',
           'hermes_state_runtime.py': '49207f5508db3255cd90433a229c518f11cfd0f7',
           'hermes_state_sessions.py': '476b3ef51d4304f3ef57d35576e2b98e73ca56fc',
           'tests/hermes_cli/conftest_dashboard_auth.py': 'f06ec93f722b26530b69d81c8fed0edf870a1599',
           'tui_gateway/change_watcher.py': '26b0be489ab0700c884897bca965b584e4d0e5be',
           'tui_gateway/contracts/canonical.py': '90295a8a14ce51e245497539e1f20bb694628fb8',
           'tui_gateway/contracts/canonical_sessions.py': 'e3569203f6536a9c86c6dcaa9c95ae6b1403864f',
           'tui_gateway/contracts/events.py': '4fc6964c1fbcc240768200f5fd121643e222daee',
           'tui_gateway/contracts/projects_pets.py': '3b4283b4d41df49430bc50fbbdc087f531568a8b',
           'tui_gateway/launch_profile_policy.py': '1127824ac984e8e65895871a9355f5d2f9261cec',
           'tui_gateway/mcp_rpc_helpers.py': '162973eb3d3e74153eeec65585bb1003e2db2066',
           'tui_gateway/method_ctx.py': '593b5e3ed83e06af03ac93a42777776bcf59fb7c',
           'tui_gateway/methods_display_watch.py': '4a17a39d79f612e23516fa0f71a96e062ec32e13',
           'tui_gateway/methods_projects.py': 'ca073cfeab027f41fe7d81cba503d9b18c1767dd',
           'tui_gateway/rpc_dispatch.py': '1f8d451f237b7b30cbeffa6c39324d2129128c6e',
           'tui_gateway/server.py': '4a845d3da35836b83d448c54c8bbb8677e39bf83',
           'tui_gateway/session_reaper.py': 'c4caa6035fd39e672f3e72f3b52e84303db51a99',
           'tui_gateway/ws.py': '833751e0df038b0c551814ee3bfea95a27d0dabf'}}
if not __debug__ or not (sys.flags.isolated and sys.flags.no_site and sys.flags.dont_write_bytecode):
    raise SystemExit('Use -I -S -B without -O; assertions and isolated imports are required.')
if os.name != 'nt':
    raise SystemExit('This bounded probe currently supports Windows only; no cross-host acceptance is claimed.')
def canonical_fixture_rpc_allowed(method, params, count, allow_heartbeat=False):
    """Finite synthetic-only canonical viewer surface; never admits tools/work."""
    if type(count) is not int or not 0 <= count < 8 or not isinstance(params, dict):
        return False
    if method == 'gateway.ping':
        return allow_heartbeat is True and params == {}
    if method == 'runtime.describe':
        return params == {}
    if method == 'session.list':
        return (set(params) == {'scope', 'limit'} and params['scope'] in ('stored', 'live')
                and type(params['limit']) is int and params['limit'] == 200)
    if method == 'session.resume':
        return params == {'session_id': 'shared-fixture', 'omit_messages': True}
    if method == 'session.events.since':
        return (set(params) == {'session_id', 'last_sequence', 'replay_epoch'}
                and params['session_id'] == 'shared-fixture'
                and type(params['last_sequence']) is int and 0 <= params['last_sequence'] <= 2**63-1
                and isinstance(params['replay_epoch'], str) and 0 < len(params['replay_epoch']) <= 256
                and not any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in params['replay_epoch']))
    return False


def canonical_viewer_activity(phase):
    """Fixed synthetic observation payloads; never a turn, tool or execution admission."""
    if phase == 'opening':
        return [('status.update', {'kind':'status','text':'Fixture public status one'}),
                ('reasoning.delta', {'text':'fixture-private-reasoning-sentinel'}),
                ('message.delta', {'text':'Fixture public reply three'})]
    if phase == 'live':
        return [('status.update', {'kind':'status','text':'Fixture public live status four'})]
    raise ValueError('Unknown bounded fixture activity phase.')


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root', type=Path, required=True)
parser.add_argument('--work-root', type=Path, required=True)
parser.add_argument('--dependency-site', type=Path, required=True)
parser.add_argument('--revision', choices=tuple(REVISIONS), default='stable-2bd378e')
parser.add_argument('--lifetime-seconds',type=int,default=180)
parser.add_argument('--surface',choices=('native-auth','projects','canonical','canonical-session'),default='native-auth')
parser.add_argument('--allow-viewer-heartbeat',action='store_true',help='Allow bounded empty heartbeat RPCs on the synthetic canonical-session fixture only.')
parser.add_argument('--emit-viewer-activity',action='store_true',help='Emit four fixed observation frames only after reviewed synthetic adoption and its periodic heartbeat.')
a = parser.parse_args()
if a.emit_viewer_activity and (a.surface!='canonical-session' or not a.allow_viewer_heartbeat):parser.error('Synthetic viewer activity requires canonical-session and explicit heartbeat opt-in.')
if a.allow_viewer_heartbeat and a.surface!='canonical-session':parser.error('Viewer heartbeat requires the canonical-session surface.')
if not 10<=a.lifetime_seconds<=600:parser.error('Lifetime must be10..600 seconds.')
if a.surface in ('canonical','canonical-session'):
    # Negotiation consumes the actual authority dispatcher, never a runtime-description stub.
    if a.revision=='stable-2bd378e':REVISIONS[a.revision]['blobs'].update({'gateway/session_authority.py': '052252b70edc38b71b6a8679f91660d6009893aa', 'gateway/session_controls.py': '3e4a89ad890cacf9645b648369d3daa682c1e114', 'gateway/session_contract.py': 'accd1d9e2109e34c19263b90d730fad400128e6c', 'gateway/owner_identity.py': 'bffdf405527e53f3bd1b608c88d609b1dab34803', 'gateway/session_local_migration.py': '82a647465420180829beeb081a6271bcdc48272f', 'gateway/session_policy.py': 'ca1cac3b27c42ab22771dc93255a9d5cdb20b30b', 'gateway/session_skin.py': 'abb696b5c959ab01ba91fa0d5d0ab6f6b4076151', 'tui_gateway/contracts/canonical.py': '8976061235c34e286a7d475ce7db1283b42a6d6d', 'tui_gateway/contracts/canonical_sessions.py': '642364a17f0ec8a12c66c285d76064996b604f10', 'tui_gateway/contracts/events.py': '4fc6964c1fbcc240768200f5fd121643e222daee', 'hermes_state_runtime.py': '49207f5508db3255cd90433a229c518f11cfd0f7'})
    if a.revision=='prior-bf0203a':REVISIONS[a.revision]['blobs'].update({'gateway/session_authority.py': '3bc6c596c77c495c1cf318495a84319c211dd523', 'gateway/session_controls.py': '65887575dec12b3ddb9f859ebe56156264ea120e', 'gateway/session_contract.py': 'dda3763a395814455cdf0de080d62f352dfd4b52', 'gateway/session_local_migration.py': '484be549e64250e509bbaabe80b6ef9cc499c18e', 'gateway/session_policy.py': 'ab3aace258bce8c74b151553153b2bb3d554bf1d', 'gateway/session_skin.py': 'abb696b5c959ab01ba91fa0d5d0ab6f6b4076151', 'tui_gateway/contracts/canonical.py': '3a9dd88a19decfea9d4c9307b90c5e2849a2d0a0', 'tui_gateway/contracts/canonical_sessions.py': 'db394b039bca3a75a9ed4e06e0fcc7a121c3ae33', 'tui_gateway/contracts/events.py': 'd782b3e6eec5b6d624aad9716ada2d415c8178f4', 'hermes_state_runtime.py': '516e3ea46152226e320dc0b6236e6af0b2a00ccb'})
_CANONICAL_STREAM_PINS = {'2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19': {'gateway/session_events.py': '5aa8636025d83497b23dcad7cc24bfd0c9ca911d', 'tui_gateway/event_replay.py': '20298bd23f80922c02d7b45a3c920716021f290b', 'tui_gateway/transport.py': '691f1d799027b211a073c685fbd7ee5d65fae3a6', 'tui_gateway/contracts/events.py': '4fc6964c1fbcc240768200f5fd121643e222daee'}, '21ae0e1238fdf466fa52a85a6ebf75d6899e49da': {'gateway/session_events.py': '5aa8636025d83497b23dcad7cc24bfd0c9ca911d', 'tui_gateway/event_replay.py': '20298bd23f80922c02d7b45a3c920716021f290b', 'tui_gateway/transport.py': '691f1d799027b211a073c685fbd7ee5d65fae3a6', 'tui_gateway/contracts/events.py': '4fc6964c1fbcc240768200f5fd121643e222daee'}, 'bf0203a794a07aac3ff764972ab9087baa4e4f4e': {'gateway/session_events.py': 'f669acfb44e3acc17058e67981b4cf8569478451', 'tui_gateway/event_replay.py': 'd2f4ab26936a8f3f8bbe10ab27e83c863b65ee8a', 'tui_gateway/transport.py': '691f1d799027b211a073c685fbd7ee5d65fae3a6', 'tui_gateway/contracts/events.py': 'd782b3e6eec5b6d624aad9716ada2d415c8178f4'}}
if a.surface in ('canonical','canonical-session'):
    for stream_path,stream_blob in _CANONICAL_STREAM_PINS[REVISIONS[a.revision]['commit']].items():
        assert REVISIONS[a.revision]['blobs'].get(stream_path) in (None,stream_blob)
        REVISIONS[a.revision]['blobs'][stream_path]=stream_blob
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
sys.path[:0] = [str(source), str(site)]
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
    for _ in range(32):
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
        for _ in range(32):
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
for name in ('/proc/1/cgroup', '/proc/self/mountinfo', '/proc/version') + ((f'/proc/{os.getpid()}/stat', f'/proc/{os.getpid()}/cmdline') if a.surface=='canonical-session' else ()):
    target = Path(root.drive + name.replace('/', '\\'))
    assert target.resolve() == target and (not target.exists())
    missing_os_probes[name] = target
missing_manifest = source.parent / 'manifest.json'
assert missing_manifest.resolve() == missing_manifest and (not missing_manifest.exists())
missing_os_probes[str(missing_manifest)] = missing_manifest
# pathlib passes a Windows spelling for the same absent Linux self-stat probe.
if a.surface=='canonical-session':
    for leaf in ('stat','cmdline'):
        self_probe=f'/proc/{os.getpid()}/{leaf}'
        missing_os_probes[str(Path(self_probe))]=missing_os_probes[self_probe]
missing_os_probe_reads = []
bound_sockets = weakref.WeakSet()
owned_socket_events = []
cases = []
sys.addaudithook(audit)

try:
    import base64,time,urllib.parse,socket
    reserved_listener=None
    if a.surface=='canonical-session':
        reserved_listener=socket.socket(socket.AF_INET,socket.SOCK_STREAM)
        reserved_listener.bind(('127.0.0.1',0));reserved_listener.listen(64)
        fixture_port=reserved_listener.getsockname()[1]
    import hermes_constants as hc
    assert hc.get_hermes_home().resolve()==root/'hermes-home'

    from hermes_state import SessionDB
    homes={'default':root/'hermes-home','worker':root/'hermes-home/profiles/worker'}
    original={}
    for profile,home in homes.items():
        home.mkdir(parents=True,exist_ok=True);(home/'SOUL.md').write_text('Synthetic '+profile+' identity only.',encoding='utf-8')
        if a.surface=='canonical-session':
            (home/'config.yaml').write_text(('model:\n  default: fixture-model\n  provider: custom\n  base_url: http://127.0.0.1:1/v1\nplatform_toolsets:\n  cli: []\nsessions:\n  orphan_end_grace_s: 0\n').replace('http://127.0.0.1:1/v1',f'http://127.0.0.1:{fixture_port}/fixture-model/v1'),encoding='utf-8')
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


    if a.surface=='projects':
        # Seed real schema only; project RPCs in this fixture are reads.
        from hermes_cli import projects_db as pdb
        project_original={}
        def project_rows(conn):
            return {table:[tuple(row) for row in conn.execute('SELECT * FROM '+table+' ORDER BY '+order)] for table,order in [('projects','id'),('project_folders','project_id,path'),('project_meta','key'),('discovered_repos','root')]}
        for profile,home in homes.items():
            (home/'config.yaml').write_text(json.dumps({'dashboard':{'startup_orphan_sweep':False}}),encoding='utf-8')
            scope_token=hc.set_hermes_home_override(str(home))
            try:
                with pdb.connect_closing() as conn:
                    for identity,slug,title,part in [('fixture-shared-project','shared-fixture',profile.title()+' fixture project','shared'),(profile+'-only-project',profile+'-only',profile.title()+' only project','only')]:
                        folder=root/'project-files'/(profile+'-project-root')/part
                        primary=folder/'primary';primary.mkdir(parents=True)
                        conn.execute('INSERT INTO projects(id,slug,name,description,primary_path,created_at,archived) VALUES (?,?,?,?,?,?,?)',(identity,slug,title,profile+' synthetic description',str(primary),1700000000,0))
                        conn.execute('INSERT INTO project_folders(project_id,path,label,is_primary,added_at) VALUES (?,?,?,?,?)',(identity,str(primary),profile.title()+' primary folder',1,1700000000))
                        if part=='shared':
                            secondary=folder/'secondary';secondary.mkdir()
                            conn.execute('INSERT INTO project_folders(project_id,path,label,is_primary,added_at) VALUES (?,?,?,?,?)',(identity,str(secondary),profile.title()+' secondary folder',0,1700000001))
                    conn.execute('INSERT INTO project_meta(key,value) VALUES (?,?)',('active_id','fixture-shared-project'));conn.commit()
                    project_original[profile]=project_rows(conn)
            finally:hc.reset_hermes_home_override(scope_token)

    from hermes_cli import web_server
    from hermes_cli.dashboard_auth import clear_providers,register_provider,native_flow
    from tests.hermes_cli.conftest_dashboard_auth import StubAuthProvider

    import asyncio,socket,uvicorn
    clear_providers();register_provider(StubAuthProvider());native_flow._reset_for_tests()
    web_server.app.state.bound_host='127.0.0.1';web_server.app.state.auth_required=True
    if a.surface=='projects':
        from tui_gateway import server as actual_project_server
    canonical_dbs=[];canonical_authority=None;runtime_replies=[]
    if a.surface in ('canonical','canonical-session'):
        from types import SimpleNamespace
        from gateway.session_authority import SessionAuthority
        from hermes_state_runtime import begin_runtime_epoch
        runner=SimpleNamespace(_draining=False,_cached_agent_for=lambda route:None,adapters={})
        if a.surface=='canonical-session':
            from gateway.session import SessionStore
            from gateway.config import GatewayConfig
            store=SessionStore(homes['default']/'sessions',GatewayConfig())
            runner.session_store=store;runner._session_db=store._db
            runner._adapter_for_source=lambda source:None;runner._evict_cached_agent=lambda route:None
        db=SessionDB(db_path=homes['default']/'state.db');canonical_dbs.append(db)
        epoch=begin_runtime_epoch(db,instance_id='fixture-native-default')
        canonical_authority=SessionAuthority(runner,profile_id=str(homes['default']),instance_id='fixture-native-default',db=db,epoch=epoch)
        web_server.app.state.session_authority=canonical_authority
    requests=[]
    emitted_activity=[]
    def emit_viewer_activity(phase):
        assert a.emit_viewer_activity and a.allow_viewer_heartbeat and a.surface=='canonical-session'
        assert canonical_authority is not None and 'shared-fixture' in canonical_authority.sessions
        from tui_gateway.contracts.events import StatusUpdatePayload, StreamDeltaPayload
        stream=canonical_authority.sessions['shared-fixture'].event_stream
        with stream.lock:
            assert stream.sequence==(0 if phase=='opening' else 3)
            for event_type,payload in canonical_viewer_activity(phase):
                model=StatusUpdatePayload if event_type=='status.update' else StreamDeltaPayload
                model.model_validate(payload)
                assert stream.publish('shared-fixture',payload,event_type=event_type) is True
                emitted_activity.append(dict(phase=phase,eventType=event_type,sequence=stream.sequence,
                    payloadSha256=hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()))
    allowed={('GET','/auth/native/authorize'),('GET','/auth/callback'),('POST','/auth/native/token'),('POST','/auth/native/refresh'),('GET','/api/auth/me'),('GET','/api/profiles'),('GET','/api/mcp/servers')}
    if a.surface in ('projects','canonical','canonical-session'):allowed.add(('POST','/api/auth/ws-ticket'))
    async def proxy(scope,receive,send):
        if scope['type']=='websocket':
            path=scope['path'];headers=list(scope['headers'])
            if a.surface not in ('projects','canonical','canonical-session') or path!='/dashboard/api/ws' or scope.get('query_string') or len(requests)>=128:
                requests.append(dict(method='WS',path='unreviewed',status=404));await send(dict(type='websocket.close',code=1008));return
            entry=dict(method='WS',path='/api/ws',bearer=any(k.lower()==b'authorization' for k,v in headers),cookie=any(k.lower()==b'cookie' for k,v in headers),origin=any(k.lower()==b'origin' for k,v in headers),ticketPresented=any(k.lower()==b'sec-websocket-protocol' and b'hermes-gateway-ticket.' in v for k,v in headers))
            requests.append(entry)
            forwarded=dict(scope,path='/api/ws',raw_path=b'/api/ws',root_path='',headers=[(k,v) for k,v in headers if k.lower()!=b'x-forwarded-prefix']+[(b'x-forwarded-prefix',b'/dashboard')])
            rpc_count=0
            rpc_methods={}
            opening_activity_ready=asyncio.Event()
            connection_pings=0
            opening_activity_sent=False
            live_activity_sent=False
            async def bounded_receive():
                nonlocal rpc_count,connection_pings,live_activity_sent
                message=await receive()
                if message['type']=='websocket.receive':
                    try:
                        raw=message.get('text');assert isinstance(raw,str) and len(raw.encode('utf-8'))<=8192 and message.get('bytes') is None and rpc_count<32 and len(requests)<128
                        def unique(pairs):
                            result={}
                            for key,value in pairs:
                                assert key not in result;result[key]=value
                            return result
                        frame=json.loads(raw,object_pairs_hook=unique)
                        assert isinstance(frame,dict) and set(frame)=={'jsonrpc','id','method','params'} and frame['jsonrpc']=='2.0' and isinstance(frame['id'],str) and 0<len(frame['id'])<=64
                        method=frame['method'];params=frame['params'];assert isinstance(params,dict)
                        assert frame['id'] not in rpc_methods;rpc_methods[frame['id']]=method
                        if a.surface in ('canonical','canonical-session'):
                            if a.surface=='canonical':
                                assert method=='runtime.describe' and params=={} and rpc_count==0
                            else:
                                assert canonical_fixture_rpc_allowed(method,params,rpc_count,a.allow_viewer_heartbeat)
                            if a.emit_viewer_activity and method=='session.events.since':
                                await asyncio.wait_for(opening_activity_ready.wait(),5)
                            if a.emit_viewer_activity and method=='gateway.ping':
                                connection_pings+=1
                                if connection_pings==3:
                                    assert opening_activity_ready.is_set() and not live_activity_sent
                                    emit_viewer_activity('live');live_activity_sent=True
                            entry_rpc=dict(method='RPC',path=method,profile=[],noSessionOrMutation=method in ('runtime.describe','session.list','gateway.ping'))
                        else:
                            assert method in ('projects.list','projects.get')
                            assert set(params)==({'profile'} if method=='projects.list' else {'profile','id'}) and params['profile'] in ('default','worker','missing-fixture')
                            if method=='projects.get':assert params['id'] in ('fixture-shared-project','default-only-project','worker-only-project')
                            entry_rpc=dict(method='RPC',path=method,profile=[params['profile']],projectId=params.get('id'))
                        rpc_count+=1;requests.append(entry_rpc)
                    except Exception:
                        requests.append(dict(method='RPC',path='unreviewed',status=400));await send(dict(type='websocket.close',code=1008))
                        from starlette.websockets import WebSocketDisconnect
                        raise WebSocketDisconnect(code=1008)
                return message
            async def observed_ws(message):
                nonlocal opening_activity_sent
                if message['type']=='websocket.accept':entry['accepted']=True;entry['subprotocol']=message.get('subprotocol')
                elif message['type']=='websocket.close':entry['closeCode']=message.get('code')
                await send(message)
                if a.emit_viewer_activity and message['type']=='websocket.send' and isinstance(message.get('text'),str):
                    reply=json.loads(message['text'])
                    if rpc_methods.get(reply.get('id'))=='session.resume' and 'result' in reply:
                        assert not opening_activity_sent
                        emit_viewer_activity('opening');opening_activity_sent=True;opening_activity_ready.set()
            await web_server.app(forwarded,bounded_receive,observed_ws);return
        if scope['type']=='http' and scope['path'].startswith('/fixture-model/'):
            assert a.surface=='canonical-session' and scope['method']=='GET'
            path=scope['path']
            assert path in ('/fixture-model/v1/models','/fixture-model/v1/props','/fixture-model/props','/fixture-model/v1/models/fixture-model')
            requests.append(dict(method='MODEL_METADATA_GET',path=path,status=200))
            payload={'object':'list','data':[{'id':'fixture-model','object':'model','context_length':4096}]}
            raw=json.dumps(payload).encode()
            await send(dict(type='http.response.start',status=200,headers=[(b'content-type',b'application/json')]))
            await send(dict(type='http.response.body',body=raw));return
        if scope['type']!='http':
            await web_server.app(scope,receive,send);return
        path=scope['path'];method=scope['method'];headers=list(scope['headers'])
        if not path.startswith('/dashboard/') or (method,path[len('/dashboard'):]) not in allowed or len(requests)>=128:
            requests.append(dict(method=method,path='unreviewed',status=404))
            await send(dict(type='http.response.start',status=404,headers=[(b'content-type',b'application/json')]))
            await send(dict(type='http.response.body',body=b'{}'));return
        short=path[len('/dashboard'):]
        query=urllib.parse.parse_qs(scope.get('query_string',b'').decode('ascii'))
        profile=query.get('profile',[])
        entry=dict(method=method,path=short,profile=profile if profile in ([],['default'],['worker']) else ['unreviewed'],bearer=any(k.lower()==b'authorization' and v.startswith(b'Bearer ') for k,v in headers),cookie=any(k.lower()==b'cookie' for k,v in headers))
        requests.append(entry)
        forwarded=dict(scope);forwarded['path']=short;forwarded['raw_path']=short.encode('ascii');forwarded['root_path']=''
        forwarded['headers']=[(k,v) for k,v in headers if k.lower()!=b'x-forwarded-prefix']+[(b'x-forwarded-prefix',b'/dashboard')]
        async def observed(message):
            if message['type']=='http.response.start':entry['status']=message['status']
            await send(message)
        await web_server.app(forwarded,receive,observed)
    async def serve():
        listener=reserved_listener
        if listener is None:
            listener=socket.socket(socket.AF_INET,socket.SOCK_STREAM)
            listener.bind(('127.0.0.1',0));listener.listen(64)
        port=listener.getsockname()[1];web_server.app.state.bound_port=port
        config=uvicorn.Config(proxy,host='127.0.0.1',port=0,loop='asyncio',http='h11',ws='websockets' if a.surface in ('projects','canonical','canonical-session') else 'none',lifespan='on',log_config=None,access_log=False,log_level='warning',timeout_graceful_shutdown=5)
        server=uvicorn.Server(config);task=asyncio.create_task(server.serve(sockets=[listener]))
        deadline=time.monotonic()+15
        while not server.started and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
        assert server.started and not task.done(),'Actual dashboard listener did not start'
        pending=root/'ready.pending'
        pending.write_text(json.dumps(dict(state='READY_OWNED_FIXTURE',pid=os.getpid(),port=port,baseUrl=f'http://127.0.0.1:{port}/dashboard/',sourceCommit=SOURCE_COMMIT,revision=a.revision,authRequired=True,fixtureRootName=root.name,surface=a.surface,viewerHeartbeatEnabled=a.allow_viewer_heartbeat,viewerActivityEnabled=a.emit_viewer_activity))+'\n',encoding='utf-8')
        pending.replace(root/'ready.json')
        deadline=time.monotonic()+a.lifetime_seconds
        try:
            while not (root/'stop').exists() and not task.done() and time.monotonic()<deadline:await asyncio.sleep(.05)
            report['stopRequested']=(root/'stop').exists()
        finally:
            server.should_exit=True
            await asyncio.wait_for(task,10)
            listener.close()
    asyncio.run(serve())
    if a.surface in ('canonical','canonical-session'):
        if a.surface=='canonical':assert canonical_authority.sessions=={}
        assert canonical_authority.events=={}
        report['canonicalSessionIds']=sorted(canonical_authority.sessions)
        for db in canonical_dbs:db.close()
        report['noCanonicalSessionRestoredOrAdopted']=(a.surface=='canonical')
    for profile,home in homes.items():
        scope_token=hc.set_hermes_home_override(str(home))
        try:
            db=SessionDB(db_path=home/'state.db',read_only=True)
            try:
                after={sid:(db.get_session(sid),db.get_messages(sid)) for sid in ('shared-fixture',profile+'-only-fixture')}
                if a.surface=='canonical-session' and profile=='default':
                    with db._read_ctx(reopen=False) as conn:
                        report['sessionAdmissionCount']=conn.execute('SELECT COUNT(*) FROM session_admissions').fetchone()[0]
                        report['workerExecutionCount']=conn.execute('SELECT COUNT(*) FROM worker_executions').fetchone()[0]
                    assert report['sessionAdmissionCount']==report['workerExecutionCount']==0
            finally:db.close()
        finally:hc.reset_hermes_home_override(scope_token)
        if a.surface!='canonical-session':assert after==original[profile]
        else:
            assert all(after[sid][1]==original[profile][sid][1] for sid in after)
            report.setdefault('sessionMetadataChanges',{})[profile]={sid:{key:{'before':original[profile][sid][0].get(key),'after':row.get(key)} for key in row if row.get(key)!=original[profile][sid][0].get(key)} for sid,(row,messages) in after.items()}
    if a.surface=='projects':
        for profile,home in homes.items():
            scope_token=hc.set_hermes_home_override(str(home))
            try:
                with pdb.connect_closing() as conn:assert project_rows(conn)==project_original[profile]
            finally:hc.reset_hermes_home_override(scope_token)
        assert not (root/'hermes-home/profiles/missing-fixture').exists()
        report['seededProjectsUnchanged']=True
    if a.surface=='canonical-session':
        changes=report.get('sessionMetadataChanges',{})
        allowed={'user_id','session_key','chat_id','chat_type','origin_json'}
        for profile,rows in changes.items():
            for sid,delta in rows.items():
                assert set(delta)<=allowed if profile=='default' and sid=='shared-fixture' else not delta
        model_reads=[row for row in requests if row['method']=='MODEL_METADATA_GET']
        assert all(row['path'].startswith('/fixture-model/') for row in model_reads)
        report['modelMetadataReads']=len(model_reads)
        report['viewerHeartbeatEnabled']=a.allow_viewer_heartbeat
        report['viewerHeartbeatCount']=sum(row['method']=='RPC' and row['path']=='gateway.ping' for row in requests)
        report['viewerActivityEnabled']=a.emit_viewer_activity
        report['viewerActivityEvents']=emitted_activity
        report['seededTranscriptMessagesUnchanged']=True
    assert not denied,denied
    report.update(state='STOPPED_REAL_TCP_FIXTURE_REVIEW_CONSUMER',seededHistoryUnchanged=True)
except BaseException as error:
    report.update(state='FAILED_INSPECT_REAL_TCP_FIXTURE',errorType=type(error).__name__)
    (root/'traceback-private.txt').write_text(traceback.format_exc(),encoding='utf-8')
finally:
    modules={}
    for name,module in list(sys.modules.items()):
        value=getattr(module,'__file__',None)
        if value:
            path=Path(value).resolve()
            if path.is_relative_to(source) and path.is_file():modules[path.relative_to(source).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    report.update(revision=a.revision,sourceCommit=SOURCE_COMMIT,requests=locals().get('requests',[]),projectModules=modules,deniedEvents=denied,denialDetails=denial_details,expectedBlockedMetadataProbes=expected_blocked_metadata_probes,ownedSocketEvents=owned_socket_events,scope='Actual dashboard auth behind a fixture-only prefix proxy on an owned loopback TCP socket, upstream synthetic IdP, protected E homes. Consumer results require independent verification. No TLS/external browser/real IdP/Pi/worker acceptance. Audit hooks are not an OS sandbox.')
    (root/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(state=report['state'],requests=len(report['requests']),modules=len(modules),deniedEvents=denied,stopRequested=report.get('stopRequested'),errorType=report.get('errorType'))))
    if report['state'].startswith('FAILED'):raise SystemExit(1)
