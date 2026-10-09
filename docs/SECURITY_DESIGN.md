# Security design

## 1. Threat model

### We protect against
- **Lost or stolen phone** — vault is encrypted; needs app PIN or enrolled biometric.
- **Someone who knows the phone's screen-lock PIN** — still needs the app PIN (separate secret).
- **Other apps / malware / anyone copying the vault file off the device (while locked)** — data is AES-256-GCM encrypted; the wrapping keys live in Android Keystore hardware and cannot be extracted.
- **Offline brute force of the 6-digit PIN** — the PIN-wrapped key is additionally wrapped by a device-bound Keystore key, so the file is useless off-device; on-device guesses go through the app's 3-attempt limit.
- **Cloud leaks** — no network permission, excluded from Android backup.
- **Screen capture / remote viewing** — FLAG_SECURE on all windows.

### We do NOT protect against
- Malware already running while the vault is **unlocked** (e.g. abusing Accessibility).
- **Rooted / modified** devices (attempt counter and Keystore guarantees can be bypassed).
- **Rolling back or swapping app files** (vault or attempt counter) — impossible on a non-rooted device (app sandbox + file-based encryption); with root it's out of scope (C17, S17).
- Someone with the unlocked app, or with the phone **and** the recovery words.
- **Device loss or uninstall** — data is gone permanently. Recovery words only help with a forgotten PIN on the same device installation.

## 2. Key hierarchy (envelope encryption)

```
                    ┌──────────────── Vault key (DEK, random 256-bit) ────────────────┐
                    │      encrypts vault payload with AES-256-GCM                    │
                    └────────────────────────────────────────────────────────────────┘
        wrapped copy #1 (PIN)          wrapped copy #2 (biometric, optional)     wrapped copy #3 (recovery)
  Argon2id(PIN, salt) → KEK_pin        Keystore key K_bio                        HKDF(entropy, recovery_salt) → KEK_rec
  + Keystore key K_device              (BIOMETRIC_STRONG required,               + Keystore key K_device
    (device-bound, no user auth)        invalidated on new enrolment)              (device-bound, no user auth)
```

K_device and K_bio: `setUnlockedDeviceRequired(true)` — **conditional on device tests** that K_device survives screen-lock change/removal (S21). Losing K_device makes the vault and recovery words permanently useless.

- **Wrapping (implemented M3, `VaultKeys`):** `inner = AES-GCM(KEK, DEK, ad = "offgrid-vault/v1/wrap/<pin|recovery>" ‖ salt)`, `outer = K_device.seal(inner, ad = label)`. Wrong PIN/words → inner fails (rejected); modified blob → outer fails (file treated as corrupt → `vault.prev`); K_device missing → "can't open on this device".
- **K_device (M3, `DeviceKey`):** alias `offgridvault.k_device.v1`, AES-256-GCM, StrongBox → TEE fallback, no user auth, `setUnlockedDeviceRequired(false)` until S21 lab results.
- **Unlock with PIN:** check attempt counter → increment & persist synchronously (S17) → Argon2id(PIN) → unwrap with KEK_pin and K_device → DEK.
- **Unlock with biometric (implemented M6, `BiometricKey`/`PinGate.unlockWithBiometric`):** platform BiometricPrompt (T20) + CryptoObject on K_bio (alias `offgridvault.k_bio.v1`, AES-256-GCM, BIOMETRIC_STRONG per use, invalidated on new enrolment, StrongBox → TEE) → DEK. Copy #2 = `K_bio.seal(DEK, ad = "offgrid-vault/v1/wrap/bio")` (single layer: K_bio is already device-bound). Refused once locked out (S3); success resets the counter (S12). If K_bio is invalidated (new fingerprint), fingerprint unlock is **turned off** (K_bio deleted, copy removed by a sensitive save after the next PIN unlock) and the user turns it on again in Settings — a newly added finger is never trusted automatically.
- **Turning fingerprint on/off (M6):** on = re-enter PIN (counts toward S3) → fresh K_bio → prompt → seal DEK → full save (C16). Off = sensitive save without the copy (C18), then K_bio deleted. Erase (S30) deletes K_bio too.
- **Recovery:** enter 12 words → HKDF → unwrap copy #3 (also needs K_device) → DEK → user sets new PIN → re-wrap copy #1 → reset counter. Words are unchanged.
- **Attempt counter (implemented M4, `PinAttempts`/`PinGate`):** own file `pin.attempts`, written + fsynced *before* each PIN check (S17); unreadable → locked out (S29). Unlock, Change PIN and "set up recovery words later" share one 3-strike budget; once locked out no PIN is tried at all.
- **Recovery → new PIN (M4, `PinReset`):** words → DEK → new PIN with new salt + fresh calibration → sensitive save (C18) → counter reset. A vault without recovery words stays locked (S6).
- **After 3 wrong PINs:** copies #1 and #2 are disabled until recovery succeeds.
- **Erase & start over (S30, M4):** user-initiated only (lockout / recovery screens), typed `ERASE` + the phone's screen-lock credential (system prompt). Order: lock session → delete K_device → delete vault files → delete counter. After K_device is gone no copy of the old vault can ever be opened.
- **Successful unlock** (PIN or biometric) resets the counter to 0 and, if it was > 0, shows "N wrong PIN attempts since your last unlock" (S12).
- Changing the PIN re-wraps copy #1 (new salt, re-calibrated Argon2id `t`). Because the header is AAD, this is a normal full save (payload re-encrypted under the same DEK, fresh nonce) and is written twice so `vault.prev` holds no old-PIN copy (C16, C18).

