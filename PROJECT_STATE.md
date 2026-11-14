# Signal Grove project state

14 November 2026. The full goal is **active**. Every unresolved requirement in
[GOAL](docs/GOAL.md), the [audit ledger](docs/AUDIT_EXECUTION_PLAN.md) and the
[on-device plan](docs/ON_DEVICE_AI_PLAN.md) remains; no narrower completion claim.

The exact next action is to observe the **existing navigation-shard1839** native
test to terminal, then inspect its raw stream and before-cleanup diagnostics.
Its host adb process was independently verified live at this checkpoint.
Do not start another run from a stale receipt, observation timeout or lost reply.

Current Android investigation:

- Tested code 827c6c0 captures the two navigation failures before caller/JUnit
  cleanup. Original selectors/assertions and the Demo test remain. A controlled
  real-activity case verifies the observer; empty native-window reads remain
  indeterminate and all snapshots are explicitly non-atomic and privacy bounded.
- Required build: 1,011 JVM cases, no skips; both lint variants, zero errors.
  Portable: 605 cases, 602 pass/three existing POSIX skips. Source, debug signer,
  APK contents and native alignment verify. No production release follows.
- The live disposable-API26 reproduction selects 541 cases: all 540 original
  cea0cbf shard cases in the same order plus the new control. No result is yet
  accepted. Both recent failures and the original source1180 Demo cause remain.
  [Current evidence](docs/contracts/2026-11-14-navigation-failure-observation.md) ·
  [Original recent failures](docs/contracts/2026-11-14-api26-cea-failures.md).

Published beta.9/code114 is unchanged and available from the
[release](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.9).
Main's newer work does not establish a final-source, original-key production
upgrade or complete release acceptance. Debug and production identities differ.

Pi deployment boundary:

- The reviewed isolated candidate has verified PM environments/source binding,
  23,628 immutable inputs/native modes and 41 separately verified bytecode outputs.
  Finite owner/cleanup, actual systemd stop-helper and guarded ASGI results pass
  within their documented separate scopes; the pristine warning remains recorded.
- The private one-existing-grant policy validates but stays uninstalled; the
  anchored reader still refuses unsafe ancestry. Current native/HTTP/worker/phone
  content and execution adoption are not accepted by those fixtures.
- Finish supported provider sign-in, pinned native HTTP transport and the full
  bounded transaction/rollback before separate live approval. The last live
  public provider read offers username/password; the local owner token gets 401.
- Canonical-home PM readiness is required for **both the old loaded stop/cleanup
  launcher and the new startup launcher**. Seven exact named directory modes,
  policy/source/drop-ins/CLI references, fresh encrypted pre/post backups and
  idle-only gateway drain/serve retirement still require that live review.
  Do not copy facts, weaken auth/readers or invoke a reaper in a shared cgroup.
  [Packet](docs/contracts/2026-11-14-runtime-cutover-packet.md) ·
  [Native component scope](docs/contracts/2026-11-14-retirement-client.md).

Remaining full acceptance includes the four explicit resumed priorities:

1. Original Demo and recent API26 causes, demonstrated fixes and final-source
   complete standard device gates; passing subsets cannot waive failures.
2. Full used/transitive upstream compatibility (last reviewed 40 stable/144 main
   used-handler changes) and operational shutdown. Keep all frozen failures.
3. Actual authenticated project/workspace/worker/NAS access and execution adoption,
   jobs/Kanban/rooms/grants/delivery, remaining integrations/configuration and
   doctor/graph validation on correct owners. Metadata alone is not execution.
4. Final phone/local-model/lifecycle/audio/background/notification/accessibility/
   battery/thermal and signed-production retention/release/security/support gates.
   Twelve external-model methods retain their own prerequisites and evidence.

Preserve package/signing/UID, saved connections/chats/projects/models/history,
the owner's on-device-only dictation choice and all unknown work. Do not repeat
the consumed phone restart. No automatic cloud fallback, unknown-work replay,
credential transfer, public exposure or user-project material on Pi.

This concise state replaces stale duplicate next actions. Historical results,
including initial native refusals and failed wrappers, remain in EVIDENCE.md,
docs/VALIDATION.md, linked contracts and the
[previous checkpoint](https://github.com/DionCroft/Signal_Grove/blob/79933f137c6125bfb44807b5be4c72c47500b4a1/PROJECT_STATE.md).
