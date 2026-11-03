"""Read-only upstream API and management drift check using the installed GitHub CLI. Parse source as AST; never import/execute it.

Examples:
  python -B scripts/check_hermes_contract.py --ref latest --output E:/Temp/hermes-stable.json
  python -B scripts/check_hermes_contract.py --ref main --output E:/Temp/hermes-main.json
Exit 2 means a used route/feature or a tracked constraint changed. A zero exit is NOT
an interoperability/security certification; inspect source/feature additions and run live contracts. The default baseline tracks the current implemented client; use --baseline for historical evidence.
"""
from __future__ import annotations
import argparse
import ast
import copy
import concurrent.futures
import json
import hashlib
from pathlib import Path
import re
import sys
import base64
import subprocess

REPO = 'NousResearch/hermes-agent'
SOURCES = {
    'api': 'gateway/platforms/api_server.py',
    'runs': 'gateway/platforms/api_server_runs.py',
    'rooms': 'gateway/platforms/api_server_room_grants.py',
}
EXTRA_SOURCES = {
    'authority': 'gateway/platforms/api_server_authority_runs.py',
    'auth': 'hermes_cli/dashboard_auth/routes.py',
    'profiles': 'hermes_cli/web_routers/profiles.py',
    'mcp': 'hermes_cli/web_routers/mcp.py',
    'mcp_helpers': 'hermes_cli/web_server_mcp.py',
    'mcp_catalog': 'hermes_cli/mcp_catalog.py',
    'mcp_oauth': 'tools/mcp_dashboard_oauth.py',
    'cron': 'hermes_cli/web_routers/cron.py',
    'cron_helpers': 'hermes_cli/web_server_cron.py',
    'cron_jobs': 'cron/jobs.py',
    'cron_time': 'cron/jobs_time.py',
    'cron_invariants': 'cron/jobs_invariants.py',
    'cron_scheduler': 'cron/scheduler.py',
    'gateway_models': 'tui_gateway/contracts/tools_commands.py',
    'gateway_rpc': 'tui_gateway/methods_tools.py',
    'gateway_scope': 'tui_gateway/model_switch.py',
    'gateway_runtime': 'tui_gateway/server.py',
    'gateway_ws': 'tui_gateway/ws.py',
    'chat_ws': 'hermes_cli/web_routers/chat_ws.py',
    'chat_auth': 'hermes_cli/web_server_chat.py',
    'ws_boundary': 'hermes_cli/web_server_boundary.py',
    'ws_tickets': 'hermes_cli/dashboard_auth/ws_tickets.py',
    'cron_cancel': 'cron/cancellation.py',
    'cron_tool': 'tools/cronjob_tools.py',
    'cron_format': 'tools/cronjob_job_args.py',
    'skills': 'hermes_cli/web_routers/skills.py',
    'skills_config': 'hermes_cli/skills_config.py',
    'skill_usage': 'tools/skill_usage.py',
    'skill_manager': 'tools/skill_manager_tool.py',
    'skill_catalog': 'tools/skills_tool.py',
    'skill_utils': 'agent/skill_utils.py',
    'web_bootstrap': 'hermes_cli/web_server.py',
    'models': 'hermes_cli/web_models.py',
    'profile_helpers': 'hermes_cli/web_server_profiles.py',
    'hub_cli': 'hermes_cli/skills_hub.py',
    'hub_search': 'tools/skills_hub_search.py',
    'hub_claw': 'tools/skills_hub_clawhub.py',
    'hub_official': 'tools/skills_hub_official.py',
    'hub_guard': 'tools/skills_guard.py',
    'hub_tier1': 'tools/skillevaluator_scan.py',
    'hub_install': 'tools/skills_hub_install.py',
    'hub_models': 'tools/skills_hub_models.py',
    'web_common': 'hermes_cli/web_routers/_common.py',
    'host_actions': 'hermes_cli/web_server_gateway.py',
    'bundled_ops': 'tools/skills_sync_bundled_ops.py',
    'gateway_plugin_models': 'tui_gateway/contracts/tools_mcp_plugins.py',
    'gateway_common': 'tui_gateway/contracts/common.py',
    'plugin_core': 'hermes_cli/plugins_cmd.py',
    'plugin_servers': 'tui_gateway/methods_tools_plugin_servers.py',
    'gateway_lookup': 'tui_gateway/methods_plugin_catalog.py',
    'gateway_method_ctx': 'tui_gateway/method_ctx.py',
    'plugin_inspect': 'hermes_cli/plugins_cmd_inspect.py',
    'plugin_presence': 'hermes_cli/plugin_catalog_presence.py',
    'plugin_catalog_gate': 'hermes_cli/plugins_cmd_catalog.py',
    'plugin_catalog_loader': 'hermes_cli/plugin_catalog.py',
    'plugin_git': 'hermes_cli/plugins_cmd_git.py',




}
MANAGEMENT_SOURCES = {'auth', 'profiles', 'mcp', 'cron', 'skills', 'chat_ws'}

