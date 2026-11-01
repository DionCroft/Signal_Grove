# Security

Only the latest alpha is maintained. This client can reach a powerful agent; it is not a sandbox for server tools.

Please use GitHub **Report a vulnerability** if private reporting is enabled, or the repository owner's private contact link. Never disclose a token, personal host, transcript or exploitable details in a public issue. If no private channel is available, open a minimal issue requesting a contact channel without sensitive details.

Release traffic requires trusted HTTPS. Debug-only HTTP requires explicit opt-in. TLS validation is never bypassed; redirects are rejected. Tokens use AES-GCM and an Android Keystore key; token text never enters DataStore, saved instance state, the database or application logs. Local Room data is private to the app but not additionally encrypted. Device backups and transfer are excluded. Android clipboard copies and speech recognition can leave app-private storage when the user explicitly invokes them.

Approvals are explicit and limited to server-advertised `once`/`deny` choices. Network EOF, `assistant.completed`, `[DONE]` and stop-request acceptance do not establish a successful server run. Saved run IDs and request keys support manual recovery. Do not retry unknown operations with a fresh key without examining the server.

Never commit `local.properties`, `.env`, keystores, signing data, endpoint information, generated logs or personal conversation databases. Use the secret-review script before publication. The repository contains only synthetic network fixtures.
