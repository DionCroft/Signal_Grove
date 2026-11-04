# Signal Grove

Signal Grove is the Android companion app. Hermes Agent is the separate upstream agent running on your infrastructure. The client was previously called Hermes Agent Mobile.

Use **Signal Grove** in app labels, headings, notifications, help, screenshots and community documentation. The compact mark is **SG**. The existing palettes, including Starhaven Grove, keep their individual theme names.

## Existing installations and update compatibility

The product rename preserves the following technical identities so existing data, logins, widgets, backups and approved updates continue to work:

| Identity | Compatibility value |
| --- | --- |
| Production Android package | `org.hermesmobile.app` |
| Development Android package | `org.hermesmobile.app.debug` |
| Source and update-feed address | `DionCroft/hermes-agent-android` |
| Public APK asset names | `hermes-agent-mobile-VERSION.apk` and `hermes-agent-mobile-VERSION-debug.apk` |
| Task-backup format marker | `hermes-mobile-tasks`, version 1 |
| Private database, keys and durable request scopes | Existing names and values |

The older updater verifies the exact repository path and APK asset names. Renaming that feed immediately would prevent those installed clients from discovering a normal upgrade. Repository links therefore use the existing compatibility address while their displayed product name is Signal Grove. No new Android package or replacement signing key is created.

Historical releases, source snapshots, signing-certificate subjects, protocol strings and upstream Hermes references retain their actual identities. The original contributor attribution remains an explicit former-name alias. These identifiers are compatibility/evidence records, not the current display brand.
