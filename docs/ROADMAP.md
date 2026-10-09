# Roadmap — milestone plan

Each milestone is **independently testable** (has its own exit tests) and **shippable** (merged to `main`, CI green, signed build uploaded to a Play testing track). Later milestones build on earlier ones but never require reopening them.

Rules for every milestone:
- CI green: build, unit tests, Lint, detekt, merged-manifest INTERNET check (T13, T14).
- `security-review` skill run on all code touching crypto, keys, storage, unlock, clipboard or autofill.
- Any new dependency: update dependency-verification metadata and re-check the merged manifest.
- New decisions recorded via `decision-log`.

| Milestone | Theme | Play track | User-visible? |
|---|---|---|---|
| M0 | Foundation & guardrails ✅ *code done 2026-10-03 — awaiting device test + Play Console* | Internal | Empty shell |
| M1 | Crypto core ✅ *done 2026-10-03 — 34 JVM + 3 on-device tests pass; Pixel 9a bench t=16 ≈ 0.6 s (cheap-phone floor check still open)* | Internal | Debug-only benchmark screen |
| M2 | Vault file format & storage ✅ *code done 2026-10-05 — 22 JVM tests incl. crash at every save step* | Internal | No |
| M3 | Setup & PIN unlock (happy path) ✅ *code done 2026-10-05 — 70 JVM tests pass; awaiting device tests + S21 lab result* | Internal | Yes |
| M4 | Lockout, recovery & session ✅ *code done 2026-10-06 — 88 JVM tests pass; awaiting device tests* | Internal | Yes |
| M5 | Vault MVP (entries, reveal, copy, generator) ✅ *done 2026-10-06 — 105 JVM tests pass, Pixel 9a device test passed; closed test moved to after M9 (P21)* | Internal | Yes — first useful app |
| M6 | Biometric unlock ✅ *code done 2026-10-06 — 110 JVM tests pass; awaiting device tests; pushed `107932c`* | Closed | Yes |
| M7 | Autofill — fill ✅ *code done 2026-10-06 — awaiting device tests* | Closed | Yes |
| M8 | Autofill — save new logins ✅ *code done 2026-10-06 — 127 JVM tests pass (34 crypto, 78 vault, 15 autofill; incl. P23); awaiting device tests* | Closed | Yes |
| M9 | Hardening & production launch 🟡 *code done 2026-10-06 (S25, signing config, release docs); open: device tests, S21, trademark, closed test* | **Closed test (14 days, P21) → Production** | Yes |

> **Personal Play account (P13):** production access requires a closed test with a minimum number of testers for 14 continuous days (check current numbers in Play Console). ~~Clock starts at M5~~ — per **P21** the closed test starts after M9; recruit testers before M9 ends so the 14 days begin immediately.

---

## M0 — Foundation & guardrails
**Goal:** a project where the hard constraints are enforced by the build, not by memory.

Scope
- Gradle KTS, version catalog, modules `:app`, `:core:crypto`, `:core:vault`, `:autofill` (T4, T12).
- Manifest: `allowBackup="false"`, data-extraction rules excluding everything, INTERNET `tools:node="remove"`; **not** `directBootAware`.
- Merged-manifest check task fails build on INTERNET (T13). Dependency verification + locking (T13).
- Single Activity with FLAG_SECURE; `SecureOn` helper for dialogs (T2).
- Compose theme + core components from `docs/DESIGN_SYSTEM.md` (PopButton, HeroBand, SharpCard, InverseCard, SharpInput, label/headline styles); fonts bundled in the APK (P15, T15, T16).
- detekt with `Log`/`println` bans; Lint security checks as errors; R8 `-assumenosideeffects` for `Log` (T12, T14).
- GitHub Actions CI.
- Play Console: app created, Play App Signing, Data safety = "no data collected", internal track.

Exit tests
- Adding a dummy dependency that declares INTERNET → build fails.
- `adb shell bmgr backupnow` / device-transfer → nothing backed up.
- Screenshot of the app shows black.
- Spike result for T1: can a Compose non-password field set `IME_FLAG_NO_PERSONALIZED_LEARNING`? Record outcome.

Ship: internal-track build installs from Play.

---

## M1 — Crypto core (`:core:crypto`)
**Goal:** every primitive the vault needs, proven against official test vectors.

Scope
- Argon2id wrapper (Lazysodium) + on-device calibration (m = 64 MiB, p = 1, floor t = 2) (C13, C14).
- AEAD + HKDF via Tink (NO_PREFIX) (T8).
- BIP-39 encode/decode/checksum, 12 words (S5, T10).
- `SecureRandom` usage centralised; secret byte arrays zeroed after use (best effort, S25).
- Debug-only benchmark screen (excluded from release).

Exit tests
- Argon2id: RFC 9106 test vectors. HKDF: RFC 5869 vectors. BIP-39: official vectors (entropy ↔ words).
- AEAD: round-trip, tamper (ciphertext/tag/AAD) fails, nonce uniqueness over many encryptions.
- **Benchmark on a cheap Android 11 phone** → confirms t ≥ 2 fits ~1 s; closes the C14 follow-up.
- 16 KB alignment check of `libsodium.so` and JNA `.so` (APK Analyzer).

Ship: internal build (release has no visible change).

---

## M2 — Vault file format & storage (`:core:vault`)
**Goal:** a crash-safe, tamper-evident vault file, independent of how keys are obtained.

Scope
- Wire schema: `Vault`, `Entry` (urls, linked_apps, password, notes, history ≤ 5), secrets `bytes` + `redacted` (T7, C15).
- File format v1: magic, format_version, generation, Argon2 params + salt, wrapped keys; header = AAD (C6, C16, C17).
- `AtomicFile` save, `vault.prev`, open/fallback logic with user warning, double-save API for sensitive operations (C5, C18).
- Format-version migration hook.

