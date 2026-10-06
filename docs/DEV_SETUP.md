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
./gradlew check assembleRelease         # what CI runs (CI adds --no-configuration-cache)
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
5b. **Cold-cache gap:** a warm local cache can miss a few checksums (typically parent POMs) that a fresh
    machine downloads. Before merging a dependency change, run **Actions → "Regenerate dependency
    checksums"** (empty cache on GitHub's runner), download the `verification-metadata` artifact and
    diff it: entries should only be **added**. A **changed** checksum for an existing artifact is a red flag.
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

## M1 device tests (crypto core)
| # | Test | How | Expect |
|---|---|---|---|
| 1 | On-device crypto vectors | `./gradlew :core:crypto:connectedDebugAndroidTest` (phone connected) | 3/3 pass: Android libsodium gives the same Argon2id key as the JVM tests; AES-GCM round-trips; BIP-39 wordlist loads from the APK |
| 2 | Argon2 benchmark | `./gradlew :app:installDebug`, open **"OGV Argon2 bench"** (debug-only launcher icon), tap RUN BENCHMARK | Times for t=2…6 at 64 MiB and the calibrated `t`. Send the numbers back — they confirm C14 (t ≥ 2 within ~1 s) |
| 3 | Benchmark screen is secure | Screenshot while on the bench screen | Blocked (FLAG_SECURE) |

> **Same phone listed twice?** With wireless debugging, `adb devices` can show the phone both as
> `IP:port` (manual `adb connect`) and as `adb-…._adb-tls-connect._tcp` (auto-discovered). Gradle
> then runs the tests on "both" at once and they collide (all tests pass, task still fails).
> Fix: `adb disconnect <IP:port>` so only one entry remains.

JVM unit tests (34, incl. RFC 9106 / RFC 5869 / GCM / BIP-39 vectors): `./gradlew :core:crypto:testDebugUnitTest`.

## Repo gotchas (learned the hard way)
- The repo lives on NTFS (`/mnt/c`), where git has `core.fileMode=false`: new scripts must be marked
  executable in the index with `git update-index --chmod=+x <file>`.
- `.gitignore` ignores build outputs only at `/build/`, `/*/build/`, `/*/*/build/`. Never use a bare
  `build/` — it also hid source packages named `build` (the `buildSrc` guardrails).
- Heavy cold-cache builds can disconnect WSL; prefer the GitHub workflow for those.

## M2 tests (vault file & storage)
JVM only (no device needed): `./gradlew :core:vault:testDebugUnitTest` — 22 tests: format round-trip, every-byte tamper detection, header limits, newer-version handling, open/fallback/repair rules, sensitive saves, `toString()` redaction, and a simulated power loss at every step of a save.

