package com.strataos.launcher.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.launcher.LauncherModel
import com.strataos.launcher.Overlay
import com.strataos.launcher.Space
import com.strataos.launcher.data.AppEntry

@Composable
fun HomeScreen(model: LauncherModel, requestCalendar: () -> Unit, requestHomeRole: () -> Unit) {
    val accent = LocalAccent.current
    val space = model.activeSpace
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(StrataColors.Ink)
            .pointerInput(Unit) {
                val threshold = 56.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        when {
                            dragTotal < -threshold -> model.overlay = Overlay.Drawer
                            dragTotal > threshold -> model.controls.expandNotifications()
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        dragTotal += amount
                        change.consume()
                    },
                )
            },
    ) {
        StrataWallpaper(accent)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(12.dp))
            SpaceSwitcher(model, space)
            SpaceCaption(model)
            if (!model.isDefaultHome) DefaultHomeBanner(requestHomeRole)
            ContextWidget(model, space, requestCalendar)
            AppGrid(model, space)
            Spacer(Modifier.weight(1f))
            SearchRow(model)
            Dock(model)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SpaceSwitcher(model: LauncherModel, active: Space) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (space in Space.entries) {
            val selected = space == active
            val fg = if (selected) StrataColors.OnAccent else StrataColors.Text
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(if (selected) accent else StrataColors.Surface)
                    .border(1.dp, if (selected) accent else StrataColors.Line, shape)
                    .selectable(selected = selected, role = Role.Tab) { model.selectSpace(space) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StrataIcon(space.icon, fg, size = 17.dp)
                Spacer(Modifier.width(6.dp))
                Text(space.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SpaceCaption(model: LauncherModel) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 12.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            model.spaceCaption,
            color = StrataColors.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (model.isPinned) {
            Text(
                "Resume auto",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { model.resumeAuto() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, accent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make Strata your home screen", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("So the Home button and gesture bring you here", color = StrataColors.Muted, fontSize = 12.sp)
        }
        StrataIcon(StrataIcons.ChevronRight, accent, size = 20.dp)
    }
}

@Composable
private fun ContextWidget(model: LauncherModel, space: Space, requestCalendar: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, shape)
            .padding(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(space.kicker, color = StrataColors.Muted, fontSize = 13.sp)
            Text(
                model.clockText(),
                style = displayStyle(46.sp),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(model.dateText(), color = StrataColors.Muted, fontSize = 13.sp)
        }
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(StrataColors.Line),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            val event = model.nextEvent
            when {
                !model.calendarAllowed -> InfoRow(
                    icon = StrataIcons.Calendar,
                    title = "Next event",
                    meta = "Tap to show",
                    onClick = requestCalendar,
                )
                event == null -> InfoRow(StrataIcons.Calendar, "No events", "Next 2 days are clear")
                else -> InfoRow(StrataIcons.Calendar, event.title, eventMeta(model, event.begin))
            }
            val battery = model.battery
            InfoRow(
                icon = if (battery.charging) StrataIcons.Charging else StrataIcons.Battery,
                title = "${battery.percent}%",
                meta = if (battery.charging) "Charging" else "Battery",
            )
        }
    }
}

private fun eventMeta(model: LauncherModel, begin: Long): String {
    val minutes = (begin - System.currentTimeMillis()) / 60_000
    val time = model.formatTime(begin)
    return when {
        minutes <= 0 -> "Now"
        minutes < 60 -> "$time · in $minutes min"
        minutes < 24 * 60 -> "$time · in ${minutes / 60} h ${minutes % 60} min"
        else -> "$time tomorrow"
    }
}

@Composable
private fun InfoRow(icon: String, title: String, meta: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(StrataColors.Raised),
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(icon, StrataColors.Text, size = 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, color = StrataColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppGrid(model: LauncherModel, space: Space) {
    val apps = model.appsIn(space)
    val cells = ArrayList<AppEntry?>(apps).apply { if (apps.size < LauncherModel.MAX_PER_SPACE) add(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (row in cells.chunked(4)) {
            Row(Modifier.fillMaxWidth()) {
                for (app in row) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (app == null) {
                            AddTile { model.overlay = Overlay.Drawer }
                        } else {
                            HomeTile(
                                app = app,
                                mono = model.monoIcons,
                                onClick = { model.launch(app) },
                                onLongClick = { menuFor = app.key },
                            )
                            AppMenu(
                                model = model,
                                app = app,
                                expanded = menuFor == app.key,
                                onDismiss = { menuFor = null },
                                currentSpace = space,
                            )
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTile(app: AppEntry, mono: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, mono)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .border(1.dp, StrataColors.LineStrong, shape),
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(StrataIcons.Plus, StrataColors.Muted, size = 22.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Add", color = StrataColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SearchRow(model: LauncherModel) {
    val accent = LocalAccent.current
    val pill = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(pill)
                .background(StrataColors.Surface)
                .border(1.dp, StrataColors.Line, pill)
                .clickable { model.overlay = Overlay.Search }
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrataIcon(StrataIcons.Search, StrataColors.Text, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Search apps and settings",
                color = StrataColors.Muted,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        model.controls.open(Intent(Intent.ACTION_VOICE_COMMAND), Intent(Intent.ACTION_ASSIST))
                    }
                    .semantics { contentDescription = "Voice assistant" },
                contentAlignment = Alignment.Center,
            ) {
                StrataIcon(StrataIcons.Mic, StrataColors.Text, size = 20.dp)
            }
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(StrataColors.Surface)
                .border(1.dp, accent, CircleShape)
                .clickable { model.overlay = Overlay.Controls }
                .semantics { contentDescription = "Open Controls" },
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(StrataIcons.Toggles, accent, size = 22.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(model: LauncherModel) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, shape),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (app in model.dock) {
            AppIcon(
                app = app,
                mono = model.monoIcons,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        onClick = { model.launch(app) },
                        onLongClick = { model.repo.openAppInfo(app) },
                    )
                    .semantics { contentDescription = app.label },
            )
        }
    }
}
