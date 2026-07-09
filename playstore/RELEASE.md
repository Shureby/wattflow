# Play Store Release Guide

## One-time Console setup (needs developer account)

1. [Play Console](https://play.google.com/console) → Create app
   - Name: WattFlow: Real Charging Power · free app with in-app purchases
2. Monetize → In-app products → Create: product ID **`wattflow_pro`**,
   one-time, price ~US$2.99 (must match `PRODUCT_ID` in
   `app/src/play/java/com/ezyapp/wattflow/ProGateFactory.kt`)
3. Store listing: copy from `listing/en-US.md` and `listing/zh-CN.md`
4. Graphics: `assets/icon-512.png`, `assets/feature-graphic-1024x500.png`,
   plus at least 2 phone screenshots (16:9 or 9:16, 320–3840 px)
5. Privacy policy URL: `https://shureby.github.io/wattflow/privacy.html`
6. Data safety: **no data collected, no data shared** (only Play Billing,
   handled by Google)
7. App content: not a kids app; contains no ads

## Every release

1. Bump `versionCode` + `versionName` in `app/build.gradle.kts`
2. Update `CHANGELOG.md` + "What's New" in `README.md` / `README.zh-CN.md`
3. Build:
   ```
   ./gradlew bundlePlayRelease      # → app/build/outputs/bundle/playRelease/app-play-release.aab
   ./gradlew assembleFossRelease    # → GitHub Release APK
   ```
4. Upload the AAB to Play Console → Production (or a testing track first)
5. Tag `vX.Y`, create GitHub Release with the FOSS APK

## Signing

`playstore/private/` (gitignored) holds:

- `wattflow-release.keystore` — release signing key (PKCS12; key password
  = store password)
- `keystore.properties` — read by `app/build.gradle.kts`

**Back both up outside this machine.** Losing them means losing the ability
to update the app on Play. If they're ever leaked, the key cannot be
rotated for an existing listing (unless enrolled in Play App Signing —
recommended: let Play manage the app signing key at first upload, keeping
this keystore as the upload key only).
