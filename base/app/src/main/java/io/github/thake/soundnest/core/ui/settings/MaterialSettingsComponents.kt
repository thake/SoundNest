/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppDestructive
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.motion.ImmersiveMotion
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePageFrame
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSegmentedControl
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveLargeTitle
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveTopBar
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveChromeScroll
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveHazeSource
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveTopContentPadding
import io.github.thake.soundnest.core.ui.look.ImmersiveSans
import io.github.thake.soundnest.core.ui.look.bottomBarsSpace
import io.github.thake.soundnest.core.ui.look.rememberReduceMotion
import io.github.thake.soundnest.core.ui.navigation.isImmersive

private val CardShape = RoundedCornerShape(20.dp)
private val IconBadgeShape = RoundedCornerShape(10.dp)
private val BannerShape = RoundedCornerShape(16.dp)
private val MaxSettingsWidth = 680.dp

/**
 * Look-adaptive Material Design 3 tokens shared by all Settings, Parental Controls, Equalizer,
 * Hidden Songs, Accounts, and About screens so Classic and Immersive share the same hierarchy
 * while keeping each look's palette, font, and chrome.
 */
@Immutable
data class M3SettingsPalette(
    val immersive: Boolean,
    val pageBg: Color,
    val cardBg: Color,
    val elevatedCardBg: Color,
    val innerCardBg: Color,
    val chipBg: Color,
    val dialogBg: Color,
    val text: Color,
    val muted: Color,
    val tertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val warning: Color,
    val divider: Color,
    val border: Color?,
    val fontFamily: FontFamily?,
    val horizontalGutter: Dp,
)

@Composable
fun rememberM3SettingsPalette(): M3SettingsPalette {
    val immersive = isImmersive()
    val accent = if (immersive) Immersive.Accent else AppAccent
    val onAccent = if (accent.luminance() > 0.45f) Color.Black else Color.White
    return if (immersive) {
        M3SettingsPalette(
            immersive = true,
            pageBg = Immersive.Page,
            cardBg = Immersive.Raised,
            elevatedCardBg = Color(0xFF24242A),
            innerCardBg = Color(0xFF26262B),
            chipBg = Color(0xFF2C2C32),
            dialogBg = Color.Transparent,
            text = Immersive.Text,
            muted = Immersive.TextSecondary,
            tertiary = Immersive.TextTertiary,
            accent = accent,
            onAccent = onAccent,
            destructive = Immersive.Destructive,
            warning = Color(0xFFFFB74D),
            divider = Immersive.Separator,
            border = Immersive.GlassEdge,
            fontFamily = ImmersiveSans,
            horizontalGutter = Immersive.Gutter,
        )
    } else {
        M3SettingsPalette(
            immersive = false,
            pageBg = AppBackground,
            cardBg = tone(0xFF202020),
            elevatedCardBg = tone(0xFF262626),
            innerCardBg = tone(0xFF282828),
            chipBg = tone(0xFF2D2D2D),
            dialogBg = tone(0xFF282828),
            text = AppText,
            muted = AppMuted,
            tertiary = AppMuted.copy(alpha = 0.70f),
            accent = accent,
            onAccent = onAccent,
            destructive = AppDestructive,
            warning = Color(0xFFFFB74D),
            divider = tone(0xFF303030),
            border = null,
            fontFamily = null,
            horizontalGutter = 16.dp,
        )
    }
}

private fun Modifier.m3ConstrainedWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = MaxSettingsWidth)
    .fillMaxWidth()

@Composable
fun m3SwitchColors(palette: M3SettingsPalette): SwitchColors =
    if (palette.immersive) {
        SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = palette.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF3A3A3F),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.7f),
            disabledCheckedTrackColor = palette.accent.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
            disabledUncheckedTrackColor = Color(0xFF2A2A2E),
            disabledUncheckedBorderColor = Color.Transparent,
        )
    } else {
        SwitchDefaults.colors(
            checkedThumbColor = palette.onAccent,
            checkedTrackColor = palette.accent,
            uncheckedThumbColor = palette.muted,
            uncheckedTrackColor = tone(0xFF2B2B2B),
            uncheckedBorderColor = tone(0xFF5A5A5A),
            disabledCheckedThumbColor = palette.onAccent.copy(alpha = 0.5f),
            disabledCheckedTrackColor = palette.accent.copy(alpha = 0.35f),
            disabledUncheckedThumbColor = palette.muted.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = tone(0xFF242424),
            disabledUncheckedBorderColor = tone(0xFF3E3E3E),
        )
    }

