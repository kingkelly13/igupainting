package com.strataos.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.remote.EditTarget
import com.strataos.remote.RemoteModel
import com.strataos.remote.data.Device
import com.strataos.remote.data.DeviceKind
import com.strataos.remote.data.Keys

private enum class Tab(val label: String) { Main("Remote"), Lights("Lights"), Numbers("Numbers"), All("All buttons") }

/** The icon a standard key is drawn with. */
fun keyIcon(key: String): String? = when (key) {
    "power", "power_on", "power_off" -> StrataIcons.Power
    "input" -> StrataIcons.Input
    "mute" -> StrataIcons.Mute
    "back" -> StrataIcons.Back
    "home" -> StrataIcons.Home
    "menu" -> StrataIcons.Menu
    "exit" -> StrataIcons.Close
    "info" -> StrataIcons.Info
    "guide" -> StrataIcons.Guide
    "prev_ch" -> StrataIcons.History
    "play" -> StrataIcons.Play
    "pause" -> StrataIcons.Pause
    "play_pause" -> StrataIcons.PlayPause
    "stop" -> StrataIcons.Stop
    "rewind" -> StrataIcons.Rewind
    "fast_forward" -> StrataIcons.FastForward
    "record" -> StrataIcons.Record
    "vol_up", "ch_up" -> StrataIcons.Plus
    "vol_down", "ch_down" -> StrataIcons.Minus
    "up" -> StrataIcons.ChevronUp
    "down" -> StrataIcons.ChevronDown
    "left" -> StrataIcons.ChevronLeft
    "right" -> StrataIcons.ChevronRight
    "bright_up" -> StrataIcons.Sun
    "bright_down" -> StrataIcons.SunSmall
    "flash", "strobe", "fade", "smooth" -> StrataIcons.Sparkle
    else -> null
}

@Composable
fun RemoteScreen(model: RemoteModel, device: Device, export: (Device) -> Unit) {
    val view = LocalView.current
    val host = remember(model, device) {
        object : KeyHost {
            override val editing: Boolean get() = model.editing
            override fun has(key: String) = device.has(key)
            override fun isPressed(key: String) = model.pressedKey == key
            override fun down(key: String) {
                if (model.editing) {
                    model.editTarget = EditTarget(key, isNew = !device.has(key))
                    return
                }
                val button = device.button(key)
                if (button == null) {
                    model.report("${Keys.label(key)} has no code yet. Tap Edit to add one.", error = true)
                    return
                }
                if (model.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                model.press(button)
            }
            override fun up(key: String) {
                if (!model.editing) model.release()
            }
        }
    }
    val tabs = if (device.kind == DeviceKind.Lights) listOf(Tab.Lights, Tab.All) else listOf(Tab.Main, Tab.Numbers, Tab.All)
    var tab by remember(device.id) { mutableStateOf(tabs.first()) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            TopBar(device.name, onBack = { model.goHome() }) {
                if (model.editing) {
                    DoneAction { model.editing = false }
                } else {
                    IconAction(StrataIcons.Edit, "Edit buttons", { model.editing = true; model.status = null })
                }
                Box {
                    IconAction(StrataIcons.More, "More", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem("Rename", StrataIcons.Edit) { menu = false; renaming = true }
                        MenuItem("Export as .ir file", StrataIcons.Export) { menu = false; export(device) }
                        MenuItem("Delete", StrataIcons.Trash) { menu = false; deleting = true }
                    }
                }
            }
            StatusLine(model, Modifier.padding(start = 4.dp, bottom = 12.dp))
            Segmented(tabs.map { it to it.label }, tab, { tab = it })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
            ) {
                when (tab) {
                    Tab.Main -> MainPad(host)
                    Tab.Lights -> LightsPad(host)
                    Tab.Numbers -> NumberPad(host)
                    Tab.All -> AllButtons(model, device, host)
                }
            }
        }

        model.editTarget?.let { target -> ButtonEditor(model, device, target) }
    }

    if (renaming) {
        RenameDialog(device.name, onDismiss = { renaming = false }) {
            model.rename(device, it)
            renaming = false
        }
    }
    if (deleting) {
        DeleteDialog(device.name, onDismiss = { deleting = false }) {
            deleting = false
            model.delete(device)
        }
    }
}

