package com.strataos.launcher

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strataos.launcher.data.AppEntry
import com.strataos.launcher.data.AppRepository
import com.strataos.launcher.data.BatteryInfo
import com.strataos.launcher.data.DeviceInfo
import com.strataos.launcher.data.NextEvent
import com.strataos.launcher.data.SystemControls
import com.strataos.launcher.ui.Accent
import com.strataos.launcher.ui.StrataIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale

enum class Space(val id: String, val label: String, val icon: String, val kicker: String) {
    Personal("personal", "Personal", StrataIcons.Home, "Today"),
    Work("work", "Work", StrataIcons.Briefcase, "Work day"),
    Out("out", "Out", StrataIcons.Compass, "Out and about"),
}

enum class Overlay { None, Drawer, Search, Controls, Settings }

/** All launcher state. Lives as long as the home activity and saves to SharedPreferences. */
class LauncherModel(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("strata", Context.MODE_PRIVATE)

    val repo = AppRepository(context)
    val controls = SystemControls(context)

    var apps: List<AppEntry> by mutableStateOf(emptyList())
        private set
    var dock: List<AppEntry> by mutableStateOf(emptyList())
        private set
    private val appsByKey: Map<String, AppEntry> by derivedStateOf { apps.associateBy { it.key } }

    var overlay by mutableStateOf(Overlay.None)

    var now: LocalDateTime by mutableStateOf(LocalDateTime.now())
        private set
    var nextEvent: NextEvent? by mutableStateOf(null)
        private set
    var calendarAllowed by mutableStateOf(false)
        private set
    var battery by mutableStateOf(BatteryInfo(0, false))
        private set
    var isDefaultHome by mutableStateOf(true)
        private set

    /** Bumped on every resume so screens re-check permissions the user may have changed. */
    var resumeCount by mutableIntStateOf(0)
        private set

    var accent by mutableStateOf(Accent.entries.getOrElse(prefs.getInt("accent", 0)) { Accent.Amber })
        private set
    var monoIcons by mutableStateOf(prefs.getBoolean("mono_icons", true))
        private set
    var autoSwitch by mutableStateOf(prefs.getBoolean("auto_switch", true))
        private set
    var workStart by mutableIntStateOf(prefs.getInt("work_start", 9))
        private set
    var workEnd by mutableIntStateOf(prefs.getInt("work_end", 18))
        private set

    private var pinnedSpace by mutableStateOf(
        prefs.getString("pinned_space", null)?.let { id -> Space.entries.firstOrNull { it.id == id } },
    )
    private var pinnedUntil by mutableLongStateOf(prefs.getLong("pinned_until", 0L))

    private val spaceKeys = mutableStateMapOf<Space, List<String>>()
    private var reloadJob: Job? = null

    init {
        for (space in Space.entries) {
            spaceKeys[space] = prefs.getString("space_${space.id}", null)
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                .orEmpty()
        }
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    fun onResume() {
        now = LocalDateTime.now()
        isDefaultHome = DeviceInfo.isDefaultHome(context)
        resumeCount++
        refreshDeviceInfo()
    }

    fun tick() {
        now = LocalDateTime.now()
        refreshDeviceInfo()
    }

    fun refreshDeviceInfo() {
        calendarAllowed = DeviceInfo.hasCalendarAccess(context)
        battery = DeviceInfo.battery(context)
        scope.launch {
            nextEvent = withContext(Dispatchers.IO) { DeviceInfo.nextEvent(context) }
        }
    }

    fun reloadApps() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            val list = repo.loadApps()
            apps = list
            dock = repo.resolveDock(list)
            if (!prefs.getBoolean("seeded", false) && list.isNotEmpty()) seedSpaces(list)
        }
    }

    fun onPackageChanged(packageName: String) {
        repo.invalidate(packageName)
        reloadApps()
    }

    // ---- Apps ------------------------------------------------------------------------------

    fun launch(app: AppEntry) {
        repo.launch(app)
        overlay = Overlay.None
    }

    fun appsIn(space: Space): List<AppEntry> = spaceKeys[space].orEmpty().mapNotNull { appsByKey[it] }

    fun isIn(space: Space, app: AppEntry): Boolean = spaceKeys[space].orEmpty().contains(app.key)

    fun toggleMembership(space: Space, app: AppEntry) {
        val keys = spaceKeys[space].orEmpty()
        val next = when {
            app.key in keys -> keys - app.key
            keys.size >= MAX_PER_SPACE -> {
                Toast.makeText(context, "${space.label} is full. Remove an app first.", Toast.LENGTH_SHORT).show()
                return
            }
            else -> keys + app.key
        }
        spaceKeys[space] = next
        saveSpace(space)
    }

    fun move(space: Space, app: AppEntry, delta: Int) {
        val keys = spaceKeys[space].orEmpty().toMutableList()
        val from = keys.indexOf(app.key)
        val to = from + delta
        if (from < 0 || to !in keys.indices) return
        Collections.swap(keys, from, to)
        spaceKeys[space] = keys
        saveSpace(space)
    }

    // ---- Spaces ----------------------------------------------------------------------------

    val activeSpace: Space
        get() {
            val pin = pinnedSpace
            if (pin != null && System.currentTimeMillis() < pinnedUntil) return pin
            return if (autoSwitch) scheduledSpace(now) else Space.Personal
        }

    val isPinned: Boolean
        get() = autoSwitch && pinnedSpace != null && System.currentTimeMillis() < pinnedUntil

    val spaceCaption: String
        get() {
            if (!autoSwitch) return "Manual · automatic switching is off"
            if (isPinned) return "Pinned until ${describe(fromEpoch(pinnedUntil))}"
            val change = nextScheduleChange(now) ?: return "Auto"
            return "Auto · ${scheduledSpace(change).label} from ${describe(change)}"
        }

    fun selectSpace(space: Space) {
        when {
            !autoSwitch -> pin(space, Long.MAX_VALUE)
            space == scheduledSpace(now) -> clearPin()
            else -> pin(space, nextScheduleChange(now)?.let { toEpoch(it) } ?: Long.MAX_VALUE)
        }
    }

    fun resumeAuto() = clearPin()

    fun scheduledSpace(time: LocalDateTime): Space {
        val weekday = time.dayOfWeek.value in 1..5
        return if (weekday && time.hour >= workStart && time.hour < workEnd) Space.Work else Space.Personal
    }

    private fun nextScheduleChange(from: LocalDateTime): LocalDateTime? {
        val current = scheduledSpace(from)
        var t = from.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        repeat(24 * 8) {
            if (scheduledSpace(t) != current) return t
            t = t.plusHours(1)
        }
        return null
    }

    private fun pin(space: Space, until: Long) {
        pinnedSpace = space
        pinnedUntil = until
        prefs.edit().putString("pinned_space", space.id).putLong("pinned_until", until).apply()
    }

    private fun clearPin() {
        pinnedSpace = null
        pinnedUntil = 0L
        prefs.edit().remove("pinned_space").remove("pinned_until").apply()
    }

    // ---- Settings --------------------------------------------------------------------------

    fun updateAccent(value: Accent) {
        accent = value
        prefs.edit().putInt("accent", value.ordinal).apply()
    }

    fun updateMonoIcons(value: Boolean) {
        monoIcons = value
        prefs.edit().putBoolean("mono_icons", value).apply()
    }

    fun updateAutoSwitch(value: Boolean) {
        autoSwitch = value
        clearPin()
        prefs.edit().putBoolean("auto_switch", value).apply()
    }

    fun updateWorkHours(start: Int, end: Int) {
        if (start !in 0..22 || end !in 1..23 || start >= end) return
        workStart = start
        workEnd = end
        prefs.edit().putInt("work_start", start).putInt("work_end", end).apply()
    }

    // ---- Formatting ------------------------------------------------------------------------

    private val timeFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")

    fun clockText(): String = now.format(timeFormatter)

    fun dateText(): String = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

    fun formatTime(epochMillis: Long): String = fromEpoch(epochMillis).format(timeFormatter)

    /** "18:00", "09:00 tomorrow" or "Mon 09:00". */
    fun describe(time: LocalDateTime): String {
        val clock = time.format(timeFormatter)
        return when (ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())) {
            0L -> clock
            1L -> "$clock tomorrow"
            else -> "${time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $clock"
        }
    }

    private fun toEpoch(time: LocalDateTime): Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun fromEpoch(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    // ---- First run -------------------------------------------------------------------------

    /** Gives each Space a sensible starting set from the apps on this phone. */
    private fun seedSpaces(list: List<AppEntry>) {
        val byPackage = list.groupBy { it.packageName }
        val dockPackages = dock.map { it.packageName }.toSet()
        val settingsKey = byPackage["com.android.settings"]?.firstOrNull()?.key

        fun pick(packages: List<String>): MutableList<String> = packages
            .filter { it !in dockPackages }
            .mapNotNull { byPackage[it]?.firstOrNull()?.key }
            .distinct()
            .toMutableList()

        val personal = pick(PERSONAL_DEFAULTS)
        for (app in list) {
            if (personal.size >= 11) break
            if (app.key !in personal && app.packageName !in dockPackages && app.key != settingsKey) personal += app.key
        }
        val seeded = mapOf(
            Space.Personal to personal,
            Space.Work to pick(WORK_DEFAULTS),
            Space.Out to pick(OUT_DEFAULTS),
        )
        for ((space, keys) in seeded) {
            val trimmed = keys.filter { it != settingsKey }.take(MAX_PER_SPACE - 1)
            spaceKeys[space] = trimmed + listOfNotNull(settingsKey)
            saveSpace(space)
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }

    private fun saveSpace(space: Space) {
        prefs.edit().putString("space_${space.id}", spaceKeys[space].orEmpty().joinToString("\n")).apply()
    }

    companion object {
        const val MAX_PER_SPACE = 16

        private val PERSONAL_DEFAULTS = listOf(
            "com.google.android.apps.photos", "com.miui.gallery", "com.whatsapp", "com.instagram.android",
            "com.spotify.music", "com.miui.player", "com.google.android.youtube",
            "com.google.android.apps.youtube.music", "com.netflix.mediaclient", "com.google.android.gm",
            "com.google.android.calendar", "com.xiaomi.calendar", "com.android.calendar",
            "com.google.android.apps.maps", "com.miui.notes", "com.google.android.keep", "com.android.vending",
        )
        private val WORK_DEFAULTS = listOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.calendar",
            "com.xiaomi.calendar", "com.android.calendar", "com.microsoft.teams", "com.Slack",
            "us.zoom.videomeetings", "com.google.android.apps.tachyon", "com.google.android.apps.docs",
            "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
            "com.microsoft.office.officehubrow", "com.google.android.keep", "com.miui.notes",
            "com.miui.calculator", "com.google.android.calculator", "com.mi.android.globalFileexplorer",
            "com.android.fileexplorer", "com.google.android.apps.nbu.files", "com.linkedin.android",
        )
        private val OUT_DEFAULTS = listOf(
            "com.google.android.apps.maps", "com.google.android.apps.walletnfcrel", "com.ubercab",
            "com.citymapper.app.release", "com.google.android.apps.translate", "com.miui.weather2",
            "com.google.android.apps.photos", "com.miui.gallery", "com.spotify.music", "com.miui.player",
            "com.mi.health", "com.google.android.apps.fitness", "com.booking", "com.airbnb.android",
        )
    }
}
