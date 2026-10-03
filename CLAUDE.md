# CLAUDE.md — context for Claude Code

## Project
Offline, device-bound **Android** password manager. No backend, no network, no cloud. Owner: Kalyan (senior backend engineer; ex-PayPay OAuth/IAM, JPMorgan, BrowserStack).

Read before doing anything:
1. `docs/DECISIONS.md` — the source of truth for what has been decided.
2. `docs/SECURITY_DESIGN.md` — threat model and crypto design.
3. `docs/OPEN_QUESTIONS.md` — what is still undecided.

## Working agreement (important)
- **Kalyan makes all product and architecture decisions.** Claude **implements milestones** (from 2026-10-02, at Kalyan's request); Kalyan reviews and approves.
- Claude stays a **sparring partner**: explain trade-offs with pros/cons, push back on risky choices, and stop to ask when an implementation needs a decision not in `DECISIONS.md`.
- Never silently change a decision. If something in `DECISIONS.md` looks wrong, raise it and let Kalyan decide.
- When Kalyan makes a new decision, use the `decision-log` skill to record it.
- Run the `security-review` skill on all code touching crypto, keys, storage, unlock or autofill — **including Claude's own code** — before handing it over.
- Explain trade-offs one concept at a time, in plain language, with a short table of options.
- Commit/push only when Kalyan asks.

## Hard constraints (never violate)
- No `INTERNET` permission. Verify the **merged** manifest after adding any dependency.
- `android:allowBackup="false"` and data-extraction rules that exclude everything (no Google cloud/device-transfer backup).
- `FLAG_SECURE` on all windows (no screenshots, screen recording, casting).
- No custom crypto primitives. Use vetted libraries (Google Tink / libsodium-Lazysodium / Bouncy Castle).
- Never log secrets, PINs, recovery words, keys or vault contents. No analytics, no crash reporting that ships data off-device.
- No donation/payment links or screens inside the app (Google Play payments policy).

## Tech stack (decided)
- Application ID: `io.github.mnvkalyansambhana.offgridvault` (P16) — **permanent, never change**.
- Android only, Kotlin, **minSdk 30 (Android 11)** — confirmed (C12): single Keystore auth code path (`setUserAuthenticationParameters`) + inline autofill suggestions.
- KDF: **Argon2id** via **Lazysodium-android** (C13; Argon2id only). Cipher: **AES-256-GCM** (AEAD). Serialization: **Protobuf**.
- Android Keystore (StrongBox when available, not required).
- UI: Jetpack Compose, single Activity, MVVM + StateFlow, manual DI, Coroutines (T1–T6). Protobuf via **Wire**; software AEAD/HKDF via **Tink**; JCA only for Keystore keys (T7–T8). Full list: `docs/DECISIONS.md` → Tech stack.
- Dev plan: `docs/ROADMAP.md` (milestones M0–M9).
- UI: **locked** OffGrid Pop design (P15) — spec `docs/DESIGN_SYSTEM.md`, screens `design/screens/pop/`. Flag any UI code that drifts from it.
