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
| 6b | Skip (P20) | Second test install: on the intro tap "Set up later" → SKIP FOR NOW | Vault opens with no banner; small amber dot on the Settings gear |
| 6c | Set up later (S28, P20) | Gear → Settings → "recovery words · Not set up" → wrong PIN, then right PIN → words → I understand | "not quite." first; then the row shows green "Set up" and the gear dot is gone |
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

## M5 device tests (vault MVP) — dummy data only
| # | Test | How | Expect |
|---|---|---|---|
| 1 | Add | Vault → + ADD → title, username, website, password → SAVE | Back on the list; "1 logins"; row shows title + username |
| 2 | Generator (P10) | Edit → GENERATE → change length (−/+), toggle classes, ↻ → USE PASSWORD | 20 chars default; "~128 bits"; can't untick the last class; password lands in the field, **not** on the clipboard (paste elsewhere → nothing new) |
| 3 | Reveal (S8/S10) | Open the login → eye | Password shown with "hides in 20s" + shrinking bar; auto-hides at 0; tap eye again → hides; reveal → switch apps → back → masked |
| 4 | Copy (S11) | Copy password → paste into another app within 30 s; wait 30 s → paste again | First paste works; after 30 s the clipboard is empty. Android 13+: the "copied" pop-up shows no preview |
| 5 | Copy + lock | Copy password → press power → wake, paste somewhere | Clipboard already empty (cleared on lock) |
| 6 | History (C9, C15) | Edit → change password → SAVE (repeat 6×); then tap the eye / copy on a history row | HISTORY · 5 / 5, oldest dropped; eye shows that old password for 20 s ("hides in Ns"), copy puts it on the clipboard (cleared after 30 s) |
| 7 | Clear history | CLEAR → confirm | History empty |
| 8 | Notes | Add notes → save → open | Notes masked; eye reveals for 20 s |
| 9 | Delete (P12) | Trash → confirm | Gone from the list; no recycle bin |
| 10 | Search (P22) | Add 3 logins → tap the search icon → type part of a title / username / website → ✕ | Field expands with keyboard; list filters, case-insensitive; ✕ / Back hides the field and shows all logins again |
| 11 | Settings | Gear (toothed wheel) → recovery words / change PIN / lock now; App → about & privacy | Rows work; About shows Privacy (no internet, no data collected, stays on phone), Open source (GPL-3.0, repo), App (version) |
| 12 | Restart | Force-stop → reopen → unlock | All logins still there |
| 13 | Screenshots | Try on list / detail / edit / generator / dialogs | All blocked |

## M6 device tests (fingerprint unlock)
First the automated K_bio tests (needs an enrolled fingerprint; skipped otherwise):
`./gradlew :core:crypto:connectedDebugAndroidTest --no-configuration-cache` → `BiometricKeyTest` 5/5
(hardware-backed, biometric per use, invalidated by enrolment, unusable without the prompt).

| # | Test | How | Expect |
|---|---|---|---|
| 1 | Turn on | Settings → "unlock with fingerprint · Off" → PIN → touch sensor | Row shows **On**; wrong PIN here counts toward the 3 strikes |
| 2 | Unlock | Lock now → unlock screen | Fingerprint prompt opens by itself; touch → vault. "Use PIN" closes it; mint fingerprint key (bottom-left) reopens it |
| 3 | Counter reset (S12) | 2 wrong PINs → unlock with fingerprint | Vault opens with "2 wrong PIN attempts since your last unlock"; next time you have 3 tries again |
| 4 | Lockout (S3) | 3 wrong PINs | Locked-out screen; no fingerprint key/prompt anywhere until recovery words are used |
| 5 | New fingerprint | Turn on → phone Settings → add a fingerprint → back to the app (lock first) | No prompt; amber notice "A fingerprint was added…"; PIN unlocks; Settings shows **Off**; turning it on again works |
| 6 | Turn off | Settings → On → tap | **Off**; unlock screen has no fingerprint key |
| 7 | No strong biometric | Phone with no fingerprint enrolled (or emulator without one) | Settings has no fingerprint row |
| 8 | Erase (S30) | With fingerprint on → lockout → erase → set up again | New vault starts with fingerprint Off |

## M7 device tests (autofill: fill) — dummy data only
Turn it on first: Settings → Autofill → "fill passwords in other apps" → pick OffGrid Vault. In Chrome also set
Chrome → Settings → Autofill services → "Autofill using another service" (Chrome restarts).
| # | Test | How | Expect |
|---|---|---|---|
| 1 | Locked | Lock the vault → open any app's login screen → tap the username field | Only **Unlock OffGrid Vault** (keyboard strip or dropdown) — no entry names. Tap → PIN/fingerprint → suggestions appear |
| 2 | Lockout (S3) | Lock out (3 wrong PINs) → tap Unlock in another app | "vault locked." screen → Open OffGrid Vault; no PIN pad |
| 3 | Web match (S14) | Entry with website `github.com` → Chrome → github.com/login | Entry suggested; tap fills username + password |
| 4 | Subdomain | Same entry → `gist.github.com` login | Suggested (same eTLD+1) |
| 5 | Shared hosting | Entry `a.github.io` → open `b.github.io` | **Not** suggested |
| 6 | App, no link | Any app's login (e.g. a test app) | Only **Search vault…** |
| 7 | Remember (S15) | Search vault… → pick entry → tick Remember for this app → FILL | Fills; next time the entry is suggested directly in that app |
| 8 | Look-alike (S13) | Optional: sideload a debug build of the same package with another signature | No suggestion |
| 9 | Own app | Tap fields inside OffGrid Vault | No OffGrid suggestions; other autofill services can't save vault data |
| 10 | Screenshots | Try on the autofill unlock / search screens | Blocked; not in recents |

## M8 device tests (autofill: save new logins) — dummy data only
| # | Test | How | Expect |
|---|---|---|---|
| 1 | Save from browser | Chrome → a login page not in the vault → type user + password → submit | Android asks "Save to OffGrid Vault?" → Save → our "save this login?" (unlock first if locked) → SAVE. New entry titled with the site (e.g. `example.com`), website = the host |
| 2 | Save from app | Same in an app | Entry titled with the app's name, linked to the app (suggested there next time), no website |
| 3 | Update | Log in again with a **new** password for a saved login | "update password?" → UPDATE; detail shows the new password and the old one in history |
| 4 | Unchanged | Log in with the same password | Android may still ask; we show "already saved." |
| 4b | Second account (P23) | Log in to the same app/site with a **different** username → save | "save this login?" (new entry); your other account is listed under "Or update a saved account" but not selected. After SAVE both accounts exist and both are suggested when filling |
| 4c | Username not captured | If the app doesn't report the username: save | Still a **new** entry unless you pick an account to update |
| 5 | Not now | Tap NOT NOW / back | Nothing saved |
| 6 | Locked + lockout | Vault locked out → save from another app | "vault locked." → nothing saved |
| 7 | Leave it | Accept Android's save prompt, then ignore our screen for > 5 min | The parked login is wiped; reopening does nothing |