def ast_signature(value):
    # ast.dump formatting/default fields differ across supported Python releases.
    if isinstance(value, ast.AST):
        return {'node': type(value).__name__, **{name: ast_signature(item) for name,item in ast.iter_fields(value) if item is not None and item != []}}
    if isinstance(value, list):
        return [ast_signature(item) for item in value]
    if isinstance(value, bytes):
        return {'bytes': value.hex()}
    if value is Ellipsis:
        return {'ellipsis': True}
    if isinstance(value, complex):
        return {'complex': [value.real, value.imag]}
    return value

def fingerprint(node):
    return hashlib.sha256(json.dumps(ast_signature(node),sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()

def definitions(tree: ast.AST, source: str) -> tuple[dict, dict]:
    handlers, fields = {}, {}
    def visit(nodes, prefix=''):
        for node in nodes:
            if isinstance(node, ast.ClassDef):
                if (source=='gateway_models' and node.name=='CronAction') or (source=='gateway_plugin_models' and node.name in ('PluginsAction','PluginCatalogLookupStatus')):handlers[f'{source}:enum:{node.name}']=fingerprint(node)
                visit(node.body, prefix+node.name+'.')
                for item in node.body:
                    if isinstance(item, ast.AnnAssign) and isinstance(item.target, ast.Name):
                        fields[f'{source}:{prefix}{node.name}.{item.target.id}'] = fingerprint(item)
            elif not prefix and isinstance(node,ast.Assign) and source in ('ws_boundary','ws_tickets','cron_tool','cron_format','hub_guard','hub_tier1','bundled_ops','gateway_rpc','plugin_inspect','plugin_presence'):
                selected={'ws_boundary':{'_GATEWAY_WS_PROTOCOL','_GATEWAY_WS_TICKET_PROTOCOL_PREFIX'},'ws_tickets':{'TTL_SECONDS'},'cron_tool':{'_JOB_ACTIONS','_JOBLESS_ACTIONS'},'cron_format':{'_FORMAT_JOB_OPTIONAL_KEYS'},'hub_guard':{'INSTALL_POLICY','VERDICT_INDEX'},'hub_tier1':{'SCAN_TIMEOUT_SECONDS','SECRETS_CLASS_CHECKS'},'bundled_ops':{'_OWN_REPO_PREFIXES'},'gateway_rpc':{'_PLUGINS_ACTIONS'},'plugin_inspect':{'_RESULT_TTL_SECONDS'},'plugin_presence':{'_TIMEOUT','_MAX_BYTES'}}[source]
                for target in node.targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif source == 'skill_utils' and not prefix and isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == 'ESSENTIAL_SKILLS':
                handlers[f'{source}:{node.target.id}'] = fingerprint(node)
            elif source == 'cron_jobs' and not prefix and isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == '_CREATE_FIELD_NORMALIZERS':
                handlers[f'{source}:{node.target.id}'] = fingerprint(node)
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                # Strip prose only; all executable statements/defaults/decorators remain in the hash.
                normalized = copy.deepcopy(node)
                if normalized.body and isinstance(normalized.body[0], ast.Expr) and isinstance(normalized.body[0].value, ast.Constant) and isinstance(normalized.body[0].value.value, str):
                    normalized.body = normalized.body[1:]
                handlers[f'{source}:{prefix}{node.name}'] = fingerprint(normalized)
                # RPC modules intentionally name many handlers `_`; preserve their public method identity.
                if source in ('gateway_rpc','gateway_lookup') and not prefix:
                    for decorator in node.decorator_list:
                        if (isinstance(decorator,ast.Call) and isinstance(decorator.func,ast.Name)
                                and decorator.func.id in ('_scoped_rpc','_rpc','method') and decorator.args
                                and isinstance(decorator.args[0],ast.Constant) and isinstance(decorator.args[0].value,str)):
                            handlers[f'{source}:rpc:{decorator.args[0].value}'] = fingerprint(normalized)
    visit(tree.body)
    return handlers, fields

def github(path: str, selector: str | None = None) -> dict:
    # Use the supported API and the owner's existing gh authentication/rate budget.
    # Never scrape raw pages, collect a new token or print credentials.
    argv = ['gh', 'api', path]
    if selector:
        argv += ['--jq', selector]
    response = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    if response.returncode:
        raise ValueError('GitHub API unavailable or rate limited; retry only after the provider permits it')
    if len(response.stdout) > 3_000_000:
        raise ValueError('Upstream response exceeds the audit limit')
    return json.loads(response.stdout)


def resolve(ref: str) -> tuple[str, str]:
    if ref == 'latest':
        ref = github(f'repos/{REPO}/releases/latest', '{tag_name: .tag_name}')['tag_name']
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,120}', ref):
        raise ValueError('Use latest, main, a release tag or a commit SHA')
    sha = github(f'repos/{REPO}/commits/{ref}', '{sha: .sha}')['sha']
    if not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise ValueError('Invalid upstream commit identity')
    return ref, sha

def extract(source: dict[str, str]) -> dict:
    if not (SOURCES.keys() | EXTRA_SOURCES.keys()) <= source.keys():
        raise ValueError('Missing required contract source; manual inspection required')
    trees = {name: ast.parse(value) for name, value in source.items()}
    flags = {}
    constraints = {}
    update_fields = []
    for node in ast.walk(trees['api']):
        if isinstance(node, ast.Assign):
            names = [t.id for t in node.targets if isinstance(t, ast.Name)]
            if '_STATIC_FEATURE_FLAGS' in names:
                flags = ast.literal_eval(node.value)
            for key in ('_MAX_NAME_LENGTH', '_MAX_PROMPT_LENGTH'):
                if key in names:
                    constraints[key] = ast.literal_eval(node.value)
            if '_UPDATE_ALLOWED_FIELDS' in names:
                update_fields = sorted(ast.literal_eval(node.value))
    if not flags or '_MAX_PROMPT_LENGTH' not in constraints:
        raise ValueError('Upstream contract layout changed; manual inspection required')
    routes = set()
    for name in SOURCES:
        tree = trees[name]
        functions = [n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in ('_http_route_table', '_http_routes')]
        if not functions:
            raise ValueError(f'Cannot identify {name} route registration; manual inspection required')
        for fn in functions:
            for node in ast.walk(fn):
                if isinstance(node, ast.Tuple) and len(node.elts) >= 2:
                    method, path = node.elts[:2]
                    if isinstance(method, ast.Constant) and isinstance(path, ast.Constant) and method.value in ('GET', 'POST', 'PATCH', 'PUT', 'DELETE') and isinstance(path.value, str) and path.value.startswith('/'):
                        routes.add(f'{method.value} {path.value}')
    management_routes, handlers, request_fields = set(), {}, {}
    for name, tree in trees.items():
        found_handlers, found_fields = definitions(tree, name)
        handlers.update(found_handlers); request_fields.update(found_fields)
        if name in MANAGEMENT_SOURCES:
            for node in ast.walk(tree):
                if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    continue
                for decorator in node.decorator_list:
                    if not isinstance(decorator, ast.Call) or not isinstance(decorator.func, ast.Attribute) or not decorator.args:
                        continue
                    method, path = decorator.func.attr.upper(), decorator.args[0]
                    if method in ('GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'WEBSOCKET') and isinstance(path, ast.Constant) and isinstance(path.value, str) and path.value.startswith('/'):
                        management_routes.add(f'{method} {path.value}')
    # FastAPI supplies this route implicitly; detect only a reviewed literal/default.
    applications=[node for node in ast.walk(trees['web_bootstrap']) if isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id=='FastAPI']
    if len(applications)!=1:raise ValueError('Cannot identify the management OpenAPI route')
    configured=next((item for item in applications[0].keywords if item.arg=='openapi_url'),None)
    openapi='/openapi.json' if configured is None else ast.literal_eval(configured.value)
    if openapi is not None:
        if not isinstance(openapi,str) or not openapi.startswith('/') or openapi.startswith('//'):raise ValueError('Invalid management OpenAPI route')
        management_routes.add('GET '+openapi)
    def integer_limit(node):
        if isinstance(node,ast.Constant) and type(node.value) is int:return node.value
        if isinstance(node,ast.BinOp) and isinstance(node.op,ast.Mult):
            result=integer_limit(node.left)*integer_limit(node.right)
            if abs(result)<=1000000:return result
        raise ValueError('Unsupported cron grace limit; inspect upstream without execution')
    grace={}
    for node in trees['cron_time'].body:
        if isinstance(node,ast.Assign):
            for target in node.targets:
                if isinstance(target,ast.Name) and target.id in ('MIN_GRACE_MINUTES','MAX_GRACE_MINUTES'):grace['management_'+target.id.lower()]=integer_limit(node.value)
    if len(grace)!=2:raise ValueError('Missing management cron grace bounds')
    constraints.update(grace)
    return {'routes': sorted(routes), 'management_routes': sorted(management_routes), 'source_hashes': {name: hashlib.sha256(value.encode()).hexdigest() for name,value in source.items()}, 'handlers': handlers, 'request_fields': request_fields,
            'static_features': flags, 'job_constraints': constraints, 'job_update_fields': update_fields}


def snapshot(ref: str) -> dict:
    resolved, sha = resolve(ref)
    def download(item):
        name, path = item
        payload = github(f'repos/{REPO}/contents/{path}?ref={sha}')
        if payload.get('encoding') != 'base64' or payload.get('size', 2_000_001) > 2_000_000:
            raise ValueError('Unsupported upstream content response')
        return name, base64.b64decode(payload['content']).decode('utf-8')
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        sources = dict(pool.map(download, (SOURCES | EXTRA_SOURCES).items()))
    return {'requested_ref': ref, 'resolved_ref': resolved, 'commit': sha, 'contract': extract(sources)}

def compare(baseline: dict, current: dict) -> dict:
    old, new = baseline['upstream']['contract'], current['contract']
    old_routes, new_routes = set(old['routes']), set(new['routes'])
    removed = sorted(old_routes-new_routes)
    used = set(baseline['mobile_used_routes'])
    flags = {key: {'before': old['static_features'].get(key), 'after': new['static_features'].get(key)} for key in sorted(set(old['static_features']) | set(new['static_features'])) if old['static_features'].get(key) != new['static_features'].get(key)}
    constraints = {} if old['job_constraints'] == new['job_constraints'] else {'before': old['job_constraints'], 'after': new['job_constraints']}
    relevant = {'removed_used_routes': sorted(set(removed) & used), 'changed_used_flags': {key: value for key, value in flags.items() if key in baseline['mobile_used_static_flags']}, 'changed_job_constraints': constraints, 'removed_used_job_update_fields': sorted((set(old['job_update_fields']) & set(baseline['mobile_used_job_update_fields'])) - set(new['job_update_fields']))}
    removed_management = sorted(set(old.get('management_routes', []))-set(new.get('management_routes', [])))
    relevant['removed_used_management_routes'] = sorted(set(removed_management) & set(baseline.get('mobile_used_management_routes', [])))
    variants=baseline.get('reviewed_handler_variants',{})
    if not isinstance(variants,dict):raise ValueError('Invalid reviewed handler variants')
    accepted={}
    for key,entries in variants.items():
        if key not in baseline.get('mobile_used_handlers',[]) or not isinstance(entries,list):raise ValueError('Invalid reviewed handler variant target')
        accepted[key]=set()
        for entry in entries:
            if (not isinstance(entry,dict) or not isinstance(entry.get('sha256'),str) or not re.fullmatch(r'[a-f0-9]{64}',entry['sha256'])
                    or not isinstance(entry.get('commit'),str) or not re.fullmatch(r'[a-f0-9]{40}',entry['commit'])
                    or not isinstance(entry.get('reason'),str) or not entry['reason'].strip()):
                raise ValueError('Incomplete reviewed handler variant evidence')
            accepted[key].add(entry['sha256'])
    relevant['changed_used_handlers'] = {key: {'before': old.get('handlers', {}).get(key), 'after': new.get('handlers', {}).get(key)}
        for key in baseline.get('mobile_used_handlers', []) if old.get('handlers', {}).get(key) != new.get('handlers', {}).get(key) and new.get('handlers', {}).get(key) not in accepted.get(key,set())}
    relevant['changed_used_request_fields'] = {key: {'before': old.get('request_fields', {}).get(key), 'after': new.get('request_fields', {}).get(key)}
        for key in baseline.get('mobile_used_request_fields', []) if old.get('request_fields', {}).get(key) != new.get('request_fields', {}).get(key)}
    return {'baseline_commit': baseline['upstream']['commit'], 'changed_source_modules': sorted(key for key in set(old.get('source_hashes', {})) | set(new.get('source_hashes', {})) if old.get('source_hashes', {}).get(key) != new.get('source_hashes', {}).get(key)), 'known_open_mobile_findings': baseline.get('known_open_mobile_findings', []), 'current': current, 'added_routes': sorted(new_routes-old_routes), 'added_management_routes': sorted(set(new.get('management_routes', []))-set(old.get('management_routes', []))), 'removed_management_routes': removed_management, 'removed_routes': removed, 'changed_static_features': flags, 'job_update_fields_changed': old['job_update_fields'] != new['job_update_fields'], 'mobile_review_required': any(relevant.values()), 'mobile_relevant_changes': relevant, 'limitations': ['Static routes/selected limits, explicitly tracked request fields and handler ASTs only; transitive helper changes, runtime feature gates, installed deployment and provider behavior still require review and live tests.', 'New routes/features must be triaged even when no used contract changed. No server credentials are sent.']}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref', default='latest')
    parser.add_argument('--baseline', type=Path, default=Path(__file__).resolve().parents[1]/'docs/contracts/hermes-baseline.json')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--snapshot-only', action='store_true')
    args = parser.parse_args()
    try:
        current = snapshot(args.ref)
        result = current if args.snapshot_only else compare(json.loads(args.baseline.read_text(encoding='utf-8')), current)
        text = json.dumps(result, indent=2, ensure_ascii=False)+'\n'
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text, encoding='utf-8')
        else:
            print(text, end='')
        return 2 if result.get('mobile_review_required') else 0
    except (ValueError, KeyError, SyntaxError, OSError, subprocess.TimeoutExpired) as exc:
        print(f'Contract check incomplete: {type(exc).__name__}. Inspect upstream/network manually.', file=sys.stderr)
        return 3

if __name__ == '__main__':
    sys.exit(main())
