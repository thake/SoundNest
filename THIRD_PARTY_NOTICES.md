# Third-party notices

**SoundNest** (`https://github.com/thake/soundnest`) is free software under the GNU General Public License v3.0 (see `LICENSE`).
It builds on the projects below; their licences are compatible with the GPL-3.0.

## Orpheon (formerly Myspoti) — GPL-3.0
https://github.com/kota-zo/Orpheon (formerly https://github.com/kota-zo/myspoti)

By `kota-zo` and contributors. SoundNest (`io.github.thake.soundnest.*`) is a parent-controlled fork of
Orpheon (`app.myspoti.*` / `com.music.spotui.*`), retaining all upstream copyright notices under the GPL-3.0.

## Beat This! — MIT
https://github.com/CPJKU/beat_this

By Francesco Foscarin, Jan Schlüter and Gerhard Widmer (CPJKU, Johannes Kepler University Linz),
"Beat This! Accurate beat tracking without DBN postprocessing" (ISMIR 2024). AutoMix ships the
`small0` checkpoint, exported to ONNX and quantized to int8 (`base/app/src/main/assets/automix/
beat-this-small0-int8.onnx`); upstream publishes the code and the weights under the MIT licence.
The log-mel front end in `base/automix/.../BeatThis.kt` follows its input contract. The training
recordings are not bundled.

## ONNX Runtime — MIT
https://github.com/microsoft/onnxruntime

Copyright Microsoft Corporation. `com.microsoft.onnxruntime:onnxruntime-android` 1.23.2 runs the
Beat This model on the phone.

## Spotui — GPL-3.0
https://github.com/Spotui/Spotui

Classic UI components in `io.github.thake.soundnest` derive from Spotui / Metrolist under the GPL-3.0.

## BitChord — GPL-3.0-or-later
https://github.com/kushagrasinghx/BitChord (commit `edc1e6f`)

By the BitChord contributors. The Immersive look is ported from it. These files in
`base/app/src/main/java/io/github/thake/soundnest/core/ui/look/` and
`base/app/src/main/java/io/github/thake/soundnest/feature/lyrics/ui/` are adapted from BitChord, stay under the
GPL-3.0-or-later, and say so in their header:

- `core/ui/look/MeshGradient.kt`, from `sharedUi/.../ui/player/MeshGradient.kt` (the mesh backdrop and `cyclingMeshPalette`)
- `core/ui/look/ThinSlider.kt`, from `sharedUi/.../ui/player/ThinSlider.kt` (the thin seek bar, without Automix)
- `feature/lyrics/ui/LyricClock.kt`, from `sharedUi/.../ui/player/LyricClock.kt` (the smoothed lyric playhead)
- `feature/lyrics/ui/SweptLyricLine.kt`, from `sharedUi/.../ui/player/PlayerLyrics.kt` (`SweptLyricLine`, its drawing
  helpers and constants) and `sharedUi/src/androidMain/.../ui/player/PlayerPlatformUi.android.kt`
- `feature/lyrics/ui/SweptLyricModel.kt`, from `shared/.../data/lyrics/LyricLine.kt` (word timing, lift and glow)

The Immersive UI across `base/app/src/main/java/io/github/thake/soundnest/core/ui/immersive/`,
`base/app/src/main/java/io/github/thake/soundnest/feature/*/ui/immersive/`, and
`base/app/src/main/java/io/github/thake/soundnest/feature/*/*/ui/immersive/` (player, lyrics, queue,
tab bar, top bars, sheets, motion, components, Home, Search, Library, Category, album/playlist, artist, and podcast pages) is
adapted from BitChord files, each named in the header of the file that carries it.

The local player's equalizer, `base/app/src/main/java/io/github/thake/soundnest/feature/equalizer/dsp/EqualizerProcessor.kt`, is
adapted from BitChord's `app/src/main/java/com/music/bitchord/playback/EqualizerProcessor.kt` (commit
`b690dc1`) under the GPL-3.0-or-later.

Code that BitChord itself carries from other projects, kept with its own header:
- `core/ui/immersive/shell/FloatingTabBar.kt`: compose-floating-tab-bar by Elyes Mansour
  (https://github.com/elyesmansour/compose-floating-tab-bar), Apache-2.0, through Echo-Music
  (https://github.com/EchoMusicApp/Echo-Music), GPL-3.0.
- `core/ui/immersive/shell/GlassSelectionPill.kt`: the pill motion follows liquid_glass_easy
  (https://pub.dev/packages/liquid_glass_easy), MIT License, Copyright (c) 2025 Ahmed Gamil.
- `core/ui/immersive/components/ImmersiveOverscroll.kt`: from Convx (https://github.com/cosmictaserdev-creator/Convx), GPL-3.0.
- `core/ui/immersive/shell/ImmersiveGlass.kt` (Liquid Glass): adapted from BitChord's `app/.../ui/components/LiquidGlass.kt`,
  itself adapted from Echo-Music's GlassEffectConfig / `Modifier.liquidGlass`
  (https://github.com/EchoMusicApp/Echo-Music), GPL-3.0.

## Backdrop — Apache-2.0
https://github.com/Kyant0/backdrop (v2.0.0, `io.github.kyant0:backdrop`), Copyright 2025 Kyant0.

The refraction, blur, rim highlight and shadow of the Immersive look's Liquid Glass. Vendored as
source in `base/app/src/main/java/io/github/thake/soundnest/core/ui/immersive/shell/backdrop/`, taken from
BitChord's vendored copy. Every file keeps its Apache-2.0 header. Licence text in
`base/licenses/Apache-2.0.txt`.

## Haze — Apache-2.0
https://github.com/chrisbanes/haze

The frosted bars of the Immersive look (Gradle dependency `dev.chrisbanes.haze:haze`).

## Reorderable — Apache-2.0

`sh.calvin.reorderable:reorderable` 3.1.0 (https://github.com/Calvin-LL/Reorderable), Copyright Calvin Liang,
used as a library (unmodified) for dragging the rows of the Immersive queue. Licence text in
`base/licenses/Apache-2.0.txt`.

## Inter — SIL Open Font License 1.1
https://github.com/rsms/inter

The Immersive look's typeface (`base/app/src/main/res/font/inter.ttf`), Copyright 2020 The Inter
Project Authors. Licence text in `base/licenses/Inter-OFL.txt`.

## Chromium `cast_channel.proto` — BSD-3-Clause
https://chromium.googlesource.com/chromium/src/+/main/components/cast_channel/proto/cast_channel.proto

Copyright The Chromium Authors. Casting to Google Cast devices
(`base/app/src/main/java/io/github/thake/soundnest/feature/speakers/cast/`) implements the CastMessage wire format defined by
this file; no Chromium source code is included.
