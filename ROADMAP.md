# Version goals

Versions are completed in order with one active version goal at a time. A goal requires implemented controls, relevant tests/build/lint, honest documentation and a reviewed public release. Unsupported backend features remain explicit. Server data, credentials and signing material never enter Git.

| Version | Scope | Status |
| --- | --- | --- |
| 0.2.0 | Better session management; task notes/search/filters; cached read-only Hermes scheduled jobs; non-destructive migration | Complete; public release available |
| 0.3.0 | Voice improvements; richer persisted tool activity; explicit approval/clarification handling and run-state recovery | Complete; public release available |
| 0.4.0 | Opt-in notifications; Android task widget; explicit, supported server automation controls | Complete; public release available |
| 1.0.0 | Stability/security/migration hardening; documented compatibility and release process; signed community release | Complete in 1.0.0; see release evidence |

## 0.2.0 evidence

See [the release record](docs/releases/0.2.0.md). Session controls distinguish local pin/hide from remote rename/delete. Jobs were read-only in 0.2.0; confirmed mutation controls arrived in 0.4.0.

## Current release and audit work

| Delivery | Scope | Status |
| --- | --- | --- |
| 1.1.0-beta.8 | Signal Grove pixel interface/updater, serving-model awareness, preset reasoning/service choices, existing chats and stored projects | Published preview; [release evidence](docs/releases/1.1.0-beta.8.md) |
| 1.1.0-beta.9 | Optional phone LLM chat/history, compatible model management, grounded setup helper, newer canonical/project navigation and startup packaging | Candidate/code 112; tested chunks on main, final transfer/device/compatibility/release gates open; [candidate record](docs/releases/1.1.0-beta.9.md) |
| Continuing audit / later stable release | Real interoperability, worker/workspace ownership and files, automation/rooms/management, background/audio/accessibility and community hardening | Active; every unresolved requirement remains in the [audit ledger](docs/AUDIT_EXECUTION_PLAN.md) |

Six earlier physical offline native cases, ten earlier candidate API26 local-AI
UI cases and the current 1,006 candidate JVM cases pass within their stated scopes.
Current native HTTPS cancellation/discard pass; resume retains timeout failures.
Phone kernel-freezer recovery is documented separately. Test totals do not establish full
milestone completion. [Current features and figures](docs/FEATURES.md).

## Further ideas

Image input/history/share and advanced profile/canonical work have since been
added to source; they are no longer merely future ideas. Full-duplex voice,
user-authored themes and Wear OS remain future possibilities unless deliberately
added to the goal. No release dates are promised. Hardware/live-server evidence
remains separate from emulator and fixture acceptance.
