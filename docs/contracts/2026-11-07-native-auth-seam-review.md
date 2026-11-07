# Private-grant and saved-gateway callback source review

The [compatibility run for 0c36c99](https://github.com/DionCroft/Signal_Grove/actions/runs/42412679981)
failed on two genuine main-source changes at
`071d31ffc69493ca1d7d368c163ce0b303d83387`: the dashboard's public-prefix list
and gated authentication function. The failed main ZIP has SHA-256
`6c737a12275de1cae5689e779452fe244556873e6f7a2a15a83e60f86dcf22f7`;
the passing stable ZIP has
`aa0d980b4dc6956bce3ad393e9b00d34b7bfad71a629ccb3237421fb41bf6916`.
Both reports remain retained. This review does not relabel that run.

## What changed

The [gated middleware](https://github.com/NousResearch/hermes-agent/blob/071d31ffc69493ca1d7d368c163ce0b303d83387/hermes_cli/dashboard_auth/middleware.py)
again recognizes `request.state.native_http_principal`, which the existing
private HTTP seam stamps after grant verification. The verified
[producer](https://github.com/NousResearch/hermes-agent/blob/071d31ffc69493ca1d7d368c163ce0b303d83387/hermes_cli/dashboard_auth/native_http.py)
is byte-identical to the original F0 source and stable 2bd. It requires exactly
one ticket header, an actual captured loopback peer, no Origin header, a ready
matching runtime instance, a served profile and the issuer's exact purpose and
capability table. Its ticket store consumes successful redemption once. The
profile guards check repeated selectors and bind the grant's home for the request.
A header named after the principal cannot create that server-side state.

The other change lets the Webapp receive a saved gateway's OAuth redirect at
`/webapp/gateway-connections/oauth/callback`. The
[callback](https://github.com/NousResearch/hermes-agent/blob/071d31ffc69493ca1d7d368c163ce0b303d83387/hermes_cli/web_routers/gateway_connections_reach.py)
uses the [flight helper](https://github.com/NousResearch/hermes-agent/blob/071d31ffc69493ca1d7d368c163ce0b303d83387/hermes_cli/gateway_connections_oauth.py)'s
one-use, expiring state. Unknown state is refused before token exchange; a
connection-specific mismatch preserves the other connection's flight. A consumed
flight cannot exchange again. The actual store rechecks the connection's issuer
URL under its token lock. Error pages escape text, responses use no-store, and
token values stay on the server. This is separate from Signal Grove's own native
login and does not add saved-gateway administration to the phone.

The review also covers the existing runtime socket boundary. Stable and main
share the same `GatewayRuntimeAPI._serve` bytes. A remote socket without Origin
passes to the existing dashboard gate; it does not redeem a private local grant
or gain an actor. This matters for native mobile clients, which do not send a
browser Origin. The exact boundary is now watched alongside the HTTP producer.

## Guarded acceptance

The original F0 identity and every existing fingerprint remain unchanged. Seven
complete dependency sources are added: private HTTP producer, ticket issuer,
runtime peer boundary, callback routes, OAuth flight helper, connection store and
normalizer. Their original source identities come from verified complete Git
blobs. The four saved-gateway modules are explicitly absent at F0, b200, stable
2bd and prior bf; complete untruncated Git trees prove those absences. Missing
files at another commit still stop source inspection.

Thirty-six additional producer, purpose, expiry, profile, peer, replay and calling
seam projections are selected. The two changed middleware variants require all
seven complete dependency hashes plus the middleware, bootstrap and Webapp
sign-in modules. The runtime socket variant binds its three native modules and
the existing WebSocket boundary. Changed or missing source bytes refuse the
variant even when the function AST remains the same. A commit label is provenance,
not a waiver. The socket variant also applies to stable because those complete
module bytes match exactly.

The older bf source has different private-runtime helper shapes, including two
absent helpers. This review does not waive them. Its separately pinned 21-case
public native-login/history acceptance remains valid for that bounded scope;
private-runtime/adoption acceptance remains open.

## Actual-source checks

The extended `compatibility_probe_native_auth_history.py` keeps the stable/prior
21-case paths and adds an explicit independently pinned main path. Main checks
52 distinct cases: the existing 21 login/history cases, 19 private-grant cases,
five socket routing/refusal cases and seven saved-gateway callback/store cases.
All cases, source pins, imported-module hashes and denied events are recorded.
The main path pins 37 complete Git blobs before importing code.

The actual FastAPI app and lifespan, native broker, profile stores, private HTTP
middleware, TicketStore, runtime socket boundary and callback/store execute in
fresh fixture homes. Synthetic authority records and a captured-peer ASGI adapter
exercise the grant path. A refusing downstream socket gate proves delegation;
it cannot authenticate a socket. A synthetic outbound token response lets the
actual callback and issuer-bound store run without contacting another gateway.
All seeded sessions/messages must remain unchanged.

Use an existing dependency installation and fresh E-based source/output roots:

```powershell
& $probePython -I -S -B scripts/compatibility_probe_native_auth_history.py `
  --source-root $verifiedSourceRoot --work-root $freshFixtureRoot `
  --dependency-site $existingDependencySite --revision main-071d31f
```

The probe requires Windows, assertions and isolated imports. Existing sterile
environment, filesystem/database/socket bounds and denied subprocess policy
remain. Python audit hooks provide defense in depth rather than OS isolation.
No upstream code or dependencies are installed or edited by the probe.

The first 47-case main pilot passed. Two later socket-fixture iterations remain
failed: a nonempty opaque ticket was incorrectly described as malformed, then
the expected close envelope omitted Starlette's empty reason field. The final
pilot uses an empty protocol credential and verifies the exact refusal envelope,
including no downstream call. It passes all 52 cases; strict refusal assertions
remain. The repository copy now independently passes stable21/prior21/main52 cases,
with819/765/848 imported project modules verified and0 unexpected denials.
All13 reviewed metadata subprocess attempts per run remain blocked.186 policy
checks, four CLI refusals and17 actual-source counterfactuals pass, together with
complete166-alias Git-verified stable/main source comparisons. Required Android
tasks pass incrementally; the prior608 JVM cases, both lint0, all71 raw reports
and original-key a879/7514 APKs are unchanged. The597-input local freeze closed
after acceptance. See VALIDATION for scope and retained command evidence.

Scope remains isolated ASGI/component behavior. Daemon/private control-channel
issuance, real TCP peer capture, accepted remote sockets, external browsers/TLS,
real identity providers, deployed Pi, inference, workers and project files each
need their own acceptance. This source review does not close those audit rows.
