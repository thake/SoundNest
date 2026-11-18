/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.ui

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleAccountCoordinator

import io.github.thake.soundnest.audible.*

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.R
import io.github.thake.soundnest.audible.AudibleBook
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import kotlinx.coroutines.launch

private enum class AudiblePickerFilter { ALL, APPROVED, PINNED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiblePickerSheet(
    onDismiss: () -> Unit,
    onSyncRequested: (suspend () -> Result<Int>)? = null,
) {
    val palette = rememberM3SettingsPalette()
    LookSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.elevatedCardBg,
    ) {
        AudiblePickerSheetBody(
            onDismiss = onDismiss,
            onSyncRequested = onSyncRequested,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AudiblePickerSheetBody(
    onDismiss: () -> Unit,
    onSyncRequested: (suspend () -> Result<Int>)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    val catalogRevision by AudibleCatalogCache.revision.collectAsState()

    var filter by remember { mutableStateOf(AudiblePickerFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }

    val allBooks = remember(context, catalogRevision) {
        AudibleCatalogCache.books(context)
    }
    val allowedSet = policy.spokenWord.audible.allowedAsins
    val pinnedSet = policy.spokenWord.audible.pinnedAsins

    val filteredBooks = remember(allBooks, allowedSet, pinnedSet, filter, searchQuery) {
        val base = when (filter) {
            AudiblePickerFilter.ALL -> allBooks
            AudiblePickerFilter.APPROVED -> allBooks.filter { it.asin in allowedSet }
            AudiblePickerFilter.PINNED -> allBooks.filter { it.asin in pinnedSet }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) base
        else base.filter { book ->
            book.title.lowercase().contains(q) ||
                book.subtitle?.lowercase()?.contains(q) == true ||
                book.authors.any { it.lowercase().contains(q) } ||
                book.seriesTitle?.lowercase()?.contains(q) == true ||
                book.asin.lowercase().contains(q)
        }
    }

    val palette = rememberM3SettingsPalette()
    val selectedChipColors = m3FilterChipColors(palette)

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("audible_picker_sheet"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audible_picker_title),
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.audible_picker_hint),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                enabled = !syncing,
                onClick = {
                    syncing = true
                    scope.launch {
                        val res = if (onSyncRequested != null) {
                            onSyncRequested()
                        } else {
                            runCatching { AudibleAccountCoordinator.syncLibrary(context).size }
                        }
                        syncing = false
                        res.onSuccess { count ->
                            Toast.makeText(context, context.getString(R.string.audible_sync_success, count), Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, context.getString(R.string.audible_sync_failed, err.message ?: ""), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.audible_sync_library_button), tint = palette.accent)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = palette.muted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == AudiblePickerFilter.ALL,
                onClick = { filter = AudiblePickerFilter.ALL },
                colors = selectedChipColors,
                leadingIcon = if (filter == AudiblePickerFilter.ALL) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text(stringResource(R.string.audible_picker_tab_all, allBooks.size), fontFamily = palette.fontFamily) },
                modifier = Modifier.testTag("audible_picker_filter_all"),
            )
            FilterChip(
                selected = filter == AudiblePickerFilter.APPROVED,
                onClick = { filter = AudiblePickerFilter.APPROVED },
                colors = selectedChipColors,
                leadingIcon = if (filter == AudiblePickerFilter.APPROVED) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text(stringResource(R.string.audible_picker_tab_approved, allowedSet.size), fontFamily = palette.fontFamily) },
                modifier = Modifier.testTag("audible_picker_filter_approved"),
            )
            FilterChip(
                selected = filter == AudiblePickerFilter.PINNED,
                onClick = { filter = AudiblePickerFilter.PINNED },
                colors = selectedChipColors,
                leadingIcon = if (filter == AudiblePickerFilter.PINNED) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text(stringResource(R.string.audible_picker_tab_pinned, pinnedSet.size), fontFamily = palette.fontFamily) },
                modifier = Modifier.testTag("audible_picker_filter_pinned"),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.audible_picker_search_hint), color = palette.muted, fontFamily = palette.fontFamily) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.divider,
                cursorColor = palette.accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audible_picker_search"),
        )

        if (filteredBooks.isEmpty()) {
            val emptyCopy = when {
                allBooks.isEmpty() -> stringResource(R.string.audible_picker_empty_none)
                searchQuery.isNotBlank() -> stringResource(R.string.audible_picker_empty_search, searchQuery.trim())
                filter == AudiblePickerFilter.APPROVED -> stringResource(R.string.audible_picker_empty_approved)
                else -> stringResource(R.string.audible_picker_empty_pinned)
            }
            Text(
                text = emptyCopy,
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(filteredBooks, key = { it.asin }) { book ->
                    AudibleBookPickerRow(
                        book = book,
                        isAllowed = book.asin in allowedSet,
                        isPinned = book.asin in pinnedSet,
                        palette = palette,
                        onToggleAllowed = { allowed ->
                            ParentalPolicyStore.setAudibleBookAllowed(
                                context = context,
                                asin = book.asin,
                                allowed = allowed,
                                title = book.title,
                                subtitle = book.authors.joinToString(", "),
                            )
                        },
                        onTogglePinned = { pinned ->
                            ParentalPolicyStore.setAudibleBookPinned(
                                context = context,
                                asin = book.asin,
                                pinned = pinned,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AudibleBookPickerRow(
    book: AudibleBook,
    isAllowed: Boolean,
    isPinned: Boolean,
    palette: M3SettingsPalette,
    onToggleAllowed: (Boolean) -> Unit,
    onTogglePinned: (Boolean) -> Unit,
) {
    val authorLine = book.authors.joinToString(", ").ifBlank { "Audible Audiobook" }
    val durationLabel = formatRuntimeHoursMinutes(book.runtimeLengthMs)
    val metaLine = listOfNotNull(
        authorLine,
        durationLabel.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.innerCardBg)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.chipBg),
        ) {
            Text(
                text = book.title.take(1).uppercase(),
                color = palette.accent,
                fontFamily = palette.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            if (book.coverUrl.isNotBlank()) {
                GlideImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.title,
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (book.isAdultProduct) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "18+",
                        color = palette.destructive,
                        fontFamily = palette.fontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.destructive.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .testTag("audible_adult_badge_${book.asin}"),
                    )
                }
            }
            Text(
                text = metaLine,
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!book.seriesTitle.isNullOrBlank()) {
                val seriesText = if (!book.seriesSequence.isNullOrBlank()) {
                    "${book.seriesTitle} #${book.seriesSequence}"
                } else {
                    book.seriesTitle.orEmpty()
                }
                Text(
                    text = seriesText,
                    color = palette.accent.copy(alpha = 0.85f),
                    fontFamily = palette.fontFamily,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            enabled = isAllowed,
            onClick = { onTogglePinned(!isPinned) },
            modifier = Modifier.testTag("audible_pin_toggle_${book.asin}"),
        ) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (isPinned) stringResource(R.string.audible_unpin_from_home) else stringResource(R.string.audible_pin_to_home),
                tint = when {
                    !isAllowed -> palette.muted.copy(alpha = 0.3f)
                    isPinned -> palette.accent
                    else -> palette.muted
                },
            )
        }

        Checkbox(
            checked = isAllowed,
            onCheckedChange = onToggleAllowed,
            colors = CheckboxDefaults.colors(
                checkedColor = palette.accent,
                checkmarkColor = palette.onAccent,
                uncheckedColor = palette.muted,
            ),
            modifier = Modifier.testTag("audible_allow_toggle_${book.asin}"),
        )
    }
}

internal fun formatRuntimeHoursMinutes(ms: Long): String {
    if (ms <= 0L) return ""
    val totalMinutes = (ms / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
