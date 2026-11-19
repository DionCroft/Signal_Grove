# Reviewed earlier turns in phone chat

19 November 2026. Development branch.

Users can copy eligible earlier turns and an optional unsent draft into a
separate phone chat. The selection begins empty. The review shows the source
turn numbers and offers a full-turn reader. It passes only the selected turns
and draft to the exact prompt counter. Confirmation saves the child with its
parent's model fingerprint and no new inference. A later Send uses the original
order of the copied turns and the new question. An over-limit request leaves the
child and drafts readable.

New selected-turn chats use transcript schema 5 inside the existing backup
container. Only these chats use a new schema. The reader still accepts the
previous four schemas. Older builds that lack schema 5 support cannot read
these new chats. Provenance records the source snapshot hash and selected
indices. Restore preserves those references, remaps included parent links and
leaves excluded parent links detached. Markdown adds a source-reference section
for selected-turn chats.

## Validation

The exact APK pair passed 1,150 JVM tests in 109 reports, both lint variants
with zero errors and 76 focused checks on Android APIs 26 and 37 without skips.
New cases checked malformed and stale selections, copy without inference, exact
prompt content after a deliberate Send, review cancellation with a changed
draft, standard toolbar height at large bilingual text sizes, retained original
bytes and backup restore with both source and child IDs
in use. The new copies preserve their selected rows and branch provenance.
The test verified that Markdown includes selected turns and omits the unchecked
turn's contents. The actual Android 8 app also copied turns 1 and 3 with weights unloaded. It retained the original byte-for-byte and displayed the draft without sending.

The earlier text-button build passed the scripted tests. At 1.3× text on the
real Android 8 app it left too little room for the chat. The final build uses a
compact branch icon with the localized Earlier turns label. The old result is
retained. Physical backup and continuation acceptance and editable reviewed
summaries remain open.

- Build receipt `20261119-151121-51c1a59261a14130b461c44b387f9be0`
- Source snapshot SHA-256 `9ba7a1f2ec9a700d2e305e9c7e53b3dce710733ed3f9924248666e633778fe31`
- Debug APK SHA-256 `ced4c17b5bc03c466a79308164e512699e32ceb6a088b70feb6ae9dfbc5668e6`
- Test companion SHA-256 `e713f32e5c09ee2382473cce7755ee39de24e1055f511d630bd71e5f04f94768`
- API 26 instrumentation log SHA-256 `6b1aa333fbf7bbf24e0d60db01855dbcd82114c6b553729df9af48012ccc129a`
- API 37 instrumentation log SHA-256 `0ff7ceda7d73342da4fcb9c9b1be0b102c1374d83c079bd6e87dedfaf84b3e6a`
