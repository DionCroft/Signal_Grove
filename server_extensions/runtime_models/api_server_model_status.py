"""Read-only, profile-scoped observations of allowlisted local inference endpoints.

No generation, model management, configuration writes, provider fallback or retries.
Credentials are resolved on the bound request context and never enter the response.
"""
import asyncio
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
import json
import re
import socket
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit


class PolicyUnavailable(ValueError):
    pass


@dataclass(frozen=True)
class Candidate:
    provider: str
    model: str
    base_url: str
    api_key: str = field(repr=False)


def _url(value):
    if not isinstance(value, str):
        raise PolicyUnavailable()
    p = urlsplit(value)
    if (p.scheme not in ('http', 'https') or not p.hostname or not p.hostname.endswith('.ts.net')
            or p.username or p.password or p.query or p.fragment
            or p.path.rstrip('/') != '/v1' or not p.port):
        raise PolicyUnavailable()
    return value.rstrip('/'), p.hostname


def candidates(config, resolve):
    policy = config.get('security', {}).get('inference', {})
    routes = policy.get('allowed_routes', {})
    if policy.get('enabled') is not True or not isinstance(routes, dict) or not 1 <= len(routes) <= 8:
        raise PolicyUnavailable()
    rows = []
    for provider, route in routes.items():
        if not isinstance(provider, str) or not re.fullmatch(r'spark-[a-z0-9._-]{1,120}', provider) or not isinstance(route, dict):
            raise PolicyUnavailable()
        models = route.get('models')
        if not isinstance(models, list) or len(models) != 1 or not isinstance(models[0], str):
            raise PolicyUnavailable()
        model = models[0]
        if not model or len(model) > 512 or any(ord(c) < 32 or ord(c) == 127 for c in model):
            raise PolicyUnavailable()
        base, host = _url(route.get('base_url'))
        resolved = resolve(provider, target_model=model)
        if (_url(resolved.get('base_url')) != (base, host) or resolved.get('provider') != 'custom'
                or not isinstance(resolved.get('api_key'), str) or not resolved['api_key']):
            raise PolicyUnavailable()
        rows.append(Candidate(provider, model, base, resolved['api_key']))
    if len({urlsplit(c.base_url).hostname for c in rows}) != 1 or len({c.base_url for c in rows}) != len(rows):
        raise PolicyUnavailable()
    return rows


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _read(opener, url, key=None):
    request = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + key} if key else {})
    with opener.open(request, timeout=2) as response:
        raw = response.read(65537)
        if len(raw) > 65536:
            raise ValueError('Response too large')
        return response.status, raw


def probe(candidate):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        _read(opener, candidate.base_url + '/models')
        return 'unsafe'  # Never send credentials to an endpoint which accepts anonymous reads.
    except urllib.error.HTTPError as error:
        if error.code != 401:
            return 'unready' if error.code == 503 else 'unsafe'
    except urllib.error.URLError as error:
        return 'offline' if isinstance(error.reason, (ConnectionRefusedError, TimeoutError)) else 'unknown'
    except (TimeoutError, socket.timeout, OSError):
        return 'offline'
    try:
        status, raw = _read(opener, candidate.base_url + '/models', candidate.api_key)
        ids = [row.get('id') for row in json.loads(raw).get('data', [])]
        if status != 200 or ids != [candidate.model]:
            return 'unsafe'
        status, _ = _read(opener, candidate.base_url.removesuffix('/v1') + '/health', candidate.api_key)
        return 'ready' if status == 200 else 'unready'
    except urllib.error.HTTPError as error:
        return 'unready' if error.code == 503 else 'unsafe'
    except Exception:
        return 'unknown'


def summarize(rows, states, now=time.time):
    allowed = {'ready', 'offline', 'unready', 'unsafe', 'unknown'}
    states = [s if s in allowed else 'unknown' for s in states]
    if len(rows) != len(states) or not rows:
        raise PolicyUnavailable()
    ready = states.count('ready')
    status = ('unverified' if any(s in ('unknown', 'unsafe') for s in states) else
              'ambiguous' if ready > 1 else 'ready' if ready == 1 else 'unavailable')
    return {'object': 'signalgrove.model_runtime', 'schema_version': 1,
            'observed_at': int(now() * 1000), 'status': status,
            'models': [{'provider': c.provider, 'model': c.model, 'state': s,
                        'verified': s == 'ready'} for c, s in zip(rows, states)]}


def _observe(rows):
    with ThreadPoolExecutor(max_workers=len(rows), thread_name_prefix='model-status') as pool:
        return summarize(rows, list(pool.map(probe, rows)))


async def read_runtime_status(config=None, resolve=None):
    try:
        if config is None:
            from hermes_cli.config import load_config_readonly
            config = load_config_readonly()
        policy = config.get('security', {}).get('inference', {})
        if policy.get('enabled') is not True:
            return {'object': 'signalgrove.model_runtime', 'schema_version': 1,
                    'observed_at': int(time.time() * 1000), 'status': 'not_configured', 'models': []}
        if resolve is None:
            from gateway.platforms.api_server import _resolve_request_runtime_agent_kwargs
            resolve = _resolve_request_runtime_agent_kwargs
        # Profile-specific configuration and credentials are resolved BEFORE offloading any I/O.
        rows = candidates(config, resolve)
        return await asyncio.to_thread(_observe, rows)
    except asyncio.CancelledError:
        raise
    except Exception:
        # Do not return/log raw provider errors, keys, URLs, hostnames, paths or configuration.
        return {'object': 'signalgrove.model_runtime', 'schema_version': 1,
                'observed_at': int(time.time() * 1000), 'status': 'unverified', 'models': []}
