# History read feedback and error layout contract

## Verified server outcome

Immutable Hermes
[stable2bd API source](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/platforms/api_server.py#L2492)
returns HTTP 404 with `session_not_found` for an unavailable existing session.
Only that exact status/code pair maps to the translated explanation that the chat
is unavailable on this connection and saved messages were kept. Other codes,
authentication failures and server failures retain their actual error category.
Arbitrary server messages and private paths are not displayed.

Requests retain the authenticated profile prefix and exact existing session ID.
Unsupported IDs are refused locally with zero requests. No automatic retry,
session recreation, matching by title, pruning or global model change is added.

## Read lifetime and saved data

Beginning selection or explicit refresh clears the preceding error and starts a
monotonic read generation. Read error/loading completion and post-read run
reconciliation require that generation, exact connection client/partition and
selected key still match. Leaving/reselecting the same key does not admit an old
failure. Connection activation and New Chat invalidate the previous generation.
Cancellation propagates. Successful completion does not clear unrelated errors
raised while work was pending.

An unavailable remote transcript leaves its cached rows intact. Existing
repository/database receipt guards remain unchanged. This generation fences UI
feedback/loading/reconciliation; it does not introduce a database generation guard
or certify successful concurrent cache-write ordering. Existing read-only origin
rules and unknown-write barriers are preserved.

## Compact error display

The global error Surface shows at most two summary lines. Adjacent 48 dp icon
buttons open Error details or dismiss the error. The details dialog retains the
entire original message in a scrollable body, with a minimum 48 dp Close control.
Closing details alone keeps the banner. A changed error resets details; clearing
the error unmounts them. Details are local read-only UI and initiate no request.
English, Chinese and pinyin labels remain in structured Words.

## Validation and limits

Five new JVM methods cover exact status/code, independent language settings,
prefixed authenticated404/no retry/no server text, and invalid-ID/no request.
Eight new native methods cover retained cached native replies, valid selection,
same-key ABA failure, late success/current failure, explicit refresh, New Chat,
connection switching and long-error full details. The two legacy history/image
fixtures use the existing updater-isolation rule; their assertions are unchanged.

Required builds pass 836 unique JVM cases in 82 XML reports and both lint 0. The
fresh native cohort passes 20 API 26/20 API 37/8 compact API 37, 48 executions. Twelve
screenshots were reviewed. API 26 retains its original 720×1280 display at 320 dpi
and font scale 1.3. Requirements include a complete actually visible native TextView
reply, displayed chat viewport >=48 dp, summary height <=96 dp, complete translated
and long details, scroll to the long message's end and reachable 48 dp controls.
All preferences, display/AVD configuration, reverse bindings, original signing
key, 28 legacy cache files and public beta.8 assets retain their verified hashes.

Earlier failed fixture/visibility/compile attempts and the real tall-banner
failure remain in validation. A diagnostic intervention used only for cleanup
invalidates that earlier API 26 cohort regardless of nominal passing statuses.
The final cohort has no such intervention. No owner-phone/Pi/live inference/full
gateway acceptance or new release publication is implied; this fix is unreleased.
