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
| 1.1.0-beta.9 | Optional phone LLM chat/history, compatible model management, grounded setup helper, newer canonical/project navigation and startup packaging | Published experimental preview/code114 at owner request; original-key downloads verify; full compatibility/final production-phone acceptance remain open; [release record](docs/releases/1.1.0-beta.9.md) |
| 1.1.0-beta.10 | Offline chat, model and helper improvements, compact-screen fixes and signed upgrade validation | Published preview. [Release evidence](docs/releases/1.1.0-beta.10.md) |
| 1.1.0-beta.11 | Hermes Activity, reviewed phone context, compact helper fixes, and capability-based workspace and schedule controls | Published preview. [Release record](docs/releases/1.1.0-beta.11.md) |
| 1.1.0-beta.12 | Compact offline setup and composer, live tool and MCP attribution, and reviewed checkout guards | Published preview, code 117. [Release and verification](docs/releases/1.1.0-beta.12.md) |
| 1.1.0-beta.13 | Animated Home, date and seasonal scenery, optional town weather and reduced motion | Published preview, code 118. [Release evidence](docs/releases/1.1.0-beta.13.md) |
| 1.1.0-beta.14 | Shared Hermes follow-up prompts, live activity, and approvals | Published preview, code 119. [Release evidence](docs/releases/1.1.0-beta.14.md) |
| 1.1.0-beta.15 | Three built-in dark themes and matching system-bar appearance | Published preview, code 120. [Release evidence](docs/releases/1.1.0-beta.15.md) |
| 1.1.0-beta.16 | Wide navigation logo and About attribution | Published preview, code 121. [Release evidence](docs/releases/1.1.0-beta.16.md) |
| Live Pi-to-Windows proof | Authenticated phone workspace attachment, exact execution attribution, approvals, a scheduled occurrence and original receipt recovery | Pending live acceptance. [Current goal](docs/GOAL.md) |
| Offline-LLM expansion | Ten planned packages for richer chat/history/context, performance, models/transfers, selected documents, writing, personal context and offline voice/help | Added 16 November; queued under the resumed goal; [priorities and acceptance](docs/ON_DEVICE_AI_BACKLOG.md) |
| Natural on-device read-aloud | Optional neural voice pack for Hermes and Phone Chat replies, with sentence-by-sentence playback | Queued for feasibility after beta.17. [Voice plan and phone acceptance](docs/ON_DEVICE_AI_BACKLOG.md#natural-on-device-read-aloud) |
| Continuing audit / later stable release | Real interoperability, worker/workspace ownership and files, automation/rooms/management, background/audio/accessibility and community hardening | Resumed at owner request on 18 November; all acceptance requirements remain in the [audit ledger](docs/AUDIT_EXECUTION_PLAN.md) and [current inventory](docs/audits/2026-11-15/PAUSED_REVIEW.md) |

Seven selected current-app physical offline methods, ten earlier API26 local-AI
UI cases and 1,011 candidate JVM cases pass within their separately stated scopes.
Current native HTTPS cancellation/discard and full foreground resume have physical
positive evidence. Earlier timeout/freezer failures remain documented separately. Test totals do not establish full
milestone completion. [Current features and figures](docs/FEATURES.md).

## Further ideas

Image input/history/share and advanced profile/canonical work have since been
added to source; they are no longer merely future ideas. Full-duplex voice,
user-authored themes and Wear OS remain future possibilities unless deliberately
added to the goal. No release dates are promised. Hardware/live-server evidence
remains separate from emulator and fixture acceptance.
