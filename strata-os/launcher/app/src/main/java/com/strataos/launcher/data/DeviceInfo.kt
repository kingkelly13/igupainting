package com.strataos.launcher.data

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Process
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.strataos.launcher.ui.StrataIcons

data class NextEvent(val title: String, val begin: Long, val location: String?)

data class BatteryInfo(val percent: Int, val charging: Boolean)

data class SensorAccess(val label: String, val icon: String, val apps: List<AppEntry>)

/** Read-only facts about the phone that the home screen and settings show. */
object DeviceInfo {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun hasCalendarAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** The next timed event in the coming two days, including one already under way. */
    fun nextEvent(context: Context): NextEvent? {
        if (!hasCalendarAccess(context)) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 2 * DAY_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.END} > ?"
        return try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                NextEvent(
                    title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Untitled event",
                    begin = cursor.getLong(1),
                    location = cursor.getString(2)?.takeIf { it.isNotBlank() },
                )
            }
        } catch (e: SecurityException) {
            null
        }
    }

    fun battery(context: Context): BatteryInfo {
        val manager = context.getSystemService(BatteryManager::class.java)
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        return BatteryInfo(percent, manager.isCharging)
    }

    fun isDefaultHome(context: Context): Boolean {
        val roles = context.getSystemService(RoleManager::class.java)
        return roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun hasFocusAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    /**
     * Which of your apps currently hold camera, microphone and location permission.
     * Android doesn't let ordinary apps read sensor-use history, so this shows access, not use.
     */
    @Suppress("DEPRECATION")
    fun sensorAccess(context: Context, apps: List<AppEntry>): List<SensorAccess> {
        val pm = context.packageManager
        val me = Process.myUserHandle()
        val camera = ArrayList<AppEntry>()
        val microphone = ArrayList<AppEntry>()
        val location = ArrayList<AppEntry>()
        for (app in apps.filter { it.user == me }.distinctBy { it.packageName }) {
            val info = try {
                pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
            val requested = info.requestedPermissions ?: continue
            val flags = info.requestedPermissionsFlags ?: continue
            fun granted(permission: String): Boolean = requested.indices.any { i ->
                requested[i] == permission && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            if (granted(Manifest.permission.CAMERA)) camera += app
            if (granted(Manifest.permission.RECORD_AUDIO)) microphone += app
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                location += app
            }
        }
        return listOf(
            SensorAccess("Camera", StrataIcons.Camera, camera),
            SensorAccess("Microphone", StrataIcons.Mic, microphone),
            SensorAccess("Location", StrataIcons.Location, location),
        )
    }
}
