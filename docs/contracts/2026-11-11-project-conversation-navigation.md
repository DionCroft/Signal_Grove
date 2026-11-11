# Project conversation navigation

## Behavior and boundary

In Hermes management → Host projects, read stored projects, open details and use
Read project conversations. This explicitly reads `projects.project_sessions`
with the frozen profile, exact `project_id` and `session_limit=500`. The selected
stored project is freshly re-read before and after projection. Actor/home checks
surround the read. Archived projects cannot request this view. Missing grouped
metadata does not delete or reinterpret the saved project.

An advertised canonical authority requires its authenticated runtime descriptor
to match the selected profile home. A valid named-profile negotiation uses the
existing single scoped ticket handshake; failure never falls back to the launch
profile. An explicit legacy non-advertisement keeps the documented profile-param
read route. Malformed advertisement fails closed.

Each row retains only bounded navigation metadata. `preview`, transcript and
arbitrary extra JSON never enter the DTO. Exact stored project ID/name/path,
explicit project kinds, non-archived rows, exact profile stamps, unique IDs,
consistent counts and finite timestamps are validated. Reported matching IDs
without an upstream repository/lane placement remain counted but do not acquire
invented opening targets.

Hermes considers the most recent eligible sessions across the profile: with
messages, non-archived, top-level, excluding cron/kanban/oneshot sources. Older
project matches may be absent. This is a bounded global window, not exhaustive
project pagination. `include_discovered=False` avoids the project's repository
cache reconciliation/discovery path. The app does not call `projects.tree` or
`projects.discover_repos` for this read.

Review opening this conversation verifies a fresh canonical stored inventory
and selects only the exact session ID. It does not guess an alias, resume or
read a saved cursor automatically. Existing explicit consent remains mandatory
before heartbeat/resume/replay. Target, profile, home, context generation and
row references fence stale UI callbacks. Closing or changing profile clears the
navigation state; invalid home clears project state. Read errors appear inside
the details dialog. Project metadata never establishes an execution worker,
changes cwd, dispatches a task or grants host file access.

## Official source review

Reviewed exact upstream commits: `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` and
`3b421578588243f920886e6fe79a6faa87ea026e` in NousResearch/hermes-agent.
The consumed modules are `tui_gateway/methods_config.py`,
`tui_gateway/methods_projects.py`, `tui_gateway/project_tree.py` and
`tui_gateway/contracts/projects_pets.py`. Original F0 blobs were fetched, then independently checked against the official
Contents response's bytes, size and Git-blob identity. Four new watches
and their exact 19-source contexts are additive; all prior source/handler hashes
and mobile scope entries remain intact. Historical synthetic vectors exclude
only these four new watches; production comparisons never use that projection.
The extended 222-source comparison retains the exact prior 1 / 47 findings.
No moving upstream ref or whole revision is waived.

## Validation and limits

Source453 digest: `dd37e717abbce1097f908ff69c6cd817d08e865228685ad776db78bc50d50255` (758 files).
Required unit/lint/debug app/test tasks pass: 1004 JVM methods in
96 reports; lint debug/release has zero errors, with
51 / 50 warnings.
Policy suite: 300 passing tests. Original debug certificate retained; all 32
packaged native libraries verified for ABI/alignment. No GGUF is packaged.

API26 and API37 each pass exactly 32 distinct starts/positive terminals, with
zero skips/partial/failing outcomes: 21 shared profile-ticket/project wire cases,
9 management project UI cases, 1 existing project gateway contract and 1 new
large-caption selection/consent UI case. UI fixture data is synthetic. Each
in-place upgrade retains exact app/test UIDs and all protected file hashes
before tests. Font/IME/animation settings return to their exact original values.
Controlled animation settings do not prove normal-animation or physical UX.

Private host artifacts remain outside Git: `local-helper-source453.json`,
`local-helper-build453.json`, `local-helper-proof453.json`,
`project-navigation464-api26/`, `project-navigation462-api37/`,
`project-policy457.log`, `project-source-review445.json`,
`project-original-blobs456.json`, API26 `disconnect-reconciliation461.json`,
`project-api26-readiness463.json`, `project-navigation-proof465.json` and both
`project-source451-canonical-*.json` reports. Raw instrumentation is strictly
parsed and independently checked against the selected method sets. Source450's
failed build is retained and never accepted. Retained API26 preflight454 and
unknown attempt459 preserve a 40.466-second cold launch and observed guest
reboot; its new explicit launch after stable-boot verification takes 1.564 seconds.
API37 preflight459 never starts instrumentation: the host focus query needs
`dumpsys window displays` on this API. A further API26 precheck462 observes null focus after configuration and stops
before instrumentation; later batch464 starts with exact focus in its first
read-only sample. API37 batch462 uses the observed platform query. Both use
unchanged APKs, protected-upgrade evidence and separate results. No data
wipe, reinstall retry, remote work replay or failure waiver is involved. The cold
launch/reboot cause and broader lifecycle/performance acceptance remain open.
Actual-owner execution of this new
read path is the next stage, not inferred from the wire fixtures or prior owner
session/cache proofs. Worker/root/file/dispatch, final physical/model/lifecycle,
production auth and signed-release gates all remain required; beta.8 is still
the published preview.
