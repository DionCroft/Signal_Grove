# Serving model and request presets - 8 November 2026

Automatic is a local preference, never a wire model ID. New/unconfigured
connections use it by default. An explicitly saved model or Server default
selection remains explicit. Before each new turn, an advertised v1 runtime
observation must be under 30 seconds old (up to 30 seconds future clock skew),
unambiguous and ready, with an exact available provider/model in the authenticated
catalog. The resolved pair is frozen into the turn request for every transport.
An unresolved/ambiguous/offline result creates no chat and submits no turn.

Standard Hermes without this optional discovery extension receives no provider
or model override in Automatic. The UI describes this as server-managed routing,
not verified loaded-model selection. Choosing Server default explicitly always
sends no provider/model override. Neither choice loads weights or changes the
saved/boot default. Existing pending requests never re-resolve; retry/recovery
retains the original body and key. A known reported conflicting lock holds
Automatic and requires inspection, without claiming complete server-lock
discovery or universal lock precedence.

The primary API accepts structured reasoning enabled/effort and service_tier.
Effort presets are minimal, low, medium, high, xhigh, max, ultra when the model
advertises reasoning; Off is separate. Hermes may clamp/adapt the effort.
The catalog deliberately does not forward supported_efforts. Service choices
follow capability flags: fast => priority, fast+ultrafast => ultrafast,
daybreak => daybreak. An absent offer leaves Server default only. No arbitrary
effort/tier input remains in the UI. Manual provider/model IDs remain available.

Reviewed primary sources at immutable stable 2bd:
- [API request decoder](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/platforms/api_server_request_options.py)
- [API facade and precedence](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/gateway/platforms/api_server.py)
- [Catalog capability projection](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/inventory.py)

Local validation: 721 JVM, 197 policy, both lint variants 0 errors and 21 affected
native methods on each preserved emulator. Exact-head hosted/release gates remain. Runtime status is an explicitly advertised Signal Grove
extension; catalog entries alone never prove weights are loaded. The owner Pi
was inspected read-only: RVN ready, stock offline, reasoning true/fast false.
Those private observations are not credentials, public fixtures or global
configuration changes. Full release/worker/workspace acceptance remains separate.
