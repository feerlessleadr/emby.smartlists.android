# Decisions

A running log of what was decided about the app, when and why. Newest at the bottom of each section. `docs/status.md` says what is built; this file says why it is the way it is. Add to it whenever a decision is made, however small.

## Foundations (v0.1 to v0.3)

- **Native Kotlin and Jetpack Compose (Material 3)**, admin-only, single user (the owner). No iOS, no tablets-specific layouts.
- **Separate public repo** (`feerlessleadr/emby.smartlists.android`, MIT) rather than living in the plugin repo, to keep both histories and release flows clean.
- **HTTPS only.** The owner's servers all have publicly trusted certificates, so the app refuses `http://` URLs (debug builds allow cleartext for the local emulator only).
- **Install through Obtainium** straight from GitHub releases: one signed APK per release, built by a tag-triggered workflow. The signing key lives outside the repo and is never printed or committed.
- **Sign in with an admin Emby user** (token stored with Android Keystore AES-GCM, sent only as `X-Emby-Token`). The plugin's API is admin-only.
- **Lists are edited as JSON documents** (`JsonObject`) through draft classes that preserve unknown properties, so a newer plugin field is never lost by an older app.
- **Rule fields, operators and sorts come from the plugin's `GET /catalog`**, not hard-coded, so the app follows the plugin.
- **Theme setting:** System, Light or Dark (stored in the app).
- **Scope of the editor:** everything the web form offers ("known limits" batch, v0.3.0).

## Duplicate (after v0.3.0)

- Duplicate exists at three levels: a **rule** (copy lands right below it), a **rule group** (adds an "or" group), and a **list** (opens the editor as a new list).
- A list copy is named `<name> (copy)`; copying a copy does not stack suffixes.
- A list copy carries **no Emby identity**: no list id, no playlist/collection ids, no per-user playlist ids. Saving creates a fresh list.

## Polish release (v0.4.0): "do all of it"

On 2026-10-10 the owner approved every idea in the quality-of-life and design proposal ("I love all of the ideas. Please do all of them") and asked that every decision be written down. The full set, with the decisions made while building each:

### Daily friction

1. **Rules as a readable sentence (collapsible rule cards).**
2. **Unsaved-changes guard** in the editor (discard prompt, a dot on Save while dirty).
3. **Undo on delete** (snackbar with Undo instead of a confirm dialog).
4. **Search and filter** on the list overview (type chips and a search box).
5. **Preview before saving.** Needs a new plugin endpoint (a dry run); this is the only item that requires a plugin release.
6. **Visible status:** refreshing spinner, failed-refresh marker with the error, pull-to-refresh.

### Look and feel

7. **Cover art on list cards.**
8. **Accent palette** in Settings (curated swatches, plus an optional Material You dynamic colour switch). A free-form colour picker was rejected: arbitrary colours break contrast in one of the two themes.
9. **Per-list colour**, stored in the app only (the plugin has no field for it, so it does not sync across devices).
10. **Motion:** animated expand/collapse, card removal and section transitions.
11. **Layout:** section index in the editor; compact/comfortable density toggle on the overview.

### Bigger ideas

12. **Home-screen widget / quick tile** to refresh all lists.
13. **Backups:** create, list, download/restore and delete plugin backups from the app.
14. **Share a list as JSON** and import one (a list is shared without ids, like a copy).

### Decisions made while building the polish release

**Preview (plugin 0.1.10 plus app)**
- New plugin call `POST /Plugins/SmartLists/preview?limit=N`: a dry run that takes a list as `POST /` would and returns `{Total, Items[{Id, Name, Type, Detail}]}`. Nothing is saved. A throwaway Id is assigned when none is sent. Playlists are evaluated for the first user they are for, collections for their owner (400 with a plain sentence when there is none). Bumpers are not woven into the preview.
- The API version stays 1 (the change only adds a call). The app tells the person "Preview needs SmartLists plugin 0.1.10 or newer" on a 404 instead of breaking, and every other feature still works with plugin 0.1.9.
- The editor shows the preview in a bottom sheet from a "Preview what matches" button under the rules (25 items; the total is always counted).

**Editor**
- Rules fold to one sentence built with `Describe.rule` ("Date Added to Library newer than 30 days"). A rule starts open while it still needs a value, and a rule just added or duplicated is open; everything else starts folded. Open/folded state is kept per rule key across rotation.
- "Unsaved changes" means: the draft's JSON differs from the JSON it had when editing began (or the list colour changed). A template or copy left untouched is not a change. Back (button or gesture) asks "Discard your changes?"; the Save button shows a dot while there are changes.
- A strip of section chips (Basics, Rules, Sorting, Limits, Refresh, More) sits above the form, scrolls to a section on tap and follows the scroll position.
- Collapsible sections and rule cards animate open/closed; list cards animate in place when lists are added, removed or filtered.

