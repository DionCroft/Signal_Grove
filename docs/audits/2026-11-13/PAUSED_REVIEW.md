# Signal Grove paused review - 13 November 2026

Resume follow-up: the owner subsequently resumed the existing goal with four
explicit [acceptance priorities](../../GOAL.md). The pause/publication entries
below are historical; [current investigation evidence](../../contracts/2026-11-13-resumed-acceptance.md)
records the resumed work without discarding prior failures.

Publication follow-up: the owner subsequently requested beta.9 for download.
It is now a [public experimental preview](../../contracts/2026-11-13-beta9-public-preview.md)
with the incomplete checks disclosed. The wider goal remains paused. The
evaluation below is the original stopping-point snapshot; its then-unpublished
beta.9 status is historical and does not describe current download availability.

The owner requested finishing the current check, then pausing the goal for an
evaluation. The thinking-aware cancellation check is finished, original phone
networking is restored and this review records the stopping point. The original
audit objective and all unresolved acceptance rows are retained. Resume only
when the owner requests it. No new implementation or release work starts while
paused; already-triggered GitHub jobs can continue independently.

## What is delivered and what is installed

| Layer | Verified state |
| --- | --- |
| Published stable | [1.0.0](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.0.0) |
| Published preview | [1.1.0-beta.8/code111](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.8); this remains the downloadable preview |
| Development candidate | Beta.9/code114, unpublished; no new Release or public APK is created by this checkpoint |
| Source delivery | Fixture/evidence commit `6034eadc761d45ca4bca5c70db202b0ce84eaa17` is pushed to main and the feature branch; this review is a subsequent documentation-only checkpoint |
| Normal phone app | Original production package/key, code112; its APK and UID are unchanged |
| Phone-tested debug app | Code114, source `0ee08e37c90e009a70bb290301c81433a11517a5`; app SHA-256 `5e1f970a985c5ee51a6a51826e46d2a107bcbaacbbf143b58cc33c7203a8af85` |
| Latest test companion | Source6034ead; SHA-256 `cdb0570164ff2590ce4e8d9ca96a93880531e4395c43f340c76dafe45d3045ed`, 7,922,095 bytes; original debug key and UID |

The normal/debug packages and their histories stay separate. A source push or
CI artifact is not a published installable release. The latest companion build
compiles only the fixture package, with native dependencies; it does not claim
a new production APK or new JVM/lint run. Its 676 other code inputs match the
unchanged app source. The prior complete signed builds remain separately bound
to source0ee and source1180.

## Accomplishments

- The core companion has Signal Grove branding, pixel themes/motion, multilingual
  controls, private connections and an approval-based signed GitHub updater.
  Existing conversation/history and stored project details were observed after
  an earlier production upgrade. Automatic model selection follows a freshly
  verified serving model; conversation/default/serving identities remain distinct.
  Reasoning and service choices use presets.
- Optional phone AI is implemented: separate offline chat/history, streamed
  output, explicit load/unload, compatible GGUF import, verified recommended
  model download/resume and licence/storage/context/deletion controls. New
  unsent drafts can be deliberately routed locally; uncertain remote work is
  never replayed. LFM3 and Qwen2.5-0.5B have actual native phone evidence.
  The architecture allowlist does not certify every GGUF file or phone.
- Static setup guidance works without a model. The optional offline helper
  selects reviewed versioned checks for setup, networking, authentication,
  models, history and projects. It provides advice and source-backed checks;
  it does not acquire Hermes tools, credentials or permission to execute actions.
- On the unchanged source0ee debug APK, seven selected native methods now have
  positive evidence across explicitly identified companions: garden answer-only
  streaming; bounded metadata/cancellation; six-topic helper plus Chinese;
  generation/cancellation/recovery/reload; Qwen import/chat/history/delete;
  LFM3/Qwen switching/history binding; and atomic import cancellation/handoff.
  They run individually with actual offline/foreground checks. This is not
  acceptance of all twelve external-model/prerequisite-dependent methods.
- The final 27.984-second method verifies Stop before any visible answer and
  during a clean monotonic answer stream, exact recovery, coroutine cancellation,
  context-overflow refusal, unload, 512-context reload and exact completion.
  Original 48/96-token probes still expose their known limits; separate bounded
  completion calls are explicit fixture requests. Product defaults/caps and
  retry behavior are unchanged. All prior failed runs retain failed status.
- The companion-only upgrade retains all23 protected metadata files byte-for-
  byte, both original models and all three UIDs. After the test,20/23 files are
  byte-identical. Four SQLite quick checks show unchanged core rows/schema,
  unchanged WorkManager work/other tables and only an advancing
  `Preference.last_force_stop_ms`. Encrypted snapshots verify; exact temporary
  plaintext DB copies are removed. Inaccessible production-private bytes are
  not claimed. No owner data is restored/deleted and no phone reboot occurs.

The final test log SHA-256 is
`50b68ea36501da2aa96513425fdbd4f26dc843184787d02176dbc98a7660a4bb`.
All17 diagnostic frames are checked against a closed stage sequence before
the unchanged strict case parser accepts the single inventoried method.
Airplane mode off, Wi-Fi connected, Bluetooth unchanged and the original
default-network state are restored. Awake15 stays unchanged. No phone test,
model CLI or local build remains running at this checkpoint.

