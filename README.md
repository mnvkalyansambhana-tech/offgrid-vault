# OffGrid Vault

An Android password manager that **never connects to the internet**. Your vault lives only on your phone, encrypted with modern cryptography. No accounts, no cloud, no sync, no tracking.

> Status: **design phase** — architecture decided, implementation not started.

## Principles
- **Device-bound.** Data never leaves the device. Uninstalling the app deletes the vault permanently.
- **No network.** The app does not request the `INTERNET` permission — anyone can verify this from the Play Store listing.
- **Free.** All features free. Optional donations via a static developer website only (never inside the app).
- **Honest security.** We promise only what we protect. See [docs/SECURITY_DESIGN.md](docs/SECURITY_DESIGN.md).

## Docs
| File | What's in it |
|---|---|
| [docs/DECISIONS.md](docs/DECISIONS.md) | Every product/architecture decision and why |
| [docs/SECURITY_DESIGN.md](docs/SECURITY_DESIGN.md) | Threat model, key hierarchy, crypto, file format, unlock & lockout |
| [docs/OPEN_QUESTIONS.md](docs/OPEN_QUESTIONS.md) | Decisions still pending |
| [docs/UX_COPY.md](docs/UX_COPY.md) | Approved user-facing text |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestone-based development plan |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Locked UI design system (tokens, type, components) |
| [design/](design/) | Versioned UI mockups (OffGrid Pop screens) |
| [CLAUDE.md](CLAUDE.md) | Context and working agreement for Claude Code |

## Licence
GPL-3.0 (see P13 in [docs/DECISIONS.md](docs/DECISIONS.md)).
