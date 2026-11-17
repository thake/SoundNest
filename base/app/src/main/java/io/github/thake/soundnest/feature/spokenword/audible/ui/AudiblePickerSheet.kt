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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
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
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import kotlinx.coroutines.launch

private enum class AudiblePickerFilter { ALL, APPROVED, PINNED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiblePickerSheet(
    onDismiss: () -> Unit,
    onSyncRequested: (suspend () -> Result<Int>)? = null,
) {
    LookSheet(
        onDismissRequest = onDismiss,
        containerColor = tone(0xFF222222),
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

    val selectedChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AppAccent,
        selectedLabelColor = Color.Black,
        selectedLeadingIconColor = Color.Black,
    )

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
                    color = AppText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Check a book to allow it in Your Library · Tap the pin icon to feature it on Home",
                    color = AppMuted,
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
                            Toast.makeText(context, "Synced $count Audible audiobooks.", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, "Sync failed: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync Library", tint = AppAccent)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = AppMuted)
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
                label = { Text("All (${allBooks.size})") },
                modifier = Modifier.testTag("audible_picker_filter_all"),
            )
            FilterChip(
                selected = filter == AudiblePickerFilter.APPROVED,
                onClick = { filter = AudiblePickerFilter.APPROVED },
                colors = selectedChipColors,
                leadingIcon = if (filter == AudiblePickerFilter.APPROVED) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text("Approved (${allowedSet.size})") },
                modifier = Modifier.testTag("audible_picker_filter_approved"),
            )
            FilterChip(
                selected = filter == AudiblePickerFilter.PINNED,
                onClick = { filter = AudiblePickerFilter.PINNED },
                colors = selectedChipColors,
                leadingIcon = if (filter == AudiblePickerFilter.PINNED) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text("Pinned (${pinnedSet.size})") },
                modifier = Modifier.testTag("audible_picker_filter_pinned"),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.audible_picker_search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audible_picker_search"),
        )

        if (filteredBooks.isEmpty()) {
            val emptyCopy = when {
                allBooks.isEmpty() -> "No Audible audiobooks cached yet. Tap Sync to fetch your owned library."
                searchQuery.isNotBlank() -> "No audiobooks match \"${searchQuery.trim()}\"."
                filter == AudiblePickerFilter.APPROVED -> "No Audible audiobooks approved yet."
                else -> "No Audible audiobooks pinned to Home yet."
            }
            Text(
                text = emptyCopy,
                color = AppMuted,
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
            .clip(RoundedCornerShape(10.dp))
            .background(tone(0xFF2B2B2B))
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tone(0xFF3A3A3A)),
        ) {
            Text(
                text = book.title.take(1).uppercase(),
                color = AppAccent,
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
                    color = AppText,
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
                        color = Color(0xFFFF6B6B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .testTag("audible_adult_badge_${book.asin}"),
                    )
                }
            }
            Text(
                text = metaLine,
                color = AppMuted,
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
                    color = AppAccent.copy(alpha = 0.85f),
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
                contentDescription = if (isPinned) "Unpin from Home" else "Pin to Home",
                tint = when {
                    !isAllowed -> AppMuted.copy(alpha = 0.3f)
                    isPinned -> AppAccent
                    else -> AppMuted
                },
            )
        }

        Checkbox(
            checked = isAllowed,
            onCheckedChange = onToggleAllowed,
            colors = CheckboxDefaults.colors(
                checkedColor = AppAccent,
                checkmarkColor = Color.Black,
                uncheckedColor = AppMuted,
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
