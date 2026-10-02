# design/

Version-controlled UI mockups for OffGrid Vault.

| Path | What |
|---|---|
| `screens/pop/` | **Locked** UI — 20 screens in the OffGrid Pop style (P15, T15). Source of truth for implementation |
| `screens/wireframes/` | Earlier grey wireframes — kept for history, superseded by `pop/` |
| `canvas.json` | Layout index of the design canvas (both pages) |
| `../docs/DESIGN_SYSTEM.md` | Tokens, typography, components — what Compose code should implement |

## Viewing / editing
- Live canvas (private to Kalyan): https://claude.ai/artifact/HTSf8uK3je6QF5WxG49xCD
- The `.dc.html` files are the canvas's source format; they need the canvas runtime (`support.js`) to render, so view them on the canvas, not by opening locally.

## Workflow
1. Change the design on the canvas (or ask Claude to).
2. Copy the changed `.dc.html` files (and `canvas.json` if the layout changed) back into this folder.
3. If tokens, components or the locked screen list change, record it via `decision-log` and update `docs/DESIGN_SYSTEM.md`.
4. Commit design changes separately from code (`design: …`).
