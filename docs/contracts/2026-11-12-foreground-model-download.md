# Foreground phone HTTPS model acceptance

The exact beta.9 debug candidate passes the original
`livePinnedDownloadCancelsAndExplicitlyResumes` method on the owner's ARM64 API37
phone. Its original timing assertions, production HTTPS transport and public
model remain unchanged. A new isolated test root preserves every earlier failed
partial; no uncertain transfer is replayed.

The resolved debug launcher is foregrounded once, 0.652 seconds after the native
command starts. The phone is unlocked and temporarily held awake over USB.
Six samples, at roughly five-second intervals, verify resumed foreground,
unlocked/awake screen and kernel cgroup freeze0. No sampled state is lost.
This supplies the foreground condition missing from the earlier frozen test.
It does not establish unrestricted background or screen-off model downloads.

| Observation | Verified result |
| --- | ---: |
| Real HTTPS cancellation | 3 ms |
| Retained prefix; no automatic resume | 8,451,250 bytes |
| Explicit resume, full hash/metadata check and atomic install | 27,010 ms |
| Full model size | 1,049,615,680 bytes |
| Native command elapsed | 29.917 s |
| Native case envelope | One exact start/finish, OK(1 test), terminal-1; no failure/skip |

The pinned SHA-256 is
`43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452`.
The method verifies the cold controller does not silently resume or load a model,
then explicitly resumes, hashes and atomically installs the complete LFM3 file.
It deletes only its newly downloaded test model after verification; no GGUF or
partial remains in that test root. The separately installed owner model remains.

Build-input digest and original-key APK hashes are unchanged from the
[cancellation-fix candidate](2026-11-12-download-cancellation.md). Raw native-log
SHA-256 is `862e1774953d8f4ee2af8f3b002937adc584c454521ed6b320d1e278c11b6dcf`.
Strict verification removes only validated diagnostic bundles, then requires the
unaltered complete successful case envelope and exact inventory identity.
Both original model hashes, owner chat/settings hashes, all three UIDs and
installed APK hashes survive. Production remains beta.8. The temporary USB-awake
setting is restored to its original value; no instrumentation remains active.

Together with the earlier current-source explicit-discard case, both HTTPS
methods now have positive physical native evidence. Earlier emulator and frozen
phone timeout outcomes remain retained. This is a single phone/network
observation, not a throughput guarantee or an endurance result. Keep On-device AI
visible and the phone awake for downloads; the activity stops work when it leaves
the foreground. Process/reboot, final standard CI/keyboard preparation,
production-key upgrade, compatibility, SBOM/provenance and publication gates
remain. The full audit stays active; no new public APK is claimed here.