## 3. Algorithms
| Purpose | Choice |
|---|---|
| PIN → key | Argon2id via Lazysodium-android / libsodium. m = 64 MiB, p = 1, t calibrated on-device at setup to ~0.5–1 s with floor t = 2; recalibrated (with new salt) on PIN change. Validated against RFC 9106 test vectors |
| Recovery words → key | HKDF-SHA256(entropy, salt = per-vault `recovery_salt`, info = `offgrid-vault/v1/recovery-kek`) — words already carry 128 bits of entropy (C19) |
| Vault + key wrapping | AES-256-GCM, fresh random 96-bit nonce per encryption, never reused |
| Hardware keys | Android Keystore (StrongBox if available) |

## 4. Vault file format (v1 — implemented in M2, `:core:vault`)
```
"PVLT" | format_version u16 | header_len u32 | header (Wire VaultHeader) | AES-256-GCM payload
\_______________________ associated data (whole prefix, C6) _______________/
```
- **Header** (`vault.proto` → `VaultHeader`): `generation`, `argon2_iterations`, `argon2_memory_kib`, `pin_salt` (16 B), `recovery_salt` (16 B, C19), `wrapped_key_pin`, `wrapped_key_recovery`, `wrapped_key_bio` (empty when off). Stored in clear, authenticated as AAD.
- **Payload**: `nonce(12) ‖ ciphertext ‖ tag(16)` of Wire `Vault { repeated Entry }`, `Entry { id, title, username, urls[], linked_apps[{package_name, cert_sha256}], password, notes, history[≤5]{password, replaced_at}, created_at, updated_at }`. All personal fields carry `(offgridvault.redacted)` → `toString()` prints `██` (T7).
- **Parse before decrypt** (needed to unlock): header is range-checked first — file ≤ 64 MiB, header ≤ 16 KiB, Argon2 params via `Argon2Params` (C14 floor), exact salt sizes, wrapped keys 1–1024 B. It is trusted only after payload decryption succeeds.
- **Versions**: newer `format_version` → `Unreadable(newerFormatVersion)` (never misparsed); older → migration hook in `VaultFormat.parse`, then re-save.
- Field numbers are permanent; new fields only.

## 5. Saving & rollback (implemented in M2)
- **Save**: write + fsync `vault.tmp` → rename `vault.bin` → `vault.prev` → rename `vault.tmp` → `vault.bin` (atomic renames; same guarantees as `AtomicFile`, plain NIO so every crash point is tested). Every save re-seals the header with `generation + 1` (C16, C17).
- **Interrupted save** (on open): `vault.tmp` + `vault.prev` without `vault.bin` → the crash hit between the renames and the temp file is complete → promote it. Any other leftover temp file is incomplete → delete.
- **Open**: `vault.bin` first; only if it is missing, unparseable or fails to decrypt with an unlocked DEK → `vault.prev`; on success the app shows "Restored from previous save — your last change may be missing" and **both files are re-saved immediately** (C18).
- **Sensitive saves** (PIN change, recovery reset, biometric removal, entry delete, clear history): after the save, `vault.prev` is overwritten with a copy of the new `vault.bin` — C18's "written twice", without a second encryption.
- A credential rejected by `vault.bin` is not retried on an identical `vault.prev` header (no second Argon2 run).
- No separate rollback counter: file rollback needs root / FBE bypass, which is out of scope (C17). Residual: flash wear-levelling may retain old *encrypted* blocks; the directory is not fsynced after rename (same as `AtomicFile`).

