package com.strataos.remote

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.strataos.remote.data.CodeLibrary
import com.strataos.remote.data.DeviceKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens to build/screenshots so layouts can be checked without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun addRemote(model: RemoteModel, kind: DeviceKind, setId: String) {
        model.startSetup()
        model.chooseKind(kind)
        model.tryCodeSets(listOf(CodeLibrary.byId(setId)!!))
        model.acceptCandidate()
        model.finishSetup()
    }

    @Test
    fun screens() {
        val model = RemoteModel(RuntimeEnvironment.getApplication())
        compose.setContent { RemoteApp(model) }
        shoot("01_home_empty")

        compose.runOnIdle { model.startSetup() }
        shoot("02_setup_kind")
        compose.runOnIdle { model.chooseKind(DeviceKind.TV) }
        shoot("03_setup_brand")
        compose.runOnIdle {
            model.tryCodeSets(CodeLibrary.forKind(DeviceKind.TV))
            model.testCurrent(confirm = false)
        }
        shoot("04_setup_test")

        compose.runOnIdle { addRemote(model, DeviceKind.TV, "samsung_tv") }
        shoot("05_remote_main")
        compose.onNodeWithText("Numbers").performClick()
        shoot("06_remote_numbers")
        compose.onNodeWithText("All buttons").performClick()
        compose.runOnIdle { model.editing = true }
        shoot("07_remote_all_editing")
        compose.runOnIdle { model.editTarget = EditTarget("mute", isNew = false) }
        shoot("08_editor")

        compose.runOnIdle {
            model.editTarget = null
            model.editing = false
            addRemote(model, DeviceKind.Lights, "led_24")
        }
        shoot("09_lights")
        compose.runOnIdle {
            addRemote(model, DeviceKind.TV, "roku")
        }
        shoot("10_roku")
        compose.runOnIdle { model.goHome() }
        shoot("11_home_list")
    }
}
