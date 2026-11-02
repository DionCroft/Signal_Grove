# Using Hermes Agent Mobile 1.0

## Install and migrate

Get `hermes-agent-mobile-1.0.0.apk` from the [community release](https://github.com/DionCroft/hermes-agent-android/releases/tag/v1.0.0). Verify its SHA-256 and signer against the release record before installing. Android may ask you to allow installation from your browser/file manager. This is a GitHub-distributed APK, not a Play Store listing.

The production package is `org.hermesmobile.app`. Earlier development APKs use `org.hermesmobile.app.debug`; both may coexist. To retain the development app's data, install the separately supplied `1.0.0-debug` upgrade. To move local tasks to production, first update the debug app to 1.0.0, then Settings → Export tasks, install/open production, and Settings → Import tasks. Review the count and confirm. Matching task IDs are replaced; unlisted tasks remain. Reimporting the same file does not duplicate IDs.

Task files are plain JSON containing titles, notes, completion, priority and dates. Choose private storage and remove exported files when no longer needed. Maximum import/export is 2 MB / 5000 tasks. Credentials, chats and server jobs are excluded. Reenter your server connection in production; server-owned sessions can be imported again from Hermes. Purely local Demo/compatibility conversation caches are not transferred. Keep the old app until you have checked the migration. Uninstalling an app deletes its private data and Keystore credentials.

## Explore or connect

Demo starts enabled and makes no agent/network requests. Try themes, tasks, streaming, the harmless approval/question exercises and simulated job changes. Demo jobs reset on Demo restart; local tasks remain.

For your server, follow [connection setup](HERMES_CONNECTION.md). Use a trusted HTTPS endpoint with bearer auth, preferably behind your private VPN. Retain profile prefixes such as `/p/work/`. Save & test activates settings only after authenticated discovery succeeds. Empty token retains the current credential only for the same server URL. Forget connection removes its vault record; cached chats/tasks remain. A credential can authorize powerful tools on your server.

## Conversations and decisions

Chat supports a conversation browser, search over loaded records, explicit pagination, local pin/hide and supported server rename/delete. Server deletion is separately confirmed. Active/unknown turns retain their receipts and cannot be deleted through the browser. Settled sessions can refresh saved server history; interrupted imports keep the old cache.

Tool activity shows visible previews, timestamps and supplied durations. Tool errors do not by themselves decide the run outcome. An approval requires a current visible request, exact identity and an explicit allow-once or deny. Questions require a reviewed answer and Submit. There are no bulk/permanent or automatic decisions. Incomplete consent disables allowing; you can deny or inspect the server.

A closed stream or completed assistant reply may leave server work running. Use Check / recover run. Saved run IDs/cursors resume without submitting new work. Replay gaps fall back to status-only recovery. Uncertain decision delivery requires status review. An advertised interrupted-execution acknowledgement is separately confirmed and never reruns the lost work. If the server has expired its records, inspect Hermes directly before starting anything new.

## Local tasks and server jobs

Tasks stay on this device: create, edit, notes, ISO due date (`YYYY-MM-DD`), priority, complete/delete, title/notes search and all/open/completed/overdue filters. Due dates use the device's calendar. Tasks are separate from server jobs.

Automation lists Hermes jobs and supplied next/last-run state. A failed refresh keeps an explicitly stale profile-specific snapshot. Enable changes for this connection to use create/edit/pause/resume/run/delete. Each action has a review and confirmation; new jobs start paused with local delivery. Schedules are validated in the server's timezone. Blank replacement schedule leaves the current definition intact. Other server fields (skills/provider/delivery) are preserved; change advanced fields in Hermes itself. Resume/run can execute tools; delete also cancels an in-flight job run. A lost acknowledgement blocks further changes until you inspect the server and acknowledge the outcome marker. That acknowledgement does not send/repeat/undo anything.

## Voice, notices and widget

Voice is foreground only. On-device-only recognition is the default; it requires Android 12+ and an available local recognizer/model. If unavailable, the app explains this. You may explicitly disable local-only mode to use the system service, which may process audio online. Grant microphone permission only when tapping to speak. Edit the transcription and transfer it to Chat; sending remains a separate action.

Optional read-aloud uses an installed matching language voice. Network voices are excluded until opted in. Use the rate slider and Stop; code fences/Markdown syntax are stripped from spoken text. Listening/playback stop on exit/background. No background microphone or full-duplex conversation is provided.

Notifications are off initially. Enable them in Settings and grant Android permission. Optional tracked-run checks are read-only. Generic due-task and run/action-needed notices omit titles, prompts and replies; lock-screen public content is generic. Receipts suppress repeats; overdue tasks can remind again on a later date. Notices open Tasks or Chat; select the relevant chat and check it for an authoritative transcript.

WorkManager checks are best effort, about every 15 minutes. Battery restrictions, force-stop, permissions and network conditions may delay or suppress them. This is not push delivery or an exact alarm. Disable notices to cancel future work and current notices. Android's notification settings control the channel too.

Add the task widget from Settings where pinning is supported, or your launcher's widget picker. It shows local open/due counts, Refresh and Manage tasks. Compact widget text uses English/Chinese; accessibility labels include your selected language/pinyin. Titles/notes and credentials stay inside the app. Complete/edit tasks after opening the app.

## Appearance, privacy and help

Settings offers Lavender, Jade, Solar and Mint independently of connectivity, Chinese and accented pinyin switches, reduced motion and system font scaling. Wide displays use a navigation rail. No bundled fonts, telemetry, advertisements or analytics are included. App-private Room storage is not database encryption; backups/transfer are disabled. Explicit clipboard, speech services and exported task files leave that private storage boundary.

For bugs, use the issue templates with app/Android/Hermes versions and a synthetic reproduction. Never attach credentials or private transcripts. For security issues use [private reporting](https://github.com/DionCroft/hermes-agent-android/security/advisories/new). See [validation](VALIDATION.md) for exactly what was tested and what requires your hardware/server.
