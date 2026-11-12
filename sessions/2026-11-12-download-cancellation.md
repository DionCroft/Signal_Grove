# Checkpoint: model download cancellation

Reproduced blocked-header/body cancellation starvation before the fix; move only
the thread-safe cancel watcher off the occupied I/O dispatcher. Full1006 JVM and
lint/build/original-key/native/DEX checks pass. Phone/emulator cancellation and
explicit discard pass; full resume fails unchanged300s bounds. Retain all partials.
Phone cgroup freeze recovery foregrounds its existing debug process once; original
data/production package and USB-awake setting remain/restored. Current APKs are
not public. README/feature figures and validation/lifecycle notes are updated.

Next: observed foreground transfer and full resume; fix hosted API26 keyboard
preparation; retain final-source process/reboot, standard union, production-key
upgrade, compatibility, SBOM/provenance and published-download gates. Full original
audit remains active. [Evidence](../docs/contracts/2026-11-12-download-cancellation.md).
