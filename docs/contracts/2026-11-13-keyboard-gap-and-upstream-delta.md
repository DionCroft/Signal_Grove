# Keyboard preparation gap and exact upstream delta

13 November 2026. All four [goal priorities](../GOAL.md) and the full audit remain
active. This chunk changes only owned-emulator setup/tests. App main source,
native tap dispatch, live services, public APKs and owner-phone data are unchanged.

## Failed hosted cohorts stay failed

The source 9744caa [hosted cohort](https://github.com/DionCroft/Signal_Grove/actions/runs/43462382381)
has two distinct failures. Its API26 shard 0 stops in keyboard preparation, before
any standard inventory or test run. The native FAILED receipt observes 37 keys,
no hint/close nodes, window top 827 and key-row top 1053. The first-stage condition
requires equality when no hint is observed, so it cannot reach hide/reopen.
This is a preparation failure, not a new Demo-test assertion.

API37 shard 0 starts 539 methods, records 538 positives and one negative result in
workspaceRefusalAdviceDoesNotClaimAcknowledgementOrRetry. The exact Review tap
reports WARNING: one accepted DOWN and one accepted UP 721 ms apart, against a
400 ms long-press timeout. Its matching review receipt observes consent present
afterward. That effect does not turn WARNING into an accepted tap. No second
DOWN or replacement gesture occurs. This is separate from the earlier b995
pre-tap viewport disagreement and the local API26 Cancel WARNING; no common
cause with either original API26 Demo/missing-list failure is established.

| Retained original evidence | SHA-256 |
| --- | --- |
| API26 artifact ZIP | `e05a5cf8f3d16d82ea69e9bf519bb8fe21dffd08f5805ae5799be42840843fc8` |
| API26 preparation raw log | `afa30106b309ae1d2b41064d80a5b14e428a753b5bab4f2f94b1f015c88fd36e` |
| API37 artifact ZIP | `7f7d11b942f7604bc01dca043ad97697bf4facb6c25a018f756aaa00079abe5b` |
| API37 raw instrumentation | `e45105b8493e7255ad49001ace3946804b2690ccb6f84744ea02ba6d7f2941c8` |

All archives match GitHub's digests and their workflow source. Failed shards
lack the success-only APK partition report; no nonexistent device hash report
is claimed. No job is rerun or partition merged into another cohort.

## One bounded preparation path; same strict final proof

On the exact guarded API26/x86_64 Gboard fixture only, a visible key row with no
hint can now proceed from an observed window/key-row gap to the existing single
hide/reopen cycle. It does **not** count as readiness. The distinct
READY_REOPENED_KEYBOARD receipt requires that initial gap, zero hint dismissals,
the same acknowledged actions, and strict unobscured native geometry after
reopening. Unknown overlays, missing keys, invalid bounds, false/partial/repeated
actions and stale/failed receipts remain refused. The final equality check,
one-second stability, time limits, keyboard identity and no-replay rules stay.
The existing no-gap and reviewed-hint branches retain their validation.

Exact reverse comparison restores all three original producer/parser/host-test
files after removing the explicit extension; every original host test method
is unchanged. Six new host tests cover the captured gap and unsafe outcomes.
All 20 keyboard receipt cases pass; complete policy 446 plus runtime-model 15
cases pass. Two added Android component methods check that the captured gap
can be prepared but remains unready, and that unknown hints/keys/bounds refuse.
Those component methods use controlled samples, not actual keyboard input.

A new disposable API26 AVD uses the already installed official r16 image.
The exact original b995 pair is verified and its keyboard helper matches the
pre-change source. One visually reviewed first-use hint is closed once. The
observed boundary becomes coherent immediately: this controlled trial does
**not** reproduce the hosted gap, and no original preparation is invoked after
that condition fails to appear. Its failed expected-gap assertion and other
setup/read errors are retained; completed installs/settings/input are not replayed.
Existing AVDs and the owner phone are not reset or rebooted.

## Fresh build and native scopes

The required build freezes 14c4d92 plus exactly four source changes across 893
inputs. Instrumentation/debug assembly, JVM tests and both lint variants pass:
1,011 JVM cases / 97 reports, no failures/errors/skips, lint 0 errors (51 debug /
50 release warnings), original debug signing, 4 ABIs/32 native libraries,
uncompressed aligned DEX, 16KiB 64-bit ELF/RELRO and SDK zipalign checks.
Frozen source digest: `86cb8bb9225f31d30687799f8e3a7e9caab4b201528eb2bd7936376d8e48d9db`.

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `8fb335359c948810ff45f1ffd9ab1ecefc2e4e03d7cc4a833f0bf38c992377f2` |
| Test APK | `2da5c6c2aefa9fb0e020500b673071ec62b86a82aec15156737327cafe62580c` |

Only the new owned emulator's debug pair is replaced after exact old APK
identity checks and synthetic data/QA backup. New API26 standard inventory is
1,139. Standard external-model exclusions are unchanged. Separate native scopes:

| Scope | Positive / started | Command time | Raw log SHA-256 |
| --- | ---: | ---: | --- |
| Workspace, outline, input and two new component cases | 30 / 30 | 48.598s | `09f2d7e81ecec3a2298d48b092acbf410e4c54a6a68a77e81a3053a6f22a5c47` |
| Original Demo/runtime/predecessor and rendered-state cases | 14 / 14 | 33.422s | `405523e7911134d0eb317c08f08508e06f46fc7d02aadc8ded8d8d5450fcb8a2` |

Both focused cohorts pass. Both commands are terminal, with no active instrumentation afterward.
Native preparation uses the already-clear branch. Acceptance of the new gap branch against an actual native gap remains open; controlled producer/host cases are separate evidence.
No original hosted failure is claimed fixed. Focused checks do not replace the
final clean-source standard gate. The last independently verified complete gate
remains c9c0718's 1,135 API26/1,136 API37 cases.

## New upstream main is inspected, not waived

The exact 4f27df1 compatibility artifacts still flag stable v0.23.1/3b42157:
40 changed used handlers. Inspected main is now
[b51a30c](https://github.com/NousResearch/hermes-agent/commit/b51a30c461ee663313ce89d01947bb10b55a619c),
with 112 flagged used handlers. The comparison pin is 0e72e8a (104 flags).
Fourteen official old/new module files match the retained CI source hashes;
all eight newly flagged used definitions match their AST hashes. Static review:

- Profile and cron listing handlers now apply scope_to_launch_profile.
- Environment loading/publication/child preparation treats launch-only keys
  separately, changing propagation and residue handling.
- Plugin pull/pinned-feed update now verifies that Git resolves to the plugin's
  own checkout before stash/status operations, adding a refusal path.

These are behavior changes, not automatic compatibility exceptions. Exact
isolated_scope/hermes_constants dependencies, authenticated profile contexts,
child environments, update failures/rollback and wider semantic/transitive
review remain required. No upstream code is executed, baseline reset or handler
variant waived in this review. Pristine shutdown findings and the experimental
SDK candidate remain separate; nothing is deployed.

Next inspect the final-source CI outcomes, continue the original API26 rendered
Demo transition investigation, and diagnose delayed native taps without replay
or weaker guards. Real workspace/worker, automation/integration, device/security
and final production upgrade/live/offline release acceptance all remain open.
Beta.9/source 0ee and stable 1.0.0 are unchanged. Source stays on its authoritative
share; all scratch/builds/AVDs/private evidence stay on E:.

Private evidence: hosted-review1417/findings1418; keyboard-gap26-1418 original
before1419 and updated-native1427; source1420/policy1420/build1421/proof1422/
diff1423; main-delta1424 and main-used-delta1425/static-triage1426.