## Validation figures and their limits

| Evidence | Accepted scope |
| --- | --- |
| 1,011 JVM cases /97 reports | Both complete unchanged-product candidate builds; no failures/errors/skips |
| Lint0 errors,51 debug/50 release warnings | Both prior complete builds; warnings retained |
| 422 policy checks | Exact source6034 fixture/documentation chunk; final review additionally checks links, source invariance and secrets |
| 1,135 API26 /1,136 API37 | Complete source0ee standard emulator suite: four disjoint raw partitions, exact source/APK binding, five ZIP digests and hosted-summary reproduction |
| Seven selected current-app native methods | Physical ARM64 Android17/API37, separately identified companions; not whole external-model or final production acceptance |
| 150 Maven versions; no known matching OSV advisories | Fresh13 November indexed runtime queries plus pinned-native query and positive control; not full security certification |
| 95 dependency inputs /32 native files /183 SBOM components | Source0ee local release inventory; later final assets need their own inventory |
| 14 pure route-verdict /16 adapted binding cases | Private stable3b42157 component probes;19 critical guards and298 loaded modules in the latter; synthetic client/six downstream adapters and explicit absent-proc/IPv6 fixtures; no live model/client or blanket SDK acceptance |

No throughput, sustained thermal/battery, universal hardware or complete Hermes
feature-parity certification follows from these figures. The LFM3 download is
1,049,615,680 bytes and remains separately licensed rather than bundled.

## Retained failures and current release blockers

1. Source1180's [Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43343517764)
   has three successful device partitions and one failed API26 shard. Its
   `Milestone03Test.demoClarificationChoiceIsReviewedBeforeSubmission` cannot
   find the multilingual Try a Demo question control before `performScrollTo`.
   The raw run reports598 cases/one failure. The15,380,029-byte failed artifact
   verifies as `5a12021aae8d5a8b4592f32f3f90b92474fc7901172c3978283cdb900c0d00e2`.
   No partition manifest is published; the union verifier correctly refuses.
   This is not relabelled flaky, retried blindly or waived. Root cause and a
   faithful readiness fix need investigation after resume.
2. Source6034 [Android CI](https://github.com/DionCroft/Signal_Grove/actions/runs/43353521950)
   is in progress at observation; [compatibility](https://github.com/DionCroft/Signal_Grove/actions/runs/43353522087)
   failed and the [worker workflow](https://github.com/DionCroft/Signal_Grove/actions/runs/43353521969)
   passed. Its full raw acceptance is not inferred. This documentation push can
   trigger another run; pending jobs are not treated as completed or cancelled.
3. The last fully inspected upstream artifacts retain40 changed used handlers
   on stable3b42157 and101 on inspected main415fa33. These are review findings,
   not confirmed bug counts. Complete semantic/transitive review and the real
   SDK shutdown-cleanup finding remain open. No compatibility baseline/source
   waiver or immutable SDK checkout changes.
4. Final code114 original-production-key upgrade, retained settings/history,
   live Hermes and offline acceptance, exact passing release-source CI,
   current assets/checksums/certificate/SBOM/provenance and verified public
   production-APK download remain required before publishing beta.9.

## What remains in the original audit

| Area | Required work |
| --- | --- |
| Real Hermes interoperability | Complete supported-version HTTP/WebSocket/browser/TLS, sign-in renewal, actor/profile isolation, approvals, images, reconnect/recovery and representative integration acceptance; retain shutdown warning evidence |
| Projects/workspaces/workers | Finish explicit chat/workspace adoption, identity and registered-root/repository/folder/file/NAS proof; review dispatch/reassignment with approvals and exact receipts. Stored project metadata alone is not execution-location proof |
| Kanban | Real status/model-override transitions, worker controls, event streams, attachment contents, pins and transfers |
| Automation and rooms | Typed advanced job editing/run/results/logs; owner forwarding/paging, membership/grants/delivery and supported goal/heartbeat/loop controls |
| Integrations/configuration | Remaining plugin policies/bundles, channel/webhook pairing and typed memory/persona/context/config/reset controls |
| Phone quality | Exact notification targeting, multi-connection behavior, background/Doze/battery/endurance, Bluetooth/audio, accessibility/performance and Mermaid/math assessment. This phone still lacks on-device dictation support |
| Release/community | Final-source emulator/physical/upgrade gates, dependency/advisory refresh as needed, final support/contributor documentation and public APK verification |

Keep every unresolved [audit ledger row](../../AUDIT_EXECUTION_PLAN.md). Preserve
the eight completed worker receipts and uncertain outcomes; do not restart that
daemon or replay unknown work. Pi, gateway, server models/defaults and credentials
are unchanged in this checkpoint. All tools/scratch/private evidence remain on
the verified execution host. The original fixes and security boundaries remain
release gates throughout.

## Recommended next stage after the owner resumes

First reproduce and diagnose the exact API26 Demo clarification failure using
its retained source/log/state, then restore complete exact-source standard CI.
Keep all assertions and failure evidence. Next review the upstream handler/
dependency and shutdown gaps, complete final production114/live/offline retention
gates and assess a beta.9 release against the full audit ledger. Do not describe
remaining project/worker/automation features as finished to justify publication.
