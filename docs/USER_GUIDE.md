# Using Signal Grove

The latest published stable release is 1.0.0. The current Signal Grove preview is [`1.1.0-beta.4`](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.4), code 105; preview additions are identified below. See [branding and upgrade compatibility](BRANDING.md) for package identities and the repository-feed migration. Later development changes are tracked in the [audit follow-up](AUDIT_FOLLOW_UP.md).

## Install and migrate

The `1.1.0-beta.1` preview introduced the pixel interface and approved in-app updates. Install the [current beta.4 preview](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.4) once to get the current updater; 1.0.0 cannot check for updates itself. After the repository rename, previews through beta.3 need one matching signed beta.4 APK upgrade from its [release page](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.4). Install over the existing package with the original signer; do not uninstall. Once on the new feed, open **Settings → App updates**, review a later release and choose **Download & update**. Android asks for this app’s install-source permission when needed and confirms the installation. See [update behavior and channels](APP_UPDATES.md).

Get `hermes-agent-mobile-1.0.0.apk` from the [community release](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.0.0). Verify its SHA-256 and signer against the release record before installing. Android may ask you to allow installation from your browser/file manager. This is a GitHub-distributed APK, not a Play Store listing.

The production package is `org.hermesmobile.app`. Earlier development APKs use `org.hermesmobile.app.debug`; both may coexist. To retain the development app's data, install the separately supplied `1.0.0-debug` upgrade. To move local tasks to production, first update the debug app to 1.0.0, then Settings → Export tasks, install/open production, and Settings → Import tasks. Review the count and confirm. Matching task IDs are replaced; unlisted tasks remain. Reimporting the same file does not duplicate IDs.

Task files are plain JSON containing titles, notes, completion, priority and dates. Choose private storage and remove exported files when no longer needed. Maximum import/export is 2 MB / 5000 tasks. Credentials, chats and server jobs are excluded. Reenter your server connection in production; server-owned sessions can be imported again from Hermes. Purely local Demo/compatibility conversation caches are not transferred. Keep the old app until you have checked the migration. Uninstalling an app deletes its private data and Keystore credentials.

## Explore or connect

Demo starts enabled and makes no Hermes-agent requests. The independent app updater checks public GitHub metadata unless automatic update checks are disabled in Settings. Try themes, tasks, streaming, the harmless approval/question exercises and simulated job changes. Demo jobs reset on Demo restart; local tasks remain.

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

Settings offers Starhaven Grove, Lavender, Jade, Solar and Mint independently of connectivity, Chinese and accented pinyin switches, reduced motion and system font scaling. New installations start in Starhaven Grove; your saved palette remains selected after upgrading. Pixel scenery uses physical-pixel edges and a gentle eight-frame-per-second clock, read only while drawing. Scenery and decorative loading motion pause while the app is backgrounded; both app reduced motion and Android’s disabled-animations setting suppress decorative motion. Stepped progress bars retain native accessibility values, including exact download progress. Wide displays use a navigation rail. No bundled fonts, telemetry, advertisements or analytics are included. App-private Room storage and image/request files are not database/file encryption; backups/transfer are disabled. Explicit clipboard, speech services and exported task files leave that private storage boundary.

