/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.settings

import io.github.thake.soundnest.feature.appearance.settings.AppLanguage

import io.github.thake.soundnest.feature.spokenword.audible.ui.AudibleLoginDialog

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import io.github.thake.soundnest.R
import io.github.thake.soundnest.audible.AudibleApiClient
import io.github.thake.soundnest.audible.AudibleAuth
import io.github.thake.soundnest.audible.AudibleBook
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.audible.AudiblePkceSession
import io.github.thake.soundnest.audible.AudibleSessionTokens
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale

object AudibleAccountCoordinator {
    internal const val EPHEMERAL_WEBVIEW_PROFILE = "audible_ephemeral_login"
    private val defaultHttp by lazy { OkHttpClient() }

    fun initialMarketplace(locale: Locale = AppLanguage.effectiveLocale): AudibleMarketplace =
        AudibleMarketplace.defaultForLocale(locale)

    /**
     * Parses a WebView navigation URL during Amazon OAuth2 + PKCE sign-in.
     * As soon as `/ap/maplanding` is reached (success or error), [wipeLoginProfile] is invoked
     * synchronously so zero Amazon web session cookies outlive the authorization redirect.
     */
    fun interceptRedirectUrl(
        url: String,
        wipeLoginProfile: () -> Unit,
    ): AudibleAuth.MapLandingResult {
        val result = AudibleAuth.parseRedirectUrl(url)
        if (result !is AudibleAuth.MapLandingResult.NotMapLanding) {
            wipeLoginProfile()
        }
        return result
    }

    /**
     * Wipes the isolated Audible login WebView profile (or expires Amazon/Audible domain cookies
     * without touching Spotify's cookies when multi-profile is unavailable on older WebViews).
     */
    fun wipeWebViewSession(
        webView: WebView?,
        marketplace: AudibleMarketplace? = null,
    ) {
        runCatching {
            webView?.stopLoading()
            webView?.clearCache(true)
            webView?.clearHistory()
            webView?.clearFormData()
        }
        val multiProfile = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)

