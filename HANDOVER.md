# Signal Grove handover

OF-02 has a tested internal read-only search foundation: 1,064 JVM cases/102
reports, required builds and zero-error lint (51/50 warnings). Controller/UI,
organization, transfers/encryption and real offline UX remain. This independent
increment proceeds while OF-01 phone installation/testing waits; it does not
close or replace that gate. [Evidence](docs/contracts/2026-11-18-local-history-search-foundation.md).

18 November 2026. The full goal is **active**. Read [PROJECT_STATE](PROJECT_STATE.md),
[GOAL](docs/GOAL.md), [audit](docs/AUDIT_EXECUTION_PLAN.md) and the ordered
[offline backlog](docs/ON_DEVICE_AI_BACKLOG.md). Do not drop original requirements.

Main `a992cf4` contains the UI readiness/native-viewport fixes. Exact-tree PR
evidence independently verifies 1,149/1,150 standard API26/API37 cases; both main
Android and Windows runs pass. Main's upstream scan fails closed: official stable
v0.23.2/current-main source trees lack tracked modules. Review moved/replaced
contracts before adjusting the checker/baseline; retain all prior findings.

The unreleased feature branch implements OF-01 storage, reviewed actions, scoped
drafts, request/source identity and passive selectable Markdown/code/full-text
readers. Latest source-bound build: 1,053 JVM cases/101 reports, zero-error lint
(51/50 warnings), app/test APKs; 36 exact selected native methods on each API26/
API37, no skips. Read the [reader contract](docs/contracts/2026-11-18-local-reply-reader.md)
and linked earlier contracts. Keep old failed/unknown evidence and strict
single-input/readiness assertions. Ordinary isolated-emulator restarts preceded
the latest run after boot-time System UI ANRs; this does not prove every old cause.

Next: final OF-01 phone/native model branch/edit/regenerate/cancel/no-replay and
accessibility gates; then OF-02. Owner phone is connected, normal code112 app
untouched; debug package was absent at preflight. Re-verify exact package/UID/
signer/model/history before installation and keep production data separate.
The existing reviewed 1,049,615,680-byte LFM3 file in Downloads matches its pinned
SHA-256. A debug install is not an upgrade-retention proof. Do not reset data,
rewrite histories, replay uncertain work or install CI-signed APKs on that phone.

Full Hermes/shutdown, corrected SDK/PM/fleet, project/workspace/worker/automation/
integration and final model/lifecycle/security/original-key release gates remain.
Beta.9 is unchanged; no new release, Pi deployment, credential/model/default/
grant change. Original isolated Pi scope does not authorize live cutover, canonical
policy/PM/launcher/private backup or restart changes.