## M3 device tests (setup & PIN unlock)
| # | Test | How | Expect |
|---|---|---|---|
| 1 | Keystore on device | `./gradlew :core:crypto:connectedDebugAndroidTest` | 8/8 pass (3 M1 + 5 DeviceKey) |
| 2 | Screen-lock gate (S22) | Fresh install with phone screen lock **off** (or skip if you don't want to remove it) | "● OFF", button disabled, tap card → Settings; set a lock, come back → "● ON" |
| 3 | Weak PIN (S19) | Setup → try `123456`, `121212`, `112233` | Red "Too easy to guess…" strip, dots reset |
| 4 | Confirm PIN (P18) | Enter a PIN, then a different one on "confirm it." | "PINs didn't match" → back to "pick a pin." |
| 5 | Words (S28, P19) | SHOW MY WORDS → try a screenshot; CONTINUE is disabled until you tick "I've saved them" | Screenshot blocked; tick enables CONTINUE |
| 6 | Optional check (P19) | Tap "Check my words" → pick a wrong word, then the right ones (3 rounds) | "Not that one"; after 3 correct → "before you go." |
| 6b | Skip (P19) | Second test install: on the intro tap "Set up later" → SKIP FOR NOW | Vault opens with the amber "No recovery words yet" banner |
| 6c | Set up later (S28) | Banner → SET UP RECOVERY WORDS → wrong PIN, then right PIN → words → I understand | "not quite." first; then back on vault, banner gone |
| 7 | Seal + vault (P17) | Tap I UNDERSTAND | ~1 s "sealing your vault." → "vault." 0 logins |
| 8 | Quit mid-setup (P17) | Second test install: quit (swipe away) while on the words screen, reopen | Back at Welcome — no vault was created |
| 9 | PIN unlock | LOCK NOW → enter wrong PIN → right PIN | "not quite." → then "vault." |
| 10 | Restart | Force-stop, reopen | Opens on "welcome back." (unlock), not setup |
| 11 | **S21 lab** | Open **OGV Keystore lab**: 1 Create → 2 Test → change phone screen lock (e.g. new PIN) → 2 Test → **remove** screen lock → 2 Test → set a lock again → 2 Test | Send the log: decides whether K_device gets `setUnlockedDeviceRequired(true)` |

Not in M3 yet: attempt limit/lockout and recovery-words unlock (M4), auto-lock (M4), entries (M5).

## M4 device tests (lockout, recovery, session)
Use a vault **with** recovery words written down (dummy data only).

| # | Test | How | Expect |
|---|---|---|---|
| 1 | Attempts left | Lock now → wrong PIN twice | "Wrong PIN · 2 attempts left", then "1 attempt left. After that, only your recovery words can unlock." |
| 2 | Notice (S12) | Then the right PIN | Vault shows amber "2 wrong PIN attempts since your last unlock" (✕ dismisses) |
| 3 | Lockout (S3) | Lock now → 3 wrong PINs | "vault locked." 3/3 screen with USE RECOVERY WORDS |
| 4 | Kill-app bypass (S17) | Wrong PIN, then force-stop immediately (`adb shell am force-stop io.github.mnvkalyansambhana.offgridvault.debug`), reopen | The attempt still counted (e.g. "1 attempt left" on the next wrong PIN) |
| 5 | Lockout survives restart | While locked out: force-stop, reopen | Opens on the lockout screen, not the PIN pad |
| 6 | Recovery → new PIN | USE RECOVERY WORDS → enter the 12 words (try pasting all 12 into box 01) → new PIN + confirm | Vault opens; old PIN now rejected, new PIN works |
| 7 | Wrong words | Enter 12 valid words that aren't yours | "These words don't match this vault." |
| 8 | Forgot PIN? | From the normal unlock screen | Same recovery flow works without being locked out |
| 9 | Change PIN (S24) | Vault → Change PIN → wrong current PIN → right one → new PIN | "Wrong PIN · N left…", then saved; next unlock needs the new PIN |
| 10 | Screen-off lock (S23) | Unlock → press power → wake | Unlock screen |
| 11 | App switch doesn't lock | Unlock → switch to another app for 1 min → back | Still unlocked |
| 12 | 5-min timeout | Unlock, leave the phone untouched (screen kept on via Developer options → Stay awake while charging, or just wait in another app) ≥ 5 min | Back on the unlock screen |
| 13 | No-recovery vault (S6→S30) | Test install with "Set up later" → 3 wrong PINs | Lockout screen: "This vault stays locked" + ERASE VAULT & START OVER |
| 14 | Erase guard (S30) | Erase screen: type `erase` (lowercase) | Button stays disabled until exactly `ERASE` |
| 15 | Erase needs phone lock | Type ERASE → ERASE EVERYTHING → cancel the system prompt | "Screen lock not confirmed — nothing was erased." |
| 16 | Erase | Again → confirm with the phone's PIN/pattern | Back at Welcome; fresh setup works; old vault gone |
| 17 | "Lost your words?" | Locked-out vault **with** words → small link under USE RECOVERY WORDS; also on the 12-words screen | Opens the same erase screen |

Not in M4: entries (M5), biometric (M6), autofill (M7).
