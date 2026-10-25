package com.strataos.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launchable activity, with its icon already rendered for Compose. */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** The app's themed (monochrome) icon layer, when it ships one. */
    val monoIcon: ImageBitmap?,
)

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconCache = HashMap<String, Pair<ImageBitmap, ImageBitmap?>>()

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.Default) {
        val density = context.resources.displayMetrics.densityDpi
        val result = ArrayList<AppEntry>()
        for (user in launcherApps.profiles) {
            for (info in launcherApps.getActivityList(null, user)) {
                val component = info.componentName
                if (component.packageName == context.packageName) continue
                val key = keyFor(component, user)
                val icons = synchronized(iconCache) { iconCache[key] }
                    ?: renderIcons(info, density).also { synchronized(iconCache) { iconCache[key] = it } }
                result += AppEntry(
                    key = key,
                    label = info.label.toString(),
                    packageName = component.packageName,
                    component = component,
                    user = user,
                    icon = icons.first,
                    monoIcon = icons.second,
                )
            }
        }
        result.sortedBy { it.label.lowercase() }
    }

    /** Drops cached icons for a package so an update shows its new icon. */
    fun invalidate(packageName: String) {
        synchronized(iconCache) {
            iconCache.keys.removeAll { it.startsWith("$packageName/") }
        }
    }

    fun registerCallback(onChange: (String) -> Unit): LauncherApps.Callback {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                packageName?.let(onChange)
            }

            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                packageName?.let(onChange)
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                packageName?.let(onChange)
            }

            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                packageNames?.forEach(onChange)
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                packageNames?.forEach(onChange)
            }
        }
        launcherApps.registerCallback(callback)
        return callback
    }

    fun unregisterCallback(callback: LauncherApps.Callback) {
        launcherApps.unregisterCallback(callback)
    }

    fun launch(app: AppEntry) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(app: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open app info", Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't uninstall ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** The phone's default dialer, messages, camera and browser, in dock order. */
    fun resolveDock(apps: List<AppEntry>): List<AppEntry> {
        val me = Process.myUserHandle()
        val packages = listOf(
            resolveDefault(Intent(Intent.ACTION_DIAL)),
            Telephony.Sms.getDefaultSmsPackage(context)
                ?: resolveDefault(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            resolveDefault(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            resolveDefault(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            ),
        )
        return packages.mapNotNull { pkg ->
            pkg?.let { p -> apps.firstOrNull { it.packageName == p && it.user == me } }
        }.distinctBy { it.key }
    }

    @Suppress("DEPRECATION")
    private fun resolveDefault(intent: Intent): String? {
        val pm = context.packageManager
        val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (resolved != null && resolved.packageName != "android" && !resolved.name.contains("Resolver")) {
            return resolved.packageName
        }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull()?.activityInfo?.packageName
    }

    private fun renderIcons(info: LauncherActivityInfo, density: Int): Pair<ImageBitmap, ImageBitmap?> {
        val drawable = info.getIcon(density)
        val full = drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            drawable.monochrome?.let { renderMonochrome(it) }
        } else {
            null
        }
        return full to mono
    }

    /** Renders the 108 dp monochrome layer and keeps the visible 72 dp centre. */
    private fun renderMonochrome(layer: Drawable): ImageBitmap {
        val size = MONO_RENDER_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, size, size)
        layer.draw(Canvas(bitmap))
        val inset = size / 6
        return Bitmap.createBitmap(bitmap, inset, inset, size - 2 * inset, size - 2 * inset).asImageBitmap()
    }

    companion object {
        private const val ICON_PX = 144
        private const val MONO_RENDER_PX = 162

        fun keyFor(component: ComponentName, user: UserHandle): String =
            "${component.flattenToShortString()}#${user.hashCode()}"
    }
}
