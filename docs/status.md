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
