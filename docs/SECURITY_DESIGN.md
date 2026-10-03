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

## 4. Vault file format (v1, draft)
```
[magic "PVLT"] [format_version] [generation]
[argon2id params + salt] [recovery_salt] [wrapped_key_pin] [wrapped_key_bio?] [wrapped_key_recovery]
[nonce] [AES-256-GCM ciphertext of Protobuf Vault message] [GCM tag]
```
- The entire header is passed as **AEAD associated data** → any header tampering fails decryption.
- Payload: Protobuf (`Vault { repeated Entry }`, `Entry { id, title, username, urls[], linked_apps[{package, cert_sha256}], password, notes, history[≤5], created, updated }`). History is capped at 5 (C15) and can be cleared per entry.
- Older `format_version` → migrate on open and re-save.
- `generation` increments on every save; used only to order `vault.bin` vs `vault.prev` (C17).

## 5. Saving & rollback
- Write `vault.tmp` → fsync → atomic rename to `vault.bin` (Android `AtomicFile`). Keep previous good file as `vault.prev`.
- Any header change (PIN change, biometric add/remove, recovery reset, recalibration) is a normal full save (C16).
- **Open:** try `vault.bin`; only if it fails to decrypt/parse, try `vault.prev`; on success warn "Restored from previous save — your last change may be missing" and re-save immediately (C18).
- **Security-sensitive saves are written twice** so `vault.prev` holds no stale secret: PIN change, recovery reset, biometric removal, entry delete, clear history (C18).
- No separate rollback counter: file rollback needs root / FBE bypass, which is out of scope (C17). Residual: flash wear-levelling may retain old *encrypted* blocks.

## 6. Runtime protections
- Auto-lock after 5 minutes of **inactivity**, immediately on screen-off; switching apps does not lock (S23). Session key and DEK wiped from memory on lock — **best effort**: the JVM/ART may copy arrays and `SecretKeySpec` copies key bytes, so wiping can't be guaranteed (S25).
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