For bugs, use the issue templates with app/Android/Hermes versions and a synthetic reproduction. Never attach credentials or private transcripts. For security issues use [private reporting](https://github.com/DionCroft/Signal_Grove/security/advisories/new). See [validation](VALIDATION.md) for exactly what was tested and what requires your hardware/server.

## Saved connections and token rotation

Settings → Saved connections lists encrypted named connection records. Connect first to save a server; rename it without changing its identity. Use this connection switches the exact saved URL/token pair after current operations finish. Switching clears the unsent draft and does not replay or reconcile old work. View cached history is read only and remains available after removing a credential.

A new URL or token creates a separate connection with separate conversations, requested runtime, approvals, recovery requests and automation consent. Previous credentials remain saved until explicitly removed. Re-enter the exact original pair to recover its original scope; the app does not guess that a rotated token can own an earlier admission. Up to 32 credentials can be saved. Removing one affects this device; server work can continue. All saved credentials and names use Android Keystore encryption; conversation/request files retain their existing app-private storage policy.

## Management login and MCP inventory

Settings → Hermes management links the separate dashboard HTTPS URL to a saved API connection. Sign in through the browser, return to the app and explicitly select a server profile. The screen lists that profile’s MCP configuration; enabled entries do not imply a successful connection test. Restored logins require Refresh to verify identity and available profiles. Renew sign-in rotates the session explicitly; after a lost or rejected renewal, start a new browser sign-in. Closing the panel or changing connections cancels pending work. Forgetting management sign-in affects this device’s management tokens; removing its API credential removes those linked tokens too. See [management setup and contracts](MANAGEMENT.md).

## MCP connection tests and configuration changes

After management sign-in and explicit profile selection, review a server action before confirming. Test connects to the configured host MCP process and lists tools. Enable/disable and Remove affect that profile’s configuration; existing sessions or processes may continue. Plugin-owned settings cannot be changed here.

An uncertain outcome stays in Management action history and blocks further changes to that same resource. Relay code/unlink requests and endpoint probes share that block across profiles on the same dashboard. Their local uncertainty review needs a fresh approval and clears only that request’s block; sending requests cannot be cleared. Read current configuration to inspect it, then explicitly Review uncertainty when ready. Neither reading nor reviewing repeats the operation. Acknowledged actions whose readback failed remain labelled acknowledged. Chat-cache clearing keeps management receipts, and the saved-connection cache viewer shows them read only. See [management safety and contracts](MANAGEMENT.md).

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


### Review and install a host agent plugin

In **Hermes management → Host plugins**, use the catalog/source inspector and inspect your chosen source. Review its components, warnings and inspected commit. Choose whether to enable it, then open the separate installation review. Enabling also authorizes dependency preparation and host activation. Confirm only after reviewing the host, profile and source. The app refuses known duplicates and changed reviews, uses the inspected commit guard and never forces replacement.

Read the result's activated, live, deferred and restart reports separately. These are host reports, not a code-safety or runtime-health guarantee. A changed source requires a fresh inspection and review. A lost/cancelled reply can leave host work running; inspect the receipt and host state before explicitly reviewing uncertainty. Installation is never automatically repeated. Receipt observation opens a new reviewed plugin read. See [installation behavior and limits](MANAGEMENT.md#reviewed-agent-plugin-installation).


### Review a host plugin's enable state

In **Hermes management → Host plugins**, read the profile's plugins, open details and choose **Review plugin enable** or **Review plugin disable**. Review the canonical key, shared dependency environment and saved platform toolset effects before confirming. Disable leaves already loaded handlers running until restart; it is not a stop, credential revocation or uninstall. The app separately reads configuration after the host acknowledgement. Live/activation reports remain distinct. Unknown states and redirected/ambiguous keys stay unavailable. A lost reply is never automatically repeated; inspect the durable receipt and host state before reviewing uncertainty. See [control behavior](MANAGEMENT.md#reviewed-agent-plugin-enable-and-disable).


### Remove a host agent-plugin directory

In **Hermes management → Host plugins**, read the selected profile, open details and choose **Review plugin removal**. Review the installed directory identifier, which can differ from the plugin's name/key. Confirming removes that directory and local edits and asks Hermes to clean related configuration and local grants; it can reset the selected memory provider. Bundled and entry-point packages remain unavailable here. Missing profile/directory proof requires refreshing management/inventory or inspecting the host.

The app separately checks reported configuration after acknowledgement. A bundled fallback may appear afterward. Running handlers, separate desktop copies, dependency packages, external credentials and locked files can remain; inventory absence does not prove file erasure or stopped work. Lost replies are uncertain and never automatically repeated. Read configuration from the receipt through a new review. See [removal behavior and concurrency limits](MANAGEMENT.md#reviewed-host-plugin-directory-removal).


### Review a host plugin update

In **Hermes management → Host plugins**, read the selected profile, open plugin details and choose **Review plugin update**. Confirm after reviewing the directory, catalog pin and host effects. If Hermes asks for capability consent or reports a caution scan, open the separate decision review, reveal any hidden declared details and explicitly approve one next attempt. The server can resolve a changed source between review and execution; the review explains this limit. Dangerous findings and unsupported targets stay unavailable.

Read acknowledgement, observed metadata and activation reports separately. Git update responses do not identify a commit; catalog metadata checks do not certify file contents or code safety. A refusal can follow host cloning or backup work. Cancelling the phone wait does not stop the host, and lost replies are never automatically repeated. Use the receipt's separately reviewed configuration read before assessing uncertainty. See [update policy and limits](MANAGEMENT.md#reviewed-host-plugin-updates).


### Read and edit a plugin setting

In **Hermes management → Host plugins**, read the selected profile, open details and choose **Read plugin settings**. Confirm the separate private-data read. Details stay hidden until shown. Declared secret fields show presence only; their **Host keys and environment** action opens the separate credential workflow for that variable.

Choose **Edit setting**, enter the declared type, then **Review setting change**. Show the new value and approve one save before confirming. Cancelling a review discards that approval. The app checks fresh metadata and reported values, but the host has no revision guard against concurrent edits. Saving may notify plugins and reconnect affected MCP processes. Acknowledgement, reported configuration and runtime adoption remain separate. Dotted settings can be saved, but the current host listing cannot reliably verify them; inspect the host when needed. Closing clears the private settings view. See [contract and privacy limits](MANAGEMENT.md#reviewed-plugin-settings).


### Host credentials and environment variables

In **Hermes management → Host keys and environment**, choose **Read key metadata** and confirm the target profile. Filter by variable/provider, then choose **Save or replace value**, **Add a key**, or **Remove credential references**. A declared plugin secret can open the same inspector with its variable selected. Existing values and masked previews are never shown or prefilled.

Enter a complete printable ASCII replacement; the current host writer strips other characters, so the app refuses them. Review the shared consumers and effects, show the new value and approve one save. Removal has a separate review and can clean references even when environment presence is absent. Saves/removals can affect configuration mirrors and environment-backed credential pools; other credential types can remain, and removal is not provider-side revocation. Results are host acknowledgements with separate presence observation, never exact-value or runtime verification. A lost reply is never repeated automatically. Closing clears the metadata/draft; receipt observation requires a new read review. See [contracts and limits](MANAGEMENT.md#host-keys-and-environment).


### Store an empty environment value

Open a variable's editor or add a custom key, then select **Explicitly store an empty string and keep this environment key**. This clears the replacement draft. Open the review, show the empty-string intent and approve one save. Cancelling and reopening requires selecting and approving it again.

An empty save keeps the key. Hermes can retain old credential mirrors/pool entries and lift environment-source suppression, so this does not mean credentials were removed or revoked. The receipt stays acknowledged after reported unset-key observation; presence cannot verify the exact stored value. Use the separately reviewed removal action when you intend reference cleanup. See [empty-value effects](MANAGEMENT.md#explicit-empty-environment-values).

### Stage discovered endpoint settings

In **Hermes management → Host models and providers → Custom endpoints**, read the selected profile and open an endpoint editor. A probe has its own destination/key review and may make a small real inference request. Reveal its private report to stage a returned address, API mode, model catalog or individual model into the editor. Each action changes only your draft. A changed returned address clears a newly entered key; review the new destination and enter a key again if needed.

Choose **Review** separately to save. Imported aliases can redirect to a canonical model and overwrite the profile-wide reasoning preference used by other routes. Reveal the original/effective model and prior/new preference before approving. An unsupported import explains why save is unavailable; clearing the staged metadata restores ordinary editing without deleting host metadata. A save acknowledgement and the later configuration observation are shown separately. Neither confirms provider health or adoption by a running conversation.


### Configure a Hermes channel

Open **Hermes management → Channels**. Opening is local; **Read channel settings** separately reviews the server, actor and selected profile before loading its catalog. Channel availability and state come from the host. Reveal private details to inspect declared fields, existing non-secret allowlists, default destinations and incoming callback addresses. Stored credentials are not shown. A reviewed documentation URL can be opened in your browser.

Choose **Edit channel settings** to stage an enablement choice and **Keep**, **Replace** or **Clear** for each declared field. Keep leaves that value alone. Replace requires a new nonblank single-line value; credentials start blank. Clear explicitly removes the environment entry, including when another secret source may still supply a value. These changes can alter who can reach the agent or interrupt clients. **Review** shows the exact values and changes; reveal them and select a new approval checkbox before confirming. Cancel sends nothing and a new editor starts without the canceled secret.

**Check reported connection and access** has a separate approval. It asks for the host's setup, gateway and authorization report; it does not make a new vendor connection or send a message. Open the private report to see its supplied message. An accepted save and a later settings observation remain separate, including managed differences and failed readback. An uncertain request is never automatically repeated; reviewing current settings is a separate read. Shared-bot account linking, Telegram/WhatsApp onboarding, pairing and source-specific webhook rules follow as separate operations.


### Team Gateway account review

Open **Hermes management → Team Gateway accounts**. Opening is local. Choose **Read linked accounts**, review the server, selected profile, signed-in actor and private-data notice, then confirm. Hermes must identify the selected profile as the running instance’s relay-fronted launch profile. If it cannot, the screen explains why account reads are unavailable; it does not switch profiles for you.

The report shows the management authority and account count first. **Show private details** reveals platform account IDs, names, handles and reported bot links. These are a host snapshot, not proof of ownership, current access or delivery. Details stay in memory and are discarded when you close, cancel, change profiles or connections, or lose authentication.

For a Portal-managed instance, choose **Review Portal handoff**, inspect the full HTTPS destination and select a fresh approval checkbox before confirming. The app rechecks the instance and destination, then opens your browser without its Hermes bearer. The browser uses its own Portal sign-in; linking and access changes happen there. Cancel opens nothing. Unknown authority and unsafe or missing destinations explain why handoff is unavailable. Connector-managed account removal and link-code creation each have their own approval flow, described below.


### Reviewed Team Gateway unlink

In **Hermes management → Team Gateway accounts**, read the account report, reveal the private details and choose **Unlink this account** for a connector-managed account. Portal-managed accounts use the separate Portal handoff; unknown authority remains read-only. A pending or uncertain management operation blocks another unlink.

The new review identifies the server, profile, signed-in actor and platform. Reveal the exact account ID/name/handle and reported bot link, then select a fresh approval checkbox before confirming. Cancel sends nothing; reopening requires new reveal and approval. The app rechecks the reported target and instance context, saves the target encrypted on this device for receipt review, and sends one unlink request. If the local target cannot be saved, no unlink is sent. Unlinking can interrupt this account’s route to the instance, but does not delete its platform account or revoke provider credentials; in-flight messages may continue.

The host acknowledgement and the later account report are separate. A still-listed account or a changed context explains the difference, and failed readback keeps the acknowledgement. A lost reply remains uncertain and is never automatically repeated. From its receipt, **Read current settings** opens the local original-target inspector; this does not contact Hermes. The saved target is historical. Read linked accounts separately for a fresh report.

**Clear saved relay targets** has its own owner/profile review and approval. It clears local encrypted targets for this sign-in, including historical and uncertain targets; receipts, pending guards and remote accounts remain. Signing out or forgetting the inference credential also removes its local targets. Up to 32 targets per exact signed-in profile and 128 in total are retained without automatic eviction. If full, clear unneeded targets before another unlink. A cleared original target cannot be reconstructed from the value-free receipt, and clearing it does not settle an uncertain request.


### Reviewed Team Gateway link codes

Open **Hermes management → Team Gateway accounts**, read the account report and reveal the platform details. A connector-managed platform offers **Create link code**. Review the exact server, profile, actor, platform and privacy effects, then select a new approval checkbox before confirming. Portal-managed instances use the separate Portal handoff; unknown authority remains read-only. A pending uncertain operation blocks new code creation.

The host’s acknowledgement means a code was issued, not that an account linked. The first state is **redemption status has not been checked**. Reveal the private command when ready. **Check original code status** explicitly checks the saved original; it never creates a replacement. Pending means the host reported it has not been redeemed. **Inactive** means used **or** expired, and does not prove a link. Refused and inactive observations hide the original code. Inspect linked accounts separately to see a later account report.

**Copy reviewed command** first rechecks this original code and instance. It copies only the private `/link …` command and marks the clipboard sensitive where Android supports it; other apps may still access clipboard data. **Review bot destination** has a separate full-URL review and fresh approval, followed by another original-code/status check before opening your browser or messaging app without a Hermes bearer. WhatsApp may prefill the command. You decide whether to send it. Nothing is automatically opened or messaged. If no supported destination was supplied, the app explains that manual copying is available.

The original copy is encrypted locally for at most 15 minutes according to the device clock, shortened by an earlier valid reported expiry. Closing the screen keeps the saved copy, so its receipt can reopen a local inspector without network traffic. A resumed copy stays hidden until an explicit fresh status/scope check. Expired, cleared or unsaved copies cannot be replaced automatically. Clearing **saved link codes** has a separate owner/profile review and grant; it removes local copies only. Remote codes, clipboard copies, receipts and uncertainty remain. Signing out or forgetting the inference credential also removes saved local copies. A new code does not cancel older codes.

Only exact documented pre-issuance refusals are recorded as no code issued. A lost, malformed or other failed creation response remains uncertain; the host may have issued a code. Do not assume a new code is safe to create from that state. A failed optional cache write or readback keeps the known acknowledgement and explains that the original copy is unavailable or needs verification. Local storage is bounded to 32 live copies per exact sign-in/profile and 128 total, with expiry cleanup and no silent eviction of live codes.


### Host projects

These read-only controls are included in the published 1.1.0-beta.4 preview. Open **Hermes management → Host projects**, then **Read stored projects**. Review the dashboard address, actor, profile and read effects before confirming. A verified supported profile with a reported host location is required. Opening or cancelling the review does not contact the project gateway.

The list includes archived projects and reports an unmatched active-project pointer explicitly. Select **Refresh project details** for a fresh canonical-ID read. Folder paths and other metadata are selectable text; the phone does not open those host paths. Closing, cancelling or changing management scope clears the private project view. A remapped profile or unsupported result requires a fresh read.

The published beta.4 screen is read-only. Reviewed project changes are available in development beta.5 below; Reviewed Kanban board changes are described below; task writes and selecting a conversation's host working directory remain subsequent work. Reading this inventory does not change an active project or existing conversation. [Scope and limits](MANAGEMENT.md#stored-host-projects).


### Project receipts (development source after beta.4)

Project receipts in **Hermes management** show readable action names and distinguish a host acknowledgement, its reported effect and a separate matching configuration observation. Original names, descriptions and host paths are not saved in the receipt. **Read current configuration** opens the current profile's project screen; approve **Read stored projects** separately to contact the host. It never repeats the original change.

An uncertain project change also guards other connections/profiles using the same reported project store at the same dashboard address. Their original private details stay hidden. **Review uncertainty** requires a fresh unchecked grant. Confirmation checks the selected sign-in and reported store location before marking only that local unknown receipt reviewed. It does not cancel, repeat or verify the host change. Inspect current projects before approving another change. Sending records and receipts from a different reported store cannot be released this way; changing the profile, actor, address or store invalidates the displayed confirmation. [Scope and limits](contracts/2026-11-05-project-receipt-review.md).

This receipt UI is subsequent to the published beta.4 APK and is used by the reviewed beta.5 development controls below.


### Reviewed project changes (development beta.5)

Read the complete inventory first. **Create project** stages a name and optional display values, board reference, host paths (one per line, without blank lines) and primary path. A blank slug lets the host choose one; it may suffix duplicates. Creation does not activate the project. Host paths are passed literally and normalized on the host; the phone cannot verify their mapping or contents. A board reference does not create or verify a board.

Open **Refresh project details** before changing an existing project. If fresh details differ from the inventory, close them and read the full inventory again. Known ID/other-slug collisions disable writes until the identities are unambiguous. **Edit project** changes only checked fields: an empty description is a saved empty string; empty icon/color/board clears that value. Unchecked fields stay unchanged. IDs and existing slugs are not edited.

**Add host folder** can also reattach an existing path to update its label. An unchecked label option keeps an existing label; checking it and leaving the input empty saves an empty label. The primary option is explicit, but if no folder is marked primary the host can choose this one even when it is off. **Set primary folder** and **Detach host folder** act on the exact selected attached path. Detaching primary chooses the oldest remaining folder or clears it. Files are not created, moved or deleted by these metadata controls.

Archive/restore changes the archive flag. Delete removes project/folder records. Neither implicitly clears a dangling active pointer; **Clear host active project** is a separate review, including for a missing target. Activation changes the host's stored pointer and does not send conversation or working-directory arguments or a runtime stop/restart request.

Every write opens a separate exact-value review with server, profile, actor and canonical target. Check the effects and concurrent-change limit, then grant approval. The host has no atomic check-and-write token; another client can change metadata or ID resolution between checking and writing. Drafts and grants are discarded on cancel/close/scope changes. One approval sends at most one mutation. A lost reply is never retried automatically; fresh reads remain available while writes are guarded. Return to management receipts for a separately approved, home-verified local uncertainty review.

Reports distinguish acknowledgement, reported requested effect and a separate matching configuration observation. A successful primary RPC can be a no-op. A nullable create reply can supply no project; neither is presented as confirmed creation/change. Acknowledgement remains inspectable when subsequent readback fails. Normalized path mapping is explicitly unverified. These development controls are not part of the immutable published beta.4 APK. See [the source boundary](contracts/2026-11-05-project-management-ui.md).


### Host Kanban boards (development beta.5)

Open **Management → Host Kanban boards** after verifying management access and choosing a supported profile with a reported location. Opening the dialog makes no Kanban request. **Read board inventory**, **Read this board** and **Refresh task details** each open a frozen read review. Cancelling sends no read. The review explains that Hermes can load the plugin and initialize, migrate or repair its board storage during GETs; names, descriptions, comments, summaries and paths may be private.

Kanban is shared host storage. The selected management profile controls access routing and related project metadata; it does not create a private per-profile Kanban queue. These are independent of the phone’s **Tasks** database. The host’s current-board pointer is displayed separately. Choosing a board here uses its explicit slug without moving that pointer or dispatching work.

Archived board metadata is listed, with its open control disabled and explained. Live board reads include archived task cards. Search matches ID, title, assignee or tenant locally; column choices also stay local and send no request. A future status remains its actual reported text even if the host puts it in a fallback column. Counts and task details are separate observations and can differ as workers act.

Task details show description/result/full summary, first task start and current attempt start separately, recorded attempts and reported outcomes/heartbeats, comments, event kinds/times, dependencies and child handoffs, and attachment metadata. A missing linked task keeps its ID; requesting it can fail explicitly. Dependency navigation retains the exact board. Task/run status or a heartbeat is not a live worker-health check. Files remain on the host; event payloads, arbitrary run metadata, claim locks and idempotency keys are omitted from the projection. Text and paths are selectable plain text.

Closing, cancelling or changing access clears private views and invalidates reviews. Every board/task read checks the selected profile’s reported location and the board’s reported database path around the read. Those strings do not prove filesystem/symlink identity or eliminate a concurrent host change. There is no background polling or automatic request replay. Larger or unsupported replies fail as a whole. Reviewed board controls are described next. Development task editing and creation are described below; dependency editing and file transfer remain subsequent audit work. The published beta.4 APK does not include this development interface.


### Reviewed board changes (development beta.5)

After **Read board inventory**, choose **Create board** or a board's **Board controls**. Edit changes only fields you check; empty and omitted values have distinct effects explained in the form. To bind a project, first use **Read projects for board binding**, approve that read, then select a canonical live project. The host may seed its primary directory; the form explains explicit clearing versus omission. The final review shows exact values and starts with approval unchecked. Delete also requires typing the exact board slug.

Create sends `switch:false`. Known live and archived slugs are refused, but another client can race creation: the host can reuse that slug and overwrite metadata. A matching readback does not prove the board is new. **Set shared current board** is a separate approved write; host context/environment may override its effective selection. Reading a board continues to use its explicit slug. These actions do not adopt a chat directory or dispatch tasks.

**Archive board directory** moves contained board data and leaves a tombstone. **Delete board directory** removes its current directory/tombstone. Deleting an archived tombstone leaves the earlier archived copy. Neither action stops workers; host path overrides may leave data elsewhere, and removal may clear current selection before failing. Default-board removal is protected. Archived metadata can be edited, while reopening/switching/rearchiving is disabled with an explanation.

Receipts distinguish an acknowledged host reply from matching observed configuration. They do not prove physical deletion, a new board, worker stop or path identity. Returned archive paths are transient, not durable receipt contents. A lost/failed attempted write stays unknown across profiles and prevents conflicting work until you explicitly review that local uncertainty in Management. Re-reading never retries or clears it. Closing, cancelling and switching scope drop private drafts and approvals. [Detailed effects and validation boundary](contracts/2026-11-05-kanban-board-controls.md).


### Review a host task edit (beta.5 development)

In **Hermes management → Host Kanban**, explicitly read the board inventory, the selected board and a task's details. The task offers **Edit task text**, **Change task priority**, **Assign or unassign task**, **Add task comment**, and **Delete host task**. Controls explain when current reads or resolution of a pending operation are needed. These controls operate on the host's shared board, separately from local phone tasks.

Text editing sends only checked fields; an explicitly empty description clears it. Priority accepts a signed 32-bit integer. Assignment displays its trimmed/lowercase value at review; an empty value explicitly unassigns. Comments require text and a declared author, which is metadata rather than a verified actor identity. Deletion requires both the exact task ID and a fresh grant. Its review explains that deletion can release dependent work and does not establish worker shutdown or file erasure.

**Review** shows the exact host, access profile, actor, board, task and selected request values. Grant permission and confirm to attempt that one request. Cancel discards the draft without sending. Changed access, closed views and shared pending operations invalidate the review. A fresh host preflight can still refuse a changed task. Host clients and workers can race after that check.

The result distinguishes a retained host acknowledgement from matching stored configuration. A comment acknowledgement supplies no created comment ID and remains an acknowledgement. Private request text and host warnings are transient; durable summaries omit their values. A failed or lost reply may follow a committed write and remains unknown. Read and inspect current state and separately review the pending receipt; the app never repeats the request automatically. After an attempt, explicitly reload inventory/board/detail before another edit. Dependency editing and worker/dispatcher/status controls remain subsequent work.


### Create a host task (beta.5 development)

Read the host board inventory and the selected board, then choose **Create host task**. Supply a title and optional description, priority, triage choice and dependencies selected from that board. Dependencies keep selection order; when tenant is omitted, the first selected parent with a tenant can supply it. **Assignment, workspace and execution options** expands the remaining fields: assignee, tenant, workspace kind/path, project, runtime, skills, goal mode/turns, model, provider and reasoning. Unchecked optional fields are omitted. A supplied empty description and an explicitly empty skill list remain distinct from omission. Skills use one name per line, without commas; names are trimmed and duplicates removed. Limits must be positive signed integers; provider needs a model. Literal reasoning **none** disables thinking; the default choice omits that override.

For a canonical project binding, close the form and separately approve **Read project choices** in the board browser first. Omitted project choice can inherit the board project, **Explicitly use no project** opts out, and scratch suppresses board inheritance. Explicitly selecting a project with a primary repository can promote scratch to a worktree. The host can derive paths, branches and initial status; readback does not establish Android/host path equivalence or runtime adoption.

Review the exact host/profile/actor, board mapping, selected values, dependencies and any project context. Ready work may be assigned or started by a running dispatcher immediately. Parent tasks can supply tenant/delivery subscriptions. Triage defers dispatch without proving future inactivity. A fresh grant and confirmation attempt creation once. The returned task ID or matching configuration does not establish exclusive creation by that request. If the returned triage state differs from your choice, its valid acknowledgement is retained without matching-effect confirmation. This does not establish the earlier state or exclusive creation. If a reply is lost, inspect the host and separately review the uncertain receipt; creation is never automatically repeated.

These are development-source controls and remain outside the published beta.4 APK. Current acceptance and its limits are tracked in VALIDATION.md. Dependency editing, status/worker/dispatcher controls and the wider audit remain separate work.
