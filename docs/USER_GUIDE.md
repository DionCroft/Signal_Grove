# Using Hermes Agent Mobile

The latest published stable release is 1.0.0. Current development builds are marked `1.1.0-dev`; development-only additions are identified below.

## Install and migrate

Get `hermes-agent-mobile-1.0.0.apk` from the [community release](https://github.com/DionCroft/hermes-agent-android/releases/tag/v1.0.0). Verify its SHA-256 and signer against the release record before installing. Android may ask you to allow installation from your browser/file manager. This is a GitHub-distributed APK, not a Play Store listing.

The production package is `org.hermesmobile.app`. Earlier development APKs use `org.hermesmobile.app.debug`; both may coexist. To retain the development app's data, install the separately supplied `1.0.0-debug` upgrade. To move local tasks to production, first update the debug app to 1.0.0, then Settings → Export tasks, install/open production, and Settings → Import tasks. Review the count and confirm. Matching task IDs are replaced; unlisted tasks remain. Reimporting the same file does not duplicate IDs.

Task files are plain JSON containing titles, notes, completion, priority and dates. Choose private storage and remove exported files when no longer needed. Maximum import/export is 2 MB / 5000 tasks. Credentials, chats and server jobs are excluded. Reenter your server connection in production; server-owned sessions can be imported again from Hermes. Purely local Demo/compatibility conversation caches are not transferred. Keep the old app until you have checked the migration. Uninstalling an app deletes its private data and Keystore credentials.

## Explore or connect

Demo starts enabled and makes no agent/network requests. Try themes, tasks, streaming, the harmless approval/question exercises and simulated job changes. Demo jobs reset on Demo restart; local tasks remain.

For your server, follow [connection setup](HERMES_CONNECTION.md). Use a trusted HTTPS endpoint with bearer auth, preferably behind your private VPN. Retain profile prefixes such as `/p/work/`. Save & test activates settings only after authenticated discovery succeeds. Empty token retains the current credential only for the same server URL. Forget connection removes its vault record; cached chats/tasks remain. A credential can authorize powerful tools on your server.

## Server details (development)

Open Settings → Server details to browse the connection's advertised capabilities, detailed readiness, provider/model catalog, installed skills and API-server toolsets. Tabs explain when an endpoint is not advertised. Refresh failures retain the previous result and timestamp. Switching connections clears the view and discards delayed results from the old server.

The readiness status comes from Hermes, including degraded or unavailable states; HTTP success alone is not a ready status. Model/provider rows include authentication state and supplied warnings. Expand a provider to browse its models and supplied metadata. A normal refresh uses the conservative catalog path. **Refresh all provider models** explicitly asks Hermes to probe saved endpoints and may take longer. The information button explains this distinction. Use the Models and providers icon in Chat to request settings for new turns.

Skills and toolsets describe what the server reports. Provider/MCP credentials and execution remain on Hermes. Installed skills may include disabled or editorial entries, and an available toolset may still need configuration. Demo entries are local simulation. Scroll the whole details page to read long or multilingual entries.

## Conversations and decisions

Chat supports a conversation browser, search over loaded records, explicit pagination, local pin/hide and supported server rename/delete. Server deletion is separately confirmed. Active/unknown turns retain their receipts and cannot be deleted through the browser. Settled sessions can refresh saved server history; interrupted imports keep the old cache.

Tool activity shows visible previews, timestamps and supplied durations. Tool errors do not by themselves decide the run outcome. An approval requires a current visible request, exact identity and an explicit allow-once or deny. Questions require a reviewed answer and Submit. There are no bulk/permanent or automatic decisions. Incomplete consent disables allowing; you can deny or inspect the server.

A closed stream or completed assistant reply may leave server work running. Use Check / recover run. Saved run IDs/cursors resume without submitting new work. Replay gaps fall back to status-only recovery. Uncertain decision delivery requires status review. An advertised interrupted-execution acknowledgement is separately confirmed and never reruns the lost work. If the server has expired its records, inspect Hermes directly before starting anything new.

### Durable recovery (development)

New turns save their exact request before sending. Check / recover uses that receipt; changing the draft or later settings does not alter an uncertain request. Missing or damaged saved request data disables resubmission. Known run IDs can still be checked. Older Runs receipts retain their original text-only body and header behavior.

New mobile conversations use a stable conversation key when Hermes advertises support. A transcript/session ID may rotate while this key remains the same. Imported and pre-upgrade conversations keep their prior memory behavior; starting a new conversation establishes a new logical channel. Memory/provider behavior is controlled by Hermes.

Recovery requests contain message/history data in app-private files. They contain no bearer credentials and are not exported with tasks. Deleting a cached conversation also deletes its saved requests. Clearing cached chats discards recovery receipts; server work may continue. App-private request files and Room data are not application-level encrypted.

### Models and providers (development)

Open the sliders icon in the Chat heading. Browse the server's provider/model inventory or explicitly enable manual IDs. Unauthenticated providers and account-unavailable models cannot be selected from the catalog. Refresh reloads the conservative inventory; full endpoint probing remains a separate action in Server details.

Set an optional reasoning mode/effort or service tier. A nonempty effort enables reasoning in the picker. Provider support varies; Hermes validates the request. Options can also be requested while leaving the model/provider at server defaults. Apply stores the choice for this connection's future turns. Server default removes the request override and options; it does not remove a server session lock. Session locks and configured route precedence still govern the effective runtime. A bare manual model on compatibility endpoints may require server direct-model support.

Uncertain requests retain their saved model/provider/options even if you change the next-turn settings. The tool icon opens activity. The status icon opens the full run outcome and ID; recovery and stop actions appear when supported. Labels remain available in English/Chinese/pinyin to accessibility services. While typing, the compact header and hidden bottom navigation leave space for the composer. Dismiss the keyboard to restore navigation. New replies follow their end; dragging back stops automatic following until you return to the bottom or start a new turn.

The branch icon opens Session controls for native Hermes conversations. Choose a requested model first, then review and confirm a session lock; a server lock takes precedence over future per-turn requests. There is no clear-lock control because the server has no verified route for it. Forking copies server history into a child and marks the original session branched. Review the child ID and title before confirming. Active runs can accept a reviewed guidance message; acceptance only confirms delivery. Refresh session metadata for server-supplied counts, runtime and usage, where present.

An uncertain session mutation blocks further mutations and new turns on that connection. Inspect Hermes using the displayed target and saved request before marking the outcome reviewed. Reviewing the marker does not repeat or undo the operation. A lost fork acknowledgement retains the chosen child ID so you can locate it on Hermes. Older compatibility chats keep their local history and do not expose native session actions.

Activity groups parallel tools by exact server call IDs, never just a shared tool name. Subagent events show child/delegation identity, public summaries, counts and reported cost when supplied. Child IDs are displayed for inspection; the app does not assume those sessions are API-owned or readable. Recent activity is bounded to 200 events.

An assistant response with accounting has a **Runtime and usage** icon. The status dialog also opens recent accounting records, which survive server history refresh. Requested and server-reported runtime are separate. Missing usage stays unknown, and counters may cover the session; the app does not total them into a provider bill. Private reasoning text is not stored or displayed. Deleting a conversation or clearing chats also removes its accounting records.

Structured inline images in native server history are cached privately and shown in Chat. Tap a preview to enlarge it. Raster images are resized, oriented and re-encoded without source metadata; transparency is flattened onto white. Remote image links show their host and open only when you choose **Open image in browser**. The app does not fetch those links automatically or attach its Hermes bearer token. Host-local files and unsupported content remain unavailable rather than using guessed download routes. Deleting a conversation or clearing chats removes its cached images. Use the photo icon in the composer to choose up to three images. Android supplies its photo picker or a document fallback; each source must be under 8 MB. The app creates private resized JPEG copies without original metadata. Review or enlarge the thumbnails, remove any you do not want, and press Send separately. Image-only messages are supported. The chosen Hermes model/provider must support images. The image draft info button explains limits.

You can cancel image preparation. Choosing another connection/conversation or restarting the app clears image drafts; returning to the same conversation retains them. A failed turn’s retry action restores the saved prompt and images for review, even if a display image is missing. Unknown admission recovery always uses the frozen original body; choosing another draft image does not change it. Compatibility history with many images may exceed the request limit and require a new conversation. You can also share images from another Android app to Hermes. Review the displayed connection and conversation, then choose **Use images in this draft**. The selection replaces existing draft images, and shared text is appended to your draft. Cancel leaves the draft unchanged. Send remains separate. Only image content-URI shares of up to three items are accepted; file paths, generic files, oversized selections and overlong captions are refused. An undecided review survives activity recreation; an accepted/cancelled share is not automatically offered again.

## Local tasks and server jobs

Tasks stay on this device: create, edit, notes, ISO due date (`YYYY-MM-DD`), priority, complete/delete, title/notes search and all/open/completed/overdue filters. Due dates use the device's calendar. Tasks are separate from server jobs.

Automation lists Hermes jobs and supplied next/last-run state. A failed refresh keeps an explicitly stale profile-specific snapshot. Enable changes for this connection to use create/edit/pause/resume/run/delete. Each action has a review and confirmation; new jobs start paused with local delivery. Schedules are validated in the server's timezone. Blank replacement schedule leaves the current definition intact. Other server fields (skills/provider/delivery) are preserved; change advanced fields in Hermes itself. Resume/run can execute tools; delete also cancels an in-flight job run. A lost acknowledgement blocks further changes until you inspect the server and acknowledge the outcome marker. That acknowledgement does not send/repeat/undo anything.

## Voice, notices and widget

Voice is foreground only. On-device-only recognition is the default; it requires Android 12+ and an available local recognizer/model. If unavailable, the app explains this. You may explicitly disable local-only mode to use the system service, which may process audio online. Grant microphone permission only when tapping to speak. Edit the transcription and transfer it to Chat; sending remains a separate action.

Optional read-aloud uses an installed matching language voice. Network voices are excluded until opted in. Use the rate slider and Stop; code fences/Markdown syntax are stripped from spoken text. Listening/playback stop on exit/background. No background microphone or full-duplex conversation is provided.

Notifications are off initially. Enable them in Settings and grant Android permission. Optional tracked-run checks are read-only. Generic due-task and run/action-needed notices omit titles, prompts and replies; lock-screen public content is generic. Receipts suppress repeats; overdue tasks can remind again on a later date. Notices open Tasks or Chat; select the relevant chat and check it for an authoritative transcript.

WorkManager checks are best effort, about every 15 minutes. Battery restrictions, force-stop, permissions and network conditions may delay or suppress them. This is not push delivery or an exact alarm. Disable notices to cancel future work and current notices. Android's notification settings control the channel too.

Add the task widget from Settings where pinning is supported, or your launcher's widget picker. It shows local open/due counts, Refresh and Manage tasks. Compact widget text uses English/Chinese; accessibility labels include your selected language/pinyin. Titles/notes and credentials stay inside the app. Complete/edit tasks after opening the app.

On Android 17, Settings includes **Allow local network access** for LAN or private VPN servers. The app opens Android’s permission dialog only when you tap that control. Denial leaves public internet connections available. After granting access, connect or refresh the run status again; no uncertain operation is automatically repeated. If Android stops showing the dialog, open **App permission settings**. Revoking permission blocks subsequent private-address connections, including background checks.

## Appearance, privacy and help

Settings offers Lavender, Jade, Solar and Mint independently of connectivity, Chinese and accented pinyin switches, reduced motion and system font scaling. Wide displays use a navigation rail. No bundled fonts, telemetry, advertisements or analytics are included. App-private Room storage and image/request files are not database/file encryption; backups/transfer are disabled. Explicit clipboard, speech services and exported task files leave that private storage boundary.

For bugs, use the issue templates with app/Android/Hermes versions and a synthetic reproduction. Never attach credentials or private transcripts. For security issues use [private reporting](https://github.com/DionCroft/hermes-agent-android/security/advisories/new). See [validation](VALIDATION.md) for exactly what was tested and what requires your hardware/server.

## Saved connections and token rotation

Settings → Saved connections lists encrypted named connection records. Connect first to save a server; rename it without changing its identity. Use this connection switches the exact saved URL/token pair after current operations finish. Switching clears the unsent draft and does not replay or reconcile old work. View cached history is read only and remains available after removing a credential.

A new URL or token creates a separate connection with separate conversations, requested runtime, approvals, recovery requests and automation consent. Previous credentials remain saved until explicitly removed. Re-enter the exact original pair to recover its original scope; the app does not guess that a rotated token can own an earlier admission. Up to 32 credentials can be saved. Removing one affects this device; server work can continue. All saved credentials and names use Android Keystore encryption; conversation/request files retain their existing app-private storage policy.

## Management login and MCP inventory

Settings → Hermes management links the separate dashboard HTTPS URL to a saved API connection. Sign in through the browser, return to the app and explicitly select a server profile. The screen lists that profile’s MCP configuration; enabled entries do not imply a successful connection test. Restored logins require Refresh to verify identity and available profiles. Renew sign-in rotates the session explicitly; after a lost or rejected renewal, start a new browser sign-in. Closing the panel or changing connections cancels pending work. Forgetting management sign-in affects this device’s management tokens; removing its API credential removes those linked tokens too. See [management setup and contracts](MANAGEMENT.md).

## MCP connection tests and configuration changes

After management sign-in and explicit profile selection, review a server action before confirming. Test connects to the configured host MCP process and lists tools. Enable/disable and Remove affect that profile’s configuration; existing sessions or processes may continue. Plugin-owned settings cannot be changed here.

An uncertain outcome stays in Management action history and blocks further changes to that same resource. Read current configuration to inspect it, then explicitly Review uncertainty when ready. Neither reading nor reviewing repeats the operation. Acknowledged actions whose readback failed remain labelled acknowledged. Chat-cache clearing keeps management receipts, and the saved-connection cache viewer shows them read only. See [management safety and contracts](MANAGEMENT.md).

## Adding MCP connections

Hermes management → Add MCP server configures a URL or command on the selected Hermes host/profile. Enter HTTP authentication or stdio arguments/environment fields, then review the exact target before confirming. New configurations are enabled by default upstream; test them separately. Existing names are protected from overwrites. Values used for provisioning are omitted from mobile receipts and are not retained for automatic retry. After an uncertain response, inspect configuration and review the uncertainty before another attempt. OAuth authorization and catalog bootstrap have their own setup flows; see [current management support](MANAGEMENT.md).

In **Settings → Management → MCP catalog**, refresh the selected profile's entries and review a new installation. Inspect the source, commands and bootstrap steps before confirming. Supplied variables stay masked. Direct installations default to disabled; background clone/build entries use the host's enabled default and remain pending work after acceptance. Entries whose background command cannot reliably receive environment values show a host-setup explanation. Read configuration and run a separate connection test afterward. Never repeat an uncertain installation until its receipt and host state have been reviewed.

For an eligible HTTP/SSE MCP server, choose **Authorize MCP OAuth**, review the host/profile/server, and confirm. Its receipt offers **Read authorization state** and a separately reviewed **Open provider authorization**. Complete authorization in the browser, then refresh the same flow. Host-reported approval can reopen for another attempt and is separate from connection readiness. **Cancel authorization flow** asks the host to cancel that flow; it does not revoke credentials already saved. Lost replies remain uncertain without automatic repeat. Provider URLs are not retained after management closes; refresh to request the current URL again.

In **Settings → Management → Advanced schedules**, refresh the selected dashboard profile's job inventory and open a specific job's details. Inspect runtime, scripts/skills, delivery, repeat/timezone/catch-up settings and reported status there. A schedule's state is separate from execution success. The view does not merge records with the inference API job cache. Unsupported IDs and replies too large for the mobile bound show an explanation.

In **Management → Advanced schedules → Create paused schedule**, supply a name, schedule and prompt/skill/script. The app checks that this management server declares safe paused creation. Runtime and automation options cover scripts, context sources, toolsets, timezone/catch-up and configured delivery. An empty explicit toolset allowlist is different from inheriting profile tools. Review the exact profile/actor and inputs before confirming, then inspect the saved paused schedule's interpretation before activating it on the host. Lost replies are kept as uncertain receipts and never automatically repeated.


### Inspect and manage installed skills

In **Settings → Hermes management**, sign in, verify the listener and choose a profile. Open **Installed skill controls** to read skill text or review changes to availability and curator edit locks. Unknown host flags leave the corresponding controls unavailable; Hermes' essential skill cannot be disabled. A curator lock affects its background editing policy, while your own edits remain allowed. Accepted configuration and existing work are separate: these controls do not stop a run. Read current configuration from a skill receipt to inspect skills again without replaying a change. See [management behavior](MANAGEMENT.md#installed-skill-controls).


### Inspect the gateway's schedules

In verified Hermes management, open **Gateway schedules** and choose **Read gateway schedules**. Review the selected listener/profile/actor and connection effect before confirming. The connection may initialize configured MCP processes on the host. The view includes paused schedules and requires the gateway to prove the selected profile; an older/unproven gateway remains unavailable. The read does not change schedules or start an agent turn. Scheduler readiness and prompt previews are labelled as host reports. Advanced schedules continues to provide the separate full dashboard configuration view and paused creation.


### Pause, resume or remove a gateway schedule

After reading the selected profile in **Gateway schedules**, choose an available action and review its exact target and effect before confirming. Pause affects future scheduling; resume can activate overdue work. Removal deletes saved output and the notepad as well as the schedule, and requests cooperative cancellation. Its receipt does not prove that execution stopped. Unknown states, legacy profile aliases and known ID/name collisions leave the corresponding controls unavailable with a reason.

A lost response blocks new changes until you inspect and explicitly review the uncertainty; it is never resent automatically. From a receipt, **Read current configuration** opens a separately reviewed read, without repeating the action. The gateway provides a prompt preview and may omit toolset distinctions; **Advanced schedules** has the fuller dashboard detail view. See [schedule control behavior and concurrency limits](MANAGEMENT.md#reviewed-gateway-schedule-controls).
