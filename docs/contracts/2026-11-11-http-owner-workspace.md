# HTTP/native real-owner workspace matrix

The [first native-owner proof](2026-11-11-native-owner-workspace.md) is now extended
with an explicit `--transport http` mode in the same bounded probe. The original
`native` mode remains the default. Both retain the exact two-commit execution
allowlist, real owner bootstrap/ready publication, final source verification,
process supervision and no-replay/unknown handling. No mobile contract baseline,
fingerprint or compatibility waiver is changed.

## What HTTP mode actually proves

The actual isolated GatewayRunner/API/dashboard runtime registers Hermes's own
Git-bound `tests.hermes_cli.conftest_dashboard_auth.StubAuthProvider` as its only
local test identity provider and requires authentication. The provider is
synthetic; the HTTP auth routes, broker, PKCE/code redemption, refresh handling,
ticket store, WebSocket handler, identity-to-owner mapping, dispatcher, authority
and SQLite mutation are the real pinned source. No Actor/runner/identity/ready
response is substituted. No production credential, sign-in, service or model
configuration is accessed.

A bounded httpx browser driver creates an S256 verifier/state, follows the
actual authorize and provider callback redirects with their cookies, checks the
exact callback address/state, exchanges the one-use code, verifies code replay
returns 400 and verifies a ticket request without a bearer returns 401. The real
refresh route renews the synthetic session. The driver parses the final loopback
callback URL; it does not deliver it through Android's NativeLogin socket or a
real operating-system browser.

The disposable session is still created through the authenticated native-owner
socket, with the same unserved synthetic inference route, no enabled toolsets
and no model/tool invocation. A separate HTTP client then mints a real
`/api/auth/ws-ticket` using the refreshed bearer and opens the canonical socket
without an Origin header, bearer header, cookie or query token on the upgrade.
It checks the actual runtime profile, instance and authority epoch before sending
the fenced workspace mutation. After the ACK and socket close, another freshly
HTTP-ticketed socket independently attaches and verifies the new directory and
fences with no running work, pending admissions or history.

The finite replay/conflict/stale-revision/stale-generation/foreign-profile/
missing-directory assertions still use the explicitly profile-bound native-owner
verifier. They prove the shared authority's guards, not a complete HTTP permission
or secondary-profile matrix. The independent creation/mutation/readback channels
are not an application retry or an unknown-work recovery procedure.

## Exact tracked matrix and post-exit checks

The updated tracked harness SHA-256 is
`422371e3d70193f040a6d8767babafd9681e7944db8794cea76e1ccd17e88463`.
Four fresh supervised executions return **2 / CORE_PASS_SHUTDOWN_FINDING_OPEN**:

| Pinned source | Transport | Git-bound imported modules |
| --- | --- | --- |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | native | 1,361 |
| `3b421578588243f920886e6fe79a6faa87ea026e` | native | 1,395 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | HTTP / synthetic IdP | 1,367 |
| `3b421578588243f920886e6fe79a6faa87ea026e` | HTTP / synthetic IdP | 1,401 |

Private attempts 193-196 retain hashes, owned child identity, logs, exact
protocol observations and final receipts. All have zero denied boundary events.
Each final read-only SQLite check is healthy and agrees on the single fixture
session's directory/revision/generation and one exact durable mutation ACK. Both
profiles have zero messages, admissions and rooms; the sibling has no session.
PID identity is removed and the original owner-loss/no-ready-fallback checks pass.
No existing or uncertain root is reused, killed or replayed.

All four still record the same real upstream backend-heartbeat write after DB
closure/reopen warning from `tui_gateway.session_reaper._atexit_clear`. The
supervisor keeps it nonzero. No lifecycle diagnostic or assertion is suppressed.
The preceding latest-only HTTP prototype 192 reached the same scoped finding and
is retained separately. The script-policy suite passes 232 tests. Android
product/native/fixture sources are unchanged from the reviewed workspace app;
these are four Python conformance executions, not four Android suites.

Reproduce using the previous probe command and append `--transport http` for
HTTP mode. Use an exact allowlisted clean E: checkout, existing native-Windows
Hermes interpreter/dependencies and a fresh E: root. The imported upstream test
fixture is covered by the retained [Hermes MIT attribution](../licenses/hermes-workspace-fixture-MIT.txt).

## Remaining end-to-end acceptance

Android's actual ManagementClient, NativeLogin callback, canonical channel,
repository/Room receipt and user review still need to be exercised together
against this real runtime. Retain prefix forwarding/TLS, remote-client and
secondary-profile checks, real-IdP renewal, lost ACK/cancellation/owner loss,
busy/permission/unknown-worker cases and the supported/deployed version matrix.
Do not combine this Python proof with separate mock-Android tests into an app
end-to-end acceptance claim. The app still reports a workspace ACK honestly; it
has no new automatic confirmation/readback behavior from this tooling change.

Worker/project identity, authoritative Windows/NAS working-copy binding, files,
reviewed dispatch, phone-to-Hermes handoff, current-source physical tests and all
original [audit/release requirements](../AUDIT_EXECUTION_PLAN.md) remain open.
No production APK, server model/default change or Pi/Spark deployment is made.
