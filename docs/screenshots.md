# Signal Grove screenshots

## Beta.12 preview

These captures use the exact beta.12 UI on the Android 8 emulator at
1.3× system text. Demo mode shows the jade theme. The Activity screen
uses a synthetic subagent and conversation identity.

| Demo home | Hermes Activity |
| --- | --- |
| <img src="screenshots/beta12-demo-home.png" width="360" alt="Signal Grove jade theme with its pixel-art orbital garden"> | <img src="screenshots/beta12-hermes-activity.png" width="360" alt="Hermes Activity showing a synthetic run, subagent and child session identity"> |

## Beta.10 signed candidate

Android 17 emulator at 1080 × 1920, 420 dpi and 100% system font. Both
captures use the signed production APK in Demo mode. The catalogue
opens with the model unloaded. Its data comes from Signal Grove's bundled
catalogue.

| Demo home | Phone model catalogue |
| --- | --- |
| <img src="screenshots/beta10-demo-home.png" width="360" alt="Signal Grove beta.10 Demo home"> | <img src="screenshots/beta10-phone-models.png" width="360" alt="Model licence, download size and RAM requirement in the offline catalogue"> |

## Beta.9 candidate: on-device AI

Captured by the exact candidate's API26 LocalAiFlowTest with English, Chinese,
pinyin and 130% font scale. The model name and draft are synthetic fixture values;
this is an actual component-test capture, not a mockup or live inference output.
All ten local-AI UI cases pass; complete release/device gates remain separate.
[Candidate scope](contracts/2026-11-12-uncompressed-dex-candidate.md).

<img src="screenshots/on-device-ai-candidate-api26.png" width="360" alt="Candidate on-device chat with English, Chinese and pinyin at large text size">

## 1.1.0-beta.3 pixel interface

Original code-native orbital-garden pixel art, stepped panels and navigation icons with readable native text. These are actual screenshots of the tested app in labelled Demo mode. The narrow capture is Android 8/API 26 at 720×1280, density 320 and 130% font. The phone capture is Android 16/API 36 at 1080×2400, density 420. Motion is paused in these still images; app/system reduced motion is tested separately. Existing explicit palettes remain selectable.

| Starhaven Grove on phone | Narrow screen / 130% text |
| --- | --- |
| ![Starhaven phone](screenshots/pixel-starhaven-phone.png) | ![Starhaven large text](screenshots/pixel-starhaven-large-text.png) |

Wide emulated layout: Android 16/API 36 at 1920×1200, density 240, with navigation rail. Both motion modes and every destination passed; this is a layout check on an owned emulator.

![Starhaven tablet](screenshots/pixel-starhaven-tablet.png)

The older stable-release screenshots below document 1.0.0 and retain their original appearance and pre-Signal Grove branding.

# Native Android screenshots — 1.0.0

Captured from the actual tested app; no mockups or image edits. Phone: Android 16/API 36 emulator, 1080×2400/420 dpi. Demo fixtures contain no real server credentials or conversations. The import review is from the signed production package using one synthetic task. Earlier `chat-demo.png`/`tasks.png` remain historical snapshots; current release evidence is below.

| Lavender | Jade | Solar | Mint |
| --- | --- | --- | --- |
| ![Lavender](screenshots/home-lavender.png) | ![Jade](screenshots/home-jade.png) | ![Solar](screenshots/home-solar.png) | ![Mint](screenshots/home-mint.png) |

| Explicit Demo approval | Persisted tool activity | Foreground voice | Automation |
| --- | --- | --- | --- |
| ![Approval](screenshots/approval-1.0.png) | ![Tools](screenshots/tools-1.0.png) | ![Voice](screenshots/voice-1.0.png) | ![Automation](screenshots/automation-1.0.png) |

| 130% system text / English-Chinese-pinyin | Signed production task import review |
| --- | --- |
| ![Large text](screenshots/large-text-1.0.png) | ![Task import](screenshots/task-import-1.0.png) |

Tablet viewport: the API-36 emulator resized to 1600×2560/240 dpi, with navigation rail. This is an emulated layout check, not physical tablet hardware.

![Mint on tablet](screenshots/tablet-mint.png)