@Composable
fun m3FilledButtonColors(palette: M3SettingsPalette): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = palette.accent,
        contentColor = palette.onAccent,
        disabledContainerColor = palette.chipBg,
        disabledContentColor = palette.muted.copy(alpha = 0.5f),
    )

@Composable
fun m3FilterChipColors(
    palette: M3SettingsPalette,
    selectedContainerColor: Color = palette.accent,
): SelectableChipColors {
    val selectedText = if (selectedContainerColor.luminance() > 0.45f) Color.Black else Color.White
    return FilterChipDefaults.filterChipColors(
        containerColor = palette.innerCardBg,
        labelColor = palette.text,
        selectedContainerColor = selectedContainerColor,
        selectedLabelColor = selectedText,
        selectedLeadingIconColor = selectedText,
    )
}

/**
 * Navigation host for multi-level Material Design 3 settings hierarchies with state preservation,
 * system Back handling, and Shared-Axis X horizontal transitions (or cross-fade under Reduce Motion).
 */
@Composable
fun <P : Enum<P>> M3SubpageHost(
    initialPage: P,
    rootPage: P,
    valueOf: (String) -> P,
    onExit: () -> Unit,
    label: String = "m3_subpage_host",
    content: @Composable (page: P, open: (P) -> Unit, back: () -> Unit, popToRoot: () -> Unit) -> Unit,
) {
    val stack = rememberSaveable(
        initialPage,
        saver = listSaver(
            save = { list -> list.map { it.name } },
            restore = { names -> names.mapNotNull { runCatching { valueOf(it) }.getOrNull() }.toMutableStateList() },
        ),
    ) {
        if (initialPage == rootPage) mutableStateListOf()
        else mutableStateListOf(initialPage)
    }
    val page = stack.lastOrNull() ?: rootPage
    val reduce = rememberReduceMotion()
    var pushing by remember { mutableStateOf(true) }
    val pop: () -> Unit = {
        if (stack.isNotEmpty()) {
            pushing = false
            stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    val open: (P) -> Unit = { target ->
        if (target != page) {
            pushing = true
            stack += target
        }
    }
    val back: () -> Unit = {
        if (stack.isNotEmpty()) pop() else onExit()
    }
    val popToRoot: () -> Unit = {
        if (stack.isNotEmpty()) {
            pushing = false
            stack.clear()
        }
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            when {
                reduce -> fadeIn(tween(ImmersiveMotion.FADE_MS)) togetherWith fadeOut(tween(ImmersiveMotion.FADE_MS))
                !pushing ->
                    (slideInHorizontally(tween(ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeIn(tween(ImmersiveMotion.MEDIUM_MS))) togetherWith
                        (slideOutHorizontally(tween(ImmersiveMotion.MEDIUM_MS)) { it } + fadeOut(tween(160)))
                else ->
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeOut(tween(ImmersiveMotion.MEDIUM_MS)))
            }
        },
        label = label,
    ) { current ->
        content(current, open, back, popToRoot)
    }
}

/**
 * Look-adaptive M3 screen frame: collapsible frosted top bar + large title in Immersive, and
 * clean M3 top app bar + centered responsive scroll container in Classic.
 */
@Composable
fun M3SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    listTestTag: String? = null,
    classicActions: (@Composable RowScope.() -> Unit)? = null,
    immersiveActions: (@Composable RowScope.() -> Unit)? = null,
    largeTitleTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.(M3SettingsPalette) -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    val listModifier = if (listTestTag != null) Modifier.testTag(listTestTag) else Modifier
    if (palette.immersive) {
        ImmersivePageFrame {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = immersiveTopContentPadding(),
                    bottom = Immersive.BottomChrome,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = modifier
                    .fillMaxSize()
                    .immersiveHazeSource()
                    .immersiveChromeScroll()
                    .then(listModifier),
            ) {
                item(key = "m3:large-title", contentType = "m3:large-title") {
                    Box(Modifier.m3ConstrainedWidth()) {
                        ImmersiveLargeTitle(
                            title = title,
                            subtitle = subtitle,
                            trailing = largeTitleTrailing,
                        )
                    }
                }
                content(palette)
            }
            ImmersiveTopBar(
                title = title,
                listState = listState,
                onBack = onBack,
                actions = immersiveActions,
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(palette.pageBg)
                .statusBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .m3ConstrainedWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                if (onBack != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onBack),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_navigate_back),
                            tint = palette.text,
                        )
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = title,
                        color = palette.text,
                        fontFamily = palette.fontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = palette.muted,
                            fontFamily = palette.fontFamily,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (classicActions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 4.dp),
                        content = classicActions,
                    )
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = bottomBarsSpace() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .then(listModifier),
            ) {
                content(palette)
            }
        }
    }
}

