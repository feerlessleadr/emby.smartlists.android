# Status log

## 2026-10-10: project started

- Owner decisions: admin-only app for the owner, native Kotlin/Compose, direct APK install with Obtainium from GitHub releases, MIT license, app name "Emby SmartLists", package `com.feerlessleadr.embysmartlists`, separate public repo. The phone is always on WireGuard to the home network; the owner's Caddy uses a publicly trusted certificate, so HTTPS with system CAs works internally and externally.
- Plugin side done first (plugin 0.1.9): `docs/api.md`, `GET /info`, `GET /catalog`.
- v0.1.0 scope done: sign-in, list overview with rules in words, refresh / enable / disable / delete. 28 unit tests (URL rules, API client against MockWebServer, describer). Verified on the emulator against the real plugin on the test server: sign-in with a throwaway admin account, list display, expand, delete (no false error), release build launches after R8 and refuses an http address.
- Signed release APK builds locally with the project key (1.5 MB). The release workflow is written but has never run; it needs the four `SIGNING_*` repository secrets before a tag can publish.
- Next: the list editor (create and edit): rule builder driven by `/catalog`, sorts, limits, auto refresh, users and libraries pickers. Then a dry run of the workflow and v0.1.0.

## Findings worth remembering

- AGP 9.3 failed on the cached Gradle 9.3.0 (`NoClassDefFoundError ... ProjectTypeBinding`): use Gradle 9.4 or newer.
- Current Compose/OkHttp artifacts require `compileSdk` 37 (platform `android-37.0` is installed here).
- `R` is an alias for `Invoke-History` in PowerShell: do not define a function with that name in scripts.

## 2026-10-10: v0.1.0 published, editor and settings built (v0.2.0)

- v0.1.0 is published (signed APK, 1.47 MB). The first tag run failed in "Set up the Android SDK" (android-actions/setup-android); replaced by accepting licenses with the runner's own sdkmanager and a "Summarize failures" annotation step; the tag was re-pushed and the run succeeded.
- Editor: `Draft.kt` (ListDraft/RuleDraft/GroupDraft/SortDraft; keeps unknown JSON properties on save, owner fixed once created, empty groups not sent), `Templates.kt`, `ui/editor/*` (template chooser, form, rule cards with value inputs by field type, grouped searchable picker dialogs, sort rows). Media-type restrictions (collection-only types) and video/audio/container field filtering mirror the plugin's admin page and are copied into the app (`MEDIA_TYPE_LABELS`, `COLLECTION_ONLY_MEDIA_TYPES`, `fieldsFor`): keep them in step with the plugin's config-core.js.
- Settings: theme System/Light/Dark (`AppSettings`, applied in `EmbySmartListsTheme`, system bar icons follow the chosen theme), account, sign out.
- Verified on the emulator against the real plugin: create from the Next Up template with a library ticked (saved rules and sort exactly right, list reloaded by itself), edit (rename; the playlist id and the "only started shows" setting survived the PUT), add a group/rule through the field picker, relative-date input, validation message inline and as a snackbar, dark theme. 40 unit tests.
- Test leftovers on the dev Emby: user `apptest`, sample lists (Next Up, Next Up Edited, Recent Movies). Emulator AVD stays installed.

## 2026-10-10: the known limits completed (v0.3.0, not yet released)

- Built everything the web page's form has that the app lacked: playlist users (`users`/`allUsers`/`isPublic`; PlaylistId kept for users who stay), collection owner change, schedules and visibility schedules (`ScheduleDraft`; Time "HH:mm:00", DayOfWeek integer, Interval time span), presentation (sort title, overview, tags with a "managed" switch, favorite), custom images (list/upload multipart/delete/preview via `GET .../images/{type}/file` with the token header; Android photo picker; existing lists only), bumpers (`BumperDraft`; one media type, like the web page), advanced options (IncludeExtras, MatchByMembers, GroupIntoCollections, RandomGroupSelection, SimilarityComparisonFields from `GET /fields`), and per-rule extras (UserId, IncludeUnknownDates, RuntimeUnit, OnlyDefaultAudioLanguage, IncludeParent*/OnlyParent* for Tags/Studios/Genres/IsFavorite, IncludeEpisodesWithinSeries, CollectionSearchDepth).
- UI: `Sections` live in `EditorScreen.kt` (collapsible: Schedules, On the Emby list, Advanced, Bumpers, Images), `RuleGroupsEditor.kt` (shared by the main rules and the bumpers), `ScheduleEditor.kt`, `ImagesSection.kt`.
- 54 unit tests (schedule JSON per trigger, user mappings keep PlaylistId, tags/favorite unmanaged = key removed, bumpers, random group, rule extras, image multipart with MockWebServer).
- Verified on the emulator against the real plugin: a playlist for two users (server created the second playlist, kept the first), a daily 4:45 PM schedule, a custom Primary image (uploaded through the photo picker, preview shown, deleted again), creating a collection (155 items), description and favorite round-tripping, the date picker, the "no date" switch, dark theme. Not exercised by hand: the tag-add field, bumpers, runtime/audio/parent/collection-depth rule options (covered by unit tests of the JSON they produce).

## Duplicate (after v0.3.0, not yet released)

- Copy icon on every rule (the copy lands right below the original), a Duplicate button on every rule group, and Duplicate in a list's menu (opens the editor as a new list named "<name> (copy)" with the same settings and no Emby ids). Unit tests in DraftTest; verified on the emulator.

## 2026-10-10: the polish release (v0.4.0, not yet released)

- Built every item of the quality-of-life proposal; the decisions are in `docs/decisions.md`. Rule/group/list duplicate, collapsible rule cards, unsaved-changes guard, section chips, preview, undo-delete, search and filter chips, refresh status (progress and failure), cover art, accent palette plus Material You, per-list colour, motion, density toggle, widget and quick tile, backups screen, share and import.
- Needs plugin 0.1.10 for Preview only (`POST /preview`, added and documented in the plugin repo; not yet tagged there). Everything else works with 0.1.9.
- Code map: `ui/ListsScreen.kt` + `ListsViewModel.kt` + `ListFiltering.kt` (overview), `data/CoverCache.kt`, `ui/BackupsScreen.kt` (screen and view model), `ui/SettingsScreen.kt` (appearance, `SwatchDot`), `ui/Theme.kt` (`colorSchemeFor`), `data/AppSettings.kt` (`Swatch`, `Density`, list colours), `QuickRefresh.kt` / `RefreshWidget.kt` / `RefreshTileService.kt`, editor changes in `EditorScreen.kt` / `EditorViewModel.kt` / `RuleEditor.kt` / `RuleGroupsEditor.kt`.
- 73 unit tests (new: filtering, cover item, sharing/import, unsaved-change comparison, settings persistence, contrast of every swatch in light and dark, status/preview/backup/cover API calls).
- Verified on the emulator against the real plugin: overview with covers, chips, compact density; editor with folded rules, section chips, colour row, preview sheet (155 items), discard prompt; delete then Undo (list kept) and delete without undo (list removed on the server); violet accent; backups (create, list); widget action (a broadcast refreshed all lists on the server); quick tile registered. Not exercised by hand: share sheet content, file/clipboard import (covered by unit tests), restore and save-a-copy of a backup, Material You (the emulator wallpaper), the failed-refresh marker.
