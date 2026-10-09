# UX copy

Approved user-facing text. Changes here are product decisions (record in DECISIONS.md).

## Recovery words setup (P9)

Flow: Screen 1 → 2 → 3 → 4. Every screen uses FLAG_SECURE.

**Screen 1 — before showing words**
> **Your recovery words**
> On the next screen you'll see 12 words. They're the only way back in if you forget your PIN.
> • Write them on paper. Screenshots are blocked.
> • Keep them somewhere safe and private.
> • You'll see them **only once**. They never change.

**Screen 2 — the words** (no copy button)
> 1. ____ 2. ____ … 12. ____
> [ I've written them down ]

**Screen 3 — type-back check** (3 randomly chosen positions)
> Enter words #3, #7 and #11 to confirm.

**Screen 4 — what they can and can't do** (shown after confirmation; completes setup)
> **Know this before you continue**
> ✓ They unlock your vault on **this phone** if you forget your PIN.
> ✗ They **won't** restore your passwords on a new phone or after uninstalling. Your vault exists only here.
> ⚠ Anyone with **this phone and these words** can open your vault. Never share them.
> OffGrid Vault will **never** ask you for these words. There's no account, no support login, nothing online.
> [ I understand ]

## Settings & About (P20, M5–M7)
- Security: **recovery words** · `Not set up` (amber) / `Set up` (green); below when not set up: "Without recovery words, forgetting your PIN locks this vault for good."
- **unlock with fingerprint** — "turned off after 3 wrong PINs" · `On` / `Off` (hidden on phones without a strong biometric)
- **change PIN** · **lock now**
- Autofill: **fill passwords in other apps** — "OffGrid Vault is your autofill service" / "choose OffGrid Vault in Android settings" · `On` / `Off`
- App: **about & privacy** → Privacy: "No internet permission — OffGrid Vault can't connect to the internet at all. Check the "Permissions" section of its Play Store listing — internet access isn't there." · "No data collected — No account, no analytics, no crash reports, no ads." · "Stays on this phone — Your vault is encrypted and stored only here. No cloud, no backup, no sync." · "Uninstall = erased — Removing the app deletes the vault for good." Open source: "GNU GPL v3.0", repo address. App: version.

## Fingerprint unlock (M6)
- Turn on: "confirm your pin." / "only you can turn on fingerprint unlock" → "touch the sensor." → body: "Any fingerprint enrolled on this phone will open the vault. Adding a new fingerprint later turns fingerprint unlock off until you turn it on again here. After 3 wrong PINs it stops working until you use your recovery words." · [SCAN FINGERPRINT] · failure: "That didn't work. Make sure a fingerprint is set up in your phone's settings, then try again."
- System prompt: "Unlock OffGrid Vault" / "Use your fingerprint" · negative button "Use PIN". Turning on: "Turn on fingerprint unlock" / "Touch the sensor to confirm".
- Unlock notices (amber): "A fingerprint was added to this phone, so fingerprint unlock was turned off. Unlock with your PIN, then turn it back on in Settings." · "Fingerprint unlock stopped working and was turned off. Unlock with your PIN."

## Autofill (M7, S16)
- Suggestions: **Unlock OffGrid Vault** (locked) · entry title + username (matches) · **Search vault…**
- Locked out: "vault locked." · "After 3 wrong PINs, only your recovery words can unlock the vault. Open OffGrid Vault to use them." · [OPEN OFFGRID VAULT] · Cancel
- Search: "fill [app name]." · subtitle: website host / "[package] · no linked login yet" / "[package] · can't verify this app" · "Search vault" · **Remember for this app** — "links this login to the app's name and signature, so look-alike apps won't match." · [FILL]

## Save login (M8, P11, P23)
- Headline: "save this login?" / "update password?" / "already saved." · label "OffGrid Vault · Autofill"
- Card: app name or website · username or "(no username)" · ••••••••••
- Body: "Saved as a new login." / "Saved as a new login next to your other accounts here." / "Updates "[title]" ([username]). The old password moves to its history." / ""[title]" already has this password."
- Picker: "Or update a saved account" (nothing selected by default)
- Buttons: Not now · [SAVE] / [UPDATE] / [DONE]

## Vault list (P22)
- Search icon → field "Search title, username or website"; icon becomes ✕ ("Close search").

