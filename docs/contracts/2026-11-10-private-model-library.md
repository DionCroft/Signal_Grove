# Private model library component

This component copies user-selected GGUF bytes into an app-private library. It
does not provide a chat interface, load weights, execute tools or establish native
model compatibility. It depends on the separately accepted bounded GGUF reader.
No picker URI, external filename, server credential or conversation is persisted.

Imports require an explicit licence acknowledgement. Each import owns a unique
partial file; size, available space, cancellation, optional expected SHA-256 and
metadata checks precede an atomic move to a content-addressed installed filename.
Partials are excluded from the installed list. Duplicate imports retain existing
bytes; a corrupted existing identity is refused instead of overwritten. Loading
requires a fresh whole-file hash and preflight. Explicit deletion accepts only a
SHA-256 identity within the private library. The integration must unload a model
before offering its Delete action.

Validation on 10 November 2026:

- Eleven JVM cases cover valid imports and another supported architecture,
  duplicate retention, wrong checksum, malformed/unsupported metadata, licence
  acknowledgement, cancellation with stream closure, space reserve, installed
  corruption, refusing corrupt duplicates and path traversal refusal.
- The first scoped JVM build passes 876 cases in 86 reports. A later development
  build passes 887 cases in 87 reports, both lint variants with zero errors, and
  compiles both debug APKs. It includes other uncommitted development work;
  these totals are not an exact-commit release claim for this component.
- Lint retains 50 debug / 49 release warnings, including two `UsableSpace`
  suggestions in the constructor. The current policy conservatively checks real
  usable space and leaves 64 MiB free; it does not count reclaimable caches as
  already available space or silently reclaim them. No lint issue was suppressed.

The measurement development application is SHA-256
`882f98f9bbdc321dd8a12e8ed897ddb96581bd282706f0ca667b6216cf05efe1`.
It has not been installed on the owner's phone or published as a release.
The exact source snapshots and original-key signature checks are retained in
private build evidence outside Git. The library's 11 cases are independent of
the larger native-engine and downloader work in that measurement tree.

Still required: Android picker integration and cancellation, download UI,
model load/unload/delete controls, process interruption, local conversation
history and routing, grounded helper and the complete original Hermes audit.
This component's tests close none of those acceptance requirements.