/** Collects rows placed inside an M3 grouped card section. */
class M3SectionScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * M3 grouped card section inside a `LazyColumn`: optional uppercase header, rounded `20.dp` card
 * with automatic inset hairline dividers between rows, and optional explanatory footer caption.
 * Skips emitting an item if [rows] adds no rows.
 */
fun LazyListScope.m3SettingsSection(
    key: String,
    palette: M3SettingsPalette,
    header: String? = null,
    footer: String? = null,
    hasLeadingIconBadges: Boolean = false,
    rows: M3SectionScope.() -> Unit,
) {
    item(key = "section:$key", contentType = "m3:section") {
        val scope = M3SectionScope().apply(rows)
        if (scope.rows.isEmpty()) return@item
        M3SectionBlock(
            palette = palette,
            header = header,
            footer = footer,
            dividerStartInset = if (hasLeadingIconBadges) 66.dp else 16.dp,
        ) {
            scope.rows.forEachIndexed { index, rowContent ->
                rowContent()
                if (index < scope.rows.lastIndex) {
                    HorizontalDivider(
                        color = palette.divider,
                        modifier = Modifier.padding(
                            start = if (hasLeadingIconBadges) 66.dp else 16.dp,
                            end = 16.dp,
                        ),
                    )
                }
            }
        }
    }
}

/** Free-standing constrained block inside `M3SettingsScaffold`. */
fun LazyListScope.m3SettingsBlock(
    key: String,
    palette: M3SettingsPalette,
    content: @Composable () -> Unit,
) {
    item(key = "block:$key", contentType = "m3:block") {
        Box(
            modifier = Modifier
                .m3ConstrainedWidth()
                .padding(horizontal = palette.horizontalGutter),
        ) {
            content()
        }
    }
}

@Composable
fun M3SectionHeader(
    title: String,
    palette: M3SettingsPalette,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        color = palette.muted,
        fontFamily = palette.fontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 6.dp),
    )
}

@Composable
fun M3SectionBlock(
    palette: M3SettingsPalette,
    header: String? = null,
    footer: String? = null,
    dividerStartInset: Dp = 16.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .m3ConstrainedWidth()
            .padding(horizontal = palette.horizontalGutter),
    ) {
        if (!header.isNullOrBlank()) {
            M3SectionHeader(title = header, palette = palette)
        }
        M3GroupCard(palette = palette) {
            content()
        }
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                color = palette.tertiary,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = if (dividerStartInset > 20.dp) 6.dp else 6.dp),
            )
        }
    }
}

