# Connect Signal Grove to Hermes

The offline guide is in **Settings → Server → Set up Hermes**. It contains nine
steps in English, Simplified Chinese and pinyin. Reading it sends no requests,
executes no commands and changes no credentials or connection preferences.

## Prepare the host

Install Hermes on the host that owns the agent, following the
[official installation guide](https://hermes-agent.nousresearch.com/docs/getting-started/installation).
Keep the existing Hermes home, named profile, identity, sessions, model routing,
skills and tools. Configure the provider there and verify a regular conversation.
This connection uses Hermes on its existing host. Beta.9 also includes experimental
optional phone AI, compatible model import/download and a setup helper. It needs
its own reviewed model and keeps separate local history. It does not move the
host's identity, tools, remote sessions or project authority onto the phone;
remote project work still requires the authenticated host and owning worker.
Use the installed version's commands and service manager; avoid starting a second
gateway alongside a managed installation. Changes to an existing production agent
need its normal backup, active-work and restart procedures.

## Enable the authenticated API

In the intended profile's `.env`, use:

```dotenv
API_SERVER_ENABLED=true
API_SERVER_HOST=127.0.0.1
API_SERVER_PORT=8642
API_SERVER_KEY=<your-private-strong-key>
```

Preserve a usable existing key. If a new one is needed, generate it privately on
the host, for example with `python3 -c "import secrets; print(secrets.token_hex(32))"`.
Do not copy the example placeholder. Keep the key in protected storage and out of
chat, logs and Git. It is separate from the dashboard password and provider key.
Restart the existing gateway with that profile/environment after checking active
work. A foreground installation uses `hermes gateway`. See the
[official API guide](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server).

## Provide trusted private HTTPS

One supported approach is Tailscale on the host and phone, in the same tailnet.
Inspect `tailscale serve status` and choose unused HTTPS ports. For an API bound
to loopback port 8642, on the host:

```sh
sudo tailscale serve --bg --https=443 http://127.0.0.1:8642
tailscale serve status
```

Follow Tailscale's HTTPS enablement prompts. Use the host's actual DNS name;
`your-host.your-tailnet.ts.net` is an example. Preserve existing Serve mappings.
Serve is private tailnet access; do not enable Funnel, public proxies or router
port forwarding. Tailnet access rules must admit the selected phone and ports.
Other trusted HTTPS reverse proxies are supported. Keep TLS validation enabled.
See [Tailscale Serve](https://tailscale.com/docs/features/tailscale-serve).

## Connect the phone

1. Connect Tailscale to the same tailnet.
2. In Settings enter `https://your-host.your-tailnet.ts.net/` as the API base URL.
   **Do not append `/v1`**: the app adds endpoint paths. Preserve configured
   profile/proxy prefixes, such as a host-supported `/p/<profile>/`.
3. Enter `API_SERVER_KEY` in the API credential field and choose **Save & test connection**.
4. On Android 17, allow local network access for private/Tailscale endpoints.
5. Open **Server details**, inspect capabilities/readiness, then send a harmless
   greeting to verify the intended agent and model. Demo mode is sample data.

## Add management separately

Management uses the Hermes dashboard, its browser login and a verified profile.
Enable dashboard authentication before sharing it. Keep existing credentials and
the signing secret. Set `HERMES_DASHBOARD_PUBLIC_URL` to the exact external HTTPS
dashboard URL in that process's environment (or use its documented
`dashboard.public_url` configuration). For a **new loopback dashboard** on 9119,
a second unused HTTPS port can forward to `http://127.0.0.1:9119`:

```sh
sudo tailscale serve --bg --https=8443 http://127.0.0.1:9119
```

Existing tailnet-bound dashboards may need a bounded loopback relay; preserve
desktop clients and trust only the actual proxy source. Do not rebind to all
interfaces or weaken authentication to make a proxy work. Use the
[official dashboard guide](https://hermes-agent.nousresearch.com/docs/user-guide/features/web-dashboard).
Enter `https://your-host.your-tailnet.ts.net:8443/` under **Hermes management**,
sign in with the browser, return to Signal Grove and select the verified profile.
The API key does not replace dashboard login. API and management URLs may differ.

For a new dashboard, the guide separates its private `.env` login configuration,
the `hermes dashboard --host 127.0.0.1 --port 9119 --no-open` command and the
Tailscale forward. For an existing dashboard, inspect its actual deployment and
Serve mappings first. The guide defaults to an existing installation and does
not issue any command itself.

## Follow the nine pages

Use **Contents** to jump directly to the relevant page:

1. Identify the existing Hermes host, or explicitly choose a new Linux host.
2. Enable the authenticated API in the intended Hermes profile.
3. Verify the phone-to-host private HTTPS route.
4. Enter the API URL and its separate key on the phone.
5. Add the dashboard login and management connection if needed.
6. Verify the serving model, request selection and any session lock.
7. Refresh history and open an existing conversation.
8. Read stored project metadata on the verified management profile.
9. Send one new harmless connection check and review troubleshooting advice.

The current saved API URL appears only when it is valid HTTPS without embedded
credentials, query or fragment. It is shown as an address, never inserted into
a shell command. Navigation and language survive activity recreation; reading
the guide does not change the saved connection.

## Troubleshooting and acceptance

| Observation | Check |
| --- | --- |
| Timeout/refused | Tailscale connectivity, tailnet policy, correct host/port, Serve target, gateway listener. |
| API 401 | API key and profile scope; use neither a model key nor dashboard password. |
| Invalid Host | Exact configured DNS hostname and dashboard public URL. |
| Certificate error | Correct HTTPS name and trusted certificate; preserve TLS checks. |
| Unsupported capability | Deployed Hermes contract/version and enabled host features. |
| Login works, model fails | Check the selected provider ID and model ID separately against the catalog. A configured default can differ from the serving model; request selection does not load weights or remove session locks. |
| Existing chats missing | Check the selected Hermes profile, refresh Conversation history and load older pages. Source/search filters cover loaded rows. Non-API histories open read-only. |
| Projects missing | Sign in under Hermes management using the separate dashboard URL, select the intended profile, then open Host projects → Read stored projects and confirm the read. This shows project metadata; it does not adopt folders into a chat workspace. |
| Host not found after phone restart | Open Tailscale, verify Connected, then Save & test connection again. |

An unauthenticated `/v1/capabilities` normally returns 401. A protected dashboard
must deny management reads without authentication; `/api/status` may expose
minimal liveness with `auth_required=true`. These checks prove transport/auth
boundaries, not successful model/tool execution. Verify a real harmless chat
separately. Keep project files on their verified worker; connecting the phone does
not enroll a worker or grant project writes. Review the exact host/profile/scope
before approving actions, and inspect uncertain receipts before retrying.
