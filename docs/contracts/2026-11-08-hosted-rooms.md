# Hosted room native foundation

This development chunk adds a pure internal Kotlin projection for room capability,
page and state responses. It does not expose a room control or send a network
request. Membership, lead/purpose/notes edits, grants and cron delivery remain
separate implementation work. An advertisement is not a permission or proof that
an execution worker is available; driver observations never establish completion.

## Official source boundary

The reviewed main source is
[21ae0e1](https://github.com/NousResearch/hermes-agent/tree/21ae0e1238fdf466fa52a85a6ebf75d6899e49da).
The declared eleven source paths include the group wire schema, standalone and
canonical group controls/forwarding, peer/member helpers, room storage/common/
context/discussion and the official Desktop canonical-group design. All present
bytes are independently bound to immutable Git blobs at three pins: main11,
stable2bd10 and priorbf0203a8. Git-tree absences remain explicit: stable has no
session_group_peers.py; prior also lacks the forwarding and room-context modules.
Those absences grant no compatibility waiver or invented fallback.

The current native schema accepts protocol2, room identifiers up to128 ASCII
identifier characters, names200 Unicode code points and stored rosters up to128
rows. These are read projections; new discussion creation has its own stricter
roster validation and seatability requirements. Known fields are typed and bounded;
unknown/raw target URLs, catalogs, grants and event/prompt payloads are not retained.
Legacy member rows may lack normalized identifiers. All collections are immutable,
all scope/home bindings are explicit and diagnostic strings are redacted.

Timestamps accept finite fractional seconds. Context remains unknown when absent;
notes are bounded to4096 UTF8 bytes and purpose to500 Unicode code points. Routing
accepts everyone/lead/auto without inferring permission to change it. A disbanded
row is metadata, not proof that a running task stopped.

## Pagination is a server contract

The canonical actor-visible list can return a continuation offset that skips
hidden rows. The native projection preserves that exact offset; it never derives
it from visible count. The forwarding server can merge gateway, standalone and
handed-off root-store rooms on the first page. That page may exceed a requested
single-store limit: the native bound is1500 rows at offset0,500 thereafter.
Duplicate identities and malformed/nonadvancing/out-of-range cursors are refused.
Paging/reordering concurrency and any cross-page reconciliation still need actual
server and UI evidence when the authenticated repository is implemented.

## Validation and remaining work

All required build tasks pass797 unique JVM cases in79 XML reports, including all
prior775 and22 new room regressions. Debug/release lint both report0 errors.
Three pure projection methods pass on API26, largeAPI37 and compactAPI37:9 runtime
executions. They check fractional JSON timestamps, filtered/merged paging,
supplementary Unicode and UTF8 byte boundaries without activities or network.
Original keys, preferences, geometry,28 legacy cache files and641 frozen build
inputs match. The existing Python sources remain byte-identical to their accepted
213-policy/15-observer checks. The signed d0 beta.8 assets and owner phone are
unchanged. No real-room/provider/worker/TLS/owner-phone conformance is claimed.

Next implement authenticated inventory/detail reads with exact API/actor/profile/
home/runtime ownership, capability refresh and per-opening fencing; extend source
drift guards; exercise isolated actual stable/prior/current source transports and
pagination; then add the translated native room browser. Mutations need their own
fresh review, effect explanations, durable exact-key receipts and unknown-outcome
inspection. No read, refresh or failed connection may automatically replay work.
