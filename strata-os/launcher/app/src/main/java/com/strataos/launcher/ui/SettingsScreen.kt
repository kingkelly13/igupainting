package com.strataos.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.launcher.LauncherModel
import com.strataos.launcher.Overlay
import com.strataos.launcher.data.DeviceInfo
import com.strataos.launcher.data.SensorAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Privacy Center plus Strata's own settings. */
@Composable
fun SettingsScreen(model: LauncherModel, requestCalendar: () -> Unit, requestHomeRole: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val apps = model.apps
    val sensors by produceState<List<SensorAccess>?>(null, apps, model.resumeCount) {
        value = withContext(Dispatchers.Default) { DeviceInfo.sensorAccess(context, apps) }
    }
    var expanded by remember { mutableStateOf<String?>(null) }

    // Re-read permission state whenever you come back from a system settings page.
    val resume = model.resumeCount
    val canWrite = remember(resume) { DeviceInfo.canWriteSettings(context) }
    val focusAccess = remember(resume) { DeviceInfo.hasFocusAccess(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(StrataColors.Ink)
            .blockTouchesBelow()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { model.overlay = Overlay.None }
                .padding(end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrataIcon(StrataIcons.ChevronLeft, accent, size = 22.dp)
            Text("Home", color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Privacy",
            style = displayStyle(34.sp, FontWeight.SemiBold),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )

        val list = sensors
        val total = list?.flatMap { it.apps }?.distinctBy { it.packageName }?.size
        Row(
            Modifier
                .padding(start = 4.dp, top = 12.dp)
                .clip(CircleShape)
                .background(StrataColors.Surface)
                .border(1.dp, StrataColors.Line, CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrataIcon(StrataIcons.Shield, accent, size = 15.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                if (total == null) "Checking your apps…" else "$total apps can use your camera, mic or location",
                color = StrataColors.Muted,
                fontSize = 13.sp,
            )
        }

        SectionLabel("Sensor access")
        StrataCard {
            if (list == null) {
                Text("Loading…", color = StrataColors.Muted, modifier = Modifier.padding(18.dp))
            } else {
                list.forEachIndexed { index, sensor ->
                    val open = expanded == sensor.label
                    SettingRow(
                        icon = sensor.icon,
                        title = sensor.label,
                        subtitle = when (sensor.apps.size) {
                            0 -> "No apps"
                            1 -> sensor.apps.first().label
                            else -> sensor.apps.take(2).joinToString(", ") { it.label } +
                                if (sensor.apps.size > 2) " and ${sensor.apps.size - 2} more" else ""
                        },
                        divider = index > 0,
                        trailing = {
                            Text("${sensor.apps.size}", color = StrataColors.Muted, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            StrataIcon(
                                if (open) StrataIcons.ChevronDown else StrataIcons.ChevronRight,
                                StrataColors.Muted,
                                size = 18.dp,
                            )
                        },
                        onClick = { expanded = if (open) null else sensor.label },
                    )
                    if (open) {
                        for (app in sensor.apps) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { model.repo.openAppInfo(app) }
                                    .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app, mono = false, size = 28.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("Manage", color = accent, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        LinkRow("Open Android privacy dashboard") { model.controls.openPrivacyDashboard() }

        SectionLabel("Spaces")
        StrataCard {
            ToggleRow(
                title = "Switch Spaces automatically",
                subtitle = "Work on weekdays during work hours, Personal the rest of the time",
                checked = model.autoSwitch,
                onToggle = { model.updateAutoSwitch(it) },
            )
            StepperRow(
                title = "Work starts",
                value = hourText(model.workStart),
                onMinus = { model.updateWorkHours(model.workStart - 1, model.workEnd) },
                onPlus = { model.updateWorkHours(model.workStart + 1, model.workEnd) },
            )
            StepperRow(
                title = "Work ends",
                value = hourText(model.workEnd),
                onMinus = { model.updateWorkHours(model.workStart, model.workEnd - 1) },
                onPlus = { model.updateWorkHours(model.workStart, model.workEnd + 1) },
            )
        }

        SectionLabel("Look")
        StrataCard {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text("Accent", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Used only for things that are on, selected or live", color = StrataColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (option in Accent.entries) {
                        val selected = option == model.accent
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .selectable(selected = selected, role = Role.RadioButton) { model.updateAccent(option) }
                                .semantics { contentDescription = option.label }
                                .border(2.dp, if (selected) StrataColors.Text else StrataColors.Ink, CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(option.color),
                        )
                    }
                }
            }
            ToggleRow(
                title = "Monochrome icons",
                subtitle = "Themed icons where apps provide them, greyscale otherwise",
                checked = model.monoIcons,
                divider = true,
                onToggle = { model.updateMonoIcons(it) },
            )
        }

        SectionLabel("Permissions for Strata")
        StrataCard {
            PermissionRow(
                title = "Default home app",
                subtitle = "Home button and gesture open Strata",
                allowed = model.isDefaultHome,
                divider = false,
                onClick = requestHomeRole,
            )
            PermissionRow(
                title = "Calendar",
                subtitle = "Shows your next event on the home screen",
                allowed = model.calendarAllowed,
                onClick = requestCalendar,
            )
            PermissionRow(
                title = "Modify system settings",
                subtitle = "Brightness, auto-brightness and rotation lock in Controls",
                allowed = canWrite,
                onClick = { model.controls.requestWriteSettings() },
            )
            PermissionRow(
                title = "Do Not Disturb access",
                subtitle = "Lets the Focus tile turn Do Not Disturb on and off",
                allowed = focusAccess,
                onClick = { model.controls.requestFocusAccess() },
            )
        }

        Text(
            "Strata 0.1 · a launcher concept for Xiaomi 15T",
            color = StrataColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 32.dp),
        )
    }
}

private fun hourText(hour: Int): String = "%02d:00".format(hour)

@Composable
private fun SettingRow(
    icon: String?,
    title: String,
    subtitle: String?,
    divider: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Column {
        if (divider) DividerLine()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                StrataIcon(icon, StrataColors.Text, size = 20.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, color = StrataColors.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    divider: Boolean = false,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        if (divider) DividerLine()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = StrataColors.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(14.dp))
            StrataSwitch(checked)
        }
    }
}

@Composable
private fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column {
        DividerLine()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StepButton(StrataIcons.Minus, "Earlier", onMinus)
            Text(
                value,
                style = displayStyle(18.sp),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            StepButton(StrataIcons.Plus, "Later", onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(StrataColors.Raised)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        StrataIcon(icon, StrataColors.Text, size = 18.dp)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    allowed: Boolean,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    SettingRow(
        icon = null,
        title = title,
        subtitle = subtitle,
        divider = divider,
        onClick = onClick,
        trailing = {
            if (allowed) {
                StrataIcon(StrataIcons.Check, accent, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text("Allowed", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Allow", color = StrataColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                StrataIcon(StrataIcons.ChevronRight, StrataColors.Muted, size = 18.dp)
            }
        },
    )
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        StrataIcon(StrataIcons.ChevronRight, accent, size = 18.dp)
    }
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(StrataColors.Line),
    )
}
