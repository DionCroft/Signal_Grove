# Natural-output phone checks and completion-budget follow-up

13 November 2026. Signed beta.9/code114 candidate source is
`0ee08e37c90e009a70bb290301c81433a11517a5`. It is on main and remains
unpublished. The normal production phone app remains code112. A separate debug
app and test companion exercise the candidate; no original conversation is reset.

## Build and release inputs

The clean 869-file source builds all required debug/test/production artifacts.
All 1,011 JVM cases in 97 reports pass, with no failures, errors or skips. Lint
has zero errors and 51 debug/50 release warnings. Original package identities
and signing certificates, all four native ABIs, 64-bit 16-KiB alignment and
uncompressed/aligned DEX verify. Models remain separate licensed downloads.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Production | 71,052,938 | `1837ea8f2ea94abcee63c38f630e14e72f92902fab9963d5c98756dff501f832` |
| Debug | 92,608,818 | `5e1f970a985c5ee51a6a51826e46d2a107bcbaacbbf143b58cc33c7203a8af85` |
| Test companion | 7,912,059 | `5cfb4813a083a1843e645a367ec59e330665ff278c0ef6c2a604b5b325874974` |

Fresh 13 November OSV requests cover the 150 exact resolved Maven versions and
the pinned llama.cpp commit. No known matching indexed advisories return; a
known-vulnerable positive control verifies the Maven query. This is bounded
advisory coverage, not a full security audit. The private release inventory
verifies 95 dependency inputs, 32 native files and a schema-valid 183-component
CycloneDX 1.6 SBOM, public certificate, checksums and local provenance. None is
published. The signed production APK still needs final in-place phone acceptance.

## Actual offline application checks

Each case runs separately on the owner's ARM64 Android 17/API37 phone. Exact
installed debug/test hashes, physical identity and the staged licensed model
hashes verify first. Airplane mode, disabled Wi-Fi and no physical/default
network verify before, during and after observed execution. A single explicit
empty-activity foreground launch accompanies each inference case; awake setting
15 stays unchanged. No phone restart, clear, uninstall or remote model change occurs.

| Native case | Result | Host-observed command seconds |
| --- | --- | ---: |
| Garden answer-only streaming | Pass; clean final text and unchanged no-marker/stream assertions | 8.232 |
| Bounded metadata scan/cancellation | Pass | 2.570 |
| Qwen import/chat/unload/history/delete | Pass in isolated roots | 4.152 |
| LFM3/Qwen switch and history binding | Pass; mismatched chat refuses, original histories survive isolated model deletion | 10.714 |
| Atomic import cancellation/Main handoff | Pass; committed model visible without reopening/loading | 5.191 |
| Six helper topics plus Chinese | Failed: all six reviewed selections finish, then the 48-token Chinese request reports TOKEN_LIMIT |
| Generation/cancellation/recovery/reload | Failed: first exact reply finishes, then the independent 96-token second reply reports TOKEN_LIMIT |

These are five positive cases and two retained failed cases, not a complete
external-model gate. The six helper selections remain observed successful stages
inside a failed combined case. Failed earlier batch/garden runs remain failed.
Only closed, validated diagnostic frames are separated before the unchanged
strict case parser checks individual native results; arbitrary status2 output
cannot silently pass. Timing is not sustained throughput or battery certification.

## Measured test correction

Standalone diagnostics use the exact candidate native libraries, same pinned
model, request text, deterministic sampling and 2,048 context as the affected
test. They do not access app data or contact Hermes. Chinese reaches EOG at
75 total tokens with a 96-token allowance; the independent second reply reaches
EOG at 108 with a 384-token allowance. The original 48/96 allowances stop at
their respective limits without markers or tool calls. Separate 4,096-context
second-reply controls are labelled separately and yield the same 108-token count.

The source follow-up changes only the physical fixture and documentation.
Every original prompt and short allowance remains. COMPLETE still requires the
exact expected reply. TOKEN_LIMIT must have clean output that is a prefix of
the exact expected reply; then a separate explicitly bounded fixture call must
complete exactly. Chinese uses 96 for that separate call; the second reply uses
the existing app default of 384. Existing cancellation, recovery, overflow and
reload assertions remain. App code, limits and automatic-retry behavior do not
change. The new companion must be compiled and the failed cases exercised afresh;
the old failures are not relabelled. Five classes/twelve external methods and
all standard-suite exclusions/prerequisites remain unchanged.

## Retention and platform bookkeeping

The normal installer upgrade preserves all three package UIDs, both original
model hashes and all 20 protected metadata files byte-for-byte. Encrypted
before/after snapshots round-trip. After the seven test attempts, 16 of those
files remain byte-identical. Only the 24-byte profile-verifier marker and three
WorkManager database files differ. Four private SQLite quick checks pass; every
core application row/schema remains identical. Every WorkManager work/other
table remains identical; its only logical change is the monotonic
`Preference.last_force_stop_ms` value. Three new metadata files belong to the
explicitly isolated family fixtures. Private temporary SQLite copies are
removed; encrypted evidence is retained. No owner file is restored or deleted.
Production APK identity is unchanged; production-private byte retention is not
claimed. The owner restores normal networking after tests and authorizes
continued USB control. Temporary changes must still be restored and receipts
checked before any retry of uncertain work.

## Public CI and original audit

[Exact-source compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/43334152098)
passes parser, pinned registry and pinned sidecar jobs. Four downloaded artifact
ZIP hashes verify. Upstream inspection still flags 40 changed used handlers on
stable `3b421578588243f920886e6fe79a6faa87ea026e` and 101 on inspected main
`415fa335b4d98ecf00e29e88625bb478dc63fb67`; no used routes are removed. Counts
are review findings, not confirmed bugs. No baseline, source waiver or guard is
relaxed. A local flat-name collision between two sidecar `result.json` members
is retained; the same ZIPs reconcile under their distinct member paths without
redownload or changes to the reports.

[Exact-source Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43334152062)
passes its build/test/lint verification job; all four API26/API37 partitions are
running at this checkpoint. Complete native union acceptance is not claimed.
The Windows worker job passes. New fixture source requires its own build/CI.

All unresolved [original audit requirements](../AUDIT_EXECUTION_PLAN.md) remain:
complete upstream semantic/transitive review and SDK shutdown cleanup, real
transport/authentication/profile/actor acceptance, project/workspace/worker/file
and NAS proof, approvals/images/recovery/integrations, automation/rooms/config,
phone/background/audio/accessibility/performance and final signed release gates.
Stored project metadata is not execution-host proof. Preserve eight completed
worker receipts and unknown outcomes; do not restart its daemon or replay them.
No Pi/gateway/default/model/credential change occurs in this checkpoint. Beta.8
remains the published preview; beta.9 needs passing required CI and final phone,
publication and download verification. The full goal remains active.