Exit tests
- Round-trip with in-memory test keys.
- Flip any header/payload byte → open fails cleanly.
- Kill process mid-save (instrumented) → valid vault or valid `vault.prev`, never neither.
- Corrupt `vault.bin` → falls back to `vault.prev` with warning; after double-save, `vault.prev` has no deleted data.
- `toString()` of any message never contains secret bytes.

Ship: internal build.

---

## M3 — Setup & PIN unlock (happy path)
**Goal:** a real user can create a vault and unlock it with their PIN.

Scope
- Keystore `K_device` (StrongBox → TEE fallback). `setUnlockedDeviceRequired` **behind a flag** until tests pass (S21).
- Secure-lock-screen check at setup (S22).
- Create PIN with in-app number pad; blocklist + pattern rejection (S19, S20).
- Recovery-words 4-screen flow, type-back 3 words (P9, S5).
- Wrap DEK ×2 (PIN, recovery); unlock with PIN; locked/unlocked `VaultSession` (T3).

Exit tests
- Fresh install → setup → kill app → PIN unlock works.
- Blocked PINs rejected (`123456`, `000000`, `121212`…).
- Recovery words never in logs, never in files (grep app data dir).
- **S21 experiment:** change device screen lock, then remove it — does `K_device` survive with `setUnlockedDeviceRequired`? Record result; decide S21 flag.
- Copying vault file to another device → cannot open.

Ship: internal build; start recruiting closed-test testers.

---

## M4 — Lockout, recovery & session
**Goal:** every failure and lock path behaves exactly as designed.

Scope
- Attempt counter: own file, synchronous write **before** verification (S12, S17).
- 3 wrong PINs → locked; recovery words → new PIN → counter reset (S3, S6).
- "N wrong PIN attempts since your last unlock" notice (S12).
- Session: 5-min inactivity, screen-off lock, wipe DEK + session key (S23).
- PIN change requiring current PIN, double-save (S24, C18). Behaviour when device lock removed later (S22).

Exit tests
- Wrong PIN then kill app before result → counter still incremented.
- 3 failures → PIN and (future) biometric path blocked; only recovery works.
- After PIN change: old PIN fails; `vault.prev` cannot be opened with old PIN.
- Screen off → locked; 5 min idle → locked; app switch → not locked.

Ship: internal build.

---

## M5 — Vault MVP
**Goal:** first genuinely useful release.

Scope
- Entry list + search, add/edit/delete with confirmation, no recycle bin (P12).
- In-memory second encryption layer with session key (S9).
- Reveal 20 s, restart on tap, re-mask on background (S8, S10).
- Copy: sensitive clip, clear at 30 s / on lock, pending-clear flag (S11, S18).
- Password history ≤ 5 + clear history (double-save) (C15, C18).
- Password generator (P10).
- Secret-field input hygiene; `filterTouchesWhenObscured` (S20).

Exit tests
- Revealed password re-masks at 20 s, on background, on screen-off.
- Clipboard empty after 30 s; after lock; after process death + reopen.
- Generator: statistical sanity (character distribution), never touches clipboard.
- Keyboard (Gboard) doesn't suggest a stored password after entering it.

Ship: **closed test** — 14-day clock starts.

---

## M6 — Biometric unlock
Scope: optional enrol (requires PIN unlock), `K_bio` with BIOMETRIC_STRONG + invalidated on enrolment, `BiometricPrompt` + `CryptoObject`, `KeyPermanentlyInvalidatedException` → PIN fallback + re-create, blocked after lockout, double-save on removal (S2, S3, C18).

Exit tests: enrol new fingerprint → biometric disabled, PIN works, re-enrol works; biometric blocked after 3 wrong PINs; device without class-3 biometrics hides the option.

Ship: closed test update.

---

## M7 — Autofill: fill
Scope: `AutofillService`; app match by package + cert SHA-256 (S13); web match eTLD+1 via bundled PSL, browser allowlist (S14, T9); "Remember for this app" linking (S15); ground rules — tap to fill, locked → "Unlock OffGrid Vault", no-match → "Search vault…" (S16); inline suggestions (C12).

Exit tests: sideloaded app with same package name, different cert → no suggestions; non-allowlisted "browser" claiming `bank.com` → treated as app; `a.github.io` never matches `b.github.io`; PSL official test file passes; locked vault never reveals entry names; lockout respected from autofill.

Ship: closed test update.

---

## M8 — Autofill: save new logins
Scope: `onSaveRequest` → user-confirmed save/update, unlock if needed, link per S13/S14, old password → history (P11, C15).

Exit tests: save from app and from allowlisted browser; update pushes previous password to history; non-allowlisted browser's claimed domain never saved as web URL.

Ship: closed test update.

---

## M9 — Hardening & production launch
Scope
- `setAccessibilityDataSensitive` on secret views (S25).
- Full `security-review` pass across the codebase; threat model re-read vs implementation.
- Final S21 decision applied; PSL refreshed (2026-10-01 list, M7); dependency audit → `docs/DEPENDENCY_AUDIT.md` (2026-10-09: 0 advisories, rerun with `tools/osv_audit.py`).
- Store listing, privacy policy (no data collected), "OffGrid" trademark/Play name check (P8). Drafts: `docs/PLAY_LISTING.md`, `docs/PRIVACY_POLICY.md`; steps: `docs/RELEASE.md`.
- Closed-test requirement met → apply for production.

Exit tests: full manual test script (setup, unlock, lockout, recovery, biometric, autofill fill/save, clipboard, reveal) on Android 11 cheap phone + latest Android flagship/emulator.

Ship: **production**.