@Composable
fun M3GroupCard(
    palette: M3SettingsPalette,
    modifier: Modifier = Modifier,
    containerColor: Color = palette.cardBg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CardShape,
        color = containerColor,
        contentColor = palette.text,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (palette.border != null) Modifier.border(1.dp, palette.border, CardShape)
                else Modifier,
            ),
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun M3LeadingIconBadge(
    icon: ImageVector,
    palette: M3SettingsPalette,
    destructive: Boolean = false,
    tintOverride: Color? = null,
) {
    val tint = tintOverride ?: if (destructive) palette.destructive else palette.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(IconBadgeShape)
            .background(tint.copy(alpha = 0.14f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun M3SettingsNavRow(
    title: String,
    palette: M3SettingsPalette,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    valueColor: Color? = null,
    detailColor: Color? = null,
    locked: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    externalLink: Boolean = false,
    showChevron: Boolean = true,
    testTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val titleColor = when {
        !enabled -> palette.tertiary
        destructive -> palette.destructive
        else -> palette.text
    }
    val resolvedDetailColor = detailColor ?: if (enabled) palette.muted else palette.tertiary
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .then(
            if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            else Modifier,
        )
        .padding(horizontal = 16.dp, vertical = if (detail.isNullOrBlank()) 13.dp else 11.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier,
    ) {
        if (icon != null) {
            M3LeadingIconBadge(
                icon = icon,
                palette = palette,
                destructive = destructive,
                tintOverride = if (!enabled) palette.tertiary else null,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(
                text = title,
                color = titleColor,
                fontFamily = palette.fontFamily,
                fontSize = 15.sp,
                fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = resolvedDetailColor,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else {
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    color = valueColor ?: palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .padding(end = if (locked || externalLink || showChevron) 6.dp else 0.dp),
                )
            }
            if (locked) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = palette.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                if (externalLink || showChevron) Spacer(Modifier.width(6.dp))
            }
            val endIcon = when {
                externalLink -> Icons.AutoMirrored.Rounded.OpenInNew
                showChevron && onClick != null -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
                else -> null
            }
            if (endIcon != null) {
                Icon(
                    imageVector = endIcon,
                    contentDescription = null,
                    tint = palette.tertiary,
                    modifier = Modifier.size(if (externalLink) 18.dp else 20.dp),
                )
            }
        }
    }
}

@Composable
fun M3SettingsSwitchRow(
    title: String,
    checked: Boolean,
    palette: M3SettingsPalette,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    switchTestTag: String? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.5f
    val switchColors = m3SwitchColors(palette)
    val rowModifier = if (switchTestTag != null) {
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 16.dp, vertical = if (detail.isNullOrBlank()) 12.dp else 10.dp)
    } else {
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = if (detail.isNullOrBlank()) 12.dp else 10.dp)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier,
    ) {
        if (icon != null) {
            M3LeadingIconBadge(icon = icon, palette = palette)
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .alpha(contentAlpha),
        ) {
            Text(
                text = title,
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (switchTestTag != null && enabled) onCheckedChange else null,
            enabled = enabled,
            colors = switchColors,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier,
        )
    }
}

@Composable
fun M3SettingsChoiceRow(
    title: String,
    selected: Boolean,
    palette: M3SettingsPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val reduce = rememberReduceMotion()
    val checkTint by animateColorAsState(
        targetValue = if (selected) palette.accent else Color.Transparent,
        animationSpec = if (reduce) snap() else ImmersiveMotion.placementSpring(),
        label = "m3_choice_check",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = if (detail.isNullOrBlank()) 12.dp else 10.dp),
    ) {
        if (!palette.immersive) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = palette.accent,
                    unselectedColor = palette.muted,
                ),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.5f),
        ) {
            Text(
                text = title,
                color = if (selected && !palette.immersive) palette.accent else palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (palette.immersive) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun M3SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    palette: M3SettingsPalette,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (palette.immersive) {
        ImmersiveSegmentedControl(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier.padding(12.dp),
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.innerCardBg)
                .padding(4.dp),
        ) {
            options.forEachIndexed { index, label ->
                val chosen = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (chosen) palette.accent else Color.Transparent)
                        .clickable(role = Role.Tab) { if (!chosen) onSelect(index) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        color = if (chosen) palette.onAccent else palette.text,
                        fontFamily = palette.fontFamily,
                        fontSize = 13.sp,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun M3InfoBannerCard(
    text: String,
    palette: M3SettingsPalette,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = palette.accent,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .clip(BannerShape)
            .background(tint.copy(alpha = 0.12f))
            .then(
                if (palette.border != null) Modifier.border(1.dp, tint.copy(alpha = 0.25f), BannerShape)
                else Modifier,
            )
            .padding(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = tint,
                    fontFamily = palette.fontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = text,
                color = if (title.isNullOrBlank()) palette.muted else palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
fun M3RemovableChip(
    label: String,
    palette: M3SettingsPalette,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
) {
    val pillShape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(pillShape)
            .background(palette.chipBg)
            .then(
                if (palette.border != null) Modifier.border(1.dp, palette.border, pillShape)
                else Modifier,
            )
            .clickable(role = Role.Button, onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = label,
            color = palette.text,
            fontFamily = palette.fontFamily,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.remove),
            tint = palette.muted,
            modifier = Modifier.size(14.dp),
        )
    }
}
