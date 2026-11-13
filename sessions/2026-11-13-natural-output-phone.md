# Natural-output phone checkpoint

13 November 2026. Source0ee08e3 is committed/pushed and builds with 1,011 JVM
passes, zero-error lint and the original signers/native packaging. Actual offline
garden, metadata, Qwen, model-switch/history and atomic-import cases pass. Two
combined cases fail at short completion allowances; six helper selections finish
before the Chinese limit. Failures are retained. Exact-context diagnostics observe
75 Chinese/108 second-reply total tokens; a test-only correction preserves every
original short probe and adds separate bounded exact-completion checks.

Owner data/rows/models/package identity are retained; 16/20 files are identical
after testing, with profile/WorkManager bookkeeping separately reconciled and
private SQLite copies removed. Fresh dependency/SBOM evidence remains local.
Compatibility still requires40/101 used-handler reviews; complete Android union
and final production acceptance/publication remain open. No audit row is closed
by this checkpoint. Next: compile the companion and exercise the failed cases
against the exact existing application without modifying owner data.

[Full evidence and limits](../docs/contracts/2026-11-13-natural-output-phone.md).