## 6. Runtime protections
- Auto-lock after 5 minutes of **inactivity**, immediately on screen-off; switching apps does not lock (S23). DEK held as one `AeadKey` per session and closed on lock (M2); wiping is **best effort**: the JVM/ART may copy arrays and `SecretKeySpec` copies key bytes, so wiping can't be guaranteed (S25).
- Secrets (passwords, notes, history) kept encrypted in memory with an ephemeral session key (S9, implemented M5 as `VaultContent`/`SealedSecret`): re-sealed right after unlock, decrypted only on reveal/copy/edit/save/autofill; the session key is closed on lock. Titles, usernames and websites stay in clear for list/search.
- Reveal auto-masks after 20 s (timer restarts on each tap, S10) and immediately on backgrounding / screen-off. Clipboard cleared after 30 s or on vault lock, whichever first (S11) via `clearPrimaryClip()` without reading first; a persisted "pending clear" flag clears on next start if the process died (S18); mark clip as sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`).
- Tapjacking protection (`filterTouchesWhenObscured`) on sensitive views.
- PIN via in-app number pad; common PINs rejected (S19). Secret fields: no suggestions, `IME_FLAG_NO_PERSONALIZED_LEARNING`; app screens excluded from other autofill services (S20).
- `setAccessibilityDataSensitive(YES)` (API 34+, S25) — implemented M9 on every window's **root view**, so the whole app (not just secret fields) is readable only by real accessibility tools (TalkBack, Switch Access). No root detection (S25).
- Device must have a secure lock screen (S22). PIN change requires the current PIN (S24).
- Autofill: see §7.

## 7. Autofill
- **Apps:** matched by package name **+ signing-certificate SHA-256** pinned at link time (S13). A sideloaded app reusing a package name won't match.
- **Websites:** matched on registrable domain (eTLD+1) via a bundled Public Suffix List (S14). `webDomain` is trusted only from allowlisted browsers (package + cert); any other requester is treated as an app.
- **Linking:** manual "Remember for this app" from vault search; no Digital Asset Links, no bundled mapping (S15).
- **Ground rules (S16):** user must tap a suggestion; locked vault shows only "Unlock OffGrid Vault" (respects lockout); no-match shows only "Search vault…", never other entries.
- **Implementation (M7, `:autofill`):** `OffGridAutofillService` shares the app's one `VaultSession` (T3/T21). `ParsedRequest` reads only the system-provided `AssistStructure` (requester package = `activityComponent`, `webDomain` from view nodes); `SigningCerts` hashes the requester's **current** signing cert (rotation lineage ignored), and package visibility comes from a launcher-apps `<queries>` (S32) — unreadable cert → no matching, no linking. `Matcher`: browsers = bundled `browsers.txt` (S31, release certs only; publicly-keyed builds excluded); eTLD+1 via bundled official PSL (`PublicSuffixList`, passes the official test file). Locked: one response-level authentication suggestion, no entry names. Unlocked: up to 5 matching datasets (passwords decrypted from the session seal only for those) + "Search vault…" (dataset authentication). `AutofillActivity` (not exported, own task, excluded from recents, FLAG_SECURE) runs the same unlock/lockout path, then returns the response or the picked dataset; "Remember for this app" pins package + current cert (S15) with a normal save. Auth PendingIntents are explicit-component + `FLAG_MUTABLE` (the system must add `EXTRA_ASSIST_STRUCTURE`); the inline attribution intent is immutable. Each field carries the `webDomain` of its own document (iframes report their own); username and password from different documents are never filled together, and the request's domain is the fields' document, never the top page's.
- **Save (M8, P11):** responses carry `SaveInfo` (password required, username optional, save-on-all-views-invisible) only when the requester's identity is known (S32). After the user accepts Android's save UI, `onSaveRequest` writes nothing: it parks the typed login in-process (`PendingSaves`: random one-shot token, wiped after 5 min — the password never travels in an Intent) and opens `AutofillActivity`, which unlocks if needed (same lockout) and shows "save this login?" / "update password?". `SavePlan` only considers entries that match this requester (S13/S14): same non-empty username (case-insensitive) → update (old password → history, C15), identical password → nothing; any other or missing username → a new entry, with the requester's other accounts offered as an explicit "update this one instead" choice (P23) — allowlisted browser → website = host, title = eTLD+1; app (incl. fake "browsers") → linked package + current cert, no website.
