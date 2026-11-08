# Reviewed canonical session attachment

This development chunk implements explicit session eligibility, one-use resume
approval, durable adoption receipts and attach-first replay. It is an internal
transport/repository foundation; the existing history browser remains separate.
It adds no user-facing canonical session control yet, no work submission,
workspace change, interrupt, explicit detach/end reason or automatic reconnect.

The pinned official sources are
[stable 2bd378e](https://github.com/NousResearch/hermes-agent/tree/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19)
and [prior bf0203a](https://github.com/NousResearch/hermes-agent/tree/bf0203a794a07aac3ff764972ab9087baa4e4f4e).
Stored and live inventories are distinct. Enumeration sends only scope/limit:
title lookup can restore a session and is excluded. Fresh inventory rechecks
the exact actor, API, selected home, profile, instance, epoch and selected row
before spending approval. Changing activity/count alone does not revoke review.

Resume can adopt a legacy transcript, resolve logical lineage, refresh policy
and hooks, and attach a viewer. It requires explicit effect/lifecycle review in
the future UI. The durable sending record precedes the one wire request.
A valid resume ACK is persisted before replay: a failed replay cannot erase a
known adoption. Lost ACK stays unknown and blocks another operation across
same-home aliases until separately inspected/reviewed. Silence is no approval.

Current Hermes maps registered dashboard logins to its single host owner;
the prior source requires a verified local native grant to adopt legacy rows.
A phone dashboard bearer/ticket does not manufacture that grant. The prior
fixture refuses legacy adoption and leaves all seeded rows unchanged. Existing
canonical live sessions remain a separate eligible path. This is source-backed
fixture behavior, not a fresh loaded-process claim about the owner's Pi.

The snapshot's reported stored ID is not asserted to be the physical transcript
target. Its false running flag proves neither idle nor terminal. The complete
CanonicalSessionHandle is returned by interrupt in these sources; never
interrupt merely to obtain one. Grants and worker/file ownership remain
operation-specific and require their own actual contracts.

The socket attaches before requesting replay and validates all buffered frames,
including covered duplicates. Exact overlaps apply once; conflicts, foreign
owners, epochs, missing events, replay truncation and bounded queue overflow
stop the view. Resume acknowledgement remains recorded. A checkpoint must be
the last cursor actually applied by its caller, never merely received.
Initial/buffer/live delivery is bounded and has one consumer. No control or
work request is inferred from an observation.

Ordinary socket close removes the viewer and may arm the server's configured
orphan close lifecycle. Stable defers that lifecycle until idle and does not
interrupt delegations. Explicit end_reason has different effects and is never
sent here. Heartbeat, UI generation guards, applied-cursor persistence and
user-facing recovery remain required before long-lived UI rollout.

The real-source fixture uses fresh synthetic homes and its own loopback
read-only model metadata endpoint. It admits no inference, admissions or worker
execution. Transcript messages must remain exact; only the selected default
legacy row may gain the five upstream ownership-binding metadata keys.
Audit hooks are defense in depth, not an OS sandbox. Synthetic fixtures never
run on the owner's phone or use saved owner credentials.

Nine added aliases preserve every earlier F0 entry and bind changed projections
to complete declaring, wire, socket, ownership, listing, snapshot and lifecycle
modules. Missing owner_identity is proven only at the immutable prior tree.
No changed current-main callback dependency is waived by this chunk.
Local validation:703 fresh JVM,196 policy,both lint0;18 native invocations
execute27 methods on three preserved emulators. Stable accepts reviewed legacy
adoption, prior lists no legacy eligibility; no admissions/workers or transcript
changes. All source/context/signature/reverse/geometry checks pass. Retained
compile/fixture failures and the later successful exact-source cohort remain
separate. Full exact-head hosted, user-facing UI and physical acceptance remain
required.
