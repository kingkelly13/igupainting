package com.strataos.remote.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strataos.remote.EditTarget
import com.strataos.remote.RemoteModel
import com.strataos.remote.data.Device
import com.strataos.remote.data.Keys
import com.strataos.remote.data.RemoteButton
import com.strataos.remote.ir.FlipperIr
import com.strataos.remote.ir.IrSignal
import com.strataos.remote.ir.Pronto
import com.strataos.remote.ir.Protocol

private enum class CodeFormat(val label: String) { Protocol("Protocol"), Pronto("Pronto hex"), Raw("Raw") }

/** Parses a hex value typed as "0x1A", "1A" or Flipper's little-endian bytes "1A 00 00 00". */
private fun parseHex(text: String, what: String): Long {
    val t = text.trim()
    require(t.isNotEmpty()) { "Enter the $what" }
    return try {
        FlipperIr.bytesLe(t)
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("The $what should be hex, like 0x1A")
    }
}

private fun plainHex(value: Long): String = "0x" + java.lang.Long.toHexString(value).uppercase().let { if (it.length % 2 == 1) "0$it" else it }

/**
 * Sets one button's code: a protocol with address and command, a Pronto hex code, or raw
 * timings. New codes default to the protocol and address the rest of the remote uses, so for a
 * missing button often only the command needs typing.
 */
@Composable
fun ButtonEditor(model: RemoteModel, device: Device, target: EditTarget) {
    val existing = device.button(target.key)
    val std = Keys.get(target.key)
    val isCustom = std == null
    val parsed = existing?.signal as? IrSignal.Parsed
    val raw = existing?.signal as? IrSignal.Raw
    // The protocol and address most of this remote's buttons share.
    val common = device.buttons.mapNotNull { it.signal as? IrSignal.Parsed }
        .groupingBy { it.protocol to it.address }.eachCount().maxByOrNull { it.value }?.key

    var label by remember(target) { mutableStateOf(existing?.label ?: std?.label ?: "") }
    var format by remember(target) { mutableStateOf(if (raw != null) CodeFormat.Raw else CodeFormat.Protocol) }
    var protocol by remember(target) { mutableStateOf(parsed?.protocol ?: common?.first ?: Protocol.NEC) }
    var address by remember(target) { mutableStateOf((parsed?.address ?: common?.second)?.let(::plainHex) ?: "") }
    var command by remember(target) { mutableStateOf(parsed?.command?.let(::plainHex) ?: "") }
    var pronto by remember(target) { mutableStateOf("") }
    var frequency by remember(target) { mutableStateOf((raw?.frequency ?: 38_000).toString()) }
    var timings by remember(target) { mutableStateOf(raw?.let { (it.once + it.repeat).joinToString(" ") } ?: "") }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var protocolMenu by remember { mutableStateOf(false) }

    fun build(): IrSignal? = try {
        error = null
        when (format) {
            CodeFormat.Protocol -> IrSignal.Parsed(protocol, parseHex(address, "address"), parseHex(command, "command"))
            CodeFormat.Pronto -> Pronto.parse(pronto)
            CodeFormat.Raw -> {
                val hz = frequency.trim().toIntOrNull()
                require(hz != null && hz in 15_000..500_000) { "The frequency should be in Hz, like 38000" }
                val list = timings.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map {
                    it.trim('+', '-').toIntOrNull()?.takeIf { v -> v > 0 }
                        ?: throw IllegalArgumentException("Timings are whole microseconds, like 9000 4500 560")
                }
                require(list.isNotEmpty()) { "Enter the timings" }
                IrSignal.Raw(hz, list)
            }
        }
    } catch (e: IllegalArgumentException) {
        error = e.message ?: "That code isn't valid"
        null
    }

    val close = { model.editTarget = null }
    val name = label.trim().ifEmpty { "Button" }

    Sheet(onDismiss = close) {
        Text(
            if (target.isNew && isCustom) "New button" else name,
            style = displayStyle(26.sp, FontWeight.SemiBold),
        )
        Text(
            if (existing == null) "No code yet. Enter one, then Test it before saving." else "Change this button's code, then Test it.",
            color = StrataColors.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        if (isCustom) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Button name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
        Segmented(CodeFormat.entries.map { it to it.label }, format, { format = it; error = null })
        Spacer(Modifier.height(14.dp))

        when (format) {
            CodeFormat.Protocol -> {
                Box {
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(shape)
                            .border(1.dp, StrataColors.LineStrong, shape)
                            .clickable(role = Role.Button) { protocolMenu = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Protocol", color = StrataColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                        Text(protocol.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        StrataIcon(StrataIcons.ChevronDown, StrataColors.Muted, size = 18.dp)
                    }
                    DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                        for (p in Protocol.entries) {
                            DropdownMenuItem(
                                text = { Text(p.label, fontSize = 14.sp) },
                                onClick = { protocol = p; protocolMenu = false },
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HexField(address, { address = it }, "Address", Modifier.weight(1f))
                    HexField(command, { command = it }, "Command", Modifier.weight(1f))
                }
                Hint(
                    "Hex values, as a Flipper Zero shows them. ${protocol.label} uses up to " +
                        "${protocol.addressBits}-bit addresses and ${protocol.commandBits}-bit commands.",
                )
            }
            CodeFormat.Pronto -> {
                OutlinedTextField(
                    value = pronto,
                    onValueChange = { pronto = it },
                    label = { Text("Pronto code") },
                    placeholder = { Text("0000 006D 0022 0002 0157 00AC …") },
                    minLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint("Paste a code from an IR code site or forum. Learned codes start with 0000.")
            }
            CodeFormat.Raw -> {
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it.filter(Char::isDigit) },
                    label = { Text("Carrier frequency (Hz)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timings,
                    onValueChange = { timings = it },
                    label = { Text("Timings (µs, on then off)") },
                    minLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                Hint("Alternating on and off times in microseconds, starting with on, as in a Flipper raw signal.")
            }
        }

        val problem = error
        if (problem != null) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                StrataIcon(StrataIcons.Info, StrataColors.Text, size = 16.dp)
                Text(problem, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            }
        } else if (model.status != null) {
            Text(model.status ?: "", color = StrataColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                "Test",
                { build()?.let { model.sendOnce(it, name) } },
                Modifier.weight(1f),
                icon = StrataIcons.Beam,
            )
            PillButton(
                "Save",
                {
                    build()?.let {
                        model.saveButton(device, RemoteButton(target.key, name, it))
                        close()
                    }
                },
                Modifier.weight(1f),
                icon = StrataIcons.Check,
                prominent = true,
            )
        }
        if (existing != null) {
            PillButton(
                if (isCustom) "Delete this button" else "Remove this code",
                {
                    model.removeButton(device, target.key)
                    close()
                },
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                icon = StrataIcons.Trash,
            )
        }
    }
}

@Composable
private fun HexField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("0x00") },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = modifier,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = StrataColors.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
}
