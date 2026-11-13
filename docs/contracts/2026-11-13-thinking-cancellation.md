# Thinking-aware phone cancellation and complete emulator evidence

13 November 2026. This checkpoint changes only physical-test fixture code and
documentation. Product inference, token limits, prompts, native libraries and
retry behavior are unchanged. Beta.8 is the published preview; beta.9/code114
remains unpublished. The full audit/on-device AI goal stays active.

## Exact installed pair and measured results

The debug app remains source `0ee08e37c90e009a70bb290301c81433a11517a5`,
SHA-256 `5e1f970a985c5ee51a6a51826e46d2a107bcbaacbbf143b58cc33c7203a8af85`.
The fresh companion is source `1180d481a99f031ed40a1c0e95b16702dfce3d3a`,
SHA-256 `54daea44c162e64d49a3aecff54d4fa636a32e8c5fc79c9b537adb793736d745`.
The two full builds pass 1,011 JVM cases/97 reports and zero-error lint with
51 debug/50 release warnings. Their main DEX and resources are byte-identical;
the second APK pair is not relabelled as physically tested. Both original
package signers, all four native ABIs, 64-bit 16-KiB and DEX alignment verify.

The fresh helper/Chinese method passes in 23.968 host-observed seconds while
actually offline. All six source-backed topic selections complete. Its retained
48-token Chinese probe reports the known limit with clean output; a separate
96-token call completes exactly. Raw log SHA-256 is
`7b1a5ef2a7b1deecd5cfe363568c0c94bbe9dc6926c52424846a3eb643ddb55a`.
Together with the unchanged-app garden, metadata, Qwen, switching/history and
atomic-import passes, six selected native methods now have positive evidence.
This is not the whole twelve-method external-model gate.

The generation method remains failed. Its second-reply 96-token probe reaches
the known limit, then a separate 384-token call completes exactly. The next
long-list request uses all 512 tokens with no visible answer, so the old callback
never requests cancellation and the assertion sees TOKEN_LIMIT. Later recovery,
coroutine cancellation, overflow and reload stages are not reached in that run.
Previous failures remain failed; no new cancellation acceptance is claimed.

## Fixture correction and independent controls

The exact-runtime standalone control confirms that the original long-list
prompt emits zero visible bytes in 512 tokens. It never invokes application
cancellation and does not establish a cancellation defect. A separate bounded
literal finishes at 457 tokens, produces 167 answer bytes, and first exceeds
100 visible bytes at token444. Both controls use the same pinned model,
2,048 context and deterministic sampling. They access no app data or Hermes.

The fixture retains the original long-list prompt and starts its operation
UNDISPATCHED, waits 250 ms, requires an active request without visible output,
then explicitly requests Stop and requires CANCELLED within ten seconds.
A separate measured literal must stream a clean, monotonic prefix, request Stop
after 100 visible characters and return CANCELLED. The original coroutine
prompt also starts UNDISPATCHED and is cancelled while active, independent of
visible output. Exact recovery, context-overflow rejection, unload/reload,
original short-limit probes and all no-marker assertions remain required.
The new companion still needs compilation and fresh physical acceptance.
Product defaults/caps and automatic-retry behavior are unchanged.

## Complete standard emulator gate

All six jobs in the [source0ee Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43334152062)
pass. Five artifact ZIPs verify against their GitHub sizes/SHA-256 digests.
The unchanged strict verifier reproduces the hosted summary exactly:
1,135 unique API26 cases across disjoint 537/598 shards, and 1,136 unique API37
cases across 538/598 shards. No failed, skipped, duplicate or missing cases are
accepted. Every partition is bound to the same exact source and APK pair:

- App: `5bcd7f604b309a3330a1a45fe9921d8766cbcd1b1978f77f5f49e1c3a381eb97`
- Test: `56bb0e4027f076fe20bb799d9f337d1e9f7da05d2d14297dc5807462a5638e1b`

Five external classes/twelve model/prerequisite-dependent methods stay
explicitly outside this standard gate. Full external/physical acceptance is not
inferred. New fixture source needs its own CI. A first local artifact-reference
shape error occurred before any download and is preserved; the corrected
verifier used fresh references without changing case parsing or expectations.

## Retention, networking and remaining work

The companion-only installer update preserves all 23 protected debug metadata
files byte-for-byte, both original model files/hashes, all three package UIDs
and both installed application APKs. Encrypted snapshots verify. This does not
claim post-test byte identity or read inaccessible production-private files.
The subsequent radio window verifies Airplane mode on, Wi-Fi disabled and no
physical/default network at observed checks, then restores the original
Airplane-off/Wi-Fi/Bluetooth and working Wi-Fi/VPN state. Awake15 is unchanged.
The owner confirms no active call and authorizes USB control. No new source-
install permission, reboot, clear, uninstall, credentials or live backend
change occurs. Post-test retention remains a separate required check.

Fourteen isolated real-source route-verdict cases pass against stable3b42157;
they cover endpoint identity, stream resets, primary/fallback verdicts and
deep-copy restoration. An extended bind-route prototype still has an audit/
environment failure and is not accepted. Neither closes full routing/plugin
compatibility. Stable40/inspected-main101 semantic/transitive handler reviews,
SDK shutdown cleanup, full transport/profile/actor behavior, projects/workers/
files/NAS, approvals/recovery/integrations, automation/rooms/config, background/
audio/accessibility/performance and final signed-release acceptance stay open.
Preserve unknown worker receipts and every original audit requirement; no
daemon restart, automatic replay or baseline waiver is introduced.
