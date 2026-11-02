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

## Later ideas

Attachments/images, full-duplex voice where supported, advanced profiles, custom themes and Wear OS are outside these four mandatory milestones unless explicitly added later. No release dates are promised. Physical-device/live-Hermes checks are reported separately from emulator/mock-contract tests.
