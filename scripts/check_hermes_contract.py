"""Read-only upstream API drift check using the installed GitHub CLI. Parse source as AST; never import/execute it.

Examples:
  python -B scripts/check_hermes_contract.py --ref latest --output E:/Temp/hermes-stable.json
  python -B scripts/check_hermes_contract.py --ref main --output E:/Temp/hermes-main.json
Exit 2 means a used route/feature or a tracked constraint changed. A zero exit is NOT
an interoperability/security certification; inspect added features and run live contracts.
"""
from __future__ import annotations
import argparse
import ast
import concurrent.futures
import json
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
    for name, tree in trees.items():
        functions = [n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in ('_http_route_table', '_http_routes')]
        if not functions:
            raise ValueError(f'Cannot identify {name} route registration; manual inspection required')
        for fn in functions:
            for node in ast.walk(fn):
                if isinstance(node, ast.Tuple) and len(node.elts) >= 2:
                    method, path = node.elts[:2]
                    if isinstance(method, ast.Constant) and isinstance(path, ast.Constant) and method.value in ('GET', 'POST', 'PATCH', 'PUT', 'DELETE') and isinstance(path.value, str) and path.value.startswith('/'):
                        routes.add(f'{method.value} {path.value}')
    return {'routes': sorted(routes), 'static_features': flags, 'job_constraints': constraints, 'job_update_fields': update_fields}

def snapshot(ref: str) -> dict:
    resolved, sha = resolve(ref)
    def download(item):
        name, path = item
        payload = github(f'repos/{REPO}/contents/{path}?ref={sha}')
        if payload.get('encoding') != 'base64' or payload.get('size', 2_000_001) > 2_000_000:
            raise ValueError('Unsupported upstream content response')
        return name, base64.b64decode(payload['content']).decode('utf-8')
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        sources = dict(pool.map(download, SOURCES.items()))
    return {'requested_ref': ref, 'resolved_ref': resolved, 'commit': sha, 'contract': extract(sources)}

def compare(baseline: dict, current: dict) -> dict:
    old, new = baseline['upstream']['contract'], current['contract']
    old_routes, new_routes = set(old['routes']), set(new['routes'])
    removed = sorted(old_routes-new_routes)
    used = set(baseline['mobile_used_routes'])
    flags = {key: {'before': old['static_features'].get(key), 'after': new['static_features'].get(key)} for key in sorted(set(old['static_features']) | set(new['static_features'])) if old['static_features'].get(key) != new['static_features'].get(key)}
    constraints = {} if old['job_constraints'] == new['job_constraints'] else {'before': old['job_constraints'], 'after': new['job_constraints']}
    relevant = {'removed_used_routes': sorted(set(removed) & used), 'changed_used_flags': {key: value for key, value in flags.items() if key in baseline['mobile_used_static_flags']}, 'changed_job_constraints': constraints, 'removed_used_job_update_fields': sorted((set(old['job_update_fields']) & set(baseline['mobile_used_job_update_fields'])) - set(new['job_update_fields']))}
    return {'baseline_commit': baseline['upstream']['commit'], 'known_open_mobile_findings': baseline.get('known_open_mobile_findings', []), 'current': current, 'added_routes': sorted(new_routes-old_routes), 'removed_routes': removed, 'changed_static_features': flags, 'job_update_fields_changed': old['job_update_fields'] != new['job_update_fields'], 'mobile_review_required': any(relevant.values()), 'mobile_relevant_changes': relevant, 'limitations': ['Static route names/selected limits only; payload semantics, runtime feature gates, dashboard auth, installed deployment and provider behavior require separate tests.', 'New routes/features must be triaged even when no used contract changed. No server credentials are sent.']}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref', default='latest')
    parser.add_argument('--baseline', type=Path, default=Path(__file__).resolve().parents[1]/'docs/audits/2026-11-02/upstream-baseline.json')
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