**Overview**
- **Delete = undo window, not a confirm dialog.** The list disappears at once and an "Undo" snackbar shows for 8 seconds; the server delete (including its Emby playlist/collection) happens when the window ends. Closing the app inside the window leaves the list untouched, the safe outcome. A second delete ends the first one's window. The old "also delete the Emby list" checkbox is gone: deleting always removes the Emby playlist/collection too, as the web page's default does.
- Search matches every typed word against the name, any case, any order. Filter chips (All, Playlists, Collections, Disabled, Failed) carry counts; Disabled and Failed only appear when something is disabled or failed.
- Status comes from `GET /Status`: refreshes in progress show a progress bar and "Refreshing… N of M" on the card (the app polls every 2.5 s while any run); the newest history entry per list decides the red "Last refresh failed" marker (the error text shows when the card is expanded). The plugin only keeps recent history, so a list with none shows no marker.
- Cover art is the primary image of the list's Emby playlist (first user's) or collection, fetched from `/Items/{id}/Images/Primary?maxWidth=200` with the token header, cached in memory (64 pictures) and looked up again when the list's last-refresh time changes. No image library was added. Lists without a picture show their type icon on a tinted tile.
- Density: Comfortable (60 dp cover, two-line summaries) or Compact (40 dp cover, one line). The toggle is in the filter row and in Settings; the choice is remembered.

**Colour**
- Eight curated swatches (Emby green, Ocean, Teal, Violet, Rose, Crimson, Amber, Graphite). Emby green keeps its hand-tuned scheme; the others are generated from the hue for light and dark, with surface layers and the snackbar tinted too. The primary colour is darkened until white text on it reaches 4.6:1. A unit test checks text contrast (primary, containers, surfaces, snackbar) for every swatch in both themes.
- "Match my wallpaper" (Material You) is offered only on Android 12+ and is off by default.
- A **list colour** (same eight swatches, or none) is chosen in the editor's Basics and shows as a 5 dp stripe on the card. It is stored on the phone only, keyed by list id, so it does not sync and is lost on reinstall (the plugin has no field for it).

**Sharing and importing**
- Share… in a list's menu sends the list's JSON as plain text through the system share sheet. It carries the rules and settings but no ids and no users (user ids belong to one server).
- Importing (New list, "Import a shared list") accepts pasted text or a file; the result opens as a new list named as shared, owned by the signed-in user, for the person to check and save. Text that is not a list is refused with a sentence.

**Backups**
- Settings, "Backups": back up now, restore (lists that still exist are skipped unless "Overwrite" is ticked), save a copy of the zip to the phone (system file picker) and delete. Times are shown as dates and times, not "x minutes ago", because the server's clock and the phone's can differ by a little.

**Widget and tile**
- A home-screen widget and a quick-settings tile each refresh all lists with one tap and say how it went. They are built with plain `RemoteViews`/`TileService` (no Glance dependency) and share `QuickRefresh`. When the app is signed out they say so.

**Process**
- The emulator may be shared with other work on the same machine (another app took the foreground once during testing); screenshots must be checked to be this app before acting on them.
## Security review (2026-10-10, v0.4.1)

A read-only review of the app and the plugin; every finding was checked against the code. No critical or high issue. The decisions:

- **Redirects are errors.** The HTTP client does not follow redirects (`followRedirects(false)`, `followSslRedirects(false)`): OkHttp only strips the standard `Authorization` header on a cross-host redirect, so our `X-Emby-Token` header (and a 307/308 sign-in body with the password) would have been sent to whatever host the redirect named. The Emby API never redirects; a 3xx now shows "The server redirected the request ... check the server address." Tested with a second server that must receive nothing.
- **Signing out ends the session on the server too** (`POST /emby/Sessions/Logout`, best effort, in the background) and **deletes the Keystore key**, so a copy of the token stops working at once instead of living until the device is removed in Emby. The key is created again at the next sign-in.
- **The widget's tap goes to a non-exported receiver** (`RefreshReceiver`). The exported `RefreshWidget` only draws (the system delivers widget updates to it), so no other app can send the refresh action. The quick tile was already protected by the system-only `BIND_QUICK_SETTINGS_TILE` permission. Checked on the emulator: the old broadcast to either receiver no longer refreshes anything.
- **Values from the server are validated before they go into a URL path** (`ApiClient.seg`: letters, digits, dash, dot, underscore, and not "." or ".."): list ids, Emby item ids, image types and backup file names. A hostile server can no longer add path segments or a query through an id.
- **Anything that arrives from outside is bounded** (`data/Safety.kt`): a shared list over 1 MB or nested deeper than 64 levels is refused before parsing (the JSON parser is recursive and overflowed its stack on deep nesting in a test); a file chosen for import is read with a cap instead of in full; cover and custom-image responses are capped (8 MB and 25 MB) and decoded with a sample size and a pixel limit; backup downloads stream straight into the file the person chose instead of through memory.
- **A release build needs the real signing key.** Gradle refuses `assembleRelease`/`bundleRelease` without `SIGNING_KEYSTORE_PATH` unless `-PallowDebugSigning` is passed, so a debug-signed APK cannot be produced by accident. The release workflow passes the flag only for dry runs, never for a tag (a tag already stops without the secrets).
- **Considered and left as is:** no certificate pinning (the owner's certificate is publicly trusted and renews often; pinning would make renewals fragile); no `FLAG_SECURE` and no app lock (a single-user admin tool; anyone with the unlocked phone has the session, as with the Emby app); GitHub Actions are pinned by version tag, not commit, and the workflow has write access for the whole run (a hardening idea: separate build and publish jobs, pin by commit, verify the Gradle wrapper and the APK signer fingerprint); `versionCode` ignores a pre-release suffix (only matters if a release candidate is ever published).
