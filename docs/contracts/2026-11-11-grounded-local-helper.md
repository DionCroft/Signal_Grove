# Versioned local setup summaries and observations

Historical freeform implementation. Superseded by [reviewed check selection](2026-11-11-reviewed-local-checks.md) after actual phone semantic probes found misleading host/phone instructions. Earlier test results below retain their original scope.

The optional helper has six preset topics: phone setup, private networking, API
authentication, serving-model selection, conversation history and host projects.
It uses the same bundled Words/SetupGuideContent facts as the deterministic guide,
revision `signal-grove-setup-2026-11-10`, plus static examples with placeholders.
No saved address is interpolated into model sources. Sources remain readable
without weights, and official-reference links require a user tap.

Observation sharing defaults off. A request can explicitly include a timestamped
typed snapshot of Demo mode, URL scheme category, process bitness and whether
local weights are loaded. There are no string fields for hostnames, credentials,
paths, logs, profile names or chat content. Scheme classification does not prove
that an address is valid, reachable or authenticated. No server probe runs.
The UI shows the exact selected snapshot and says server readiness is unknown.

Each helper call has a new bounded prompt containing only selected sources and
that snapshot. It does not read/write local chat history or Hermes state, and
later chat requests do not inherit its facts. Candidate advice is withheld until
generation finishes and citation/language/known-setting/inline-code/link checks
pass. Failed, incomplete and rejected summaries leave the source guide available.
These narrow checks are not semantic proof that all advice is correct. No model
output changes configuration, executes a command, answers an approval or sends
anything to Hermes. Leaving releases weights and clears transient helper output.

## Scoped development evidence

The combined measurement build passes 914 JVM cases across 90 reports, including
thirteen controller cases and six knowledge/response-guard cases. Both lint
variants have zero errors (50 debug / 49 release warnings). Required debug/test
APK assembly, original debug signer, four ABI and ELF alignment checks pass.
The final source also passes all 229 policy cases. Eight synthetic Compose flows
pass on each of API 26 and API 37, including explicit observation sharing and
retained conversation/composer space at font scale 1.3 with Chinese and pinyin.
App-content captures on both are visually inspected: all tab captions and footer
controls are readable. The earlier global screenshot that captured the API 26
launcher is retained and is not visual acceptance; the test now captures its own
Compose root. Visual review also caught cut-corner tab-caption clipping; native
tabs correct it without shortening the translated labels.

App SHA-256: `a32548753e100d7238e855a4fc7f68f1b1364e3d0260d5c96eaf4ea6c7d29184`.
Test SHA-256: `a38892dc84fbdb1a28e952bed3aef44fbf18a8d7c790402a4e7bd562e467fae9`.
Private frozen input manifests, reports and immutable APKs remain on the authorized
build volume. The preceding build's test-helper compile error is retained; it was
corrected using explicit bounds subtraction without weakening the layout assertion.

Phone CLI prompt preflight first exposed verbosity, an invented `API_ENABLED`
setting and an English answer to a Chinese request. Those outputs are retained
as failures of the candidate helper quality, not accepted setup guidance. Adding
exact versioned examples and language-specific concise questions produced correct
API setting names and a Chinese answer in three subsequent synthetic CLI probes.
The guard independently rejects the observed wrong setting/language, unknown
citations/links/inline commands and fenced code. CLI prompts start an ephemeral
phone-loopback service that exits with the CLI; this is not embedded acceptance.

The debug launcher now says Signal Grove (debug), separate from the unchanged
production package. The verified public model/licence are staged in Downloads
for a normal, reviewed document-picker import. No production APK, saved connection,
Pi credential, service, server model or default was changed by this chunk.

The matching app/test updates are hash-verified in the phone's Downloads; Xiaomi
requires the owner's normal installer prompts. Production APK hash remains
unchanged. They are staged development updates, not a published release.

Still required: actual embedded helper quality/language checks, the physical app
UI with radios disabled, real picker/download cancellation and integrity controls,
model-library lifecycle/process/upgrade retention, broader model/device coverage
and exact-source release checks. The complete original Hermes audit remains open.
