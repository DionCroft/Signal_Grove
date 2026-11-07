# Native management client over a real Hermes TCP fixture

This stage connects the production Android NativeLogin and ManagementClient to
the actual pinned Hermes app, native broker, auth gate and scoped profile/MCP
readers over a process-owned loopback TCP listener. It is separate from the
in-process ASGI21+21 matrix. Local acceptance is recorded in VALIDATION; exact-head hosted gates remain separate.

NativeManagementContractTest has one JUnit method and13 semantic checks. The
normal full suite uses a local HTTP fixture; an explicit instrumentation argument
selects a separately owned real Hermes fixture. Both modes execute the same
client/callback/identity/profile/refresh/refusal assertions, with no skipped or
empty test branch. The real server source and wire observations require their
own bound receipts; a mock run is never reported as actual Hermes acceptance.

The browser driver is an in-memory OkHttp cookie jar, not Chrome/Firefox. It
permits only the synthetic IdP's same-origin callback and the exact NativeLogin
loopback destination. A wrong-state request must return400 before the real
callback succeeds. Exchange, actor verification, profile projection, explicit
worker-profile MCP inventory, refresh, used-code refusal, invalid bearer and
invalid refresh all use the shipped client classes. Saved encrypted credential
preferences must match before/after; raw values are never exported. No app
preference, user conversation, receipt or project mutation is requested.

The optional real endpoint must be HTTP127.0.0.1 on an explicit unprivileged port
under exactly /dashboard/, with no userinfo/query/fragment. Only the controller's
fresh, owned fixture is eligible. Run only on preserved, verified emulators,
never the owner's phone. Android uses an explicitly reviewed ADB reverse for the
exact fixture port; remove that exact binding after its command finishes.

scripts/compatibility_fixture_native_auth_tcp.py runs only on Windows using an
existing interpreter with -I -S -B.43 source Git blobs per stable2bd/priorbf pin
are verified, along with clean HEAD. It uses sterile E homes, upstream synthetic
HMAC IdP and fixture SessionDBs. The ephemeral listener binds127.0.0.1:0 and stops
within10..600 seconds or an owned stop marker. It is a test fixture, never an
app backend, daemon deployment or general authentication proxy.

A small fixture-only adapter models a trusted path-stripping reverse proxy:
it accepts /dashboard/, supplies X-Forwarded-Prefix, and forwards only reviewed
auth/profile/MCP routes to the unchanged real app. Bootstrap callbacks may use
their own cookies; protected app reads must carry bearer auth with no Cookie.
Observations contain route/method/profile/status and booleans, never codes,
verifiers, cookie values, bearer values or response bodies. All other requests
are refused by the fixture adapter. This bounds the test; it does not certify
arbitrary proxy configurations.

After the two read-only source preflight commands, every subprocess remains
blocked. The13 reviewed metadata attempts are still denied and classified.
Reads/writes/databases/sockets retain the existing sterile probe boundaries;
Python audit hooks are defense in depth, not an OS security sandbox. All
actually imported upstream files receive SHA-256 inventories. Seeded history
must remain unchanged after teardown.

The first host TCP pilot stopped cleanly before any request because the Windows
venv launcher PID differed from its Python child. That failed receipt remains.
The controller now starts the already-installed base interpreter directly,
with the existing dependency site explicitly supplied, so Popen/ready PID match.
The next host smoke passed eight TCP/prefix/cookie/PKCE/bearer/profile/MCP/refresh/
replay checks with0 unexpected denials. The subsequent repository/native variants are accepted below.

No TLS/certificate, external browser, password form, real IdP, deployed Pi,
canonical session adoption, project file or worker acceptance follows from this
fixture. Owner phone, OEM lifecycle and exact release retention remain separate.

The first three repository-native mock runs failed the strict explicit-IPv4
base-URL precondition before any client authentication. MockWebServer returns
a localhost URL even when bound to127.0.0.1. Both fixture base and synthetic IdP
callback now explicitly use127.0.0.1; the strict endpoint assertion is unchanged.
Those failed receipts remain; the corrected native cohort passed as described below.

## Local acceptance — 7 November 2026

One distinct JUnit method passed nine invocations: local mock, actual stable
2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19 and actual prior
bf0203a794a07aac3ff764972ab9087baa4e4f4e, each on preserved API26/large37/compact37
profiles. Each invocation executes13 semantic checks. The six real fixtures
independently record the expected11-request sequence and unchanged SessionDB
history. All928 stable/866 prior imported project-module hashes were rechecked;
all13 expected metadata subprocess attempts remained blocked, with0 unexpected
denials. Each owned process exited0 and its exact reverse binding was removed.

A stable/API26 controller attempt read an empty readiness file while it was
being written. It stopped its owned server before any request and remains failed.
A new controller waits within the existing45-second startup bound for complete
JSON, verifies the same exact PID/source/root/listener, and uses fresh evidence.
It does not replay an unknown request or weaken the native assertions.

Required incremental Android tasks passed;608 retained JVM cases and all71 prior
raw reports are unchanged. Both lint reports have0 errors;180 policy checks and
six CLI refusals pass. The original-key production debug app remains
a879c78b17ab38ef52da8f2f936dbe0e44aed123fc16bdc0399b1e11cea3aeaf.
The new original-key companion is
7514fbc080d16c25b4e6439a32efb19907ec4c522db7c9f271f7c92e9fefee6c.
The595-input local source freeze closed after all evidence checks.
Normal full CI still runs the same method in local-mock mode. Actual-server
acceptance requires the separate source-bound controller/server receipts.
