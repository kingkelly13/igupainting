package com.strataos.remote

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.strataos.remote.ui.Amber
import com.strataos.remote.ui.HomeScreen
import com.strataos.remote.ui.RemoteScreen
import com.strataos.remote.ui.SetupScreen
import com.strataos.remote.ui.StrataColors
import com.strataos.remote.ui.StrataTheme
import com.strataos.remote.ui.rememberIrExporter

class MainActivity : ComponentActivity() {
    private lateinit var model: RemoteModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        model = RemoteModel(applicationContext)
        setContent { RemoteApp(model) }
    }

    override fun onStop() {
        // Never leave a held button repeating in the background.
        model.release()
        super.onStop()
    }

    /** While a remote is open, the phone's volume buttons send its volume codes. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> "vol_up"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "vol_down"
            else -> null
        }
        val device = model.currentDevice
        if (key != null && model.volumeKeys && device != null && !model.editing && model.editTarget == null) {
            val button = device.button(key)
            if (button != null) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) model.press(button)
                    KeyEvent.ACTION_UP -> model.release()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
internal fun RemoteApp(model: RemoteModel) {
    StrataTheme(accent = Amber) {
        BackHandler(enabled = model.screen != Screen.Home) { model.back() }
        val export = rememberIrExporter()
        Box(
            Modifier
                .fillMaxSize()
                .background(StrataColors.Ink),
        ) {
            Crossfade(targetState = model.screen, label = "screen") { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(model, export)
                    Screen.Setup -> SetupScreen(model)
                    is Screen.Remote -> {
                        val device = model.device(screen.deviceId)
                        if (device != null) {
                            RemoteScreen(model, device, export)
                        } else {
                            LaunchedEffect(screen) { if (model.screen == screen) model.goHome() }
                        }
                    }
                }
            }
        }
    }
}
