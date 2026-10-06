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
- **Unlock with biometric:** BiometricPrompt + CryptoObject on K_bio → DEK. If K_bio is invalidated (new fingerprint), fall back to PIN and re-create copy #2.
- **Recovery:** enter 12 words → HKDF → unwrap copy #3 (also needs K_device) → DEK → user sets new PIN → re-wrap copy #1 → reset counter. Words are unchanged.
- **After 3 wrong PINs:** copies #1 and #2 are disabled until recovery succeeds.
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
- Secrets (passwords, history) kept encrypted in memory with an ephemeral session key; decrypted only on reveal/copy/autofill.
- Reveal auto-masks after 20 s (timer restarts on each tap, S10) and immediately on backgrounding / screen-off. Clipboard cleared after 30 s or on vault lock, whichever first (S11) via `clearPrimaryClip()` without reading first; a persisted "pending clear" flag clears on next start if the process died (S18); mark clip as sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`).
- Tapjacking protection (`filterTouchesWhenObscured`) on sensitive views.
- PIN via in-app number pad; common PINs rejected (S19). Secret fields: no suggestions, `IME_FLAG_NO_PERSONALIZED_LEARNING`; app screens excluded from other autofill services (S20).
- `setAccessibilityDataSensitive(true)` on secret views (API 34+). No root detection (S25).
- Device must have a secure lock screen (S22). PIN change requires the current PIN (S24).
- Autofill: see §7.

## 7. Autofill
- **Apps:** matched by package name **+ signing-certificate SHA-256** pinned at link time (S13). A sideloaded app reusing a package name won't match.
- **Websites:** matched on registrable domain (eTLD+1) via a bundled Public Suffix List (S14). `webDomain` is trusted only from allowlisted browsers (package + cert); any other requester is treated as an app.
- **Linking:** manual "Remember for this app" from vault search; no Digital Asset Links, no bundled mapping (S15).
- **Ground rules (S16):** user must tap a suggestion; locked vault shows only "Unlock OffGrid Vault" (respects lockout); no-match shows only "Search vault…", never other entries.
