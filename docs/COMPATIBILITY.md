# Compatibility and protocol scope

Android 8.0/API 26 minimum; compile/target API 37. Foreground local-only speech needs Android 12/API 31 plus an installed service/model. System recognizer and TTS availability vary by device. Network speech is explicit; physical audio quality is not inferred from text/policy tests.

The app follows the [official Hermes API server reference](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/) and the upstream gateway/pending-control contracts reviewed during these milestones. No minimum Hermes version string can establish all feature support across changing/forked installs: authenticated capability discovery chooses native sessions/Runs, session streaming or OpenAI-compatible fallback. Unsupported routes remain explicit. Exact unknown-run acknowledgement is exposed only when advertised. Jobs use documented bearer-protected routes after per-connection opt-in; no invented `jobs_admin` capability is assumed.

API-owned sessions are supported; imported CLI/Desktop shared ownership is outside scope. Compatibility completion responses do not prove tool/run completion. Native Runs support persisted request keys and sequence-based manual resume; expired receipts/event buffers require server review. Jobs preserve unedited fields. No OAuth, file/image attachments, remote agent backend, automatic approvals or server-push service is included.

The shipped Demo is a local simulator. Fixture tests validate request/response and lifecycle behavior without a production agent. The owner's live endpoint/credentials and physical microphone/speaker hardware were not supplied. See the release validation record for device matrix, exact counts and any remaining manual checks.
