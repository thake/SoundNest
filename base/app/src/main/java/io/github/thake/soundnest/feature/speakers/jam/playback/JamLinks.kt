/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.jam.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Jam invite codes: `open.spotify.com/socialsession/<token>`, or a spotify.link short link to it. */
object JamLinks {
    private val http = OkHttpClient.Builder().followRedirects(true).build()

    suspend fun token(raw: String): String? {
        val url = if (raw.contains("spotify.link") || raw.contains("spoti.fi")) resolve(raw) ?: raw else raw
        Regex("socialsession/([A-Za-z0-9_-]+)").find(url)?.let { return it.groupValues[1] }
        Regex("spotify:socialsession:([A-Za-z0-9_-]+)").find(url)?.let { return it.groupValues[1] }
        return null
    }

    /** Where a short link leads (its final URL after redirects). */
    private suspend fun resolve(link: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(link).header("user-agent", "Mozilla/5.0").build()).execute().use { r ->
                val final = r.request.url.toString()
                // Some short links answer with a page that holds the target in a meta refresh / link.
                if (final.contains("socialsession")) final
                else Regex("https://open\\.spotify\\.com/socialsession/[A-Za-z0-9_-]+").find(r.body.string())?.value ?: final
            }
        }.getOrNull()
    }
}
