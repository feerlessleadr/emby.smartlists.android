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
