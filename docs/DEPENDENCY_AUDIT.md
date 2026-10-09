# Dependency audit (M9)

Re-run before every release: `python3 tools/osv_audit.py` (checks every artifact in the release APK's
`releaseRuntimeClasspath`, from `app/gradle.lockfile`, against [OSV](https://osv.dev)). Every artifact is
also SHA-256 pinned in `gradle/verification-metadata.xml` and version-locked (T13).

## 2026-10-09 — commit `107932c`
**111 release runtime artifacts, 0 known advisories.**

What actually ships (direct dependencies and the security-relevant ones):

| Library | Version | Role | Notes |
|---|---|---|---|
| `com.google.crypto.tink:tink-android` | 1.23.0 | AES-256-GCM on DEK/KEK, HKDF (T8) | Google-maintained |
| `com.goterl:lazysodium-android` + `net.java.dev.jna:jna` (AAR) | 5.2.0 + 5.19.1 | Argon2id via libsodium (C13) | Native libs 16 KB aligned (build check). Watch maintenance cadence (C13: fallback argon2kt) |
| `com.squareup.wire:wire-runtime` + `okio` | 7.1.0 + 3.18.2 | Protobuf vault format (T7) | No `protobuf-java` in the APK (only build tooling) |
| `androidx.autofill:autofill` | 1.3.0 | Inline autofill suggestions (C12, T20) | Added M7 |
| Compose foundation / navigation / lifecycle / activity / core | BOM 2026.09.00, nav 2.10.2, lifecycle 2.11.0 | UI (T1–T6) | No Material |
| `kotlin-stdlib` / `kotlinx-coroutines` | 2.4.20 / 1.11.0 | Language/runtime | |

Not shipped (build/test only): Bouncy Castle and lazysodium-java (JVM tests), protobuf-java, older
kotlin-stdlib versions, jna-platform (lint/AGP tooling).

Permissions in the merged release manifest: `USE_BIOMETRIC` and AndroidX's
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (app-internal signature permission). **No `INTERNET`** (enforced
by `checkReleaseNoInternet`).
