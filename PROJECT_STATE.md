# Signal Grove project state

Current milestone: beta.9 uncompressed-DEX candidate and download/release checks.
Current objective: finish lifecycle and final-source release acceptance,
finish candidate device/release checks and keep the separate worker audit moving.
Assurance state: fresh stable40/main100 findings and full original audit remain.
Last checkpoint: 12 November 2026, physical-restart-and-ready-keyboard.
Last handover: HANDOVER.md.

## Current checkpoint - physical restart and keyboard readiness

The owner's ARM64 API37 phone passes the original native cold-recovery and
post-reboot cases, 2.849s and 1.856s JUnit. One explicitly approved normal Android
restart has a changed kernel boot ID. Completed/interrupted history remains,
with zero automatic model load/generation/replay. The deliberately interrupted
preparation is not a positive test. Both original LFM files, protected metadata,
package UIDs/APKs and owner SQLite rows remain; only cache freshness/framework
bookkeeping changes. Scoped encrypted snapshots verify. The owner's USB-awake
setting is unchanged, and only the isolated input/model copy is removed.

API26 preparation now accepts either the exact reviewed first-use tip or an
already-clear keyboard. Both native paths pass on a fresh disposable fixture;
all four unchanged focused app cases and six exact native DOWN/UP taps pass.
Initial unexplained no-tip gaps, unsupported versions, failed/partial receipts
and repeated actions remain rejected. No app visibility/consent assertion or
five-class/eleven-method external-model inventory changes.

Fresh original-key APKs pass 1,006 JVM cases /96 reports, lint0 errors51/50
warnings, all four native ABIs and uncompressed/aligned DEX checks. Portable
policies422 and runtime-model policies15 pass. These are scoped checks, not a
complete final hosted-suite or production-upgrade pass. Prior failed/unknown
fixtures remain, including the offline older API37 VM and a separate API26
host-install timeout. Published beta.8 is unchanged; beta.9 remains unpublished.

[Physical lifecycle](docs/contracts/2026-11-12-physical-restart-retention.md) ·
[Keyboard fix and exact candidate](docs/contracts/2026-11-12-ready-keyboard.md).

Earlier foreground checkpoint:

The full foreground phone HTTPS resume case now passes unchanged assertions:
3 ms cancellation, stable 8,451,250-byte prefix, full 1,049,615,680-byte hash/metadata
check and atomic installation in 27,010 ms, followed by scoped test-model deletion.
Six sampled kernel/screen/foreground states pass; original models/chat/settings/
UIDs/APKs and production beta.8 remain. USB-awake setting is restored. Both HTTPS
methods have positive physical evidence; prior failures stay retained.
[Current foreground evidence](docs/contracts/2026-11-12-foreground-model-download.md).

Earlier cancellation checkpoint:

Latest checkpoint: the saturated-I/O cancellation defect is reproduced by two
failing regressions and fixed; full 1,006 JVM /96 reports and zero-error lint51/50
pass. Current signed APKs retain original keys, native alignment and uncompressed
DEX. Cancellation/discard pass on the phone and emulator, but resume retains
300s failures. Phone cgroup freeze is reconciled by foregrounding the exact
existing process once; no restart/replay, assertion relaxation or root occurs.
Original phone models/chat/preferences/UIDs and production APK remain. Temporary
USB-awake state is restored and encrypted snapshots verify.
[Current cancellation evidence](docs/contracts/2026-11-12-download-cancellation.md).

The owner explicitly requests publication from main once the application passes
its release checks, plus current repository/README features and figures. README,
feature/validation matrix, roadmap, usage/compatibility/development and screenshot
index are refreshed with published/candidate distinctions and scoped measurements.
The new screenshot is a synthetic API26 candidate UI capture. Documentation-only
changes do not alter candidate APKs or close any original release/audit gate.

Earlier packaging-only candidate checks retain their exact scope:10 API26 local-AI
UI passes and351 API37 /66 API26 scoped core hashes across upgrades. Its original
two transfer failures remain [historical evidence](docs/contracts/2026-11-12-uncompressed-dex-candidate.md).
Fresh compatibility remains stable40/current main100 handler findings. Advisory
review covers150 Maven modules and the pinned native revision with known-indexed
coverage limits. Published beta.8 and the phone's production app remain unchanged.

