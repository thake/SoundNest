/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.jam.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.thake.soundnest.R
import io.github.thake.soundnest.api.JamAvailability
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.feature.parental.settings.FeatureGates
import io.github.thake.soundnest.feature.speakers.jam.playback.JamLinks
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green: Color get() = AppAccent

/** Spotify Jam: listen together. Free accounts can join (Spotify keeps starting one to Premium). */
@Composable
fun JamSection(onDismiss: () -> Unit) {
    val jam = SoundNestRuntime.api.jam
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by jam.session.collectAsState()
    val availability by produceState<JamAvailability?>(null) {
        value = runCatching { jam.refresh(); jam.availability() }.getOrDefault(JamAvailability.UNKNOWN)
    }
    fun failed() = Toast.makeText(context, context.getString(R.string.jam_failed), Toast.LENGTH_SHORT).show()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.jam_title), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val s = session
        if (s != null) {
            Text(stringResource(R.string.jam_members, s.members.size), color = Green, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            // A member: where the music plays, as Spotify let this phone in (see JamSession.listensHere).
            if (!s.isHost) Text(
                stringResource(if (s.listensHere) R.string.jam_plays_here else R.string.jam_plays_on_host),
                color = AppMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            Text(
                stringResource(R.string.jam_invite),
                color = AppText,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        val url = runCatching { jam.inviteLink() }.getOrNull() ?: return@launch failed()
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }.padding(vertical = 10.dp),
            )
            Text(
                stringResource(R.string.jam_leave),
                color = AppText,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { runCatching { if (s.isHost) jam.end() else jam.leave() }; onDismiss() }
                }.padding(vertical = 10.dp),
            )
            return@Column
        }
        if (availability == JamAvailability.AVAILABLE) Text(
            stringResource(R.string.jam_start),
            color = Green,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                scope.launch { if (runCatching { jam.start() }.isFailure) failed() }
            }.padding(vertical = 10.dp),
        )
        // Spotify refuses to start a Jam on a free account (FREE_USER_NOT_ALLOWED); joining one works.
        if (availability == JamAvailability.RESTRICTED && session == null) Text(
            stringResource(R.string.jam_start_premium),
            color = AppMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        // Join: scan the QR code another phone shows in its Jam.
        Text(
            stringResource(R.string.jam_scan),
            color = Green,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { code ->
                        val raw = code.rawValue.orEmpty()
                        joinJam(context, raw, onDismiss)
                    }
                    // No scanner on this phone (Play services missing or too old): say so.
                    .addOnFailureListener { failed() }
            }.padding(vertical = 10.dp),
        )
        // Join: an invite link copied from a message.
        Text(
            stringResource(R.string.jam_paste_link),
            color = Green,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val raw = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                joinJam(context, raw, onDismiss)
            }.padding(vertical = 10.dp),
        )
    }
}

/**
 * Joins the Jam of the invite [raw] (a link, a QR code's text) on the app's scope, not the sheet's.
 * Reported: "Spotify refused the Jam" while the phone was in it: the sheet closes as soon as the
 * Jam's device shows up, which cancelled the join still waiting on the page, and that cancellation
 * was shown as a failure. Only an answer Spotify refused (or a link that is not one) says so now.
 */
private fun joinJam(context: Context, raw: String, onDismiss: () -> Unit) {
    if (!FeatureGates.canUseJam(context)) {
        onDismiss()
        return
    }
    fun failed() = Toast.makeText(context.applicationContext, context.getString(R.string.jam_failed), Toast.LENGTH_SHORT).show()
    onDismiss()
    SoundNestRuntime.scope.launch {
        // A short link (spotify.link) is followed to its token first.
        val token = JamLinks.token(raw)
            ?: return@launch withContext(Dispatchers.Main) { failed() }
        val error = runCatching { SoundNestRuntime.api.jam.join(token) }.exceptionOrNull()
        if (error != null && error !is CancellationException) {
            Logbook.log("jam: join failed: ${error.message}")
            withContext(Dispatchers.Main) { failed() }
        }
    }
}
