# Verified optional model download

The download component pins the recommended LFM3-1.7B QAD Q4_0 to the revision,
size and SHA-256 in `RecommendedLocalModel`, matching
[the provenance manifest](../on-device-ai-provenance.json). Weights are not
bundled in the application or source repository. The UI integration must present
the separate LFM Open License v1.0 and obtain explicit download approval.

The component requires licence acknowledgement and uses an independent HTTPS
client, with no Hermes authentication/cookies, transport retry or protocol
downgrade. A partial belongs to its content identity. Resumption requires an
exact `206 Content-Range`; an ignored range cannot silently truncate it. Size,
whole-file checksum and GGUF checks precede atomic installation. Cancellation
retains a resumable partial; explicit discard removes only that partial.

Eleven JVM cases pass for installation, exact-offset resume, ignored-range
retention, checksum failure/discard, cancellation/resume, licence/space refusal
before a request, insecure source refusal, malformed ranges/statuses, a corrupt
resume prefix, incompatible metadata despite matching hash and wrong length.
They use controlled HTTP responses; they do not certify a live Android download.

The development build passes 895 JVM cases in 88 reports, both lint variants with
zero errors (50 debug / 49 release warnings), and compiles instrumentation. It
contains other work and is not an exact-commit release claim for this component.
The measurement app is SHA-256
`0b350d8f08c004fd2a93f068fd11621a3933b182ccaab2db37797f5e8ac55ade`.
Raw source/signature evidence stays outside Git. The component has no chat or
tool-execution capability. Android consent/download UI, live transfer recovery,
picker integration, native lifecycle, local chat, helper and the full Hermes
audit remain required.
