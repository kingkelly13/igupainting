package com.strataos.launcher.data

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A snapshot of the switches the Controls sheet shows. */
data class ControlsState(
    val wifiOn: Boolean = false,
    val wifiConnected: Boolean = false,
    val cellularActive: Boolean = false,
    val dataEnabled: Boolean? = null,
    val bluetoothOn: Boolean = false,
    val focusOn: Boolean = false,
    val vibrate: Boolean = false,
    val rotationLocked: Boolean = false,
    val autoBrightness: Boolean = false,
    val locationOn: Boolean = false,
    val airplaneOn: Boolean = false,
    val batterySaver: Boolean = false,
    val canWriteSettings: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
)

/**
 * Talks to the system for the Controls sheet. Android lets a home-screen app change some
 * settings directly (torch, media volume, ringer, and brightness/rotation once you allow it);
 * the rest open the matching system panel.
 */
class SystemControls(private val context: Context) {
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)
    private val bluetooth: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private val resolver = context.contentResolver

    private val torchCameraId: String? = findTorchCamera()
    val hasTorch: Boolean get() = torchCameraId != null

    var torchOn by mutableStateOf(false)
        private set

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }
    }

    /** The highest value the system brightness setting accepts on this phone. */
    private val brightnessMax: Int by lazy {
        val res = Resources.getSystem()
        @SuppressLint("DiscouragedApi")
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        val configured = if (id != 0) runCatching { res.getInteger(id) }.getOrDefault(255) else 255
        configured.coerceAtLeast(1)
    }

    fun start() {
        runCatching { camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
    }

    fun stop() {
        runCatching { camera.unregisterTorchCallback(torchCallback) }
    }

    @SuppressLint("MissingPermission")
    fun read(): ControlsState {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val rawBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessMax / 2)
        val max = maxOf(brightnessMax, rawBrightness).toFloat()
        val musicMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val filter = notifications.currentInterruptionFilter
        return ControlsState(
            wifiOn = runCatching { wifi.isWifiEnabled }.getOrDefault(false),
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellularActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            dataEnabled = runCatching { telephony?.isDataEnabled }.getOrNull(),
            bluetoothOn = runCatching { bluetooth?.adapter?.isEnabled == true }.getOrDefault(false),
            focusOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            vibrate = audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE,
            rotationLocked = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            autoBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            locationOn = location.isLocationEnabled,
            airplaneOn = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            batterySaver = power.isPowerSaveMode,
            canWriteSettings = Settings.System.canWrite(context),
            // The slider is perceptual: position squared maps to the linear setting.
            brightness = sqrt((rawBrightness / max).coerceIn(0f, 1f)),
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / musicMax,
        )
    }

    fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        runCatching { camera.setTorchMode(id, on) }
    }

    fun setVibrate(on: Boolean) {
        runCatching {
            audio.ringerMode = if (on) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt(), 0) }
    }

    fun toggleFocus() {
        if (!notifications.isNotificationPolicyAccessGranted) {
            Toast.makeText(context, "Allow Do Not Disturb access for Strata to use Focus", Toast.LENGTH_LONG).show()
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val on = notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        notifications.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
    }

    fun setRotationLocked(locked: Boolean) = writeSystem {
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1)
    }

    fun setAutoBrightness(on: Boolean) = writeSystem {
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    fun setBrightness(fraction: Float) = writeSystem {
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 0)
        val max = maxOf(brightnessMax, current)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (max * fraction * fraction).roundToInt().coerceIn(1, max),
        )
    }

    fun requestWriteSettings() {
        Toast.makeText(context, "Allow Strata to modify system settings", Toast.LENGTH_LONG).show()
        open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    fun requestFocusAccess() = open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun openWifi() = open(Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS))

    fun openInternet() = open(
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )

    fun openBluetooth() = open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openLocation() = open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun openAirplane() = open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    fun openBatterySaver() = open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))

    fun openCast() = open(Intent(Settings.ACTION_CAST_SETTINGS), Intent(Settings.ACTION_DISPLAY_SETTINGS))

    fun openSettings() = open(Intent(Settings.ACTION_SETTINGS))

    fun openHomeSettings() = open(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun openPrivacyDashboard() = open(
        Intent("android.intent.action.REVIEW_PERMISSION_USAGE"),
        Intent(Settings.ACTION_PRIVACY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    /** Pulls down the system notification shade, as on the stock home screen. */
    fun expandNotifications() {
        runCatching {
            @SuppressLint("WrongConstant")
            val service = context.getSystemService("statusbar") ?: return
            service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    /** Starts the first intent that resolves; tells you if none does. */
    fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        Toast.makeText(context, "Not available on this phone", Toast.LENGTH_SHORT).show()
    }

    private fun writeSystem(block: () -> Unit) {
        if (Settings.System.canWrite(context)) runCatching(block) else requestWriteSettings()
    }

    private fun findTorchCamera(): String? = runCatching {
        val ids = camera.cameraIdList
        fun hasFlash(id: String) =
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        ids.firstOrNull { id ->
            hasFlash(id) && camera.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull { hasFlash(it) }
    }.getOrNull()
}
