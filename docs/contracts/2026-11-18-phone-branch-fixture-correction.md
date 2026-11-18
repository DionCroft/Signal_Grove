# Physical branch probe: retained failure and fixture correction

18 November 2026. This is a test-source correction. The application and its
stale/current-conversation guard are unchanged. OF-01 remains in progress.

The first actual ARM64/API37 offline method fails at edited generation. It
successfully performs original generation at requested context 2,048,
regeneration at 1,024 and a fork without generation, then incorrectly asks to
edit the parent while the fork is current. The app correctly refuses that stale
selection before publishing or generating an edit. No whole-method pass is
claimed from its intermediate assertions. Stop/recovery later in the method
was not reached. The native method reports 14.128 s; the outer observation is
15.026 s, which is not a generation-throughput benchmark.

Independent private-file review verifies exactly three QA histories, exact
parent/regeneration source hashes, a matching fork and two nonempty replies
without model/reasoning markers. No edit/cancel child is published. The global
debug user-chat directory stays empty; model mappings are released. Airplane,
Wi-Fi, Bluetooth and stay-awake preferences match their exact original values.
Original failure evidence and the three histories are preserved.

The corrected fixture explicitly reopens the parent before editing; all its
assertions and counters stay. A new JVM regression proves stale edit refusal
after a fork, no publication/generation on that refusal, then exactly one edit
after reopening the parent, with parent/fork bytes unchanged. Required builds,
**1,065 JVM cases / 102 reports** and zero-error lint (51 debug/50 release
warnings) pass. The corrected external-model method itself still needs its
separate actual-phone run. The manifest remains 13 methods in five classes.

Only the original-key test companion is staged for update. The retained app
APK is not replaced. Every existing main source/asset/configuration file is
compared with that subject's build; the later unused history-search helper is
the only added main class and is not referenced by instrumentation sources.
This is an explicitly identified retained-subject/new-companion pairing, not
an assertion that both APKs came from one build. Package hashes/UIDs and all
three existing QA JSON hashes are recorded for post-install retention checks.

## Model-transfer observation

Windows ADB shell stdin stops exactly at the model's first Ctrl+Z byte at
offset 68,658. The partial hash matches that exact source prefix. Bounded
all-byte pipe probes reproduce the truncation on both installed ADB clients;
the raw `exec-in` probe preserves all 131,072 bytes. A separately named raw
transfer then verifies the full 1,049,615,680-byte pinned model SHA-256 before
atomic publication. Failed partial/synthetic fixtures remain preserved. A raw
`exec-out` read does not establish remote command exit status on this device;
preflight uses shell-v2/explicit markers and exact content checks.

No network download, credential access, normal-app update/data change, Pi
deployment, gateway/model-default or worker-grant changes occur. Fresh offline
native, UI/accessibility and final release gates remain required. The current
phone test APKs do not include the later search helper/UI as a qualified feature.

Corrected companion source snapshot: `040859774042b3e6ab919bb1fdf82b028b0912e28569d57040adcb12ed56fa32`.

Retained subject source snapshot: `638e73f7cb33810196d98524ead1d1bbe1eb57b7d90f9e5cc42a1a232b42d0bc`.

Corrected test APK SHA-256: `6615e3f947e60997e33788f60c14a863ec16f812b712afab1d3cad53f76bf465`.

Failed native transcript SHA-256: `4adff6bbec9be16f6dae464b87fe92b953709329940ec941aed4996ac1b155af`.
