package com.strataos.remote

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.strataos.remote.data.CodeLibrary
import com.strataos.remote.data.CodeSet
import com.strataos.remote.data.Device
import com.strataos.remote.data.DeviceKind
import com.strataos.remote.data.DeviceStore
import com.strataos.remote.data.Keys
import com.strataos.remote.data.RemoteButton
import com.strataos.remote.ir.FlipperIr
import com.strataos.remote.ir.IrBlaster
import com.strataos.remote.ir.IrSignal
import com.strataos.remote.ir.describe
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Setup : Screen
    data class Remote(val deviceId: String) : Screen
}

enum class SetupStep { Kind, Brand, Test, Name }

/** The add-a-remote flow: pick a kind, a brand, find a code that works, name it. */
class SetupState {
    var step by mutableStateOf(SetupStep.Kind)
    var kind by mutableStateOf(DeviceKind.TV)
    var candidates by mutableStateOf<List<CodeSet>>(emptyList())
    var index by mutableIntStateOf(0)
    var tried by mutableStateOf(false)
    var exhausted by mutableStateOf(false)
    var buttons by mutableStateOf<List<RemoteButton>>(emptyList())
    var source by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var note by mutableStateOf<String?>(null)
    var cameFromTest by mutableStateOf(false)

    val current: CodeSet? get() = candidates.getOrNull(index)
}

/** Which button the editor sheet is open for. */
data class EditTarget(val key: String, val isNew: Boolean)

class RemoteModel(context: Context) {
    val blaster = IrBlaster(context)
    private val store = DeviceStore(context)
    private val prefs = context.getSharedPreferences("strata_remote", Context.MODE_PRIVATE)

    var devices by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
    val setup = SetupState()

    var volumeKeys by mutableStateOf(prefs.getBoolean(PREF_VOLUME_KEYS, true))
        private set
    var haptics by mutableStateOf(prefs.getBoolean(PREF_HAPTICS, true))
        private set

    /** The line under the remote's name: what was just sent, or why it wasn't. */
    var status by mutableStateOf<String?>(null)
    var statusIsError by mutableStateOf(false)

    /** The key being held, drawn in the accent colour while its code goes out. */
    var pressedKey by mutableStateOf<String?>(null)
        private set

    var editing by mutableStateOf(false)
    var editTarget by mutableStateOf<EditTarget?>(null)

    fun device(id: String): Device? = devices.firstOrNull { it.id == id }

    val currentDevice: Device? get() = (screen as? Screen.Remote)?.let { device(it.deviceId) }

    // ---- Sending ----

    fun press(button: RemoteButton) {
        pressedKey = button.key
        blaster.press(
            signal = button.signal,
            repeatable = Keys.isRepeatable(button.key),
            onSent = { report("${button.label} · ${button.signal.describe()}", error = false) },
            onError = { report(it, error = true) },
        )
    }

    fun release() {
        blaster.release()
        pressedKey = null
    }

    /** Sends a code once, e.g. to test it in the editor or during setup. */
    fun sendOnce(signal: IrSignal, label: String) {
        blaster.press(
            signal = signal,
            repeatable = false,
            onSent = { report("$label · ${signal.describe()}", error = false) },
            onError = { report(it, error = true) },
        )
    }

    fun report(message: String, error: Boolean) {
        status = message
        statusIsError = error
    }

    // ---- Navigation ----

    fun open(device: Device) {
        editing = false
        editTarget = null
        status = null
        screen = Screen.Remote(device.id)
    }

    fun goHome() {
        release()
        editing = false
        editTarget = null
        status = null
        screen = Screen.Home
    }

    /** Handles Back. Returns false when there is nothing left to close. */
    fun back(): Boolean {
        when (screen) {
            Screen.Home -> return false
            is Screen.Remote -> when {
                editTarget != null -> editTarget = null
                editing -> editing = false
                else -> goHome()
            }
            Screen.Setup -> setupBack()
        }
        return true
    }

    // ---- Saved remotes ----

    private fun commit(list: List<Device>) {
        devices = list
        runCatching { store.save(list) }.onFailure { report("Couldn't save: ${it.message}", error = true) }
    }

    fun update(device: Device) = commit(devices.map { if (it.id == device.id) device else it })

