# Emby SmartLists (Android)

Native Android app (Kotlin, Jetpack Compose, Material 3, OkHttp, kotlinx.serialization) that manages the SmartLists plugin on an Emby server through the plugin's HTTP API. Administrator-only, single user (the owner). The plugin lives in https://github.com/feerlessleadr/emby.smartlists (local folder `C:\dev\jellyfin-smartlists-plugin-emby`); its API contract is `docs/api.md` there and is the source of truth for what this app may call. The app checks `GET /info` `ApiVersion` against `SUPPORTED_API_VERSION` (Models.kt).

## Decisions (do not relitigate without new evidence)

The full, dated log of what was decided and why is `docs/decisions.md`: read it first and add to it whenever a decision is made, however small (the owner asked for this explicitly). The short list below is the foundation.

- **HTTPS only in release builds**, system CAs only, no cleartext (network security config; the debug source set allows cleartext to a local test server). `UrlRules.normalize` enforces it in the sign-in form.
- **Token only in the `X-Emby-Token` header**, stored encrypted with an Android Keystore AES-GCM key (`KeystoreSecureStore`). The password is never stored.
- **Lists are kept as `JsonObject`** (`SmartListDoc`) and edited in place so unknown or future plugin properties survive a PUT.
- Rule/sort/field knowledge comes from `GET /catalog`; do not hard-code field lists in the app.
- Separate repo from the plugin on purpose (the plugin's GitHub installer and Obtainium would each see the other's releases, and the plugin workflow triggers on every `v*` tag).
- Distribution: GitHub releases, one signed APK asset, installed with Obtainium. The release signing key is NOT in the repo (see Releasing); a debug-signed build must never be published.

## Commands (Windows, PowerShell)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat test                 # unit tests (MockWebServer for the API client)
./gradlew.bat assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Versions live in `gradle/libs.versions.toml` (AGP 9.x needs Gradle 9.4+; the wrapper is 9.5.1; compileSdk 37 because current libraries require it). `local.properties` (git-ignored) holds `sdk.dir`.

## Testing on the emulator

AVD `Medium_Phone_API_37.0` (in `%LOCALAPPDATA%\Android\Sdk`); the host's test Emby is reachable at `http://10.0.2.2:8096` from the debug build. `adb` is in `platform-tools`. Drive with `adb shell input tap/text` and read screenshots from `adb exec-out screencap -p`; the screen is 1080x2400. Use a throwaway admin account on the test server, never a real password.

## Releasing

Write `docs/releases/vX.Y.Z.md`, push, then push tag `vX.Y.Z`; `.github/workflows/release.yml` tests, signs and publishes. It needs the repo secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` (the keystore and a file with the values are in `%USERPROFILE%\.emby-smartlists-signing\`, outside the repo). Losing the key means installed copies can never be updated.

## Conventions

- Commit attribution line: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Commit, push and tag only when the owner asks.
- Keep README and `docs/` current; note anything a later session needs in `docs/status.md`.
