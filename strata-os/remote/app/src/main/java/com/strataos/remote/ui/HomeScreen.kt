package com.strataos.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.remote.RemoteModel
import com.strataos.remote.data.Device
import com.strataos.remote.data.DeviceKind

fun kindIcon(kind: DeviceKind): String = when (kind) {
    DeviceKind.TV -> StrataIcons.Tv
    DeviceKind.Streaming -> StrataIcons.Box
    DeviceKind.Lights -> StrataIcons.Bulb
    DeviceKind.Audio -> StrataIcons.Speaker
    DeviceKind.Projector -> StrataIcons.Projector
    DeviceKind.Other -> StrataIcons.Remote
}

@Composable
fun HomeScreen(model: RemoteModel, export: (Device) -> Unit) {
    var renaming by remember { mutableStateOf<Device?>(null) }
    var deleting by remember { mutableStateOf<Device?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        // Glance: read-only, at the top.
        Spacer(Modifier.height(28.dp))
        Text("Remotes", style = displayStyle(46.sp), modifier = Modifier.padding(start = 4.dp))
        BlasterStatus(model, Modifier.padding(start = 4.dp, top = 10.dp))

        // Browse.
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (model.devices.isEmpty()) {
                item { EmptyCard() }
            }
            items(model.devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    onOpen = { model.open(device) },
                    onRename = { renaming = device },
                    onExport = { export(device) },
                    onDelete = { deleting = device },
                )
            }
            item {
                Column {
                    SectionLabel("Settings")
                    SettingsCard(model)
                }
            }
        }

        // Act: the one action, in thumb reach.
        PillButton(
            text = "Add a remote",
            onClick = { model.startSetup() },
            icon = StrataIcons.Plus,
            prominent = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
        )
    }

    renaming?.let { device ->
        RenameDialog(device.name, onDismiss = { renaming = null }) {
            model.rename(device, it)
            renaming = null
        }
    }
    deleting?.let { device ->
        DeleteDialog(device.name, onDismiss = { deleting = null }) {
            model.delete(device)
            deleting = null
        }
    }
}

@Composable
private fun BlasterStatus(model: RemoteModel, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val blaster = model.blaster
    val ranges = blaster.frequencyRanges
    val text = when {
        !blaster.hasEmitter -> "No IR blaster found on this phone"
        ranges.isEmpty() -> "IR blaster ready"
        else -> "IR blaster ready · %d–%d kHz".format(ranges.minOf { it.first } / 1000, ranges.maxOf { it.last } / 1000)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (blaster.hasEmitter) accent else StrataColors.LineStrong),
        )
        Text(text, color = StrataColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EmptyCard() {
    StrataCard {
        Column(Modifier.padding(20.dp)) {
            StrataIcon(StrataIcons.Remote, StrataColors.Muted, size = 28.dp)
            Text(
                "No remotes yet",
                style = displayStyle(22.sp, FontWeight.SemiBold),
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Add your TV, streaming box, LED lights or anything else with an infrared remote. " +
                    "Strata Remote finds the right code by testing them with you, or imports one from a file.",
                color = StrataColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    StrataCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StrataColors.Raised),
                contentAlignment = Alignment.Center,
            ) {
                StrataIcon(kindIcon(device.kind), StrataColors.Text, size = 24.dp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(device.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val count = device.buttons.size
                Text(
                    "${device.kind.label} · $count ${if (count == 1) "button" else "buttons"}",
                    color = StrataColors.Muted,
                    fontSize = 13.sp,
                )
            }
            Box {
                IconAction(StrataIcons.More, "More for ${device.name}", { menu = true }, StrataColors.Muted)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Rename", StrataIcons.Edit) { menu = false; onRename() }
                    MenuItem("Export as .ir file", StrataIcons.Export) { menu = false; onExport() }
                    MenuItem("Delete", StrataIcons.Trash) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
fun MenuItem(label: String, icon: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        leadingIcon = { StrataIcon(icon, StrataColors.Text, size = 18.dp) },
        onClick = onClick,
    )
}

@Composable
private fun SettingsCard(model: RemoteModel) {
    StrataCard {
        ToggleRow(
            title = "Volume buttons control the remote",
            subtitle = "While a remote is open, your phone's volume buttons send its volume codes.",
            checked = model.volumeKeys,
            onChange = model::updateVolumeKeys,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(StrataColors.Line),
        )
        ToggleRow(
            title = "Vibrate on press",
            subtitle = "A short tick each time a code goes out.",
            checked = model.haptics,
            onChange = model::updateHaptics,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = StrataColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        StrataSwitch(checked)
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StrataColors.Sheet,
        title = { Text("Rename remote") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteDialog(name: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StrataColors.Sheet,
        title = { Text("Delete $name?") },
        text = { Text("Its buttons and codes are removed from this phone.", color = StrataColors.Muted) },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
