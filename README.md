<h1 align="center">Signal Grove</h1>

<p align="center"><strong>An open-source Android companion for Hermes Agent</strong></p>

<p align="center">
  Follow your agent from your phone, or load a compatible model for a separate offline chat.
  Original pixel art, five themes, and English, Simplified Chinese and pinyin.
</p>

<p align="center">
  <a href="https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.11/hermes-agent-mobile-1.1.0-beta.11.apk"><strong>Download beta.11 preview APK</strong></a>
  · <a href="https://github.com/DionCroft/Signal_Grove/releases/tag/v1.0.0">Stable release</a>
  · <a href="docs/SETUP_GUIDE.md">Setup guide</a>
</p>

<p align="center">
  <img src="docs/screenshots/beta10-demo-home.png" width="228" alt="Signal Grove Demo home with a pixel-art orbital garden and a New conversation button">
  <img src="docs/screenshots/beta10-phone-models.png" width="228" alt="On-device model catalogue showing the LFM3 licence, download size and memory requirement before download">
</p>

<p align="center"><em>Demo home and on-device model catalogue, captured in beta.10.</em> <a href="docs/screenshots.md">More screenshots</a></p>

## Install or update

Signal Grove runs on **Android 8.0 and later**. It is an independent community app for [Hermes Agent](https://github.com/NousResearch/hermes-agent).

| Channel | Version | Download |
| --- | --- | --- |
| **Preview** | **1.1.0-beta.11**, version code 116 | [Production APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.11/hermes-agent-mobile-1.1.0-beta.11.apk) · [Release notes and checksums](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.11) |
| **Stable** | **1.0.0** | [Production APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.0.0/hermes-agent-mobile-1.0.0.apk) · [Release notes and assets](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.0.0) |

For an in-place update, install the matching production APK over your existing production installation. Matching the package and signing key preserves its data. Preview builds with the updater can check **Settings → App updates**. Enable **Include preview releases** to find betas, review the download, and confirm the installation in Android. [Update and migration guide](docs/APP_UPDATES.md)

## Get started

1. **Explore in Demo mode.** It starts enabled on a fresh installation. Try the interface, chat, tasks and sample approvals without a Hermes server. [User guide](docs/USER_GUIDE.md)
2. **Connect your Hermes server.** Use its authenticated API over trusted HTTPS, for example through a private Tailscale network. In **Settings → Server**, enter the API root and bearer token, then tap **Save & test connection**. Management features use a separate dashboard sign-in. [Full connection guide](docs/SETUP_GUIDE.md)
3. **Try offline chat.** In beta.11, open **Settings → On-device AI → Phone models**. Review a catalogue model's licence or import a compatible GGUF file, then tap **Load**. You’ll need a supported 64-bit device, enough memory and storage, and a separately installed model. Phone chat has its own history. [Model and offline chat guide](docs/ON_DEVICE_AI.md)

## What you can do in beta.11

| Area | How it helps |
| --- | --- |
| **Follow Hermes conversations** | Stream replies, browse existing history, and see reported tool and subagent activity, waits, outcomes, and model and provider. Automatic model selection can follow the verified serving model. |
| **Work offline on your phone** | Generate text locally, review the prompt budget, edit or branch a chat, search history, and export or back it up. LFM3 is verified at up to 4K on the tested phone. |
| **Review work before it runs** | Follow approval requests, browse bounded read-only workspace previews, and review upcoming schedules, time-zone offsets and scoped run receipts where supported. |
| **Make it yours** | Use built-in phone tasks, a task widget, five pixel-art themes, scalable text and reduced motion. Dictation and read-aloud use Android’s installed speech services. |

Hermes controls depend on the connected runtime, authentication, and configured capabilities. Workspace browsing also needs an enrolled worker. The [compatibility guide](docs/COMPATIBILITY.md) and [feature matrix](docs/FEATURES.md) explain availability by area.

## Release verification

The published beta.11 APK is **76.6 MB**. Its exact source passed **1,262 Android 8** and **1,263 Android 17** instrumentation methods. A physical Xiaomi in-place upgrade retained the saved Hermes connection, existing history and local model library. The APK checksum, public signing certificate, runtime SBOM and build provenance are available with the release. [Beta.11 verification](docs/releases/1.1.0-beta.11.md) · [Full validation evidence](docs/VALIDATION.md)

## Help and development

- **Using Signal Grove** · [Setup](docs/SETUP_GUIDE.md) · [On-device AI](docs/ON_DEVICE_AI.md) · [Management](docs/MANAGEMENT.md) · [Troubleshooting](docs/DEVICE_DIAGNOSTICS.md)
- **Project status** · [Changelog](CHANGELOG.md) · [Roadmap](ROADMAP.md) · [Open audit items](docs/AUDIT_EXECUTION_PLAN.md)
- **Contributing** · [Development guide](docs/DEVELOPMENT.md) · [Contributing guide](CONTRIBUTING.md) · [Report a bug](https://github.com/DionCroft/Signal_Grove/issues/new/choose) · [Report a security issue](SECURITY.md)

The app uses Kotlin and Jetpack Compose. Use JDK 24 and the checked-in Gradle wrapper.

~~~sh
./gradlew testDebugUnitTest lint assembleDebug assembleDebugAndroidTest
~~~

On Windows, use the [snapshot build wrapper](scripts/build-windows.ps1) with tools and temporary files on the E drive.

~~~powershell
.\scripts\build-windows.ps1 -WorkRoot E:\Temp\hermes-mobile -AndroidSdk E:\Android\Sdk
~~~

[MIT licence](LICENSE) · [Third-party notices](NOTICE.md)
