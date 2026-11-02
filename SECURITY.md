# Security

The latest 1.x community release is maintained. Earlier development releases are superseded. This client can reach a powerful agent; it is not a sandbox for server tools.

Report vulnerabilities privately through [GitHub private reporting](https://github.com/DionCroft/hermes-agent-android/security/advisories/new), which is enabled for this repository. Never disclose tokens, private hosts/transcripts or exploit details in a public issue. If GitHub reporting is unavailable, open a minimal request for a private contact channel without sensitive details.

Production traffic requires trusted HTTPS. Debug-only HTTP requires explicit opt-in. TLS validation is never bypassed, redirects and automatic transport retries are disabled, and path identifiers cannot escape the configured profile prefix. URL, token and debug policy are stored together in an atomically replaced AES-GCM payload backed by Android Keystore. Workers never combine separately read URL/token stores. Tokens never enter DataStore, Room, saved instance state or application logs. Room data and task exports are not additionally encrypted. Device backups and transfer are excluded.

Approvals are current-request, individual, explicit allow-once/deny decisions. Prompt content/generation/identity/choices are rechecked before mutation. Missing or incomplete consent cannot authorize execution. Unknown delivery never automatically repeats an approval, job mutation or agent turn with a new key. Closed streams, assistant completion and accepted stop requests are not authoritative terminal outcomes. Background workers only read already tracked run IDs; they cannot execute agent work, answer questions or approve tools.

Notifications are opt-in and omit private task/prompt/reply text. Widget content is limited to counts. Foreground speech and explicit clipboard/file exports can leave app-private storage when invoked. Task imports are bounded, versioned, validated entirely before a confirmed atomic merge, and cannot import credentials or job execution definitions.

Production signing material stays in ignored owner-controlled storage. See [release/signing guidance](docs/RELEASING.md), including DPAPI portability and backup requirements. Never commit local properties, environment files, keys/passwords, endpoint information, generated logs or personal databases. Run the secret-review script before publication. Network fixtures use synthetic credentials only.

Build dependencies and their metadata use committed SHA-256 verification with strict CI/Windows entry points and no blanket artifact trust. Changes require explicit hash review; ordinary CI never bootstraps trust. The initial baseline is trust on first use and does not certify absence of vulnerabilities. See [maintenance and integrity guidance](docs/MAINTENANCE.md#dependency-integrity).
