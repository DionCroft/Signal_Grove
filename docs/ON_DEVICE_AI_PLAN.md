# On-device AI delivery and acceptance

## Offline feature extension - 16 November 2026

The owner requested more local-LLM features following positive user feedback.
The [new phased backlog](ON_DEVICE_AI_BACKLOG.md) adds ten planned work packages:
editable/branched chat; searchable history/backup; context management; performance
presets; model discovery/local-first setup; transfers/lifecycle; local documents;
writing tools; opt-in personal profiles/memory/temporary chats; and offline voice/
guided help. Each has explicit acceptance criteria and dependencies.

This is a planning-only scope extension. The full goal remains **paused**.
No new app feature, model support, APK, live service or physical test is claimed.
The original four priorities, twelve-method external-model gate and final
production/upgrade/security requirements remain; fix retained UI failures first.

## Owner-requested pause - 15 November 2026

The full goal is **paused**. All four acceptance priorities and every original
audit/local-AI/release requirement remain. Reviewed SDK source is already on
main at 62c8bbe. Unfinished route diagnostics are saved/pushed only to the feature
branch at a2a67b2. The final focused run has 16/16 API26 positives and 15/16
API37 positives with a retained phone-draft Cancel/keyboard viewport failure.
This does not qualify a newer stable APK. Beta.9/code114 remains publicly
downloadable; fresh anonymous production APK/checksum downloads verify.
No implementation or deployment continues while paused.

[Complete paused inventory and release status](audits/2026-11-15/PAUSED_REVIEW.md).

## 12 November 2026 - production upgrade/read checks and native output blocker

The installed original-key beta.9/code112 retains its identity and saved
connection. Refreshed current-server history and stored project details load.
An older cached missing chat remains preserved and is not counted as a live pass.
Production LFM3 completes a genuinely offline turn and its reply reloads with
weights unloaded, but exposes a reasoning-close marker and repeats a sentence:
native output acceptance and publication remain blocked. No history is rewritten.
The owner restores normal networking. Bounded resource points are not sustained
benchmarks. Fresh b599 complete standard CI independently verifies1,135 API26 /
1,136 API37 unique cases; external models remain separate. All original audit
rows, stable40/main101 compatibility findings and live worker/runtime limits stay.
[Exact observations, failures and scope](contracts/2026-11-12-production-phone-offline-output.md).

Authorized 10 November 2026. This extends the complete audit; it does not replace
or close any requirement in AUDIT_EXECUTION_PLAN.md. The previous pause is revoked
for the user's requested work. The owner edited and resumed the expanded goal
through the app controls on 10 November; the active objective matches docs/GOAL.md.
Never mark unfinished audit work complete to work around goal API restrictions.

1. Physical feasibility first. Pin and verify the official LFM3-1.7B QAD Q4_0
   weights and a compatible ARM64 runtime. Record device/OS/ABI, storage and RAM,
   source/hash provenance, native identity, prompt/context/generation settings,
   actual generated output, timing/token throughput and observed peak memory.
   Prove offline behavior and cancellation with the actual phone. Record bounded
   temperature/battery observations without calling them a long-term battery
   benchmark. Preserve the production app, keys, data, connection and device
   settings. No root, data reset, unrelated app/process termination or Pi change.
2. Native engine and offline chat. Add an optional embedded inference engine,
   streamed cancellable generation and bounded lifecycle/resource handling.
   Keep local conversations and source identity distinct from Hermes sessions;
   label where inference runs. Local chat works without Hermes connectivity.
   Any fallback is explicit and applies only to new unsent messages. Preserve
   unknown remote receipts; never replay their work locally or on reconnection.
3. User-controlled model management. Provide a verified recommended download,
   resumable/atomic installation, checksum and size checks, Android file-picker
   import, supported architecture/template validation, honest compatibility and
   licence information, private storage and context/resource limits. Test load,
   unload, cancellation, delete, malformed/oversized files and insufficient space.
   A GGUF container alone does not establish architecture or device compatibility.
4. Grounded onboarding and diagnostics. Reconcile the retained setup draft into a
   versioned offline knowledge source. Give the helper only current-profile,
   explicitly selected, redacted observations with freshness labels. Distinguish
   observation from advice; offline cached facts cannot prove live server state.
   Model text never directly changes configuration, handles credentials, approves
   work or executes tools. Apply existing exact-value reviews for any supported
   action. The deterministic setup guide stays usable before model download.
5. Integration and delivery. Test local/server isolation, opt-in routing, explicit
   summary handoff, context limits, language behavior and Android process/lifecycle
   recovery. Run required Android/JVM/policy/native checks and physical acceptance.
   Commit/push each tested chunk; retain exact build/model/native hashes and failed
   attempts. Model weights/generated/private evidence stay out of Git. Publication
   requires its own complete source/signature/upgrade/compatibility release gates.

Offline inference supplies local conversation and granted phone-side capabilities.
Pi identity, remote tools, live project files, server jobs and worker execution
remain owned by their verified hosts. A full autonomous phone Hermes backend is
not part of this extension. No optional implementation is accepted by a benchmark
or source parser whose scope does not cover that feature.
