package io.github.thake.soundnest.core.catalog

/** What the app shows: with a flag off, its rows and filters leave the UI. */
object Features {
    /** Podcasts (shows, episodes, Your Episodes, playback speed). */
    val PODCASTS: Boolean get() = io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUsePodcasts()

    /** Audiobooks (Spotify audiobooks and parent-approved Audible audiobooks). */
    val AUDIOBOOKS: Boolean get() = io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseAudiobooks()
}
