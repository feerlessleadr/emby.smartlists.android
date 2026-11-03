# Decisions (Emby SmartLists Android, superseded)

Append-only, newest at the bottom. Never rewrite an entry; to reverse one, add a new entry that references it.

**Existing detailed log:** `docs/decisions.md` has every decision made while building v0.1 to v0.4.1 with the reason. Search it when a past decision matters. The entries below are the ones a future session most needs.

### 2026-10-10, Separate public app repo, HTTPS only, Obtainium distribution
- **Context:** Starting the app for the SmartLists plugin.
- **Options considered:** Live in the plugin repo; separate repo.
- **Decision:** Separate public repo (MIT), native Kotlin/Compose, admin-only, HTTPS only in release, one signed APK per tag, installed with Obtainium.
- **Rationale:** Keeps both histories and release flows clean; the plugin's GitHub installer and Obtainium would otherwise each see the other's releases. The owner's servers have publicly trusted certificates.
- **Impact:** The plugin's `docs/api.md` is the contract. The app checks `GET /info` `ApiVersion` against `SUPPORTED_API_VERSION`.

### 2026-10-10, Lists are raw JSON edited through drafts; catalog-driven editor
- **Context:** The plugin adds fields over time.
- **Decision:** Keep lists as `JsonObject` and edit through draft classes that preserve unknown properties. Rule fields, operators and sorts come from `GET /catalog`.
- **Rationale:** A newer plugin field must never be lost by an older app.
- **Impact:** Carried into Embidextrous unchanged.

### 2026-10-10, Frozen after v0.4.1 and merged into Embidextrous
- **Context:** The owner wanted one app for Hall Monitor and SmartLists.
- **Decision:** This repo stays untouched at v0.4.1 (commit 79444cb) as the source of the port. See `C:\dev\embidextrous\DECISIONS.md` ("One combined app").
- **Rationale:** New package means a new app, so this one cannot be updated into the other; keeping it frozen avoids two codebases drifting.
- **Impact:** Do not develop here. Archiving on GitHub with a pointer is a later, owner-approved step.
