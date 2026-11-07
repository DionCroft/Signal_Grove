# Authenticated project reads through the Android gateway client

This contract covers stored Hermes project metadata through the production
NativeLogin, ManagementClient, GatewayRpcSession, HostProjectsRepository and
HostProjectProtocol. It does not establish workspace adoption, canonical session
attachment, worker ownership or access to project files.

The pinned primary implementations are stable
2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19 and prior
bf0203a794a07aac3ff764972ab9087baa4e4f4e:
hermes_cli/web_routers/chat_ws.py, dashboard_auth/ws_tickets.py,
tui_gateway/ws.py, server.py, methods_projects.py, method_ctx.py and
hermes_cli/projects_db.py. The fixture verifies43 complete Git blobs per pin,
a clean source HEAD and every actually imported project module hash.

The native method projectReadsKeepVerifiedActorProfileHomeAndExactIdentity uses
the existing native S256 login with an in-memory synthetic browser cookie jar.
Protected HTTP reads and ticket requests carry bearer authentication without
cookies. The WebSocket carries a single-use ticket in its subprotocols, without
Cookie, Authorization or Origin. No query token, subscription, server-request
opt-in, reconnect or mutation replay is introduced.

Project inventory and detail use explicit profile parameters. The repository
checks the verified actor and home before transport and rechecks the home after
the response. Two fixture profiles deliberately contain the same project ID
with distinct names and folder paths. Tests require isolated inventories,
active-project metadata, exact IDs, primary/secondary folder metadata, a5062
foreign-ID refusal and a4064 missing-profile refusal. A missing local profile
and mismatched actor must fail before a project RPC. Legacy gateway.ready does
not advertise session authority; no authority or writer grant is invented.

Normal full Android CI uses a bounded MockWebServer. An explicit optional
signalGroveProjectBase selects only a separately owned HTTP127.0.0.1 listener,
an unprivileged port and the exact /dashboard/ prefix; credentials, query and
fragment are forbidden. signalGroveProjectLabel identifies its QA receipt.
Saved credential preferences must remain identical before and after.

The existing Windows TCP fixture adds an explicit --surface projects option.
The default native-auth surface retains the original route restrictions.
Project mode seeds fresh actual project databases and folders on the mandated
temporary drive, with startup orphan sweeping disabled in those fixture homes.
It admits only projects.list/get for the three synthetic IDs and reviewed
profiles, with unique JSON fields, bounded frames and request counts. The
unchanged upstream app performs authentication, dispatch, profile selection and
project lookup. After teardown, logical project rows and seeded history must
match their initial state. The reviewed13 metadata subprocess attempts remain
blocked; unexpected audit denials fail acceptance. Python audit hooks provide
defense in depth and are not an OS sandbox.

Private preparatory host pilots passed19 checks per pinned revision, including
single-use and missing-ticket refusals, profile restoration and unchanged
history/project records. Repository/native acceptance is now recorded in VALIDATION: two methods in
nine targeted invocations,24 semantic checks each, six exact49-request actual
traces,950 stable/888 prior imported modules, unchanged stored history/projects
and0 unexpected denials. Seven CLI refusals and the default8-case host surface
also pass. Full exact-head gates for this new chunk remain separate. Local mocks, preparatory pilots, actual
repository fixtures and hosted full suites are distinct evidence.

Only preserved emulators may run these credential fixtures. Never run them on
the owner phone, change saved connections, uninstall/reset data or alter AVD
geometry. Each controller verifies exact source/APK/signers, device identity,
owned listener PID/root/port and the exact temporary ADB reverse binding.
Uncertain execution requires inspection of the same handles before any retry.
