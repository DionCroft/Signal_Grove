# Named-profile gateway tickets and real retirement: backend foundation

## Discovery and contract

The Android canonical channel currently mints the generic dashboard ticket and
sends an empty `runtime.describe`. On the reviewed Hermes revisions that ticket
binds the launch profile. A named sibling cannot select itself by adding a
`profile` parameter to `runtime.describe`: the actual dispatcher refuses it with
`invalid_params`. The Android binding check already refuses a mismatched home;
this finding concerns named-profile canonical access, not normal bearer chat
history. This chunk adds a repeatable backend proof, not Android support.

On the supported Webapp surface, a native bearer client can request
`POST /api/webapp/socket-ticket?socket=gateway&profile=field`. The server binds a
one-use, 30-second ticket to that profile's home. The WebSocket still upgrades at
`/api/ws`, using `hermes-gateway-v1` plus the ticket-bearing subprotocol. The
credential does not belong in a query, Cookie, Origin or Authorization upgrade
header. The gateway-only surface returns 404 for authenticated scoped minting;
there must be no launch-profile fallback when the requested profile is absent.

The reviewed authentication model maps registered dashboard identities to the
single host owner. A manufactured reduced-role actor would not prove a real
permission boundary. Real reduced-permission acceptance remains open; the
synthetic identity provider here exercises actual native PKCE and bearer routes,
not a production account or browser login.

Authoritative source is read at both immutable reviewed pins:

- [3b42157 Webapp ticket route](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/web_routers/webapp.py),
  [surface policies](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/web_server_surface.py),
  [WebSocket routing](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/web_routers/chat_ws.py).
- [2bd378e Webapp ticket route](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/web_routers/webapp.py),
  [canonical controls](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_controls.py),
  [mutation admission](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/session_mutations.py).

## Repeatable supervised proof

`compatibility_fixture_owner_workspace_tcp.py --probe-profile-tickets gateway`
or `--probe-profile-tickets webapp` runs without Android. It rejects Android API,
controlled host signals and other fixture effect modes. Existing Android modes
still require an explicit API. Use the existing required `--source-commit`,
`--source-root`, `--dependency-site` and a new, unused `--work-root` argument.
The allowed revisions remain the two reviewed commits, not arbitrary source.

The existing parent supervision, scrubbed child environment, immutable Git/blob
checks, loopback-only networking, port-zero-only bind guard, E-only write/SQLite
boundary, deadlines and exact-attempt receipts remain. No dependency install or
production service, credential, configuration, model or phone change is needed.
The Webapp component uses the real API factory with a minimal renderer placeholder;
no renderer asset or complete Webapp startup/commissioning is accepted by this test.

Each case creates one disposable local session per profile through the real
native owner, without inference or work admission. Then it verifies actual
native PKCE using the synthetic IdP, token exchange and bearer identity without
browser cookies on bearer requests. Unauthenticated scoped-ticket minting returns
401. Generic ticket describe binds the launch home, and sibling selection in
`runtime.describe` is refused.

The gateway cases verify authenticated scoped minting returns 404. The Webapp
cases additionally verify:

1. The scoped socket reports the named profile home and current authority epoch;
   its live list contains only that profile's session. A foreign launch-session
   lookup returns `not_found`.
2. Reusing the scoped ticket cannot upgrade another socket: HTTP 403.
3. Real `runner._unserve_profile` retires and removes the idle sibling authority.
   One stale workspace mutation returns code 4001 with exact `runtime_draining`
   message/reason; the global runner remains available. The admission gate runs
   before the deeper retired-authority check, so `runtime_retired` would be the
   wrong assertion for this operation on both pins.
4. A fresh named-profile ticket can still mint while its stored home exists,
   but its WebSocket upgrade returns 403. It cannot fall back to the launch home.
5. A new, explicitly requested default ticket still describes the default home
   and reads the unchanged original session directory.

After child exit the parent independently opens both SQLite stores read-only.
It checks exact session ID, cwd, runtime revision and execution generation;
quick-check must be `ok`. Each store has exactly one session, zero messages,
admissions, rooms, workers, transcript leases and compression locks, and zero
mutation receipts. Matching paths alone cannot substitute for those checks.
Twelve new policy cases reject changed fences, foreign identities/directories,
receipts, additional work and invalid fixture-mode combinations.

## Measured results and preserved failures

Formal harness SHA-256 is
`a9a3926c463bbb2b34ff7824db73404fcd47f9fc70ff4c25b856313289a780fc`.
The independent aggregate receipt is `profile-ticket-proof363.json` in private
validation storage, with exact reports/receipt hashes and all seven table counts.

