package io.github.thake.soundnest.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import io.github.thake.soundnest.data.api.InviteLinks

/**
 * Descriptions (show, episode, audiobook, artist bio, playlist) with their links made tappable.
 * Same text, same layout: the [AnnotatedString] goes to the page's own `Text`, so lines, "more"
 * toggles and truncation stay as they were. A Spotify link (open.spotify.com/…, spotify:…) to a
 * playlist, album, artist or show opens its page in the app; any other link opens in the browser.
 */
private val LINK = Regex(
    """(?:https?://|www\.)[^\s<>"']+|spotify:(?:playlist|album|artist|show|track|episode):[A-Za-z0-9]{22}""",
    RegexOption.IGNORE_CASE,
)
private val PAGE_LINK = Regex(
    """(?:open|www|play)\.spotify\.com/(?:intl-[a-z-]+/)?(playlist|album|artist|show)/([A-Za-z0-9]{22})""",
    RegexOption.IGNORE_CASE,
)
private val URI = Regex("""spotify:(playlist|album|artist|show|track|episode):([A-Za-z0-9]{22})""", RegexOption.IGNORE_CASE)

/** [text] with every link in it tappable, in [linkColor] and underlined. */
@Composable
fun rememberLinkedText(text: String, linkColor: Color): AnnotatedString {
    val context = LocalContext.current
    return remember(text, linkColor) {
        if (!LINK.containsMatchIn(text)) return@remember AnnotatedString(text)
        val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            var last = 0
            for (hit in LINK.findAll(text)) {
                // Sentence punctuation after a link is not part of it.
                val link = hit.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
                append(text.substring(last, hit.range.first))
                withLink(LinkAnnotation.Clickable("link", styles) { openDescriptionLink(context, link) }) { append(link) }
                last = hit.range.first + link.length
            }
            append(text.substring(last))
        }
    }
}

/** A tapped link: Spotify links go through [io.github.thake.soundnest.parental.SpotifyLinkGuard]; external URLs open in the OS browser (Google Family Link). */
fun openDescriptionLink(context: android.content.Context, link: String) {
    val policy = io.github.thake.soundnest.parental.ParentalPolicyStore.current(context)
    when (val decision = io.github.thake.soundnest.parental.SpotifyLinkGuard.evaluate(link, policy)) {
        is io.github.thake.soundnest.parental.LinkDecision.ExternalBrowser -> {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(decision.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        is io.github.thake.soundnest.parental.LinkDecision.OpenInAppPage -> {
            InviteLinks.page.value = decision.spotifyUri
        }
        is io.github.thake.soundnest.parental.LinkDecision.PlaySpotifyTrack -> {
            InviteLinks.handle(link)
        }
        is io.github.thake.soundnest.parental.LinkDecision.JoinCollaborativeInvite,
        is io.github.thake.soundnest.parental.LinkDecision.ResolveShortLink -> {
            InviteLinks.handle(link)
        }
        is io.github.thake.soundnest.parental.LinkDecision.Blocked -> Unit
    }
}
