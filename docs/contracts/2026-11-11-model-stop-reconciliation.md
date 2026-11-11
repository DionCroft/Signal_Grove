# Stopped transfers reconcile actual model files

The controller now reads installed model metadata and the recommended partial's
size before a cancelled import/download returns to idle. Atomic publication may
already have completed even if cancellation prevents its result reaching Main.
A completed model remains installed and visible; Stop does not silently delete it
or repeat the import. No weights are automatically loaded and no chat is replayed.

A stopped provider may throw its own close/cancellation exception while a read is
blocked. The import boundary checks coroutine cancellation before forwarding an
active exception. This covers cancellation without turning genuine active errors
into success. Reconciliation failure uses the existing bounded error reasons;
it preserves files and releases the work guard after recording the failure.
Only model-library/partial state is read during reconciliation. It does not
recover or rewrite chat history, query Hermes or read connection credentials.

## Regression and real Android evidence

Three new JVM cases first failed on the unchanged controller: committed imports
were absent from the screen state, a closing provider produced operation_failed,
and a corrupt companion file was not reported during stop reconciliation. Those
baseline results are retained. The same cases now pass, including guard retention
while I/O is pending, no automatic engine load/request, exact committed file
retention, typed metadata errors, original corrupt-file preservation and cleanup
of only the unfinished import partial.

The new opt-in LocalImportCommitIntegrationTest runs the production controller,
store and publisher-verified 1,049,615,680-byte LFM3 file on Android 17 x86_64.
It keeps Main responsive during the large copy. After copy EOF it briefly holds
the return-to-Main path while real sync, metadata checks and atomic publication
finish. It observes the committed file and active guard, requests cancellation,
then releases Main. The correct model becomes visible without reopening its
input stream, loading weights or creating a chat. Its complete SHA-256 is
verified again. Explicit cleanup removes only the test copy; the original staged
publisher file remains unchanged. Both release gates have independent timeouts
and are released in finally. This is a controlled publication/dispatch race,
not Android process-death or reboot acceptance.

The same application also passes fresh unchanged live HTTPS checks: Android 8
cancellation/discard and Android 17 retained partial, controller recreation,
explicit full resume, publisher checksum and atomic install. These are three
new Android executions, not a complete instrumentation suite. The full build
passes 921 JVM cases in 90 reports, 229 policies, both APK assemblies and
zero-error lint (50 debug / 49 release warnings). Original debug signatures and
package IDs are retained. Native inference, networking policy and the already-
staged phone helper APKs are unchanged by this controller correction.

Frozen source digest: `854258d1edc06777967d58a3d8e1ab72876036399a2d6614400f1b6e555039af`.

- app APK SHA-256: `06825a7bb44d7e140bca2dcc6d61786f4bc63da5cd44ec853335abdc4d2cb747`
- test APK SHA-256: `7787223e02b44898aecce01a8922050894b684b9e2910f36aa75754092cf47d5`

To reproduce, stage the exact official LFM3 file as lfm3.gguf in a unique app-private
other-model-probe-<12 hex digits> directory. Run LocalImportCommitIntegrationTest
with localOtherModelProbeDirectory set to that directory. Without staged input
this opt-in case is skipped. The live network class has its separate explicit
licence/network argument, documented in [the transfer contract](2026-11-11-live-model-transfer.md).

Still open: separately controlled download-publication interruption, actual
Android process/force-stop/background/reboot recovery, physical
phone/current-source upgrade and helper/download checks, normal Qwen picker and
physical model switching/cancellation, broader device coverage, every original
Hermes audit requirement and exact-source signed-release acceptance. These tests
do not publish a production APK or certify all corruption-recovery UI behavior.
