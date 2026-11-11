# Android named-profile gateway connection

## Behavior and boundaries

A live canonical view first verifies the signed-in actor and the selected profile
home, then uses the existing generic ticket. This preserves current default and
custom launch-profile connections. If a complete, supported runtime descriptor
reports a different valid profile home, the unverified socket closes. The client
rechecks the same actor/home, mints one profile-bound Webapp ticket, and verifies
one new socket against the selected home before exposing it.

Only `runtime.describe` with empty params is sent during either handshake.
Malformed descriptors, missing capabilities, an invalid path, disconnects, failed
generic minting and cancellation do not start scoped negotiation. A different
home on the scoped socket cannot start a third handshake. This is pre-session
transport selection, not a retry of adoption, chat, tool work or workspace writes.
Existing persisted unknown-result barriers and reviewed mutation fences remain.

The new request is `POST api/webapp/socket-ticket?socket=gateway&profile=<name>`
with the same native bearer and an empty JSON body. The configured address prefix
is retained. The WebSocket uses only the reviewed ticket-bearing subprotocol plus
`hermes-gateway-v1`: no bearer header, cookie, Origin or credential query. TLS,
redirect and DNS permission rules remain. Ticket minting uses the one-send client.

The gateway-only surface cannot issue a named-profile ticket. Its 404 becomes a
specific English/Chinese/pinyin explanation to use a Webapp address serving the
selected profile. A 401 remains an expired-sign-in error. Other HTTP failures do
not become a default-profile connection. The actor and profile home must still
match after the final handshake.

For the owner's existing setup, the working default profile continues to use its
saved management address. Named profiles need an existing compatible Webapp
listener serving that profile; listing a stored profile does not prove that its
live authority is served. This change creates no listener or public exposure and
does not change the Pi, Spark model, credentials, phone connection or server state.

## Source review

The previous [actual backend proof](2026-11-11-profile-ticket-foundation.md)
verifies native PKCE, one-use profile tickets and genuine profile retirement on
both reviewed execution pins. The app now consumes that ticket path. New static
watches bind three complete modules: Webapp ticket routes, surface policy and
socket kinds, together with eight direct existing auth/profile/socket dependencies.

The original F0 blobs are fetched at the exact historical commit and verified by
Git blob identity before parsing. Three additive source/handler entries and six
complete reviewed variants preserve every previous digest and scope entry. The
historical test corpus projects away only these exact three additions and their
new route; its existing golden values remain unchanged. Production comparisons
never use that projection. New production-mode tests require every one of the
11 dependencies and reject mixed contexts. The independent projection equals the
entire previous baseline exactly, not only selected golden hashes.

All 218 source identities are checked against immutable Git trees or explicit
reviewed absences. The new watches accept the two reviewed ticket contexts while
retaining every existing finding: one changed used handler at 2bd378e and 47 at
3b42157. This is no blanket compatibility waiver or acceptance of moving main.

## Tests and scope

Eighteen shared wire/refusal tests compile for both JVM and Android, preventing
independent fixtures from drifting. They cover matching launch profiles, exact
prefix/actor/home binding, scoped 404/401/403/503, invalid tickets/lifetimes,
malformed foreign descriptors, disconnects, actor/home drift and the two-socket
limit. All observed RPCs must be empty-parameter `runtime.describe`; the fixture
admits no session adoption or workspace work. Controller and large translated UI
checks verify the specific unavailable-profile advice without automatic retry.

The initial build379 fails because shared Kotlin tests were registered only as
Java sources. No test pass is claimed for that build. The corrected candidate
registers the shared folder as Kotlin sources. The initial policy run records
nine failures: old inventory counts/golden projections and synthetic legacy
fixtures did not yet account for the additive watches. Their original digests
remain fixed; the dedicated historical projection restores their prior scope
while new production tests cover the full extension. All 284 policies then pass.
Final build and device results are recorded only after their exact receipts pass.

