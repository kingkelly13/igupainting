package com.strataos.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.strataos.launcher.ui.AppDrawer
import com.strataos.launcher.ui.ControlsScrim
import com.strataos.launcher.ui.ControlsSheet
import com.strataos.launcher.ui.HomeScreen
import com.strataos.launcher.ui.SettingsScreen
import com.strataos.launcher.ui.StrataColors
import com.strataos.launcher.ui.StrataTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var model: LauncherModel
    private var appsCallback: android.content.pm.LauncherApps.Callback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        model = LauncherModel(applicationContext, lifecycleScope)
        model.reloadApps()
        appsCallback = model.repo.registerCallback { packageName -> model.onPackageChanged(packageName) }
        setContent { StrataApp(model) }
    }

    override fun onStart() {
        super.onStart()
        model.controls.start()
    }

    override fun onResume() {
        super.onResume()
        model.onResume()
    }

    override fun onStop() {
        model.controls.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while Strata is showing closes whatever is open.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            model.overlay = Overlay.None
        }
    }

    override fun onDestroy() {
        appsCallback?.let { model.repo.unregisterCallback(it) }
        super.onDestroy()
    }
}

@Composable
private fun StrataApp(model: LauncherModel) {
    val context = LocalContext.current
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.refreshDeviceInfo()
    }
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.onResume()
    }
    val requestCalendar: () -> Unit = {
        if (model.calendarAllowed) {
            model.controls.open(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }
    val requestHomeRole: () -> Unit = {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            runCatching { homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onFailure { model.controls.openHomeSettings() }
        } else {
            model.controls.openHomeSettings()
        }
    }

    StrataTheme(accent = model.accent.color) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
                model.tick()
            }
        }
        BackHandler(enabled = model.overlay != Overlay.None) {
            model.overlay = Overlay.None
        }

        val overlay = model.overlay
        Box(
            Modifier
                .fillMaxSize()
                .background(StrataColors.Ink),
        ) {
            HomeScreen(model, requestCalendar, requestHomeRole)

            AnimatedVisibility(
                visible = overlay == Overlay.Drawer || overlay == Overlay.Search,
                enter = slideInVertically { it / 8 } + fadeIn(),
                exit = fadeOut(),
            ) {
                AppDrawer(model, focusSearch = overlay == Overlay.Search)
            }

            AnimatedVisibility(
                visible = overlay == Overlay.Controls,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ControlsScrim { model.overlay = Overlay.None }
            }
            AnimatedVisibility(
                visible = overlay == Overlay.Controls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                ControlsSheet(model)
            }

            AnimatedVisibility(
                visible = overlay == Overlay.Settings,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                SettingsScreen(model, requestCalendar, requestHomeRole)
            }
        }
    }
}
