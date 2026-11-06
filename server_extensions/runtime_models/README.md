# Optional local-model status extension

This small source adapter adds an authenticated, read-only observation route to
an explicitly reviewed Hermes API runtime. It creates no agent, model controller,
SSH service, inference service or cloud fallback. Standard Hermes installations
remain usable without it; Signal Grove shows that serving-model status is not
reported when the capability is absent.

The observer uses the selected profile's existing `security.inference.allowed_routes`
and registered provider credential resolver. It accepts at most eight `spark-*`
routes, each naming one expected model on the same private `.ts.net` inference
host. No URL, provider, model or credential can be supplied by a phone request.
It first requires anonymous rejection, then authenticated exact model identity
and health. It never generates text, loads/unloads weights or changes defaults.
Saved catalogs, credential presence, an API alias and gateway health alone never
establish a serving model.

See [the versioned contract](../../docs/RUNTIME_MODEL_STATUS.md). Credentials stay
in the host's existing protected stores. The installer does not copy or modify
them. Missing credentials or uncertain observations return an unverified state.

## Review and deployment

Before touching a live runtime, inspect its instructions/current source, take and
verify its encrypted backup, check for active work and preserve custom fixes.
Review all four adapter insertions against the exact target. Preview only:

```sh
python3 -B install_adapter.py --runtime-root /path/to/hermes/runtime --expected-api-sha256 REVIEWED_RAW_SOURCE_SHA256
```

Only after review use the same command with `--apply`. It refuses changed source,
ambiguous anchors, an existing observer or mixed source newlines. CRLF/LF style
and original API permissions are preserved. The module is created exclusively,
then the API is replaced atomically with a second source-hash check. A failed
partial admission leaves source for inspection; do not blindly rerun it or remove
unknown staging files. Keep an exact original-source rollback copy separately.

Restart only the existing idle gateway through its service manager. Verify the
advertised capability, private trusted HTTPS, unauthenticated/invalid-key 401,
GET-only behavior, query refusal, profile isolation and real authenticated model
identity. Run adjacent gateway, dashboard, doctor and continuity checks. Record
limitations and a verified post-change backup. This source adapter is intentionally
not a universal installer: different Hermes source requires a fresh review.

## Tests

```sh
python3 -B -m unittest discover -s server_extensions/runtime_models -v
```

The suite checks actual loopback HTTP authentication/identity/health, anonymous
credential withholding, redirects, unknown/ambiguous observations, policy refusal,
concurrent profile separation, secret exclusion and changed-source refusal. It
never calls inference or controls a model. Live deployment tests remain separate.
