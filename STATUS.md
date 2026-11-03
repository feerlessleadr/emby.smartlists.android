# Emby SmartLists (Android): status

Snapshot written 2026-11-03. **SUPERSEDED and frozen.** Its code was copied into Embidextrous (`C:\dev\embidextrous`), which now has the SmartLists screens.

## State
- Latest release: v0.4.1 (2026-10-10, security fixes from the 2026-10-10 review). Branch `main` equals `origin/main` at 79444cb.
- Only local change: untracked `HANDOFF.md` (written 2026-10-10 as a merge guide for the Embidextrous port). It is not committed. Whether to commit it is the owner's call.
- 86 unit tests. Stack: Kotlin 2.4.21, AGP 9.3.3, Gradle 9.5.1, compileSdk 37, targetSdk 36, minSdk 26.

## In progress
- Nothing. No further development is planned.

## If asked to change SmartLists app behavior
- Make the change in `C:\dev\embidextrous`, not here. Only touch this repo if the owner explicitly asks (for example to archive it on GitHub with a pointer to Embidextrous, or to commit `HANDOFF.md`).
- The plugin API contract is the plugin's `docs/api.md` (`C:\dev\jellyfin-smartlists-plugin-emby`). Needs plugin 0.1.9 or newer (0.1.10 for preview).

## Known limits
- Per-list colors are stored on the phone only (the plugin has no field for them).
- `versionCode` ignores a pre-release suffix.
- CI hardening gaps: actions pinned by tag, `contents: write` for the whole release run.
- Some hard casts in `SmartListDoc.parse`.

## Not verified (by hand)
- Share sheet, import, restore backup, widget placement, bumpers UI. Many editor features were hand-verified on an emulator; these were only unit tested or not exercised.

## Manual steps for the owner
- Decide whether to archive the GitHub repo and commit `HANDOFF.md`.
- Signing keystore and values file live outside the repo, in a folder under the owner's profile (see `CLAUDE.md` Releasing). Do not read them.
