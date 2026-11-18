# OF-01 request provenance — 18 November 2026

Unreleased feature-branch work; OF-01 and the full audit remain active/incomplete.
Builds on [reviewed actions](2026-11-18-local-chat-actions.md).

New local questions atomically record requested context, CPU threads and output
cap before inference. The cap is explicitly passed to the engine. A native
engine records its greedy/model-metadata policy and a source SHA-256 computed
from the CMake/CPP/header and Android build configuration bytes. This identifies
reviewed source, not an APK/binary digest, measured token use, memory or speed.
The display labels these as request settings; the output cap includes reasoning/
control tokens. Model identity remains the conversation's verified model hash.

Regeneration records its new load/request settings; earlier turns retain their
original records. Cancellation and cold interrupted recovery preserve metadata.
Old schema-1 and schema-2 histories read without migration writes or invented
settings. New recorded questions use schema 3, allowing mixed legacy/known rows.
Unknown policies/fields, invalid types and out-of-range settings are preserved
and refused. Wrappers without a runtime identity explicitly record unknown.

Request settings can be read with weights unloaded. The original-chat link and
branch description scroll inside history, keeping the composer reachable with
large English/Chinese/pinyin text. Incomplete replies explain why Branch here is
disabled and offer edit/regenerate. No server/model/credential/grant change occurs.

Final tested source snapshot `4cb10303b80f32cc892e128d7ae4569342dd8a220d2ae95721520756f6f55ce4`:

- 1,045 distinct JVM cases / 100 XML reports, zero skips/errors/failures. Seven
  new codec/provenance cases and two controller cases verify recorded parameters
  against the fake engine's actual request arguments and retain old metadata.
- Required debug app/test APK builds and zero-error debug/release lint pass;
  51/50 warnings remain. APK CRCs and published hashes match. The native-source
  fingerprint is independently reproduced from source bytes and matches all
  three generated app/debug-test/release BuildConfig constants.
- The same exact APK pair passes 24/24 selected actual methods on each API26/
  API37, zero skips, with matching dry/actual inventories. These are eleven
  synthetic action/detail UI cases, three real Android filesystem cases and ten
  existing local-AI UI cases. No real model weights or Hermes are used.

Retained failed runs: initial snapshot `ee0537db95c285fcaf8ac86296d21f6471ec762f60345aa3f5302bf1e1a007ea`
passed 22/24 on each API; two new tests addressed lazy-list descendants before
scrolling the list. The next test-only snapshot passed 23/24 on each; one test
used an exact text-element match for a number inside a full translated label.
The final tests first scroll the container, require visible controls and assert
the complete translated value. No product input changed between these attempts;
no failure, case or assertion is removed or relabelled. All raw logs remain.

Build receipt `20261118-193621-85284dbaed614d3abe2abf7df285bc33` (SHA-256
`7d52723730c350301b0b568cc68d348e4ebf14565d33821a7d80fe00fedb58a4`). Runtime source SHA-256
`0223a5df63104eb358eac6b6ebaf4a47af4f87bd3f8d3220627be3e730fa7063`. App APK SHA-256
`480476e8ec8ba7e878c55ae18e37cc6b8767f1177235ebbd7a9903e924544cb8`; test APK SHA-256
`eec73377162007421d1e15bb1140074eeedecceb6636860f05275f5f707e2ef5`.

Remaining OF-01: passive Markdown/code copying, long replies, further accessibility/
TalkBack, actual model-backed branches and physical-phone/lifecycle acceptance.
The phone is connected and its existing public LFM3 Downloads file is hash-verified;
no app/model/history/settings was changed by that read-only preflight. Public
beta.9 and the normal phone app remain unchanged. Keep every wider audit and
signed-release gate; separate UI-guard PR #1 has its own hosted source evidence.
