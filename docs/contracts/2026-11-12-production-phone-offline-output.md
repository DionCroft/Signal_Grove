# Production phone upgrade, live reads and offline output finding

12 November 2026. This checkpoint verifies the original-key production beta.9
candidate from source `b182ae87d1240cdeb6eece402ae2ff9f6baecb3d`, versionCode112.
It does not accept its local-output formatting or publish a release.

## Upgrade and current Hermes reads

The owner installed the production APK through the normal Android installer.
The installed package/version/hash, original production UID and non-debuggable
status match. APK SHA-256:
`137d00f73229988ad64af7a61ab6e2ef3d8442f6519805cd485750b5147a8ec9`.
Both original debug model hashes, all20 protected debug metadata files and all
three package UIDs remain. Before/after debug snapshots round-trip through local
encryption. Production-private files are inaccessible to the Android shell;
their byte-for-byte retention is not claimed.

After normal Tailscale reconnection, Save & test connection succeeds without
editing the saved server values. An older cached test transcript is retained but
the server reports that selected chat unavailable. This is not counted as a live
history pass. Explicitly refreshing history and opening a different current
conversation loads its earlier messages without that banner. The owner confirms
both outcomes. No old failed message is retried and no server turn is sent by
automation. A bounded read-only Pi lookup does not establish the cause of the old
chat's absence; other transcript contents are not exported.

Hermes management opens the stored project list and a refreshed existing project's
detail window. This proves authenticated metadata access, not Windows worker,
registered-root, repository/file/NAS or dispatch acceptance. Sign-in renewal versus
retained sign-in is not independently distinguished. Private screenshots, chat
contents, project identities and connection values remain outside Git.

## Actual production offline observation and release blocker

The owner imports the existing reviewed LFM3 GGUF and explicitly loads it in the
production app. Model SHA-256:
`43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452`.
The observed context is4096 tokens. With Airplane mode on and Wi-Fi disabled,
there is no active default network or physical network agent before/after the
turn. A residual VPN-only agent is not physical connectivity. The actual typed
prompt asks for a short sentence about a peaceful garden; the requested test tag
was not copied verbatim. Native generation completes on the phone.

**Output formatting fails:** the answer exposes a `</think>` marker and repeats
its sentence. The erroneous saved answer is preserved. Closing local AI unloads
the model; reopening Phone history loads that same answer without loading weights.
Do not silently rewrite it or count this as full production native acceptance.
Investigate the pinned upstream chat parser/template and sampling projection;
preserve user control-token isolation, Unicode streaming and cancellation.

Bounded resource points show loaded RSS2,615,080 KiB, post-reply RSS2,597,444 KiB
and unloaded-history RSS409,868 KiB. Battery temperature is29.8-30.0 degrees C,
charge100%, Android thermal status0; Android reports AC power while USB is
connected. These are point observations, not peak memory, sustained thermal,
battery life, generation latency or tokens-per-second measurements.

The original offline-network regex fixture rejected CR-terminated ADB output;
exact split-line comparison reconciles the retained raw observation without
rerunning generation. The first aggregation also expected a bare hash where the
UI displays a SHA-256 prefix; that bookkeeping assertion is corrected without
changing evidence or the app. Normal Wi-Fi/networking is subsequently restored
by the owner and observed; no settings are changed by automation.

## Fresh complete standard Android gate and limits

Hosted run43284079368, source `b599e88941287d23a9c933ed8843962a387f2c11`, completes
all six jobs. Five downloaded ZIPs have independently checked digest/size.
Raw disjoint shard identities and the hosted union summary reproduce exactly:
1,135 API26 /1,136 API37 unique standard cases, no failure/error/skip/duplicate
or missing case. Five external-model classes/11 methods remain a separate gate.
All543 Android/native/build inputs still match the b182 candidate at this
documentation checkpoint. The CI APK hash pair is not the original-key local
production APK; CI binaries/signers are not inferred from report downloads.

The candidate remains unpublished. Fix and test native output formatting, then
build a fresh increasing-version candidate and repeat applicable exact-source
phone/build/release gates. Stable40/inspected-main101 compatibility findings,
the native runtime shutdown warning, intermittent Pi/Windows read path and all
remaining original audit rows stay open. No source waiver, live configuration
change, cloud fallback or goal completion is added.

[Candidate provenance](2026-11-12-reviewed-main-beta9-candidate.md) ·
[Full audit](../AUDIT_EXECUTION_PLAN.md) · [Current state](../../PROJECT_STATE.md).
