# Model status and foreground discovery

Signal Grove keeps these observations separate:

- **Verified serving model** comes from the optional authenticated runtime observer.
- **Server default** is the selected model/provider in Hermes's model-options payload.
- **Requested for this chat** is the phone's saved request selection; an empty selection inherits the server default.
- **Last model reported by this chat** comes from that conversation's public response telemetry, with its timestamp and reported lock state. It describes a past turn.

The Home status summary and Chat header status button open full details. Chat
keeps its message viewport; the summary adds no permanent rows there. Refresh is a
read; Choose request model opens the existing picker. Selecting a model does not
load weights or change a server default. Existing session locks retain precedence.
English, Chinese and pinyin labels use the normal structured language layer.

Readiness, model options and serving observations refresh conservatively while
Home or the Models & connection dialog is resumed. Each resource has a 60-second attempt interval, including
failed reads, and duplicate reads share one in-flight job. There is no background
model polling or automatic full-provider probing. Manual refresh remains available.
The API client/profile identity must still match when a reply arrives. Old replies
cannot replace another connection's observations. Errors retain the previous
payload and label it cached; old data and clock rollback never become fresh merely
because a request returned HTTP200. Host-check and receipt times are shown
separately. A host observation at least two minutes old or over30 seconds ahead
of the device clock is labelled cached, even after a fresh HTTP200 receipt.

## Optional extension contract, version 1

This is a Signal Grove companion extension, not a claim that stock Hermes exposes
this route. The app requests it only when `features.model_runtime_status_v1` is
true and `endpoints.model_runtime_status` advertises exactly GET
`/api/model/runtime-status`. Existing configured profile/proxy prefixes and bearer
authentication are retained. No query parameters or request body are accepted.
The gateway's existing profile middleware and bearer check protect its profile mirrors.

```json
{"object":"signalgrove.model_runtime","schema_version":1,"observed_at":123000,
 "status":"ready","models":[{"provider":"spark-example","model":"example-local-model",
 "state":"ready","verified":true}]}
```

`observed_at` is positive epoch milliseconds. There are at most eight unique
provider rows. Provider/model identifiers are bounded to128/512 characters and
reject control/format characters. Each state is `ready`, `offline`, `unready`,
`unsafe` or `unknown`; `verified` is true exactly for `ready`. The client rejects
malformed, contradictory or oversized observations instead of inventing a model.

The aggregate is `unverified` if any route is unsafe/unknown, otherwise `ambiguous`
for multiple ready routes, `ready` for one ready route, and `unavailable` for none.
An empty list is allowed only with `not_configured` or `unverified`. A verified row
still retains its individual identity when another route is unknown; no unique
execution route is claimed in that case. No credentials, inference URLs, hostnames,
paths, commands or raw provider errors are returned. There is no shared model cache.

The [optional adapter](../server_extensions/runtime_models/README.md) resolves
profile credentials before threaded transport reads and uses fixed allowlisted
GETs, normal TLS validation, no proxy inheritance, no redirects and no retries.
Each transport read uses a two-second timeout and a64KiB response limit. Endpoint
state is an observation, not a promise about a later request or memory residency
on every kind of inference backend.

Hermes's standard [model-options/API contract](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server)
explains that `/v1/models` advertises the agent alias and that model options are a
richer provider catalog. Neither is substituted for this runtime observation.
