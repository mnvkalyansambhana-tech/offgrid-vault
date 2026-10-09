# Release — signing, Play closed test, production (M9)

Order per **P21**: all milestones first, then the closed test (14 days), then production.

## 1. Upload key (once, kept forever, never committed)
Play App Signing holds the real app-signing key; you sign uploads with an **upload key**.
```bash
keytool -genkeypair -v -keystore ~/keys/offgridvault-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```
Back up the `.jks` and its passwords offline (e.g. password manager on another device + paper).
Losing the upload key is recoverable (Play support can reset it); leaking it is not good — keep it out of the repo.

Create `keystore.properties` at the repo root (git-ignored):
```properties
storeFile=/home/<you>/keys/offgridvault-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```
Without this file `assembleRelease`/`bundleRelease` still build, just unsigned (that's what CI does).

## 2. Build the upload bundle
```bash
./gradlew check bundleRelease --no-configuration-cache
# → app/build/outputs/bundle/release/app-release.aab
```
Before every upload: bump `versionCode` (and `versionName`) in `app/build.gradle.kts`, refresh the PSL
(`curl -o autofill/src/main/resources/.../public_suffix_list.dat https://publicsuffix.org/list/public_suffix_list.dat`)
and the browser list (`python3 tools/update_browsers.py`), review both diffs, run the M3–M8 device checklists.

## 3. Play Console — closed test (personal account, P13)
1. **Create app** → name "OffGrid Vault", app, free. Package comes from the first upload (`io.github.mnvkalyansambhana.offgridvault`, P16 — permanent).
2. **App content**
   - Privacy policy URL → publish `docs/PRIVACY_POLICY.md` on `mnvkalyansambhana-tech.github.io` (P16) and paste the URL.
   - **Data safety** → "Does your app collect or share any of the required user data types?" **No**. (Nothing leaves the device; no INTERNET permission.)
   - Ads: **No**. App access: **all functionality available without special access**. Target audience: 18+ (not designed for children). Content rating questionnaire: utility, no user-generated content shared.
   - No donation/payment links anywhere in the listing or app (P3).
3. **Store listing** → texts in `docs/PLAY_LISTING.md`; screenshots need debug builds with dummy data (FLAG_SECURE blocks screenshots in the real app — use an emulator build with a temporary screenshot-allowed flag **never committed**, or a design mock).
4. **Testing → Closed testing** → create track → upload the `.aab` → add testers (email list or Google Group). Personal accounts need **at least 12 opted-in testers for 14 continuous days** (check the current numbers in Play Console). Testers must keep it installed and opted in.
5. After 14 days → **Apply for production** (Play asks about the test). Then roll out to production.

## 4. Before production (M9 exit)
- [ ] Full manual test script (`docs/DEV_SETUP.md` M3–M8 checklists) on an **Android 11 cheap phone** and the Pixel 9a.
- [ ] S21 decision applied from the Keystore lab result.
- [ ] Trademark / Play name check for "OffGrid" (P8).
- [ ] `security-review` skill pass on the release commit.
- [ ] Merged release manifest: no `INTERNET` (the build enforces it).
