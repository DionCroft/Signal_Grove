# Selected phone-chat Markdown foundation — 19 November 2026

## Implemented internal component

LocalConversationStore can now read one exact expected saved conversation and
prepare a frozen Markdown document. The source JSON's actual byte SHA-256 is
recorded, alongside conversation ID, model fingerprint, saved UTC activity,
terminal/partial reply states and any recorded context/thread/output/runtime
settings. Older missing settings remain explicitly unrecorded. It never
recovers an active generation, rewrites a transcript, changes labels, loads
weights, inspects other conversations or reads server state/credentials.

The format1 document uses fixed English field headings and UTF-8, plus an
optional literal display title. Each question/reply/title is kept inside a
literal code block, including its original Unicode, line endings and Markdown.
The delimiter exceeds every matching run in that body, so embedded fences,
HTML and links stay text. This exports the source text rather than applying
rich reply styling. The [CommonMark fenced-block rules](https://spec.commonmark.org/0.31.2/#fenced-code-blocks)
provide the syntax basis. The suggested filename uses only an ID prefix, never
question/title text or a local model path.

Preparation refuses a changed expected snapshot, active/empty chat, corrupt
source, malformed UTF-16/UTF-8 conversion or invalid title. Bounds retain the
existing 30-turn/input/reply limits; the resulting document is capped at12MiB,
including delimiter expansion. Frozen private bytes survive later source
changes and cannot be mutated by a consumer. Bounded8KiB writes have cancellation
checkpoints and propagate failure without another write attempt. The caller
owns destination opening, flush and close acknowledgement; an output failure
can leave a partial destination. The component never deletes such evidence or
claims that a destination was successfully published.

## Verification

Required build/JVM/lint/APK tasks pass 1,107 distinct JVM cases/104 reports with
no failures/errors/skips; lint has zero errors and retains51 debug/50 release
warnings. Fourteen new JVM cases exercise selected-only reads even beside a
broken unrelated chat, exact content/fingerprints, malicious fences/HTML/links,
recorded versus unknown settings, all non-generating states, frozen and stale
snapshots/model identity, active/empty/corrupt/malformed refusals, cancellation,
no-retry destination failure, consumer mutation, the full supported30-turn size
and locale-independent bytes. The oversized malicious-title fixture is bounded
before its first compilation; no failure/pass is inferred from that draft.

The same new app/companion passes exactly58 selected methods on each API26/37
without skips. All earlier56 remain. Two new methods use real Android private
QA files, verify Unicode/state/settings/source hash and cancellation with an
8192-byte partial output, and prove unchanged history bytes/file inventory.
These fixtures use no user histories, models, network or external destinations.

Source snapshot: `4ebb0c71f79cd2b6517abbf613cab1686a81ae99eca64d8be5696ac1f4591505`.

Build receipt: `20261119-094347-ff3e81a1cd6f4e12a8c1f8f04178b7ed`.

Emulator app SHA-256: `0972541645a0833bfebb37a8f29bb4de12996807ad1dc07c7e3f575bc05661d8`.

Companion SHA-256: `01ec09ffeb40c0b4b7a0a699acd273cb7c4efb303cac658aefe2b711c7fd5e47`.

API26 actual raw SHA-256: `3560b109e8199fbfcad191cfd05dc776fd7450ec6365196591c3ae2ac5a537bb`.

API37 actual raw SHA-256: `dcd13913daff4dae80f82e8dfa8442a0216d32d0a26a468fc41261ddee373d48`.

## Remaining work and other checkpoints

This component has no user-facing export control yet. Finish the selected-chat
review, one-shot destination callback, Android document writer/cancellation,
process/recreation handling, truthful partial/unknown outcomes and real offline
phone/provider checks. The planned picker uses Android's
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
Versioned/encrypted backup/import remains a separate required OF-02 delivery;
this readable plaintext document is not an importable or encrypted backup.

The prior UI-only88a8d05 full run44496075372 has a successful build/policy job
and four live device shards at this checkpoint. Its build ZIP is independently
verified: 1,093 JVM/103 reports, zero lint errors (52/51 hosted warnings), exact
APK hashes and matching PR-head/merge tree3d36495. No complete device result is
claimed. Preserve that run before pushing this component; its source predates
the new codec/tests. Final-source complete coverage remains required.

The owner phone is reconnected. Read-only observation verifies the retained
app8bda, all four actual UI chat files, no running debug process/model and no
saved organization index. Companion960fac43d1de is staged against the unchanged
installed subject; 13 protected non-database metadata files, both models and
all APKs remain unchanged during staging. Database hashes are recorded
separately. Manual installation/physical identification and organization checks
are pending observation. The normal code112 app remains unchanged; these new
foundation APKs are only tested on emulators and are not staged to the phone.

Official latest stable is still v0.23.2 ae2a81c. The newly observed development
head is [01e7f71](https://github.com/NousResearch/hermes-agent/commit/01e7f71373ce810e7dc798e49c57c77df0fa95c9).
Its hermes_state shim explicitly points to persistence/persistence_assembly,
and cron argument helpers exist at cron/job_args.py. This read-only observation
updates the next review target; it does not close the fail-closed source-tree,
transitive/semantic or owner shutdown findings. No source guard/baseline, Pi
service, credential, profile grant, server model/default, main or release changes.

Every original audit and OF-01..OF-10 requirement remains active. Next: finish
the current phone/UI gate and full88 run, push this tested component, then
connect the reviewed Markdown/file-picker flow and validate actual outcomes.
