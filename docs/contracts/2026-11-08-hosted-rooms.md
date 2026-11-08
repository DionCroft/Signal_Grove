# Hosted room browser and read contract

Signal Grove now provides a foreground, read-only room browser in Hermes
management. It reads the selected connection and profile, shows stored room
members and context, and distinguishes source update time from client read time.
Refresh, disbanded filtering, explicit continuation and return to the first page
are bounded user actions. Lifecycle stop closes the reader; resuming does not
silently reopen, poll or retry. Room controls and decisions remain in Hermes.

## Official source boundary

The implementation was reviewed against immutable official
[main21ae](https://github.com/NousResearch/hermes-agent/tree/21ae0e1238fdf466fa52a85a6ebf75d6899e49da),
[stable2bd](https://github.com/NousResearch/hermes-agent/tree/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19)
and [priorbf0203a](https://github.com/NousResearch/hermes-agent/tree/bf0203a794a07aac3ff764972ab9087baa4e4f4e).
Source review covers the group wire schema, handlers, forwarding, room stores,
membership/context/discussion, imports, standalone service, driver/activity and
owner-forward helpers. Source bytes are independently matched to Git blobs.
A capability advert does not grant permission or establish worker readiness.

Each read verifies authenticated actor identity and the exact profile home before
and after one fresh, ticketed WebSocket. The negotiated gateway subprotocol stays
strict. Capabilities are read before and after the requested list or detail; an
authority/protocol change or withdrawn read method holds the read. The repository
preserves API path prefixes, verifies token actor/expiry and does not put bearer
authentication or browser cookies on the ticketed socket. It does not adopt a
canonical session, change preferences or submit work.

Detail must refer to the exact immutable row selected from the current listing.
Its ID, room authority and epoch must match, and its revision cannot go backwards.
A forwarded room can legitimately belong to an authority different from the
serving gateway. Late results from a different connection, actor, token, profile,
home or view-model generation are refused. Update guard leases are independent,
so an old completion cannot release a newer read's installation guard.

## Native schema and pagination

The schema accepts protocol2, identifiers up to128 ASCII identifier characters,
names up to200 Unicode code points and stored rosters up to128 rows. Creation has
separate, stricter seatability and roster rules. Known fields are typed, bounded
and immutable. Raw target URLs, grants, catalogs, prompts and event payloads are
not retained. Legacy member rows can lack normalized identifiers. Diagnostic
strings are redacted. Finite fractional timestamps are preserved; purpose is
bounded to500 code points and notes to4096 UTF8 bytes.

Canonical actor-filtered continuation offsets can skip hidden rows. Signal Grove
uses the exact server cursor, never visible count. The standalone forwarder can
merge gateway, standalone and handed-off root-store rooms on page0; that page can
exceed the requested single-store limit. Bounds are1500 rows at offset0 and500
thereafter. Duplicate identities and invalid/nonadvancing cursors are refused.
Only one page is retained. Filter changes restart at0 and discard the other
filter's data; the UI explains ordering and the explicit refresh boundary.

A same-context read failure labels the previous timestamped snapshot and offers
explicit refresh. It never displays raw exception content or retries on its own.
Missing context or driver observations remain unknown. Working, blocked,
disbanded, disconnected and no-work observations never establish execution
completion. Missing detail support disables View with an explanation. Full room
names remain in the scrolling detail body when the dialog title is abbreviated.

## Source drift guards

The checker now watches205 sources, adding16 room-related modules. All original
189 F0 source entries, handler values, mobile scope and prior review entries are
preserved by golden assertions. Fourteen original room handlers opt into complete
original-source guards; reviewed present handlers use a complete19-source vector
including the shared gateway, canonical controls and native authentication.
An unchanged handler AST cannot hide a changed helper in that opted-in context.
The new optional mechanism does not change unlisted targets' baseline policy.

Five new requirements were first run against the unchanged old checker: four
failed as expected while the original/reviewed positive modes passed. The new
implementation passes all five. Four further regressions enforce original189
preservation, complete mode vectors, exact historical absences and parse-only
permission/wire drift. All earlier policy assertions remain. Six initially mixed
synthetic fixtures were completed to one coherent reviewed room mode before their
existing negative mutations; no old assertion or comparison scope was removed.

Full205-source comparisons pass at immutable stable2bd and main21ae. Prior
bf0203a was reviewed only as the19-source room/dependency subset, not a full205
compatibility claim. Missing peers/context/forward/import modules are recorded
only for the exact verified historical trees. No blanket commit waiver or
unrelated cron-source absence was added.

## Validation and limits

All five build tasks pass: testDebugUnitTest, lint, lintRelease, assembleDebug and
assembleDebugAndroidTest. There are831 unique JVM cases in81 XML reports: all797
previous cases plus16 repository and18 controller regressions. Both lint reports
have0 errors. The full policy suite passes222 cases; the runtime-model observer
sources remain byte-identical to their accepted15-case revision.

Thirteen exact Android methods pass on each preserved API26, largeAPI37 and
compactAPI37 profile:39 executions. Nine UI methods cover paging/filtering,
details, held errors, missing context/status, lifecycle and large trilingual
text. The native MockWebServer contract proves six read RPCs on two ticketed
sockets with four profile-home checks, no credentials changed and no adoption
or work submission. Three existing Android JSON/Unicode projections also pass.
Nine synthetic screenshots were retained and visually inspected. Scrolling keeps
View, Back and Close reachable. All651 frozen inputs, original keys, preferences,
geometry,28 legacy cache files and the separate beta.8 assets match.

These are native UI, mock transport and immutable static-source checks. Actual
Hermes room/profile/pagination transport conformance remains required, including
historical compatibility and runtime forwarding behavior. This chunk does not
certify the owner's phone/Pi, real TLS, inference or workers. Full hosted gates at
this exact application revision remain separate. Earlier foundation CI failed
twice downloading Temurin24 withHTTP504 before compilation; both logs are retained
and no further blind retry was started. Earlier privacy API26 has a successful
hosted job awaiting raw acceptance; its specific API37 retry is running.

Next exercise isolated actual immutable Hermes room transports and same-ID rooms
across profiles, actor-filtered/merged pagination and ownership changes. Context,
membership, grants and cron delivery need separate fresh contract review,
explicit effects/approvals, exact durable receipts and unknown-outcome inspection.
No read or failed connection may replay work. Every other audit row remains open.