These fixture cases and the prior Python backend proof are separate evidence.
Actual Android-to-owner named-profile/retirement/Room recovery, production browser
login, reduced-role permissions, active/unknown work and complete clean shutdown
remain acceptance gates. Existing worker/project/files, model/helper/physical,
upstream advisory and signed-release requirements remain in the complete audit
ledger. No new APK release or full audit completion is claimed.

## Preserved hosted CI result

Main45b90db API26 job 130701019352 completes all 1107 declared starts in 3186.772
seconds: 1099 distinct positive methods, eight prerequisite assumptions, zero
assertion failures and no ADB interruption. The strict collector rejects the
assumptions; the job remains failed. They belong to the import-commit, model-family,
live-transfer and process-recovery integration classes, which require explicitly
staged weights, licence/network consent or a prepared interrupted/reboot state.
They are not equivalent to standard no-model instrumentation. Standard registration
must be corrected separately; these gates still require their own actual evidence.

All 17 viewer UI methods and eight history-feedback methods complete positively,
including the prior large-text and workspace/native-IME cases. The captured native
workspace form shows full translated Cancel/review captions above the keyboard.
The older incomplete ADB-loss run stays unclassified, and this later pass does not
prove its root cause. Artifact 13883852166 is retained with SHA-256
`8fd28a9d3328cf3dab5d5cd43830bb43e69710285d95ba553e5a6137ea53f2ed`.
API37 and newer queued runs are not inferred to pass from API26.
## Final measured source and artifacts

Source384 freezes 750 files at digest
`e0cf72a2614db98de2b39d3cb42efccb39be652cb417c9a86df5b58845ebc056`.
The required build passes 987 JVM cases / 95 reports, both lint variants with zero
errors (51 debug / 50 release warnings), app and test APKs. The added warning is
lint's newer-version metadata for kotlinx-serialization-json 1.13.0; dependencies
remain unchanged, and version/advisory review remains a release gate. The original
debug signing key and all 20 native libraries' ABI/load alignments are verified.

- App SHA-256: `f7948187d2f06bb6db4a28c3e84673a520708e5ac56d99f49cc9f6df7d1a4e00`.
- Companion SHA-256: `82c75e7f18d01536721a74606c71ee16a99e3e7c5e399515c30f9a31420794b6`.

Run385 measures six classes per API: 18 profile-ticket cases, one native-login/
gateway-only contract, 13 auth/workspace cases, five storage, two closed-history
and 18 viewer UI cases. API37 passes all 57. API26 passes 56 and records one
failure in the existing large bilingual workspace case: after the strict native
IME/button/caption geometry assertions pass, the single native Cancel tap does
not report `Tapper.Status.SUCCESS`. Its precise non-success status and whether
Cancel was activated are not captured, so that action remains unverified. Do not
infer an input-denial cause or rerun it merely to obtain a green batch.

All 18 ticket cases and the new translated-guidance case pass on both APIs. The
API26 batch remains failed. Every start, distinct positive named completion and
negative code is independently reconciled in profile-device-evidence388.json.
Installed APK hashes and retained package UIDs are verified; original system
font/IME settings are restored exactly. No physical phone or production app is
changed. New source, docs and hashes do not overwrite the failed source379,
policy380 or run385 records.

Both new guidance images are inspected. The text and Close caption fit in all
three enabled languages. The API26 image catches a dimmed/overlapping dialog
transition; settled color/animation and complete accessibility acceptance remain
open. These images do not certify the final visual state. No APK/model, database,
credential, personal chat or private validation file is committed.

Private evidence also retains the original-pin Git blobs (377), additive review
378, static preservation383, build/signature384 and per-method device385 receipts.
The full original and local-AI scope stays active. Next correct standard CI's
external-model registration while preserving its strict zero-skip criteria, then
complete Android-to-owner named-profile/retirement/Room checks and instrument the
retained native Cancel finding. No signed release or full-goal completion claim.
