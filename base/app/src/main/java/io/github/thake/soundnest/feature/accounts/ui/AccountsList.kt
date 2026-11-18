/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3LeadingIconBadge
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.accounts.session.ProfileCache
import io.github.thake.soundnest.feature.accounts.session.SessionActions
import io.github.thake.soundnest.host.android.AccountList
import io.github.thake.soundnest.host.android.SavedAccount
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.spotify.AccountPlan

/**
 * The Spotify accounts kept on this phone, styled with [rememberM3SettingsPalette] for both
 * Classic and Immersive looks: active profile first with plan + check mark, saved profiles with
 * remove buttons, and an "Add account" row.
 */
@Composable
fun AccountsList(
    onLeave: () -> Unit = {},
    showFooter: Boolean = false,
) {
    val palette = rememberM3SettingsPalette()
    val ctx = LocalContext.current
    val host = if (SoundNestRuntime.isStarted) SoundNestRuntime.host else null
    val list by if (host != null) {
        host.accounts.list.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(AccountList()) }
    }
    val multiProfile = host?.multiProfile == true
    val livePlan by AccountPlan.premium.collectAsStateWithLifecycle()
    var switchTo by remember { mutableStateOf<SavedAccount?>(null) }
    var remove by remember { mutableStateOf<SavedAccount?>(null) }
    var addSingle by remember { mutableStateOf(false) }

    val shown = remember(list) {
        val active = list.current ?: SavedAccount(profile = list.active)
        listOf(active) + list.accounts
            .filter { it.profile != list.active }
            .sortedWith(compareBy<SavedAccount> { !it.known }.thenByDescending { it.usedAt })
    }

    Column(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, account ->
            val active = account.profile == list.active
            val name = (if (active) ProfileCache.name else null) ?: account.name
            val image = (if (active) ProfileCache.imageUrl else null) ?: account.imageUrl
            val premium = if (active) livePlan ?: account.premium else account.premium
            val planText = when (premium) {
                true -> stringResource(R.string.accounts_plan_premium)
                false -> stringResource(R.string.accounts_plan_free)
                null -> null
            }
            val detail = listOfNotNull(
                planText,
                if (active) stringResource(R.string.accounts_in_use) else null,
            ).joinToString(" · ")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !active, role = Role.Button) { switchTo = account }
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            ) {
                ProfileAvatar(url = image, name = name, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name?.takeIf { it.isNotBlank() }
                            ?: stringResource(if (account.known || active) R.string.spotify_account else R.string.accounts_not_signed_in),
                        color = palette.text,
                        fontFamily = palette.fontFamily,
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            color = if (active) palette.accent else palette.muted,
                            fontFamily = palette.fontFamily,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (active) {
                    if (planText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(palette.accent.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = planText,
                                color = palette.accent,
                                fontFamily = palette.fontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.accounts_in_use),
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    IconButton(onClick = { remove = account }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.accounts_remove),
                            tint = palette.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (index < shown.lastIndex) {
                HorizontalDivider(
                    color = palette.divider,
                    modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                )
            }
        }

        HorizontalDivider(
            color = palette.divider,
            modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    if (multiProfile) {
                        onLeave()
                        SessionActions.addAccount(Graph.app)
                    } else {
                        addSingle = true
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            M3LeadingIconBadge(icon = Icons.Rounded.Add, palette = palette)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.accounts_add),
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(if (multiProfile) R.string.accounts_add_detail else R.string.accounts_add_single_detail),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        if (showFooter) {
            Text(
                text = stringResource(if (multiProfile) R.string.accounts_footer else R.string.accounts_single_footer),
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    switchTo?.let { account ->
        val name = account.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.spotify_account)
        LookAlertDialog(
            onDismissRequest = { switchTo = null },
            containerColor = palette.dialogBg,
            title = { Text(stringResource(R.string.accounts_switch_title, name), color = palette.text) },
            text = { Text(stringResource(R.string.accounts_switch_text), color = palette.muted) },
            confirmButton = {
                TextButton(onClick = { switchTo = null; onLeave(); SessionActions.switchAccount(Graph.app, account.profile) }) {
                    Text(stringResource(R.string.accounts_switch_confirm), color = palette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { switchTo = null }) {
                    Text(stringResource(R.string.add_to_playlist_button_cancel), color = palette.text)
                }
            },
        )
    }

    remove?.let { account ->
        val name = account.name?.takeIf { it.isNotBlank() }
            ?: stringResource(if (account.known) R.string.spotify_account else R.string.accounts_not_signed_in)
        LookAlertDialog(
            onDismissRequest = { remove = null },
            containerColor = palette.dialogBg,
            title = { Text(stringResource(R.string.accounts_remove_title, name), color = palette.text) },
            text = { Text(stringResource(R.string.accounts_remove_text), color = palette.muted) },
            confirmButton = {
                TextButton(onClick = { remove = null; SessionActions.removeAccount(ctx, account.profile) }) {
                    Text(stringResource(R.string.accounts_remove), color = palette.destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { remove = null }) {
                    Text(stringResource(R.string.add_to_playlist_button_cancel), color = palette.text)
                }
            },
        )
    }

    if (addSingle) {
        LookAlertDialog(
            onDismissRequest = { addSingle = false },
            containerColor = palette.dialogBg,
            title = { Text(stringResource(R.string.accounts_add), color = palette.text) },
            text = { Text(stringResource(R.string.accounts_single_footer), color = palette.muted) },
            confirmButton = {
                TextButton(onClick = { addSingle = false; onLeave(); SessionActions.addAccount(Graph.app) }) {
                    Text(stringResource(R.string.connect_device_menu_logout), color = palette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { addSingle = false }) {
                    Text(stringResource(R.string.add_to_playlist_button_cancel), color = palette.text)
                }
            },
        )
    }
}

/** The detail of the "Switch account" row behind the avatar: the other accounts kept, by name. */
@Composable
fun switchAccountDetail(): String {
    if (!SoundNestRuntime.isStarted) {
        return stringResource(R.string.accounts_switch_row_detail)
    }
    val list by SoundNestRuntime.host.accounts.list.collectAsStateWithLifecycle()
    return list.others.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }.joinToString(", ")
        .ifEmpty { stringResource(R.string.accounts_switch_row_detail) }
}
