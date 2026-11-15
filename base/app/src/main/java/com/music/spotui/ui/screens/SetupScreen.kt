package com.music.spotui.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import app.myspoti.power.BatteryExemption
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.navigation.isImmersive
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone
import kotlinx.coroutines.delay

object Setup {
    private const val KEY = "SetupDone"

    fun needed(context: Context) =
        !permissionsDone(context) ||
            !com.music.spotui.data.preferences.isQualityChosen(context) ||
            !com.music.spotui.data.preferences.LookPref.isChosen(context)

    fun permissionsDone(context: Context) = prefs(context).getBoolean(KEY, false)
    fun done(context: Context) = prefs(context).edit().putBoolean(KEY, true).apply()

    internal fun steps(context: Context): List<Int> {
        val needPerms = !permissionsDone(context)
        return listOfNotNull(
            STEP_INTRO.takeIf { needPerms },
            STEP_LOOK.takeIf { !com.music.spotui.data.preferences.LookPref.isChosen(context) },
            STEP_QUALITY.takeIf { !com.music.spotui.data.preferences.isQualityChosen(context) },
            STEP_PERMISSIONS.takeIf { needPerms },
        ).ifEmpty { listOf(STEP_PERMISSIONS) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)
}

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val order = remember { Setup.steps(context) }
    var index by remember { mutableIntStateOf(0) }
    val steps = order.size
    fun next() { if (index + 1 < order.size) index++ else onDone() }
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    androidx.activity.compose.BackHandler(enabled = index > 0) { index-- }
    val immersive = isImmersive()
    Box(Modifier.fillMaxSize().background(if (immersive) Immersive.Page else AppBackground)) {
        androidx.compose.animation.Crossfade(immersive, animationSpec = tween(350), label = "setupBackdrop") { imm ->
            if (imm) Box(Modifier.fillMaxSize()) {
                com.music.spotui.ui.look.MeshGradientBackground(com.music.spotui.ui.look.LookMeshPalettes.first(), animated = false)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            } else Glow(Modifier.fillMaxSize(), alpha = 0.16f)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (steps > 1) StepDots(count = steps, current = index, Modifier.padding(start = 20.dp, top = 16.dp))
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    if (reduce) EnterTransition.None togetherWith ExitTransition.None
                    else (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(250)))
                },
                label = "setupStep",
                modifier = Modifier.weight(1f),
            ) { i ->
                when (order[i]) {
                    STEP_INTRO -> IntroStep(onNext = ::next)
                    STEP_QUALITY -> QualityStep(onNext = {
                        com.music.spotui.data.preferences.setQualityChosen(context)
                        next()
                    })
                    STEP_LOOK -> com.music.spotui.ui.look.LookStep(onNext = {
                        com.music.spotui.data.preferences.LookPref.setChosen(context)
                        next()
                    })
                    else -> PermissionsStep(onNext = { Setup.done(context); next() })
                }
            }
        }
    }
}

internal const val STEP_INTRO = 0
internal const val STEP_LOOK = 1
internal const val STEP_QUALITY = 2
internal const val STEP_PERMISSIONS = 4

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        for (i in 0 until count) {
            val reduce = com.music.spotui.ui.look.rememberReduceMotion()
            val w by animateDpAsState(if (i == current) 22.dp else 8.dp, if (reduce) snap() else tween(350), label = "dot")
            val on = if (isImmersive()) Color.White else AppAccent
            val c by animateColorAsState(if (i == current) on else AppText.copy(alpha = 0.25f), if (reduce) snap() else tween(350), label = "dotColor")
            Box(Modifier.height(6.dp).width(w).clip(RoundedCornerShape(50)).background(c))
        }
    }
}

@Composable
private fun Modifier.riseIn(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    LaunchedEffect(Unit) { if (reduce) a.snapTo(1f) else { delay(80L + index * 70L); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) } }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 18.dp.toPx() }
}

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(800); tick++ } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var row = 0
        SetupTitle(stringResource(R.string.setup_title), Modifier.riseIn(row++))
        SetupSubtitle(stringResource(R.string.setup_subtitle), Modifier.riseIn(row++))
        Spacer(Modifier.height(8.dp))
        @Suppress("UNUSED_EXPRESSION") tick
        if (Build.VERSION.SDK_INT >= 33) SetupRow(
            stringResource(R.string.setup_notifications_title), stringResource(R.string.setup_notifications_detail),
            granted(context, Manifest.permission.POST_NOTIFICATIONS), Modifier.riseIn(row++),
        ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (Build.VERSION.SDK_INT >= 31) SetupRow(
            stringResource(R.string.setup_bluetooth_title), stringResource(R.string.setup_bluetooth_detail),
            granted(context, Manifest.permission.BLUETOOTH_CONNECT), Modifier.riseIn(row++),
        ) { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
        SetupRow(
            stringResource(R.string.setup_battery_title), stringResource(R.string.setup_battery_detail),
            BatteryExemption.isExempt(context), Modifier.riseIn(row++),
        ) { BatteryExemption.request(context) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.riseIn(row)) {
            SetupButton(stringResource(R.string.setup_continue), onNext)
        }
    }
}

