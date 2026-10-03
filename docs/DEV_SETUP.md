# Developer setup & guardrails

## Toolchain
| Thing | Version | Notes |
|---|---|---|
| JDK | 17+ (CI uses 21) | Gradle runs fine on 25 |
| Gradle | 9.8.0 via `./gradlew` | Wrapper download is SHA-256 pinned |
| AGP / Kotlin | 9.4.1 / 2.4.20 | AGP 9 has built-in Kotlin (no `kotlin-android` plugin in modules) |
| Android SDK | **Platform 37** (`compileSdk`), build-tools 37 | Compose 1.12 requires compileSdk ≥ 37. `targetSdk` stays at Play's requirement (36, T12) |

Install the platform once: `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"`.

## Everyday commands
```bash
./gradlew assembleDebug                 # installable debug APK (id ...offgridvault.debug)
./gradlew check assembleRelease         # exactly what CI runs
./gradlew :app:installDebug             # install on a connected device/emulator
```

> ⚠️ **Never put real passwords in a debug build.** Debug builds are debuggable, so
> `adb shell run-as` can read their private files (the vault). Only Play/release builds are
> non-debuggable (enforced by lint `HardcodedDebugMode`).

## Guardrails (all run in `check`, all fail the build)
| Task | Enforces |
|---|---|
| `:app:check{Debug,Release}NoInternet` | Merged manifest has no `android.permission.INTERNET` (hard constraint). Report: `app/build/reports/no-internet/` |
| `:app:check{Debug,Release}NativeLibAlignment` | Every 64-bit `.so` in the APK is 16 KB aligned (Play). Report: `app/build/reports/native-alignment/` |
| `checkForbiddenApis` | No `android.util.Log`, `Log.x(`, `print/println`, `System.out/err`, `printStackTrace` in any source; raw Compose `Dialog`/`Popup` only inside `SecureDialog.kt`, raw `BasicTextField` only inside `SharpInput.kt`, no `android.app.(Alert)Dialog` |
| `:app:lintDebug` / `lintRelease` | Lint with security checks as errors (`AllowBackup`, `DataExtractionRules`, `SecureRandom`, `GetInstance`, exported components…) |
| Release R8 | Strips `android.util.Log` calls from dependencies too |

## Adding or upgrading a dependency (T13)
1. Edit `gradle/libs.versions.toml`.
2. Refresh locks: `./gradlew resolveAllDependencies --write-locks`
3. Refresh checksums: `./gradlew --write-verification-metadata sha256 check assembleRelease assembleDebug resolveAllDependencies --no-configuration-cache`
4. Review the diff of `gradle/verification-metadata.xml` and `*/gradle.lockfile` — new entries should be exactly what you meant to add.
5. Run `./gradlew check` — the INTERNET check proves the dependency didn't add network access.
6. If AGP changes, add the Windows/macOS `aapt2` checksums by hand (CI only resolves the Linux one).

## M0 manual test checklist (needs a phone or emulator)
| # | Test | How | Expect |
|---|---|---|---|
| 1 | App starts | `./gradlew :app:installDebug`, open "OffGrid Vault" | Welcome screen: dark band, serif headline, mint "nowhere else.", black pop button with 3D edges |
| 2 | Pop button press | Press and hold "SET UP VAULT" | Face sinks into the edges; edges disappear; nothing else happens (M0) |
| 3 | FLAG_SECURE | Take a screenshot; open Recents | Screenshot blocked/black; Recents thumbnail blank |
| 4 | No INTERNET | `adb shell dumpsys package io.github.mnvkalyansambhana.offgridvault.debug \| grep -i internet` | No output |
| 5 | No backup | `adb shell bmgr backupnow io.github.mnvkalyansambhana.offgridvault.debug` | Reports backup not allowed / nothing backed up |
| 6 | Fonts offline | Airplane mode, cold-start the app | Same fonts as online (they are bundled) |
| 7 | Separate debug app | Install debug next to a future Play build | Two apps, independent data |