    fun rename(device: Device, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) update(device.copy(name = clean))
    }

    fun delete(device: Device) {
        commit(devices.filterNot { it.id == device.id })
        if ((screen as? Screen.Remote)?.deviceId == device.id) goHome()
    }

    fun saveButton(device: Device, button: RemoteButton) = update(device.with(button))

    fun removeButton(device: Device, key: String) = update(device.without(key))

    // ---- Settings ----

    fun updateVolumeKeys(on: Boolean) {
        volumeKeys = on
        prefs.edit().putBoolean(PREF_VOLUME_KEYS, on).apply()
    }

    fun updateHaptics(on: Boolean) {
        haptics = on
        prefs.edit().putBoolean(PREF_HAPTICS, on).apply()
    }

    // ---- Add-a-remote flow ----

    fun startSetup() {
        release()
        setup.step = SetupStep.Kind
        setup.note = null
        status = null
        screen = Screen.Setup
    }

    fun chooseKind(kind: DeviceKind) {
        setup.kind = kind
        setup.step = SetupStep.Brand
    }

    fun tryCodeSets(sets: List<CodeSet>) {
        setup.candidates = sets
        setup.index = 0
        setup.tried = false
        setup.exhausted = false
        status = null
        setup.step = SetupStep.Test
    }

    fun testCurrent(confirm: Boolean) {
        val set = setup.current ?: return
        val button = (if (confirm) set.confirmButton else null) ?: set.testButton
        sendOnce(button.signal, button.label)
        setup.tried = true
    }

    fun nextCandidate() {
        status = null
        if (setup.index + 1 < setup.candidates.size) {
            setup.index += 1
            setup.tried = false
        } else {
            setup.exhausted = true
        }
    }

    fun acceptCandidate() {
        val set = setup.current ?: return
        setup.buttons = set.buttons
        setup.source = set.id
        setup.name = defaultName(set)
        setup.note = null
        setup.cameFromTest = true
        setup.step = SetupStep.Name
    }

    fun startEmpty() {
        setup.buttons = emptyList()
        setup.source = null
        setup.name = "My ${setup.kind.noun}"
        setup.note = "You'll add each button's code yourself. Tap Edit on the remote to start."
        setup.cameFromTest = false
        setup.step = SetupStep.Name
    }

    /** Imports a Flipper `.ir` file. Returns an error message, or null on success. */
    fun importFlipper(text: String, fileName: String?): String? {
        val result = try {
            FlipperIr.parse(text)
        } catch (e: IllegalArgumentException) {
            return e.message ?: "That file couldn't be read"
        }
        if (result.entries.isEmpty()) {
            return "None of the ${result.skipped.size} buttons in that file use a protocol Strata Remote supports"
        }
        setup.buttons = Device.buttonsFrom(result.entries, setup.kind)
        setup.source = null
        setup.name = fileName
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.trim()
            ?.ifEmpty { null }
            ?: "Imported ${setup.kind.noun}"
        setup.note = buildString {
            append("Imported ${result.entries.size} buttons.")
            if (result.skipped.isNotEmpty()) {
                append(" Skipped ${result.skipped.size} that use unsupported protocols: ")
                append(result.skipped.take(5).joinToString())
                if (result.skipped.size > 5) append("…")
            }
        }
        setup.cameFromTest = false
        setup.step = SetupStep.Name
        return null
    }

    fun finishSetup() {
        val device = Device(
            id = UUID.randomUUID().toString(),
            name = setup.name.trim().ifEmpty { "My ${setup.kind.noun}" },
            kind = setup.kind,
            source = setup.source,
            buttons = setup.buttons,
        )
        commit(devices + device)
        open(device)
        // A blank remote opens straight into edit mode.
        if (device.buttons.isEmpty()) editing = true
    }

    private fun setupBack() {
        status = null
        when (setup.step) {
            SetupStep.Kind -> screen = Screen.Home
            SetupStep.Brand -> setup.step = SetupStep.Kind
            SetupStep.Test -> setup.step = SetupStep.Brand
            SetupStep.Name -> setup.step = if (setup.cameFromTest) SetupStep.Test else SetupStep.Brand
        }
    }

    private fun defaultName(set: CodeSet): String = when (setup.kind) {
        DeviceKind.Lights -> "LED lights"
        DeviceKind.TV -> "${set.brand.substringBefore(" (")} TV"
        else -> "${set.brand.substringBefore(" (")} ${setup.kind.noun}"
    }

    /** All built-in code sets for the chosen kind. */
    val setupCodeSets: List<CodeSet> get() = CodeLibrary.forKind(setup.kind)

    private companion object {
        const val PREF_VOLUME_KEYS = "volume_keys"
        const val PREF_HAPTICS = "haptics"
    }
}
