# Current feature and validation matrix

As of 12 November 2026. This describes `main`, whose planned beta.9/code 112 APK
is still unpublished. Published beta.8/code 111 and stable 1.0.0 retain their own
release notes. Implementation, fixture acceptance, live-system acceptance and
release inclusion are separate. [Release status and figures](../README.md).

| Feature | Current main implementation | Validation or remaining boundary |
| --- | --- | --- |
| Remote chat | Authenticated capability discovery; Runs/session streams; explicit compatibility fallback; image input/history/share support | Supported routes/toolsets vary by server; exact native run outcomes and uncertain effects retain their receipts |
| Existing conversations | Profile-scoped search/paging/history; local pin/hide; supported API-owned rename/delete and controls | Earlier phone upgrade/history/live reply verified; other-origin write ownership stays with its original interface |
| Canonical viewer | Read-only canonical conversation browsing and separately reviewed native attachment/resume | [Viewer](contracts/2026-11-08-canonical-viewer.md); production native actor/profile/recovery acceptance remains scoped |
| Hosted rooms | Explicit read-only room inventory/history browsing | [Room contract](contracts/2026-11-08-hosted-rooms.md); forwarding, membership/grants/delivery controls remain in the audit |
| Serving models | Verified serving identity, freshness/errors, server default and requested/session model shown separately; Automatic for new turns; reasoning/service presets | Live owner setup was exercised; model choices do not load/evict server weights by themselves |
| Phone inference | Optional CPU-native LFM3 text chat, streaming, cancellation, context limits, load/unload/delete and model-bound local history | Six physical native cases across LFM3/helper and Qwen/import/family behavior; final beta.9 phone upgrade still due |
| Model management | Pinned LFM3 download, explicit resume/discard, Android picker import, hash/size/licence/template checks and private atomic installation | Eight metadata architecture names, not eight universally supported model families; real HTTPS cancel/discard and full foreground resume have physical passes; earlier failures remain |
| Setup helper | Static guide plus six topics; model selects eligible IDs and UI renders exact versioned English/Chinese/pinyin sources | All six topics have physical native evidence; selected redacted observations require opt-in; no settings/tool/approval execution |
| Local/remote handoff | Review a new unsent text-only question into a separate phone draft; review local advice before copying | Credentials, remote history, images, approvals and attempted/unknown work are excluded; sending remains explicit |
| Stored projects | Reviewed inventory/details and create/edit/archive/restore/delete/active-pointer controls; project-conversation navigation | Owner confirmed stored project details; metadata does not establish worker identity, a mounted root or permission to execute |
| Workspace files | Optional enrolled Windows read provider, protected local pipe and coordinator source | Native component 66-case gate passes; real Pi coordinator remains intermittently unavailable; native HTTP/WS and Android file browser/NAS acceptance remain open |
| Host Kanban | Board/task inspection, comments/dependencies/events/attachment metadata, reviewed task/board effects and overrides | Worker controls, real-server integration and richer attachment/transfer behavior remain in the audit |
| Providers | Curated/plugin provider catalog, device-code accounts, credential pools, fallback chains and custom endpoints with separate probe review | Native management sign-in required; live provider combinations and further configuration controls remain under audit |
| MCPs/plugins/skills/channels | Advertised management surfaces and exact-value reviewed supported effects | Update policies/bundles, pairing/webhooks and representative real integrations remain under audit |
| Automation | Opt-in advertised scheduled-job reads/controls, profile-partitioned cached snapshots and durable mutation receipts | Advanced execution/results/logging, owner rooms and supported goal/heartbeat/loop control remain in the audit |
| Phone tasks | Local notes/search/filters, priority/due date and reviewed bounded JSON export/import | Independent of host Kanban and Hermes jobs; no credentials/conversations in task export |
| Voice | Foreground Android speech/TTS, language/speed/stop and explicit online-service choices | Owner phone lacks offline recognition; read-aloud/stop confirmed separately; Bluetooth/audio/background checks remain |
| Notifications/widget | Opt-in private task/run monitoring, action requests and task-count widget | Best-effort WorkManager scheduling; precise targeting, multiple connections and reboot/battery behavior remain in the audit |
| Interface | Five palettes, original animated pixel scenery, multilingual native text, reduced motion, large text and phone/tablet navigation | Candidate API26 local-AI UI 10/10 passes; [captures](screenshots.md) identify historical/current synthetic views |
| App updates | Public GitHub checks, preview opt-in, release review, verified private APK download and Android confirmation | Package/version/current signer/checksum guards and active-work blocking; publication and final retained-data production upgrade are still release gates |

## Model observations and figures

The physical tests use an ARM64 Android 17/API37 phone. All six cases run with
Airplane mode on, Wi-Fi actually disabled and no default network, then normal
networking is restored. They use an earlier original-debug-key candidate; they
do not certify installation of beta.9's new packaging/version.

| File or observation | Result |
| --- | --- |
| LFM3-1.7B QAD Q4_0 | 1,049,615,680 bytes; SHA-256 `43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452` |
| Qwen2.5-0.5B-Instruct Q4_K_M | 491,400,032 bytes; SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Three physical imports | 649, 685 and 1,438 ms |
| Highest observed VmHWM | 2,608,447,488 bytes (2.43 GiB); short USB-charging observations, not minimum RAM or a complete peak/endurance benchmark |
| Candidate APKs | Production 70,993,990 bytes; original-key debug 92,542,534 bytes; test 7,865,099 bytes; no model weights bundled |
| Candidate JVM/lint | 1,006 cases / 96 reports; no failures/errors/skips; lint 0 errors, 51 debug/50 release warnings |
| Physical lifecycle | Two original cold/post-reboot cases pass, 2.849/1.856s JUnit; one approved normal restart with no automatic load/replay and original data retained. Intentional preparation is not a positive test; earlier emulator boot remains unverified |
| Keyboard CI preparation | Both reviewed fresh-tip/already-clear native states pass, plus four unchanged focused API26 methods/six exact native taps; full hosted union remains separate |
| Earlier candidate native local-AI UI | API26: 10 distinct positive cases, no skips/failures |
| Prior standard gate | Source 7ee5c59: API26 1,132 and API37 1,133 complete distinct cases; new candidate union not yet accepted |
| Current transfer gate | Physical cancel/discard pass; full foreground phone resume/hash/install passes in 27,010 ms; earlier frozen-phone/emulator timeouts and their partials remain |
| Advisory query | 150 resolved Maven versions, zero known matching OSV advisories on 12 November; pinned native commit query also returned none, with known-indexed coverage limits |

Import numbers are individual observations, not a model/storage performance
promise. No exact sustained token throughput or battery endurance is claimed for
these final-source batches. Generic GGUF admission does not establish licence,
publisher, native compatibility or safe memory requirements on another phone.

[Physical evidence](contracts/2026-11-12-physical-offline-model-acceptance.md) ·
[Physical restart retention](contracts/2026-11-12-physical-restart-retention.md) · [Both keyboard states](contracts/2026-11-12-ready-keyboard.md) · [Earlier emulator cold recovery](contracts/2026-11-12-native-cold-recovery.md) · [Full foreground phone download](contracts/2026-11-12-foreground-model-download.md) · [Cancellation/build evidence](contracts/2026-11-12-download-cancellation.md) · [Earlier candidate evidence](contracts/2026-11-12-uncompressed-dex-candidate.md) ·
[On-device usage](ON_DEVICE_AI.md) · [Full audit](AUDIT_EXECUTION_PLAN.md).