@Composable
private fun QualityStep(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupTitle(stringResource(R.string.setup_quality_title), Modifier.padding(horizontal = 20.dp).riseIn(0))
        SetupSubtitle(stringResource(R.string.setup_quality_subtitle), Modifier.padding(horizontal = 20.dp).riseIn(1))
        Box(Modifier.riseIn(2)) { com.music.spotui.ui.components.StreamingQualityPicker(Modifier.padding(top = 8.dp)) }
        SetupButton(stringResource(R.string.setup_continue), onNext, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(50.dp).riseIn(3))
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var row = 0
        SetupTitle(stringResource(R.string.setup_intro_title), Modifier.riseIn(row++))
        SetupSubtitle(stringResource(R.string.setup_intro_subtitle), Modifier.riseIn(row++))
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Rounded.GraphicEq, stringResource(R.string.setup_intro_spotify_title), stringResource(R.string.setup_intro_spotify_detail), Modifier.riseIn(row++))
        InfoRow(Icons.Rounded.AutoAwesome, stringResource(R.string.setup_intro_more_title), stringResource(R.string.setup_intro_more_detail), Modifier.riseIn(row++))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.riseIn(row), horizontalAlignment = Alignment.CenterHorizontally) {
            SetupButton(stringResource(R.string.setup_continue), onNext, Modifier.fillMaxWidth().height(50.dp))
        }
    }
}

@Composable
private fun SetupTitle(text: String, modifier: Modifier = Modifier) {
    if (isImmersive()) Text(text, style = Immersive.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold), modifier = modifier)
    else Text(text, color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun SetupSubtitle(text: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 14.sp) {
    if (isImmersive()) Text(text, style = Immersive.Subhead.copy(fontSize = size, color = Color.White.copy(alpha = 0.72f)), modifier = modifier)
    else Text(text, color = AppMuted, fontSize = size, modifier = modifier)
}

@Composable
private fun SetupButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth().height(50.dp)) {
    val immersive = isImmersive()
    Button(
        onClick = onClick,
        shape = if (immersive) Immersive.Button else ButtonDefaults.shape,
        colors = ButtonDefaults.buttonColors(containerColor = if (immersive) Color.White else AppAccent, contentColor = Color.Black),
        modifier = modifier,
    ) {
        Text(text, fontFamily = if (immersive) com.music.spotui.ui.look.ImmersiveSans else null, fontSize = 16.sp,
            fontWeight = if (immersive) FontWeight.SemiBold else FontWeight.Bold)
    }
}

@Composable
private fun Modifier.setupCard(): Modifier =
    if (isImmersive()) this.fillMaxWidth().clip(Immersive.Card).background(Immersive.Raised.copy(alpha = 0.92f))
    else this.fillMaxWidth().background(tone(0xFF1E1E1E), RoundedCornerShape(12.dp))

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    val immersive = isImmersive()
    val accent = if (immersive) Immersive.Accent else AppAccent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.setupCard().padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            CardTitle(title)
            CardDetail(detail)
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, on: Boolean, modifier: Modifier = Modifier, onEnable: () -> Unit) {
    val accent = if (isImmersive()) Immersive.Accent else AppAccent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.setupCard().padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            CardTitle(title)
            CardDetail(detail, lineHeight = 17.sp)
        }
        AnimatedContent(
            targetState = on,
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "allowed",
        ) { allowed ->
            if (allowed) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.padding(start = 12.dp).size(28.dp))
            else TextButton(onClick = onEnable) { Text(stringResource(R.string.setup_enable), color = accent, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    if (isImmersive()) Text(text, style = Immersive.BodyStrong)
    else Text(text, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CardDetail(text: String, lineHeight: androidx.compose.ui.unit.TextUnit = 18.sp) {
    if (isImmersive()) Text(text, style = Immersive.Caption.copy(fontSize = 13.sp, lineHeight = lineHeight))
    else Text(text, color = AppMuted, fontSize = 13.sp, lineHeight = lineHeight)
}
