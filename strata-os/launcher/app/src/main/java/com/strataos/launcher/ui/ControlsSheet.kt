package com.strataos.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.launcher.LauncherModel
import com.strataos.launcher.Overlay
import com.strataos.launcher.data.ControlsState
import kotlinx.coroutines.delay

/** The Controls sheet: everything in the bottom of the screen, within thumb reach. */
@Composable
fun ControlsSheet(model: LauncherModel) {
    val controls = model.controls
    val accent = LocalAccent.current
    var state by remember { mutableStateOf(ControlsState()) }
    fun refresh() {
        state = controls.read()
    }
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(1000)
        }
    }
    val close = { model.overlay = Overlay.None }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(StrataColors.Sheet)
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal > threshold) close() },
                    onVerticalDrag = { _, amount -> dragTotal += amount },
                )
            }
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(CircleShape)
                .background(StrataColors.LineStrong),
        )
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigTile(
                label = "Wi-Fi",
                sub = when {
                    !state.wifiOn -> "Off"
                    state.wifiConnected -> "Connected"
                    else -> "On"
                },
                icon = StrataIcons.Wifi,
                on = state.wifiOn,
                modifier = Modifier.weight(1f),
            ) { controls.openWifi() }
            val dataOn = state.dataEnabled ?: state.cellularActive
            BigTile(
                label = "Mobile data",
                sub = when {
                    state.cellularActive -> "In use"
                    dataOn -> "On"
                    else -> "Off"
                },
                icon = StrataIcons.Data,
                on = dataOn,
                modifier = Modifier.weight(1f),
            ) { controls.openInternet() }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigTile(
                label = "Bluetooth",
                sub = if (state.bluetoothOn) "On" else "Off",
                icon = StrataIcons.Bluetooth,
                on = state.bluetoothOn,
                modifier = Modifier.weight(1f),
            ) { controls.openBluetooth() }
            BigTile(
                label = "Focus",
                sub = if (state.focusOn) "Priority only" else "Off",
                icon = StrataIcons.Moon,
                on = state.focusOn,
                modifier = Modifier.weight(1f),
            ) {
                controls.toggleFocus()
                refresh()
            }
        }

        Spacer(Modifier.height(18.dp))
        Row {
            if (controls.hasTorch) {
                SmallToggle("Torch", StrataIcons.Torch, controls.torchOn, Modifier.weight(1f)) {
                    controls.setTorch(!controls.torchOn)
                }
            }
            SmallToggle("Vibrate", StrataIcons.Vibrate, state.vibrate, Modifier.weight(1f)) {
                controls.setVibrate(!state.vibrate)
                refresh()
            }
            SmallToggle("Rotation lock", StrataIcons.Rotate, state.rotationLocked, Modifier.weight(1f)) {
                controls.setRotationLocked(!state.rotationLocked)
                refresh()
            }
            SmallToggle("Auto brightness", StrataIcons.AutoBrightness, state.autoBrightness, Modifier.weight(1f)) {
                controls.setAutoBrightness(!state.autoBrightness)
                refresh()
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            SmallToggle("Location", StrataIcons.Location, state.locationOn, Modifier.weight(1f)) { controls.openLocation() }
            SmallToggle("Airplane", StrataIcons.Plane, state.airplaneOn, Modifier.weight(1f)) { controls.openAirplane() }
            SmallToggle("Battery saver", StrataIcons.Leaf, state.batterySaver, Modifier.weight(1f)) { controls.openBatterySaver() }
            SmallToggle("Cast", StrataIcons.Cast, false, Modifier.weight(1f)) { controls.openCast() }
        }

        Spacer(Modifier.height(18.dp))
        LevelSlider(
            label = "Brightness",
            icon = StrataIcons.Sun,
            value = state.brightness,
            enabled = state.canWriteSettings,
            onChange = { controls.setBrightness(it) },
            onLockedClick = { controls.requestWriteSettings() },
        )
        Spacer(Modifier.height(10.dp))
        LevelSlider(
            label = "Media volume",
            icon = StrataIcons.Volume,
            value = state.volume,
            enabled = true,
            onChange = { controls.setVolume(it) },
            onLockedClick = {},
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "All settings",
                color = StrataColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { controls.openSettings() }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { model.overlay = Overlay.Settings }
                    .padding(start = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Privacy and settings", color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                StrataIcon(StrataIcons.ChevronRight, accent, size = 18.dp)
            }
        }
    }
}

/** Dims the home screen behind the sheet; tapping it closes the sheet. */
@Composable
fun ControlsScrim(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(StrataColors.Scrim.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    )
}

@Composable
private fun BigTile(
    label: String,
    sub: String,
    icon: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val fg = if (on) StrataColors.OnAccent else StrataColors.Text
    Row(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (on) accent else StrataColors.Raised)
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { stateDescription = if (on) "On" else "Off" }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on) Color.Black.copy(alpha = 0.12f) else StrataColors.Line),
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(icon, fg, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                sub,
                color = if (on) StrataColors.OnAccentMuted else StrataColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallToggle(label: String, icon: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { stateDescription = if (on) "On" else "Off" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (on) accent else StrataColors.Raised),
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(icon, if (on) StrataColors.OnAccent else StrataColors.Text, size = 22.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = StrataColors.Muted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** A big touch-friendly level slider. When locked, tapping it asks for the needed permission. */
@Composable
private fun LevelSlider(
    label: String,
    icon: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onLockedClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value

    fun update(fraction: Float) {
        local = fraction.coerceIn(0f, 1f)
        onChange(local)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(StrataColors.Raised)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        update(target)
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            update(offset.x / width)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            update(local + amount / width)
                        },
                    )
                }
            }
            .pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (enabled) update(offset.x / width) else onLockedClick()
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceAtLeast(0.14f))
                .background(if (enabled) accent else StrataColors.LineStrong),
        )
        StrataIcon(
            icon,
            if (enabled) StrataColors.OnAccent else StrataColors.Text,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
            size = 22.dp,
        )
        if (!enabled) {
            Text(
                "Tap to allow",
                color = StrataColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            )
        }
    }
}
