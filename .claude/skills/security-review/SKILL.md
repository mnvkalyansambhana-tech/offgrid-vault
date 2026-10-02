---
name: security-review
description: Review code or designs in the offgrid-vault project that touch crypto, keys, Keystore, unlock/PIN/biometric, recovery words, vault storage, memory handling, clipboard, autofill, manifest or dependencies. Use whenever Kalyan shares such code or asks for a security check.
---

# Security review for offgrid-vault

You are reviewing, not rewriting. Give findings as comments with severity (Critical / High / Medium / Low / Nit) and a short "why". Only provide replacement code if Kalyan asks.

Always re-read `docs/DECISIONS.md` and `docs/SECURITY_DESIGN.md` first and flag any code that contradicts a recorded decision.

## Checklist

### Platform / manifest
- [ ] Merged manifest has **no `android.permission.INTERNET`** (check `app/build/intermediates/merged_manifest/...` after adding any dependency).
- [ ] `android:allowBackup="false"`, `dataExtractionRules` exclude all domains for cloud-backup and device-transfer.
- [ ] `FLAG_SECURE` set on every Activity/window, including dialogs and the recovery-words screen.
- [ ] Every Compose `Dialog`/`Popup`/bottom sheet uses `SecureFlagPolicy.SecureOn` (T2).
- [ ] `INTERNET` has `tools:node="remove"` and the merged-manifest check task passes (T13); dependency verification metadata updated for any new dependency.
- [ ] minSdk 30.

### Crypto
- [ ] No hand-rolled primitives; vetted library only.
- [ ] Software AEAD/HKDF via Tink (NO_PREFIX); JCA only for AndroidKeyStore keys (T8).
- [ ] Protobuf secret fields are `bytes` + `wire.redacted` (T7).
- [ ] AES-256-GCM with a **fresh random 96-bit nonce per encryption**; nonce never reused with the same key.
- [ ] Header passed as **associated data** to every vault encryption/decryption.
- [ ] Argon2id params read from header; salt random per vault (and refreshed when PIN changes).
- [ ] Recovery words → HKDF (not stored anywhere on device).
- [ ] Constant-time comparisons for any MAC/secret comparison.
- [ ] Use `SecureRandom` only.

### Keys & Keystore
- [ ] DEK random 256-bit, never written unwrapped.
- [ ] PIN copy and recovery copy are additionally wrapped by device-bound `K_device` (no user-auth requirement).
- [ ] Biometric key `K_bio`: `setUserAuthenticationRequired(true)`, BIOMETRIC_STRONG, `setInvalidatedByBiometricEnrollment(true)`; used via `BiometricPrompt.CryptoObject` (not a UI-only success callback).
- [ ] `KeyPermanentlyInvalidatedException` handled → fall back to PIN, re-create K_bio.
- [ ] StrongBox requested when available with graceful fallback.
- [ ] `setUnlockedDeviceRequired(true)` on K_device/K_bio only if screen-lock change/removal tests passed (S21).
- [ ] Setup blocked without secure lock screen (S22).

### Unlock & lockout
- [ ] Attempt counter incremented **and persisted before** PIN verification.
- [ ] After 3 failures: PIN **and** biometric blocked until recovery succeeds.
- [ ] Common-PIN blocklist + pattern checks on set/change (S19); PIN change requires current PIN, failures count (S24).
- [ ] Session: 5 min inactivity + screen-off lock (S23).
- [ ] Counter written synchronously (`AtomicFile`+fsync or `commit()`), **never `apply()`**, before verification (S17).

### Storage
- [ ] Writes via `AtomicFile` (tmp → fsync → rename); `vault.prev` kept.
- [ ] Header changes go through a full save (C16); `generation` increments every save (C17).
- [ ] `vault.prev` used only if `vault.bin` fails to decrypt/parse; user warned, immediate re-save (C18).
- [ ] Security-sensitive saves (PIN change, recovery reset, biometric removal, delete, clear history) written twice (C18).
- [ ] No plaintext temp files, caches, or logs.

### Memory & UI
- [ ] Secrets stored as `ByteArray`/`CharArray` and zeroed after use (avoid `String` for secrets where possible).
- [ ] Secrets double-encrypted in memory with an ephemeral session key; decrypted only on reveal/copy/autofill.
- [ ] Session lock after 5 min and on screen-off; DEK + session key wiped.
- [ ] Reveal auto-re-masks; clipboard cleared after timeout / on lock without reading it, marked sensitive, pending-clear flag survives process death (S18).
- [ ] `filterTouchesWhenObscured` on sensitive inputs/buttons.
- [ ] In-app PIN pad; secret fields `textNoSuggestions` + `IME_FLAG_NO_PERSONALIZED_LEARNING`; screens `importantForAutofill=noExcludeDescendants` (S20).
- [ ] `setAccessibilityDataSensitive(true)` on secret views, API 34+ (S25).
- [ ] Password generator: `SecureRandom`, rejection sampling, never auto-copies (P10).
- [ ] No secrets in logs, exceptions, crash reports, analytics, notifications, or `toString()`.

### Autofill
- [ ] Apps matched by package name **+ signing-cert SHA-256** (S13); no package-name-only match.
- [ ] Web matched on eTLD+1 via bundled PSL; `webDomain` trusted only from allowlisted browsers (S14).
- [ ] App↔site links only via explicit user "Remember for this app" (S15); no network lookups.
- [ ] Never fill without user tap; locked → only "Unlock" suggestion respecting lockout; no-match → only "Search vault…" (S16).
- [ ] Save flow (P11): user-confirmed, requires unlock, links per S13/S14, old password → history.