        if (multiProfile) {
            runCatching {
                val store = ProfileStore.getInstance()
                val profile = store.getProfile(EPHEMERAL_WEBVIEW_PROFILE)
                profile?.cookieManager?.removeAllCookies(null)
                profile?.cookieManager?.flush()
                profile?.webStorage?.deleteAllData()
                if (webView == null) {
                    store.deleteProfile(EPHEMERAL_WEBVIEW_PROFILE)
                }
            }
        } else {
            // Scoped domain cookie expiry so Spotify's default WebView profile remains untouched.
            runCatching {
                val cm = CookieManager.getInstance()
                val markets = if (marketplace != null) listOf(marketplace) else AudibleMarketplace.entries
                for (m in markets) {
                    for (host in listOf("https://www.${m.amazonDomain}", "https://${m.amazonDomain}", "https://www.${m.audibleDomain}", "https://${m.audibleDomain}")) {
                        val raw = cm.getCookie(host).orEmpty()
                        raw.split(';').forEach { pair ->
                            val name = pair.substringBefore('=').trim()
                            if (name.isNotEmpty()) {
                                cm.setCookie(host, "$name=; Max-Age=0; Path=/")
                                cm.setCookie(host, "$name=; Max-Age=0; Domain=.${m.amazonDomain}; Path=/")
                                cm.setCookie(host, "$name=; Max-Age=0; Domain=.${m.audibleDomain}; Path=/")
                            }
                        }
                    }
                }
                cm.flush()
            }
        }
    }

    suspend fun completeAuthorization(
        context: Context,
        session: AudiblePkceSession,
        authorizationCode: String,
        http: OkHttpClient = defaultHttp,
    ): List<AudibleBook> {
        val tokens = AudibleAuth.registerDevice(
            http = http,
            session = session,
            authorizationCode = authorizationCode,
        )
        AudibleCredentialStore.save(context, tokens)
        return runCatching { syncLibrary(context) }.getOrDefault(emptyList())
    }

    /**
     * Fetches the parent's owned Audible library (`GET /1.0/library`) and updates [AudibleCatalogCache].
     * Fail-closed invariant: newly synced books are NEVER auto-added to `allowedAudibleAsins`.
     */
    suspend fun syncLibrary(
        context: Context,
        clientOverride: AudibleApiClient? = null,
    ): List<AudibleBook> {
        val client = clientOverride ?: AudibleApiClient(
            http = defaultHttp,
            tokensProvider = { AudibleCredentialStore.loadTokens(context) },
        )
        val books = client.fetchLibrary()
        AudibleCatalogCache.saveBooks(context, books)
        val allowed = ParentalPolicyStore.current(context).spokenWord.audible.allowedAsins
        books.filter { it.asin in allowed }.forEach { book ->
            RuleLabelStore.record(
                context = context,
                kind = "audible",
                idOrUri = book.asin,
                title = book.title,
                subtitle = book.authors.joinToString(", "),
            )
        }
        return books
    }

    suspend fun disconnect(
        context: Context,
        deregister: (suspend (AudibleSessionTokens) -> Boolean)? = null,
    ) {
        AudiblePlayer.stop()
        val existingTokens = AudibleCredentialStore.loadTokens(context)
        AudibleCredentialStore.clear(context)
        AudibleCatalogCache.clear(context)
        AudibleProgressStore.clearAll(context)
        wipeWebViewSession(null, existingTokens?.marketplace)
        if (existingTokens != null) {
            runCatching {
                if (deregister != null) {
                    deregister(existingTokens)
                } else {
                    AudibleAuth.deregisterDevice(defaultHttp, existingTokens)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudibleParentControlsCard(
    audibleEnabled: Boolean = true,
    audiobooksEnabled: Boolean = audibleEnabled,
    defaultLocale: Locale = AppLanguage.effectiveLocale,
    onOpenPicker: () -> Unit,
    onStartSignIn: ((AudibleMarketplace) -> Unit)? = null,
    onSyncLibraryOverride: (suspend () -> Result<Int>)? = null,
    onDisconnectOverride: (suspend () -> Unit)? = null,
) {
    val isAudibleActive = audibleEnabled && audiobooksEnabled
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    val summary by AudibleCredentialStore.summaryFlow.collectAsState()
    val catalogRevision by AudibleCatalogCache.revision.collectAsState()

    LaunchedEffect(context) {
        AudibleCredentialStore.summary(context)
        ParentalPolicyStore.current(context)
    }

    val autoDetectedMarketplace = remember(defaultLocale) {
        AudibleAccountCoordinator.initialMarketplace(defaultLocale)
    }
    var selectedMarketplace by remember(autoDetectedMarketplace) {
        mutableStateOf(autoDetectedMarketplace)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var activeLoginMarketplace by remember { mutableStateOf<AudibleMarketplace?>(null) }
    var busy by remember { mutableStateOf(false) }

    val syncedBooks = remember(context, catalogRevision, summary) {
        if (summary == null) emptyList() else AudibleCatalogCache.books(context)
    }
    val approvedCount = policy.spokenWord.audible.allowedAsins.size
    val pinnedCount = policy.spokenWord.audible.pinnedAsins.size

    val palette = rememberM3SettingsPalette()
    val accentButtonColors = m3FilledButtonColors(palette)
    val subCardShape = RoundedCornerShape(12.dp)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audible_parent_card"),
    ) {
        Text(
            text = stringResource(R.string.audible_section_title),
            color = palette.text,
            fontFamily = palette.fontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.audible_section_subtitle),
            color = palette.muted,
            fontFamily = palette.fontFamily,
            fontSize = 12.sp,
        )

        val connected = summary
        if (connected != null && !isAudibleActive) {
            Text(
                text = stringResource(R.string.audible_paused_notice),
                color = palette.warning,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                modifier = Modifier.testTag("audible_paused_notice"),
            )
        }

        if (connected == null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(subCardShape)
                        .background(palette.innerCardBg)
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("audible_marketplace_selector"),
                ) {
                    Column(Modifier.weight(1f)) {
                        val originLabel = if (selectedMarketplace == autoDetectedMarketplace) {
                            "${stringResource(R.string.audible_marketplace_label)} · ${stringResource(R.string.audible_marketplace_autodetected)}"
                        } else {
                            "${stringResource(R.string.audible_marketplace_label)} · ${stringResource(R.string.audible_marketplace_manual)}"
                        }
                        Text(originLabel, color = palette.muted, fontFamily = palette.fontFamily, fontSize = 11.sp)
                        Text(
                            text = selectedMarketplace.localizedName(context),
                            color = palette.text,
                            fontFamily = palette.fontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select marketplace", tint = palette.accent)
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(palette.elevatedCardBg),
                ) {
                    AudibleMarketplace.entries.forEach { market ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = market.localizedName(context),
                                        color = if (market == selectedMarketplace) palette.accent else palette.text,
                                        fontFamily = palette.fontFamily,
                                        fontWeight = if (market == selectedMarketplace) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (market == selectedMarketplace) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                selectedMarketplace = market
                                dropdownExpanded = false
                            },
                            modifier = Modifier.testTag("audible_marketplace_option_${market.code}"),
                        )
                    }
                }
            }

            Button(
                enabled = !busy,
                onClick = {
                    if (onStartSignIn != null) {
                        onStartSignIn(selectedMarketplace)
                    } else {
                        activeLoginMarketplace = selectedMarketplace
                    }
                },
                colors = accentButtonColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audible_sign_in_button"),
            ) {
                Text(
                    text = stringResource(R.string.audible_sign_in_button),
                    fontFamily = palette.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(subCardShape)
                    .background(palette.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                val headerText = if (!connected.customerName.isNullOrBlank()) {
                    "✓ Connected: ${connected.customerName} (${connected.marketplace.audibleDomain})"
                } else {
                    context.getString(R.string.audible_connected_to, connected.marketplace.localizedName(context))
                }
                Text(
                    text = headerText,
                    color = palette.accent,
                    fontFamily = palette.fontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.audible_status_summary, syncedBooks.size, approvedCount, pinnedCount),
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                )
            }

            Button(
                enabled = !busy,
                onClick = onOpenPicker,
                colors = accentButtonColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audible_manage_books_button"),
            ) {
                Text(
                    text = "${stringResource(R.string.audible_manage_books_button)} ($approvedCount)",
                    fontFamily = palette.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val res = if (onSyncLibraryOverride != null) {
                                onSyncLibraryOverride()
                            } else {
                                runCatching { AudibleAccountCoordinator.syncLibrary(context).size }
                            }
                            busy = false
                            res.onSuccess { count ->
                                Toast.makeText(context, context.getString(R.string.audible_sync_success, count), Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, context.getString(R.string.audible_sync_failed, err.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("audible_sync_library_button"),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = palette.text, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.audible_sync_library_button),
                        color = palette.text,
                        fontFamily = palette.fontFamily,
                    )
                }

                OutlinedButton(
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.destructive),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                if (onDisconnectOverride != null) {
                                    onDisconnectOverride()
                                } else {
                                    AudibleAccountCoordinator.disconnect(context)
                                }
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.testTag("audible_disconnect_button"),
                ) {
                    Text(
                        text = stringResource(R.string.audible_disconnect_button),
                        fontFamily = palette.fontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    activeLoginMarketplace?.let { market ->
        AudibleLoginDialog(
            marketplace = market,
            onDismiss = { activeLoginMarketplace = null },
            onConnected = { count ->
                activeLoginMarketplace = null
                scope.launch {
                    ParentalPolicyStore.update(context) {
                        it.copy(spokenWord = it.spokenWord.copy(allowAudible = true))
                    }
                }
                Toast.makeText(context, "Audible connected ($count books synced).", Toast.LENGTH_SHORT).show()
                onOpenPicker()
            },
        )
    }
}
