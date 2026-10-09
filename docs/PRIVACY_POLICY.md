# OffGrid Vault — Privacy Policy

_Last updated: 2026-10-06_

OffGrid Vault is an offline password manager for Android, published by Kalyan (GitHub: mnvkalyansambhana-tech) as open
source (GPL-3.0): https://github.com/mnvkalyansambhana-tech/offgrid-vault

## The short version
**OffGrid Vault collects no data. It cannot: the app has no internet permission.**

## What the app stores
Everything you put in the vault (logins, passwords, notes) is encrypted (AES-256-GCM) and stored
only in the app's private storage on your phone. The keys that protect it are bound to your phone's
secure hardware (Android Keystore) and your PIN. Your recovery words are shown once and never stored.

## What leaves your phone
Nothing. The app does not request Android's `INTERNET` permission, so it cannot connect to any
server — ours or anyone else's. There are no accounts, analytics, crash reports, ads or trackers.
The vault is excluded from Android cloud backup and device-to-device transfer. Uninstalling the app
deletes the vault.

## Permissions
- **Use biometric hardware** — only for the optional fingerprint unlock and to confirm erasing the
  vault with your phone's screen lock. Fingerprint data never reaches the app; Android only tells it
  whether the check succeeded.
- **Seeing which apps are installed (launcher apps only)** — the autofill feature reads the name and
  signing certificate of the app you are logging into, on your phone, so that look-alike apps can't
  receive your passwords. This information is not stored, except the app's identity on a login you
  explicitly link with "Remember for this app".

## Autofill
If you choose OffGrid Vault as your autofill service, Android shows it the login fields of the app
or website you are using. It only reads what it needs to suggest or save a login, entirely on your
phone, and it never fills anything without your tap.

## Children
The app is not directed at children.

## Changes and contact
Changes to this policy are published at this address and in the repository history. Questions:
open an issue on the GitHub repository.
