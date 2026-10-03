# Open questions

- [x] **Minimum Android version** — Android 11 (API 30) → decided, see **C12**. Minimum RAM still follows the Argon2id benchmark.

- [x] **Argon2id parameters** — on-device calibration → decided, see **C14**.
  - [x] Pixel 9a (Android 16, 2026-10-03): t=2 → 87 ms, ~37 ms per extra pass at 64 MiB; calibrated t=16 (cap) ≈ 0.6 s. On-device vectors 3/3 pass.
  - [ ] Still to do: same benchmark on a **cheap Android 11** phone (floor case: confirm t ≥ 2 fits ~1 s and the 64 MiB native alloc is safe); set Play Console minimum RAM only if needed.
- [x] **Argon2id library** — Lazysodium-android → decided, see **C13**.
- [x] **Password history depth** — 5 + "Clear history" → decided, see **C15**.
- [x] **Reveal auto-mask timeout** — 20 s, restart on tap → decided, see **S10**.
- [x] **Clipboard clear timeout** — 30 s + clear on lock → decided, see **S11**.
- [x] **Confirm:** successful unlock (PIN or biometric) resets the wrong-PIN counter → yes, with failed-attempts notice, see **S12**.
- [x] **Recovery words count** — 12 confirmed, see **S5**.
- [x] **App name** — OffGrid Vault → decided, see **P8**.
  - [ ] Check Play Store / trademark clashes for "OffGrid" before listing.
- [x] **Autofill matching rules** → decided, see **S13–S16**.
- [x] Setup-screen copy for recovery words → decided, see **P9** / `docs/UX_COPY.md`.
