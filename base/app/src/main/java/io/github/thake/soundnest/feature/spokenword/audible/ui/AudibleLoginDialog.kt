/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.thake.soundnest.R
import io.github.thake.soundnest.audible.AudibleAuth
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleAccountCoordinator
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AudibleLoginDialog(
    marketplace: AudibleMarketplace,
    onDismiss: () -> Unit,
    onConnected: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    val pkceSession = remember(marketplace, attempt) { AudibleAuth.createPkceSession(marketplace) }
    val signInUrl = remember(pkceSession) { AudibleAuth.buildSignInUrl(pkceSession) }
    var redirectHandled by remember(attempt) { mutableStateOf(false) }

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
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = AppText)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audible_sign_in_button),
                        color = AppText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.audible_cookies_notice, marketplace.audibleDomain),
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
                            Text(stringResource(R.string.audible_login_registering), color = AppText, fontSize = 14.sp)
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
                            Text(stringResource(R.string.audible_login_error), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(errorMessage.orEmpty(), color = AppMuted, fontSize = 13.sp)
                            Button(
                                onClick = {
                                    errorMessage = null
                                    attempt++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
                            ) {
                                Text(stringResource(R.string.audible_login_try_again), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    val handleRedirect: (WebView, String) -> Boolean = { view, targetUrl ->
                        if (redirectHandled) {
                            true
                        } else {
                            val res = AudibleAccountCoordinator.interceptRedirectUrl(targetUrl) {
                                AudibleAccountCoordinator.wipeWebViewSession(view, marketplace)
                            }
                            when (res) {
                                is AudibleAuth.MapLandingResult.NotMapLanding -> false
                                is AudibleAuth.MapLandingResult.Error -> {
                                    redirectHandled = true
                                    view.stopLoading()
                                    AudibleAccountCoordinator.wipeWebViewSession(view, marketplace)
                                    errorMessage = res.description ?: "Amazon returned error: ${res.errorCode}"
                                    true
                                }
                                is AudibleAuth.MapLandingResult.Authorized -> {
                                    redirectHandled = true
                                    view.stopLoading()
                                    AudibleAccountCoordinator.wipeWebViewSession(view, marketplace)
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
                                        return handleRedirect(view, request.url?.toString().orEmpty())
                                    }

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String?,
                                        favicon: Bitmap?,
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        if (url != null) {
                                            handleRedirect(view, url)
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
