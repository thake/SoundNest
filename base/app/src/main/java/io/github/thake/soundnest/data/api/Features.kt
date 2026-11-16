package io.github.thake.soundnest.data.api

/** What the app shows: with a flag off, its rows and filters leave the UI. */
object Features {
    /** Podcasts and audiobooks (shows, episodes, Your Episodes, playback speed). */
    val PODCASTS: Boolean get() = io.github.thake.soundnest.parental.FeatureGates.canUsePodcasts()
}
