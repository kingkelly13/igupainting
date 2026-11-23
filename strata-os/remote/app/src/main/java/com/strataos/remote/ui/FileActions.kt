package com.strataos.remote.ui

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.strataos.remote.data.Device

/** Opens the system file picker for a Flipper `.ir` file and hands back its text and name. */
@Composable
fun rememberIrFilePicker(onFile: (text: String, name: String?) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("The file couldn't be opened")
            require(bytes.size < 8 * 1024 * 1024) { "That file is too big to be a remote" }
            onFile(String(bytes, Charsets.UTF_8), name)
        } catch (e: Exception) {
            onError(e.message ?: "The file couldn't be read")
        }
    }
    // Flipper files have no registered type, so allow any file.
    return { launcher.launch(arrayOf("*/*")) }
}

/** Saves a remote as a Flipper `.ir` file wherever the person picks. */
@Composable
fun rememberIrExporter(): (Device) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Device?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val device = pending
        pending = null
        if (uri == null || device == null) return@rememberLauncherForActivityResult
        val message = try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(device.toFlipper().toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("The file couldn't be created")
            "Saved ${device.name}"
        } catch (e: Exception) {
            "Couldn't save: ${e.message}"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    return { device ->
        pending = device
        val safe = device.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifEmpty { "remote" }
        launcher.launch("$safe.ir")
    }
}
