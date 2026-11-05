# Signal Grove

Signal Grove is the Android companion app. Hermes Agent is the separate upstream agent running on your infrastructure. The client was previously called Hermes Agent Mobile.

Use **Signal Grove** in app labels, headings, notifications, help, screenshots and community documentation. The compact mark is **SG**. The existing palettes, including Starhaven Grove, keep their individual theme names.

## Existing installations and update compatibility

Signal Grove retains its installation identities. The source and update-feed address follows the renamed repository:

| Identity | Current value |
| --- | --- |
| Production Android package | `org.hermesmobile.app` |
| Development Android package | `org.hermesmobile.app.debug` |
| Source and update-feed address | `DionCroft/Signal_Grove` |
| Public APK asset names | `hermes-agent-mobile-VERSION.apk` and `hermes-agent-mobile-VERSION-debug.apk` |
| Task-backup format marker | `hermes-mobile-tasks`, version 1 |
| Private database, keys and durable request scopes | Existing names and values |

The repository is now **DionCroft/Signal_Grove**, retaining GitHub repository ID **1442319827**. The published [`1.1.0-beta.4` preview](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.4), code 105, checks that canonical feed and accept assets only from its exact release paths. Public APK filenames remain unchanged. No new Android package or replacement signing key is created.

Published previews through `1.1.0-beta.3` contain the former `DionCroft/hermes-agent-android` feed and require HTTP 200 metadata plus the former exact asset path. GitHub now redirects that address. A repository rename cannot replace code inside those installed APKs: they need one matching signed [`1.1.0-beta.4` APK upgrade](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.4). Install over the existing app with the original signer; private data and Keystore credentials stay with the same package. Later in-app checks use the canonical feed. GitHub's existing repository redirects remain in place.


Historical releases, source snapshots, signing-certificate subjects, protocol strings and upstream Hermes references retain their actual identities. The original contributor attribution remains an explicit former-name alias. These identifiers are compatibility/evidence records, not the current display brand.
