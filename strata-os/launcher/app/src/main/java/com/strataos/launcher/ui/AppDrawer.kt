package com.strataos.launcher.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.launcher.LauncherModel
import com.strataos.launcher.Overlay
import kotlinx.coroutines.delay

/** A settings page the search can jump to. A null action means Strata's own settings. */
private class Shortcut(val label: String, val keywords: String, val icon: String, val action: String?) {
    fun matches(query: String): Boolean {
        val haystack = "$label $keywords".lowercase()
        return query.lowercase().split(' ').filter { it.isNotBlank() }.all { it in haystack }
    }
}

private val Shortcuts = listOf(
    Shortcut("Strata settings", "strata launcher spaces accent icons privacy home", StrataIcons.Shield, null),
    Shortcut("Wi-Fi", "wifi wireless internet network", StrataIcons.Wifi, Settings.ACTION_WIFI_SETTINGS),
    Shortcut("Bluetooth", "bluetooth earbuds headphones pair", StrataIcons.Bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
    Shortcut("Display and brightness", "display screen brightness refresh", StrataIcons.Sun, Settings.ACTION_DISPLAY_SETTINGS),
    Shortcut("Sound and vibration", "sound volume ringtone vibrate", StrataIcons.Volume, Settings.ACTION_SOUND_SETTINGS),
    Shortcut("Battery", "battery power saver charging", StrataIcons.Battery, Intent.ACTION_POWER_USAGE_SUMMARY),
    Shortcut("Apps", "apps applications manage", StrataIcons.Grid, Settings.ACTION_APPLICATION_SETTINGS),
    Shortcut("Location", "location gps", StrataIcons.Location, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    Shortcut("Security", "security lock screen fingerprint password", StrataIcons.Lock, Settings.ACTION_SECURITY_SETTINGS),
    Shortcut("Privacy", "privacy permissions", StrataIcons.Shield, Settings.ACTION_PRIVACY_SETTINGS),
    Shortcut("Storage", "storage space files", StrataIcons.Files, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    Shortcut("Date and time", "date time clock", StrataIcons.Clock, Settings.ACTION_DATE_SETTINGS),
    Shortcut("Languages and input", "language keyboard input", StrataIcons.Translate, Settings.ACTION_LOCALE_SETTINGS),
    Shortcut("About phone", "about phone version hyperos", StrataIcons.Info, Settings.ACTION_DEVICE_INFO_SETTINGS),
    Shortcut("Default home app", "default launcher home", StrataIcons.Home, Settings.ACTION_HOME_SETTINGS),
)

/** All apps, with search at the bottom where your thumb is. */
@Composable
fun AppDrawer(model: LauncherModel, focusSearch: Boolean) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = query.trim()
    val apps = model.apps
    val results = remember(trimmed, apps) {
        if (trimmed.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.contains(trimmed, ignoreCase = true) }
                .sortedBy { !it.label.startsWith(trimmed, ignoreCase = true) }
        }
    }
    val shortcuts = remember(trimmed) {
        if (trimmed.isEmpty()) emptyList() else Shortcuts.filter { it.matches(trimmed) }
    }

    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun webSearch() {
        model.controls.open(
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, trimmed),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(trimmed))),
        )
    }

    fun openShortcut(shortcut: Shortcut) {
        val action = shortcut.action
        if (action == null) model.overlay = Overlay.Settings else model.controls.open(Intent(action))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(StrataColors.Ink)
            .blockTouchesBelow()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (trimmed.isEmpty()) "All apps" else "Search", style = displayStyle(28.sp, FontWeight.SemiBold))
                Text(
                    if (trimmed.isEmpty()) "${apps.size} apps · hold one to add it to a Space" else "${results.size} apps",
                    color = StrataColors.Muted,
                    fontSize = 12.sp,
                )
            }
            CircleButton(StrataIcons.Settings, "Strata settings") { model.overlay = Overlay.Settings }
            Spacer(Modifier.width(8.dp))
            CircleButton(StrataIcons.Close, "Close") { model.overlay = Overlay.None }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shortcuts, span = { GridItemSpan(maxLineSpan) }) { shortcut ->
                ListRow(shortcut.icon, shortcut.label, "Settings") { openShortcut(shortcut) }
            }
            items(results, key = { it.key }) { app ->
                Box(contentAlignment = Alignment.TopCenter) {
                    HomeTile(
                        app = app,
                        mono = model.monoIcons,
                        onClick = { model.launch(app) },
                        onLongClick = { menuFor = app.key },
                    )
                    AppMenu(model, app, expanded = menuFor == app.key, onDismiss = { menuFor = null })
                }
            }
            if (trimmed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListRow(StrataIcons.Globe, "Search the web for “$trimmed”", "Browser") { webSearch() }
                }
            }
        }

        SearchField(
            query = query,
            onChange = { query = it },
            focusRequester = focusRequester,
            onGo = {
                val first = results.firstOrNull()
                when {
                    first != null -> model.launch(first)
                    shortcuts.isNotEmpty() -> openShortcut(shortcuts.first())
                    trimmed.isNotEmpty() -> webSearch()
                }
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, focusRequester: FocusRequester, onGo: () -> Unit) {
    val accent = LocalAccent.current
    val pill = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(pill)
            .background(StrataColors.Surface)
            .border(1.dp, accent, pill)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrataIcon(StrataIcons.Search, StrataColors.Text, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = StrataColors.Text),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search apps and settings", color = StrataColors.Muted, fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onChange("") }
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) {
                StrataIcon(StrataIcons.Close, StrataColors.Muted, size = 18.dp)
            }
        }
    }
}

@Composable
private fun ListRow(icon: String, title: String, meta: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StrataColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(StrataColors.Raised),
            contentAlignment = Alignment.Center,
        ) {
            StrataIcon(icon, StrataColors.Text, size = 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = StrataColors.Muted, fontSize = 12.sp)
        }
        StrataIcon(StrataIcons.ChevronRight, StrataColors.Muted, size = 18.dp)
    }
}

@Composable
fun CircleButton(icon: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(StrataColors.Surface)
            .border(1.dp, StrataColors.Line, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        StrataIcon(icon, StrataColors.Text, size = 20.dp)
    }
}
