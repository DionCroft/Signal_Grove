# Signal Grove handover

Continue the same active full audit/on-device AI goal in `DionCroft/Signal_Grove`.
Read [current state](PROJECT_STATE.md), the [full unresolved audit](docs/AUDIT_EXECUTION_PLAN.md)
and [on-device plan](docs/ON_DEVICE_AI_PLAN.md). Do not replace the goal with a
smaller release-only task or treat a fixture pass as live integration acceptance.

## Current checkpoint

- Native owner room reads verify 20 checks through real Windows SID/control
  tickets/canonical sockets and synthetic room data; six rooms/18 events per
  profile remain exact after reads and process exit. Empty mode also verifies.
  Both still return 2 for the one-warning SDK shutdown finding; full lifecycle
  stays open. Four preflight refusals and 422/15/checksum policies pass.
  [Native owner room scope](docs/contracts/2026-11-12-native-owner-room-reads.md).

- Current stable canonical authority passes 46 authority/wire and 13 attach-first
  cases with 57 critical source guards and 532/520 loaded-module hashes. Existing
  assertions and older variants remain. Three source/invocation controls and
  422 portable/15 runtime/checksum policies pass. Actual dispatcher/store/event
  behavior uses synthetic actors/transports; live socket/room/worker acceptance
  stays separate. [Current-stable authority](docs/contracts/2026-11-12-current-stable-authority.md).

The final stable 3b42157 real-source probe passes 87 distinct cases, 55 critical
source guards and 949 loaded module hashes. All earlier 77 cases remain; actual
loader helpers/import lock and public start-entry/PM behavior use explicit
synthetic/stopping adapters. No actual package install, input read or listener
starts. Three source/invocation controls and 422 portable/15 runtime/checksum
policies pass. [Exact source/adapters](docs/contracts/2026-11-12-native-loader-startup.md).

The independently verified d50518c complete standard Android gate passes1,132
API26/1,133 API37 cases. The newer workspace test source 2970ffb also passes its complete 1,135/1,136
gate; its focused API26 batch passes 31 cases/16 one-attempt native taps.
The build passes 1,006 JVM/96 reports and zero-error lint 51/50 with original keys,
all native ABIs/page alignment and uncompressed DEX. This Python/docs chunk
changes no Android/native/build input. [Workspace evidence](docs/contracts/2026-11-12-native-workspace-review.md).

## Immediate continuation

1. Fresh clean b182ae8 original-key build and independent artifact/inventory
   checks pass. The exact production APK is staged for Xiaomi's normal installer;
   reconcile that pending install before any retry, then verify its hash/UID/
   version and saved production connection/history/live behavior. The encrypted
   debug snapshot does not prove production-private retention. Complete2970
   standard coverage is 1,135/1,136, with543 identical Android inputs here;
   pending exact-head CI and compatibility failures remain separate.
2. Complete current-stable used-handler dependency closure and real canonical
   socket/actor/profile/transport acceptance. Stable 40/inspected-main1773ba3 101 findings
   remain; no handler/absence/cohort waiver is added by the component probe.
3. Continue the Windows/Pi read-path root cause, actual worker/root/file/NAS and
   reviewed action acceptance, preserving receipts and every original audit row.
4. Finish final original-production-key phone upgrade/live retention and publish
   the checked versioned APK/checksum/provenance assets from main, then verify the
   public download and provide its direct link. Publication is already authorized.

## Retained phone, worker and recovery boundaries

Phone cold recovery and one approved normal restart pass with original models,
history/settings/UIDs and production APK preserved; no automatic replay/load occurs.
Do not reboot it again. Keep the owner's USB-awake setting unchanged. Full phone
foreground LFM3 resume now passes 27,010 ms with full file hash/atomic install and
3ms cancellation; old 300s failures/freezer observations remain historical. Six
earlier physical offline model/helper cases retain their source scope; sustained
throughput/thermal/battery and remaining physical methods are still open.
[Phone retention](docs/contracts/2026-11-12-physical-restart-retention.md) ·
[Download proof](docs/contracts/2026-11-12-foreground-model-download.md) ·
[Offline model scope](docs/contracts/2026-11-12-physical-offline-model-acceptance.md).

The reviewed optional Windows deadline component retains its daemon/task/source
and eight completed receipts. The approved Pi in-memory read trial passes
status/prepare but list fails before submission; later status is ready. Its cause
remains open. Do not replay uncertain effects, restart the daemon or change live
Pi/gateway/models/credentials/jobs. [Worker proof](docs/contracts/2026-11-12-pipe-exchange-deadline.md).

The two newly owned review emulators are normally stopped with disks/evidence
retained. API37 guest boot was not verified and received no app instrumentation.
Older unknown VMs/install outcomes remain untouched. Reconcile exact receipts,
source/APK hashes and active handles before a deliberate new trial.

## Delivery and records

Published beta.8/code 111 and the phone's production package remain unchanged;
beta.9/code 112 is unpublished. Fresh b182ae8 assets are verified/staged with
183-component SBOM, 95 inputs and fresh 150-version/pinned-native advisory scope.
Earlier f9e3835 assets retain their own source/hash identities.
[Fresh signed inventory and phone stage](docs/contracts/2026-11-12-reviewed-main-beta9-candidate.md).

Source pushes and temporary CI artifacts are not public APK delivery. Refresh
[README figures](README.md) and [features](docs/FEATURES.md) at final publication.
All project source stays in the chosen repository; tools, caches, models, build
outputs and private evidence use E on this workstation. Keep credentials out of
source/chat/helper context, preserve TLS/profile isolation and exact approvals,
and never silently replay unknown remote work or use a cloud fallback.

Historical detail remains in [validation](docs/VALIDATION.md), [evidence](EVIDENCE.md),
dated contracts, sessions and Git; current state is deliberately concise.