The owner's physical ARM64 API37 phone passes six exact offline native cases on
the original-debug-key candidate: LFM3 output/cancel/recover/unload, all six grounded
helper topics/Chinese output, metadata cancellation, committed-import cancellation,
Qwen chat/history/deletion and separate two-family histories. Original memory and
timing guards remain; imports take 649-1438 ms. Normal networking is restored.
Original preferences, both original model files, package UIDs and earlier local
chat remain, with the owner's confirmed new turn preserved. SQLite changes are
limited to cache freshness/framework bookkeeping. Scoped encrypted recovery verifies
and only owned duplicate test inputs are removed; production app is unchanged.
[Physical scope and remaining five methods](docs/contracts/2026-11-12-physical-offline-model-acceptance.md).
Earlier emulator one-pass/two-failure evidence remains retained separately.

The late-client pipe deadline fix is deployed as an exact optional Windows
component after idle/source/policy/task/receipt checks and encrypted recovery.
All eight completed receipts, worker/daemon source and task remain unchanged.
417 portable and 66 native cases pass locally on two Python versions and the
66-case hosted artifact is independently verified. The fresh approved Pi trial
passes status/prepare but list fails before submission. Later status is ready;
its cause is not yet established. No persistent Pi/gateway/credential/model/job
change or request replay occurs. Doctor and graph findings remain open.
[Deployment and limitations](docs/contracts/2026-11-12-pipe-exchange-deadline.md).

Complete standard Android source7ee raw evidence remains API26 1132/1132 and
API37 1133/1133 distinct cases, 1004 JVM/96 reports, zero-error lint (52/51 warnings),
32 native ABI/page checks and all 545 unchanged Android/build/gate inputs. Latest
c55d070 Android workflow also succeeds; CI signer is not the production signer.
[Standard gate](docs/contracts/2026-11-12-complete-standard-android-gate.md).
Fresh source c55 reports stable40/current main100 changed handlers; the older
saved main97 outcome remains historical evidence. No source/commit waiver applies.

**Next exact action:** push the tested keyboard fix and physical lifecycle evidence
to main, retain the resulting complete hosted device union and compatibility
findings, then finish original-key production upgrade/live connection and release
provenance/public-asset checks. Preserve the two older unresponsive emulator
outcomes without reboot/install replay. Continue actual Windows read-path root
cause, native actor/profile/Android workspace/NAS and all original audit rows.
Do not repeat the physical restart or reset owner data.

## Continuing scope and authoritative records

Published application: v1.1.0-beta.8 (code111). Current source includes later
optional phone inference, user-model/helper and canonical/project-navigation
work. Source changes do not certify that published APK or the final phone build.
Final delivery explicitly includes the original-key production APK as a public
versioned GitHub Release asset, verified download/checksums and retained-data
phone upgrade, with a direct APK link. Publish after applicable release gates;
source pushes or temporary CI artifacts do not fulfil that delivery.
The goal remains active; all unresolved requirements stay in the
[full audit ledger](docs/AUDIT_EXECUTION_PLAN.md) and
[on-device AI plan](docs/ON_DEVICE_AI_PLAN.md).

Worker/root/file ownership and reviewed dispatch, real production auth/recovery/
approvals/images/integrations, advanced Kanban/automation/rooms/configuration,
notification/multi-connection/voice/background performance, physical/model
acceptance, advisories/provenance and the signed community release remain open.
Project metadata or a passing fixture does not establish a live worker root.

Prior checkpoints remain in Git and the detailed evidence records:

- [Default-keyboard readiness](docs/contracts/2026-11-12-owned-keyboard-readiness.md).
- [Actual-owner project reads](docs/contracts/2026-11-11-actual-owner-project-conversations.md).
- [Project navigation](docs/contracts/2026-11-11-project-conversation-navigation.md).
- [Native input diagnostics](docs/contracts/2026-11-11-native-input-diagnostics.md).
- [Complete validation history](docs/VALIDATION.md).