@Composable
private fun DoneAction(onClick: () -> Unit) {
    val accent = LocalAccent.current
    Text(
        "Done",
        color = StrataColors.OnAccent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** The glance line under the title: what just went out, or what to do. */
@Composable
private fun StatusLine(model: RemoteModel, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val live = model.pressedKey != null
    val text = model.status ?: when {
        model.editing -> "Editing. Tap any button to set or change its code."
        !model.blaster.hasEmitter -> "This phone has no IR blaster, so codes can't be sent"
        else -> "Point the top of your phone at the device"
    }
    Row(modifier.heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) accent else if (model.statusIsError) StrataColors.LineStrong else StrataColors.Line),
        )
        Text(
            text,
            color = if (model.statusIsError) StrataColors.Text else StrataColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A row of equal keys. Keys without a code are left out unless editing or [keepMissing]. */
@Composable
private fun KeyRow(host: KeyHost, keys: List<String>, height: Int = 56, keepMissing: Boolean = true, labels: Boolean = false) {
    val shown = if (keepMissing || host.editing) keys else keys.filter { host.has(it) }
    if (shown.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (key in shown) {
            StandardKey(key, host, Modifier.weight(1f).height(height.dp), labels)
        }
    }
}

@Composable
private fun StandardKey(key: String, host: KeyHost, modifier: Modifier, withLabel: Boolean = false) {
    val icon = keyIcon(key)
    RemoteKey(
        key = key,
        host = host,
        modifier = modifier,
        label = if (icon == null || withLabel) Keys.label(key) else null,
        icon = icon,
    )
}

@Composable
private fun MainPad(host: KeyHost) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 12.dp
        val dpad = (maxWidth - 64.dp * 2 - gap * 2).coerceIn(150.dp, 240.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            KeyRow(host, listOf("power", "input", "mute"), labels = true)
            val media = listOf("rewind", "play", "play_pause", "pause", "stop", "fast_forward")
            if (host.editing) {
                KeyRow(host, listOf("rewind", "play", "pause", "stop", "fast_forward"))
            } else {
                KeyRow(host, media, keepMissing = false)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Rocker("vol_up", "vol_down", "VOL", host, Modifier.height(dpad))
                DPad(host, size = dpad)
                Rocker("ch_up", "ch_down", "CH", host, Modifier.height(dpad), StrataIcons.ChevronUp, StrataIcons.ChevronDown)
            }
            KeyRow(host, listOf("back", "home", "menu", "exit"), height = 60, labels = true)
        }
    }
}

@Composable
private fun NumberPad(host: KeyHost) {
    BottomAligned {
        val colours = listOf("red", "green", "yellow", "blue")
        if (host.editing || colours.any { host.has(it) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val swatches = listOf(Color(0xFFE5484D), Color(0xFF30A46C), Color(0xFFF5D90A), Color(0xFF3E63DD))
                colours.forEachIndexed { i, key ->
                    RemoteKey(
                        key = key,
                        host = host,
                        modifier = Modifier.weight(1f).height(36.dp),
                        label = Keys.label(key),
                        swatch = swatches[i],
                        shape = CircleShape,
                        fontSize = 12,
                    )
                }
            }
        }
        KeyRow(host, listOf("guide", "info", "record"), keepMissing = false, labels = true)
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (n in row) DigitKey(n, host, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StandardKey("prev_ch", host, Modifier.weight(1f).height(64.dp), withLabel = true)
            DigitKey(0, host, Modifier.weight(1f))
            RemoteKey("ok", host, Modifier.weight(1f).height(64.dp), label = "Enter")
        }
    }
}

@Composable
private fun DigitKey(n: Int, host: KeyHost, modifier: Modifier) {
    RemoteKey(
        key = "num_$n",
        host = host,
        modifier = modifier.height(64.dp),
        label = "$n",
        fontSize = 24,
    )
}

/** Content that sits at the bottom of the space, in thumb reach, and scrolls if it must. */
@Composable
private fun BottomAligned(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

/** The 24-key LED remote's layout: on/off, brightness, then R/G/B shade columns and effects. */
@Composable
private fun LightsPad(host: KeyHost) {
    BottomAligned {
        val power = listOf("power_on", "power_off", "power")
        val shownPower = if (host.editing) power else power.filter { host.has(it) }.ifEmpty { listOf("power_on", "power_off") }
        KeyRow(host, shownPower, labels = true)
        KeyRow(host, listOf("bright_down", "bright_up", "play_pause").filter { it != "play_pause" || host.has(it) }, labels = true)
        val extras = listOf("led_w", "flash", "strobe", "fade", "smooth")
        for (shade in 0..4) {
            val suffix = if (shade == 0) "" else "$shade"
            val keys = listOf("led_r$suffix", "led_g$suffix", "led_b$suffix", extras[shade])
            if (!host.editing && keys.none { host.has(it) }) continue
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (key in keys) LightKey(key, host, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LightKey(key: String, host: KeyHost, modifier: Modifier) {
    val std = Keys.get(key)
    val swatch = std?.swatch
    if (swatch != null) {
        RemoteKey(
            key = key,
            host = host,
            modifier = modifier.aspectRatio(1f),
            label = std.label,
            swatch = Color(0xFF000000L or swatch.toLong()),
            shape = CircleShape,
            fontSize = 11,
        )
    } else {
        RemoteKey(
            key = key,
            host = host,
            modifier = modifier.aspectRatio(1f),
            label = Keys.label(key),
            icon = keyIcon(key),
            shape = CircleShape,
        )
    }
}

/** Every button the remote has, including imported ones with no place on the main layout. */
@Composable
private fun AllButtons(model: RemoteModel, device: Device, host: KeyHost) {
    val order = Keys.all.map { it.id }
    val buttons = device.buttons.sortedBy { b -> order.indexOf(b.key).let { if (it < 0) Int.MAX_VALUE else it } }
    if (buttons.isEmpty() && !host.editing) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No buttons yet", style = displayStyle(22.sp, FontWeight.SemiBold))
            Text(
                "Tap Edit, then any button, to add its code.",
                color = StrataColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (host.editing) {
            item(key = "__add") {
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .height(64.dp)
                        .clip(shape)
                        .background(StrataColors.Raised)
                        .clickable(role = Role.Button) {
                            model.editTarget = EditTarget(device.newCustomKey(), isNew = true)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StrataIcon(StrataIcons.Plus, StrataColors.Text, size = 18.dp)
                        Text("Add", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        items(buttons, key = { it.key }) { button ->
            RemoteKey(
                key = button.key,
                host = host,
                modifier = Modifier.height(64.dp),
                label = button.label,
                hold = false,
                fontSize = 13,
            )
        }
    }
}