| Case | Reviewed pin | Surface | Git-bound imported modules | Protocol | Shutdown |
| --- | --- | --- | ---: | --- | --- |
| 358 | 3b42157 | gateway | 1391 | passed | finding open |
| 359 | 3b42157 | Webapp | 1399 | passed | finding open |
| 360 | 2bd378e | gateway | 1358 | passed | finding open |
| 361 | 2bd378e | Webapp | 1364 | passed | finding open |

All four children exit zero with zero denied operations. The supervisor accepts
the protocol and both final stores, then intentionally exits **2**, recording
`CORE_PASS_SHUTDOWN_FINDING_OPEN`. Each retains the existing upstream reaper
cleanup warning about reopening a closed state.db write. This is not a clean
lifecycle pass. Do not change that state or rerun uncertain work for a green exit.

Prototype receipts stay separate: 348 proves the gateway subset; 349 fails on an
unavailable PyYAML import; 351 hits the fixed-port bind guard and cannot establish
the intended surface; 353 rejects the incorrect retired-error oracle. Prototype
355 corrects the oracle using source ordering. Formal cases add unauthenticated
minting, one-use replay refusal and exact error-code/reason assertions. Those
failed attempts were not erased or counted as passes.

The complete Python policy suite passes **275 tests**. Required Android build
results are recorded below after verification. Application and Android-test
sources were unchanged by this backend-only chunk. Prior source336's 69 selected
methods per API remain bound to their original installed APKs, not these probes.

## Next acceptance

Implement and test Android's supported profile-bound ticket path while retaining
prefixes, exact identity/home binding and legacy single-profile compatibility.
Unavailable scoped minting must produce clear advice and no fallback; an attempted
write must retain its unknown-result barrier and never replay automatically.
Add source watches for newly consumed routes without replacing historical digests.
Then run real Android/profile-loss and Room recovery cases, not just a Python
client. Production/native browser login, reduced-role permissions, active/unknown
work and complete owner lifecycle acceptance remain open.

The complete [audit ledger](../AUDIT_EXECUTION_PLAN.md) remains authoritative,
including Windows worker/project/files and reviewed dispatch, integrations,
rooms/jobs, physical/local-AI acceptance, dependency/native advisory coverage
and signed release. No APK version or release changes in this chunk.

## Required build and Android regression checks

Source362 freezes 745 files at digest
`c3578168a8146e641edcb0c53202caa72822f1a904d8c7a061e692e762efeb69`.
All required build tasks pass: 968 JVM cases / 94 XML reports, both lint variants
with zero errors (50 debug / 49 release warnings), debug app and test APKs. The
original debug signer and all 20 native libraries' ABI/load alignment checks pass.

- App SHA-256: `57ffcfdcf60754ac4fdb3c961e1bee6ed7bc8d4f749e726d2bfefdb9b3eb102e`.
- Companion SHA-256: `f1837562d2591c7bf32ec36d9a66beb782bb2dc3d9cdb4f7310b7e2f5cb1a43d`.

Run367 passes 20 distinct methods per API on the fresh APKs: 13 authentication/
workspace contracts, five Room storage cases and two closed-history contracts.
Each exact method starts and completes positively with no skips; installed hashes
and unchanged app/test UIDs are verified. API26 runs at font 1.3 and API37 at 1.0;
original system font and native-IME settings are restored exactly. This is a
focused fixture regression, not Android acceptance of the backend ticket probe.

Independent build368 comparison proves all app/build inputs match source336;
app DEX/resources/manifest match and the test APK is byte-identical. The 20 native
libraries rebuild with separate hashes. Earlier 69-method-per-API acceptance stays
bound to source336's actual APK. The new contract and closing state/validation/
ledger/changelog edits follow the build; only documentation changes afterward.

The 275 policy tests, 15 model-status extension tests and checksum policy (495
components / 1071 artifacts, no blanket trust) pass. No new dependency is installed.

Main `45b90db` hosted Android run `43069326933` has a passing verify job; device
jobs `130701019291` (API37) and `130701019352` (API26) remain running at this
observation. Do not cancel or restart them merely because they are slow. Full
hosted device acceptance, the older workspace/IME finding and ADB loss remain open.
Compatibility run `43069326927` passes parser/registry/sidecar jobs and fails the
upstream used-contract gate. Its verified main artifact observes commit `8f8696f`,
126 changed source modules and 90 changed used handlers; that source has not been
accepted for execution or rebaselined. Exact post-push workflow receipts are kept
separately. No full compatibility pass or new APK release is claimed.
