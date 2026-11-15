# Orpheon Kids

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

**Orpheon Kids** (`github.com/thake/Orpheon-kids`) is an Android parent-controlled Spotify Premium client for children and teens (ages 9–16), forked from **[Orpheon](https://github.com/kota-zo/Orpheon)** (formerly Myspoti) under the **GNU General Public License v3.0 (GPL-3.0)**.

## Key Principles & Scope

- **Spotify Premium Required**: Requires an active Spotify Premium (Individual, Duo, or Family) subscription. Free ad-supported accounts are not supported.
- **No Ad-Blocking, Extensions, or Stream Downloaders**: All ad-blocking, SponsorBlock, third-party script extensions, alternative catalog scrapers, and stream downloaders have been removed.
- **Parental Controls**:
  - PIN / biometric-protected Parent Settings with fail-closed setup.
  - Configurable content filtering (*Filtered Open Search* blocking explicit tracks, keywords/regexes, and banned items, or *Strict Allowlist* mode).
  - Granular front-page (`Home`) section and shelf visibility controls.
  - Feature gates for Podcasts/Audiobooks, Live Radio, Local Files, Lyrics/Genius meanings, Autoplay/Radio, Jam, and Read-Only Spotify Library protection.
  - Trusted Home Wi-Fi gating for Spotify Connect / Alexa / LAN speakers, plus Chromecast support and a built-in Sleep Timer.

## Building & Testing

Requirements: JDK 21 and Android SDK (`compileSdk = 37`).

```bash
cd base
./gradlew :api-spotify:test :engine:jvmTest :host-android:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Disclaimer

Not affiliated with or endorsed by Spotify. Spotify is a trademark of Spotify AB.

## Credits & GPL-3.0 Attribution

- Derived from **[Orpheon / Myspoti](https://github.com/kota-zo/Orpheon)** by `kota-zo` and contributors (GPL-3.0).
- Immersive UI and equalizer adapted from **[BitChord](https://github.com/kushagrasinghx/BitChord)** by `kushagrasinghx` and contributors (GPL-3.0-or-later).
- Classic UI roots from **[Spotui](https://github.com/Spotui/Spotui)** / **[Metrolist](https://github.com/MetrolistGroup/Metrolist)** (GPL-3.0).
- Full third-party notices are maintained in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## License

Free software licensed under the **GNU General Public License v3.0** — see [`LICENSE`](LICENSE).
