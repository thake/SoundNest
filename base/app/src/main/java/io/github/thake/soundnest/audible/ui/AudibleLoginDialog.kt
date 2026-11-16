/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.thake.soundnest.R
import io.github.thake.soundnest.audible.AudibleApiClient
import io.github.thake.soundnest.audible.AudibleAuth
import io.github.thake.soundnest.audible.AudibleBook
import io.github.thake.soundnest.audible.AudibleCatalogCache
import io.github.thake.soundnest.audible.AudibleCredentialStore
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.audible.AudiblePlayer
import io.github.thake.soundnest.audible.AudibleProgressStore
import io.github.thake.soundnest.audible.AudibleSessionTokens
import io.github.thake.soundnest.parental.ParentalPolicyStore
import io.github.thake.soundnest.parental.RuleLabelStore
import io.github.thake.soundnest.ui.theme.AppAccent
import io.github.thake.soundnest.ui.theme.AppBackground
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale

object AudibleAccountCoordinator {
    internal const val EPHEMERAL_WEBVIEW_PROFILE = "audible_ephemeral_login"
    private val defaultHttp by lazy { OkHttpClient() }

    fun initialMarketplace(locale: Locale = Locale.getDefault()): AudibleMarketplace =
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
        session: io.github.thake.soundnest.audible.AudiblePkceSession,
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
        val allowed = ParentalPolicyStore.current(context).allowedAudibleAsins
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
    audiobooksEnabled: Boolean,
    defaultLocale: Locale = Locale.getDefault(),
    onOpenPicker: () -> Unit,
    onStartSignIn: ((AudibleMarketplace) -> Unit)? = null,
    onSyncLibraryOverride: (suspend () -> Result<Int>)? = null,
    onDisconnectOverride: (suspend () -> Unit)? = null,
) {
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
    val approvedCount = policy.allowedAudibleAsins.size
    val pinnedCount = policy.pinnedAudibleAsins.size

    val accentButtonColors = ButtonDefaults.buttonColors(
        containerColor = AppAccent,
        contentColor = Color.Black,
        disabledContainerColor = tone(0xFF2E2E2E),
        disabledContentColor = AppMuted.copy(alpha = 0.5f),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone(0xFF282828))
            .padding(14.dp)
            .testTag("audible_parent_card"),
    ) {
        Text(
            text = stringResource(R.string.audible_section_title),
            color = AppText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.audible_section_subtitle),
            color = AppMuted,
            fontSize = 12.sp,
        )

        if (!audiobooksEnabled) {
            Text(
                text = "Turn on the Audiobooks toggle above to enable Audible playback and library selection.",
                color = Color(0xFFFFB74D),
                fontSize = 12.sp,
            )
        }

        val connected = summary
        if (connected == null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tone(0xFF1F1F1F))
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("audible_marketplace_selector"),
                ) {
                    Column(Modifier.weight(1f)) {
                        val originLabel = if (selectedMarketplace == autoDetectedMarketplace) {
                            "${stringResource(R.string.audible_marketplace_label)} · Auto-detected"
                        } else {
                            "${stringResource(R.string.audible_marketplace_label)} · Manual override"
                        }
                        Text(originLabel, color = AppMuted, fontSize = 11.sp)
                        Text(
                            text = selectedMarketplace.displayName,
                            color = AppText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select marketplace", tint = AppAccent)
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(tone(0xFF2B2B2B)),
                ) {
                    AudibleMarketplace.entries.forEach { market ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = market.displayName,
                                        color = if (market == selectedMarketplace) AppAccent else AppText,
                                        fontWeight = if (market == selectedMarketplace) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (market == selectedMarketplace) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Check, contentDescription = null, tint = AppAccent, modifier = Modifier.size(16.dp))
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
                enabled = audiobooksEnabled && !busy,
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
                Text(stringResource(R.string.audible_sign_in_button), fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppAccent.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                val headerText = if (!connected.customerName.isNullOrBlank()) {
                    "✓ Connected: ${connected.customerName} (${connected.marketplace.audibleDomain})"
                } else {
                    "✓ Connected to ${connected.marketplace.displayName}"
                }
                Text(
                    text = headerText,
                    color = AppAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${syncedBooks.size} in library · $approvedCount approved · $pinnedCount pinned to Home",
                    color = AppText,
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
                                Toast.makeText(context, "Synced $count Audible audiobooks.", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Audible sync failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("audible_sync_library_button"),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AppText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.audible_sync_library_button), color = AppText)
                }

                OutlinedButton(
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
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
                    Text(stringResource(R.string.audible_disconnect_button), fontWeight = FontWeight.SemiBold)
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
                Toast.makeText(context, "Audible connected ($count books synced).", Toast.LENGTH_SHORT).show()
                onOpenPicker()
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AudibleLoginDialog(
    marketplace: AudibleMarketplace,
    onDismiss: () -> Unit,
    onConnected: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pkceSession = remember(marketplace) { AudibleAuth.createPkceSession(marketplace) }
    val signInUrl = remember(pkceSession) { AudibleAuth.buildSignInUrl(pkceSession) }

    var registering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(marketplace) {
        onDispose {
            AudibleAccountCoordinator.wipeWebViewSession(webViewRef, marketplace)
            runCatching { webViewRef?.destroy() }
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = {
            if (!registering) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .statusBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                IconButton(enabled = !registering, onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppText)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audible_sign_in_button),
                        color = AppText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${marketplace.audibleDomain} · Web cookies wiped after sign-in",
                        color = AppMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            when {
                registering -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppAccent)
                            Spacer(Modifier.height(12.dp))
                            Text("Registering SoundNest Audible player & syncing library…", color = AppText, fontSize = 14.sp)
                        }
                    }
                }
                errorMessage != null -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Sign-in did not complete", color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(errorMessage.orEmpty(), color = AppMuted, fontSize = 13.sp)
                            Button(
                                onClick = { errorMessage = null },
                                colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
                            ) {
                                Text("Try Again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                runCatching {
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                                        ProfileStore.getInstance().getOrCreateProfile(AudibleAccountCoordinator.EPHEMERAL_WEBVIEW_PROFILE)
                                        WebViewCompat.setProfile(this, AudibleAccountCoordinator.EPHEMERAL_WEBVIEW_PROFILE)
                                    }
                                }
                                AudibleAccountCoordinator.wipeWebViewSession(this, marketplace)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.saveFormData = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val targetUrl = request.url?.toString().orEmpty()
                                        val res = AudibleAccountCoordinator.interceptRedirectUrl(targetUrl) {
                                            AudibleAccountCoordinator.wipeWebViewSession(view, marketplace)
                                        }
                                        return when (res) {
                                            is AudibleAuth.MapLandingResult.NotMapLanding -> false
                                            is AudibleAuth.MapLandingResult.Error -> {
                                                errorMessage = res.description ?: "Amazon returned error: ${res.errorCode}"
                                                true
                                            }
                                            is AudibleAuth.MapLandingResult.Authorized -> {
                                                registering = true
                                                scope.launch {
                                                    runCatching {
                                                        AudibleAccountCoordinator.completeAuthorization(
                                                            context = context,
                                                            session = pkceSession,
                                                            authorizationCode = res.authorizationCode,
                                                        )
                                                    }.onSuccess { books ->
                                                        registering = false
                                                        onConnected(books.size)
                                                    }.onFailure { err ->
                                                        registering = false
                                                        errorMessage = err.message ?: "Audible registration failed."
                                                    }
                                                }
                                                true
                                            }
                                        }
                                    }
                                }
                                webViewRef = this
                                loadUrl(signInUrl)
                            }
                        },
                    )
                }
            }
        }
    }
}
