# Signal Grove voice

Open **Voice** and choose **Dictation** or **Read aloud** at the top. The two
sections use separate Android services and privacy settings. Dictation errors do
not indicate whether read-aloud works.

## Dictation

The default **On-device speech recognition only** setting uses Android's actual
on-device recognition API. If Android reports that service unavailable, the
microphone stays disabled. You can still type and use Read aloud. Signal Grove
does not switch to system recognition automatically. Explicitly disabling this
setting selects Android's system recognizer, which may process audio online.

Choose English or Simplified Chinese, tap the microphone and review the
transcription. Chat copies the text into your draft; it does not send it.
Cancel, leaving Voice or leaving the app ends listening. Mode/language changes
clear old recognition errors. Cancelled and completed attempts cannot replace a
later attempt's transcription or error, and an obsolete permission result cannot
start a different attempt. Permission, recording, language-data, busy, service
and no-match errors have separate explanations.

## Read aloud

Send a **typed** message in Chat and wait for the assistant reply. Return to
Voice, select **Read aloud**, then **Read latest reply aloud**. No microphone
permission or recognizer is needed. With online voices disabled, Signal Grove
selects a matching installed voice that Android marks as not requiring a network
connection. Voices marked as missing data are excluded. A missing suitable voice
does not enable online voices. Use **Open speech settings** to check Android's
**Preferred engine**. An engine can be installed without being selected as the
default; this can prevent read-aloud from starting. Select a trusted installed
engine, review its normal Android prompts and check that it has an offline voice
for the selected language. Return to Signal Grove and use **Refresh speech
services**. Keep **Allow online read-aloud voices** off for offline-only playback.

The setup action opens Android's text-to-speech page, or its Accessibility settings
when the dedicated page is unavailable. If neither opens, the app explains how to
find it manually. Opening settings does not select an engine, install data, grant
consent, play a sample or change either Signal Grove privacy switch. Android and
the selected engine own any subsequent setup/download prompts.

The disabled button explains whether a reply, readable text, a finished chat
turn, an initialized engine or a suitable voice is missing. Playback errors are
separate from dictation errors. Stop speaking, changing section, leaving Voice
or leaving the app stops playback. Online voices require their own explicit
switch and may send response text to Android's speech provider. Code blocks are
skipped; text is not translated.

Availability refreshes when the screen resumes. **Refresh speech services** also
clears stale errors and recreates the read-aloud engine; it installs nothing and
does not change either privacy switch. Provider installation and language-data
availability remain Android/device responsibilities. No Hermes audio endpoint or
additional speech/model service is assumed.

## Validation boundaries

Pure state regressions and isolated Compose tests cover stale callbacks,
configuration changes, offline unavailability, independent read-aloud controls,
disabled reasons, cancellation and bilingual large text. Presentation fixtures
do not record or synthesize audio. Actual microphone/speaker/Bluetooth behavior,
offline language availability and OEM lifecycle behavior require physical-device
evidence; see [VALIDATION](VALIDATION.md) for each tested artifact and scope.

Implementation follows Android's [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
and [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
contracts. A successful enqueue is not proof that the user heard audio.


## Small screens and large text

The Read aloud action and any disabled reason appear directly below the section
selectors, ahead of the longer instructions. Language choices wrap as whole
controls. The screen retains its accessible Voice title without a duplicate
large visual heading. These arrangements keep the primary action within the
initial viewport with app bars and enlarged bilingual text. Other settings remain
scrollable; the privacy switches and explicit playback behavior are unchanged.


The dedicated setup action follows the exported TTS settings action in the
[AOSP Settings manifest](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/AndroidManifest.xml).
Its guarded fallback uses Android's documented
[Accessibility settings action](https://developer.android.com/reference/android/provider/Settings#ACTION_ACCESSIBILITY_SETTINGS).
Engine selection and language-data installation remain explicit Android/provider
flows; Signal Grove refreshes actual voice availability on return.
