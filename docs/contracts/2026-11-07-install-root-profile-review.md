# Installation-root and profile-publication follow-up

The [992 compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/42348318248)
passed166 parser/15 runtime/8 pinned real-registry cases, but failed its main
canary after upstream advanced from981 to
`d4e7c3434209861f5cc6da0649e2df1f11f53e73`. Stable v0.23.0 remained clean.
That failed outcome is retained. All three artifact ZIP digests were verified
before their reports were read:

| Artifact | ZIP SHA-256 |
| --- | --- |
| main | `854fc8acd0ebb2d94fd5c570b804ee4f5d93db4f74912c5a41d91b69f52f220d` |
| stable | `9391e7960759a5b1b836cb4f02c518bf9f1b76e2cd89a2ee683685051e6c6ee9` |
| pinned registry | `8a65268e7b1d19238443a829dd34e8554cc45a6c8ee1aa5954d083e4f35ae8ad` |

## Exact changed source

The [pinned comparison](https://github.com/NousResearch/hermes-agent/compare/981791d9d41fd2efde27f563873ce8c48ef1dac6...d4e7c3434209861f5cc6da0649e2df1f11f53e73)
contains nine commits. The source watcher saw three unique consumed files change
(two selected module IDs share web_server.py). All complete before/after files
were checked against primary Git blob IDs, current SHA-256 values matched the CI
report, and their complete small diff was read without importing the modules.

In [config.py](https://github.com/NousResearch/hermes-agent/blob/d4e7c3434209861f5cc6da0649e2df1f11f53e73/hermes_cli/config.py),
get_project_root now wraps the canonical project_root_str helper. In
[web_server.py](https://github.com/NousResearch/hermes-agent/blob/d4e7c3434209861f5cc6da0649e2df1f11f53e73/hermes_cli/web_server.py),
PROJECT_ROOT uses that same helper. The complete
[_startup_fast.py](https://github.com/NousResearch/hermes-agent/blob/d4e7c3434209861f5cc6da0649e2df1f11f53e73/hermes_cli/_startup_fast.py)
Git blob `fe780f6190df500dc9f01c0bc14f15da8315b270` is identical at both pins.
Its helper returns realpath(join(dirname(__file__), pardir)), selecting the
installation root. It does not select a user project or grant workspace access.
No filesystem/symlink/deployment equivalence beyond that source review is claimed.

In [profiles.py](https://github.com/NousResearch/hermes-agent/blob/d4e7c3434209861f5cc6da0649e2df1f11f53e73/hermes_cli/profiles.py),
successful import clears the destination's deletion tombstone, while successful
rename clears both old and new names rather than only conditionally clearing the
old one. Associated comments changed. Native profile inventory does not become
an import/rename control, and these paths were not executed against owner data.

No consumed route, flag, request field or watched AST changed. The six findings
were caused by the new complete config.py identity
`ac6677722a209d62a602fe6d3140abdd394502a9c2926447dc194f9cd2a35166`
failing the four-source guard vector introduced in the
[Yuanbao review](UPSTREAM_981791D_REVIEW.md). The other three guard modules and all
six selected AST fingerprints are unchanged. This is expected guard behavior,
not a reason to waive the source fence.

## Separate reviewed mode and limits

Six additional entries bind the same ASTs to the separately reviewed d4 vector.
The981 entries and F0/all earlier evidence remain intact. Every entry still
requires all four exact modules; there is no moving-ref, wildcard or changed-AST
allowance. A new synthetic regression proves that two complete reviewed source
vectors can coexist while an unreviewed mixture is refused. The production-policy
test checks both exact vectors and excludes an unguarded duplicate of either AST.
No checker algorithm, Android app/test/build input, schema or network behavior
changes in this follow-up.

All167 parser and15 runtime tests pass locally. Saved digest-verified stable,
981 and d4 snapshots compare cleanly. Sixteen missing/changed-source
counterfactuals (eight for each reviewed main vector) re-open all six findings.
The previous39 controlled function probes are retained as their original cohort;
they are not relabeled as new live tests. New exact-source hosted gates remain
required. No Pi service, model/default, credential, phone package or data changed.

Complete-byte guards cover the four specified modules. Other transitive helpers,
runtime filesystem/profile lifecycle and installed provider behavior remain
separate review/live-acceptance responsibilities. Fresh final-source signed
assets and publication checks are still required; the signed992 pair is retained
as an unshipped predecessor with only VCS metadata differing from accepted b02
payloads. The full audit remains active.
