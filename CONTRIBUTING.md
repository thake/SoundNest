# Contributing to SoundNest

Thanks for helping make **SoundNest** safe, reliable, and enjoyable for families. Bug reports, parental-control improvements, accessibility enhancements, and translations are welcome.

## Build, test, and screenshots

Requirements: JDK 17+ (JDK 21 recommended) and the Android SDK (`compileSdk = 37`). The Android project lives in `base/`.

```bash
cd base
./gradlew assembleDebug
./gradlew :api:test :api-spotify:test :automix:test :engine:jvmTest \
          :engine-android:testDebugUnitTest :host-android:testDebugUnitTest \
          :host-cdp:test :app:testDebugUnitTest
```

If your change updates visible UI screens documented in `README.md`, regenerate the headless Roborazzi documentation screenshots:

```bash
cd base
./gradlew :app:generateDocScreenshots
```

Put your SDK path in `base/local.properties` (`sdk.dir=...`). Live browser-bridge tests (`:host-cdp:test`) run offline unit checks by default and can optionally connect to a signed-in desktop Chromium instance.

## Changes

- Develop changes in a **test-driven (TDD)** manner: add or update unit/Robolectric tests alongside every behavioral or parental-policy change (`@Config(sdk = [35])` for Robolectric tests in `:app`).
- Keep changes focused and match surrounding Kotlin style. Repository code, comments, default strings (`values/`), and docs are in English; translations go in `values-xx/`.

## Rules

- **Parental safety (fail closed):** new discovery surfaces, external content sources, or account/network actions must respect `ParentalPolicy`, `FeatureGates`, `ParentSecurityManager`, and `TrustedNetworkGate`.
- **Spotify & zero circumvention:** SoundNest talks to Spotify the way its Web Player does and supports both Spotify Free and Spotify Premium accounts honestly. No contribution may add ad-blocking, ad-muting, SponsorBlock, audio downloading/decryption, scraper extensions, or workarounds against Spotify's DRM/attestation/rate limits.
- **License (GPL-3.0):** SoundNest is free software under **GPL-3.0**, derived from [Orpheon / Myspoti](https://github.com/kota-zo/Orpheon). Your contribution is released under GPL-3.0. Any redistribution or fork must retain copyright notices, credit both **SoundNest** and **Orpheon / Myspoti**, and publish complete source code under GPL-3.0.
