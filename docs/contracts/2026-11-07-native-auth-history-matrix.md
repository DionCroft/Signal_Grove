# Real native-auth and profile-history matrix

The bounded Windows probe scripts/compatibility_probe_native_auth_history.py
imports the actual Hermes dashboard app, starts its lifespan, and exercises its
real ASGI routes, auth middleware, native broker and SessionDB history readers.
It uses the upstream synthetic HMAC provider, not owner credentials or an external
IdP. No application/server source is patched. This is not an Android-browser,
TCP/TLS/reverse-proxy, deployed Pi, worker, inference or full bootstrap certification.

Both reviewed source pins are explicit: stable2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19
and priorbf0203a794a07aac3ff764972ab9087baa4e4f4e. The latter matches the Pi generation's
reported canonical source vector; custom overlays and loaded deployment remain
separate.23 exact Git-blob guards cover app assembly, auth/broker/cookies/registry/
refresh/request/prefix/public-path boundaries, metadata helpers, session/profile
routes and session-store foundations. Source HEAD and cleanliness are checked;
all actually imported project modules receive a SHA-256 inventory.

Each revision exercises21 cases: missing/invalid bearer refusal; real authorize
and callback with no access cookie; PKCE code exchange; cookieless verified
identity; same-identity refresh dispatch; profile discovery; separate inventories
and transcripts under identical IDs in two profiles; foreign-only ID refusal;
unknown profile refusal without directory creation; non-loopback redirect and
plain PKCE rejection; used-code replay rejection; wrong-verifier consumption
with the same generic error; invalid-refresh sign-in response; and unchanged
seeded session rows/messages after browsing.

Token rotation by a real IdP, token expiry timing, live websocket tickets,
canonical restore/adoption, exact native callback delivery and workspace/file/
worker authority are still acceptance gaps. A successful REST read does not
grant writable ownership of a foreign history. No mutation is issued against a
user project. Fixture SessionDB initialization precedes dashboard startup, as an
owning gateway initializes a real store; browsing is not asked to create it.

Use the existing supported Windows Hermes interpreter, with assertions enabled:

    python -I -S -B scripts/compatibility_probe_native_auth_history.py --source-root <reviewed-source> --work-root <fresh-E-fixture> --dependency-site <existing-site-packages> --revision stable-2bd378e

Repeat with prior-bf0203a and its exact source root. Source/output must use E:,
cannot overlap, and output must not already exist. No package installation,
site hooks or bytecode writes. The process receives a sterile fixture home/env;
reads are bounded to fixture/source/dependencies/interpreter/script, writes to
the fixture, databases to the fixture and sockets to process-owned loopback
ports. Every subprocess after preflight is blocked. Exact pinned version/source/
safe-directory Git metadata attempts are narrowly classified and still raise
PermissionError; all other denials fail acceptance. No actual global Git config
or source status command is run by dashboard startup. Two preflight read-only
Git checks use a separate controlled environment before the audit hook.

Python audit hooks are defense in depth, not an OS sandbox. Preserve failed
pilots and inspect their exact receipts before changing a boundary. The initial
six-case pilot passed its auth assertions but failed on13 unexpected metadata
subprocess attempts. After reviewing their source/caller/argv/cwd, they remained
blocked and were separately classified. No failed receipt was relabelled.

Authoritative validation and current limitations are in docs/VALIDATION.md.
