package io.github.thake.soundnest.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.thake.soundnest.ui.look.rememberReduceMotion

/**
 * Scrolls a one-line title that is wider than its room instead of cutting it with "…": a pause
 * at the start, then a gentle crawl. For the main titles of headers, bars and players, never
 * for the lines of a list.
 *
 * The line keeps the size it had with the ellipsis (the marquee measures the text unbounded but
 * reports the width it was given, and clips inside), so put it AFTER any `clickable` or padding
 * the title already had: the touch target does not grow, shrink or push its neighbours.
 * With "Remove animations" nothing scrolls and the ellipsis stays.
 *
 * @param iterations how many passes before it rests at the start; the players that stay up
 * for the whole song pass [Int.MAX_VALUE], a mini player or a bar scrolls once or twice per title.
 */
@Composable
fun Modifier.titleMarquee(enabled: Boolean = true, iterations: Int = 2): Modifier =
    if (!enabled || rememberReduceMotion()) this
    else this.basicMarquee(iterations = iterations, repeatDelayMillis = 2_800, initialDelayMillis = 2_800, velocity = 26.dp)

/** Softens the right edge of an overflowing marquee line so glyphs fade out instead of slicing mid-character. */
fun Modifier.overflowRightFade(enabled: Boolean): Modifier =
    if (!enabled) this
    else this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fade = 16.dp.toPx().coerceAtMost(size.width * 0.2f)
            if (fade > 1f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
