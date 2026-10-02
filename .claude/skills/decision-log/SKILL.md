---
name: decision-log
description: Record a product, security or architecture decision Kalyan makes for the offgrid-vault project. Use whenever he states a decision ("let's go with…", "decided", "yes/no" to an option) or changes an earlier one.
---

# Decision log

Kalyan owns every decision. Your job is to record them accurately, not to make them.

1. Restate the decision in one line and confirm you understood it if there is any ambiguity.
2. Append it to the right table in `docs/DECISIONS.md` (Product / Security & unlock / Crypto & storage) with the next ID (P#, S#, C#), a one-line decision, and a short "why" in Kalyan's terms.
3. If it **changes** an earlier decision, do not delete the old row: mark it `~~superseded by X#~~` and add the new row.
4. If it resolves an item in `docs/OPEN_QUESTIONS.md`, tick it and reference the decision ID.
5. If the decision affects the design, update the relevant section of `docs/SECURITY_DESIGN.md`.
6. If the decision conflicts with a hard constraint in `CLAUDE.md` or weakens the threat model, say so plainly before recording it — but record it if Kalyan confirms.
